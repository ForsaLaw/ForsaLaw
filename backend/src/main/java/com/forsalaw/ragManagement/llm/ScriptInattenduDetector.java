package com.forsalaw.ragManagement.llm;

import java.util.regex.Pattern;

/**
 * Detection d'un basculement d'alphabet dans la sortie d'un modele auto-heberge (Ollama).
 *
 * <p>Extrait de {@code OllamaHydeQueryRewriter}, ou ce defaut a ete mesure et non suppose :
 * sur 6 essais de reformulation en arabe avec qwen2.5:3b-instruct, 3 sorties etaient degradees,
 * dont un basculement COMPLET en chinois au milieu d'un paragraphe par ailleurs correct. Un
 * succes HTTP ne garantit donc rien sur le contenu — cette verification est ce qui transforme
 * "le modele a repondu" en "la reponse est utilisable".</p>
 *
 * <p>Partage entre HyDE (reformulation de requete, jamais montree a l'utilisateur) et la
 * generation de reponse en flux ({@code OllamaChatGenerationClient}, montree en direct) : le
 * meme modele presente le meme defaut dans les deux roles, la definition du defaut ne doit pas
 * diverger entre les deux points d'appel.</p>
 */
public final class ScriptInattenduDetector {

    private ScriptInattenduDetector() {
    }

    /** Scripts jamais legitimes ici (ni francais, ni arabe) : leur presence signale une sortie degradee. */
    private static final Pattern SCRIPT_INATTENDU = Pattern.compile(
            "[一-鿿぀-ヿ가-힣Ѐ-ӿ]");

    /** Nombre de caracteres d'un alphabet inattendu (chinois, japonais, coreen, cyrillique). */
    public static long compterCaracteresInattendus(String texte) {
        if (texte == null || texte.isEmpty()) {
            return 0;
        }
        return SCRIPT_INATTENDU.matcher(texte).results().count();
    }
}
