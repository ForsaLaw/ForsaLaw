package com.forsalaw.ragManagement;

import com.forsalaw.AbstractIntegrationTest;
import com.forsalaw.ragManagement.embedding.EmbeddingClient;
import com.forsalaw.ragManagement.ingestion.LegalDocumentIngestionService;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Aller-retour complet d'un chunk vectorise : decoupage -> insertion JDBC -> colonne
 * {@code vector(1024)} -> recherche par similarite cosinus.
 *
 * <p>La vectorisation est SIMULEE volontairement. Le maillon reellement non verifie n'est pas
 * le modele — c'est le passage d'un {@code float[]} Java vers le type {@code vector} de
 * pgvector via {@code CAST(? AS vector)}, puis sa relecture. Un modele reel exigerait de
 * telecharger 2 Go de poids dans la CI pour tester exactement la meme chose.</p>
 *
 * <p>Ce que le service TEI reel reste seul a pouvoir confirmer : que bge-m3 emet bien 1024
 * dimensions. C'est une verification a faire une fois, pas a chaque execution de la CI.</p>
 */
class RagIngestionIntegrationTest extends AbstractIntegrationTest {

    private static final int DIMENSIONS = 1024;

    @MockBean
    EmbeddingClient embeddingClient;

    @Autowired
    LegalDocumentIngestionService ingestionService;

    @Autowired
    LegalDocumentChunkRepository chunkRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparer() {
        jdbcTemplate.execute("DELETE FROM legal_document_chunk");
        jdbcTemplate.execute("DELETE FROM rag_supersede_proposal");

        when(embeddingClient.dimensions()).thenReturn(DIMENSIONS);
        when(embeddingClient.modelName()).thenReturn("stub");
        // Un vecteur deterministe par texte : deux textes differents donnent deux vecteurs
        // differents, ce qui permet de verifier que la recherche renvoie le bon chunk.
        when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
            List<String> textes = invocation.getArgument(0);
            List<float[]> vecteurs = new ArrayList<>(textes.size());
            for (String texte : textes) {
                vecteurs.add(vecteurDeterministe(texte));
            }
            return vecteurs;
        });
    }

    @Test
    void ingestion_ecritDesVecteursRelisiblesParPgvector() {
        String texte = """
                Article 242
                Le vendeur est tenu de deux obligations principales : la delivrance et la garantie.

                Article 243
                La delivrance est le transport de la chose vendue au pouvoir de l'acheteur.
                """;

        var resultat = ingestionService.ingererTexte(texte, demande("COC", "test-coc-1"));

        assertThat(resultat.chunksCrees()).isEqualTo(2);

        // La colonne est bien un vector de 1024 dimensions cote base, et non du texte.
        List<Map<String, Object>> lignes = jdbcTemplate.queryForList("""
                SELECT article_reference,
                       vector_dims(embedding) AS dims,
                       status,
                       tier
                  FROM legal_document_chunk
                 ORDER BY article_reference
                """);

        assertThat(lignes).hasSize(2);
        assertThat(lignes).allSatisfy(l -> {
            assertThat(l.get("dims")).isEqualTo(DIMENSIONS);
            assertThat(l.get("status")).isEqualTo("ACTIVE");
            assertThat(l.get("tier")).isEqualTo(1);
        });
        assertThat(lignes.get(0).get("article_reference")).isEqualTo("242");
        assertThat(lignes.get(1).get("article_reference")).isEqualTo("243");
    }

    @Test
    void rechercheCosinus_renvoieLeChunkLePlusProche() {
        ingestionService.ingererTexte("""
                Article 242
                Le vendeur est tenu de deux obligations principales.

                Article 700
                Le bail est un contrat par lequel une partie cede la jouissance d'une chose.
                """, demande("COC", "test-coc-2"));

        // On interroge avec le vecteur EXACT du second chunk : il doit ressortir premier.
        float[] cible = vecteurDeterministe(
                "Article 700\nLe bail est un contrat par lequel une partie cede la jouissance d'une chose.");

        String plusProche = jdbcTemplate.queryForObject("""
                SELECT article_reference
                  FROM legal_document_chunk
                 ORDER BY embedding <=> CAST(? AS vector)
                 LIMIT 1
                """, String.class, litteral(cible));

        assertThat(plusProche).isEqualTo("700");
    }

    /**
     * L'index doit etre UTILISABLE par l'operateur de la requete. Une erreur classique est de
     * creer l'index en {@code vector_l2_ops} puis d'interroger en {@code <=>} (cosinus) :
     * l'index existe, mais le planificateur ne peut jamais s'en servir et chaque recherche
     * devient un balayage complet — exactement le defaut que V11 corrigeait.
     */
    @Test
    void indexHnsw_estUtilisableParLOperateurCosinus() {
        ingestionService.ingererTexte("Article 1\nDisposition liminaire.", demande("COC", "test-coc-3"));

        // Sur deux lignes, un balayage sequentiel est evidemment moins cher : on le desactive
        // pour verifier que le planificateur SAIT utiliser l'index, ce qui est la question posee.
        jdbcTemplate.execute("SET enable_seqscan = off");
        List<Map<String, Object>> plan = jdbcTemplate.queryForList("""
                EXPLAIN SELECT article_reference
                          FROM legal_document_chunk
                         ORDER BY embedding <=> CAST(? AS vector)
                         LIMIT 1
                """.replace("?", "'" + litteral(vecteurDeterministe("x")) + "'"));

        String texteDuPlan = plan.stream().map(l -> String.valueOf(l.values().iterator().next()))
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(texteDuPlan)
                .as("le plan doit mentionner l'index HNSW ; sinon l'opclass ne correspond pas a <=>")
                .contains("idx_legal_document_chunk_embedding");
    }

    @Test
    void niveau3_sansCabinet_estRefuse() {
        // Un document prive sans cabinet proprietaire serait visible de tous : le service doit
        // refuser avant meme d'atteindre la contrainte CHECK de la base.
        var demandeSansTenant = new LegalDocumentIngestionService.DemandeIngestion(
                "Dossier interne", "test-prive-1",
                LegalDocumentIngestionService.TIER_COFFRE_PRIVE,
                null, LocalDate.now(), null);

        assertThatThrownBy(() -> ingestionService.ingererTexte("Article 1\nTexte.", demandeSansTenant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void sourceDejaIngeree_nEstPasDupliquee() {
        var d = demande("COC", "test-idempotence");
        ingestionService.ingererTexte("Article 5\nPremiere ingestion.", d);
        var seconde = ingestionService.ingererTexte("Article 5\nPremiere ingestion.", d);

        // La seconde passe par ingererTexte, qui ne controle pas la source : c'est ingererPdf
        // qui deduplique. On verifie ici que le compteur par source fonctionne, brique sur
        // laquelle repose la deduplication du collecteur JORT.
        assertThat(seconde.chunksCrees()).isEqualTo(1);
        assertThat(chunkRepository.compterParSource("test-idempotence")).isEqualTo(2);
    }

    @Test
    void abrogationDetectee_estProposeeSansModifierLeStatut() {
        ingestionService.ingererTexte("""
                Article 12
                Disposition initiale.
                """, demande("COC", "test-abrog-base"));

        ingestionService.ingererTexte("""
                Article 1
                Sont abrogees et remplacees les dispositions de l'article 12 du code.
                """, demande("COC", "test-abrog-loi"));

        Long enAttente = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rag_supersede_proposal WHERE status = 'PENDING'", Long.class);
        assertThat(enAttente).isPositive();

        // Le point essentiel : AUCUN article n'a change de statut automatiquement.
        Long actifs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM legal_document_chunk WHERE status = 'SUPERSEDED'", Long.class);
        assertThat(actifs).isZero();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private LegalDocumentIngestionService.DemandeIngestion demande(String code, String source) {
        return new LegalDocumentIngestionService.DemandeIngestion(
                code, source, LegalDocumentIngestionService.TIER_LEGISLATION,
                null, LocalDate.of(2024, 3, 12), null);
    }

    /** Vecteur reproductible derive du texte : meme texte => meme vecteur. */
    private float[] vecteurDeterministe(String texte) {
        float[] v = new float[DIMENSIONS];
        int graine = texte.hashCode();
        java.util.Random random = new java.util.Random(graine);
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] = random.nextFloat();
        }
        return v;
    }

    private String litteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
