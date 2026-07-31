package com.forsalaw.ragManagement.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extrait de OllamaHydeQueryRewriter (voir sa javadoc pour la mesure d'origine) : partage
 * desormais avec OllamaChatGenerationClient, la definition du defaut ne doit pas diverger
 * entre les deux points d'appel.
 */
class ScriptInattenduDetectorTest {

    @Test
    void texteFrancaisOuArabe_neDeclencheRien() {
        assertThat(ScriptInattenduDetector.compterCaracteresInattendus(
                "Le délai de prescription est de quinze ans.")).isZero();
        assertThat(ScriptInattenduDetector.compterCaracteresInattendus(
                "يعاقب مرتكب جريمة السرقة بالسجن.")).isZero();
    }

    @Test
    void basculementChinois_estCompte() {
        long n = ScriptInattenduDetector.compterCaracteresInattendus("لل离婚，申请人必须");
        assertThat(n).isGreaterThan(2);
    }

    @Test
    void caracteresCyrilliques_sontComptes() {
        assertThat(ScriptInattenduDetector.compterCaracteresInattendus("Привет мир"))
                .isGreaterThan(2);
    }

    @Test
    void videOuNul_renvoieZero() {
        assertThat(ScriptInattenduDetector.compterCaracteresInattendus("")).isZero();
        assertThat(ScriptInattenduDetector.compterCaracteresInattendus(null)).isZero();
    }
}
