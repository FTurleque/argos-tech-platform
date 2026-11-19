// Chaîne de veille automatisée (changement automatiser-chaine-veille, tâche 4.3 ; décisions J1 à J7 du porteur du
// projet, 2026-11-19) : sans aucune action d'un utilisateur ni d'un exploitant, la synchronisation planifiée des
// sources d'avis (OSV simulé) et l'import planifié des inventaires (deux dépôts, GitLab et GitHub simulés) créent
// les impacts du projet ; un nouveau composant importé est recorrélé sans nouvelle collecte ; un contributeur
// confirme un impact dans l'IHM ; le composant retiré par un import suivant résout l'impact (auteur « système ») ;
// un abonnement suspendu ne produit aucune alerte, puis réactivé en produit de nouveau.
// Les deux tâches planifiées tournent dans la pile toutes les 5 secondes (playwright.config.ts, [PROPOSITION] PI-1)
// et n'interrogent que support/sources-simulees.mjs, programmé avec les réponses enregistrées de donnees/chaine/.
// Données : support/donnees.ts (preparerChaine), donnees/chaine.sql.
import type { Page } from '@playwright/test'
import {
  alertesChaine,
  api,
  AVIS_CHAINE,
  CHAINE,
  COMPTE_CHAINE,
  impactsChaine,
  preparerChaine,
  programmerSources,
  requetesSources,
  type AvisSimule,
} from '../support/donnees'
import { expect, seConnecterDepuis, test } from '../support/fixtures'

/** Délai d'attente d'une échéance planifiée (cadence de 5 secondes dans la pile, plus le traitement). */
const ECHEANCE = { timeout: 90_000, intervals: [1_000, 2_000, 3_000] }

const FORGES_V1 = { [CHAINE.sbomGitlab]: 'sbom-front-v1.json', [CHAINE.sbomGithub]: 'sbom-api-v1.json' }
const FORGES_V2 = { [CHAINE.sbomGitlab]: 'sbom-front-v1.json', [CHAINE.sbomGithub]: 'sbom-api-v2.json' }
const FORGES_V3 = { [CHAINE.sbomGitlab]: 'sbom-front-v2.json', [CHAINE.sbomGithub]: 'sbom-api-v2.json' }

interface Synthese {
  status: string
  summary: { componentCount: number; repositories: { provider: string; repository: string; importedAt: string | null }[] } | null
}

interface AbonnementApi {
  id: string
  scope: string
  state: string
  channel: string
  frequency: string
  filters: { type: string; value: string }[]
}

async function synthese(): Promise<Synthese> {
  return api<Synthese>(COMPTE_CHAINE, `/api/v1/projects/${CHAINE.projet}/inventory/summary`)
}

async function etatImpact(externe: string): Promise<string | undefined> {
  const impacts = await impactsChaine(['OUVERT', 'CONFIRME', 'ECARTE', 'RESOLU'])
  return impacts.find((i) => i.signalExternalId === externe)?.etat
}

/** Identifiant de l'alerte DESKTOP de gina pour l'avis donné (son titre reprend celui de l'avis), ou undefined. */
async function alertePour(avis: AvisSimule): Promise<string | undefined> {
  const numero = avis.id.slice(-4)
  return (await alertesChaine()).find((a) => a.titre.includes(`faille fictive ${numero}`))?.id
}

async function abonnementDeGina(): Promise<AbonnementApi> {
  const abonnements = await api<AbonnementApi[]>(COMPTE_CHAINE, '/api/v1/subscriptions')
  const personnel = abonnements.find((s) => s.scope === 'PERSONAL')
  if (!personnel) throw new Error('abonnement personnel de gina absent')
  return personnel
}

/** Ligne « Mes abonnements » de l'abonnement personnel de gina dans « Ma veille ». */
function ligneAbonnement(page: Page) {
  return page
    .getByRole('region', { name: 'Mes abonnements' })
    .getByRole('listitem')
    .filter({ hasText: 'Abonnement personnel' })
}

test.describe.configure({ mode: 'serial' })

test('chaîne automatisée : collecte et import planifiés, recorrélation, traitement d’un impact, abonnement suspendu', async ({ page }) => {
  test.setTimeout(10 * 60_000)
  await preparerChaine()

  await test.step('collecte horaire et import quotidien simulés : signaux, inventaire à deux dépôts, impact (COL-09-S1, INV-07-S1, INV-10-S1)', async () => {
    await programmerSources([AVIS_CHAINE.lib, AVIS_CHAINE.nouveau], FORGES_V1)
    // Aucune route ne déclenche ces traitements : seules les échéances planifiées interrogent les sources simulées.
    await expect.poll(async () => {
      const recues = await requetesSources()
      return ['/osv-export/Maven/modified_id.csv', CHAINE.sbomGitlab, CHAINE.sbomGithub].every((c) => recues.includes(`GET ${c}`))
    }, ECHEANCE).toBe(true)
    await expect.poll(async () => (await synthese()).summary?.repositories.filter((d) => d.importedAt).length ?? 0, ECHEANCE).toBe(2)
    expect((await synthese()).summary?.componentCount, 'union des dépôts : lib, commun (une fois) et outil').toBe(3)
    await expect.poll(async () => etatImpact(AVIS_CHAINE.lib.id), ECHEANCE).toBe('OUVERT')
    expect(await etatImpact(AVIS_CHAINE.nouveau.id), 'nouveau absent de l’inventaire : aucun impact').toBeUndefined()

    await seConnecterDepuis(page, `/inventaire/${CHAINE.projet}`, COMPTE_CHAINE)
    const depots = page.getByRole('region', { name: 'Dépôts du projet' })
    await expect(depots.getByRole('row').filter({ hasText: 'e2e/chaine-front' })).toHaveCount(1)
    await expect(depots.getByRole('row').filter({ hasText: 'e2e/chaine-api' })).toHaveCount(1)
    const commun = page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'org.e2e.chaine/commun', exact: true }) })
    await expect(commun, 'composant présent dans les deux dépôts, compté une fois').toHaveCount(1)
    await expect(commun.getByRole('cell', { name: 'e2e/chaine-front, e2e/chaine-api' })).toHaveCount(1)
  })

  await test.step('import d’un nouveau composant : impact créé sans nouvelle collecte (INV-09-S1)', async () => {
    await programmerSources([AVIS_CHAINE.lib, AVIS_CHAINE.nouveau], FORGES_V2)
    await expect.poll(async () => etatImpact(AVIS_CHAINE.nouveau.id), ECHEANCE).toBe('OUVERT')
    const recues = await requetesSources()
    expect(recues.filter((r) => r.startsWith('GET /osv-api/')), 'aucun avis relu : recorrélation sur les avis connus').toEqual([])
  })

  await test.step('confirmation d’un impact par un contributeur dans l’IHM (COR-10-S1)', async () => {
    await expect.poll(async () => alertePour(AVIS_CHAINE.lib), ECHEANCE).toBeDefined()
    const alerte = await alertePour(AVIS_CHAINE.lib)
    await page.goto(`/#/alertes/${alerte}`)
    const impact = page.locator('[data-traitement-impacts] [data-impact]').filter({ hasText: 'Chaîne E2E' })
    await expect(impact.locator('[data-champ="etat"]')).toHaveText('ouvert')
    await impact.getByRole('button', { name: 'Historique et actions' }).click()
    await impact.locator('[data-actions]').getByRole('button', { name: 'Confirmer' }).click()
    await impact.getByLabel('Commentaire').fill('Confirmé en revue de sécurité E2E')
    await impact.locator('form').getByRole('button', { name: 'Confirmer' }).click()
    await expect(impact.locator('[data-champ="etat"]')).toHaveText('confirmé')
    await expect(impact.locator('[data-transition]').last()).toContainText('ouvert → confirmé')
    await expect(impact.locator('[data-transition]').last()).toContainText('Confirmé en revue de sécurité E2E')
    expect(await etatImpact(AVIS_CHAINE.lib.id)).toBe('CONFIRME')
  })

  await test.step('composant retiré par l’import suivant : impact résolu par le système (COR-11-S1, INV-09-S2)', async () => {
    await programmerSources([AVIS_CHAINE.lib, AVIS_CHAINE.nouveau], FORGES_V3)
    await expect.poll(async () => etatImpact(AVIS_CHAINE.lib.id), ECHEANCE).toBe('RESOLU')
    expect((await impactsChaine()).map((i) => i.signalExternalId), 'liste par défaut : impacts ouverts et confirmés')
      .toEqual([AVIS_CHAINE.nouveau.id])
    // La session vit en mémoire (jetons hors du stockage) : rechargement par la navigation de l'application.
    const alerte = await alertePour(AVIS_CHAINE.lib)
    await page.goto('/#/alertes')
    await page.goto(`/#/alertes/${alerte}`)
    const impact = page.locator('[data-traitement-impacts] [data-impact]').filter({ hasText: 'Chaîne E2E' })
    await expect(impact.locator('[data-champ="etat"]')).toHaveText('résolu')
    await impact.getByRole('button', { name: 'Historique et actions' }).click()
    const derniere = impact.locator('[data-transition]').last()
    await expect(derniere).toContainText('confirmé → résolu')
    await expect(derniere).toContainText('par système')
    await expect(derniere).toContainText('composant retiré de l’inventaire')
  })

  await test.step('abonnement suspendu : critères conservés, aucune alerte (ABO-07-S1)', async () => {
    const avant = await abonnementDeGina()
    await page.goto('/#/veille')
    await expect(ligneAbonnement(page)).toContainText('Actif')
    await ligneAbonnement(page).getByRole('button', { name: 'Suspendre' }).click()
    await expect(ligneAbonnement(page)).toContainText('Suspendu')
    const suspendu = await abonnementDeGina()
    expect(suspendu.state).toBe('SUSPENDED')
    expect([suspendu.filters, suspendu.channel, suspendu.frequency], 'critères, canal et fréquence inchangés')
      .toEqual([avant.filters, avant.channel, avant.frequency])

    await programmerSources([AVIS_CHAINE.lib, AVIS_CHAINE.nouveau, AVIS_CHAINE.suspendu], FORGES_V3)
    await expect.poll(async () => etatImpact(AVIS_CHAINE.suspendu.id), ECHEANCE).toBe('OUVERT')
    // Les alertes sont produites à l'enregistrement des impacts : un délai court suffit à constater leur absence.
    await page.waitForTimeout(5_000)
    expect(await alertePour(AVIS_CHAINE.suspendu), 'aucune alerte au titre de l’abonnement suspendu').toBeUndefined()
  })

  await test.step('abonnement réactivé : une alerte pour le nouvel avis seulement (ABO-07-S2)', async () => {
    await ligneAbonnement(page).getByRole('button', { name: 'Réactiver' }).click()
    await expect(ligneAbonnement(page)).toContainText('Actif')
    expect((await abonnementDeGina()).state).toBe('ACTIVE')

    await programmerSources([AVIS_CHAINE.lib, AVIS_CHAINE.nouveau, AVIS_CHAINE.suspendu, AVIS_CHAINE.reactive], FORGES_V3)
    await expect.poll(async () => alertePour(AVIS_CHAINE.reactive), ECHEANCE).toBeDefined()
    expect(await alertePour(AVIS_CHAINE.suspendu), 'aucune alerte pour l’avis corrélé pendant la suspension').toBeUndefined()
  })
})
