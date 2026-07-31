package com.forsalaw.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Limitation de debit sur les endpoints exposes aux abus : authentification (force brute),
 * annuaire public des avocats (aspiration) et assistant IA (epuisement de jetons).
 *
 * <p><b>Place APRES {@code JwtAuthenticationFilter}</b> (voir SecurityConfig) : le quota de
 * {@code /api/ai/chat} est par utilisateur, il faut donc que le SecurityContext soit deja
 * renseigne. Les endpoints d'authentification, eux, sont par nature anonymes : leur quota est
 * par IP.</p>
 *
 * <p><b>Non applique aux dispatches ASYNC.</b> {@code OncePerRequestFilter} les ignore par
 * defaut et ce comportement est volontairement conserve ici : une reponse en flux (SSE) repasse
 * par la chaine de filtres a sa fermeture, ce qui decompterait un second jeton pour une seule
 * requete utilisateur. C'est l'inverse du besoin de {@code JwtAuthenticationFilter}, qui doit
 * lui s'executer sur ce second passage.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String EN_TETE_TRANSFERE = "X-Forwarded-For";

    private final RateLimitProperties proprietes;
    private final RateLimitBucketRegistry registre;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (!proprietes.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Quota quota = quotaPour(request);
        if (quota == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket seau = registre.seau(quota.cle(), quota.parMinute());
        if (seau.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Limitation de debit atteinte sur {} (quota {}/min).", quota.categorie(), quota.parMinute());
        refuser(response, quota);
    }

    /** Quota applicable, ou {@code null} si l'endpoint n'est pas limite. */
    private Quota quotaPour(HttpServletRequest request) {
        String chemin = request.getRequestURI();

        if (estAuthentification(chemin)) {
            return new Quota("auth", "auth|" + ipAppelant(request), proprietes.getAuthPerMinute());
        }
        if (estAnnuaireAvocatsPublic(request, chemin)) {
            return new Quota("avocats", "avocats|" + ipAppelant(request), proprietes.getAvocatsPerMinute());
        }
        // Endpoint ouvert qui ecrit en base et declenche un paiement : a proteger des abus, mais
        // avec un quota large — un proche affole peut legitimement s'y reprendre a plusieurs fois.
        if (chemin.startsWith("/api/sos/")) {
            return new Quota("sos", "sos|" + ipAppelant(request), proprietes.getSosPerMinute());
        }
        if (chemin.startsWith("/api/ai/")) {
            // Par utilisateur : le cout reel est porte par le compte, pas par l'adresse. Repli
            // sur l'IP si la requete n'est pas authentifiee (elle sera refusee ensuite, mais on
            // veut deja la compter).
            return new Quota("ai", "ai|" + identiteAppelant(request), proprietes.getAiChatPerMinute());
        }
        return null;
    }

    private boolean estAuthentification(String chemin) {
        return chemin.equals("/api/auth/login")
                || chemin.equals("/api/auth/register")
                || chemin.equals("/api/auth/forgot-password")
                || chemin.equals("/api/auth/reset-password")
                || chemin.equals("/api/auth/request-unlock");
    }

    /** Seul l'annuaire public en lecture est limite ; /api/avocats/me releve d'un compte. */
    private boolean estAnnuaireAvocatsPublic(HttpServletRequest request, String chemin) {
        return HttpMethod.GET.matches(request.getMethod())
                && (chemin.equals("/api/avocats") || chemin.startsWith("/api/avocats/"))
                && !chemin.startsWith("/api/avocats/me");
    }

    private String identiteAppelant(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return ipAppelant(request);
    }

    /**
     * IP reelle de l'appelant. {@code getRemoteAddr()} seul renverrait l'adresse du proxy (Vite
     * en dev, load balancer en prod) et ferait partager UN SEUL seau a tous les utilisateurs.
     * La propriete {@code server.forward-headers-strategy=framework} fait deja resoudre
     * {@code getRemoteAddr()} depuis les en-tetes transferes ; la lecture explicite ci-dessous
     * couvre le cas ou cette strategie serait desactivee.
     */
    private String ipAppelant(HttpServletRequest request) {
        String transfere = request.getHeader(EN_TETE_TRANSFERE);
        if (transfere != null && !transfere.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — le client est en tete.
            int virgule = transfere.indexOf(',');
            String premier = (virgule >= 0 ? transfere.substring(0, virgule) : transfere).trim();
            if (!premier.isEmpty()) {
                return premier;
            }
        }
        return request.getRemoteAddr();
    }

    private void refuser(HttpServletResponse response, Quota quota) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "message", "Trop de requetes. Veuillez patienter une minute avant de reessayer."
        )));
    }

    private record Quota(String categorie, String cle, int parMinute) {}
}
