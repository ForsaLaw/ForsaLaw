package com.forsalaw.reclamationManagement.service;

import com.forsalaw.reclamationManagement.entity.Reclamation;
import com.forsalaw.reclamationManagement.entity.ReclamationAttachment;
import com.forsalaw.reclamationManagement.repository.ReclamationAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.forsalaw.documentManagement.entity.ContexteDocument;
import com.forsalaw.documentManagement.model.DocumentMetadataDTO;
import com.forsalaw.documentManagement.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final ReclamationAttachmentRepository attachmentRepository;
    private final ReclamationService reclamationService;
    private final DocumentService documentService;

    @Transactional
    public ReclamationAttachment stockerFichier(MultipartFile fichier, String reclamationId, String email) throws IOException {
        Reclamation r = reclamationService.verifierAccesEtObtenirReclamation(email, reclamationId);

        // Depôt dans le Coffre-fort Numérique (génère SHA-256 et gère le stockage central)
        DocumentMetadataDTO docDto = documentService.uploadDocument(
                email, 
                fichier, 
                ContexteDocument.RECLAMATION, 
                reclamationId, 
                null
        );

        String nomOriginal = fichier.getOriginalFilename();

        ReclamationAttachment pieces = new ReclamationAttachment();
        pieces.setReclamation(r);
        pieces.setNomFichier(nomOriginal);
        pieces.setCheminFichier(docDto.getId()); // On stocke l'ID inter-système du DocumentVault
        pieces.setTypeContenu(fichier.getContentType());
        pieces.setTailleFichier(fichier.getSize());
        
        return attachmentRepository.save(pieces);
    }
    
    public Resource getFichier(String chemin) {
        // "chemin" correspond à l'ID du document dans le coffre-fort
        return documentService.telechargerDocumentSysteme(chemin);
    }
}
