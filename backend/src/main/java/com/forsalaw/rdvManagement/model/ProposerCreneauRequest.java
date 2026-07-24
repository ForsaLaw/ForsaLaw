package com.forsalaw.rdvManagement.model;

import com.forsalaw.rdvManagement.entity.TypeRendezVous;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ProposerCreneauRequest {
    private OffsetDateTime dateHeureDebut;
    private OffsetDateTime dateHeureFin;
    private TypeRendezVous typeRendezVous;
    private String commentaireAvocat;
}