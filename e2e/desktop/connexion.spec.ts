// Parcours de connexion du client desktop Electron (changement connecter-ihm-oidc, tâche 5.2) :
// autorisation dans le « navigateur du système », retour unique sur l'interface de bouclage
// 127.0.0.1, fenêtre ramenée au premier plan, session ouverte dans l'IHM du desktop, CSP.
// Le navigateur du système est une page Chromium pilotée par Playwright : la doublure de
// shell.openExternal (support/electron.ts) lui transmet l'adresse que le desktop voulait ouvrir.
import type { Page, Response } from '@playwright/test'
import { EMETTEUR, URL_API } from '../support/environnement'
import {
  connecterCompte, lancerDesktop, reduireFenetre, type ApplicationDesktop, type RequeteDesktop,
} from '../support/electron'
import { expect, test } from '../support/fixtures'
import { PORT_SOURCES } from '../support/pile.mjs'

const ORIGINE_EMETTEUR = new URL(EMETTEUR).origin
const ORIGINE_API = new URL(URL_API).origin
/** Origine locale hors de la politique du desktop (sources simulées) : toute requête vers elle est bloquée. */
const ORIGINE_BLOQUEE = `http://127.0.0.1:${PORT_SOURCES}`
const CSP_ATTENDUE = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "font-src 'self'",
  `connect-src 'self' ${ORIGINE_API} ${ORIGINE_EMETTEUR}`,
  "object-src 'none'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
].join('; ')
const STATUT_OK = 200
/** Attente maximale des violations de CSP dans la page, en millisecondes. */
const ATTENTE_VIOLATIONS = 5_000

let desktop: ApplicationDesktop | undefined

test.beforeEach(async () => {
  desktop = undefined
  desktop = await lancerDesktop({ dossierDonnees: test.info().outputPath('donnees-desktop') })
})

test.afterEach(async () => {
  await desktop?.fermer()
  desktop = undefined
})

/**
 * ACC-10-S1 : adresse d'autorisation de l'émetteur configuré, PKCE S256, redirection vers 127.0.0.1 sur un port
 * choisi par le système ; renvoie l'adresse de redirection.
 */
function verifierAutorisation(urlAutorisation: URL): string {
  expect(urlAutorisation.origin).toBe(ORIGINE_EMETTEUR)
  expect(urlAutorisation.pathname).toBe(`${new URL(EMETTEUR).pathname}/protocol/openid-connect/auth`)
  const parametres = urlAutorisation.searchParams
  expect(parametres.get('response_type')).toBe('code')
  expect(parametres.get('client_id')).toBe('argos-ui')
  expect(parametres.get('code_challenge_method')).toBe('S256')
  expect(parametres.get('code_challenge')).toBeTruthy()
  expect(parametres.get('state')).toBeTruthy()
  const redirectUri = parametres.get('redirect_uri') ?? ''
  const port = Number(/^http:\/\/127\.0\.0\.1:(\d+)\/argos\/retour$/.exec(redirectUri)?.[1])
  expect(port).toBeGreaterThan(0)
  return redirectUri
}

/** ACC-10-S2 : le fournisseur redirige vers l'écoute locale, qui répond par une page statique sans le code. */
async function verifierPageRetour(retour: Response, redirectUri: string, state: string | null, navigateur: Page) {
  const adresseRetour = new URL(retour.url())
  expect(`${adresseRetour.origin}${adresseRetour.pathname}`).toBe(redirectUri)
  expect(adresseRetour.searchParams.get('state')).toBe(state)
  expect(adresseRetour.searchParams.get('code')).toBeTruthy()
  expect(retour.status()).toBe(STATUT_OK)
  const entetes = retour.headers()
  expect(entetes['content-security-policy']).toBe("default-src 'none'")
  expect(entetes['cache-control']).toBe('no-store')
  expect(entetes['referrer-policy']).toBe('no-referrer')
  expect(entetes['x-content-type-options']).toBe('nosniff')
  const page = await retour.text()
  expect(page).toContain('Connexion transmise à ARGOS')
  for (const valeur of adresseRetour.searchParams.values()) expect(page).not.toContain(valeur)
  await expect(navigateur.getByRole('heading', { name: 'Connexion transmise à ARGOS' })).toBeVisible()
  await navigateur.screenshot({ path: 'resultats/desktop-connexion-page-retour.jpg', type: 'jpeg', quality: 60 })
}

/** ACC-10-S2 : la fenêtre ARGOS revient au premier plan après avoir été réduite. */
async function verifierRetourAuPremierPlan(client: ApplicationDesktop): Promise<void> {
  await expect.poll(async () => {
    const etat = await client.etatFenetre()
    return etat.visible && !etat.reduite
  }).toBe(true)
  const etat = await client.etatFenetre()
  expect(etat.evenements).toEqual(expect.arrayContaining(['minimize', 'restore']))
  expect(etat.focus).toBe(true)
}

/** Résultat du contrôle de la CSP exécuté dans la fenêtre. */
interface ControleCsp {
  connexionBloquee: boolean
  scriptExterne: string
  scriptEnLigne: boolean
  violations: string[]
}

/**
 * ACC-10-S4 : vers une origine hors politique, connexion, script externe et script en ligne sont bloqués avant le
 * réseau. Exécuté dans la page : attente sur condition des deux directives attendues, bornée.
 */
function controlerCsp({ adresse, attente }: { adresse: string, attente: number }): Promise<ControleCsp> {
  const violations: string[] = []
  const attendues = ['connect-src', 'script-src-elem']
  let signaler: () => void = () => {}
  const recues = new Promise<void>((resolve) => { signaler = resolve })
  const delai = setTimeout(() => signaler(), attente)
  document.addEventListener('securitypolicyviolation', (evenement) => {
    violations.push(evenement.effectiveDirective)
    if (attendues.every((directive) => violations.includes(directive))) signaler()
  })
  const connexion = fetch(adresse).then(() => false, () => true)
  const externe = document.createElement('script')
  externe.src = `${adresse}script-e2e.js`
  const chargement = new Promise<string>((resolve) => {
    externe.onload = () => resolve('chargé')
    externe.onerror = () => resolve('bloqué')
  })
  document.head.appendChild(externe)
  const enLigne = document.createElement('script')
  enLigne.textContent = 'window.__scriptEnLigneE2E = true'
  return Promise.all([connexion, chargement]).then(([connexionBloquee, scriptExterne]) => {
    document.head.appendChild(enLigne)
    return recues.then(() => {
      clearTimeout(delai)
      externe.remove()
      enLigne.remove()
      const fenetreE2E = window as unknown as { __scriptEnLigneE2E?: boolean }
      return { connexionBloquee, scriptExterne, scriptEnLigne: fenetreE2E.__scriptEnLigneE2E === true, violations }
    })
  })
}

/** Origine d'une requête du journal, le protocole interne compris. */
function origineDe(requete: RequeteDesktop): string {
  return requete.protocole === 'app:' ? 'app://argos' : `${requete.protocole}//${requete.hote}`
}

/**
 * Journal des requêtes de la session : aucune vers un hôte hors de la boucle locale, aucun document hors du protocole
 * interne (la page du fournisseur ne s'affiche jamais dans la fenêtre), vers l'émetteur seulement la découverte et
 * l'échange du code, rien vers l'origine bloquée, aucune autre origine que l'IHM, l'API et l'émetteur.
 */
function verifierJournal(journal: RequeteDesktop[]): void {
  expect(journal.filter((requete) => requete.refusee).map((requete) => requete.adresse), 'hôtes hors de la boucle locale').toEqual([])
  const documents = journal.filter((requete) => requete.type === 'mainFrame' || requete.type === 'subFrame')
  expect(documents.map((requete) => requete.protocole)).toEqual(expect.arrayContaining(['app:']))
  for (const requete of documents) expect(requete.protocole, requete.adresse).toBe('app:')
  const versEmetteur = new Set(journal.filter((requete) => requete.adresse.startsWith(ORIGINE_EMETTEUR)).map((requete) => requete.adresse))
  expect([...versEmetteur].sort()).toEqual([
    `${EMETTEUR}/.well-known/openid-configuration`,
    `${EMETTEUR}/protocol/openid-connect/token`,
  ])
  expect(journal.filter((requete) => requete.adresse.startsWith(ORIGINE_BLOQUEE))).toEqual([])
  for (const origine of new Set(journal.map(origineDe))) {
    expect(['app://argos', ORIGINE_API, ORIGINE_EMETTEUR, 'file://'], origine).toContain(origine)
  }
}

test('ACC-10-S1, S2, S4 : connexion par le navigateur du système et retour sur 127.0.0.1', async ({ page: navigateur }) => {
  const client = desktop
  if (!client) throw new Error('client desktop non lancé')
  const { fenetre } = client

  // ACC-10-S4 : l'IHM est servie par le protocole interne avec la CSP limitée au serveur et à l'émetteur.
  await expect(fenetre).toHaveURL(/^app:\/\/argos\/index\.html#\//)
  const csp = await fenetre.evaluate(async () => (await fetch('app://argos/index.html')).headers.get('content-security-policy'))
  expect(csp).toBe(CSP_ATTENDUE)

  // Vue protégée sans session : écran de connexion.
  await fenetre.evaluate(() => { window.location.hash = '#/recherche' })
  await expect(fenetre).toHaveURL(/#\/connexion\?retour=\/recherche$/)

  const { urlAutorisation, retour } = await connecterCompte(client, navigateur, 'alice', async () => {
    // ACC-10-S1 : la fenêtre attend le navigateur du système, sans afficher la page du fournisseur.
    await expect(fenetre.getByText('Poursuivez la connexion dans votre navigateur, puis revenez à ARGOS.')).toBeVisible()
    expect((await client.etatFenetre()).adresse).toMatch(/^app:\/\/argos\/index\.html#\/connexion/)
    // La personne passe dans son navigateur : la fenêtre ARGOS est réduite.
    await reduireFenetre(client)
  })
  const redirectUri = verifierAutorisation(urlAutorisation)
  await verifierPageRetour(retour, redirectUri, urlAutorisation.searchParams.get('state'), navigateur)
  await verifierRetourAuPremierPlan(client)

  // ACC-10-S2 : retour unique, l'écoute locale est fermée.
  await expect(fetch(redirectUri)).rejects.toThrow()

  // La personne est connectée dans l'IHM du desktop, sur la vue demandée.
  await expect(fenetre.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()
  await expect(fenetre.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  await expect(fenetre).toHaveURL(/^app:\/\/argos\/index\.html#\/recherche$/)
  // Appel API authentifié par le jeton porteur, accepté par le serveur.
  await expect.poll(async () => (await client.journal()).some((requete) => (
    requete.adresse === `${URL_API}/api/v1/moi` && requete.methode === 'GET' && requete.porteur && requete.statut === STATUT_OK
  ))).toBe(true)
  await fenetre.screenshot({ path: 'resultats/desktop-connexion-recherche.jpg', type: 'jpeg', quality: 60 })

  // ACC-10-S4 : la CSP est appliquée à une origine locale hors politique.
  const controle = await fenetre.evaluate(controlerCsp, { adresse: `${ORIGINE_BLOQUEE}/`, attente: ATTENTE_VIOLATIONS })
  expect(controle.connexionBloquee).toBe(true)
  expect(controle.scriptExterne).toBe('bloqué')
  expect(controle.scriptEnLigne).toBe(false)
  expect(controle.violations).toEqual(expect.arrayContaining(['connect-src', 'script-src-elem']))

  verifierJournal(await client.journal())
})
