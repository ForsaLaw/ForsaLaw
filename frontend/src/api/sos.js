/**
 * Appels de la prise en charge d'urgence « SOS Arrestation ».
 *
 * L'endpoint de depot est ouvert : un proche doit pouvoir alerter sans compte. Le jeton CSRF
 * est neanmoins requis (POST protege). Il est amorce explicitement AVANT l'envoi : un visiteur
 * anonyme arrivant directement sur la page peut ne pas encore l'avoir, et un 403 a cet instant
 * couterait les minutes qui comptent.
 */

import { apiFetch, parseApiError, initCsrfToken } from './client.js'

/** @returns {Promise<object>} le signalement cree (id, statuts) */
export async function signalerArrestation(donnees) {
  await initCsrfToken()

  const res = await apiFetch('/api/sos/arrest', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(donnees),
  })
  if (!res.ok) {
    throw new Error(await parseApiError(res))
  }
  return res.json()
}

/** Etat d'un signalement : permet de suivre paiement puis mobilisation. */
export async function consulterSignalement(id) {
  const res = await apiFetch(`/api/sos/arrest/${encodeURIComponent(id)}`)
  if (!res.ok) {
    throw new Error(await parseApiError(res))
  }
  return res.json()
}
