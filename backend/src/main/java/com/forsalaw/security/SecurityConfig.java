package com.forsalaw.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository oAuth2AuthorizationRequestRepository;

    /** Origines frontend autorisees pour CORS (liste separee par des virgules), voir application.properties. */
    @Value("${forsalaw.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF REACTIVE : l'authentification passe desormais par un cookie, que le navigateur
                // envoie automatiquement — un formulaire tiers pourrait donc declencher une action.
                // Double-submit : le token est depose dans le cookie LISIBLE XSRF-TOKEN, et le front
                // doit le renvoyer dans l'en-tete X-XSRF-TOKEN.
                // Endpoints exemptes : ceux appeles sans session prealable (login/register/reset),
                // le flux OAuth2, le WebSocket et les endpoints publics.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Sans ceci, CsrfConfigurer ajoute INCONDITIONNELLEMENT sa propre
                        // CsrfAuthenticationStrategy au SessionManagementConfigurer (verifie par
                        // decompilation : CsrfConfigurer.configure() appelle toujours
                        // sessionManagementConfigurer.addSessionAuthenticationStrategy(...), qui
                        // ADDITIONNE plutot que remplacer). Definir sessionAuthenticationStrategy
                        // sur .sessionManagement() seul ne suffit donc pas : il faut neutraliser
                        // la strategie CSRF elle-meme, ici, sur le configurer CSRF.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/request-unlock",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/ws/**",
                                "/api/documents/public/verify"
                        )
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Sinon, après OAuth2, Spring peut rediriger vers une URL sauvegardée (ex. Swagger) au lieu du handler JWT.
                .requestCache(cache -> cache.disable())
                // STATELESS : aucune session serveur. Possible car la requete d'autorisation OAuth2
                // est desormais conservee dans un cookie (HttpCookieOAuth2AuthorizationRequestRepository)
                // et non plus en session — sinon le callback Google echouerait en authorization_request_not_found.
                //
                // sessionAuthenticationStrategy=Null ici aussi : couvre toute autre strategie de
                // fixation de session par defaut (en plus de celle de CSRF, neutralisee ci-dessus).
                // Sans session a fixer, ces strategies n'ont aucune utilite dans une API stateless.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/avocats/domaines", "/api/avocats/specialites").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/forum/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rendezvous/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/messenger/attachments/download").permitAll()
                        .requestMatchers(getAvocatsPublicMatcher()).permitAll()
                        .requestMatchers("/api/admin/rendezvous/**").hasRole("ADMIN")
                        .requestMatchers("/api/rendezvous/avocat/**").hasRole("AVOCAT")
                        .requestMatchers("/api/rendezvous/**").hasRole("CLIENT")
                        .requestMatchers("/api/admin/messenger/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/documents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/documents/public/verify").permitAll()
                        .requestMatchers("/api/documents/**").authenticated()
                        .requestMatchers("/api/ai/**").authenticated()
                        .requestMatchers("/api/admin/affaires/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/whatsapp/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/rag/**").hasRole("ADMIN")
                        .requestMatchers("/api/messenger/avocat/**").hasRole("AVOCAT")
                        .requestMatchers("/api/messenger/**").hasRole("CLIENT")
                        .requestMatchers("/api/audit-logs/**").authenticated()
                        .requestMatchers("/api/avocats/me", "/api/avocats/me/**").hasAnyRole("CLIENT", "AVOCAT")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .oauth2Login(oauth2 -> oauth2
                        // Requete d'autorisation stockee en cookie (et non en session) => compatible STATELESS.
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(oAuth2AuthorizationRequestRepository))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** GET /api/avocats et GET /api/avocats/** = public (liste avocats, détail). */
    private static RequestMatcher getAvocatsPublicMatcher() {
        return request -> HttpMethod.GET.name().equals(request.getMethod())
                && (new AntPathRequestMatcher("/api/avocats").matches(request)
                || new AntPathRequestMatcher("/api/avocats/**").matches(request));
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("message", "Token JWT requis. Utilisez Authorize dans Swagger avec Bearer <token>.")
            ));
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("message", "Accès refusé : Vous n'avez pas les permissions nécessaires pour consulter ou modifier cette ressource.")
            ));
        };
    }

    /**
     * CORS strict : seules les origines frontend declarees (forsalaw.cors.allowed-origins) sont autorisees.
     * allowCredentials=true impose des origines explicites (jamais "*").
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-XSRF-TOKEN : en-tete portant le jeton CSRF renvoye par le front (double-submit cookie).
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
