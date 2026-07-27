package com.forsalaw.ragManagement.ingestion.controller;

import com.forsalaw.ragManagement.ingestion.RagSupersedeAdminService;
import com.forsalaw.ragManagement.ingestion.model.ConfirmerPropositionRequest;
import com.forsalaw.ragManagement.ingestion.model.DeclarerInstrumentSupersededRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Revue humaine des propositions d'abrogation (V11) et declaration de remplacement
 * d'instrument (V12). Aucune de ces actions n'est automatique : voir
 * {@code RagSupersedeAdminService}.
 */
@RestController
@RequestMapping("/api/admin/rag/supersede")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminRagSupersedeController {

    private final RagSupersedeAdminService supersedeAdminService;

    @Operation(summary = "Propositions d'abrogation en attente de revue (Admin)")
    @GetMapping("/proposals")
    public ResponseEntity<List<RagSupersedeAdminService.Proposition>> listerEnAttente() {
        return ResponseEntity.ok(supersedeAdminService.listerEnAttente());
    }

    @Operation(summary = "Confirmer une proposition : l'article passe SUPERSEDED (Admin)")
    @PostMapping("/proposals/{id}/confirm")
    public ResponseEntity<Void> confirmer(Authentication authentication, @PathVariable long id,
                                          @Valid @RequestBody ConfirmerPropositionRequest request) {
        supersedeAdminService.confirmer(id, request.getSupersededAt(), authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Rejeter une proposition (faux positif) (Admin)")
    @PostMapping("/proposals/{id}/reject")
    public ResponseEntity<Void> rejeter(Authentication authentication, @PathVariable long id) {
        supersedeAdminService.rejeter(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Declarer qu'un instrument entier en remplace un autre (Admin)",
            description = "Pour un remplacement TOTAL (ex. Constitution 2014 -> 2022), qui ne "
                    + "s'exprime jamais comme une abrogation d'article dans le texte remplacant "
                    + "et ne peut donc pas etre detecte automatiquement.")
    @PutMapping("/instruments/{codeName}")
    public ResponseEntity<Void> declarerInstrumentSuperseded(
            Authentication authentication, @PathVariable String codeName,
            @Valid @RequestBody DeclarerInstrumentSupersededRequest request) {
        supersedeAdminService.declarerInstrumentSuperseded(
                codeName, request.getTitle(), request.getInForceFrom(), request.getInForceUntil(),
                request.getSupersededByCodeName(), authentication.getName(), request.getNote());
        return ResponseEntity.noContent().build();
    }
}
