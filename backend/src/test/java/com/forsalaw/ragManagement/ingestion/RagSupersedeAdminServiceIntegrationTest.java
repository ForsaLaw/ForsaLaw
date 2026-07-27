package com.forsalaw.ragManagement.ingestion;

import com.forsalaw.AbstractIntegrationTest;
import com.forsalaw.ragManagement.embedding.EmbeddingClient;
import com.forsalaw.ragManagement.repository.LegalInstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Revue humaine des propositions (confirmation/rejet) et declaration de remplacement
 * d'instrument. Aucune des deux voies n'applique quoi que ce soit sans un appel explicite
 * de cette classe : c'est precisement ce que ces tests verifient.
 */
class RagSupersedeAdminServiceIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    EmbeddingClient embeddingClient;

    @Autowired
    LegalDocumentIngestionService ingestionService;

    @Autowired
    SupersedeProposalService supersedeProposalService;

    @Autowired
    RagSupersedeAdminService adminService;

    @Autowired
    LegalInstrumentRepository instrumentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparer() {
        jdbcTemplate.execute("DELETE FROM legal_document_chunk");
        jdbcTemplate.execute("DELETE FROM rag_supersede_proposal");
        jdbcTemplate.execute("DELETE FROM legal_instrument WHERE source = 'DCAF'");

        when(embeddingClient.dimensions()).thenReturn(1024);
        when(embeddingClient.modelName()).thenReturn("stub");
        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<String> textes = inv.getArgument(0);
            return textes.stream().map(t -> new float[1024]).toList();
        });
    }

    @Test
    void confirmer_passeLeChunkSupersededEtLaPropositionConfirmee() {
        ingestionService.ingererTexte("Article 12\nDisposition initiale.",
                new LegalDocumentIngestionService.DemandeIngestion(
                        "coc", "test-admin-confirm", LegalDocumentIngestionService.TIER_LEGISLATION,
                        null, null, null));

        long propositionId = creerPropositionPending("coc", "12");

        adminService.confirmer(propositionId, LocalDate.of(2024, 6, 1), "admin@forsalaw.tn");

        Map<String, Object> chunk = jdbcTemplate.queryForMap(
                "SELECT status, superseded_at FROM legal_document_chunk "
                        + "WHERE code_name = 'coc' AND article_reference = '12'");
        assertThat(chunk.get("status")).isEqualTo("SUPERSEDED");
        assertThat(chunk.get("superseded_at")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2024, 6, 1)));

        Map<String, Object> proposition = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by FROM rag_supersede_proposal WHERE id = ?", propositionId);
        assertThat(proposition.get("status")).isEqualTo("CONFIRMED");
        assertThat(proposition.get("reviewed_by")).isEqualTo("admin@forsalaw.tn");
    }

    @Test
    void confirmerDeuxFois_echoueEnConflit() {
        long propositionId = creerPropositionPending("cc", "5");

        adminService.confirmer(propositionId, LocalDate.now(), "admin@forsalaw.tn");

        assertThatThrownBy(() -> adminService.confirmer(propositionId, LocalDate.now(), "autre@forsalaw.tn"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void confirmerIdInexistant_echoueEnNotFound() {
        assertThatThrownBy(() -> adminService.confirmer(999_999L, LocalDate.now(), "admin@forsalaw.tn"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void rejeter_neTouchePasLeChunk() {
        ingestionService.ingererTexte("Article 8\nDisposition.",
                new LegalDocumentIngestionService.DemandeIngestion(
                        "ct", "test-admin-reject", LegalDocumentIngestionService.TIER_LEGISLATION,
                        null, null, null));
        long propositionId = creerPropositionPending("ct", "8");

        adminService.rejeter(propositionId, "admin@forsalaw.tn");

        Map<String, Object> chunk = jdbcTemplate.queryForMap(
                "SELECT status FROM legal_document_chunk WHERE code_name = 'ct' AND article_reference = '8'");
        assertThat(chunk.get("status")).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM rag_supersede_proposal WHERE id = ?", String.class, propositionId))
                .isEqualTo("REJECTED");
    }

    @Test
    void declarerInstrumentSuperseded_ecraseUneImportationDcaf() {
        instrumentRepository.importerDepuisDcaf("test-code-x", "Ancien titre",
                LocalDate.of(2020, 1, 1), "EN_VIGUEUR");

        adminService.declarerInstrumentSuperseded("test-code-x", "Nouveau titre",
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), "test-code-y",
                "admin@forsalaw.tn", "Remplace par decret ulterieur.");

        var instrument = instrumentRepository.trouverParCode("test-code-x").orElseThrow();
        assertThat(instrument.status()).isEqualTo("ABROGE");
        assertThat(instrument.source()).isEqualTo("MANUEL");
        assertThat(instrument.supersededByCodeName()).isEqualTo("test-code-y");
        assertThat(instrument.declaredBy()).isEqualTo("admin@forsalaw.tn");
    }

    private long creerPropositionPending(String codeName, String articleReference) {
        supersedeProposalService.proposeSupersedeAction(
                codeName, articleReference, null, "test-source", "extrait de test");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM rag_supersede_proposal WHERE code_name = ? AND article_reference = ? "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class, codeName, articleReference);
    }
}
