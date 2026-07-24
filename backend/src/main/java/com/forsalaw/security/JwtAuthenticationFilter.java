package com.forsalaw.security;

import com.forsalaw.userManagement.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
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
            String message = "Compte desactive.";
            if (userOpt.isPresent() && userOpt.get().isBlockedByFailedAttempts()) {
                message = "Compte bloque apres 3 tentatives. Veuillez contacter l'administrateur.";
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("message", message)
            ));
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
