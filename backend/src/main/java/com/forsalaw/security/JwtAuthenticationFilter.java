package com.forsalaw.security;

import com.forsalaw.userManagement.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JwtCookieService jwtCookieService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // Source principale : cookie HttpOnly (le front n'a plus le JWT en JS).
        // Repli : en-tete Authorization, conserve pour Swagger et les clients HTTP.
        String token = jwtCookieService.readToken(request).orElseGet(() -> extractFromHeader(request));

        if (token == null || token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtService.extractEmail(token);
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !userOpt.get().isActif()) {
            // Jeton signe valide, mais le compte n'existe plus ou est desactive/bloque : on
            // PERIME LE COOKIE et on poursuit en anonyme, sans court-circuiter la chaine.
            //
            // Repondre 403 ici enfermait l'utilisateur : le filtre s'execute avant
            // l'autorisation, donc TOUS les endpoints repondaient 403 — y compris les
            // permitAll /api/auth/login, /api/auth/register ET /api/auth/logout. Le compte
            // desactive ne pouvait donc ni se reconnecter, ni se deconnecter, ni s'inscrire a
            // nouveau : seul un vidage manuel des cookies le liberait.
            //
            // En poursuivant en anonyme, l'acces reste bien revoque (les endpoints proteges
            // repondent 401 via l'AuthenticationEntryPoint), mais les endpoints publics
            // redeviennent joignables. Le motif exact (desactive / bloque apres 3 tentatives)
            // est reporte a la tentative de connexion, ou AuthService le renvoie deja.
            response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieService.clear().toString());
            filterChain.doFilter(request, response);
            return;
        }

        String role = jwtService.extractRole(token);
        if (role == null) role = "";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(authority)
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    /**
     * Rejoue l'authentification sur les dispatches ASYNC (par defaut, OncePerRequestFilter les
     * ignore).
     *
     * <p>Indispensable pour les reponses en flux (SseEmitter, cf. {@code /api/ai/chat}) : quand
     * l'emitter se termine, Tomcat redispatche la requete a travers la chaine de filtres. Comme
     * la session est STATELESS, aucun SecurityContext n'est restaure sur ce second passage ; si
     * ce filtre est saute, la requete redevient anonyme, l'AuthorizationFilter la refuse, et
     * comme la reponse est deja commitee (les jetons ont ete streames), l'erreur ne peut plus
     * etre ecrite : la connexion reste ouverte et le client n'est jamais notifie de la fin du
     * flux. Le JWT etant porte par le cookie de la requete, il est simplement relu ici.</p>
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /** JWT depuis l'en-tete Authorization (Swagger / clients HTTP), null si absent. */
    private String extractFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        // Tolere le format brut "Authorization: <jwt>" (certains clients Swagger/HTTP)
        return authHeader.trim();
    }
}
