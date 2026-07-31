package com.forsalaw.avocatManagement.service;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.entity.AvocatVerificationStatus;
import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import com.forsalaw.avocatManagement.model.CreateAvocatRequest;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.ProfilePhotoService;
import com.forsalaw.userManagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prise en compte du numero d'inscription ONAT lors d'une demande avocat.
 *
 * <p>Le numero identifie une inscription au tableau de l'Ordre : deux profils ne peuvent pas le
 * partager, sans quoi un praticien pourrait se prevaloir de l'inscription d'un confrere.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvocatServiceOnatTest {

    private static final String EMAIL = "avocat@forsalaw.tn";

    @Mock AvocatRepository avocatRepository;
    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @Mock ProfilePhotoService profilePhotoService;

    private AvocatService service;

    @BeforeEach
    void preparer() {
        service = new AvocatService(avocatRepository, userRepository, userService, profilePhotoService);

        User user = new User();
        user.setId("2026-USR-00001");
        user.setEmail(EMAIL);
        user.setNom("Trabelsi");
        user.setPrenom("Mouna");
        user.setRoleUser(RoleUser.client);

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userService.generateNextId("AVC")).thenReturn("2026-AVC-00001");
        when(avocatRepository.existsByUserId(anyString())).thenReturn(false);
        when(avocatRepository.existsByNumeroCarteProfessionnelleIgnoreCase(anyString())).thenReturn(false);
        when(avocatRepository.existsByCinIgnoreCase(anyString())).thenReturn(false);
        when(avocatRepository.existsByNumeroOnatIgnoreCase(anyString())).thenReturn(false);
        when(avocatRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CreateAvocatRequest requete() {
        CreateAvocatRequest r = new CreateAvocatRequest();
        r.setDomaine(DomaineJuridique.DROIT_PENAL);
        r.setSpecialite(SpecialiteJuridique.penal);
        r.setAnneesExperience(12);
        r.setVille("Tunis");
        r.setNumeroCarteProfessionnelle("CP-123");
        r.setCin("09876543");
        r.setBarreau("Tunis");
        r.setNumeroOnat("  ONAT-4512  ");
        return r;
    }

    @Test
    void numeroOnat_estEnregistreDetourEtExposeDansLeDTO() {
        var dto = service.createProfile(EMAIL, requete());

        ArgumentCaptor<Avocat> captor = ArgumentCaptor.forClass(Avocat.class);
        verify(avocatRepository).save(captor.capture());

        // Espaces de saisie retires, comme pour les autres identifiants.
        assertThat(captor.getValue().getNumeroOnat()).isEqualTo("ONAT-4512");
        assertThat(dto.getNumeroOnat()).isEqualTo("ONAT-4512");
    }

    @Test
    void nouvelleDemande_resteNonVerifieeJusquaDecisionAdmin() {
        var dto = service.createProfile(EMAIL, requete());

        // Fournir un numero ONAT ne vaut PAS verification : seul un administrateur l'accorde.
        // Sans cela, le badge « Verifie par l'ONAT » s'afficherait sur simple declaration.
        assertThat(dto.getVerificationStatus()).isEqualTo(AvocatVerificationStatus.PENDING);
        assertThat(dto.isVerifie()).isFalse();
    }

    @Test
    void numeroOnatDejaUtilise_estRefuseSansRienEnregistrer() {
        when(avocatRepository.existsByNumeroOnatIgnoreCase("ONAT-4512")).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(EMAIL, requete()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ONAT");

        verify(avocatRepository, never()).save(any());
    }
}
