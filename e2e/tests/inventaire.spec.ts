// Vue d'inventaire du portail web (formaliser-chaine-securite-poc, tâches 3.7 et 3.8 ; INV-01-S3, INV-06 ;
// décisions E4, F1 et F5 du porteur du projet, 2026-11-19) : projet choisi dans la liste des projets visibles
// depuis l'entrée « Inventaire » de la navigation, synthèse de l'inventaire de référence, composants
// inventoriés paginés et parcourus au clavier, composants écartés faute de PURL, valeur hostile de la SBOM
// affichée comme texte ; projet sans inventaire ; identifiant invalide refusé sans appel.
// Données : support/donnees.ts (preparerInventaire), donnees/inventaire.sql, donnees/veille.sql.
import { INVENTAIRE } from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

test('3.7, 3.8 (INV-01-S3, INV-06) : projet choisi, composants paginés et composants ignorés dans la vue d’inventaire du portail web',
  async ({ page }) => {
    const dialogues: string[] = []
    page.on('dialog', (d) => {
      dialogues.push(d.message())
      void d.dismiss()
    })
    // Vue protégée : connexion, puis retour sur la vue d'inventaire.
    await seConnecterDepuis(page, '/inventaire', 'alice')
    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()

    // 1. Entrée « Inventaire » de la navigation : liste des projets lue sur l'API, limitée aux espaces d'alice.
    await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'État' }).click()
    const liste = page.waitForResponse((r) => r.url().includes('/api/v1/projects?'))
    await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Inventaire' }).click()
    expect((await liste).status()).toBe(200)
    await test.info().attach('reponse-projets.json', { body: await (await liste).body(), contentType: 'application/json' })
    await expect(page).toHaveURL(/#\/inventaire$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Inventaire d’un projet' })).toBeVisible()
    const choix = page.getByRole('combobox', { name: 'Projet' })
    await expect(choix.getByRole('option', { name: INVENTAIRE.libelle })).toHaveCount(1)
    await expect(choix.getByRole('option', { name: INVENTAIRE.libelleSansSbom })).toHaveCount(1)
    await expect(choix.getByRole('option', { name: new RegExp(INVENTAIRE.projetHorsEspaces) })).toHaveCount(0)
    await expect(page.getByText(/projets? dans vos espaces\./)).toBeVisible()

    // 2. Aucun projet choisi : refus nommé ; identifiant invalide : refusé sans appel de l'API.
    const appels: string[] = []
    page.on('request', (r) => {
      if (r.url().includes('/inventory')) appels.push(r.url())
    })
    await page.getByRole('button', { name: 'Afficher l’inventaire' }).click()
    await expect(page.getByText('Choisissez un projet dans la liste.')).toBeVisible()
    await expect(choix).toHaveAttribute('aria-invalid', 'true')
    await page.getByLabel('Ou identifiant du projet').fill('../ops/sources')
    await page.getByRole('button', { name: 'Afficher par identifiant' }).click()
    await expect(page.getByText('Identifiant de projet invalide : un UUID est attendu.')).toBeVisible()
    await expect(page.getByLabel('Ou identifiant du projet')).toHaveAttribute('aria-invalid', 'true')
    expect(appels).toEqual([])

    // 3. Projet « Inventaire E2E » choisi dans la liste : synthèse et première page des composants inventoriés.
    await choix.selectOption({ label: INVENTAIRE.libelle })
    await expect(page).toHaveURL(/#\/inventaire$/) // aucun changement de contexte à la sélection
    const reponse = page.waitForResponse((r) => r.url().endsWith(`/api/v1/projects/${INVENTAIRE.projet}/inventory`))
    const premierePage = page.waitForResponse((r) => r.url().includes(`/api/v1/projects/${INVENTAIRE.projet}/inventory/components?page=0&size=20`))
    await page.getByRole('button', { name: 'Afficher l’inventaire' }).click()
    expect((await reponse).status()).toBe(200)
    expect((await premierePage).status()).toBe(200)
    await test.info().attach('reponse-inventaire.json', { body: await (await reponse).body(), contentType: 'application/json' })
    await test.info().attach('reponse-composants-page-0.json', { body: await (await premierePage).body(), contentType: 'application/json' })
    await expect(page).toHaveURL(new RegExp(`#/inventaire/${INVENTAIRE.projet}$`))
    await expect(choix).toHaveValue(INVENTAIRE.projet)
    // Projet affiché : messages de validation précédents effacés.
    await expect(page.getByText('Identifiant de projet invalide : un UUID est attendu.')).toHaveCount(0)
    await expect(page.getByText('Choisissez un projet dans la liste.')).toHaveCount(0)
    await expect(page.locator('[data-champ="origine"]')).toHaveText('SBOM')
    await expect(page.locator('[data-champ="composants"]')).toHaveText(String(INVENTAIRE.composants))
    await expect(page.getByRole('heading', { level: 2, name: 'Composants inventoriés' })).toBeVisible()
    const inventories = page.getByRole('table', { name: /^Composants inventoriés du projet / })
    await expect(inventories).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 1 sur 2`)
    await expect(inventories.getByRole('columnheader')).toHaveText(['Nom', 'Version', 'PURL', 'Dépendance'])
    await expect(inventories.locator('tbody tr')).toHaveCount(20)
    await expect(inventories.locator('tbody tr').nth(0).getByRole('cell'))
      .toHaveText(['e2e-paquet-01', '1.0.1', 'pkg:npm/e2e-paquet-01@1.0.1', 'directe'])
    await expect(inventories.locator('tbody tr').nth(19).getByRole('cell'))
      .toHaveText(['e2e-paquet-20', '1.0.20', 'pkg:npm/e2e-paquet-20@1.0.20', 'transitive'])
    await expect(page.getByText('25 composants inventoriés ; composants 1 à 20 affichés.')).toBeVisible()
    const pagination = page.getByRole('navigation', { name: 'Pagination des composants inventoriés' })
    await expect(pagination.getByText('Page 1 sur 2')).toBeVisible()
    await expect(pagination.getByRole('button', { name: 'Page précédente' })).toHaveAttribute('aria-disabled', 'true')

    // 4. Pagination au clavier : « Page suivante » au clavier, page annoncée, tableau focalisé.
    await pagination.getByRole('button', { name: 'Page suivante' }).focus()
    const secondePage = page.waitForResponse((r) => r.url().includes(`/inventory/components?page=1&size=20`))
    await page.keyboard.press('Enter')
    expect((await secondePage).status()).toBe(200)
    await expect(inventories).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 2 sur 2`)
    await expect(inventories.locator('tbody tr')).toHaveCount(5)
    await expect(inventories.locator('tbody tr').nth(0).getByRole('cell').first()).toHaveText('e2e-paquet-21')
    await expect(inventories.locator('tbody tr').nth(4).getByRole('cell'))
      .toHaveText(['org.e2e/inventaire', '1.0', 'pkg:maven/org.e2e/inventaire@1.0', 'inconnue'])
    await expect(page.getByText('25 composants inventoriés ; composants 21 à 25 affichés.')).toBeVisible()
    await expect(page.locator('output[aria-live="polite"]', { hasText: 'Page 2 sur 2 des composants inventoriés chargée.' })).toHaveCount(1)
    await expect(inventories).toBeFocused()
    await expect(pagination.getByRole('button', { name: 'Page suivante' })).toHaveAttribute('aria-disabled', 'true')
    await page.screenshot({ path: 'resultats/inventaire-composants-page-2.png', fullPage: true })
    // Retour à la première page par la touche Espace sur « Page précédente », atteinte par tabulation depuis
    // le tableau.
    await page.keyboard.press('Tab')
    await expect(pagination.getByRole('button', { name: 'Page précédente' })).toBeFocused()
    await page.keyboard.press('Space')
    await expect(inventories).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 1 sur 2`)
    await expect(inventories.locator('tbody tr')).toHaveCount(20)

    // 5. Composants écartés faute de PURL, après les composants inventoriés.
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

    // 6. Projet sans inventaire, désigné par son identifiant : état nommé, sans tableau.
    await page.getByLabel('Ou identifiant du projet').fill(INVENTAIRE.projetSansSbom)
    await page.getByRole('button', { name: 'Afficher par identifiant' }).click()
    await expect(page).toHaveURL(new RegExp(`#/inventaire/${INVENTAIRE.projetSansSbom}$`))
    await expect(choix).toHaveValue(INVENTAIRE.projetSansSbom)
    await expect(page.getByRole('heading', { level: 2, name: 'Inventaire de référence' })).toBeVisible()
    await expect(page.getByText('Aucun inventaire importé pour ce projet.')).toHaveCount(1)
    await expect(page.getByRole('heading', { name: 'Composants inventoriés' })).toHaveCount(0)
    await expect(page.getByRole('heading', { name: 'Composants ignorés' })).toHaveCount(0)
    await expect(page.getByRole('table')).toHaveCount(0)
    expect(dialogues).toEqual([])
  })
