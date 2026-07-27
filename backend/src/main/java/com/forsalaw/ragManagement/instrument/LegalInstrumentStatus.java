package com.forsalaw.ragManagement.instrument;

/**
 * Valeurs de {@code legal_instrument.status} (contrainte CHECK, V12).
 *
 * <p>Constantes plutot qu'un enum : convention deja suivie par {@code status} sur
 * {@code legal_document_chunk} (ACTIVE/SUPERSEDED, chaines simples liees a la base par JDBC).</p>
 */
public final class LegalInstrumentStatus {

    private LegalInstrumentStatus() {
    }

    public static final String EN_VIGUEUR = "EN_VIGUEUR";
    public static final String ABROGE = "ABROGE";
    public static final String NON_EN_VIGUEUR = "NON_EN_VIGUEUR";
    /** Aucune information : ne doit jamais masquer un resultat de recherche. */
    public static final String INCONNU = "INCONNU";
}
