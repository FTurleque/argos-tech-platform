// Recherche de veille et vues enregistrées (RCH-01 à RCH-04) : appels à `GET /api/v1/recherche` et
// `/api/v1/vues`, tous par le point d'appel commun `appelerApi` du client REST.
import { appelerApi } from './client'
import {
  TAILLE_PAGE,
  corpsNouvelleVue,
  estUuid,
  parametresPage,
  parametresRecherche,
  type CriteresRecherche,
} from './recherche-criteres'

export type { CriteresRecherche } from './recherche-criteres'

/** Projet visible de l'appelant concerné par un élément (schéma `ProjetVise`). */
export interface ProjetVise {
  projetId: string
  projet: string | null
}

/**
 * Élément trouvé (schéma `Element`). Titre, extrait et lien viennent de la source : données externes,
 * rendues comme texte ; le lien n'est cliquable qu'après `urlSure`.
 */
export interface ElementRecherche {
  type: string
  id: string
  source: string | null
  identifiant: string | null
  titre: string | null
  extrait: string | null
  gravite: string | null
  date: string | null
  lien: string | null
  pertinence: number
  projets: ProjetVise[]
}

/** Page de résultats (schéma `Resultats`). */
export interface ResultatsRecherche {
  elements: ElementRecherche[]
  page: number
  taille: number
  total: number
  message: string | null
}

/** Vue enregistrée (schéma `VueEnregistree`). */
export interface VueEnregistree {
  id: string
  nom: string
  criteres: CriteresRecherche | null
  creeLe: string
}

function cheminVue(id: string): string {
  if (!estUuid(id)) throw new TypeError('identifiant de vue UUID attendu')
  return `/api/v1/vues/${encodeURIComponent(id.trim())}`
}

export function rechercher(criteres: CriteresRecherche, page = 0, taille = TAILLE_PAGE): Promise<ResultatsRecherche> {
  return appelerApi<ResultatsRecherche>(`/api/v1/recherche?${parametresRecherche(criteres, page, taille)}`)
}

export function listerVues(): Promise<VueEnregistree[]> {
  return appelerApi<VueEnregistree[]>('/api/v1/vues')
}

export function enregistrerVue(nom: string, criteres: CriteresRecherche): Promise<VueEnregistree> {
  return appelerApi<VueEnregistree>('/api/v1/vues', { methode: 'POST', corps: corpsNouvelleVue(nom, criteres) })
}

export function lireVue(id: string): Promise<VueEnregistree> {
  return appelerApi<VueEnregistree>(cheminVue(id))
}

/** Rappelle une vue : ses mots et ses filtres appliqués par le serveur sous les droits courants. */
export function resultatsVue(id: string, page = 0, taille = TAILLE_PAGE): Promise<ResultatsRecherche> {
  return appelerApi<ResultatsRecherche>(`${cheminVue(id)}/resultats?${parametresPage(page, taille)}`)
}

export function supprimerVue(id: string): Promise<void> {
  return appelerApi<void>(cheminVue(id), { methode: 'DELETE' })
}
