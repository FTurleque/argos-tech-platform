// Choix de la mise en page (AFF-01, AFF-03, AFF-04).
import { describe, expect, test } from 'vitest'
import { appelsVers, attendre, erreurApi, monter, simulerApi } from './support'

const PREFERENCES = {
  miseEnPage: 'classique',
  miseEnPageChoisie: null,
  miseEnPageIndisponible: false,
  message: null,
  profilsActifs: ['securite'],
  profilsParDefaut: true,
  profilsIndisponibles: [],
}
const CATALOGUE = [
  { identifiant: 'classique', libelle: 'Classique', description: 'Sobre', parDefaut: true },
  { identifiant: 'journal', libelle: 'Journal', description: 'Colonnes', parDefaut: false },
]

describe('AffichageView', () => {
  test('liste les mises en page et enregistre le choix', async () => {
    const appels = simulerApi({
      'GET /api/v1/preferences': { corps: PREFERENCES },
      'GET /api/v1/catalogue/mises-en-page': { corps: CATALOGUE },
      'PUT /api/v1/preferences/mise-en-page': { corps: { ...PREFERENCES, miseEnPage: 'journal', miseEnPageChoisie: 'journal' } },
    })
    const { wrapper } = await monter('/affichage')
    const choix = wrapper.findAll('[role="radio"]')
    expect(choix.map((bouton) => bouton.text())).toEqual(['Classique', 'Journal'])
    await choix[1].trigger('click')
    await attendre()
    expect(appelsVers(appels, 'PUT', '/api/v1/preferences/mise-en-page')).toHaveLength(1)
    expect(wrapper.findAll('[role="radio"]')[1].attributes('aria-checked')).toBe('true')
  })

  test('affiche le refus du serveur', async () => {
    simulerApi({
      'GET /api/v1/preferences': erreurApi(500, 'Préférences illisibles.'),
      'GET /api/v1/catalogue/mises-en-page': { corps: CATALOGUE },
    })
    const { wrapper } = await monter('/affichage')
    expect(wrapper.get('[role="alert"]').text()).toBe('Préférences illisibles.')
  })
})
