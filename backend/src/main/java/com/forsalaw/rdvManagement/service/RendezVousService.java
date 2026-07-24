package com.forsalaw.rdvManagement.service;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.rdvManagement.entity.*;
import com.forsalaw.rdvManagement.model.*;
import com.forsalaw.affaireManagement.service.AffaireService;
import com.forsalaw.rdvManagement.repository.RendezVousRepository;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RendezVousService {

    private final RendezVousRepository rendezVousRepository;
    private final UserRepository userRepository;
    private final AvocatRepository avocatRepository;
    private final UserService userService;
    private final AvocatAgendaService avocatAgendaService;
    private final RdvNotificationEmailService rdvNotificationEmailService;
    private final RdvOnlineMeetingService rdvOnlineMeetingService;
    private final AffaireService affaireService;

    private static final Set<StatutRendezVous> STATUTS_OCCUPES = Set.of(StatutRendezVous.PROPOSE, StatutRendezVous.CONFIRME);

    @Transactional
    public RendezVousDTO creerDemandeClient(String emailClient, String idAvocat, CreerDemandeRendezVousRequest request) {
        User client = requireUser(emailClient);
        assertRole(client, RoleUser.client, "Acces reserve aux clients.");

        Avocat avocat = avocatRepository.findById(idAvocat)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouve."));
        if (!avocat.isActif()) {
            throw new IllegalArgumentException("Cet avocat n'est pas disponible.");
        }

        RendezVous rdv = new RendezVous();
        rdv.setIdRendezVous(userService.generateNextId("RDV"));
        rdv.setClient(client);
        rdv.setAvocat(avocat);
        rdv.setStatutRendezVous(StatutRendezVous.EN_ATTENTE);
        rdv.setMotifConsultation(trimOrNull(request.getMotifConsultation()));
        rdv.setTypeRendezVous(request.getTypeRendezVous() != null ? request.getTypeRendezVous() : TypeRendezVous.EN_LIGNE);
        rdv.setCreePar(CreePar.CLIENT);

        rdv = rendezVousRepository.save(rdv);
        rdvNotificationEmailService.notifyDemandeRecue(rdv.getIdRendezVous());
        return toDTO(rdv);
    }

    @Transactional(readOnly = true)
    public Page<RendezVousDTO> listerMesDemandesClient(String emailClient, Pageable pageable) {
        User client = requireUser(emailClient);
        assertRole(client, RoleUser.client, "Acces reserve aux clients.");
        return rendezVousRepository.findByClient_IdOrderByDateMiseAJourDesc(client.getId(), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public RendezVousDTO getMonRendezVousClient(String emailClient, String idRendezVous) {
        User client = requireUser(emailClient);
        assertRole(client, RoleUser.client, "Acces reserve aux clients.");
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        return toDTO(rdv);
    }

    @Transactional(readOnly = true)
    public Page<RendezVousDTO> listerDemandesRecuesAvocat(String emailAvocat, Pageable pageable) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        return rendezVousRepository.findByAvocat_IdOrderByDateMiseAJourDesc(avocat.getId(), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public RendezVousDTO getDemandeRecueAvocat(String emailAvocat, String idRendezVous) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getAvocat().getId().equals(avocat.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        return toDTO(rdv);
    }

    @Transactional
    public RendezVousDTO proposerCreneauAvocat(String emailAvocat, String idRendezVous, ProposerCreneauRequest request) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getAvocat().getId().equals(avocat.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        if (rdv.getStatutRendezVous() == StatutRendezVous.ANNULE) {
            throw new IllegalArgumentException("Ce rendez-vous est deja annule.");
        }

        OffsetDateTime debut = request.getDateHeureDebut();
        OffsetDateTime fin = request.getDateHeureFin();
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            throw new IllegalArgumentException("Le creneau propose est invalide.");
        }
        if (debut.isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("La date proposee doit etre dans le futur.");
        }

        avocatAgendaService.validerPropositionCreneau(avocat.getId(), rdv.getIdRendezVous(), debut, fin);

        boolean conflit = rendezVousRepository.hasConflitCreneauAvocat(
                avocat.getId(),
                STATUTS_OCCUPES,
                rdv.getIdRendezVous(),
                debut,
                fin
        );
        if (conflit) {
            throw new IllegalArgumentException("Ce creneau est deja occupe pour l'avocat.");
        }

        rdv.setDateHeureDebut(debut);
        rdv.setDateHeureFin(fin);
        rdv.setTypeRendezVous(request.getTypeRendezVous() != null ? request.getTypeRendezVous() : rdv.getTypeRendezVous());
        if (rdv.getTypeRendezVous() != TypeRendezVous.EN_LIGNE) {
            // Garantit qu'un RDV presentiel ne conserve jamais d'info de salle en ligne.
            rdv.setMeetingUrl(null);
        }
        rdv.setCommentaireAvocat(trimOrNull(request.getCommentaireAvocat()));
        rdv.setStatutRendezVous(StatutRendezVous.PROPOSE);

        rdv = rendezVousRepository.save(rdv);
        rdvNotificationEmailService.notifyCreneauPropose(rdv.getIdRendezVous());
        return toDTO(rdv);
    }

    @Transactional
    public RendezVousDTO accepterPropositionClient(String emailClient, String idRendezVous) {
        User client = requireUser(emailClient);
        assertRole(client, RoleUser.client, "Acces reserve aux clients.");
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        if (rdv.getStatutRendezVous() != StatutRendezVous.PROPOSE) {
            throw new IllegalArgumentException("Aucune proposition a accepter.");
        }
        if (rdv.getTypeRendezVous() == TypeRendezVous.EN_LIGNE) {
            if (rdv.getMeetingUrl() == null || rdv.getMeetingUrl().isBlank()) {
                rdv.setMeetingUrl(rdvOnlineMeetingService.generateRoomCode(rdv));
            }
        } else {
            rdv.setMeetingUrl(null);
        }
        rdv.setStatutRendezVous(StatutRendezVous.CONFIRME);
        rdv = rendezVousRepository.save(rdv);
        rendezVousRepository.flush();
        affaireService.creerAffaireSiRendezVousConfirme(rdv);
        rdvNotificationEmailService.notifyConfirmation(rdv.getIdRendezVous());
        return toDTO(rdv);
    }

    @Transactional
    public RendezVousDTO refuserPropositionClient(String emailClient, String idRendezVous, RefuserPropositionRequest request) {
        User client = requireUser(emailClient);
        assertRole(client, RoleUser.client, "Acces reserve aux clients.");
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        if (rdv.getStatutRendezVous() != StatutRendezVous.PROPOSE) {
            throw new IllegalArgumentException("Aucune proposition a refuser.");
        }
        rdv.setStatutRendezVous(StatutRendezVous.ANNULE);
        rdv.setRaisonAnnulation(trimOrNull(request.getRaisonRefus()));
        rdv = rendezVousRepository.save(rdv);
        rdvNotificationEmailService.notifyAnnulation(rdv.getIdRendezVous(), true);
        return toDTO(rdv);
    }

    @Transactional
    public RendezVousDTO annulerParClient(String emailClient, String idRendezVous, AnnulerRendezVousRequest request) {
        User client = requireUser(emailClient);
        assertRole(client, RoleUser.client, "Acces reserve aux clients.");
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        if (rdv.getStatutRendezVous() == StatutRendezVous.ANNULE) {
            throw new IllegalArgumentException("Ce rendez-vous est deja annule.");
        }
        rdv.setStatutRendezVous(StatutRendezVous.ANNULE);
        rdv.setRaisonAnnulation(trimOrNull(request.getRaisonAnnulation()));
        rdv = rendezVousRepository.save(rdv);
        rdvNotificationEmailService.notifyAnnulation(rdv.getIdRendezVous(), true);
        return toDTO(rdv);
    }

    @Transactional
    public RendezVousDTO annulerParAvocat(String emailAvocat, String idRendezVous, AnnulerRendezVousRequest request) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getAvocat().getId().equals(avocat.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        if (rdv.getStatutRendezVous() == StatutRendezVous.ANNULE) {
            throw new IllegalArgumentException("Ce rendez-vous est deja annule.");
        }
        rdv.setStatutRendezVous(StatutRendezVous.ANNULE);
        rdv.setRaisonAnnulation(trimOrNull(request.getRaisonAnnulation()));
        rdv = rendezVousRepository.save(rdv);
        rdvNotificationEmailService.notifyAnnulation(rdv.getIdRendezVous(), false);
        return toDTO(rdv);
    }

    @Transactional(readOnly = true)
    public Page<RendezVousDTO> listerPourAdmin(
            String clientUserId,
            String avocatId,
            StatutRendezVous statut,
            OffsetDateTime du,
            OffsetDateTime au,
            Pageable pageable
    ) {
        // Evite les erreurs PostgreSQL de typage quand les parametres date sont null.
        OffsetDateTime borneBasse = du != null ? du : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime borneHaute = au != null ? au : OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
        return rendezVousRepository.findForAdmin(
                        blankToNull(clientUserId),
                        blankToNull(avocatId),
                        statut,
                        borneBasse,
                        borneHaute,
                        pageable
                )
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public RendezVousDTO getPourAdmin(String idRendezVous) {
        return toDTO(requireRendezVous(idRendezVous));
    }

    @Transactional(readOnly = true)
    public RendezVousMeetingAccessDTO getMeetingAccessClient(String emailClient, String idRendezVous) {
        User client = requireUser(emailClient);
        assertRole(client, RoleUser.client, "Acces reserve aux clients.");
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        return toMeetingAccessDTO(rdv);
    }

    @Transactional(readOnly = true)
    public RendezVousMeetingAccessDTO getMeetingAccessAvocat(String emailAvocat, String idRendezVous) {
        Avocat avocat = requireAvocatByEmail(emailAvocat);
        RendezVous rdv = requireRendezVous(idRendezVous);
        if (!rdv.getAvocat().getId().equals(avocat.getId())) {
            throw new AccessDeniedException("Acces refuse.");
        }
        return toMeetingAccessDTO(rdv);
    }

    private RendezVous requireRendezVous(String idRendezVous) {
        return rendezVousRepository.findById(idRendezVous)
                .orElseThrow(() -> new IllegalArgumentException("Rendez-vous non trouve."));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve."));
    }

    private Avocat requireAvocatByEmail(String email) {
        User user = requireUser(email);
        assertRole(user, RoleUser.avocat, "Acces reserve aux avocats.");
        return avocatRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Profil avocat non trouve."));
    }

    private void assertRole(User user, RoleUser role, String message) {
        if (user.getRoleUser() != role) {
            throw new AccessDeniedException(message);
        }
    }

    private String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private RendezVousDTO toDTO(RendezVous rdv) {
        String nomClient = rdv.getClient().getNom() + " " + rdv.getClient().getPrenom();
        String nomAvocat = rdv.getAvocat().getUser().getNom() + " " + rdv.getAvocat().getUser().getPrenom();
        return new RendezVousDTO(
                rdv.getIdRendezVous(),
                rdv.getClient().getId(),
                nomClient,
                rdv.getAvocat().getId(),
                nomAvocat,
                rdv.getStatutRendezVous(),
                rdv.getMotifConsultation(),
                rdv.getDateHeureDebut(),
                rdv.getDateHeureFin(),
                rdv.getTypeRendezVous(),
                rdv.getCreePar(),
                rdv.getRaisonAnnulation(),
                rdv.getCommentaireAvocat(),
                rdv.getMeetingUrl(),
                rdv.getDateCreation(),
                rdv.getDateMiseAJour()
        );
    }

    private RendezVousMeetingAccessDTO toMeetingAccessDTO(RendezVous rdv) {
        if (rdv.getStatutRendezVous() != StatutRendezVous.CONFIRME) {
            throw new IllegalArgumentException("Acces a la salle disponible uniquement pour les rendez-vous confirmes.");
        }
        if (rdv.getTypeRendezVous() != TypeRendezVous.EN_LIGNE) {
            throw new IllegalArgumentException("Ce rendez-vous est en presentiel.");
        }
        if (rdv.getMeetingUrl() == null || rdv.getMeetingUrl().isBlank()) {
            throw new IllegalStateException("Salle en ligne indisponible pour ce rendez-vous.");
        }
        return new RendezVousMeetingAccessDTO(
                rdv.getIdRendezVous(),
                rdv.getMeetingUrl(),
                "/rendezvous/" + rdv.getIdRendezVous() + "/online-room"
        );
    }
}