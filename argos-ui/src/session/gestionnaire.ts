// Session OIDC de l'IHM (changement connecter-ihm-oidc : ACC-07, ACC-08, ACC-10).
// Toutes les dépendances sont injectées (réseau, horloge, minuteries, transport du retour) :
// la logique est testée par node:test sans navigateur ni fournisseur réel (tests/session.test.mjs).
// Les jetons vivent dans un champ privé de l'instance, jamais dans un stockage du navigateur,
// jamais dans une adresse ni dans un journal (ACC-08).
import {
  aleatoire,
  cheminInterne,
  construireUrlAutorisation,
  construireUrlDeconnexion,
  corpsEchangeCode,
  corpsRenouvellement,
  defiPkce,
  delaiRenouvellement,
  jetonValide,
  lireDecouverte,
  lireRetour,
  lireReponseJetons,
  messageRefus,
  normaliserEmetteur,
  type ConfigOidc,
  type Decouverte,
  type Jetons,
} from './oidc.ts'

/** États présentés par l'IHM. */
export type EtatSession =
  | 'ANONYME'
  | 'CONNEXION_EN_COURS'
  | 'CONNECTE'
  | 'EXPIREE'
  | 'REFUSEE'
  | 'ERREUR'

/** Demande d'autorisation en cours : ce qu'il faut pour vérifier le retour et échanger le code. */
export interface DemandeAutorisation {
  state: string
  verificateur: string
  redirectUri: string
  /** Vue à afficher une fois connecté. */
  retour: string
}

/**
 * Transport du retour du fournisseur, propre à la plateforme :
 * - portail web : redirection de la page entière, retour sur la même adresse ;
 * - client desktop : navigateur du système et redirection vers l'interface de bouclage (ACC-10).
 */
export interface TransportConnexion {
  /** Adresse de redirection à déclarer dans la demande. */
  preparer(): Promise<string>
  /**
   * Lance l'autorisation. Renvoie la chaîne de requête du retour (desktop) ou null quand la page
   * quitte l'application (web : le retour est repris au chargement suivant par `reprendre`).
   */
  lancer(url: string, demande: DemandeAutorisation): Promise<string | null>
  /** Retour en attente au chargement de la page (web), lu puis effacé ; null sinon. */
  reprendre(): { recherche: string; demande: DemandeAutorisation | null } | null
  /** Adresse où revenir après la fin de session (web), ou null (desktop). */
  retourApresDeconnexion(): string | null
  /** Ouvre la fin de session du fournisseur. */
  deconnecter(url: string): void
}

export interface DependancesSession {
  config: ConfigOidc
  fetch: (url: string, init?: RequestInit) => Promise<Response>
  transport: TransportConnexion
  maintenant?: () => number
  planifier?: (fn: () => void, ms: number) => unknown
  annuler?: (minuterie: unknown) => void
  crypto?: Crypto
  /** Appelé à chaque changement d'état, avec un message à afficher le cas échéant. */
  surEtat?: (etat: EtatSession, message: string | null) => void
}

export class GestionnaireSession {
  #d: DependancesSession
  #maintenant: () => number
  #planifier: (fn: () => void, ms: number) => unknown
  #annuler: (minuterie: unknown) => void
  #crypto: Crypto
  #decouverte: Decouverte | null = null
  #jetons: Jetons | null = null
  #minuterie: unknown = null
  #etat: EtatSession = 'ANONYME'
  #message: string | null = null

  constructor(d: DependancesSession) {
    this.#d = d
    this.#maintenant = d.maintenant ?? (() => Date.now())
    this.#planifier = d.planifier ?? ((fn, ms) => setTimeout(fn, ms))
    this.#annuler = d.annuler ?? ((m) => clearTimeout(m as ReturnType<typeof setTimeout>))
    this.#crypto = d.crypto ?? globalThis.crypto
  }

  get etat(): EtatSession {
    return this.#etat
  }

  get message(): string | null {
    return this.#message
  }

  /** Jeton d'accès s'il est encore valide, sinon null (ACC-09-S1). */
  jetonAcces(): string | null {
    return jetonValide(this.#jetons, this.#maintenant()) ? this.#jetons!.acces : null
  }

  /**
   * Démarre la connexion pour revenir ensuite sur la vue `retour` (ACC-07-S1).
   * Renvoie la vue à afficher quand la connexion aboutit dans la même page (desktop), sinon null.
   */
  async seConnecter(retour: string): Promise<string | null> {
    this.#changer('CONNEXION_EN_COURS', null)
    let demande: DemandeAutorisation
    let url: string
    try {
      const decouverte = await this.#decouvrir()
      const verificateur = aleatoire(32, this.#crypto)
      demande = {
        state: aleatoire(32, this.#crypto),
        verificateur,
        redirectUri: await this.#d.transport.preparer(),
        retour: cheminInterne(retour),
      }
      url = construireUrlAutorisation({
        pointAutorisation: decouverte.authorization_endpoint,
        clientId: this.#d.config.clientId,
        redirectUri: demande.redirectUri,
        portee: this.#d.config.portee,
        state: demande.state,
        defi: await defiPkce(verificateur, this.#crypto),
      })
    } catch {
      this.#changer('ERREUR', 'Fournisseur d’identité injoignable. Réessayez plus tard.')
      return null
    }
    let recherche: string | null
    try {
      recherche = await this.#d.transport.lancer(url, demande)
    } catch {
      this.#changer('ERREUR', 'La connexion n’a pas pu être lancée.')
      return null
    }
    if (recherche === null) return null
    return this.terminer(recherche, demande)
  }

  /** Reprend, au chargement de la page, un retour du fournisseur laissé dans l'adresse (web). */
  async reprendre(): Promise<string | null> {
    const enAttente = this.#d.transport.reprendre()
    if (!enAttente) return null
    if (!enAttente.demande) {
      this.#changer('ERREUR', 'Retour de connexion inattendu : aucune connexion n’était en cours.')
      return null
    }
    return this.terminer(enAttente.recherche, enAttente.demande)
  }

  /**
   * Vérifie le retour (state), échange le code contre les jetons avec le vérificateur PKCE et
   * planifie le renouvellement. Renvoie la vue à afficher, ou null en cas d'échec.
   */
  async terminer(recherche: string, demande: DemandeAutorisation): Promise<string | null> {
    const retour = lireRetour(recherche, demande.state)
    if (retour.type === 'refus') {
      this.#changer('REFUSEE', messageRefus(retour.erreur))
      return null
    }
    if (retour.type !== 'code') {
      // state absent ou différent : aucun échange de code (ACC-07-S3).
      this.#changer('ERREUR', 'Retour de connexion invalide : la demande a été ignorée.')
      return null
    }
    try {
      const decouverte = await this.#decouvrir()
      const jetons = await this.#demanderJetons(decouverte.token_endpoint, corpsEchangeCode({
        code: retour.code,
        redirectUri: demande.redirectUri,
        clientId: this.#d.config.clientId,
        verificateur: demande.verificateur,
      }))
      this.#installer(jetons)
      this.#changer('CONNECTE', null)
      return cheminInterne(demande.retour)
    } catch {
      this.#oublier()
      this.#changer('ERREUR', 'La connexion n’a pas pu aboutir auprès du fournisseur d’identité.')
      return null
    }
  }

  /** Renouvelle les jetons ; en cas d'échec, la session passe à l'état expiré (ACC-08-S3). */
  async renouveler(): Promise<boolean> {
    const renouvellement = this.#jetons?.renouvellement
    if (!renouvellement || !this.#decouverte) {
      this.expirer()
      return false
    }
    try {
      const jetons = await this.#demanderJetons(this.#decouverte.token_endpoint, corpsRenouvellement({
        renouvellement,
        clientId: this.#d.config.clientId,
      }))
      // Le fournisseur peut ne pas renvoyer de nouveau jeton d'identité ou de renouvellement.
      this.#installer({
        ...jetons,
        renouvellement: jetons.renouvellement ?? renouvellement,
        identite: jetons.identite ?? this.#jetons?.identite ?? null,
      })
      return true
    } catch {
      this.expirer()
      return false
    }
  }

  /** La session n'est plus valide (renouvellement refusé, API qui répond 401) : jetons oubliés. */
  expirer(): void {
    const etaitConnecte = this.#etat === 'CONNECTE'
    this.#oublier()
    if (etaitConnecte) this.#changer('EXPIREE', 'Votre session a expiré. Reconnectez-vous pour continuer.')
  }

  /** Déconnexion : jetons oubliés, puis fin de session chez le fournisseur (ACC-08-S4). */
  deconnecter(): void {
    const identite = this.#jetons?.identite ?? null
    this.#oublier()
    this.#changer('ANONYME', null)
    const url = construireUrlDeconnexion({
      pointFinSession: this.#decouverte?.end_session_endpoint ?? null,
      clientId: this.#d.config.clientId,
      jetonIdentite: identite,
      retourApresDeconnexion: this.#d.transport.retourApresDeconnexion(),
    })
    if (url) this.#d.transport.deconnecter(url)
  }

  async #decouvrir(): Promise<Decouverte> {
    if (this.#decouverte) return this.#decouverte
    const emetteur = normaliserEmetteur(this.#d.config.emetteur)
    const reponse = await this.#d.fetch(`${emetteur}/.well-known/openid-configuration`, {
      headers: { Accept: 'application/json' },
    })
    if (!reponse.ok) throw new Error(`découverte : ${reponse.status}`)
    this.#decouverte = lireDecouverte(await reponse.json(), emetteur)
    return this.#decouverte
  }

  async #demanderJetons(point: string, corps: URLSearchParams): Promise<Jetons> {
    const reponse = await this.#d.fetch(point, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: corps.toString(),
      // Aucun cookie : le client public s'authentifie par le vérificateur PKCE seul.
      credentials: 'omit',
    })
    if (!reponse.ok) throw new Error(`jetons : ${reponse.status}`)
    return lireReponseJetons(await reponse.json(), this.#maintenant())
  }

  #installer(jetons: Jetons): void {
    this.#jetons = jetons
    if (this.#minuterie !== null) this.#annuler(this.#minuterie)
    this.#minuterie = this.#planifier(() => {
      this.#minuterie = null
      void this.renouveler()
    }, delaiRenouvellement(jetons.expireLe, this.#maintenant()))
  }

  #oublier(): void {
    this.#jetons = null
    if (this.#minuterie !== null) this.#annuler(this.#minuterie)
    this.#minuterie = null
  }

  #changer(etat: EtatSession, message: string | null): void {
    this.#etat = etat
    this.#message = message
    this.#d.surEtat?.(etat, message)
  }
}
