package com.forsalaw.ragManagement.chat.model;

import java.time.LocalDateTime;

/**
 * Consentement IA enregistre en base.
 *
 * @param version version acceptee, {@code null} si l'utilisateur n'a jamais consenti cote serveur
 * @param consentedAt horodatage de l'acceptation, {@code null} dans le meme cas
 */
public record AiConsentResponse(Integer version, LocalDateTime consentedAt) {}
