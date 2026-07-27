package com.forsalaw.ragManagement.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decoupage d'un texte juridique en chunks alignes sur les ARTICLES.
 *
 * <p>Un decoupage a taille fixe couperait au milieu d'un article et produirait des passages
 * qui ne veulent plus rien dire juridiquement (« ... est puni de » / « cinq ans
 * d'emprisonnement ... »). La frontiere naturelle du droit tunisien est l'article :
 * {@code الفصل 242} en arabe, {@code Article 242} en francais.</p>
 *
 * <p>Repli : si aucun marqueur d'article n'est trouve (preambule, expose des motifs, arret
 * non structure), on decoupe par fenetres avec recouvrement plutot que de renoncer.</p>
 */
@Component
@Slf4j
public class LegalArticleChunker {

    /**
     * Debut d'article, en arabe ou en francais, EN DEBUT DE LIGNE.
     *
     * <p>Les graphies varient d'un code a l'autre, et chaque variante non couverte fait
     * disparaitre SILENCIEUSEMENT tous les articles du code concerne : faute de marqueur,
     * {@link #decouper(String)} se rabat sur un decoupage par fenetres et les references
     * d'article sont perdues. Formes relevees dans le corpus reel :</p>
     * <ul>
     *   <li>{@code Article 242} — forme courante ;</li>
     *   <li>{@code Article 13.-} — PDF officiels de l'Imprimerie Officielle ;</li>
     *   <li>{@code Art. 191. -} — Code des obligations et des contrats (1 318 fois) ;</li>
     *   <li>{@code ART. 116. -} — meme code, 9 fois ;</li>
     *   <li>{@code Article. 10 :} — Code du travail, POINT APRES « Article » (446 articles) ;</li>
     *   <li>{@code Article premier} — 585 fois dans le corpus ;</li>
     *   <li>{@code الفصل 242}, {@code الفصــل 242} (tatweel), {@code المادة 5} ;</li>
     *   <li>chiffres arabes-indiens {@code ٠-٩} des deux cotes.</li>
     * </ul>
     *
     * <p>L'ancrage en debut de ligne est VOLONTAIRE : sans lui, « conformement a l'article 5 »
     * en plein corps de texte serait pris pour un debut d'article et fragmenterait la
     * disposition. En contrepartie, la conversion HTML/PDF -> texte DOIT placer chaque
     * en-tete en debut de ligne. Voir docs/CORPUS_MANIFEST.md.</p>
     *
     * <p>La casse n'est volontairement PAS ignoree : « article 5 » tout en minuscules est,
     * dans ce corpus, une reference au fil du texte, pas un en-tete.</p>
     */
    private static final Pattern DEBUT_ARTICLE = Pattern.compile(
            "(?m)^\\s*(?:"
                    // Arabe : tatweel (ـ) tolere entre les lettres, separateur optionnel.
                    + "(?:الفـ*صـ*ل|المـ*ادة)\\s*[:.\\-]?\\s*([0-9\\u0660-\\u0669]+)"
                    // Francais : Article / Articles / Art / ART, point facultatif apres le
                    // mot ET apres le numero, « premier » accepte comme numero.
                    + "|(?:Articles?|ARTICLES?|Arts?|ARTS?)\\.?\\s*[:.\\-]?\\s*"
                    // Le suffixe reste DANS le groupe capturant : l'article 5 bis est un
                    // article distinct de l'article 5, les confondre fausse toute citation.
                    + "(premier|[0-9\\u0660-\\u0669]+(?:\\s*(?:bis|ter|quater|quinquies|sexies))?)"
                    + ")",
            Pattern.UNICODE_CASE);

    @Value("${forsalaw.rag.chunking.max-chars:4000}")
    private int maxCaracteres;

    @Value("${forsalaw.rag.chunking.overlap-chars:200}")
    private int recouvrement;

    /** Un chunk pret a etre vectorise. */
    public record Chunk(String articleReference, String contenu) {}

    public List<Chunk> decouper(String texte) {
        if (texte == null || texte.isBlank()) {
            return List.of();
        }
        String normalise = normaliser(texte);

        List<Chunk> chunks = decouperParArticles(normalise);
        if (chunks.isEmpty()) {
            log.info("Aucun marqueur d'article detecte : decoupage par fenetres de {} caracteres.",
                    maxCaracteres);
            return decouperParFenetres(normalise, null);
        }
        return chunks;
    }

    private List<Chunk> decouperParArticles(String texte) {
        Matcher matcher = DEBUT_ARTICLE.matcher(texte);

        List<Integer> positions = new ArrayList<>();
        List<String> references = new ArrayList<>();
        while (matcher.find()) {
            positions.add(matcher.start());
            String numero = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            references.add(normaliserReference(numero));
        }
        if (positions.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();

        // Le texte qui PRECEDE le premier marqueur n'appartient a aucun article : decret de
        // promulgation, expose des motifs, ou — pour un arret — l'en-tete, les faits et la
        // procedure. Sans ce chunk il disparaissait purement et simplement.
        // Mesure sur le corpus converti : 83 % du texte d'un arret de cassation et 33 %
        // d'une page de code commencant par un expose partaient a la poubelle.
        // Reference nulle : ce passage n'est pas un article et ne doit pas etre cite comme tel.
        String tete = texte.substring(0, positions.get(0)).trim();
        if (!tete.isEmpty()) {
            chunks.addAll(decouperParFenetres(tete, null));
        }

        for (int i = 0; i < positions.size(); i++) {
            int fin = (i + 1 < positions.size()) ? positions.get(i + 1) : texte.length();
            String corps = texte.substring(positions.get(i), fin).trim();
            if (corps.isEmpty()) {
                continue;
            }
            // Un article exceptionnellement long est refendu, mais garde sa reference : le
            // lecteur doit toujours savoir de quel article provient le passage cite.
            if (corps.length() > maxCaracteres) {
                chunks.addAll(decouperParFenetres(corps, references.get(i)));
            } else {
                chunks.add(new Chunk(references.get(i), corps));
            }
        }
        return chunks;
    }

    private List<Chunk> decouperParFenetres(String texte, String reference) {
        List<Chunk> chunks = new ArrayList<>();
        int pas = Math.max(1, maxCaracteres - recouvrement);
        for (int debut = 0; debut < texte.length(); debut += pas) {
            String fenetre = texte.substring(debut, Math.min(debut + maxCaracteres, texte.length())).trim();
            if (!fenetre.isEmpty()) {
                chunks.add(new Chunk(reference, fenetre));
            }
            if (debut + maxCaracteres >= texte.length()) {
                break;
            }
        }
        return chunks;
    }

    /**
     * Reference comparable : chiffres arabes-indiens (٠-٩) ramenes en chiffres latins,
     * espaces retires, et « premier » ramene a « 1 ».
     *
     * <p>Sans cette normalisation, « ٢٦٤ » et « 264 » designeraient deux articles distincts,
     * et « Article premier » — 585 occurrences dans le corpus — ne se resoudrait jamais
     * lorsqu'un utilisateur demande l'article 1.</p>
     */
    private String normaliserReference(String numero) {
        String brut = numero.trim();
        if (brut.regionMatches(true, 0, "premier", 0, 7)) {
            return "1";
        }
        StringBuilder sb = new StringBuilder(brut.length());
        for (char c : brut.toCharArray()) {
            if (c >= '٠' && c <= '٩') {
                sb.append((char) ('0' + (c - '٠')));
            } else if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Les extractions PDF laissent des espaces insecables et des retours a la ligne au milieu
     * des phrases ; sans normalisation, la regex d'article rate des debuts de ligne.
     */
    private String normaliser(String texte) {
        return texte.replace(' ', ' ')
                .replaceAll("\r\n?", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n");
    }
}
