package com.forsalaw.ragManagement.ingestion;

import com.forsalaw.ragManagement.embedding.EmbeddingClient;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingestion d'un document juridique : PDF -> texte -> chunks par article -> vecteurs -> base.
 *
 * <p>Les trois niveaux du corpus passent par ce meme service ; seules les metadonnees
 * changent (niveau, cabinet proprietaire, date d'effet).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegalDocumentIngestionService {

    /** 1 = legislation (JORT), 2 = jurisprudence (CEJJ), 3 = coffre prive de cabinet. */
    public static final int TIER_LEGISLATION = 1;
    public static final int TIER_JURISPRUDENCE = 2;
    public static final int TIER_COFFRE_PRIVE = 3;

    private final PdfTextExtractor pdfTextExtractor;
    private final LegalArticleChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final LegalDocumentChunkRepository chunkRepository;
    private final SupersedeProposalService supersedeProposalService;

    /** Parametres d'une ingestion. */
    public record DemandeIngestion(
            String codeName,
            String sourceReference,
            int tier,
            String tenantId,
            LocalDate effectiveDate,
            String articleTitle
    ) {}

    /** Resultat, pour journalisation et retour d'API. */
    public record ResultatIngestion(
            String sourceReference,
            int chunksCrees,
            int propositionsAbrogation
    ) {}

    /**
     * Ingere un PDF a couche texte.
     *
     * @throws ScannedPdfException si le document est un scan sans texte exploitable
     */
    public ResultatIngestion ingererPdf(InputStream pdf, DemandeIngestion demande) throws IOException {
        validerDemande(demande);

        if (chunkRepository.compterParSource(demande.sourceReference()) > 0) {
            log.info("Source « {} » deja ingeree : ingestion ignoree.", demande.sourceReference());
            return new ResultatIngestion(demande.sourceReference(), 0, 0);
        }

        String texte = pdfTextExtractor.extraire(pdf, demande.sourceReference());
        return ingererTexte(texte, demande);
    }

    /** Variante texte : utilisee par le scraper JORT, qui recupere deja du texte. */
    public ResultatIngestion ingererTexte(String texte, DemandeIngestion demande) {
        validerDemande(demande);

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(texte);
        if (chunks.isEmpty()) {
            log.warn("Aucun chunk produit pour « {} » : rien a vectoriser.", demande.sourceReference());
            return new ResultatIngestion(demande.sourceReference(), 0, 0);
        }

        List<String> contenus = chunks.stream().map(LegalArticleChunker.Chunk::contenu).toList();
        List<float[]> vecteurs = embeddingClient.embed(contenus);

        List<LegalDocumentChunkRepository.NouveauChunk> aInserer = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            LegalArticleChunker.Chunk chunk = chunks.get(i);
            aInserer.add(new LegalDocumentChunkRepository.NouveauChunk(
                    demande.codeName(),
                    chunk.articleReference() != null ? chunk.articleReference() : "N/A",
                    demande.articleTitle(),
                    chunk.contenu(),
                    vecteurs.get(i),
                    demande.tier(),
                    demande.tenantId(),
                    demande.effectiveDate(),
                    demande.sourceReference()
            ));
        }

        int inseres = chunkRepository.insererLot(aInserer);

        // Detection d'abrogation : PROPOSE seulement. Voir SupersedeProposalService.
        int propositions = supersedeProposalService.detecterEtProposer(
                texte, demande.codeName(), demande.effectiveDate(), demande.sourceReference());

        log.info("Ingestion « {} » (niveau {}) : {} chunk(s) inseres, {} proposition(s) d'abrogation.",
                demande.sourceReference(), demande.tier(), inseres, propositions);

        return new ResultatIngestion(demande.sourceReference(), inseres, propositions);
    }

    /**
     * Visibilite de paquetage (et non privee) : {@code AdminRagIngestionService} l'appelle
     * explicitement AVANT d'archiver le PDF dans le stockage objet, pour rejeter un couple
     * tier/tenantId invalide sans effet de bord ecrit. L'appeler ici aussi reste necessaire
     * pour {@link #ingererTexte}, qui n'a pas cette etape d'archivage en amont.
     */
    void validerDemande(DemandeIngestion demande) {
        if (demande.codeName() == null || demande.codeName().isBlank()) {
            throw new IllegalArgumentException("codeName est obligatoire.");
        }
        if (demande.sourceReference() == null || demande.sourceReference().isBlank()) {
            throw new IllegalArgumentException("sourceReference est obligatoire (tracabilite de la source).");
        }
        if (demande.tier() < TIER_LEGISLATION || demande.tier() > TIER_COFFRE_PRIVE) {
            throw new IllegalArgumentException("tier doit valoir 1, 2 ou 3.");
        }
        // Refus symetrique de la contrainte CHECK posee en V11 : un contenu de niveau 3 sans
        // cabinet proprietaire serait visible de tous les cabinets.
        if (demande.tier() == TIER_COFFRE_PRIVE && (demande.tenantId() == null || demande.tenantId().isBlank())) {
            throw new IllegalArgumentException(
                    "Un document de niveau 3 exige un tenantId : sans lui, il ne serait cloisonne dans aucun cabinet.");
        }
        if (demande.tier() != TIER_COFFRE_PRIVE && demande.tenantId() != null) {
            throw new IllegalArgumentException(
                    "tenantId n'a de sens que pour le niveau 3 (coffre prive).");
        }
    }
}
