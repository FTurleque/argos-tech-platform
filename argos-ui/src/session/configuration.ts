// Configuration OIDC de l'IHM, propre à l'instance (ACC-11, design.md D1) : l'émetteur public et le client
// public sont lus dans la réponse publique de GET /api/v1/system/info au démarrage de la page, jamais figés
// dans le bundle. Aucune valeur de repli : sans réponse valable, aucune connexion n'est tentée.
// Fonction pure, réseau injecté (tests/session.test.mjs).
import type { ConfigOidc } from './oidc.ts'

const CHEMIN_IDENTITE = '/api/v1/system/info'
const STATUT_REUSSITE_MIN = 200
const STATUT_REUSSITE_MAX = 299

/** Portée demandée au fournisseur : l'IHM n'a besoin que de l'identité de la session. */
const PORTEE = 'openid'

/** Échec de lecture de la configuration de l'instance : serveur injoignable ou réponse inattendue. */
export class ConfigurationIndisponible extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options)
    this.name = 'ConfigurationIndisponible'
  }
}

function texteNonVide(valeur: unknown): valeur is string {
  return typeof valeur === 'string' && valeur.trim() !== ''
}

/**
 * Lit l'émetteur et le client de l'IHM auprès du serveur.
 * @throws ConfigurationIndisponible si le serveur ne répond pas, répond en erreur ou ne désigne pas d'émetteur
 */
export async function lireConfigurationOidc(
  fetcher: (url: string, init?: RequestInit) => Promise<Response>,
  adresseServeur: string,
): Promise<ConfigOidc> {
  let reponse: Response
  try {
    reponse = await fetcher(`${adresseServeur}${CHEMIN_IDENTITE}`, { headers: { Accept: 'application/json' } })
  } catch (cause) {
    throw new ConfigurationIndisponible('serveur injoignable', { cause })
  }
  if (reponse.status < STATUT_REUSSITE_MIN || reponse.status > STATUT_REUSSITE_MAX) {
    throw new ConfigurationIndisponible(`réponse ${reponse.status} du serveur`)
  }
  let corps: Record<string, unknown>
  try {
    corps = (await reponse.json()) as Record<string, unknown>
  } catch (cause) {
    throw new ConfigurationIndisponible('réponse du serveur illisible', { cause })
  }
  if (!texteNonVide(corps.oidcIssuer) || !texteNonVide(corps.oidcClientId)) {
    throw new ConfigurationIndisponible('le serveur ne désigne ni émetteur ni client OIDC')
  }
  return { emetteur: corps.oidcIssuer, clientId: corps.oidcClientId, portee: PORTEE }
}
