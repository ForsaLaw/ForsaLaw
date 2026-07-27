package com.forsalaw.ragManagement.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Fenetre de validite d'un instrument juridique entier (V12).
 *
 * <p>JdbcTemplate, memes raisons que {@code LegalDocumentChunkRepository} : ce sont des lignes
 * de reference sans graphe d'objets, ecrites soit en masse (import DCAF), soit une par une
 * (declaration administrateur).</p>
 */
@Repository
public class LegalInstrumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public LegalInstrumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Instrument tel que lu en base, avec sa fenetre de validite. */
    public record Instrument(
            String codeName,
            String title,
            LocalDate inForceFrom,
            LocalDate inForceUntil,
            String supersededByCodeName,
            String status,
            String source,
            String declaredBy,
            String sourceNote
    ) {}

    private static final RowMapper<Instrument> MAPPEUR = (rs, i) -> new Instrument(
            rs.getString("code_name"),
            rs.getString("title"),
            rs.getObject("in_force_from", LocalDate.class),
            rs.getObject("in_force_until", LocalDate.class),
            rs.getString("superseded_by_code_name"),
            rs.getString("status"),
            rs.getString("source"),
            rs.getString("declared_by"),
            rs.getString("source_note")
    );

    public Optional<Instrument> trouverParCode(String codeName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM legal_instrument WHERE code_name = ?", MAPPEUR, codeName));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Import DCAF en masse : cree ou met a jour la ligne a partir du champ {@code statut} du
     * front-matter. N'ecrase JAMAIS une declaration humaine (source = MANUEL) : une source
     * publisher ne doit pas silencieusement remplacer un jugement deja saisi par un
     * administrateur.
     */
    @Transactional
    public void importerDepuisDcaf(String codeName, String title, LocalDate posted, String status) {
        Long dejaManuel = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM legal_instrument WHERE code_name = ? AND source = 'MANUEL'",
                Long.class, codeName);
        if (dejaManuel != null && dejaManuel > 0) {
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO legal_instrument (code_name, title, in_force_from, status, source)
                VALUES (?, ?, ?, ?, 'DCAF')
                ON CONFLICT (code_name) DO UPDATE SET
                    title = EXCLUDED.title,
                    in_force_from = COALESCE(legal_instrument.in_force_from, EXCLUDED.in_force_from),
                    status = EXCLUDED.status
                WHERE legal_instrument.source = 'DCAF'
                """,
                codeName, title, posted == null ? null : Date.valueOf(posted), status);
    }

    /**
     * Declaration administrateur : un instrument entier remplace un autre. Ecrase toute ligne
     * existante, y compris une precedente importation DCAF — une decision humaine explicite
     * prime sur un champ publisher.
     */
    @Transactional
    public void declarerSuperseded(String codeName, String title, LocalDate inForceFrom,
                                   LocalDate inForceUntil, String supersededByCodeName,
                                   String status, String declaredBy, String sourceNote) {
        jdbcTemplate.update("""
                INSERT INTO legal_instrument
                    (code_name, title, in_force_from, in_force_until, superseded_by_code_name,
                     status, source, declared_by, source_note)
                VALUES (?, ?, ?, ?, ?, ?, 'MANUEL', ?, ?)
                ON CONFLICT (code_name) DO UPDATE SET
                    title = COALESCE(EXCLUDED.title, legal_instrument.title),
                    in_force_from = COALESCE(EXCLUDED.in_force_from, legal_instrument.in_force_from),
                    in_force_until = EXCLUDED.in_force_until,
                    superseded_by_code_name = EXCLUDED.superseded_by_code_name,
                    status = EXCLUDED.status,
                    source = 'MANUEL',
                    declared_by = EXCLUDED.declared_by,
                    declared_at = now(),
                    source_note = EXCLUDED.source_note
                """,
                codeName, title,
                inForceFrom == null ? null : Date.valueOf(inForceFrom),
                inForceUntil == null ? null : Date.valueOf(inForceUntil),
                supersededByCodeName, status, declaredBy, sourceNote);
    }
}
