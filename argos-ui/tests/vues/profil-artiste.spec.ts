// Vue du profil artiste (ART-07) : enregistrement des suivis, message « suivis requis », opportunité de l'adresse
// mise en avant avec son résumé, sources vérifiées pour l'administrateur plateforme.
import { afterEach, describe, expect, it } from 'vitest'
import { etatSession } from '../../src/session/session'
import type { VueWrapper } from '@vue/test-utils'
import { appelsVers, erreurApi, monter, simulerApi, soumettre, texte, type AppelApi, type TableApi } from './support'

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

const SUIVIS_REQUIS = { suivisRequis: true, message: MESSAGE_SUIVIS, items: [] }
const SUIVIS_COMPLETS = { ...SANS_SUIVI, disciplines: ['musique'], territoires: ['bretagne'], complets: true }
const ADMIN_PLATEFORME = { id: 'moi', nomAffiche: null, email: null, adminPlateforme: true, creeLe: '2026-01-01T00:00:00Z' }

/** API dont les opportunités ne sont restituées qu'après l'enregistrement des suivis. */
function enregistrementDesSuivis(): TableApi {
  let enregistres = false
  return {
    ...lectures(SANS_SUIVI, SUIVIS_REQUIS),
    [`GET ${BASE}/opportunites`]: () => ({ corps: enregistres ? OPPORTUNITES : SUIVIS_REQUIS }),
    [`PUT ${BASE}/suivis`]: (appel) => {
      enregistres = true
      return { corps: { ...SANS_SUIVI, ...(appel.corps as object), complets: true } }
    },
  }
}

/** API d'un administrateur plateforme : une source vérifiée et une source à vérifier. */
function registreDesSources(): TableApi {
  const source = { profil: 'artiste', type: 'FLUX', adresse: 'https://example.org/flux', rubrique: 'residences' }
  const conditions = { licence: 'CC-BY', etat: 'ACTIVE', conditionsUrl: 'https://example.org/cgu' }
  const verifiee = { ...source, ...conditions, id: 'a', libelle: 'Flux vérifié' }
  const aVerifier = { ...source, id: 'b', libelle: 'Flux à vérifier', licence: null, etat: 'CONDITIONS_A_VERIFIER', conditionsUrl: null }
  return { ...lectures(SANS_SUIVI, SUIVIS_REQUIS), [`GET ${BASE}/sources`]: { corps: [verifiee, aVerifier] } }
}

/** Monte la vue d'une opportunité dont le résumé est `resume`. */
async function monterOpportunite(resume: unknown): Promise<{ wrapper: VueWrapper; appels: AppelApi[] }> {
  const appels = simulerApi({
    ...lectures(SUIVIS_COMPLETS, OPPORTUNITES),
    [`GET ${BASE}/opportunites/${OPPORTUNITE_ID}/resume`]: { corps: resume },
  })
  const { wrapper } = await monter(`/profils/artiste/opportunites/${OPPORTUNITE_ID}`)
  return { wrapper, appels }
}

/** L'opportunité de l'adresse est mise en avant avec son résumé, ses mentions et son lien officiel. */
function verifierMiseEnAvant(wrapper: VueWrapper): void {
  const enAvant = wrapper.get('[data-opportunite-en-avant]')
  expect(enAvant.get('h2').text()).toBe('Résidence d’écriture')
  expect(enAvant.get('[data-mention="ia"]').text()).toBe('produit par l’IA')
  expect(enAvant.get('[data-mention="eligibilite"]').text()).toContain('ne se prononce pas')
  expect(enAvant.get('a[target="_blank"]').attributes('href')).toBe('https://example.org/residence')
  expect(wrapper.get(`[data-opportunite="${OPPORTUNITE_ID}"]`).attributes('aria-current')).toBe('true')
}

afterEach(() => {
  etatSession.compte = null
})

describe('ProfilArtisteView : suivis', () => {
  it('sans suivi, affiche le message « suivis requis » sans liste', async () => {
    simulerApi(lectures(SANS_SUIVI, SUIVIS_REQUIS))
    const { wrapper } = await monter('/profils/artiste')
    expect(texte(wrapper, '[data-suivis-requis]')).toBe(MESSAGE_SUIVIS)
    expect(wrapper.find('[data-liste="opportunites"]').exists()).toBe(false)
    expect(texte(wrapper, '[data-liste="rubriques"]')).toContain('Résidences')
  })

  it('enregistre les suivis choisis puis affiche les opportunités rapprochées', async () => {
    const appels = simulerApi(enregistrementDesSuivis())
    const { wrapper } = await monter('/profils/artiste')
    await wrapper.get('input[name="discipline"][value="musique"]').setValue(true)
    await wrapper.get('input[name="territoire"][value="bretagne"]').setValue(true)
    await soumettre(wrapper, '[data-formulaire="suivis"]')
    expect(appelsVers(appels, 'PUT', `${BASE}/suivis`)[0].corps).toEqual({ disciplines: ['musique'], territoires: ['bretagne'] })
    expect(texte(wrapper, '[data-issue="suivis"]')).toBe('Suivis enregistrés.')
    expect(wrapper.find('[data-suivis-requis]').exists()).toBe(false)
    expect(texte(wrapper, '[data-liste="opportunites"]')).toContain('Concours de composition')
  })

  it('affiche le refus des suivis renvoyé par le serveur', async () => {
    simulerApi({
      [`GET ${BASE}/listes`]: { corps: LISTES },
      [`GET ${BASE}/suivis`]: erreurApi(409, 'Activez le profil artiste pour choisir vos suivis.', 'PROFIL_INACTIF'),
      [`GET ${BASE}/opportunites`]: erreurApi(409, 'Activez le profil artiste.', 'PROFIL_INACTIF'),
      'GET /api/v1/preferences/rubriques': { corps: [] },
    })
    const { wrapper } = await monter('/profils/artiste')
    expect(texte(wrapper, '[data-erreur="suivis"]')).toContain('Activez le profil artiste pour choisir vos suivis.')
    expect(texte(wrapper, '[data-erreur="opportunites"]')).toBe('Activez le profil artiste.')
  })
})

describe('ProfilArtisteView : opportunité de l’adresse', () => {
  it('met en avant l’opportunité avec son résumé, liens dangereux non rendus', async () => {
    const { wrapper, appels } = await monterOpportunite({ ...RESUME, mentionIa: 'produit par l’IA' })
    expect(appelsVers(appels, 'GET', `${BASE}/opportunites/${OPPORTUNITE_ID}/resume`)).toHaveLength(1)
    verifierMiseEnAvant(wrapper)
    expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false)
  })

  it('sans mention de l’IA, le résumé n’est pas marqué « produit par l’IA »', async () => {
    const { wrapper } = await monterOpportunite(RESUME)
    expect(wrapper.find('[data-resume]').exists()).toBe(true)
    expect(wrapper.find('[data-mention="ia"]').exists()).toBe(false)
  })
})

describe('ProfilArtisteView : sources', () => {
  it('affiche les seules sources vérifiées à l’administrateur plateforme', async () => {
    etatSession.compte = ADMIN_PLATEFORME
    simulerApi(registreDesSources())
    const { wrapper } = await monter('/profils/artiste')
    expect(texte(wrapper, '[data-liste="sources"]')).toContain('Flux vérifié')
    expect(texte(wrapper, '[data-liste="sources"]')).not.toContain('Flux à vérifier')
  })
})
