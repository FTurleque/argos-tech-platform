#!/usr/bin/env node
// Sources et forges simulées de la pile E2E (changement automatiser-chaine-veille, tâche 4.3) : serveur HTTP
// local qui remplace, pour le serveur ARGOS de la pile, l'export et l'API OSV, les sources d'enrichissement
// (NVD, KEV, EPSS, CERT-FR) et les API de GitLab et de GitHub. Aucune requête ne quitte le poste : le serveur
// ARGOS est configuré par playwright.config.ts pour n'interroger que cette adresse.
//
// Les réponses servies sont les fichiers enregistrés de e2e/donnees/chaine/ (avis OSV et SBOM CycloneDX
// fictifs), choisis par le parcours :
//   PUT  /__etat      corps JSON { osv: [{ id, modified, fichier }], forges: { "<chemin?requête>": fichier } }
//   GET  /__requetes  requêtes reçues depuis le dernier PUT /__etat (méthode, chemin et requête)
//   GET  /__pret      200 : le serveur écoute (attendu par Playwright)
// Sans état programmé, l'index OSV est vide et toute autre adresse répond 404.
// Usage : node support/sources-simulees.mjs   (port : ARGOS_E2E_PORT_SOURCES, 8099 par défaut)
import { createServer } from 'node:http'
import { readFileSync } from 'node:fs'
import { dirname, join, normalize, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const DONNEES = join(dirname(fileURLToPath(import.meta.url)), '..', 'donnees', 'chaine')
const PORT = Number(process.env.ARGOS_E2E_PORT_SOURCES ?? 8099)
const TAILLE_MAX_CORPS = 64 * 1024

// Date de génération des SBOM : « __DATE-<minutes>__ » est remplacé par l'instant de démarrage du serveur moins ce
// nombre de minutes, pour qu'une SBOM enregistrée ne devienne jamais obsolète (INV-03) et qu'une version suivante
// soit plus récente que la précédente.
const DEMARRAGE = Date.now()
let etat = { osv: [], forges: {} }
let requetes = []

/** Contenu d'un fichier enregistré de donnees/chaine/, sans sortir de ce dossier, dates de génération résolues. */
function fichier(nom) {
  const chemin = normalize(join(DONNEES, nom))
  if (!chemin.startsWith(DONNEES + sep)) throw new Error(`fichier hors de donnees/chaine : ${nom}`)
  return readFileSync(chemin, 'utf8').replace(/__DATE-(\d+)__/g, (_, minutes) =>
    new Date(DEMARRAGE - Number(minutes) * 60_000).toISOString().replace(/\.\d{3}Z$/, 'Z'))
}

function repondre(res, statut, corps, type = 'application/json') {
  res.writeHead(statut, { 'Content-Type': type, 'Cache-Control': 'no-store' })
  res.end(corps)
}

function lireCorps(req) {
  return new Promise((resoudre, rejeter) => {
    const morceaux = []
    let taille = 0
    req.on('data', (m) => {
      taille += m.length
      if (taille > TAILLE_MAX_CORPS) {
        rejeter(new Error('corps trop volumineux'))
        req.destroy()
      } else morceaux.push(m)
    })
    req.on('end', () => resoudre(Buffer.concat(morceaux).toString('utf8')))
    req.on('error', rejeter)
  })
}

/** Index modified_id.csv de l'export OSV : « date,identifiant », du plus récent au plus ancien. */
function indexOsv() {
  return [...etat.osv]
    .sort((a, b) => b.modified.localeCompare(a.modified))
    .map((a) => `${a.modified},${a.id}`)
    .join('\n')
}

async function traiter(req, res) {
  const url = new URL(req.url, `http://localhost:${PORT}`)
  const cible = `${url.pathname}${url.search}`
  if (url.pathname === '/__pret') return repondre(res, 200, '{"pret":true}')
  if (url.pathname === '/__requetes') return repondre(res, 200, JSON.stringify(requetes))
  if (url.pathname === '/__etat' && req.method === 'PUT') {
    const recu = JSON.parse(await lireCorps(req))
    for (const avis of recu.osv ?? []) fichier(avis.fichier) // fichier présent, ou refus immédiat
    for (const nom of Object.values(recu.forges ?? {})) fichier(nom)
    etat = { osv: recu.osv ?? [], forges: recu.forges ?? {} }
    requetes = []
    return repondre(res, 204, '')
  }
  requetes.push(`${req.method} ${cible}`)
  if (req.method !== 'GET') return repondre(res, 405, '{"message":"méthode non simulée"}')
  if (/^\/osv-export\/[^/]+\/modified_id\.csv$/.test(url.pathname)) return repondre(res, 200, indexOsv(), 'text/csv')
  const vuln = /^\/osv-api\/v1\/vulns\/([^/]+)$/.exec(url.pathname)
  if (vuln) {
    const avis = etat.osv.find((a) => a.id === decodeURIComponent(vuln[1]))
    return avis ? repondre(res, 200, fichier(avis.fichier)) : repondre(res, 404, '{"code":5,"message":"Bug not found."}')
  }
  if (url.pathname === '/kev.json') return repondre(res, 200, '{"title":"KEV simulé","count":0,"vulnerabilities":[]}')
  const forge = etat.forges[cible]
  if (forge) {
    const github = url.pathname.startsWith('/github/')
    return repondre(res, 200, fichier(forge), github ? 'application/vnd.github.raw+json; charset=utf-8' : 'application/json')
  }
  // Dépôt GitHub interrogé après un 404 de fichier : il existe (RepositoryFiles distingue fichier absent et dépôt).
  if (/^\/github\/repos\/[^/]+\/[^/]+$/.test(url.pathname)) return repondre(res, 200, '{"id":1,"private":true}')
  if (url.pathname.startsWith('/gitlab/')) return repondre(res, 404, '{"message":"404 File Not Found"}')
  return repondre(res, 404, '{"message":"Not Found"}')
}

// « localhost » se résout en 127.0.0.1 pour le serveur Java et souvent en ::1 pour Node : écoute sur les deux
// adresses de la boucle locale, jamais sur une interface réseau.
for (const hote of ['127.0.0.1', '::1']) {
  createServer((req, res) => {
    traiter(req, res).catch((e) => repondre(res, 400, JSON.stringify({ message: String(e?.message ?? e) })))
  }).listen(PORT, hote, () => {
    console.log(`sources simulées sur ${hote}:${PORT}`)
  })
}
