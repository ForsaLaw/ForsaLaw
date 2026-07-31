package com.forsalaw.sosManagement.entity;

/** Etat de la mobilisation des avocats. Voir la contrainte CHECK de V15__sos_arrests.sql. */
public enum StatutDispatch {
    WAITING,
    DISPATCHED,
    /** Envoi tente sans succes : distinct de WAITING, qui n'a encore rien tente. */
    FAILED
}
