/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ARGOS_API_URL?: string
}

/** Pont exposé par le client desktop Electron (argos-desktop/src/preload.js). */
interface ArgosDesktopBridge {
  readonly platform: string
  notify(title: string, body: string): Promise<boolean>
  /** Notifie une alerte : identifiant, gravité et nombre de projets seulement (NOT-05). */
  notifyAlert(alerte: { id: string; gravite: string | null; projets: number }): Promise<boolean>
  /** Nombre d'alertes ouvertes affiché dans la zone de notification (NOT-03). */
  setAlertCount(n: number): Promise<boolean>
  /** Abonnement aux clics sur les notifications d'alerte (NOT-02) ; renvoie de quoi se désabonner. */
  onOpenAlert(callback: (id: string) => void): () => void
  getSettings(): Promise<ArgosReglagesPoste | null>
  setSettings(reglages: Partial<Pick<ArgosReglagesPoste, 'notificationsPoste' | 'demarrageSession'>>): Promise<ArgosReglagesPoste | null>
  onSettingsChanged(callback: (reglages: ArgosReglagesPoste) => void): () => void
}

/** Réglages du poste tenus par le client desktop (NOT-04, Q-2). */
interface ArgosReglagesPoste {
  readonly notificationsPoste: boolean
  readonly demarrageSession: boolean
  readonly demarrageSessionDisponible: boolean
}

interface Window {
  argosDesktop?: ArgosDesktopBridge
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
