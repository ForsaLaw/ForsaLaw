package com.forsalaw.ragManagement.chat.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Classification du domaine via le meme sidecar Ollama auto-heberge que HyDE et la generation.
 *
 * <p><b>Sortie contrainte, jamais interpretee.</b> Le modele doit repondre par une seule
 * etiquette prise dans la liste fournie ; toute sortie qui ne correspond pas EXACTEMENT a une
 * valeur de {@link DomaineJuridique} est rejetee. Deviner le domaine le plus proche produirait
 * une recommandation d'avocats plausible mais fausse — plus nuisible qu'aucune recommandation,
 * l'utilisateur n'ayant aucun moyen de reperer l'erreur.</p>
 *
 * <p>Appel court et non streame : la liste des domaines suffit comme contexte, la question
 * n'est pas reformulee.</p>
 */
@Component
@ConditionalOnProperty(name = "forsalaw.rag.routing.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OllamaDomaineClassifier implements DomaineClassifier {

    /** Aucune reponse credible ne depasse cette taille : au-dela, le modele a disserte. */
    private static final int LONGUEUR_MAX_REPONSE = 120;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String modele;
    private final String consignes;

    public OllamaDomaineClassifier(
            ObjectMapper objectMapper,
            @Value("${forsalaw.rag.routing.base-url:http://localhost:11434}") String baseUrl,
            @Value("${forsalaw.rag.routing.model:qwen2.5:3b-instruct}") String modele,
            @Value("${forsalaw.rag.routing.timeout-seconds:8}") int timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());

        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.modele = modele;
        this.consignes = construireConsignes();
    }

    private static String construireConsignes() {
        return consignesPour(DomaineJuridique.values());
    }

    /**
     * Consignes de classification, derivees du referentiel : ajouter un domaine a l'enum suffit
     * a l'exposer au modele.
     *
     * <p>Visible pour les tests, qui verifient que les desambiguisations mesurees comme
     * necessaires (travail vs affaires, famille vers prive) figurent bien dans l'invite. Ces
     * regles sont la seule chose qui separe une recommandation juste d'une recommandation
     * plausible mais fausse.</p>
     */
    static String consignesPour(DomaineJuridique[] domaines) {
        String catalogue = Arrays.stream(domaines)
                .map(d -> "- " + d.name() + " : " + d.getLibelle())
                .collect(Collectors.joining("\n"));

        return """
                Tu classes la question juridique d'un utilisateur tunisien dans UN domaine du droit.

                Domaines autorises :
                %s

                Distinctions qui posent probleme — applique-les avant de repondre :
                - Licenciement, salaire, contrat de travail, employeur, demission, conges, \
                accident du travail, syndicat => DROIT_TRAVAIL_ET_SOCIAL (JAMAIS \
                DROIT_DES_AFFAIRES : la relation employeur-salarie prime sur le fait que \
                l'employeur soit une entreprise).
                - Societe, associes, fonds de commerce, faillite, concurrence, contrat \
                commercial entre professionnels => DROIT_DES_AFFAIRES.
                - Mariage, divorce, garde d'enfants, succession, filiation, pension \
                alimentaire => DROIT_PRIVE (il n'existe PAS de domaine « famille » distinct).
                - Vente ou location d'un bien, propriete, bail, voisinage => DROIT_PRIVE.
                - Garde a vue, arrestation, plainte, infraction, peine, prison => DROIT_PENAL.
                - Impot, administration, permis, marche public, collectivite => DROIT_PUBLIC.

                Exemples :
                Question : « Mon employeur m'a licencie sans preavis, que faire ? »
                Reponse : DROIT_TRAVAIL_ET_SOCIAL
                Question : « Mon associe veut vendre ses parts sans mon accord. »
                Reponse : DROIT_DES_AFFAIRES
                Question : « Comment se passe la garde des enfants apres un divorce ? »
                Reponse : DROIT_PRIVE

                Reponds UNIQUEMENT par l'etiquette exacte (ex. DROIT_PENAL), sans phrase, sans \
                ponctuation, sans explication. Si la question ne releve clairement d'aucun de ces \
                domaines, reponds exactement AUCUN.""".formatted(catalogue);
    }

    @Override
    public Optional<DomaineJuridique> classer(String question) {
        try {
            // Corps recupere en String puis parse a la main, et NON via .body(Record.class).
            // Ollama renvoie son JSON avec un Content-Type application/octet-stream : la
            // negociation de contenu de RestClient ne trouve alors aucun convertisseur et leve
            // une RestClientException. L'echec etant rattrape plus bas, la fonctionnalite se
            // desactivait en silence — defaut constate en direct, et identique dans
            // OllamaHydeQueryRewriter. Lire du texte brut rend l'appel insensible au Content-Type.
            String corps = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new RequeteOllama(modele, List.of(
                            new Message("system", consignes),
                            new Message("user", question)
                    ), false, Map.of("temperature", 0)))
                    .retrieve()
                    .body(String.class);

            if (corps == null || corps.isBlank()) {
                return Optional.empty();
            }
            JsonNode contenu = objectMapper.readTree(corps).path("message").path("content");
            return interpreter(contenu.isTextual() ? contenu.asText() : null);

        } catch (JsonProcessingException e) {
            log.warn("Routage : reponse illisible du service de classification, aucune recommandation.", e);
            return Optional.empty();

        } catch (RestClientException e) {
            log.warn("Routage : classification du domaine indisponible, aucune recommandation "
                    + "d'avocat ne sera proposee ({}).", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Correspondance EXACTE avec une valeur de l'enum, apres nettoyage typographique.
     *
     * <p>Volontairement stricte : aucune correspondance approximative, aucun repli sur le
     * domaine « le plus proche ». Un domaine faux oriente l'utilisateur vers des avocats de la
     * mauvaise matiere sans qu'il puisse s'en rendre compte.</p>
     */
    Optional<DomaineJuridique> interpreter(String brut) {
        if (brut == null || brut.isBlank() || brut.length() > LONGUEUR_MAX_REPONSE) {
            return Optional.empty();
        }
        // Le modele ajoute regulierement guillemets, points ou puces malgre la consigne.
        String nettoye = brut.strip()
                .replaceAll("[\"'`*.,;:]", "")
                .strip()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_');

        if (nettoye.isEmpty() || "AUCUN".equals(nettoye)) {
            return Optional.empty();
        }
        return Arrays.stream(DomaineJuridique.values())
                .filter(d -> d.name().equals(nettoye))
                .findFirst()
                .or(() -> {
                    log.debug("Routage : sortie « {} » hors referentiel, ignoree.", brut.strip());
                    return Optional.empty();
                });
    }

    private record RequeteOllama(String model, List<Message> messages, boolean stream,
                                 Map<String, Object> options) {}

    private record Message(String role, String content) {}

}
