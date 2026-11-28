// Tests de scripts/signaler-fichiers-longs.mjs (AUD-QUA-18).
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { compterLignes, fichiersJava, fichiersLongs } from './signaler-fichiers-longs.mjs';

test('compterLignes : saut final compris ou non, texte vide', () => {
  assert.equal(compterLignes(''), 0);
  assert.equal(compterLignes('a\nb\n'), 2);
  assert.equal(compterLignes('a\nb'), 2);
});

test('fichiersLongs : seuls les fichiers au-delà du seuil, du plus long au plus court', () => {
  const contenus = { 'A.java': 'x\n'.repeat(3), 'B.java': 'x\n'.repeat(5), 'C.java': 'x\n'.repeat(4) };
  const longs = fichiersLongs(Object.keys(contenus), (fichier) => contenus[fichier], 3);
  assert.deepEqual(longs, [{ fichier: 'B.java', lignes: 5 }, { fichier: 'C.java', lignes: 4 }]);
});

test('fichiersJava : parcours récursif, fichiers .java seulement', () => {
  const dossier = mkdtempSync(join(tmpdir(), 'fichiers-longs-'));
  try {
    mkdirSync(join(dossier, 'paquet'));
    writeFileSync(join(dossier, 'paquet', 'Classe.java'), 'class Classe {}\n');
    writeFileSync(join(dossier, 'notes.txt'), 'texte\n');
    assert.deepEqual(fichiersJava(dossier), [join(dossier, 'paquet', 'Classe.java')]);
  } finally {
    rmSync(dossier, { recursive: true, force: true });
  }
});
