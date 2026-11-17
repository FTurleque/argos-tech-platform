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
  const f = {
    codes: new Map(),
    appels: [],
    emis: 0,
    renouvellementRefuse: false,
    decouverteEnPanne: false,
    async fetch(url, init = {}) {
      f.appels.push({ url, init })
      if (url === `${EMETTEUR}/.well-known/openid-configuration`) {
        if (f.decouverteEnPanne) return reponse(503, {})
        return reponse(200, {
          issuer: EMETTEUR,
          authorization_endpoint: `${EMETTEUR}/auth`,
          token_endpoint: `${EMETTEUR}/token`,
          end_session_endpoint: `${EMETTEUR}/logout`,
        })
      }
      if (url === `${EMETTEUR}/token`) {
        const p = new URLSearchParams(init.body)
        if (p.get('grant_type') === 'authorization_code') {
          const attendu = f.codes.get(p.get('code'))
          const defi = createHash('sha256').update(p.get('code_verifier') ?? '').digest('base64url')
          if (!attendu || attendu.defi !== defi || attendu.redirectUri !== p.get('redirect_uri')) {
            return reponse(400, { error: 'invalid_grant' })
          }
          f.codes.delete(p.get('code'))
          return reponse(200, f.jetons())
        }
        if (p.get('grant_type') === 'refresh_token') {
          if (f.renouvellementRefuse) return reponse(400, { error: 'invalid_grant' })
          return reponse(200, { ...f.jetons(), id_token: undefined })
        }
      }
      return reponse(404, {})
    },
    jetons() {
      f.emis += 1
      return { access_token: `acces-${f.emis}`, refresh_token: `renouv-${f.emis}`, id_token: `id-${f.emis}`, expires_in: 600, token_type: 'Bearer' }
    },
    /** Autorise la demande contenue dans l'URL et renvoie la chaîne de requête du retour. */
    autoriser(url, { state } = {}) {
      const u = new URL(url)
      const code = `code-${f.codes.size + 1}`
      f.codes.set(code, { defi: u.searchParams.get('code_challenge'), redirectUri: u.searchParams.get('redirect_uri') })
      return `?state=${state ?? u.searchParams.get('state')}&session_state=x&code=${code}`
    },
  }
  return f
}

function reponse(status, corps) {
  return { ok: status >= 200 && status < 300, status, json: async () => corps }
}

function installer({ transport, ...options } = {}) {
  const kc = fournisseur()
  const horloge = { t: 1_000_000, minuteries: [] }
  const etats = []
  const ouvertes = []
  const t = transport ?? {
    preparer: async () => 'http://127.0.0.1:50123/argos/retour',
    lancer: async (url) => kc.autoriser(url),
    reprendre: () => null,
    retourApresDeconnexion: () => null,
    deconnecter: (url) => ouvertes.push(url),
  }
  const session = new GestionnaireSession({
    config: { emetteur: EMETTEUR, clientId: 'argos-ui', portee: 'openid' },
    fetch: kc.fetch,
    transport: t,
    maintenant: () => horloge.t,
    planifier: (fn, ms) => { const m = { fn, ms, active: true }; horloge.minuteries.push(m); return m },
    annuler: (m) => { m.active = false },
    surEtat: (etat, message) => etats.push({ etat, message }),
    ...options,
  })
  return { kc, horloge, etats, ouvertes, session }
}

test('ACC-07-S2 : connexion aboutie → jetons en mémoire, vue demandée, renouvellement planifié', async () => {
  const { kc, horloge, etats, session } = installer()
  assert.equal(session.jetonAcces(), null)
  const vue = await session.seConnecter('/alertes/42')
  assert.equal(vue, '/alertes/42')
  assert.equal(session.etat, 'CONNECTE')
  assert.equal(session.jetonAcces(), 'acces-1')
  assert.deepEqual(etats.map((e) => e.etat), ['CONNEXION_EN_COURS', 'CONNECTE'])
  const actives = horloge.minuteries.filter((m) => m.active)
  assert.equal(actives.length, 1)
  assert.equal(actives[0].ms, 540_000)
  // L'échange porte le vérificateur PKCE et n'envoie aucun cookie.
  const echange = kc.appels.find((a) => a.url.endsWith('/token'))
  assert.match(echange.init.body, /code_verifier=/)
  assert.equal(echange.init.credentials, 'omit')
  // Aucun jeton ni vérificateur dans une adresse appelée.
  for (const a of kc.appels) assert.doesNotMatch(a.url, /acces-|code_verifier|renouv-/)
})

test('ACC-07-S3 : un state différent n’échange aucun code et n’ouvre aucune session', async () => {
  const kc = fournisseur()
  const { session, etats } = installer({
    transport: {
      preparer: async () => 'http://127.0.0.1:50123/argos/retour',
      lancer: async (url) => kc.autoriser(url, { state: 'falsifie' }),
      reprendre: () => null,
      retourApresDeconnexion: () => null,
      deconnecter: () => {},
    },
    fetch: kc.fetch,
  })
  assert.equal(await session.seConnecter('/'), null)
  assert.equal(session.etat, 'ERREUR')
  assert.equal(session.jetonAcces(), null)
  assert.equal(kc.appels.some((a) => a.url.endsWith('/token')), false)
  assert.match(etats.at(-1).message, /invalide/)
})

test('ACC-07-S4 : refus du fournisseur → état refusé avec un message explicite, aucun jeton', async () => {
  const { session, kc } = installer({
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
  assert.equal(kc.appels.some((a) => a.url.endsWith('/token')), false)
})

test('portail web : le retour repris au chargement est échangé avec la demande enregistrée', async () => {
  const kc = fournisseur()
  let demandeEnregistree = null
  let urlAutorisation = null
  const transport = {
    preparer: async () => 'http://localhost:5173/',
    lancer: async (url, demande) => { demandeEnregistree = demande; urlAutorisation = url; return null },
    reprendre: () => (demandeEnregistree ? { recherche: kc.autoriser(urlAutorisation), demande: demandeEnregistree } : null),
    retourApresDeconnexion: () => 'http://localhost:5173/',
    deconnecter: () => {},
  }
  const avant = installer({ transport, fetch: kc.fetch })
  assert.equal(await avant.session.seConnecter('/affichage'), null)
  assert.equal(avant.session.etat, 'CONNEXION_EN_COURS')
  // Nouvelle page (rechargement) : nouvelle instance, mêmes données transitoires.
  const apres = installer({ transport, fetch: kc.fetch })
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
  const { session, kc } = installer({
    transport: {
      preparer: async () => 'http://localhost:5173/', lancer: async () => { lance = true; return null },
      reprendre: () => null, retourApresDeconnexion: () => null, deconnecter: () => {},
    },
  })
  kc.decouverteEnPanne = true
  assert.equal(await session.seConnecter('/'), null)
  assert.equal(session.etat, 'ERREUR')
  assert.equal(lance, false)
})

test('ACC-08-S2 : la minuterie renouvelle les jetons avant expiration et en planifie une autre', async () => {
  const { session, horloge } = installer()
  await session.seConnecter('/')
  horloge.t += 540_000
  const premiere = horloge.minuteries.find((m) => m.active)
  premiere.active = false // une minuterie échue n'est plus active
  premiere.fn()
  // Le renouvellement est asynchrone (réponse simulée) : quelques tours de boucle suffisent.
  for (let i = 0; i < 5; i += 1) await new Promise((r) => setImmediate(r))
  assert.equal(session.jetonAcces(), 'acces-2')
  assert.equal(session.etat, 'CONNECTE')
  assert.equal(horloge.minuteries.filter((m) => m.active).length, 1)
})

test('ACC-08-S3 : renouvellement refusé → session expirée, jetons oubliés', async () => {
  const { session, kc, etats } = installer()
  await session.seConnecter('/')
  kc.renouvellementRefuse = true
  assert.equal(await session.renouveler(), false)
  assert.equal(session.etat, 'EXPIREE')
  assert.equal(session.jetonAcces(), null)
  assert.match(etats.at(-1).message, /expiré/)
})

test('un jeton arrivé à expiration n’est plus fourni, même sans renouvellement', async () => {
  const { session, horloge } = installer()
  await session.seConnecter('/')
  horloge.t += 600_000
  assert.equal(session.jetonAcces(), null)
})

test('ACC-09-S3 : un 401 de l’API (expirer) oublie les jetons et signale la session expirée', async () => {
  const { session, horloge } = installer()
  await session.seConnecter('/')
  session.expirer()
  assert.equal(session.etat, 'EXPIREE')
  assert.equal(session.jetonAcces(), null)
  assert.equal(horloge.minuteries.filter((m) => m.active).length, 0)
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
  assert.equal(horloge.minuteries.filter((m) => m.active).length, 0)
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
  const kc = fournisseur()
  const attentes = []
  const { session, etats } = installer({
    transport: {
      preparer: async () => {
        // Comme le processus principal : une nouvelle écoute ferme la précédente (retour null).
        attentes.splice(0).forEach((a) => a.resoudre(null))
        return 'http://127.0.0.1:50123/argos/retour'
      },
      lancer: (url) => new Promise((resoudre) => attentes.push({ url, resoudre })),
      reprendre: () => null,
      retourApresDeconnexion: () => null,
      deconnecter: () => {},
    },
    fetch: kc.fetch,
  })
  const enAttente = async () => {
    for (let i = 0; i < 200 && attentes.length === 0; i += 1) await new Promise((r) => setTimeout(r, 1))
    assert.equal(attentes.length, 1, 'une seule autorisation en attente')
  }
  const premiere = session.seConnecter('/alertes')
  await enAttente()
  const seconde = session.seConnecter('/recherche')
  assert.equal(await premiere, null)
  await enAttente()
  assert.equal(session.etat, 'CONNEXION_EN_COURS')
  assert.equal(etats.some((e) => e.etat === 'ERREUR'), false)
  const courante = attentes.at(-1)
  courante.resoudre(kc.autoriser(courante.url))
  assert.equal(await seconde, '/recherche')
  assert.equal(session.etat, 'CONNECTE')
})

test('RFC 9207 : un retour annonçant un autre émetteur n’échange aucun code', async () => {
  const kc = fournisseur()
  const { session } = installer({
    transport: {
      preparer: async () => 'http://127.0.0.1:50123/argos/retour',
      lancer: async (url) => `${kc.autoriser(url)}&iss=${encodeURIComponent('http://autre.test/realms/argos')}`,
      reprendre: () => null,
      retourApresDeconnexion: () => null,
      deconnecter: () => {},
    },
    fetch: kc.fetch,
  })
  assert.equal(await session.seConnecter('/'), null)
  assert.equal(session.etat, 'ERREUR')
  assert.equal(kc.appels.some((a) => a.url.endsWith('/token')), false)
})
