package com.forsalaw.rdvManagement.repository;

import com.forsalaw.rdvManagement.entity.RendezVous;
import com.forsalaw.rdvManagement.entity.StatutRendezVous;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RendezVousRepository extends JpaRepository<RendezVous, String> {

    @Query("""
        SELECT r FROM RendezVous r
        JOIN FETCH r.client
        JOIN FETCH r.avocat a
        JOIN FETCH a.user
        WHERE r.idRendezVous = :id
        """)
    Optional<RendezVous> findByIdWithClientAndAvocat(@Param("id") String id);

    Page<RendezVous> findByClient_IdOrderByDateMiseAJourDesc(String clientId, Pageable pageable);

    Page<RendezVous> findByAvocat_IdOrderByDateMiseAJourDesc(String avocatId, Pageable pageable);

    boolean existsByClient_IdAndAvocat_IdAndStatutRendezVous(String clientId, String avocatId, StatutRendezVous statutRendezVous);

    @Query("""
        SELECT r FROM RendezVous r
        WHERE (:clientUserId IS NULL OR r.client.id = :clientUserId)
          AND (:avocatId IS NULL OR r.avocat.id = :avocatId)
          AND (:statut IS NULL OR r.statutRendezVous = :statut)
          AND r.dateCreation >= :du
          AND r.dateCreation <= :au
        ORDER BY r.dateMiseAJour DESC
    """)
    Page<RendezVous> findForAdmin(
            @Param("clientUserId") String clientUserId,
            @Param("avocatId") String avocatId,
            @Param("statut") StatutRendezVous statut,
            @Param("du") OffsetDateTime du,
            @Param("au") OffsetDateTime au,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(r) > 0 FROM RendezVous r
        WHERE r.avocat.id = :avocatId
          AND r.statutRendezVous IN :statutsOccupes
          AND (:excludeId IS NULL OR r.idRendezVous <> :excludeId)
          AND r.dateHeureDebut < :fin
          AND r.dateHeureFin > :debut
    """)
    boolean hasConflitCreneauAvocat(
            @Param("avocatId") String avocatId,
            @Param("statutsOccupes") Collection<StatutRendezVous> statutsOccupes,
            @Param("excludeId") String excludeId,
            @Param("debut") OffsetDateTime debut,
            @Param("fin") OffsetDateTime fin
    );

    @Query("""
        SELECT r FROM RendezVous r
        WHERE r.avocat.id = :avocatId
          AND r.statutRendezVous IN :statutsOccupes
          AND r.dateHeureDebut IS NOT NULL
          AND r.dateHeureFin IS NOT NULL
          AND r.dateHeureDebut < :fin
          AND r.dateHeureFin > :debut
        ORDER BY r.dateHeureDebut
    """)
    List<RendezVous> findOccupyingForAvocatInRange(
            @Param("avocatId") String avocatId,
            @Param("statutsOccupes") Collection<StatutRendezVous> statutsOccupes,
            @Param("debut") OffsetDateTime debut,
            @Param("fin") OffsetDateTime fin
    );

    @Query("""
        SELECT r FROM RendezVous r
        JOIN FETCH r.client
        JOIN FETCH r.avocat a
        JOIN FETCH a.user
        WHERE r.statutRendezVous = :confirme
          AND r.rappelJ1Envoye = false
          AND r.dateHeureDebut IS NOT NULL
          AND r.dateHeureDebut >= :debut
          AND r.dateHeureDebut < :fin
        """)
    List<RendezVous> findConfirmePourRappelJ1(
            @Param("confirme") StatutRendezVous confirme,
            @Param("debut") OffsetDateTime debut,
            @Param("fin") OffsetDateTime fin
    );

    @Query("""
        SELECT r FROM RendezVous r
        JOIN FETCH r.client
        JOIN FETCH r.avocat a
        JOIN FETCH a.user
        WHERE r.statutRendezVous = :confirme
          AND r.rappelH1Envoye = false
          AND r.dateHeureDebut IS NOT NULL
          AND r.dateHeureDebut >= :debut
          AND r.dateHeureDebut < :fin
        """)
    List<RendezVous> findConfirmePourRappelH1(
            @Param("confirme") StatutRendezVous confirme,
            @Param("debut") OffsetDateTime debut,
            @Param("fin") OffsetDateTime fin
    );
}