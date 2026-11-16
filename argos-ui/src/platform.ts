// Ce qui diffère entre le navigateur et le client desktop Electron (ADR-0022).
// L'IHM reste identique ; seules les capacités natives changent.

/** Vrai quand l'IHM tourne dans le client desktop (pont exposé par son preload). */
export const isDesktop: boolean = typeof window !== 'undefined' && window.argosDesktop !== undefined

/**
 * Affiche une notification : notification système dans le desktop,
 * API Notification du navigateur sinon. Renvoie false si elle est refusée.
 */
export async function notify(title: string, body: string): Promise<boolean> {
  if (window.argosDesktop) {
    return window.argosDesktop.notify(title, body)
  }
  if (!('Notification' in window)) return false
  let permission = Notification.permission
  if (permission === 'default') permission = await Notification.requestPermission()
  if (permission !== 'granted') return false
  new Notification(title, { body })
  return true
}

/** Alerte transmise au poste : le processus principal construit lui-même le texte (NOT-05). */
export interface AlerteNotifiable {
  id: string
  gravite: string | null
  projets: number
}

export type ReglagesPoste = ArgosReglagesPoste

/** Notification système d'une alerte ; hors du desktop, aucune notification (canal DESKTOP). */
export async function notifierAlerte(alerte: AlerteNotifiable): Promise<boolean> {
  if (!window.argosDesktop) return false
  return window.argosDesktop.notifyAlert(alerte)
}

/** Compteur d'alertes ouvertes dans la zone de notification (NOT-03). */
export function afficherCompteur(n: number): void {
  void window.argosDesktop?.setAlertCount(n)
}

/** Clic sur la notification d'une alerte (NOT-02) ; renvoie de quoi se désabonner. */
export function surOuvertureAlerte(callback: (id: string) => void): () => void {
  return window.argosDesktop?.onOpenAlert(callback) ?? (() => {})
}

export async function lireReglagesPoste(): Promise<ReglagesPoste | null> {
  return window.argosDesktop ? window.argosDesktop.getSettings() : null
}

export async function changerReglagesPoste(
  reglages: Partial<Pick<ReglagesPoste, 'notificationsPoste' | 'demarrageSession'>>,
): Promise<ReglagesPoste | null> {
  return window.argosDesktop ? window.argosDesktop.setSettings(reglages) : null
}

export function surChangementReglages(callback: (reglages: ReglagesPoste) => void): () => void {
  return window.argosDesktop?.onSettingsChanged(callback) ?? (() => {})
}
