package com.forsalaw.sosManagement.model;

import com.forsalaw.sosManagement.entity.SosArrest;
import com.forsalaw.sosManagement.entity.StatutDispatch;
import com.forsalaw.sosManagement.entity.StatutPaiement;

import java.time.LocalDateTime;

/**
 * Etat d'un signalement, tel que renvoye au declarant.
 *
 * <p>N'expose ni {@code userId} ni les coordonnees des avocats mobilises : le declarant a
 * besoin de savoir ou en est sa demande, pas qui a ete contacte.</p>
 */
public record SosArrestResponse(
        String id,
        String nomDetenu,
        String lieuArrestation,
        LocalDateTime dateHeureArrestation,
        StatutPaiement statutPaiement,
        StatutDispatch statutDispatch,
        LocalDateTime createdAt
) {
    public static SosArrestResponse depuis(SosArrest s) {
        return new SosArrestResponse(
                s.getId(),
                s.getNomDetenu(),
                s.getLieuArrestation(),
                s.getDateHeureArrestation(),
                s.getStatutPaiement(),
                s.getStatutDispatch(),
                s.getCreatedAt()
        );
    }
}
