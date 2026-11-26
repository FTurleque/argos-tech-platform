// Réglages du poste (client desktop, NOT-04) : le pont desktop est posé avant le chargement des modules.
import { describe, expect, test, vi } from 'vitest'
import { attendre, monter, simulerApi } from './support'

const pont = vi.hoisted(() => {
  const reglages = { notificationsPoste: true, demarrageSession: false, demarrageSessionDisponible: true }
  const bridge = {
    platform: 'win32',
    getSettings: async () => reglages,
    setSettings: async (): Promise<typeof reglages | null> => null,
    onSettingsChanged: () => () => {},
    onOpenAlert: () => () => {},
  }
  ;(window as unknown as { argosDesktop: unknown }).argosDesktop = bridge
  return bridge
})

describe('ReglagesPosteView', () => {
  test('affiche les réglages lus dans le client desktop', async () => {
    simulerApi({})
    const { wrapper } = await monter('/poste')
    const cases = wrapper.findAll('input[type="checkbox"]')
    expect(cases).toHaveLength(2)
    expect((cases[0].element as HTMLInputElement).checked).toBe(true)
    expect((cases[1].element as HTMLInputElement).checked).toBe(false)
  })

  test('signale un réglage non enregistré', async () => {
    simulerApi({})
    const enregistrement = vi.spyOn(pont, 'setSettings')
    const { wrapper } = await monter('/poste')
    await wrapper.findAll('input[type="checkbox"]')[0].setValue(false)
    await attendre()
    expect(enregistrement).toHaveBeenCalledWith({ notificationsPoste: false })
    expect(wrapper.text()).toContain('Réglage non enregistré.')
  })
})
