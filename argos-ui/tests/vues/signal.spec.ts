// Vue d'un signal (ALR-09-S1) : provenance et historique, ou erreur.
import { describe, expect, it } from 'vitest'
import { erreurApi, monter, simulerApi } from './support'

const SIGNAL_ID = '12121212-1212-4121-8121-121212121212'
const DETAIL = {
  signal: {
    id: SIGNAL_ID,
    source: 'OSV',
    externalId: 'GHSA-1234',
    title: 'Injection dans <script>alert(1)</script>',
    summary: 'Une injection permet l’exécution de code.',
    severity: 'HIGH',
    publishedAt: '2026-11-01T00:00:00Z',
    modifiedAt: null,
    collectedAt: '2026-11-02T00:00:00Z',
    currentVersionId: null,
    aliases: ['CVE-2026-0001'],
    affected: [{ packageKey: 'npm:lodash', declaredPurl: true, ecosystem: 'npm', name: 'lodash', versions: ['4.17.20'] }],
  },
  versions: [{ id: 'v1', versionNo: 1, contentHash: 'abc', sourceModifiedAt: null, collectedAt: '2026-11-02T00:00:00Z' }],
  enrichments: [],
  exploited: true,
}

describe('SignalView', () => {
  it('affiche le signal, sa provenance et ses paquets affectés, titre rendu comme texte', async () => {
    simulerApi({
      [`GET /api/v1/signals/${SIGNAL_ID}`]: { corps: DETAIL },
      [`GET /api/v1/signals/${SIGNAL_ID}/impacts`]: { corps: { items: [], page: 0, size: 50, total: 0 } },
    })
    const { wrapper } = await monter(`/signaux/${SIGNAL_ID}`)
    expect(wrapper.get('h1').text()).toBe('Injection dans <script>alert(1)</script>')
    expect(wrapper.find('h1 script').exists()).toBe(false)
    expect(wrapper.get('[data-provenance]').text()).toContain('OSV GHSA-1234 · gravité élevée')
    expect(wrapper.find('[data-exploite]').exists()).toBe(true)
    expect(wrapper.text()).toContain('npm:lodash')
    expect(wrapper.text()).toContain('Alias : CVE-2026-0001')
  })

  it('affiche l’erreur d’un signal introuvable', async () => {
    simulerApi({ [`GET /api/v1/signals/${SIGNAL_ID}`]: erreurApi(404, 'Signal introuvable.') })
    const { wrapper } = await monter(`/signaux/${SIGNAL_ID}`)
    expect(wrapper.get('[role="alert"]').text()).toContain('Signal introuvable.')
  })

  it('refuse un identifiant qui n’est pas un UUID, sans appel', async () => {
    const appels = simulerApi({})
    const { wrapper } = await monter('/signaux/pas-un-uuid')
    expect(appels).toHaveLength(0)
    expect(wrapper.get('[role="alert"]').text()).toContain('Identifiant de signal invalide.')
  })
})
