package com.forsalaw.avocatManagement.service;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.entity.AvocatVerificationStatus;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.ProfilePhotoService;
import com.forsalaw.userManagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Ce qu'une reponse NON AUTHENTIFIEE a le droit de contenir.
 *
 * <p>La liste publique exposait le CIN — carte d'identite nationale — a n'importe quel visiteur.
 * Ces assertions sont la pour que la fuite ne puisse pas revenir sans qu'un test tombe.</p>
 */
@ExtendWith(MockitoExtension.class)
class AvocatServicePublicProjectionTest {

    @Mock AvocatRepository avocatRepository;
    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @Mock ProfilePhotoService profilePhotoService;

    private AvocatService service;
    private Avocat avocat;

    @BeforeEach
    void preparer() {
        service = new AvocatService(avocatRepository, userRepository, userService, profilePhotoService);

        User user = new User();
        user.setId("2026-USR-00001");
        user.setEmail("avocat@forsalaw.tn");
        user.setNom("Trabelsi");
        user.setPrenom("Mouna");

        avocat = new Avocat();
        avocat.setId("2026-AVC-00001");
        avocat.setUser(user);
        avocat.setSpecialite(SpecialiteJuridique.penal);
        avocat.setVille("Tunis");
        avocat.setActif(true);
        avocat.setVerifie(true);
        avocat.setVerificationStatus(AvocatVerificationStatus.APPROVED);
        avocat.setCin("11223344");
        avocat.setNumeroCarteProfessionnelle("CP-777");
        avocat.setNumeroOnat("ONAT-4512");
    }

    @Test
    void fichePublique_neContientAucunIdentifiantPersonnel() {
        when(avocatRepository.findById("2026-AVC-00001")).thenReturn(Optional.of(avocat));

        var dto = service.getById("2026-AVC-00001");

        assertThat(dto.getCin()).as("le CIN ne doit JAMAIS sortir sur un endpoint public").isNull();
        assertThat(dto.getNumeroCarteProfessionnelle()).isNull();
        assertThat(dto.getNumeroOnat()).isNull();
    }

    @Test
    void fichePublique_conserveDeQuoiAfficherLeBadge() {
        when(avocatRepository.findById("2026-AVC-00001")).thenReturn(Optional.of(avocat));

        var dto = service.getById("2026-AVC-00001");

        // C'est le couple statut/verifie qui atteste la verification cote public, pas le numero :
        // un visiteur ne pourrait de toute facon pas confronter ce dernier au tableau de l'Ordre.
        assertThat(dto.getVerificationStatus()).isEqualTo(AvocatVerificationStatus.APPROVED);
        assertThat(dto.isVerifie()).isTrue();
        assertThat(dto.getUserNom()).isEqualTo("Trabelsi");
    }

    @Test
    void ficheAdmin_conserveLesIdentifiantsPourLeControle() {
        when(avocatRepository.findById("2026-AVC-00001")).thenReturn(Optional.of(avocat));

        var dto = service.getByIdAdmin("2026-AVC-00001");

        // L'expurgation ne vaut que pour le public : c'est precisement sur ces numeros que
        // l'administrateur s'appuie pour verifier l'inscription.
        assertThat(dto.getCin()).isEqualTo("11223344");
        assertThat(dto.getNumeroOnat()).isEqualTo("ONAT-4512");
    }
}
