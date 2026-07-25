package com.forsalaw.storage;

/** Echec d'une operation de stockage objet (reseau, droits, bucket absent...). */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /** L'objet demande n'existe pas dans le bucket. */
    public static class ObjectNotFound extends StorageException {
        public ObjectNotFound(String key, Throwable cause) {
            super("Objet introuvable dans le stockage : " + key, cause);
        }
    }
}
