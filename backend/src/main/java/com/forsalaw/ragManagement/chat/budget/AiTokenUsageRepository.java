package com.forsalaw.ragManagement.chat.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AiTokenUsageRepository extends JpaRepository<AiTokenUsage, Long> {

    /**
     * Jetons decomptes a un utilisateur depuis {@code depuis}.
     *
     * <p>{@code COALESCE} car une somme sur un ensemble vide renvoie {@code null} en SQL : sans
     * lui, le premier appel d'un nouvel utilisateur leverait un NullPointerException au
     * deballage vers {@code long}.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(u.tokensTotal), 0)
            FROM AiTokenUsage u
            WHERE u.userId = :userId AND u.createdAt >= :depuis
            """)
    long totalUtilisateurDepuis(@Param("userId") String userId, @Param("depuis") LocalDateTime depuis);

    /** Jetons decomptes tous comptes confondus depuis {@code depuis} (plafond global). */
    @Query("""
            SELECT COALESCE(SUM(u.tokensTotal), 0)
            FROM AiTokenUsage u
            WHERE u.createdAt >= :depuis
            """)
    long totalGlobalDepuis(@Param("depuis") LocalDateTime depuis);
}
