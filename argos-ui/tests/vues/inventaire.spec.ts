// Vue d'inventaire (INV-01, INV-06, INV-10) et alimentation de l'inventaire (INV-12, INV-13, PIN-06) : appels
// envoyés par les formulaires et issue affichée.
import { describe, expect, it } from 'vitest'
import {
  appelsVers,
  choisirFichiers,
  cliquer,
  erreurApi,
  monter,
  simulerApi,
  soumettre,
  texte,
  type AppelApi,
  type TableApi,
} from './support'

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

/** Lectures et création d'un projet aboutie, avec la lecture du projet créé. */
function creationAboutie(): TableApi {
  return {
    ...lectures(),
    'POST /api/v1/projects': { statut: 201, corps: { id: NOUVEAU_PROJET_ID, espaceId: ESPACE_ID, nom: 'Nouveau' } },
    [`GET /api/v1/projects/${NOUVEAU_PROJET_ID}/inventory/summary`]: {
      corps: { projectId: NOUVEAU_PROJET_ID, status: 'NO_SBOM', summary: null },
    },
    [`GET /api/v1/projects/${NOUVEAU_PROJET_ID}/activite`]: { corps: { ...ACTIVITE, projectId: NOUVEAU_PROJET_ID } },
  }
}

/** Lectures et réponses des actions sur les dépôts : rattachement, détachement et relance de l'import. */
function actionsDepots(): TableApi {
  const resultat = { repositoryId: DEPOT_ID, provider: 'GITHUB', repository: 'acme/portail', result: IMPORT_REUSSI }
  return {
    ...lectures(),
    [`POST ${BASE_PROJET}/repositories`]: { statut: 201, corps: { id: 'nouveau', principal: false } },
    [`DELETE ${BASE_PROJET}/repositories/${DEPOT_ID}`]: { statut: 204 },
    [`POST ${BASE_PROJET}/inventory/refresh`]: { corps: { projectId: PROJET_ID, depots: [resultat] } },
  }
}

/** Corps du premier appel reçu pour une méthode et un chemin. */
function corpsEnvoye(appels: readonly AppelApi[], methode: string, chemin: string): unknown {
  return appelsVers(appels, methode, chemin)[0]?.corps
}

/** Options d'un sélecteur, dans l'ordre affiché. */
function options(wrapper: Awaited<ReturnType<typeof monter>>['wrapper'], selecteur: string): string[] {
  return wrapper.findAll(`${selecteur} option`).map((option) => option.text())
}

describe('InventaireView : liste et création de projet', () => {
  it('liste les projets et propose les seuls espaces administrés, avec leur radar', async () => {
    simulerApi(lectures())
    const { wrapper } = await monter('/inventaire')
    expect(options(wrapper, '#choix-projet')).toContain('Portail — Équipe plateforme')
    expect(options(wrapper, '#creation-espace')).toEqual(['— Choisir un espace —', 'Équipe plateforme'])
    expect(wrapper.find(`a[href="/espaces/${ESPACE_ID}/radar"]`).exists()).toBe(true)
  })

  it('crée un projet avec son dépôt puis affiche le projet créé', async () => {
    const appels = simulerApi(creationAboutie())
    const { wrapper, router } = await monter('/inventaire')
    await wrapper.get('#creation-nom').setValue('  Nouveau  ')
    await wrapper.get('#creation-depot-forge').setValue('GITLAB')
    await wrapper.get('#creation-depot-depot').setValue('acme/nouveau')
    await soumettre(wrapper, '[data-formulaire="creation-projet"]')
    const attendu = { espaceId: ESPACE_ID, nom: 'Nouveau', depot: { forge: 'GITLAB', depot: 'acme/nouveau' } }
    expect(corpsEnvoye(appels, 'POST', '/api/v1/projects')).toEqual(attendu)
    expect(texte(wrapper, '[data-issue="creation-projet"]')).toBe('Projet « Nouveau » créé.')
    expect(router.currentRoute.value.fullPath).toBe(`/inventaire/${NOUVEAU_PROJET_ID}`)
  })

  it('affiche le refus de la création renvoyé par le serveur', async () => {
    simulerApi({ ...lectures(), 'POST /api/v1/projects': erreurApi(400, 'Le nom du projet est obligatoire.', 'DONNEE_INVALIDE') })
    const { wrapper } = await monter('/inventaire')
    await wrapper.get('#creation-nom').setValue('Projet')
    await soumettre(wrapper, '[data-formulaire="creation-projet"]')
    expect(texte(wrapper, '[data-erreur="creation-projet"]')).toBe('Le nom du projet est obligatoire.')
  })

  it('affiche l’échec de la lecture des projets', async () => {
    simulerApi({ 'GET /api/v1/projects': erreurApi(500, 'Panne.'), 'GET /api/v1/espaces': { corps: [] } })
    const { wrapper } = await monter('/inventaire')
    expect(wrapper.text()).toContain('Recharger la liste')
  })
})

describe('InventaireView : imports manuels', () => {
  it('importe une SBOM choisie et affiche l’issue', async () => {
    const appels = simulerApi({ ...lectures(), [`POST ${BASE_PROJET}/inventory/sbom`]: { corps: IMPORT_REUSSI } })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    const document = { bomFormat: 'CycloneDX', specVersion: '1.5', components: [] }
    await choisirFichiers(wrapper, '#fichier-sbom', [new File([JSON.stringify(document)], 'sbom.json')])
    await soumettre(wrapper, '[data-formulaire="sbom"]')
    expect(corpsEnvoye(appels, 'POST', `${BASE_PROJET}/inventory/sbom`)).toEqual(document)
    expect(texte(wrapper, '[data-issue="alimentation"]')).toBe(
      'SBOM : Inventaire importé (12 composant(s) inventorié(s), 1 écarté(s) faute de PURL).',
    )
  })

  it('refuse localement un fichier SBOM qui n’est pas du JSON, sans appel', async () => {
    const appels = simulerApi(lectures())
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await choisirFichiers(wrapper, '#fichier-sbom', [new File(['<bom/>'], 'sbom.xml')])
    await soumettre(wrapper, '[data-formulaire="sbom"]')
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/inventory/sbom`)).toHaveLength(0)
    expect(texte(wrapper, '[data-erreur="alimentation"]')).toContain('sbom.xml')
  })

  it('importe des manifestes et affiche le refus du serveur', async () => {
    const refus = erreurApi(403, 'Rôle contributeur requis.', 'ROLE_INSUFFISANT')
    const appels = simulerApi({ ...lectures(), [`POST ${BASE_PROJET}/inventory/manifests`]: refus })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await choisirFichiers(wrapper, '#fichiers-manifestes', [new File(['{"name":"a"}'], 'package.json')])
    await soumettre(wrapper, '[data-formulaire="manifestes"]')
    const attendu = { manifestes: [{ chemin: 'package.json', contenu: '{"name":"a"}' }] }
    expect(corpsEnvoye(appels, 'POST', `${BASE_PROJET}/inventory/manifests`)).toEqual(attendu)
    expect(texte(wrapper, '[data-erreur="alimentation"]')).toBe('Rôle contributeur requis.')
  })
})

describe('InventaireView : dépôts du projet', () => {
  it('rattache un dépôt saisi', async () => {
    const appels = simulerApi(actionsDepots())
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await wrapper.get('#rattachement-depot').setValue('acme/second')
    await wrapper.get('#rattachement-branche').setValue('develop')
    await soumettre(wrapper, '[data-formulaire="rattachement"]')
    const attendu = { forge: 'GITHUB', depot: 'acme/second', branche: 'develop' }
    expect(corpsEnvoye(appels, 'POST', `${BASE_PROJET}/repositories`)).toEqual(attendu)
    expect(texte(wrapper, '[data-issue="alimentation"]')).toBe('Dépôt acme/second rattaché.')
  })

  it('détache un dépôt du projet', async () => {
    const appels = simulerApi(actionsDepots())
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await cliquer(wrapper, 'button[aria-label="Détacher le dépôt acme/portail"]')
    expect(appelsVers(appels, 'DELETE', `${BASE_PROJET}/repositories/${DEPOT_ID}`)).toHaveLength(1)
    expect(texte(wrapper, '[data-issue="alimentation"]')).toBe('Dépôt acme/portail détaché.')
  })

  it('relance l’import et affiche l’issue de chaque dépôt', async () => {
    const appels = simulerApi(actionsDepots())
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await cliquer(wrapper, '[data-action="relancer"]')
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/inventory/refresh`)).toHaveLength(1)
    expect(texte(wrapper, '[data-issue="alimentation"]')).toContain('acme/portail : Inventaire importé')
  })
})

describe('InventaireView : activité du projet', () => {
  it('affiche l’activité et collecte maintenant', async () => {
    const appels = simulerApi({ ...lectures(), [`POST ${BASE_PROJET}/activite/collecte`]: { corps: { issue: 'REPORTEE_QUOTA' } } })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    expect(texte(wrapper, '[data-fraicheur]')).toContain('Collecte à jour')
    await cliquer(wrapper, '[data-action="collecter"]')
    expect(appelsVers(appels, 'POST', `${BASE_PROJET}/activite/collecte`)).toHaveLength(1)
    expect(texte(wrapper, '[data-issue="collecte"]')).toBe('Collecte reportée : le quota de la forge est atteint.')
    expect(appelsVers(appels, 'GET', `${BASE_PROJET}/activite`)).toHaveLength(2)
  })

  it('affiche le refus de la collecte', async () => {
    simulerApi({ ...lectures(), [`POST ${BASE_PROJET}/activite/collecte`]: erreurApi(403, 'Rôle contributeur requis.') })
    const { wrapper } = await monter(`/inventaire/${PROJET_ID}`)
    await cliquer(wrapper, '[data-action="collecter"]')
    expect(texte(wrapper, '[data-erreur="collecte"]')).toBe('Rôle contributeur requis.')
  })
})
