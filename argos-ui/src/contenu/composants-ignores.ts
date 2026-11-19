// Composants ignorés faute de PURL (INV-01-S3, décision D6 du 2026-11-18, décision E4 du 2026-11-19) :
// conversion de la réponse de `GET /api/v1/projects/{projectId}/inventory` en données affichables. Module
// pur, sans appel réseau ni dépendance à Vue : testé directement par `node --test`.
//
// Aucune règle métier ici (arc42 §5.1) : le serveur écarte les composants, les compte et borne la liste.
// Ce module les nomme et applique les mêmes bornes que l'API, sans en retirer ni en ajouter d'autres.
import {
  MAX_CARACTERES_CHAMP,
  MAX_COMPOSANTS_IGNORES,
  type ComposantIgnoreApi,
  type InventaireProjetApi,
} from '../api/inventaire-modele.ts'

/** Composant ignoré prêt à afficher ; `null` : champ absent de la SBOM. */
export interface ComposantIgnoreAffiche {
  nom: string | null
  groupe: string | null
  version: string | null
  motif: string
}

/** Composants ignorés d'un projet, prêts à afficher. */
export type ComposantsIgnoresVue =
  | { etat: 'SANS_INVENTAIRE' }
  | {
      etat: 'DISPONIBLE'
      /** Nombre total de composants écartés au dernier import (peut dépasser la liste). */
      total: number
      composants: ComposantIgnoreAffiche[]
      /** Vrai quand le total dépasse le nombre de composants listés. */
      tronque: boolean
    }

const MOTIFS: Readonly<Record<string, string>> = {
  SANS_PURL: 'sans PURL',
  PURL_INVALIDE: 'PURL invalide',
}

/** Libellé d'un motif d'écart ; un motif inconnu est restitué tel quel. */
export function libelleMotif(code: string | null | undefined): string {
  if (!code) return 'non précisé'
  return MOTIFS[code] ?? borne(code) ?? 'non précisé'
}

function borne(valeur: unknown): string | null {
  if (typeof valeur !== 'string') return null
  const v = valeur.trim()
  if (!v) return null
  return v.length > MAX_CARACTERES_CHAMP ? v.slice(0, MAX_CARACTERES_CHAMP) : v
}

function composant(c: ComposantIgnoreApi): ComposantIgnoreAffiche {
  return { nom: borne(c?.name), groupe: borne(c?.group), version: borne(c?.version), motif: libelleMotif(c?.reason) }
}

/** Conversion de la réponse de l'API : liste bornée comme l'API, total et indication de troncature. */
export function composantsIgnores(reponse: InventaireProjetApi | null | undefined): ComposantsIgnoresVue {
  const inventaire = reponse?.status === 'AVAILABLE' ? reponse.inventory : null
  if (!inventaire) return { etat: 'SANS_INVENTAIRE' }
  const liste = Array.isArray(inventaire.ignoredComponents) ? inventaire.ignoredComponents : []
  const composants = liste.slice(0, MAX_COMPOSANTS_IGNORES).map(composant)
  const declare = Number.isInteger(inventaire.ignoredCount) && inventaire.ignoredCount > 0 ? inventaire.ignoredCount : 0
  const total = Math.max(declare, composants.length)
  return { etat: 'DISPONIBLE', total, composants, tronque: total > composants.length }
}

/** Phrase de synthèse affichée au-dessus de la liste. */
export function syntheseComposantsIgnores(vue: ComposantsIgnoresVue): string {
  if (vue.etat === 'SANS_INVENTAIRE') return 'Aucun inventaire importé pour ce projet.'
  if (vue.total === 0) return 'Aucun composant ignoré au dernier import.'
  const nombre = vue.total === 1 ? '1 composant écarté' : `${vue.total} composants écartés`
  const suite = vue.tronque ? ` ; les ${vue.composants.length} premiers sont listés` : ''
  return `${nombre} de l'inventaire au dernier import, faute de PURL exploitable${suite}.`
}

/** Message d'erreur du chargement de l'inventaire d'un projet. */
export function messageErreurInventaire(statut: number | undefined): string {
  if (statut === 401) return 'Connectez-vous pour consulter l’inventaire.'
  if (statut === 403 || statut === 404) return 'Inventaire introuvable ou hors de vos espaces.'
  return 'Inventaire indisponible pour le moment.'
}
