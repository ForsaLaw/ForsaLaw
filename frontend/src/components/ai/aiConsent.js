/**
 * Consentement a l'usage de l'assistant IA.
 *
 * VERSIONNEMENT : toute modification de fond du texte affiche (perimetre, traitement des
 * donnees, nature du service) doit s'accompagner d'une incrementation de AI_CONSENT_VERSION.
 * Un consentement recueilli sur une ancienne formulation ne vaut pas pour la nouvelle : la
 * modale se reaffiche alors automatiquement.
 *
 * PORTEE ACTUELLE (v2) : /api/ai/chat existe reellement. La question est transmise a un
 * backend qui recherche dans le corpus juridique et genere une reponse via un modele
 * auto-heberge (Ollama) -- aucun appel a un prestataire tiers, mais des donnees quittent
 * desormais le navigateur, contrairement a la v1 (demonstration pure, aucun appel reseau).
 * D'ou l'incrementation : un consentement donne sur le texte v1 ("tout reste dans votre
 * navigateur") ne peut pas valoir pour ce nouveau comportement.
 *
 * DETTE CONNUE, PAS CORRIGEE ICI : le stockage reste local (localStorage), alors que ce
 * fichier anticipait deja "des qu'un vrai backend IA existera, le consentement devra etre
 * enregistre COTE SERVEUR (identifiant utilisateur, version acceptee, horodatage)". C'est
 * maintenant le cas, mais l'enregistrement serveur est une fonctionnalite a part entiere
 * (table, endpoint) au-dela de la mise a jour de texte demandee ici -- localStorage reste
 * effacable par l'utilisateur et ne constitue pas une preuve opposable de consentement.
 */

export const AI_CONSENT_VERSION = 2

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
