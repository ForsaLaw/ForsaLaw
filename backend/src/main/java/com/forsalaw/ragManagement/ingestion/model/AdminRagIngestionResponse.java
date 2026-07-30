package com.forsalaw.ragManagement.ingestion.model;

/**
 * Resultat d'une ingestion PDF via l'endpoint admin.
 *
 * @param dejaIngere true si {@code sourceReference} existait deja : aucun chunk n'a ete cree
 *                   et le PDF n'a pas ete archive une seconde fois. Distinct d'un succes a
 *                   0 chunk pour que l'administrateur ne confonde pas un doublon avec un echec.
 */
public record AdminRagIngestionResponse(
        String sourceReference,
        String codeName,
        int tier,
        int chunksCrees,
        int propositionsAbrogation,
        boolean dejaIngere
) {}
