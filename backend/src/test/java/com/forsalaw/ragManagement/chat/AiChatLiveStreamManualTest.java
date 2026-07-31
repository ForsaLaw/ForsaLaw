package com.forsalaw.ragManagement.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve, contre un Ollama en direct, que la generation arrive reellement JETON PAR JETON
 * (pas une reponse complete decoupee artificiellement) et que le flux se termine proprement.
 *
 * <p>Desactive par defaut, memes raisons que {@code HydeTopTenHitRateManualTest} : necessite
 * Ollama en direct (localhost:11434), jamais execute en CI.</p>
 */
@Disabled("Necessite Ollama en direct (localhost:11434) ; jamais execute en CI. "
        + "Derniere execution : 10 jetons recus entre 7431ms et 7588ms, reponse coherente.")
class AiChatLiveStreamManualTest {

    @Test
    void jetonsArriventProgressivementEtLeFluxSeTermine() throws InterruptedException {
        var client = new OllamaChatGenerationClient(
                new ObjectMapper(), "http://localhost:11434", "qwen2.5:3b-instruct", 30);

        var messages = List.of(
                new ChatGenerationClient.Message("system",
                        "Tu es un assistant. Reponds en une phrase courte, en francais."),
                new ChatGenerationClient.Message("user", "Quelle est la capitale de la Tunisie ?")
        );

        StringBuilder texteAccumule = new StringBuilder();
        java.util.List<Long> instantsReception = new java.util.ArrayList<>();
        AtomicBoolean fini = new AtomicBoolean(false);
        AtomicBoolean erreur = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        long debut = System.nanoTime();
        client.genererEnFlux(messages, new ChatGenerationClient.GestionnaireFlux() {
            @Override
            public void surJeton(String delta) {
                texteAccumule.append(delta);
                instantsReception.add((System.nanoTime() - debut) / 1_000_000);
            }

            @Override
            public void surFin() {
                fini.set(true);
                latch.countDown();
            }

            @Override
            public void surErreur(String messageErreur) {
                erreur.set(true);
                System.out.println("ERREUR : " + messageErreur);
                latch.countDown();
            }
        });

        assertThat(latch.await(60, TimeUnit.SECONDS)).as("le flux doit se terminer sous 60s").isTrue();

        System.out.println("Reponse complete : " + texteAccumule);
        System.out.println("Nombre de jetons recus : " + instantsReception.size());
        System.out.println("Instants de reception (ms) : " + instantsReception);

        assertThat(erreur).isFalse();
        assertThat(fini).isTrue();
        assertThat(texteAccumule.toString()).isNotBlank();
        // Preuve que ce n'est PAS une reponse complete decoupee apres coup : plusieurs jetons
        // distincts, arrivant a des instants differents (pas tous au meme milliseconde).
        assertThat(instantsReception.size()).isGreaterThan(1);
        assertThat(new java.util.HashSet<>(instantsReception).size()).isGreaterThan(1);
    }
}
