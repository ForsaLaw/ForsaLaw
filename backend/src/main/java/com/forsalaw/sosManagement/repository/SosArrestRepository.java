package com.forsalaw.sosManagement.repository;

import com.forsalaw.sosManagement.entity.SosArrest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SosArrestRepository extends JpaRepository<SosArrest, String> {

    /** Signalements d'un declarant connecte, les plus recents d'abord. */
    List<SosArrest> findByUserIdOrderByCreatedAtDesc(String userId);
}
