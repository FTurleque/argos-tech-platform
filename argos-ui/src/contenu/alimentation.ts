// Alimentation de l'inventaire depuis l'IHM (INV-12, INV-13, PIN-06) : saisie d'un dépôt, lecture des fichiers
// choisis et libellés des issues renvoyées par le serveur. Module pur, sans appel réseau ni dépendance à Vue.
//
// Aucune règle métier ici (arc42 §5.1) : la validité d'un dépôt, d'une SBOM ou d'un manifeste est décidée par le
// serveur ; ce module met en forme la saisie et nomme les issues.
import type { Depot, Forge, IssueCollecte, Manifeste, ResultatImport, StatutImport } from '../api/projets'

/** Saisie d'un dépôt dans un formulaire : champs texte bruts. */
export interface SaisieDepot {
  forge: Forge
  depot: string
  branche: string
  cheminSbom: string
  jobSbom: string
  /** Chemins des manifestes, un par ligne ou séparés par des virgules. */
  manifestes: string
}

/** Saisie vide : forge GitHub, aucun champ renseigné. */
export function saisieDepotVide(): SaisieDepot {
  return { forge: 'GITHUB', depot: '', branche: '', cheminSbom: '', jobSbom: '', manifestes: '' }
}

const SEPARATEUR_MANIFESTES = /[\n,]/

function renseigne(valeur: string): string | undefined {
  const nettoyee = valeur.trim()
  return nettoyee === '' ? undefined : nettoyee
}

/** Dépôt à envoyer, champs vides omis ; `undefined` si l'identifiant du dépôt n'est pas saisi. */
export function depotDeSaisie(saisie: SaisieDepot): Depot | undefined {
  const depot = renseigne(saisie.depot)
  if (depot === undefined) return undefined
  const manifestes = saisie.manifestes
    .split(SEPARATEUR_MANIFESTES)
    .map((chemin) => chemin.trim())
    .filter((chemin) => chemin !== '')
  const resultat: Depot = { forge: saisie.forge, depot }
  const branche = renseigne(saisie.branche)
  const cheminSbom = renseigne(saisie.cheminSbom)
  const jobSbom = renseigne(saisie.jobSbom)
  if (branche !== undefined) resultat.branche = branche
  if (cheminSbom !== undefined) resultat.cheminSbom = cheminSbom
  if (jobSbom !== undefined) resultat.jobSbom = jobSbom
  if (manifestes.length > 0) resultat.manifestes = manifestes
  return resultat
}

/** Fichier lisible comme texte (interface commune de `File` et des fichiers simulés des tests). */
export interface FichierTexte {
  readonly name: string
  text(): Promise<string>
}

/** Lecture d'une SBOM choisie : le document JSON, ou le message qui explique le refus local. */
export type LectureSbom = { document: unknown } | { erreur: string }

/** Lit la SBOM choisie ; seul un document JSON est envoyé au serveur, qui en juge le contenu. */
export async function lireSbom(fichier: FichierTexte | undefined): Promise<LectureSbom> {
  if (fichier === undefined) return { erreur: 'Choisissez un fichier SBOM CycloneDX au format JSON.' }
  try {
    return { document: JSON.parse(await fichier.text()) as unknown }
  } catch {
    return { erreur: `Le fichier ${fichier.name} n’est pas un document JSON lisible.` }
  }
}

/** Lit les manifestes choisis : chemin (nom du fichier) et contenu texte de chacun. */
export async function lireManifestes(fichiers: readonly FichierTexte[]): Promise<Manifeste[]> {
  return Promise.all(fichiers.map(async (fichier) => ({ chemin: fichier.name, contenu: await fichier.text() })))
}

const LIBELLES_IMPORT: Readonly<Record<StatutImport, string>> = {
  IMPORTED: 'Inventaire importé',
  REJECTED: 'Import refusé, inventaire précédent conservé',
  KEPT: 'Inventaire inchangé, conservé',
  NONE: 'Aucun inventaire trouvé',
  ERROR: 'Échec de l’import',
  DEFERRED: 'Import reporté',
}

/** Phrase qui décrit l'issue d'un import : statut, nombres de composants et motif éventuel du serveur. */
export function libelleImport(resultat: ResultatImport): string {
  const statut = LIBELLES_IMPORT[resultat.status] ?? `Issue ${resultat.status}`
  const details: string[] = []
  if (resultat.status === 'IMPORTED') {
    details.push(`${resultat.components} composant(s) inventorié(s)`, `${resultat.ignored} écarté(s) faute de PURL`)
  }
  if (resultat.reason) details.push(`motif : ${resultat.reason}`)
  return details.length > 0 ? `${statut} (${details.join(', ')}).` : `${statut}.`
}

const LIBELLES_COLLECTE: Readonly<Record<IssueCollecte, string>> = {
  A_JOUR: 'Activité collectée : les données sont à jour.',
  REPORTEE_QUOTA: 'Collecte reportée : le quota de la forge est atteint.',
  ECHEC: 'Échec de la collecte de l’activité.',
  NON_ECHUE: 'Collecte non nécessaire : la dernière collecte est encore récente.',
  SANS_DEPOT: 'Aucun dépôt rattaché : rien à collecter.',
}

/** Phrase qui décrit l'issue d'une collecte immédiate. */
export function libelleCollecte(issue: IssueCollecte): string {
  return LIBELLES_COLLECTE[issue] ?? `Issue de la collecte : ${issue}.`
}

/** Erreur d'appel minimale : message de l'`ApiError`. */
interface ErreurAvecMessage {
  message?: unknown
}

/** Message d'une erreur d'appel : celui du serveur (`ApiError.message`), un message générique sinon. */
export function messageEchec(echec: unknown): string {
  const message = (echec as ErreurAvecMessage | null | undefined)?.message
  return typeof message === 'string' && message.trim() !== '' ? message : 'Erreur inattendue.'
}
