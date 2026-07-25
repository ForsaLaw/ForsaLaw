package com.forsalaw.auditManagement;

import com.forsalaw.AbstractIntegrationTest;
import com.forsalaw.auditManagement.model.AuditIntegrityReport;
import com.forsalaw.auditManagement.service.AuditIntegrityService;
import com.forsalaw.auditManagement.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie le chainage cryptographique du journal d'audit contre une vraie base PostgreSQL.
 *
 * <p>Le test central est {@link #empreintesSql_etJava_sontIdentiques()} : le remplissage
 * initial de V10 calcule les empreintes en SQL, l'application les calcule en Java. Si les deux
 * representations canoniques divergent d'un seul caractere, la verification signalerait une
 * alteration sur toutes les lignes anterieures a la migration — une fausse alerte permanente,
 * pire qu'absente sur une plateforme de preuve juridique. Rien d'autre ne couvre cette parite.</p>
 */
class AuditChainIntegrityIntegrationTest extends AbstractIntegrationTest {

    /** Expression canonique de V10__audit_log_hash_chain.sql, a l'identique. */
    private static final String CANONIQUE_SQL = """
            a.id                                                || '|' ||
            coalesce(a.module_name, '')                         || '|' ||
            coalesce(a.action, '')                              || '|' ||
            coalesce(a.method, '')                              || '|' ||
            coalesce(a.endpoint, '')                            || '|' ||
            coalesce(a.resource_id, '')                         || '|' ||
            coalesce(a.http_status::text, '')                   || '|' ||
            coalesce(a.ip_address, '')                          || '|' ||
            coalesce(a.user_agent, '')                          || '|' ||
            coalesce(a.details, '')                             || '|' ||
            coalesce(a.actor_user_id, '')                       || '|' ||
            to_char(a.created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US')
            """;

    @Autowired
    AuditLogService auditLogService;

    @Autowired
    AuditIntegrityService auditIntegrityService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void journalVierge() {
        // Le journal est append-only : le trigger interdit DELETE. On le neutralise le temps
        // du nettoyage pour que chaque test parte d'une chaine connue.
        jdbcTemplate.execute("ALTER TABLE audit_log DISABLE TRIGGER trg_audit_log_no_mutation");
        jdbcTemplate.execute("DELETE FROM audit_log");
        jdbcTemplate.execute("ALTER TABLE audit_log ENABLE TRIGGER trg_audit_log_no_mutation");
    }

    private void ecrireTroisEntrees() {
        // Accents et caracteres non latins : la representation canonique est encodee en UTF-8
        // des deux cotes (convert_to(..., 'UTF8') en SQL, getBytes(UTF_8) en Java).
        auditLogService.logAction(null, "documents", "UPLOAD", "POST", "/api/documents",
                "DOC-1", 201, "10.0.0.1", "JUnit", "Dépôt d'une pièce — عربية");
        // Champs optionnels a null : coalesce(...,'') cote SQL doit correspondre a "" cote Java.
        auditLogService.logAction(null, "auth", "LOGIN", null, null, null, null, null, null, null);
        auditLogService.logAction(null, "rdv", "CANCEL", "DELETE", "/api/rendezvous/9",
                "RDV-9", 204, "10.0.0.2", "JUnit", "Annulation");
    }

    @Test
    void journalIntact_lesTroisControlesPassent() {
        ecrireTroisEntrees();

        AuditIntegrityReport rapport = auditIntegrityService.verifier();

        assertThat(rapport.integre()).isTrue();
        assertThat(rapport.trigger().valide()).isTrue();
        assertThat(rapport.trigger().actif()).isTrue();
        assertThat(rapport.sequence().valide()).isTrue();
        assertThat(rapport.chaine().valide()).isTrue();
        assertThat(rapport.chaine().lignesControlees()).isEqualTo(3);
    }

    @Test
    void empreintesSql_etJava_sontIdentiques() {
        ecrireTroisEntrees();

        // Recalcul par l'expression SQL de la migration, en chainant sur le prev_hash stocke.
        List<Boolean> concordances = jdbcTemplate.queryForList(
                "SELECT encode(sha256(convert_to((" + CANONIQUE_SQL
                        + ") || '|' || a.prev_hash, 'UTF8')), 'hex') = a.row_hash "
                        + "FROM audit_log a ORDER BY a.id",
                Boolean.class);

        assertThat(concordances)
                .as("le calcul SQL de V10 doit reproduire exactement l'empreinte calculee en Java")
                .isNotEmpty()
                .containsOnly(true);
    }

    @Test
    void contenuModifieEnBase_estDetecte() {
        ecrireTroisEntrees();
        String cible = jdbcTemplate.queryForObject(
                "SELECT id FROM audit_log ORDER BY id OFFSET 1 LIMIT 1", String.class);

        // Falsification realiste : contourner la protection, modifier une ligne, la remettre.
        jdbcTemplate.execute("ALTER TABLE audit_log DISABLE TRIGGER trg_audit_log_no_mutation");
        jdbcTemplate.update("UPDATE audit_log SET details = 'contenu falsifie' WHERE id = ?", cible);
        jdbcTemplate.execute("ALTER TABLE audit_log ENABLE TRIGGER trg_audit_log_no_mutation");

        AuditIntegrityReport rapport = auditIntegrityService.verifier();

        assertThat(rapport.integre()).isFalse();
        assertThat(rapport.chaine().valide()).isFalse();
        assertThat(rapport.chaine().ruptures()).isNotEmpty();
        assertThat(rapport.chaine().ruptures().get(0).auditLogId()).isEqualTo(cible);
        assertThat(rapport.chaine().ruptures().get(0).type()).isEqualTo("CONTENU_MODIFIE");
        // La ligne reste numerotee : seul le chainage revele la modification.
        assertThat(rapport.sequence().valide()).isTrue();
    }

    @Test
    void entreeSupprimee_estDetectee() {
        ecrireTroisEntrees();
        String cible = jdbcTemplate.queryForObject(
                "SELECT id FROM audit_log ORDER BY id OFFSET 1 LIMIT 1", String.class);

        jdbcTemplate.execute("ALTER TABLE audit_log DISABLE TRIGGER trg_audit_log_no_mutation");
        jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", cible);
        jdbcTemplate.execute("ALTER TABLE audit_log ENABLE TRIGGER trg_audit_log_no_mutation");

        AuditIntegrityReport rapport = auditIntegrityService.verifier();

        assertThat(rapport.integre()).isFalse();
        // Deux controles independants attrapent la suppression : le trou de numerotation
        // et le maillon rompu de la ligne suivante.
        assertThat(rapport.sequence().valide()).isFalse();
        assertThat(rapport.sequence().anomalies().get(0).manquants()).isEqualTo(1);
        assertThat(rapport.chaine().valide()).isFalse();
        assertThat(rapport.chaine().ruptures().get(0).type()).isEqualTo("MAILLON_ROMPU");
    }

    @Test
    void protectionDesactivee_estDetectee() {
        ecrireTroisEntrees();
        jdbcTemplate.execute("ALTER TABLE audit_log DISABLE TRIGGER trg_audit_log_no_mutation");
        try {
            AuditIntegrityReport rapport = auditIntegrityService.verifier();

            assertThat(rapport.integre()).isFalse();
            assertThat(rapport.trigger().valide()).isFalse();
            assertThat(rapport.trigger().present()).isTrue();
            assertThat(rapport.trigger().actif()).isFalse();
            // Le contenu, lui, n'a pas bouge : la chaine reste valide.
            assertThat(rapport.chaine().valide()).isTrue();
        } finally {
            jdbcTemplate.execute("ALTER TABLE audit_log ENABLE TRIGGER trg_audit_log_no_mutation");
        }
    }
}
