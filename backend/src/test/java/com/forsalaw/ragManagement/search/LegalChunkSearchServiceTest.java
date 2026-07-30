package com.forsalaw.ragManagement.search;

import com.forsalaw.ragManagement.embedding.EmbeddingClient;
import com.forsalaw.ragManagement.hyde.HydeQueryRewriter;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cablage HyDE dans {@link LegalChunkSearchService} : verifie que l'hypothese remplace bien
 * la question pour la vectorisation quand elle est disponible, et que la question brute est
 * utilisee sans qu'aucune exception ne remonte quand {@link HydeQueryRewriter} echoue.
 *
 * <p>Test rapide, sans reseau ni Spring : {@link HydeQueryRewriter} est simule. La preuve que
 * la reformulation ameliore reellement le rang en top 10 se fait separement, contre le corpus
 * reel et un Ollama en direct — voir {@code HydeTopTenHitRateManualTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
class LegalChunkSearchServiceTest {

    @Mock EmbeddingClient embeddingClient;
    @Mock HydeQueryRewriter hydeQueryRewriter;
    @Mock LegalDocumentChunkRepository chunkRepository;

    @InjectMocks LegalChunkSearchService searchService;

    private static final float[] VECTEUR = new float[]{0.1f};

    @Test
    void hypotheseDisponible_estEmbeddeeALaPlaceDeLaQuestion() {
        String question = "Quel est le délai de prescription de droit commun ?";
        String hypothese = "Le délai de prescription de droit commun est de quinze ans.";

        when(hydeQueryRewriter.reformuler(question, 1)).thenReturn(Optional.of(hypothese));
        when(embeddingClient.embedOne(hypothese)).thenReturn(VECTEUR);
        when(chunkRepository.rechercherParSimilarite(eq(VECTEUR), eq(1), eq((String) null), any(), eq(10)))
                .thenReturn(List.of());

        searchService.rechercher(question, 1, null, LocalDate.now(), 10);

        verify(embeddingClient).embedOne(hypothese);
        verify(embeddingClient, never()).embedOne(question);
    }

    @Test
    void hydeIndisponible_replieSurLaQuestionBrute() {
        String question = "Quel est le délai de prescription de droit commun ?";

        when(hydeQueryRewriter.reformuler(question, 1)).thenReturn(Optional.empty());
        when(embeddingClient.embedOne(question)).thenReturn(VECTEUR);
        when(chunkRepository.rechercherParSimilarite(eq(VECTEUR), eq(1), eq((String) null), any(), eq(10)))
                .thenReturn(List.of());

        // Ne doit lever aucune exception : un HyDE absent ne doit jamais faire echouer une
        // recherche, c'est la garantie centrale de ce cablage.
        assertThatCode(() -> searchService.rechercher(question, 1, null, LocalDate.now(), 10))
                .doesNotThrowAnyException();

        verify(embeddingClient).embedOne(question);
    }

    @Test
    void hydeAppelePourTousLesTiers_yComprisLeNiveau3() {
        // Auto-heberge : contrairement a une API tierce, aucune restriction de tier n'est
        // necessaire (voir OllamaHydeQueryRewriter). Le tenantId reste obligatoire pour le
        // niveau 3, verifie ici comme pour les autres tiers.
        String question = "Une clause de non-concurrence est-elle valable pour ce dossier ?";
        when(hydeQueryRewriter.reformuler(question, 3)).thenReturn(Optional.empty());
        when(embeddingClient.embedOne(anyString())).thenReturn(VECTEUR);
        when(chunkRepository.rechercherParSimilarite(any(), eq(3), eq("2026-FIRM-00001"), any(), eq(10)))
                .thenReturn(List.of());

        searchService.rechercher(question, 3, "2026-FIRM-00001", LocalDate.now(), 10);

        verify(hydeQueryRewriter).reformuler(question, 3);
    }

    @Test
    void tenantIdManquantPourNiveau3_estRefuse() {
        assertThatThrownBy(() -> searchService.rechercher("q", 3, null, LocalDate.now(), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }
}
