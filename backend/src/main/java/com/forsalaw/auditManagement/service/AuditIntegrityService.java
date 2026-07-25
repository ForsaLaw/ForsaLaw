package com.forsalaw.auditManagement.service;

import com.forsalaw.auditManagement.model.AuditIntegrityReport;
import com.forsalaw.auditManagement.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Verification d'integrite du journal d'audit.
 *
 * <p>Les trois controles sont complementaires et n'ont pas la meme force :</p>
 * <ul>
 *   <li><b>Trigger</b> : detecte la neutralisation de la protection anti-mutation. Ne dit rien
 *       de ce qui a pu se passer PENDANT que le trigger etait desactive.</li>
 *   <li><b>Sequence</b> : detecte des lignes supprimees, meme si le trigger a ete remis en
 *       place ensuite — sauf si l'auteur a aussi renumerote les lignes restantes.</li>
 *   <li><b>Chaine</b> : detecte toute modification, suppression ou insertion. C'est le seul
 *       controle reellement probant, et il indique la PREMIERE ligne alteree.</li>
 * </ul>
 *
 * <p><b>Limite a garder en tete :</b> qui possede un acces complet a la base peut recalculer la
 * chaine entiere apres avoir modifie une ligne, et les trois controles redeviendraient verts.
 * La detection n'est definitive que si la tete de chaine est archivee a l'exterieur de la base
 * (export periodique vers le bucket de sauvegarde).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditIntegrityService {

    /** Taille des tranches lues pour le recalcul (pagination par cle). */
    private static final int TAILLE_TRANCHE = 500;

    /** Au-dela, on arrete d'accumuler le detail : la premiere rupture suffit a diagnostiquer. */
    private static final int MAX_RUPTURES_SIGNALEES = 20;

    private final AuditLogRepository auditLogRepository;
    private final AuditChainService auditChainService;

    @Transactional(readOnly = true)
    public AuditIntegrityReport verifier() {
        long debut = System.currentTimeMillis();

        AuditIntegrityReport.TriggerCheck trigger = verifierTrigger();
        AuditIntegrityReport.SequenceCheck sequence = verifierSequence();
        AuditIntegrityReport.ChaineCheck chaine = verifierChaine();

        boolean integre = trigger.valide() && sequence.valide() && chaine.valide();
        if (!integre) {
            log.error("Verification d'integrite du journal d'audit EN ECHEC : trigger={} sequence={} chaine={}",
                    trigger.valide(), sequence.valide(), chaine.valide());
        }

        return new AuditIntegrityReport(
                LocalDateTime.now(),
                chaine.lignesControlees(),
                System.currentTimeMillis() - debut,
                integre,
                trigger,
                sequence,
                chaine
        );
    }

    // ─── Controle A : protection anti-mutation ───────────────────────────────

    private AuditIntegrityReport.TriggerCheck verifierTrigger() {
        String etat = auditLogRepository.findImmutabilityTriggerState().orElse(null);

        if (etat == null) {
            return new AuditIntegrityReport.TriggerCheck(false, false, false, null,
                    "Le trigger d'immuabilite trg_audit_log_no_mutation est ABSENT : le journal "
                            + "accepte les UPDATE et les DELETE.");
        }
        // 'O' = actif pour les operations d'origine ; 'D' = desactive.
        boolean actif = "O".equals(etat) || "A".equals(etat) || "R".equals(etat);
        if (!actif) {
            return new AuditIntegrityReport.TriggerCheck(false, true, false, etat,
                    "Le trigger d'immuabilite est present mais DESACTIVE (etat '" + etat
                            + "') : le journal est modifiable.");
        }
        return new AuditIntegrityReport.TriggerCheck(true, true, true, etat,
                "Trigger d'immuabilite present et actif.");
    }

    // ─── Controle B : continuite des identifiants ────────────────────────────

    private AuditIntegrityReport.SequenceCheck verifierSequence() {
        List<AuditIntegrityReport.SequenceCheck.AnomalieAnnee> anomalies = new ArrayList<>();

        for (AuditLogRepository.AuditSequenceRow ligne : auditLogRepository.findSequenceStatsByYear()) {
            long attendu = (long) ligne.getDernier() - ligne.getPremier() + 1;
            long manquants = attendu - ligne.getNombre();
            if (manquants != 0) {
                anomalies.add(new AuditIntegrityReport.SequenceCheck.AnomalieAnnee(
                        ligne.getAnnee(), ligne.getNombre(), ligne.getPremier(), ligne.getDernier(), manquants));
            }
        }

        if (anomalies.isEmpty()) {
            return new AuditIntegrityReport.SequenceCheck(true, List.of(),
                    "Identifiants continus : aucune entree manquante.");
        }
        return new AuditIntegrityReport.SequenceCheck(false, anomalies,
                "Des identifiants manquent dans la numerotation : des entrees ont probablement ete supprimees.");
    }

    // ─── Controle C : chainage cryptographique ───────────────────────────────

    private AuditIntegrityReport.ChaineCheck verifierChaine() {
        List<AuditIntegrityReport.ChaineCheck.Rupture> ruptures = new ArrayList<>();
        String attenduPrevHash = AuditChainService.GENESIS_HASH;
        String dernierId = null;
        long lignes = 0;

        while (true) {
            List<AuditLogRepository.AuditChainRow> tranche =
                    auditLogRepository.findChainSlice(dernierId, TAILLE_TRANCHE);
            if (tranche.isEmpty()) {
                break;
            }

            for (AuditLogRepository.AuditChainRow ligne : tranche) {
                lignes++;

                // 1. Le maillon pointe-t-il bien vers la ligne precedente ?
                if (!attenduPrevHash.equals(ligne.getPrevHash())) {
                    ajouterRupture(ruptures, ligne.getId(), "MAILLON_ROMPU",
                            attenduPrevHash, ligne.getPrevHash());
                }

                // 2. Le contenu correspond-il toujours a son empreinte ?
                String canonical = auditChainService.canonical(
                        ligne.getId(),
                        ligne.getModuleName(),
                        ligne.getAction(),
                        ligne.getMethod(),
                        ligne.getEndpoint(),
                        ligne.getResourceId(),
                        ligne.getHttpStatus(),
                        ligne.getIpAddress(),
                        ligne.getUserAgent(),
                        ligne.getDetails(),
                        ligne.getActorUserId(),
                        ligne.getCreatedAt().toLocalDateTime()
                );
                String recalcule = auditChainService.hash(canonical, ligne.getPrevHash());
                if (!recalcule.equals(ligne.getRowHash())) {
                    ajouterRupture(ruptures, ligne.getId(), "CONTENU_MODIFIE",
                            recalcule, ligne.getRowHash());
                }

                // On enchaine sur l'empreinte STOCKEE : une seule ligne alteree ne doit pas
                // faire remonter une rupture sur toutes les suivantes.
                attenduPrevHash = ligne.getRowHash();
                dernierId = ligne.getId();
            }

            if (tranche.size() < TAILLE_TRANCHE) {
                break;
            }
        }

        if (ruptures.isEmpty()) {
            return new AuditIntegrityReport.ChaineCheck(true, lignes, List.of(),
                    "Chainage intact sur " + lignes + " entree(s).");
        }
        return new AuditIntegrityReport.ChaineCheck(false, lignes, ruptures,
                "Chainage rompu : le journal a ete altere. Premiere anomalie sur l'entree "
                        + ruptures.get(0).auditLogId() + ".");
    }

    private void ajouterRupture(List<AuditIntegrityReport.ChaineCheck.Rupture> ruptures,
                                String id, String type, String attendu, String trouve) {
        if (ruptures.size() < MAX_RUPTURES_SIGNALEES) {
            ruptures.add(new AuditIntegrityReport.ChaineCheck.Rupture(id, type, attendu, trouve));
        }
    }
}
