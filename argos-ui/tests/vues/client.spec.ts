// Point d'appel unique de l'API (`appelerApi`, ACC-09) : jeton porteur, réponse sans contenu, erreurs `Erreur`,
// refus 401 et 403, délai dépassé et annulation par l'appelant.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, appelerApi, configurerAutorisation } from '../../src/api/client'
import { erreurApi, simulerApi } from './support'

const JETON = 'jeton-de-test'
const DELAI_COURT_MS = 20
const surRefus = vi.fn()

beforeEach(() => {
  surRefus.mockReset()
  configurerAutorisation({ jeton: () => JETON, surRefus })
})

afterEach(() => {
  configurerAutorisation({ jeton: () => null, surRefus: () => {} })
})

/** Serveur qui ne répond jamais : l'appel n'aboutit que par l'annulation de son signal. */
function simulerServeurMuet(): void {
  vi.stubGlobal(
    'fetch',
    (_adresse: string, init: RequestInit) =>
      new Promise((_resoudre, rejeter) => {
        init.signal?.addEventListener('abort', () => rejeter(new DOMException('Interrompu', 'AbortError')))
      }),
  )
}

describe('appelerApi : demande envoyée', () => {
  it('ajoute le jeton porteur aux appels de /api/v1, sauf à l’identité publique du serveur', async () => {
    const appels = simulerApi({ 'GET /api/v1/moi': { corps: { id: 'moi' } }, 'GET /api/v1/system/info': { corps: {} } })
    await appelerApi('/api/v1/moi')
    await appelerApi('/api/v1/system/info')
    expect(appels[0].entetes.authorization).toBe(`Bearer ${JETON}`)
    expect(appels[1].entetes.authorization).toBeUndefined()
  })

  it('envoie le corps en JSON avec la méthode demandée', async () => {
    const appels = simulerApi({ 'POST /api/v1/projects': { statut: 201, corps: { id: 'p' } } })
    const reponse = await appelerApi<{ id: string }>('/api/v1/projects', { methode: 'POST', corps: { nom: 'Projet' } })
    expect(reponse).toEqual({ id: 'p' })
    expect(appels[0]).toMatchObject({ methode: 'POST', corps: { nom: 'Projet' } })
    expect(appels[0].entetes['content-type']).toBe('application/json')
  })

  it('renvoie undefined pour une réponse 204', async () => {
    simulerApi({ 'DELETE /api/v1/vues/1': { statut: 204 } })
    await expect(appelerApi('/api/v1/vues/1', { methode: 'DELETE' })).resolves.toBeUndefined()
  })
})

describe('appelerApi : refus du serveur', () => {
  it('lève une ApiError portant le statut, le code et le message du corps Erreur', async () => {
    simulerApi({ 'POST /api/v1/projects': erreurApi(400, 'Nom obligatoire.', 'DONNEE_INVALIDE') })
    const echec = await appelerApi('/api/v1/projects', { methode: 'POST', corps: {} }).catch((erreur: unknown) => erreur)
    expect(echec).toBeInstanceOf(ApiError)
    expect(echec).toMatchObject({ status: 400, code: 'DONNEE_INVALIDE', message: 'Nom obligatoire.' })
    expect(surRefus).not.toHaveBeenCalled()
  })

  it('donne un message générique quand le corps d’erreur n’est pas JSON', async () => {
    vi.stubGlobal('fetch', async () => new Response('panne', { status: 500 }))
    await expect(appelerApi('/api/v1/moi')).rejects.toMatchObject({ status: 500, message: 'Le serveur a répondu 500' })
  })

  it('signale un 401 et un 403 à la session par le rappel surRefus', async () => {
    simulerApi({ 'GET /api/v1/moi': erreurApi(401, 'Session expirée.'), 'GET /api/v1/espaces': erreurApi(403, 'Interdit.') })
    await expect(appelerApi('/api/v1/moi')).rejects.toMatchObject({ status: 401 })
    await expect(appelerApi('/api/v1/espaces')).rejects.toMatchObject({ status: 403 })
    expect(surRefus).toHaveBeenNthCalledWith(1, 'NON_AUTHENTIFIE', '/api/v1/moi')
    expect(surRefus).toHaveBeenNthCalledWith(2, 'INTERDIT', '/api/v1/espaces')
  })
})

describe('appelerApi : sans réponse du serveur', () => {
  it('lève DELAI_DEPASSE quand le serveur ne répond pas dans le délai', async () => {
    simulerServeurMuet()
    await expect(appelerApi('/api/v1/moi', { delaiMs: DELAI_COURT_MS })).rejects.toMatchObject({ motif: 'DELAI_DEPASSE' })
  })

  it('lève ANNULE quand l’appelant annule l’appel', async () => {
    simulerServeurMuet()
    const annulation = new AbortController()
    const appel = appelerApi('/api/v1/moi', { signal: annulation.signal })
    annulation.abort()
    await expect(appel).rejects.toMatchObject({ motif: 'ANNULE', message: 'Appel annulé.' })
  })

  it('lève ANNULE sans appeler le serveur quand le signal est déjà annulé', async () => {
    const appels = simulerApi({ 'GET /api/v1/moi': { corps: {} } })
    const annulation = new AbortController()
    annulation.abort()
    await expect(appelerApi('/api/v1/moi', { signal: annulation.signal })).rejects.toMatchObject({ motif: 'ANNULE' })
    expect(appels).toHaveLength(0)
  })

  it('signale un serveur injoignable', async () => {
    vi.stubGlobal('fetch', async () => {
      throw new TypeError('réseau coupé')
    })
    await expect(appelerApi('/api/v1/moi')).rejects.toMatchObject({ message: expect.stringContaining('injoignable') })
  })
})
