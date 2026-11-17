// Lecture des écrans d'argos-ui, communs au portail web (Chromium) et au client desktop (fenêtre
// Electron) : les mêmes fonctions servent aux parcours des deux projets Playwright.
import { expect, type Locator, type Page } from '@playwright/test'
import { api, SIGNAL_ATTENDU, SIGNAUX_EXCLUS, VEILLE } from './donnees'

// ------------------------------------------------------------------ Recherche (rechercher-et-filtrer)

export function resultats(page: Page): Locator {
  return page.locator('ol.resultats > li h3')
}

/** Recherche « Oracle », projet « Paie », 30 jours : un seul résultat, le signal attendu. */
export async function verifierResultatUnique(page: Page): Promise<void> {
  await expect(resultats(page)).toHaveText([SIGNAL_ATTENDU])
  await expect(page.locator('section p.muted', { hasText: /^1 résultat\.$/ })).toBeVisible()
  await expect(page.locator('ol.resultats > li')).toContainText('Projets : Paie')
  for (const exclu of SIGNAUX_EXCLUS) await expect(page.getByText(exclu, { exact: true })).toHaveCount(0)
}

/** Critères de la vue rappelée restitués dans le formulaire : mots, période et projet. */
export async function verifierCriteresRappeles(page: Page, nomVue: string): Promise<void> {
  await expect(page.getByText(`Vue rappelée : ${nomVue}`)).toBeVisible()
  await expect(page.getByLabel('Mots recherchés')).toHaveValue('Oracle')
  await expect(page.getByLabel('Période')).toHaveValue('30')
  await expect(page.getByRole('group', { name: 'Projets' }).getByLabel('Paie')).toBeChecked()
}

// ------------------------------------------------------------------ « Ma veille » (personnaliser-profils-et-affichage)

export const MISES_EN_PAGE = ['classique', 'journal', 'fantaisie', 'gazette'] as const
export const DEUX_PROFILS = ['developpeur', 'expert-metier']
/** Union attendue pour jules (developpeur + expert-metier + abonnement personnel), sans doublon. */
export const UNION_JULES = [VEILLE.avisCommun, VEILLE.avisAbonne, VEILLE.ficheIa, VEILLE.ficheExpert]
/** Ce que jules n'a pas le droit de voir, ou qui n'est pas diffusé (fiche non validée). */
export const JAMAIS_POUR_JULES = [VEILLE.avisEspaceB, VEILLE.ficheEnAttente, VEILLE.projetB, VEILLE.idProjetB]

export const miseEnPage = (page: Page): Locator => page.locator('[data-mise-en-page]')
export const articles = (page: Page): Locator => page.locator('[data-mise-en-page] [data-element]')

export function article(page: Page, titre: string): Locator {
  return articles(page).filter({ has: page.locator('[data-champ="titre"]', { hasText: titre }) })
}

export async function titres(page: Page): Promise<string[]> {
  return page.locator('[data-mise-en-page] [data-element] [data-champ="titre"]').allTextContents().then((t) => t.map((x) => x.trim()))
}

/** Capture légère : JPEG de la fenêtre, mise en page en haut de l'écran. */
export async function capture(page: Page, nom: string): Promise<void> {
  await miseEnPage(page).first().evaluate((el) => el.scrollIntoView({ block: 'start' }))
  await page.screenshot({ path: `resultats/${nom}.jpg`, type: 'jpeg', quality: 60 })
}

/** Choisit une mise en page dans `#/affichage` par la navigation et attend son enregistrement sur le serveur. */
export async function choisirMiseEnPage(page: Page, utilisateur: string, id: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Mise en page' }).click()
  const radio = page.getByRole('radiogroup', { name: 'Mises en page disponibles' }).getByRole('radio', { name: new RegExp(id, 'i') })
  await radio.click()
  await expect(radio).toHaveAttribute('aria-checked', 'true')
  await expect.poll(async () => (await api<{ miseEnPage: string }>(utilisateur, '/api/v1/preferences')).miseEnPage).toBe(id)
}
