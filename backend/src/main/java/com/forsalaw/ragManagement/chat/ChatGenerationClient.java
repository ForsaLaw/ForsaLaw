package com.forsalaw.ragManagement.chat;

import java.util.List;

/**
 * Generation d'une reponse en flux (jeton par jeton) par un modele auto-heberge.
 *
 * <p>Contrairement a {@code EmbeddingClient} (un appel, une reponse complete), cette
 * interface est intrinsequement STREAMING : la reponse est montree a l'utilisateur au fur et
 * a mesure de sa generation, elle ne peut donc pas etre validee dans son ensemble avant d'etre
 * affichee comme l'est {@code HydeQueryRewriter}. Voir {@code OllamaChatGenerationClient} pour
 * ce que cette contrainte implique.</p>
 */
public interface ChatGenerationClient {

    record Message(String role, String content) {}

    /**
     * @param messages messages systeme + utilisateur, dans l'ordre d'envoi au modele
     * @param gestionnaire recoit les jetons au fur et a mesure, puis la fin ou l'erreur
     */
    void genererEnFlux(List<Message> messages, GestionnaireFlux gestionnaire);

    /** Callback de reception. Un seul de {@code surFin}/{@code surErreur} est appele, jamais les deux. */
    interface GestionnaireFlux {
        void surJeton(String delta);

        void surFin();

        void surErreur(String messageErreur);
    }
}
