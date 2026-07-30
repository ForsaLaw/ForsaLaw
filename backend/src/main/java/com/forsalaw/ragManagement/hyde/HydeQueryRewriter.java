package com.forsalaw.ragManagement.hyde;

import java.util.Optional;

/**
 * Reformulation HyDE (Hypothetical Document Embeddings) d'une question avant vectorisation.
 *
 * <p>Constat mesure sur le corpus reel ingere (95 465 chunks de niveau 1) : une question
 * conversationnelle embeddee brute manque son article de reponse dans 6 cas sur 10 (absent du
 * top 10, parfois au-dela du rang 4000). Le vocabulaire d'une question et celui d'un article
 * de loi divergent trop pour bge-m3 seul. Embeder a la place une hypothese de redaction
 * imitant le style statutaire — meme fabriquee, meme potentiellement fausse sur le fond —
 * rapproche l'embedding de la reponse reelle.</p>
 *
 * <p>Deux mesures, a ne pas confondre : des hypotheses REDIGEES A LA MAIN (prototype initial,
 * sans les defauts d'un petit modele) faisaient passer le top 10 de 2/10 a 6/10. Avec
 * l'implementation reelle ci-dessous (qwen2.5:3b-instruct, filtree par validation), le meme
 * jeu de 10 questions passe de 2/10 a 4/10 — un gain reel mais plus modeste, le modele
 * generant parfois des hypotheses rejetees par la validation (repli sur la question brute,
 * pas pire que la base) — voir {@code HydeTopTenHitRateManualTest}.</p>
 *
 * <p>L'implementation ne doit JAMAIS faire echouer une recherche : en cas de panne, timeout,
 * ou sortie de mauvaise qualite (voir {@code OllamaHydeQueryRewriter}), elle renvoie
 * {@link Optional#empty()} et l'appelant retombe sur la question brute.</p>
 */
public interface HydeQueryRewriter {

    /**
     * @param question texte de la question utilisateur
     * @param tier      niveau du corpus interroge (1 = legislation, 2 = jurisprudence,
     *                  3 = coffre prive) : oriente la langue de redaction probable
     * @return l'hypothese generee, ou {@link Optional#empty()} si indisponible ou invalide
     */
    Optional<String> reformuler(String question, int tier);
}
