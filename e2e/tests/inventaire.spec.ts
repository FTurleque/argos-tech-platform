// Vue d'inventaire du portail web (formaliser-chaine-securite-poc, tâche 3.7 ; INV-01-S3 ; décision E4 du
// porteur du projet, 2026-11-19) : projet choisi par son identifiant depuis l'entrée « Inventaire » de la
// navigation, synthèse de l'inventaire de référence et composants écartés faute de PURL, valeur hostile de
// la SBOM affichée comme texte ; projet sans inventaire ; identifiant invalide refusé sans appel.
// Données : support/donnees.ts (preparerInventaire), donnees/inventaire.sql.
import { INVENTAIRE } from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

test('3.7 (INV-01-S3) : composants ignorés faute de PURL affichés dans la vue d’inventaire du portail web', async ({ page }) => {
  const dialogues: string[] = []
  page.on('dialog', (d) => {
    dialogues.push(d.message())
    void d.dismiss()
  })
  await seConnecterDepuis(page, '/', 'alice')
  await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()

  // 1. Entrée « Inventaire » de la navigation, identifiant invalide : refusé sans appel de l'API.
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Inventaire' }).click()
  await expect(page).toHaveURL(/#\/inventaire$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Inventaire d’un projet' })).toBeVisible()
  const appels: string[] = []
  page.on('request', (r) => {
    if (r.url().includes('/inventory')) appels.push(r.url())
  })
  await page.getByLabel('Identifiant du projet').fill('../ops/sources')
  await page.getByRole('button', { name: 'Afficher l’inventaire' }).click()
  await expect(page.getByText('Identifiant de projet invalide : un UUID est attendu.')).toBeVisible()
  await expect(page.getByLabel('Identifiant du projet')).toHaveAttribute('aria-invalid', 'true')
  expect(appels).toEqual([])

  // 2. Projet « Inventaire E2E » : synthèse et composants écartés.
  await page.getByLabel('Identifiant du projet').fill(INVENTAIRE.projet)
  const reponse = page.waitForResponse((r) => r.url().endsWith(`/api/v1/projects/${INVENTAIRE.projet}/inventory`))
  await page.getByRole('button', { name: 'Afficher l’inventaire' }).click()
  expect((await reponse).status()).toBe(200)
  await test.info().attach('reponse-inventaire.json', { body: await (await reponse).body(), contentType: 'application/json' })
  await expect(page).toHaveURL(new RegExp(`#/inventaire/${INVENTAIRE.projet}$`))
  await expect(page.locator('[data-champ="origine"]')).toHaveText('SBOM')
  await expect(page.locator('[data-champ="composants"]')).toHaveText('1')
  await expect(page.getByRole('heading', { level: 2, name: 'Composants ignorés' })).toBeVisible()
  await expect(page.getByText(
    "3 composants écartés de l'inventaire au dernier import, faute de PURL exploitable ; les 2 premiers sont listés.",
  )).toBeVisible()
  const tableau = page.getByRole('table', { name: `Composants ignorés du projet ${INVENTAIRE.projet}` })
  await expect(tableau.getByRole('columnheader')).toHaveText(['Nom', 'Groupe', 'Version', 'Motif'])
  const lignes = tableau.locator('tbody tr')
  await expect(lignes).toHaveCount(2)
  await expect(lignes.nth(0).getByRole('cell')).toHaveText(['outil-interne', 'fr.e2e', '1.0', 'sans PURL'])
  // Valeur hostile de la SBOM : affichée comme texte, aucun élément créé, aucun script exécuté.
  await expect(lignes.nth(1).getByRole('cell')).toHaveText([INVENTAIRE.nomHostile, 'non renseigné', 'non renseignée', 'PURL invalide'])
  await expect(tableau.locator('img')).toHaveCount(0)
  await page.screenshot({ path: 'resultats/inventaire-composants-ignores.png', fullPage: true })

  // 3. Projet sans inventaire : état nommé, sans tableau.
  await page.goto(`/#/inventaire/${INVENTAIRE.projetSansSbom}`)
  await expect(page.getByRole('heading', { level: 2, name: 'Inventaire de référence' })).toBeVisible()
  await expect(page.getByText('Aucun inventaire importé pour ce projet.')).toHaveCount(1)
  await expect(page.getByRole('heading', { name: 'Composants ignorés' })).toHaveCount(0)
  await expect(page.getByRole('table')).toHaveCount(0)
  expect(dialogues).toEqual([])
})
