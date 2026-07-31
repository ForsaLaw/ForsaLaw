package com.forsalaw.userManagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entité User (schéma ForsaLaw).
 */
@Entity
@Table(name = "users", indexes = @Index(unique = true, columnList = "email"))
@Getter
@Setter
public class User {

    @Id
    @Column(length = 20)
    private String id; // Format: AAAA-USR-NNNNN (année-type-numéro)

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "telephone", length = 20)
    private String telephone; // Format international : +21612345678

    /** Id document coffre-fort (DOC…) pour la photo de profil ; null si aucune. */
    @Column(name = "profile_photo_document_id", length = 20)
    private String profilePhotoDocumentId;

    @Column(name = "motdepasse", nullable = false)
    private String motDePasse; // stocké hashé (BCrypt)

    @Column(name = "password_reset_token", length = 120)
    private String passwordResetToken;

    @Column(name = "password_reset_expires_at")
    private LocalDateTime passwordResetExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleUser roleUser = RoleUser.client;

    @Column(nullable = false)
    private boolean actif = true; // true = compte actif, false = bloqué

    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "integer default 0")
    private int failedLoginAttempts = 0;

    @Column(name = "blocked_by_failed_attempts", nullable = false, columnDefinition = "boolean default false")
    private boolean blockedByFailedAttempts = false;

    /** Plafond quotidien de jetons IA, surchargeable par compte (voir AiTokenBudgetService). */
    @Column(name = "daily_token_budget", nullable = false, columnDefinition = "integer default 50000")
    private int dailyTokenBudget = 50_000;

    /**
     * Version du texte de consentement IA acceptee, {@code null} si jamais accepte.
     *
     * <p>Non retro-remplissable : le consentement anterieur n'existe que dans le localStorage
     * du navigateur. Voir AiConsentController pour la consequence sur l'application du controle.</p>
     */
    @Column(name = "ai_consent_version")
    private Integer aiConsentVersion;

    @Column(name = "ai_consented_at")
    private LocalDateTime aiConsentedAt;

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
