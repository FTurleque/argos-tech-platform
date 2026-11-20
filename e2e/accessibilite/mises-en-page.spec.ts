// Audit d'accessibilité scripté des quatre mises en page, dans Chromium (tâche 2.6, AFF-05-S1 ;
// RGAA 4.1.2 critères 3.2, 3.3, 7.3, 10.7, 10.11, 12.8, 13.8 ; WCAG 1.4.3, 1.4.10, 1.4.11, 2.3.3,
// 2.4.3, 2.4.7). Complète les contrôles du HTML et des palettes exécutés par « npm --prefix argos-ui test ».
// Aucune requête ne quitte le poste : toute requête vers un autre hôte que 127.0.0.1 fait échouer le test,
// ce qui prouve aussi qu'aucune police n'est chargée depuis un service externe.
import { expect, test, type Page } from '@playwright/test'
import { etatFocus, mesurerContrastes } from '../support/mesures'

const MISES_EN_PAGE = ['classique', 'journal', 'fantaisie', 'gazette'] as const
const POLICES: Record<(typeof MISES_EN_PAGE)[number], string[]> = {
  classique: ['700 16px "Space Grotesk"', '400 16px Inter'],
  journal: ['400 16px UnifrakturMaguntia', '700 16px "Playfair Display"', '400 16px "Source Serif 4"'],
  fantaisie: ['900 16px Fraunces', '700 16px Caveat', '400 16px Literata'],
  gazette: ['700 16px UnifrakturCook', '400 16px "Alfa Slab One"', '700 16px "Playfair Display SC"', '400 16px "IM Fell English"'],
}
const LOCAUX = new Set(['127.0.0.1', 'localhost', '[::1]'])

async function ouvrir(page: Page, id: string): Promise<string[]> {
  const externes: string[] = []
  await page.route('**/*', (route) => {
    const hote = new URL(route.request().url()).hostname
    if (LOCAUX.has(hote) || hote === '') return route.continue()
    externes.push(route.request().url())
    return route.abort()
  })
  await page.goto(`/banc/index.html?mise-en-page=${id}`)
  await expect(page.locator(`[data-mise-en-page="${id}"]`)).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
  return externes
}

for (const id of MISES_EN_PAGE) {
  test.describe(`mise en page « ${id} »`, () => {
    test('polices embarquées chargées, aucune requête hors du poste', async ({ page }) => {
      const externes = await ouvrir(page, id)
      for (const police of POLICES[id]) {
        expect(await page.evaluate((nomPolice) => document.fonts.check(nomPolice), police), police).toBe(true)
      }
      const chargees = await page.evaluate(() =>
        [...document.fonts].filter((fonte) => fonte.status === 'loaded').map((fonte) => fonte.family))
      expect(chargees.length).toBeGreaterThanOrEqual(POLICES[id].length)
      expect(externes).toEqual([])
      await page.screenshot({ path: test.info().outputPath(`${id}-bureau.png`), fullPage: true })
    })

    test('RGAA 3.2 : contraste de chaque texte rendu (4,5:1, ou 3:1 au-delà de 24 px ou 18,66 px gras)', async ({ page }) => {
      await ouvrir(page, id)
      const mesures = await page.evaluate(mesurerContrastes)
      expect(mesures.length).toBeGreaterThan(30)
      const ecarts = mesures.filter((mesure) => mesure.rapport < mesure.seuil)
      expect(ecarts, JSON.stringify(ecarts, null, 1)).toEqual([])
      test.info().annotations.push({
        type: 'contraste minimal',
        description: String(Math.min(...mesures.map((mesure) => mesure.rapport))),
      })
    })

    test('RGAA 12.8, 10.7 : ordre de tabulation dans l’ordre du document, focus visible et contrasté (3:1)', async ({ page }) => {
      await ouvrir(page, id)
      const liens = await page.locator('[data-mise-en-page] a[href]')
        .evaluateAll((ancres) => ancres.map((ancre) => ancre.getAttribute('href')))
      expect(liens.length).toBeGreaterThan(0)
      const parcourus: string[] = []
      for (let tabulation = 0; tabulation < liens.length + 1; tabulation++) {
        await page.keyboard.press('Tab')
        const etat = await page.evaluate(etatFocus)
        // Contraste du contour de focus sur le fond effectif de l'élément (WCAG 1.4.11, RGAA 3.3).
        const focus = etat?.dansMiseEnPage ? etat : null
        if (!focus) break
        parcourus.push(focus.href ?? '')
        expect(focus.style, 'contour de focus').not.toBe('none')
        expect(focus.largeur).toBeGreaterThanOrEqual(2)
        expect(focus.contraste, `contraste du contour de focus (${focus.href})`).toBeGreaterThanOrEqual(3)
      }
      expect(parcourus).toEqual(liens)
    })

    test('RGAA 10.11 (WCAG 1.4.10) : lisible à 320 px de large, sans défilement horizontal', async ({ page }) => {
      await page.setViewportSize({ width: 320, height: 800 })
      await ouvrir(page, id)
      const debord = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(debord).toBeLessThanOrEqual(0)
      const horsCadre = await page.locator('[data-mise-en-page] [data-element]').evaluateAll((els) =>
        els.filter((element) => element.getBoundingClientRect().right > document.documentElement.clientWidth + 1).length,
      )
      expect(horsCadre).toBe(0)
    })

    test('à la largeur d’un téléphone (375 px), les colonnes s’empilent', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 812 })
      await ouvrir(page, id)
      const gauches = await page.locator('[data-mise-en-page] [data-element]').evaluateAll((els) =>
        els.map((element) => Math.round(element.getBoundingClientRect().left)),
      )
      expect(new Set(gauches).size, `positions horizontales : ${gauches.join(', ')}`).toBe(1)
      await page.screenshot({ path: `resultats/accessibilite/${id}-telephone.png` })
    })

    test('RGAA 13.8 : aucun mouvement quand l’utilisateur demande la réduction des animations', async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await ouvrir(page, id)
      await page.locator('[data-mise-en-page] [data-element]').first().hover()
      const animes = await page.locator('[data-mise-en-page], [data-mise-en-page] *').evaluateAll((els) =>
        els.filter((element) => {
          const style = getComputedStyle(element)
          return style.transitionDuration.split(',').some((duree) => parseFloat(duree) > 0) || style.animationName !== 'none'
        }).length,
      )
      expect(animes).toBe(0)
    })

    test('arbre d’accessibilité : région, articles, titres et liens nommés ; décor ignoré', async ({ page }) => {
      await ouvrir(page, id)
      const racine = page.locator(`[data-mise-en-page="${id}"]`)
      await expect(racine).toHaveRole('region')
      await expect(racine.getByRole('article')).toHaveCount(6)
      await expect(racine.getByRole('heading', { level: 3 })).toHaveCount(7)
      for (const lien of await racine.getByRole('link').all()) {
        await expect(lien).toHaveAccessibleName(/\(nouvelle fenêtre\)$/)
      }
      const arbre = await racine.ariaSnapshot()
      for (const decor of ['à lire avant le café', 'Les intrus', 'Avis de recherche', 'Signalement', 'Alerte critique']) {
        expect(arbre).not.toContain(decor)
      }
      expect(arbre).toContain('Données d’exemple')
    })
  })
}

test('AFF-02 (banc) : les quatre mises en page restituent les mêmes faits du même jeu de données', async ({ page }) => {
  const parMiseEnPage: Record<string, string[][]> = {}
  for (const id of MISES_EN_PAGE) {
    await ouvrir(page, id)
    parMiseEnPage[id] = await page.locator('[data-mise-en-page] [data-element]').evaluateAll((articles) =>
      articles.map((article) =>
        [...article.querySelectorAll('[data-champ], [data-mention]')].map(
          (champ) => `${champ.getAttribute('data-champ') ?? champ.getAttribute('data-mention')}=`
            + `${(champ.textContent ?? '').replace(/\s+/g, ' ').trim()}`,
        ),
      ),
    )
    await page.unrouteAll()
  }
  expect(parMiseEnPage.classique).toHaveLength(6)
  for (const id of MISES_EN_PAGE) expect(parMiseEnPage[id], id).toEqual(parMiseEnPage.classique)
})
