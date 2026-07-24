package com.forsalaw.auditManagement.controller;

import com.forsalaw.auditManagement.model.AuditLogDTO;
import com.forsalaw.auditManagement.model.CreateAuditLogRequest;
import com.forsalaw.auditManagement.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Créer un log d'audit (admin)")
    @PostMapping
    public ResponseEntity<AuditLogDTO> create(@Valid @RequestBody CreateAuditLogRequest request) {
        return ResponseEntity.ok(auditLogService.createByAdmin(request));
    }

    @Operation(summary = "Lister les logs d'audit (admin)")
    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> list(
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer httpStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(auditLogService.listForAdmin(
                actorUserId, moduleName, action, method, httpStatus, pageable));
    }

    @Operation(summary = "Détail d'un log d'audit (admin)")
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDTO> getById(@PathVariable String id) {
        return ResponseEntity.ok(auditLogService.getByIdForAdmin(id));
    }

    // Aucune suppression : le journal d'audit est append-only (immuable). Voir V8__audit_log_immutable.sql
    // et docs/ERASURE_POLICY.md pour la reconciliation avec le droit a l'effacement (RGPD/INPDP).
}
