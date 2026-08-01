import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/**
 * Portillon des fonctionnalites.
 *
 * L'enjeu tient en une phrase : si le serveur ne repond pas, le bouton « SOS Arrestation » ne
 * doit PAS s'afficher. Un bouton affiche a tort promet une intervention d'avocat sur une garde
 * a vue reelle ; un bouton masque a tort se repare d'un rechargement.
 *
 * Le module porte un cache de portee module : chaque test le recharge pour partir d'un etat
 * propre, sinon le premier resultat contaminerait les suivants.
 */
async function chargerModuleNeuf() {
  vi.resetModules()
  return import('./features')
}

describe('chargerFeatures', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('rend les fonctionnalites annoncees par le serveur', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({ sosArrest: true }) })
    const { chargerFeatures } = await chargerModuleNeuf()

    await expect(chargerFeatures()).resolves.toEqual({ sosArrest: true })
  })

  it('desactive tout quand le serveur est injoignable', async () => {
    fetch.mockRejectedValue(new TypeError('Failed to fetch'))
    const { chargerFeatures } = await chargerModuleNeuf()

    await expect(chargerFeatures()).resolves.toEqual({ sosArrest: false })
  })

  it('desactive tout sur une reponse en erreur', async () => {
    // Un 500 rendant du HTML serait par ailleurs illisible en JSON : le raccourci par res.ok
    // evite d'avoir a distinguer les cas.
    fetch.mockResolvedValue({ ok: false, status: 500, json: async () => ({ sosArrest: true }) })
    const { chargerFeatures } = await chargerModuleNeuf()

    await expect(chargerFeatures()).resolves.toEqual({ sosArrest: false })
  })

  it('ne met PAS en cache un repli : le service peut revenir', async () => {
    // Mettre le repli en cache figerait la fonctionnalite a « desactivee » pour toute la duree
    // de la session, longtemps apres le retablissement du serveur.
    fetch.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const { chargerFeatures } = await chargerModuleNeuf()
    await chargerFeatures()

    fetch.mockResolvedValue({ ok: true, json: async () => ({ sosArrest: true }) })
    await expect(chargerFeatures()).resolves.toEqual({ sosArrest: true })
  })

  it('ne rappelle pas le serveur une fois la reponse obtenue', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({ sosArrest: false }) })
    const { chargerFeatures } = await chargerModuleNeuf()

    await chargerFeatures()
    await chargerFeatures()

    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('interroge la route publique en transmettant les cookies', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({ sosArrest: false }) })
    const { chargerFeatures } = await chargerModuleNeuf()

    await chargerFeatures()

    expect(fetch).toHaveBeenCalledWith('/api/public/features',
      expect.objectContaining({ credentials: 'include' }))
  })
})
