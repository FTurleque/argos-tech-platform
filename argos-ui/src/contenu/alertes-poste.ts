// Suivi des alertes du poste dans la couche contenu (changement notifier-sur-le-poste :
// NOT-01, NOT-03, NOT-06). Module pur, sans import : testé directement par `node --test`.
//
// Contrat serveur (`alertes-digests`, `AlertesResource`) : le client lit les alertes du canal
// DESKTOP à l'état EN_ATTENTE, les présente, puis confirme leur remise (POST .../remise). Une alerte
// non confirmée reste EN_ATTENTE côté serveur : après une coupure, la lecture suivante la
// renvoie, sans perte. Les identifiants déjà traités pendant la session évitent toute double
// notification si la confirmation échoue (NOT-06-S1).

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
  readonly titre: string
  readonly gravite: string | null
  readonly projets: number
}

export type Connexion = 'INCONNUE' | 'CONNECTE' | 'HORS_LIGNE' | 'NON_AUTORISE'

/** État présenté à l'utilisateur. */
export interface EtatSuivi {
  readonly connexion: Connexion
  /** Alertes ouvertes : reçues sur ce poste et pas encore consultées dans l'application (NOT-03). */
  readonly ouvertes: number
  /** Alertes arrivées à la reconnexion qui suit une coupure (NOT-06), sinon 0. */
  readonly recuesPendantCoupure: number
}

/** Mémoire des alertes ouvertes : identifiants seulement, jamais leur contenu. */
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
  compteur(n: number): void
  /** Reçoit chaque changement d'état. */
  surEtat(etat: EtatSuivi): void
  stockage?: StockageOuvertes
  /** Planifie `fn` dans `ms` millisecondes ; renvoie de quoi l'annuler. */
  planifier?(fn: () => void, ms: number): () => void
}

/** Intervalle entre deux lectures quand tout va bien. */
export const INTERVALLE_MS = 30_000
/** Première attente après un échec, doublée à chaque échec suivant (arc42 §8.5). */
export const REPRISE_INITIALE_MS = 5_000
/** Attente maximale entre deux tentatives. */
export const REPRISE_MAX_MS = 300_000
/** Nombre maximal d'alertes ouvertes mémorisées. */
export const OUVERTES_MAX = 500

/** Attente avant la prochaine tentative après `echecs` échecs consécutifs (attente croissante). */
export function delaiReprise(echecs: number): number {
  if (echecs <= 0) return INTERVALLE_MS
  return Math.min(REPRISE_INITIALE_MS * 2 ** Math.min(echecs - 1, 20), REPRISE_MAX_MS)
}

function echapperRegex(texte: string): string {
  return texte.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Titre sans le nom des projets cités par l'alerte : une notification ne nomme aucun projet
 * (NOT-05), même si le titre public du signal en reprend un.
 */
export function titreSansProjets(titre: string, noms: ReadonlyArray<string>): string {
  let resultat = titre
  for (const nom of noms) {
    const propre = nom.trim()
    if (propre.length < 2) continue
    resultat = resultat.replace(new RegExp(echapperRegex(propre), 'gi'), '…')
  }
  return resultat
}

/** Réduction d'une alerte reçue à ce que le poste a le droit d'afficher (NOT-05). */
export function alertePoste(alerte: AlerteRecue): AlertePoste {
  const ids = new Set<string>()
  const noms: string[] = []
  for (const element of alerte.contenu?.elements ?? []) {
    for (const projet of element.projets ?? []) {
      ids.add(projet.id ?? projet.nom ?? '')
      if (typeof projet.nom === 'string') noms.push(projet.nom)
    }
  }
  return {
    id: alerte.id,
    titre: titreSansProjets(alerte.titre, noms),
    gravite: alerte.gravite,
    projets: ids.size,
  }
}

function statut(erreur: unknown): number | undefined {
  if (erreur && typeof erreur === 'object' && 'status' in erreur) {
    const s = (erreur as { status?: unknown }).status
    return typeof s === 'number' ? s : undefined
  }
  return undefined
}

/**
 * Suivi des alertes du poste : lecture périodique, notification, confirmation de remise, compteur
 * des alertes ouvertes et reprise après une coupure.
 */
export class SuiviAlertesPoste {
  private readonly d: DependancesSuivi
  /** Alertes déjà traitées pendant la session (notifiées ou comptées), confirmées ou non. */
  private readonly traitees = new Set<string>()
  private ouvertes: string[]
  private connexion: Connexion = 'INCONNUE'
  private echecs = 0
  private recuesPendantCoupure = 0
  private annuler: (() => void) | null = null
  private actif = false

  constructor(dependances: DependancesSuivi) {
    this.d = dependances
    this.ouvertes = this.lireOuvertes()
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
      recues = await this.d.lister()
    } catch (erreur) {
      const s = statut(erreur)
      this.connexion = s === 401 || s === 403 ? 'NON_AUTORISE' : 'HORS_LIGNE'
      this.echecs += 1
      this.recuesPendantCoupure = 0
      this.publier()
      return delaiReprise(this.echecs)
    }
    const apresCoupure = this.connexion === 'HORS_LIGNE' || this.connexion === 'NON_AUTORISE'
    this.connexion = 'CONNECTE'
    this.echecs = 0
    let nouvelles = 0
    const aTraiter = [...recues]
      .filter((a) => a.canal === 'DESKTOP' && a.etat === 'EN_ATTENTE')
      .sort((a, b) => String(a.creeLe ?? '').localeCompare(String(b.creeLe ?? '')))
    for (const alerte of aTraiter) {
      if (!this.traitees.has(alerte.id)) {
        this.traitees.add(alerte.id)
        nouvelles += 1
        // Seules les alertes immédiates sont notifiées et comptées ; un résumé est seulement remis.
        if (alerte.nature === 'IMMEDIATE') {
          this.ajouterOuverte(alerte.id)
          try {
            await this.d.notifier(alertePoste(alerte))
          } catch {
            // notification refusée ou indisponible : l'alerte reste comptée et visible (NOT-01-S2)
          }
        }
      }
      try {
        await this.d.remettre(alerte.id)
      } catch {
        // confirmation perdue : l'alerte reviendra à la prochaine lecture, sans nouvelle notification
      }
    }
    // Information gardée jusqu'à sa lecture (oublierCoupure) ou jusqu'à la prochaine coupure.
    if (apresCoupure) this.recuesPendantCoupure = nouvelles
    this.publier()
    return INTERVALLE_MS
  }

  /** L'utilisateur a consulté l'alerte dans l'application : elle n'est plus ouverte (NOT-03). */
  marquerConsultee(id: string): void {
    const avant = this.ouvertes.length
    this.ouvertes = this.ouvertes.filter((o) => o !== id)
    if (this.ouvertes.length !== avant) {
      this.ecrireOuvertes()
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
    const planifier = this.d.planifier ?? ((fn: () => void, ms: number) => {
      const t = setTimeout(fn, ms)
      return () => clearTimeout(t)
    })
    this.annuler = planifier(() => void this.boucle(), attente)
  }

  private ajouterOuverte(id: string): void {
    if (this.ouvertes.includes(id)) return
    this.ouvertes = [...this.ouvertes, id].slice(-OUVERTES_MAX)
    this.ecrireOuvertes()
  }

  private lireOuvertes(): string[] {
    try {
      const ids = this.d.stockage?.lire() ?? []
      return Array.isArray(ids) ? ids.filter((i) => typeof i === 'string').slice(-OUVERTES_MAX) : []
    } catch {
      return []
    }
  }

  private ecrireOuvertes(): void {
    try {
      this.d.stockage?.ecrire(this.ouvertes)
    } catch {
      // stockage indisponible : le compteur reste juste pour la session
    }
  }

  private publier(): void {
    const etat = this.etat
    this.d.compteur(etat.ouvertes)
    this.d.surEtat(etat)
  }
}
