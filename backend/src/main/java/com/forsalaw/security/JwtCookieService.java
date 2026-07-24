package com.forsalaw.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Gestion centralisee du cookie d'authentification portant le JWT.
 *
 * <p>Le JWT n'est plus expose au JavaScript (fini {@code localStorage} : un XSS ne peut plus
 * voler la session). Attributs du cookie :</p>
 * <ul>
 *     <li><b>HttpOnly</b> : illisible depuis JS ;</li>
 *     <li><b>Secure</b> : configurable ({@code forsalaw.auth.cookie.secure}). DOIT etre {@code false}
 *         en developpement sur http://localhost, sinon le navigateur ignore le cookie ;</li>
 *     <li><b>SameSite=Lax</b> : protege du CSRF cross-site tout en autorisant le retour de
 *         redirection Google OAuth2 (un {@code Strict} casserait le login Google) ;</li>
 *     <li><b>Path=/</b> et <b>Max-Age</b> aligne sur l'expiration du JWT.</li>
 * </ul>
 */
@Service
public class JwtCookieService {

    public static final String AUTH_COOKIE_NAME = "forsalaw_token";

    @Value("${forsalaw.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${forsalaw.auth.cookie.secure:false}")
    private boolean secure;

    @Value("${forsalaw.auth.cookie.same-site:Lax}")
    private String sameSite;

    /** Cookie d'authentification a poser apres login / register / OAuth2. */
    public ResponseCookie build(String jwt) {
        return baseBuilder(jwt)
                .maxAge(Duration.ofMillis(expirationMs))
                .build();
    }

    /** Cookie vide et immediatement expire : deconnexion (seul le serveur peut effacer un HttpOnly). */
    public ResponseCookie clear() {
        return baseBuilder("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/");
    }

    /** Lit le JWT depuis les cookies de la requete, si present. */
    public Optional<String> readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> AUTH_COOKIE_NAME.equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
