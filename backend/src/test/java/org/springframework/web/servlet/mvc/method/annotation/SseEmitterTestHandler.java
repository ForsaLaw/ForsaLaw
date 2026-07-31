package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pont de test : {@code ResponseBodyEmitter.Handler} est package-private dans spring-webmvc,
 * seule une classe du MEME NOM DE PAQUETAGE peut l'implementer -- d'ou cette classe placee ici
 * plutot que dans le paquetage du test qui l'utilise. Elle n'existe que pour observer ce
 * qu'un {@code SseEmitter} envoie reellement sans monter une vraie requete HTTP.
 */
public final class SseEmitterTestHandler implements ResponseBodyEmitter.Handler {

    private final List<String> envois = new ArrayList<>();
    private boolean complete;
    private boolean completeAvecErreur;

    /** Attache ce handler a un emitter : equivalent de ce que fait Spring MVC pour une vraie requete. */
    public void attacherA(SseEmitter emitter) {
        try {
            emitter.initialize(this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String texteEnvoye() {
        return String.join("", envois);
    }

    public boolean estComplete() {
        return complete;
    }

    public boolean estCompleteAvecErreur() {
        return completeAvecErreur;
    }

    @Override
    public void send(Object data, MediaType mediaType) {
        envois.add(String.valueOf(data));
    }

    @Override
    public void send(Set<ResponseBodyEmitter.DataWithMediaType> data) {
        for (ResponseBodyEmitter.DataWithMediaType d : data) {
            envois.add(String.valueOf(d.getData()));
        }
    }

    @Override
    public void complete() {
        complete = true;
    }

    @Override
    public void completeWithError(Throwable failure) {
        complete = true;
        completeAvecErreur = true;
    }

    @Override
    public void onTimeout(Runnable callback) {
    }

    @Override
    public void onError(Consumer<Throwable> callback) {
    }

    @Override
    public void onCompletion(Runnable callback) {
    }
}
