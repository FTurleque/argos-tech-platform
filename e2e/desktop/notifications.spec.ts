// Parcours des alertes du poste dans le client desktop Electron (changement notifier-sur-le-poste,
// tâches 1.1, 1.2 et 3.2). Le desktop est lancé tel qu'il est livré et connecté à la pile locale
// (support/electron.ts) ; les alertes immédiates DESKTOP sont écrites dans la base de test comme les
// écrit le moteur d'alertes (donnees/alertes-poste.sql) et lues par l'IHM toutes les 30 s.
//
// Observé dans le processus principal par les doublures de support/electron.ts :
// - les objets Notification réels d'Electron créés et affichés (`show` enveloppé, puis appelé) ;
//   le clic sur la bulle est simulé en émettant l'événement `click` de la notification créée ;
// - les infobulles de l'icône de la zone de notification (`setToolTip` enveloppé, puis appelé) ;
// - la coupure du serveur : la session du desktop annule toute requête vers l'API, le serveur restant
//   en marche (son arrêt réel arrêterait aussi le Keycloak des Dev Services et ses clés de signature).
// - le refus du système : `Notification.isSupported` faux, ou événement `failed` émis à la place de
//   l'appel au système, comme le fait Windows quand ses réglages refusent la notification.
// Non observable par un test automatisé : l'affichage de la bulle par Windows, son refus réel dans
// les réglages du système (NOT-01-S2) et le clic réel sur la bulle.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { URL_API } from '../support/environnement'
import {
  COMPTE_POSTE, emettreAlertePoste, etatAlerte, preparerAlertesPoste, type AlerteDeTest,
} from '../support/donnees'
import { connecterCompte, lancerDesktop, masquerFenetre, type ApplicationDesktop } from '../support/electron'
import { expect, test } from '../support/fixtures'
import type { Page } from '@playwright/test'

/** Délai d'une lecture des alertes par l'IHM (30 s), avec sa marge. */
const CYCLE = 45_000
const TITRE_NOTIFICATION = 'Nouvelle alerte ARGOS'
const LECTURE_ALERTES = `${URL_API}/api/v1/alertes`

let desktop: ApplicationDesktop | undefined
let compte: string
let dossierDonnees: string

test.beforeEach(async () => {
  // Remis à zéro d'abord : un échec de préparation ne laisse pas l'application du test précédent.
  desktop = undefined
  const testInfo = test.info()
  testInfo.setTimeout(4 * 60_000)
  compte = await preparerAlertesPoste()
  dossierDonnees = testInfo.outputPath('donnees-desktop')
  desktop = await lancerDesktop({ dossierDonnees })
})

test.afterEach(async () => {
  await desktop?.fermer()
  desktop = undefined
})

/** Application lancée par le beforeEach du parcours en cours. */
function actif(): ApplicationDesktop {
  if (!desktop) throw new Error('client desktop non lancé')
  return desktop
}

/** Lectures réussies des alertes DESKTOP en attente par l'IHM (journal de la session). */
async function lecturesReussies(): Promise<number> {
  return (await actif().journal())
    .filter((requete) => requete.adresse === LECTURE_ALERTES && requete.methode === 'GET' && requete.statut === 200).length
}

/** Tentatives de lecture des alertes annulées par la coupure simulée (journal de la session). */
async function lecturesCoupees(): Promise<number> {
  return (await actif().journal())
    .filter((requete) => requete.adresse === LECTURE_ALERTES && requete.methode === 'GET' && requete.coupee).length
}

async function derniereInfobulle(): Promise<string | undefined> {
  return (await actif().infobulles()).at(-1)
}

/** Connexion de lina depuis une vue protégée qui ne lit pas les alertes, puis première lecture faite. */
async function connecter(navigateur: Page): Promise<void> {
  await actif().fenetre.evaluate(() => { window.location.hash = '#/recherche' })
  await connecterCompte(actif(), navigateur, COMPTE_POSTE)
  await expect(actif().fenetre.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  await expect.poll(lecturesReussies, { timeout: CYCLE }).toBeGreaterThan(0)
  await expect.poll(derniereInfobulle).toBe('ARGOS — aucune alerte ouverte')
}

/** NOT-05 : la notification ne nomme ni le signal, ni un projet, ni un composant. */
function sansDetail(notification: { titre: string; corps: string }, alertes: AlerteDeTest[]): void {
  const texte = `${notification.titre} ${notification.corps}`
  for (const alerte of alertes) {
    expect(texte).not.toContain(alerte.titre)
    for (const projet of alerte.projets) expect(texte).not.toContain(projet)
  }
  expect(texte).not.toMatch(/e2e-|E2E/)
}

async function journalSansHoteExterne(): Promise<void> {
  const journal = await actif().journal()
  expect(journal.filter((requete) => requete.refusee).map((requete) => requete.adresse), 'hôtes hors de la boucle locale').toEqual([])
}

test('NOT-01-S1, NOT-05-S1, NOT-03-S1, NOT-02-S1 : fenêtre masquée, notification, compteur, clic', async ({ page: navigateur }) => {
  const { fenetre } = actif()
  await connecter(navigateur)
  // Fenêtre fermée par l'utilisateur : masquée, l'application reste dans la zone de notification.
  await masquerFenetre(actif())

  // NOT-03-S1 GIVEN deux alertes ouvertes.
  const alerte1 = emettreAlertePoste(compte, '01')
  const alerte2 = emettreAlertePoste(compte, '02')
  await expect.poll(derniereInfobulle, { timeout: CYCLE }).toBe('ARGOS — 2 alertes ouvertes')
  const deux = await actif().notifications()
  expect(deux.map((notification) => notification.titre)).toEqual([TITRE_NOTIFICATION, TITRE_NOTIFICATION])
  expect(deux.map((notification) => notification.corps).sort()).toEqual(['Gravité moyenne · 2 projets', 'Gravité élevée · 1 projet'].sort())

  // NOT-01-S1 : une alerte critique est émise, fenêtre toujours masquée : une notification système
  // est créée et affichée par le processus principal.
  expect((await actif().etatFenetre()).visible).toBe(false)
  const alerte3 = emettreAlertePoste(compte, '03')
  await expect.poll(async () => (await actif().notifications()).length, { timeout: CYCLE }).toBe(3)
  expect((await actif().etatFenetre()).visible).toBe(false)
  const notifications = await actif().notifications()
  // NOT-05-S1 : titre fixe, gravité et « 3 projets », sans titre de signal ni nom de projet.
  expect(notifications[2]).toEqual({ titre: TITRE_NOTIFICATION, corps: 'Gravité critique · 3 projets' })
  for (const notification of notifications) sansDetail(notification, [alerte1, alerte2, alerte3])

  // NOT-03-S1 THEN la zone de notification indique trois alertes ouvertes.
  await expect.poll(derniereInfobulle).toBe('ARGOS — 3 alertes ouvertes')
  // Remise confirmée au serveur pour chacune.
  for (const alerte of [alerte1, alerte2, alerte3]) await expect.poll(() => etatAlerte(alerte.id)).toBe('ENVOYEE')

  // NOT-02-S1 : clic sur la notification de l'alerte critique : la fenêtre revient au premier plan
  // sur cette alerte, affichée en détail.
  await actif().cliquerNotification(2)
  await expect.poll(async () => {
    const etat = await actif().etatFenetre()
    return etat.visible && !etat.reduite && etat.focus
  }).toBe(true)
  await expect(fenetre).toHaveURL(new RegExp(`^app://argos/index\\.html#/alertes/${alerte3.id}$`))
  await expect(fenetre.getByRole('heading', { level: 1, name: alerte3.titre })).toBeVisible()
  await expect(fenetre.getByText('Gravité critique', { exact: false })).toBeVisible()
  for (const projet of alerte3.projets) await expect(fenetre.getByText(`${projet} (EXACT)`)).toBeVisible()
  // Alerte consultée : elle n'est plus ouverte.
  await expect.poll(derniereInfobulle).toBe('ARGOS — 2 alertes ouvertes')
  await expect(fenetre.getByRole('link', { name: 'Alertes (2)' })).toBeVisible()
  await fenetre.screenshot({ path: 'resultats/desktop-notification-clic.jpg', type: 'jpeg', quality: 60 })

  await journalSansHoteExterne()
})

test('NOT-04-S1 (partie poste) : notifications du poste coupées, alerte comptée et visible', async ({ page: navigateur }) => {
  const { fenetre } = actif()
  await connecter(navigateur)

  // Réglage « Notifications du poste » coupé depuis la vue « Poste » de l'IHM, gardé sur le poste.
  await fenetre.getByRole('link', { name: 'Poste' }).click()
  const reglage = fenetre.getByRole('checkbox', { name: 'Notifications du poste' })
  await expect(reglage).toBeChecked()
  await reglage.uncheck()
  await expect(reglage).not.toBeChecked()
  await expect.poll(() => JSON.parse(readFileSync(join(dossierDonnees, 'reglages-poste.json'), 'utf8')))
    .toEqual({ notificationsPoste: false, demarrageSession: false })

  await masquerFenetre(actif())
  const alerte4 = emettreAlertePoste(compte, '04')
  // L'alerte est lue et comptée dans la zone de notification ; aucune notification n'est créée.
  await expect.poll(derniereInfobulle, { timeout: CYCLE }).toBe('ARGOS — 1 alerte ouverte')
  expect(await actif().notifications()).toEqual([])
  await expect.poll(() => etatAlerte(alerte4.id)).toBe('ENVOYEE')

  // Clic sur l'icône de la zone de notification : la fenêtre revient ; l'alerte est visible.
  await actif().cliquerZoneNotification()
  await expect.poll(async () => (await actif().etatFenetre()).visible).toBe(true)
  await fenetre.getByRole('link', { name: 'Alertes (1)' }).click()
  await expect(fenetre.getByRole('heading', { name: 'Alertes du poste' })).toBeVisible()
  await expect(fenetre.getByRole('link', { name: alerte4.titre })).toBeVisible()
  await fenetre.screenshot({ path: 'resultats/desktop-notifications-coupees.jpg', type: 'jpeg', quality: 60 })
  expect(await actif().notifications()).toEqual([])

  await journalSansHoteExterne()
})

test('NOT-01-S2 (partie application) : notifications refusées par le système, alertes comptées et visibles',
  async ({ page: navigateur }) => {
    const { fenetre } = actif()
    await connecter(navigateur)
    await masquerFenetre(actif())

    // Système sans notification disponible : Notification.isSupported renvoie faux (simulé).
    await actif().refuserNotificationsSysteme('non-supportees')
    const alerte1 = emettreAlertePoste(compte, '01')
    await expect.poll(derniereInfobulle, { timeout: CYCLE }).toBe('ARGOS — 1 alerte ouverte')
    await expect.poll(() => etatAlerte(alerte1.id)).toBe('ENVOYEE')
    expect(await actif().notifications()).toEqual([])
    expect(await actif().notificationsRefusees()).toEqual([])

    // Réglages de Windows : la notification est demandée, le système la refuse (événement `failed` simulé).
    await actif().refuserNotificationsSysteme('reglages-windows')
    const alerte3 = emettreAlertePoste(compte, '03')
    await expect.poll(derniereInfobulle, { timeout: CYCLE }).toBe('ARGOS — 2 alertes ouvertes')
    await expect.poll(() => etatAlerte(alerte3.id)).toBe('ENVOYEE')
    expect(await actif().notifications()).toEqual([])
    expect(await actif().notificationsRefusees())
      .toEqual([{ titre: TITRE_NOTIFICATION, corps: 'Gravité critique · 3 projets' }])
    expect((await actif().etatFenetre()).visible).toBe(false)

    // Les deux alertes sont visibles dans l'application.
    await actif().cliquerZoneNotification()
    await expect.poll(async () => (await actif().etatFenetre()).visible).toBe(true)
    await fenetre.getByRole('link', { name: 'Alertes (2)' }).click()
    await expect(fenetre.getByRole('heading', { name: 'Alertes du poste' })).toBeVisible()
    for (const alerte of [alerte1, alerte3]) await expect(fenetre.getByRole('link', { name: alerte.titre })).toBeVisible()
    await fenetre.screenshot({ path: 'resultats/desktop-notifications-refusees.jpg', type: 'jpeg', quality: 60 })
    expect(await actif().notifications()).toEqual([])

    await journalSansHoteExterne()
  })

test('NOT-06-S1 : coupure du serveur, deux alertes émises pendant la coupure, reprise sans perte ni doublon',
  async ({ page: navigateur }) => {
    const { fenetre } = actif()
    await connecter(navigateur)
    const hoteApi = new URL(URL_API).host

    // Coupure : le serveur est injoignable depuis le desktop ; la lecture suivante échoue.
    await actif().couper(hoteApi)
    await expect(fenetre.getByText('Hors ligne : serveur ARGOS injoignable.', { exact: false })).toBeVisible({ timeout: CYCLE })
    await fenetre.screenshot({ path: 'resultats/desktop-reprise-hors-ligne.jpg', type: 'jpeg', quality: 60 })

    // Deux alertes immédiates émises pendant la coupure. Le desktop tente à nouveau de les lire et
    // cette tentative est coupée : l'état est relu après elle, les alertes restent en attente côté serveur.
    const alerte5 = emettreAlertePoste(compte, '05')
    const alerte6 = emettreAlertePoste(compte, '06')
    const tentativesAvant = await lecturesCoupees()
    await expect.poll(lecturesCoupees, { timeout: CYCLE }).toBeGreaterThan(tentativesAvant)
    expect(await etatAlerte(alerte5.id)).toBe('EN_ATTENTE')
    expect(await etatAlerte(alerte6.id)).toBe('EN_ATTENTE')
    expect(await actif().notifications()).toEqual([])

    // Retour de la connexion : le desktop reprend à sa tentative suivante (l'attente croissante entre
    // deux tentatives est vérifiée sur horloge simulée par argos-ui/tests/reprise-poste.test.mjs).
    await actif().retablir()
    await expect(fenetre.getByText(/Connexion rétablie : 2 alertes émises pendant la coupure\./)).toBeVisible({ timeout: CYCLE })
    await expect(fenetre.getByText('Hors ligne', { exact: false })).toHaveCount(0)
    await fenetre.screenshot({ path: 'resultats/desktop-reprise-retablie.jpg', type: 'jpeg', quality: 60 })

    // Exactement deux notifications, une par alerte, et les deux alertes ENVOYEE côté serveur.
    const notifications = await actif().notifications()
    expect(notifications).toEqual([
      { titre: TITRE_NOTIFICATION, corps: 'Gravité critique · 1 projet' },
      { titre: TITRE_NOTIFICATION, corps: 'Gravité critique · 1 projet' },
    ])
    for (const notification of notifications) sansDetail(notification, [alerte5, alerte6])
    await expect.poll(() => etatAlerte(alerte5.id)).toBe('ENVOYEE')
    await expect.poll(() => etatAlerte(alerte6.id)).toBe('ENVOYEE')
    await expect.poll(derniereInfobulle).toBe('ARGOS — 2 alertes ouvertes')

    // Aucun doublon à la lecture suivante.
    const lectures = await lecturesReussies()
    await expect.poll(lecturesReussies, { timeout: CYCLE }).toBeGreaterThan(lectures)
    expect(await actif().notifications()).toHaveLength(2)
    expect(await derniereInfobulle()).toBe('ARGOS — 2 alertes ouvertes')

    // La coupure a bien porté sur les requêtes du desktop vers l'API, et sur elles seules.
    const journal = await actif().journal()
    const coupees = journal.filter((requete) => requete.coupee)
    expect(coupees.length).toBeGreaterThan(0)
    for (const requete of coupees) expect(requete.hote).toBe(hoteApi)
    await journalSansHoteExterne()
  })
