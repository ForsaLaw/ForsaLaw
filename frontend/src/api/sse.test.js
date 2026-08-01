import { describe, it, expect, vi } from 'vitest'
import { lireFluxSse } from './sse'

/**
 * Lecture du flux SSE de l'assistant juridique.
 *
 * Le cas qui compte n'est pas la trame bien formee mais la trame COUPEE : le corps arrive par
 * paquets reseau qui ne respectent aucune frontiere d'evenement. Un lecteur qui supposerait
 * « un paquet = un evenement » fonctionnerait en developpement, sur des reponses courtes, et
 * perdrait des jetons en production.
 */

/** Fabrique une Response dont le corps rend les morceaux fournis, un par lecture. */
function reponseAvecMorceaux(...morceaux) {
  const encodeur = new TextEncoder()
  let i = 0
  return {
    body: {
      getReader: () => ({
        read: async () =>
          i < morceaux.length
            ? { value: encodeur.encode(morceaux[i++]), done: false }
            : { value: undefined, done: true },
      }),
    },
  }
}

async function collecter(...morceaux) {
  const recus = []
  await lireFluxSse(reponseAvecMorceaux(...morceaux), (e) => recus.push(e))
  return recus
}

describe('lireFluxSse', () => {
  it('rend un evenement complet', async () => {
    expect(await collecter('event:jeton\ndata:"Bonjour"\n\n')).toEqual([
      { event: 'jeton', data: '"Bonjour"' },
    ])
  })

  it('reassemble un evenement coupe entre deux paquets reseau', async () => {
    // LE cas critique : la coupure tombe au milieu du mot « jeton ».
    const recus = await collecter('event:je', 'ton\ndata:"Bon', 'jour"\n\n')
    expect(recus).toEqual([{ event: 'jeton', data: '"Bonjour"' }])
  })

  it('rend plusieurs evenements arrives dans un seul paquet', async () => {
    const recus = await collecter('event:jeton\ndata:"a"\n\nevent:jeton\ndata:"b"\n\n')
    expect(recus.map((e) => e.data)).toEqual(['"a"', '"b"'])
  })

  it('conserve un espace de tete dans la donnee', async () => {
    // Defaut reellement constate : le SSE retire UN espace optionnel apres « data: », et le
    // SseEmitter de Spring n'en ecrit pas. Les jetons sont donc encodes en JSON cote serveur ;
    // ce test verrouille le fait que le client n'en retire pas un de plus.
    const recus = await collecter('event:jeton\ndata:" les extraits"\n\n')
    expect(JSON.parse(recus[0].data)).toBe(' les extraits')
  })

  it('joint les lignes data multiples par un saut de ligne', async () => {
    const recus = await collecter('event:jeton\ndata:premiere\ndata:seconde\n\n')
    expect(recus[0].data).toBe('premiere\nseconde')
  })

  it('applique « message » par defaut quand aucun event n\'est nomme', async () => {
    const recus = await collecter('data:"sans nom"\n\n')
    expect(recus[0].event).toBe('message')
  })

  it('ignore les blocs vides plutot que d\'emettre un evenement fantome', async () => {
    // Les commentaires de maintien en vie (« :ping ») et les separateurs surnumeraires ne
    // doivent pas etre pris pour des jetons : ils s'afficheraient dans la reponse.
    expect(await collecter('\n\n', 'event:jeton\ndata:"x"\n\n')).toHaveLength(1)
  })

  it('n\'emet rien pour une trame jamais terminee', async () => {
    // Flux coupe en plein evenement : mieux vaut perdre un fragment que rendre une trame
    // partielle, qui serait un JSON.parse en echec cote appelant.
    expect(await collecter('event:jeton\ndata:"incom')).toEqual([])
  })

  it('se termine quand le flux se ferme', async () => {
    const onEvent = vi.fn()
    await expect(lireFluxSse(reponseAvecMorceaux('event:fin\ndata:{}\n\n'), onEvent))
      .resolves.toBeUndefined()
    expect(onEvent).toHaveBeenCalledTimes(1)
  })
})
