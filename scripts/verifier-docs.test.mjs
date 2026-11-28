// Tests du contrôle documentaire (scripts/verifier-docs.mjs) : chemins cités en code en ligne, README des sous-projets.
// Exécution : node --test scripts/*.test.mjs (aucune dépendance, aucun accès réseau).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { cheminsCites, readmeSousProjets } from './verifier-docs.mjs';

test('un chemin de fichier ou de dossier du dépôt cité en code est relevé, ancre et ligne retirées', () => {
  const ligne = 'voir `/openspec/specs/comptes-acces/spec.md#acc-01`, `scripts/verifier-docs.mjs:12` et `docs/deploiement/`';
  assert.deepEqual(cheminsCites(ligne),
    ['openspec/specs/comptes-acces/spec.md', 'scripts/verifier-docs.mjs', 'docs/deploiement']);
});

test('les motifs, chemins générés, noms sans extension et dossiers hors dépôt ne sont pas relevés', () => {
  const ligne = [
    '`docs/specifications/<capability-path>.md`', '`argos-server/target/site/jacoco/index.html`',
    '`openspec/changes/*/tasks.md`', '`e2e/chaine-front`', '`io.argos.mcp`', '`ARGOS_DB_URL`',
    '`npm --prefix e2e test`', '`docs/audit/2026-11-21/findings.md`', '`src/main.js`',
  ].join(' ');
  assert.deepEqual(cheminsCites(ligne), []);
});

test('seuls les README des dossiers de premier niveau hors docs, openspec et dossiers cachés sont retenus', () => {
  const racine = mkdtempSync(join(tmpdir(), 'argos-docs-'));
  try {
    for (const dossier of ['argos-server', 'e2e', 'docs', '.github', 'sans-readme']) mkdirSync(join(racine, dossier));
    for (const dossier of ['argos-server', 'e2e', 'docs', '.github']) writeFileSync(join(racine, dossier, 'README.md'), '# x\n');
    assert.deepEqual(readmeSousProjets(racine), [join(racine, 'argos-server', 'README.md'), join(racine, 'e2e', 'README.md')]);
  } finally {
    rmSync(racine, { recursive: true, force: true });
  }
});
