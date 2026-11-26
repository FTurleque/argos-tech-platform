// Vue « État du serveur » : identité du serveur, ou serveur hors ligne.
import { describe, expect, it, vi } from 'vitest'
import { erreurApi, monter, simulerApi } from './support'

describe('StatusView', () => {
  it('affiche l’identité du serveur', async () => {
    simulerApi({ 'GET /api/v1/system/info': { corps: { application: 'ARGOS', version: '1.2.3', apiVersion: 'v1' } } })
    const { wrapper } = await monter('/')
    expect(wrapper.text()).toContain('Connecté')
    expect(wrapper.text()).toContain('ARGOS · version 1.2.3 · API v1')
  })

  it('affiche « Hors ligne » et le message du serveur en cas d’erreur', async () => {
    simulerApi({ 'GET /api/v1/system/info': erreurApi(503, 'Base indisponible.') })
    const { wrapper } = await monter('/')
    expect(wrapper.text()).toContain('Hors ligne')
    expect(wrapper.text()).toContain('Base indisponible.')
  })

  it('affiche « Hors ligne » quand le serveur est injoignable', async () => {
    vi.stubGlobal('fetch', async () => {
      throw new TypeError('réseau coupé')
    })
    const { wrapper } = await monter('/')
    expect(wrapper.text()).toContain('Serveur ARGOS injoignable')
  })
})
