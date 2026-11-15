/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ARGOS_API_URL?: string
}

/** Pont exposé par le client desktop Electron (argos-desktop/src/preload.js). */
interface ArgosDesktopBridge {
  readonly platform: string
  notify(title: string, body: string): Promise<boolean>
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
