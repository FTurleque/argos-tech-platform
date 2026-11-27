// Pile e2e choisie par l'environnement, partagée par scripts/lancer.mjs, playwright.config.ts et support/donnees.ts.
//   ARGOS_E2E_IMAGE : étiquette de l'image construite (Dockerfile). Définie, le serveur et le portail web sont ceux de
//     l'image (e2e/compose.image.yaml) ; absente, Playwright démarre quarkus:dev et Vite.
//   ARGOS_E2E_REUTILISER=1 : réutilise un serveur et un Vite déjà lancés sur les ports de la pile (mise au point).
//     Sans elle, un port déjà occupé fait échouer le lancement : aucun serveur inconnu n'est testé à son insu.
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const E2E = join(dirname(fileURLToPath(import.meta.url)), '..')

/** Étiquette de l'image testée, ou chaîne vide pour la pile de développement. */
export const IMAGE = process.env.ARGOS_E2E_IMAGE ?? ''

/** Vrai si la pile réutilise des serveurs déjà lancés. */
export const REUTILISER = process.env.ARGOS_E2E_REUTILISER === '1'

/** Arguments `docker compose` de la pile : base seule, ou base, Keycloak et image. */
export function argumentsCompose() {
  const fichiers = [join(E2E, 'compose.yaml'), ...(IMAGE ? [join(E2E, 'compose.image.yaml')] : [])]
  return ['compose', ...fichiers.flatMap((fichier) => ['-f', fichier])]
}
