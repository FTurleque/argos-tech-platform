// Règles de sécurité de la session de l'IHM, vérifiées sur les sources (changement
// connecter-ihm-oidc : ACC-08-S1). Aucune dépendance : lecture des fichiers de src/.
//   - stockage du navigateur : seuls suivi-poste.ts (identifiants d'alertes) et session.ts (demande
//     transitoire du portail web, clé argos.oidc.demande) y écrivent ; aucun jeton n'y est écrit ;
//   - aucun cookie n'est lu ou écrit par l'IHM ;
//   - aucun v-html (contenus externes) ;
//   - aucun journal dans la couche session.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(RACINE, 'src')

function fichiers(dossier) {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom)
    if (statSync(chemin).isDirectory()) return fichiers(chemin)
    return /\.(ts|vue|js|mjs)$/.test(nom) ? [chemin] : []
  })
}

const sources = fichiers(SRC).map((chemin) => ({
  nom: relative(SRC, chemin).split(sep).join('/'),
  texte: readFileSync(chemin, 'utf8'),
}))

test('ACC-08-S1 : seuls deux modules touchent au stockage du navigateur', () => {
  const utilisateurs = sources.filter((s) => /\b(localStorage|sessionStorage|indexedDB)\b/.test(s.texte)).map((s) => s.nom)
  assert.deepEqual(utilisateurs.sort(), ['contenu/suivi-poste.ts', 'session/session.ts'])
})

test('ACC-08-S1 : la session n’écrit dans le stockage de session que la demande transitoire', () => {
  const session = sources.find((s) => s.nom === 'session/session.ts').texte
  assert.doesNotMatch(session, /localStorage|indexedDB/)
  const ecritures = [...session.matchAll(/sessionStorage\.setItem\(([^,]+),\s*([^)]+\))/g)]
  assert.equal(ecritures.length, 1)
  assert.equal(ecritures[0][1].trim(), 'CLE_DEMANDE')
  assert.match(ecritures[0][2], /JSON\.stringify\(demande\)/)
  assert.match(session, /sessionStorage\.removeItem\(CLE_DEMANDE\)/)
  // La demande ne contient ni jeton ni code : seulement state, vérificateur, adresse et vue.
  const gestionnaire = sources.find((s) => s.nom === 'session/gestionnaire.ts').texte
  const demande = gestionnaire.match(/export interface DemandeAutorisation \{([^}]*)\}/)[1]
  const champs = [...demande.matchAll(/^\s*(\w+):/gm)].map((m) => m[1])
  assert.deepEqual(champs, ['state', 'verificateur', 'redirectUri', 'retour'])
})

test('aucun cookie lu ou écrit par l’IHM', () => {
  assert.deepEqual(sources.filter((s) => /document\.cookie/.test(s.texte)).map((s) => s.nom), [])
})

test('aucun v-html dans l’IHM', () => {
  assert.deepEqual(sources.filter((s) => /v-html/.test(s.texte)).map((s) => s.nom), [])
})

test('aucun journal dans la couche session ni dans le client REST', () => {
  const concernes = sources.filter((s) => s.nom.startsWith('session/') || s.nom.startsWith('api/'))
  assert.ok(concernes.length >= 5)
  assert.deepEqual(concernes.filter((s) => /\bconsole\./.test(s.texte)).map((s) => s.nom), [])
})
