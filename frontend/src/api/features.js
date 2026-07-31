/**
 * Fonctionnalites activees, lues depuis le serveur.
 *
 * Le serveur est la SEULE source de verite : dupliquer l'interrupteur dans une variable de
 * build ferait diverger les deux, et l'ecart se verrait au pire moment — un bouton « SOS
 * Arrestation » visible alors que l'API repond 503, dans une situation d'urgence.
 *
 * En cas d'echec (serveur injoignable), tout est considere DESACTIVE. Une fonctionnalite
 * masquee a tort se repare d'un rechargement ; une fonctionnalite affichee a tort promet une
 * intervention qui n'aura pas lieu.
 */

const TOUT_DESACTIVE = { sosArrest: false }

let cache = null

export async function chargerFeatures() {
  if (cache) return cache
  try {
    const res = await fetch('/api/public/features', { credentials: 'include' })
    if (!res.ok) return TOUT_DESACTIVE
    cache = await res.json()
    return cache
  } catch {
    return TOUT_DESACTIVE
  }
}
