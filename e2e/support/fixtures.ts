// Fixtures communes des parcours : garde réseau (aucune requête hors de la boucle locale) et
// connexion sur la page du fournisseur d'identité de test.
import { expect, test as base, type Page } from '@playwright/test'
import { HOTES_LOCAUX } from './environnement'

interface Fixtures {
  /** Requêtes refusées parce qu'elles visaient un hôte hors de la boucle locale. */
  requetesExternes: string[]
}

export const test = base.extend<Fixtures>({
  requetesExternes: [
    async ({ context }, use) => {
      const refusees: string[] = []
      await context.route('**/*', (route) => {
        const url = new URL(route.request().url())
        if (url.protocol === 'data:' || url.protocol === 'blob:' || HOTES_LOCAUX.has(url.hostname)) {
          return route.continue()
        }
        refusees.push(`${url.protocol}//${url.host}`)
        return route.abort('blockedbyclient')
      })
      await use(refusees)
      // Aucun parcours ne doit tenter de joindre Internet.
      expect(refusees, 'requêtes hors de la boucle locale').toEqual([])
    },
    { auto: true },
  ],
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
