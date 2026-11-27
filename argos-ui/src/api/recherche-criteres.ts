// Mise en forme des mots et filtres de la recherche de veille pour l'API (RCH-01, RCH-02, RCH-04).
// Module pur, sans autre import que les statuts HTTP partagés : testé directement par `node --test`. Aucune règle métier ici
// (arc42 §5.1) : la validation, les droits et la sémantique des filtres restent dans argos-server
// (`TraductionCriteres`, `RechercheVeille`).

import {
  STATUT_CONFLIT,
  STATUT_INTERDIT,
  STATUT_INTROUVABLE,
  STATUT_NON_AUTHENTIFIE,
  STATUT_REQUETE_INVALIDE,
  STATUT_TROP_DE_DEMANDES,
} from './statuts-http.ts'

/** Mots et filtres d'une recherche (schéma OpenAPI `CriteresRecherche`) ; attribut absent : filtre absent. */
export interface CriteresRecherche {
  texte?: string
  projets?: string[]
  technologies?: string[]
  types?: string[]
  gravite?: string
  horizonJours?: number
  profils?: string[]
}

/** Saisie de l'écran de recherche. Les profils ne se saisissent pas : ils viennent d'une vue rappelée. */
export interface FormulaireRecherche {
  texte: string
  projets: string[]
  /** Technologies séparées par des virgules. */
  technologies: string
  types: string[]
  gravite: string
  /** Période : éléments des N derniers jours ; null : toute période. */
  horizonJours: number | null
  profils: string[]
}

/** Taille de page demandée à l'API (valeur par défaut du serveur). */
export const TAILLE_PAGE = 20

/** Périodes proposées ; `horizon` de l'API, en jours. */
export const PERIODES: ReadonlyArray<{ jours: number | null; libelle: string }> = [
  { jours: null, libelle: 'Toute période' },
  { jours: 7, libelle: '7 derniers jours' },
  { jours: 30, libelle: '30 derniers jours' },
  { jours: 90, libelle: '90 derniers jours' },
  { jours: 365, libelle: '12 derniers mois' },
]

/** Types de contenu admis par l'API (`type`), avec leur libellé. */
export const TYPES_CONTENU: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'AVIS_SECURITE', libelle: 'Avis de sécurité' },
  { code: 'ARTICLE', libelle: 'Article' },
  { code: 'CHANGEMENT_PAGE', libelle: 'Changement de page surveillée' },
]

/** Gravités minimales admises par l'API (`gravite`), de la plus faible à la plus forte. */
export const GRAVITES: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'LOW', libelle: 'faible' },
  { code: 'MODERATE', libelle: 'modérée' },
  { code: 'MEDIUM', libelle: 'moyenne' },
  { code: 'HIGH', libelle: 'élevée' },
  { code: 'CRITICAL', libelle: 'critique' },
]

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** Vrai pour un identifiant UUID (projet, vue) ; sert à ne pas construire d'URL avec une saisie arbitraire. */
export function estUuid(valeur: unknown): valeur is string {
  return typeof valeur === 'string' && UUID.test(valeur.trim())
}

function valeurs(liste: ReadonlyArray<string> | undefined): string[] {
  const vues = new Set<string>()
  for (const valeur of liste ?? []) {
    const nettoyee = typeof valeur === 'string' ? valeur.trim() : ''
    if (nettoyee !== '') vues.add(nettoyee)
  }
  return [...vues]
}

/** Formulaire vide : aucun mot, aucun filtre. */
export function formulaireVide(): FormulaireRecherche {
  return { texte: '', projets: [], technologies: '', types: [], gravite: '', horizonJours: null, profils: [] }
}

/** Critères envoyés à l'API : valeurs rognées et dédoublonnées, filtres vides omis. */
export function criteresDuFormulaire(formulaire: FormulaireRecherche): CriteresRecherche {
  const criteres: CriteresRecherche = {}
  const texte = formulaire.texte.trim()
  if (texte !== '') criteres.texte = texte
  const projets = valeurs(formulaire.projets)
  if (projets.length > 0) criteres.projets = projets
  const technologies = valeurs(formulaire.technologies.split(','))
  if (technologies.length > 0) criteres.technologies = technologies
  const types = valeurs(formulaire.types)
  if (types.length > 0) criteres.types = types
  if (formulaire.gravite.trim() !== '') criteres.gravite = formulaire.gravite.trim()
  if (formulaire.horizonJours !== null && Number.isInteger(formulaire.horizonJours)) criteres.horizonJours = formulaire.horizonJours
  const profils = valeurs(formulaire.profils)
  if (profils.length > 0) criteres.profils = profils
  return criteres
}

/** Formulaire prérempli avec les critères d'une vue rappelée. */
export function formulaireDesCriteres(criteres: CriteresRecherche | null | undefined): FormulaireRecherche {
  const connus = criteres ?? {}
  return {
    texte: connus.texte ?? '',
    projets: valeurs(connus.projets),
    technologies: valeurs(connus.technologies).join(', '),
    types: valeurs(connus.types),
    gravite: connus.gravite ?? '',
    horizonJours: typeof connus.horizonJours === 'number' ? connus.horizonJours : null,
    profils: valeurs(connus.profils),
  }
}

/** Vrai si aucun mot ni filtre n'est saisi. */
export function criteresVides(criteres: CriteresRecherche): boolean {
  return Object.keys(criteres).length === 0
}

/**
 * Paramètres de `GET /api/v1/recherche` : `q`, `projet`, `technologie`, `type`, `profil` (répétés),
 * `gravite`, `horizon`, `page`, `taille`.
 */
export function parametresRecherche(criteres: CriteresRecherche, page = 0, taille = TAILLE_PAGE): URLSearchParams {
  const parametres = new URLSearchParams()
  if (criteres.texte) parametres.set('q', criteres.texte)
  for (const projet of criteres.projets ?? []) parametres.append('projet', projet)
  for (const technologie of criteres.technologies ?? []) parametres.append('technologie', technologie)
  for (const type of criteres.types ?? []) parametres.append('type', type)
  if (criteres.gravite) parametres.set('gravite', criteres.gravite)
  if (criteres.horizonJours !== undefined) parametres.set('horizon', String(criteres.horizonJours))
  for (const profil of criteres.profils ?? []) parametres.append('profil', profil)
  parametres.set('page', String(page))
  parametres.set('taille', String(taille))
  return parametres
}

/** Paramètres de pagination seuls (`GET /api/v1/vues/{id}/resultats`). */
export function parametresPage(page = 0, taille = TAILLE_PAGE): URLSearchParams {
  return new URLSearchParams({ page: String(page), taille: String(taille) })
}

/** Corps de `POST /api/v1/vues` (schéma `NouvelleVue`). */
export function corpsNouvelleVue(nom: string, criteres: CriteresRecherche): { nom: string; criteres: CriteresRecherche } {
  return { nom: nom.trim(), criteres }
}

/** Libellé d'une période (`horizonJours`). */
export function libellePeriode(jours: number | null | undefined): string {
  if (jours === null || jours === undefined) return 'Toute période'
  return PERIODES.find((periode) => periode.jours === jours)?.libelle ?? `${jours} derniers jours`
}

/** Libellé d'un type de contenu. */
export function libelleType(code: string): string {
  return TYPES_CONTENU.find((typeContenu) => typeContenu.code === code)?.libelle ?? code
}

/** Libellé d'une gravité ; null : non précisée. */
export function libelleGravite(code: string | null | undefined): string {
  if (!code) return 'non précisée'
  return GRAVITES.find((gravite) => gravite.code === code)?.libelle ?? code
}

/** Nombre de pages d'un total de résultats. */
export function nombrePages(total: number, taille: number): number {
  if (!(total > 0) || !(taille > 0)) return 0
  return Math.ceil(total / taille)
}

/** Nombre de résultats en toutes lettres ; un total plafonné vaut « plus de » ce total. */
function libelleNombre(total: number, plafonne: boolean): string {
  if (plafonne) return `Plus de ${total.toLocaleString('fr-FR')} résultats`
  return total === 1 ? '1 résultat' : `${total} résultats`
}

/**
 * Annonce des résultats pour les technologies d'assistance (zone `aria-live`). Un total plafonné
 * (RCH-07) s'annonce « plus de » ce total.
 */
export function annonceResultats(total: number, page: number, taille: number, plafonne = false): string {
  if (total <= 0) return 'Aucun résultat.'
  const pages = nombrePages(total, taille)
  const nombre = libelleNombre(total, plafonne)
  return pages > 1 ? `${nombre}, page ${page + 1} sur ${pages}.` : `${nombre}.`
}

/** Projet cité par un résultat (schéma `ProjetVise`). */
export interface ProjetConnu {
  projetId: string
  projet: string | null
}

/**
 * Projets proposés dans le filtre : ceux déjà connus, complétés par ceux des résultats, sans doublon,
 * triés par nom. L'API n'expose pas la liste des projets de l'utilisateur.
 */
export function projetsConnus(connus: ReadonlyArray<ProjetConnu>, nouveaux: ReadonlyArray<ProjetConnu>): ProjetConnu[] {
  const parId = new Map<string, ProjetConnu>()
  for (const projet of [...connus, ...nouveaux]) {
    if (!estUuid(projet.projetId)) continue
    const deja = parId.get(projet.projetId)
    if (!deja || (!deja.projet && projet.projet)) parId.set(projet.projetId, { projetId: projet.projetId, projet: projet.projet ?? null })
  }
  return [...parId.values()].sort((premier, second) =>
    (premier.projet ?? premier.projetId).localeCompare(second.projet ?? second.projetId, 'fr'))
}

/** Message d'erreur affiché pour un refus de l'API, selon l'opération. */
export function messageErreur(
  operation: 'recherche' | 'vue' | 'enregistrement' | 'suppression' | 'liste',
  status: number | undefined,
  message: string,
): string {
  if (status === undefined) return message
  if (status === STATUT_NON_AUTHENTIFIE) return 'Connectez-vous pour rechercher dans votre veille.'
  if (status === STATUT_INTROUVABLE) {
    if (operation === 'recherche' || operation === 'enregistrement') return 'Projet introuvable ou hors de vos espaces.'
    return 'Vue introuvable.'
  }
  if (status === STATUT_REQUETE_INVALIDE || status === STATUT_CONFLIT) return message
  if (status === STATUT_INTERDIT) return 'Accès refusé : vos droits ne permettent pas cette opération.'
  if (status === STATUT_TROP_DE_DEMANDES) return 'Trop de demandes en peu de temps : réessayez dans quelques instants.'
  return `Le serveur a répondu ${status}.`
}
