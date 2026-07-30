package com.forsalaw.ragManagement.ingestion;

import com.forsalaw.documentManagement.service.DocumentFileValidator;
import com.forsalaw.ragManagement.ingestion.model.AdminRagIngestionResponse;
import com.forsalaw.ragManagement.repository.LegalDocumentChunkRepository;
import com.forsalaw.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Ingestion d'un PDF unique depose par un administrateur (par opposition a
 * {@code CorpusImportRunner}, qui traite un repertoire entier hors ligne).
 *
 * <p>N'implemente PAS le pipeline d'extraction/decoupage/vectorisation : celui-ci existe deja
 * dans {@link LegalDocumentIngestionService#ingererPdf}, partage avec le collecteur JORT. Ce
 * service n'ajoute que ce qu'un depot interactif exige en plus : validation reelle du contenu,
 * resolution de la reference source, deduplication AVANT tout travail couteux, et archivage de
 * l'original.</p>
 *
 * <p><b>Niveau 3 (coffre prive) : cloisonnement NON applique a l'ecriture.</b> Comme documente
 * sur {@code legal_document_chunk.tenant_id}, cette colonne ne protege rien par elle-meme —
 * aucune entite cabinet n'existe encore pour verifier qu'un administrateur a le droit d'ecrire
 * sous tel {@code tenantId}. Accepte deliberement pour l'instant : aucune recherche ne peut
 * aujourd'hui LIRE le niveau 3 (pas d'endpoint expose), le risque immediat est donc nul. A
 * corriger en meme temps que la creation d'une entite cabinet.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRagIngestionService {

    private final DocumentFileValidator documentFileValidator;
    private final LegalDocumentIngestionService ingestionService;
    private final LegalDocumentChunkRepository chunkRepository;
    private final S3StorageService s3StorageService;

    public AdminRagIngestionResponse ingererPdf(
            MultipartFile fichier, String codeName, int tier, String tenantId,
            LocalDate effectiveDate, String articleTitle, String sourceReferenceFourni,
            String deposePar) throws IOException {

        // Contenu reel, pas l'extension declaree : un .pdf renomme depuis un autre type ne
        // doit pas atteindre PDFBox. Reutilise le meme validateur que le coffre-fort
        // documentaire (Phase 8), qui accepte plusieurs extensions — ce point d'entree n'en
        // veut qu'une.
        String extension = documentFileValidator.validateAndResolveExtension(fichier);
        if (!".pdf".equals(extension)) {
            throw new IllegalArgumentException(
                    "Seuls les fichiers PDF sont acceptes par cet endpoint (recu : " + extension + ").");
        }

        byte[] octets = fichier.getBytes();

        // Reference fournie par l'administrateur (numero de JORT, identifiant de dossier...) —
        // ce qui apparaitra dans les citations — sinon deduite du contenu : un meme fichier
        // redepose est ainsi reconnu sans qu'un identifiant n'ait a etre invente.
        String sourceReference = (sourceReferenceFourni == null || sourceReferenceFourni.isBlank())
                ? "upload:sha256:" + sha256(octets)
                : sourceReferenceFourni.trim();

        // Court-circuite AVANT l'archivage S3 et l'ingestion : un doublon ne doit ni
        // reoccuper de stockage objet, ni revectoriser un contenu deja indexe.
        if (chunkRepository.compterParSource(sourceReference) > 0) {
            log.info("Ingestion admin : « {} » deja presente, depot ignore.", sourceReference);
            return new AdminRagIngestionResponse(sourceReference, codeName, tier, 0, 0, true);
        }

        // Archivage de l'original : une plateforme de preuve juridique doit pouvoir remonter
        // au document source, pas seulement aux chunks qui en derivent.
        String cleS3 = S3StorageService.RAG_SOURCES_PREFIX + cleSure(sourceReference) + ".pdf";
        s3StorageService.upload(cleS3, new ByteArrayInputStream(octets), octets.length, "application/pdf");

        var demande = new LegalDocumentIngestionService.DemandeIngestion(
                codeName, sourceReference, tier, tenantId, effectiveDate, articleTitle);
        var resultat = ingestionService.ingererPdf(new ByteArrayInputStream(octets), demande);

        log.warn("Ingestion admin : « {} » (niveau {}) deposee par {} -- {} chunk(s), "
                        + "{} proposition(s) d'abrogation, archivee sous {}.",
                sourceReference, tier, deposePar, resultat.chunksCrees(),
                resultat.propositionsAbrogation(), cleS3);

        return new AdminRagIngestionResponse(sourceReference, codeName, tier,
                resultat.chunksCrees(), resultat.propositionsAbrogation(), false);
    }

    /** Cle S3 lisible et stable : caracteres hors alphanumerique/-_./ remplaces. */
    private String cleSure(String sourceReference) {
        return sourceReference.replaceAll("[^A-Za-z0-9_./-]", "_");
    }

    private String sha256(byte[] octets) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(octets));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 fait partie de toute JVM standard : ne peut pas survenir en pratique.
            throw new IllegalStateException("SHA-256 indisponible.", e);
        }
    }
}
