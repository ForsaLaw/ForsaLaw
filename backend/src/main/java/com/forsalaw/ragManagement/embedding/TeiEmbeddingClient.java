package com.forsalaw.ragManagement.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client du service Text Embeddings Inference (TEI) de HuggingFace, qui sert bge-m3.
 *
 * <p>Le modele tourne dans un conteneur voisin plutot que dans la JVM : il n'existe pas de
 * runtime d'inference correct pour ce type de modele en Java, et un sidecar permet de
 * changer de modele sans toucher au backend.</p>
 *
 * <p>TEI expose {@code POST /embed} avec {@code {"inputs": [...]}} et renvoie un tableau de
 * tableaux de flottants, dans l'ordre des entrees.</p>
 */
@Component
@Slf4j
public class TeiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final int dimensions;
    private final int tailleLot;
    private final String modelName;

    public TeiEmbeddingClient(
            @Value("${forsalaw.rag.embedding.base-url}") String baseUrl,
            @Value("${forsalaw.rag.embedding.dimensions:1024}") int dimensions,
            @Value("${forsalaw.rag.embedding.batch-size:16}") int tailleLot,
            @Value("${forsalaw.rag.embedding.model-name:BAAI/bge-m3}") String modelName,
            @Value("${forsalaw.rag.embedding.timeout-seconds:120}") int timeoutSeconds
    ) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        // La vectorisation d'un lot sur CPU peut etre lente : un timeout court transformerait
        // une ingestion lente en ingestion echouee.
        requestFactory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.dimensions = dimensions;
        this.tailleLot = tailleLot;
        this.modelName = modelName;
    }

    @Override
    public List<float[]> embed(List<String> textes) {
        if (textes == null || textes.isEmpty()) {
            return List.of();
        }

        List<float[]> resultats = new ArrayList<>(textes.size());
        // Decoupage en lots : un envoi unique de plusieurs centaines de chunks depasserait la
        // memoire GPU du service et ferait echouer l'ingestion entiere plutot qu'un seul lot.
        for (int debut = 0; debut < textes.size(); debut += tailleLot) {
            List<String> lot = textes.subList(debut, Math.min(debut + tailleLot, textes.size()));
            resultats.addAll(appelerService(lot));
        }
        return resultats;
    }

    private List<float[]> appelerService(List<String> lot) {
        try {
            float[][] reponse = restClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RequeteTei(lot))
                    .retrieve()
                    .body(float[][].class);

            if (reponse == null || reponse.length != lot.size()) {
                throw new EmbeddingException("Reponse du service de vectorisation incoherente : "
                        + (reponse == null ? "vide" : reponse.length + " vecteurs")
                        + " pour " + lot.size() + " texte(s).");
            }

            for (float[] vecteur : reponse) {
                // Une dimension inattendue signifie que le service sert un AUTRE modele que
                // celui pour lequel la colonne vector(N) a ete creee. Mieux vaut echouer ici
                // que remplir la base de vecteurs inexploitables.
                if (vecteur.length != dimensions) {
                    throw new EmbeddingException("Dimension inattendue : " + vecteur.length
                            + " au lieu de " + dimensions + ". Le service sert-il bien " + modelName + " ?");
                }
            }
            return List.of(reponse);

        } catch (RestClientException e) {
            throw new EmbeddingException("Service de vectorisation injoignable ou en erreur.", e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    /** Corps de requete TEI. */
    private record RequeteTei(List<String> inputs) {}
}
