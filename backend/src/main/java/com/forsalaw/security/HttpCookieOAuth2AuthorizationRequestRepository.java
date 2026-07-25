package com.forsalaw.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Stocke la requete d'autorisation OAuth2 en cours dans un cookie plutot qu'en session HTTP.
 *
 * <p>C'est ce qui permet de passer Spring Security en {@link org.springframework.security.config.http.SessionCreationPolicy#STATELESS}
 * sans casser le login Google : par defaut Spring conserve la requete d'autorisation en session
 * entre la redirection vers Google et le callback, et un mode STATELESS naif provoque
 * {@code authorization_request_not_found} au retour.</p>
 *
 * <p>Serialisation JSON via les modules Jackson de Spring Security (validation de types par
 * liste blanche) plutot que la serialisation Java : le contenu du cookie est fourni par le
 * client, et le desserialiser via {@code ObjectInputStream} exposerait a des attaques par
 * gadgets de desserialisation.</p>
 *
 * <p>Cookie : HttpOnly, SameSite=Lax (obligatoire — le callback Google est une navigation
 * cross-site ; en {@code Strict} le cookie ne serait pas renvoye), duree de vie courte.</p>
 */
@Component
@Slf4j
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_REQUEST_COOKIE_NAME = "forsalaw_oauth2_request";
    private static final Duration COOKIE_TTL = Duration.ofMinutes(3);

    private final ObjectMapper objectMapper;

    @Value("${forsalaw.auth.cookie.secure:false}")
    private boolean secure;

    public HttpCookieOAuth2AuthorizationRequestRepository() {
        this.objectMapper = new ObjectMapper();
        // Modules Spring Security : mixins + validation de types par liste blanche.
        this.objectMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        this.objectMapper.registerModule(new OAuth2ClientJackson2Module());
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeCookie(response);
            return;
        }
        String value = serialize(authorizationRequest);
        if (value == null) {
            return;
        }
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                cookieBuilder(value).maxAge(COOKIE_TTL).build().toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        removeCookie(response);
        return authorizationRequest;
    }

    private ResponseCookie.ResponseCookieBuilder cookieBuilder(String value) {
        return ResponseCookie.from(OAUTH2_REQUEST_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
    }

    private void removeCookie(HttpServletResponse response) {
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                cookieBuilder("").maxAge(Duration.ZERO).build().toString());
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> OAUTH2_REQUEST_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(authorizationRequest);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            log.error("OAuth2 : serialisation de la requete d'autorisation impossible", e);
            return null;
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(value);
            return objectMapper.readValue(new String(json, StandardCharsets.UTF_8), OAuth2AuthorizationRequest.class);
        } catch (Exception e) {
            // Cookie expire, tronque ou falsifie : on repart d'une authentification propre.
            log.warn("OAuth2 : cookie de requete d'autorisation illisible ({}), il sera ignore.", e.getMessage());
            return null;
        }
    }
}
