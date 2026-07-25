package com.forsalaw.security;

import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.service.GoogleOAuth2UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Locale;

/**
 * Après Google OAuth, envoie le JWT vers la page front (forsalaw.oauth2.success-redirect-uri), pas vers Swagger.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DEFAULT_FRONT_CALLBACK = "http://localhost:3000/auth/google/callback";
    /** Clé de session utilisée par {@link org.springframework.security.web.savedrequest.HttpSessionRequestCache}. */
    private static final String SPRING_SECURITY_SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";

    private final GoogleOAuth2UserService googleOAuth2UserService;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;

    /** Instance locale : avec {@code requestCache.disable()} dans SecurityConfig, aucun bean RequestCache n'est exposé. */
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Value("${forsalaw.oauth2.success-redirect-uri:http://localhost:3000/auth/google/callback}")
    private String successRedirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        requestCache.removeRequest(request, response);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SPRING_SECURITY_SAVED_REQUEST);
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        User user = googleOAuth2UserService.upsertGoogleUser(oauth2User);

        String token = jwtService.generateToken(user.getEmail(), user.getRoleUser().name());

        // Le JWT part dans un cookie HttpOnly, plus dans l'URL (fuite en historique, logs proxy,
        // en-tete Referer). Le front appelle ensuite /api/users/me pour connaitre l'utilisateur.
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieService.build(token).toString());

        String base = safeFrontendCallbackBase(successRedirectUri);
        String redirectUrl = UriComponentsBuilder.fromUriString(base)
                .queryParam("status", "success")
                .build()
                .toUriString();

        log.info("OAuth2 Google OK pour {}, redirection vers {}", user.getEmail(), base);
        response.sendRedirect(redirectUrl);
    }

    /**
     * Évite une config accidentelle vers Swagger (FRONTEND_BASE_URL vs OAUTH2) ou une chaîne vide.
     */
    private static String safeFrontendCallbackBase(String configured) {
        if (configured == null) {
            return DEFAULT_FRONT_CALLBACK;
        }
        String t = configured.trim();
        if (t.isEmpty()) {
            log.warn("forsalaw.oauth2.success-redirect-uri est vide ; utilisation de {}", DEFAULT_FRONT_CALLBACK);
            return DEFAULT_FRONT_CALLBACK;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("swagger-ui") || lower.contains("/v3/api-docs") || lower.contains("swagger-ui.html")) {
            log.warn(
                    "forsalaw.oauth2.success-redirect-uri pointe vers la doc API ({}), pas le front. Utilisation de {}.",
                    t,
                    DEFAULT_FRONT_CALLBACK);
            return DEFAULT_FRONT_CALLBACK;
        }
        return t;
    }
}
