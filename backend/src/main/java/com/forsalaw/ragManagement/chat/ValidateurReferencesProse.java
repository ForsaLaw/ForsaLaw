package com.forsalaw.ragManagement.chat;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifie les references d'articles ecrites EN TOUTES LETTRES contre les extraits reellement
 * fournis au modele.
 *
 * <p><b>Le trou que {@link AssainisseurCitations} ne pouvait pas boucher.</b> Ce dernier
 * n'agit que sur les crochets : {@code [7]} sur trois extraits disparait. Mais un modele 3B
 * n'invente pas seulement des numeros d'extrait, il invente des numeros d'ARTICLE, et il les
 * ecrit dans la phrase. Defaut constate en direct : « les articles 141 du Code de procedure
 * civile et commercial (CPCC) », alors qu'aucun extrait fourni ne portait cet article. Aucun
 * crochet, donc rien a filtrer — et pour qui lit, cette phrase a exactement l'autorite d'une
 * reference exacte. C'est le cas le plus dangereux du produit : une personne sans moyen de
 * verifier repartira avec un numero d'article faux.</p>
 *
 * <p><b>Marquer plutot que supprimer.</b> Retirer « 141 » de « les articles 141 du CPCC »
 * laisserait une phrase qui se lit normalement et affirme toujours quelque chose, en ayant
 * seulement perdu de quoi la contester. La reference est donc remplacee par une mention
 * visible : le lecteur voit qu'un element a ete ecarte, ce qui vaut mieux qu'un texte
 * silencieusement rafistole.</p>
 *
 * <p><b>Meme contrainte de flux que le filtre des crochets.</b> « article 402 » arrive
 * fragmente ; emettre « article » puis decouvrir que « 402 » est faux serait trop tard. La
 * sortie est donc RETENUE des qu'un debut de reference apparait, jusqu'a pouvoir trancher.</p>
 *
 * <p><b>A chainer APRES l'assainisseur de crochets</b> : la mention inserée ici ne doit pas
 * etre reprise pour une citation par un filtre place en aval.</p>
 */
@Slf4j
public class ValidateurReferencesProse {

    /** Mots introduisant une reference, francais et arabe (le modele repond dans la langue posee). */
    private static final List<String> MOTS_CLES =
            List.of("articles", "article", "art.", "art", "الفصول", "الفصل", "المادة", "المواد");

    /** Au-dela, aucune reference credible ne reste en cours de formation : on cesse de retenir. */
    private static final int LONGUEUR_MAX_RETENUE = 48;

    /** Reference complete : mot-cle, espace(s), numero, suffixe ordinal facultatif. */
    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)(articles?|art\\.?|الفصول|الفصل|المادة|المواد)([\\s\\u00A0]+)"
                    + "([0-9\\u0660-\\u0669]+(?:[\\s\\u00A0-]*(?:bis|ter|quater))?)");

    /** Ce qui, apres un mot-cle, peut encore devenir un numero complet. */
    private static final Pattern SUITE_INCOMPLETE = Pattern.compile(
            "(?i)^[\\s\\u00A0.]*[0-9\\u0660-\\u0669]*[\\s\\u00A0-]*"
                    + "(?:b|bi|bis|t|te|ter|q|qu|qua|quat|quate|quater)?$");

    private static final String MENTION_FR = "(réf. non vérifiée)";
    private static final String MENTION_AR = "(مرجع غير مؤكد)";

    private final Set<String> referencesFournies;
    private final StringBuilder tampon = new StringBuilder();
    private int rejets = 0;

    /**
     * @param referencesFournies les {@code article_reference} des extraits remis au modele.
     *     Vide, TOUTE reference en prose est refusee : le modele n'a alors aucune source d'ou
     *     un numero d'article pourrait legitimement provenir.
     */
    public ValidateurReferencesProse(List<String> referencesFournies) {
        this.referencesFournies = referencesFournies.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(ValidateurReferencesProse::normaliser)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void accepter(String fragment, Consumer<String> sortie) {
        tampon.append(fragment);
        int limite = indexRetenue();
        if (limite > 0) {
            String valide = valider(tampon.substring(0, limite));
            tampon.delete(0, limite);
            if (!valide.isEmpty()) {
                sortie.accept(valide);
            }
        }
    }

    /** Vide le tampon en fin de flux : une reference restee incomplete est validee telle quelle. */
    public void terminer(Consumer<String> sortie) {
        if (tampon.length() > 0) {
            String valide = valider(tampon.toString());
            tampon.setLength(0);
            if (!valide.isEmpty()) {
                sortie.accept(valide);
            }
        }
        if (rejets > 0) {
            log.warn("Chat : {} reference(s) d'article inventee(s) en prose, signalee(s) dans la "
                    + "reponse ({} references reellement fournies au modele).",
                    rejets, referencesFournies.size());
        }
    }

    /** Nombre de references refusees — expose pour les tests et la mesure du taux de defaut. */
    public int rejets() {
        return rejets;
    }

    /**
     * Index jusqu'auquel il est sur d'emettre : debut d'une reference encore en formation, ou
     * la fin du tampon si aucune ne l'est.
     */
    private int indexRetenue() {
        int longueur = tampon.length();
        int debut = Math.max(0, longueur - LONGUEUR_MAX_RETENUE);
        for (int j = debut; j < longueur; j++) {
            if (peutEncoreGrandir(tampon.substring(j))) {
                return j;
            }
        }
        return longueur;
    }

    /** Vrai si le reste du tampon peut encore devenir une reference complete. */
    private static boolean peutEncoreGrandir(String reste) {
        for (String mot : MOTS_CLES) {
            // Debut de mot-cle encore incomplet (« artic », « الفص »).
            if (reste.length() < mot.length()
                    && mot.regionMatches(true, 0, reste, 0, reste.length())) {
                return true;
            }
            // Mot-cle complet, suivi de ce qui peut encore devenir un numero.
            if (reste.regionMatches(true, 0, mot, 0, mot.length())
                    && SUITE_INCOMPLETE.matcher(reste.substring(mot.length())).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Remplace toute reference absente des extraits par une mention visible. */
    private String valider(String texte) {
        Matcher m = REFERENCE.matcher(texte);
        StringBuilder sortie = new StringBuilder();
        while (m.find()) {
            String numero = m.group(3);
            String remplacement = referencesFournies.contains(normaliser(numero))
                    ? m.group(0)
                    : mentionPour(m.group(1), m.group(2));
            if (!remplacement.equals(m.group(0))) {
                rejets++;
                log.debug("Chat : reference en prose absente des extraits — {}", m.group(0).strip());
            }
            m.appendReplacement(sortie, Matcher.quoteReplacement(remplacement));
        }
        m.appendTail(sortie);
        return sortie.toString();
    }

    private static String mentionPour(String motCle, String separateur) {
        boolean arabe = !motCle.isEmpty() && motCle.charAt(0) >= '؀';
        return motCle + separateur + (arabe ? MENTION_AR : MENTION_FR);
    }

    /**
     * Ramene « 13 bis », « 13-bis » et « 13bis » a une meme forme, et les chiffres arabes a
     * leurs equivalents ASCII : le corpus ecrit {@code 13bis}, le modele ecrit ce qu'il veut.
     */
    private static String normaliser(String reference) {
        StringBuilder sb = new StringBuilder(reference.length());
        for (char c : reference.toCharArray()) {
            if (c >= '٠' && c <= '٩') {
                sb.append((char) ('0' + c - '٠'));
            } else if (!Character.isWhitespace(c) && c != '-' && c != '.' && c != ' ') {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
