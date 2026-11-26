// Vue radar d'un espace (RAD-09) : consultation par un lecteur sans commande de décision, décision par
// l'administrateur envoyée au serveur.
import { describe, expect, it } from 'vitest'
import type { VueWrapper } from '@vue/test-utils'
import { appelsVers, attendre, cliquer, erreurApi, monter, simulerApi, texte, type TableApi } from './support'

const ESPACE_ID = '13131313-1313-4131-8131-131313131313'
const SUGGESTION_ID = '14141414-1414-4141-8141-141414141414'
const BASE = `/api/v1/radar/espaces/${ESPACE_ID}`

const RADAR = {
  horizon: '30j',
  depuis: '2026-11-26',
  jusqua: '2026-12-26',
  echeances: [
    {
      cle: 'postgresql',
      libelle: 'PostgreSQL',
      version: '14',
      nature: 'FIN_DE_SUPPORT',
      date: '2026-12-01',
      source: 'endoflife',
      note: null,
    },
  ],
  sources: [],
}
const STANDARD = {
  id: 's1',
  espaceId: ESPACE_ID,
  cle: 'postgresql',
  libelle: 'PostgreSQL',
  statut: 'ADOPT',
  versionRecommandee: '14',
  responsable: 'Alice',
  justification: null,
  dateRevue: '2027-01-01',
  produitCycleVie: 'postgresql',
  modifieLe: '2026-11-01T00:00:00Z',
}
const SUGGESTION = {
  id: SUGGESTION_ID,
  espaceId: ESPACE_ID,
  cle: 'postgresql',
  statut: 'HOLD',
  versionRecommandee: '14',
  justification: 'Fin de support échue.',
  origine: 'IA',
  etat: 'EN_ATTENTE',
  creeLe: '2026-11-26T00:00:00Z',
  decideLe: null,
}
const DETTE = {
  id: 'd1',
  projetId: '15151515-1515-4151-8151-151515151515',
  projet: 'Portail',
  cle: 'postgresql',
  version: '12',
  cycle: '12',
  finSupport: '2024-11-14',
  versionStandard: '14',
  source: 'endoflife',
  creeLe: '2026-11-01T00:00:00Z',
}

function lectures(role: string): TableApi {
  return {
    [`GET /api/v1/espaces/${ESPACE_ID}`]: { corps: { id: ESPACE_ID, nom: 'Plateforme', role, creeLe: '2026-01-01T00:00:00Z' } },
    [`GET ${BASE}/echeances`]: { corps: RADAR },
    [`GET ${BASE}/dettes`]: { corps: [DETTE] },
    [`GET ${BASE}/standards`]: { corps: [STANDARD] },
    [`GET ${BASE}/suggestions`]: { corps: [SUGGESTION] },
  }
}

const ADRESSE_RADAR = `/espaces/${ESPACE_ID}/radar`

/** API d'un administrateur : accepter la suggestion l'applique au référentiel relu ensuite. */
function decisionAcceptee(): TableApi {
  let standardApplique = false
  return {
    ...lectures('ADMINISTRATEUR'),
    [`GET ${BASE}/standards`]: () => ({ corps: [standardApplique ? { ...STANDARD, statut: 'HOLD' } : STANDARD] }),
    [`POST ${BASE}/suggestions/${SUGGESTION_ID}/decision`]: () => {
      standardApplique = true
      return { corps: [{ ...SUGGESTION, etat: 'ACCEPTEE', decideLe: '2026-11-26T09:00:00Z' }] }
    },
  }
}

/** Toutes les lectures refusées : espace inconnu ou dont l'utilisateur n'est pas membre. */
function espaceRefuse(): TableApi {
  const refus = erreurApi(404, 'Espace introuvable.')
  const sections = ['echeances', 'dettes', 'standards', 'suggestions'].map((suite) => `${BASE}/${suite}`)
  const chemins = [`/api/v1/espaces/${ESPACE_ID}`, ...sections]
  return Object.fromEntries(chemins.map((chemin) => [`GET ${chemin}`, refus]))
}

/** Les quatre sections du radar sont affichées avec les données de l'espace. */
function verifierSections(wrapper: VueWrapper): void {
  expect(texte(wrapper, 'h1')).toBe('Radar de l’espace Plateforme')
  expect(texte(wrapper, '[data-liste="echeances"]')).toContain('PostgreSQL 14 · fin de support')
  expect(texte(wrapper, '[data-liste="dettes"]')).toContain('Portail')
  expect(texte(wrapper, '[data-standard="postgresql"]')).toContain('ADOPT')
  expect(texte(wrapper, `[data-suggestion="${SUGGESTION_ID}"]`)).toContain('passer en HOLD')
}

/** Après l'acceptation : suggestion retirée des attentes, issue annoncée, référentiel au statut suggéré. */
function verifierDecisionAppliquee(wrapper: VueWrapper): void {
  expect(wrapper.find(`[data-suggestion="${SUGGESTION_ID}"]`).exists()).toBe(false)
  expect(texte(wrapper, '[data-issue="decision"]')).toBe('Suggestion acceptée pour postgresql.')
  expect(texte(wrapper, '[data-standard="postgresql"]')).toContain('HOLD')
}

describe('RadarView : consultation et décision', () => {
  it('un lecteur voit échéances, dettes, référentiel et suggestions, sans commande de décision', async () => {
    const appels = simulerApi(lectures('LECTEUR'))
    const { wrapper } = await monter(ADRESSE_RADAR)
    expect(appelsVers(appels, 'GET', `${BASE}/echeances`)[0].chemin).toContain('horizon=30j')
    verifierSections(wrapper)
    expect(wrapper.find('[data-decision]').exists()).toBe(false)
  })

  it('l’administrateur accepte une suggestion : décision envoyée, liste et référentiel relus', async () => {
    const appels = simulerApi(decisionAcceptee())
    const { wrapper } = await monter(ADRESSE_RADAR)
    await cliquer(wrapper, '[data-decision="ACCEPTER"]')
    expect(appelsVers(appels, 'POST', `${BASE}/suggestions/${SUGGESTION_ID}/decision`)[0].corps).toEqual({ decision: 'ACCEPTER' })
    verifierDecisionAppliquee(wrapper)
  })
})

describe('RadarView : horizons et erreurs', () => {
  it('change d’horizon et affiche l’erreur d’une section sans masquer les autres', async () => {
    const appels = simulerApi({ ...lectures('LECTEUR'), [`GET ${BASE}/dettes`]: erreurApi(500, 'Dettes illisibles.') })
    const { wrapper } = await monter(ADRESSE_RADAR)
    expect(wrapper.text()).toContain('Dettes illisibles.')
    expect(wrapper.find('[data-liste="echeances"]').exists()).toBe(true)
    await wrapper.get('#horizon-radar').setValue('6m')
    await attendre()
    expect(appelsVers(appels, 'GET', `${BASE}/echeances`).map((appel) => appel.chemin.split('=')[1])).toEqual(['30j', '6m'])
  })

  it('affiche le refus d’un espace dont l’utilisateur n’est pas membre', async () => {
    simulerApi(espaceRefuse())
    const { wrapper } = await monter(ADRESSE_RADAR)
    expect(texte(wrapper, '[data-erreur="espace"]')).toBe('Espace introuvable.')
  })
})
