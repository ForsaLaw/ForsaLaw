package com.forsalaw.ragManagement.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retrait de l'en-tete YAML des fichiers Markdown du corpus.
 *
 * <p>Ce qui se joue ici n'est pas cosmetique : tout caractere laisse dans le corps part dans
 * le vecteur. L'en-tete etant presque identique d'un fichier a l'autre, l'y laisser rapproche
 * artificiellement des articles sans rapport.</p>
 */
class CorpusImportRunnerEnTeteTest {

    @Test
    void enTeteRetire_etCodeLu() {
        String fichier = """
                ---
                source: "jurisite"
                tier: 1
                code: "Constitution_2014"
                lang: "fr"
                url: "https://www.jurisitetunisie.com/tunisie/codes/Constitution_2014/const1005a.htm"
                ---

                الفصل 1

                تونس دولة حرّة، مستقلّة، ذات سيادة.
                """;

        var enTete = CorpusImportRunner.EnTete.lire(fichier);

        assertThat(enTete.code()).isEqualTo("Constitution_2014");
        assertThat(enTete.corps())
                .startsWith("الفصل 1")
                .doesNotContain("jurisite", "url:", "converted_at", "---");
    }

    @Test
    void sansEnTete_contenuIntact() {
        String fichier = "Article 242\nLe vendeur est tenu de deux obligations.";

        var enTete = CorpusImportRunner.EnTete.lire(fichier);

        assertThat(enTete.code()).isNull();
        assertThat(enTete.corps()).isEqualTo(fichier);
    }

    @Test
    void enTeteNonFerme_contenuIntact() {
        // Fichier tronque : mieux vaut ingerer du texte bruite que perdre le document.
        String fichier = "---\nsource: \"jurisite\"\ncode: \"ccl\"\nArticle 1 sans fermeture";

        var enTete = CorpusImportRunner.EnTete.lire(fichier);

        assertThat(enTete.code()).isNull();
        assertThat(enTete.corps()).isEqualTo(fichier);
    }

    @Test
    void codeSansGuillemets_estLu() {
        String fichier = """
                ---
                code: ccl
                tier: 1
                ---
                Article 1
                """;

        assertThat(CorpusImportRunner.EnTete.lire(fichier).code()).isEqualTo("ccl");
    }

    @Test
    void tiretsDansLeCorps_neCoupentPasTropTot() {
        // Un « --- » plus loin dans le texte ne doit pas etre confondu avec la fin de
        // l'en-tete : le corps commence au PREMIER delimiteur fermant, pas au dernier.
        String fichier = """
                ---
                code: "coc"
                ---
                Article 1

                ---

                Article 2
                """;

        var enTete = CorpusImportRunner.EnTete.lire(fichier);

        assertThat(enTete.code()).isEqualTo("coc");
        assertThat(enTete.corps()).startsWith("Article 1").contains("Article 2");
    }
}
