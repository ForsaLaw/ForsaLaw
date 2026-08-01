package com.forsalaw.ragManagement.chat;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Filtre mecanique des citations, applique entre le modele et l'utilisateur.
 *
 * <p><b>Pourquoi mecanique et non par consigne.</b> L'invite decrit et demontre deja le format
 * attendu, ce qui a fait passer les citations de zero a deux par reponse — mais un modele 3B
 * continue de fabriquer des references d'une autre forme (defaut constate en direct :
 * {@code [cc, art. 652]}, un numero d'article invente presente comme une source). Une consigne
 * reduit ce taux, elle ne le supprime pas. Seule une verification en aval garantit qu'aucune
 * autorite inventee n'atteint l'ecran.</p>
 *
 * <p><b>Contrainte du flux : impossible de decider jeton par jeton.</b> Une citation arrive
 * fragmentee — {@code [}, {@code 1}, {@code 2}, {@code ]} peuvent etre quatre jetons distincts.
 * Emettre le crochet ouvrant des sa reception reviendrait a publier une citation avant de savoir
 * si elle est valide. Ce filtre RETIENT donc la sortie des l'ouverture d'un crochet, jusqu'a
 * pouvoir trancher.</p>
 *
 * <p>Trois issues possibles pour un contenu entre crochets :</p>
 * <ul>
 *   <li>{@code [n]} avec 1 &le; n &le; nombre d'extraits fournis : transmis tel quel ;</li>
 *   <li>{@code [n]} avec n hors de cette plage : SUPPRIME — le modele renvoie a un extrait qui
 *       ne lui a jamais ete donne ;</li>
 *   <li>tout autre contenu ({@code [cc, art. 652]}, {@code [source]}...) : SUPPRIME — ce n'est
 *       pas une reference aux extraits, c'est une autorite fabriquee.</li>
 * </ul>
 *
 * <p><b>Consequence assumee :</b> supprimer une citation laisse la phrase sans source plutot
 * qu'avec une fausse. C'est le moindre mal — une affirmation non sourcee se discute, une
 * reference inventee fait autorite aupres de quelqu'un qui n'a pas les moyens de la verifier.
 * Chaque rejet est journalise, pour que le taux de defaut du modele reste mesurable.</p>
 */
@Slf4j
public class AssainisseurCitations {

    /**
     * Au-dela de cette longueur, le contenu entre crochets n'est plus un candidat credible a une
     * citation : on cesse de retenir et on transmet tel quel. Sans cette borne, une phrase
     * contenant un crochet jamais referme bloquerait le flux jusqu'a la fin de la reponse.
     */
    private static final int LONGUEUR_MAX_RETENUE = 40;

    private final int nombreExtraits;
    private final StringBuilder tampon = new StringBuilder();
    private boolean crochetOuvert = false;
    private int rejets = 0;

    public AssainisseurCitations(int nombreExtraits) {
        this.nombreExtraits = nombreExtraits;
    }

    /**
     * Traite un fragment recu du modele et transmet ce qui peut l'etre.
     *
     * @param sortie recoit le texte assaini ; peut ne rien recevoir si tout est retenu
     */
    public void accepter(String fragment, Consumer<String> sortie) {
        StringBuilder aEmettre = new StringBuilder();

        for (int i = 0; i < fragment.length(); i++) {
            char c = fragment.charAt(i);

            if (!crochetOuvert) {
                if (c == '[') {
                    crochetOuvert = true;
                    tampon.setLength(0);
                    tampon.append(c);
                } else {
                    aEmettre.append(c);
                }
                continue;
            }

            tampon.append(c);

            if (c == ']') {
                aEmettre.append(resoudre(tampon.toString()));
                crochetOuvert = false;
                tampon.setLength(0);
            } else if (c == '[') {
                // Crochet ouvrant a l'interieur d'un crochet non referme : le precedent ne sera
                // jamais valide. On le rend tel quel et on repart sur le nouveau.
                tampon.setLength(tampon.length() - 1);
                aEmettre.append(tampon);
                tampon.setLength(0);
                tampon.append('[');
            } else if (tampon.length() > LONGUEUR_MAX_RETENUE) {
                aEmettre.append(tampon);
                crochetOuvert = false;
                tampon.setLength(0);
            }
        }

        if (aEmettre.length() > 0) {
            sortie.accept(aEmettre.toString());
        }
    }

    /**
     * Vide le tampon en fin de flux. Un crochet reste ouvert quand le modele s'interrompt en
     * pleine citation : le texte retenu est alors transmis brut plutot que perdu — il ne
     * constitue pas une citation, donc il ne peut pas faire autorite.
     */
    public void terminer(Consumer<String> sortie) {
        if (tampon.length() > 0) {
            sortie.accept(tampon.toString());
            tampon.setLength(0);
        }
        crochetOuvert = false;
        if (rejets > 0) {
            log.warn("Chat : {} citation(s) non conforme(s) retiree(s) de la reponse "
                    + "({} extraits fournis au modele).", rejets, nombreExtraits);
        }
    }

    /** Nombre de citations rejetees — expose pour les tests et la mesure du taux de defaut. */
    public int rejets() {
        return rejets;
    }

    /** @return la citation si elle est conforme ET dans la plage des extraits, sinon "". */
    private String resoudre(String bloc) {
        String interieur = bloc.substring(1, bloc.length() - 1).trim();

        if (!interieur.isEmpty() && interieur.chars().allMatch(Character::isDigit)) {
            try {
                int n = Integer.parseInt(interieur);
                if (n >= 1 && n <= nombreExtraits) {
                    // Forme normalisee : "[ 2 ]" et "[2]" designent le meme extrait.
                    return "[" + n + "]";
                }
            } catch (NumberFormatException e) {
                // Suite de chiffres trop longue pour un int : ce n'est pas un numero d'extrait.
            }
        }

        rejets++;
        log.debug("Chat : citation non conforme retiree — {}", bloc);
        return "";
    }
}
