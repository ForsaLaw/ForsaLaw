package com.forsalaw.ragManagement.chat;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.forsalaw.ragManagement.ingestion.LegalDocumentIngestionService;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository.ResultatRecherche;
import com.forsalaw.ragManagement.search.LegalChunkSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Orchestration de {@code /api/ai/chat} : recherche (avec HyDE, deja integre a
 * {@link LegalChunkSearchService}), construction du prompt RAG, generation en flux.
 *
 * <p><b>Restreint aux niveaux 1 et 2 pour cette version.</b> Le niveau 3 (coffre prive)
 * exigerait de verifier qu'un cabinet a le droit d'interroger tel {@code tenantId} — aucune
 * entite cabinet n'existe encore pour cette verification (meme limite deja documentee sur
 * {@code AdminRagIngestionService} et sur la colonne {@code tenant_id} elle-meme). Plutot que
 * de reproduire ce risque ici, le niveau 3 est refuse explicitement.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private static final int NOMBRE_CHUNKS = 10;

    private final LegalChunkSearchService searchService;
    private final ChatGenerationClient chatGenerationClient;

    /**
     * Consignes de citation stricte : le modele ne doit repondre qu'a partir des extraits
     * fournis, jamais inventer une reference absente du contexte. La sortie reste generee par
     * un modele 3B dont le taux de defaut a ete mesure (voir OllamaChatGenerationClient) : ces
     * consignes reduisent le risque, elles ne l'eliminent pas — d'ou le garde-fou de script en
     * aval et le disclaimer permanent cote interface (AiDisclaimerBanner).
     */
    private static final String CONSIGNES = """
            Tu es un assistant d'information juridique tunisien. Reponds a la question de \
            l'utilisateur UNIQUEMENT a partir des extraits ci-dessous.

            Regles strictes :
            - Cite les extraits par leur numero entre crochets, ex. [1], [2].
            - Si aucun extrait ne repond a la question, dis-le explicitement plutot que \
            d'inventer une reponse ou une reference.
            - N'invente JAMAIS de numero d'article, de code ou de citation absent des extraits.
            - Reponds dans la langue de la question.
            - Rappelle que tu ne remplaces pas l'avis d'un avocat inscrit au barreau.
            """;

    public void repondreEnFlux(String question, int tier, SseEmitter emitter) {
        if (tier != LegalDocumentIngestionService.TIER_LEGISLATION
                && tier != LegalDocumentIngestionService.TIER_JURISPRUDENCE) {
            envoyerErreurEtFermer(emitter, "Seuls les niveaux 1 (legislation) et 2 (jurisprudence) "
                    + "sont accessibles depuis cet assistant.");
            return;
        }

        List<ResultatRecherche> resultats;
        try {
            resultats = searchService.rechercher(question, tier, null, LocalDate.now(), NOMBRE_CHUNKS);
        } catch (RuntimeException e) {
            log.error("Chat : recherche impossible pour la question posee.", e);
            envoyerErreurEtFermer(emitter, "La recherche dans le corpus juridique a echoue.");
            return;
        }

        String prompt = construirePrompt(resultats, question);

        var messages = List.of(
                new ChatGenerationClient.Message("system", CONSIGNES),
                new ChatGenerationClient.Message("user", prompt)
        );

        chatGenerationClient.genererEnFlux(messages, new ChatGenerationClient.GestionnaireFlux() {
            @Override
            public void surJeton(String delta) {
                try {
                    emitter.send(SseEmitter.event().name("jeton").data(encoderJeton(delta)));
                } catch (IOException e) {
                    // Le client a ferme la connexion (onglet ferme, navigation) : rien de plus
                    // a faire, l'emitter se completera de lui-meme via son propre listener.
                    log.debug("Chat : envoi impossible, client probablement deconnecte.", e);
                }
            }

            @Override
            public void surFin() {
                emitter.complete();
            }

            @Override
            public void surErreur(String messageErreur) {
                envoyerErreurEtFermer(emitter, messageErreur);
            }
        });
    }

    /**
     * Encode un jeton en chaine JSON (guillemets compris) avant de l'envoyer en SSE.
     *
     * <p>Indispensable : le protocole SSE est sensible aux espaces et aux sauts de ligne. Un
     * client conforme retire UN espace optionnel apres {@code data:}, alors que le SseEmitter de
     * Spring ecrit {@code data:} sans espace — un jeton commencant par une espace perdait donc
     * son espace (defaut constate : "Selonlesextraits..."). Un jeton contenant un saut de ligne
     * casserait en plus le decoupage des trames. Encoder en JSON rend le transport neutre :
     * espaces, sauts de ligne et caracteres speciaux traversent intacts, le client faisant
     * simplement {@code JSON.parse} (voir sse.js / AiSanctumPage.jsx).</p>
     */
    private static String encoderJeton(String delta) {
        return '"' + new String(JsonStringEncoder.getInstance().quoteAsString(delta)) + '"';
    }

    private void envoyerErreurEtFermer(SseEmitter emitter, String messageErreur) {
        try {
            emitter.send(SseEmitter.event().name("erreur").data(messageErreur));
        } catch (IOException ignored) {
            // Client deja deconnecte : rien a envoyer.
        } finally {
            emitter.complete();
        }
    }

    private String construirePrompt(List<ResultatRecherche> resultats, String question) {
        StringBuilder sb = new StringBuilder();
        if (resultats.isEmpty()) {
            sb.append("Aucun extrait pertinent n'a ete trouve dans le corpus pour cette question.\n\n");
        } else {
            sb.append("Extraits du corpus juridique tunisien :\n\n");
            for (int i = 0; i < resultats.size(); i++) {
                ResultatRecherche r = resultats.get(i);
                sb.append('[').append(i + 1).append("] ")
                        .append(r.codeName())
                        .append(", art. ").append(r.articleReference())
                        .append('\n')
                        .append('"').append(r.content().strip()).append('"')
                        .append("\n\n");
            }
        }
        sb.append("Question : ").append(question);
        return sb.toString();
    }
}
