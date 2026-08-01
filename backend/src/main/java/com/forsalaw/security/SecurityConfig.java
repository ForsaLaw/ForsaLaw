package com.forsalaw.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forsalaw.security.ratelimit.RateLimitFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
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
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository oAuth2AuthorizationRequestRepository;

    /** Origines frontend autorisees pour CORS (liste separee par des virgules), voir application.properties. */
    @Value("${forsalaw.cors.allowed-origins}")
    private String allowedOrigins;

    /** Politique CSP, surchargeable par environnement (voir application.properties). */
    @Value("${forsalaw.security.content-security-policy}")
    private String contentSecurityPolicy;

    private static final long HSTS_UN_AN_EN_SECONDES = 31_536_000L;

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
                        // Le renvoi interne vers /error repasse par cette chaine. Sans cette
                        // ligne, il tombe sur anyRequest().authenticated() ; or les filtres
                        // OncePerRequestFilter (dont JwtAuthenticationFilter) ignorent par defaut
                        // les dispatches ERROR, donc ce second passage est TOUJOURS anonyme.
                        // Resultat : le vrai statut d'erreur etait remplace par un 401 trompeur —
                        // un 503 « fonctionnalite desactivee » ou un 404 devenait « Token JWT
                        // requis », y compris pour un utilisateur parfaitement authentifie.
                        // permitAll ne divulgue rien : le corps de /error est deja neutralise par
                        // server.error.include-message=never.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // Interrupteurs de fonctionnalites : le front doit les connaitre AVANT
                        // toute authentification, pour ne pas afficher un bouton menant a un 503.
                        .requestMatchers(HttpMethod.GET, "/api/public/features").permitAll()
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
                        // SOS : le depot d'un signalement et son suivi sont OUVERTS. Une
                        // arrestation est signalee par un proche, souvent depuis un telephone qui
                        // n'est pas celui du titulaire du compte ; imposer une inscription a cet
                        // instant ferait perdre le seul moment ou l'intervention compte.
                        // /mine est place AVANT le permitAll, sinon il serait absorbe par lui.
                        .requestMatchers(HttpMethod.GET, "/api/sos/arrest/mine").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/sos/arrest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sos/arrest/*").permitAll()
                        .requestMatchers("/api/ai/**").authenticated()
                        .requestMatchers("/api/admin/affaires/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/whatsapp/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/rag/**").hasRole("ADMIN")
                        .requestMatchers("/api/messenger/avocat/**").hasRole("AVOCAT")
                        .requestMatchers("/api/messenger/**").hasRole("CLIENT")
                        .requestMatchers("/api/audit-logs/**").authenticated()
                        .requestMatchers("/api/avocats/me", "/api/avocats/me/**").hasAnyRole("CLIENT", "AVOCAT")
                        // Filet pour TOUTE route d'administration non listee ci-dessus.
                        //
                        // Ce n'est pas un correctif : les controleurs concernes (avocats, users,
                        // reclamations) portent deja @PreAuthorize("hasRole('ADMIN')") au niveau
                        // de la classe, et un client authentifie recoit bien 403 — verifie.
                        // C'est une defense en profondeur, pour deux raisons precises :
                        //   - WhatsAppController n'a AUCUN @PreAuthorize et ne tient que par son
                        //     matcher ; le supprimer par megarde l'ouvrirait a tout compte connecte ;
                        //   - un futur controleur /api/admin/** cree sans @PreAuthorize ET sans
                        //     matcher tomberait sur anyRequest().authenticated(), donc accessible
                        //     a n'importe quel client. Ce filet rend cet oubli impossible.
                        // Place ici, apres les regles specifiques : il ne change rien pour les
                        // routes deja couvertes, il ne fait que fermer le reste.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
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
                // En-tetes de securite. X-Frame-Options passe de SAMEORIGIN a DENY : le
                // SAMEORIGIN n'existait que pour la console H2, dont la dependance est en
                // portee "test" — elle ne peut donc pas tourner hors tests. Plus rien de
                // legitime n'affiche cette application dans une frame.
                .headers(headers -> headers
                        .frameOptions(FrameOptionsConfig::deny)
                        // nosniff : actif par defaut, rendu explicite pour qu'une future
                        // reecriture de ce bloc ne le retire pas sans s'en apercevoir.
                        .contentTypeOptions(Customizer.withDefaults())
                        // HSTS. Spring n'emet cet en-tete que sur une requete DEJA en HTTPS :
                        // il reste donc absent en developpement HTTP, ce qui est correct.
                        // preload volontairement NON active : l'inscription sur la liste des
                        // navigateurs est difficilement reversible et engage tous les
                        // sous-domaines, y compris ceux qui n'existent pas encore.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_UN_AN_EN_SECONDES))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
                        // Evite de fuiter le chemin complet (souvent porteur d'identifiants
                        // de dossier) vers un site tiers via le Referer.
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // APRES le filtre JWT : le quota de /api/ai/** est par utilisateur, il lui faut
                // donc un SecurityContext deja renseigne. Les endpoints d'authentification
                // restent limites par IP, ce que cette position ne change pas.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

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
