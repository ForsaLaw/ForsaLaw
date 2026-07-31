package com.forsalaw.userManagement.config;

import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Garde-fous de l'amorcage du compte ADMIN.
 *
 * <p>Ce compte detient tous les droits et son adresse est previsible : un secret faible, ou un
 * deploiement sans aucun admin, doivent se voir au demarrage et non le jour ou quelqu'un tente
 * d'administrer.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSeederTest {

    private static final String SECRET_SOLIDE = "9zQ4-vR7x!Lm2Pd8_Kt3Nb6Yw";

    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @Mock PasswordEncoder passwordEncoder;

    private AdminSeeder seeder;

    @BeforeEach
    void preparer() {
        seeder = new AdminSeeder(userRepository, userService, passwordEncoder);
        when(userRepository.existsByRoleUser(RoleUser.admin)).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userService.generateNextId("USR")).thenReturn("2026-USR-00001");
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$empreinte");
    }

    private void configurer(String email, String motDePasse, boolean requis) {
        ReflectionTestUtils.setField(seeder, "seedEmail", email);
        ReflectionTestUtils.setField(seeder, "seedPassword", motDePasse);
        ReflectionTestUtils.setField(seeder, "seedRequired", requis);
    }

    @Test
    void secretAbsentEtAmorcageExige_interrompLeDemarrage() {
        configurer("", "", true);

        // Sans admin ET sans amorcage, le deploiement est inutilisable : plus personne ne peut
        // verifier un avocat ni moderer. Echouer franchement vaut mieux que le decouvrir plus tard.
        assertThatThrownBy(() -> seeder.onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_SEED_EMAIL");
    }

    @Test
    void secretAbsentSansExigence_laisseDemarrer() {
        configurer("", "", false);

        // Developpement et CI : l'absence d'amorcage ne doit pas bloquer.
        assertThatCode(() -> seeder.onApplicationEvent(null)).doesNotThrowAnyException();
        verify(userRepository, never()).save(any());
    }

    @Test
    void secretTropCourt_interrompLeDemarrage() {
        configurer("admin@forsalaw.tn", "Admin123!", false);

        // Refus franc, jamais un repli silencieux : le compte amorce est tout-puissant.
        assertThatThrownBy(() -> seeder.onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_SEED_PASSWORD");

        verify(userRepository, never()).save(any());
    }

    @Test
    void secretSolide_creeLAdminAvecUnMotDePasseEncode() {
        configurer("  Admin@ForsaLaw.TN  ", SECRET_SOLIDE, true);

        seeder.onApplicationEvent(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User cree = captor.getValue();

        assertThat(cree.getEmail()).isEqualTo("admin@forsalaw.tn");
        assertThat(cree.getRoleUser()).isEqualTo(RoleUser.admin);
        // Le secret n'est JAMAIS stocke en clair.
        assertThat(cree.getMotDePasse()).isNotEqualTo(SECRET_SOLIDE).startsWith("$2a$");
    }

    @Test
    void adminDejaPresent_neRefaitRien() {
        when(userRepository.existsByRoleUser(RoleUser.admin)).thenReturn(true);
        configurer("admin@forsalaw.tn", SECRET_SOLIDE, true);

        seeder.onApplicationEvent(null);

        verify(userRepository, never()).save(any());
    }
}
