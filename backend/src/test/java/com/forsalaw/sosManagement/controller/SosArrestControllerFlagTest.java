package com.forsalaw.sosManagement.controller;

import com.forsalaw.sosManagement.SosFeatureProperties;
import com.forsalaw.sosManagement.model.SosArrestRequest;
import com.forsalaw.sosManagement.service.SosArrestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Comportement de l'interrupteur SOS.
 *
 * <p>Le point critique : desactive, AUCUN signalement ne doit etre enregistre. Un signalement
 * accepte puis jamais transmis laisserait un proche croire qu'un avocat intervient sur une
 * garde a vue — c'est precisement le risque que l'interrupteur existe pour ecarter.</p>
 */
@ExtendWith(MockitoExtension.class)
class SosArrestControllerFlagTest {

    @Mock SosArrestService sosArrestService;

    private SosFeatureProperties fonctionnalite;
    private SosArrestController controller;

    @BeforeEach
    void preparer() {
        fonctionnalite = new SosFeatureProperties();
        controller = new SosArrestController(sosArrestService, fonctionnalite);
    }

    private SosArrestRequest requete() {
        SosArrestRequest r = new SosArrestRequest();
        r.setNomDetenu("Ali Ben Salah");
        r.setLieuArrestation("Poste de police, Ariana");
        r.setDateHeureArrestation(LocalDateTime.now());
        r.setContactUrgence("+216 20 000 000");
        return r;
    }

    @Test
    void parDefaut_laFonctionnaliteEstDesactivee() {
        // Le defaut compte : un deploiement qui oublie la variable ne doit pas promettre
        // une mobilisation d'avocats qui n'aura pas lieu.
        assertThat(new SosFeatureProperties().isEnabled()).isFalse();
    }

    @Test
    void desactivee_leSignalementEstRefuseSansRienEnregistrer() {
        assertThatThrownBy(() -> controller.signaler(requete(), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verifyNoInteractions(sosArrestService);
    }

    @Test
    void desactivee_laConsultationEstAussiRefusee() {
        // Afficher l'etat d'un signalement suppose que le dispositif fonctionne.
        assertThatThrownBy(() -> controller.consulter("2026-SOS-00001"))
                .isInstanceOf(ResponseStatusException.class);
        verify(sosArrestService, never()).consulter(anyString());
    }

    @Test
    void activee_leSignalementPasse() {
        fonctionnalite.setEnabled(true);

        controller.signaler(requete(), null);

        verify(sosArrestService).enregistrer(any(), any());
    }
}
