// Vue d'inventaire du portail web (formaliser-chaine-securite-poc, tâches 3.7 et 3.8 ; INV-01-S3, INV-06 ;
// décisions E4, F1, F5 et G5 du porteur du projet, 2026-11-19) : projet choisi dans la liste des projets visibles
// depuis l'entrée « Inventaire » de la navigation, synthèse de l'inventaire de référence lue sans la liste des
// composants, composants inventoriés paginés et parcourus au clavier, composants écartés faute de PURL, valeur
// hostile de la SBOM affichée comme texte ; projet sans inventaire ; identifiant invalide refusé sans appel.
// Données : support/donnees.ts (preparerInventaire), donnees/inventaire.sql, donnees/veille.sql.
import type { Locator, Page } from '@playwright/test'
import { INVENTAIRE } from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

/** Adresses des appels de l'inventaire émis par la page (synthèse, composants et inventaire complet). */
function suivreAppelsInventaire(page: Page): string[] {
  const adressesAppels: string[] = []
  page.on('request', (requete) => {
    if (requete.url().includes('/inventory')) adressesAppels.push(requete.url())
  })
  return adressesAppels
}

/** Liste « Projet » ouverte depuis l'entrée « Inventaire » de la navigation, limitée aux espaces d'alice. */
async function ouvrirDepuisLaNavigation(page: Page): Promise<Locator> {
  const navigation = page.getByRole('navigation', { name: 'Navigation principale' })
  await navigation.getByRole('link', { name: 'État' }).click()
  const reponseProjets = page.waitForResponse((reponse) => reponse.url().includes('/api/v1/projects?'))
  await navigation.getByRole('link', { name: 'Inventaire' }).click()
  expect((await reponseProjets).status()).toBe(200)
  await test.info().attach('reponse-projets.json', { body: await (await reponseProjets).body(), contentType: 'application/json' })
  await expect(page).toHaveURL(/#\/inventaire$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Inventaire d’un projet' })).toBeVisible()
  const selecteurProjet = page.getByRole('combobox', { name: 'Projet' })
  await expect(selecteurProjet.getByRole('option', { name: INVENTAIRE.libelle })).toHaveCount(1)
  await expect(selecteurProjet.getByRole('option', { name: INVENTAIRE.libelleSansSbom })).toHaveCount(1)
  await expect(selecteurProjet.getByRole('option', { name: new RegExp(INVENTAIRE.projetHorsEspaces) })).toHaveCount(0)
  await expect(page.getByText(/projets? dans vos espaces\./)).toBeVisible()
  return selecteurProjet
}

/** Aucun projet choisi : refus nommé ; identifiant invalide : refusé sans appel de l'API. */
async function refuserSansProjetNiIdentifiantValide(page: Page, selecteurProjet: Locator, appelsInventaire: string[]): Promise<void> {
  await page.getByRole('button', { name: 'Afficher l’inventaire' }).click()
  await expect(page.getByText('Choisissez un projet dans la liste.')).toBeVisible()
  await expect(selecteurProjet).toHaveAttribute('aria-invalid', 'true')
  await page.getByLabel('Ou identifiant du projet').fill('../ops/sources')
  await page.getByRole('button', { name: 'Afficher par identifiant' }).click()
  await expect(page.getByText('Identifiant de projet invalide : un UUID est attendu.')).toBeVisible()
  await expect(page.getByLabel('Ou identifiant du projet')).toHaveAttribute('aria-invalid', 'true')
  expect(appelsInventaire).toEqual([])
}

/** Projet « Inventaire E2E » choisi : synthèse sans composants, puis première page des composants inventoriés. */
async function afficherProjetChoisi(page: Page, selecteurProjet: Locator): Promise<Locator> {
  await selecteurProjet.selectOption({ label: INVENTAIRE.libelle })
  await expect(page).toHaveURL(/#\/inventaire$/) // aucun changement de contexte à la sélection
  const reponseSynthese = page.waitForResponse(
    (reponse) => reponse.url().endsWith(`/api/v1/projects/${INVENTAIRE.projet}/inventory/summary`),
  )
  const reponsePremierePage = page.waitForResponse(
    (reponse) => reponse.url().includes(`/api/v1/projects/${INVENTAIRE.projet}/inventory/components?page=0&size=20`),
  )
  await page.getByRole('button', { name: 'Afficher l’inventaire' }).click()
  expect((await reponseSynthese).status()).toBe(200)
  expect((await reponsePremierePage).status()).toBe(200)
  const corpsSynthese = await (await reponseSynthese).json()
  expect(corpsSynthese.summary.componentCount).toBe(INVENTAIRE.composants)
  expect(corpsSynthese.summary).not.toHaveProperty('components')
  await test.info().attach('reponse-synthese-inventaire.json',
    { body: await (await reponseSynthese).body(), contentType: 'application/json' })
  await test.info().attach('reponse-composants-page-0.json',
    { body: await (await reponsePremierePage).body(), contentType: 'application/json' })
  await expect(page).toHaveURL(new RegExp(`#/inventaire/${INVENTAIRE.projet}$`))
  await expect(selecteurProjet).toHaveValue(INVENTAIRE.projet)
  // Projet affiché : messages de validation précédents effacés.
  await expect(page.getByText('Identifiant de projet invalide : un UUID est attendu.')).toHaveCount(0)
  await expect(page.getByText('Choisissez un projet dans la liste.')).toHaveCount(0)
  await expect(page.locator('[data-champ="origine"]')).toHaveText('SBOM')
  await expect(page.locator('[data-champ="composants"]')).toHaveText(String(INVENTAIRE.composants))
  await expect(page.getByRole('heading', { level: 2, name: 'Composants inventoriés' })).toBeVisible()
  const tableauComposants = page.getByRole('table', { name: /^Composants inventoriés du projet / })
  await expect(tableauComposants).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 1 sur 2`)
  await expect(tableauComposants.getByRole('columnheader')).toHaveText(['Nom', 'Version', 'PURL', 'Dépendance'])
  await expect(tableauComposants.locator('tbody tr')).toHaveCount(20)
  await expect(tableauComposants.locator('tbody tr').nth(0).getByRole('cell'))
    .toHaveText(['e2e-paquet-01', '1.0.1', 'pkg:npm/e2e-paquet-01@1.0.1', 'directe'])
  await expect(tableauComposants.locator('tbody tr').nth(19).getByRole('cell'))
    .toHaveText(['e2e-paquet-20', '1.0.20', 'pkg:npm/e2e-paquet-20@1.0.20', 'transitive'])
  await expect(page.getByText('25 composants inventoriés ; composants 1 à 20 affichés.')).toBeVisible()
  const pagination = page.getByRole('navigation', { name: 'Pagination des composants inventoriés' })
  await expect(pagination.getByText('Page 1 sur 2')).toBeVisible()
  await expect(pagination.getByRole('button', { name: 'Page précédente' })).toHaveAttribute('aria-disabled', 'true')
  return tableauComposants
}

/** « Page suivante » au clavier (Entrée) : page annoncée, tableau focalisé ; retour par tabulation et Espace. */
async function paginerAuClavier(page: Page, tableauComposants: Locator): Promise<void> {
  const pagination = page.getByRole('navigation', { name: 'Pagination des composants inventoriés' })
  await pagination.getByRole('button', { name: 'Page suivante' }).focus()
  const reponseSecondePage = page.waitForResponse((reponse) => reponse.url().includes(`/inventory/components?page=1&size=20`))
  await page.keyboard.press('Enter')
  expect((await reponseSecondePage).status()).toBe(200)
  await expect(tableauComposants).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 2 sur 2`)
  await expect(tableauComposants.locator('tbody tr')).toHaveCount(5)
  await expect(tableauComposants.locator('tbody tr').nth(0).getByRole('cell').first()).toHaveText('e2e-paquet-21')
  await expect(tableauComposants.locator('tbody tr').nth(4).getByRole('cell'))
    .toHaveText(['org.e2e/inventaire', '1.0', 'pkg:maven/org.e2e/inventaire@1.0', 'inconnue'])
  await expect(page.getByText('25 composants inventoriés ; composants 21 à 25 affichés.')).toBeVisible()
  await expect(page.locator('output[aria-live="polite"]', { hasText: 'Page 2 sur 2 des composants inventoriés chargée.' })).toHaveCount(1)
  await expect(tableauComposants).toBeFocused()
  await expect(pagination.getByRole('button', { name: 'Page suivante' })).toHaveAttribute('aria-disabled', 'true')
  await page.screenshot({ path: 'resultats/inventaire-composants-page-2.png', fullPage: true })
  // Retour à la première page par la touche Espace sur « Page précédente », atteinte par tabulation depuis le tableau.
  await page.keyboard.press('Tab')
  await expect(pagination.getByRole('button', { name: 'Page précédente' })).toBeFocused()
  await page.keyboard.press('Space')
  await expect(tableauComposants).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 1 sur 2`)
  await expect(tableauComposants.locator('tbody tr')).toHaveCount(20)
}

/** Composants écartés faute de PURL, après les composants inventoriés ; valeur hostile affichée comme texte. */
async function verifierComposantsIgnores(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { level: 2, name: 'Composants ignorés' })).toBeVisible()
  await expect(page.getByText(
    "3 composants écartés de l'inventaire au dernier import, faute de PURL exploitable ; les 2 premiers sont listés.",
  )).toBeVisible()
  const tableauComposantsIgnores = page.getByRole('table', { name: `Composants ignorés du projet ${INVENTAIRE.projet}` })
  await expect(tableauComposantsIgnores.getByRole('columnheader')).toHaveText(['Nom', 'Groupe', 'Version', 'Motif'])
  const lignesComposantsIgnores = tableauComposantsIgnores.locator('tbody tr')
  await expect(lignesComposantsIgnores).toHaveCount(2)
  await expect(lignesComposantsIgnores.nth(0).getByRole('cell')).toHaveText(['outil-interne', 'fr.e2e', '1.0', 'sans PURL'])
  // Valeur hostile de la SBOM : affichée comme texte, aucun élément créé, aucun script exécuté.
  await expect(lignesComposantsIgnores.nth(1).getByRole('cell'))
    .toHaveText([INVENTAIRE.nomHostile, 'non renseigné', 'non renseignée', 'PURL invalide'])
  await expect(tableauComposantsIgnores.locator('img')).toHaveCount(0)
  await page.screenshot({ path: 'resultats/inventaire-composants-ignores.png', fullPage: true })
}

/** Projet sans inventaire, désigné par son identifiant : état nommé, sans tableau. */
async function afficherProjetSansInventaire(page: Page, selecteurProjet: Locator): Promise<void> {
  await page.getByLabel('Ou identifiant du projet').fill(INVENTAIRE.projetSansSbom)
  await page.getByRole('button', { name: 'Afficher par identifiant' }).click()
  await expect(page).toHaveURL(new RegExp(`#/inventaire/${INVENTAIRE.projetSansSbom}$`))
  await expect(selecteurProjet).toHaveValue(INVENTAIRE.projetSansSbom)
  await expect(page.getByRole('heading', { level: 2, name: 'Inventaire de référence' })).toBeVisible()
  await expect(page.getByText('Aucun inventaire importé pour ce projet.')).toHaveCount(1)
  await expect(page.getByRole('heading', { name: 'Composants inventoriés' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Composants ignorés' })).toHaveCount(0)
  await expect(page.getByRole('table')).toHaveCount(0)
}

test('3.7, 3.8 (INV-01-S3, INV-06) : projet choisi, composants paginés et composants ignorés dans la vue d’inventaire du portail web',
  async ({ page }) => {
    const messagesDialogues: string[] = []
    page.on('dialog', (dialogue) => {
      messagesDialogues.push(dialogue.message())
      void dialogue.dismiss()
    })
    // Vue protégée : connexion, puis retour sur la vue d'inventaire.
    await seConnecterDepuis(page, '/inventaire', 'alice')
    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()

    const selecteurProjet = await test.step('1. liste des projets ouverte depuis la navigation', () => ouvrirDepuisLaNavigation(page))
    const appelsInventaire = suivreAppelsInventaire(page)
    await test.step('2. aucun projet choisi, identifiant invalide refusé sans appel',
      () => refuserSansProjetNiIdentifiantValide(page, selecteurProjet, appelsInventaire))
    const tableauComposants = await test.step('3. projet choisi : synthèse et première page des composants',
      () => afficherProjetChoisi(page, selecteurProjet))
    await test.step('4. pagination des composants au clavier', () => paginerAuClavier(page, tableauComposants))
    await test.step('5. composants ignorés faute de PURL', () => verifierComposantsIgnores(page))
    await test.step('6. projet sans inventaire désigné par son identifiant', () => afficherProjetSansInventaire(page, selecteurProjet))

    // Décision G5 : la vue lit la synthèse et les pages de composants, jamais l'inventaire complet.
    expect(appelsInventaire.filter((adresse) => new URL(adresse).pathname.endsWith('/inventory'))).toEqual([])
    const syntheseSansSbom = `/api/v1/projects/${INVENTAIRE.projetSansSbom}/inventory/summary`
    expect(appelsInventaire.some((adresse) => adresse.endsWith(syntheseSansSbom))).toBe(true)
    expect(messagesDialogues).toEqual([])
  })
