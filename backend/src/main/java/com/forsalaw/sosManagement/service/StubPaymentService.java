package com.forsalaw.sosManagement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;

/**
 * Bouchon de paiement : marque le signalement comme regle apres un court delai.
 *
 * <p><b>Aucun encaissement reel n'a lieu.</b> Il n'existe ni compte marchand ni cle d'API a ce
 * stade ; ce bouchon sert a eprouver le flux complet (saisie -> reglement -> mobilisation) sans
 * bloquer sur une integration bancaire.</p>
 *
 * <p>Le delai est planifie, jamais obtenu par {@code Thread.sleep} : dormir immobiliserait le
 * thread de la requete pendant deux secondes, sur un endpoint concu pour repondre dans
 * l'urgence. Le declarant recoit sa confirmation immediatement, le reglement se conclut ensuite.</p>
 *
 * <p><b>A ne jamais activer en production.</b> Ce bouchon declare tout signalement comme paye :
 * la propriete est explicitement lue et journalisee au demarrage pour que sa presence ne passe
 * pas inapercue.</p>
 */
@Service
@ConditionalOnProperty(name = "forsalaw.sos.payment.stub-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class StubPaymentService implements PaymentService {

    private final TaskScheduler scheduler;
    // ObjectProvider : SosArrestService depend de PaymentService, qui dependrait ici de
    // SosArrestService — une resolution paresseuse rompt ce cycle de construction.
    private final ObjectProvider<SosArrestService> sosArrestServiceProvider;
    private final Duration delai;

    public StubPaymentService(
            TaskScheduler scheduler,
            ObjectProvider<SosArrestService> sosArrestServiceProvider,
            @Value("${forsalaw.sos.payment.stub-delay-seconds:2}") int delaiSecondes
    ) {
        this.scheduler = scheduler;
        this.sosArrestServiceProvider = sosArrestServiceProvider;
        this.delai = Duration.ofSeconds(delaiSecondes);
        log.warn("SOS : service de paiement SIMULE actif — tout signalement sera marque paye "
                + "apres {} s, sans aucun encaissement reel.", delaiSecondes);
    }

    @Override
    public void demarrerPaiement(String sosArrestId) {
        log.info("SOS : simulation de paiement pour le signalement {} ({} s)...", sosArrestId, delai.toSeconds());
        scheduler.schedule(
                () -> {
                    try {
                        sosArrestServiceProvider.getObject().confirmerPaiement(sosArrestId);
                    } catch (RuntimeException e) {
                        // Sur un thread planifie, une exception non capturee disparait
                        // silencieusement : le signalement resterait bloque en PENDING sans trace.
                        log.error("SOS : la confirmation simulee du paiement {} a echoue.", sosArrestId, e);
                    }
                },
                Instant.now().plus(delai)
        );
    }
}
