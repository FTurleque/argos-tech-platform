// Préférence de mise en page sur le portail web (personnaliser-profils-et-affichage, tâche 2.5,
// partie web ; AFF-01-S1, AFF-03-S1) : choix de « gazette », enregistré sur le serveur et retrouvé
// dans une nouvelle session. La partie desktop est dans desktop/affichage.spec.ts.
import { api } from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

test('AFF-01-S1, AFF-03-S1 (web) : la mise en page « gazette » choisie est retrouvée dans une nouvelle session', async ({ page }) => {
  // État initial connu : mise en page « classique » (AFF-04-S1).
  await api('alice', '/api/v1/preferences/mise-en-page', { method: 'PUT', body: JSON.stringify({ miseEnPage: 'classique' }) })

  await seConnecterDepuis(page, '/affichage', 'alice')
  await expect(page.getByRole('heading', { name: 'Mise en page', exact: true })).toBeVisible()
  const choix = page.getByRole('radiogroup', { name: 'Mises en page disponibles' })
  const gazette = choix.getByRole('radio', { name: /gazette/i })
  await expect(choix.getByRole('radio', { name: /classique/i })).toHaveAttribute('aria-checked', 'true')

  await gazette.click()
  await expect(gazette).toHaveAttribute('aria-checked', 'true')

  // Nouvelle session : la préférence est relue sur le serveur.
  await page.reload()
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page).toHaveURL(/#\/affichage$/)
  await expect(choix.getByRole('radio', { name: /gazette/i })).toHaveAttribute('aria-checked', 'true')
  await page.screenshot({ path: 'resultats/mise-en-page-gazette-web.png', fullPage: true })

  const preferences = await api<{ miseEnPage: string }>('alice', '/api/v1/preferences')
  expect(preferences.miseEnPage).toBe('gazette')
})
