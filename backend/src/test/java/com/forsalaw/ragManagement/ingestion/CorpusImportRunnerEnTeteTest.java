package com.forsalaw.ragManagement.ingestion;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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

    @Test
    void champsDcaf_luesPourUnDocumentSansCode() {
        // legislation-securite (DCAF) n'a pas de champ « code » : id/title/statut/posted
        // doivent etre lus quand meme, pour permettre le repli sur id et le seed de
        // legal_instrument (V12).
        String fichier = """
                ---
                source: "legislation-securite"
                tier: 1
                id: "10004"
                title: "Arrete du 13 juin 2012"
                lang: "ar"
                statut: ["انتهى به العمل"]
                posted: "2021-07-14"
                ---
                Texte de l'arrete.
                """;

        var enTete = CorpusImportRunner.EnTete.lire(fichier);

        assertThat(enTete.code()).isNull();
        assertThat(enTete.id()).isEqualTo("10004");
        assertThat(enTete.title()).isEqualTo("Arrete du 13 juin 2012");
        assertThat(enTete.posted()).isEqualTo(LocalDate.of(2021, 7, 14));
        assertThat(enTete.statut()).containsExactly("انتهى به العمل");
    }

    @Test
    void statutAbsent_listeVide() {
        String fichier = "---\ncode: \"coc\"\n---\nArticle 1\n";

        assertThat(CorpusImportRunner.EnTete.lire(fichier).statut()).isEmpty();
    }

    @Test
    void datePosteeMalformee_estIgnoreePlutotQueRejetee() {
        String fichier = "---\nid: \"1\"\nposted: \"pas-une-date\"\n---\nTexte.\n";

        assertThat(CorpusImportRunner.EnTete.lire(fichier).posted()).isNull();
    }

    @Test
    void titreAvecCaracteresSpeciaux_luJusquauGuillemetFermant() {
        String fichier = """
                ---
                id: "42"
                title: "Decret n° 2020-123 du 5 mars 2020, relatif a..."
                ---
                Texte.
                """;

        assertThat(CorpusImportRunner.EnTete.lire(fichier).title())
                .isEqualTo("Decret n° 2020-123 du 5 mars 2020, relatif a...");
    }
}
