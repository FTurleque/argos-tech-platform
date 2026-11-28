// Veille par profils et choix des profils actifs (PRF-01, PRF-02, décision Q-3 de `profils-veille`) :
// appels à `GET /api/v1/me/veille`, au catalogue des profils, aux préférences et aux abonnements (lecture,
// suspension, réactivation et suppression : ABO-07, ABO-08), tous
// par le point d'appel commun `appelerApi` du client REST. Aucune règle métier ici (arc42 §5.1) : l'union
// des profils et des abonnements PERSONAL, le dédoublonnage, les garde-fous et les droits sont calculés
// par le serveur.
import { appelerApi } from './client'

/** Réponse de `GET /api/v1/preferences` (schéma OpenAPI `PreferencesEffectives`). */
export interface PreferencesEffectives {
  miseEnPage: string
  miseEnPageChoisie: string | null
  miseEnPageIndisponible: boolean
  message: string | null
  profilsActifs: string[]
  profilsParDefaut: boolean
  profilsIndisponibles: string[]
}

/** Rubrique d'un profil actif portée par un élément (schéma `RubriqueElementVeille`). */
export interface RubriqueElementVeille {
  profil: string
  identifiant: string
  libelle: string
}

/** Projet des espaces de l'appelant concerné par un élément (schéma `ProjetConcerne`). */
export interface ProjetConcerne {
  projetId: string
  projet: string | null
  niveau: string | null
}

/**
 * Élément de la veille, restitué une seule fois (schéma `ElementVeilleProfils`). Titre, résumé et lien
 * viennent de la source : données externes, rendues comme texte ; le lien n'est cliquable qu'après `urlSure`.
 */
export interface ElementVeilleProfils {
  type: string
  id: string
  source: string | null
  identifiant: string | null
  titre: string | null
  resume: string | null
  gravite: string | null
  date: string | null
  dateEffet: string | null
  lien: string | null
  /** Profils actifs dont l'élément relève ; vide si seuls des abonnements PERSONAL le retiennent. */
  profils: string[]
  rubriques: RubriqueElementVeille[]
  /** Mentions obligatoires des garde-fous des profils actifs qui rangent l'élément. */
  gardeFous: string[]
  mentionValidation: string | null
  projets: ProjetConcerne[]
  /** Abonnements PERSONAL de l'appelant qui ont retenu l'élément (décision Q-3). */
  abonnements?: string[]
  /** Mention « produit par l'IA » quand le résumé vient du modèle (ADR-0023) ; absente sinon. */
  mentionIa?: string | null
  /** Mention de la source, de sa licence et de la date de mise à jour des données, si la source l'exige. */
  mentionSource?: string | null
}

/**
 * Page de `GET /api/v1/me/veille` (schéma `VeilleProfils`, PRF-09, ADR-0040) : éléments de la page, numéro de page
 * à partir de 0, taille appliquée, nombre d'éléments de la veille et profils actifs.
 */
export interface VeilleProfils {
  items: ElementVeilleProfils[]
  page: number
  size: number
  total: number
  profilsActifs: string[]
}

/** Point d'accès de la veille de l'appelant. */
const CHEMIN_VEILLE = '/api/v1/me/veille'

/**
 * Taille d'une page de « Ma veille », envoyée à chaque lecture : taille par défaut des listes de l'API (ADR-0040,
 * décision du porteur du 2026-11-27) ; la taille appliquée revient dans `size`.
 */
export const TAILLE_PAGE_VEILLE = 50

/** Profil publié au catalogue (schéma `ProfilCatalogue`). */
export interface ProfilCatalogue {
  identifiant: string
  libelle: string
  description: string
  version: string
  rubriques: { identifiant: string; libelle: string }[]
  gardeFous: string[]
}

/** Critère d'un abonnement de veille (schéma `Filter`). */
export interface FiltreAbonnement {
  type: string
  value: string
}

/** Abonnement qui s'applique à l'appelant (schéma `Subscription` de `GET /api/v1/subscriptions`). */
export interface Abonnement {
  id: string
  scope: string
  ownerWorkspaceId: string | null
  ownerUserId: string | null
  filters: FiltreAbonnement[]
  channel: string
  frequency: string
  mandatory: boolean
  /** `ACTIVE` ou `SUSPENDED` (ABO-07) ; seul un abonnement actif produit des notifications. */
  state: string
  createdAt: string
}

/** Page de la veille de l'appelant : union de ses profils actifs et de ses abonnements PERSONAL, sous ses droits. */
export function lireVeille(page = 0, signal?: AbortSignal): Promise<VeilleProfils> {
  const parametres = new URLSearchParams({ page: String(page), size: String(TAILLE_PAGE_VEILLE) })
  return appelerApi<VeilleProfils>(`${CHEMIN_VEILLE}?${parametres}`, { signal, delaiMs: 15_000 })
}

/** Profils de veille publiés au catalogue. */
export function lireCatalogueProfils(signal?: AbortSignal): Promise<ProfilCatalogue[]> {
  return appelerApi<ProfilCatalogue[]>('/api/v1/catalogue/profils', { signal })
}

/**
 * Préférences appliquées : mise en page, profils actifs, profils par défaut et profils retirés du
 * catalogue. Lecture de `GET /api/v1/preferences` partagée par la veille, les profils et la mise en page.
 */
export function lirePreferences(signal?: AbortSignal): Promise<PreferencesEffectives> {
  return appelerApi<PreferencesEffectives>('/api/v1/preferences', { signal })
}

/** Enregistre la mise en page choisie et renvoie les préférences effectives (AFF-01, AFF-03). */
export function choisirMiseEnPage(miseEnPage: string): Promise<PreferencesEffectives> {
  return appelerApi<PreferencesEffectives>('/api/v1/preferences/mise-en-page', {
    methode: 'PUT',
    corps: { miseEnPage },
  })
}

/**
 * Remplace l'ensemble des profils actifs. Le serveur refuse un profil absent du catalogue
 * (400 `PROFIL_INCONNU`) et un ensemble vide (400 `AU_MOINS_UN_PROFIL`).
 */
export function choisirProfils(profilsActifs: readonly string[]): Promise<PreferencesEffectives> {
  return appelerApi<PreferencesEffectives>('/api/v1/preferences/profils', {
    methode: 'PUT',
    corps: { profilsActifs: [...profilsActifs] },
  })
}

/**
 * Abonnements visibles de l'appelant, actifs ou suspendus (ABO-07-S4), chacun avec son état : pour nommer
 * l'origine des éléments de sa veille et pour les gérer.
 */
export function lireAbonnements(signal?: AbortSignal): Promise<Abonnement[]> {
  return appelerApi<Abonnement[]>('/api/v1/subscriptions', { signal })
}

/**
 * Suspend un abonnement : critères conservés, plus aucune notification (ABO-07). Le serveur décide des droits :
 * 403 pour un abonnement d'équipe dont l'appelant n'administre pas l'espace, 404 s'il est inconnu ou invisible.
 */
export function suspendreAbonnement(id: string): Promise<Abonnement> {
  return appelerApi<Abonnement>(`/api/v1/subscriptions/${encodeURIComponent(id)}/suspension`, { methode: 'POST' })
}

/** Réactive un abonnement suspendu, sans rejouer la période de suspension (ABO-07-S2). */
export function reactiverAbonnement(id: string): Promise<Abonnement> {
  return appelerApi<Abonnement>(`/api/v1/subscriptions/${encodeURIComponent(id)}/reactivation`, { methode: 'POST' })
}

/** Supprime définitivement un abonnement, actif ou suspendu (ABO-08) ; réponse 204 sans corps. */
export function supprimerAbonnement(id: string): Promise<void> {
  return appelerApi<void>(`/api/v1/subscriptions/${encodeURIComponent(id)}`, { methode: 'DELETE' })
}
