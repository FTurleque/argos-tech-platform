// Mise en page choisie sur le portail web, appliquée par le client desktop (changement
// personnaliser-profils-et-affichage, tâches 2.5 et 5.2 ; AFF-03-S1). Chaque parcours agit d'abord
// sur le portail web (Chromium), puis ouvre l'application Electron réelle, connectée avec le même
// compte par le navigateur du système, et y vérifie la préférence relue sur le serveur.
// Données de « Ma veille » : support/donnees.ts (preparerVeille), donnees/veille.sql.
import { api, fixerPreferences, VEILLE, veilleDe } from '../support/donnees'
import {
  article,
  capture,
  choisirMiseEnPage,
  DEUX_PROFILS,
  JAMAIS_POUR_JULES,
  titres,
  UNION_JULES,
} from '../support/ecrans'
import { appelApiAuthentifie, lancerDesktop, ouvrirVueConnectee, verifierJournalLocal, type ApplicationDesktop } from '../support/electron'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

let desktop: ApplicationDesktop | undefined

test.afterEach(async () => {
  await desktop?.fermer()
  desktop = undefined
})

test('2.5 (AFF-03-S1) : « fantaisie » choisie sur le web, le client desktop s’affiche en « fantaisie »',
  async ({ page, navigateurSysteme }, testInfo) => {
    // État initial connu : mise en page « classique » (AFF-04-S1).
    await api('alice', '/api/v1/preferences/mise-en-page', { method: 'PUT', body: JSON.stringify({ miseEnPage: 'classique' }) })

    // 1. Portail web : alice choisit « fantaisie ».
    await seConnecterDepuis(page, '/affichage', 'alice')
    const choixWeb = page.getByRole('radiogroup', { name: 'Mises en page disponibles' })
    await expect(choixWeb.getByRole('radio', { name: /classique/i })).toHaveAttribute('aria-checked', 'true')
    await choisirMiseEnPage(page, 'alice', 'fantaisie')
    await expect(page.locator('[data-mise-en-page="fantaisie"]')).toBeVisible()

    // 2. Client desktop, même compte : la préférence est relue sur le serveur et appliquée.
    desktop = await lancerDesktop({ dossierDonnees: testInfo.outputPath('donnees-desktop') })
    const { fenetre } = desktop
    await ouvrirVueConnectee(desktop, navigateurSysteme, 'alice', '/affichage')
    await expect(fenetre.getByText('Application desktop', { exact: true })).toBeVisible()
    const choix = fenetre.getByRole('radiogroup', { name: 'Mises en page disponibles' })
    await expect(choix.getByRole('radio', { name: /fantaisie/i })).toHaveAttribute('aria-checked', 'true')
    for (const autre of ['classique', 'journal', 'gazette']) {
      await expect(choix.getByRole('radio', { name: new RegExp(autre, 'i') })).toHaveAttribute('aria-checked', 'false')
    }
    // L'aperçu du desktop s'affiche dans la mise en page « fantaisie », et dans elle seule.
    await expect(fenetre.locator('[data-mise-en-page]')).toHaveCount(1)
    await expect(fenetre.locator('[data-mise-en-page="fantaisie"]')).toBeVisible()
    await capture(fenetre, 'desktop-mise-en-page-fantaisie')

    // « Ma veille » du desktop suit la même préférence.
    const veille = await veilleDe('alice')
    await fenetre.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Ma veille' }).click()
    await expect(fenetre.getByRole('heading', { name: 'Ma veille', level: 1 })).toBeVisible()
    // Précondition du jeu de données : la veille d'alice n'est pas vide, sinon la mise en page n'aurait rien à afficher.
    expect(veille.items.length, 'veille d’alice non vide').toBeGreaterThan(0)
    await expect(fenetre.locator('[data-mise-en-page]')).toHaveCount(1)
    await expect(fenetre.locator('[data-mise-en-page="fantaisie"]')).toBeVisible()

    // La préférence est lue par le desktop sur le serveur, avec son jeton porteur.
    await expect.poll(() => appelApiAuthentifie(desktop!, '/api/v1/preferences')).toBe(true)
    expect((await api<{ miseEnPage: string }>('alice', '/api/v1/preferences')).miseEnPage).toBe('fantaisie')
    await verifierJournalLocal(desktop)
  })

test('5.2 : developpeur + expert-metier, « gazette » choisie sur le web, retrouvée sur le desktop avec l’union de ses veilles',
  async ({ page, navigateurSysteme }, testInfo) => {
    await fixerPreferences('jules', DEUX_PROFILS, 'classique')

    // 1. Portail web : jules choisit « gazette ».
    await seConnecterDepuis(page, '/affichage', 'jules')
    await expect(page.getByRole('heading', { name: 'Mise en page', exact: true })).toBeVisible()
    await choisirMiseEnPage(page, 'jules', 'gazette')

    // 2. Client desktop, même compte : « Ma veille » s'affiche en gazette.
    desktop = await lancerDesktop({ dossierDonnees: testInfo.outputPath('donnees-desktop') })
    const { fenetre } = desktop
    await ouvrirVueConnectee(desktop, navigateurSysteme, 'jules', '/veille')
    await expect(fenetre.getByText('Application desktop', { exact: true })).toBeVisible()
    await expect(fenetre.getByRole('heading', { name: 'Ma veille', level: 1 })).toBeVisible()
    await expect(fenetre.locator('[data-mise-en-page]')).toHaveCount(1)
    await expect(fenetre.locator('[data-mise-en-page="gazette"]')).toBeVisible()
    await fenetre.evaluate(() => document.fonts.ready)

    // Union des deux profils et de l'abonnement personnel, sans doublon, dans l'ordre du serveur.
    const reponse = await veilleDe('jules')
    expect(reponse.profilsActifs).toEqual(DEUX_PROFILS)
    await expect(fenetre.getByText('Profils actifs : Développeur, Expert métier')).toBeVisible()
    const affiches = await titres(fenetre)
    expect(affiches).toEqual(reponse.items.map((element) => element.titre))
    expect([...affiches].sort()).toEqual([...UNION_JULES].sort())
    expect(new Set(affiches).size, 'aucun doublon').toBe(affiches.length)
    // Rubriques des deux profils ; l'avis du seul abonnement n'en porte pas ; mention IA conservée.
    await expect(article(fenetre, VEILLE.avisCommun).locator('[data-champ="rubrique"]')).toHaveText(['Sécurité'])
    await expect(article(fenetre, VEILLE.ficheIa).locator('[data-champ="rubrique"]')).toHaveText(['Réglementation'])
    await expect(article(fenetre, VEILLE.ficheExpert).locator('[data-champ="rubrique"]')).toHaveText(['Réglementation'])
    await expect(article(fenetre, VEILLE.avisAbonne).locator('[data-champ="rubrique"]')).toHaveCount(0)
    await expect(article(fenetre, VEILLE.ficheIa).locator('[data-mention="ia"]')).toBeVisible()
    // Rien de plus que les droits de jules.
    const texte = await fenetre.locator('body').innerText()
    for (const absent of JAMAIS_POUR_JULES) expect(texte).not.toContain(absent)
    await capture(fenetre, 'desktop-veille-gazette')

    await expect.poll(() => appelApiAuthentifie(desktop!, '/api/v1/preferences')).toBe(true)
    await expect.poll(() => appelApiAuthentifie(desktop!, '/api/v1/me/veille')).toBe(true)
    await verifierJournalLocal(desktop)
  })
