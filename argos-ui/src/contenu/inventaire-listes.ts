// Listes de la vue d'inventaire (INV-06 ; décisions F1 et F5 du porteur du projet, 2026-11-19) : choix du projet
// parmi les projets visibles (`GET /api/v1/projects`) et page des composants inventoriés d'un projet
// (`GET /api/v1/projects/{projectId}/inventory/components`). Module pur, sans appel réseau ni dépendance à Vue :
// la lecture de l'API lui est passée en paramètre, ce qui le rend testable directement par `node --test`.
//
// Aucune règle métier ici (arc42 §5.1) : la visibilité des projets (RLS), le tri, le nombre total et les bornes
// des pages sont établis par le serveur ; ce module les nomme et les met en forme.
import { estUuid } from '../api/recherche-criteres.ts'
import {
  type ComposantApi,
  type PageComposantsApi,
  type PageProjetsApi,
  type ProjetApi,
} from '../api/inventaire-modele.ts'

/** Erreur d'appel minimale lue par les chargements : statut HTTP et motif d'absence de réponse. */
interface ErreurAppel {
  status?: number
  motif?: string
}

// Valeurs restituées entières : leurs longueurs sont bornées par les colonnes du serveur.
function texte(valeur: unknown): string | null {
  if (typeof valeur !== 'string') return null
  const v = valeur.trim()
  return v ? v : null
}

function entierPositif(valeur: unknown): number {
  return typeof valeur === 'number' && Number.isInteger(valeur) && valeur >= 0 ? valeur : 0
}

function annule(e: unknown, signal?: AbortSignal): boolean {
  return signal?.aborted === true || ((e ?? {}) as ErreurAppel).motif === 'ANNULE'
}

// ------------------------------------------------------------------ Choix du projet

/** Projet proposé dans la liste de choix. */
export interface OptionProjet {
  /** Identifiant du projet (UUID en minuscules). */
  id: string
  /** Libellé : nom du projet, puis nom de l'espace. */
  libelle: string
}

/** Liste de choix des projets, prête à afficher. */
export interface ListeProjets {
  options: OptionProjet[]
  /** Nombre total de projets visibles déclaré par le serveur. */
  total: number
  /** Vrai quand le serveur déclare plus de projets que la liste n'en contient. */
  tronquee: boolean
}

/** État du chargement de la liste des projets. */
export interface EtatListeProjets {
  liste: ListeProjets | null
  erreur: string
}

function option(p: ProjetApi): OptionProjet | null {
  if (!estUuid(p?.id)) return null
  const nom = texte(p.name) ?? 'projet sans nom'
  const espace = texte(p.workspaceName)
  return { id: p.id.trim().toLowerCase(), libelle: espace ? `${nom} — ${espace}` : nom }
}

/** Conversion de la page de projets : options dans l'ordre du serveur, entrées sans UUID écartées. */
export function listeProjets(reponse: PageProjetsApi | null | undefined): ListeProjets {
  const items = Array.isArray(reponse?.items) ? reponse.items : []
  const options = items.map(option).filter((o): o is OptionProjet => o !== null)
  const total = Math.max(entierPositif(reponse?.total), options.length)
  return { options, total, tronquee: total > options.length }
}

/** Phrase d'état de la liste : aucun projet, ou liste limitée aux premiers projets. */
export function syntheseProjets(liste: ListeProjets): string {
  if (liste.options.length === 0) return 'Aucun projet dans vos espaces.'
  if (liste.tronquee) {
    return `${liste.total} projets dans vos espaces ; les ${liste.options.length} premiers sont proposés, les autres se désignent par leur identifiant.`
  }
  return liste.total === 1 ? '1 projet dans vos espaces.' : `${liste.total} projets dans vos espaces.`
}

/** Message d'erreur du chargement de la liste des projets. */
export function messageErreurProjets(statut: number | undefined): string {
  if (statut === 401) return 'Connectez-vous pour consulter vos projets.'
  return 'Liste des projets indisponible pour le moment.'
}

/** Charge la liste des projets ; `null` si l'appel a été annulé. `lire` est `fetchProjets`. */
export async function chargerProjets(
  lire: (signal?: AbortSignal) => Promise<PageProjetsApi>,
  signal?: AbortSignal,
): Promise<EtatListeProjets | null> {
  try {
    const reponse = await lire(signal)
    if (signal?.aborted) return null
    return { liste: listeProjets(reponse), erreur: '' }
  } catch (e) {
    if (annule(e, signal)) return null
    return { liste: null, erreur: messageErreurProjets(((e ?? {}) as ErreurAppel).status) }
  }
}

// ------------------------------------------------------------------ Composants inventoriés

/** Composant inventorié prêt à afficher ; `null` : champ absent. */
export interface ComposantAffiche {
  /** Clé de ligne : identifiant du composant, ou rang à défaut. */
  cle: string
  nom: string | null
  version: string | null
  purl: string | null
  /** « directe », « transitive » ou « inconnue ». */
  dependance: string
}

/** Page de composants inventoriés, prête à afficher. */
export interface PageComposants {
  composants: ComposantAffiche[]
  /** Numéro de page (à partir de 0) restitué par le serveur. */
  page: number
  /** Taille de page appliquée par le serveur. */
  taille: number
  /** Nombre total de composants inventoriés du projet. */
  total: number
  /** Nombre de pages (au moins 1). */
  pages: number
}

/** État du chargement d'une page de composants. */
export interface EtatComposants {
  page: PageComposants | null
  erreur: string
}

function dependance(transitive: unknown): string {
  if (transitive === true) return 'transitive'
  if (transitive === false) return 'directe'
  return 'inconnue'
}

function composant(c: ComposantApi, rang: number): ComposantAffiche {
  const id = texte(c?.id)
  return {
    cle: id ?? `rang-${rang}`,
    nom: texte(c?.name),
    version: texte(c?.version),
    purl: texte(c?.purl),
    dependance: dependance(c?.transitive),
  }
}

/** Conversion d'une page de l'API : composants dans l'ordre du serveur, total, page et nombre de pages. */
export function pageComposants(reponse: PageComposantsApi | null | undefined): PageComposants {
  const items = Array.isArray(reponse?.items) ? reponse.items : []
  const taille = Math.max(entierPositif(reponse?.size), 1)
  const page = entierPositif(reponse?.page)
  // Total jamais inférieur aux composants reçus ; une page vide n'en atteste aucun.
  const atteste = items.length > 0 ? page * taille + items.length : 0
  const total = Math.max(entierPositif(reponse?.total), atteste)
  return {
    composants: items.map(composant),
    page,
    taille,
    total,
    pages: Math.max(1, Math.ceil(total / taille)),
  }
}

/** Phrase de synthèse de la page affichée. */
export function syntheseComposants(p: PageComposants): string {
  if (p.total === 0) return 'Aucun composant inventorié.'
  if (p.composants.length === 0) return `${p.total} composants inventoriés ; cette page est vide.`
  const premier = p.page * p.taille + 1
  const dernier = p.page * p.taille + p.composants.length
  const nombre = p.total === 1 ? '1 composant inventorié' : `${p.total} composants inventoriés`
  return `${nombre} ; composants ${premier} à ${dernier} affichés.`
}

/** Libellé de la position dans la pagination. */
export function libellePage(p: PageComposants): string {
  return `Page ${p.page + 1} sur ${p.pages}`
}

/** Message d'erreur du chargement des composants. */
export function messageErreurComposants(statut: number | undefined): string {
  if (statut === 401) return 'Connectez-vous pour consulter les composants.'
  if (statut === 403 || statut === 404) return 'Composants introuvables ou hors de vos espaces.'
  return 'Composants indisponibles pour le moment.'
}

/**
 * Charge une page des composants inventoriés d'un projet ; `null` si l'appel a été annulé. Une page au-delà de la
 * dernière (inventaire remplacé entre deux pages) est remplacée par la dernière page, en un seul appel de plus.
 * `lire` est `fetchComposantsProjet`.
 */
export async function chargerComposants(
  projetId: string,
  page: number,
  lire: (projetId: string, page: number, signal?: AbortSignal) => Promise<PageComposantsApi>,
  signal?: AbortSignal,
): Promise<EtatComposants | null> {
  if (!estUuid(projetId)) return { page: null, erreur: 'Identifiant de projet invalide : un UUID est attendu.' }
  const demandee = Number.isInteger(page) && page >= 0 ? page : 0
  try {
    let resultat = pageComposants(await lire(projetId, demandee, signal))
    if (signal?.aborted) return null
    if (resultat.composants.length === 0 && resultat.total > 0 && resultat.page > 0) {
      resultat = pageComposants(await lire(projetId, resultat.pages - 1, signal))
      if (signal?.aborted) return null
    }
    return { page: resultat, erreur: '' }
  } catch (e) {
    if (annule(e, signal)) return null
    return { page: null, erreur: messageErreurComposants(((e ?? {}) as ErreurAppel).status) }
  }
}
