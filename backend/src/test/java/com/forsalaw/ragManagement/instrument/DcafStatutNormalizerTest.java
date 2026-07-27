package com.forsalaw.ragManagement.instrument;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vocabulaire {@code statut} de legislation-securite (DCAF), francais et arabe.
 *
 * <p>Le point non-evident que ce test fige : {@code انتهى به العمل} s'aligne sur
 * {@code abrogé} et NON sur sa traduction litterale "n'est plus en vigueur" (mesure par
 * comptage sur 5528 fichiers, voir {@link DcafStatutNormalizer}). Une regression qui
 * "corrigerait" ce mapping vers la traduction litterale casserait le classement de 357
 * documents arabes sans qu'aucun test ne s'en apercoive autrement.</p>
 */
class DcafStatutNormalizerTest {

    @Test
    void vocabulaireFrancais() {
        assertThat(DcafStatutNormalizer.normaliser(List.of("en vigueur")))
                .isEqualTo(LegalInstrumentStatus.EN_VIGUEUR);
        assertThat(DcafStatutNormalizer.normaliser(List.of("abrogé")))
                .isEqualTo(LegalInstrumentStatus.ABROGE);
        assertThat(DcafStatutNormalizer.normaliser(List.of("n'est plus en vigueur")))
                .isEqualTo(LegalInstrumentStatus.NON_EN_VIGUEUR);
    }

    @Test
    void vocabulaireArabe_pairageParEffectifsPasParTraductionLitterale() {
        assertThat(DcafStatutNormalizer.normaliser(List.of("ساري المفعول")))
                .isEqualTo(LegalInstrumentStatus.EN_VIGUEUR);
        // Le piege : la traduction litterale suggererait NON_EN_VIGUEUR.
        assertThat(DcafStatutNormalizer.normaliser(List.of("انتهى به العمل")))
                .isEqualTo(LegalInstrumentStatus.ABROGE);
        assertThat(DcafStatutNormalizer.normaliser(List.of("ملغى")))
                .isEqualTo(LegalInstrumentStatus.NON_EN_VIGUEUR);
    }

    @Test
    void valeurInconnue_neDeclencheAucuneErreur() {
        assertThat(DcafStatutNormalizer.normaliser(List.of("valeur jamais vue")))
                .isEqualTo(LegalInstrumentStatus.INCONNU);
    }

    @Test
    void listeVideOuNulle_estInconnue() {
        assertThat(DcafStatutNormalizer.normaliser(List.of())).isEqualTo(LegalInstrumentStatus.INCONNU);
        assertThat(DcafStatutNormalizer.normaliser(null)).isEqualTo(LegalInstrumentStatus.INCONNU);
    }

    @Test
    void valeursContradictoires_laPlusRestrictiveGagne() {
        // Mieux vaut signaler a tort un texte perime comme non confirme en vigueur que
        // l'inverse : un faux "en vigueur" est l'erreur la plus couteuse pour un avocat.
        assertThat(DcafStatutNormalizer.normaliser(List.of("en vigueur", "abrogé")))
                .isEqualTo(LegalInstrumentStatus.ABROGE);
        assertThat(DcafStatutNormalizer.normaliser(List.of("en vigueur", "n'est plus en vigueur")))
                .isEqualTo(LegalInstrumentStatus.NON_EN_VIGUEUR);
    }

    @Test
    void apostropheTypographique_estAcceptee() {
        assertThat(DcafStatutNormalizer.normaliser(List.of("n’est plus en vigueur")))
                .isEqualTo(LegalInstrumentStatus.NON_EN_VIGUEUR);
    }
}
