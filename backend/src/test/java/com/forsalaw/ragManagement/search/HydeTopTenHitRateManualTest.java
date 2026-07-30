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
                new OllamaHydeQueryRewriter("http://localhost:11434", "qwen2.5:3b-instruct", 15);

        rechercheAvecHyde = new LegalChunkSearchService(embeddingClient, hydeQueryRewriter, chunkRepository);
        rechercheSansHyde = new LegalChunkSearchService(embeddingClient, new NoOpHydeQueryRewriter(), chunkRepository);
    }

    /** Les 10 questions du jeu d'evaluation (backend/src/test/resources/rag-eval/eval_set.json). */
    private record CasEval(String codeName, String articleReference, String question) {}

    private static final List<CasEval> QUESTIONS = List.of(
            new CasEval("coc", "402", "Quel est le délai de prescription de droit commun en matière d'obligations en Tunisie ?"),
            new CasEval("coc", "2", "ما هي شروط صحة العقد في القانون التونسي؟"),
            new CasEval("csp", "5", "Quel est l'âge minimum légal du mariage en Tunisie ?"),
            new CasEval("csp", "31", "ما هي الإجراءات القانونية للطلاق في تونس؟"),
            new CasEval("ct", "6-4", "Quelle est la durée de la période d'essai dans un contrat de travail à durée indéterminée ?"),
            new CasEval("cp", "264", "ما هي عقوبة السرقة في المجلة الجزائية التونسية؟"),
            new CasEval("cpcc", "185", "Quel est le délai pour former un pourvoi en cassation en matière civile ?"),
            new CasEval("cs", "161", "ما هو الحد الأدنى لرأس مال الشركة خفية الاسم؟"),
            new CasEval("coc", "742", "Quelles sont les obligations du bailleur dans un contrat de louage ?"),
            new CasEval("cpp", "13-bis", "ما هي مدة الاحتفاظ بالمحضر في حالة التلبس بجريمة؟")
    );

    @Test
    void hydeAmelioreLeTauxDeReussiteEnTop10() {
        int reussitesAvecHyde = 0;
        int reussitesSansHyde = 0;

        for (CasEval cas : QUESTIONS) {
            boolean trouveAvec = contientArticleAttendu(
                    rechercheAvecHyde.rechercher(cas.question(), 1, null, LocalDate.now(), 10), cas);
            boolean trouveSans = contientArticleAttendu(
                    rechercheSansHyde.rechercher(cas.question(), 1, null, LocalDate.now(), 10), cas);

            System.out.printf("%-8s art.%-8s sans_hyde=%-5s avec_hyde=%-5s%n",
                    cas.codeName(), cas.articleReference(), trouveSans, trouveAvec);

            if (trouveAvec) reussitesAvecHyde++;
            if (trouveSans) reussitesSansHyde++;
        }

        System.out.printf("Top 10 : sans HyDE = %d/10, avec HyDE = %d/10%n",
                reussitesSansHyde, reussitesAvecHyde);

        assertThat(reussitesAvecHyde)
                .as("HyDE doit ameliorer (ou au pire egaler) le taux de reussite en top 10")
                .isGreaterThanOrEqualTo(reussitesSansHyde);
        assertThat(reussitesAvecHyde)
                .as("l'amelioration doit etre reelle, pas marginale (mesure prealable : 2/10 -> 6/10)")
                .isGreaterThan(reussitesSansHyde);
    }

    private boolean contientArticleAttendu(
            List<LegalDocumentChunkRepository.ResultatRecherche> resultats, CasEval cas) {
        return resultats.stream().anyMatch(r ->
                cas.codeName().equals(r.codeName()) && cas.articleReference().equals(r.articleReference()));
    }
}
