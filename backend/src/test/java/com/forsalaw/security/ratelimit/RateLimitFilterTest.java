package com.forsalaw.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock FilterChain filterChain;

    private RateLimitFilter filtre;
    private RateLimitProperties proprietes;

    @BeforeEach
    void preparer() {
        proprietes = new RateLimitProperties();
        proprietes.setAuthPerMinute(3);
        proprietes.setAvocatsPerMinute(3);
        filtre = new RateLimitFilter(proprietes, new RateLimitBucketRegistry(), new ObjectMapper());
    }

    @Test
    void connexion_estRefuseeAuDelaDuQuota() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse reponse = appeler("/api/auth/login", "POST", "10.0.0.1");
            assertThat(reponse.getStatus()).as("appel %d dans le quota", i + 1).isEqualTo(200);
        }

        MockHttpServletResponse refusee = appeler("/api/auth/login", "POST", "10.0.0.1");

        assertThat(refusee.getStatus()).isEqualTo(429);
        assertThat(refusee.getHeader("Retry-After")).isEqualTo("60");
        assertThat(refusee.getContentAsString()).contains("Trop de requetes");
        verify(filterChain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * Sans lecture de X-Forwarded-For, toutes les requetes relayees par un proxy partagent
     * l'adresse du proxy : les premiers appels verrouilleraient la plateforme pour tout le monde.
     */
    @Test
    void adressesClientesDistinctesDerriereUnProxy_nePartagentPasLeMemeSeau() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(appeler("/api/auth/login", "POST", "203.0.113.7").getStatus()).isEqualTo(200);
        }
        assertThat(appeler("/api/auth/login", "POST", "203.0.113.7").getStatus()).isEqualTo(429);

        // Autre client, meme proxy : son quota doit etre intact.
        assertThat(appeler("/api/auth/login", "POST", "203.0.113.8").getStatus()).isEqualTo(200);
    }

    @Test
    void endpointNonLimite_passeToujours() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(appeler("/api/forum/topics", "GET", "10.0.0.2").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void desactive_laisseToutPasser() throws Exception {
        proprietes.setEnabled(false);
        for (int i = 0; i < 10; i++) {
            assertThat(appeler("/api/auth/login", "POST", "10.0.0.3").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse appeler(String chemin, String methode, String ipClient) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        requete.setRequestURI(chemin);
        // Le proxy se presente sous sa propre adresse et transmet le client dans l'en-tete.
        requete.setRemoteAddr("172.17.0.1");
        requete.addHeader("X-Forwarded-For", ipClient + ", 172.17.0.1");
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, filterChain);
        return reponse;
    }
}
