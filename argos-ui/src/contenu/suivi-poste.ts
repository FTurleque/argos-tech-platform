// Branchement du suivi des alertes du poste sur l'API, le pont desktop et l'état réactif de l'IHM
// (changement notifier-sur-le-poste). La logique est dans alertes-poste.ts.
import { reactive } from 'vue'
import { confirmerRemise, fetchAlertesPosteEnAttente } from '../api/client'
import { afficherCompteur, isDesktop, notifierAlerte } from '../platform'
import { SuiviAlertesPoste, type EtatSuivi, type StockageOuvertes } from './alertes-poste'

const CLE_OUVERTES = 'argos.alertes-ouvertes'

/** Mémoire locale de l'IHM : identifiants des alertes ouvertes seulement, jamais leur contenu. */
const stockageLocal: StockageOuvertes = {
  lire() {
    const brut = window.localStorage.getItem(CLE_OUVERTES)
    return brut ? (JSON.parse(brut) as string[]) : []
  },
  ecrire(ids) {
    window.localStorage.setItem(CLE_OUVERTES, JSON.stringify(ids))
  },
}

export const etatPoste = reactive<EtatSuivi>({ connexion: 'INCONNUE', ouvertes: 0, recuesPendantCoupure: 0 })

let suivi: SuiviAlertesPoste | null = null

/** Démarre le suivi des alertes dans le client desktop ; sans effet dans le navigateur. */
export function demarrerSuiviPoste(): void {
  if (!isDesktop || suivi) return
  suivi = new SuiviAlertesPoste({
    lister: fetchAlertesPosteEnAttente,
    remettre: confirmerRemise,
    notifier: notifierAlerte,
    compteur: afficherCompteur,
    surEtat: (etat) => Object.assign(etatPoste, etat),
    stockage: stockageLocal,
  })
  suivi.demarrer()
}

/** L'alerte a été affichée dans l'application : elle n'est plus ouverte. */
export function alerteConsultee(id: string): void {
  suivi?.marquerConsultee(id)
}

export function oublierCoupure(): void {
  suivi?.oublierCoupure()
}
