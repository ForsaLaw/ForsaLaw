package com.forsalaw.messengerManagement.service;

import com.forsalaw.documentManagement.entity.ContexteDocument;
import com.forsalaw.documentManagement.model.DocumentMetadataDTO;
import com.forsalaw.documentManagement.service.DocumentService;
import com.forsalaw.messengerManagement.entity.AttachmentScanStatus;
import com.forsalaw.messengerManagement.entity.MessengerAttachment;
import com.forsalaw.messengerManagement.entity.MessengerMessage;
import com.forsalaw.messengerManagement.model.MessengerAttachmentDTO;
import com.forsalaw.messengerManagement.repository.MessengerAttachmentRepository;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessengerAttachmentService {

    private final MessengerAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ClamAvScanService clamAvScanService;
    private final MessengerAttachmentFileValidator fileValidator;
    private final MessengerAttachmentDownloadTokenService downloadTokenService;
    private final DocumentService documentService;

    @Value("${forsalaw.messenger.attachments.max-size-bytes:10485760}")
    private long maxSizeBytes;

    @Value("${forsalaw.messenger.attachments.max-files-per-message:5}")
    private int maxFilesPerMessage;

    @Value("${forsalaw.messenger.attachments.clamav.enabled:false}")
    private boolean clamAvEnabled;

    @Value("${forsalaw.server.public-url:http://localhost:8081}")
    private String publicApiBaseUrl;

    public List<MessengerAttachment> saveAttachmentsForMessage(MessengerMessage message, MultipartFile[] files, String senderEmail) throws IOException {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Au moins un fichier est requis.");
        }
        if (files.length > maxFilesPerMessage) {
            throw new IllegalArgumentException("Nombre maximum de fichiers par message: " + maxFilesPerMessage + ".");
        }

        List<MessengerAttachment> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Fichier vide.");
            }
            if (file.getSize() > maxSizeBytes) {
                throw new IllegalArgumentException("Fichier trop volumineux (max " + (maxSizeBytes / 1024 / 1024) + " Mo).");
            }
            String origName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String ext = fileValidator.extensionOf(origName);
            if (!fileValidator.isAllowedExtension(ext)) {
                throw new IllegalArgumentException("Extension non autorisee: " + ext + ". Autorise: pdf, png, jpg, jpeg, gif, webp.");
            }
            byte[] head = fileValidator.readHead(file, 16);
            if (!fileValidator.magicBytesMatch(ext, head)) {
                throw new IllegalArgumentException("Le contenu du fichier ne correspond pas a l'extension declaree.");
            }
            byte[] full = file.getBytes();
            AttachmentScanStatus scanStatus;
            if (!clamAvEnabled) {
                scanStatus = AttachmentScanStatus.SKIPPED;
            } else {
                boolean clean = clamAvScanService.scanBytes(full);
                scanStatus = clean ? AttachmentScanStatus.CLEAN : AttachmentScanStatus.INFECTED;
                if (!clean) {
                    throw new IllegalArgumentException("Fichier refuse par l'analyse antivirus.");
                }
            }

            // Depôt dans le Coffre-fort Numérique (génère SHA-256 et gère le stockage)
            DocumentMetadataDTO docDto = documentService.uploadDocument(
                    senderEmail, 
                    file, 
                    ContexteDocument.MESSENGER, 
                    message.getId(), 
                    null
            );

            MessengerAttachment att = new MessengerAttachment();
            att.setId(userService.generateNextId("MAT"));
            att.setMessage(message);
            att.setOriginalFilename(origName);
            att.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            att.setSizeBytes(full.length);
            att.setStorageKey(docDto.getId()); // On stocke l'ID du document du coffre-fort !
            att.setScanStatus(scanStatus);
            saved.add(attachmentRepository.save(att));
        }
        return saved;
    }

    public Resource loadFile(MessengerAttachment attachment) throws IOException {
        // Redirection vers le coffre-fort numérique (la clé de stockage est l'ID du document)
        return documentService.telechargerDocumentSysteme(attachment.getStorageKey());
    }

    /**
     * Verifie que l'email dans le JWT correspond a un participant de la conversation ou un admin.
     */
    public void assertViewerCanDownload(MessengerAttachment attachment, String viewerEmail) {
        User viewer = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces refuse."));
        if (viewer.getRoleUser() == RoleUser.admin) {
            return;
        }
        String clientEmail = attachment.getMessage().getConversation().getClient().getEmail();
        String avocatEmail = attachment.getMessage().getConversation().getAvocat().getUser().getEmail();
        if (viewerEmail.equalsIgnoreCase(clientEmail) || viewerEmail.equalsIgnoreCase(avocatEmail)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces refuse.");
    }

    public void assertSafeToServe(MessengerAttachment attachment) {
        AttachmentScanStatus s = attachment.getScanStatus();
        if (s != AttachmentScanStatus.CLEAN && s != AttachmentScanStatus.SKIPPED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier indisponible.");
        }
    }

    public MessengerAttachmentDTO toDto(MessengerAttachment a, String viewerEmail) {
        String token = downloadTokenService.generateDownloadToken(a.getId(), viewerEmail);
        String url = publicApiBaseUrl.replaceAll("/$", "")
                + "/api/messenger/attachments/download?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return new MessengerAttachmentDTO(
                a.getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getScanStatus(),
                url
        );
    }
}
