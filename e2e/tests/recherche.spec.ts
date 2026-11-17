// Parcours de recherche du portail web (changement rechercher-et-filtrer, tâche 4.2, partie web) :
// recherche « Oracle » filtrée sur le projet « Paie » et 30 jours, enregistrée en vue, rappelée dans
// la même session puis dans une nouvelle session. Données : support/donnees.ts, donnees/recherche.sql.
import { SIGNAL_ATTENDU, SIGNAUX_EXCLUS } from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'
import type { Page } from '@playwright/test'

const NOM_VUE = `Oracle Paie 30 jours ${Date.now()}`

function resultats(page: Page) {
  return page.locator('ol.resultats > li h3')
}

async function verifierResultatUnique(page: Page): Promise<void> {
  await expect(resultats(page)).toHaveText([SIGNAL_ATTENDU])
  await expect(page.locator('section p.muted', { hasText: /^1 résultat\.$/ })).toBeVisible()
  await expect(page.locator('ol.resultats > li')).toContainText('Projets : Paie')
  for (const exclu of SIGNAUX_EXCLUS) await expect(page.getByText(exclu, { exact: true })).toHaveCount(0)
}

test('RCH-01, RCH-02-S1, RCH-04-S1 : recherche « Oracle », projet Paie, 30 jours, vue enregistrée puis rappelée', async ({ page }) => {
  await seConnecterDepuis(page, '/recherche', 'alice')
  await expect(page.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()

  // 1. Mots et période : les signaux « Oracle » des 30 derniers jours, tous projets.
  await page.getByLabel('Mots recherchés').fill('Oracle')
  await page.getByLabel('Période').selectOption({ label: '30 derniers jours' })
  const reponse = page.waitForResponse((r) => r.url().includes('/api/v1/recherche?'))
  await page.getByRole('button', { name: 'Rechercher' }).click()
  // Réponse de l'API jointe au rapport : elle explique un écart d'affichage éventuel.
  await test.info().attach('reponse-recherche-oracle-30j.json', { body: await (await reponse).body(), contentType: 'application/json' })
  // L'ordre dépend de la pertinence : seul l'ensemble est vérifié.
  await expect(resultats(page)).toHaveCount(2)
  await expect(resultats(page).filter({ hasText: SIGNAL_ATTENDU })).toHaveCount(1)
  await expect(resultats(page).filter({ hasText: 'Oracle Java : faille' })).toHaveCount(1)
  await expect(page.getByText('Oracle Database : faille ancienne', { exact: true })).toHaveCount(0)

  // 2. Filtre projet : les projets cités par les résultats sont proposés.
  await page.getByRole('group', { name: 'Projets' }).getByLabel('Paie').check()
  await page.getByRole('button', { name: 'Rechercher' }).click()
  await verifierResultatUnique(page)
  await page.screenshot({ path: 'resultats/recherche-oracle-paie-30j.png', fullPage: true })

  // 3. Enregistrement de la vue.
  await page.getByLabel('Nom de la vue').fill(NOM_VUE)
  await page.getByRole('button', { name: 'Enregistrer la recherche' }).click()
  const lienVue = page.getByRole('link', { name: NOM_VUE })
  await expect(lienVue).toBeVisible()
  // L'écran passe sur l'adresse de la vue enregistrée.
  await expect(page).toHaveURL(/#\/recherche\/vues\/[0-9a-f-]{36}$/)
  const adresseVue = new URL(page.url()).hash

  // 4. Rappel dans la même session : retour à une recherche vierge, puis clic sur la vue.
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Recherche' }).click()
  await expect(page).toHaveURL(/#\/recherche$/)
  await page.getByRole('button', { name: 'Effacer les filtres' }).click()
  await expect(page.getByLabel('Mots recherchés')).toHaveValue('')
  await lienVue.click()
  await expect(page).toHaveURL((url) => url.hash === adresseVue)
  await expect(page).toHaveURL(/#\/recherche\/vues\/[0-9a-f-]{36}$/)
  await expect(page.getByText(`Vue rappelée : ${NOM_VUE}`)).toBeVisible()
  await expect(page.getByLabel('Mots recherchés')).toHaveValue('Oracle')
  await expect(page.getByLabel('Période')).toHaveValue('30')
  await expect(page.getByRole('group', { name: 'Projets' }).getByLabel('Paie')).toBeChecked()
  await verifierResultatUnique(page)

  // 5. Rappel dans une nouvelle session (page rechargée : jetons oubliés, vue lue sur le serveur).
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  await page.getByRole('button', { name: 'Se connecter' }).click()
  // Session du fournisseur encore ouverte : retour direct, sans saisie.
  await expect(page).toHaveURL((url) => url.hash === adresseVue)
  await expect(page.getByText(`Vue rappelée : ${NOM_VUE}`)).toBeVisible()
  await verifierResultatUnique(page)
})
