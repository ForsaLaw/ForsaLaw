package com.forsalaw.sosManagement.entity;

/** Etat du reglement d'un signalement SOS. Voir la contrainte CHECK de V15__sos_arrests.sql. */
public enum StatutPaiement {
    PENDING,
    PAID,
    /** Reglement refuse ou expire : le signalement reste en base, jamais dispatche. */
    FAILED
}
