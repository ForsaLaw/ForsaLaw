package com.forsalaw.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Seaux a jetons en memoire, indexes par cle d'appelant (IP ou identifiant utilisateur).
 *
 * <p>Le cache expire les entrees inactives : sans cette expiration, une map indexee par IP
 * grossirait indefiniment au fil des visiteurs — une fuite memoire lente mais certaine sur un
 * service public. Une entree expiree repart avec un seau plein, ce qui est sans consequence :
 * elle n'expire qu'apres une periode d'inactivite tres superieure a la fenetre du quota.</p>
 */
@Component
public class RateLimitBucketRegistry {

    private static final Duration FENETRE = Duration.ofMinutes(1);
    private static final Duration EXPIRATION_INACTIVITE = Duration.ofMinutes(10);
    private static final long CAPACITE_MAX_ENTREES = 100_000;

    private final Cache<String, Bucket> seaux = Caffeine.newBuilder()
            .expireAfterAccess(EXPIRATION_INACTIVITE)
            .maximumSize(CAPACITE_MAX_ENTREES)
            .build();

    /** Seau associe a la cle, cree au premier appel avec le quota demande. */
    public Bucket seau(String cle, int parMinute) {
        return seaux.get(cle, ignore -> construire(parMinute));
    }

    private Bucket construire(int parMinute) {
        // Recharge progressive (greedy) plutot qu'en bloc : le quota se reconstitue au fil de la
        // minute au lieu de liberer d'un coup toutes les requetes a chaque top d'horloge.
        Bandwidth limite = Bandwidth.classic(parMinute, Refill.greedy(parMinute, FENETRE));
        return Bucket.builder().addLimit(limite).build();
    }

    /** Vide tous les seaux — reservé aux tests, pour repartir d'un etat connu. */
    public void reinitialiser() {
        seaux.invalidateAll();
    }
}
