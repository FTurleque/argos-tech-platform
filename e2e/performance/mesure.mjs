// Campagne de mesure de la latence p95 de l'API REST et du serveur MCP (PERF-01, PERF-02 ; décision J8 du porteur du
// projet, 2026-11-19 ; changement automatiser-chaine-veille, tâche 3.6). Fonctions pures, sans dépendance ni appel
// réseau, testées par `node --test e2e/performance/` : garde réseau, centiles, verdict, opérations retenues et
// exclues, rapports JSON et Markdown. L'exécution (pile locale, appels) est dans `mesurer-p95.mjs`.

/** Cible d'arc42 (§8, cibles initiales ; §10.2 Q-05) : p95 strictement inférieur à 500 ms. */
export const SEUIL_P95_MS = 500

/** Hôtes admis : la boucle locale seulement (PERF-02-S3). */
export const HOTES_LOCAUX = new Set(['localhost', '127.0.0.1', '[::1]', '::1'])

/** Paramètres par défaut de la campagne (P-9, en attendant Q-PERF-1 et Q-PERF-2), surchargés par l'environnement. */
export const PARAMETRES_PAR_DEFAUT = Object.freeze({
  projets: 20,
  composantsParProjet: 100,
  signaux: 1000,
  appelsParOperation: 200,
  concurrence: 4,
  echauffement: 20,
})

/** Requête refusée par la garde réseau : la campagne échoue en nommant l'hôte (PERF-02-S3). */
export class HoteNonLocal extends Error {
  constructor(hote) {
    super(`requête refusée : l'hôte « ${hote} » est hors de la boucle locale ; aucun rapport n'est valide`)
    this.name = 'HoteNonLocal'
    this.hote = hote
  }
}

/** Vérifie qu'une URL vise la boucle locale en http ou https ; lève {@link HoteNonLocal} sinon. */
export function verifierHoteLocal(url) {
  let analysee
  try {
    analysee = new URL(url)
  } catch {
    throw new HoteNonLocal(String(url))
  }
  if (analysee.protocol !== 'http:' && analysee.protocol !== 'https:') throw new HoteNonLocal(`${analysee.protocol}${analysee.host}`)
  if (!HOTES_LOCAUX.has(analysee.hostname)) throw new HoteNonLocal(analysee.hostname)
  return analysee
}

/** Lit un entier positif de l'environnement, sinon la valeur par défaut. */
export function entierPositif(valeur, defaut) {
  if (valeur === undefined || valeur === '') return defaut
  const n = Number(valeur)
  if (!Number.isInteger(n) || n < 1) throw new Error(`valeur entière positive attendue : « ${valeur} »`)
  return n
}

/** Paramètres de la campagne : valeurs par défaut (P-9) surchargées par `ARGOS_PERF_*`. */
export function parametres(env = {}) {
  const d = PARAMETRES_PAR_DEFAUT
  return {
    projets: entierPositif(env.ARGOS_PERF_PROJETS, d.projets),
    composantsParProjet: entierPositif(env.ARGOS_PERF_COMPOSANTS, d.composantsParProjet),
    signaux: entierPositif(env.ARGOS_PERF_SIGNAUX, d.signaux),
    appelsParOperation: entierPositif(env.ARGOS_PERF_APPELS, d.appelsParOperation),
    concurrence: entierPositif(env.ARGOS_PERF_CONCURRENCE, d.concurrence),
    echauffement: entierPositif(env.ARGOS_PERF_ECHAUFFEMENT, d.echauffement),
  }
}

/**
 * Centile par la méthode du rang le plus proche : plus petite valeur telle qu'au moins p % des mesures lui sont
 * inférieures ou égales. Liste vide : `null`.
 */
export function centile(valeurs, p) {
  if (!Array.isArray(valeurs) || valeurs.length === 0) return null
  if (!(p > 0 && p <= 100)) throw new Error(`centile hors de ]0, 100] : ${p}`)
  const triees = [...valeurs].sort((a, b) => a - b)
  const rang = Math.ceil((p / 100) * triees.length)
  return triees[Math.max(rang, 1) - 1]
}

function arrondi(ms) {
  return ms === null ? null : Math.round(ms * 10) / 10
}

/**
 * Mesures d'une opération : nombre d'appels, p50, p95, maximum, erreurs, verdict. Une opération dont un appel
 * échoue (statut hors 2xx, erreur JSON-RPC) est « en erreur » : sa mesure ne prouve rien.
 */
export function resumerOperation(operation, durees, erreurs = []) {
  const p95 = centile(durees, 95)
  let verdict
  if (erreurs.length > 0 || durees.length === 0) verdict = 'EN_ERREUR'
  else verdict = p95 < SEUIL_P95_MS ? 'DANS_LA_CIBLE' : 'HORS_CIBLE'
  return {
    id: operation.id,
    type: operation.type,
    libelle: operation.libelle,
    appels: durees.length,
    p50Ms: arrondi(centile(durees, 50)),
    p95Ms: arrondi(p95),
    maxMs: arrondi(durees.length ? Math.max(...durees) : null),
    erreurs: erreurs.length,
    premiereErreur: erreurs[0] ?? null,
    verdict,
  }
}

/** Verdict global et code de sortie : 0 si toutes les opérations sont dans la cible, 1 sinon (PERF-01-S1, S2). */
export function verdictGlobal(resultats) {
  const horsCible = resultats.filter((r) => r.verdict === 'HORS_CIBLE').map((r) => r.id)
  const enErreur = resultats.filter((r) => r.verdict === 'EN_ERREUR').map((r) => r.id)
  const valide = resultats.length > 0 && enErreur.length === 0
  return {
    valide,
    dansLaCible: valide && horsCible.length === 0,
    horsCible,
    enErreur,
    codeSortie: valide && horsCible.length === 0 ? 0 : 1,
  }
}

/**
 * Opérations exclues de la mesure, avec le motif (PERF-01-S3) : elles appellent une source externe ou un modèle d'IA,
 * ou écrivent des données.
 */
export const OPERATIONS_EXCLUES = Object.freeze([
  { id: 'POST /api/v1/ops/sources/{id}/synchronizations', motif: 'synchronisation d’une source externe (OSV)' },
  { id: 'collectes planifiées (réglementaire, artistes, radar, activité, veille web)', motif: 'appels à des sources externes ; tâches désactivées pendant la campagne' },
  { id: 'résumés IA des alertes, synthèses IA des fiches, analyse complémentaire', motif: 'appel à un modèle d’IA (passerelle IA absente pendant la campagne)' },
  { id: 'GET /api/v1/veille-web/… (récupération d’une page à la demande)', motif: 'récupération d’une page externe' },
  { id: 'MCP explain_change', motif: 'synthèse d’un changement par le modèle d’IA' },
  { id: 'écritures (transitions d’impact, abonnements, préférences, vues, imports)', motif: 'hors du périmètre « consultation » de la cible' },
])

/**
 * Opérations de consultation retenues (DC-8) : lecture seule, sans appel externe ni IA. `contexte` donne les
 * identifiants du jeu de données (projet, signal, impact, paquet).
 */
export function operationsRetenues(contexte) {
  const { projet, signal, impact, paquet, nomPaquet } = contexte
  const rest = (id, libelle, chemin) => ({ id, type: 'REST', libelle, chemin })
  const mcp = (outil, args) => ({ id: `MCP ${outil}`, type: 'MCP', libelle: `outil MCP ${outil}`, outil, args })
  return [
    rest('GET /api/v1/projects', 'projets visibles', '/api/v1/projects'),
    rest('GET /api/v1/projects/{id}/inventory/summary', 'synthèse d’inventaire', `/api/v1/projects/${projet}/inventory/summary`),
    rest('GET /api/v1/projects/{id}/inventory/components', 'composants d’inventaire (page 1)', `/api/v1/projects/${projet}/inventory/components`),
    rest('GET /api/v1/impacts/{id}', 'impact, provenance et historique', `/api/v1/impacts/${impact}`),
    rest('GET /api/v1/signals/{id}/impacts', 'impacts d’un signal', `/api/v1/signals/${signal}/impacts`),
    rest('GET /api/v1/me/veille', 'Ma veille', '/api/v1/me/veille'),
    rest('GET /api/v1/recherche', 'recherche plein texte « performance »', '/api/v1/recherche?q=performance'),
    rest('GET /api/v1/subscriptions', 'abonnements', '/api/v1/subscriptions'),
    rest('GET /api/v1/alertes', 'alertes', '/api/v1/alertes'),
    mcp('get_project_context', { project_id: projet }),
    mcp('get_technology_standard', { ecosystem: 'Maven', name: nomPaquet }),
    mcp('upcoming_releases', { horizon_days: 90 }),
    mcp('get_regulatory_changes', { limit: 20 }),
    mcp('check_dependency', { ecosystem: 'Maven', name: nomPaquet, version: '1.0.0' }),
    mcp('search_signals', { packages: [paquet], limit: 20 }),
    mcp('get_security_alerts', { project_id: projet, limit: 50 }),
  ]
}

function cellule(valeur) {
  return valeur === null || valeur === undefined ? '—' : String(valeur).replaceAll('|', '\\|')
}

const LIBELLES_VERDICT = { DANS_LA_CIBLE: 'dans la cible', HORS_CIBLE: '**hors cible**', EN_ERREUR: '**en erreur**' }

/** Rapport Markdown versionné (PERF-02-S1) : date, révision, environnement, jeu de données, concurrence, mesures. */
export function rapportMarkdown(rapport) {
  const v = rapport.verdict
  const lignes = [
    '# Mesure de la latence p95 de l’API et du serveur MCP',
    '',
    '<!-- Rapport produit par `npm --prefix e2e run test:performance` (e2e/performance/mesurer-p95.mjs) ; ne pas modifier à la main. -->',
    '',
    `Cible : p95 < ${SEUIL_P95_MS} ms par opération de consultation, hors appels externes et IA (arc42 §8, §10.2 Q-05 ; PERF-01). Exigences : PERF-01, PERF-02 ([fiche](../../specifications/performance-api-mcp.md)).`,
    '',
    '## Exécution',
    '',
    '| Élément | Valeur |',
    '|---|---|',
    `| Date | ${cellule(rapport.date)} |`,
    `| Révision Git | ${cellule(rapport.revision)} |`,
    `| Environnement | ${cellule(rapport.environnement.description)} |`,
    `| Système | ${cellule(rapport.environnement.systeme)} |`,
    `| Node.js | ${cellule(rapport.environnement.node)} |`,
    `| Jeu de données | ${cellule(rapport.jeuDeDonnees.description)} |`,
    `| Appels par opération | ${rapport.parametres.appelsParOperation} (après ${rapport.parametres.echauffement} appels d’échauffement) |`,
    `| Concurrence | ${rapport.parametres.concurrence} clients simultanés |`,
    `| Verdict | ${v.valide ? (v.dansLaCible ? 'toutes les opérations dans la cible' : `hors cible : ${v.horsCible.join(', ')}`) : `campagne invalide : ${v.enErreur.join(', ')}`} |`,
    `| Code de sortie | ${v.codeSortie} |`,
    '',
    '## Mesures par opération',
    '',
    '| Opération | Type | Appels | p50 (ms) | p95 (ms) | max (ms) | Erreurs | Verdict |',
    '|---|---|---|---|---|---|---|---|',
    ...rapport.resultats.map((r) =>
      `| ${cellule(r.id)} | ${r.type} | ${r.appels} | ${cellule(r.p50Ms)} | ${cellule(r.p95Ms)} | ${cellule(r.maxMs)} | ${r.erreurs} | ${LIBELLES_VERDICT[r.verdict] ?? r.verdict} |`,
    ),
    '',
    '## Opérations exclues',
    '',
    '| Opération | Motif de l’exclusion |',
    '|---|---|',
    ...rapport.exclues.map((e) => `| ${cellule(e.id)} | ${cellule(e.motif)} |`),
    '',
    '## Méthode',
    '',
    '- Pile de test locale : PostgreSQL jetable de `e2e/compose.yaml`, serveur `quarkus:dev` sur le port 8090 avec le Keycloak des Dev Services (royaume de test du dépôt), tâches planifiées qui joindraient une source externe désactivées, aucune passerelle IA.',
    '- Garde réseau : toute requête hors de la boucle locale (`localhost`, `127.0.0.1`, `[::1]`) fait échouer la campagne en nommant l’hôte (PERF-02-S3).',
    '- Jetons : comptes du royaume de test (flux par mot de passe du client de test `argos-server`), aucun secret réel.',
    '- Durée d’un appel : de l’envoi de la requête à la lecture complète de la réponse, mesurée par `performance.now()` côté client ; centiles par la méthode du rang le plus proche.',
    '- Outillage : Node.js seul (`fetch`, `node:perf_hooks`), aucune dépendance ajoutée (ADR-0021). La campagne n’est lancée ni par `mvn verify`, ni par `npm --prefix argos-ui test`, ni par `npm --prefix e2e test` (PERF-02-S2).',
    '',
  ]
  return lignes.join('\n')
}
