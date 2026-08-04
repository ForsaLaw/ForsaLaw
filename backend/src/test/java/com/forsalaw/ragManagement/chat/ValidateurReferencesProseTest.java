package com.forsalaw.ragManagement.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification des references d'articles ecrites en toutes lettres.
 *
 * <p>Le cas fondateur est reel : la reponse livree en direct ne contenait AUCUN crochet — donc
 * rien pour l'assainisseur de citations — et affirmait pourtant « les articles 141 du Code de
 * procedure civile et commercial (CPCC) » sans qu'aucun extrait ne porte cet article.</p>
 */
class ValidateurReferencesProseTest {

    /** Rejoue un flux fragment par fragment et rend le texte reellement transmis. */
    private static String filtrer(List<String> fournies, String... fragments) {
        ValidateurReferencesProse v = new ValidateurReferencesProse(fournies);
        List<String> sortie = new ArrayList<>();
        for (String f : fragments) {
            v.accepter(f, sortie::add);
        }
        v.terminer(sortie::add);
        return String.join("", sortie);
    }

    @Test
    void referenceInventee_estSignalee() {
        // Le defaut constate en direct, mot pour mot.
        String recu = filtrer(List.of("402", "403"),
                "Selon les articles 141 du Code de procedure civile et commercial (CPCC).");

        assertThat(recu).doesNotContain("141");
        assertThat(recu).contains("réf. non vérifiée");
        // La phrase subsiste : c'est l'autorite inventee qui disparait, pas le propos.
        assertThat(recu).contains("Code de procedure civile");
    }

    @Test
    void referenceReellementFournie_estConservee() {
        assertThat(filtrer(List.of("402"), "L'article 402 fixe la prescription."))
                .isEqualTo("L'article 402 fixe la prescription.");
    }

    @Test
    void referenceFragmentee_estTraiteeCommeUnTout() {
        // En flux, « article 141 » arrive en morceaux : emettre « article » puis decouvrir que
        // 141 est faux serait trop tard, le mot est deja a l'ecran.
        assertThat(filtrer(List.of("402"), "Voir l'arti", "cle ", "14", "1 du code."))
                .doesNotContain("141")
                .contains("réf. non vérifiée");
    }

    @Test
    void referenceValideFragmentee_resteIntacte() {
        assertThat(filtrer(List.of("402"), "Voir l'art", "icle ", "40", "2 du code."))
                .isEqualTo("Voir l'article 402 du code.");
    }

    @Test
    void abreviationArt_estTraitee() {
        assertThat(filtrer(List.of("402"), "Cf. art. 999.")).contains("réf. non vérifiée");
        assertThat(filtrer(List.of("999"), "Cf. art. 999.")).isEqualTo("Cf. art. 999.");
    }

    @Test
    void suffixeOrdinal_estRapproche_quelleQueSoitLaGraphie() {
        // Le corpus ecrit « 13bis » ; le modele ecrit « 13 bis » ou « 13-bis ».
        assertThat(filtrer(List.of("13bis"), "L'article 13 bis prevoit.")).contains("13 bis");
        assertThat(filtrer(List.of("13bis"), "L'article 13-bis prevoit.")).contains("13-bis");
        assertThat(filtrer(List.of("13bis"), "L'article 14 bis prevoit."))
                .contains("réf. non vérifiée");
    }

    @Test
    void referenceArabe_estTraiteeDansSaLangue() {
        // Le modele repond dans la langue de la question : une mention en francais au milieu
        // d'une reponse arabe serait illisible pour son destinataire.
        String recu = filtrer(List.of("402"), "حسب الفصل 141 من المجلة.");
        assertThat(recu).doesNotContain("141").contains("مرجع غير مؤكد");

        assertThat(filtrer(List.of("402"), "حسب الفصل 402 من المجلة."))
                .isEqualTo("حسب الفصل 402 من المجلة.");
    }

    @Test
    void aucunExtrait_refuseToutesLesReferences() {
        // Recherche vide : aucun numero d'article ne peut venir d'une source.
        assertThat(filtrer(List.of(), "L'article 402 s'applique.")).contains("réf. non vérifiée");
    }

    @Test
    void nombresQuiNeSontPasDesReferences_sontIntacts() {
        // Le filtre ne doit toucher qu'aux numeros introduits par un mot-cle : une duree, un
        // montant ou une date n'ont rien a voir avec un article.
        String texte = "La prescription est de 15 ans, pour un montant de 500 dinars en 2024.";
        assertThat(filtrer(List.of("402"), texte)).isEqualTo(texte);
    }

    @Test
    void motCommencantParArt_nEstPasPrisPourUneReference() {
        String texte = "L'articulation de ces regles et l'artisanat local sont concernes.";
        assertThat(filtrer(List.of("402"), texte)).isEqualTo(texte);
    }

    @Test
    void referencesMultiples_sontValideesIndividuellement() {
        String recu = filtrer(List.of("402"), "Les articles 402 et l'article 700 s'appliquent.");
        assertThat(recu).contains("402").doesNotContain("700").contains("réf. non vérifiée");
    }

    @Test
    void texteSansReference_estIntact() {
        String texte = "Aucun extrait ne repond directement a cette question.";
        assertThat(filtrer(List.of("402"), texte)).isEqualTo(texte);
    }

    @Test
    void referenceJamaisTerminee_neBloquePasLeFlux() {
        // Le modele s'interrompt en pleine reference : le texte retenu doit ressortir.
        assertThat(filtrer(List.of("402"), "La phrase se termine par l'article"))
                .isEqualTo("La phrase se termine par l'article");
    }

    @Test
    void nombreDeRejets_estComptabilise() {
        ValidateurReferencesProse v = new ValidateurReferencesProse(List.of("402"));
        List<String> out = new ArrayList<>();
        v.accepter("article 402, article 700, art. 900", out::add);
        v.terminer(out::add);

        assertThat(v.rejets()).isEqualTo(2);
    }
}
