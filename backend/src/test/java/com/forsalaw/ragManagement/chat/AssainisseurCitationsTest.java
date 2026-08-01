package com.forsalaw.ragManagement.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtre mecanique des citations.
 *
 * <p>Le cas decisif n'est pas la chaine complete mais la chaine FRAGMENTEE : en flux, {@code [12]}
 * arrive frequemment en quatre jetons distincts. Un filtre qui ne traiterait que des fragments
 * entiers laisserait passer une citation inventee des que le modele la decoupe.</p>
 */
class AssainisseurCitationsTest {

    /** Rejoue un flux fragment par fragment et rend le texte reellement transmis. */
    private static String filtrer(int nombreExtraits, String... fragments) {
        AssainisseurCitations a = new AssainisseurCitations(nombreExtraits);
        List<String> sortie = new ArrayList<>();
        for (String f : fragments) {
            a.accepter(f, sortie::add);
        }
        a.terminer(sortie::add);
        return String.join("", sortie);
    }

    @Test
    void citationValide_estConservee() {
        assertThat(filtrer(3, "La prescription est de quinze ans [1]."))
                .isEqualTo("La prescription est de quinze ans [1].");
    }

    @Test
    void citationFabriquee_estRetiree() {
        // Defaut CONSTATE en direct : le modele fabrique une reference d'article et la presente
        // comme une source. La phrase subsiste, mais sans autorite inventee.
        assertThat(filtrer(5, "un delai de 6 mois [cc, art. 652] s'applique."))
                .isEqualTo("un delai de 6 mois  s'applique.");
    }

    @Test
    void citationHorsPlage_estRetiree() {
        // Trois extraits fournis : [7] renvoie a une source que le modele n'a jamais recue.
        assertThat(filtrer(3, "Selon [7], le delai varie.")).isEqualTo("Selon , le delai varie.");
        assertThat(filtrer(3, "Selon [0], le delai varie.")).isEqualTo("Selon , le delai varie.");
    }

    @Test
    void citationALaLimiteDeLaPlage_estConservee() {
        assertThat(filtrer(3, "texte [3] fin")).isEqualTo("texte [3] fin");
        assertThat(filtrer(3, "texte [4] fin")).isEqualTo("texte  fin");
    }

    @Test
    void citationFragmentee_estTraiteeCommeUnTout() {
        // LE cas critique : en flux, chaque caractere peut etre un jeton distinct.
        assertThat(filtrer(12, "Le delai ", "[", "1", "2", "]", " est acquis."))
                .isEqualTo("Le delai [12] est acquis.");
    }

    @Test
    void citationFabrikeeFragmentee_estAussiRetiree() {
        // Meme defaut, decoupe : le filtre ne doit pas se laisser contourner par le decoupage.
        assertThat(filtrer(5, "un delai ", "[", "cc, ", "art. ", "652", "]", " existe."))
                .isEqualTo("un delai  existe.");
    }

    @Test
    void citationsConsecutives_sontTraiteesIndividuellement() {
        assertThat(filtrer(3, "fonde sur [1][3] et non [9]."))
                .isEqualTo("fonde sur [1][3] et non .");
    }

    @Test
    void espacesDansLaCitation_sontNormalises() {
        assertThat(filtrer(3, "texte [ 2 ] fin")).isEqualTo("texte [2] fin");
    }

    @Test
    void crochetJamaisReferme_estRenduTelQuel() {
        // Le modele s'interrompt en pleine citation : le texte retenu ne doit pas disparaitre.
        assertThat(filtrer(3, "une phrase [incomplete")).isEqualTo("une phrase [incomplete");
    }

    @Test
    void contenuTropLongEntreCrochets_cesseDetreRetenu() {
        // Sans cette borne, un crochet ouvert dans de la prose bloquerait le flux jusqu'a la fin.
        String longue = "[" + "x".repeat(60) + "]";
        assertThat(filtrer(3, "avant " + longue + " apres")).contains("avant [").contains("apres");
    }

    @Test
    void aucunExtrait_invalideToutesLesCitations() {
        // Recherche vide : aucune citation ne peut etre fondee, y compris [1].
        assertThat(filtrer(0, "Selon [1], c'est ainsi.")).isEqualTo("Selon , c'est ainsi.");
    }

    @Test
    void texteSansCitation_estIntact() {
        String t = "Aucun extrait ne repond a cette question de maniere directe.";
        assertThat(filtrer(3, t)).isEqualTo(t);
    }

    @Test
    void nombreDeRejets_estComptabilise() {
        AssainisseurCitations a = new AssainisseurCitations(2);
        List<String> out = new ArrayList<>();
        a.accepter("[1] ok, [9] non, [cc, art. 1] non plus", out::add);
        a.terminer(out::add);

        // Le compteur rend le taux de defaut du modele mesurable dans le temps.
        assertThat(a.rejets()).isEqualTo(2);
    }
}
