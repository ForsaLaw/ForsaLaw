package com.forsalaw.ragManagement.search;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Preuve de bout en bout du probleme constate a l'ingestion reelle : une recherche
 * constitutionnelle faisait remonter l'article de la Constitution de 2014 en premier
 * resultat, sans aucune indication qu'elle avait ete integralement remplacee en 2022.
 *
 * <p>Le vecteur d'embedding est un STUB CONSTANT (identique pour la requete et les deux
 * chunks) : la distance cosinus est donc egale entre les deux candidats, et seul le filtre
 * temporel de {@code rechercherParSimilarite} determine lequel des deux (2014 ou 2022)
 * apparait dans le jeu de resultats a une date donnee. C'est deliberement le comportement
 * teste, pas la pertinence semantique (deja verifiee dans RagIngestionIntegrationTest).</p>
 */
class LegalChunkSearchServiceIntegrationTest extends AbstractIntegrationTest {

    private static final int DIMENSIONS = 1024;

    @MockBean
    EmbeddingClient embeddingClient;

    @Autowired
    LegalDocumentIngestionService ingestionService;

    @Autowired
    LegalChunkSearchService searchService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparer() {
        jdbcTemplate.execute("DELETE FROM legal_document_chunk");
        jdbcTemplate.execute("DELETE FROM rag_supersede_proposal");

        when(embeddingClient.dimensions()).thenReturn(DIMENSIONS);
        when(embeddingClient.modelName()).thenReturn("stub");

        float[] vecteurConstant = new float[DIMENSIONS];
        vecteurConstant[0] = 1.0f;

        when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
            List<String> textes = invocation.getArgument(0);
            List<float[]> vecteurs = new ArrayList<>(textes.size());
            for (String ignore : textes) {
                vecteurs.add(vecteurConstant);
            }
            return vecteurs;
        });
        when(embeddingClient.embedOne(anyString())).thenReturn(vecteurConstant);
    }

    @Test
    void memeQuestion_deuxDatesDeReference_deuxReponsesCorrectes() {
        ingestionService.ingererTexte("""
                Article 31
                La liberte d'opinion, de pensee, d'expression, d'information et de publication
                sont garanties.
                """, demande("Constitution_2014", "test-constit-2014"));

        ingestionService.ingererTexte("""
                Article 37
                Les libertes d'opinion, de pensee, d'expression, d'information et de publication
                sont garanties.
                """, demande("Constitution_2022", "test-constit-2022"));

        var resultats2018 = searchService.rechercher(
                "la liberte d'expression est-elle garantie ?", 1, null, LocalDate.of(2018, 1, 1), 5);

        assertThat(resultats2018)
                .as("en 2018, seule la Constitution de 2014 etait en vigueur")
                .extracting(LegalDocumentChunkRepository.ResultatRecherche::codeName)
                .containsExactly("Constitution_2014");
        assertThat(resultats2018.get(0).instrumentStatus()).isEqualTo("ABROGE");
        assertThat(resultats2018.get(0).inForceUntil()).isEqualTo(LocalDate.of(2022, 8, 16));

        var resultatsAujourdhui = searchService.rechercher(
                "la liberte d'expression est-elle garantie ?", 1, null, LocalDate.now(), 5);

        assertThat(resultatsAujourdhui)
                .as("aujourd'hui, seule la Constitution de 2022 est en vigueur")
                .extracting(LegalDocumentChunkRepository.ResultatRecherche::codeName)
                .containsExactly("Constitution_2022");
        assertThat(resultatsAujourdhui.get(0).instrumentStatus()).isEqualTo("EN_VIGUEUR");
    }

    @Test
    void aucuneLigneInstrument_neBloqueJamaisUnResultat() {
        // La quasi-totalite du corpus n'a pas de ligne legal_instrument (seules deux
        // Constitutions sont seedees en V12) : l'absence d'information ne doit jamais se
        // traduire par un resultat cache.
        ingestionService.ingererTexte("Article 1\nDisposition liminaire.",
                demande("coc", "test-coc-search"));

        var resultats = searchService.rechercher("disposition liminaire", 1, null, LocalDate.now(), 5);

        assertThat(resultats).extracting(LegalDocumentChunkRepository.ResultatRecherche::codeName)
                .contains("coc");
        assertThat(resultats.get(0).instrumentStatus()).isNull();
    }

    private LegalDocumentIngestionService.DemandeIngestion demande(String code, String source) {
        return new LegalDocumentIngestionService.DemandeIngestion(
                code, source, LegalDocumentIngestionService.TIER_LEGISLATION, null, null, null);
    }
}
