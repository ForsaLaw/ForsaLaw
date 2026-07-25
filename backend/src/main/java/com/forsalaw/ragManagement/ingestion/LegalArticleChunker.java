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
     * Debut d'article, en arabe ou en francais, en debut de ligne.
     * L'arabe utilise « الفصل » (code) ou « الفصل » suivi du numero ; le francais « Article »
     * ou l'abreviation « Art. ». Les chiffres peuvent etre arabes-indiens (٠-٩).
     */
    private static final Pattern DEBUT_ARTICLE = Pattern.compile(
            "(?m)^\\s*(?:(?:الفصل|الفصــل|المادة)\\s*([0-9\\u0660-\\u0669]+)"
                    + "|(?:Article|ARTICLE|Art\\.)\\s*([0-9]+(?:\\s*(?:bis|ter|quater))?))",
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

    /** Chiffres arabes-indiens (٠-٩) ramenes aux chiffres latins pour une reference comparable. */
    private String normaliserReference(String numero) {
        StringBuilder sb = new StringBuilder(numero.length());
        for (char c : numero.trim().toCharArray()) {
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
