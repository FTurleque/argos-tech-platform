// Écran de refus d'un compte non créé faute d'invitation (ACC-09-S4).
import { afterEach, describe, expect, it } from 'vitest'
import { etatSession } from '../../src/session/session'
import { monter, simulerApi } from './support'

describe('CompteNonCreeView', () => {
  afterEach(() => {
    etatSession.etat = 'ANONYME'
    etatSession.compteNonCree = false
  })

  it('sans refus en cours, renvoie vers la connexion', async () => {
    simulerApi({})
    const { wrapper } = await monter('/compte-non-cree')
    expect(wrapper.text()).toContain('Aucun refus en cours')
  })

  it('explique le refus d’une identité reconnue sans compte ARGOS', async () => {
    simulerApi({})
    etatSession.etat = 'CONNECTE'
    etatSession.compteNonCree = true
    const { wrapper } = await monter('/compte-non-cree')
    expect(wrapper.get('[role="alert"]').text()).toContain('aucun compte ARGOS n\'a été créé')
    expect(wrapper.get('button').text()).toBe('Se déconnecter')
  })
})
