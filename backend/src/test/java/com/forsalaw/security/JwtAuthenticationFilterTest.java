package com.forsalaw.security;

import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comportement du filtre face a un JWT signe valide dont le compte n'est plus utilisable.
 *
 * <p>Ce cas repondait auparavant 403 en court-circuitant la chaine. Comme le filtre s'execute
 * AVANT l'autorisation, tous les endpoints devenaient inaccessibles — y compris les
 * {@code permitAll} {@code /api/auth/login}, {@code /register} et {@code /logout} : le compte
 * concerne ne pouvait ni se reconnecter, ni se deconnecter, et restait bloque tant qu'il ne
 * vidait pas ses cookies a la main.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String JETON = "jeton.signe.valide";
    private static final String EMAIL = "compte@forsalaw.tn";

    @Mock JwtService jwtService;
    @Mock UserRepository userRepository;
    @Mock JwtCookieService jwtCookieService;
    @Mock FilterChain filterChain;

    private JwtAuthenticationFilter filtre;
    private MockHttpServletRequest requete;
    private MockHttpServletResponse reponse;

    @BeforeEach
    void preparer() {
        filtre = new JwtAuthenticationFilter(jwtService, userRepository, jwtCookieService);
        requete = new MockHttpServletRequest();
        reponse = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void compteSupprime_perimeLeCookieEtPoursuitEnAnonyme() throws Exception {
        donnerJetonValidePour(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(jwtCookieService.clear()).thenReturn(cookieVide());

        filtre.doFilter(requete, reponse, filterChain);

        // La chaine DOIT continuer : c'est ce qui rend /api/auth/login a nouveau joignable.
        verify(filterChain).doFilter(requete, reponse);
        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(reponse.getHeader(HttpHeaders.SET_COOKIE))
                .as("le cookie doit etre perime, sinon l'utilisateur reste enferme au rechargement")
                .contains("forsalaw_token=")
                .contains("Max-Age=0");
        // Acces bien revoque : aucune authentification n'est posee.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void compteDesactive_perimeLeCookieEtPoursuitEnAnonyme() throws Exception {
        donnerJetonValidePour(EMAIL);
        User desactive = new User();
        desactive.setEmail(EMAIL);
        desactive.setActif(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(desactive));
        when(jwtCookieService.clear()).thenReturn(cookieVide());

        filtre.doFilter(requete, reponse, filterChain);

        verify(filterChain).doFilter(requete, reponse);
        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void compteActif_authentifieNormalement() throws Exception {
        donnerJetonValidePour(EMAIL);
        when(jwtService.extractRole(JETON)).thenReturn("client");
        User actif = new User();
        actif.setEmail(EMAIL);
        actif.setActif(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(actif));

        filtre.doFilter(requete, reponse, filterChain);

        verify(filterChain).doFilter(requete, reponse);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(EMAIL);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CLIENT");
        // Aucun cookie touche sur le chemin nominal.
        assertThat(reponse.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private void donnerJetonValidePour(String email) {
        when(jwtCookieService.readToken(requete)).thenReturn(Optional.of(JETON));
        when(jwtService.isTokenValid(JETON)).thenReturn(true);
        when(jwtService.extractEmail(JETON)).thenReturn(email);
    }

    private ResponseCookie cookieVide() {
        return ResponseCookie.from(JwtCookieService.AUTH_COOKIE_NAME, "")
                .path("/")
                .maxAge(Duration.ZERO)
                .httpOnly(true)
                .build();
    }
}
