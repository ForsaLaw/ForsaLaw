package com.forsalaw.rdvManagement.entity;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.userManagement.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rendez_vous")
@Getter
@Setter
public class RendezVous {

    @Id
    @Column(length = 20)
    private String idRendezVous;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_user_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avocat_id", nullable = false)
    private Avocat avocat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutRendezVous statutRendezVous = StatutRendezVous.EN_ATTENTE;

    @Column(length = 1000)
    private String motifConsultation;

    @Column(name = "date_heure_debut")
    private OffsetDateTime dateHeureDebut;

    @Column(name = "date_heure_fin")
    private OffsetDateTime dateHeureFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeRendezVous typeRendezVous = TypeRendezVous.EN_LIGNE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreePar creePar = CreePar.CLIENT;

    @Column(length = 1000)
    private String raisonAnnulation;

    @Column(length = 1000)
    private String commentaireAvocat;

    @Column(length = 1000)
    private String meetingUrl;

    // Noms de colonnes explicites : la strategie CamelCaseToUnderscores mappe "dateMiseAJour"
    // vers "date_mise_ajour" (pas de _ entre majuscules consecutives AJ), ce qui ne correspond
    // pas au baseline V0 (date_mise_a_jour). On fige donc les noms ici.
    @Column(name = "date_creation", nullable = false, updatable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_mise_a_jour", nullable = false)
    private OffsetDateTime dateMiseAJour;

    @Column(name = "rappel_j1_envoye", nullable = false)
    private boolean rappelJ1Envoye = false;

    @Column(name = "rappel_h1_envoye", nullable = false)
    private boolean rappelH1Envoye = false;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        dateCreation = now;
        dateMiseAJour = now;
    }

    @PreUpdate
    protected void onUpdate() {
        dateMiseAJour = OffsetDateTime.now();
    }
}