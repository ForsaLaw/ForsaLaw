package com.forsalaw.ragManagement.chat.routing;

import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interpretation de la sortie du modele.
 *
 * <p>Le point critique est le REJET : une etiquette hors referentiel doit produire « aucun
 * domaine », jamais le domaine le plus ressemblant. Recommander des avocats de la mauvaise
 * matiere sur la foi d'une correspondance approximative serait invisible pour l'utilisateur.</p>
 */
class OllamaDomaineClassifierTest {

    private final OllamaDomaineClassifier classifier =
            new OllamaDomaineClassifier(new com.fasterxml.jackson.databind.ObjectMapper(), "http://localhost:11434", "modele-test", 8);

    @Test
    void etiquetteExacte_estReconnue() {
        assertThat(classifier.interpreter("DROIT_PENAL")).contains(DomaineJuridique.DROIT_PENAL);
    }

    @Test
    void espacesEtPonctuationParasites_sontToleres() {
        // Le modele ajoute regulierement guillemets, point final ou espaces malgre la consigne.
        assertThat(classifier.interpreter("  \"DROIT_PENAL\".  ")).contains(DomaineJuridique.DROIT_PENAL);
        assertThat(classifier.interpreter("droit penal")).contains(DomaineJuridique.DROIT_PENAL);
    }

    @Test
    void reponseAucun_neDonneAucunDomaine() {
        assertThat(classifier.interpreter("AUCUN")).isEmpty();
    }

    @Test
    void etiquetteHorsReferentiel_estRejetee() {
        // DROIT_FAMILLE n'existe PAS dans DomaineJuridique (la famille releve de DROIT_PRIVE).
        // Le rejet est volontaire : proposer DROIT_PRIVE « parce que ca s'en rapproche »
        // fabriquerait une recommandation que rien ne justifie.
        assertThat(classifier.interpreter("DROIT_FAMILLE")).isEmpty();
        assertThat(classifier.interpreter("DROIT_IMMOBILIER")).isEmpty();
    }

    @Test
    void phraseComplete_estRejetee() {
        assertThat(classifier.interpreter(
                "Cette question releve manifestement du droit penal tunisien, car elle concerne "
                        + "une infraction reprimee par le code penal.")).isEmpty();
    }

    @Test
    void reponseVideOuNulle_estRejetee() {
        assertThat(classifier.interpreter(null)).isEmpty();
        assertThat(classifier.interpreter("   ")).isEmpty();
    }
}
