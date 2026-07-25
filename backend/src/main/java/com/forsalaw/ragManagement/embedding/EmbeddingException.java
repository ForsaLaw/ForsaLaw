package com.forsalaw.ragManagement.embedding;

/** Echec de vectorisation (service injoignable, reponse malformee, dimension inattendue). */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
