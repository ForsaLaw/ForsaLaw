package com.forsalaw.ragManagement.chat.controller;

import com.forsalaw.ragManagement.chat.model.AiConsentRequest;
import com.forsalaw.ragManagement.chat.model.AiConsentResponse;
import com.forsalaw.ragManagement.chat.service.AiConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consentement au traitement IA, conserve cote serveur.
 *
 * <p>Il n'etait jusqu'ici stocke que dans le {@code localStorage} du navigateur : un utilisateur
 * vidant ses donnees effacait la seule trace de son consentement, qui devenait donc
 * indemontrable — inacceptable pour une plateforme juridique.</p>
 *
 * <p><b>Non applique en blocage sur {@code /api/ai/chat} a ce stade.</b> La base ne contient
 * aucun consentement anterieur (ils n'existent que dans les navigateurs), donc refuser l'acces
 * aux comptes sans ligne en base bloquerait TOUS les utilisateurs existants, y compris ceux
 * ayant deja consenti. Le blocage devra etre active une fois le parc reconsenti.</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "IA - Consentement")
public class AiConsentController {

    private final AiConsentService consentService;

    @Operation(summary = "Consentement IA enregistre pour l'utilisateur courant")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/consent")
    public ResponseEntity<AiConsentResponse> consentement(Authentication authentication) {
        return ResponseEntity.ok(consentService.lire(authentication.getName()));
    }

    @Operation(summary = "Enregistre le consentement IA de l'utilisateur courant")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/consent")
    public ResponseEntity<AiConsentResponse> enregistrer(
            @Valid @RequestBody AiConsentRequest requete,
            Authentication authentication
    ) {
        return ResponseEntity.ok(consentService.enregistrer(authentication.getName(), requete.getVersion()));
    }
}
