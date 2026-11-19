// Vue d'inventaire du client desktop (formaliser-chaine-securite-poc, tâches 3.7 et 3.8 ; INV-01-S3, INV-06 ;
// décisions E4, F1, F5 et G5 du porteur du projet, 2026-11-19) : l'application Electron réelle, connectée par le
// navigateur du système, affiche la même vue que le portail web (projet choisi dans la liste des projets
// visibles, composants inventoriés paginés, composants ignorés), lue avec le jeton porteur.
// Données : support/donnees.ts (preparerInventaire), donnees/inventaire.sql, donnees/veille.sql.
import { INVENTAIRE } from '../support/donnees'
import { appelApiAuthentifie, lancerDesktop, ouvrirVueConnectee, verifierJournalLocal, type ApplicationDesktop } from '../support/electron'
import { expect, test } from '../support/fixtures'

let desktop: ApplicationDesktop | undefined

test.afterEach(async () => {
  await desktop?.fermer()
  desktop = undefined
})

test('3.7, 3.8 (INV-01-S3, INV-06) : projet choisi, composants paginés et composants ignorés dans la vue d’inventaire du desktop',
  async ({ navigateurSysteme }, testInfo) => {
    desktop = await lancerDesktop({ dossierDonnees: testInfo.outputPath('donnees-desktop') })
    const { fenetre } = desktop
    await ouvrirVueConnectee(desktop, navigateurSysteme, 'alice', '/inventaire')
    await expect(fenetre.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Inventaire' })).toBeVisible()
    await expect(fenetre.getByRole('heading', { level: 1, name: 'Inventaire d’un projet' })).toBeVisible()

    // Projet choisi dans la liste des projets visibles d'alice.
    const selecteurProjet = fenetre.getByRole('combobox', { name: 'Projet' })
    await expect(selecteurProjet.getByRole('option', { name: INVENTAIRE.libelle })).toHaveCount(1)
    await expect(selecteurProjet.getByRole('option', { name: new RegExp(INVENTAIRE.projetHorsEspaces) })).toHaveCount(0)
    await selecteurProjet.selectOption({ label: INVENTAIRE.libelle })
    await fenetre.getByRole('button', { name: 'Afficher l’inventaire' }).click()
    await expect(fenetre).toHaveURL((url) => url.protocol === 'app:' && url.hash === `#/inventaire/${INVENTAIRE.projet}`)
    await expect(fenetre.locator('[data-champ="origine"]')).toHaveText('SBOM')
    await expect(fenetre.locator('[data-champ="composants"]')).toHaveText(String(INVENTAIRE.composants))

    // Composants inventoriés : première page, puis seconde page au clavier.
    const tableauComposants = fenetre.getByRole('table', { name: /^Composants inventoriés du projet / })
    await expect(tableauComposants).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 1 sur 2`)
    await expect(tableauComposants.locator('tbody tr')).toHaveCount(20)
    await expect(tableauComposants.locator('tbody tr').nth(0).getByRole('cell'))
      .toHaveText(['e2e-paquet-01', '1.0.1', 'pkg:npm/e2e-paquet-01@1.0.1', 'directe'])
    const pagination = fenetre.getByRole('navigation', { name: 'Pagination des composants inventoriés' })
    await pagination.getByRole('button', { name: 'Page suivante' }).focus()
    await fenetre.keyboard.press('Enter')
    await expect(tableauComposants).toHaveAccessibleName(`Composants inventoriés du projet ${INVENTAIRE.libelle}, page 2 sur 2`)
    await expect(tableauComposants.locator('tbody tr')).toHaveCount(5)
    await expect(tableauComposants.locator('tbody tr').nth(4).getByRole('cell'))
      .toHaveText(['org.e2e/inventaire', '1.0', 'pkg:maven/org.e2e/inventaire@1.0', 'inconnue'])
    await expect(tableauComposants).toBeFocused()

    // Composants ignorés : même tableau que sur le web, nom hostile affiché comme texte.
    const tableauComposantsIgnores = fenetre.getByRole('table', { name: `Composants ignorés du projet ${INVENTAIRE.projet}` })
    const lignesComposantsIgnores = tableauComposantsIgnores.locator('tbody tr')
    await expect(lignesComposantsIgnores).toHaveCount(2)
    await expect(lignesComposantsIgnores.nth(0).getByRole('cell')).toHaveText(['outil-interne', 'fr.e2e', '1.0', 'sans PURL'])
    await expect(lignesComposantsIgnores.nth(1).getByRole('cell'))
      .toHaveText([INVENTAIRE.nomHostile, 'non renseigné', 'non renseignée', 'PURL invalide'])
    await expect(tableauComposantsIgnores.locator('img')).toHaveCount(0)
    await fenetre.screenshot({ path: 'resultats/desktop-inventaire-composants-ignores.jpg', type: 'jpeg', quality: 60 })

    // Liste des projets, synthèse de l'inventaire et composants lus sur le serveur avec le jeton porteur du desktop ;
    // l'inventaire complet n'est jamais demandé (décision G5).
    await expect.poll(() => appelApiAuthentifie(desktop!, '/api/v1/projects')).toBe(true)
    await expect.poll(() => appelApiAuthentifie(desktop!, `/api/v1/projects/${INVENTAIRE.projet}/inventory/summary`)).toBe(true)
    await expect.poll(() => appelApiAuthentifie(desktop!, `/api/v1/projects/${INVENTAIRE.projet}/inventory/components`)).toBe(true)
    expect((await desktop.journal()).filter((entree) => new URL(entree.adresse).pathname.endsWith('/inventory'))).toEqual([])
    await verifierJournalLocal(desktop)
  })
