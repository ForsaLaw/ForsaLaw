package com.forsalaw.rdvManagement;

import com.forsalaw.AbstractIntegrationTest;
import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.rdvManagement.entity.CreePar;
import com.forsalaw.rdvManagement.entity.RendezVous;
import com.forsalaw.rdvManagement.entity.StatutRendezVous;
import com.forsalaw.rdvManagement.entity.TypeRendezVous;
import com.forsalaw.rdvManagement.repository.RendezVousRepository;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prouve que le refactor RendezVous LocalDateTime -> OffsetDateTime est mathematiquement
 * correct une fois stocke en PostgreSQL (colonnes timestamptz, migration V3).
 *
 * <p>On traverse la bascule d'heure d'ete d'Europe/Paris (Africa/Tunis n'observe pas le DST) :
 * le 2026-03-29 les horloges sautent de 02:00 (CET, +01:00) a 03:00 (CEST, +02:00). Deux
 * rendez-vous a 01:30 puis 03:30 (heure murale de Paris) sont distants de 2 h sur l'horloge
 * mais de 1 h en temps reel (l'heure manquante). Un ancien LocalDateTime naif aurait calcule
 * 2 h (faux) ; timestamptz preserve l'instant et donne 1 h (correct).</p>
 */
class RendezVousDstIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    AvocatRepository avocatRepository;
    @Autowired
    RendezVousRepository rendezVousRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    @Transactional
    void offsetDateTimeSurvivesParisSpringForwardInPostgres() {
        User client = userRepository.save(user("2026-USR-DST-C", "client.dst@forsalaw.test", RoleUser.client));
        User lawyerUser = userRepository.save(user("2026-USR-DST-A", "avocat.dst@forsalaw.test", RoleUser.avocat));
        Avocat avocat = avocatRepository.save(avocat("2026-AVC-DST", lawyerUser));

        ZoneId paris = ZoneId.of("Europe/Paris");
        // Heures murales de Paris de part et d'autre de la bascule (2026-03-29, 02:00 -> 03:00).
        OffsetDateTime before = ZonedDateTime.of(LocalDate.of(2026, 3, 29), LocalTime.of(1, 30), paris).toOffsetDateTime();
        OffsetDateTime after = ZonedDateTime.of(LocalDate.of(2026, 3, 29), LocalTime.of(3, 30), paris).toOffsetDateTime();

        // Pre-conditions : la bascule DST est bien capturee par les offsets (avant meme la base).
        assertThat(before.getOffset()).isEqualTo(ZoneOffset.ofHours(1));   // CET
        assertThat(after.getOffset()).isEqualTo(ZoneOffset.ofHours(2));    // CEST
        assertThat(Duration.between(before, after)).isEqualTo(Duration.ofHours(1)); // 1 h reelle, pas 2 h murales

        rendezVousRepository.save(rdv("2026-RDV-DST-1", client, avocat, before));
        rendezVousRepository.save(rdv("2026-RDV-DST-2", client, avocat, after));

        // Ecrit en base puis vide le contexte de persistance : findById relira reellement Postgres.
        entityManager.flush();
        entityManager.clear();

        OffsetDateTime reloadedBefore = rendezVousRepository.findById("2026-RDV-DST-1").orElseThrow().getDateHeureDebut();
        OffsetDateTime reloadedAfter = rendezVousRepository.findById("2026-RDV-DST-2").orElseThrow().getDateHeureDebut();

        // timestamptz preserve l'INSTANT au round-trip (l'offset peut etre normalise par le driver).
        assertThat(reloadedBefore.toInstant()).isEqualTo(before.toInstant());
        assertThat(reloadedAfter.toInstant()).isEqualTo(after.toInstant());

        // Le coeur du test : l'ecart reel a travers la bascule est d'exactement UNE heure.
        assertThat(Duration.between(reloadedBefore.toInstant(), reloadedAfter.toInstant()))
                .isEqualTo(Duration.ofHours(1));
    }

    private User user(String id, String email, RoleUser role) {
        User u = new User();
        u.setId(id);
        u.setNom("Nom");
        u.setPrenom("Prenom");
        u.setEmail(email);
        u.setMotDePasse("{noop}test");
        u.setRoleUser(role);
        u.setActif(true);
        return u;
    }

    private Avocat avocat(String id, User user) {
        Avocat a = new Avocat();
        a.setId(id);
        a.setUser(user);
        a.setSpecialite(SpecialiteJuridique.civil);
        a.setVille("Tunis");
        return a;
    }

    private RendezVous rdv(String id, User client, Avocat avocat, OffsetDateTime debut) {
        RendezVous r = new RendezVous();
        r.setIdRendezVous(id);
        r.setClient(client);
        r.setAvocat(avocat);
        r.setStatutRendezVous(StatutRendezVous.CONFIRME);
        r.setTypeRendezVous(TypeRendezVous.EN_LIGNE);
        r.setCreePar(CreePar.AVOCAT);
        r.setDateHeureDebut(debut);
        r.setDateHeureFin(debut.plusMinutes(45));
        // ID pre-assigne => save() passe par merge(), qui ne declenche pas @PrePersist ici :
        // on renseigne explicitement les timestamps d'audit non-null (hors sujet du test).
        r.setDateCreation(OffsetDateTime.now());
        r.setDateMiseAJour(OffsetDateTime.now());
        return r;
    }
}
