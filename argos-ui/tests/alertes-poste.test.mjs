// Suivi des alertes du poste (changement notifier-sur-le-poste : NOT-01, NOT-03, NOT-05, NOT-06).
// Le serveur est simulé en mémoire selon le contrat de `AlertesResource` : une alerte DESKTOP reste
// EN_ATTENTE tant que sa remise n'est pas confirmée. Aucune requête réseau.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  INTERVALLE_MS,
  REPRISE_INITIALE_MS,
  REPRISE_MAX_MS,
  SuiviAlertesPoste,
  alertePoste,
  delaiReprise,
} from '../src/contenu/alertes-poste.ts'

let sequence = 0

function alerte(projets = ['portail-rh'], extra = {}) {
  sequence += 1
  const numero = String(sequence).padStart(12, '0')
  return {
    id: `00000000-0000-4000-8000-${numero}`,
    nature: 'IMMEDIATE',
    canal: 'DESKTOP',
    etat: 'EN_ATTENTE',
    titre: `Avis ${sequence}`,
    gravite: 'CRITICAL',
    contenu: {
      elements: [{ projets: projets.map((nom, rang) => ({ id: `p-${nom}-${rang}`, nom, niveau: 'EXACT' })) }],
    },
    creeLe: `2026-11-16T08:00:${String(sequence % 60).padStart(2, '0')}Z`,
    ...extra,
  }
}

/** Serveur simulé : alertes du compte, coupure, refus de confirmation. */
function serveur() {
  const simule = {
    alertes: [],
    joignable: true,
    statut: undefined,
    remiseEnEchec: false,
    remises: [],
    async lister() {
      if (!simule.joignable) throw Object.assign(new Error('injoignable'), { status: simule.statut })
      return simule.alertes.filter((enAttente) => enAttente.etat === 'EN_ATTENTE').map((enAttente) => ({ ...enAttente }))
    },
    async remettre(id) {
      if (!simule.joignable || simule.remiseEnEchec) throw new Error('remise impossible')
      const remise = simule.alertes.find((candidate) => candidate.id === id)
      remise.etat = 'ENVOYEE'
      simule.remises.push(id)
    },
  }
  return simule
}

function suivi(serveurSimule, options = {}) {
  const poste = { notifications: [], compteurs: [], etats: [], memoire: options.memoire ?? [], traitees: options.traitees ?? [] }
  const suiviAlertes = new SuiviAlertesPoste({
    lister: serveurSimule.lister,
    remettre: serveurSimule.remettre,
    notifier: async (notifiee) => {
      poste.notifications.push(notifiee)
      return options.notificationsAffichees ?? true
    },
    compteur: (nombre) => poste.compteurs.push(nombre),
    surEtat: (etat) => poste.etats.push(etat),
    stockage: { lire: () => [...poste.memoire], ecrire: (ids) => { poste.memoire = [...ids] } },
    stockageTraitees: { lire: () => [...poste.traitees], ecrire: (ids) => { poste.traitees = [...ids] } },
    planifier: () => () => {},
  })
  return { suiviAlertes, poste }
}

test('NOT-01 : une alerte immédiate en attente est notifiée puis sa remise est confirmée', async () => {
  const serveurSimule = serveur()
  const alerteEmise = alerte(['portail-rh', 'paie', 'intranet'])
  serveurSimule.alertes.push(alerteEmise)
  const { suiviAlertes, poste } = suivi(serveurSimule)
  assert.equal(await suiviAlertes.cycle(), INTERVALLE_MS)
  assert.deepEqual(poste.notifications, [{ id: alerteEmise.id, gravite: 'CRITICAL', projets: 3 }])
  assert.deepEqual(serveurSimule.remises, [alerteEmise.id])
  assert.equal(suiviAlertes.etat.connexion, 'CONNECTE')
})

test('NOT-01-S2 : notification refusée par le système, l’alerte est quand même comptée', async () => {
  const serveurSimule = serveur()
  serveurSimule.alertes.push(alerte())
  const { suiviAlertes, poste } = suivi(serveurSimule, { notificationsAffichees: false })
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.ouvertes, 1)
  assert.equal(poste.compteurs.at(-1), 1)
})

test('NOT-03-S1 : deux alertes ouvertes, une troisième arrive, le compteur passe à trois', async () => {
  const serveurSimule = serveur()
  serveurSimule.alertes.push(alerte(), alerte())
  const { suiviAlertes, poste } = suivi(serveurSimule)
  await suiviAlertes.cycle()
  assert.equal(poste.compteurs.at(-1), 2)
  serveurSimule.alertes.push(alerte())
  await suiviAlertes.cycle()
  assert.equal(poste.compteurs.at(-1), 3)
  suiviAlertes.marquerConsultee(serveurSimule.alertes[0].id)
  assert.equal(poste.compteurs.at(-1), 2)
})

test('NOT-03 : les alertes ouvertes sont retrouvées après un redémarrage (identifiants seulement)', async () => {
  const serveurSimule = serveur()
  serveurSimule.alertes.push(alerte())
  const premier = suivi(serveurSimule)
  await premier.suiviAlertes.cycle()
  assert.deepEqual(premier.poste.memoire, [serveurSimule.alertes[0].id])
  const second = suivi(serveurSimule, { memoire: premier.poste.memoire })
  assert.equal(second.suiviAlertes.etat.ouvertes, 1)
})

test('NOT-06-S1 : serveur injoignable pendant deux alertes, puis rétabli : deux alertes, sans perte ni doublon', async () => {
  const serveurSimule = serveur()
  serveurSimule.alertes.push(alerte())
  const { suiviAlertes, poste } = suivi(serveurSimule)
  await suiviAlertes.cycle()
  assert.equal(poste.notifications.length, 1)

  serveurSimule.joignable = false
  assert.equal(await suiviAlertes.cycle(), REPRISE_INITIALE_MS)
  assert.equal(suiviAlertes.etat.connexion, 'HORS_LIGNE')
  const alerte2 = alerte()
  const alerte3 = alerte()
  serveurSimule.alertes.push(alerte2, alerte3) // émises pendant la coupure
  assert.equal(await suiviAlertes.cycle(), REPRISE_INITIALE_MS * 2)

  serveurSimule.joignable = true
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.connexion, 'CONNECTE')
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 2)
  assert.deepEqual(poste.notifications.map((notifiee) => notifiee.id).slice(1), [alerte2.id, alerte3.id])

  await suiviAlertes.cycle()
  assert.equal(poste.notifications.length, 3, 'aucune alerte notifiée deux fois')
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 2, 'information gardée jusqu’à sa lecture')
  suiviAlertes.oublierCoupure()
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 0)
  assert.equal(new Set(serveurSimule.remises).size, 3)
})

test('NOT-06 : une confirmation de remise perdue n’entraîne ni perte ni seconde notification', async () => {
  const serveurSimule = serveur()
  const alerteEmise = alerte()
  serveurSimule.alertes.push(alerteEmise)
  serveurSimule.remiseEnEchec = true
  const { suiviAlertes, poste } = suivi(serveurSimule)
  await suiviAlertes.cycle()
  assert.equal(alerteEmise.etat, 'EN_ATTENTE')
  serveurSimule.remiseEnEchec = false
  await suiviAlertes.cycle()
  assert.equal(alerteEmise.etat, 'ENVOYEE')
  assert.equal(poste.notifications.length, 1)
})

test('un refus d’authentification est distingué d’une coupure', async () => {
  const serveurSimule = serveur()
  serveurSimule.joignable = false
  serveurSimule.statut = 401
  const { suiviAlertes } = suivi(serveurSimule)
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.connexion, 'NON_AUTORISE')
})

test('un résumé DESKTOP est remis sans notification ni compteur ; les autres canaux sont ignorés', async () => {
  const serveurSimule = serveur()
  const resume = alerte([], { nature: 'DAILY' })
  const email = alerte(['paie'], { canal: 'EMAIL' })
  serveurSimule.alertes.push(resume, email)
  const { suiviAlertes, poste } = suivi(serveurSimule)
  await suiviAlertes.cycle()
  assert.equal(poste.notifications.length, 0)
  assert.equal(suiviAlertes.etat.ouvertes, 0)
  assert.deepEqual(serveurSimule.remises, [resume.id])
})

test('NOT-05-S1 : le poste ne reçoit ni titre, ni nom de projet, ni composant', () => {
  const alerteEmise = alerte(['Portail RH', 'paie', 'paie'], { titre: 'Prototype pollution in lodash (portail rh)' })
  const transmise = alertePoste(alerteEmise)
  assert.deepEqual(Object.keys(transmise).sort(), ['gravite', 'id', 'projets'])
  assert.equal(transmise.projets, 3)
  const transmis = JSON.stringify(transmise)
  for (const interdit of ['lodash', 'Portail', 'paie', 'Prototype']) assert.ok(!transmis.includes(interdit), interdit)
})

test('NOT-06 : un rechargement de la fenêtre ne renotifie pas une alerte dont la remise a échoué', async () => {
  const serveurSimule = serveur()
  const alerteEmise = alerte()
  serveurSimule.alertes.push(alerteEmise)
  serveurSimule.remiseEnEchec = true
  const premier = suivi(serveurSimule)
  await premier.suiviAlertes.cycle()
  assert.equal(premier.poste.notifications.length, 1)
  serveurSimule.remiseEnEchec = false
  const second = suivi(serveurSimule, { memoire: premier.poste.memoire, traitees: premier.poste.traitees })
  await second.suiviAlertes.cycle()
  assert.equal(second.poste.notifications.length, 0)
  assert.equal(alerteEmise.etat, 'ENVOYEE')
})

test('NOT-06 : la première connexion n’est pas une reprise ; les résumés ne comptent pas', async () => {
  const serveurSimule = serveur()
  serveurSimule.joignable = false
  serveurSimule.statut = 401
  const { suiviAlertes } = suivi(serveurSimule)
  await suiviAlertes.cycle()
  serveurSimule.joignable = true
  serveurSimule.alertes.push(alerte(), alerte([], { nature: 'DAILY' }))
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 0)
  serveurSimule.joignable = false
  serveurSimule.statut = undefined
  await suiviAlertes.cycle()
  serveurSimule.alertes.push(alerte(), alerte([], { nature: 'WEEKLY' }))
  serveurSimule.joignable = true
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 1)
})

test('attente croissante après des échecs, bornée à cinq minutes', () => {
  assert.equal(delaiReprise(0), INTERVALLE_MS)
  assert.equal(delaiReprise(1), 5_000)
  assert.equal(delaiReprise(3), 20_000)
  assert.equal(delaiReprise(50), REPRISE_MAX_MS)
})

test('cycle : ordre des notifications et des remises, délais renvoyés et reprise après un refus', async () => {
  const journal = []
  const sansDate = alerte(['a'], { creeLe: undefined })
  const recente = alerte(['b'], { creeLe: '2026-11-16T09:00:00Z' })
  const ancienne = alerte(['c'], { creeLe: '2026-11-16T07:00:00Z' })
  const resume = alerte([], { nature: 'DAILY', creeLe: '2026-11-16T08:00:00Z' })
  const dejaRemise = alerte(['d'], { etat: 'ENVOYEE', creeLe: '2026-11-16T06:00:00Z' })
  let reponse = () => Promise.reject(Object.assign(new Error('refus'), { status: 403 }))
  const suiviAlertes = new SuiviAlertesPoste({
    lister: () => reponse(),
    remettre: async (id) => {
      journal.push(`remise ${id}`)
    },
    notifier: async (notifiee) => {
      journal.push(`notification ${notifiee.id}`)
      if (notifiee.id === recente.id) throw new Error('notification indisponible')
      return true
    },
    compteur: (nombre) => journal.push(`compteur ${nombre}`),
    surEtat: (etat) => journal.push(`etat ${etat.connexion} ${etat.ouvertes} ${etat.recuesPendantCoupure}`),
    planifier: () => () => {},
  })
  assert.equal(await suiviAlertes.cycle(), REPRISE_INITIALE_MS)
  assert.equal(suiviAlertes.etat.connexion, 'NON_AUTORISE')
  assert.equal(await suiviAlertes.cycle(), REPRISE_INITIALE_MS * 2)
  reponse = async () => []
  assert.equal(await suiviAlertes.cycle(), INTERVALLE_MS)
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 0)
  reponse = () => Promise.reject(new Error('coupure'))
  assert.equal(await suiviAlertes.cycle(), REPRISE_INITIALE_MS)
  assert.equal(suiviAlertes.etat.connexion, 'HORS_LIGNE')
  reponse = async () => [recente, resume, sansDate, dejaRemise, ancienne, recente]
  journal.length = 0
  assert.equal(await suiviAlertes.cycle(), INTERVALLE_MS)
  assert.deepEqual(journal, [
    `notification ${sansDate.id}`,
    `remise ${sansDate.id}`,
    `notification ${ancienne.id}`,
    `remise ${ancienne.id}`,
    `remise ${resume.id}`,
    `notification ${recente.id}`,
    `remise ${recente.id}`,
    `remise ${recente.id}`,
    'compteur 3',
    'etat CONNECTE 3 3',
  ])
})
