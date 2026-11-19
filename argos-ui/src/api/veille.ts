// Veille par profils et choix des profils actifs (PRF-01, PRF-02, décision Q-3 de `profils-veille`) :
// appels à `GET /api/v1/me/veille`, au catalogue des profils, aux préférences et aux abonnements, tous
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

/** Réponse de `GET /api/v1/me/veille` (schéma `VeilleProfils`). */
export interface VeilleProfils {
  profilsActifs: string[]
  elements: ElementVeilleProfils[]
}

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
  state: string
  createdAt: string
}

/** Veille de l'appelant : union de ses profils actifs et de ses abonnements PERSONAL, sous ses droits. */
export function lireVeille(signal?: AbortSignal): Promise<VeilleProfils> {
  return appelerApi<VeilleProfils>('/api/v1/me/veille', { signal, delaiMs: 15_000 })
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

/** Abonnements qui s'appliquent à l'appelant, pour nommer l'origine des éléments de sa veille. */
export function lireAbonnements(signal?: AbortSignal): Promise<Abonnement[]> {
  return appelerApi<Abonnement[]>('/api/v1/subscriptions', { signal })
}
