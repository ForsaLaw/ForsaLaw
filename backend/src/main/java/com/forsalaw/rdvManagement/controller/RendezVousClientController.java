package com.forsalaw.rdvManagement.controller;

import com.forsalaw.rdvManagement.model.*;
import com.forsalaw.rdvManagement.service.AvocatAgendaService;
import com.forsalaw.rdvManagement.service.RendezVousService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequestMapping("/api/rendezvous")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
@SecurityRequirement(name = "bearerAuth")
public class RendezVousClientController {

    private final RendezVousService rendezVousService;
    private final AvocatAgendaService avocatAgendaService;

    @Operation(summary = "Lister les creneaux disponibles pour un avocat (selon son agenda, fuseau horaire de l'avocat)")
    @GetMapping("/avocats/{idAvocat}/creneaux-disponibles")
    public ResponseEntity<CreneauxDisponiblesResponse> listerCreneauxDisponibles(
            @PathVariable String idAvocat,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime debut,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime fin
    ) {
        return ResponseEntity.ok(avocatAgendaService.listerCreneauxDisponiblesPourAvocat(idAvocat, debut, fin));
    }

    @Operation(summary = "Creer une demande depuis le profil avocat")
    @PostMapping("/avocats/{idAvocat}/demandes")
    public ResponseEntity<RendezVousDTO> creerDemande(
            Authentication authentication,
            @PathVariable String idAvocat,
            @RequestBody CreerDemandeRendezVousRequest request
    ) {
        return ResponseEntity.ok(rendezVousService.creerDemandeClient(authentication.getName(), idAvocat, request));
    }

    @Operation(summary = "Lister mes demandes et rendez-vous")
    @GetMapping("/mes-demandes")
    public ResponseEntity<Page<RendezVousDTO>> listerMesDemandes(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "dateMiseAJour", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(rendezVousService.listerMesDemandesClient(authentication.getName(), pageable));
    }

    @Operation(summary = "Detail d'un rendez-vous du client")
    @GetMapping("/mes-demandes/{idRendezVous}")
    public ResponseEntity<RendezVousDTO> getDetail(
            Authentication authentication,
            @PathVariable String idRendezVous
    ) {
        return ResponseEntity.ok(rendezVousService.getMonRendezVousClient(authentication.getName(), idRendezVous));
    }

    @Operation(summary = "Acces a la salle du rendez-vous en ligne (client)")
    @GetMapping("/{idRendezVous}/meeting-access")
    public ResponseEntity<RendezVousMeetingAccessDTO> getMeetingAccess(
            Authentication authentication,
            @PathVariable String idRendezVous
    ) {
        return ResponseEntity.ok(rendezVousService.getMeetingAccessClient(authentication.getName(), idRendezVous));
    }

    @Operation(summary = "Accepter la proposition de creneau")
    @PatchMapping("/{idRendezVous}/accepter-proposition")
    public ResponseEntity<RendezVousDTO> accepterProposition(
            Authentication authentication,
            @PathVariable String idRendezVous
    ) {
        return ResponseEntity.ok(rendezVousService.accepterPropositionClient(authentication.getName(), idRendezVous));
    }

    @Operation(summary = "Refuser la proposition de creneau")
    @PatchMapping("/{idRendezVous}/refuser-proposition")
    public ResponseEntity<RendezVousDTO> refuserProposition(
            Authentication authentication,
            @PathVariable String idRendezVous,
            @RequestBody RefuserPropositionRequest request
    ) {
        return ResponseEntity.ok(rendezVousService.refuserPropositionClient(authentication.getName(), idRendezVous, request));
    }

    @Operation(summary = "Annuler un rendez-vous (client)")
    @PatchMapping("/{idRendezVous}/annuler")
    public ResponseEntity<RendezVousDTO> annuler(
            Authentication authentication,
            @PathVariable String idRendezVous,
            @RequestBody AnnulerRendezVousRequest request
    ) {
        return ResponseEntity.ok(rendezVousService.annulerParClient(authentication.getName(), idRendezVous, request));
    }
}