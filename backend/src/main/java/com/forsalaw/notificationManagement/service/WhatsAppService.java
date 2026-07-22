package com.forsalaw.notificationManagement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service qui délègue l'envoi WhatsApp au micro-bridge Node.js local.
 * Le bridge écoute sur http://localhost:3099 et exige un jeton Bearer partagé
 * (forsalaw.whatsapp.bridge-token / WHATSAPP_BRIDGE_TOKEN) sur chaque requête.
 */
@Service
@Slf4j
public class WhatsAppService {

    private final RestTemplate restTemplate;

    @Value("${forsalaw.whatsapp.bridge-url:http://localhost:3099}")
    private String bridgeUrl;

    @Value("${forsalaw.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${forsalaw.whatsapp.bridge-token:}")
    private String bridgeToken;

    public WhatsAppService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Envoie un message WhatsApp via le bridge Node.js.
     *
     * @param telephone Numéro international (ex: +21612345678)
     * @param message   Texte du message
     */
    public void envoyer(String telephone, String message) {
        if (!enabled) {
            log.info("[WhatsApp DISABLED] -> {} : {}", telephone, message.substring(0, Math.min(50, message.length())));
            return;
        }
        try {
            Map<String, String> body = Map.of("to", telephone, "message", message);
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String resp = restTemplate.exchange(
                    bridgeUrl + "/send", HttpMethod.POST, new HttpEntity<>(body, headers), String.class
            ).getBody();
            log.info("[WhatsApp] Message envoyé à {} : {}", telephone, resp);
        } catch (RestClientException e) {
            log.error("[WhatsApp] Bridge inaccessible ou refus : {}", e.getMessage());
        }
    }

    /**
     * Récupère le QR Code (Base64) depuis le bridge pour la connexion initiale.
     */
    public String getQrCode() {
        try {
            return restTemplate.exchange(
                    bridgeUrl + "/qr", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class
            ).getBody();
        } catch (Exception e) {
            log.error("[WhatsApp] Impossible de récupérer le QR Code : {}", e.getMessage());
            return null;
        }
    }

    /**
     * Vérifie si le bridge est en ligne et la session est active.
     */
    public Map<?, ?> getStatus() {
        try {
            return restTemplate.exchange(
                    bridgeUrl + "/status", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class
            ).getBody();
        } catch (Exception e) {
            return Map.of("connected", false, "error", e.getMessage());
        }
    }

    /** En-têtes portant le jeton Bearer partagé avec le bridge (si configuré). */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (bridgeToken != null && !bridgeToken.isBlank()) {
            headers.setBearerAuth(bridgeToken);
        }
        return headers;
    }
}
