package com.forsalaw.sosManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Refuse la combinaison « SOS ouvert au public + bouchons actifs ».
 *
 * <p><b>Le danger n'est pas le bouchon, c'est le bouchon invisible.</b> Pris separement,
 * chacun est raisonnable : {@code StubPaymentService} marque tout signalement paye au bout de
 * deux secondes, {@code StubDispatchService} ne previent aucun avocat. Ensemble et en ligne,
 * ils produisent un ecran qui annonce « avocats mobilises » a un proche dont la garde a vue
 * est reelle, alors que personne n'a ete prevenu et que rien n'a ete encaisse. Un simple
 * avertissement au demarrage ne suffit pas : il se noie dans les journaux, et la valeur par
 * defaut des deux bouchons est {@code true}.</p>
 *
 * <p><b>Pourquoi pas un test sur le profil actif.</b> L'application n'en declare aucun — ni
 * {@code spring.profiles.active}, ni {@code application-prod.properties}. Un garde-fou fonde
 * sur « suis-je en production ? » serait donc toujours faux, c'est-a-dire inexistant. Le
 * critere retenu ne depend d'aucune convention de deploiement : la fonctionnalite est-elle
 * ouverte au public alors que ce qu'elle promet est simule ?</p>
 *
 * <p>La levee de bouclier reste possible pour une demonstration de bout en bout, mais elle
 * doit etre ECRITE ({@code forsalaw.sos.stubs-acknowledged=true}) : on ne peut plus y tomber
 * en oubliant une variable d'environnement.</p>
 */
@Component
@Slf4j
public class SosStubGuard {

    private final SosFeatureProperties fonctionnalite;
    private final boolean paiementSimule;
    private final boolean mobilisationSimulee;
    private final boolean acquitte;

    public SosStubGuard(
            SosFeatureProperties fonctionnalite,
            @Value("${forsalaw.sos.payment.stub-enabled:true}") boolean paiementSimule,
            @Value("${forsalaw.sos.dispatch.stub-enabled:true}") boolean mobilisationSimulee,
            @Value("${forsalaw.sos.stubs-acknowledged:false}") boolean acquitte
    ) {
        this.fonctionnalite = fonctionnalite;
        this.paiementSimule = paiementSimule;
        this.mobilisationSimulee = mobilisationSimulee;
        this.acquitte = acquitte;
    }

    /**
     * Verifie apres le demarrage complet, et non dans le constructeur : l'echec est ainsi
     * rapporte comme une erreur applicative lisible plutot que comme un echec de creation de
     * bean noye dans une trace de contexte Spring.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifier() {
        if (!fonctionnalite.isEnabled()) {
            return;
        }
        if (!paiementSimule && !mobilisationSimulee) {
            log.info("SOS : ouvert au public, paiement et mobilisation reels.");
            return;
        }

        String simules = (paiementSimule ? "paiement" : "")
                + (paiementSimule && mobilisationSimulee ? " et " : "")
                + (mobilisationSimulee ? "mobilisation des avocats" : "");

        if (acquitte) {
            log.warn("SOS : ouvert au public avec {} SIMULE(S). Acquitte explicitement par "
                    + "forsalaw.sos.stubs-acknowledged=true. Aucun avocat n'est reellement "
                    + "prevenu ; a ne jamais laisser dans cet etat face a de vrais usagers.", simules);
            return;
        }

        throw new IllegalStateException("""
                SOS Arrestation est active (SOS_ARREST_ENABLED=true) alors que %s est simule.

                Dans cet etat, un proche qui signale une garde a vue voit « avocats mobilises »
                sans qu'aucun avocat soit prevenu, et sans qu'aucun paiement soit encaisse.

                Deux issues :
                  - brancher les services reels : SOS_PAYMENT_STUB=false et SOS_DISPATCH_STUB=false ;
                  - ou, pour une demonstration hors production uniquement, acquitter le risque
                    explicitement : SOS_STUBS_ACKNOWLEDGED=true.
                """.formatted(simules));
    }
}
