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
function resultat<T>(reponse: ReponseMcp): T {
  expect(reponse.statut, JSON.stringify(reponse.message)).toBe(200)
  expect(reponse.message.error, JSON.stringify(reponse.message)).toBeUndefined()
  expect(reponse.message.result?.isError, JSON.stringify(reponse.message)).toBe(false)
  return reponse.message.result?.structuredContent as T
}

/** Garde la réponse dans le dossier de sortie du test (test-results/), pièce du compte rendu d'exécution. */
function consigner(nom: string, reponse: ReponseMcp): void {
  writeFileSync(test.info().outputPath(`${nom}.json`), `${normaliser(JSON.stringify({ statut: reponse.statut,
    typeContenu: reponse.typeContenu, sessionId: reponse.sessionId, message: reponse.message }, null, 2))}\n`)
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

// ------------------------------------------------------------------ Étapes du parcours d'oscar

/** Le fichier ne porte qu'une référence à la variable d'environnement : aucun jeton. */
function verifierFichierSansSecret(): void {
  const brut = readFileSync(FICHIER_MCP, 'utf8')
  expect(brut).toContain('Bearer ${ARGOS_MCP_TOKEN}')
  expect(brut).not.toMatch(/eyJ[\w-]+\.[\w-]+/)
  expect(() => lireConfiguration(FICHIER_MCP, 'argos', environnement())).toThrow(/ARGOS_MCP_TOKEN/)
}

/** Appel d'outil d'un client sans en-tête Authorization : refusé (401). */
async function verifierAppelSansJetonRefuse(): Promise<void> {
  const anonyme = new ClientMcp({ type: 'http', url: `${URL_API}/mcp`, headers: {} })
  const refus = await anonyme.appeler('check_dependency',
    { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionVulnerable })
  expect(refus.statut).toBe(401)
}

/** Protocole sans état : server/discover annonce les versions ; le client retient 2026-07-28. */
async function verifierDecouverte(client: ClientMcp): Promise<void> {
  const reponse = await client.decouvrir()
  consigner('server-discover', reponse)
  expect(reponse.statut, JSON.stringify(reponse.message)).toBe(200)
  expect(reponse.sessionId, 'aucune session').toBeNull()
  expect(reponse.message.result?.supportedVersions).toContain(VERSION_PROTOCOLE)
  expect(reponse.message.result?.capabilities).toHaveProperty('tools')
  expect(JSON.stringify(reponse.message.result?._meta)).toContain('"name":"argos"')
}

/**
 * Négociation par initialize d'un client en version 2025-06-18 : le serveur répond dans la version
 * demandée et s'identifie (comportement constaté, consigné comme écart dans la fiche serveur-mcp).
 * Une session éventuelle ne remplace jamais le jeton : sans lui, la requête est refusée (401).
 */
async function verifierInitializeAncienneVersion(serveur: ServeurMcp): Promise<void> {
  const ancien = await new ClientMcp(serveur, '2025-06-18').envoyer('initialize', {
    protocolVersion: '2025-06-18',
    capabilities: {},
    clientInfo: { name: 'client-mcp-2025', version: '1' },
  })
  consigner('initialize-2025-06-18', ancien)
  expect(ancien.statut, JSON.stringify(ancien.message)).toBe(200)
  expect(ancien.message.result?.protocolVersion).toBe('2025-06-18')
  expect(JSON.stringify(ancien.message.result?.serverInfo)).toContain('"name":"argos"')
  const sansJeton = await new ClientMcp({
    type: 'http',
    url: serveur.url,
    headers: { 'Mcp-Session-Id': ancien.sessionId ?? 'session-absente' },
  }, '2025-06-18').lister()
  expect(sansJeton.statut).toBe(401)
}

/** Huit outils de consultation en lecture seule ; schémas d'entrée et de sortie de check_dependency. */
async function verifierListeDesOutils(client: ClientMcp): Promise<void> {
  const reponse = await client.lister()
  expect(reponse.statut).toBe(200)
  const outils = reponse.message.result?.tools as {
    name: string
    description: string
    annotations: { readOnlyHint: boolean; destructiveHint: boolean }
    inputSchema: { properties: Record<string, unknown> }
    outputSchema: { type: string }
  }[]
  expect(outils.map((outil) => outil.name).sort()).toEqual(OUTILS)
  for (const outil of outils) {
    expect(outil.annotations.readOnlyHint, outil.name).toBe(true)
    expect(outil.annotations.destructiveHint, outil.name).toBe(false)
  }
  const verifier = outils.find((outil) => outil.name === 'check_dependency')!
  expect(Object.keys(verifier.inputSchema.properties).sort()).toEqual(['ecosystem', 'name', 'version'])
  expect(verifier.outputSchema.type).toBe('object')
  expect(verifier.description).toContain(NATURE_EXTERNE)
}

/** Avis de test cité avec son alias, ses sources et leurs liens ; titre et résumé marqués externes. */
function verifierFailleCitee(faille: Verification['failles'][number]): void {
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
}

/** check_dependency sur la version vulnérable : avis cité et sourcé, version recommandée de l'espace. */
async function verifierVersionVulnerable(client: ClientMcp): Promise<void> {
  const reponse = await client.appeler('check_dependency',
    { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionVulnerable })
  consigner('check-dependency-3.2', reponse)
  const verification = resultat<Verification>(reponse)
  expect(verification.cle).toBe(MCP.cle)
  expect(verification.version).toBe(MCP.versionVulnerable)
  expect(verification.connue).toBe(true)
  expect(verification.constat).toMatch(/^1 faille\(s\) connue\(s\) affectent cette version\./)
  expect(verification.failles).toHaveLength(1)
  verifierFailleCitee(verification.failles[0])
  expect(verification.constat).not.toContain(MCP.consignePiegee)
  // Référentiel de l'espace d'oscar : version recommandée.
  expect(verification.referentiel)
    .toEqual([expect.objectContaining({ statut: 'ADOPT', versionRecommandee: MCP.versionRecommandee })])
  expect(verification.versionRecommandee).toBe(MCP.versionRecommandee)
  // Bloc texte lu par les clients qui ignorent structuredContent : les mêmes faits, mêmes marques.
  const contenu = reponse.message.result?.content as { type: string; text: string }[]
  expect(contenu.map((bloc) => bloc.type)).toEqual(['text'])
  expect(JSON.parse(contenu[0].text)).toEqual(verification)
}

/** La version recommandée n'est affectée par aucune faille connue. */
async function verifierVersionRecommandee(client: ClientMcp): Promise<void> {
  const verification = resultat<Verification>(await client.appeler('check_dependency',
    { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionRecommandee }))
  expect(verification.connue).toBe(true)
  expect(verification.failles).toEqual([])
  expect(verification.versionRecommandee).toBe(MCP.versionRecommandee)
}

/** Alerte du projet concerné, dans le périmètre d'oscar, titre marqué externe. */
async function verifierAlertesDuProjet(client: ClientMcp): Promise<void> {
  const alertes = resultat<Alertes>(await client.appeler('get_security_alerts', { project_id: MCP.idProjet }))
  expect(alertes.alertes.map((alerte) => [alerte.projet, alerte.signalIdentifiant])).toEqual([[MCP.projet, MCP.avis]])
  expect(alertes.alertes[0].titre.nature).toBe(NATURE_EXTERNE)
}

// ------------------------------------------------------------------ Étapes du parcours de nina

/** L'avis est public, mais ni le projet d'oscar ni le standard de son espace ne sont renvoyés. */
async function verifierAvisSansDonneesDuVoisin(client: ClientMcp): Promise<void> {
  const reponse = await client.appeler('check_dependency',
    { ecosystem: MCP.ecosysteme, name: MCP.paquet, version: MCP.versionVulnerable })
  const verification = resultat<Verification>(reponse)
  expect(verification.failles.map((faille) => faille.identifiant)).toEqual([MCP.avis])
  expect(verification.referentiel).toEqual([])
  expect(verification.versionRecommandee).toBeNull()
  expect(JSON.stringify(reponse.message)).not.toContain(MCP.projet)
  expect(JSON.stringify(reponse.message)).not.toContain(MCP.versionRecommandee)
}

/** Les alertes de nina ne citent ni l'avis ni le projet d'oscar. */
async function verifierAlertesSansProjetDuVoisin(client: ClientMcp): Promise<void> {
  const toutes = await client.appeler('get_security_alerts', {})
  const alertes = resultat<Alertes>(toutes)
  expect(alertes.alertes.map((alerte) => alerte.signalIdentifiant)).not.toContain(MCP.avis)
  expect(JSON.stringify(toutes.message)).not.toContain(MCP.projet)
}

/** Le projet d'oscar demandé par nina : erreur d'outil INTROUVABLE, sans fuite du projet ni de l'avis. */
async function verifierProjetDuVoisinIntrouvable(client: ClientMcp): Promise<void> {
  const refus = await client.appeler('get_security_alerts', { project_id: MCP.idProjet })
  expect(refus.statut).toBe(200)
  expect(refus.message.result?.isError).toBe(true)
  const contenu = JSON.stringify(refus.message.result)
  expect(contenu).toMatch(/INTROUVABLE/)
  expect(contenu).not.toContain(MCP.projet)
  expect(contenu).not.toContain(MCP.avis)
}

test.describe.serial('5.2 (partie protocole) : assistant configuré par .mcp.json, check_dependency cité et sourcé', () => {
  test.beforeAll(async () => {
    await preparerMcp()
  })

  test('.mcp.json versionné sans secret ; appel sans jeton refusé (MCP-03-S1)', async () => {
    verifierFichierSansSecret()
    await verifierAppelSansJetonRefuse()
  })

  test('Q-3, MCP-01, MCP-02-S1, MCP-05 : jeton par le flux d\'appareil, découverte, outils, '
    + 'version vulnérable citée et sourcée', async ({ page }) => {
    const oscar = await api<{ id: string }>(COMPTE_MCP, '/api/v1/moi')
    const repere = dernierAppel()
    const serveur = await serveurDe(page, COMPTE_MCP)
    const client = new ClientMcp(serveur)

    await test.step('négociation : découverte sans état, version de protocole, initialize', async () => {
      await verifierDecouverte(client)
      await verifierInitializeAncienneVersion(serveur)
    })
    await test.step('liste des outils : huit outils de consultation, schémas publiés',
      () => verifierListeDesOutils(client))
    await test.step('check_dependency sur la version vulnérable : avis cité, sources et liens, contenu externe balisé',
      () => verifierVersionVulnerable(client))
    await test.step('la version recommandée n\'est affectée par aucune faille connue',
      () => verifierVersionRecommandee(client))
    await test.step('alertes du projet concerné, dans le périmètre d\'oscar',
      () => verifierAlertesDuProjet(client))
    await test.step('chaque appel est journalisé (MCP-06)', async () => {
      expect(appelsJournalises(oscar.id, repere, 'check_dependency', 'SUCCES')).toBe(2)
      expect(appelsJournalises(oscar.id, repere, 'get_security_alerts', 'SUCCES')).toBe(1)
    })
  })

  test('MCP-04-S1 : un compte d\'un autre espace ne voit pas les projets concernés', async ({ page }) => {
    const nina = await api<{ id: string }>(COMPTE_MCP_VOISIN, '/api/v1/moi')
    const repere = dernierAppel()
    const client = new ClientMcp(await serveurDe(page, COMPTE_MCP_VOISIN))

    await verifierAvisSansDonneesDuVoisin(client)
    await verifierAlertesSansProjetDuVoisin(client)
    await verifierProjetDuVoisinIntrouvable(client)
    expect(appelsJournalises(nina.id, repere, 'get_security_alerts', 'REFUS_ACCES')).toBe(1)
  })
})
