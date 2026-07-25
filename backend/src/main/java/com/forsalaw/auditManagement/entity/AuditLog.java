package com.forsalaw.auditManagement.entity;

import com.forsalaw.userManagement.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_actor", columnList = "actor_user_id"),
        @Index(name = "idx_audit_log_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_log_module", columnList = "module_name")
})
@Getter
@Setter
public class AuditLog {

    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "module_name", nullable = false, length = 100)
    private String moduleName;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 20)
    private String method;

    @Column(length = 255)
    private String endpoint;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Empreinte SHA-256 de la ligne, chainee sur {@link #prevHash}.
     * Calculee par AuditChainService : ne jamais renseigner ces deux champs a la main.
     */
    @Column(name = "row_hash", nullable = false, updatable = false, length = 64)
    private String rowHash;

    /** Empreinte de la ligne precedente (chaine d'integrite). */
    @Column(name = "prev_hash", nullable = false, updatable = false, length = 64)
    private String prevHash;

    @PrePersist
    protected void onCreate() {
        // Uniquement si le service ne l'a pas deja fixe : l'horodatage entre dans le calcul
        // de l'empreinte, l'ecraser ici invaliderait la chaine des l'insertion.
        if (createdAt == null) {
            createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
