package com.forsalaw.ragManagement.search;

import com.forsalaw.ragManagement.embedding.EmbeddingClient;
import com.forsalaw.ragManagement.hyde.HydeQueryRewriter;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Recherche semantique dans le corpus juridique.
 *
 * <p>Ce service n'existait pas avant ce changement : aucune recherche vectorielle n'etait
 * appelee depuis du code Java, seulement verifiee manuellement en base lors de la premiere
 * ingestion reelle. C'est la ou le probleme de supersession a ete constate — une question
 * constitutionnelle faisait remonter l'article de la Constitution de 2014 en premier resultat,
 * sans aucune indication qu'elle avait ete integralement remplacee en 2022 — d'ou le filtrage
 * par date porte par {@link LegalDocumentChunkRepository#rechercherParSimilarite}.</p>
 *
 * <p><b>Reformulation HyDE avant vectorisation.</b> Mesure sur le corpus reel (95 465 chunks
 * de niveau 1) : une question embeddee brute manque son article de reponse dans 6 cas sur 10.
 * {@link HydeQueryRewriter} tente de generer une hypothese de redaction statutaire, EMBEDDEE A
 * LA PLACE de la question quand elle est disponible. Aucune tentative n'est OBLIGATOIRE : en
 * cas d'echec, de timeout, ou de sortie invalide, {@code reformuler} renvoie
 * {@link java.util.Optional#empty()} et la question brute est embeddee comme avant — une
 * recherche ne doit jamais echouer parce que la reformulation a echoue.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegalChunkSearchService {

    private final EmbeddingClient embeddingClient;
    private final HydeQueryRewriter hydeQueryRewriter;
    private final LegalDocumentChunkRepository chunkRepository;

    /**
     * @param asOf date de reference pour la validite temporelle ; {@code null} = aujourd'hui.
     *             Permet a la fois « quel est le droit en vigueur ? » (asOf = aujourd'hui,
     *             valeur par defaut) et « ce texte etait-il en vigueur en 2018 ? » (asOf passe).
     */
    public List<LegalDocumentChunkRepository.ResultatRecherche> rechercher(
            String requete, int tier, String tenantId, LocalDate asOf, int limite) {

        validerParametres(requete, tier, tenantId, limite);

        String texteAEmbedder = hydeQueryRewriter.reformuler(requete, tier).orElseGet(() -> {
            log.debug("HyDE indisponible ou rejetee : embedding de la question brute.");
            return requete;
        });

        float[] vecteur = embeddingClient.embedOne(texteAEmbedder);
        return chunkRepository.rechercherParSimilarite(vecteur, tier, tenantId, asOf, limite);
    }

    private void validerParametres(String requete, int tier, String tenantId, int limite) {
        if (requete == null || requete.isBlank()) {
            throw new IllegalArgumentException("La requete de recherche est obligatoire.");
        }
        if (tier < 1 || tier > 3) {
            throw new IllegalArgumentException("tier doit valoir 1, 2 ou 3.");
        }
        // Symetrique de la contrainte CHECK posee en V11 sur legal_document_chunk : un appel
        // niveau 3 sans cabinet interrogerait potentiellement les coffres de TOUS les cabinets.
        if (tier == 3 && (tenantId == null || tenantId.isBlank())) {
            throw new IllegalArgumentException(
                    "Un cabinet (tenantId) est obligatoire pour interroger le niveau 3 (coffre prive).");
        }
        if (tier != 3 && tenantId != null) {
            throw new IllegalArgumentException("tenantId n'a de sens que pour le niveau 3 (coffre prive).");
        }
        if (limite < 1 || limite > 50) {
            throw new IllegalArgumentException("limite doit etre comprise entre 1 et 50.");
        }
    }
}
