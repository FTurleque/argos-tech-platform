'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { resolveUiFile, contentSecurityPolicy, mimeType } = require('../src/ui-protocol');

const UI = path.resolve('/opt/argos/ui');

test('la racine sert index.html', () => {
  assert.equal(resolveUiFile('app://argos/', UI), path.join(UI, 'index.html'));
});

test('un fichier du build est servi', () => {
  assert.equal(resolveUiFile('app://argos/assets/index.js', UI), path.join(UI, 'assets', 'index.js'));
});

test('impossible de sortir du dossier de l\u2019IHM', () => {
  const inside = (file) => file === null || file === UI || file.startsWith(UI + path.sep);
  for (const url of [
    'app://argos/../../etc/passwd',
    'app://argos/%2e%2e/%2e%2e/etc/passwd',
    'app://argos/..%2f..%2fsecret',
    'app://argos/assets/..%5c..%5csecret',
  ]) {
    assert.ok(inside(resolveUiFile(url, UI)), url);
  }
  assert.equal(resolveUiFile('app://argos/..%2f..%2fsecret', UI), null);
});

test('un autre hôte ou protocole est refusé', () => {
  assert.equal(resolveUiFile('app://autre/index.html', UI), null);
  assert.equal(resolveUiFile('file:///opt/argos/ui/index.html', UI), null);
  assert.equal(resolveUiFile('pas une url', UI), null);
});

test('la CSP n’autorise que le serveur ARGOS configuré', () => {
  const csp = contentSecurityPolicy('https://argos.interne/api');
  assert.match(csp, /connect-src 'self' https:\/\/argos\.interne;/);
  assert.match(csp, /object-src 'none'/);
});

test('types MIME', () => {
  assert.equal(mimeType('a/b.js'), 'text/javascript; charset=utf-8');
  assert.equal(mimeType('x.unknown'), 'application/octet-stream');
});
