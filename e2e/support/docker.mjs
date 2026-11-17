// Chemin absolu de l'exécutable Docker, pris parmi des emplacements d'installation fixes : les tests
// ne dépendent pas du PATH. ARGOS_E2E_DOCKER désigne un autre emplacement.
import { existsSync } from 'node:fs'

const EMPLACEMENTS = [
  'C:/Program Files/Docker/Docker/resources/bin/docker.exe',
  '/usr/bin/docker',
  '/usr/local/bin/docker',
  '/opt/homebrew/bin/docker',
  '/Applications/Docker.app/Contents/Resources/bin/docker',
]

/** Chemin de l'exécutable Docker ; lève une erreur explicite s'il est introuvable. */
export function cheminDocker() {
  const choisi = process.env.ARGOS_E2E_DOCKER ?? EMPLACEMENTS.find((chemin) => existsSync(chemin))
  if (!choisi || !existsSync(choisi)) {
    throw new Error('Docker introuvable : installez-le ou indiquez son chemin dans ARGOS_E2E_DOCKER.')
  }
  return choisi
}
