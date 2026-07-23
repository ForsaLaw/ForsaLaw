package com.forsalaw.rdvManagement.controller;

import com.forsalaw.rdvManagement.model.CreneauxDisponiblesResponse;
import com.forsalaw.rdvManagement.model.AgendaCompletDTO;
import com.forsalaw.rdvManagement.service.AvocatAgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequestMapping("/api/rendezvous/public")
@RequiredArgsConstructor
public class PublicRendezVousController {

    private final AvocatAgendaService avocatAgendaService;

    @GetMapping("/avocats/{idAvocat}/creneaux-disponibles")
    public ResponseEntity<CreneauxDisponiblesResponse> listerCreneauxDisponiblesPublic(
            @PathVariable String idAvocat,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime debut,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime fin
    ) {
        return ResponseEntity.ok(avocatAgendaService.listerCreneauxDisponiblesPourAvocat(idAvocat, debut, fin));
    }

    @GetMapping("/avocats/{idAvocat}/agenda")
    public ResponseEntity<AgendaCompletDTO> getAgendaPublic(@PathVariable String idAvocat) {
        return ResponseEntity.ok(avocatAgendaService.getAgendaCompletPublic(idAvocat));
    }
}
