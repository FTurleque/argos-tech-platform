// « Ma veille » (PRF-02) et abonnements (ABO-07, ABO-08).
import { describe, expect, test } from 'vitest'
import { appelsVers, attendre, erreurApi, monter, simulerApi, type Repondeur, type TableApi } from './support'

const ABONNEMENT_ID = '44444444-4444-4444-8444-444444444444'
const ABONNEMENT = {
  id: ABONNEMENT_ID,
  scope: 'PERSONAL',
  ownerWorkspaceId: null,
  ownerUserId: 'moi',
  filters: [{ type: 'TECHNOLOGY', value: 'java' }],
  channel: 'EMAIL',
  frequency: 'IMMEDIATE',
  mandatory: false,
  state: 'ACTIVE',
  createdAt: '2026-11-01T00:00:00Z',
}

/** Avis de sécurité minimal de la veille (schéma `ElementVeilleProfils`), titré « Avis <nom> ». */
function avis(nom: string) {
  return {
    type: 'AVIS_SECURITE', id: `id-${nom}`, source: 'OSV', identifiant: `GHSA-${nom}`, titre: `Avis ${nom}`,
    resume: null, gravite: 'HIGH', date: '2026-11-20T00:00:00Z', dateEffet: null, lien: null, profils: ['securite'],
    rubriques: [], gardeFous: [], mentionValidation: null, projets: [], abonnements: [], mentionIa: null, mentionSource: null,
  }
}

function lectures(veille: Repondeur): TableApi {
  return {
    'GET /api/v1/me/veille': veille,
    'GET /api/v1/catalogue/profils': { corps: [] },
    'GET /api/v1/subscriptions': { corps: [ABONNEMENT] },
    'GET /api/v1/preferences': erreurApi(500, 'Préférences illisibles.'),
  }
}

describe('VeilleView', () => {
  test('affiche une veille vide et suspend un abonnement', async () => {
    const appels = simulerApi({
      ...lectures({ corps: { profilsActifs: ['securite'], items: [], page: 0, size: 50, total: 0 } }),
      [`POST /api/v1/subscriptions/${ABONNEMENT_ID}/suspension`]: { corps: { ...ABONNEMENT, state: 'SUSPENDED' } },
    })
    const { wrapper } = await monter('/veille')
    expect(wrapper.text()).toContain('Aucun élément dans votre veille')
    const suspendre = wrapper.findAll('.abonnement button').find((bouton) => bouton.text().startsWith('Suspendre'))
    expect(suspendre).toBeDefined()
    await suspendre?.trigger('click')
    await attendre()
    expect(appelsVers(appels, 'POST', `/api/v1/subscriptions/${ABONNEMENT_ID}/suspension`)).toHaveLength(1)
  })

  test('PRF-09-S4 : affiche le total et passe à la page suivante', async () => {
    const appels = simulerApi(
      lectures((appel) => {
        const page = Number(new URLSearchParams(appel.chemin.split('?')[1]).get('page') ?? '0')
        return { corps: { profilsActifs: ['securite'], items: [avis(page === 0 ? 'premier' : 'second')], page, size: 50, total: 60 } }
      }),
    )
    const { wrapper } = await monter('/veille')
    expect(wrapper.get('[data-champ="synthese-veille"]').text()).toBe('60 éléments dans votre veille')
    expect(wrapper.get('[data-champ="position-veille"]').text()).toBe('Page 1 sur 2')
    const suivante = wrapper.findAll('.pagination button').find((bouton) => bouton.text() === 'Page suivante')
    await suivante?.trigger('click')
    await attendre()
    expect(wrapper.get('[data-champ="position-veille"]').text()).toBe('Page 2 sur 2')
    expect(wrapper.text()).toContain('Avis second')
    const chemins = appelsVers(appels, 'GET', '/api/v1/me/veille').map((appel) => appel.chemin)
    expect(chemins).toEqual(['/api/v1/me/veille?page=0&size=50', '/api/v1/me/veille?page=1&size=50'])
  })

  test('affiche l’échec de la lecture de la veille', async () => {
    simulerApi(lectures(erreurApi(400, 'Veille indisponible.')))
    const { wrapper } = await monter('/veille')
    expect(wrapper.get('[role="alert"]').text()).toContain('Veille indisponible.')
  })
})
