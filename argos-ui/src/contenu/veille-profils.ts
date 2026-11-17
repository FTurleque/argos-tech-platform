// Présentation de la veille par profils (couche contenu, PRF-02, décision Q-3) et du choix des profils
// (PRF-01). Module pur, sans appel réseau ni dépendance à Vue : testé directement par `node --test`.
//
// Aucune règle métier ici (arc42 §5.1) : les éléments reçus sont déjà autorisés, filtrés, dédoublonnés et
// accompagnés de leurs mentions par le serveur. Ce module les range pour l'affichage, sans en retirer ni en
// ajouter, et nomme leur origine.
import type {
  Abonnement,
  ElementVeilleProfils,
  FiltreAbonnement,
  ProfilCatalogue,
} from '../api/veille'

/** Clé du groupe des éléments que seuls des abonnements PERSONAL retiennent. */
export const GROUPE_ABONNEMENTS = 'abonnements-personnels'

/** Groupe d'affichage : une rubrique d'un profil actif, ou les abonnements personnels. */
export interface GroupeVeille {
  readonly cle: string
  readonly titre: string
  /** Libellé du profil de la rubrique ; null pour le groupe des abonnements personnels. */
  readonly profil: string | null
  readonly elements: readonly ElementVeilleProfils[]
}

/** Libellé d'un profil d'après le catalogue, ou son identifiant s'il n'y figure pas. */
export function libelleProfil(identifiant: string, catalogue: readonly ProfilCatalogue[]): string {
  return catalogue.find((p) => p.identifiant === identifiant)?.libelle ?? identifiant
}

/**
 * Range les éléments par rubrique, dans l'ordre de première apparition des rubriques. Chaque élément
 * apparaît une seule fois, dans le groupe de sa première rubrique ; ses autres rubriques restent affichées
 * avec lui. Les éléments sans rubrique, que seuls des abonnements PERSONAL retiennent, forment le dernier
 * groupe. Aucun élément n'est omis : un élément sans rubrique ni abonnement, que le serveur ne restitue pas,
 * irait lui aussi dans ce dernier groupe.
 */
export function grouperParRubrique(
  elements: readonly ElementVeilleProfils[],
  catalogue: readonly ProfilCatalogue[] = [],
): GroupeVeille[] {
  const groupes = new Map<string, { titre: string; profil: string | null; elements: ElementVeilleProfils[] }>()
  const sansRubrique: ElementVeilleProfils[] = []
  for (const element of elements) {
    const premiere = element.rubriques?.[0]
    if (!premiere) {
      sansRubrique.push(element)
      continue
    }
    const cle = `${premiere.profil}/${premiere.identifiant}`
    let groupe = groupes.get(cle)
    if (!groupe) {
      groupe = { titre: premiere.libelle, profil: libelleProfil(premiere.profil, catalogue), elements: [] }
      groupes.set(cle, groupe)
    }
    groupe.elements.push(element)
  }
  const resultat: GroupeVeille[] = [...groupes].map(([cle, g]) => ({ cle, ...g }))
  if (sansRubrique.length > 0) {
    resultat.push({ cle: GROUPE_ABONNEMENTS, titre: 'Abonnements personnels', profil: null, elements: sansRubrique })
  }
  return resultat
}

const TYPES_FILTRE: Readonly<Record<string, string>> = {
  TECHNOLOGY: 'technologie',
  PACKAGE: 'paquet',
  VENDOR: 'éditeur',
  THEME: 'thème',
  DOMAIN: 'domaine',
  SOURCE: 'source',
  PROJECT: 'projet',
  SEVERITY: 'gravité minimale',
}

function libelleFiltre(f: FiltreAbonnement): string {
  return `${TYPES_FILTRE[f.type] ?? f.type.toLowerCase()} : ${f.value}`
}

/** Nom d'un abonnement personnel d'après ses critères, par exemple « Abonnement personnel (technologie : rust) ». */
export function libelleAbonnement(abonnement: Abonnement | undefined): string {
  if (!abonnement || abonnement.filters.length === 0) return 'Abonnement personnel'
  return `Abonnement personnel (${abonnement.filters.map(libelleFiltre).join(', ')})`
}

/**
 * Origine d'un élément, telle que le serveur la restitue : les profils actifs dont il relève, puis les
 * abonnements PERSONAL qui l'ont retenu. Un abonnement inconnu de la liste fournie est nommé sans critère.
 */
export function originesElement(
  element: ElementVeilleProfils,
  catalogue: readonly ProfilCatalogue[] = [],
  abonnements: readonly Abonnement[] = [],
): string[] {
  const origines = (element.profils ?? []).map((p) => `Profil ${libelleProfil(p, catalogue)}`)
  for (const id of element.abonnements ?? []) {
    origines.push(libelleAbonnement(abonnements.find((a) => a.id === id)))
  }
  return origines
}

const TYPES_ELEMENT: Readonly<Record<string, string>> = {
  AVIS_SECURITE: 'Avis de sécurité',
  FICHE_REGLEMENTAIRE: 'Fiche réglementaire',
}

/** Libellé du type d'un élément ; un type inconnu est affiché tel quel. */
export function libelleType(type: string): string {
  return TYPES_ELEMENT[type] ?? type
}

const GRAVITES: Readonly<Record<string, string>> = {
  CRITICAL: 'critique',
  HIGH: 'élevée',
  MEDIUM: 'moyenne',
  MODERATE: 'moyenne',
  LOW: 'faible',
}

/** Libellé d'une gravité de source ; null si aucune n'est donnée. */
export function libelleGravite(gravite: string | null | undefined): string | null {
  if (!gravite) return null
  return GRAVITES[gravite.toUpperCase()] ?? gravite.toLowerCase()
}

/** Date lisible en français ; la valeur reçue si elle n'est pas une date ; null si absente. */
export function dateLisible(iso: string | null | undefined): string | null {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
}

/**
 * Ensemble complet des profils demandé après un clic sur un profil : ajouté s'il était inactif, retiré
 * sinon. Le serveur décide seul de la validité de l'ensemble (au moins un profil, profils publiés).
 */
export function basculerProfil(actifs: readonly string[], identifiant: string): string[] {
  return actifs.includes(identifiant) ? actifs.filter((p) => p !== identifiant) : [...actifs, identifiant]
}

/** Message affiché pour un échec d'appel de la veille ou des profils. */
export function messageErreurVeille(
  operation: 'veille' | 'profils' | 'choix',
  status: number | undefined,
  message: string,
): string {
  if (status === undefined) return message
  if (status === 401) {
    return operation === 'veille' ? 'Connectez-vous pour consulter votre veille.' : 'Connectez-vous pour choisir vos profils.'
  }
  // Refus métier du serveur (profil inconnu, au moins un profil actif) : son message est affiché tel quel.
  if (status === 400) return message
  if (status === 403) return 'Accès refusé : vos droits ne permettent pas cette opération.'
  if (status === 429) return 'Trop de demandes en peu de temps : réessayez dans quelques instants.'
  return `Le serveur a répondu ${status}.`
}
