'use strict';
// Processus principal (main.js, ADR-0022, AUD-TST-10) : le fichier livré est chargé avec un
// Electron factice qui enregistre la configuration de sécurité posée au démarrage.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const systemeExploitation = require('node:os');
const systemeFichiers = require('node:fs');
const { contentSecurityPolicy, SCHEME, ORIGIN } = require('../src/ui-protocol');
const { API_URL_PAR_DEFAUT, OIDC_URL_PAR_DEFAUT } = require('../src/reglages-emballes');
const { chargerMain } = require('./electron-principal-factice');

const EMBALLES = { apiUrl: 'https://argos.exemple.fr', oidcUrl: 'https://auth.exemple.fr/realms/argos' };
const ENVIRONNEMENT = { ARGOS_API_URL: 'http://nas:8080', ARGOS_OIDC_URL: 'http://nas:8180/realms/argos' };
const DEV_URL = 'http://localhost:5173';
const STATUT_OK = 200;
const STATUT_INTROUVABLE = 404;
const CONTENU_INDEX = '<!doctype html><title>ARGOS</title>';

/** Appelle le gestionnaire de permissions retenu et renvoie la réponse transmise au rappel. */
function repondre(journal, permission) {
  let reponse;
  journal.gestionnairePermissions({}, permission, (accordee) => { reponse = accordee; });
  return reponse;
}

/** Simule une navigation de la fenêtre principale ; renvoie vrai si elle a été empêchée. */
function naviguer(fenetre, url) {
  let empechee = false;
  fenetre.webContents.ecouteurs['will-navigate']({ preventDefault: () => { empechee = true; } }, url);
  return empechee;
}

/** Dossier de ressources d'une application empaquetée contenant `ui/index.html`. */
function creerRessources(contexte) {
  const dossier = systemeFichiers.mkdtempSync(path.join(systemeExploitation.tmpdir(), 'argos-desktop-ressources-'));
  systemeFichiers.mkdirSync(path.join(dossier, 'ui'));
  systemeFichiers.writeFileSync(path.join(dossier, 'ui', 'index.html'), CONTENU_INDEX, 'utf8');
  contexte.after(() => systemeFichiers.rmSync(dossier, { recursive: true, force: true }));
  return dossier;
}

test('la fenêtre principale est créée avec les options de sécurité et charge l’IHM emballée', async () => {
  const journal = await chargerMain();
  assert.equal(journal.fenetres.length, 1);
  const [fenetre] = journal.fenetres;
  const { webPreferences } = fenetre.options;
  assert.equal(webPreferences.contextIsolation, true);
  assert.equal(webPreferences.nodeIntegration, false);
  assert.equal(webPreferences.sandbox, true);
  assert.equal(webPreferences.backgroundThrottling, false);
  assert.equal(webPreferences.preload, path.join(__dirname, '..', 'src', 'preload.js'));
  assert.equal(fenetre.options.show, false);
  assert.equal(fenetre.urlChargee, `${ORIGIN}/index.html`);
});

test('le schéma de l’IHM est déclaré privilégié et servi avec la CSP par défaut', async () => {
  const journal = await chargerMain();
  assert.deepEqual(journal.schemasPrivilegies.map((schema) => schema.scheme), [SCHEME]);
  assert.ok(journal.protocoles.has(SCHEME));
});

test('la CSP servie suit les réglages emballés du package.json', async (contexte) => {
  const dossierRessources = creerRessources(contexte);
  const journal = await chargerMain({
    emballes: EMBALLES,
    empaquetee: true,
    dossierRessources,
    lireFichier: () => ({ body: CONTENU_INDEX }),
  });
  const reponse = await journal.protocoles.get(SCHEME)({ url: `${ORIGIN}/index.html` });
  assert.equal(reponse.status, STATUT_OK);
  assert.equal(reponse.headers.get('Content-Security-Policy'), contentSecurityPolicy(EMBALLES.apiUrl, EMBALLES.oidcUrl));
  assert.match(reponse.headers.get('Content-Type'), /^text\/html/);
  assert.equal(await reponse.text(), CONTENU_INDEX);
});

test('la variable d’environnement l’emporte sur le réglage emballé dans la CSP', async (contexte) => {
  const dossierRessources = creerRessources(contexte);
  const journal = await chargerMain({
    environnement: ENVIRONNEMENT,
    emballes: EMBALLES,
    empaquetee: true,
    dossierRessources,
    lireFichier: () => ({ body: CONTENU_INDEX }),
  });
  const reponse = await journal.protocoles.get(SCHEME)({ url: `${ORIGIN}/index.html` });
  const attendue = contentSecurityPolicy(ENVIRONNEMENT.ARGOS_API_URL, ENVIRONNEMENT.ARGOS_OIDC_URL);
  assert.equal(reponse.headers.get('Content-Security-Policy'), attendue);
  assert.notEqual(attendue, contentSecurityPolicy(EMBALLES.apiUrl, EMBALLES.oidcUrl));
});

test('sans réglage, la CSP vise le serveur et l’émetteur par défaut ; un fichier absent répond 404', async (contexte) => {
  const dossierRessources = creerRessources(contexte);
  const journal = await chargerMain({ empaquetee: true, dossierRessources, lireFichier: () => ({ body: CONTENU_INDEX }) });
  const gestionnaire = journal.protocoles.get(SCHEME);
  const reponse = await gestionnaire({ url: `${ORIGIN}/index.html` });
  assert.equal(reponse.headers.get('Content-Security-Policy'), contentSecurityPolicy(API_URL_PAR_DEFAUT, OIDC_URL_PAR_DEFAUT));
  assert.equal((await gestionnaire({ url: `${ORIGIN}/absent.js` })).status, STATUT_INTROUVABLE);
  assert.equal((await gestionnaire({ url: `${ORIGIN}/../secret.txt` })).status, STATUT_INTROUVABLE);
});

test('le gestionnaire de permissions n’accorde que les notifications', async () => {
  const journal = await chargerMain();
  assert.equal(typeof journal.gestionnairePermissions, 'function');
  assert.equal(repondre(journal, 'notifications'), true);
  for (const permission of ['media', 'geolocation', 'clipboard-read', 'openExternal']) {
    assert.equal(repondre(journal, permission), false, permission);
  }
});

test('toute ouverture de fenêtre est refusée ; un lien web part dans le navigateur du système', async () => {
  const journal = await chargerMain();
  const { gestionnaireOuverture } = journal.fenetres[0].webContents;
  assert.deepEqual(gestionnaireOuverture({ url: 'https://exemple.fr/doc' }), { action: 'deny' });
  assert.deepEqual(gestionnaireOuverture({ url: 'file:///etc/passwd' }), { action: 'deny' });
  assert.deepEqual(journal.ouverturesExternes, ['https://exemple.fr/doc']);
});

test('une navigation hors de l’origine de l’IHM est empêchée', async () => {
  const journal = await chargerMain();
  const [fenetre] = journal.fenetres;
  assert.equal(naviguer(fenetre, `${ORIGIN}/projets`), false);
  assert.equal(naviguer(fenetre, 'https://exemple.fr'), true);
  assert.equal(naviguer(fenetre, `${DEV_URL}/`), true);
  assert.equal(naviguer(fenetre, 'file:///etc/passwd'), true);
  assert.deepEqual(journal.ouverturesExternes, ['https://exemple.fr', `${DEV_URL}/`]);
});

test('en mode développement, l’IHM vient du serveur de développement et le protocole interne n’est pas servi', async () => {
  const journal = await chargerMain({ dev: true });
  const [fenetre] = journal.fenetres;
  assert.equal(fenetre.urlChargee, DEV_URL);
  assert.equal(journal.protocoles.size, 0);
  assert.equal(naviguer(fenetre, `${DEV_URL}/projets`), false);
  assert.equal(naviguer(fenetre, `${ORIGIN}/index.html`), true);
});

test('AUD-DEP-15 : application empaquetée sous Windows ou Linux, la mise à jour est recherchée au démarrage', async (contexte) => {
  for (const plateforme of ['win32', 'linux']) {
    const dossierRessources = creerRessources(contexte);
    const journal = await chargerMain({ empaquetee: true, dossierRessources, plateforme });
    assert.equal(journal.miseAJour.recherches, 1, plateforme);
    assert.equal(journal.miseAJour.autoDownload, true);
    assert.equal(journal.miseAJour.autoInstallOnAppQuit, true);
  }
});

test('AUD-DEP-15 : application empaquetée sous macOS, electron-updater n’est pas chargé', async (contexte) => {
  const dossierRessources = creerRessources(contexte);
  const journal = await chargerMain({ empaquetee: true, dossierRessources, plateforme: 'darwin' });
  assert.equal(journal.miseAJour, undefined);
});

test('AUD-DEP-15 : lancée depuis les sources, l’application ne charge pas electron-updater', async () => {
  const journal = await chargerMain();
  assert.equal(journal.miseAJour, undefined);
});
