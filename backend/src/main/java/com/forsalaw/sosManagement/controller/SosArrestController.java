package com.forsalaw.sosManagement.controller;

import com.forsalaw.sosManagement.model.SosArrestRequest;
import com.forsalaw.sosManagement.model.SosArrestResponse;
import com.forsalaw.sosManagement.service.SosArrestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prise en charge d'urgence « SOS Arrestation ».
 *
 * <p><b>Le depot d'un signalement est ouvert sans authentification.</b> Une arrestation est
 * signalee par un proche, dans les minutes qui suivent, souvent depuis un telephone qui n'est
 * pas celui du titulaire du compte. Imposer une inscription a cet instant ferait perdre le seul
 * moment ou l'intervention compte. La consultation, elle, reste rattachee au declarant.</p>
 */
@RestController
@RequestMapping("/api/sos")
@RequiredArgsConstructor
@Tag(name = "SOS Arrestation")
public class SosArrestController {

    private final SosArrestService sosArrestService;

    @Operation(
            summary = "Signaler une arrestation (public)",
            description = "Enregistre le signalement puis declenche le reglement. Accessible sans "
                    + "compte : le declarant est le plus souvent un proche. Si l'appelant est "
                    + "authentifie, le signalement lui est rattache."
    )
    @PostMapping("/arrest")
    public ResponseEntity<SosArrestResponse> signaler(
            @Valid @RequestBody SosArrestRequest requete,
            Authentication authentication
    ) {
        // authentication est null quand l'appel est anonyme : c'est le cas nominal ici.
        String email = authentication == null ? null : authentication.getName();
        SosArrestResponse reponse = sosArrestService.enregistrer(requete, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(reponse);
    }

    @Operation(
            summary = "Etat d'un signalement",
            description = "Permet au declarant de suivre le reglement et la mobilisation. "
                    + "L'identifiant fait office de jeton de suivi pour un declarant non inscrit."
    )
    @GetMapping("/arrest/{id}")
    public ResponseEntity<SosArrestResponse> consulter(@PathVariable String id) {
        return ResponseEntity.ok(sosArrestService.consulter(id));
    }

    @Operation(summary = "Mes signalements", description = "Signalements rattaches au compte connecte.")
    @GetMapping("/arrest/mine")
    public ResponseEntity<List<SosArrestResponse>> mesSignalements(Authentication authentication) {
        return ResponseEntity.ok(sosArrestService.mesSignalements(authentication.getName()));
    }
}
