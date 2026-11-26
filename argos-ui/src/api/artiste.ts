// Profil de veille « artiste » (ART-02, ART-03, ART-05, ART-06, ART-07) : listes du profil, suivis de l'utilisateur,
// opportunités rapprochées et leur résumé, sources du profil (administrateur plateforme) et rubriques de la veille,
// par le point d'appel commun `appelerApi`. Aucune règle métier ici (arc42 §5.1) : la validation des suivis, le
// rapprochement et le résumé sont décidés par le serveur ; titres, critères et liens sont des données externes.
import { appelerApi } from './client'
import { avecIdentifiants } from './identifiants'

/** Valeur d'une liste du profil (schéma `ValeurListeArtiste`). */
export interface ValeurListe {
  identifiant: string
  libelle: string
}

/** Disciplines et territoires du profil, et jours de rappel par défaut (schéma `ListesArtiste`). */
export interface ListesArtiste {
  disciplines: ValeurListe[]
  territoires: ValeurListe[]
  rappelsJoursParDefaut: number[]
}

/** Suivis de l'utilisateur (schéma `SuivisArtiste`) ; `complets` : au moins une discipline et un territoire. */
export interface SuivisArtiste {
  disciplines: string[]
  territoires: string[]
  rappelsJours: number[]
  rappelsParDefaut: boolean
  complets: boolean
}

/** Choix des suivis (schéma `ChoixSuivisArtiste`) ; jours de rappel absents : ceux par défaut. */
export interface ChoixSuivis {
  disciplines: string[]
  territoires: string[]
  rappelsJours?: number[]
}

/**
 * Résumé d'une opportunité (schéma `Resume`) : critères tels qu'énoncés par l'organisme et mention « aucun avis
 * d'éligibilité ». `mentionIa` porte « produit par l'IA » quand le serveur indique un résumé produit par le modèle ;
 * absent sinon.
 */
export interface ResumeOpportunite {
  titre: string | null
  organisme: string | null
  criteres: string | null
  criteresTronques: boolean
  dateLimite: string | null
  texteOfficiel: string | null
  mention: string | null
  mentionIa?: string | null
}

/** Opportunité rapprochée des suivis (schéma `OpportuniteArtiste`). */
export interface OpportuniteArtiste {
  id: string
  rubrique: string | null
  titre: string | null
  organisme: string | null
  dateLimite: string | null
  dateAConfirmer: boolean
  lienOfficiel: string | null
  disciplines: string[]
  territoires: string[]
  explication: string | null
  resume: ResumeOpportunite | null
}

/** Opportunités restituées (schéma `OpportunitesArtiste`) ; `suivisRequis` : aucune liste sans suivis complets. */
export interface OpportunitesArtiste {
  suivisRequis: boolean
  message: string | null
  items: OpportuniteArtiste[]
}

/** Source du profil et état de ses conditions d'usage (schéma `SourceProfil`). */
export interface SourceProfil {
  id: string
  profil: string
  type: string
  adresse: string | null
  libelle: string | null
  rubrique: string | null
  licence: string | null
  conditionsUsage: string | null
  conditionsUrl: string | null
  conditionsVerifieesLe: string | null
  etat: string
}

/** Rubrique de la veille de l'utilisateur, issue d'un de ses profils actifs (schéma `RubriqueVeille`). */
export interface RubriqueVeille {
  profil: string
  identifiant: string
  libelle: string
}

export function fetchListesArtiste(signal?: AbortSignal): Promise<ListesArtiste> {
  return appelerApi<ListesArtiste>('/api/v1/profils/artiste/listes', { signal })
}

/** Suivis de l'utilisateur ; 409 `PROFIL_INACTIF` quand le profil artiste n'est pas actif. */
export function fetchSuivisArtiste(signal?: AbortSignal): Promise<SuivisArtiste> {
  return appelerApi<SuivisArtiste>('/api/v1/profils/artiste/suivis', { signal })
}

/** Remplace les suivis ; 400 pour une valeur absente des listes du profil ou une liste vide. */
export function enregistrerSuivisArtiste(choix: ChoixSuivis): Promise<SuivisArtiste> {
  return appelerApi<SuivisArtiste>('/api/v1/profils/artiste/suivis', { methode: 'PUT', corps: choix })
}

export function fetchOpportunitesArtiste(signal?: AbortSignal): Promise<OpportunitesArtiste> {
  return appelerApi<OpportunitesArtiste>('/api/v1/profils/artiste/opportunites', { signal })
}

/** Résumé d'une opportunité ; 404 pour une opportunité inconnue. */
export function fetchResumeOpportunite(opportuniteId: string, signal?: AbortSignal): Promise<ResumeOpportunite> {
  return avecIdentifiants("Identifiant d'opportunité", [opportuniteId], ([opportunite]) =>
    appelerApi<ResumeOpportunite>(`/api/v1/profils/artiste/opportunites/${opportunite}/resume`, { signal }),
  )
}

/** Sources du profil et état de leurs conditions d'usage ; réservé à l'administrateur plateforme (403 sinon). */
export function fetchSourcesArtiste(signal?: AbortSignal): Promise<SourceProfil[]> {
  return appelerApi<SourceProfil[]>('/api/v1/profils/artiste/sources', { signal })
}

/** Rubriques de la veille de l'utilisateur, issues de ses profils actifs. */
export function fetchRubriquesVeille(signal?: AbortSignal): Promise<RubriqueVeille[]> {
  return appelerApi<RubriqueVeille[]>('/api/v1/preferences/rubriques', { signal })
}
