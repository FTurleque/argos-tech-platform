// « Ma veille » sur le portail web (personnaliser-profils-et-affichage, tâches 2.4, 2.6, 3.4, 3.9 et 5.2,
// partie web) : veille réelle restituée par GET /api/v1/me/veille, sous les droits de l'utilisateur,
// affichée dans la mise en page de sa préférence. Jeu de données : support/donnees.ts (preparerVeille) et
// donnees/veille.sql. L'union, le dédoublonnage et les droits sont calculés par le serveur : les parcours
// comparent ce que montre l'IHM à la réponse de l'API et vérifient qu'aucune mise en page n'en change les faits.
import type { Locator, Page } from '@playwright/test'
import { api, fixerPreferences, VEILLE, veilleDe } from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'
import { etatFocus, mesurerContrastes } from '../support/mesures'

const MISES_EN_PAGE = ['classique', 'journal', 'fantaisie', 'gazette'] as const
const DEUX_PROFILS = ['developpeur', 'expert-metier']
/** Union attendue pour jules (developpeur + expert-metier + abonnement personnel), sans doublon. */
const UNION_JULES = [VEILLE.avisCommun, VEILLE.avisAbonne, VEILLE.ficheIa, VEILLE.ficheExpert]
/** Ce que jules n'a pas le droit de voir, ou qui n'est pas diffusé (fiche non validée). */
const JAMAIS_POUR_JULES = [VEILLE.avisEspaceB, VEILLE.ficheEnAttente, VEILLE.projetB, VEILLE.idProjetB]

const miseEnPage = (page: Page) => page.locator('[data-mise-en-page]')
const articles = (page: Page) => page.locator('[data-mise-en-page] [data-element]')

function article(page: Page, titre: string): Locator {
  return articles(page).filter({ has: page.locator('[data-champ="titre"]', { hasText: titre }) })
}

/** Ouvre « Ma veille » par la navigation (même session) et attend la mise en page donnée. */
async function ouvrirMaVeille(page: Page, id: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Ma veille' }).click()
  await expect(page.getByRole('heading', { name: 'Ma veille', level: 1 })).toBeVisible()
  await expect(page.locator(`[data-mise-en-page="${id}"]`)).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
}

/** Faits restitués par chaque article : champs, mentions et liens, dans l'ordre du document. */
async function faits(page: Page): Promise<string[][]> {
  return articles(page).evaluateAll((els) =>
    els.map((a) => [
      ...[...a.querySelectorAll('[data-champ], [data-mention]')].map(
        (e) => `${e.getAttribute('data-champ') ?? e.getAttribute('data-mention')}=${(e.textContent ?? '').replace(/\s+/g, ' ').trim()}`,
      ),
      ...[...a.querySelectorAll('a[href]')].map((l) => `lien=${l.getAttribute('href')}`),
    ]),
  )
}

async function titres(page: Page): Promise<string[]> {
  return page.locator('[data-mise-en-page] [data-element] [data-champ="titre"]').allTextContents().then((t) => t.map((x) => x.trim()))
}

/** Capture légère : JPEG de la fenêtre, mise en page en haut de l'écran. */
async function capture(page: Page, nom: string): Promise<void> {
  await miseEnPage(page).evaluate((el) => el.scrollIntoView({ block: 'start' }))
  await page.screenshot({ path: `resultats/${nom}.jpg`, type: 'jpeg', quality: 60 })
}

async function choisirMiseEnPage(page: Page, id: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Mise en page' }).click()
  const radio = page.getByRole('radiogroup', { name: 'Mises en page disponibles' }).getByRole('radio', { name: new RegExp(id, 'i') })
  await radio.click()
  await expect(radio).toHaveAttribute('aria-checked', 'true')
  await expect.poll(async () => (await api<{ miseEnPage: string }>('jules', '/api/v1/preferences')).miseEnPage).toBe(id)
}

test('3.4, 3.9 (PRF-02, PRF-02-S3) : deux profils actifs et un abonnement personnel, union sans doublon avec rubriques', async ({ page }) => {
  await fixerPreferences('jules', ['developpeur'], 'classique')

  // Profils choisis dans l'IHM : developpeur (déjà actif) + expert-metier.
  await seConnecterDepuis(page, '/veille/profils', 'jules')
  await expect(page.getByRole('heading', { name: 'Vos profils de veille' })).toBeVisible()
  const developpeur = page.getByRole('button', { name: /^Développeur/ })
  const expert = page.getByRole('button', { name: /^Expert métier/ })
  await expect(developpeur).toHaveAttribute('aria-pressed', 'true')
  await expect(expert).toHaveAttribute('aria-pressed', 'false')
  await expert.click()
  await expect(expert).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(async () => (await api<{ profilsActifs: string[] }>('jules', '/api/v1/preferences')).profilsActifs).toEqual(DEUX_PROFILS)

  await ouvrirMaVeille(page, 'classique')
  const reponse = await veilleDe('jules')
  expect(reponse.profilsActifs).toEqual(DEUX_PROFILS)
  const attendus = reponse.elements.map((e) => e.titre)
  expect([...attendus].sort()).toEqual([...UNION_JULES].sort())
  expect(new Set(attendus).size, 'aucun doublon dans la réponse du serveur').toBe(attendus.length)
  await expect(page.getByText('Profils actifs : Développeur, Expert métier')).toBeVisible()
  // L'IHM restitue exactement les éléments du serveur, dans son ordre, chacun une fois.
  expect(await titres(page)).toEqual(attendus)

  // Avis du profil developpeur, retenu aussi par l'abonnement personnel : une occurrence, rubrique et deux origines.
  const commun = reponse.elements.find((e) => e.titre === VEILLE.avisCommun)!
  expect(commun.profils).toEqual(['developpeur'])
  expect(commun.abonnements).toHaveLength(1)
  const avisCommun = article(page, VEILLE.avisCommun)
  await expect(avisCommun).toHaveCount(1)
  await expect(avisCommun.locator('[data-champ="rubrique"]')).toHaveText(['Sécurité'])
  await expect(avisCommun.locator('[data-champ="projet"]')).toHaveText([VEILLE.projetA])
  await expect(avisCommun.locator('[data-mention="origine"]')).toContainText('Profil Développeur')
  await expect(avisCommun.locator('[data-mention="origine"]')).toContainText(' · ')

  // Avis retenu par le seul abonnement personnel : sans rubrique, sans le projet de l'espace B.
  const avisAbonne = article(page, VEILLE.avisAbonne)
  await expect(avisAbonne).toHaveCount(1)
  await expect(avisAbonne.locator('[data-champ="rubrique"]')).toHaveCount(0)
  await expect(avisAbonne.locator('[data-champ="sans-projet"]')).toBeVisible()
  await expect(avisAbonne.locator('[data-mention="origine"]')).not.toContainText('Profil')

  // Fiches validées du profil expert-metier : rubrique, validation, licence ; mention IA pour le résumé du modèle.
  for (const titre of [VEILLE.ficheIa, VEILLE.ficheExpert]) {
    const fiche = article(page, titre)
    await expect(fiche.locator('[data-champ="rubrique"]')).toHaveText(['Réglementation'])
    await expect(fiche.locator('[data-mention="validation"]')).toBeVisible()
    await expect(fiche.locator('[data-mention="attribution"]')).toBeVisible()
    await expect(fiche.locator('[data-mention="origine"]')).toHaveText('Profil Expert métier')
  }
  await expect(article(page, VEILLE.ficheIa).locator('[data-mention="ia"]')).toBeVisible()
  await expect(article(page, VEILLE.ficheExpert).locator('[data-mention="ia"]')).toHaveCount(0)

  const sommaire = await page.locator('[data-mise-en-page] [data-rubrique]').allTextContents()
  expect(sommaire.join(' | ')).toContain('Sécurité')
  expect(sommaire.join(' | ')).toContain('Réglementation')

  const texte = await page.locator('main, body').first().innerText()
  for (const absent of JAMAIS_POUR_JULES) expect(texte).not.toContain(absent)
  expect(texte, 'signaux de l’espace de recherche d’alice').not.toContain('Oracle')
  await capture(page, 'veille-union-classique')
})

test('3.9, AFF-02-S2 (PRF-02-S4) : un utilisateur d’un autre espace ne voit rien de plus que ses droits', async ({ page }) => {
  await fixerPreferences('karim', DEUX_PROFILS, 'classique')
  await seConnecterDepuis(page, '/veille', 'karim')
  await expect(page.locator('[data-mise-en-page="classique"]')).toBeVisible()

  const reponse = await veilleDe('karim')
  expect(reponse.elements.map((e) => e.titre).sort()).toEqual([VEILLE.avisAbonne, VEILLE.avisEspaceB].sort())
  expect(await titres(page)).toEqual(reponse.elements.map((e) => e.titre))
  await expect(article(page, VEILLE.avisAbonne).locator('[data-champ="projet"]')).toHaveText([VEILLE.projetB])
  const texte = await page.locator('body').innerText()
  for (const absent of [VEILLE.avisCommun, VEILLE.ficheIa, VEILLE.ficheExpert, VEILLE.ficheEnAttente, VEILLE.projetA]) {
    expect(texte).not.toContain(absent)
  }

  // Côté jules : le projet de l'espace B n'est restitué ni par l'API ni par aucun champ de sa veille.
  await fixerPreferences('jules', DEUX_PROFILS, 'classique')
  const deJules = JSON.stringify(await veilleDe('jules'))
  expect(deJules).not.toContain(VEILLE.projetB)
  expect(deJules).not.toContain(VEILLE.idProjetB)
  expect(deJules).not.toContain(VEILLE.avisEspaceB)
})

test('2.4 (AFF-02-S1, AFF-02-S2) : la même veille réelle dans les quatre mises en page', async ({ page }) => {
  await fixerPreferences('jules', DEUX_PROFILS, 'classique')
  const reponse = await veilleDe('jules')
  const liens = reponse.elements.map((e) => e.lien).filter((l): l is string => !!l)
  expect(liens.length, 'liens officiels des fiches').toBe(2)

  await seConnecterDepuis(page, '/affichage', 'jules')
  await expect(page.getByRole('heading', { name: 'Mise en page', exact: true })).toBeVisible()
  const parMiseEnPage: Record<string, string[][]> = {}
  for (const id of MISES_EN_PAGE) {
    // Changement de mise en page dans l'IHM (classique, puis journal, fantaisie, gazette), puis « Ma veille ».
    await choisirMiseEnPage(page, id)
    await ouvrirMaVeille(page, id)
    expect(await titres(page), id).toEqual(reponse.elements.map((e) => e.titre))
    parMiseEnPage[id] = await faits(page)
    // AFF-01 : la veille réelle n'est jamais annoncée comme un exemple.
    await expect(miseEnPage(page).locator('[data-exemple]')).toHaveCount(0)
    // ADR-0023 : la fiche au résumé produit par l'IA porte la mention dans chaque mise en page.
    await expect(article(page, VEILLE.ficheIa).locator('[data-mention="ia"]'), id).toBeVisible()
    // Liens externes : http(s) seulement, nouvelle fenêtre, sans référent ni accès à la fenêtre d'origine.
    const hrefs = await miseEnPage(page).locator('[data-element] a[href]').evaluateAll((as) =>
      as.map((a) => ({ href: a.getAttribute('href'), rel: a.getAttribute('rel'), target: a.getAttribute('target') })),
    )
    expect(hrefs.map((h) => h.href), id).toEqual(liens)
    for (const h of hrefs) expect(h, id).toMatchObject({ rel: 'noopener noreferrer', target: '_blank' })
    // AFF-02-S2 : l'impact sur le projet de l'espace B n'est restitué dans aucune mise en page.
    const texte = await page.locator('body').innerText()
    for (const absent of JAMAIS_POUR_JULES) expect(texte, id).not.toContain(absent)
    await capture(page, `veille-${id}`)
  }
  expect(parMiseEnPage.classique).toHaveLength(UNION_JULES.length)
  for (const id of MISES_EN_PAGE) expect(parMiseEnPage[id], id).toEqual(parMiseEnPage.classique)
  // Mêmes projets dans chacune : celui de l'espace A pour l'avis du profil, aucun pour l'avis de l'abonnement.
  expect(parMiseEnPage.classique.flat()).toContain(`projet=${VEILLE.projetA}`)
  expect(parMiseEnPage.classique.flat()).toContain('sans-projet=aucun projet visible')
})

test('5.2 (partie web) : « gazette » choisie sur le web, « Ma veille » affichée en gazette avec l’union des veilles', async ({ page }) => {
  await fixerPreferences('jules', DEUX_PROFILS, 'classique')
  await seConnecterDepuis(page, '/affichage', 'jules')
  await choisirMiseEnPage(page, 'gazette')

  // Nouvelle session : la préférence et la veille sont relues sur le serveur.
  await page.reload()
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page).toHaveURL(/#\/affichage$/)
  await ouvrirMaVeille(page, 'gazette')

  const reponse = await veilleDe('jules')
  expect(reponse.profilsActifs).toEqual(DEUX_PROFILS)
  expect(await titres(page)).toEqual(reponse.elements.map((e) => e.titre))
  expect([...(await titres(page))].sort()).toEqual([...UNION_JULES].sort())
  await expect(article(page, VEILLE.avisCommun).locator('[data-champ="rubrique"]')).toHaveText(['Sécurité'])
  await expect(article(page, VEILLE.ficheIa).locator('[data-champ="rubrique"]')).toHaveText(['Réglementation'])
  await expect(article(page, VEILLE.avisAbonne).locator('[data-champ="rubrique"]')).toHaveCount(0)
  await capture(page, 'veille-gazette-web')
})

for (const id of MISES_EN_PAGE) {
  test(`2.6 : accessibilité de « ${id} » sur la veille réelle (contrastes, tabulation, noms accessibles)`, async ({ page }) => {
    await fixerPreferences('jules', DEUX_PROFILS, id)
    await seConnecterDepuis(page, '/veille', 'jules')
    const racine = page.locator(`[data-mise-en-page="${id}"]`)
    await expect(racine).toBeVisible()
    await page.evaluate(() => document.fonts.ready)

    // RGAA 3.2 : contraste de chaque texte rendu sur son fond effectif.
    const mesures = await page.evaluate(mesurerContrastes)
    expect(mesures.length).toBeGreaterThan(20)
    const ecarts = mesures.filter((m) => m.rapport < m.seuil)
    expect(ecarts, JSON.stringify(ecarts, null, 1)).toEqual([])
    test.info().annotations.push({ type: 'contraste minimal', description: String(Math.min(...mesures.map((m) => m.rapport))) })

    // RGAA 12.8, 10.7, 3.3 : ordre de tabulation dans la mise en page = ordre du document ; focus visible et contrasté.
    const liens = await racine.locator('a[href]').evaluateAll((as) => as.map((a) => a.getAttribute('href')))
    expect(liens.length).toBeGreaterThan(0)
    const parcourus: string[] = []
    let contrasteFocus = Infinity
    for (let i = 0; i < 80; i++) {
      await page.keyboard.press('Tab')
      const focus = await page.evaluate(etatFocus)
      if (!focus?.dansMiseEnPage) {
        if (parcourus.length > 0) break
        continue
      }
      parcourus.push(focus.href ?? '')
      expect(focus.style, 'contour de focus').not.toBe('none')
      expect(focus.largeur).toBeGreaterThanOrEqual(2)
      expect(focus.contraste, `contraste du contour de focus (${focus.href})`).toBeGreaterThanOrEqual(3)
      contrasteFocus = Math.min(contrasteFocus, focus.contraste)
    }
    expect(parcourus).toEqual(liens)
    test.info().annotations.push({ type: 'contraste minimal du focus', description: contrasteFocus.toFixed(2) })

    // Noms accessibles : région nommée, un article par élément, nommé par son titre ; liens annoncés.
    const reponse = await veilleDe('jules')
    await expect(racine).toHaveRole('region')
    await expect(racine).toHaveAccessibleName(/.+/)
    await expect(racine.getByRole('article')).toHaveCount(reponse.elements.length)
    for (const e of reponse.elements) {
      await expect(racine.getByRole('article', { name: e.titre, exact: true })).toHaveCount(1)
      await expect(racine.getByRole('heading', { name: e.titre, exact: true })).toHaveCount(1)
    }
    for (const lien of await racine.getByRole('link').all()) {
      await expect(lien).toHaveAccessibleName(/\(nouvelle fenêtre\)$/)
    }
    expect(await racine.ariaSnapshot()).not.toContain('Données d’exemple')
  })
}
