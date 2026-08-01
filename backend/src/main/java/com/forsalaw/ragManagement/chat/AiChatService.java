package com.forsalaw.ragManagement.chat;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import com.forsalaw.ragManagement.chat.budget.AiTokenBudgetService;
import com.forsalaw.ragManagement.chat.routing.DomaineClassifier;
import com.forsalaw.ragManagement.ingestion.LegalDocumentIngestionService;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository.ResultatRecherche;
import com.forsalaw.ragManagement.search.LegalChunkSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestration de {@code /api/ai/chat} : recherche (avec HyDE, deja integre a
 * {@link LegalChunkSearchService}), construction du prompt RAG, generation en flux.
 *
 * <p><b>Restreint aux niveaux 1 et 2 pour cette version.</b> Le niveau 3 (coffre prive)
 * exigerait de verifier qu'un cabinet a le droit d'interroger tel {@code tenantId} — aucune
 * entite cabinet n'existe encore pour cette verification (meme limite deja documentee sur
 * {@code AdminRagIngestionService} et sur la colonne {@code tenant_id} elle-meme). Plutot que
 * de reproduire ce risque ici, le niveau 3 est refuse explicitement.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private static final int NOMBRE_CHUNKS = 10;

    /** Court : la classification a demarre avant la generation, elle doit deja etre terminee. */
    private static final long ATTENTE_MAX_DOMAINE_MS = 1500;

    private final LegalChunkSearchService searchService;
    private final ChatGenerationClient chatGenerationClient;
    private final AiTokenBudgetService budgetService;
    private final DomaineClassifier domaineClassifier;
    /** Type {@link Executor} et non ExecutorService : le cycle de vie du pool appartient a la
     *  configuration, ce service ne fait que soumettre — et un test peut ainsi injecter un
     *  executeur direct sans monter de pool. */
    private final Executor routingExecutor;

    /**
     * Consignes de citation stricte : le modele ne doit repondre qu'a partir des extraits
     * fournis, jamais inventer une reference absente du contexte. La sortie reste generee par
     * un modele 3B dont le taux de defaut a ete mesure (voir OllamaChatGenerationClient) : ces
     * consignes reduisent le risque, elles ne l'eliminent pas — d'ou le garde-fou de script en
     * aval et le disclaimer permanent cote interface (AiDisclaimerBanner).
     */
    private static final String CONSIGNES = """
            Tu es un assistant d'information juridique tunisien. Tu reponds UNIQUEMENT a partir \
            des extraits fournis.

            REGLE ABSOLUE : chaque affirmation juridique se termine par le numero de l'extrait \
            qui la fonde, entre crochets. Une phrase juridique sans [n] est une faute.

            Format impose :
            - 3 a 5 phrases MAXIMUM. Pas d'introduction, pas de plan, pas de titres en gras.
            - Chaque affirmation porte sa source : [1], [2]... Plusieurs sources : [1][3].
            - Derniere ligne, exactement : Ceci est une information generale, pas un conseil \
            juridique : consultez un avocat inscrit au barreau.

            Exemple de reponse ATTENDUE :
            La prescription des actions civiles est de quinze ans [1]. Ce delai court a compter \
            du jour ou l'obligation est devenue exigible [2]. Des delais plus courts existent \
            pour certaines actions particulieres [1].
            Ceci est une information generale, pas un conseil juridique : consultez un avocat \
            inscrit au barreau.

            Interdits :
            - Inventer un numero d'article, un code ou une reference absent des extraits.
            - Citer un extrait qui ne traite pas reellement de la question.
            - Repondre sur des connaissances generales : si les extraits ne repondent pas, \
            ecris-le franchement en une phrase, puis la ligne finale.

            Reponds dans la langue de la question. Sois bref : une reponse courte et sourcee \
            vaut mieux qu'un expose. RAPPEL : aucune phrase juridique sans [n].""";

    /**
     * @param emailUtilisateur identite capturee sur le thread de la requete par le controleur.
     *     Elle DOIT etre passee explicitement : cette methode s'execute sur un pool dedie, ou le
     *     SecurityContext n'est pas propage — le lire ici renverrait un contexte vide.
     */
    public void repondreEnFlux(String question, int tier, String emailUtilisateur, SseEmitter emitter) {
        if (tier != LegalDocumentIngestionService.TIER_LEGISLATION
                && tier != LegalDocumentIngestionService.TIER_JURISPRUDENCE) {
            envoyerErreurEtFermer(emitter, "Seuls les niveaux 1 (legislation) et 2 (jurisprudence) "
                    + "sont accessibles depuis cet assistant.");
            return;
        }

        // Depot preleve AVANT toute generation : le cout reel n'est connu qu'a la fin du flux.
        final Long idUsage;
        try {
            idUsage = budgetService.reserver(emailUtilisateur);
        } catch (AiTokenBudgetService.BudgetEpuiseException e) {
            envoyerErreurEtFermer(emitter, e.getMessage());
            return;
        }

        // Classification lancee EN PARALLELE de la recherche et de la generation, jamais avant.
        // En serie, elle ajouterait son propre aller-retour au delai avant le premier jeton, qui
        // est deja la partie la plus visible de l'attente. Lancee ici, elle se deroule pendant la
        // recherche puis la generation et est terminee depuis longtemps quand on la consulte, a
        // la fin du flux : le cout percu est nul.
        CompletableFuture<Optional<DomaineJuridique>> domaineFutur =
                CompletableFuture.supplyAsync(() -> domaineClassifier.classer(question), routingExecutor)
                        .exceptionally(e -> {
                            log.warn("Routage : classification en echec, aucune recommandation.", e);
                            return Optional.empty();
                        });

        List<ResultatRecherche> resultats;
        try {
            resultats = searchService.rechercher(question, tier, null, LocalDate.now(), NOMBRE_CHUNKS);
        } catch (RuntimeException e) {
            log.error("Chat : recherche impossible pour la question posee.", e);
            envoyerErreurEtFermer(emitter, "La recherche dans le corpus juridique a echoue.");
            return;
        }

        String prompt = construirePrompt(resultats, question);

        var messages = List.of(
                new ChatGenerationClient.Message("system", CONSIGNES),
                new ChatGenerationClient.Message("user", prompt)
        );

        chatGenerationClient.genererEnFlux(messages, new ChatGenerationClient.GestionnaireFlux() {
            @Override
            public void surJeton(String delta) {
                try {
                    emitter.send(SseEmitter.event().name("jeton").data(encoderJeton(delta)));
                } catch (IOException e) {
                    // Le client a ferme la connexion (onglet ferme, navigation) : rien de plus
                    // a faire, l'emitter se completera de lui-meme via son propre listener.
                    log.debug("Chat : envoi impossible, client probablement deconnecte.", e);
                }
            }

            @Override
            public void surFin(ChatGenerationClient.UsageJetons usage) {
                // Ajustement du depot au cout reel. Si le client s'est deconnecte avant cette
                // trame, ce code n'est jamais atteint et le depot reste acquis : c'est ce qui
                // rend une boucle d'abandon couteuse.
                budgetService.regler(idUsage, usage.invite(), usage.reponse());
                envoyerDomaineSiConnu(emitter, domaineFutur);
                emitter.complete();
            }

            @Override
            public void surErreur(String messageErreur) {
                envoyerErreurEtFermer(emitter, messageErreur);
            }
        });
    }

    /**
     * Encode un jeton en chaine JSON (guillemets compris) avant de l'envoyer en SSE.
     *
     * <p>Indispensable : le protocole SSE est sensible aux espaces et aux sauts de ligne. Un
     * client conforme retire UN espace optionnel apres {@code data:}, alors que le SseEmitter de
     * Spring ecrit {@code data:} sans espace — un jeton commencant par une espace perdait donc
     * son espace (defaut constate : "Selonlesextraits..."). Un jeton contenant un saut de ligne
     * casserait en plus le decoupage des trames. Encoder en JSON rend le transport neutre :
     * espaces, sauts de ligne et caracteres speciaux traversent intacts, le client faisant
     * simplement {@code JSON.parse} (voir sse.js / AiSanctumPage.jsx).</p>
     */
    private static String encoderJeton(String delta) {
        return '"' + new String(JsonStringEncoder.getInstance().quoteAsString(delta)) + '"';
    }

    /**
     * Emet l'evenement {@code domaine} juste avant la fermeture du flux, si la classification a
     * abouti.
     *
     * <p>Envoye APRES le dernier jeton et depuis le meme thread : un envoi concurrent depuis le
     * thread de classification pourrait s'entrelacer avec les jetons et corrompre les trames SSE.
     * L'attente est bornee et courte — la classification a demarre bien avant la generation,
     * elle est normalement finie ; si elle ne l'est pas, la reponse part sans recommandation
     * plutot que de faire patienter l'utilisateur pour un simple complement.</p>
     */
    private void envoyerDomaineSiConnu(SseEmitter emitter,
                                       CompletableFuture<Optional<DomaineJuridique>> domaineFutur) {
        try {
            Optional<DomaineJuridique> domaine =
                    domaineFutur.get(ATTENTE_MAX_DOMAINE_MS, TimeUnit.MILLISECONDS);
            if (domaine.isEmpty()) {
                return;
            }
            DomaineJuridique d = domaine.get();
            emitter.send(SseEmitter.event().name("domaine").data(
                    "{\"code\":\"" + d.name() + "\",\"libelle\":\"" + echapper(d.getLibelle()) + "\"}"));
        } catch (TimeoutException e) {
            log.debug("Routage : classification non terminee a la fin du flux, aucune recommandation.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | IOException e) {
            log.debug("Routage : envoi du domaine impossible ({}).", e.toString());
        }
    }

    private static String echapper(String texte) {
        return new String(JsonStringEncoder.getInstance().quoteAsString(texte));
    }

    private void envoyerErreurEtFermer(SseEmitter emitter, String messageErreur) {
        try {
            emitter.send(SseEmitter.event().name("erreur").data(messageErreur));
        } catch (IOException ignored) {
            // Client deja deconnecte : rien a envoyer.
        } finally {
            emitter.complete();
        }
    }

    private String construirePrompt(List<ResultatRecherche> resultats, String question) {
        StringBuilder sb = new StringBuilder();
        if (resultats.isEmpty()) {
            sb.append("Aucun extrait pertinent n'a ete trouve dans le corpus pour cette question.\n\n");
        } else {
            sb.append("Extraits du corpus juridique tunisien :\n\n");
            for (int i = 0; i < resultats.size(); i++) {
                ResultatRecherche r = resultats.get(i);
                sb.append('[').append(i + 1).append("] ")
                        .append(r.codeName())
                        .append(", art. ").append(r.articleReference())
                        .append('\n')
                        .append('"').append(r.content().strip()).append('"')
                        .append("\n\n");
            }
        }
        sb.append("Question : ").append(question);
        return sb.toString();
    }
}
