package com.forsalaw.avocatManagement.repository;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvocatRepository extends JpaRepository<Avocat, String> {

    Optional<Avocat> findByUserId(String userId);

    boolean existsByUserId(String userId);

    /**
     * Avocats actifs dont la specialite releve d'un domaine, les mieux notes d'abord.
     *
     * <p>Le filtre porte sur une LISTE de specialites, et non sur un domaine : le domaine
     * n'existe pas en colonne, il est porte par l'enum {@code SpecialiteJuridique}. L'appelant
     * developpe donc le domaine en ses specialites (voir
     * {@code AvocatService.trouverParDomaine}), ce qui laisse la requete sur un simple IN
     * indexable plutot qu'un calcul par ligne.</p>
     *
     * <p>Tri par note puis par nombre de dossiers : a note egale (fréquent quand peu d'avis ont
     * ete deposes), l'experience reelle departage plutot qu'un ordre arbitraire.</p>
     */
    @Query("SELECT a FROM Avocat a JOIN FETCH a.user "
            + "WHERE a.actif = true AND a.specialite IN :specialites "
            + "ORDER BY a.verifie DESC, a.noteMoyenne DESC, a.totalDossiers DESC")
    List<Avocat> findActifsParSpecialites(
            @Param("specialites") List<SpecialiteJuridique> specialites,
            Pageable pageable
    );

    boolean existsByNumeroCarteProfessionnelleIgnoreCase(String numeroCarteProfessionnelle);

    boolean existsByCinIgnoreCase(String cin);

    /** Double emploi d'un numero ONAT : deux avocats ne peuvent pas partager la meme inscription. */
    boolean existsByNumeroOnatIgnoreCase(String numeroOnat);

    @Query(value = "SELECT a FROM Avocat a JOIN FETCH a.user WHERE a.actif = true " +
           "AND (:specialite IS NULL OR a.specialite = :specialite) " +
           "AND (:ville IS NULL OR :ville = '' OR LOWER(a.ville) LIKE LOWER(CONCAT('%', :ville, '%'))) " +
           "AND (:verifie IS NULL OR a.verifie = :verifie)",
           countQuery = "SELECT COUNT(a) FROM Avocat a WHERE a.actif = true " +
           "AND (:specialite IS NULL OR a.specialite = :specialite) " +
           "AND (:ville IS NULL OR :ville = '' OR LOWER(a.ville) LIKE LOWER(CONCAT('%', :ville, '%'))) " +
           "AND (:verifie IS NULL OR a.verifie = :verifie)")
    Page<Avocat> findAllActifsFiltered(
            @Param("specialite") SpecialiteJuridique specialite,
            @Param("ville") String ville,
            @Param("verifie") Boolean verifie,
            Pageable pageable
    );

    @Query(value = "SELECT a FROM Avocat a JOIN FETCH a.user WHERE " +
           "(:specialite IS NULL OR a.specialite = :specialite) " +
           "AND (:ville IS NULL OR :ville = '' OR LOWER(a.ville) LIKE LOWER(CONCAT('%', :ville, '%'))) " +
           "AND (:verifie IS NULL OR a.verifie = :verifie) " +
           "AND (:actif IS NULL OR a.actif = :actif)",
           countQuery = "SELECT COUNT(a) FROM Avocat a WHERE " +
           "(:specialite IS NULL OR a.specialite = :specialite) " +
           "AND (:ville IS NULL OR :ville = '' OR LOWER(a.ville) LIKE LOWER(CONCAT('%', :ville, '%'))) " +
           "AND (:verifie IS NULL OR a.verifie = :verifie) " +
           "AND (:actif IS NULL OR a.actif = :actif)")
    Page<Avocat> findAllFiltered(
            @Param("specialite") SpecialiteJuridique specialite,
            @Param("ville") String ville,
            @Param("verifie") Boolean verifie,
            @Param("actif") Boolean actif,
            Pageable pageable
    );
}
