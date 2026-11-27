// Audit d'accessibilité des écrans de gestion (AUD-TST-23) : vue d'inventaire, actions sur les abonnements de
// « Ma veille » et traitement des impacts d'un avis. Chaque écran est audité sur la pile réelle, avec
// le jeu de données des parcours (support/donnees.ts) : contrastes, parcours au clavier et focus, noms accessibles,
// redistribution à 320 px (support/audit-accessibilite.ts). Aucune donnée n'est modifiée : les actions sont ouvertes,
// puis annulées.
import { auditerZone } from '../support/audit-accessibilite'
import { INVENTAIRE, VEILLE } from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

/** Avis V1 du jeu de veille (donnees/veille.sql), qui touche « Paie E2E veille » de l'espace A, administré par ines. */
const AVIS_ESPACE_A = '0e2e0000-0000-4000-8000-0000000001a1'
const ZONE_ABONNEMENTS = 'section[aria-labelledby="titre-abonnements"]'
const ZONE_IMPACTS = '[data-traitement-impacts]'
/** Textes mesurés au moins dans chaque zone : la mesure porte bien sur un écran rendu. */
const TEXTES_INVENTAIRE_MIN = 20
const TEXTES_ZONE_MIN = 3

test('vue d’inventaire : synthèse, dépôts, composants paginés et écartés', async ({ page }) => {
  await seConnecterDepuis(page, `/inventaire/${INVENTAIRE.projet}`, 'alice')
  await expect(page.getByRole('table', { name: /^Composants inventoriés du projet / })).toBeVisible()
  await auditerZone(page, 'main', TEXTES_INVENTAIRE_MIN)
})

test('« Mes abonnements » : état et actions de chaque abonnement, confirmation de suppression', async ({ page }) => {
  await seConnecterDepuis(page, '/veille', 'jules')
  const abonnements = page.getByRole('region', { name: 'Mes abonnements' })
  await expect(abonnements.getByRole('listitem').first()).toBeVisible()
  await auditerZone(page, ZONE_ABONNEMENTS, TEXTES_ZONE_MIN)
  await abonnements.getByRole('listitem').first().getByRole('button', { name: 'Supprimer' }).click()
  await expect(abonnements.getByRole('group', { name: 'Confirmer la suppression' })).toBeVisible()
  await auditerZone(page, ZONE_ABONNEMENTS, TEXTES_ZONE_MIN)
  await abonnements.getByRole('button', { name: 'Annuler' }).click()
  await expect(abonnements.getByRole('group', { name: 'Confirmer la suppression' })).toHaveCount(0)
})

test('traitement des impacts : état, historique, actions et formulaire de transition', async ({ page }) => {
  await seConnecterDepuis(page, `/signaux/${AVIS_ESPACE_A}`, 'ines')
  const impact = page.locator(`${ZONE_IMPACTS} [data-impact]`).filter({ hasText: VEILLE.projetA })
  await expect(impact.locator('[data-champ="etat"]')).toBeVisible()
  await auditerZone(page, ZONE_IMPACTS, TEXTES_ZONE_MIN)
  await impact.getByRole('button', { name: 'Historique et actions' }).click()
  await expect(impact.locator('[data-actions]')).toBeVisible()
  await auditerZone(page, ZONE_IMPACTS, TEXTES_ZONE_MIN)
  await impact.locator('[data-actions]').getByRole('button').first().click()
  await expect(impact.locator('form')).toBeVisible()
  await auditerZone(page, ZONE_IMPACTS, TEXTES_ZONE_MIN)
  await impact.locator('form').getByRole('button', { name: 'Annuler' }).click()
  await expect(impact.locator('form')).toHaveCount(0)
})
