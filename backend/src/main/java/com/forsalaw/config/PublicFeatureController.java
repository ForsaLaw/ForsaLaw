package com.forsalaw.config;

import com.forsalaw.sosManagement.SosFeatureProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fonctionnalites activees, telles que le front doit les voir.
 *
 * <p>Le front ne peut pas deviner cet etat : dupliquer l'interrupteur dans une variable de build
 * ferait diverger les deux — un bouton visible alors que l'API repond 503, ou l'inverse. Le
 * serveur reste la seule source de verite, le front s'y conforme.</p>
 *
 * <p>N'expose QUE des booleens d'activation, jamais de configuration : cet endpoint est public
 * et non authentifie.</p>
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Configuration publique")
public class PublicFeatureController {

    private final SosFeatureProperties sosFeature;

    @Operation(
            summary = "Fonctionnalites activees (public)",
            description = "Permet au frontend de masquer une fonctionnalite desactivee cote serveur."
    )
    @GetMapping("/features")
    public ResponseEntity<Map<String, Boolean>> features() {
        return ResponseEntity.ok(Map.of("sosArrest", sosFeature.isEnabled()));
    }
}
