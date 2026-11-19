// Vue d'inventaire du client desktop (formaliser-chaine-securite-poc, tâche 3.7 ; INV-01-S3 ; décision E4
// du porteur du projet, 2026-11-19) : l'application Electron réelle, connectée par le navigateur du système,
// affiche la même vue que le portail web, lue avec le jeton porteur.
// Données : support/donnees.ts (preparerInventaire), donnees/inventaire.sql.
import { INVENTAIRE } from '../support/donnees'
import { appelApiAuthentifie, lancerDesktop, ouvrirVueConnectee, verifierJournalLocal, type ApplicationDesktop } from '../support/electron'
import { expect, test } from '../support/fixtures'

let desktop: ApplicationDesktop | undefined

test.afterEach(async () => {
  await desktop?.fermer()
  desktop = undefined
})

test('3.7 (INV-01-S3) : composants ignorés faute de PURL affichés dans la vue d’inventaire du desktop',
  async ({ navigateurSysteme }, testInfo) => {
    desktop = await lancerDesktop({ dossierDonnees: testInfo.outputPath('donnees-desktop') })
    const { fenetre } = desktop
    await ouvrirVueConnectee(desktop, navigateurSysteme, 'alice', '/inventaire')
    await expect(fenetre.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Inventaire' })).toBeVisible()
    await expect(fenetre.getByRole('heading', { level: 1, name: 'Inventaire d’un projet' })).toBeVisible()
    await fenetre.getByLabel('Identifiant du projet').fill(INVENTAIRE.projet)
    await fenetre.getByRole('button', { name: 'Afficher l’inventaire' }).click()
    await expect(fenetre).toHaveURL((url) => url.protocol === 'app:' && url.hash === `#/inventaire/${INVENTAIRE.projet}`)
    await expect(fenetre.locator('[data-champ="origine"]')).toHaveText('SBOM')
    const tableau = fenetre.getByRole('table', { name: `Composants ignorés du projet ${INVENTAIRE.projet}` })
    const lignes = tableau.locator('tbody tr')
    await expect(lignes).toHaveCount(2)
    await expect(lignes.nth(0).getByRole('cell')).toHaveText(['outil-interne', 'fr.e2e', '1.0', 'sans PURL'])
    await expect(lignes.nth(1).getByRole('cell')).toHaveText([INVENTAIRE.nomHostile, 'non renseigné', 'non renseignée', 'PURL invalide'])
    await expect(tableau.locator('img')).toHaveCount(0)
    await fenetre.screenshot({ path: 'resultats/desktop-inventaire-composants-ignores.jpg', type: 'jpeg', quality: 60 })

    // Inventaire lu sur le serveur avec le jeton porteur du desktop.
    await expect.poll(() => appelApiAuthentifie(desktop!, `/api/v1/projects/${INVENTAIRE.projet}/inventory`)).toBe(true)
    await verifierJournalLocal(desktop)
  })
