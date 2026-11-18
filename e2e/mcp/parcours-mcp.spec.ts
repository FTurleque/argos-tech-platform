// Parcours du serveur MCP (exposer-serveur-mcp, tâche 5.2, partie protocole) : ce que fait Claude Code
// configuré par un .mcp.json, rejoué au niveau du protocole par un client écrit avec fetch, sans aucun
// modèle d'IA. Le client lit le fichier versionné donnees/mcp.json (format de Claude Code, sans secret),
// obtient le jeton de l'utilisateur par le flux d'appareil du client Keycloak argos-mcp (décision Q-3),
// découvre le serveur, liste les outils puis appelle check_dependency sur la version vulnérable de test de
// donnees/mcp.sql. Serveur réel (quarkus:dev), Keycloak et PostgreSQL de test ; aucune requête hors du poste.
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { readFileSync, writeFileSync } from 'node:fs'
import { api, COMPTE_MCP, COMPTE_MCP_VOISIN, MCP, preparerMcp, valeurSql } from '../support/donnees'
import { URL_API } from '../support/environnement'
import { expect, test } from '../support/fixtures'
import {
  ClientMcp,
  jetonParFluxAppareil,
  lireConfiguration,
  VERSION_PROTOCOLE,
  type ReponseMcp,
  type ServeurMcp,
} from '../support/mcp'

const FICHIER_MCP = join(dirname(fileURLToPath(import.meta.url)), '..', 'donnees', 'mcp.json')

const OUTILS = ['check_dependency', 'explain_change', 'get_project_context', 'get_regulatory_changes',
  'get_security_alerts', 'get_technology_standard', 'search_signals', 'upcoming_releases']

const NATURE_EXTERNE = 'CONTENU_EXTERNE_NON_FIABLE'

interface Reference {
  source: string
  identifiant: string
  lien: string | null
}

interface TexteExterne {
  nature: string
  source: string
  texte: string
  tronque: boolean
}

interface Verification {
  cle: string
  version: string
  connue: boolean
  constat: string
  failles: {
    source: string
    identifiant: string
    alias: string[]
    severite: string
    correspondance: string
    references: Reference[]
    titre: TexteExterne
    resume: TexteExterne
  }[]
  referentiel: { statut: string; versionRecommandee: string | null }[]
  versionRecommandee: string | null
}

interface Alertes {
  nombre: number
  alertes: { projet: string; signalIdentifiant: string; titre: TexteExterne }[]
}

/** Environnement de l'assistant : jeton dans ARGOS_MCP_TOKEN, adresse par défaut du fichier sauf pile déplacée. */
function environnement(jeton?: string): Record<string, string | undefined> {
  return {
    ARGOS_MCP_TOKEN: jeton,
    ARGOS_MCP_URL: process.env.ARGOS_E2E_URL_API ? `${URL_API}/mcp` : undefined,
  }
}

/** Client configuré par donnees/mcp.json pour l'utilisateur, jeton obtenu par le flux d'appareil. */
async function serveurDe(page: Parameters<typeof jetonParFluxAppareil>[0], utilisateur: string): Promise<ServeurMcp> {
  const jeton = await jetonParFluxAppareil(page, utilisateur)
  const serveur = lireConfiguration(FICHIER_MCP, 'argos', environnement(jeton))
  expect(serveur.type).toBe('http')
  expect(serveur.url).toBe(`${URL_API}/mcp`)
  // Comparaison booléenne : le jeton n'apparaît jamais dans un message d'échec ni dans le rapport.
  expect(serveur.headers.Authorization === `Bearer ${jeton}`, 'en-tête Authorization développé depuis ARGOS_MCP_TOKEN')
    .toBe(true)
  return serveur
}

/** Résultat structuré d'un appel d'outil réussi. */
function resultat<T>(r: ReponseMcp): T {
  expect(r.statut, JSON.stringify(r.message)).toBe(200)
  expect(r.message.error, JSON.stringify(r.message)).toBeUndefined()
  expect(r.message.result?.isError, JSON.stringify(r.message)).toBe(false)
  return r.message.result?.structuredContent as T
}

/** Garde la réponse dans le dossier de sortie du test (test-results/), pièce du compte rendu d'exécution. */
function consigner(nom: string, r: ReponseMcp): void {
  writeFileSync(test.info().outputPath(`${nom}.json`), `${normaliser(JSON.stringify({ statut: r.statut,
    typeContenu: r.typeContenu, sessionId: r.sessionId, message: r.message }, null, 2))}\n`)
}

/**
 * Masque les valeurs propres à une exécution (identifiant de session, identifiant d'espace, y compris
 * dans le texte JSON du bloc content) : les réponses consignées restent comparables d'une exécution à
 * l'autre.
 */
function normaliser(json: string): string {
  return json
    .replace(/("sessionId": )"[^"]+"/, '$1"<session>"')
    .replace(/(\\?"espaceId\\?":\s*\\?")[0-9a-f-]{36}/g, '$1<espace>')
}

/** Dernier identifiant du journal des appels MCP (repère pour ne compter que les appels du parcours). */
function dernierAppel(): number {
  return Number(valeurSql('SELECT coalesce(max(id), 0) FROM mcp_appel;'))
}

/** Valeurs passées en variables psql et citées par psql (:'nom') : aucune interpolation dans le SQL. */
function appelsJournalises(compte: string, depuis: number, outil: string, issue: string): number {
  return Number(valeurSql(`SELECT count(*) FROM mcp_appel WHERE id > (:'depuis')::bigint
    AND compte_id = (:'compte')::uuid AND outil = :'outil' AND issue = :'issue';`,
  { depuis: String(depuis), compte, outil, issue }))
}

test.describe.serial('5.2 (partie protocole) : assistant configuré par .mcp.json, check_dependency cité et sourcé', () => {
  test.beforeAll(async () => {
    await preparerMcp()
  })

  test('.mcp.json versionné sans secret ; appel sans jeton refusé (MCP-03-S1)', async () => {
    const brut = readFileSync(FICHIER_MCP, 'utf8')
    // Le fichier ne porte qu'une référence à la variable d'environnement : aucun jeton.
    expect(brut).toContain('Bearer ${ARGOS_MCP_TOKEN}')
    expect(brut).not.toMatch(/eyJ[\w-]+\.[\w-]+/)
    expect(() => lireConfiguration(FICHIER_MCP, 'argos', environnement())).toThrow(/ARGOS_MCP_TOKEN/)

    const anonyme = new ClientMcp({ type: 'http', url: `${URL_API}/mcp`, headers: {} })
    const refus = await anonyme.appeler('check_dependency',
      { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionVulnerable })
    expect(refus.statut).toBe(401)
  })

  test('Q-3, MCP-01, MCP-02-S1, MCP-05 : jeton par le flux d\'appareil, découverte, outils, version vulnérable citée et sourcée', async ({ page }) => {
    const oscar = await api<{ id: string }>(COMPTE_MCP, '/api/v1/moi')
    const repere = dernierAppel()
    const serveur = await serveurDe(page, COMPTE_MCP)
    const client = new ClientMcp(serveur)

    await test.step('négociation : découverte sans état, version de protocole, initialize', async () => {
      // Protocole sans état : server/discover annonce les versions ; le client retient 2026-07-28.
      const r = await client.decouvrir()
      consigner('server-discover', r)
      expect(r.statut, JSON.stringify(r.message)).toBe(200)
      expect(r.sessionId, 'aucune session').toBeNull()
      expect(r.message.result?.supportedVersions).toContain(VERSION_PROTOCOLE)
      expect(r.message.result?.capabilities).toHaveProperty('tools')
      expect(JSON.stringify(r.message.result?._meta)).toContain('"name":"argos"')

      // Négociation par initialize d'un client en version 2025-06-18 : le serveur répond dans la version
      // demandée et s'identifie (comportement constaté, consigné comme écart dans la fiche serveur-mcp).
      const ancien = await new ClientMcp(serveur, '2025-06-18').envoyer('initialize', {
        protocolVersion: '2025-06-18',
        capabilities: {},
        clientInfo: { name: 'client-mcp-2025', version: '1' },
      })
      consigner('initialize-2025-06-18', ancien)
      expect(ancien.statut, JSON.stringify(ancien.message)).toBe(200)
      expect(ancien.message.result?.protocolVersion).toBe('2025-06-18')
      expect(JSON.stringify(ancien.message.result?.serverInfo)).toContain('"name":"argos"')
      // Une session éventuelle ne remplace jamais le jeton : sans lui, la requête est refusée (401).
      const sansJeton = await new ClientMcp({
        type: 'http',
        url: serveur.url,
        headers: { 'Mcp-Session-Id': ancien.sessionId ?? 'session-absente' },
      }, '2025-06-18').lister()
      expect(sansJeton.statut).toBe(401)
    })

    await test.step('liste des outils : huit outils de consultation, schémas publiés', async () => {
      const r = await client.lister()
      expect(r.statut).toBe(200)
      const outils = r.message.result?.tools as {
        name: string
        description: string
        annotations: { readOnlyHint: boolean; destructiveHint: boolean }
        inputSchema: { properties: Record<string, unknown> }
        outputSchema: { type: string }
      }[]
      expect(outils.map((o) => o.name).sort()).toEqual(OUTILS)
      for (const o of outils) {
        expect(o.annotations.readOnlyHint, o.name).toBe(true)
        expect(o.annotations.destructiveHint, o.name).toBe(false)
      }
      const verifier = outils.find((o) => o.name === 'check_dependency')!
      expect(Object.keys(verifier.inputSchema.properties).sort()).toEqual(['ecosystem', 'name', 'version'])
      expect(verifier.outputSchema.type).toBe('object')
      expect(verifier.description).toContain(NATURE_EXTERNE)
    })

    await test.step('check_dependency sur la version vulnérable : avis cité, sources et liens, contenu externe balisé', async () => {
      const r = await client.appeler('check_dependency',
        { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionVulnerable })
      consigner('check-dependency-3.2', r)
      const v = resultat<Verification>(r)
      expect(v.cle).toBe(MCP.cle)
      expect(v.version).toBe(MCP.versionVulnerable)
      expect(v.connue).toBe(true)
      expect(v.constat).toMatch(/^1 faille\(s\) connue\(s\) affectent cette version\./)
      expect(v.failles).toHaveLength(1)
      const faille = v.failles[0]
      expect(faille.source).toBe('OSV')
      expect(faille.identifiant).toBe(MCP.avis)
      expect(faille.alias).toContain(MCP.alias)
      expect(faille.severite).toBe('CRITICAL')
      expect(faille.correspondance).toBe('AFFECTEE')
      expect(faille.references).toEqual([
        { source: 'OSV', identifiant: MCP.avis, lien: MCP.lienAvis },
        { source: 'NVD', identifiant: MCP.alias, lien: MCP.lienAlias },
      ])
      // Titre et résumé de la source : champs marqués externes, distincts des champs produits par Argos.
      expect(faille.titre).toMatchObject({ nature: NATURE_EXTERNE, source: 'OSV', texte: MCP.titreAvis, tronque: false })
      expect(faille.resume.nature).toBe(NATURE_EXTERNE)
      expect(faille.resume.texte).toContain(MCP.consignePiegee)
      expect(v.constat).not.toContain(MCP.consignePiegee)
      // Référentiel de l'espace d'oscar : version recommandée.
      expect(v.referentiel).toEqual([expect.objectContaining({ statut: 'ADOPT', versionRecommandee: MCP.versionRecommandee })])
      expect(v.versionRecommandee).toBe(MCP.versionRecommandee)
      // Bloc texte lu par les clients qui ignorent structuredContent : les mêmes faits, mêmes marques.
      const contenu = r.message.result?.content as { type: string; text: string }[]
      expect(contenu.map((c) => c.type)).toEqual(['text'])
      expect(JSON.parse(contenu[0].text)).toEqual(v)
    })

    await test.step('la version recommandée n\'est affectée par aucune faille connue', async () => {
      const v = resultat<Verification>(await client.appeler('check_dependency',
        { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionRecommandee }))
      expect(v.connue).toBe(true)
      expect(v.failles).toEqual([])
      expect(v.versionRecommandee).toBe(MCP.versionRecommandee)
    })

    await test.step('alertes du projet concerné, dans le périmètre d\'oscar', async () => {
      const a = resultat<Alertes>(await client.appeler('get_security_alerts', { project_id: MCP.idProjet }))
      expect(a.alertes.map((x) => [x.projet, x.signalIdentifiant])).toEqual([[MCP.projet, MCP.avis]])
      expect(a.alertes[0].titre.nature).toBe(NATURE_EXTERNE)
    })

    await test.step('chaque appel est journalisé (MCP-06)', async () => {
      expect(appelsJournalises(oscar.id, repere, 'check_dependency', 'SUCCES')).toBe(2)
      expect(appelsJournalises(oscar.id, repere, 'get_security_alerts', 'SUCCES')).toBe(1)
    })
  })

  test('MCP-04-S1 : un compte d\'un autre espace ne voit pas les projets concernés', async ({ page }) => {
    const nina = await api<{ id: string }>(COMPTE_MCP_VOISIN, '/api/v1/moi')
    const repere = dernierAppel()
    const client = new ClientMcp(await serveurDe(page, COMPTE_MCP_VOISIN))

    // L'avis est public, mais ni le projet d'oscar ni le standard de son espace ne sont renvoyés.
    const r = await client.appeler('check_dependency',
      { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionVulnerable })
    const v = resultat<Verification>(r)
    expect(v.failles.map((f) => f.identifiant)).toEqual([MCP.avis])
    expect(v.referentiel).toEqual([])
    expect(v.versionRecommandee).toBeNull()
    expect(JSON.stringify(r.message)).not.toContain(MCP.projet)
    expect(JSON.stringify(r.message)).not.toContain(MCP.versionRecommandee)

    const toutes = await client.appeler('get_security_alerts', {})
    const alertes = resultat<Alertes>(toutes)
    expect(alertes.alertes.map((a) => a.signalIdentifiant)).not.toContain(MCP.avis)
    expect(JSON.stringify(toutes.message)).not.toContain(MCP.projet)

    const refus = await client.appeler('get_security_alerts', { project_id: MCP.idProjet })
    expect(refus.statut).toBe(200)
    expect(refus.message.result?.isError).toBe(true)
    const contenu = JSON.stringify(refus.message.result)
    expect(contenu).toMatch(/INTROUVABLE/)
    expect(contenu).not.toContain(MCP.projet)
    expect(contenu).not.toContain(MCP.avis)
    expect(appelsJournalises(nina.id, repere, 'get_security_alerts', 'REFUS_ACCES')).toBe(1)
  })
})
