/**
 * Consommation manuelle d'un flux SSE recu via fetch().
 *
 * Pas `new EventSource(url)` : EventSource ne sait faire que des requetes GET, or une
 * question juridique peut contenir des details personnels ou sensibles qui ne doivent jamais
 * transiter par une chaine de requete (journalisee cote serveur/proxy). Le flux est donc lu
 * directement sur le corps de la reponse d'un fetch() en POST (voir apiFetch).
 */

/** Découpe un bloc `event:...\ndata:...\n` (les lignes `data:` multiples sont jointes par \n). */
function parseBlocSse(bloc) {
  let event = 'message'
  const lignesDonnees = []
  for (const ligne of bloc.split('\n')) {
    if (ligne.startsWith('event:')) {
      event = ligne.slice('event:'.length).trim()
    } else if (ligne.startsWith('data:')) {
      lignesDonnees.push(ligne.slice('data:'.length).replace(/^ /, ''))
    }
  }
  return { event, data: lignesDonnees.join('\n') }
}

/**
 * Lit le corps d'une Response en flux SSE et appelle `onEvent({ event, data })` pour chaque
 * evenement complet recu. Se termine quand le flux se ferme (fin normale ou coupure reseau).
 */
export async function lireFluxSse(response, onEvent) {
  const lecteur = response.body.getReader()
  const decodeur = new TextDecoder('utf-8')
  let tampon = ''

  for (;;) {
    const { value, done } = await lecteur.read()
    if (done) break
    tampon += decodeur.decode(value, { stream: true })

    let indexSeparateur
    while ((indexSeparateur = tampon.indexOf('\n\n')) !== -1) {
      const bloc = tampon.slice(0, indexSeparateur)
      tampon = tampon.slice(indexSeparateur + 2)
      if (bloc.trim()) {
        onEvent(parseBlocSse(bloc))
      }
    }
  }
}
