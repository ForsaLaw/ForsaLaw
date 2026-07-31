package com.forsalaw.ragManagement.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AiChatConfig {

    /**
     * Execute la generation en flux HORS du thread Servlet qui a recu la requete : celui-ci
     * doit repartir immediatement (SseEmitter renvoye) pendant que la generation, potentiellement
     * longue, se poursuit en arriere-plan.
     *
     * <p>Borne a 4 : Ollama sert un seul modele sur un seul GPU, donc des generations
     * simultanees se serialisent de toute facon cote Ollama — un pool plus large n'accelererait
     * rien, il ne ferait qu'accepter plus de requetes en attente sans les traiter plus vite.</p>
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiChatExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
