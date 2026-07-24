package com.forsalaw.affaireManagement.service;

import com.forsalaw.affaireManagement.entity.Affaire;
import com.forsalaw.affaireManagement.entity.StatutAffaire;
import com.forsalaw.affaireManagement.entity.TypeAffaire;
import com.forsalaw.affaireManagement.model.AffaireDTO;
import com.forsalaw.affaireManagement.model.AffaireTimelineStepDTO;
import com.forsalaw.affaireManagement.repository.AffaireRepository;
import com.forsalaw.auditManagement.entity.AuditLog;
import com.forsalaw.auditManagement.repository.AuditLogRepository;
import com.forsalaw.rdvManagement.entity.RendezVous;
import com.forsalaw.rdvManagement.entity.StatutRendezVous;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.IdSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AffaireService {

    private static final int TITRE_MAX = 250;
    /** Module name used when writing affaire milestone events to the AuditLog. */
    public static final String MODULE_AFFAIRE = "AFFAIRE";

    private final AffaireRepository affaireRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final IdSequenceService idSequenceService;

    /**
     * Appelé lorsque le RDV passe à {@link StatutRendezVous#CONFIRME} (client a accepté la proposition de l'avocat).
     * Une seule affaire par RDV.
     */
    @Transactional
    public void creerAffaireSiRendezVousConfirme(RendezVous rdv) {
        if (rdv.getStatutRendezVous() != StatutRendezVous.CONFIRME) {
            return;
        }
        if (affaireRepository.existsByRendezVous_IdRendezVous(rdv.getIdRendezVous())) {
            return;
        }

        Affaire affaire = new Affaire();
        affaire.setId(idSequenceService.generateNextId("AFF"));
        affaire.setTitre(buildTitreDepuisRdv(rdv));
        affaire.setDescription(rdv.getMotifConsultation());
        affaire.setType(TypeAffaire.CIVIL);
        affaire.setStatut(StatutAffaire.INSTRUCTION);
        affaire.setClient(rdv.getClient());
        affaire.setAvocat(rdv.getAvocat());
        affaire.setRendezVous(rdv);
        // Affaire.dateProchaineAudience reste un LocalDateTime (hors scope Phase 7) : on convertit
        // l'instant du RDV en heure murale Africa/Tunis de facon deterministe pour l'affichage.
        affaire.setDateProchaineAudience(rdv.getDateHeureDebut() == null ? null
                : rdv.getDateHeureDebut().atZoneSameInstant(ZoneId.of("Africa/Tunis")).toLocalDateTime());

        affaireRepository.save(affaire);

        // Record the opening event in the audit log so the dynamic timeline can track it
        recordAffaireEvent(affaire.getId(), "OUVERTURE_AFFAIRE",
                "Dossier ouvert automatiquement suite au RDV " + rdv.getIdRendezVous());

        log.info("Affaire {} ouverte automatiquement depuis RDV confirme {}", affaire.getId(), rdv.getIdRendezVous());
    }

    private static String buildTitreDepuisRdv(RendezVous rdv) {
        String motif = rdv.getMotifConsultation();
        if (motif != null && !motif.isBlank()) {
            String t = motif.trim();
            if (t.length() > TITRE_MAX) {
                return t.substring(0, TITRE_MAX - 1) + "…";
            }
            return t;
        }
        return "Consultation — RDV " + rdv.getIdRendezVous();
    }

    // ─── Consultation (admin uniquement) ───────────────────────────────────────

    @Transactional(readOnly = true)
    public AffaireDTO getAffairePourAdmin(String affaireId, String emailActeur) {
        assertAdmin(requireUser(emailActeur));
        Affaire affaire = requireAffaire(affaireId);
        return toDTO(affaire, true);
    }

    @Transactional(readOnly = true)
    public Page<AffaireDTO> toutesLesAffaires(StatutAffaire statut, Pageable pageable) {
        Page<Affaire> page = (statut != null)
                ? affaireRepository.findByStatut(statut, pageable)
                : affaireRepository.findAll(pageable);
        return page.map(a -> toDTO(a, true));
    }

    // ─── Utilitaires privés ───────────────────────────────────────────────────

    private Affaire requireAffaire(String id) {
        return affaireRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Affaire introuvable."));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur inconnu."));
    }

    private static void assertAdmin(User acteur) {
        if (acteur.getRoleUser() != RoleUser.admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve aux administrateurs.");
        }
    }

    // ─── Timeline (Dynamique — pilotée par l'AuditLog) ───────────────────────

    @Transactional(readOnly = true)
    public List<AffaireTimelineStepDTO> getTimelinePourAdmin(String affaireId, String emailActeur) {
        assertAdmin(requireUser(emailActeur));
        Affaire affaire = requireAffaire(affaireId);
        return genererTimelineDynamique(affaire);
    }

    /**
     * Generates a timeline by combining:
     * 1. All audit log events tagged with MODULE_AFFAIRE + this affaire's ID (real timestamps).
     * 2. A static scaffold of the standard status progression as pending future steps.
     */
    private List<AffaireTimelineStepDTO> genererTimelineDynamique(Affaire affaire) {
        // Fetch real audit events for this affaire
        List<AuditLog> events = auditLogRepository
                .findByModuleNameAndResourceIdOrderByCreatedAtAsc(MODULE_AFFAIRE, affaire.getId());

        // Build a map: actionName → earliest occurrence timestamp
        Map<String, java.time.LocalDateTime> eventTimestamps = events.stream()
                .collect(Collectors.toMap(
                        AuditLog::getAction,
                        AuditLog::getCreatedAt,
                        (a, b) -> a // keep earliest
                ));

        // Standard status progression scaffold
        StatutAffaire[] ordre = {
            StatutAffaire.INSTRUCTION,
            StatutAffaire.AUDIENCE,
            StatutAffaire.DELIBERE,
            StatutAffaire.JUGEMENT,
            StatutAffaire.APPEL,
            StatutAffaire.CLOS
        };

        List<AffaireTimelineStepDTO> timeline = new ArrayList<>();
        boolean foundCurrent = false;

        for (int i = 0; i < ordre.length; i++) {
            StatutAffaire s = ordre[i];
            boolean active = (s == affaire.getStatut());
            if (active) foundCurrent = true;
            boolean completed = !foundCurrent && !active;

            // Look for a matching audit event: action name matches the status name or OUVERTURE_AFFAIRE for INSTRUCTION
            java.time.LocalDateTime occurredAt = null;
            if (s == StatutAffaire.INSTRUCTION) {
                occurredAt = eventTimestamps.getOrDefault("OUVERTURE_AFFAIRE",
                             eventTimestamps.get(StatutAffaire.INSTRUCTION.name()));
            } else {
                occurredAt = eventTimestamps.get(s.name());
            }
            // If no specific event, use the affaire opening date for completed steps
            if (occurredAt == null && completed && affaire.getDateOuverture() != null) {
                occurredAt = affaire.getDateOuverture();
            }

            timeline.add(new AffaireTimelineStepDTO(
                "STEP_" + (i + 1),
                s.name(),
                completed,
                active,
                occurredAt
            ));
        }

        // Append any extra audit events not part of the standard scaffold (custom milestones)
        events.stream()
              .filter(e -> !e.getAction().equals("OUVERTURE_AFFAIRE") &&
                           java.util.Arrays.stream(ordre).noneMatch(s -> s.name().equals(e.getAction())))
              .forEach(e -> timeline.add(new AffaireTimelineStepDTO(
                      e.getId(),
                      e.getAction(),
                      true,
                      false,
                      e.getCreatedAt()
              )));

        return timeline;
    }

    /**
     * Records a named affaire event to the AuditLog so it appears in the dynamic timeline.
     * Call this from any service method that represents a meaningful affaire milestone.
     */
    @Transactional
    public void recordAffaireEvent(String affaireId, String action, String details) {
        AuditLog event = new AuditLog();
        event.setId(idSequenceService.generateNextId("ADT"));
        event.setModuleName(MODULE_AFFAIRE);
        event.setAction(action);
        event.setResourceId(affaireId);
        event.setMethod("SYSTEM");
        event.setDetails(details != null && details.length() > 8000 ? details.substring(0, 7997) + "..." : details);
        auditLogRepository.save(event);
    }

    private AffaireDTO toDTO(Affaire a, boolean includePrivate) {
        AffaireDTO dto = new AffaireDTO();
        dto.setId(a.getId());
        dto.setTitre(a.getTitre());
        dto.setDescription(a.getDescription());
        dto.setType(a.getType());
        dto.setStatut(a.getStatut());
        dto.setClientId(a.getClient().getId());
        dto.setClientNom(a.getClient().getPrenom() + " " + a.getClient().getNom());
        if (a.getAvocat() != null) {
            dto.setAvocatId(a.getAvocat().getId());
            dto.setAvocatNom(a.getAvocat().getUser().getPrenom() + " " + a.getAvocat().getUser().getNom());
        }
        if (a.getReclamation() != null) {
            dto.setReclamationId(a.getReclamation().getId());
        }
        if (a.getRendezVous() != null) {
            dto.setRendezVousId(a.getRendezVous().getIdRendezVous());
        }
        dto.setDateProchaineAudience(a.getDateProchaineAudience());
        dto.setDateOuverture(a.getDateOuverture());
        dto.setDateMiseAJour(a.getDateMiseAJour());
        dto.setDateCloture(a.getDateCloture());
        if (includePrivate) {
            dto.setNotesInternes(a.getNotesInternes());
        }
        return dto;
    }
}
