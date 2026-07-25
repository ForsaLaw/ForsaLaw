package com.forsalaw.auditManagement.repository;

import com.forsalaw.auditManagement.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /** Empreinte de la derniere ligne du journal (tete de chaine), vide si le journal est neuf. */
    @Query(value = "SELECT row_hash FROM audit_log ORDER BY id DESC LIMIT 1", nativeQuery = true)
    java.util.Optional<String> findChainHead();

    /**
     * Tranche du journal pour la verification d'integrite, en pagination par cle (keyset) :
     * plus stable et plus efficace qu'un OFFSET sur une table qui ne fait que grandir.
     * Projection native volontaire — elle expose actor_user_id directement et evite de
     * declencher le chargement paresseux de l'utilisateur sur chaque ligne.
     */
    @Query(value = """
            SELECT id                AS id,
                   module_name       AS moduleName,
                   action            AS action,
                   method            AS method,
                   endpoint          AS endpoint,
                   resource_id       AS resourceId,
                   http_status       AS httpStatus,
                   ip_address        AS ipAddress,
                   user_agent        AS userAgent,
                   details           AS details,
                   actor_user_id     AS actorUserId,
                   created_at        AS createdAt,
                   row_hash          AS rowHash,
                   prev_hash         AS prevHash
              FROM audit_log
             WHERE (:afterId IS NULL OR id > :afterId)
             ORDER BY id
             LIMIT :taille
            """, nativeQuery = true)
    java.util.List<AuditChainRow> findChainSlice(@Param("afterId") String afterId, @Param("taille") int taille);

    /** Etat du trigger d'immuabilite pose en V8 ('O' = actif). */
    @Query(value = """
            SELECT t.tgenabled
              FROM pg_trigger t
              JOIN pg_class c ON c.oid = t.tgrelid
             WHERE c.relname = 'audit_log'
               AND t.tgname  = 'trg_audit_log_no_mutation'
               AND NOT t.tgisinternal
            """, nativeQuery = true)
    java.util.Optional<String> findImmutabilityTriggerState();

    /**
     * Continuite des identifiants, par annee : 'AAAA-ADT-NNNNN'. Une ligne supprimee laisse
     * un trou entre le minimum et le maximum.
     */
    @Query(value = """
            SELECT substring(id from 1 for 4)                  AS annee,
                   count(*)                                    AS nombre,
                   min(CAST(split_part(id, '-', 3) AS integer)) AS premier,
                   max(CAST(split_part(id, '-', 3) AS integer)) AS dernier
              FROM audit_log
             GROUP BY substring(id from 1 for 4)
             ORDER BY 1
            """, nativeQuery = true)
    java.util.List<AuditSequenceRow> findSequenceStatsByYear();

    /** Projection d'une ligne pour le recalcul de la chaine. */
    interface AuditChainRow {
        String getId();
        String getModuleName();
        String getAction();
        String getMethod();
        String getEndpoint();
        String getResourceId();
        Integer getHttpStatus();
        String getIpAddress();
        String getUserAgent();
        String getDetails();
        String getActorUserId();
        java.sql.Timestamp getCreatedAt();
        String getRowHash();
        String getPrevHash();
    }

    /** Statistiques de continuite des identifiants pour une annee. */
    interface AuditSequenceRow {
        String getAnnee();
        Long getNombre();
        Integer getPremier();
        Integer getDernier();
    }

    Page<AuditLog> findByActor_Id(String actorUserId, Pageable pageable);

    /** Used by the affaire timeline: fetches all audit events for a given resource, ordered chronologically. */
    java.util.List<AuditLog> findByModuleNameAndResourceIdOrderByCreatedAtAsc(String moduleName, String resourceId);

    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:actorUserId IS NULL OR a.actor.id = :actorUserId) AND " +
            "(:moduleNamePattern IS NULL OR LOWER(a.moduleName) LIKE :moduleNamePattern) AND " +
            "(:actionPattern IS NULL OR LOWER(a.action) LIKE :actionPattern) AND " +
            "(:method IS NULL OR UPPER(a.method) = :method) AND " +
            "(:httpStatus IS NULL OR a.httpStatus = :httpStatus)")
    Page<AuditLog> findForAdmin(
            @Param("actorUserId") String actorUserId,
            @Param("moduleNamePattern") String moduleNamePattern,
            @Param("actionPattern") String actionPattern,
            @Param("method") String method,
            @Param("httpStatus") Integer httpStatus,
            Pageable pageable
    );
}
