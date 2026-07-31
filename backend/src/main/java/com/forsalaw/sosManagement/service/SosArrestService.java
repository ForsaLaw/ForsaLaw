package com.forsalaw.sosManagement.service;

import com.forsalaw.sosManagement.entity.SosArrest;
import com.forsalaw.sosManagement.entity.StatutDispatch;
import com.forsalaw.sosManagement.entity.StatutPaiement;
import com.forsalaw.sosManagement.model.SosArrestRequest;
import com.forsalaw.sosManagement.model.SosArrestResponse;
import com.forsalaw.sosManagement.repository.SosArrestRepository;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.IdSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Prise en charge d'un signalement SOS : enregistrement, reglement, mobilisation.
 *
 * <p><b>Le signalement est enregistre AVANT toute tentative de reglement.</b> Si la passerelle
 * de paiement est indisponible, la demande existe et reste traitable a la main ; l'inverse
 * perdrait le signalement d'une privation de liberte pour un incident de facturation.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SosArrestService {

    private final SosArrestRepository sosArrestRepository;
    private final UserRepository userRepository;
    private final IdSequenceService idSequenceService;
    private final PaymentService paymentService;
    private final DispatchService dispatchService;

    /**
     * @param emailDeclarant email du declarant connecte, ou {@code null} : le signalement est
     *     accepte sans compte, un proche devant pouvoir alerter sans passer par une inscription.
     */
    @Transactional
    public SosArrestResponse enregistrer(SosArrestRequest requete, String emailDeclarant) {
        SosArrest signalement = new SosArrest();
        signalement.setId(idSequenceService.generateNextId("SOS"));
        signalement.setNomDetenu(requete.getNomDetenu().strip());
        signalement.setLieuArrestation(requete.getLieuArrestation().strip());
        signalement.setDateHeureArrestation(requete.getDateHeureArrestation());
        signalement.setContactUrgence(requete.getContactUrgence().strip());
        signalement.setDetails(requete.getDetails());

        if (emailDeclarant != null) {
            userRepository.findByEmail(emailDeclarant)
                    .ifPresent(u -> signalement.setUserId(u.getId()));
        }

        SosArrest enregistre = sosArrestRepository.save(signalement);
        log.warn("SOS : nouveau signalement {} — {} a {}.",
                enregistre.getId(), enregistre.getNomDetenu(), enregistre.getLieuArrestation());

        // Declenche apres l'enregistrement : le reglement est asynchrone, il ne doit pas retarder
        // la reponse au declarant.
        paymentService.demarrerPaiement(enregistre.getId());

        return SosArrestResponse.depuis(enregistre);
    }

    /**
     * Prend acte du reglement puis mobilise les avocats.
     *
     * <p>Idempotente : une passerelle de paiement peut notifier deux fois le meme reglement, et
     * une double notification ne doit pas declencher une seconde mobilisation.</p>
     */
    @Transactional
    public void confirmerPaiement(String sosArrestId) {
        SosArrest signalement = sosArrestRepository.findById(sosArrestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Signalement introuvable : " + sosArrestId));

        if (signalement.getStatutPaiement() == StatutPaiement.PAID) {
            log.info("SOS : paiement du signalement {} deja confirme, notification ignoree.", sosArrestId);
            return;
        }

        signalement.marquerPaye();

        boolean transmis = dispatchService.mobiliserAvocats(signalement);
        if (transmis) {
            signalement.marquerDispatche();
        } else {
            // Le paiement reste PAID : il a bien eu lieu. C'est la mobilisation qui a echoue, et
            // les distinguer est ce qui permet de reprendre la seule etape defaillante.
            signalement.setStatutDispatch(StatutDispatch.FAILED);
            log.error("SOS : signalement {} paye mais NON transmis — reprise manuelle requise.", sosArrestId);
        }
        sosArrestRepository.save(signalement);
    }

    @Transactional(readOnly = true)
    public SosArrestResponse consulter(String sosArrestId) {
        return sosArrestRepository.findById(sosArrestId)
                .map(SosArrestResponse::depuis)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Signalement introuvable : " + sosArrestId));
    }

    @Transactional(readOnly = true)
    public List<SosArrestResponse> mesSignalements(String emailDeclarant) {
        return userRepository.findByEmail(emailDeclarant)
                .map(u -> sosArrestRepository.findByUserIdOrderByCreatedAtDesc(u.getId()).stream()
                        .map(SosArrestResponse::depuis)
                        .toList())
                .orElseGet(List::of);
    }
}
