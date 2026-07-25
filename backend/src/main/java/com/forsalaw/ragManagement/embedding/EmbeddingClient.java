package com.forsalaw.ragManagement.embedding;

import java.util.List;

/**
 * Vectorisation de texte pour la recherche semantique.
 *
 * <p>Interface volontairement minimale : changer de modele doit rester un changement de
 * configuration, pas une reecriture. Le modele retenu (bge-m3) est AUTO-HEBERGE, choix
 * dicte par le niveau 3 du corpus — les pieces confidentielles des cabinets ne doivent pas
 * transiter par une API tierce pour etre vectorisees.</p>
 */
public interface EmbeddingClient {

    /**
     * Vectorise un lot de textes.
     *
     * @return un vecteur par texte, dans le MEME ordre que l'entree
     * @throws EmbeddingException si le service est injoignable ou repond de travers
     */
    List<float[]> embed(List<String> textes);

    /** Dimension attendue des vecteurs : doit correspondre a la colonne vector(N) en base. */
    int dimensions();

    /** Nom du modele, trace lors de l'ingestion pour savoir avec quoi un corpus a ete vectorise. */
    String modelName();

    default float[] embedOne(String texte) {
        return embed(List.of(texte)).get(0);
    }
}
