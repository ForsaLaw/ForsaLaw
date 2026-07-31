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

    /**
     * Pool DISTINCT pour la classification du domaine.
     *
     * <p>Le partager avec {@code aiChatExecutor} creerait un interblocage : ce dernier n'a que 4
     * fils, chacun occupe par une generation qui, a sa fin, attend le resultat d'une
     * classification. Sous 4 conversations simultanees, les classifications resteraient en file
     * derriere les generations qui les attendent — chacune bloquant l'autre.</p>
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService routingExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
