package com.forsalaw.rdvManagement.model;

import com.forsalaw.rdvManagement.entity.CreePar;
import com.forsalaw.rdvManagement.entity.StatutRendezVous;
import com.forsalaw.rdvManagement.entity.TypeRendezVous;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RendezVousDTO {
    private String idRendezVous;
    private String idClient;
    private String nomClient;
    private String idAvocat;
    private String nomAvocat;
    private StatutRendezVous statutRendezVous;
    private String motifConsultation;
    private OffsetDateTime dateHeureDebut;
    private OffsetDateTime dateHeureFin;
    private TypeRendezVous typeRendezVous;
    private CreePar creePar;
    private String raisonAnnulation;
    private String commentaireAvocat;
    private String meetingUrl;
    private OffsetDateTime dateCreation;
    private OffsetDateTime dateMiseAJour;
}