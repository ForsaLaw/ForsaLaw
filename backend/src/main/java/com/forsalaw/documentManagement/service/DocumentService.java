package com.forsalaw.documentManagement.service;

import com.forsalaw.documentManagement.entity.*;
import com.forsalaw.documentManagement.model.*;
import com.forsalaw.documentManagement.repository.DocumentAccessLogRepository;
import com.forsalaw.documentManagement.repository.DocumentMetadataRepository;
import com.forsalaw.messengerManagement.service.ClamAvScanService;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.IdSequenceService;
import com.forsalaw.util.HashingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    @Value("${forsalaw.documents.dir:uploads/documents}")
    private String dossierDocuments;

    private final DocumentMetadataRepository documentRepository;
    private final DocumentAccessLogRepository accessLogRepository;
    private final UserRepository userRepository;
    private final IdSequenceService idSequenceService;
    private final HashingService hashingService;
    private final DocumentFileValidator fileValidator;
    private final ClamAvScanService clamAvScanService;

    // ─── Upload ──────────────────────────────────────────────────────────────

    @Transactional
    public DocumentMetadataDTO uploadDocument(
            String emailDeposeur,
            MultipartFile fichier,
            ContexteDocument contexteType,
            String contexteId,
            HttpServletRequest servletRequest
    ) throws IOException {
        User deposeur = requireUser(emailDeposeur);

        // 1. Préparer le dossier de stockage
        Path emplacement = Paths.get(dossierDocuments).toAbsolutePath().normalize();
        Files.createDirectories(emplacement);

        // 2. Valider le fichier : extension sur liste blanche + vrai type MIME (Tika, anti-spoof).
        //    Rejette par ex. un .jsp renommé .pdf. Retourne l'extension validée (ex. ".pdf").
        String nomOriginal = fichier.getOriginalFilename();
        String extension = fileValidator.validateAndResolveExtension(fichier);

        // 3. Analyse antivirus (ClamAV). Activé et injoignable => scanBytes renvoie false => rejet (fail-closed).
        if (!clamAvScanService.scanBytes(fichier.getBytes())) {
            throw new DocumentFileValidator.InvalidFileException(
                    "Le fichier a été rejeté par l'analyse antivirus ou l'analyse est indisponible.");
        }

        // 4. Nom de stockage unique basé UNIQUEMENT sur l'extension validée (nom d'origine ignoré).
        String nomStockage = UUID.randomUUID().toString() + extension;
        Path cibleFichier = emplacement.resolve(nomStockage);

        // 5. Calculer le hash SHA-256 AVANT de stocker (pour garantir l'intégrité)
        String hashSha256 = hashingService.calculerHashSha256(fichier.getInputStream());

        // 6. Copier le fichier sur le disque
        Files.copy(fichier.getInputStream(), cibleFichier, StandardCopyOption.REPLACE_EXISTING);

        // 7. Enregistrer les métadonnées en base de données
        DocumentMetadata doc = new DocumentMetadata();
        doc.setId(idSequenceService.generateNextId("DOC"));
        doc.setDeposeur(deposeur);
        doc.setNomOriginal(nomOriginal);
        doc.setNomStockage(nomStockage);
        doc.setCheminFichier(cibleFichier.toString());
        doc.setTypeContenu(fichier.getContentType());
        doc.setTailleFichier(fichier.getSize());
        doc.setHashSha256(hashSha256);
        doc.setContexteType(contexteType != null ? contexteType : ContexteDocument.GENERAL);
        doc.setContexteId(contexteId);
        doc.setSupprime(false);
        doc = documentRepository.save(doc);

        // 8. Tracer l'action d'upload dans le journal d'audit
        enregistrerLog(doc, deposeur, ActionDocument.UPLOAD, servletRequest, null, null);

        return toDTO(doc);
    }

    // ─── Téléchargement ──────────────────────────────────────────────────────

    @Transactional
    public Resource telechargerDocument(String emailActeur, String documentId, HttpServletRequest servletRequest) {
        DocumentMetadata doc = requireDocument(documentId);
        User acteur = requireUser(emailActeur);

        // Vérification des droits : seul le déposeur ou un admin peut télécharger
        verifierDroitsAcces(acteur, doc);

        // Tracer le téléchargement
        enregistrerLog(doc, acteur, ActionDocument.TELECHARGEMENT, servletRequest, null, null);

        try {
            Path cheminFichier = Paths.get(doc.getCheminFichier());
            Resource resource = new UrlResource(cheminFichier.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Le fichier est introuvable sur le serveur : " + doc.getNomOriginal());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Chemin de fichier invalide.", e);
        }
    }

    @Transactional(readOnly = true)
    public Resource telechargerDocumentSysteme(String documentId) {
        DocumentMetadata doc = requireDocument(documentId);
        try {
            Path cheminFichier = Paths.get(doc.getCheminFichier());
            Resource resource = new UrlResource(cheminFichier.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Le fichier est introuvable sur le serveur : " + doc.getNomOriginal());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Chemin de fichier invalide.", e);
        }
    }

    // ─── Vérification d'intégrité ────────────────────────────────────────────

    @Transactional
    public VerificationIntegriteDTO verifierIntegrite(String emailActeur, String documentId, HttpServletRequest servletRequest) throws IOException {
        DocumentMetadata doc = requireDocument(documentId);
        User acteur = requireUser(emailActeur);
        verifierDroitsAcces(acteur, doc);

        Path cheminFichier = Paths.get(doc.getCheminFichier());
        String hashActuel = hashingService.calculerHashSha256(Files.newInputStream(cheminFichier));
        boolean integre = hashActuel.equals(doc.getHashSha256());

        String details = integre
                ? "Document intact depuis le dépôt initial."
                : "ALERTE : Le document a été modifié depuis son dépôt !";

        // Tracer la vérification avec son résultat
        enregistrerLog(doc, acteur, ActionDocument.VERIFICATION_INTEGRITE, servletRequest, integre, details);

        return new VerificationIntegriteDTO(hashActuel, doc.getHashSha256(), integre, doc.getId(), doc.getNomOriginal());
    }

    // ─── Consultation ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DocumentMetadataDTO> mesDocuments(String email, Pageable pageable) {
        User user = requireUser(email);
        return documentRepository
                .findByDeposeur_IdAndSupprime(user.getId(), false, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<DocumentMetadataDTO> documentsParContexte(ContexteDocument contexteType, String contexteId) {
        return documentRepository
                .findByContexteTypeAndContexteIdAndSupprime(contexteType, contexteId, false)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<DocumentAccessLogDTO> historiqueDocument(String emailActeur, String documentId, Pageable pageable) {
        DocumentMetadata doc = requireDocument(documentId);
        User acteur = requireUser(emailActeur);
        verifierDroitsAcces(acteur, doc);
        return accessLogRepository.findByDocument_IdOrderByDateActionDesc(documentId, pageable).map(this::toLogDTO);
    }

    @Transactional(readOnly = true)
    public DocumentMetadataDTO getDocumentDTO(String documentId) {
        return toDTO(requireDocument(documentId));
    }

    // ─── Public Vault Verification ────────────────────────────────────────────

    /**
     * Vérifie l'authenticité d'un document sans authentification.
     * Recalcule le SHA-256 du fichier soumis et le compare à la base ForsaLaw.
     * Retourne les informations de signature si le document est reconnu.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> verifierDocumentPublic(MultipartFile fichier) throws IOException {
        String hashSoumis = hashingService.calculerHashSha256(fichier.getInputStream());

        // Chercher d'abord par hash après signature (document signé), sinon hash original
        java.util.Optional<DocumentMetadata> found = documentRepository.findByHashApresSignature(hashSoumis);
        if (found.isEmpty()) {
            found = documentRepository.findByHashSha256(hashSoumis);
        }

        if (found.isEmpty()) {
            return java.util.Map.of(
                "authentique", false,
                "hashSoumis", hashSoumis,
                "message", "Ce document n'est pas reconnu dans le registre ForsaLaw. Il n'a peut-être pas été émis ou signé via cette plateforme."
            );
        }

        DocumentMetadata doc = found.get();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("authentique", true);
        result.put("hashSoumis", hashSoumis);
        result.put("documentId", doc.getId());
        result.put("nomOriginal", doc.getNomOriginal());
        result.put("dateDepot", doc.getDateCreation());
        result.put("estSigne", doc.isEstSigne());
        result.put("message", "Document authentifié dans le registre ForsaLaw.");

        if (doc.isEstSigne()) {
            result.put("signataire", doc.getSignataireEmail());
            result.put("dateSignature", doc.getDateSignature());
        }

        // Deposeur anonymisé pour la vie privée
        if (doc.getDeposeur() != null) {
            String nom = doc.getDeposeur().getNom();
            String prenom = doc.getDeposeur().getPrenom();
            // Masquer partiellement : "A. Martin"
            String initiale = (prenom != null && !prenom.isEmpty()) ? prenom.charAt(0) + "." : "";
            result.put("deposeur", initiale + " " + (nom != null ? nom : ""));
        }

        return result;
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DocumentMetadataDTO> tousLesDocuments(Pageable pageable) {
        return documentRepository.findBySupprime(false, pageable).map(this::toDTO);
    }

    @Transactional
    public void supprimerDocument(String emailAdmin, String documentId, HttpServletRequest servletRequest) {
        DocumentMetadata doc = requireDocument(documentId);
        User admin = requireUser(emailAdmin);
        doc.setSupprime(true);
        documentRepository.save(doc);
        enregistrerLog(doc, admin, ActionDocument.SUPPRESSION, servletRequest, null, "Suppression logique par admin.");
    }

    // ─── Helpers privés ───────────────────────────────────────────────────────

    private void enregistrerLog(DocumentMetadata doc, User acteur, ActionDocument action,
                                HttpServletRequest request, Boolean integriteValide, String details) {
        DocumentAccessLog log = new DocumentAccessLog();
        log.setId(idSequenceService.generateNextId("DAL"));
        log.setDocument(doc);
        log.setActeur(acteur);
        log.setAction(action);
        log.setAdresseIp(request != null ? request.getRemoteAddr() : "system");
        log.setIntegriteValide(integriteValide);
        log.setDetails(details);
        accessLogRepository.save(log);
    }

    private void verifierDroitsAcces(User acteur, DocumentMetadata doc) {
        boolean estAdmin = acteur.getRoleUser().name().equalsIgnoreCase("admin");
        boolean estDeposeur = doc.getDeposeur().getId().equals(acteur.getId());
        if (!estAdmin && !estDeposeur) {
            throw new AccessDeniedException("Accès refusé : vous n'êtes pas autorisé à accéder à ce document.");
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }

    private DocumentMetadata requireDocument(String documentId) {
        return documentRepository.findById(documentId)
                .filter(d -> !d.isSupprime())
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable ou supprimé."));
    }

    private DocumentMetadataDTO toDTO(DocumentMetadata doc) {
        User deposeur = doc.getDeposeur();
        return new DocumentMetadataDTO(
                doc.getId(),
                deposeur.getId(),
                deposeur.getNom() + " " + deposeur.getPrenom(),
                doc.getNomOriginal(),
                doc.getTypeContenu(),
                doc.getTailleFichier(),
                doc.getHashSha256(),
                doc.getContexteType(),
                doc.getContexteId(),
                doc.isEstSigne(),
                doc.getDateSignature(),
                doc.getSignataireEmail(),
                doc.getHashApresSignature(),
                doc.isSupprime(),
                doc.getDateCreation()
        );
    }

    private DocumentAccessLogDTO toLogDTO(DocumentAccessLog log) {
        User acteur = log.getActeur();
        return new DocumentAccessLogDTO(
                log.getId(),
                acteur != null ? acteur.getId() : null,
                acteur != null ? acteur.getNom() + " " + acteur.getPrenom() : "Système",
                log.getAction(),
                log.getAdresseIp(),
                log.getIntegriteValide(),
                log.getDetails(),
                log.getDateAction()
        );
    }
}
