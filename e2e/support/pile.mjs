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

/** Port des sources et forges simulées (support/sources-simulees.mjs). */
export const PORT_SOURCES = Number(process.env.ARGOS_E2E_PORT_SOURCES ?? 8099)
const SOURCES = `http://localhost:${PORT_SOURCES}`

/** Tâches planifiées qui joindraient une source externe : désactivées (aucun appel à Internet). */
const SANS_COLLECTE_EXTERNE = {
  ARGOS_REGLEMENTAIRE_COLLECTE_CRON: 'off',
  ARGOS_ARTISTE_COLLECTE_CRON: 'off',
  ARGOS_RADAR_CRON: 'off',
  ARGOS_ACTIVITE_CADENCE: 'off',
  ARGOS_VEILLE_WEB_CADENCE: 'off',
}

/**
 * Chaîne automatisée (automatiser-chaine-veille, tâche 4.3 ; [PROPOSITION] PI-1) : la synchronisation des sources
 * d'avis (J1) et l'import des inventaires (J3) restent actifs, rapprochés à 5 secondes, et n'interrogent que les
 * sources et forges simulées ; jetons de forge fictifs.
 */
const CHAINE_SIMULEE = {
  ARGOS_INGESTION_PLANIFICATION_CADENCE: '5s',
  ARGOS_INVENTORY_IMPORT_CRON: '*/5 * * * * ?',
  ARGOS_SOURCES_OSV_EXPORT_URL: `${SOURCES}/osv-export`,
  ARGOS_SOURCES_OSV_API_URL: `${SOURCES}/osv-api`,
  ARGOS_SOURCES_OSV_ECOSYSTEMS: 'Maven',
  ARGOS_SOURCES_NVD_URL: `${SOURCES}/nvd`,
  ARGOS_SOURCES_KEV_URL: `${SOURCES}/kev.json`,
  ARGOS_SOURCES_EPSS_URL: `${SOURCES}/epss`,
  ARGOS_SOURCES_CERTFR_FEED_URL: `${SOURCES}/certfr/feed/`,
  ARGOS_SOURCES_CERTFR_BASE_URL: `${SOURCES}/certfr/`,
  ARGOS_SCM_GITLAB_URL: `${SOURCES}/gitlab`,
  ARGOS_SCM_GITHUB_URL: `${SOURCES}/github`,
  ARGOS_GITLAB_TOKEN: 'jeton-fictif-e2e',
  ARGOS_GITHUB_TOKEN: 'jeton-fictif-e2e',
}

/**
 * Variables du serveur de la pile, communes à quarkus:dev et à l'image (compose.image.yaml les reprend par leur nom,
 * sans valeur : Docker Compose les lit dans l'environnement du lancement).
 */
export const ENVIRONNEMENT_SERVEUR = Object.freeze({ ...SANS_COLLECTE_EXTERNE, ...CHAINE_SIMULEE })

/** Arguments `docker compose` de la pile : base seule, ou base, Keycloak et image. */
export function argumentsCompose() {
  const fichiers = [join(E2E, 'compose.yaml'), ...(IMAGE ? [join(E2E, 'compose.image.yaml')] : [])]
  return ['compose', ...fichiers.flatMap((fichier) => ['-f', fichier])]
}
