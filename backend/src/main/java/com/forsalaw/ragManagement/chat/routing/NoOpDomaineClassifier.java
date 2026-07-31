package com.forsalaw.ragManagement.chat.routing;

import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation neutre utilisee quand {@code forsalaw.rag.routing.enabled=false}.
 *
 * <p>Meme role que {@code NoOpHydeQueryRewriter} : garantir qu'un bean
 * {@link DomaineClassifier} existe toujours exactement une fois, que le sidecar soit deploye ou
 * non. {@code AiChatService} n'a ainsi pas a savoir si le routage est actif — il obtient
 * simplement un domaine toujours absent, donc aucune recommandation d'avocat.</p>
 */
@Component
@ConditionalOnProperty(name = "forsalaw.rag.routing.enabled", havingValue = "false")
public class NoOpDomaineClassifier implements DomaineClassifier {

    @Override
    public Optional<DomaineJuridique> classer(String question) {
        return Optional.empty();
    }
}
