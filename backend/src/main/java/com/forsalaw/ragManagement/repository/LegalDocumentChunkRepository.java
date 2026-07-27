package com.forsalaw.ragManagement.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
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
     * Passe un article a SUPERSEDED a la date donnee.
     *
     * <p>Volontairement NON appele par la detection automatique : voir
     * {@code SupersedeProposalService}. Reserve a une confirmation humaine.</p>
     *
     * <p>{@code supersededAt} (V12) est ce qui permet a la recherche de re-exhumer ce chunk
     * pour une question posee sur une date anterieure : sans elle, un article abroge
     * deviendrait invisible pour TOUTE date, y compris celles ou il etait en vigueur.</p>
     */
    @Transactional
    public int marquerSuperseded(String codeName, String articleReference, LocalDate supersededAt) {
        return jdbcTemplate.update("""
                UPDATE legal_document_chunk
                   SET status = 'SUPERSEDED', superseded_at = ?
                 WHERE code_name = ? AND article_reference = ? AND status = 'ACTIVE'
                """, Date.valueOf(supersededAt), codeName, articleReference);
    }

    /** Une ligne de resultat, fenetre de validite de l'instrument comprise (V12). */
    public record ResultatRecherche(
            String codeName,
            String articleReference,
            String articleTitle,
            String content,
            double distance,
            int tier,
            String sourceReference,
            LocalDate effectiveDate,
            String chunkStatus,
            String instrumentStatus,
            LocalDate inForceFrom,
            LocalDate inForceUntil,
            String supersededByCodeName
    ) {}

    private static final RowMapper<ResultatRecherche> MAPPEUR_RECHERCHE = (rs, i) -> new ResultatRecherche(
            rs.getString("code_name"),
            rs.getString("article_reference"),
            rs.getString("article_title"),
            rs.getString("content"),
            rs.getDouble("distance"),
            rs.getInt("tier"),
            rs.getString("source_reference"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getString("status"),
            rs.getString("instrument_status"),
            rs.getObject("in_force_from", LocalDate.class),
            rs.getObject("in_force_until", LocalDate.class),
            rs.getString("superseded_by_code_name")
    );

    /**
     * Recherche par similarite cosinus, filtree par date de reference plutot que par statut.
     *
     * <p><b>Ce n'est PAS un filtre "droit en vigueur uniquement".</b> Un tel filtre casserait
     * la question qu'un avocat pose le plus souvent : « ce contrat etait-il licite en 2018 ? »
     * exige de pouvoir retrouver un texte abroge AUJOURD'HUI s'il etait en vigueur a la date
     * visee. {@code asOf} selectionne donc la tranche de droit applicable A CETTE DATE — passe
     * ou present — et n'exclut que ce qui est positivement CONNU comme inapplicable a cette
     * date (fenetre de {@code legal_instrument}). L'absence de ligne dans {@code legal_instrument}
     * ne bloque jamais un resultat : une information manquante ne doit pas se traduire par un
     * resultat cache.</p>
     */
    public List<ResultatRecherche> rechercherParSimilarite(float[] vecteur, int tier, String tenantId,
                                                            LocalDate asOf, int limite) {
        String vecteurLitteral = versLitteralVecteur(vecteur);
        LocalDate reference = asOf != null ? asOf : LocalDate.now();
        Date referenceSql = Date.valueOf(reference);

        // Clause statique (deux valeurs possibles, jamais construite a partir d'une entree
        // utilisateur non liee) : le cabinet est toujours passe par parametre lie ci-dessous.
        String clauseTenant = tenantId == null ? "c.tenant_id IS NULL" : "c.tenant_id = ?";

        String sql = """
                SELECT c.code_name, c.article_reference, c.article_title, c.content,
                       (c.embedding <=> CAST(? AS vector)) AS distance,
                       c.tier, c.source_reference, c.effective_date, c.status,
                       i.status AS instrument_status, i.in_force_from, i.in_force_until,
                       i.superseded_by_code_name
                  FROM legal_document_chunk c
                  LEFT JOIN legal_instrument i ON i.code_name = c.code_name
                 WHERE c.tier = ?
                   AND %s
                   AND (c.effective_date IS NULL OR c.effective_date <= ?)
                   AND (c.status = 'ACTIVE' OR c.superseded_at IS NULL OR ? < c.superseded_at)
                   AND (i.code_name IS NULL OR (
                         (i.in_force_from IS NULL OR i.in_force_from <= ?)
                         AND (i.in_force_until IS NULL OR ? < i.in_force_until)
                       ))
                 ORDER BY c.embedding <=> CAST(? AS vector)
                 LIMIT ?
                """.formatted(clauseTenant);

        List<Object> params = new ArrayList<>();
        params.add(vecteurLitteral);
        params.add(tier);
        if (tenantId != null) {
            params.add(tenantId);
        }
        params.add(referenceSql);
        params.add(referenceSql);
        params.add(referenceSql);
        params.add(referenceSql);
        params.add(vecteurLitteral);
        params.add(limite);

        return jdbcTemplate.query(sql, MAPPEUR_RECHERCHE, params.toArray());
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
