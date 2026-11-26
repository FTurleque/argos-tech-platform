// Vue du profil artiste (ART-07) : enregistrement des suivis, message « suivis requis », opportunité de l'adresse
// mise en avant avec son résumé, sources vérifiées pour l'administrateur plateforme.
import { afterEach, describe, expect, it } from 'vitest'
import { etatSession } from '../../src/session/session'
import { appelsVers, attendre, erreurApi, monter, simulerApi, type TableApi } from './support'

const OPPORTUNITE_ID = '16161616-1616-4161-8161-161616161616'
const AUTRE_ID = '17171717-1717-4171-8171-171717171717'
const BASE = '/api/v1/profils/artiste'
const MESSAGE_SUIVIS = 'Choisissez au moins une discipline et un territoire pour recevoir les opportunités du profil artiste.'

const LISTES = {
  disciplines: [
    { identifiant: 'musique', libelle: 'Musique' },
    { identifiant: 'danse', libelle: 'Danse' },
  ],
  territoires: [{ identifiant: 'bretagne', libelle: 'Bretagne' }],
  rappelsJoursParDefaut: [7, 1],
}
const SANS_SUIVI = { disciplines: [], territoires: [], rappelsJours: [7, 1], rappelsParDefaut: true, complets: false }
const RESUME = {
  titre: 'Résidence d’écriture',
  organisme: 'Région Bretagne',
  criteres: 'Artistes résidant en Bretagne.',
  criteresTronques: false,
  dateLimite: '2026-12-15',
  texteOfficiel: 'https://example.org/residence',
  mention: 'Argos ne se prononce pas sur votre éligibilité : reportez-vous au texte officiel de l’organisme.',
}

function opportunite(id: string, titre: string) {
  return {
    id,
    rubrique: 'residences',
    titre,
    organisme: 'Région Bretagne',
    dateLimite: '2026-12-15',
    dateAConfirmer: false,
    lienOfficiel: 'javascript:alert(1)',
    disciplines: ['musique'],
    territoires: ['bretagne'],
    explication: 'Discipline et territoire suivis.',
    resume: RESUME,
  }
}

const OPPORTUNITES = {
  suivisRequis: false,
  message: null,
  items: [opportunite(AUTRE_ID, 'Concours de composition'), opportunite(OPPORTUNITE_ID, 'Résidence d’écriture')],
}

function lectures(suivis: unknown, opportunites: unknown): TableApi {
  return {
    [`GET ${BASE}/listes`]: { corps: LISTES },
    [`GET ${BASE}/suivis`]: { corps: suivis },
    [`GET ${BASE}/opportunites`]: { corps: opportunites },
    'GET /api/v1/preferences/rubriques': { corps: [{ profil: 'artiste', identifiant: 'residences', libelle: 'Résidences' }] },
  }
}

describe('ProfilArtisteView', () => {
  afterEach(() => {
    etatSession.compte = null
  })

  it('sans suivi, affiche le message « suivis requis » sans liste', async () => {
    simulerApi(lectures(SANS_SUIVI, { suivisRequis: true, message: MESSAGE_SUIVIS, items: [] }))
    const { wrapper } = await monter('/profils/artiste')
    expect(wrapper.get('[data-suivis-requis]').text()).toBe(MESSAGE_SUIVIS)
    expect(wrapper.find('[data-liste="opportunites"]').exists()).toBe(false)
    expect(wrapper.get('[data-liste="rubriques"]').text()).toContain('Résidences')
  })

  it('enregistre les suivis choisis puis affiche les opportunités rapprochées', async () => {
    let enregistres = false
    const appels = simulerApi({
      ...lectures(SANS_SUIVI, { suivisRequis: true, message: MESSAGE_SUIVIS, items: [] }),
      [`GET ${BASE}/opportunites`]: () => ({
        corps: enregistres ? OPPORTUNITES : { suivisRequis: true, message: MESSAGE_SUIVIS, items: [] },
      }),
      [`PUT ${BASE}/suivis`]: (appel) => {
        enregistres = true
        return { corps: { ...SANS_SUIVI, ...(appel.corps as object), complets: true } }
      },
    })
    const { wrapper } = await monter('/profils/artiste')
    await wrapper.get('input[name="discipline"][value="musique"]').setValue(true)
    await wrapper.get('input[name="territoire"][value="bretagne"]').setValue(true)
    await wrapper.get('[data-formulaire="suivis"]').trigger('submit')
    await attendre()
    expect(appelsVers(appels, 'PUT', `${BASE}/suivis`)[0].corps).toEqual({ disciplines: ['musique'], territoires: ['bretagne'] })
    expect(wrapper.get('[data-issue="suivis"]').text()).toBe('Suivis enregistrés.')
    expect(wrapper.find('[data-suivis-requis]').exists()).toBe(false)
    expect(wrapper.get('[data-liste="opportunites"]').text()).toContain('Concours de composition')
  })

  it('affiche le refus des suivis renvoyé par le serveur', async () => {
    simulerApi({
      [`GET ${BASE}/listes`]: { corps: LISTES },
      [`GET ${BASE}/suivis`]: erreurApi(409, 'Activez le profil artiste pour choisir vos suivis.', 'PROFIL_INACTIF'),
      [`GET ${BASE}/opportunites`]: erreurApi(409, 'Activez le profil artiste.', 'PROFIL_INACTIF'),
      'GET /api/v1/preferences/rubriques': { corps: [] },
    })
    const { wrapper } = await monter('/profils/artiste')
    expect(wrapper.get('[data-erreur="suivis"]').text()).toContain('Activez le profil artiste pour choisir vos suivis.')
    expect(wrapper.get('[data-erreur="opportunites"]').text()).toBe('Activez le profil artiste.')
  })

  it('met en avant l’opportunité de l’adresse avec son résumé, liens dangereux non rendus', async () => {
    const appels = simulerApi({
      ...lectures({ ...SANS_SUIVI, disciplines: ['musique'], territoires: ['bretagne'], complets: true }, OPPORTUNITES),
      [`GET ${BASE}/opportunites/${OPPORTUNITE_ID}/resume`]: { corps: { ...RESUME, mentionIa: 'produit par l’IA' } },
    })
    const { wrapper } = await monter(`/profils/artiste/opportunites/${OPPORTUNITE_ID}`)
    expect(appelsVers(appels, 'GET', `${BASE}/opportunites/${OPPORTUNITE_ID}/resume`)).toHaveLength(1)
    const enAvant = wrapper.get('[data-opportunite-en-avant]')
    expect(enAvant.get('h2').text()).toBe('Résidence d’écriture')
    expect(enAvant.get('[data-mention="ia"]').text()).toBe('produit par l’IA')
    expect(enAvant.get('[data-mention="eligibilite"]').text()).toContain('ne se prononce pas')
    expect(enAvant.get('a[target="_blank"]').attributes('href')).toBe('https://example.org/residence')
    expect(wrapper.get(`[data-opportunite="${OPPORTUNITE_ID}"]`).attributes('aria-current')).toBe('true')
    expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false)
  })

  it('sans mention de l’IA, le résumé n’est pas marqué « produit par l’IA »', async () => {
    simulerApi({
      ...lectures({ ...SANS_SUIVI, complets: true }, OPPORTUNITES),
      [`GET ${BASE}/opportunites/${OPPORTUNITE_ID}/resume`]: { corps: RESUME },
    })
    const { wrapper } = await monter(`/profils/artiste/opportunites/${OPPORTUNITE_ID}`)
    expect(wrapper.find('[data-resume]').exists()).toBe(true)
    expect(wrapper.find('[data-mention="ia"]').exists()).toBe(false)
  })

  it('affiche les seules sources vérifiées à l’administrateur plateforme', async () => {
    etatSession.compte = { id: 'moi', nomAffiche: null, email: null, adminPlateforme: true, creeLe: '2026-01-01T00:00:00Z' }
    const source = { profil: 'artiste', type: 'FLUX', adresse: 'https://example.org/flux', rubrique: 'residences' }
    simulerApi({
      ...lectures(SANS_SUIVI, { suivisRequis: true, message: MESSAGE_SUIVIS, items: [] }),
      [`GET ${BASE}/sources`]: {
        corps: [
          { ...source, id: 'a', libelle: 'Flux vérifié', licence: 'CC-BY', etat: 'ACTIVE', conditionsUrl: 'https://example.org/cgu' },
          { ...source, id: 'b', libelle: 'Flux à vérifier', licence: null, etat: 'CONDITIONS_A_VERIFIER', conditionsUrl: null },
        ],
      },
    })
    const { wrapper } = await monter('/profils/artiste')
    expect(wrapper.get('[data-liste="sources"]').text()).toContain('Flux vérifié')
    expect(wrapper.get('[data-liste="sources"]').text()).not.toContain('Flux à vérifier')
  })
})
