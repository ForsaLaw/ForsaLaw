package com.forsalaw.sosManagement.service;

/**
 * Encaissement du forfait d'intervention SOS.
 *
 * <p>Interface posee des maintenant pour que l'integration reelle (Flouci, ClicToPay) remplace
 * une implementation sans toucher au reste du flux. Le contrat suppose deja un reglement
 * ASYNCHRONE — c'est le fonctionnement de ces passerelles, qui redirigent l'utilisateur puis
 * notifient le marchand : concevoir l'appelant autour d'un retour synchrone imposerait de tout
 * reprendre le jour de l'integration.</p>
 */
public interface PaymentService {

    /**
     * Lance le reglement du signalement. Retourne immediatement ; l'issue est notifiee plus tard
     * via {@link SosArrestService#confirmerPaiement(String)}.
     */
    void demarrerPaiement(String sosArrestId);
}
