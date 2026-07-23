package com.forsalaw.rdvManagement.service;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.rdvManagement.entity.*;
import com.forsalaw.rdvManagement.model.*;
import com.forsalaw.rdvManagement.repository.AvocatAgendaConfigRepository;
import com.forsalaw.rdvManagement.repository.AvocatAgendaExceptionRepository;
import com.forsalaw.rdvManagement.repository.AvocatPlageRecurrenteRepository;
import com.forsalaw.rdvManagement.repository.RendezVousRepository;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AvocatAgendaService {

    private static final Set<StatutRendezVous> STATUTS_OCCUPES = Set.of(StatutRendezVous.PROPOSE, StatutRendezVous.CONFIRME);

    private final AvocatAgendaConfigRepository agendaConfigRepository;
    private final AvocatPlageRecurrenteRepository plageRepository;
    private final AvocatAgendaExceptionRepository exceptionRepository;
    private final RendezVousRepository rendezVousRepository;
    private final AvocatRepository avocatRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public AgendaCompletDTO getAgendaComplet(String emailAvocat) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        AvocatAgendaConfig cfg = ensureConfig(avocat);
        return toAgendaCompletDTO(cfg);
    }

    @Transactional
    public AgendaCompletDTO updateAgendaConfig(String emailAvocat, UpdateAgendaConfigRequest request) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        AvocatAgendaConfig cfg = ensureConfig(avocat);
        if (request.getZoneId() != null && !request.getZoneId().isBlank()) {
            try {
                ZoneId.of(request.getZoneId().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Fuseau horaire invalide (ex. Africa/Tunis, Europe/Paris).");
            }
            cfg.setZoneId(request.getZoneId().trim());
        }
        if (request.getDureeCreneauMinutes() != null) {
            if (request.getDureeCreneauMinutes() < 5 || request.getDureeCreneauMinutes() > 480) {
                throw new IllegalArgumentException("La duree du creneau doit etre entre 5 et 480 minutes.");
            }
            cfg.setDureeCreneauMinutes(request.getDureeCreneauMinutes());
        }
        if (request.getBufferMinutes() != null) {
            if (request.getBufferMinutes() < 0 || request.getBufferMinutes() > 120) {
                throw new IllegalArgumentException("La marge doit etre entre 0 et 120 minutes.");
            }
            cfg.setBufferMinutes(request.getBufferMinutes());
        }
        if (request.getAgendaActif() != null) {
            cfg.setAgendaActif(request.getAgendaActif());
        }
        agendaConfigRepository.save(cfg);
        return toAgendaCompletDTO(cfg);
    }

    @Transactional
    public PlageRecurrenteDTO ajouterPlage(String emailAvocat, CreatePlageRecurrenteRequest request) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        ensureConfig(avocat);
        if (request.getDayOfWeek() == null || request.getHeureDebut() == null || request.getHeureFin() == null) {
            throw new IllegalArgumentException("dayOfWeek, heureDebut et heureFin sont requis.");
        }
        int dow = request.getDayOfWeek();
        if (dow < 1 || dow > 7) {
            throw new IllegalArgumentException("dayOfWeek doit etre entre 1 (lundi) et 7 (dimanche).");
        }
        if (!request.getHeureFin().isAfter(request.getHeureDebut())) {
            throw new IllegalArgumentException("heureFin doit etre apres heureDebut.");
        }
        AvocatPlageRecurrente p = new AvocatPlageRecurrente();
        p.setId(userService.generateNextId("PAG"));
        p.setAvocat(avocat);
        p.setDayOfWeek(dow);
        p.setHeureDebut(request.getHeureDebut());
        p.setHeureFin(request.getHeureFin());
        plageRepository.save(p);
        return toPlageDTO(p);
    }

    @Transactional
    public void supprimerPlage(String emailAvocat, String idPlage) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        plageRepository.deleteByAvocat_IdAndId(avocat.getId(), idPlage);
    }

    @Transactional
    public AgendaExceptionDTO ajouterException(String emailAvocat, CreateAgendaExceptionRequest request) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        ensureConfig(avocat);
        if (request.getDateDebut() == null || request.getDateFin() == null) {
            throw new IllegalArgumentException("dateDebut et dateFin sont requis.");
        }
        if (request.getDateFin().isBefore(request.getDateDebut())) {
            throw new IllegalArgumentException("dateFin doit etre >= dateDebut.");
        }
        AvocatAgendaException ex = new AvocatAgendaException();
        ex.setId(userService.generateNextId("EXC"));
        ex.setAvocat(avocat);
        ex.setDateDebut(request.getDateDebut());
        ex.setDateFin(request.getDateFin());
        ex.setLibelle(trimOrNull(request.getLibelle()));
        exceptionRepository.save(ex);
        return toExceptionDTO(ex);
    }

    @Transactional
    public void supprimerException(String emailAvocat, String idException) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        exceptionRepository.deleteByAvocat_IdAndId(avocat.getId(), idException);
    }

    @Transactional(readOnly = true)
    public CreneauxDisponiblesResponse listerCreneauxDisponiblesPourAvocat(String idAvocat, OffsetDateTime debut, OffsetDateTime fin) {
        Avocat avocat = avocatRepository.findById(idAvocat)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouve."));
        if (!avocat.isActif()) {
            throw new IllegalArgumentException("Cet avocat n'est pas disponible.");
        }
        AvocatAgendaConfig cfg = agendaConfigRepository.findById(avocat.getId()).orElse(null);
        if (cfg == null || !cfg.isAgendaActif()) {
            return new CreneauxDisponiblesResponse(
                    cfg != null ? cfg.getZoneId() : "Africa/Tunis",
                    cfg != null ? cfg.getDureeCreneauMinutes() : 45,
                    cfg != null ? cfg.getBufferMinutes() : 10,
                    false,
                    List.of()
            );
        }
        ZoneId zone = ZoneId.of(cfg.getZoneId());
        if (!fin.isAfter(debut)) {
            throw new IllegalArgumentException("La date fin doit etre apres la date debut.");
        }
        if (ChronoUnit.DAYS.between(debut, fin) > 62) {
            throw new IllegalArgumentException("L'intervalle ne peut pas depasser 62 jours.");
        }
        List<CreneauDisponibleDTO> slots = calculerCreneaux(avocat.getId(), cfg, zone, debut, fin);
        return new CreneauxDisponiblesResponse(
                cfg.getZoneId(),
                cfg.getDureeCreneauMinutes(),
                cfg.getBufferMinutes(),
                true,
                slots
        );
    }

    @Transactional(readOnly = true)
    public AgendaCompletDTO getAgendaCompletPublic(String idAvocat) {
        Avocat avocat = avocatRepository.findById(idAvocat)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouve."));
        if (!avocat.isActif()) {
            throw new IllegalArgumentException("Cet avocat n'est pas disponible.");
        }
        AvocatAgendaConfig cfg = agendaConfigRepository.findById(avocat.getId()).orElse(null);
        if (cfg == null) {
            return new AgendaCompletDTO("Africa/Tunis", 45, 10, false, List.of(), List.of());
        }
        return toAgendaCompletDTO(cfg);
    }

    /**
     * Si l'agenda est actif et qu'il existe au moins une plage : valide le creneau propose.
     */
    public void validerPropositionCreneau(String avocatId, String excludeRdvId, OffsetDateTime debut, OffsetDateTime fin) {
        AvocatAgendaConfig cfg = agendaConfigRepository.findById(avocatId).orElse(null);
        if (cfg == null || !cfg.isAgendaActif()) {
            return;
        }
        List<AvocatPlageRecurrente> plages = plageRepository.findByAvocat_IdOrderByDayOfWeekAscHeureDebutAsc(avocatId);
        if (plages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Agenda actif : definissez au moins une plage horaire recurrente, ou desactivez l'agenda."
            );
        }
        ZoneId zone = ZoneId.of(cfg.getZoneId());
        int duree = cfg.getDureeCreneauMinutes();
        int buffer = cfg.getBufferMinutes();
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            throw new IllegalArgumentException("Le creneau propose est invalide.");
        }
        long minutes = ChronoUnit.MINUTES.between(debut, fin);
        if (minutes != duree) {
            throw new IllegalArgumentException("La duree du creneau doit etre de " + duree + " minutes (agenda actif).");
        }
        ZonedDateTime zd = debut.atZoneSameInstant(zone);
        LocalDate day = zd.toLocalDate();
        LocalTime t0 = zd.toLocalTime();
        LocalTime t1 = fin.atZoneSameInstant(zone).toLocalTime();
        if (estJourEnException(day, exceptionRepository.findByAvocat_IdOrderByDateDebutAsc(avocatId))) {
            throw new IllegalArgumentException("Ce jour est marque comme indisponible (conge / exception).");
        }
        if (!estDansPlages(zd.getDayOfWeek().getValue(), t0, t1, plages)) {
            throw new IllegalArgumentException("Le creneau ne correspond pas aux plages horaires definies.");
        }
        List<RendezVous> occupes = rendezVousRepository.findOccupyingForAvocatInRange(
                avocatId, STATUTS_OCCUPES, debut.minusDays(1), fin.plusDays(1)
        );
        for (RendezVous r : occupes) {
            if (excludeRdvId != null && excludeRdvId.equals(r.getIdRendezVous())) {
                continue;
            }
            if (r.getDateHeureDebut() == null || r.getDateHeureFin() == null) {
                continue;
            }
            OffsetDateTime rEndBuf = r.getDateHeureFin().plusMinutes(buffer);
            if (debut.isBefore(rEndBuf) && fin.isAfter(r.getDateHeureDebut())) {
                throw new IllegalArgumentException("Ce creneau chevauche un autre rendez-vous (avec marge de " + buffer + " min).");
            }
        }
    }

    private List<CreneauDisponibleDTO> calculerCreneaux(
            String avocatId,
            AvocatAgendaConfig cfg,
            ZoneId zone,
            OffsetDateTime debut,
            OffsetDateTime fin
    ) {
        int duree = cfg.getDureeCreneauMinutes();
        int buffer = cfg.getBufferMinutes();
        List<AvocatPlageRecurrente> plages = plageRepository.findByAvocat_IdOrderByDayOfWeekAscHeureDebutAsc(avocatId);
        if (plages.isEmpty()) {
            return List.of();
        }
        List<AvocatAgendaException> exceptions = exceptionRepository.findByAvocat_IdOrderByDateDebutAsc(avocatId);
        ZonedDateTime zStart = debut.atZoneSameInstant(zone);
        ZonedDateTime zEnd = fin.atZoneSameInstant(zone);
        LocalDate first = zStart.toLocalDate();
        LocalDate last = zEnd.toLocalDate();
        OffsetDateTime now = OffsetDateTime.now(zone);

        List<RendezVous> rdvs = rendezVousRepository.findOccupyingForAvocatInRange(
                avocatId, STATUTS_OCCUPES, debut.minusHours(24), fin.plusHours(24)
        );

        List<CreneauDisponibleDTO> out = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (estJourEnException(day, exceptions)) {
                continue;
            }
            int dow = day.getDayOfWeek().getValue();
            List<AvocatPlageRecurrente> jourPlages = plages.stream()
                    .filter(p -> p.getDayOfWeek() == dow)
                    .toList();
            for (AvocatPlageRecurrente plage : jourPlages) {
                ZonedDateTime plageStart = ZonedDateTime.of(day, plage.getHeureDebut(), zone);
                ZonedDateTime plageEnd = ZonedDateTime.of(day, plage.getHeureFin(), zone);
                if (!plageEnd.isAfter(plageStart)) {
                    continue;
                }
                List<ZSpan> busy = buildBusySpansForDay(day, zone, buffer, rdvs);
                busy.sort(Comparator.comparing(ZSpan::start));
                List<ZSpan> merged = mergeSpans(busy);

                ZonedDateTime cursor = plageStart;
                while (cursor.plusMinutes(duree).compareTo(plageEnd) <= 0) {
                    ZonedDateTime slotEnd = cursor.plusMinutes(duree);
                    if (slotEnd.isAfter(plageEnd)) {
                        break;
                    }
                    ZSpan slot = new ZSpan(cursor, slotEnd);
                    ZSpan overlap = findFirstOverlap(slot, merged);
                    if (overlap != null) {
                        cursor = overlap.end();
                        continue;
                    }
                    OffsetDateTime slotStart = cursor.toOffsetDateTime();
                    OffsetDateTime slotEndOdt = slotEnd.toOffsetDateTime();
                    if (slotEndOdt.isBefore(debut) || slotStart.isAfter(fin)) {
                        cursor = slotEnd.plusMinutes(buffer);
                        continue;
                    }
                    if (!slotStart.isBefore(now)) {
                        out.add(new CreneauDisponibleDTO(slotStart, slotEndOdt));
                    }
                    cursor = slotEnd.plusMinutes(buffer);
                }
            }
        }
        return out;
    }

    private static ZSpan findFirstOverlap(ZSpan slot, List<ZSpan> merged) {
        for (ZSpan m : merged) {
            if (slot.start().isBefore(m.end()) && slot.end().isAfter(m.start())) {
                return m;
            }
        }
        return null;
    }

    private List<ZSpan> buildBusySpansForDay(LocalDate day, ZoneId zone, int bufferMinutes, List<RendezVous> rdvs) {
        ZonedDateTime dayStart = day.atStartOfDay(zone);
        ZonedDateTime dayEnd = day.plusDays(1).atStartOfDay(zone);
        List<ZSpan> raw = new ArrayList<>();
        for (RendezVous r : rdvs) {
            if (r.getDateHeureDebut() == null || r.getDateHeureFin() == null) {
                continue;
            }
            ZonedDateTime a = r.getDateHeureDebut().atZoneSameInstant(zone);
            ZonedDateTime b = r.getDateHeureFin().atZoneSameInstant(zone).plusMinutes(bufferMinutes);
            ZonedDateTime clipStart = a.isBefore(dayStart) ? dayStart : a;
            ZonedDateTime clipEnd = b.isAfter(dayEnd) ? dayEnd : b;
            if (clipStart.isBefore(clipEnd)) {
                raw.add(new ZSpan(clipStart, clipEnd));
            }
        }
        return raw;
    }

    private static List<ZSpan> mergeSpans(List<ZSpan> raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        List<ZSpan> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.comparing(ZSpan::start));
        List<ZSpan> out = new ArrayList<>();
        ZSpan cur = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            ZSpan n = sorted.get(i);
            if (!n.start().isAfter(cur.end())) {
                cur = new ZSpan(cur.start(), max(cur.end(), n.end()));
            } else {
                out.add(cur);
                cur = n;
            }
        }
        out.add(cur);
        return out;
    }

    private static ZonedDateTime max(ZonedDateTime a, ZonedDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private boolean estDansPlages(int dayOfWeekValue, LocalTime t0, LocalTime t1, List<AvocatPlageRecurrente> plages) {
        for (AvocatPlageRecurrente p : plages) {
            if (p.getDayOfWeek() != dayOfWeekValue) {
                continue;
            }
            if (!t0.isBefore(p.getHeureDebut()) && !t1.isAfter(p.getHeureFin())) {
                return true;
            }
        }
        return false;
    }

    private boolean estJourEnException(LocalDate day, List<AvocatAgendaException> exceptions) {
        for (AvocatAgendaException e : exceptions) {
            if (!day.isBefore(e.getDateDebut()) && !day.isAfter(e.getDateFin())) {
                return true;
            }
        }
        return false;
    }

    private AvocatAgendaConfig ensureConfig(Avocat avocat) {
        return agendaConfigRepository.findById(avocat.getId()).orElseGet(() -> {
            AvocatAgendaConfig c = new AvocatAgendaConfig();
            c.setAvocat(avocat);
            c.setZoneId("Africa/Tunis");
            c.setDureeCreneauMinutes(45);
            c.setBufferMinutes(10);
            c.setAgendaActif(false);
            return agendaConfigRepository.save(c);
        });
    }

    private AgendaCompletDTO toAgendaCompletDTO(AvocatAgendaConfig cfg) {
        String id = cfg.getAvocatId();
        List<PlageRecurrenteDTO> plages = plageRepository.findByAvocat_IdOrderByDayOfWeekAscHeureDebutAsc(id).stream()
                .map(this::toPlageDTO)
                .toList();
        List<AgendaExceptionDTO> ex = exceptionRepository.findByAvocat_IdOrderByDateDebutAsc(id).stream()
                .map(this::toExceptionDTO)
                .toList();
        return new AgendaCompletDTO(
                cfg.getZoneId(),
                cfg.getDureeCreneauMinutes(),
                cfg.getBufferMinutes(),
                cfg.isAgendaActif(),
                plages,
                ex
        );
    }

    private PlageRecurrenteDTO toPlageDTO(AvocatPlageRecurrente p) {
        return new PlageRecurrenteDTO(p.getId(), p.getDayOfWeek(), p.getHeureDebut(), p.getHeureFin());
    }

    private AgendaExceptionDTO toExceptionDTO(AvocatAgendaException e) {
        return new AgendaExceptionDTO(e.getId(), e.getDateDebut(), e.getDateFin(), e.getLibelle());
    }

    private Avocat requireAvocatByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve."));
        if (user.getRoleUser() != RoleUser.avocat) {
            throw new AccessDeniedException("Acces reserve aux avocats.");
        }
        return avocatRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Profil avocat non trouve."));
    }

    private String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record ZSpan(ZonedDateTime start, ZonedDateTime end) {}
}
