// Client MCP des parcours (exposer-serveur-mcp, tâche 5.2, partie protocole) : ce que fait un assistant
// configuré par un fichier .mcp.json au format de Claude Code, écrit avec fetch, sans dépendance ajoutée
// et sans aucun modèle d'IA.
//   1. lecture du fichier de configuration et expansion des variables ${VAR} et ${VAR:-défaut} ;
//   2. jeton de l'utilisateur obtenu par le flux d'appareil OAuth 2.0 (RFC 8628) du client Keycloak
//      argos-mcp (décision Q-3), l'utilisateur approuvant le code dans un navigateur ;
//   3. requêtes JSON-RPC sur HTTP « streamable » sans état (spécification MCP 2026-07-28) : en-têtes
//      MCP-Protocol-Version, Mcp-Method, Mcp-Name et champs _meta de chaque requête ; réponse JSON ou
//      flux d'événements (text/event-stream).
import { readFileSync } from 'node:fs'
import { expect, type Page } from '@playwright/test'
import { EMETTEUR } from './environnement'

/** Version de protocole demandée par le client (seule admise par le serveur). */
export const VERSION_PROTOCOLE = '2026-07-28'

/** Client Keycloak public des assistants (flux d'appareil seulement). */
export const CLIENT_MCP = 'argos-mcp'

const CLIENT_INFO = { name: 'argos-e2e-client-mcp', version: '1' }

/** Serveur MCP HTTP déclaré dans .mcp.json, après expansion des variables. */
export interface ServeurMcp {
  type: string
  url: string
  headers: Record<string, string>
}

/** Remplace ${VAR} et ${VAR:-défaut}, comme Claude Code ; une variable absente sans défaut est une erreur. */
export function developper(valeur: string, env: Record<string, string | undefined>): string {
  return valeur.replace(/\$\{([A-Za-z_]\w*)(?::-([^}]*))?\}/g, (_tout, nom: string, defaut: string | undefined) => {
    const v = env[nom]
    if (v !== undefined && v !== '') return v
    if (defaut !== undefined) return defaut
    throw new Error(`variable ${nom} absente de l'environnement`)
  })
}

/** Lit le serveur `nom` d'un fichier .mcp.json (clé mcpServers) et développe ses variables. */
export function lireConfiguration(fichier: string, nom: string, env: Record<string, string | undefined>): ServeurMcp {
  const brut = JSON.parse(readFileSync(fichier, 'utf8')) as {
    mcpServers?: Record<string, { type?: string; url?: string; headers?: Record<string, string> }>
  }
  const serveur = brut.mcpServers?.[nom]
  if (!serveur?.url) throw new Error(`${fichier} : serveur MCP « ${nom} » sans adresse`)
  const headers: Record<string, string> = {}
  for (const [cle, valeur] of Object.entries(serveur.headers ?? {})) headers[cle] = developper(valeur, env)
  return { type: serveur.type ?? 'stdio', url: developper(serveur.url, env), headers }
}

// ------------------------------------------------------------------ Flux d'appareil (Q-3)

interface Formulaire {
  [cle: string]: string
}

async function formulaire(url: string, champs: Formulaire): Promise<{ statut: number; corps: Record<string, unknown> }> {
  const reponse = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(champs),
  })
  return { statut: reponse.status, corps: (await reponse.json()) as Record<string, unknown> }
}

/** Approuve le code d'appareil sur la page de vérification du Keycloak de test, comme l'utilisateur. */
async function approuver(page: Page, pageVerification: string, utilisateur: string): Promise<void> {
  const connexion = page.locator('#username')
  const code = page.locator('input[name="device_user_code"]')
  // Écran « Grant Access to argos-mcp » : bouton d'accord du consentement de Keycloak.
  const accord = page.locator('[name="accept"]')
  await page.goto(pageVerification)
  await expect(connexion.or(code).first()).toBeVisible()
  if (await code.isVisible()) {
    // Code non prérempli : l'utilisateur le valide tel qu'affiché.
    await page.locator('[type="submit"]').first().click()
    await expect(connexion).toBeVisible()
  }
  await connexion.fill(utilisateur)
  await page.locator('#password').fill(utilisateur)
  await page.locator('#kc-login').click()
  await expect(connexion).toBeHidden()
  // Consentement demandé au premier accès du compte au client argos-mcp.
  const consentement = await accord.first().waitFor({ state: 'visible', timeout: 5_000 }).then(() => true, () => false)
  if (consentement) {
    await accord.first().click()
    await expect(accord.first()).toBeHidden()
  }
}

/**
 * Jeton d'accès de l'utilisateur par le flux d'appareil du client argos-mcp : demande d'autorisation
 * d'appareil, approbation dans le navigateur, puis interrogation du point de jeton à l'intervalle
 * indiqué (RFC 8628 §3.4 et §3.5). Aucun secret de client : argos-mcp est un client public.
 */
export async function jetonParFluxAppareil(page: Page, utilisateur: string): Promise<string> {
  const demande = await formulaire(`${EMETTEUR}/protocol/openid-connect/auth/device`, {
    client_id: CLIENT_MCP,
    scope: 'openid',
  })
  expect(demande.statut, JSON.stringify(demande.corps)).toBe(200)
  const codeAppareil = demande.corps.device_code as string
  let intervalle = Number(demande.corps.interval ?? 5)

  // Avant approbation, le jeton n'est pas délivré.
  const attente = await formulaire(`${EMETTEUR}/protocol/openid-connect/token`, {
    client_id: CLIENT_MCP,
    device_code: codeAppareil,
    grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
  })
  expect(attente.corps.error).toBe('authorization_pending')

  await approuver(page, demande.corps.verification_uri_complete as string, utilisateur)

  const limite = Date.now() + 60_000
  while (Date.now() < limite) {
    await new Promise((r) => setTimeout(r, intervalle * 1000))
    const r = await formulaire(`${EMETTEUR}/protocol/openid-connect/token`, {
      client_id: CLIENT_MCP,
      device_code: codeAppareil,
      grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
    })
    if (r.statut === 200) return r.corps.access_token as string
    if (r.corps.error === 'slow_down') intervalle += 5
    else if (r.corps.error !== 'authorization_pending') throw new Error(`flux d'appareil : ${JSON.stringify(r.corps)}`)
  }
  throw new Error(`flux d'appareil : code non approuvé pour ${utilisateur}`)
}

// ------------------------------------------------------------------ Client JSON-RPC sans état

/** Réponse HTTP d'une requête MCP : statut, en-têtes utiles et message JSON-RPC décodé. */
export interface ReponseMcp {
  statut: number
  typeContenu: string
  sessionId: string | null
  message: {
    jsonrpc?: string
    id?: number
    result?: Record<string, unknown>
    error?: { code: number; message: string }
  }
}

/** Dernier message JSON-RPC d'un flux text/event-stream (champs data: de chaque événement). */
function depuisFlux(texte: string): ReponseMcp['message'] {
  const evenements = texte.split(/\r?\n\r?\n/).map((bloc) =>
    bloc.split(/\r?\n/).filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trimStart()).join('\n'),
  ).filter((d) => d !== '')
  if (evenements.length === 0) throw new Error(`flux MCP sans message : ${texte.slice(0, 200)}`)
  return JSON.parse(evenements[evenements.length - 1])
}

export class ClientMcp {
  private id = 0

  constructor(private readonly serveur: ServeurMcp, private readonly version: string = VERSION_PROTOCOLE) {
    if (serveur.type !== 'http') throw new Error(`transport ${serveur.type} non pris en charge par ce client`)
  }

  /** Envoie une requête JSON-RPC ; `nom` renseigne l'en-tête Mcp-Name (outil appelé). */
  async envoyer(methode: string, params: Record<string, unknown> = {}, nom?: string): Promise<ReponseMcp> {
    const corps = {
      jsonrpc: '2.0',
      id: ++this.id,
      method: methode,
      params: {
        ...params,
        _meta: {
          'io.modelcontextprotocol/protocolVersion': this.version,
          'io.modelcontextprotocol/clientInfo': CLIENT_INFO,
          'io.modelcontextprotocol/clientCapabilities': {},
        },
      },
    }
    const reponse = await fetch(this.serveur.url, {
      method: 'POST',
      headers: {
        ...this.serveur.headers,
        'Content-Type': 'application/json',
        Accept: 'application/json, text/event-stream',
        'MCP-Protocol-Version': this.version,
        'Mcp-Method': methode,
        ...(nom ? { 'Mcp-Name': nom } : {}),
      },
      body: JSON.stringify(corps),
    })
    const typeContenu = reponse.headers.get('content-type') ?? ''
    const texte = await reponse.text()
    let message: ReponseMcp['message'] = {}
    if (texte.trim() !== '') message = typeContenu.includes('text/event-stream') ? depuisFlux(texte) : JSON.parse(texte)
    return { statut: reponse.status, typeContenu, sessionId: reponse.headers.get('mcp-session-id'), message }
  }

  /** Découverte du serveur (protocole sans état) : versions, capacités, identité. */
  decouvrir(): Promise<ReponseMcp> {
    return this.envoyer('server/discover')
  }

  lister(): Promise<ReponseMcp> {
    return this.envoyer('tools/list')
  }

  appeler(outil: string, args: Record<string, unknown>): Promise<ReponseMcp> {
    return this.envoyer('tools/call', { name: outil, arguments: args }, outil)
  }
}
