package com.forsalaw.ragManagement.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

/**
 * Acces aux chunks vectorises.
 *
 * <p>JdbcTemplate et non JPA : Hibernate ne sait pas mapper le type {@code vector} de
 * pgvector, et l'ecrire en JPA imposerait un type utilisateur pour un gain nul — cette table
 * n'a pas de graphe d'objets, seulement des insertions en masse et des recherches par
 * similarite.</p>
 */
@Repository
@RequiredArgsConstructor
public class LegalDocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Une ligne prete a etre inseree. */
    public record NouveauChunk(
            String codeName,
            String articleReference,
            String articleTitle,
            String contenu,
            float[] embedding,
            int tier,
            String tenantId,
            LocalDate effectiveDate,
            String sourceReference
    ) {}

    @Transactional
    public int insererLot(List<NouveauChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO legal_document_chunk
                    (code_name, article_reference, article_title, content, embedding,
                     tier, tenant_id, effective_date, source_reference, status)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, 'ACTIVE')
                """;

        int[][] resultats = jdbcTemplate.batchUpdate(sql, chunks, chunks.size(), (ps, chunk) -> {
            ps.setString(1, chunk.codeName());
            ps.setString(2, chunk.articleReference());
            ps.setString(3, chunk.articleTitle());
            ps.setString(4, chunk.contenu());
            // pgvector accepte la forme textuelle '[0.1,0.2,...]' ; le CAST explicite evite
            // d'avoir a enregistrer un type JDBC personnalise.
            ps.setString(5, versLitteralVecteur(chunk.embedding()));
            ps.setInt(6, chunk.tier());
            if (chunk.tenantId() == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, chunk.tenantId());
            }
            if (chunk.effectiveDate() == null) {
                ps.setNull(8, Types.DATE);
            } else {
                ps.setDate(8, Date.valueOf(chunk.effectiveDate()));
            }
            ps.setString(9, chunk.sourceReference());
        });

        int total = 0;
        for (int[] lot : resultats) {
            total += lot.length;
        }
        return total;
    }

    /** Nombre de chunks deja presents pour une source : evite de reingerer un JORT deja traite. */
    public long compterParSource(String sourceReference) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM legal_document_chunk WHERE source_reference = ?",
                Long.class, sourceReference);
        return total == null ? 0 : total;
    }

    /**
     * Passe un article a SUPERSEDED.
     *
     * <p>Volontairement NON appele par la detection automatique : voir
     * {@code SupersedeProposalService}. Reserve a une confirmation humaine.</p>
     */
    @Transactional
    public int marquerSuperseded(String codeName, String articleReference) {
        return jdbcTemplate.update("""
                UPDATE legal_document_chunk
                   SET status = 'SUPERSEDED'
                 WHERE code_name = ? AND article_reference = ? AND status = 'ACTIVE'
                """, codeName, articleReference);
    }

    private String versLitteralVecteur(float[] vecteur) {
        StringBuilder sb = new StringBuilder(vecteur.length * 12 + 2);
        sb.append('[');
        for (int i = 0; i < vecteur.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vecteur[i]);
        }
        return sb.append(']').toString();
    }
}
