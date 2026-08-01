package com.forsalaw.userManagement.service;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.documentManagement.repository.DocumentMetadataRepository;
import com.forsalaw.storage.S3StorageService;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Effacement d'un compte : ce qui doit REELLEMENT disparaitre.
 *
 * <p>Le defaut corrige ici n'etait pas une erreur de logique mais un ecart entre la politique
 * ecrite ({@code docs/ERASURE_POLICY.md}, qui detaille l'anonymisation champ par champ) et le
 * code, qui se contentait de {@code actif = false}. Un compte « supprime » conservait nom,
 * prenom, email et telephone en clair. Ces tests verifient l'absence des donnees, pas
 * l'execution du code.</p>
 */
class ErasureServiceTest {

    private UserRepository userRepository;
    private AvocatRepository avocatRepository;
    private DocumentMetadataRepository documentMetadataRepository;
    private ErasureService erasureService;

    @BeforeEach
    void preparer() {
        userRepository = mock(UserRepository.class);
        avocatRepository = mock(AvocatRepository.class);
        documentMetadataRepository = mock(DocumentMetadataRepository.class);
        erasureService = new ErasureService(userRepository, avocatRepository,
                documentMetadataRepository, mock(S3StorageService.class));
    }

    private User compte() {
        User u = new User();
        u.setId("2026-USR-00042");
        u.setNom("Ben Salah");
        u.setPrenom("Amine");
        u.setEmail("amine@example.tn");
        u.setTelephone("+21612345678");
        u.setMotDePasse(new BCryptPasswordEncoder().encode("MotDePasse123!"));
        u.setActif(true);
        when(userRepository.findByEmail("amine@example.tn")).thenReturn(Optional.of(u));
        when(avocatRepository.findByUserId("2026-USR-00042")).thenReturn(Optional.empty());
        return u;
    }

    @Test
    void donneesPersonnelles_nesubsistentPas() {
        User u = compte();

        erasureService.effacer("amine@example.tn");

        assertThat(u.getNom()).doesNotContain("Ben Salah");
        assertThat(u.getPrenom()).doesNotContain("Amine");
        assertThat(u.getEmail()).doesNotContain("amine@example.tn");
        assertThat(u.getTelephone()).isNull();
        assertThat(u.isActif()).isFalse();
        verify(userRepository).save(u);
    }

    @Test
    void identifiantEstConserve_pourNePasCasserLeJournalDaudit() {
        // audit_log.actor_user_id pointe sur cette ligne et le journal est immuable (V8) :
        // l'identifiant doit survivre a l'effacement.
        User u = compte();

        erasureService.effacer("amine@example.tn");

        assertThat(u.getId()).isEqualTo("2026-USR-00042");
    }

    @Test
    void emailDeRemplacement_estUniqueEtNonRoutable() {
        // La colonne email est UNIQUE en base : un placeholder constant ferait echouer le
        // deuxieme effacement. Le domaine .invalid (RFC 2606) interdit tout acheminement.
        User u = compte();

        erasureService.effacer("amine@example.tn");

        assertThat(u.getEmail()).contains("2026-USR-00042").endsWith("@invalid");
    }

    @Test
    void motDePasse_nePeutPlusCorrespondreAAucuneSaisie() {
        User u = compte();

        erasureService.effacer("amine@example.tn");

        var encodeur = new BCryptPasswordEncoder();
        assertThat(encodeur.matches("MotDePasse123!", u.getMotDePasse())).isFalse();
        // Le piege : un hash de chaine vide laisserait passer une connexion sans mot de passe.
        assertThat(encodeur.matches("", u.getMotDePasse())).isFalse();
        // Et la valeur ne doit pas porter de prefixe d'encodeur ({noop}...), qui deviendrait
        // un mot de passe en clair accepte sous un DelegatingPasswordEncoder.
        assertThat(u.getMotDePasse()).doesNotStartWith("{");
    }

    @Test
    void ficheAvocat_perdSesIdentifiantsProfessionnels() {
        User u = compte();
        Avocat avocat = new Avocat();
        avocat.setId("2026-AVC-00007");
        avocat.setCin("09876543");
        avocat.setNumeroCarteProfessionnelle("CP-2019-441");
        avocat.setNumeroOnat("ONAT-8891");
        avocat.setBarreau("Tunis");
        avocat.setDescription("Ancien magistrat, joignable au 98 123 456.");
        avocat.setVerifie(true);
        when(avocatRepository.findByUserId("2026-USR-00042")).thenReturn(Optional.of(avocat));

        erasureService.effacer("amine@example.tn");

        // CIN et numero ONAT designent une personne aussi surement qu'un nom.
        assertThat(avocat.getCin()).isNull();
        assertThat(avocat.getNumeroCarteProfessionnelle()).isNull();
        assertThat(avocat.getNumeroOnat()).isNull();
        assertThat(avocat.getBarreau()).isNull();
        assertThat(avocat.getDescription()).isNull();
        // Un profil anonymise ne doit plus etre propose dans la recherche d'avocats.
        assertThat(avocat.isVerifie()).isFalse();
        verify(avocatRepository).save(avocat);
    }

    @Test
    void compteSansFicheAvocat_estTraiteSansErreur() {
        compte();

        erasureService.effacer("amine@example.tn");

        verify(avocatRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void secondEffacement_resteSansEffetObservable() {
        // Idempotence : une double demande, ou une reprise apres restauration de sauvegarde,
        // ne doit pas lever d'erreur.
        User u = compte();
        erasureService.effacer("amine@example.tn");
        String emailApresPremier = u.getEmail();

        when(userRepository.findByEmail(emailApresPremier)).thenReturn(Optional.of(u));
        erasureService.effacer(emailApresPremier);

        assertThat(u.getEmail()).isEqualTo(emailApresPremier);
        assertThat(u.isActif()).isFalse();
    }
}
