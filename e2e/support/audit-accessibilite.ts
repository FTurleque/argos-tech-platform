// Audit d'accessibilité d'une zone d'un écran réel (AUD-TST-23 ; RGAA 4.1.2 critères 3.2, 3.3, 10.7, 10.11, 12.8 ;
// WCAG 1.4.3, 1.4.10, 1.4.11, 2.4.3, 2.4.7) : contraste de chaque texte rendu, parcours au clavier de chaque élément
// focalisable dans l'ordre du document avec un focus visible et contrasté, nom accessible des commandes, lecture à
// 320 px de large sans défilement horizontal. Mesures : support/mesures.ts.
import type { Page } from '@playwright/test'
import { expect, test } from './fixtures'
import { etatFocus, mesurerContrastes } from './mesures'

/** Seuil de contraste du contour de focus sur son fond (WCAG 1.4.11). */
const CONTRASTE_FOCUS_MIN = 3
/** Épaisseur minimale du contour de focus, en pixels. */
const CONTOUR_MIN = 2
/** Largeur d'écran de la règle de redistribution (WCAG 1.4.10). */
const LARGEUR_ETROITE = 320
const HAUTEUR_ETROITE = 800
/** Tabulations au plus avant d'atteindre la zone, depuis le haut de la page. */
const TABULATIONS_MAX = 120

const FOCALISABLES = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), '
  + 'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** RGAA 3.2 : chaque texte rendu de la zone atteint son seuil de contraste (4,5:1, ou 3:1 en grand corps). */
export async function verifierContrastes(page: Page, selecteur: string, textesMin: number): Promise<void> {
  const mesures = await page.evaluate(mesurerContrastes, selecteur)
  expect(mesures.length, `textes mesurés dans ${selecteur}`).toBeGreaterThanOrEqual(textesMin)
  const ecarts = mesures.filter((mesure) => mesure.rapport < mesure.seuil)
  expect(ecarts, JSON.stringify(ecarts, null, 1)).toEqual([])
  test.info().annotations.push({
    type: `contraste minimal (${selecteur})`,
    description: String(Math.min(...mesures.map((mesure) => mesure.rapport))),
  })
}

/**
 * RGAA 12.8, 10.7, 3.3 : depuis le haut de la page, la tabulation parcourt les éléments focalisables de la zone dans
 * l'ordre du document, chacun avec un contour de focus visible, épais d'au moins 2 px et contrasté (3:1) ; chaque
 * commande porte un nom accessible.
 */
export async function verifierClavier(page: Page, selecteur: string): Promise<void> {
  const zone = page.locator(selecteur)
  const attendus = await zone.locator(FOCALISABLES).evaluateAll((elements) => elements
    .filter((element) => (element as HTMLElement).offsetParent !== null)
    .map((element) => (element.getAttribute('aria-label') ?? element.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 50)))
  expect(attendus.length, `éléments focalisables de ${selecteur}`).toBeGreaterThan(0)
  for (const commande of await zone.getByRole('button').all()) {
    await expect(commande).toHaveAccessibleName(/\S/)
  }
  // Point de départ de la tabulation en haut du document : un repère focalisable placé en tête, focalisé puis retiré
  // (après un simple blur, Chromium reprendrait la tabulation après l'élément qui avait le focus).
  await page.evaluate(() => {
    const repere = document.createElement('div')
    repere.tabIndex = -1
    document.body.prepend(repere)
    repere.focus()
    repere.remove()
  })
  const parcourus: string[] = []
  for (let tabulation = 0; tabulation < TABULATIONS_MAX + attendus.length; tabulation++) {
    await page.keyboard.press('Tab')
    const focus = await page.evaluate(etatFocus, selecteur)
    if (!focus?.dansZone) {
      if (parcourus.length > 0) break
      continue
    }
    parcourus.push(focus.nom)
    expect(focus.style, `contour de focus de « ${focus.nom} »`).not.toBe('none')
    expect(focus.largeur, `épaisseur du contour de « ${focus.nom} »`).toBeGreaterThanOrEqual(CONTOUR_MIN)
    expect(focus.contraste, `contraste du contour de « ${focus.nom} »`).toBeGreaterThanOrEqual(CONTRASTE_FOCUS_MIN)
  }
  expect(parcourus).toEqual(attendus)
}

/** RGAA 10.11 (WCAG 1.4.10) : à 320 px de large, la page ne défile pas horizontalement. */
export async function verifierRedistribution(page: Page, selecteur: string): Promise<void> {
  const tailleInitiale = page.viewportSize()
  await page.setViewportSize({ width: LARGEUR_ETROITE, height: HAUTEUR_ETROITE })
  try {
    await expect(page.locator(selecteur)).toBeVisible()
    const { debord, depassants } = await page.evaluate(() => {
      const largeur = document.documentElement.clientWidth
      // Éléments les plus profonds dont le bord droit dépasse la fenêtre : ils nomment la cause d'un débordement.
      const depasse = [...document.body.querySelectorAll('*')]
        .filter((element) => element.getBoundingClientRect().right > largeur + 1)
      const feuilles = depasse.filter((element) => !depasse.some((autre) => autre !== element && element.contains(autre)))
      return {
        debord: document.documentElement.scrollWidth - largeur,
        depassants: feuilles.slice(0, 5).map((element) => {
          const nom = element.getAttribute('id') ?? element.getAttribute('name') ?? element.textContent?.trim().slice(0, 30)
          const parent = element.parentElement
          return `${element.tagName.toLowerCase()} « ${nom ?? ''} » dans ${parent?.tagName.toLowerCase()}.${parent?.className ?? ''}`
            + ` (bord droit ${Math.round(element.getBoundingClientRect().right)} px)`
        }),
      }
    })
    expect(debord, `défilement horizontal à 320 px ; éléments en cause : ${depassants.join(', ')}`).toBeLessThanOrEqual(0)
    await page.screenshot({ path: test.info().outputPath('etroit.png'), fullPage: true })
  } finally {
    if (tailleInitiale) await page.setViewportSize(tailleInitiale)
  }
}

/** Les trois contrôles sur une zone : contrastes, clavier, redistribution. */
export async function auditerZone(page: Page, selecteur: string, textesMin: number): Promise<void> {
  await page.evaluate(() => document.fonts.ready)
  await verifierContrastes(page, selecteur, textesMin)
  await verifierClavier(page, selecteur)
  await verifierRedistribution(page, selecteur)
}
