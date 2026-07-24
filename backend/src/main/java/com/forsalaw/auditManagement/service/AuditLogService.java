package com.forsalaw.auditManagement.service;

import com.forsalaw.auditManagement.entity.AuditLog;
import com.forsalaw.auditManagement.model.AuditLogDTO;
import com.forsalaw.auditManagement.model.CreateAuditLogRequest;
import com.forsalaw.auditManagement.repository.AuditLogRepository;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.IdSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    /** Maximum length for the details payload stored in audit_log.details (TEXT column, safety guard). */
    private static final int DETAILS_MAX_LENGTH = 8_000;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final IdSequenceService idSequenceService;

    @Transactional
    public AuditLogDTO createByAdmin(CreateAuditLogRequest request) {
        User actor = resolveOptionalActor(request.getActorUserId());

        AuditLog log = new AuditLog();
        log.setId(idSequenceService.generateNextId("ADT"));
        log.setActor(actor);
        log.setModuleName(clean(request.getModuleName()));
        log.setAction(clean(request.getAction()));
        log.setMethod(clean(request.getMethod()));
        log.setEndpoint(clean(request.getEndpoint()));
        log.setResourceId(clean(request.getResourceId()));
        log.setHttpStatus(request.getHttpStatus());
        log.setIpAddress(clean(request.getIpAddress()));
        log.setUserAgent(clean(request.getUserAgent()));
        log.setDetails(truncateDetails(clean(request.getDetails())));

        return toDTO(auditLogRepository.save(log));
    }

    @Transactional
    public AuditLogDTO logAction(
            String actorUserId,
            String moduleName,
            String action,
            String method,
            String endpoint,
            String resourceId,
            Integer httpStatus,
            String ipAddress,
            String userAgent,
            String details
    ) {
        AuditLog log = new AuditLog();
        log.setId(idSequenceService.generateNextId("ADT"));
        log.setActor(resolveOptionalActor(actorUserId));
        log.setModuleName(requireField(moduleName, "moduleName"));
        log.setAction(requireField(action, "action"));
        log.setMethod(clean(method));
        log.setEndpoint(clean(endpoint));
        log.setResourceId(clean(resourceId));
        log.setHttpStatus(httpStatus);
        log.setIpAddress(clean(ipAddress));
        log.setUserAgent(clean(userAgent));
        log.setDetails(truncateDetails(clean(details)));
        return toDTO(auditLogRepository.save(log));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> listForAdmin(
            String actorUserId,
            String moduleName,
            String action,
            String method,
            Integer httpStatus,
            Pageable pageable
    ) {
        return auditLogRepository.findForAdmin(
                blankToNull(actorUserId),
                toLikePatternLower(moduleName),
                toLikePatternLower(action),
                toUpperOrNull(method),
                httpStatus,
                pageable
        ).map(this::toDTO);
    }

    private String toUpperOrNull(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase();
    }

    private String toLikePatternLower(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : "%" + cleaned.toLowerCase() + "%";
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> listMine(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        return auditLogRepository.findByActor_Id(user.getId(), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public AuditLogDTO getByIdForAdmin(String id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Audit log introuvable."));
        return toDTO(log);
    }

    // Suppression volontairement absente : le journal d'audit est append-only (immuable),
    // enforce au niveau base par un trigger (V8__audit_log_immutable.sql).

    /**
     * Resolves the actor User from an ID. Returns null if the ID is blank or the user has been
     * deleted, to avoid crashing audit log writes caused by deleted user accounts.
     */
    private User resolveOptionalActor(String actorUserId) {
        String cleanedId = blankToNull(actorUserId);
        if (cleanedId == null) {
            return null;
        }
        return userRepository.findById(cleanedId).orElseGet(() -> {
            log.warn("AuditLogService: actor user '{}' not found (deleted?), logging without actor.", cleanedId);
            return null;
        });
    }

    /**
     * Truncates a details payload to {@value #DETAILS_MAX_LENGTH} characters to prevent
     * JDBC buffer overflow errors on very large audit payloads.
     */
    private String truncateDetails(String details) {
        if (details == null || details.length() <= DETAILS_MAX_LENGTH) {
            return details;
        }
        log.warn("AuditLogService: details payload truncated from {} to {} chars.", details.length(), DETAILS_MAX_LENGTH);
        return details.substring(0, DETAILS_MAX_LENGTH - 3) + "...";
    }

    private String requireField(String value, String fieldName) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(fieldName + " est obligatoire.");
        }
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToNull(String value) {
        return clean(value);
    }

    private String toLowerOrNull(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase();
    }

    private AuditLogDTO toDTO(AuditLog log) {
        String actorId = null;
        String actorNomComplet = null;
        if (log.getActor() != null) {
            actorId = log.getActor().getId();
            actorNomComplet = log.getActor().getNom() + " " + log.getActor().getPrenom();
        }
        return new AuditLogDTO(
                log.getId(),
                actorId,
                actorNomComplet,
                log.getModuleName(),
                log.getAction(),
                log.getMethod(),
                log.getEndpoint(),
                log.getResourceId(),
                log.getHttpStatus(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
