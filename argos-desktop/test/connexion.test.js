'use strict';
// Connexion OIDC du client desktop (connexion.js, changement connecter-ihm-oidc : ACC-10).
// Un vrai serveur node:http écoute sur 127.0.0.1 ; le « navigateur du système » est une doublure
// qui appelle l'adresse de redirection comme le ferait le fournisseur. Aucune requête vers Internet.
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { creerConnexion, demandeAcceptable, urlAutorisationSure, CHEMIN_RETOUR } = require('../src/connexion');

const EMETTEUR = 'http://localhost:8180/realms/argos';

function urlAutorisation(redirectUri, modifs = {}) {
  const url = new URL(`${EMETTEUR}/protocol/openid-connect/auth`);
  const p = {
    response_type: 'code', client_id: 'argos-ui', redirect_uri: redirectUri, scope: 'openid',
    state: 'etat-1', code_challenge: 'defi-1', code_challenge_method: 'S256', ...modifs,
  };
  for (const [k, v] of Object.entries(p)) if (v !== undefined) url.searchParams.set(k, v);
  return url.toString();
}

/** GET local, renvoie { status, headers, corps }. */
function lire(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let corps = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { corps += c; });
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, corps }));
    }).on('error', reject);
  });
}

function installer(options = {}) {
  const ouvertes = [];
  let premierPlan = 0;
  const connexion = creerConnexion({
    http,
    emetteur: EMETTEUR,
    ouvrirNavigateur: (url) => { ouvertes.push(url); return options.ouvrir?.(url); },
    afficherFenetre: () => { premierPlan += 1; },
    ...options.deps,
  });
  return { connexion, ouvertes, premierPlan: () => premierPlan };
}

test('ACC-10-S1 : écoute sur 127.0.0.1, port éphémère, navigateur du système ouvert sur l’émetteur', async () => {
  const { connexion, ouvertes, premierPlan } = installer({
    ouvrir: (url) => {
      const retour = new URL(url).searchParams.get('redirect_uri');
      setImmediate(() => { void lire(`${retour}?state=etat-1&session_state=x&code=code-1`); });
    },
  });
  const redirectUri = await connexion.preparer();
  assert.match(redirectUri, new RegExp(`^http://127\\.0\\.0\\.1:\\d+${CHEMIN_RETOUR}$`));
  assert.notEqual(new URL(redirectUri).port, '');
  const recherche = await connexion.autoriser(urlAutorisation(redirectUri));
  assert.equal(recherche, '?state=etat-1&session_state=x&code=code-1');
  assert.equal(ouvertes.length, 1);
  assert.equal(premierPlan(), 1);
  // ACC-10-S2 : l'écoute est fermée après un seul retour.
  assert.equal(connexion.enAttente(), false);
  await assert.rejects(lire(redirectUri));
});

test('ACC-10-S2 : la page de retour est statique et ne reprend pas les paramètres reçus', async () => {
  let page = null;
  const { connexion } = installer({
    ouvrir: (url) => {
      const retour = new URL(url).searchParams.get('redirect_uri');
      setImmediate(async () => { page = await lire(`${retour}?state=etat-1&code=<script>alert(1)</script>`); });
    },
  });
  const redirectUri = await connexion.preparer();
  await connexion.autoriser(urlAutorisation(redirectUri));
  while (page === null) await new Promise((r) => setImmediate(r));
  assert.equal(page.status, 200);
  assert.match(page.corps, /^<!DOCTYPE html>\n<html lang="fr">/);
  assert.doesNotMatch(page.corps, /script|code|etat-1/);
  assert.equal(page.headers['content-security-policy'], "default-src 'none'");
  assert.equal(page.headers['cache-control'], 'no-store');
  assert.equal(page.headers['referrer-policy'], 'no-referrer');
});

test('une autre adresse locale répond 404 et ne termine pas la connexion', async () => {
  const { connexion } = installer();
  const redirectUri = await connexion.preparer();
  const attente = connexion.autoriser(urlAutorisation(redirectUri));
  const autre = await lire(redirectUri.replace(CHEMIN_RETOUR, '/autre?code=x&state=etat-1'));
  assert.equal(autre.status, 404);
  assert.equal(connexion.enAttente(), true);
  await lire(`${redirectUri}?code=c&state=etat-1`);
  assert.equal(await attente, '?code=c&state=etat-1');
});

test('ACC-10-S3 : une URL d’autorisation hors émetteur ou non conforme n’ouvre aucun navigateur', async () => {
  const { connexion, ouvertes } = installer();
  const redirectUri = await connexion.preparer();
  for (const url of [
    urlAutorisation(redirectUri).replace('localhost:8180', 'malveillant.example'),
    urlAutorisation(redirectUri).replace('/realms/argos/', '/realms/autre/'),
    urlAutorisation(redirectUri, { code_challenge_method: 'plain' }),
    urlAutorisation(redirectUri, { code_challenge: undefined }),
    urlAutorisation(redirectUri, { state: undefined }),
    urlAutorisation(redirectUri, { response_type: 'token' }),
    urlAutorisation('http://127.0.0.1:1/argos/retour'),
    'file:///etc/passwd',
    'pas une url',
  ]) {
    assert.equal(await connexion.autoriser(url), null, url);
  }
  assert.deepEqual(ouvertes, []);
  connexion.fermer();
});

test('sans écoute préparée, ou pendant une autorisation en cours, une nouvelle demande est refusée', async () => {
  const { connexion, ouvertes } = installer();
  assert.equal(await connexion.autoriser(urlAutorisation('http://127.0.0.1:1/argos/retour')), null);
  const redirectUri = await connexion.preparer();
  const premiere = connexion.autoriser(urlAutorisation(redirectUri));
  assert.equal(await connexion.autoriser(urlAutorisation(redirectUri)), null);
  assert.equal(ouvertes.length, 1);
  // Une nouvelle préparation abandonne la précédente (résolue à null).
  const seconde = await connexion.preparer();
  assert.equal(await premiere, null);
  assert.notEqual(seconde, null);
  connexion.fermer();
});

test('délai dépassé ou navigateur impossible à ouvrir : null et écoute fermée', async () => {
  const minuteries = [];
  const { connexion } = installer({
    deps: { planifier: (fn) => { minuteries.push(fn); return minuteries.length; }, annuler: () => {} },
  });
  const redirectUri = await connexion.preparer();
  const attente = connexion.autoriser(urlAutorisation(redirectUri));
  minuteries[0]();
  assert.equal(await attente, null);
  assert.equal(connexion.enAttente(), false);

  const echec = installer({ ouvrir: () => Promise.reject(new Error('aucun navigateur')) });
  const r = await echec.connexion.preparer();
  assert.equal(await echec.connexion.autoriser(urlAutorisation(r)), null);
  assert.equal(echec.connexion.enAttente(), false);
});

test('validation pure de la demande d’autorisation', () => {
  const redirectUri = 'http://127.0.0.1:50000/argos/retour';
  assert.equal(demandeAcceptable(urlAutorisation(redirectUri), { emetteur: EMETTEUR, redirectUri }), true);
  assert.equal(demandeAcceptable(urlAutorisation(redirectUri), { emetteur: `${EMETTEUR}/`, redirectUri }), true);
  assert.equal(demandeAcceptable('x'.repeat(9000), { emetteur: EMETTEUR, redirectUri }), false);
  assert.equal(demandeAcceptable(urlAutorisation(redirectUri).replace('http://', 'http://u:p@'), { emetteur: EMETTEUR, redirectUri }), false);
  assert.equal(demandeAcceptable(42, { emetteur: EMETTEUR, redirectUri }), false);
});

test('l’URL ouverte est reconstruite depuis l’émetteur configuré, avec les seuls paramètres attendus', () => {
  const redirectUri = 'http://127.0.0.1:50000/argos/retour';
  const contexte = { emetteur: EMETTEUR, redirectUri };
  assert.equal(urlAutorisationSure(urlAutorisation(redirectUri), contexte), urlAutorisation(redirectUri));
  const enrichie = `${urlAutorisation(redirectUri)}&kc_idp_hint=autre#fragment`;
  assert.equal(urlAutorisationSure(enrichie, contexte), urlAutorisation(redirectUri));
  const autreChemin = urlAutorisation(redirectUri).replace('/protocol/openid-connect/auth', '/protocol/openid-connect/logout');
  assert.equal(urlAutorisationSure(autreChemin, contexte), null);
});
