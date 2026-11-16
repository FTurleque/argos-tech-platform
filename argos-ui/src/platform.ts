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
