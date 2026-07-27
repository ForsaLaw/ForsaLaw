package com.forsalaw.ragManagement.instrument;

import java.util.List;
import java.util.Map;

/**
 * Normalise le champ {@code statut} du front-matter legislation-securite (DCAF) vers les
 * trois valeurs de {@code legal_instrument.status}.
 *
 * <p><b>Le vocabulaire francais et le vocabulaire arabe NE se traduisent PAS litteralement
 * l'un vers l'autre.</b> Mesure sur le corpus reel (5528 fichiers, comptage par valeur) :</p>
 * <pre>
 *   FR : en vigueur (1883) / abroge (340) / n'est plus en vigueur (97)
 *   AR : sari al-mafoul (2425) / intaha bihi al-amal (357) / mulgha (126)
 * </pre>
 *
 * <p>Le rapprochement par effectifs comparables donne : {@code انتهى به العمل} (357) avec
 * {@code abrogé} (340) — PAS avec la traduction litterale attendue ("n'est plus en vigueur").
 * Et {@code ملغى} (126, litteralement "annule/abroge") avec {@code n'est plus en vigueur}
 * (97). Traiter {@code ملغى} comme synonyme francais d'"abroge" aurait donc classe la mauvaise
 * moitie du corpus arabe. Sans confirmation d'un locuteur juridique sur cette distinction,
 * mieux vaut suivre les effectifs mesures que la traduction qui parait naturelle.</p>
 */
public final class DcafStatutNormalizer {

    private DcafStatutNormalizer() {
    }

    private static final Map<String, String> VOCABULAIRE = Map.of(
            "en vigueur", LegalInstrumentStatus.EN_VIGUEUR,
            "abrogé", LegalInstrumentStatus.ABROGE,
            "n'est plus en vigueur", LegalInstrumentStatus.NON_EN_VIGUEUR,
            "n’est plus en vigueur", LegalInstrumentStatus.NON_EN_VIGUEUR, // apostrophe typographique
            "ساري المفعول", LegalInstrumentStatus.EN_VIGUEUR,
            "انتهى به العمل", LegalInstrumentStatus.ABROGE,
            "ملغى", LegalInstrumentStatus.NON_EN_VIGUEUR
    );

    /**
     * Normalise une liste de valeurs {@code statut} (le front-matter en porte parfois
     * plusieurs) vers un statut unique.
     *
     * <p>En cas de valeurs contradictoires, la plus restrictive l'emporte : mieux vaut
     * signaler a tort un texte perime comme non confirme en vigueur, que l'inverse.</p>
     *
     * @return le statut normalise, ou {@link LegalInstrumentStatus#INCONNU} si la liste est
     *         vide ou ne contient que des valeurs non reconnues
     */
    public static String normaliser(List<String> valeursStatut) {
        if (valeursStatut == null || valeursStatut.isEmpty()) {
            return LegalInstrumentStatus.INCONNU;
        }

        boolean vuAbroge = false;
        boolean vuNonEnVigueur = false;
        boolean vuEnVigueur = false;

        for (String valeur : valeursStatut) {
            if (valeur == null) {
                continue;
            }
            String normalisee = VOCABULAIRE.get(valeur.trim());
            if (LegalInstrumentStatus.ABROGE.equals(normalisee)) {
                vuAbroge = true;
            } else if (LegalInstrumentStatus.NON_EN_VIGUEUR.equals(normalisee)) {
                vuNonEnVigueur = true;
            } else if (LegalInstrumentStatus.EN_VIGUEUR.equals(normalisee)) {
                vuEnVigueur = true;
            }
        }

        if (vuAbroge) {
            return LegalInstrumentStatus.ABROGE;
        }
        if (vuNonEnVigueur) {
            return LegalInstrumentStatus.NON_EN_VIGUEUR;
        }
        if (vuEnVigueur) {
            return LegalInstrumentStatus.EN_VIGUEUR;
        }
        return LegalInstrumentStatus.INCONNU;
    }
}
