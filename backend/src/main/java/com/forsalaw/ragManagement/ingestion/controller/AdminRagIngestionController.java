package com.forsalaw.ragManagement.ingestion.controller;

import com.forsalaw.ragManagement.ingestion.AdminRagIngestionService;
import com.forsalaw.ragManagement.ingestion.model.AdminRagIngestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Depot d'un PDF juridique unique par un administrateur.
 *
 * <p>Complement de {@code CorpusImportRunner} (repertoire entier, hors ligne) : ici, un seul
 * fichier, en direct, avec retour immediat du nombre de chunks crees. Le pipeline
 * extraction/decoupage/vectorisation est le meme dans les deux cas — voir
 * {@link AdminRagIngestionService}.</p>
 */
@RestController
@RequestMapping("/api/admin/rag/ingestion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminRagIngestionController {

    private final AdminRagIngestionService adminRagIngestionService;

    @Operation(
            summary = "Deposer un PDF juridique (Admin)",
            description = "Extrait le texte, decoupe par article, vectorise et insere dans le corpus. "
                    + "tier : 1 = legislation, 2 = jurisprudence, 3 = coffre prive (tenantId alors "
                    + "obligatoire). sourceReference est optionnelle : a defaut, deduite du contenu "
                    + "(SHA-256) pour que redeposer le meme fichier soit reconnu sans identifiant a "
                    + "inventer. Le PDF original est archive dans le stockage objet, retrouvable via "
                    + "sourceReference — une plateforme de preuve doit pouvoir remonter au document "
                    + "source, pas seulement aux chunks extraits."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminRagIngestionResponse> ingererPdf(
            Authentication authentication,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("codeName") String codeName,
            @RequestParam("tier") int tier,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "effectiveDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            @RequestParam(value = "articleTitle", required = false) String articleTitle,
            @RequestParam(value = "sourceReference", required = false) String sourceReference
    ) throws IOException {
        return ResponseEntity.ok(adminRagIngestionService.ingererPdf(
                fichier, codeName, tier, tenantId, effectiveDate, articleTitle,
                sourceReference, authentication.getName()));
    }
}
