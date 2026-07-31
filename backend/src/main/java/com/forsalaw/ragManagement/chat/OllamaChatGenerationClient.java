package com.forsalaw.ragManagement.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forsalaw.ragManagement.llm.ScriptInattenduDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Generation en flux via Ollama, meme sidecar auto-heberge que {@code OllamaHydeQueryRewriter}
 * (voir sa javadoc pour le choix du modele qwen2.5:3b-instruct et son defaut mesure).
 *
 * <p><b>Lecture reellement incrementale, pas {@code RestClient.retrieve().body(Class)}.</b>
 * Cette derniere attend la reponse COMPLETE avant de la deserialiser — inutilisable pour un
 * effet de frappe en direct. {@code RestClient.exchange(...)} donne acces au
 * {@code ClientHttpResponse} avant toute deserialisation : son flux est lu ligne par ligne au
 * fur et a mesure qu'Ollama les envoie (verifie : chaque ligne est un objet JSON independant,
 * {@code {"message":{"content":"..."},"done":false}}, la derniere portant {@code "done":true}).</p>
 *
 * <p><b>Le garde-fou de {@link ScriptInattenduDetector} s'applique ICI AUSSI, jeton par
 * jeton.</b> Contrairement a HydeQueryRewriter (une hypothese complete, jamais montree,
 * validee avant tout usage), une reponse de chat est montree AU FUR ET A MESURE : elle ne peut
 * pas etre validee dans son ensemble avant d'etre affichee sans annuler l'interet du flux. Des
 * qu'un jeton fait franchir le seuil de caracteres inattendus, la lecture s'arrete et
 * {@code surErreur} est appele — mais les jetons deja envoyes avant ce point ne peuvent pas
 * etre retires de l'ecran : c'est la limite assumee d'un flux reellement en direct, pas d'une
 * validation a posteriori sur reponse complete.</p>
 */
@Component
@Slf4j
public class OllamaChatGenerationClient implements ChatGenerationClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String modele;

    public OllamaChatGenerationClient(
            ObjectMapper objectMapper,
            @Value("${forsalaw.rag.chat.base-url:http://localhost:11434}") String baseUrl,
            @Value("${forsalaw.rag.chat.model:qwen2.5:3b-instruct}") String modele,
            @Value("${forsalaw.rag.chat.read-timeout-seconds:30}") int readTimeoutSeconds
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        // S'applique par LECTURE, pas a la duree totale : tant qu'Ollama envoie un jeton dans
        // cette fenetre, une generation plus longue que ce delai continue normalement.
        requestFactory.setReadTimeout((int) Duration.ofSeconds(readTimeoutSeconds).toMillis());

        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.modele = modele;
    }

    @Override
    public void genererEnFlux(List<Message> messages, GestionnaireFlux gestionnaire) {
        Map<String, Object> corps = Map.of(
                "model", modele,
                "messages", messages,
                "stream", true,
                "options", Map.of("temperature", 0.2)
        );

        StringBuilder accumulateur = new StringBuilder();
        try {
            restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .exchange((request, response) -> {
                        try (var flux = response.getBody();
                             var lecteur = new BufferedReader(
                                     new InputStreamReader(flux, StandardCharsets.UTF_8))) {

                            String ligne;
                            while ((ligne = lecteur.readLine()) != null) {
                                if (ligne.isBlank()) {
                                    continue;
                                }
                                JsonNode noeud = objectMapper.readTree(ligne);
                                String delta = texteDelta(noeud);

                                if (!delta.isEmpty()) {
                                    accumulateur.append(delta);
                                    long inattendus = ScriptInattenduDetector
                                            .compterCaracteresInattendus(accumulateur.toString());
                                    if (inattendus > 2) {
                                        log.warn("Chat : {} caractere(s) d'un alphabet inattendu "
                                                + "dans la reponse en cours, flux interrompu.", inattendus);
                                        gestionnaire.surErreur(
                                                "La reponse generee est devenue illisible et a ete interrompue.");
                                        return null;
                                    }
                                    gestionnaire.surJeton(delta);
                                }

                                if (noeud.path("done").asBoolean(false)) {
                                    gestionnaire.surFin();
                                    return null;
                                }
                            }
                            // Flux ferme par Ollama sans ligne "done":true : anormal, mais ne
                            // doit pas laisser l'appelant attendre indefiniment.
                            gestionnaire.surErreur("Le service de generation a ferme la connexion "
                                    + "sans signaler la fin de la reponse.");
                            return null;
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("Chat : service de generation injoignable ou en erreur.", e);
            gestionnaire.surErreur("Le service de generation est indisponible pour le moment.");
        }
    }

    private String texteDelta(JsonNode noeud) {
        JsonNode contenu = noeud.path("message").path("content");
        return contenu.isTextual() ? contenu.asText() : "";
    }
}
