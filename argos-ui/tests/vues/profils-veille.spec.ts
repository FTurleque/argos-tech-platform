// Choix des profils de veille actifs (PRF-01).
import { describe, expect, test } from 'vitest'
import { appelsVers, attendre, erreurApi, monter, simulerApi } from './support'

const PREFERENCES = {
  miseEnPage: 'classique',
  miseEnPageChoisie: null,
  miseEnPageIndisponible: false,
  message: null,
  profilsActifs: ['securite'],
  profilsParDefaut: false,
  profilsIndisponibles: [],
}
const CATALOGUE = [
  { identifiant: 'securite', libelle: 'Sécurité', description: 'Failles', version: '1', rubriques: [], gardeFous: [] },
  { identifiant: 'artiste', libelle: 'Artiste', description: 'Appels à projets', version: '1', rubriques: [], gardeFous: [] },
]

describe('ProfilsVeilleView', () => {
  test('active un profil en envoyant l’ensemble complet demandé', async () => {
    const appels = simulerApi({
      'GET /api/v1/catalogue/profils': { corps: CATALOGUE },
      'GET /api/v1/preferences': { corps: PREFERENCES },
      'PUT /api/v1/preferences/profils': { corps: { ...PREFERENCES, profilsActifs: ['securite', 'artiste'] } },
    })
    const { wrapper } = await monter('/veille/profils')
    const boutons = wrapper.findAll('button.profil')
    expect(boutons.map((bouton) => bouton.attributes('aria-pressed'))).toEqual(['true', 'false'])
    await boutons[1].trigger('click')
    await attendre()
    expect(appelsVers(appels, 'PUT', '/api/v1/preferences/profils')[0].corps).toEqual({ profilsActifs: ['securite', 'artiste'] })
    expect(wrapper.findAll('button.profil')[1].attributes('aria-pressed')).toBe('true')
  })

  test('affiche l’échec de la lecture du catalogue', async () => {
    simulerApi({
      'GET /api/v1/catalogue/profils': erreurApi(400, 'Catalogue illisible.'),
      'GET /api/v1/preferences': { corps: PREFERENCES },
    })
    const { wrapper } = await monter('/veille/profils')
    expect(wrapper.get('[role="alert"]').text()).toBe('Catalogue illisible.')
  })
})
