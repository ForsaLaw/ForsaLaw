package com.forsalaw.rdvManagement.controller;

import com.forsalaw.rdvManagement.service.AvocatAgendaService;
import com.forsalaw.rdvManagement.service.RendezVousService;
import com.forsalaw.security.JwtAuthenticationFilter;
import com.forsalaw.security.JwtService;
import com.forsalaw.security.OAuth2AuthenticationSuccessHandler;
import com.forsalaw.security.SecurityConfig;
import com.forsalaw.userManagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie que la chaine de securite reelle (SecurityConfig) protege /api/rendezvous/** :
 * une requete non authentifiee doit recevoir 401 (authenticationEntryPoint).
 */
@WebMvcTest(RendezVousClientController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "forsalaw.cors.allowed-origins=http://localhost:3000",
        "DB_USERNAME=test",
        "DB_PASSWORD=test",
        "JWT_SECRET=test-secret-test-secret-test-secret-1234"
})
class RendezVousClientControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean RendezVousService rendezVousService;
    @MockBean AvocatAgendaService avocatAgendaService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;
    @MockBean OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void unauthenticatedRequest_toRendezvous_returns401() throws Exception {
        mockMvc.perform(get("/api/rendezvous/mes-demandes"))
                .andExpect(status().isUnauthorized());
    }
}
