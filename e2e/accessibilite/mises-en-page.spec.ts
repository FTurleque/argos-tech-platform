// Audit d'accessibilité scripté des quatre mises en page, dans Chromium (tâche 2.6, AFF-05-S1 ;
// RGAA 4.1.2 critères 3.2, 3.3, 7.3, 10.7, 10.11, 12.8, 13.8 ; WCAG 1.4.3, 1.4.10, 1.4.11, 2.3.3,
// 2.4.3, 2.4.7). Complète les contrôles du HTML et des palettes exécutés par « npm --prefix argos-ui test ».
// Aucune requête ne quitte le poste : toute requête vers un autre hôte que 127.0.0.1 fait échouer le test,
// ce qui prouve aussi qu'aucune police n'est chargée depuis un service externe.
import { expect, test, type Page } from '@playwright/test'

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

/** Écarts de contraste de tout texte visible de la mise en page, sur son fond effectif. */
function mesurerContrastes() {
  type Rgba = [number, number, number, number]
  const lire = (c: string): Rgba => {
    const m = c.match(/rgba?\(([^)]+)\)/)
    if (!m) return [0, 0, 0, 0]
    const p = m[1].split(/[\s,/]+/).filter(Boolean).map(Number)
    return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1]
  }
  const fusion = (dessus: Rgba, dessous: Rgba): Rgba => {
    const a = dessus[3] + dessous[3] * (1 - dessus[3])
    if (a === 0) return [0, 0, 0, 0]
    const c = [0, 1, 2].map((i) => (dessus[i] * dessus[3] + dessous[i] * dessous[3] * (1 - dessus[3])) / a)
    return [c[0], c[1], c[2], a]
  }
  const lum = ([r, g, b]: Rgba) => {
    const f = (v: number) => {
      const s = v / 255
      return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  const rapport = (a: Rgba, b: Rgba) => {
    const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p)
    return (x + 0.05) / (y + 0.05)
  }
  const fond = (el: Element | null): Rgba => {
    const couches: Rgba[] = []
    for (let e = el; e; e = e.parentElement) {
      const c = lire(getComputedStyle(e).backgroundColor)
      if (c[3] > 0) couches.push(c)
      if (c[3] >= 1) break
    }
    return couches.reduceRight<Rgba>((dessous, dessus) => fusion(dessus, dessous), [255, 255, 255, 1])
  }
  const racine = document.querySelector('[data-mise-en-page]')!
  const mesures: { texte: string; rapport: number; seuil: number; decoratif: boolean }[] = []
  const marcheur = document.createTreeWalker(racine, NodeFilter.SHOW_TEXT)
  for (let n = marcheur.nextNode(); n; n = marcheur.nextNode()) {
    const texte = n.textContent?.trim()
    const el = n.parentElement
    if (!texte || !el) continue
    const r = el.getBoundingClientRect()
    const s = getComputedStyle(el)
    if (r.width * r.height < 4 || s.visibility === 'hidden' || s.display === 'none') continue
    const taille = parseFloat(s.fontSize)
    const gras = Number(s.fontWeight) >= 700
    const seuil = taille >= 24 || (gras && taille >= 18.66) ? 3 : 4.5
    const couleur = fusion(lire(s.color), fond(el))
    mesures.push({
      texte: texte.slice(0, 50),
      rapport: Math.floor(rapport(couleur, fond(el)) * 100) / 100,
      seuil,
      decoratif: !!el.closest('[aria-hidden="true"]'),
    })
  }
  return mesures
}

for (const id of MISES_EN_PAGE) {
  test.describe(`mise en page « ${id} »`, () => {
    test('polices embarquées chargées, aucune requête hors du poste', async ({ page }) => {
      const externes = await ouvrir(page, id)
      for (const police of POLICES[id]) {
        expect(await page.evaluate((p) => document.fonts.check(p), police), police).toBe(true)
      }
      const chargees = await page.evaluate(() => [...document.fonts].filter((f) => f.status === 'loaded').map((f) => f.family))
      expect(chargees.length).toBeGreaterThanOrEqual(POLICES[id].length)
      expect(externes).toEqual([])
      await page.screenshot({ path: test.info().outputPath(`${id}-bureau.png`), fullPage: true })
    })

    test('RGAA 3.2 : contraste de chaque texte rendu (4,5:1, ou 3:1 au-delà de 24 px ou 18,66 px gras)', async ({ page }) => {
      await ouvrir(page, id)
      const mesures = await page.evaluate(mesurerContrastes)
      expect(mesures.length).toBeGreaterThan(30)
      const ecarts = mesures.filter((m) => m.rapport < m.seuil)
      expect(ecarts, JSON.stringify(ecarts, null, 1)).toEqual([])
      test.info().annotations.push({
        type: 'contraste minimal',
        description: String(Math.min(...mesures.map((m) => m.rapport))),
      })
    })

    test('RGAA 12.8, 10.7 : ordre de tabulation dans l’ordre du document, focus visible et contrasté (3:1)', async ({ page }) => {
      await ouvrir(page, id)
      const liens = await page.locator('[data-mise-en-page] a[href]').evaluateAll((as) => as.map((a) => a.getAttribute('href')))
      expect(liens.length).toBeGreaterThan(0)
      const parcourus: string[] = []
      for (let i = 0; i < liens.length + 1; i++) {
        await page.keyboard.press('Tab')
        const focus = await page.evaluate(() => {
          const el = document.activeElement as HTMLElement | null
          if (!el || el === document.body || !el.closest('[data-mise-en-page]')) return null
          const s = getComputedStyle(el)
          // Contraste du contour de focus sur le fond effectif de l'élément (WCAG 1.4.11, RGAA 3.3).
          const lire = (c: string): number[] => {
            const m = c.match(/rgba?\(([^)]+)\)/)
            if (!m) return [0, 0, 0, 0]
            const p = m[1].split(/[\s,/]+/).filter(Boolean).map(Number)
            return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1]
          }
          const fusion = (dessus: number[], dessous: number[]): number[] => {
            const a = dessus[3] + dessous[3] * (1 - dessus[3])
            if (a === 0) return [0, 0, 0, 0]
            const c = [0, 1, 2].map((i) => (dessus[i] * dessus[3] + dessous[i] * dessous[3] * (1 - dessus[3])) / a)
            return [c[0], c[1], c[2], a]
          }
          const couches: number[][] = []
          for (let e: Element | null = el; e; e = e.parentElement) {
            const c = lire(getComputedStyle(e).backgroundColor)
            if (c[3] > 0) couches.push(c)
            if (c[3] >= 1) break
          }
          const fond = couches.reduceRight((dessous, dessus) => fusion(dessus, dessous), [255, 255, 255, 1])
          const contour = fusion(lire(s.outlineColor), fond)
          const lum = (c: number[]) => {
            const f = (v: number) => {
              const x = v / 255
              return x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4
            }
            return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2])
          }
          const [clair, sombre] = [lum(contour), lum(fond)].sort((x, y) => y - x)
          return {
            href: el.getAttribute('href'),
            style: s.outlineStyle,
            largeur: parseFloat(s.outlineWidth),
            contraste: (clair + 0.05) / (sombre + 0.05),
          }
        })
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
        els.filter((e) => e.getBoundingClientRect().right > document.documentElement.clientWidth + 1).length,
      )
      expect(horsCadre).toBe(0)
    })

    test('à la largeur d’un téléphone (375 px), les colonnes s’empilent', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 812 })
      await ouvrir(page, id)
      const gauches = await page.locator('[data-mise-en-page] [data-element]').evaluateAll((els) =>
        els.map((e) => Math.round(e.getBoundingClientRect().left)),
      )
      expect(new Set(gauches).size, `positions horizontales : ${gauches.join(', ')}`).toBe(1)
      await page.screenshot({ path: `resultats/accessibilite/${id}-telephone.png` })
    })

    test('RGAA 13.8 : aucun mouvement quand l’utilisateur demande la réduction des animations', async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await ouvrir(page, id)
      await page.locator('[data-mise-en-page] [data-element]').first().hover()
      const animes = await page.locator('[data-mise-en-page], [data-mise-en-page] *').evaluateAll((els) =>
        els.filter((e) => {
          const s = getComputedStyle(e)
          return s.transitionDuration.split(',').some((d) => parseFloat(d) > 0) || s.animationName !== 'none'
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
      articles.map((a) =>
        [...a.querySelectorAll('[data-champ], [data-mention]')].map(
          (e) => `${e.getAttribute('data-champ') ?? e.getAttribute('data-mention')}=${(e.textContent ?? '').replace(/\s+/g, ' ').trim()}`,
        ),
      ),
    )
    await page.unrouteAll()
  }
  expect(parMiseEnPage.classique).toHaveLength(6)
  for (const id of MISES_EN_PAGE) expect(parMiseEnPage[id], id).toEqual(parMiseEnPage.classique)
})
