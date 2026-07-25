package com.forsalaw.auditManagement.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rapport de verification d'integrite du journal d'audit.
 *
 * <p>Trois controles independants, du plus faible au plus fort :</p>
 * <ol>
 *   <li>{@code trigger} — la protection anti-mutation de V8 est-elle toujours en place ?</li>
 *   <li>{@code sequence} — manque-t-il des identifiants (lignes supprimees) ?</li>
 *   <li>{@code chaine} — le chainage cryptographique est-il intact ?</li>
 * </ol>
 */
public record AuditIntegrityReport(
        LocalDateTime verifieLe,
        long lignesVerifiees,
        long dureeMs,
        boolean integre,
        TriggerCheck trigger,
        SequenceCheck sequence,
        ChaineCheck chaine
) {

    /** Controle A : le trigger d'immuabilite existe et est actif. */
    public record TriggerCheck(
            boolean valide,
            boolean present,
            boolean actif,
            String etat,
            String message
    ) {}

    /** Controle B : continuite des identifiants sequentiels, par annee. */
    public record SequenceCheck(
            boolean valide,
            List<AnomalieAnnee> anomalies,
            String message
    ) {
        public record AnomalieAnnee(
                String annee,
                long nombreLignes,
                int premierId,
                int dernierId,
                long manquants
        ) {}
    }

    /** Controle C : recalcul complet du chainage SHA-256. */
    public record ChaineCheck(
            boolean valide,
            long lignesControlees,
            List<Rupture> ruptures,
            String message
    ) {
        /** Premiere ligne dont l'empreinte ne correspond plus : point d'alteration probable. */
        public record Rupture(
                String auditLogId,
                String type,
                String attendu,
                String trouve
        ) {}
    }
}
