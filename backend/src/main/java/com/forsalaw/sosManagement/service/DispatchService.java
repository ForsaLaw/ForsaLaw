package com.forsalaw.sosManagement.service;

import com.forsalaw.sosManagement.entity.SosArrest;

/**
 * Mobilisation des avocats penalistes une fois le signalement regle.
 *
 * <p>Interface posee pour que l'integration reelle (SMS Twilio, WhatsApp) remplace le bouchon
 * sans toucher au flux. Reçoit le signalement complet et non son seul identifiant : le message
 * envoye a l'avocat doit contenir lieu et heure d'arrestation pour etre exploitable.</p>
 *
 * @return {@code true} si la mobilisation est partie, {@code false} sinon — l'appelant
 *     distingue ainsi DISPATCHED de FAILED plutot que de supposer le succes.
 */
public interface DispatchService {

    boolean mobiliserAvocats(SosArrest signalement);
}
