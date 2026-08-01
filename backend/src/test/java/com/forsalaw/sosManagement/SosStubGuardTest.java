package com.forsalaw.sosManagement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le garde-fou porte sur UNE combinaison : fonctionnalite ouverte au public + promesse
 * simulee. Chaque bouchon pris isolement est legitime ; c'est leur exposition qui ne l'est pas.
 */
class SosStubGuardTest {

    private static SosStubGuard garde(boolean sosActif, boolean paiementSimule,
                                      boolean mobilisationSimulee, boolean acquitte) {
        SosFeatureProperties props = new SosFeatureProperties();
        props.setEnabled(sosActif);
        return new SosStubGuard(props, paiementSimule, mobilisationSimulee, acquitte);
    }

    @Test
    void sosDesactive_lesBouchonsNeGenentPersonne() {
        // Etat par defaut du depot : SOS ferme, bouchons actifs. Rien n'est promis a personne.
        assertThatCode(() -> garde(false, true, true, false).verifier()).doesNotThrowAnyException();
    }

    @Test
    void sosOuvertAvecBouchons_refuseDeDemarrer() {
        assertThatThrownBy(() -> garde(true, true, true, false).verifier())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOS_PAYMENT_STUB=false")
                .hasMessageContaining("SOS_STUBS_ACKNOWLEDGED=true");
    }

    @Test
    void unSeulBouchonSuffitABloquer() {
        // Un paiement reel avec une mobilisation simulee reste un ecran qui ment.
        assertThatThrownBy(() -> garde(true, false, true, false).verifier())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mobilisation des avocats");

        assertThatThrownBy(() -> garde(true, true, false, false).verifier())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paiement");
    }

    @Test
    void acquittementExplicite_autoriseLaDemonstration() {
        assertThatCode(() -> garde(true, true, true, true).verifier()).doesNotThrowAnyException();
    }

    @Test
    void servicesReels_passentSansAcquittement() {
        assertThatCode(() -> garde(true, false, false, false).verifier()).doesNotThrowAnyException();
    }

    @Test
    void leMessage_nommeLesDeuxIssues() {
        // Un garde-fou qui bloque sans dire quoi faire se contourne au hasard.
        assertThatThrownBy(() -> garde(true, true, true, false).verifier())
                .satisfies(e -> assertThat(e.getMessage())
                        .contains("SOS_DISPATCH_STUB=false")
                        .contains("avocats mobilises"));
    }
}
