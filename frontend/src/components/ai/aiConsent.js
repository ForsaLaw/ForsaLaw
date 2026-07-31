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
 * STOCKAGE : le consentement est desormais enregistre COTE SERVEUR (users.ai_consent_version
 * et users.ai_consented_at, via /api/ai/consent). Le localStorage est conserve uniquement
 * comme cache d'affichage, pour eviter un appel reseau bloquant avant de savoir s'il faut
 * afficher la modale -- il n'est plus la preuve du consentement, la base l'est. Un utilisateur
 * qui vide son navigateur ne reconsent donc plus : la valeur serveur fait autorite.
 */

import { apiFetch } from '../../api/client.js'

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

/**
 * Lit le consentement faisant autorite, cote serveur.
 *
 * @returns {Promise<boolean|null>} vrai/faux si le serveur a repondu, `null` s'il est
 *   injoignable ou l'utilisateur non authentifie — l'appelant retombe alors sur le cache local
 *   plutot que de supposer un refus (ce qui rafficherait la modale a chaque coupure reseau).
 */
export async function litConsentementServeur() {
  try {
    const res = await apiFetch('/api/ai/consent')
    if (!res.ok) return null
    const donnees = await res.json()
    return donnees?.version === AI_CONSENT_VERSION
  } catch {
    return null
  }
}

/**
 * Enregistre le consentement cote serveur (preuve opposable) ET en cache local.
 *
 * Le cache est ecrit meme si l'appel serveur echoue : l'utilisateur a bien clique, lui
 * reafficher la modale en boucle sur une coupure reseau serait une regression d'usage. La
 * valeur serveur reste la reference et sera reecrite au prochain consentement reussi.
 */
export async function enregistrerConsentementServeur() {
  enregistrerConsentement()
  try {
    await apiFetch('/api/ai/consent', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ version: AI_CONSENT_VERSION }),
    })
  } catch {
    // Voir ci-dessus : le geste local est conserve, l'enregistrement serveur reessaiera.
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
