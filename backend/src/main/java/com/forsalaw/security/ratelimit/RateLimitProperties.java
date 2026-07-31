package com.forsalaw.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Quotas de limitation de debit (voir application.properties, prefixe forsalaw.ratelimit). */
@Component
@ConfigurationProperties(prefix = "forsalaw.ratelimit")
@Getter
@Setter
public class RateLimitProperties {

    /** Permet de desactiver entierement la limitation (tests, environnement de recette). */
    private boolean enabled = true;

    /** Connexion, inscription, reinitialisation de mot de passe : par IP. */
    private int authPerMinute = 10;

    /** Annuaire public des avocats : par IP. */
    private int avocatsPerMinute = 30;

    /** Assistant IA : par utilisateur authentifie. */
    private int aiChatPerMinute = 5;

    /**
     * Signalements SOS : par IP. Volontairement large — l'endpoint est ouvert et doit resister
     * aux abus, mais bloquer un proche qui s'y reprend a plusieurs fois serait pire que le risque.
     */
    private int sosPerMinute = 10;
}
