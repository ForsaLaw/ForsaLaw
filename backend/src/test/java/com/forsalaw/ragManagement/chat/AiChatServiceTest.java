package com.forsalaw.ragManagement.chat;

import com.forsalaw.ragManagement.chat.budget.AiTokenBudgetService;
import com.forsalaw.ragManagement.chat.routing.DomaineClassifier;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository.ResultatRecherche;
import com.forsalaw.ragManagement.search.LegalChunkSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitterTestHandler;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cablage de {@link AiChatService} : recherche -> prompt -> flux SSE. Le client Ollama est
 * simule et son {@code GestionnaireFlux} est CAPTURE puis pilote manuellement depuis le test —
 * ce qui est verifie ici, c'est le cablage vers {@link SseEmitter}, pas le comportement reseau
 * (voir {@code OllamaChatGenerationClient} pour le flux reel).
 *
 * <p>{@link SseEmitterTestHandler} observe ce qu'un {@code SseEmitter} envoie reellement sans
 * monter une vraie requete HTTP -- necessaire car {@code ResponseBodyEmitter.Handler} est
 * package-private dans spring-webmvc.</p>
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    private static final String EMAIL = "client@forsalaw.tn";
    private static final Long ID_USAGE = 42L;

    @Mock LegalChunkSearchService searchService;
    @Mock ChatGenerationClient chatGenerationClient;
    @Mock AiTokenBudgetService budgetService;
    @Mock DomaineClassifier domaineClassifier;
    /** Executeur direct : la classification s'execute sur le thread du test, resultat immediat. */
    private final Executor routingExecutor = Runnable::run;
    @Captor ArgumentCaptor<List<ChatGenerationClient.Message>> messagesCaptor;
    @Captor ArgumentCaptor<ChatGenerationClient.GestionnaireFlux> gestionnaireCaptor;

    private AiChatService service;

    @BeforeEach
    void preparer() {
        service = new AiChatService(searchService, chatGenerationClient, budgetService, domaineClassifier, routingExecutor);
    }

    @Test
    void niveau3_estRefuseAvantTouteRecherche() {
        SseEmitterTestHandler espion = new SseEmitterTestHandler();
        SseEmitter emitter = nouvelEmitter(espion);

        service.repondreEnFlux("question confidentielle", 3, EMAIL, emitter);

        verify(searchService, never()).rechercher(anyString(), anyInt(), any(), any(), anyInt());
        verify(chatGenerationClient, never()).genererEnFlux(any(), any());
        assertThat(espion.texteEnvoye()).contains("niveau");
        assertThat(espion.estComplete()).isTrue();
    }

    @Test
    void resultatsTrouves_sontFormattesAvecCitationsDansLePrompt() {
        SseEmitter emitter = nouvelEmitter(new SseEmitterTestHandler());

        when(searchService.rechercher(eq("Quel délai de prescription ?"), eq(1), eq(null),
                any(LocalDate.class), eq(10)))
                .thenReturn(List.of(resultat("coc", "402", "Toutes les actions sont prescrites par quinze ans.")));

        service.repondreEnFlux("Quel délai de prescription ?", 1, EMAIL, emitter);

        verify(chatGenerationClient).genererEnFlux(messagesCaptor.capture(), any());
        String promptUtilisateur = messagesCaptor.getValue().get(1).content();

        assertThat(promptUtilisateur)
                .contains("[1]")
                .contains("coc")
                .contains("art. 402")
                .contains("Toutes les actions sont prescrites par quinze ans.")
                .contains("Quel délai de prescription ?");
    }

    @Test
    void jetonsRecus_sontRelayesVersLEmitter() {
        SseEmitterTestHandler espion = new SseEmitterTestHandler();
        SseEmitter emitter = nouvelEmitter(espion);

        when(searchService.rechercher(anyString(), anyInt(), any(), any(), anyInt())).thenReturn(List.of());
        when(budgetService.reserver(EMAIL)).thenReturn(ID_USAGE);

        service.repondreEnFlux("question", 1, EMAIL, emitter);

        verify(chatGenerationClient).genererEnFlux(any(), gestionnaireCaptor.capture());
        var gestionnaire = gestionnaireCaptor.getValue();

        gestionnaire.surJeton("Bonjour");
        gestionnaire.surJeton(" le monde.");
        gestionnaire.surJeton("\nSuite.");
        gestionnaire.surFin(new ChatGenerationClient.UsageJetons(120, 80));

        // Le depot est ajuste au reel a la fin du flux.
        verify(budgetService).regler(ID_USAGE, 120, 80);

        // Les jetons partent encodes en JSON : c'est ce qui preserve l'espace de tete et le saut
        // de ligne. Sans cet encodage, SSE mangeait l'espace initial de " le monde." (le client
        // retire un espace optionnel apres "data:", que Spring n'ecrit pas) et un "\n" brut
        // aurait coupe la trame en deux. Assertions sur la forme JSON exacte, pas sur un
        // simple "contains" du mot, qui passerait meme avec le defaut.
        assertThat(espion.texteEnvoye())
                .contains("\"Bonjour\"")
                .contains("\" le monde.\"")
                .contains("\"\\nSuite.\"");
        assertThat(espion.estComplete()).isTrue();
        assertThat(espion.estCompleteAvecErreur()).isFalse();
    }

    @Test
    void erreurDeGeneration_envoieUnEvenementErreurEtFerme() {
        SseEmitterTestHandler espion = new SseEmitterTestHandler();
        SseEmitter emitter = nouvelEmitter(espion);

        when(searchService.rechercher(anyString(), anyInt(), any(), any(), anyInt())).thenReturn(List.of());

        service.repondreEnFlux("question", 1, EMAIL, emitter);

        verify(chatGenerationClient).genererEnFlux(any(), gestionnaireCaptor.capture());
        gestionnaireCaptor.getValue().surErreur("reponse illisible, flux interrompu");

        assertThat(espion.texteEnvoye()).contains("illisible");
        assertThat(espion.estComplete()).isTrue();
    }

    @Test
    void budgetEpuise_refuseAvantToutAppelAuModele() {
        SseEmitterTestHandler espion = new SseEmitterTestHandler();
        SseEmitter emitter = nouvelEmitter(espion);

        when(budgetService.reserver(EMAIL)).thenThrow(
                new AiTokenBudgetService.BudgetEpuiseException("Votre quota quotidien est epuise."));

        service.repondreEnFlux("question", 1, EMAIL, emitter);

        // Ni recherche ni generation : le refus doit intervenir AVANT de depenser quoi que ce soit.
        verify(searchService, never()).rechercher(anyString(), anyInt(), any(), any(), anyInt());
        verify(chatGenerationClient, never()).genererEnFlux(any(), any());
        assertThat(espion.texteEnvoye()).contains("quota quotidien");
        assertThat(espion.estComplete()).isTrue();
    }

    @Test
    void fluxAbandonne_conserveLeDepot() {
        SseEmitterTestHandler espion = new SseEmitterTestHandler();
        SseEmitter emitter = nouvelEmitter(espion);

        when(searchService.rechercher(anyString(), anyInt(), any(), any(), anyInt())).thenReturn(List.of());

        service.repondreEnFlux("question", 1, EMAIL, emitter);

        verify(chatGenerationClient).genererEnFlux(any(), gestionnaireCaptor.capture());
        var gestionnaire = gestionnaireCaptor.getValue();

        // Client deconnecte : des jetons arrivent, mais la trame "done" ne vient JAMAIS.
        gestionnaire.surJeton("debut de reponse");

        // Le depot n'est donc jamais ajuste : il reste acquis. C'est precisement ce qui rend une
        // boucle d'abandon couteuse — sans cela, elle consommerait du calcul sans etre decomptee.
        verify(budgetService, never()).regler(any(), anyInt(), anyInt());
    }

    private ResultatRecherche resultat(String codeName, String articleReference, String content) {
        return new ResultatRecherche(codeName, articleReference, null, content, 0.1, 1,
                "src", LocalDate.now(), "ACTIVE", null, null, null, null);
    }

    private SseEmitter nouvelEmitter(SseEmitterTestHandler espion) {
        SseEmitter emitter = new SseEmitter(0L);
        espion.attacherA(emitter);
        return emitter;
    }
}
