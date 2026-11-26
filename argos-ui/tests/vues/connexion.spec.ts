// Vue de connexion : invitation à se connecter, ou état d'échec de la connexion.
import { afterEach, describe, expect, it } from 'vitest'
import { etatSession } from '../../src/session/session'
import { monter, simulerApi } from './support'

describe('ConnexionView', () => {
  afterEach(() => {
    etatSession.etat = 'ANONYME'
    etatSession.message = null
  })

  it('propose de se connecter sans saisir de mot de passe dans l’application', async () => {
    simulerApi({})
    const { wrapper } = await monter('/connexion?retour=/veille')
    expect(wrapper.get('button').text()).toBe('Se connecter')
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
  })

  it('affiche l’échec de la connexion avec son message', async () => {
    simulerApi({})
    etatSession.etat = 'ERREUR'
    etatSession.message = 'Configuration de connexion indisponible.'
    const { wrapper } = await monter('/connexion')
    expect(wrapper.get('[role="alert"]').text()).toContain('Connexion impossible')
    expect(wrapper.get('[role="alert"]').text()).toContain('Configuration de connexion indisponible.')
  })
})
