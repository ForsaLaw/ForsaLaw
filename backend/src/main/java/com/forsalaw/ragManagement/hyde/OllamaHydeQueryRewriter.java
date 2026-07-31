package com.forsalaw.ragManagement.hyde;

import com.forsalaw.ragManagement.llm.ScriptInattenduDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reformulation HyDE via un petit modele instruct auto-heberge (Ollama), sidecar au meme
 * titre que TEI pour les embeddings.
 *
 * <p><b>Auto-heberge et non API tierce</b>, pour la meme raison que bge-m3 : une question de
 * recherche peut porter sur un fait confidentiel (client, dossier) au meme titre qu'un
 * document. Contrairement au niveau 3 de {@code LegalChunkSearchService}, aucune restriction
 * de tier n'est appliquee ici : le modele ne quittant jamais l'infrastructure, il n'y a pas de
 * fuite a eviter en distinguant les niveaux.</p>
 *
 * <p><b>Modele retenu : qwen2.5:3b-instruct.</b> Teste avant adoption (comme bge-m3) plutot
 * que suppose : sur les 10 questions du jeu d'evaluation, un modele 7B aurait ete plus fiable
 * mais depasserait la VRAM disponible une fois TEI charge (6 Go au total, TEI en occupe deja
 * environ 1,5 Go) — le meme type de plafond qui avait declenche l'OOM de TEI corrige plus tot.
 * Le 3B choisi presente un defaut reel et mesure : sur 6 essais de reformulation en arabe,
 * 3 sorties etaient degradees (un basculement complet en chinois au milieu d'un paragraphe,
 * un mot etranger isole, une citation fabriquee avec un numero de forme "X/YY/XZ"). D'ou la
 * validation ci-dessous, qui ne se contente PAS d'un succes HTTP : Ollama peut repondre 200
 * avec un texte inutilisable, et le simple echec reseau/timeout ne l'aurait jamais detecte.</p>
 *
 * <p>Chaque proposition non retenue est signalee en log au niveau WARN — un taux d'echec
 * inhabituel est le signal qu'il faut reconsiderer la taille du modele, pas seulement
 * qu'une requete individuelle a echoue.</p>
 */
@Component
@ConditionalOnProperty(name = "forsalaw.rag.hyde.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OllamaHydeQueryRewriter implements HydeQueryRewriter {

    /**
     * Systeme de consignes : choix de langue explicitement DECOUPLE de la langue de la
     * question (mesure : une hypothese dans la mauvaise langue degrade le rang de la bonne
     * reponse plutot que de l'ameliorer — voir eval-006 dans le rapport de validation, ou une
     * hypothese arabe a fait chuter le rang de 78 a 416 parce que l'article reel est en
     * francais). Aucune reference d'article n'est demandee : la sortie n'est jamais montree a
     * l'utilisateur, seulement vectorisee.
     */
    private static final String SYSTEME = """
            Tu aides un moteur de recherche juridique tunisien a mieux comprendre une question \
            en la reformulant.

            Tache : redige un court paragraphe (2-3 phrases) qui ressemble a un article de loi \
            ou de reglement tunisien qui REPONDRAIT a la question. Style de redaction juridique \
            formelle, PAS une explication ni un resume.

            Ne cite AUCUN numero d'article precis. Reponds UNIQUEMENT en francais ou en arabe — \
            JAMAIS une autre langue, meme partiellement. N'insere aucun caractere chinois, \
            cyrillique ou d'un autre alphabet.

            Choisis la langue selon la matiere, PAS selon la langue de la question : \
            jurisprudence, droit de la famille, statut personnel, collectivites locales et \
            Constitution admettent souvent l'arabe comme langue source ; les codes generalistes \
            (obligations, commerce, travail, penal, procedures, fiscalite) sont presque \
            toujours en francais dans ce corpus. En cas de doute, prefere le francais.

            Reponds UNIQUEMENT par le paragraphe hypothetique, sans introduction, sans \
            guillemets, sans commentaire.""";

    private static final int MAX_CARACTERES_INATTENDUS_TOLERES = 2;
    private static final int LONGUEUR_MIN = 15;
    private static final int LONGUEUR_MAX = 1500;

    private final RestClient restClient;
    private final String modele;

    public OllamaHydeQueryRewriter(
            @Value("${forsalaw.rag.hyde.base-url:http://localhost:11434}") String baseUrl,
            @Value("${forsalaw.rag.hyde.model:qwen2.5:3b-instruct}") String modele,
            @Value("${forsalaw.rag.hyde.timeout-seconds:8}") int timeoutSeconds
    ) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        // Court : cet appel bloque une recherche utilisateur en direct. Un delai depasse
        // retombe sur la question brute (voir reformuler ci-dessous), jamais une erreur visible.
        requestFactory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());

        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.modele = modele;
    }

    @Override
    public Optional<String> reformuler(String question, int tier) {
        String contexte = tier == 2
                ? "Cette question porte sur la jurisprudence (tier 2)."
                : "Cette question porte sur un texte legislatif (tier " + tier + ").";

        try {
            var reponse = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RequeteOllama(modele, List.of(
                            new Message("system", SYSTEME),
                            new Message("user", contexte + "\n\nQuestion : " + question)
                    ), false, Map.of("temperature", 0.2)))
                    .retrieve()
                    .body(ReponseOllama.class);

            String hypothese = reponse == null || reponse.message() == null
                    ? null : reponse.message().content();

            return validerOuRejeter(hypothese, question);

        } catch (RestClientException e) {
            log.warn("HyDE : service de reformulation injoignable ou en timeout, repli sur la "
                    + "question brute ({}).", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Un succes HTTP ne garantit rien sur le contenu : voir le defaut mesure en javadoc de
     * classe. Cette validation est ce qui transforme "le modele a repondu" en "la reponse est
     * utilisable", et c'est elle — pas seulement le try/catch reseau — qui porte la garantie
     * de repli sur la question brute.
     */
    Optional<String> validerOuRejeter(String hypothese, String question) {
        if (hypothese == null || hypothese.isBlank()) {
            log.warn("HyDE : reponse vide pour « {} », repli sur la question brute.", question);
            return Optional.empty();
        }
        String nettoyee = hypothese.strip();
        if (nettoyee.length() < LONGUEUR_MIN || nettoyee.length() > LONGUEUR_MAX) {
            log.warn("HyDE : longueur suspecte ({} caracteres) pour « {} », repli sur la "
                    + "question brute.", nettoyee.length(), question);
            return Optional.empty();
        }
        long inattendus = ScriptInattenduDetector.compterCaracteresInattendus(nettoyee);
        if (inattendus > MAX_CARACTERES_INATTENDUS_TOLERES) {
            log.warn("HyDE : {} caractere(s) d'un alphabet inattendu dans la reponse a « {} », "
                    + "repli sur la question brute.", inattendus, question);
            return Optional.empty();
        }
        return Optional.of(nettoyee);
    }

    private record Message(String role, String content) {}

    private record RequeteOllama(String model, List<Message> messages, boolean stream,
                                  Map<String, Object> options) {}

    private record ReponseOllama(Message message, boolean done) {}
}
