package com.forsalaw.userManagement.controller;

import com.forsalaw.ragManagement.ingestion.ScannedPdfException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleAuthError(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    /**
     * Un PDF sans couche texte (scan) est une erreur de SAISIE, pas une panne serveur : sans ce
     * handler, elle remonterait en 500 alors que le client doit simplement fournir un autre
     * fichier (ou attendre une chaine OCR, prevue comme etape distincte).
     */
    @ExceptionHandler(ScannedPdfException.class)
    public ResponseEntity<Map<String, String>> handleScannedPdf(ScannedPdfException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    /**
     * Corps JSON illisible (ex. {@code LocalDateTime} avec suffixe {@code Z} : Jackson echoue avant d'atteindre le service).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableJson(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
        String message = "JSON invalide ou format de date refuse. Pour dateHeureDebut/dateHeureFin, utilisez un format ISO-8601 "
                + "avec decalage horaire, ex. \"2026-03-27T10:00:00+01:00\" ou \"2026-03-27T09:00:00Z\". "
                + (detail.isEmpty() ? "" : "Detail: " + detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.<String, String>of(
                        "champ", err.getField(),
                        "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Valeur invalide"
                ))
                .collect(Collectors.toList());
        String firstMessage = errors.isEmpty() ? "Données invalides" : errors.get(0).get("message");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", firstMessage,
                "errors", errors
        ));
    }
}
