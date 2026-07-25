package com.forsalaw.documentManagement.service;

import com.forsalaw.documentManagement.entity.ActionDocument;
import com.forsalaw.documentManagement.entity.DocumentAccessLog;
import com.forsalaw.documentManagement.entity.DocumentMetadata;
import com.forsalaw.documentManagement.model.DocumentMetadataDTO;
import com.forsalaw.documentManagement.repository.DocumentAccessLogRepository;
import com.forsalaw.documentManagement.repository.DocumentMetadataRepository;
import com.forsalaw.storage.S3StorageService;
import com.forsalaw.storage.StorageException;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.UserService;
import com.forsalaw.util.HashingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureService {

    private final DocumentMetadataRepository documentRepository;
    private final DocumentAccessLogRepository accessLogRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final HashingService hashingService;
    private final DocumentService documentService;
    private final S3StorageService storageService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional
    public DocumentMetadataDTO signerDocument(String documentId, String emailUser) {
        DocumentMetadata document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable."));

        if (document.isEstSigne()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce document est déjà signé.");
        }

        if (!"application/pdf".equals(document.getTypeContenu())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seuls les documents PDF peuvent être signés électroniquement.");
        }

        User user = userRepository.findByEmail(emailUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable."));

        String ancienneCle = document.getCheminFichier();
        if (!storageService.exists(ancienneCle)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fichier physique introuvable.");
        }

        // PDFBox travaille sur des File : on fait donc un aller-retour par des fichiers temporaires,
        // supprimes dans le finally meme en cas d'erreur (sinon on remplit /tmp du serveur).
        Path pdfSource = null;
        Path pdfSigne = null;
        try {
            String newFileName = document.getNomStockage().replace(".pdf", "_signe.pdf");
            String nouvelleCle = S3StorageService.DOCUMENTS_PREFIX + newFileName;

            pdfSource = Files.createTempFile("forsalaw-sign-src-", ".pdf");
            pdfSigne = Files.createTempFile("forsalaw-sign-out-", ".pdf");

            try (InputStream source = storageService.openStream(ancienneCle)) {
                Files.copy(source, pdfSource, StandardCopyOption.REPLACE_EXISTING);
            }

            apposerSceau(pdfSource.toFile(), pdfSigne.toFile(), user);

            // Recalculer le hash et enregistrer
            String nouveauHash;
            try (InputStream signe = Files.newInputStream(pdfSigne)) {
                nouveauHash = hashingService.calculerHashSha256(signe);
            }
            long tailleSignee = Files.size(pdfSigne);

            try (InputStream signe = Files.newInputStream(pdfSigne)) {
                storageService.upload(nouvelleCle, signe, tailleSignee, "application/pdf");
            }

            document.setCheminFichier(nouvelleCle);
            document.setNomStockage(newFileName);
            document.setTailleFichier(tailleSignee);
            document.setNomOriginal(document.getNomOriginal().replace(".pdf", "_signe.pdf"));

            document.setEstSigne(true);
            document.setDateSignature(LocalDateTime.now());
            document.setSignataireEmail(user.getEmail());
            document.setHashApresSignature(nouveauHash);

            documentRepository.save(document);

            // Supprimer l'ancien objet (on ne garde pas d'archive, pour économiser de la place)
            storageService.delete(ancienneCle);

            logAudit(document, user, ActionDocument.SIGNATURE, "Signature électronique apposée avec succès.");

            log.info("Document {} signé avec succès par {}", documentId, user.getEmail());

            return documentService.getDocumentDTO(documentId);

        } catch (Exception e) {
            log.error("Erreur lors de la signature du document PDF:", e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            if (e.getCause() != null) errorMsg += " | Cause: " + e.getCause().getMessage();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur Signature PDF [V2] : " + errorMsg);
        } finally {
            supprimerTemporaire(pdfSource);
            supprimerTemporaire(pdfSigne);
        }
    }

    private void supprimerTemporaire(Path chemin) {
        if (chemin == null) {
            return;
        }
        try {
            Files.deleteIfExists(chemin);
        } catch (IOException e) {
            log.warn("Fichier temporaire de signature non supprime : {}", chemin, e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verifierSignature(String documentId) {
        DocumentMetadata document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable."));

        if (!document.isEstSigne()) {
            return Map.of(
                    "estSigne", false,
                    "message", "Le document ne contient pas de signature électronique ForsaLaw."
            );
        }

        try {
            String currentHash;
            try (InputStream contenu = storageService.openStream(document.getCheminFichier())) {
                currentHash = hashingService.calculerHashSha256(contenu);
            }
            boolean isIntact = currentHash.equals(document.getHashApresSignature());

            return Map.of(
                    "estSigne", true,
                    "valide", isIntact,
                    "signataire", document.getSignataireEmail(),
                    "dateSignature", document.getDateSignature(),
                    "hashAttendu", document.getHashApresSignature(),
                    "hashActuel", currentHash
            );
        } catch (StorageException.ObjectNotFound e) {
            return Map.of("estSigne", document.isEstSigne(), "valide", false, "message", "Fichier introuvable.");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de la lecture du fichier pour vérification.");
        }
    }

    private void apposerSceau(File inFile, File outFile, User signataire) throws IOException {
        try (PDDocument document = Loader.loadPDF(inFile)) {
            // Prendre la dernière page
            PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, lastPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
                
                String signatureText1 = "Sceau électronique ForsaLaw";
                String signatureText2 = String.format("Signé par : %s %s (%s)", signataire.getPrenom(), signataire.getNom(), signataire.getEmail());
                String signatureText3 = String.format("Date : %s", LocalDateTime.now().format(DATE_FMT));

                // Position en bas à gauche
                float startX = 50;
                float startY = 50;

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                contentStream.newLineAtOffset(startX, startY + 30);
                contentStream.showText(signatureText1);
                
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                contentStream.newLineAtOffset(0, -12);
                contentStream.showText(signatureText2);
                
                contentStream.newLineAtOffset(0, -12);
                contentStream.showText(signatureText3);
                
                contentStream.endText();
            }
            
            document.save(outFile);
        }
    }

    private void logAudit(DocumentMetadata document, User acteur, ActionDocument action, String details) {
        DocumentAccessLog logEntry = new DocumentAccessLog();
        logEntry.setId(userService.generateNextId("DAL"));
        logEntry.setDocument(document);
        logEntry.setActeur(acteur);
        logEntry.setAction(action);
        logEntry.setDetails(details);
        accessLogRepository.save(logEntry);
    }
}
