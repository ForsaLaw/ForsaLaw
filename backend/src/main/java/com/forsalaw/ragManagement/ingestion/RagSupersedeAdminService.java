package com.forsalaw.ragManagement.ingestion;

import com.forsalaw.ragManagement.instrument.LegalInstrumentStatus;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import com.forsalaw.ragManagement.repository.LegalInstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Revue humaine des propositions d'abrogation (ARTICLE) et declaration de remplacement
 * d'instrument (INSTRUMENT).
 *
 * <p>Deux mecanismes distincts, volontairement separes :</p>
 * <ul>
 *   <li><b>Article</b> : {@link SupersedeProposalService} DETECTE des tournures d'abrogation
 *       dans le texte et cree une proposition PENDING. Ce service confirme ou rejette —
 *       jamais d'application automatique (voir V11).</li>
 *   <li><b>Instrument</b> : un remplacement total (ex. Constitution 2014 -> 2022) ne
 *       s'exprime JAMAIS dans le texte du remplacant. Rien a detecter : ce service enregistre
 *       directement une declaration humaine dans {@code legal_instrument} (V12).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagSupersedeAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final LegalDocumentChunkRepository chunkRepository;
    private final LegalInstrumentRepository instrumentRepository;

    /** Une proposition telle que soumise a la revue administrateur. */
    public record Proposition(
            long id, String codeName, String articleReference, LocalDate effectiveDate,
            String sourceReference, String matchedText, String status,
            java.time.LocalDateTime createdAt
    ) {}

    private static final RowMapper<Proposition> MAPPEUR = (rs, i) -> new Proposition(
            rs.getLong("id"),
            rs.getString("code_name"),
            rs.getString("article_reference"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getString("source_reference"),
            rs.getString("matched_text"),
            rs.getString("status"),
            rs.getObject("created_at", java.time.LocalDateTime.class)
    );

    public List<Proposition> listerEnAttente() {
        return jdbcTemplate.query(
                "SELECT * FROM rag_supersede_proposal WHERE status = 'PENDING' ORDER BY created_at",
                MAPPEUR);
    }

    /**
     * Confirme une proposition : l'article vise passe SUPERSEDED a la date donnee.
     *
     * <p>{@code supersededAt} est saisie par l'administrateur, PAS deduite du texte source —
     * la detection ne sait localiser qu'une tournure d'abrogation, pas etablir avec certitude
     * sa date d'entree en vigueur.</p>
     *
     * @throws ResponseStatusException 404 si la proposition n'existe pas, 409 si elle a deja
     *                                  ete revue (evite une double application)
     */
    @Transactional
    public void confirmer(long id, LocalDate supersededAt, String reviewedBy) {
        Proposition proposition = trouverPending(id);

        int misesAJour = chunkRepository.marquerSuperseded(
                proposition.codeName(), proposition.articleReference(), supersededAt);
        if (misesAJour == 0) {
            log.warn("Proposition {} confirmee mais aucun chunk ACTIVE ne correspond a "
                    + "(code_name={}, article_reference={}) : deja traite ailleurs ?",
                    id, proposition.codeName(), proposition.articleReference());
        }

        marquerRevue(id, "CONFIRMED", reviewedBy);
        log.warn("Proposition {} CONFIRMEE par {} : {} chunk(s) « {} » art. {} -> SUPERSEDED "
                + "au {}.", id, reviewedBy, misesAJour, proposition.codeName(),
                proposition.articleReference(), supersededAt);
    }

    @Transactional
    public void rejeter(long id, String reviewedBy) {
        trouverPending(id);
        marquerRevue(id, "REJECTED", reviewedBy);
    }

    private Proposition trouverPending(long id) {
        List<Proposition> trouvees = jdbcTemplate.query(
                "SELECT * FROM rag_supersede_proposal WHERE id = ?", MAPPEUR, id);
        if (trouvees.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Proposition « " + id + " » introuvable.");
        }
        Proposition p = trouvees.get(0);
        if (!"PENDING".equals(p.status())) {
            throw new ResponseStatusException(CONFLICT,
                    "Proposition « " + id + " » deja revue (statut " + p.status() + ").");
        }
        return p;
    }

    private void marquerRevue(long id, String statut, String reviewedBy) {
        jdbcTemplate.update("""
                UPDATE rag_supersede_proposal
                   SET status = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ?
                 WHERE id = ?
                """, statut, reviewedBy, id);
    }

    /**
     * Declare qu'un instrument entier remplace un autre (ex. Constitution 2014 -> 2022).
     * Ecrase toute ligne existante, y compris une importation DCAF : une decision humaine
     * explicite prime toujours sur un champ publisher.
     */
    @Transactional
    public void declarerInstrumentSuperseded(String codeName, String title, LocalDate inForceFrom,
                                              LocalDate inForceUntil, String supersededByCodeName,
                                              String declaredBy, String note) {
        instrumentRepository.declarerSuperseded(codeName, title, inForceFrom, inForceUntil,
                supersededByCodeName, LegalInstrumentStatus.ABROGE, declaredBy, note);
        log.warn("Instrument « {} » declare ABROGE par {} (remplace par « {} », a compter du {}).",
                codeName, declaredBy, supersededByCodeName, inForceUntil);
    }
}
