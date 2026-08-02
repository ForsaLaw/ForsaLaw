package com.forsalaw.ragManagement.search;

import com.forsalaw.ragManagement.embedding.EmbeddingClient;
import com.forsalaw.ragManagement.embedding.TeiEmbeddingClient;
import com.forsalaw.ragManagement.hyde.HydeQueryRewriter;
import com.forsalaw.ragManagement.hyde.NoOpHydeQueryRewriter;
import com.forsalaw.ragManagement.hyde.OllamaHydeQueryRewriter;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve, sur le corpus REEL et un Ollama en direct, que la reformulation HyDE ameliore le
 * taux de reussite en top 10 par rapport a la question brute — les 10 questions du jeu
 * d'evaluation, code JAVA reellement expedie (pas le prototype Python utilise pour la
 * decision initiale).
 *
 * <p><b>Desactive par defaut, jamais execute en CI.</b> Necessite : TEI et Ollama en direct
 * (localhost:8092 et 11434 par defaut), et le corpus ingere via {@code CorpusImportRunner}
 * deja present dans la base {@code forsalaw_rag}. Aucun de ces prerequis n'existe dans
 * l'environnement CI (memes raisons que {@code CorpusImportRunner} : des services et des
 * donnees de plusieurs gigaoctets, pas une image Testcontainers ephemere). A executer
 * manuellement (retirer temporairement {@code @Disabled}) pour valider un changement de
 * prompt, de modele ou de logique de validation.</p>
 *
 * <p>Se connecte DIRECTEMENT a la base existante (pas {@code AbstractIntegrationTest}, qui
 * demarre un Postgres Testcontainers VIDE) : re-ingerer 109 024 chunks pour un seul test
 * serait impraticable. Consequence assumee : ce test lit un etat partage, pas isole.</p>
 */
@Disabled("Necessite TEI + Ollama en direct et le corpus reel dans forsalaw_rag ; jamais execute en CI. "
        + "Derniere mesure (implementation reelle, qwen2.5:3b-instruct) : sans HyDE 2/10, avec HyDE 4/10.")
class HydeTopTenHitRateManualTest {

    private static LegalChunkSearchService rechercheAvecHyde;
    private static LegalChunkSearchService rechercheSansHyde;

    @BeforeAll
    static void demarrer() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://localhost:5433/forsalaw_rag", "forsalaw", "forsalaw");
        var chunkRepository = new LegalDocumentChunkRepository(new JdbcTemplate(dataSource));

        EmbeddingClient embeddingClient =
                new TeiEmbeddingClient("http://localhost:8092", 1024, 16, "BAAI/bge-m3", 60);
        HydeQueryRewriter hydeQueryRewriter =
                new OllamaHydeQueryRewriter(new com.fasterxml.jackson.databind.ObjectMapper(), "http://localhost:11434", "qwen2.5:3b-instruct", 15);

        rechercheAvecHyde = new LegalChunkSearchService(embeddingClient, hydeQueryRewriter, chunkRepository);
        rechercheSansHyde = new LegalChunkSearchService(embeddingClient, new NoOpHydeQueryRewriter(), chunkRepository);
    }

    // Le jeu etait auparavant RECOPIE ici, en plus de eval_set.json. Les deux copies avaient
    // diverge (ce code visait « cs » quand le JSON disait « Code des Societes Commerciales »),
    // et aucune ne correspondait au corpus. Le JSON fait desormais foi ; voir JeuEvaluation.
    @Test
    void hydeAmelioreLeTauxDeReussiteEnTop10() {
        // Seules les questions ATTEIGNABLES entrent dans le score : deux entrees visent un
        // article absent du corpus (eval-005, eval-008) et seraient comptees comme des echecs
        // de pertinence alors qu'aucune recherche ne peut les reussir.
        List<JeuEvaluation.Cas> questions = JeuEvaluation.atteignables();
        int total = questions.size();
        int reussitesAvecHyde = 0;
        int reussitesSansHyde = 0;

        for (JeuEvaluation.Cas cas : questions) {
            boolean trouveAvec = contientArticleAttendu(
                    rechercheAvecHyde.rechercher(cas.question(), 1, null, LocalDate.now(), 10), cas);
            boolean trouveSans = contientArticleAttendu(
                    rechercheSansHyde.rechercher(cas.question(), 1, null, LocalDate.now(), 10), cas);

            System.out.printf("%-8s art.%-8s sans_hyde=%-5s avec_hyde=%-5s%n",
                    cas.codeName(), cas.articleReference(), trouveSans, trouveAvec);

            if (trouveAvec) reussitesAvecHyde++;
            if (trouveSans) reussitesSansHyde++;
        }

        System.out.printf("Top 10 : sans HyDE = %d/%d, avec HyDE = %d/%d%n",
                reussitesSansHyde, total, reussitesAvecHyde, total);

        assertThat(reussitesAvecHyde)
                .as("HyDE doit ameliorer (ou au pire egaler) le taux de reussite en top 10")
                .isGreaterThanOrEqualTo(reussitesSansHyde);
        assertThat(reussitesAvecHyde)
                .as("l'amelioration doit etre reelle, pas marginale (mesure prealable : 2/10 -> 6/10)")
                .isGreaterThan(reussitesSansHyde);
    }

    private boolean contientArticleAttendu(
            List<LegalDocumentChunkRepository.ResultatRecherche> resultats, JeuEvaluation.Cas cas) {
        return resultats.stream().anyMatch(r ->
                cas.codeName().equals(r.codeName()) && cas.articleReference().equals(r.articleReference()));
    }
}
