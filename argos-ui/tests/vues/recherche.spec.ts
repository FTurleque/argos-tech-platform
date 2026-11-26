// Recherche et vues enregistrées (RCH-01 à RCH-04).
import { describe, expect, test } from 'vitest'
import { appelsVers, attendre, erreurApi, monter, simulerApi } from './support'

const VUE_ID = '33333333-3333-4333-8333-333333333333'

describe('RechercheView', () => {
  test('liste les vues enregistrées et lance une recherche', async () => {
    const appels = simulerApi({
      'GET /api/v1/vues': { corps: [{ id: VUE_ID, nom: 'Java critique', criteres: null, creeLe: '2026-11-01T00:00:00Z' }] },
      'GET /api/v1/recherche': { corps: { elements: [], page: 0, taille: 20, total: 0, message: null } },
    })
    const { wrapper } = await monter('/recherche')
    expect(wrapper.get(`a[href="/recherche/vues/${VUE_ID}"]`).text()).toBe('Java critique')
    await wrapper.get('#recherche-texte').setValue('log4j')
    await wrapper.get('form[role="search"]').trigger('submit')
    await attendre()
    const recherches = appelsVers(appels, 'GET', '/api/v1/recherche')
    expect(recherches).toHaveLength(1)
    expect(recherches[0].chemin).toContain('log4j')
  })

  test('affiche l’échec de la lecture des vues', async () => {
    simulerApi({ 'GET /api/v1/vues': erreurApi(500, 'Vues illisibles.') })
    const { wrapper } = await monter('/recherche')
    expect(wrapper.findAll('[role="alert"]').some((alerte) => alerte.text().length > 0)).toBe(true)
  })
})
