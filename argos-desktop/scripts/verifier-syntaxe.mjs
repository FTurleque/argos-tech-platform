// Contrôle de syntaxe des sources du processus principal et du preload : « node --check » sur
// chaque fichier .js de src/, sans exécuter le code. Code de sortie non nul au premier fichier invalide.
import { spawnSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dossierSources = join(dirname(fileURLToPath(import.meta.url)), '..', 'src');
const fichiers = readdirSync(dossierSources).filter((nom) => nom.endsWith('.js')).sort();

for (const nom of fichiers) {
  const controle = spawnSync(process.execPath, ['--check', join(dossierSources, nom)], { stdio: 'inherit' });
  if (controle.status !== 0) {
    console.error(`syntaxe invalide : src/${nom}`);
    process.exit(controle.status ?? 1);
  }
}
console.log(`syntaxe valide : ${fichiers.length} fichiers de src/`);
