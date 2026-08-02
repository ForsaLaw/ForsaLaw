package com.forsalaw.ragManagement.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Chargement du jeu d'evaluation depuis {@code rag-eval/eval_set.json}.
 *
 * <p><b>Pourquoi cette classe existe.</b> Le jeu etait recopie A LA MAIN dans le code Java de
 * {@link HydeTopTenHitRateManualTest}, en plus du fichier JSON. Les deux copies avaient
 * diverge : le Java visait le code {@code cs} la ou le JSON disait
 * « Code des Societes Commerciales », et aucun des deux ne correspondait au corpus. Une verite
 * terrain en double exemplaire n'est plus une verite terrain — c'est deux jeux d'evaluation
 * dont on ne sait pas lequel fait foi.</p>
 *
 * <p>Le JSON est desormais la seule source. Toute correction s'y applique une fois.</p>
 */
final class JeuEvaluation {

    private JeuEvaluation() {
    }

    /**
     * @param attainable false quand l'article attendu est ABSENT du corpus : la question ne
     *     peut alors etre reussie par aucune recherche, et la compter dans le denominateur
     *     ferait passer une lacune d'ingestion pour un defaut de pertinence.
     */
    record Cas(String id, String codeName, String articleReference, String question,
               boolean attainable, boolean verified) {}

    static List<Cas> charger() {
        try (InputStream flux = JeuEvaluation.class.getResourceAsStream("/rag-eval/eval_set.json")) {
            if (flux == null) {
                throw new IllegalStateException("rag-eval/eval_set.json introuvable dans le classpath.");
            }
            JsonNode racine = new ObjectMapper().readTree(flux);
            List<Cas> cas = new ArrayList<>();
            for (JsonNode q : racine.path("questions")) {
                JsonNode attendu = q.path("expected");
                cas.add(new Cas(
                        q.path("id").asText(),
                        attendu.path("code_name").asText(),
                        attendu.path("article_reference").asText(),
                        q.path("question").asText(),
                        // Absent du fichier => atteignable : une entree nouvelle est presumee
                        // valide, c'est le test de coherence qui tranche.
                        q.path("attainable").asBoolean(true),
                        q.path("verified").asBoolean(false)));
            }
            return List.copyOf(cas);
        } catch (IOException e) {
            throw new IllegalStateException("Jeu d'evaluation illisible.", e);
        }
    }

    /** Les seuls cas sur lesquels un taux de reussite veut dire quelque chose. */
    static List<Cas> atteignables() {
        return charger().stream().filter(Cas::attainable).toList();
    }
}
