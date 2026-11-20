// Branchement de la session OIDC sur le navigateur, le pont desktop, le client REST et l'état
// réactif de l'IHM (changement connecter-ihm-oidc). La logique est dans gestionnaire.ts et oidc.ts.
import { reactive, watch } from 'vue'
import type { Router } from 'vue-router'
import { ApiError, configurerAutorisation, fetchMoi, type CompteCourant } from '../api/client'
import { CODE_COMPTE_NON_CREE, type Refus } from '../api/autorisation'
import { STATUT_INTERDIT } from '../api/statuts-http'
import { GestionnaireSession, type DemandeAutorisation, type EtatSession, type TransportConnexion } from './gestionnaire'
import { decisionExpiration, decisionGarde } from './garde'
import { contientRetour, urlNettoyee } from './oidc'

const EMETTEUR_PAR_DEFAUT = 'http://localhost:8180/realms/argos'
const CLIENT_PAR_DEFAUT = 'argos-ui'

/**
 * Seule donnée écrite dans le stockage de session du navigateur, et seulement le temps de la
 * redirection vers le fournisseur (portail web) : state, vérificateur PKCE, adresse de retour et vue
 * demandée. Effacée dès le retour, avant toute vérification. Aucun jeton n'y est jamais écrit.
 */
const CLE_DEMANDE = 'argos.oidc.demande'

export interface EtatSessionIhm {
  etat: EtatSession
  message: string | null
  compte: CompteCourant | null
  /** Authentifié, mais aucun compte Argos créé faute d'invitation (ACC-02, Q-2). */
  compteNonCree: boolean
  /** Refus (403) à afficher. */
  refus: string | null
}

export const etatSession = reactive<EtatSessionIhm>({
  etat: 'ANONYME',
  message: null,
  compte: null,
  compteNonCree: false,
  refus: null,
})

function adressePage(): string {
  return `${window.location.origin}${window.location.pathname}`
}

/** Portail web : redirection de la page entière, retour sur la même adresse. */
const transportWeb: TransportConnexion = {
  async preparer() {
    return adressePage()
  },
  async lancer(url: string, demande: DemandeAutorisation) {
    window.sessionStorage.setItem(CLE_DEMANDE, JSON.stringify(demande))
    window.location.assign(url)
    return null
  },
  reprendre() {
    if (!contientRetour(window.location.search)) return null
    const recherche = window.location.search
    let demande: DemandeAutorisation | null = null
    try {
      const brut = window.sessionStorage.getItem(CLE_DEMANDE)
      demande = brut ? (JSON.parse(brut) as DemandeAutorisation) : null
    } catch {
      demande = null
    } finally {
      try {
        window.sessionStorage.removeItem(CLE_DEMANDE)
      } catch {
        // stockage indisponible : rien à effacer
      }
    }
    // Le code et le state quittent la barre d'adresse et l'historique avant leur vérification.
    window.history.replaceState(null, '', urlNettoyee(window.location.href, demande?.retour ?? '/'))
    return { recherche, demande }
  },
  retourApresDeconnexion() {
    return adressePage()
  },
  deconnecter(url: string) {
    window.location.assign(url)
  },
}

/**
 * Client desktop : navigateur du système et redirection vers l'interface de bouclage, menés par
 * le processus principal (ACC-10, RFC 8252). La page du fournisseur ne s'affiche jamais dans
 * l'application.
 */
function transportDesktop(pont: ArgosDesktopBridge): TransportConnexion {
  return {
    async preparer() {
      const redirectUri = await pont.preparerConnexion()
      if (!redirectUri) throw new Error('écoute locale indisponible')
      return redirectUri
    },
    async lancer(url: string) {
      const recherche = await pont.autoriserConnexion(url)
      if (recherche === null) throw new Error('connexion abandonnée ou expirée')
      return recherche
    },
    reprendre() {
      return null
    },
    retourApresDeconnexion() {
      return null
    },
    deconnecter(url: string) {
      // Ouverte dans le navigateur du système, où vit la session du fournisseur (setWindowOpenHandler).
      window.open(url, '_blank', 'noopener')
    },
  }
}

const gestionnaire = new GestionnaireSession({
  config: {
    emetteur: import.meta.env.VITE_ARGOS_OIDC_ISSUER || EMETTEUR_PAR_DEFAUT,
    clientId: import.meta.env.VITE_ARGOS_OIDC_CLIENT_ID || CLIENT_PAR_DEFAUT,
    portee: 'openid',
  },
  fetch: (url, init) => window.fetch(url, init),
  transport: window.argosDesktop ? transportDesktop(window.argosDesktop) : transportWeb,
  surEtat(etat, message) {
    etatSession.etat = etat
    etatSession.message = message
    if (etat !== 'CONNECTE') {
      etatSession.compte = null
      etatSession.compteNonCree = false
      etatSession.refus = null
    }
  },
})

/** Vrai si les vues protégées sont accessibles (session ouverte et compte créé). */
export function sessionOuverte(): boolean {
  return etatSession.etat === 'CONNECTE' && !etatSession.compteNonCree
}

/** Lit le compte courant ; un 403 « inscription sur invitation » ouvre l'écran dédié. */
async function verifierCompte(): Promise<void> {
  try {
    etatSession.compte = await fetchMoi()
    etatSession.compteNonCree = false
  } catch (erreur) {
    if (erreur instanceof ApiError && erreur.status === STATUT_INTERDIT && erreur.code === CODE_COMPTE_NON_CREE) {
      etatSession.compteNonCree = true
    }
  }
}

/** Vue à afficher une fois la connexion aboutie, compte vérifié. */
async function apresConnexion(vue: string | null): Promise<string | null> {
  if (vue === null) return null
  await verifierCompte()
  return etatSession.compteNonCree ? '/compte-non-cree' : vue
}

/** Au chargement : reprend un retour du fournisseur laissé dans l'adresse (portail web). */
export async function demarrerSession(): Promise<string | null> {
  return apresConnexion(await gestionnaire.reprendre())
}

/** Lance la connexion ; renvoie la vue à afficher si elle aboutit sans quitter la page (desktop). */
export async function seConnecter(retour: string): Promise<string | null> {
  return apresConnexion(await gestionnaire.seConnecter(retour))
}

export function seDeconnecter(): void {
  gestionnaire.deconnecter()
}

export function oublierRefus(): void {
  etatSession.refus = null
}

/** Branche le client REST et la garde des vues sur la session (ACC-07-S1, ACC-09). */
export function installerSession(router: Router): void {
  configurerAutorisation({
    jeton: () => gestionnaire.jetonAcces(),
    surRefus(refus: Refus) {
      if (refus === 'NON_AUTHENTIFIE') {
        // L'écran de connexion est ouvert par le suivi de l'état « session expirée » ci-dessous.
        if (gestionnaire.etat === 'CONNECTE') gestionnaire.expirer()
      } else if (refus === 'COMPTE_NON_CREE') {
        etatSession.compteNonCree = true
        void router.push({ name: 'compte-non-cree' })
      } else {
        etatSession.refus = 'Accès refusé : vos droits ne permettent pas cette action.'
      }
    },
  })
  // Session expirée pendant l'affichage d'une vue (renouvellement refusé ou réponse 401) : une vue
  // protégée cède la place à l'écran de connexion, avec l'état « session expirée » (ACC-08-S3, ACC-09-S3).
  watch(
    () => etatSession.etat,
    (etat) => {
      if (etat !== 'EXPIREE') return
      const courante = router.currentRoute.value
      const decision = decisionExpiration({ publique: courante.meta.public === true, chemin: courante.fullPath })
      if (decision.type === 'connexion') void router.push({ name: 'connexion', query: { retour: decision.retour } })
    },
  )
  router.beforeEach((destination) => {
    const decision = decisionGarde({
      publique: destination.meta.public === true,
      connecte: etatSession.etat === 'CONNECTE',
      compteNonCree: etatSession.compteNonCree,
      chemin: destination.fullPath,
    })
    if (decision.type === 'connexion') return { name: 'connexion', query: { retour: decision.retour } }
    if (decision.type === 'compte-non-cree') return { name: 'compte-non-cree' }
    return true
  })
}
