package com.forsalaw.avocatManagement.entity;

import com.forsalaw.userManagement.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entité Avocat (profil métier lié à un User avec rôle avocat).
 */
@Entity
@Table(name = "avocats", indexes = {
        @Index(unique = true, columnList = "user_id"),
        @Index(unique = true, columnList = "numero_carte_professionnelle"),
        @Index(unique = true, columnList = "cin")
})
@Getter
@Setter
public class Avocat {

    @Id
    @Column(length = 20)
    private String id; // Format: AAAA-AVC-NNNNN (année-type-numéro)

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecialiteJuridique specialite;

    @Column(name = "anneesexperience", nullable = false)
    private int anneesExperience = 0;

    @Column(nullable = false, length = 100)
    private String ville = "";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "numero_carte_professionnelle", length = 100)
    private String numeroCarteProfessionnelle;

    /**
     * Numero d'inscription a l'ONAT (Ordre National des Avocats de Tunisie).
     *
     * <p>Nullable en base : les profils anterieurs a V16 n'ont jamais eu a le fournir. Le
     * caractere obligatoire ne vaut que pour les NOUVELLES demandes, ou il est impose par
     * {@code CreateAvocatRequest} — une contrainte NOT NULL invaliderait retroactivement le
     * parc existant.</p>
     */
    @Column(name = "numero_onat", length = 100)
    private String numeroOnat;

    @Column(name = "cin", length = 50)
    private String cin;

    @Column(name = "barreau", length = 100)
    private String barreau;

    @Column(name = "notemoyenne", nullable = false)
    private double noteMoyenne = 0.0;

    @Column(name = "totaldossiers", nullable = false)
    private int totalDossiers = 0;

    @Column(nullable = false)
    private boolean verifie = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private AvocatVerificationStatus verificationStatus = AvocatVerificationStatus.PENDING;

    @Column(name = "verification_comment", length = 1000)
    private String verificationComment;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "datecreation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "datemiseajour", nullable = false)
    private LocalDateTime dateMiseAJour;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateMiseAJour = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateMiseAJour = LocalDateTime.now();
    }
}
