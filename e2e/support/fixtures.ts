// Fixtures communes des parcours : garde réseau (aucune requête hors de la boucle locale) et
// connexion sur la page du fournisseur d'identité de test.
import { expect, test as base, type BrowserContext, type Page } from '@playwright/test'
import { HOTES_LOCAUX } from './environnement'

interface Fixtures {
  /** Requêtes refusées parce qu'elles visaient un hôte hors de la boucle locale. */
  requetesExternes: string[]
  /**
   * « Navigateur du système » des parcours desktop qui commencent sur le portail web : page d'un
   * second contexte Chromium, sans la session Keycloak du portail ouverte dans `page`, soumise à la
   * même garde réseau. Le desktop s'y connecte comme sur un poste où le portail n'a pas été ouvert.
   */
  navigateurSysteme: Page
}

/** Refuse toute requête du contexte vers un hôte hors de la boucle locale et renvoie la liste des refus. */
async function garderReseau(context: BrowserContext): Promise<string[]> {
  const refusees: string[] = []
  await context.route('**/*', (route) => {
    const url = new URL(route.request().url())
    if (url.protocol === 'data:' || url.protocol === 'blob:' || HOTES_LOCAUX.has(url.hostname)) {
      return route.continue()
    }
    refusees.push(`${url.protocol}//${url.host}`)
    return route.abort('blockedbyclient')
  })
  return refusees
}

export const test = base.extend<Fixtures>({
  requetesExternes: [
    async ({ context }, use) => {
      const refusees = await garderReseau(context)
      await use(refusees)
      // Aucun parcours ne doit tenter de joindre Internet.
      expect(refusees, 'requêtes hors de la boucle locale').toEqual([])
    },
    { auto: true },
  ],
  navigateurSysteme: async ({ browser, locale }, use) => {
    const contexte = await browser.newContext({ locale })
    const refusees = await garderReseau(contexte)
    await use(await contexte.newPage())
    await contexte.close()
    expect(refusees, 'requêtes du navigateur du système hors de la boucle locale').toEqual([])
  },
})

export { expect }

/** Saisit les identifiants d'un compte du royaume de test sur la page de connexion Keycloak. */
export async function saisirIdentifiants(page: Page, utilisateur: string): Promise<void> {
  await expect(page).toHaveURL(/localhost:8180\/realms\/argos\/protocol\/openid-connect\/auth/)
  await page.locator('#username').fill(utilisateur)
  await page.locator('#password').fill(utilisateur)
  await page.locator('#kc-login').click()
}

/** Ouvre une vue protégée, passe par l'écran de connexion et le fournisseur, revient sur la vue. */
export async function seConnecterDepuis(page: Page, vue: string, utilisateur: string): Promise<void> {
  await page.goto(`/#${vue}`)
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await saisirIdentifiants(page, utilisateur)
}
