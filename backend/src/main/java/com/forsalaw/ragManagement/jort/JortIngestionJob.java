package com.forsalaw.ragManagement.jort;

import com.forsalaw.ragManagement.ingestion.LegalDocumentIngestionService;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Collecte planifiee des nouveaux numeros du JORT (niveau 1 du corpus).
 *
 * <p><b>Desactivee par defaut</b> ({@code forsalaw.rag.jort.enabled=false}). Il s'agit d'un
 * site gouvernemental : une tache planifiee active par defaut le solliciterait depuis chaque
 * poste de developpement, chaque execution de CI et chaque replica de production. Elle ne
 * doit etre activee que sur UNE instance, deliberement.</p>
 *
 * <p>ShedLock garantit qu'un seul replica execute la collecte, sur le modele de
 * {@code RdvReminderScheduler}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JortIngestionJob {

    /** Nom de code du corpus legislatif ; sert de {@code code_name} par defaut. */
    private static final String CODE_NAME_JORT = "JORT";

    private final JortHtmlParser parser;
    private final LegalDocumentIngestionService ingestionService;
    private final LegalDocumentChunkRepository chunkRepository;

    @Value("${forsalaw.rag.jort.enabled:false}")
    private boolean active;

    /**
     * Miroir jort.tn et NON iort.gov.tn.
     *
     * <p>L'audit des sources (docs/CORPUS_MANIFEST.md) a etabli que {@code iort.gov.tn} est
     * inexploitable par un client HTTP : application WinDev a session, sans HTTPS. La valeur
     * par defaut precedente ne pouvait donc jamais aboutir — la tache aurait echoue a chaque
     * execution une fois activee.</p>
     */
    @Value("${forsalaw.rag.jort.base-url:https://www.jort.tn}")
    private String urlBase;

    @Value("${forsalaw.rag.jort.index-path:/}")
    private String cheminIndex;

    /** Pause entre deux requetes : on ne martele pas un service public. */
    @Value("${forsalaw.rag.jort.delay-between-requests-ms:3000}")
    private long delaiEntreRequetesMs;

    /** Garde-fou : au-dela, on s'arrete et on reprendra a la prochaine execution. */
    @Value("${forsalaw.rag.jort.max-issues-per-run:5}")
    private int maxNumerosParExecution;

    /**
     * Mardi et vendredi a 2h30 (heure de Tunis) : le JORT parait en general ces jours-la, et
     * la nuit evite les heures ouvrables du service.
     *
     * <p>{@code lockAtMostFor} depasse largement la duree attendue : si l'instance meurt en
     * cours de collecte, le verrou se libere quand meme.</p>
     */
    @Scheduled(cron = "${forsalaw.rag.jort.cron:0 30 2 * * TUE,FRI}",
            zone = "${forsalaw.notifications.timezone:Africa/Tunis}")
    @SchedulerLock(name = "jortIngestion", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void collecterNouveauxNumeros() {
        if (!active) {
            log.debug("Collecte JORT desactivee (forsalaw.rag.jort.enabled=false).");
            return;
        }

        log.info("Collecte JORT : demarrage.");
        RestClient client = construireClient();

        List<JortHtmlParser.NumeroJort> numeros;
        try {
            String indexHtml = client.get().uri(cheminIndex).retrieve().body(String.class);
            numeros = parser.extraireNumeros(indexHtml == null ? "" : indexHtml, urlBase);
        } catch (RestClientException e) {
            log.error("Page d'index JORT injoignable : collecte abandonnee pour cette execution.", e);
            return;
        }

        int traites = 0;
        for (JortHtmlParser.NumeroJort numero : numeros) {
            if (traites >= maxNumerosParExecution) {
                log.info("Limite de {} numero(s) par execution atteinte : la suite sera traitee "
                        + "a la prochaine collecte.", maxNumerosParExecution);
                break;
            }
            // Deja en base : on ne retelecharge meme pas la page.
            if (chunkRepository.compterParSource(numero.reference()) > 0) {
                continue;
            }
            if (traiterNumero(client, numero)) {
                traites++;
            }
            temporiser();
        }

        log.info("Collecte JORT terminee : {} nouveau(x) numero(s) ingere(s).", traites);
    }

    private boolean traiterNumero(RestClient client, JortHtmlParser.NumeroJort numero) {
        try {
            String html = client.get().uri(numero.url()).retrieve().body(String.class);
            String texte = parser.extraireTexte(html == null ? "" : html, numero.url());

            if (texte.isBlank()) {
                log.warn("Numero JORT « {} » : aucun texte extrait, ignore.", numero.reference());
                return false;
            }

            // Date de PUBLICATION, jamais la date d'ingestion. Le corpus couvre 1957-2026 :
            // horodater a LocalDate.now() rendrait effective_date sans valeur, alors que c'est
            // precisement la colonne sur laquelle repose la distinction ACTIVE / SUPERSEDED.
            // Inconnue => null : une date absente est exploitable, une date fausse ne l'est pas.
            var demande = new LegalDocumentIngestionService.DemandeIngestion(
                    CODE_NAME_JORT,
                    numero.reference(),
                    LegalDocumentIngestionService.TIER_LEGISLATION,
                    null,
                    numero.publicationDate(),
                    numero.reference()
            );
            var resultat = ingestionService.ingererTexte(texte, demande);
            log.info("Numero JORT « {} » ingere : {} chunk(s).", numero.reference(), resultat.chunksCrees());
            return true;

        } catch (RestClientException e) {
            // Un numero en echec ne doit pas interrompre les suivants.
            log.error("Numero JORT « {} » injoignable, passage au suivant.", numero.reference(), e);
            return false;
        } catch (RuntimeException e) {
            log.error("Numero JORT « {} » : echec d'ingestion, passage au suivant.", numero.reference(), e);
            return false;
        }
    }

    private RestClient construireClient() {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

        return RestClient.builder()
                .baseUrl(urlBase)
                .requestFactory(requestFactory)
                // Agent identifiable : un service public doit pouvoir savoir qui le sollicite
                // et nous joindre en cas de probleme.
                .defaultHeader("User-Agent", "ForsaLawBot/1.0 (+https://forsalaw.tn; contact@forsalaw.tn)")
                .defaultHeader("Accept", MediaType.TEXT_HTML_VALUE)
                .build();
    }

    private void temporiser() {
        try {
            Thread.sleep(delaiEntreRequetesMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
