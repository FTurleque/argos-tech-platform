// Vue de recherche enregistrée sur le portail web, rappelée depuis le client desktop (changement
// rechercher-et-filtrer, tâche 4.2 ; RCH-01, RCH-02-S1, RCH-04-S1). Le même compte (alice) enregistre
// sur le web la recherche « Oracle » filtrée sur le projet « Paie » et 30 jours, puis l'application
// Electron réelle, connectée par le navigateur du système, la rappelle depuis la liste des vues.
// Données : support/donnees.ts (preparerRecherche), donnees/recherche.sql.
import { api, SIGNAL_ATTENDU } from '../support/donnees'
import { resultats, verifierCriteresRappeles, verifierResultatUnique } from '../support/ecrans'
import { appelApiAuthentifie, lancerDesktop, ouvrirVueConnectee, verifierJournalLocal, type ApplicationDesktop } from '../support/electron'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

const NOM_VUE = `Oracle Paie 30 jours desktop ${Date.now()}`

let desktop: ApplicationDesktop | undefined

test.afterEach(async () => {
  await desktop?.fermer()
  desktop = undefined
})

test('4.2 (RCH-02-S1, RCH-04-S1) : recherche enregistrée en vue sur le web, rappelée depuis le desktop',
  async ({ page, navigateurSysteme }, testInfo) => {
    // 1. Portail web (Chromium) : recherche « Oracle », 30 jours, projet « Paie », enregistrée en vue.
    await seConnecterDepuis(page, '/recherche', 'alice')
    await expect(page.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()
    await page.getByLabel('Mots recherchés').fill('Oracle')
    await page.getByLabel('Période').selectOption({ label: '30 derniers jours' })
    await page.getByRole('button', { name: 'Rechercher' }).click()
    await expect(resultats(page)).toHaveCount(2)
    await page.getByRole('group', { name: 'Projets' }).getByLabel('Paie').check()
    await page.getByRole('button', { name: 'Rechercher' }).click()
    await verifierResultatUnique(page)
    await page.getByLabel('Nom de la vue').fill(NOM_VUE)
    await page.getByRole('button', { name: 'Enregistrer la recherche' }).click()
    await expect(page.getByRole('link', { name: NOM_VUE })).toBeVisible()
    await expect(page).toHaveURL(/#\/recherche\/vues\/[0-9a-f-]{36}$/)
    const idVue = /#\/recherche\/vues\/([0-9a-f-]{36})$/.exec(page.url())![1]
    // La vue est enregistrée sur le serveur, au nom d'alice.
    const vues = await api<{ id: string; nom: string }[]>('alice', '/api/v1/vues')
    expect(vues.filter((vue) => vue.nom === NOM_VUE).map((vue) => vue.id)).toEqual([idVue])

    // 2. Client desktop : application Electron réelle, connectée avec le même compte.
    desktop = await lancerDesktop({ dossierDonnees: testInfo.outputPath('donnees-desktop') })
    const { fenetre } = desktop
    await ouvrirVueConnectee(desktop, navigateurSysteme, 'alice', '/recherche')
    await expect(fenetre.getByRole('heading', { name: 'Recherche', exact: true })).toBeVisible()
    // Écran vierge : aucune saisie ne passe du portail au desktop, seule la vue enregistrée est partagée.
    await expect(fenetre.getByLabel('Mots recherchés')).toHaveValue('')

    // 3. Rappel de la vue depuis la liste des vues du desktop.
    const lienVue = fenetre.getByRole('link', { name: NOM_VUE })
    await expect(lienVue).toBeVisible()
    await lienVue.click()
    await expect(fenetre).toHaveURL((url) => url.protocol === 'app:' && url.hash === `#/recherche/vues/${idVue}`)
    await verifierCriteresRappeles(fenetre, NOM_VUE)
    await verifierResultatUnique(fenetre)
    await expect(resultats(fenetre)).toHaveText([SIGNAL_ATTENDU])
    await fenetre.screenshot({ path: 'resultats/desktop-recherche-vue-rappelee.jpg', type: 'jpeg', quality: 60 })

    // La vue et ses résultats sont lus sur le serveur avec le jeton porteur du desktop.
    await expect.poll(() => appelApiAuthentifie(desktop!, '/api/v1/vues')).toBe(true)
    await expect.poll(() => appelApiAuthentifie(desktop!, `/api/v1/vues/${idVue}`)).toBe(true)
    await expect.poll(() => appelApiAuthentifie(desktop!, `/api/v1/vues/${idVue}/resultats`)).toBe(true)
    await verifierJournalLocal(desktop)
  })
