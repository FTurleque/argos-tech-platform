// Tests de scripts/verifier-licences-npm.mjs (AUD-DEP-19).
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { avis, dependancesLivrees, EntreeInvalide, licenceAdmise, resoudre, texteLicence } from './verifier-licences-npm.mjs';

const VERROU = {
  packages: {
    '': { dependencies: { vue: '^3.5.0' }, devDependencies: { vite: '^8.0.0' } },
    'node_modules/vue': { version: '3.5.46', license: 'MIT', dependencies: { '@vue/shared': '3.5.46', outil: '^1' },
      optionalDependencies: { 'binaire-autre-plateforme': '1.0.0' } },
    'node_modules/@vue/shared': { version: '3.5.46', license: 'MIT' },
    'node_modules/vue/node_modules/outil': { version: '1.2.0', license: '(MIT OR GPL-3.0)' },
    'node_modules/outil': { version: '2.0.0', license: 'GPL-3.0' },
    'node_modules/vite': { version: '8.3.8', license: 'MIT', dev: true },
  },
};

test('licenceAdmise : identifiants admis, choix OR, conjonction AND, licence absente', () => {
  assert.ok(licenceAdmise('MIT'));
  assert.ok(licenceAdmise('OFL-1.1'));
  assert.ok(licenceAdmise('(MIT OR GPL-3.0)'));
  assert.ok(!licenceAdmise('MIT AND GPL-3.0'));
  assert.ok(!licenceAdmise('GPL-3.0'));
  assert.ok(!licenceAdmise(undefined));
});

test('resoudre : copie imbriquée avant la copie de premier niveau, comme le chargeur de Node', () => {
  assert.equal(resoudre(VERROU.packages, 'node_modules/vue', 'outil'), 'node_modules/vue/node_modules/outil');
  assert.equal(resoudre(VERROU.packages, 'node_modules/vue', '@vue/shared'), 'node_modules/@vue/shared');
  assert.equal(resoudre(VERROU.packages, '', 'absent'), undefined);
});

test('dependancesLivrees : dépendances atteintes depuis `dependencies`, sans les outils de développement', () => {
  assert.deepEqual(dependancesLivrees(VERROU).map(({ nom, version }) => `${nom}@${version}`),
    ['@vue/shared@3.5.46', 'outil@1.2.0', 'vue@3.5.46']);
});

test('dependancesLivrees : une dépendance obligatoire absente du verrou est une entrée invalide', () => {
  const incomplet = { packages: { '': { dependencies: { absent: '1' } } } };
  assert.throws(() => dependancesLivrees(incomplet), EntreeInvalide);
});

test('texteLicence : fichier du paquet, sinon texte de référence et auteur déclaré, sinon chaîne vide', () => {
  const dossier = mkdtempSync(join(tmpdir(), 'licences-npm-'));
  try {
    const avecTexte = join(dossier, 'avec');
    const sansTexte = join(dossier, 'sans');
    const references = join(dossier, 'references');
    for (const sousDossier of [avecTexte, sansTexte, references]) mkdirSync(sousDossier);
    writeFileSync(join(avecTexte, 'LICENSE.md'), 'Copyright Evan You\n');
    writeFileSync(join(sansTexte, 'package.json'), JSON.stringify({ author: { name: 'Autrice' } }));
    writeFileSync(join(references, 'MIT.txt'), 'Texte MIT\n');
    assert.equal(texteLicence(avecTexte, 'MIT', references), 'Copyright Evan You');
    assert.equal(texteLicence(sansTexte, 'MIT', references),
      'Le paquet ne fournit pas de texte de licence ; texte de référence MIT, auteur déclaré : Autrice.\n\nTexte MIT');
    assert.equal(texteLicence(sansTexte, 'ISC', references), '');
  } finally {
    rmSync(dossier, { recursive: true, force: true });
  }
});

test('avis : un bloc par dépendance, dédoublonné sur le nom et la version', () => {
  const texte = avis([
    { nom: 'vue', version: '3.5.46', licence: 'MIT', texte: 'Texte de vue' },
    { nom: 'vue', version: '3.5.46', licence: 'MIT', texte: 'Texte de vue' },
  ]);
  assert.equal(texte.match(/^=== /gm).length, 1);
  assert.match(texte, /=== vue 3\.5\.46 — MIT\n\nTexte de vue/);
});
