// Alertes du poste : liste, détail avec lien vers le signal, alerte introuvable.
import { describe, expect, test } from 'vitest'
import { erreurApi, monter, simulerApi } from './support'

const ALERTE_ID = '11111111-1111-4111-8111-111111111111'
const SIGNAL_ID = '22222222-2222-4222-8222-222222222222'
const ALERTE = {
  id: ALERTE_ID,
  nature: 'IMPACT',
  canal: 'DESKTOP',
  etat: 'ENVOYEE',
  titre: 'Faille critique dans log4j',
  gravite: 'CRITICAL',
  contenu: {
    elements: [{ signalId: SIGNAL_ID, titre: 'CVE-2021-44228', gravite: 'CRITICAL', projets: [], sources: [], lien: null }],
    resumeIa: null,
    etatResumeIa: 'ABSENT',
  },
  tentatives: 1,
  creeLe: '2026-11-20T10:00:00Z',
  envoyeLe: '2026-11-20T10:01:00Z',
}

describe('AlertesView', () => {
  test('liste les alertes du poste', async () => {
    simulerApi({ 'GET /api/v1/alertes': { corps: [ALERTE] } })
    const { wrapper } = await monter('/alertes')
    expect(wrapper.get(`a[href="/alertes/${ALERTE_ID}"]`).text()).toBe('Faille critique dans log4j')
    expect(wrapper.text()).toContain('gravité critique')
  })

  test('le détail d’une alerte mène à la vue du signal', async () => {
    simulerApi({
      [`GET /api/v1/alertes/${ALERTE_ID}`]: { corps: ALERTE },
      [`GET /api/v1/signals/${SIGNAL_ID}/impacts`]: { corps: { items: [], page: 0, size: 50, total: 0 } },
    })
    const { wrapper } = await monter(`/alertes/${ALERTE_ID}`)
    expect(wrapper.get('h1').text()).toBe('Faille critique dans log4j')
    expect(wrapper.find(`a[href="/signaux/${SIGNAL_ID}"]`).exists()).toBe(true)
  })

  test('signale une alerte introuvable', async () => {
    simulerApi({ [`GET /api/v1/alertes/${ALERTE_ID}`]: erreurApi(404, 'Inconnue.') })
    const { wrapper } = await monter(`/alertes/${ALERTE_ID}`)
    expect(wrapper.text()).toContain('Alerte introuvable.')
  })
})
