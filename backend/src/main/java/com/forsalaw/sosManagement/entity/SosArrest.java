package com.forsalaw.sosManagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Signalement d'une arrestation, en vue de mobiliser un avocat penaliste en urgence.
 *
 * <p><b>Le declarant n'est pas la personne arretee</b>, et n'est pas necessairement inscrit :
 * c'est le plus souvent un proche qui appelle dans les minutes qui suivent. D'ou un
 * {@code userId} facultatif mais un {@code contactUrgence} obligatoire — une demande sans
 * compte reste traitable, une demande sans numero joignable ne l'est pas.</p>
 */
@Entity
@Table(
        name = "sos_arrests",
        indexes = {
                @Index(name = "idx_sos_arrests_statut_dispatch", columnList = "statut_dispatch, created_at"),
                @Index(name = "idx_sos_arrests_user", columnList = "user_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SosArrest {

    @Id
    @Column(length = 20)
    private String id; // Format: AAAA-SOS-NNNNN

    /** Declarant connecte, {@code null} si le signalement emane d'un proche non inscrit. */
    @Column(name = "user_id", length = 20)
    private String userId;

    @Column(name = "nom_detenu", nullable = false)
    private String nomDetenu;

    @Column(name = "lieu_arrestation", nullable = false)
    private String lieuArrestation;

    @Column(name = "date_heure_arrestation", nullable = false)
    private LocalDateTime dateHeureArrestation;

    @Column(name = "contact_urgence", nullable = false, length = 30)
    private String contactUrgence;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_paiement", nullable = false, length = 16)
    private StatutPaiement statutPaiement = StatutPaiement.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_dispatch", nullable = false, length = 16)
    private StatutDispatch statutDispatch = StatutDispatch.WAITING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void marquerPaye() {
        this.statutPaiement = StatutPaiement.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void marquerDispatche() {
        this.statutDispatch = StatutDispatch.DISPATCHED;
        this.dispatchedAt = LocalDateTime.now();
    }
}
