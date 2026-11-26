// « Ma veille » (PRF-02) et abonnements (ABO-07, ABO-08).
import { describe, expect, it } from 'vitest'
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

function lectures(veille: Repondeur): TableApi {
  return {
    'GET /api/v1/me/veille': veille,
    'GET /api/v1/catalogue/profils': { corps: [] },
    'GET /api/v1/subscriptions': { corps: [ABONNEMENT] },
    'GET /api/v1/preferences': erreurApi(500, 'Préférences illisibles.'),
  }
}

describe('VeilleView', () => {
  it('affiche une veille vide et suspend un abonnement', async () => {
    const appels = simulerApi({
      ...lectures({ corps: { profilsActifs: ['securite'], elements: [] } }),
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

  it('affiche l’échec de la lecture de la veille', async () => {
    simulerApi(lectures(erreurApi(400, 'Veille indisponible.')))
    const { wrapper } = await monter('/veille')
    expect(wrapper.get('[role="alert"]').text()).toContain('Veille indisponible.')
  })
})
