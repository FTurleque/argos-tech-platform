// Tests de scripts/signaler-fichiers-longs.mjs (AUD-QUA-18).
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { compterLignes, fichiersJava, fichiersLongs, lireOptions, SEUIL_PAR_DEFAUT, SOURCES_PAR_DEFAUT } from './signaler-fichiers-longs.mjs';

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

test('lireOptions : valeurs par défaut, options reconnues, refus nommé', () => {
  assert.deepEqual(lireOptions([]), { options: { seuil: SEUIL_PAR_DEFAUT, racine: SOURCES_PAR_DEFAUT } });
  assert.deepEqual(lireOptions(['--seuil', '120', '--racine', 'src']), { options: { seuil: 120, racine: 'src' } });
  assert.deepEqual(lireOptions(['--seuil', 'beaucoup']), { refus: 'option inexploitable : --seuil beaucoup' });
  assert.deepEqual(lireOptions(['--inconnue']), { refus: 'option inexploitable : --inconnue' });
});
