// Fonctions pures du flux OIDC Authorization Code + PKCE de l'IHM (changement connecter-ihm-oidc,
// ACC-07, ACC-08). Aucune dépendance, aucun état, aucun accès au DOM ni au stockage du navigateur :
// testées par node:test (tests/oidc.test.mjs). Les jetons ne sont jamais journalisés ici.

/** Paramètres de l'IHM auprès du fournisseur d'identité (variables Vite). */
export interface ConfigOidc {
  /** Émetteur : URL du royaume, par exemple http://localhost:8180/realms/argos. */
  emetteur: string
  /** Client public déclaré dans le royaume (`argos-ui`). */
  clientId: string
  /** Portées demandées ; `openid` est toujours présente. */
  portee: string
}

/** Points d'accès lus dans le document de découverte du fournisseur. */
export interface Decouverte {
  issuer: string
  authorization_endpoint: string
  token_endpoint: string
  end_session_endpoint: string | null
}

/** Jetons tenus en mémoire par la session (jamais persistés). */
export interface Jetons {
  acces: string
  renouvellement: string | null
  identite: string | null
  /** Instant (ms depuis l'époque) où le jeton d'accès expire. */
  expireLe: number
}

/** Retour du fournisseur après l'autorisation : un code, ou un refus. */
export type RetourAutorisation =
  | { type: 'code'; code: string }
  | { type: 'refus'; erreur: string }
  | { type: 'state-invalide' }
  | { type: 'emetteur-invalide' }
  | { type: 'absent' }

/** Paramètres de retour retirés de l'adresse une fois lus (ACC-07-S2). */
export const PARAMETRES_RETOUR = ['code', 'state', 'session_state', 'iss', 'error', 'error_description', 'error_uri'] as const

const SANS_BARRE_FINALE = /\/+$/

/** Émetteur normalisé : sans barre oblique finale. */
export function normaliserEmetteur(emetteur: string): string {
  return emetteur.trim().replace(SANS_BARRE_FINALE, '')
}

/** Encodage base64url sans remplissage (RFC 7636, annexe A). */
export function base64Url(octets: Uint8Array): string {
  let binaire = ''
  for (const octet of octets) binaire += String.fromCharCode(octet)
  return btoa(binaire).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Chaîne aléatoire base64url issue de `nombreOctets` octets du générateur cryptographique. */
export function aleatoire(nombreOctets = 32, source: Crypto = globalThis.crypto): string {
  const octets = new Uint8Array(nombreOctets)
  source.getRandomValues(octets)
  return base64Url(octets)
}

/** Défi PKCE S256 : BASE64URL(SHA-256(vérificateur)) (RFC 7636 §4.2). */
export async function defiPkce(verificateur: string, source: Crypto = globalThis.crypto): Promise<string> {
  const empreinte = await source.subtle.digest('SHA-256', new TextEncoder().encode(verificateur))
  return base64Url(new Uint8Array(empreinte))
}

/**
 * Valide le document de découverte : l'émetteur annoncé doit être exactement celui configuré
 * (OpenID Connect Discovery §4.3) et les points d'autorisation et de jetons présents.
 */
export function lireDecouverte(document: unknown, emetteurAttendu: string): Decouverte {
  const decouverte = (document ?? {}) as Record<string, unknown>
  const attendu = normaliserEmetteur(emetteurAttendu)
  if (typeof decouverte.issuer !== 'string' || normaliserEmetteur(decouverte.issuer) !== attendu) {
    throw new Error('Émetteur inattendu dans le document de découverte')
  }
  for (const champ of ['authorization_endpoint', 'token_endpoint']) {
    if (typeof decouverte[champ] !== 'string' || !/^https?:\/\//.test(decouverte[champ] as string)) {
      throw new Error(`Point d'accès ${champ} absent du document de découverte`)
    }
  }
  return {
    issuer: decouverte.issuer,
    authorization_endpoint: decouverte.authorization_endpoint as string,
    token_endpoint: decouverte.token_endpoint as string,
    end_session_endpoint: typeof decouverte.end_session_endpoint === 'string' ? decouverte.end_session_endpoint : null,
  }
}

/** Portée demandée : celle configurée, `openid` ajoutée si elle manque. */
export function portee(configuree: string): string {
  const valeurs = configuree.split(/\s+/).filter(Boolean)
  return (valeurs.includes('openid') ? valeurs : ['openid', ...valeurs]).join(' ')
}

/** URL d'autorisation : code d'autorisation, PKCE S256 et state (ACC-07). */
export function construireUrlAutorisation(parametres: {
  pointAutorisation: string
  clientId: string
  redirectUri: string
  portee: string
  state: string
  defi: string
}): string {
  const url = new URL(parametres.pointAutorisation)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('client_id', parametres.clientId)
  url.searchParams.set('redirect_uri', parametres.redirectUri)
  url.searchParams.set('scope', portee(parametres.portee))
  url.searchParams.set('state', parametres.state)
  url.searchParams.set('code_challenge', parametres.defi)
  url.searchParams.set('code_challenge_method', 'S256')
  url.searchParams.set('response_mode', 'query')
  return url.toString()
}

/** Comparaison en temps constant de deux chaînes (le state ne fuit pas par la durée). */
function egales(premiere: string, seconde: string): boolean {
  if (premiere.length !== seconde.length) return false
  let difference = 0
  for (let position = 0; position < premiere.length; position += 1) {
    difference |= premiere.charCodeAt(position) ^ seconde.charCodeAt(position)
  }
  return difference === 0
}

function sansBarreFinale(adresse: string): string {
  return adresse.endsWith('/') ? adresse.slice(0, -1) : adresse
}

/**
 * Lit le retour du fournisseur dans une chaîne de requête (`?code=…&state=…`).
 * Le state doit être exactement celui de la demande en cours (ACC-07-S3) ; un refus du
 * fournisseur n'est accepté qu'avec ce state, sinon il est traité comme un retour falsifié.
 * Le paramètre `iss`, quand le fournisseur l'envoie, doit désigner l'émetteur attendu (RFC 9207).
 */
export function lireRetour(
  recherche: string,
  stateAttendu: string | null,
  emetteurAttendu?: string,
): RetourAutorisation {
  const parametres = new URLSearchParams(recherche.startsWith('?') ? recherche.slice(1) : recherche)
  const code = parametres.get('code')
  const erreur = parametres.get('error')
  if (code === null && erreur === null) return { type: 'absent' }
  const state = parametres.get('state')
  if (!stateAttendu || state === null || !egales(state, stateAttendu)) return { type: 'state-invalide' }
  // RFC 9207 : un émetteur annoncé dans le retour doit être celui qui a reçu la demande.
  const iss = parametres.get('iss')
  if (emetteurAttendu !== undefined && iss !== null && sansBarreFinale(iss) !== sansBarreFinale(emetteurAttendu)) {
    return { type: 'emetteur-invalide' }
  }
  if (erreur !== null) return { type: 'refus', erreur }
  return code ? { type: 'code', code } : { type: 'state-invalide' }
}

/** Vrai si la chaîne de requête porte un retour du fournisseur à traiter. */
export function contientRetour(recherche: string): boolean {
  const parametres = new URLSearchParams(recherche.startsWith('?') ? recherche.slice(1) : recherche)
  return parametres.has('code') || parametres.has('error')
}

/**
 * Adresse de la page débarrassée des paramètres de retour (code, state…), positionnée sur la vue
 * `route` de l'historique par « hash » (ACC-07-S2) : le code ne reste ni dans la barre d'adresse
 * ni dans l'historique.
 */
export function urlNettoyee(href: string, route: string): string {
  const url = new URL(href)
  for (const nom of PARAMETRES_RETOUR) url.searchParams.delete(nom)
  url.hash = cheminInterne(route)
  return url.toString()
}

/**
 * Chemin de vue interne sûr : commence par une seule barre oblique et ne contient ni schéma ni
 * caractère de contrôle ; sinon la vue d'accueil. Empêche toute redirection ouverte après la connexion.
 */
export function cheminInterne(chemin: unknown): string {
  if (typeof chemin !== 'string') return '/'
  if (!chemin.startsWith('/') || chemin.startsWith('//') || chemin.includes('\\')) return '/'
  if (chemin.length > 512 || contientControle(chemin)) return '/'
  return chemin
}

/** Vrai si la chaîne contient un caractère de contrôle (U+0000 à U+001F, U+007F). */
function contientControle(texte: string): boolean {
  for (let position = 0; position < texte.length; position += 1) {
    const codeCaractere = texte.charCodeAt(position)
    if (codeCaractere < 0x20 || codeCaractere === 0x7f) return true
  }
  return false
}

/** Corps du formulaire d'échange du code (RFC 6749 §4.1.3, RFC 7636 §4.5). */
export function corpsEchangeCode(parametres: {
  code: string
  redirectUri: string
  clientId: string
  verificateur: string
}): URLSearchParams {
  return new URLSearchParams({
    grant_type: 'authorization_code',
    code: parametres.code,
    redirect_uri: parametres.redirectUri,
    client_id: parametres.clientId,
    code_verifier: parametres.verificateur,
  })
}

/** Corps du formulaire de renouvellement (RFC 6749 §6). */
export function corpsRenouvellement(parametres: { renouvellement: string; clientId: string }): URLSearchParams {
  return new URLSearchParams({
    grant_type: 'refresh_token',
    refresh_token: parametres.renouvellement,
    client_id: parametres.clientId,
  })
}

/** Lit la réponse du point de jetons ; rejette une réponse sans jeton d'accès ou sans durée valide. */
export function lireReponseJetons(reponse: unknown, maintenant: number): Jetons {
  const corps = (reponse ?? {}) as Record<string, unknown>
  if (typeof corps.access_token !== 'string' || corps.access_token === '') {
    throw new Error('Réponse du fournisseur sans jeton d’accès')
  }
  if (typeof corps.token_type === 'string' && corps.token_type.toLowerCase() !== 'bearer') {
    throw new Error('Type de jeton inattendu')
  }
  const duree = Number(corps.expires_in)
  if (!Number.isFinite(duree) || duree <= 0) throw new Error('Durée de validité du jeton absente')
  return {
    acces: corps.access_token,
    renouvellement: typeof corps.refresh_token === 'string' && corps.refresh_token !== '' ? corps.refresh_token : null,
    identite: typeof corps.id_token === 'string' && corps.id_token !== '' ? corps.id_token : null,
    expireLe: maintenant + duree * 1000,
  }
}

/** Marge de renouvellement : 20 % de la durée restante, au plus 60 s. */
export const MARGE_MAX_MS = 60_000

/**
 * Délai avant renouvellement : avant l'expiration, d'une marge de 20 % de la durée restante
 * plafonnée à 60 s (ACC-08-S2). Jamais négatif.
 */
export function delaiRenouvellement(expireLe: number, maintenant: number): number {
  const restant = expireLe - maintenant
  if (restant <= 0) return 0
  return Math.max(0, restant - Math.min(MARGE_MAX_MS, restant * 0.2))
}

/** Vrai si le jeton d'accès est encore valide à l'instant donné. */
export function jetonValide(jetons: Jetons | null, maintenant: number): boolean {
  return jetons !== null && maintenant < jetons.expireLe
}

/** URL de fin de session du fournisseur (OpenID Connect RP-Initiated Logout 1.0), ou null. */
export function construireUrlDeconnexion(parametres: {
  pointFinSession: string | null
  clientId: string
  jetonIdentite: string | null
  retourApresDeconnexion: string | null
}): string | null {
  if (!parametres.pointFinSession) return null
  const url = new URL(parametres.pointFinSession)
  url.searchParams.set('client_id', parametres.clientId)
  if (parametres.jetonIdentite) url.searchParams.set('id_token_hint', parametres.jetonIdentite)
  if (parametres.retourApresDeconnexion) url.searchParams.set('post_logout_redirect_uri', parametres.retourApresDeconnexion)
  return url.toString()
}

/** Messages présentés pour un refus du fournisseur : jamais le texte reçu dans l'adresse (ACC-07-S4). */
const MESSAGES_REFUS: Record<string, string> = {
  access_denied: 'Le fournisseur d’identité a refusé la connexion.',
  login_required: 'Le fournisseur d’identité demande une nouvelle authentification.',
  temporarily_unavailable: 'Le fournisseur d’identité est momentanément indisponible.',
  server_error: 'Le fournisseur d’identité a rencontré une erreur.',
}

/** Message explicite d'un refus du fournisseur, choisi dans une liste fixe. */
export function messageRefus(erreur: string): string {
  return MESSAGES_REFUS[erreur] ?? 'La connexion a été refusée par le fournisseur d’identité.'
}
