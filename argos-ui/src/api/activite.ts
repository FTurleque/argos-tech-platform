// Activité factuelle d'un projet (PIN-01, PIN-02, PIN-04, PIN-05) : `GET /api/v1/projects/{id}/activite` et sa
// synthèse produite par l'IA, par le point d'appel commun `appelerApi`. Aucune règle métier ici (arc42 §5.1) :
// compteurs, avancement des jalons, fraîcheur et synthèse sont calculés par le serveur ; aucun indicateur ne désigne
// une personne (PIN-03).
import { appelerApi } from './client'
import { avecIdentifiants } from './identifiants'

/** Période des compteurs consultée par défaut, en jours. */
export const JOURS_ACTIVITE = 30

/** Délai d'une demande de synthèse, qui sollicite le modèle d'IA. */
const DELAI_SYNTHESE_MS = 30_000

const PROJET = 'Identifiant de projet'

/** Période des compteurs, bornes comprises. */
export interface PeriodeActivite {
  debut: string
  fin: string
  jours: number
}

/** Compteurs agrégés par type d'événement. */
export interface CompteursActivite {
  ticketsOuverts: number
  ticketsFermes: number
  fusionsOuvertes: number
  fusionsFusionnees: number
  pipelinesReussis: number
  pipelinesEchoues: number
  pipelinesAutres: number
  versionsPubliees: number
}

/** Avancement d'un jalon, avec son dénominateur (PIN-02). */
export interface AvancementJalon {
  fermes: number
  total: number
  pourcentage: number
  libelle: string
}

/** Jalon au dernier relevé ; le titre vient de la forge (texte externe). */
export interface JalonActivite {
  titre: string | null
  etat: string | null
  echeance: string | null
  ticketsFermes: number
  ticketsTotal: number
  avancement: AvancementJalon | null
}

/** Fraîcheur des données : `JAMAIS_COLLECTE`, `A_JOUR`, `REPORTEE_QUOTA` ou `ECHEC`. */
export interface FraicheurActivite {
  etat: string
  derniereMiseAJour: string | null
  prochaineCollecte: string | null
  complet: boolean
  donneesDepuis: string | null
  motif: string | null
}

/** Activité d'un projet sur une période (schéma `VueActivite`). */
export interface VueActivite {
  projectId: string
  periode: PeriodeActivite
  compteurs: CompteursActivite
  jalons: JalonActivite[]
  fraicheur: FraicheurActivite
}

/** Synthèse de l'activité produite par l'IA ; `mention` vaut toujours « produite par l'IA ». */
export interface SyntheseActivite {
  etat: string
  texte: string | null
  mention: string
  periode: PeriodeActivite
  indicateurs: string[]
}

/** Activité du projet sur les `jours` derniers jours. */
export function fetchActivite(projetId: string, jours: number = JOURS_ACTIVITE, signal?: AbortSignal): Promise<VueActivite> {
  return avecIdentifiants(PROJET, [projetId], ([projet]) =>
    appelerApi<VueActivite>(`/api/v1/projects/${projet}/activite?jours=${Math.trunc(jours)}`, { signal }),
  )
}

/** Synthèse de l'activité produite par l'IA à partir des seuls indicateurs affichés. */
export function fetchSyntheseActivite(projetId: string, jours: number = JOURS_ACTIVITE): Promise<SyntheseActivite> {
  return avecIdentifiants(PROJET, [projetId], ([projet]) =>
    appelerApi<SyntheseActivite>(`/api/v1/projects/${projet}/activite/synthese?jours=${Math.trunc(jours)}`, {
      delaiMs: DELAI_SYNTHESE_MS,
    }),
  )
}
