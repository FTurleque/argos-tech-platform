// Fonctions pures du flux OIDC de l'IHM (changement connecter-ihm-oidc : ACC-07, ACC-08).
// Aucune requête réseau : seules des chaînes et le générateur cryptographique de Node sont utilisés.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import {
  aleatoire,
  base64Url,
  cheminInterne,
  construireUrlAutorisation,
  construireUrlDeconnexion,
  contientRetour,
  corpsEchangeCode,
  corpsRenouvellement,
  defiPkce,
  delaiRenouvellement,
  jetonValide,
  lireDecouverte,
  lireRetour,
  lireReponseJetons,
  messageRefus,
  portee,
  urlNettoyee,
} from '../src/session/oidc.ts'

const EMETTEUR = 'http://localhost:8180/realms/argos'

test('RFC 7636 annexe B : défi S256 du vérificateur de référence', async () => {
  const verificateur = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'
  assert.equal(await defiPkce(verificateur), 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')
})

test('le défi est le SHA-256 base64url du vérificateur, sans remplissage', async () => {
  const verificateur = aleatoire(32)
  const attendu = createHash('sha256').update(verificateur).digest('base64url')
  assert.equal(await defiPkce(verificateur), attendu)
  assert.doesNotMatch(await defiPkce(verificateur), /[+/=]/)
})

test('valeurs aléatoires : 43 caractères base64url pour 32 octets, toutes différentes', () => {
  const valeurs = new Set(Array.from({ length: 50 }, () => aleatoire(32)))
  assert.equal(valeurs.size, 50)
  for (const v of valeurs) assert.match(v, /^[A-Za-z0-9_-]{43}$/)
  assert.equal(base64Url(new Uint8Array([0xfb, 0xff])), '-_8')
})

test('ACC-07 : l’URL d’autorisation demande un code avec PKCE S256 et un state', () => {
  const url = new URL(construireUrlAutorisation({
    pointAutorisation: `${EMETTEUR}/protocol/openid-connect/auth`,
    clientId: 'argos-ui',
    redirectUri: 'http://localhost:5173/',
    portee: 'profile',
    state: 'etat-1',
    defi: 'defi-1',
  }))
  assert.equal(url.origin + url.pathname, `${EMETTEUR}/protocol/openid-connect/auth`)
  assert.equal(url.searchParams.get('response_type'), 'code')
  assert.equal(url.searchParams.get('client_id'), 'argos-ui')
  assert.equal(url.searchParams.get('redirect_uri'), 'http://localhost:5173/')
  assert.equal(url.searchParams.get('scope'), 'openid profile')
  assert.equal(url.searchParams.get('state'), 'etat-1')
  assert.equal(url.searchParams.get('code_challenge'), 'defi-1')
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256')
  assert.equal(url.searchParams.get('response_mode'), 'query')
  // Ni vérificateur ni jeton dans l'adresse.
  assert.equal(url.searchParams.has('code_verifier'), false)
  assert.equal(portee('openid email'), 'openid email')
})

test('ACC-07-S2 : un retour avec le bon state donne le code', () => {
  assert.deepEqual(lireRetour('?state=s1&session_state=x&iss=y&code=c1', 's1'), { type: 'code', code: 'c1' })
  assert.equal(contientRetour('?state=s1&code=c1'), true)
  assert.equal(contientRetour('?autre=1'), false)
  assert.deepEqual(lireRetour('', 's1'), { type: 'absent' })
})

test('ACC-07-S3 : state absent, différent ou sans demande en cours → retour invalide', () => {
  assert.deepEqual(lireRetour('?code=c1', 's1'), { type: 'state-invalide' })
  assert.deepEqual(lireRetour('?code=c1&state=s2', 's1'), { type: 'state-invalide' })
  assert.deepEqual(lireRetour('?code=c1&state=s1', null), { type: 'state-invalide' })
  assert.deepEqual(lireRetour('?code=c1&state=s', 's1'), { type: 'state-invalide' })
  // Un refus sans le bon state est traité comme falsifié.
  assert.deepEqual(lireRetour('?error=access_denied&state=autre', 's1'), { type: 'state-invalide' })
})

test('ACC-07-S4 : un refus du fournisseur donne un message fixe, jamais le texte reçu', () => {
  assert.deepEqual(
    lireRetour('?error=access_denied&error_description=Appelez%20le%2006&state=s1', 's1'),
    { type: 'refus', erreur: 'access_denied' },
  )
  assert.equal(messageRefus('access_denied'), 'Le fournisseur d’identité a refusé la connexion.')
  assert.equal(messageRefus('<script>'), 'La connexion a été refusée par le fournisseur d’identité.')
})

test('ACC-07-S2 : l’adresse nettoyée ne garde ni code ni state et pointe sur la vue demandée', () => {
  const propre = urlNettoyee(
    'http://localhost:5173/?state=s1&session_state=x&iss=http%3A%2F%2Fkc&code=c1&error_uri=u&garde=1',
    '/alertes/42',
  )
  assert.equal(propre, 'http://localhost:5173/?garde=1#/alertes/42')
  assert.equal(urlNettoyee('app://argos/index.html?code=c&state=s', '/'), 'app://argos/index.html#/')
})

test('après connexion, seule une vue interne est acceptée (pas de redirection ouverte)', () => {
  assert.equal(cheminInterne('/affichage'), '/affichage')
  assert.equal(cheminInterne('/alertes/1?x=2'), '/alertes/1?x=2')
  for (const dangereux of ['https://malveillant.example', '//malveillant.example', 'javascript:alert(1)', '/\\evil', '/a\nb', 42, undefined, '']) {
    assert.equal(cheminInterne(dangereux), '/', String(dangereux))
  }
})

test('découverte : l’émetteur annoncé doit être celui configuré', () => {
  const doc = {
    issuer: EMETTEUR,
    authorization_endpoint: `${EMETTEUR}/protocol/openid-connect/auth`,
    token_endpoint: `${EMETTEUR}/protocol/openid-connect/token`,
    end_session_endpoint: `${EMETTEUR}/protocol/openid-connect/logout`,
  }
  assert.equal(lireDecouverte(doc, `${EMETTEUR}/`).token_endpoint, doc.token_endpoint)
  assert.throws(() => lireDecouverte({ ...doc, issuer: 'http://autre/realms/argos' }, EMETTEUR), /Émetteur/)
  assert.throws(() => lireDecouverte({ ...doc, token_endpoint: undefined }, EMETTEUR), /token_endpoint/)
  assert.equal(lireDecouverte({ ...doc, end_session_endpoint: undefined }, EMETTEUR).end_session_endpoint, null)
})

test('corps des requêtes de jetons : code + vérificateur PKCE, ou jeton de renouvellement', () => {
  assert.equal(
    corpsEchangeCode({ code: 'c1', redirectUri: 'http://localhost:5173/', clientId: 'argos-ui', verificateur: 'v1' }).toString(),
    'grant_type=authorization_code&code=c1&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F&client_id=argos-ui&code_verifier=v1',
  )
  assert.equal(
    corpsRenouvellement({ renouvellement: 'r1', clientId: 'argos-ui' }).toString(),
    'grant_type=refresh_token&refresh_token=r1&client_id=argos-ui',
  )
})

test('réponse de jetons : expiration calculée, réponse incomplète refusée', () => {
  const jetons = lireReponseJetons({ access_token: 'a', refresh_token: 'r', id_token: 'i', expires_in: 600, token_type: 'Bearer' }, 1_000)
  assert.deepEqual(jetons, { acces: 'a', renouvellement: 'r', identite: 'i', expireLe: 601_000 })
  assert.equal(lireReponseJetons({ access_token: 'a', expires_in: 60 }, 0).renouvellement, null)
  assert.throws(() => lireReponseJetons({ expires_in: 60 }, 0))
  assert.throws(() => lireReponseJetons({ access_token: 'a' }, 0))
  assert.throws(() => lireReponseJetons({ access_token: 'a', expires_in: 60, token_type: 'mac' }, 0))
  assert.equal(jetonValide(jetons, 600_999), true)
  assert.equal(jetonValide(jetons, 601_000), false)
  assert.equal(jetonValide(null, 0), false)
})

test('ACC-08-S2 : renouvellement avant l’expiration (marge de 20 %, au plus 60 s)', () => {
  assert.equal(delaiRenouvellement(600_000, 0), 540_000)
  assert.equal(delaiRenouvellement(60_000, 0), 48_000)
  assert.equal(delaiRenouvellement(1_000, 2_000), 0)
})

test('ACC-08-S4 : URL de fin de session avec indice d’identité et retour éventuel', () => {
  const url = new URL(construireUrlDeconnexion({
    pointFinSession: `${EMETTEUR}/protocol/openid-connect/logout`,
    clientId: 'argos-ui',
    jetonIdentite: 'i1',
    retourApresDeconnexion: 'http://localhost:5173/',
  }))
  assert.equal(url.searchParams.get('id_token_hint'), 'i1')
  assert.equal(url.searchParams.get('client_id'), 'argos-ui')
  assert.equal(url.searchParams.get('post_logout_redirect_uri'), 'http://localhost:5173/')
  const desktop = new URL(construireUrlDeconnexion({
    pointFinSession: `${EMETTEUR}/protocol/openid-connect/logout`, clientId: 'argos-ui', jetonIdentite: null, retourApresDeconnexion: null,
  }))
  assert.equal(desktop.searchParams.has('post_logout_redirect_uri'), false)
  assert.equal(construireUrlDeconnexion({ pointFinSession: null, clientId: 'x', jetonIdentite: null, retourApresDeconnexion: null }), null)
})

test('RFC 9207 : le paramètre iss du retour doit désigner l’émetteur attendu', () => {
  const emetteur = 'http://kc.test/realms/argos'
  assert.deepEqual(lireRetour(`?state=s1&code=c1&iss=${encodeURIComponent(emetteur)}`, 's1', emetteur), { type: 'code', code: 'c1' })
  assert.deepEqual(lireRetour(`?state=s1&code=c1&iss=${encodeURIComponent(emetteur + '/')}`, 's1', emetteur), { type: 'code', code: 'c1' })
  assert.deepEqual(lireRetour('?state=s1&code=c1', 's1', emetteur), { type: 'code', code: 'c1' })
  assert.deepEqual(lireRetour('?state=s1&code=c1&iss=http%3A%2F%2Fautre.test', 's1', emetteur), { type: 'emetteur-invalide' })
})
