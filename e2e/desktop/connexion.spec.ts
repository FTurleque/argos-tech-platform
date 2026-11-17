// Parcours de connexion du client desktop Electron (changement connecter-ihm-oidc, tâche 5.2) :
// autorisation dans le « navigateur du système », retour unique sur l'interface de bouclage
// 127.0.0.1, fenêtre ramenée au premier plan, session ouverte dans l'IHM du desktop, CSP.
// Le navigateur du système est une page Chromium pilotée par Playwright : la doublure de
// shell.openExternal (support/electron.ts) lui transmet l'adresse que le desktop voulait ouvrir.
import { EMETTEUR, URL_API, URL_IHM } from '../support/environnement'
import { connecterCompte, lancerDesktop, reduireFenetre, type ApplicationDesktop } from '../support/electron'
import { expect, test } from '../support/fixtures'

const ORIGINE_EMETTEUR = new URL(EMETTEUR).origin
const CSP_ATTENDUE = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "font-src 'self'",
  `connect-src 'self' ${new URL(URL_API).origin} ${ORIGINE_EMETTEUR}`,
  "object-src 'none'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
].join('; ')

let desktop: ApplicationDesktop

test.beforeEach(async ({}, testInfo) => {
  desktop = await lancerDesktop({ dossierDonnees: testInfo.outputPath('donnees-desktop') })
})

test.afterEach(async () => {
  await desktop?.fermer()
})

test('ACC-10-S1, S2, S4 : connexion par le navigateur du système et retour sur 127.0.0.1', async ({ page: navigateur }) => {
  const { fenetre } = desktop

  // ACC-10-S4 : l'IHM est servie par le protocole interne avec la CSP limitée au serveur et à l'émetteur.
  await expect(fenetre).toHaveURL(/^app:\/\/argos\/index\.html#\//)
  const csp = await fenetre.evaluate(async () => (await fetch('app://argos/index.html')).headers.get('content-security-policy'))
  expect(csp).toBe(CSP_ATTENDUE)

  // Vue protégée sans session : écran de connexion.
  await fenetre.evaluate(() => { window.location.hash = '#/recherche' })
  await expect(fenetre).toHaveURL(/#\/connexion\?retour=\/recherche$/)

  let redirectUri = ''
  const { urlAutorisation, retour } = await connecterCompte(desktop, navigateur, 'alice', async () => {
    // ACC-10-S1 : la fenêtre attend le navigateur du système, sans afficher la page du fournisseur.
    await expect(fenetre.getByText('Poursuivez la connexion dans votre navigateur, puis revenez à ARGOS.')).toBeVisible()
    expect((await desktop.etatFenetre()).adresse).toMatch(/^app:\/\/argos\/index\.html#\/connexion/)
    // La personne passe dans son navigateur : la fenêtre ARGOS est réduite.
    await reduireFenetre(desktop)
  })

  // ACC-10-S1 : adresse d'autorisation de l'émetteur configuré, PKCE S256, redirection vers
  // 127.0.0.1 sur un port choisi par le système.
  expect(urlAutorisation.origin).toBe(ORIGINE_EMETTEUR)
  expect(urlAutorisation.pathname).toBe(`${new URL(EMETTEUR).pathname}/protocol/openid-connect/auth`)
  const p = urlAutorisation.searchParams
  expect(p.get('response_type')).toBe('code')
  expect(p.get('client_id')).toBe('argos-ui')
  expect(p.get('code_challenge_method')).toBe('S256')
  expect(p.get('code_challenge')).toBeTruthy()
  expect(p.get('state')).toBeTruthy()
  redirectUri = p.get('redirect_uri') ?? ''
  const port = Number(/^http:\/\/127\.0\.0\.1:(\d+)\/argos\/retour$/.exec(redirectUri)?.[1])
  expect(port).toBeGreaterThan(0)

  // ACC-10-S2 : le fournisseur redirige vers l'écoute locale, qui répond par une page statique.
  const adresseRetour = new URL(retour.url())
  expect(`${adresseRetour.origin}${adresseRetour.pathname}`).toBe(redirectUri)
  expect(adresseRetour.searchParams.get('state')).toBe(p.get('state'))
  expect(adresseRetour.searchParams.get('code')).toBeTruthy()
  expect(retour.status()).toBe(200)
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

  // ACC-10-S2 : la fenêtre ARGOS revient au premier plan.
  await expect.poll(async () => {
    const etat = await desktop.etatFenetre()
    return etat.visible && !etat.reduite
  }).toBe(true)
  const etat = await desktop.etatFenetre()
  expect(etat.evenements).toEqual(expect.arrayContaining(['minimize', 'restore']))
  expect(etat.focus).toBe(true)

  // ACC-10-S2 : retour unique, l'écoute locale est fermée.
  await expect(fetch(redirectUri)).rejects.toThrow()

  // La personne est connectée dans l'IHM du desktop, sur la vue demandée.
  await expect(fenetre.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()
  await expect(fenetre.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  await expect(fenetre).toHaveURL(/^app:\/\/argos\/index\.html#\/recherche$/)
  // Appel API authentifié par le jeton porteur, accepté par le serveur.
  await expect.poll(async () => (await desktop.journal()).some((r) => (
    r.adresse === `${URL_API}/api/v1/moi` && r.methode === 'GET' && r.porteur && r.statut === 200
  ))).toBe(true)
  await fenetre.screenshot({ path: 'resultats/desktop-connexion-recherche.jpg', type: 'jpeg', quality: 60 })

  // ACC-10-S4 : la CSP est appliquée. Origine locale hors politique (le portail Vite) : connexion,
  // script externe et script en ligne sont bloqués avant le réseau.
  const cible = `${URL_IHM}/`
  const controle = await fenetre.evaluate(async (adresse) => {
    const violations: string[] = []
    document.addEventListener('securitypolicyviolation', (e) => violations.push(e.effectiveDirective))
    let connexionBloquee = false
    try {
      await fetch(adresse)
    } catch {
      connexionBloquee = true
    }
    const externe = document.createElement('script')
    externe.src = `${adresse}script-e2e.js`
    const chargement = new Promise((resolve) => {
      externe.onload = () => resolve('chargé')
      externe.onerror = () => resolve('bloqué')
    })
    document.head.appendChild(externe)
    const scriptExterne = await chargement
    const enLigne = document.createElement('script')
    enLigne.textContent = 'window.__scriptEnLigneE2E = true'
    document.head.appendChild(enLigne)
    await new Promise((resolve) => setTimeout(resolve, 300))
    externe.remove()
    enLigne.remove()
    const w = window as unknown as { __scriptEnLigneE2E?: boolean }
    return { connexionBloquee, scriptExterne, scriptEnLigne: w.__scriptEnLigneE2E === true, violations }
  }, cible)
  expect(controle.connexionBloquee).toBe(true)
  expect(controle.scriptExterne).toBe('bloqué')
  expect(controle.scriptEnLigne).toBe(false)
  expect(controle.violations).toEqual(expect.arrayContaining(['connect-src', 'script-src-elem']))

  // Journal des requêtes de la session : aucune vers un hôte hors de la boucle locale, aucun
  // document hors du protocole interne (la page du fournisseur ne s'affiche jamais dans la fenêtre),
  // vers l'émetteur seulement la découverte et l'échange du code, rien vers l'origine bloquée.
  const journal = await desktop.journal()
  expect(journal.filter((r) => r.refusee).map((r) => r.adresse), 'hôtes hors de la boucle locale').toEqual([])
  expect(journal.filter((r) => r.type === 'mainFrame' || r.type === 'subFrame').map((r) => r.protocole))
    .toEqual(expect.arrayContaining(['app:']))
  for (const r of journal.filter((x) => x.type === 'mainFrame' || x.type === 'subFrame')) expect(r.protocole, r.adresse).toBe('app:')
  const versEmetteur = new Set(journal.filter((r) => r.adresse.startsWith(ORIGINE_EMETTEUR)).map((r) => r.adresse))
  expect([...versEmetteur].sort()).toEqual([
    `${EMETTEUR}/.well-known/openid-configuration`,
    `${EMETTEUR}/protocol/openid-connect/token`,
  ])
  expect(journal.filter((r) => r.adresse.startsWith(new URL(URL_IHM).origin))).toEqual([])
  const origines = new Set(journal.map((r) => (r.protocole === 'app:' ? 'app://argos' : `${r.protocole}//${r.hote}`)))
  for (const origine of origines) {
    expect(['app://argos', new URL(URL_API).origin, ORIGINE_EMETTEUR, 'file://'], origine).toContain(origine)
  }
})
