// Session OIDC de l'IHM (gestionnaire.ts, changement connecter-ihm-oidc : ACC-07, ACC-08).
// Le fournisseur d'identité est simulé en mémoire (découverte, point de jetons) : aucune requête
// réseau. Les minuteries et l'horloge sont injectées.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { GestionnaireSession } from '../src/session/gestionnaire.ts'

const EMETTEUR = 'http://kc.test/realms/argos'

/** Fournisseur simulé : vérifie PKCE comme Keycloak et émet des jetons numérotés. */
function fournisseur() {
  const simule = {
    codes: new Map(),
    appels: [],
    emis: 0,
    renouvellementRefuse: false,
    decouverteEnPanne: false,
    async fetch(url, init = {}) {
      simule.appels.push({ url, init })
      if (url === `${EMETTEUR}/.well-known/openid-configuration`) {
        if (simule.decouverteEnPanne) return reponse(503, {})
        return reponse(200, {
          issuer: EMETTEUR,
          authorization_endpoint: `${EMETTEUR}/auth`,
          token_endpoint: `${EMETTEUR}/token`,
          end_session_endpoint: `${EMETTEUR}/logout`,
        })
      }
      if (url === `${EMETTEUR}/token`) {
        const parametres = new URLSearchParams(init.body)
        if (parametres.get('grant_type') === 'authorization_code') {
          const attendu = simule.codes.get(parametres.get('code'))
          const defi = createHash('sha256').update(parametres.get('code_verifier') ?? '').digest('base64url')
          if (!attendu || attendu.defi !== defi || attendu.redirectUri !== parametres.get('redirect_uri')) {
            return reponse(400, { error: 'invalid_grant' })
          }
          simule.codes.delete(parametres.get('code'))
          return reponse(200, simule.jetons())
        }
        if (parametres.get('grant_type') === 'refresh_token') {
          if (simule.renouvellementRefuse) return reponse(400, { error: 'invalid_grant' })
          return reponse(200, { ...simule.jetons(), id_token: undefined })
        }
      }
      return reponse(404, {})
    },
    jetons() {
      simule.emis += 1
      return { access_token: `acces-${simule.emis}`, refresh_token: `renouv-${simule.emis}`, id_token: `id-${simule.emis}`, expires_in: 600, token_type: 'Bearer' }
    },
    /** Autorise la demande contenue dans l'URL et renvoie la chaîne de requête du retour. */
    autoriser(url, { state } = {}) {
      const adresse = new URL(url)
      const code = `code-${simule.codes.size + 1}`
      simule.codes.set(code, { defi: adresse.searchParams.get('code_challenge'), redirectUri: adresse.searchParams.get('redirect_uri') })
      return `?state=${state ?? adresse.searchParams.get('state')}&session_state=x&code=${code}`
    },
  }
  return simule
}

function reponse(status, corps) {
  return { ok: status >= 200 && status < 300, status, json: async () => corps }
}

function installer({ transport, ...options } = {}) {
  const fournisseurSimule = fournisseur()
  const horloge = { instant: 1_000_000, minuteries: [] }
  const etats = []
  const ouvertes = []
  const transportChoisi = transport ?? {
    preparer: async () => 'http://127.0.0.1:50123/argos/retour',
    lancer: async (url) => fournisseurSimule.autoriser(url),
    reprendre: () => null,
    retourApresDeconnexion: () => null,
    deconnecter: (url) => ouvertes.push(url),
  }
  const session = new GestionnaireSession({
    config: { emetteur: EMETTEUR, clientId: 'argos-ui', portee: 'openid' },
    fetch: fournisseurSimule.fetch,
    transport: transportChoisi,
    maintenant: () => horloge.instant,
    planifier: (action, delai) => {
      const minuterie = { action, delai, active: true }; horloge.minuteries.push(minuterie); return minuterie
    },
    annuler: (minuterie) => { minuterie.active = false },
    surEtat: (etat, message) => etats.push({ etat, message }),
    ...options,
  })
  return { fournisseurSimule, horloge, etats, ouvertes, session }
}

test('ACC-07-S2 : connexion aboutie → jetons en mémoire, vue demandée, renouvellement planifié', async () => {
  const { fournisseurSimule, horloge, etats, session } = installer()
  assert.equal(session.jetonAcces(), null)
  const vue = await session.seConnecter('/alertes/42')
  assert.equal(vue, '/alertes/42')
  assert.equal(session.etat, 'CONNECTE')
  assert.equal(session.jetonAcces(), 'acces-1')
  assert.deepEqual(etats.map((entree) => entree.etat), ['CONNEXION_EN_COURS', 'CONNECTE'])
  const actives = horloge.minuteries.filter((minuterie) => minuterie.active)
  assert.equal(actives.length, 1)
  assert.equal(actives[0].delai, 540_000)
  // L'échange porte le vérificateur PKCE et n'envoie aucun cookie.
  const echange = fournisseurSimule.appels.find((appel) => appel.url.endsWith('/token'))
  assert.match(echange.init.body, /code_verifier=/)
  assert.equal(echange.init.credentials, 'omit')
  // Aucun jeton ni vérificateur dans une adresse appelée.
  for (const appel of fournisseurSimule.appels) assert.doesNotMatch(appel.url, /acces-|code_verifier|renouv-/)
})

test('ACC-07-S3 : un state différent n’échange aucun code et n’ouvre aucune session', async () => {
  const fournisseurSimule = fournisseur()
  const { session, etats } = installer({
    transport: {
      preparer: async () => 'http://127.0.0.1:50123/argos/retour',
      lancer: async (url) => fournisseurSimule.autoriser(url, { state: 'falsifie' }),
      reprendre: () => null,
      retourApresDeconnexion: () => null,
      deconnecter: () => {},
    },
    fetch: fournisseurSimule.fetch,
  })
  assert.equal(await session.seConnecter('/'), null)
  assert.equal(session.etat, 'ERREUR')
  assert.equal(session.jetonAcces(), null)
  assert.equal(fournisseurSimule.appels.some((appel) => appel.url.endsWith('/token')), false)
  assert.match(etats.at(-1).message, /invalide/)
})

test('ACC-07-S4 : refus du fournisseur → état refusé avec un message explicite, aucun jeton', async () => {
  const { session, fournisseurSimule } = installer({
    transport: {
      preparer: async () => 'http://localhost:5173/',
      lancer: async (url) => `?error=access_denied&error_description=texte%20libre&state=${new URL(url).searchParams.get('state')}`,
      reprendre: () => null,
      retourApresDeconnexion: () => null,
      deconnecter: () => {},
    },
  })
  assert.equal(await session.seConnecter('/'), null)
  assert.equal(session.etat, 'REFUSEE')
  assert.equal(session.message, 'Le fournisseur d’identité a refusé la connexion.')
  assert.equal(fournisseurSimule.appels.some((appel) => appel.url.endsWith('/token')), false)
})

test('portail web : le retour repris au chargement est échangé avec la demande enregistrée', async () => {
  const fournisseurSimule = fournisseur()
  let demandeEnregistree = null
  let urlAutorisation = null
  const transport = {
    preparer: async () => 'http://localhost:5173/',
    lancer: async (url, demande) => { demandeEnregistree = demande; urlAutorisation = url; return null },
    reprendre: () => (demandeEnregistree ? { recherche: fournisseurSimule.autoriser(urlAutorisation), demande: demandeEnregistree } : null),
    retourApresDeconnexion: () => 'http://localhost:5173/',
    deconnecter: () => {},
  }
  const avant = installer({ transport, fetch: fournisseurSimule.fetch })
  assert.equal(await avant.session.seConnecter('/affichage'), null)
  assert.equal(avant.session.etat, 'CONNEXION_EN_COURS')
  // Nouvelle page (rechargement) : nouvelle instance, mêmes données transitoires.
  const apres = installer({ transport, fetch: fournisseurSimule.fetch })
  assert.equal(await apres.session.reprendre(), '/affichage')
  assert.equal(apres.session.jetonAcces(), 'acces-1')
})

test('un retour sans demande en cours est refusé', async () => {
  const { session } = installer({
    transport: {
      preparer: async () => '', lancer: async () => null, retourApresDeconnexion: () => null, deconnecter: () => {},
      reprendre: () => ({ recherche: '?code=c&state=s', demande: null }),
    },
  })
  assert.equal(await session.reprendre(), null)
  assert.equal(session.etat, 'ERREUR')
})

test('fournisseur injoignable : état d’erreur, aucune redirection lancée', async () => {
  let lance = false
  const { session, fournisseurSimule } = installer({
    transport: {
      preparer: async () => 'http://localhost:5173/', lancer: async () => { lance = true; return null },
      reprendre: () => null, retourApresDeconnexion: () => null, deconnecter: () => {},
    },
  })
  fournisseurSimule.decouverteEnPanne = true
  assert.equal(await session.seConnecter('/'), null)
  assert.equal(session.etat, 'ERREUR')
  assert.equal(lance, false)
})

test('ACC-08-S2 : la minuterie renouvelle les jetons avant expiration et en planifie une autre', async () => {
  const { session, horloge } = installer()
  await session.seConnecter('/')
  horloge.instant += 540_000
  const premiere = horloge.minuteries.find((minuterie) => minuterie.active)
  premiere.active = false // une minuterie échue n'est plus active
  premiere.action()
  // Le renouvellement est asynchrone (réponse simulée) : quelques tours de boucle suffisent.
  for (let tour = 0; tour < 5; tour += 1) await new Promise((resoudre) => setImmediate(resoudre))
  assert.equal(session.jetonAcces(), 'acces-2')
  assert.equal(session.etat, 'CONNECTE')
  assert.equal(horloge.minuteries.filter((minuterie) => minuterie.active).length, 1)
})

test('ACC-08-S3 : renouvellement refusé → session expirée, jetons oubliés', async () => {
  const { session, fournisseurSimule, etats } = installer()
  await session.seConnecter('/')
  fournisseurSimule.renouvellementRefuse = true
  assert.equal(await session.renouveler(), false)
  assert.equal(session.etat, 'EXPIREE')
  assert.equal(session.jetonAcces(), null)
  assert.match(etats.at(-1).message, /expiré/)
})

test('un jeton arrivé à expiration n’est plus fourni, même sans renouvellement', async () => {
  const { session, horloge } = installer()
  await session.seConnecter('/')
  horloge.instant += 600_000
  assert.equal(session.jetonAcces(), null)
})

test('ACC-09-S3 : un 401 de l’API (expirer) oublie les jetons et signale la session expirée', async () => {
  const { session, horloge } = installer()
  await session.seConnecter('/')
  session.expirer()
  assert.equal(session.etat, 'EXPIREE')
  assert.equal(session.jetonAcces(), null)
  assert.equal(horloge.minuteries.filter((minuterie) => minuterie.active).length, 0)
  // Sans session ouverte, expirer ne change rien.
  session.expirer()
  assert.equal(session.etat, 'EXPIREE')
})

test('ACC-08-S4 : déconnexion → jetons oubliés, fin de session du fournisseur ouverte', async () => {
  const { session, ouvertes, horloge } = installer()
  await session.seConnecter('/')
  session.deconnecter()
  assert.equal(session.etat, 'ANONYME')
  assert.equal(session.jetonAcces(), null)
  assert.equal(horloge.minuteries.filter((minuterie) => minuterie.active).length, 0)
  assert.equal(ouvertes.length, 1)
  const url = new URL(ouvertes[0])
  assert.equal(url.origin + url.pathname, `${EMETTEUR}/logout`)
  assert.equal(url.searchParams.get('id_token_hint'), 'id-1')
})

test('l’instance n’expose aucun jeton par ses propriétés', async () => {
  const { session } = installer()
  await session.seConnecter('/')
  assert.doesNotMatch(JSON.stringify(session), /acces-|renouv-|id-1/)
  assert.deepEqual(Object.keys(session), [])
})

test('desktop : une connexion relancée rend caduque la précédente sans afficher d’erreur', async () => {
  const fournisseurSimule = fournisseur()
  const attentes = []
  const { session, etats } = installer({
    transport: {
      preparer: async () => {
        // Comme le processus principal : une nouvelle écoute ferme la précédente (retour null).
        attentes.splice(0).forEach((attente) => attente.resoudre(null))
        return 'http://127.0.0.1:50123/argos/retour'
      },
      lancer: (url) => new Promise((resoudre) => attentes.push({ url, resoudre })),
      reprendre: () => null,
      retourApresDeconnexion: () => null,
      deconnecter: () => {},
    },
    fetch: fournisseurSimule.fetch,
  })
  const enAttente = async () => {
    for (let tour = 0; tour < 200 && attentes.length === 0; tour += 1) await new Promise((resoudre) => setTimeout(resoudre, 1))
    assert.equal(attentes.length, 1, 'une seule autorisation en attente')
  }
  const premiere = session.seConnecter('/alertes')
  await enAttente()
  const seconde = session.seConnecter('/recherche')
  assert.equal(await premiere, null)
  await enAttente()
  assert.equal(session.etat, 'CONNEXION_EN_COURS')
  assert.equal(etats.some((entree) => entree.etat === 'ERREUR'), false)
  const courante = attentes.at(-1)
  courante.resoudre(fournisseurSimule.autoriser(courante.url))
  assert.equal(await seconde, '/recherche')
  assert.equal(session.etat, 'CONNECTE')
})

test('RFC 9207 : un retour annonçant un autre émetteur n’échange aucun code', async () => {
  const fournisseurSimule = fournisseur()
  const { session } = installer({
    transport: {
      preparer: async () => 'http://127.0.0.1:50123/argos/retour',
      lancer: async (url) => `${fournisseurSimule.autoriser(url)}&iss=${encodeURIComponent('http://autre.test/realms/argos')}`,
      reprendre: () => null,
      retourApresDeconnexion: () => null,
      deconnecter: () => {},
    },
    fetch: fournisseurSimule.fetch,
  })
  assert.equal(await session.seConnecter('/'), null)
  assert.equal(session.etat, 'ERREUR')
  assert.equal(fournisseurSimule.appels.some((appel) => appel.url.endsWith('/token')), false)
})
