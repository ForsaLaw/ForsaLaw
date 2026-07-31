package com.forsalaw.ragManagement.chat.controller;

import com.forsalaw.ragManagement.chat.AiChatService;
import com.forsalaw.ragManagement.chat.model.AiChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;

/**
 * Assistant juridique en flux (SSE), fondation de la page Sanctuaire IA du frontend.
 *
 * <p>Requete en POST plutot qu'en GET malgre la convention habituelle de {@code EventSource}
 * (qui ne supporte que GET) : une question juridique peut contenir des details personnels ou
 * sensibles, qui ne doivent jamais transiter par une chaine de requete ou finir dans des
 * journaux de serveur/proxy. Le frontend consomme donc le flux via {@code fetch()} et une
 * lecture manuelle du corps de reponse, pas via {@code new EventSource(url)}.</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    /** Aucun delai serveur : le flux se termine par complete()/completeWithError(), jamais un timeout arbitraire. */
    private static final long AUCUN_DELAI = 0L;

    private final AiChatService aiChatService;
    private final ExecutorService aiChatExecutor;

    @Operation(
            summary = "Poser une question juridique a l'assistant (flux SSE)",
            description = "Recherche les 10 extraits les plus pertinents (niveau 1 ou 2 uniquement), "
                    + "puis genere une reponse en flux via un modele auto-heberge. Evenements SSE : "
                    + "\"jeton\" (fragment de texte), \"erreur\" (message, flux ferme ensuite)."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatRequest requete, Authentication authentication) {
        // Identite capturee ICI, sur le thread de la requete : la generation s'execute sur
        // aiChatExecutor, ou le SecurityContext n'est PAS propage. La lire depuis le pool
        // renverrait un contexte vide et le budget ne serait rattache a personne.
        String email = authentication.getName();

        SseEmitter emitter = new SseEmitter(AUCUN_DELAI);
        aiChatExecutor.execute(() ->
                aiChatService.repondreEnFlux(requete.getQuestion(), requete.getTier(), email, emitter));
        return emitter;
    }
}
