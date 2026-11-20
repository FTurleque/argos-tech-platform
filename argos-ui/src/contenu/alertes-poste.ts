// Suivi des alertes du poste dans la couche contenu (changement notifier-sur-le-poste :
// NOT-01, NOT-03, NOT-06). Module pur, sans autre import que les statuts HTTP partagés : testé
// directement par `node --test`.
//
// Contrat serveur (`alertes-digests`, `AlertesResource`) : le client lit les alertes du canal
// DESKTOP à l'état EN_ATTENTE, les présente, puis confirme leur remise (POST .../remise). Une alerte
// non confirmée reste EN_ATTENTE côté serveur : après une coupure, la lecture suivante la
// renvoie, sans perte. Les identifiants déjà traités (500 derniers au plus, gardés dans la mémoire
// locale de l'IHM) évitent une double notification si la confirmation échoue, y compris après un
// rechargement de la fenêtre (NOT-06-S1). Seul un arrêt de l'application entre la notification et
// l'enregistrement de cet identifiant peut encore produire un doublon.
//
// Le texte de la notification ne reprend pas le titre de l'alerte : le titre public d'un signal
// nomme souvent le composant touché, et une notification ne nomme aucun projet ni composant
// (NOT-05-S1). Seuls l'identifiant, la gravité et le nombre de projets sont transmis au poste.

import { nonAutorise } from '../api/statuts-http.ts'

/** Élément d'une alerte tel que le renvoie le serveur (schéma `ContenuAlerte.Element`), réduit. */
export interface ElementAlerte {
  readonly projets?: ReadonlyArray<{ readonly id?: string; readonly nom?: string | null }>
}

/** Alerte telle que la renvoie `GET /api/v1/alertes` (schéma `AlerteVue`), réduite à l'utile. */
export interface AlerteRecue {
  readonly id: string
  readonly nature: string
  readonly canal: string
  readonly etat: string
  readonly titre: string
  readonly gravite: string | null
  readonly contenu?: { readonly elements?: ReadonlyArray<ElementAlerte> } | null
  readonly creeLe?: string
}

/** Ce qui est transmis au poste pour une notification : rien d'autre (NOT-05). */
export interface AlertePoste {
  readonly id: string
  readonly gravite: string | null
  readonly projets: number
}

export type Connexion = 'INCONNUE' | 'CONNECTE' | 'HORS_LIGNE' | 'NON_AUTORISE'

/** État présenté à l'utilisateur. */
export interface EtatSuivi {
  readonly connexion: Connexion
  /** Alertes ouvertes : reçues sur ce poste et pas encore consultées dans l'application (NOT-03). */
  readonly ouvertes: number
  /**
   * Alertes immédiates arrivées à la reconnexion qui suit une coupure (NOT-06), sinon 0. La
   * première connexion de la session n'est pas une reprise après coupure.
   */
  readonly recuesPendantCoupure: number
}

/** Mémoire locale d'une liste d'identifiants d'alertes, jamais leur contenu. */
export interface StockageOuvertes {
  lire(): string[]
  ecrire(ids: string[]): void
}

export interface DependancesSuivi {
  /** Alertes DESKTOP EN_ATTENTE de l'utilisateur ; rejette avec `{ status }` si le serveur refuse. */
  lister(): Promise<ReadonlyArray<AlerteRecue>>
  /** Confirme la remise d'une alerte au poste. */
  remettre(id: string): Promise<unknown>
  /** Demande une notification système ; renvoie false si elle n'est pas affichée. */
  notifier(alerte: AlertePoste): Promise<boolean>
  /** Met à jour le compteur de la zone de notification. */
  compteur(nombre: number): void
  /** Reçoit chaque changement d'état. */
  surEtat(etat: EtatSuivi): void
  /** Alertes ouvertes (NOT-03). */
  stockage?: StockageOuvertes
  /** Alertes déjà traitées : évite une seconde notification après un rechargement (NOT-06). */
  stockageTraitees?: StockageOuvertes
  /** Planifie `fn` dans `ms` millisecondes ; renvoie de quoi l'annuler. */
  planifier?(action: () => void, delai: number): () => void
}

/** Intervalle entre deux lectures quand tout va bien. */
export const INTERVALLE_MS = 30_000
/** Première attente après un échec, doublée à chaque échec suivant (arc42 §8.5). */
export const REPRISE_INITIALE_MS = 5_000
/** Attente maximale entre deux tentatives. */
export const REPRISE_MAX_MS = 300_000
/** Nombre maximal d'alertes ouvertes mémorisées. */
export const OUVERTES_MAX = 500
/** Nombre maximal d'identifiants d'alertes traitées mémorisés. */
export const TRAITEES_MAX = 500

/** Attente avant la prochaine tentative après `echecs` échecs consécutifs (attente croissante). */
export function delaiReprise(echecs: number): number {
  if (echecs <= 0) return INTERVALLE_MS
  return Math.min(REPRISE_INITIALE_MS * 2 ** Math.min(echecs - 1, 20), REPRISE_MAX_MS)
}

/** Réduction d'une alerte reçue à ce que le poste a le droit d'afficher (NOT-05). */
export function alertePoste(alerte: AlerteRecue): AlertePoste {
  const ids = new Set<string>()
  for (const element of alerte.contenu?.elements ?? []) {
    for (const projet of element.projets ?? []) {
      ids.add(projet.id ?? projet.nom ?? '')
    }
  }
  return {
    id: alerte.id,
    gravite: alerte.gravite,
    projets: ids.size,
  }
}

function statut(erreur: unknown): number | undefined {
  if (erreur && typeof erreur === 'object' && 'status' in erreur) {
    const valeur = (erreur as { status?: unknown }).status
    return typeof valeur === 'number' ? valeur : undefined
  }
  return undefined
}

/** Alertes du canal DESKTOP encore EN_ATTENTE, des plus anciennes aux plus récentes. */
function alertesATraiter(recues: ReadonlyArray<AlerteRecue>): AlerteRecue[] {
  return [...recues]
    .filter((alerte) => alerte.canal === 'DESKTOP' && alerte.etat === 'EN_ATTENTE')
    .sort((premiere, seconde) => String(premiere.creeLe ?? '').localeCompare(String(seconde.creeLe ?? '')))
}

/** Identifiants mémorisés ; stockage absent, illisible ou invalide : liste vide. */
function lireIds(stockage: StockageOuvertes | undefined, max: number): string[] {
  try {
    const ids = stockage?.lire() ?? []
    return Array.isArray(ids) ? ids.filter((identifiant) => typeof identifiant === 'string').slice(-max) : []
  } catch {
    return []
  }
}

function ecrireIds(stockage: StockageOuvertes | undefined, ids: string[]): void {
  try {
    stockage?.ecrire(ids)
  } catch {
    // stockage indisponible : la liste reste juste pour la session
  }
}

/**
 * Suivi des alertes du poste : lecture périodique, notification, confirmation de remise, compteur
 * des alertes ouvertes et reprise après une coupure.
 */
export class SuiviAlertesPoste {
  private readonly dependances: DependancesSuivi
  /** Alertes déjà traitées (notifiées ou remises), confirmées ou non, dans l'ordre d'arrivée. */
  private traitees: string[]
  private ouvertes: string[]
  private connexion: Connexion = 'INCONNUE'
  /** Vrai dès qu'une lecture a réussi pendant la session. */
  private dejaConnecte = false
  private echecs = 0
  private recuesPendantCoupure = 0
  private annuler: (() => void) | null = null
  private actif = false

  constructor(dependances: DependancesSuivi) {
    this.dependances = dependances
    this.ouvertes = lireIds(this.dependances.stockage, OUVERTES_MAX)
    this.traitees = lireIds(this.dependances.stockageTraitees, TRAITEES_MAX)
  }

  get etat(): EtatSuivi {
    return {
      connexion: this.connexion,
      ouvertes: this.ouvertes.length,
      recuesPendantCoupure: this.recuesPendantCoupure,
    }
  }

  /** Démarre les lectures périodiques ; la première a lieu tout de suite. */
  demarrer(): void {
    if (this.actif) return
    this.actif = true
    this.publier()
    void this.boucle()
  }

  arreter(): void {
    this.actif = false
    this.annuler?.()
    this.annuler = null
  }

  /**
   * Une lecture : notifie les nouvelles alertes, des plus anciennes aux plus récentes, puis
   * confirme leur remise. Renvoie l'attente avant la lecture suivante.
   */
  async cycle(): Promise<number> {
    let recues: ReadonlyArray<AlerteRecue>
    try {
      recues = await this.dependances.lister()
    } catch (erreur) {
      return this.echecLecture(erreur)
    }
    const apresCoupure = this.lectureReussie()
    let nouvelles = 0
    for (const alerte of alertesATraiter(recues)) {
      if (await this.traiter(alerte)) nouvelles += 1
    }
    // Information gardée jusqu'à sa lecture (oublierCoupure) ou jusqu'à la prochaine coupure.
    if (apresCoupure) this.recuesPendantCoupure = nouvelles
    this.publier()
    return INTERVALLE_MS
  }

  /** L'utilisateur a consulté l'alerte dans l'application : elle n'est plus ouverte (NOT-03). */
  marquerConsultee(id: string): void {
    const avant = this.ouvertes.length
    this.ouvertes = this.ouvertes.filter((ouverte) => ouverte !== id)
    if (this.ouvertes.length !== avant) {
      ecrireIds(this.dependances.stockage, this.ouvertes)
      this.publier()
    }
  }

  /** Efface l'information « alertes reçues pendant la coupure » une fois lue. */
  oublierCoupure(): void {
    if (this.recuesPendantCoupure === 0) return
    this.recuesPendantCoupure = 0
    this.publier()
  }

  private async boucle(): Promise<void> {
    if (!this.actif) return
    const attente = await this.cycle()
    if (!this.actif) return
    const planifier = this.dependances.planifier ?? ((action: () => void, delai: number) => {
      const minuterie = setTimeout(action, delai)
      return () => clearTimeout(minuterie)
    })
    this.annuler = planifier(() => void this.boucle(), attente)
  }

  /** Lecture refusée ou impossible : état de la connexion, compte des échecs, attente croissante. */
  private echecLecture(erreur: unknown): number {
    this.connexion = nonAutorise(statut(erreur)) ? 'NON_AUTORISE' : 'HORS_LIGNE'
    this.echecs += 1
    this.recuesPendantCoupure = 0
    this.publier()
    return delaiReprise(this.echecs)
  }

  /**
   * Lecture réussie : la session passe à l'état connecté. Renvoie vrai pour une reprise après
   * coupure, c'est-à-dire une lecture réussie après un échec, une fois la session connectée.
   */
  private lectureReussie(): boolean {
    const apresCoupure = this.dejaConnecte && (this.connexion === 'HORS_LIGNE' || this.connexion === 'NON_AUTORISE')
    this.dejaConnecte = true
    this.connexion = 'CONNECTE'
    this.echecs = 0
    return apresCoupure
  }

  /**
   * Une alerte lue : notifiée si elle est nouvelle et immédiate, puis remise dans tous les cas.
   * Renvoie vrai pour une nouvelle alerte immédiate.
   */
  private async traiter(alerte: AlerteRecue): Promise<boolean> {
    let nouvelleImmediate = false
    if (!this.traitees.includes(alerte.id)) {
      this.ajouterTraitee(alerte.id)
      // Seules les alertes immédiates sont notifiées et comptées ; un résumé est seulement remis.
      if (alerte.nature === 'IMMEDIATE') {
        nouvelleImmediate = true
        this.ajouterOuverte(alerte.id)
        await this.notifierSansEchec(alerte)
      }
    }
    await this.remettreSansEchec(alerte.id)
    return nouvelleImmediate
  }

  private async notifierSansEchec(alerte: AlerteRecue): Promise<void> {
    try {
      await this.dependances.notifier(alertePoste(alerte))
    } catch {
      // notification refusée ou indisponible : l'alerte reste comptée et visible (NOT-01-S2)
    }
  }

  private async remettreSansEchec(id: string): Promise<void> {
    try {
      await this.dependances.remettre(id)
    } catch {
      // confirmation perdue : l'alerte reviendra à la prochaine lecture, sans nouvelle notification
    }
  }

  private ajouterOuverte(id: string): void {
    if (this.ouvertes.includes(id)) return
    this.ouvertes = [...this.ouvertes, id].slice(-OUVERTES_MAX)
    ecrireIds(this.dependances.stockage, this.ouvertes)
  }

  private ajouterTraitee(id: string): void {
    this.traitees = [...this.traitees, id].slice(-TRAITEES_MAX)
    ecrireIds(this.dependances.stockageTraitees, this.traitees)
  }

  private publier(): void {
    const etat = this.etat
    this.dependances.compteur(etat.ouvertes)
    this.dependances.surEtat(etat)
  }
}
