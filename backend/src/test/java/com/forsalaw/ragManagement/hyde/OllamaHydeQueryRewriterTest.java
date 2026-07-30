package com.forsalaw.ragManagement.hyde;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation de sortie du modele HyDE, INDEPENDANTE de tout appel reseau.
 *
 * <p>Reproduit les defauts reellement mesures sur qwen2.5:3b-instruct (6 essais de
 * reformulation en arabe, 3 sorties degradees) : un succes HTTP ne garantit rien sur le
 * contenu, donc un simple try/catch reseau n'aurait rien detecte. C'est cette validation,
 * pas le transport, qui porte la garantie de repli sur la question brute.</p>
 */
class OllamaHydeQueryRewriterTest {

    private final OllamaHydeQueryRewriter rewriter =
            new OllamaHydeQueryRewriter("http://localhost:1", "test-model", 1);

    @Test
    void hypotheseFrancaiseValide_estAcceptee() {
        var resultat = rewriter.validerOuRejeter(
                "Le délai de prescription de droit commun est de quinze ans à compter du jour "
                        + "où l'action devient exigible.", "question");

        assertThat(resultat).isPresent();
    }

    @Test
    void hypotheseArabeValide_estAcceptee() {
        var resultat = rewriter.validerOuRejeter(
                "يعاقب مرتكب جريمة السرقة بالسجن مدة تتراوح بين خمس سنوات وعشرين سنة.", "question");

        assertThat(resultat).isPresent();
    }

    @Test
    void basculementCompletVersLeChinois_estRejete() {
        // Reproduit exactement le defaut mesure sur eval-004 (essai 3) : le debut de la
        // reponse est en arabe, puis bascule integralement en chinois au milieu du paragraphe.
        String sortieDegradee = "لل离婚，申请人必须首先向法院提交一份正式的离婚申请书，"
                + "并提供证明双方婚姻关系破裂的证据。随后，法庭将安排一次或多次听证会。";

        assertThat(rewriter.validerOuRejeter(sortieDegradee, "question")).isEmpty();
    }

    @Test
    void motEtrangerIsoleAuMilieuDArabe_estTolere() {
        // Reproduit eval-004 (essai 2) : un seul mot latin ("Tunisia") au milieu d'un texte
        // arabe par ailleurs correct. L'alphabet latin est LEGITIME (c'est celui du francais) —
        // seuls les alphabets jamais attendus ici (chinois, cyrillique...) disqualifient.
        String sortieAvecMotIsole = "للطلاق في Tunisia، يجب تقديم طلب إلى المحكمة المختصة "
                + "مع إثبات الحالة التي تستوجب الطلاق.";

        assertThat(rewriter.validerOuRejeter(sortieAvecMotIsole, "question")).isPresent();
    }

    @Test
    void caracteresCyrilliques_sontRejetes() {
        assertThat(rewriter.validerOuRejeter(
                "Привет, это тестовый текст на русском языке для проверки.", "question"))
                .isEmpty();
    }

    @Test
    void reponseVideOuNulle_estRejetee() {
        assertThat(rewriter.validerOuRejeter("", "question")).isEmpty();
        assertThat(rewriter.validerOuRejeter("   ", "question")).isEmpty();
        assertThat(rewriter.validerOuRejeter(null, "question")).isEmpty();
    }

    @Test
    void reponseTropCourte_estRejetee() {
        assertThat(rewriter.validerOuRejeter("Trop court.", "question")).isEmpty();
    }

    @Test
    void reponseTropLongue_estRejetee() {
        assertThat(rewriter.validerOuRejeter("A".repeat(1600), "question")).isEmpty();
    }
}
