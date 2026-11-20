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
  const numero = String(sequence).padStart(12, '0')
  return {
    id: `00000000-0000-4000-9000-${numero}`,
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
  const simule = {
    alertes: [],
    joignable: true,
    lectures: 0,
    remises: [],
    async lister() {
      simule.lectures += 1
      if (!simule.joignable) throw new Error('injoignable')
      return simule.alertes.filter((alerte) => alerte.etat === 'EN_ATTENTE').map((alerte) => ({ ...alerte }))
    },
    async remettre(id) {
      if (!simule.joignable) throw new Error('injoignable')
      simule.alertes.find((alerte) => alerte.id === id).etat = 'ENVOYEE'
      simule.remises.push(id)
    },
  }
  return simule
}

/** Laisse s'exécuter les promesses en attente (le cycle est asynchrone). */
async function laisserTourner() {
  for (let tour = 0; tour < 20; tour += 1) await Promise.resolve()
}

function poste(serveurSimule, memoire = { ouvertes: [], traitees: [] }) {
  const releve = { notifications: [], compteurs: [], etats: [], memoire }
  const suivi = new SuiviAlertesPoste({
    lister: serveurSimule.lister,
    remettre: serveurSimule.remettre,
    notifier: async (alerte) => { releve.notifications.push(alerte.id); return true },
    compteur: (nombre) => releve.compteurs.push(nombre),
    surEtat: (etat) => releve.etats.push(etat),
    stockage: { lire: () => [...memoire.ouvertes], ecrire: (ids) => { memoire.ouvertes = [...ids] } },
    stockageTraitees: { lire: () => [...memoire.traitees], ecrire: (ids) => { memoire.traitees = [...ids] } },
  })
  return { suivi, releve }
}

test('NOT-06-S1 : boucle réelle, coupure pendant deux alertes puis retour : deux alertes, sans perte ni doublon', async (contexte) => {
  contexte.mock.timers.enable({ apis: ['setTimeout'] })
  const serveurSimule = serveur()
  serveurSimule.alertes.push(alerte())
  const { suivi, releve } = poste(serveurSimule)

  suivi.demarrer()
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 1, 'première lecture immédiate')
  assert.equal(releve.notifications.length, 1)
  assert.equal(suivi.etat.connexion, 'CONNECTE')

  contexte.mock.timers.tick(INTERVALLE_MS - 1)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 1, 'pas de lecture avant 30 s')

  serveurSimule.joignable = false
  contexte.mock.timers.tick(1)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 2)
  assert.equal(suivi.etat.connexion, 'HORS_LIGNE', 'perte de connexion signalée')

  const pendantCoupure = [alerte(), alerte()]
  serveurSimule.alertes.push(...pendantCoupure)
  contexte.mock.timers.tick(REPRISE_INITIALE_MS)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 3, 'nouvelle tentative au bout de 5 s')
  assert.equal(suivi.etat.connexion, 'HORS_LIGNE')

  serveurSimule.joignable = true
  contexte.mock.timers.tick(REPRISE_INITIALE_MS * 2 - 1)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 3, 'attente doublée après le deuxième échec')
  contexte.mock.timers.tick(1)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 4)
  assert.equal(suivi.etat.connexion, 'CONNECTE')
  assert.equal(suivi.etat.recuesPendantCoupure, 2, 'le client indique les deux alertes émises pendant la coupure')
  assert.deepEqual(releve.notifications.slice(1), pendantCoupure.map((alerte) => alerte.id))

  contexte.mock.timers.tick(INTERVALLE_MS)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 5)
  assert.equal(releve.notifications.length, 3, 'aucune alerte notifiée deux fois')
  assert.equal(new Set(serveurSimule.remises).size, 3, 'aucune alerte perdue : toutes remises')
  assert.ok(serveurSimule.alertes.every((alerte) => alerte.etat === 'ENVOYEE'))
  assert.equal(releve.compteurs.at(-1), 3)

  suivi.arreter()
  contexte.mock.timers.tick(INTERVALLE_MS * 4)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 5, 'plus aucune lecture après l’arrêt')
})

test('NOT-06 : rechargement de la fenêtre pendant une coupure, puis retour : rien de perdu ni de renotifié', async (contexte) => {
  contexte.mock.timers.enable({ apis: ['setTimeout'] })
  const serveurSimule = serveur()
  const deja = alerte()
  serveurSimule.alertes.push(deja)
  const memoire = { ouvertes: [], traitees: [] }
  const premier = poste(serveurSimule, memoire)
  premier.suivi.demarrer()
  await laisserTourner()
  // La confirmation de remise est perdue : l'alerte reste EN_ATTENTE côté serveur.
  deja.etat = 'EN_ATTENTE'
  serveurSimule.remises.length = 0
  serveurSimule.joignable = false
  contexte.mock.timers.tick(INTERVALLE_MS)
  await laisserTourner()
  premier.suivi.arreter() // rechargement de la fenêtre

  const nouvelle = alerte()
  serveurSimule.alertes.push(nouvelle)
  const second = poste(serveurSimule, memoire)
  second.suivi.demarrer()
  await laisserTourner()
  assert.equal(second.suivi.etat.connexion, 'HORS_LIGNE')
  serveurSimule.joignable = true
  contexte.mock.timers.tick(REPRISE_INITIALE_MS)
  await laisserTourner()
  assert.equal(second.suivi.etat.connexion, 'CONNECTE')
  assert.deepEqual(second.releve.notifications, [nouvelle.id], 'seule l’alerte nouvelle est notifiée')
  assert.deepEqual(serveurSimule.remises.sort(), [deja.id, nouvelle.id].sort(), 'les deux sont remises')
  assert.equal(second.suivi.etat.ouvertes, 2)
  second.suivi.arreter()
})

test('démarrer deux fois ne lance qu’une seule boucle de lecture', async (contexte) => {
  contexte.mock.timers.enable({ apis: ['setTimeout'] })
  const serveurSimule = serveur()
  const { suivi } = poste(serveurSimule)
  suivi.demarrer()
  suivi.demarrer()
  await laisserTourner()
  contexte.mock.timers.tick(INTERVALLE_MS)
  await laisserTourner()
  assert.equal(serveurSimule.lectures, 2)
  suivi.arreter()
})
