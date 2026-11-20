// Dépôts d'un projet dans la vue d'inventaire (INV-10 ; décision J6 du porteur du projet, 2026-11-19 ; changement
// automatiser-chaine-veille, tâche 2.5) : un projet regroupe plusieurs dépôts et son inventaire est l'union des leurs.
// Mise en forme des dépôts de la synthèse (`repositories` de `GET …/inventory/summary`) et des dépôts d'un composant
// (`repositories` de `GET …/inventory/components`). Module pur, sans appel réseau ni dépendance à Vue : testé
// directement par `node --test`.
//
// Aucune règle métier ici (arc42 §5.1) : l'union, le dernier import et le dernier échec de chaque dépôt sont
// calculés par le serveur ; ce module les nomme. Les valeurs reçues (nom du dépôt, motif d'échec) sont rendues comme
// texte par la vue.
import type { DepotComposantApi, SyntheseInventaireProjetApi } from '../api/inventaire-modele.ts'

/** Dépôt du projet prêt à afficher ; `null` : valeur absente. */
export interface DepotAffiche {
  /** Clé de ligne : identifiant du dépôt, ou rang à défaut. */
  cle: string
  /** « GitLab », « GitHub » ou « forge non précisée ». */
  forge: string
  depot: string
  branche: string | null
  principal: boolean
  /** « SBOM », « manifestes », ou `null` tant qu'aucun import n'a réussi. */
  origine: string | null
  dateSource: string | null
  importeLe: string | null
  /** Dernier échec postérieur au dernier import réussi, `null` s'il n'y en a pas. */
  echec: { motif: string; date: string | null } | null
}

const FORGES: Readonly<Record<string, string>> = { GITLAB: 'GitLab', GITHUB: 'GitHub' }
const ORIGINES: Readonly<Record<string, string>> = { SBOM: 'SBOM', MANIFEST: 'manifestes' }

function texte(valeur: unknown): string | null {
  return typeof valeur === 'string' && valeur.trim() ? valeur : null
}

/** Dépôts de la synthèse, dans l'ordre du serveur (principal d'abord) ; liste vide sans dépôt ou sans synthèse. */
export function depotsInventaire(reponse: SyntheseInventaireProjetApi | null | undefined): DepotAffiche[] {
  const synthese = reponse?.status === 'AVAILABLE' ? reponse.summary : null
  const recus = Array.isArray(synthese?.repositories) ? synthese.repositories : []
  return recus.map((recu, rang) => {
    const motif = texte(recu?.lastFailure?.reason)
    const origine = texte(recu?.origin)
    return {
      cle: texte(recu?.id) ?? `rang-${rang}`,
      forge: FORGES[texte(recu?.provider) ?? ''] ?? 'forge non précisée',
      depot: texte(recu?.repository) ?? 'dépôt non renseigné',
      branche: texte(recu?.branch),
      principal: recu?.principal === true,
      origine: origine === null ? null : (ORIGINES[origine] ?? 'non précisée'),
      dateSource: texte(recu?.sourceDate),
      importeLe: texte(recu?.importedAt),
      echec: motif === null ? null : { motif, date: texte(recu?.lastFailure?.receivedAt) },
    }
  })
}

/** Noms des dépôts qui contiennent un composant, dans l'ordre du serveur ; liste vide s'ils sont absents. */
export function depotsComposant(recus: DepotComposantApi[] | null | undefined): string[] {
  return (Array.isArray(recus) ? recus : []).map((depot) => texte(depot?.repository)).filter((nom): nom is string => nom !== null)
}

/**
 * La colonne « Dépôts » des composants n'est utile que pour un projet à plusieurs dépôts : avec un seul dépôt, tous
 * les composants en proviennent.
 */
export function afficherColonneDepots(depots: readonly DepotAffiche[]): boolean {
  return depots.length > 1
}

/** Libellé de la cellule « Dépôts » d'un composant. */
export function libelleDepotsComposant(depots: readonly string[]): string {
  return depots.length > 0 ? depots.join(', ') : 'aucun dépôt (import manuel)'
}
