'use strict';
// Configuration de sécurité de la fenêtre principale (fenetre.js, ADR-0022, AUD-TST-10).
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  optionsFenetre, permissionAccordee, isExternalWebUrl, navigationAutorisee,
  gestionnaireNavigation, gestionnaireOuverture, gestionnairePermissions,
  FENETRE_LARGEUR, FENETRE_HAUTEUR, FENETRE_LARGEUR_MIN, FENETRE_HAUTEUR_MIN,
} = require('../src/fenetre');

const ORIGINE = 'app://argos';
const DEV_URL = 'http://localhost:5173';
const CHEMINS = { preload: '/opt/argos/src/preload.js', icone: '/opt/argos/assets/icon.png' };
const CONTEXTE_EMBALLE = { dev: false, devUrl: DEV_URL, origine: ORIGINE };
const CONTEXTE_DEV = { dev: true, devUrl: DEV_URL, origine: ORIGINE };

/** Événement de navigation factice qui retient s'il a été empêché. */
function evenement() {
  return { empeche: false, preventDefault() { this.empeche = true; } };
}

/** Ouverture externe factice qui retient les URL reçues. */
function ouvertures() {
  const urls = [];
  return { urls, ouvrir: (url) => { urls.push(url); } };
}

test('la fenêtre isole la page : contexte isolé, sans Node, en bac à sable, avec le preload', () => {
  const { webPreferences } = optionsFenetre(CHEMINS);
  assert.deepEqual(webPreferences, {
    preload: CHEMINS.preload,
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: true,
    backgroundThrottling: false,
  });
});

test('la fenêtre s’ouvre masquée, à la taille prévue, avec l’icône et le titre ARGOS', () => {
  const options = optionsFenetre(CHEMINS);
  assert.equal(options.show, false);
  assert.equal(options.title, 'ARGOS');
  assert.equal(options.icon, CHEMINS.icone);
  assert.equal(options.autoHideMenuBar, true);
  assert.deepEqual(
    [options.width, options.height, options.minWidth, options.minHeight],
    [FENETRE_LARGEUR, FENETRE_HAUTEUR, FENETRE_LARGEUR_MIN, FENETRE_HAUTEUR_MIN],
  );
  assert.ok(options.minWidth <= options.width && options.minHeight <= options.height);
});

test('seule la permission notifications est accordée', () => {
  assert.equal(permissionAccordee('notifications'), true);
  for (const permission of ['media', 'geolocation', 'clipboard-read', 'openExternal', 'fullscreen', '', undefined]) {
    assert.equal(permissionAccordee(permission), false, `permission ${permission}`);
  }
});

test('le gestionnaire de permissions répond par le rappel d’Electron', () => {
  const reponses = [];
  gestionnairePermissions({}, 'notifications', (accordee) => reponses.push(accordee));
  gestionnairePermissions({}, 'media', (accordee) => reponses.push(accordee));
  assert.deepEqual(reponses, [true, false]);
});

test('isExternalWebUrl ne reconnaît que les URL http(s) bien formées', () => {
  assert.equal(isExternalWebUrl('https://argos.exemple.fr/aide'), true);
  assert.equal(isExternalWebUrl('http://localhost:8080'), true);
  for (const url of ['file:///etc/passwd', 'app://argos/index.html', 'javascript:alert(1)', 'mailto:a@b.fr', 'pas une url', '']) {
    assert.equal(isExternalWebUrl(url), false, url);
  }
});

test('hors mode développement, seule l’origine de l’IHM emballée est une navigation autorisée', () => {
  assert.equal(navigationAutorisee('app://argos/index.html', CONTEXTE_EMBALLE), true);
  assert.equal(navigationAutorisee('app://argos/projets/42', CONTEXTE_EMBALLE), true);
  assert.equal(navigationAutorisee('app://argos.malveillant/index.html', CONTEXTE_EMBALLE), false);
  assert.equal(navigationAutorisee('app://argos', CONTEXTE_EMBALLE), false);
  assert.equal(navigationAutorisee(`${DEV_URL}/`, CONTEXTE_EMBALLE), false);
  assert.equal(navigationAutorisee('https://exemple.fr', CONTEXTE_EMBALLE), false);
});

test('en mode développement, seule l’URL du serveur de développement est une navigation autorisée', () => {
  assert.equal(navigationAutorisee(`${DEV_URL}/projets`, CONTEXTE_DEV), true);
  assert.equal(navigationAutorisee('app://argos/index.html', CONTEXTE_DEV), false);
  assert.equal(navigationAutorisee('https://exemple.fr', CONTEXTE_DEV), false);
});

test('une navigation vers l’IHM n’est ni empêchée ni ouverte à l’extérieur', () => {
  const externes = ouvertures();
  const navigation = evenement();
  gestionnaireNavigation({ ...CONTEXTE_EMBALLE, ouvrirExterne: externes.ouvrir })(navigation, 'app://argos/index.html');
  assert.equal(navigation.empeche, false);
  assert.deepEqual(externes.urls, []);
});

test('une navigation vers un site web est empêchée et ouverte dans le navigateur du système', () => {
  const externes = ouvertures();
  const navigation = evenement();
  gestionnaireNavigation({ ...CONTEXTE_EMBALLE, ouvrirExterne: externes.ouvrir })(navigation, 'https://exemple.fr/doc');
  assert.equal(navigation.empeche, true);
  assert.deepEqual(externes.urls, ['https://exemple.fr/doc']);
});

test('une navigation vers un schéma non web est empêchée sans rien ouvrir', () => {
  const externes = ouvertures();
  const navigation = evenement();
  gestionnaireNavigation({ ...CONTEXTE_EMBALLE, ouvrirExterne: externes.ouvrir })(navigation, 'file:///etc/passwd');
  assert.equal(navigation.empeche, true);
  assert.deepEqual(externes.urls, []);
});

test('toute ouverture de fenêtre est refusée ; un lien web part dans le navigateur du système', () => {
  const externes = ouvertures();
  const ouvrir = gestionnaireOuverture(externes.ouvrir);
  assert.deepEqual(ouvrir({ url: 'https://exemple.fr' }), { action: 'deny' });
  assert.deepEqual(ouvrir({ url: 'file:///etc/passwd' }), { action: 'deny' });
  assert.deepEqual(ouvrir({ url: 'app://argos/autre.html' }), { action: 'deny' });
  assert.deepEqual(externes.urls, ['https://exemple.fr']);
});
