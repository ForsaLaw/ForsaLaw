package com.forsalaw.ragManagement.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Composition des deux filtres, telle que {@code AiChatService} la cable.
 *
 * <p>Chaque filtre est deja couvert isolement. Ce qui se teste ici est leur ASSEMBLAGE, ou se
 * logent les erreurs qu'aucun test unitaire ne verrait : un ordre inverse, ou un tampon vide
 * dans le mauvais sens en fin de flux. Le symptome serait alors une reference inventee qui
 * atteint l'ecran — precisement ce que la chaine existe pour empecher.</p>
 */
class ChaineFiltresCitationsTest {

    /** Rejoue un flux a travers la chaine complete et rend le texte reellement transmis. */
    private static String chaine(int nombreExtraits, List<String> references, String... fragments) {
        AssainisseurCitations assainisseur = new AssainisseurCitations(nombreExtraits);
        ValidateurReferencesProse validateur = new ValidateurReferencesProse(references);
        List<String> sortie = new ArrayList<>();

        for (String f : fragments) {
            assainisseur.accepter(f, texte -> validateur.accepter(texte, sortie::add));
        }
        // Ordre de vidage identique a celui du service : ce que l'assainisseur libere doit
        // encore traverser le validateur.
        assainisseur.terminer(texte -> validateur.accepter(texte, sortie::add));
        validateur.terminer(sortie::add);

        return String.join("", sortie);
    }

    @Test
    void lesDeuxMensonges_sontTraitesDansLeMemeFlux() {
        // Une citation d'extrait hors plage ET une reference d'article inventee, cote a cote.
        String recu = chaine(2, List.of("402"),
                "La prescription est de quinze ans [1]. Voir aussi l'article 999 [7].");

        assertThat(recu).contains("[1]");                    // extrait fourni : conserve
        assertThat(recu).doesNotContain("[7]");              // extrait inexistant : retire
        assertThat(recu).doesNotContain("999");              // article invente : signale
        assertThat(recu).contains("réf. non vérifiée");
    }

    @Test
    void mentionDuValidateur_survitAuFiltreDesCrochets() {
        // L'ordre compte : place en aval, l'assainisseur n'aurait aucune raison de respecter
        // la mention inseree par le validateur. Elle n'utilise donc pas de crochets, et le
        // validateur passe APRES. Ce test verrouille les deux decisions a la fois.
        String recu = chaine(1, List.of(), "Selon l'article 402 [1], le delai court.");

        assertThat(recu).contains("réf. non vérifiée");
        assertThat(recu).doesNotContain("402");
    }

    @Test
    void referenceRetenueJusquAuDernierJeton_estQuandMemeVerifiee() {
        // Le cas que seul l'ordre de vidage attrape : la reference est encore dans le tampon
        // de l'assainisseur quand le flux se termine. Vider le validateur en premier la
        // laisserait sortir sans verification.
        String recu = chaine(3, List.of("402"), "Le texte se termine par l'article 88");

        assertThat(recu).doesNotContain("88");
        assertThat(recu).contains("réf. non vérifiée");
    }

    @Test
    void texteSain_traverseLaChaineIntact() {
        // Un filtre qui abime les reponses correctes coute plus qu'il ne rapporte.
        String texte = "La prescription est de quinze ans [1]. L'article 402 le prevoit [2].";
        assertThat(chaine(2, List.of("402"), texte)).isEqualTo(texte);
    }

    @Test
    void fragmentationArbitraire_neChangeRien() {
        // Le decoupage en jetons ne doit pas influer sur le resultat : c'est la propriete qui
        // distingue une chaine correcte d'une chaine qui marche sur des chaines completes.
        String entier = "Voir l'article 999 [7] et l'article 402 [1].";
        String[] morceaux = entier.split("(?<=\\G.{3})");   // paquets de 3 caracteres

        assertThat(chaine(2, List.of("402"), morceaux))
                .isEqualTo(chaine(2, List.of("402"), entier));
    }
}
