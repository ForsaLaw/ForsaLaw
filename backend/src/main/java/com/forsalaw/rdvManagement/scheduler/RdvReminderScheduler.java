package com.forsalaw.rdvManagement.scheduler;

import com.forsalaw.rdvManagement.entity.RendezVous;
import com.forsalaw.rdvManagement.entity.StatutRendezVous;
import com.forsalaw.rdvManagement.repository.RendezVousRepository;
import com.forsalaw.rdvManagement.service.RdvNotificationEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Rappels email : J-1 (une fois par jour) et H-1 (fenetre ~1h avant, toutes les 15 min).
 * Les dates RDV en base sont interpretees dans {@code forsalaw.notifications.timezone}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RdvReminderScheduler {

    private final RendezVousRepository rendezVousRepository;
    private final RdvNotificationEmailService rdvNotificationEmailService;

    @Value("${forsalaw.notifications.timezone:Africa/Tunis}")
    private String notificationZoneId;

    /** Tous les jours a 8h00 (fuseau configure). */
    @Scheduled(cron = "0 0 8 * * *", zone = "${forsalaw.notifications.timezone:Africa/Tunis}")
    @SchedulerLock(name = "rdvReminderJ1", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    @Transactional
    public void envoyerRappelsJ1() {
        ZoneId zone = ZoneId.of(notificationZoneId);
        LocalDate tomorrow = LocalDate.now(zone).plusDays(1);
        OffsetDateTime debut = tomorrow.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime fin = tomorrow.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<RendezVous> list = rendezVousRepository.findConfirmePourRappelJ1(StatutRendezVous.CONFIRME, debut, fin);
        for (RendezVous rdv : list) {
            try {
                rdvNotificationEmailService.notifyRappelJ1(rdv);
                rdv.setRappelJ1Envoye(true);
            } catch (Exception e) {
                log.error("Erreur rappel J-1 pour {}", rdv.getIdRendezVous(), e);
            }
        }
        if (!list.isEmpty()) {
            rendezVousRepository.saveAll(list);
        }
    }

    /** Toutes les 15 minutes : RDV confirmes dont l'heure de debut est dans ~55–65 minutes. */
    @Scheduled(cron = "0 */15 * * * *", zone = "${forsalaw.notifications.timezone:Africa/Tunis}")
    @SchedulerLock(name = "rdvReminderH1", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    @Transactional
    public void envoyerRappelsH1() {
        ZoneId zone = ZoneId.of(notificationZoneId);
        OffsetDateTime now = OffsetDateTime.now(zone);
        OffsetDateTime debut = now.plusMinutes(55);
        OffsetDateTime fin = now.plusMinutes(65);
        List<RendezVous> list = rendezVousRepository.findConfirmePourRappelH1(StatutRendezVous.CONFIRME, debut, fin);
        for (RendezVous rdv : list) {
            try {
                rdvNotificationEmailService.notifyRappelH1(rdv);
                rdv.setRappelH1Envoye(true);
            } catch (Exception e) {
                log.error("Erreur rappel H-1 pour {}", rdv.getIdRendezVous(), e);
            }
        }
        if (!list.isEmpty()) {
            rendezVousRepository.saveAll(list);
        }
    }
}
