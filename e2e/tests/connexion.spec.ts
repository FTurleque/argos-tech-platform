// Parcours de session du portail web (changement connecter-ihm-oidc, tâche 5.1) : connexion réelle
// auprès du Keycloak de test, retour sur la vue demandée, adresse nettoyée, jetons hors du stockage,
// jeton porteur, déconnexion, compte non créé faute d'invitation, session expirée.
import type { Page, Request } from '@playwright/test'
import { URL_API } from '../support/environnement'
import { expect, saisirIdentifiants, seConnecterDepuis, test } from '../support/fixtures'

function appelsApi(page: Page): Request[] {
  const appels: Request[] = []
  page.on('request', (r) => {
    if (r.url().startsWith(`${URL_API}/api/v1/`)) appels.push(r)
  })
  return appels
}

/** Valeurs ressemblant à un jeton (JWT) dans les stockages et cookies lisibles de la page. */
async function jetonsDansLeNavigateur(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const valeurs: string[] = [document.cookie]
    for (const stockage of [window.localStorage, window.sessionStorage]) {
      for (let i = 0; i < stockage.length; i++) valeurs.push(stockage.getItem(stockage.key(i)!) ?? '')
    }
    return valeurs.filter((v) => /eyJ[\w-]+\.[\w-]+\./.test(v))
  })
}

test('ACC-07-S1 : une vue protégée sans session affiche la connexion sans appeler l’API', async ({ page }) => {
  const appels = appelsApi(page)
  await page.goto('/#/recherche')
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  await expect(page).toHaveURL(/#\/connexion\?retour=\/recherche$/)
  expect(appels.map((r) => r.url()).filter((u) => !u.endsWith('/api/v1/system/info'))).toEqual([])
})

test('ACC-07-S2, ACC-08-S1, ACC-09-S1/S2 : connexion réelle, retour sur la vue, adresse nettoyée', async ({ page }) => {
  const appels = appelsApi(page)
  await seConnecterDepuis(page, '/recherche', 'alice')

  // Retour sur la vue demandée, sans code ni state dans l'adresse.
  await expect(page.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/^http:\/\/localhost:5173\/#\/recherche$/)
  expect(page.url()).not.toMatch(/[?&](code|state|session_state|iss)=/)
  await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()

  // Aucun jeton dans le stockage local, le stockage de session ni les cookies lisibles (ACC-08-S1).
  expect(await jetonsDansLeNavigateur(page)).toEqual([])
  // La demande transitoire du portail web est effacée dès le retour ; seule reste la clé de
  // défilement de vue-router.
  expect(await page.evaluate(() => Object.keys(window.sessionStorage))).not.toContain('argos.oidc.demande')
  expect(await page.evaluate(() => Object.keys(window.localStorage))).toEqual([])

  // Jeton porteur sur /api/v1 sauf l'identité du serveur, jamais dans l'adresse (ACC-09-S1, S2).
  await page.getByRole('link', { name: 'État' }).click()
  await expect(page.getByText(/argos-server · version/)).toBeVisible()
  const protegees = appels.filter((r) => !r.url().endsWith('/api/v1/system/info'))
  expect(protegees.length).toBeGreaterThan(0)
  for (const r of protegees.filter((x) => x.method() !== 'OPTIONS')) {
    expect(await r.headerValue('authorization'), r.url()).toMatch(/^Bearer eyJ/)
    expect(r.url()).not.toMatch(/eyJ/)
  }
  const info = appels.filter((r) => r.url().endsWith('/api/v1/system/info') && r.method() === 'GET')
  expect(info.length).toBeGreaterThan(0)
  for (const r of info) expect(await r.headerValue('authorization')).toBeNull()
})

test('ACC-07-S3 : un retour au state différent de la demande est refusé sans échange de code', async ({ page }) => {
  const echanges: string[] = []
  page.on('request', (r) => {
    if (r.url().endsWith('/protocol/openid-connect/token')) echanges.push(r.url())
  })
  await page.goto('/#/recherche')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.locator('#username')).toBeVisible()
  // Retour forgé : code et state inventés, la demande en cours attend un autre state.
  await page.goto('/?code=code-forge&state=state-forge')
  await expect(page.getByText('Connexion impossible')).toBeVisible()
  await expect(page.getByText('Retour de connexion invalide : la demande a été ignorée.')).toBeVisible()
  expect(page.url()).not.toMatch(/code-forge|state-forge/)
  expect(echanges).toEqual([])
  await expect(page.getByRole('link', { name: 'Se connecter' })).toBeVisible()
})

test('ACC-08-S4 : la déconnexion ferme la session de l’IHM et celle du fournisseur', async ({ page }) => {
  await seConnecterDepuis(page, '/recherche', 'alice')
  await expect(page.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Se déconnecter' }).click()
  // Fin de session du fournisseur, puis retour sur le portail.
  await page.waitForURL(/^http:\/\/localhost:5173\//)
  await expect(page.getByRole('link', { name: 'Se connecter' })).toBeVisible()

  // Une vue protégée ouverte ensuite affiche l'écran de connexion.
  await page.goto('/#/recherche')
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  // La session du fournisseur est close : il redemande les identifiants.
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.locator('#username')).toBeVisible()
})

test('ACC-09-S4 : une personne sans invitation voit le refus dédié et aucune vue protégée', async ({ page }) => {
  // paul n'est invité dans aucun espace du jeu de données.
  await seConnecterDepuis(page, '/recherche', 'paul')
  await expect(page.getByRole('heading', { name: 'Accès refusé : aucun compte ARGOS' })).toBeVisible()
  await expect(page).toHaveURL(/#\/compte-non-cree$/)
  await expect(page.getByRole('alert')).toContainText('aucune invitation en attente')
  await expect(page.getByText(/inviter/i).filter({ has: page.locator('button') })).toHaveCount(0)

  // Les vues protégées restent inaccessibles.
  await page.getByRole('link', { name: 'Recherche' }).click()
  await expect(page).toHaveURL(/#\/compte-non-cree$/)
  await expect(page.getByRole('heading', { name: 'Accès refusé : aucun compte ARGOS' })).toBeVisible()
})

test('ACC-08-S3 : renouvellement refusé par le fournisseur, état « session expirée »', async ({ page, context }) => {
  // Horloge de la page maîtrisée : le renouvellement planifié est déclenché sans attendre 9 minutes.
  await page.clock.install()
  await seConnecterDepuis(page, '/recherche', 'alice')
  await expect(page.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()

  // La session du fournisseur est close ailleurs (autre onglet) : le jeton de renouvellement est révoqué.
  const onglet = await context.newPage()
  await onglet.goto('http://localhost:8180/realms/argos/protocol/openid-connect/logout')
  await onglet.locator('#kc-logout').click()
  await expect(onglet.getByText(/you are logged out|vous êtes déconnecté/i).first()).toBeVisible()
  await onglet.close()

  const renouvellement = page.waitForResponse((r) => r.url().endsWith('/protocol/openid-connect/token'))
  await page.clock.fastForward('10:00')
  expect((await renouvellement).status()).toBe(400)

  // L'IHM présente l'état « session expirée » et propose de se reconnecter.
  await expect(page.getByText('Session expirée')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible()
  await expect(page).toHaveURL(/#\/connexion\?retour=\/recherche$/)

  // Reconnexion : retour sur la vue d'origine.
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await saisirIdentifiants(page, 'alice')
  await expect(page.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()
})
