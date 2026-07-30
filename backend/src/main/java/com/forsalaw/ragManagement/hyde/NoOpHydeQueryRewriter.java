package com.forsalaw.ragManagement.hyde;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation neutre utilisee quand {@code forsalaw.rag.hyde.enabled=false}.
 *
 * <p>Garantit qu'un bean {@link HydeQueryRewriter} existe toujours exactement une fois,
 * que le sidecar Ollama soit deploye ou non : {@code LegalChunkSearchService} n'a ainsi jamais
 * besoin de savoir si HyDE est active, seulement d'utiliser le resultat (toujours absent ici,
 * donc toujours un repli sur la question brute).</p>
 */
@Component
@ConditionalOnProperty(name = "forsalaw.rag.hyde.enabled", havingValue = "false")
public class NoOpHydeQueryRewriter implements HydeQueryRewriter {

    @Override
    public Optional<String> reformuler(String question, int tier) {
        return Optional.empty();
    }
}
