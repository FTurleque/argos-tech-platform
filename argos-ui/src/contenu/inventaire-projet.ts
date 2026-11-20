// Vue d'inventaire d'un projet (INV-01-S3, INV-02, INV-03 ; décisions E4 et G5 du porteur du projet,
// 2026-11-19) : mise en forme de la synthèse de `GET /api/v1/projects/{projectId}/inventory/summary`, sans la liste
// des composants, et chargement de l'écran. Module pur, sans appel réseau ni dépendance à Vue : la lecture de l'API
// lui est passée en paramètre, ce qui le rend testable directement par `node --test`.
//
// Aucune règle métier ici (arc42 §5.1) : l'origine, l'âge, l'obsolescence, le nombre de composants et les
// composants écartés sont calculés par le serveur ; ce module les nomme.
import { estUuid } from '../api/recherche-criteres.ts'
import type { SyntheseInventaireProjetApi } from '../api/inventaire-modele.ts'
import { composantsIgnores, messageErreurInventaire, type ComposantsIgnoresVue } from './composants-ignores.ts'
import { depotsInventaire, type DepotAffiche } from './inventaire-depots.ts'

/** Synthèse de l'inventaire de référence d'un projet, prête à afficher. */
export type ResumeInventaire =
  | { etat: 'SANS_INVENTAIRE' }
  | {
      etat: 'DISPONIBLE'
      /** Libellé de l'origine : « SBOM » ou « manifestes ». */
      origine: string
      /** Date de la SBOM ou des manifestes (ISO 8601), `null` si inconnue. */
      dateSource: string | null
      /** Date de l'import (ISO 8601), `null` si inconnue. */
      importeLe: string | null
      /** Âge de la source en jours, `null` si inconnu. */
      ageJours: number | null
      /** Vrai quand le serveur juge l'inventaire obsolète (INV-03). */
      obsolete: boolean
      /** Nombre de composants inventoriés (avec un PURL exploitable). */
      composants: number
    }

/** État complet de l'écran après un chargement. */
export interface EtatInventaireProjet {
  resume: ResumeInventaire | null
  ignores: ComposantsIgnoresVue | null
  /** Dépôts du projet (INV-10), vide sans dépôt, sans inventaire ou en cas d'erreur. */
  depots: DepotAffiche[]
  erreur: string
}

const ORIGINES: Readonly<Record<string, string>> = { SBOM: 'SBOM', MANIFEST: 'manifestes' }

/** Erreur d'appel minimale lue par le chargement : statut HTTP et motif d'absence de réponse. */
interface ErreurAppel {
  status?: number
  motif?: string
}

function entierPositif(valeur: unknown): number | null {
  return typeof valeur === 'number' && Number.isInteger(valeur) && valeur >= 0 ? valeur : null
}

/** Synthèse de la réponse de l'API ; projet sans inventaire quand `status` ne vaut pas `AVAILABLE`. */
export function resumeInventaire(reponse: SyntheseInventaireProjetApi | null | undefined): ResumeInventaire {
  const synthese = reponse?.status === 'AVAILABLE' ? reponse.summary : null
  if (!synthese) return { etat: 'SANS_INVENTAIRE' }
  const origine = typeof synthese.origin === 'string' ? (ORIGINES[synthese.origin] ?? 'non précisée') : 'non précisée'
  return {
    etat: 'DISPONIBLE',
    origine,
    dateSource: synthese.sourceDate ?? null,
    importeLe: synthese.importedAt ?? null,
    ageJours: entierPositif(synthese.ageDays),
    obsolete: synthese.obsolete === true,
    composants: entierPositif(synthese.componentCount) ?? 0,
  }
}

/** Message de validation de l'identifiant saisi ; chaîne vide s'il est valide. */
export function messageIdentifiantProjet(saisie: unknown): string {
  if (typeof saisie !== 'string' || !saisie.trim()) return 'Saisissez l’identifiant du projet.'
  return estUuid(saisie) ? '' : 'Identifiant de projet invalide : un UUID est attendu.'
}

/**
 * Charge l'inventaire d'un projet et renvoie l'état de l'écran ; `null` si l'appel a été annulé (le projet
 * affiché a changé entre-temps). `lire` est la lecture de la synthèse (`fetchSyntheseInventaire`).
 */
export async function chargerInventaireProjet(
  projetId: string,
  lire: (projetId: string, signal?: AbortSignal) => Promise<SyntheseInventaireProjetApi>,
  signal?: AbortSignal,
): Promise<EtatInventaireProjet | null> {
  if (!estUuid(projetId)) return { resume: null, ignores: null, depots: [], erreur: messageIdentifiantProjet(projetId) }
  try {
    const reponse = await lire(projetId, signal)
    if (signal?.aborted) return null
    return { resume: resumeInventaire(reponse), ignores: composantsIgnores(reponse), depots: depotsInventaire(reponse), erreur: '' }
  } catch (cause) {
    const erreur = (cause ?? {}) as ErreurAppel
    if (signal?.aborted || erreur.motif === 'ANNULE') return null
    return { resume: null, ignores: null, depots: [], erreur: messageErreurInventaire(erreur.status) }
  }
}
