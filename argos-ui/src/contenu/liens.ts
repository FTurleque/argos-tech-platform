// Liens vers les sources externes des éléments de veille (AFF-02, sécurité de l'IHM).
// Module pur, sans import : testé directement par `node --test`.

const SCHEMAS_AUTORISES = new Set(['http:', 'https:'])

/**
 * Adresse utilisable dans un `href`, ou null : seules les URL absolues `http` et `https` sont
 * acceptées. Une adresse `javascript:`, `data:` ou relative, venue d'un avis externe, n'est jamais
 * rendue cliquable (le portail web comme le client desktop Electron l'exécuteraient).
 */
export function urlSure(url: string | null | undefined): string | null {
  if (typeof url !== 'string' || url.trim() === '') {
    return null
  }
  let analysee: URL
  try {
    analysee = new URL(url.trim())
  } catch {
    return null
  }
  return SCHEMAS_AUTORISES.has(analysee.protocol) ? analysee.href : null
}
