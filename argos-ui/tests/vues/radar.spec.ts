// Vue radar d'un espace (RAD-09) : consultation par un lecteur sans commande de décision, décision par
// l'administrateur envoyée au serveur.
import { describe, expect, it } from 'vitest'
import { appelsVers, attendre, erreurApi, monter, simulerApi, type TableApi } from './support'

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

describe('RadarView', () => {
  it('un lecteur voit échéances, dettes, référentiel et suggestions, sans commande de décision', async () => {
    const appels = simulerApi(lectures('LECTEUR'))
    const { wrapper } = await monter(`/espaces/${ESPACE_ID}/radar`)
    expect(wrapper.get('h1').text()).toBe('Radar de l’espace Plateforme')
    expect(appelsVers(appels, 'GET', `${BASE}/echeances`)[0].chemin).toContain('horizon=30j')
    expect(wrapper.get('[data-liste="echeances"]').text()).toContain('PostgreSQL 14 · fin de support')
    expect(wrapper.get('[data-liste="dettes"]').text()).toContain('Portail')
    expect(wrapper.get('[data-standard="postgresql"]').text()).toContain('ADOPT')
    expect(wrapper.get(`[data-suggestion="${SUGGESTION_ID}"]`).text()).toContain('passer en HOLD')
    expect(wrapper.find('[data-decision]').exists()).toBe(false)
  })

  it('l’administrateur accepte une suggestion : décision envoyée, liste et référentiel relus', async () => {
    let standardApplique = false
    const appels = simulerApi({
      ...lectures('ADMINISTRATEUR'),
      [`GET ${BASE}/standards`]: () => ({ corps: [standardApplique ? { ...STANDARD, statut: 'HOLD' } : STANDARD] }),
      [`POST ${BASE}/suggestions/${SUGGESTION_ID}/decision`]: () => {
        standardApplique = true
        return { corps: [{ ...SUGGESTION, etat: 'ACCEPTEE', decideLe: '2026-11-26T09:00:00Z' }] }
      },
    })
    const { wrapper } = await monter(`/espaces/${ESPACE_ID}/radar`)
    await wrapper.get('[data-decision="ACCEPTER"]').trigger('click')
    await attendre()
    expect(appelsVers(appels, 'POST', `${BASE}/suggestions/${SUGGESTION_ID}/decision`)[0].corps).toEqual({ decision: 'ACCEPTER' })
    expect(wrapper.find(`[data-suggestion="${SUGGESTION_ID}"]`).exists()).toBe(false)
    expect(wrapper.get('[data-issue="decision"]').text()).toBe('Suggestion acceptée pour postgresql.')
    expect(wrapper.get('[data-standard="postgresql"]').text()).toContain('HOLD')
  })

  it('change d’horizon et affiche l’erreur d’une section sans masquer les autres', async () => {
    const appels = simulerApi({ ...lectures('LECTEUR'), [`GET ${BASE}/dettes`]: erreurApi(500, 'Dettes illisibles.') })
    const { wrapper } = await monter(`/espaces/${ESPACE_ID}/radar`)
    expect(wrapper.text()).toContain('Dettes illisibles.')
    expect(wrapper.find('[data-liste="echeances"]').exists()).toBe(true)
    await wrapper.get('#horizon-radar').setValue('6m')
    await attendre()
    expect(appelsVers(appels, 'GET', `${BASE}/echeances`).map((appel) => appel.chemin.split('=')[1])).toEqual(['30j', '6m'])
  })

  it('affiche le refus d’un espace dont l’utilisateur n’est pas membre', async () => {
    const refus = erreurApi(404, 'Espace introuvable.')
    simulerApi({
      [`GET /api/v1/espaces/${ESPACE_ID}`]: refus,
      [`GET ${BASE}/echeances`]: refus,
      [`GET ${BASE}/dettes`]: refus,
      [`GET ${BASE}/standards`]: refus,
      [`GET ${BASE}/suggestions`]: refus,
    })
    const { wrapper } = await monter(`/espaces/${ESPACE_ID}/radar`)
    expect(wrapper.get('[data-erreur="espace"]').text()).toBe('Espace introuvable.')
  })
})
