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
  const n = String(sequence).padStart(12, '0')
  return {
    id: `00000000-0000-4000-8000-${n}`,
    nature: 'IMMEDIATE',
    canal: 'DESKTOP',
    etat: 'EN_ATTENTE',
    titre: `Avis ${sequence}`,
    gravite: 'CRITICAL',
    contenu: {
      elements: [{ projets: projets.map((nom, i) => ({ id: `p-${nom}-${i}`, nom, niveau: 'EXACT' })) }],
    },
    creeLe: `2026-11-16T08:00:${String(sequence % 60).padStart(2, '0')}Z`,
    ...extra,
  }
}

/** Serveur simulé : alertes du compte, coupure, refus de confirmation. */
function serveur() {
  const s = {
    alertes: [],
    joignable: true,
    statut: undefined,
    remiseEnEchec: false,
    remises: [],
    async lister() {
      if (!s.joignable) throw Object.assign(new Error('injoignable'), { status: s.statut })
      return s.alertes.filter((a) => a.etat === 'EN_ATTENTE').map((a) => ({ ...a }))
    },
    async remettre(id) {
      if (!s.joignable || s.remiseEnEchec) throw new Error('remise impossible')
      const a = s.alertes.find((x) => x.id === id)
      a.etat = 'ENVOYEE'
      s.remises.push(id)
    },
  }
  return s
}

function suivi(s, options = {}) {
  const poste = { notifications: [], compteurs: [], etats: [], memoire: options.memoire ?? [], traitees: options.traitees ?? [] }
  const suiviAlertes = new SuiviAlertesPoste({
    lister: s.lister,
    remettre: s.remettre,
    notifier: async (a) => {
      poste.notifications.push(a)
      return options.notificationsAffichees ?? true
    },
    compteur: (n) => poste.compteurs.push(n),
    surEtat: (e) => poste.etats.push(e),
    stockage: { lire: () => [...poste.memoire], ecrire: (ids) => { poste.memoire = [...ids] } },
    stockageTraitees: { lire: () => [...poste.traitees], ecrire: (ids) => { poste.traitees = [...ids] } },
    planifier: () => () => {},
  })
  return { suiviAlertes, poste }
}

test('NOT-01 : une alerte immédiate en attente est notifiée puis sa remise est confirmée', async () => {
  const s = serveur()
  const a = alerte(['portail-rh', 'paie', 'intranet'])
  s.alertes.push(a)
  const { suiviAlertes, poste } = suivi(s)
  assert.equal(await suiviAlertes.cycle(), INTERVALLE_MS)
  assert.deepEqual(poste.notifications, [{ id: a.id, gravite: 'CRITICAL', projets: 3 }])
  assert.deepEqual(s.remises, [a.id])
  assert.equal(suiviAlertes.etat.connexion, 'CONNECTE')
})

test('NOT-01-S2 : notification refusée par le système, l’alerte est quand même comptée', async () => {
  const s = serveur()
  s.alertes.push(alerte())
  const { suiviAlertes, poste } = suivi(s, { notificationsAffichees: false })
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.ouvertes, 1)
  assert.equal(poste.compteurs.at(-1), 1)
})

test('NOT-03-S1 : deux alertes ouvertes, une troisième arrive, le compteur passe à trois', async () => {
  const s = serveur()
  s.alertes.push(alerte(), alerte())
  const { suiviAlertes, poste } = suivi(s)
  await suiviAlertes.cycle()
  assert.equal(poste.compteurs.at(-1), 2)
  s.alertes.push(alerte())
  await suiviAlertes.cycle()
  assert.equal(poste.compteurs.at(-1), 3)
  suiviAlertes.marquerConsultee(s.alertes[0].id)
  assert.equal(poste.compteurs.at(-1), 2)
})

test('NOT-03 : les alertes ouvertes sont retrouvées après un redémarrage (identifiants seulement)', async () => {
  const s = serveur()
  s.alertes.push(alerte())
  const premier = suivi(s)
  await premier.suiviAlertes.cycle()
  assert.deepEqual(premier.poste.memoire, [s.alertes[0].id])
  const second = suivi(s, { memoire: premier.poste.memoire })
  assert.equal(second.suiviAlertes.etat.ouvertes, 1)
})

test('NOT-06-S1 : serveur injoignable pendant deux alertes, puis rétabli : deux alertes, sans perte ni doublon', async () => {
  const s = serveur()
  s.alertes.push(alerte())
  const { suiviAlertes, poste } = suivi(s)
  await suiviAlertes.cycle()
  assert.equal(poste.notifications.length, 1)

  s.joignable = false
  assert.equal(await suiviAlertes.cycle(), REPRISE_INITIALE_MS)
  assert.equal(suiviAlertes.etat.connexion, 'HORS_LIGNE')
  const a2 = alerte()
  const a3 = alerte()
  s.alertes.push(a2, a3) // émises pendant la coupure
  assert.equal(await suiviAlertes.cycle(), REPRISE_INITIALE_MS * 2)

  s.joignable = true
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.connexion, 'CONNECTE')
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 2)
  assert.deepEqual(poste.notifications.map((n) => n.id).slice(1), [a2.id, a3.id])

  await suiviAlertes.cycle()
  assert.equal(poste.notifications.length, 3, 'aucune alerte notifiée deux fois')
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 2, 'information gardée jusqu’à sa lecture')
  suiviAlertes.oublierCoupure()
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 0)
  assert.equal(new Set(s.remises).size, 3)
})

test('NOT-06 : une confirmation de remise perdue n’entraîne ni perte ni seconde notification', async () => {
  const s = serveur()
  const a = alerte()
  s.alertes.push(a)
  s.remiseEnEchec = true
  const { suiviAlertes, poste } = suivi(s)
  await suiviAlertes.cycle()
  assert.equal(a.etat, 'EN_ATTENTE')
  s.remiseEnEchec = false
  await suiviAlertes.cycle()
  assert.equal(a.etat, 'ENVOYEE')
  assert.equal(poste.notifications.length, 1)
})

test('un refus d’authentification est distingué d’une coupure', async () => {
  const s = serveur()
  s.joignable = false
  s.statut = 401
  const { suiviAlertes } = suivi(s)
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.connexion, 'NON_AUTORISE')
})

test('un résumé DESKTOP est remis sans notification ni compteur ; les autres canaux sont ignorés', async () => {
  const s = serveur()
  const resume = alerte([], { nature: 'DAILY' })
  const email = alerte(['paie'], { canal: 'EMAIL' })
  s.alertes.push(resume, email)
  const { suiviAlertes, poste } = suivi(s)
  await suiviAlertes.cycle()
  assert.equal(poste.notifications.length, 0)
  assert.equal(suiviAlertes.etat.ouvertes, 0)
  assert.deepEqual(s.remises, [resume.id])
})

test('NOT-05-S1 : le poste ne reçoit ni titre, ni nom de projet, ni composant', () => {
  const a = alerte(['Portail RH', 'paie', 'paie'], { titre: 'Prototype pollution in lodash (portail rh)' })
  const p = alertePoste(a)
  assert.deepEqual(Object.keys(p).sort(), ['gravite', 'id', 'projets'])
  assert.equal(p.projets, 3)
  const transmis = JSON.stringify(p)
  for (const interdit of ['lodash', 'Portail', 'paie', 'Prototype']) assert.ok(!transmis.includes(interdit), interdit)
})

test('NOT-06 : un rechargement de la fenêtre ne renotifie pas une alerte dont la remise a échoué', async () => {
  const s = serveur()
  const a = alerte()
  s.alertes.push(a)
  s.remiseEnEchec = true
  const premier = suivi(s)
  await premier.suiviAlertes.cycle()
  assert.equal(premier.poste.notifications.length, 1)
  s.remiseEnEchec = false
  const second = suivi(s, { memoire: premier.poste.memoire, traitees: premier.poste.traitees })
  await second.suiviAlertes.cycle()
  assert.equal(second.poste.notifications.length, 0)
  assert.equal(a.etat, 'ENVOYEE')
})

test('NOT-06 : la première connexion n’est pas une reprise ; les résumés ne comptent pas', async () => {
  const s = serveur()
  s.joignable = false
  s.statut = 401
  const { suiviAlertes } = suivi(s)
  await suiviAlertes.cycle()
  s.joignable = true
  s.alertes.push(alerte(), alerte([], { nature: 'DAILY' }))
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 0)
  s.joignable = false
  s.statut = undefined
  await suiviAlertes.cycle()
  s.alertes.push(alerte(), alerte([], { nature: 'WEEKLY' }))
  s.joignable = true
  await suiviAlertes.cycle()
  assert.equal(suiviAlertes.etat.recuesPendantCoupure, 1)
})

test('attente croissante après des échecs, bornée à cinq minutes', () => {
  assert.equal(delaiReprise(0), INTERVALLE_MS)
  assert.equal(delaiReprise(1), 5_000)
  assert.equal(delaiReprise(3), 20_000)
  assert.equal(delaiReprise(50), REPRISE_MAX_MS)
})
