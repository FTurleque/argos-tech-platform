'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { resolveUiFile, contentSecurityPolicy, mimeType } = require('../src/ui-protocol');

const RACINE_UI = path.resolve('/opt/argos/ui');

test('la racine sert index.html', () => {
  assert.equal(resolveUiFile('app://argos/', RACINE_UI), path.join(RACINE_UI, 'index.html'));
});

test('un fichier du build est servi', () => {
  assert.equal(resolveUiFile('app://argos/assets/index.js', RACINE_UI), path.join(RACINE_UI, 'assets', 'index.js'));
});

test('impossible de sortir du dossier de l\u2019IHM', () => {
  const inside = (file) => file === null || file === RACINE_UI || file.startsWith(RACINE_UI + path.sep);
  for (const url of [
    'app://argos/../../etc/passwd',
    'app://argos/%2e%2e/%2e%2e/etc/passwd',
    'app://argos/..%2f..%2fsecret',
    'app://argos/assets/..%5c..%5csecret',
  ]) {
    assert.ok(inside(resolveUiFile(url, RACINE_UI)), url);
  }
  assert.equal(resolveUiFile('app://argos/..%2f..%2fsecret', RACINE_UI), null);
});

test('un autre hôte ou protocole est refusé', () => {
  assert.equal(resolveUiFile('app://autre/index.html', RACINE_UI), null);
  assert.equal(resolveUiFile('file:///opt/argos/ui/index.html', RACINE_UI), null);
  assert.equal(resolveUiFile('pas une url', RACINE_UI), null);
});

test('la CSP n’autorise que le serveur ARGOS configuré', () => {
  const csp = contentSecurityPolicy('https://argos.interne/api');
  assert.match(csp, /connect-src 'self' https:\/\/argos\.interne;/);
  assert.match(csp, /object-src 'none'/);
});

test('ACC-10-S4 : la CSP ajoute seulement l’origine du fournisseur d’identité configuré', () => {
  const csp = contentSecurityPolicy('http://localhost:8080', 'http://localhost:8180/realms/argos');
  assert.match(csp, /connect-src 'self' http:\/\/localhost:8080 http:\/\/localhost:8180;/);
  assert.match(csp, /script-src 'self';/);
  assert.match(csp, /default-src 'self';/);
  assert.doesNotMatch(csp, /\*|unsafe-eval|realms/);
  // Même origine : pas de doublon.
  const meme = contentSecurityPolicy('https://argos.example', 'https://argos.example/auth/realms/argos');
  assert.match(meme, /connect-src 'self' https:\/\/argos\.example;/);
});

test('types MIME', () => {
  assert.equal(mimeType('a/b.js'), 'text/javascript; charset=utf-8');
  assert.equal(mimeType('x.unknown'), 'application/octet-stream');
});
