/**
 * Consentement a l'usage de l'assistant IA.
 *
 * VERSIONNEMENT : toute modification de fond du texte affiche (perimetre, traitement des
 * donnees, nature du service) doit s'accompagner d'une incrementation de AI_CONSENT_VERSION.
 * Un consentement recueilli sur une ancienne formulation ne vaut pas pour la nouvelle : la
 * modale se reaffiche alors automatiquement.
 *
 * PORTEE ACTUELLE (v1) : l'assistant est une DEMONSTRATION. Aucun appel reseau n'est emis,
 * aucune donnee saisie ne quitte le navigateur, aucun traitement par un tiers n'a lieu.
 * Le stockage local suffit donc : il n'y a pas de traitement a prouver.
 *
 * DES QU'UN VRAI BACKEND IA EXISTERA : le consentement devra etre enregistre COTE SERVEUR
 * (identifiant utilisateur, version acceptee, horodatage). Un localStorage est effacable par
 * l'utilisateur et ne constitue pas une preuve opposable du consentement.
 */

export const AI_CONSENT_VERSION = 1

const CLE_STOCKAGE = 'forsalaw.ai.consent'

/** Vrai si l'utilisateur a accepte la version courante du texte. */
export function aConsentiVersionCourante() {
  try {
    const brut = window.localStorage.getItem(CLE_STOCKAGE)
    if (!brut) return false
    const enregistre = JSON.parse(brut)
    return enregistre?.version === AI_CONSENT_VERSION
  } catch {
    // localStorage indisponible (navigation privee, stockage desactive) : on redemande.
    return false
  }
}

/** Enregistre l'acceptation de la version courante. */
export function enregistrerConsentement() {
  try {
    window.localStorage.setItem(
      CLE_STOCKAGE,
      JSON.stringify({ version: AI_CONSENT_VERSION, acceptedAt: new Date().toISOString() }),
    )
  } catch {
    // Echec d'ecriture : la modale se reaffichera a la prochaine visite, ce qui est le
    // comportement sur — mieux vaut redemander que supposer un accord.
  }
}

/** Retire le consentement (utile pour les tests et un futur ecran de confidentialite). */
export function revoquerConsentement() {
  try {
    window.localStorage.removeItem(CLE_STOCKAGE)
  } catch {
    /* rien a faire */
  }
}
