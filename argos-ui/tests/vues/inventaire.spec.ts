// Vue d'inventaire (INV-01, INV-06, INV-10) et alimentation de l'inventaire (INV-12, INV-13, PIN-06) : appels
// envoyés par les formulaires et issue affichée.
import { describe, expect, it } from 'vitest'
import { appelsVers, attendre, choisirFichiers, erreurApi, monter, simulerApi, type TableApi } from './support'

const PROJET_ID = '55555555-5555-4555-8555-555555555555'
const NOUVEAU_PROJET_ID = '66666666-6666-4666-8666-666666666666'
const ESPACE_ID = '77777777-7777-4777-8777-777777777777'
const DEPOT_ID = '88888888-8888-4888-8888-888888888888'
const BASE_PROJET = `/api/v1/projects/${PROJET_ID}`

const ESPACES = [
  { id: ESPACE_ID, nom: 'Équipe plateforme', role: 'ADMINISTRATEUR', creeLe: '2026-01-01T00:00:00Z' },
  { id: '99999999-9999-4999-8999-999999999999', nom: 'Lecture', role: 'LECTEUR', creeLe: '2026-01-01T00:00:00Z' },
]
const PROJETS = {
  items: [{ id: PROJET_ID, workspaceId: ESPACE_ID, workspaceName: 'Équipe plateforme', name: 'Portail' }],
  page: 0,
  size: 200,
  total: 1,
}
const SYNTHESE = {
  projectId: PROJET_ID,
  status: 'AVAILABLE',
  summary: {
    projectId: PROJET_ID,
    origin: 'SBOM',
    sourceDate: '2026-11-20T00:00:00Z',
    ageDays: 6,
    obsolete: false,
    importedAt: '2026-11-20T01:00:00Z',
    componentCount: 0,
    ignoredCount: 0,
    ignoredComponents: [],
    repositories: [
      {
        id: DEPOT_ID,
        provider: 'GITHUB',
        repository: 'acme/portail',
        branch: 'main',
        principal: true,
        origin: 'SBOM',
        sourceDate: '2026-11-20T00:00:00Z',
        importedAt: '2026-11-20T01:00:00Z',
        lastFailure: null,
      },
    ],
  },
}
const ACTIVITE = {
  projectId: PROJET_ID,
  periode: { debut: '2026-10-27', fin: '2026-11-26', jours: 30 },
  compteurs: {
    ticketsOuverts: 4,
    ticketsFermes: 3,
    fusionsOuvertes: 2,
    fusionsFusionnees: 1,
    pipelinesReussis: 10,
    pipelinesEchoues: 1,
    pipelinesAutres: 0,
    versionsPubliees: 1,
  },
  jalons: [],
  fraicheur: {
    etat: 'A_JOUR',
    derniereMiseAJour: '2026-11-26T08:00:00Z',
    prochaineCollecte: null,
    complet: true,
    donneesDepuis: null,
    motif: null,
  },
}
const IMPORT_REUSSI = { status: 'IMPORTED', origin: 'SBOM', components: 12, ignored: 1, reason: null, ignoredComponents: [] }

/** Lectures de la vue pour le projet affiché. */
function lectures(): TableApi {
  return {
    'GET /api/v1/projects': { corps: PROJETS },
    'GET /api/v1/espaces': { corps: ESPACES },
    [`GET ${BASE_PROJET}/inventory/summary`]: { corps: SYNTHESE },
    [`GET ${BASE_PROJET}/inventory/components`]: { corps: { projectId: PROJET_ID, items: [], page: 0, size: 20, total: 0 } },
    [`GET ${BASE_PROJET}/activite`]: { corps: ACTIVITE },
  }
}

describe('InventaireView', () => {
  it('liste les projets et crée un projet avec son dépôt dans un espace administré', async () => {
    const appels = simulerApi({
      ...lectures(),
      'POST /api/v1/projects': { statut: 201, corps: { id: NOUVEAU_PROJET_ID, espaceId: ESPACE_ID, nom: 'Nouveau' } },
      [`GET /api/v1/projects/${NOUVEAU_PROJET_ID}/inventory/summary`]: {
        corps: { projectId: NOUVEAU_PROJET_ID, status: 'NO_SBOM', summary: null },
      },
      [`GET /api/v1/projects/${NOUVEAU_PROJET_ID}/activite`]: { corps: { ...ACTIVITE, projectId: NOUVEAU_PROJET_ID } },
    })
    const { wrapper, router } = await monter('/inventaire')
    expect(wrapper.findAll('#choix-projet option').map((option) => option.text())).toContain('Portail — Équipe plateforme')
    expect(wrapper.findAll('#creation-espace option').map((option) => option.text())).toEqual([
      '— Choisir un espace —',
      'Équipe plateforme',
    ])
    expect(wrapper.find(`a[href="/espaces/${ESPACE_ID}/radar"]`).exists()).toBe(true)
    await wrapper.get('#creation-nom').setValue('  Nouveau  ')
    await wrapper.get('#creation-depot-forge').setValue('GITLAB')
    await wrapper.get('#creation-depot-depot').setValue('acme/nouveau')
    await wrapper.get('[data-formulaire="creation-projet"]').trigger('submit')
    await attendre()
    expect(appelsVers(appels, 'POST', '/api/v1/projects')[0].corps).toEqual({
      espaceId: ESPACE_ID,
      nom: 'Nouveau',
      depot: { forge: 'GITLAB', depot: 'acme/nouveau' },
    })
    expect(wrapper.get('[data-issue="creation-projet"]').text()).toBe('Projet « Nouveau » créé.')
    expect(router.currentRoute.value.fullPath).toBe(`/inventaire/${NOUVEAU_PROJET_ID}`)
  })

  it('affiche le refus de la création renvoyé par le serveur', async () => {
    simulerApi({ ...lectures(), 'POST /api/v1/projects': erreurApi(400, 'Le nom du projet est obligatoire.', 'DONNEE_INVALIDE') })
    const { wrapper } = await monter('/inventaire')
    await wrapper.get('#creation-nom').setValue('Projet')
    await wrapper.get('[data-formulaire="creation-projet"]').trigger('submit')
    await attendre()
    expect(wrapper.get('[data-erreur="creation-projet"]').text()).toBe('Le nom du projet est obligatoire.')
  })

  it('affiche l’échec de la lecture des projets', async () => {
    simulerApi({ 'GET /api/v1/projects': erreurApi(500, 'Panne.'), 'GET /api/v1/espaces': { corps: [] } })
    const { wrapper } = await monter('/inventaire')
    expect(wrapper.text()).toContain('Recharger la liste')
  })

  it('importe une SBOM choisie et affiche l’issue', async () => {
    const appels = simulerApi({ ...lectures(), [`POST ${BASE_PROJET}/inventory/sbom`]: { corps: IMPORT_REUSSI } })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    const document = { bomFormat: 'CycloneDX', specVersion: '1.5', components: [] }
    await choisirFichiers(wrapper, '#fichier-sbom', [new File([JSON.stringify(document)], 'sbom.json')])
    await wrapper.get('[data-formulaire="sbom"]').trigger('submit')
    await attendre()
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/inventory/sbom`)[0].corps).toEqual(document)
    expect(wrapper.get('[data-issue="alimentation"]').text()).toBe(
      'SBOM : Inventaire importé (12 composant(s) inventorié(s), 1 écarté(s) faute de PURL).',
    )
  })

  it('refuse localement un fichier SBOM qui n’est pas du JSON, sans appel', async () => {
    const appels = simulerApi(lectures())
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await choisirFichiers(wrapper, '#fichier-sbom', [new File(['<bom/>'], 'sbom.xml')])
    await wrapper.get('[data-formulaire="sbom"]').trigger('submit')
    await attendre()
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/inventory/sbom`)).toHaveLength(0)
    expect(wrapper.get('[data-erreur="alimentation"]').text()).toContain('sbom.xml')
  })

  it('importe des manifestes et affiche le refus du serveur', async () => {
    const appels = simulerApi({
      ...lectures(),
      [`POST ${BASE_PROJET}/inventory/manifests`]: erreurApi(403, 'Rôle contributeur requis.', 'ROLE_INSUFFISANT'),
    })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await choisirFichiers(wrapper, '#fichiers-manifestes', [new File(['{"name":"a"}'], 'package.json')])
    await wrapper.get('[data-formulaire="manifestes"]').trigger('submit')
    await attendre()
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/inventory/manifests`)[0].corps).toEqual({
      manifestes: [{ chemin: 'package.json', contenu: '{"name":"a"}' }],
    })
    expect(wrapper.get('[data-erreur="alimentation"]').text()).toBe('Rôle contributeur requis.')
  })

  it('rattache, détache un dépôt et relance l’import', async () => {
    const appels = simulerApi({
      ...lectures(),
      [`POST ${BASE_PROJET}/repositories`]: { statut: 201, corps: { id: 'nouveau', principal: false } },
      [`DELETE ${BASE_PROJET}/repositories/${DEPOT_ID}`]: { statut: 204 },
      [`POST ${BASE_PROJET}/inventory/refresh`]: {
        corps: {
          projectId: PROJET_ID,
          depots: [{ repositoryId: DEPOT_ID, provider: 'GITHUB', repository: 'acme/portail', result: IMPORT_REUSSI }],
        },
      },
    })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await wrapper.get('#rattachement-depot').setValue('acme/second')
    await wrapper.get('#rattachement-branche').setValue('develop')
    await wrapper.get('[data-formulaire="rattachement"]').trigger('submit')
    await attendre()
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/repositories`)[0].corps).toEqual({
      forge: 'GITHUB',
      depot: 'acme/second',
      branche: 'develop',
    })
    expect(wrapper.get('[data-issue="alimentation"]').text()).toBe('Dépôt acme/second rattaché.')

    await wrapper.get('button[aria-label="Détacher le dépôt acme/portail"]').trigger('click')
    await attendre()
    expect(appelsVers(appels, 'DELETE', `${BASE_PROJET}/repositories/${DEPOT_ID}`)).toHaveLength(1)
    expect(wrapper.get('[data-issue="alimentation"]').text()).toBe('Dépôt acme/portail détaché.')

    await wrapper.get('[data-action="relancer"]').trigger('click')
    await attendre()
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/inventory/refresh`)).toHaveLength(1)
    expect(wrapper.get('[data-issue="alimentation"]').text()).toContain('acme/portail : Inventaire importé')
  })

  it('affiche l’activité et collecte maintenant', async () => {
    const appels = simulerApi({ ...lectures(), [`POST ${BASE_PROJET}/activite/collecte`]: { corps: { issue: 'REPORTEE_QUOTA' } } })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    expect(wrapper.get('[data-fraicheur]').text()).toContain('Collecte à jour')
    await wrapper.get('[data-action="collecter"]').trigger('click')
    await attendre()
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/activite/collecte`)).toHaveLength(1)
    expect(wrapper.get('[data-issue="collecte"]').text()).toBe('Collecte reportée : le quota de la forge est atteint.')
    expect(appelsVers(appels, 'GET', `${BASE_PROJET}/activite`)).toHaveLength(2)
  })

  it('affiche le refus de la collecte', async () => {
    simulerApi({ ...lectures(), [`POST ${BASE_PROJET}/activite/collecte`]: erreurApi(403, 'Rôle contributeur requis.') })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await wrapper.get('[data-action="collecter"]').trigger('click')
    await attendre()
    expect(wrapper.get('[data-erreur="collecte"]').text()).toBe('Rôle contributeur requis.')
  })
})
