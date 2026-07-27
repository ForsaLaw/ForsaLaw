package com.forsalaw.ragManagement.ingestion;

import com.forsalaw.ragManagement.instrument.DcafStatutNormalizer;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import com.forsalaw.ragManagement.repository.LegalInstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Import en masse d'un corpus Markdown deja collecte et converti sur disque.
 *
 * <p>Le collecteur JORT ({@code JortIngestionJob}) ne couvre que les parutions nouvelles.
 * Le corpus historique, lui, est constitue hors application puis converti en Markdown ; il
 * faut donc une porte d'entree pour l'injecter une fois. C'est le role de cette classe.</p>
 *
 * <p><b>Traitement par lots, pas fonctionnalite applicative.</b> Desactive par defaut et
 * garde par {@code forsalaw.rag.corpus-import.enabled}. Une fois le corpus parcouru, le
 * processus s'arrete : cette classe n'a de sens que lancee expressement, jamais dans une
 * instance qui sert du trafic.</p>
 *
 * <p><b>Reprise apres interruption.</b> Un import complet se compte en heures sur processeur.
 * La reference de source est le chemin du fichier relatif a la racine, et chaque fichier deja
 * present en base est saute : relancer la commande apres une coupure reprend ou l'on s'etait
 * arrete, sans doublon. Ce controle est fait ICI et non dans le service :
 * {@code ingererTexte} ne verifie pas les doublons (seul {@code ingererPdf} le fait), donc
 * s'en remettre a lui doublerait chaque chunk a la seconde execution.</p>
 */
@Component
@ConditionalOnProperty(name = "forsalaw.rag.corpus-import.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CorpusImportRunner implements ApplicationRunner {

    private final LegalDocumentIngestionService ingestionService;
    private final LegalDocumentChunkRepository chunkRepository;
    private final LegalInstrumentRepository instrumentRepository;
    private final ApplicationContext contexte;

    @Value("${forsalaw.rag.corpus-import.directory}")
    private String racine;

    @Value("${forsalaw.rag.corpus-import.code-name}")
    private String codeName;

    @Value("${forsalaw.rag.corpus-import.tier:1}")
    private int tier;

    /** 0 = tout le repertoire. Utile pour valider la chaine sur quelques fichiers d'abord. */
    @Value("${forsalaw.rag.corpus-import.limit:0}")
    private int limite;

    /**
     * Date d'effet par defaut, utilisee seulement quand le fichier n'a pas de champ
     * {@code posted} propre (jurisite). Laissee vide par defaut : mieux vaut une date absente
     * qu'une date inventee, cette colonne pilotant desormais aussi le classement temporel des
     * resultats de recherche.
     */
    @Value("${forsalaw.rag.corpus-import.effective-date:}")
    private String dateEffet;

    @Override
    public void run(ApplicationArguments args) {
        try {
            importer();
        } finally {
            // Traitement par lots : sans cet arret, la JVM resterait vivante a servir du
            // trafic HTTP alors que l'operateur a lance une commande ponctuelle.
            System.exit(SpringApplication.exit(contexte, () -> 0));
        }
    }

    private void importer() {
        Path racineChemin = Path.of(racine);
        if (!Files.isDirectory(racineChemin)) {
            log.error("Import de corpus : « {} » n'est pas un repertoire. Rien a faire.", racine);
            return;
        }

        List<Path> fichiers;
        try (Stream<Path> flux = Files.walk(racineChemin)) {
            Stream<Path> tries = flux
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    // Ordre stable : une reprise apres coupure reparcourt la meme sequence,
                    // ce qui rend la progression journalisee comparable d'une execution a l'autre.
                    .sorted(Comparator.comparing(Path::toString));
            fichiers = (limite > 0 ? tries.limit(limite) : tries).toList();
        } catch (IOException e) {
            log.error("Import de corpus : parcours de « {} » impossible.", racine, e);
            return;
        }

        LocalDate effetParDefaut = dateEffet == null || dateEffet.isBlank() ? null : LocalDate.parse(dateEffet);

        log.info("Import de corpus : {} fichier(s) sous « {} », code « {} », niveau {}.",
                fichiers.size(), racine, codeName, tier);

        Compteurs c = new Compteurs();
        Instant debut = Instant.now();

        for (int i = 0; i < fichiers.size(); i++) {
            traiter(fichiers.get(i), racineChemin, effetParDefaut, c);
            if ((i + 1) % 25 == 0 || i + 1 == fichiers.size()) {
                journaliserProgression(i + 1, fichiers.size(), debut, c);
            }
        }

        log.info("Import de corpus termine en {} : {} fichier(s) ingere(s), {} deja present(s), "
                        + "{} sans chunk, {} en echec, {} chunk(s) au total.",
                humaniser(Duration.between(debut, Instant.now())),
                c.ingeres, c.deja, c.vides, c.echecs, c.chunks);
    }

    private void traiter(Path fichier, Path racineChemin, LocalDate effetParDefaut, Compteurs c) {
        // Chemin relatif normalise en separateurs « / » : la reference doit etre identique
        // sous Windows et sous Linux, sinon une reprise sur une autre machine reingererait tout.
        String reference = racineChemin.relativize(fichier).toString().replace('\\', '/');

        try {
            if (chunkRepository.compterParSource(reference) > 0) {
                c.deja++;
                return;
            }

            String brut = Files.readString(fichier, StandardCharsets.UTF_8);
            EnTete enTete = EnTete.lire(brut);
            String texte = enTete.corps();
            if (texte.isBlank()) {
                c.vides++;
                return;
            }

            // Le code reel (« Constitution_2014 », « ccl »...) vient de l'en-tete quand il y
            // est : sans cela, les 1 836 fichiers de Jurisite porteraient tous le meme
            // code_name, et le rapprochement (code_name, article_reference) sur lequel
            // repose la detection d'abrogation confondrait des articles 1 sans rapport.
            //
            // A defaut de « code: », un repli sur « id: » (namespace par le code CLI) :
            // legislation-securite (DCAF) n'a PAS de champ « code », et sans ce repli ses
            // 5 528 documents autonomes s'empileraient tous sous le meme code_name generique
            // -- la meme classe de bug, juste sur une autre source.
            String code = enTete.code() != null ? enTete.code()
                    : enTete.id() != null ? codeName + ":" + enTete.id()
                    : codeName;

            // Date de PUBLICATION propre au fichier (« posted », DCAF/cassation/bct) quand
            // elle existe : un parametre CLI unique pour tout un repertoire de 5 528 documents
            // publies a des dates differentes leur donnerait a tous la MEME date d'effet.
            LocalDate effet = enTete.posted() != null ? enTete.posted() : effetParDefaut;

            var resultat = ingestionService.ingererTexte(texte,
                    new LegalDocumentIngestionService.DemandeIngestion(
                            code, reference, tier, null, effet, enTete.title()));

            if (resultat.chunksCrees() == 0) {
                c.vides++;
            } else {
                c.ingeres++;
                c.chunks += resultat.chunksCrees();
            }

            // legislation-securite (DCAF) publie un statut par document (en vigueur / abroge /
            // n'est plus en vigueur) : une source publisher-maintained de statut d'instrument,
            // qu'il vaut mieux reprendre que re-deviner par regex sur un texte qu'on n'a pas
            // ecrit (V12). N'ecrit rien si le fichier n'a pas de champ « statut ».
            if (!enTete.statut().isEmpty()) {
                instrumentRepository.importerDepuisDcaf(
                        code, enTete.title(), effet, DcafStatutNormalizer.normaliser(enTete.statut()));
            }

        } catch (IOException | RuntimeException e) {
            // Un fichier en echec ne doit pas interrompre un traitement de plusieurs heures.
            // Il reste absent de la base, donc une relance le retentera de lui-meme.
            c.echecs++;
            log.warn("Import de corpus : « {} » ignore ({}).", reference, e.toString());
        }
    }

    private void journaliserProgression(int faits, int total, Instant debut, Compteurs c) {
        Duration ecoule = Duration.between(debut, Instant.now());
        double parSeconde = ecoule.toMillis() > 0 ? faits * 1000.0 / ecoule.toMillis() : 0;
        String reste = parSeconde > 0
                ? humaniser(Duration.ofSeconds((long) ((total - faits) / parSeconde)))
                : "inconnu";

        log.info("Import de corpus : {}/{} fichiers ({} chunks, {} echecs) — {} ecoule, reste ~{}.",
                faits, total, c.chunks, c.echecs, humaniser(ecoule), reste);
    }

    private String humaniser(Duration d) {
        long h = d.toHours();
        long min = d.toMinutesPart();
        return h > 0 ? h + " h " + min + " min" : min + " min " + d.toSecondsPart() + " s";
    }

    /**
     * En-tete YAML place en tete des fichiers Markdown par {@code scripts/convert-corpus.py}.
     *
     * <p><b>Il doit etre retire avant vectorisation.</b> Laisse en place, ce bloc
     * (« source », « url », « converted_at »...) part dans le vecteur au meme titre que le
     * texte de loi : sur un article court il pese davantage que l'article lui-meme, et il est
     * quasi identique d'un fichier a l'autre, ce qui rapproche artificiellement des articles
     * sans rapport. Constate a l'ingestion : les premiers chunks commencaient tous par
     * {@code --- source: "jurisite" tier: 1 ...}.</p>
     *
     * <p>Analyse deliberement minimale : on ne cherche que les cles utiles, sans dependance
     * YAML. Un en-tete absent ou malforme rend le fichier entier comme corps, ce qui degrade
     * la qualite mais n'interrompt pas un import de plusieurs heures.</p>
     */
    record EnTete(String corps, String code, String id, String title, LocalDate posted, List<String> statut) {

        private static final String DELIMITEUR = "---";
        private static final Pattern CODE = Pattern.compile("(?m)^code:\\s*\"?([^\"\\r\\n]+?)\"?\\s*$");
        private static final Pattern ID = Pattern.compile("(?m)^id:\\s*\"?([^\"\\r\\n]+?)\"?\\s*$");
        private static final Pattern TITLE = Pattern.compile("(?m)^title:\\s*\"([^\"\\r\\n]*)\"\\s*$");
        private static final Pattern POSTED = Pattern.compile("(?m)^posted:\\s*\"?(\\d{4}-\\d{2}-\\d{2})\"?\\s*$");
        private static final Pattern STATUT_LIGNE = Pattern.compile("(?m)^statut:\\s*(\\[.*])\\s*$");
        private static final Pattern STATUT_VALEUR = Pattern.compile("\"([^\"]*)\"");

        static EnTete lire(String contenu) {
            String normalise = contenu.stripLeading();
            if (!normalise.startsWith(DELIMITEUR)) {
                return new EnTete(contenu, null, null, null, null, List.of());
            }
            // Fin du bloc : le « --- » suivant, en debut de ligne.
            int fin = normalise.indexOf("\n" + DELIMITEUR, DELIMITEUR.length());
            if (fin < 0) {
                return new EnTete(contenu, null, null, null, null, List.of());
            }

            String bloc = normalise.substring(DELIMITEUR.length(), fin);
            String corps = normalise.substring(fin + 1 + DELIMITEUR.length()).stripLeading();

            String code = extraireGroupe(CODE, bloc);
            String id = extraireGroupe(ID, bloc);
            String title = extraireGroupe(TITLE, bloc);
            LocalDate posted = null;
            String postedTexte = extraireGroupe(POSTED, bloc);
            if (postedTexte != null) {
                try {
                    posted = LocalDate.parse(postedTexte);
                } catch (RuntimeException ignored) {
                    // Date malformee : laissee absente plutot qu'incorrecte.
                }
            }

            List<String> statut = new ArrayList<>();
            Matcher ligneStatut = STATUT_LIGNE.matcher(bloc);
            if (ligneStatut.find()) {
                Matcher valeurs = STATUT_VALEUR.matcher(ligneStatut.group(1));
                while (valeurs.find()) {
                    statut.add(valeurs.group(1));
                }
            }

            return new EnTete(corps, code, id, title, posted, statut);
        }

        private static String extraireGroupe(Pattern motif, String bloc) {
            Matcher m = motif.matcher(bloc);
            return m.find() ? m.group(1).trim() : null;
        }
    }

    private static final class Compteurs {
        int ingeres;
        int deja;
        int vides;
        int echecs;
        int chunks;
    }
}
