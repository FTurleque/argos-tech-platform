// Reprise du suivi des alertes du poste avec la boucle réelle (notifier-sur-le-poste : NOT-01,
// NOT-06-S1). `demarrer()` planifie ses lectures avec setTimeout, dont l'horloge est simulée par
// `mock.timers` de node:test ; le serveur est simulé en mémoire selon le contrat de
// `AlertesResource` (une alerte DESKTOP reste EN_ATTENTE tant que sa remise n'est pas confirmée).
// Aucune requête réseau. Le parcours contre un serveur réel reste à faire (tâche 3.2).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  INTERVALLE_MS,
  REPRISE_INITIALE_MS,
  SuiviAlertesPoste,
} from '../src/contenu/alertes-poste.ts'

let sequence = 0

function alerte() {
  sequence += 1
  const n = String(sequence).padStart(12, '0')
  return {
    id: `00000000-0000-4000-9000-${n}`,
    nature: 'IMMEDIATE',
    canal: 'DESKTOP',
    etat: 'EN_ATTENTE',
    titre: `Avis ${sequence}`,
    gravite: 'HIGH',
    contenu: { elements: [{ projets: [{ id: 'p1', nom: 'portail' }] }] },
    creeLe: `2026-11-17T08:00:${String(sequence % 60).padStart(2, '0')}Z`,
  }
}

/** Serveur simulé : ne renvoie que les alertes EN_ATTENTE (l'état tient lieu de curseur, D1). */
function serveur() {
  const s = {
    alertes: [],
    joignable: true,
    lectures: 0,
    remises: [],
    async lister() {
      s.lectures += 1
      if (!s.joignable) throw new Error('injoignable')
      return s.alertes.filter((a) => a.etat === 'EN_ATTENTE').map((a) => ({ ...a }))
    },
    async remettre(id) {
      if (!s.joignable) throw new Error('injoignable')
      s.alertes.find((a) => a.id === id).etat = 'ENVOYEE'
      s.remises.push(id)
    },
  }
  return s
}

/** Laisse s'exécuter les promesses en attente (le cycle est asynchrone). */
async function laisserTourner() {
  for (let i = 0; i < 20; i += 1) await Promise.resolve()
}

function poste(s, memoire = { ouvertes: [], traitees: [] }) {
  const p = { notifications: [], compteurs: [], etats: [], memoire }
  const suivi = new SuiviAlertesPoste({
    lister: s.lister,
    remettre: s.remettre,
    notifier: async (a) => { p.notifications.push(a.id); return true },
    compteur: (n) => p.compteurs.push(n),
    surEtat: (e) => p.etats.push(e),
    stockage: { lire: () => [...memoire.ouvertes], ecrire: (ids) => { memoire.ouvertes = [...ids] } },
    stockageTraitees: { lire: () => [...memoire.traitees], ecrire: (ids) => { memoire.traitees = [...ids] } },
  })
  return { suivi, p }
}

test('NOT-06-S1 : boucle réelle, coupure pendant deux alertes puis retour : deux alertes, sans perte ni doublon', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const s = serveur()
  s.alertes.push(alerte())
  const { suivi, p } = poste(s)

  suivi.demarrer()
  await laisserTourner()
  assert.equal(s.lectures, 1, 'première lecture immédiate')
  assert.equal(p.notifications.length, 1)
  assert.equal(suivi.etat.connexion, 'CONNECTE')

  t.mock.timers.tick(INTERVALLE_MS - 1)
  await laisserTourner()
  assert.equal(s.lectures, 1, 'pas de lecture avant 30 s')

  s.joignable = false
  t.mock.timers.tick(1)
  await laisserTourner()
  assert.equal(s.lectures, 2)
  assert.equal(suivi.etat.connexion, 'HORS_LIGNE', 'perte de connexion signalée')

  const pendantCoupure = [alerte(), alerte()]
  s.alertes.push(...pendantCoupure)
  t.mock.timers.tick(REPRISE_INITIALE_MS)
  await laisserTourner()
  assert.equal(s.lectures, 3, 'nouvelle tentative au bout de 5 s')
  assert.equal(suivi.etat.connexion, 'HORS_LIGNE')

  s.joignable = true
  t.mock.timers.tick(REPRISE_INITIALE_MS * 2 - 1)
  await laisserTourner()
  assert.equal(s.lectures, 3, 'attente doublée après le deuxième échec')
  t.mock.timers.tick(1)
  await laisserTourner()
  assert.equal(s.lectures, 4)
  assert.equal(suivi.etat.connexion, 'CONNECTE')
  assert.equal(suivi.etat.recuesPendantCoupure, 2, 'le client indique les deux alertes émises pendant la coupure')
  assert.deepEqual(p.notifications.slice(1), pendantCoupure.map((a) => a.id))

  t.mock.timers.tick(INTERVALLE_MS)
  await laisserTourner()
  assert.equal(s.lectures, 5)
  assert.equal(p.notifications.length, 3, 'aucune alerte notifiée deux fois')
  assert.equal(new Set(s.remises).size, 3, 'aucune alerte perdue : toutes remises')
  assert.ok(s.alertes.every((a) => a.etat === 'ENVOYEE'))
  assert.equal(p.compteurs.at(-1), 3)

  suivi.arreter()
  t.mock.timers.tick(INTERVALLE_MS * 4)
  await laisserTourner()
  assert.equal(s.lectures, 5, 'plus aucune lecture après l’arrêt')
})

test('NOT-06 : rechargement de la fenêtre pendant une coupure, puis retour : rien de perdu ni de renotifié', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const s = serveur()
  const deja = alerte()
  s.alertes.push(deja)
  const memoire = { ouvertes: [], traitees: [] }
  const premier = poste(s, memoire)
  premier.suivi.demarrer()
  await laisserTourner()
  // La confirmation de remise est perdue : l'alerte reste EN_ATTENTE côté serveur.
  deja.etat = 'EN_ATTENTE'
  s.remises.length = 0
  s.joignable = false
  t.mock.timers.tick(INTERVALLE_MS)
  await laisserTourner()
  premier.suivi.arreter() // rechargement de la fenêtre

  const nouvelle = alerte()
  s.alertes.push(nouvelle)
  const second = poste(s, memoire)
  second.suivi.demarrer()
  await laisserTourner()
  assert.equal(second.suivi.etat.connexion, 'HORS_LIGNE')
  s.joignable = true
  t.mock.timers.tick(REPRISE_INITIALE_MS)
  await laisserTourner()
  assert.equal(second.suivi.etat.connexion, 'CONNECTE')
  assert.deepEqual(second.p.notifications, [nouvelle.id], 'seule l’alerte nouvelle est notifiée')
  assert.deepEqual(s.remises.sort(), [deja.id, nouvelle.id].sort(), 'les deux sont remises')
  assert.equal(second.suivi.etat.ouvertes, 2)
  second.suivi.arreter()
})

test('démarrer deux fois ne lance qu’une seule boucle de lecture', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const s = serveur()
  const { suivi } = poste(s)
  suivi.demarrer()
  suivi.demarrer()
  await laisserTourner()
  t.mock.timers.tick(INTERVALLE_MS)
  await laisserTourner()
  assert.equal(s.lectures, 2)
  suivi.arreter()
})
