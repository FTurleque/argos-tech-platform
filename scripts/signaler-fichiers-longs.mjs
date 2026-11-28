#!/usr/bin/env node
// Fichiers Java de production longs (AUD-QUA-18 ; décision du porteur du projet, 2026-11-27) : tout fichier de plus de
// 300 lignes est signalé, sans faire échouer le build. Les seuils bloquants portent sur les méthodes (PMD,
// argos-server/pmd-regles.xml) ; la longueur d'un fichier est une alerte de lecture.
// Usage : node scripts/signaler-fichiers-longs.mjs [--seuil <lignes>] [--racine <dossier>]
// Sortie : une annotation « ::warning » de GitHub Actions par fichier signalé, puis un bilan ; code de sortie 0,
// ou 2 si une option est inexploitable.

import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RACINE_DEPOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

export const SEUIL_PAR_DEFAUT = 300;
export const SOURCES_PAR_DEFAUT = 'argos-server/src/main/java';
const CODE_OPTION_INVALIDE = 2;

/** Nombre de lignes d'un texte (une fin de fichier sans saut de ligne compte pour une ligne). */
export function compterLignes(texte) {
  if (texte.length === 0) return 0;
  const sauts = texte.split('\n').length - 1;
  return texte.endsWith('\n') ? sauts : sauts + 1;
}

/** Fichiers .java sous `dossier`, chemins absolus, dans l'ordre alphabétique. */
export function fichiersJava(dossier) {
  return readdirSync(dossier, { withFileTypes: true, recursive: true })
    .filter((entree) => entree.isFile() && entree.name.endsWith('.java'))
    .map((entree) => join(entree.parentPath, entree.name))
    .sort();
}

/** Fichiers de plus de `seuil` lignes : [{ fichier, lignes }], du plus long au plus court. */
export function fichiersLongs(fichiers, lire, seuil) {
  return fichiers.map((fichier) => ({ fichier, lignes: compterLignes(lire(fichier)) }))
    .filter(({ lignes }) => lignes > seuil)
    .sort((premier, second) => second.lignes - premier.lignes);
}

function lireOptions(arguments_) {
  const options = { seuil: SEUIL_PAR_DEFAUT, racine: SOURCES_PAR_DEFAUT };
  for (let rang = 0; rang < arguments_.length; rang += 2) {
    const [nom, valeur] = [arguments_[rang], arguments_[rang + 1]];
    if (nom === '--seuil' && /^\d+$/.test(valeur ?? '')) options.seuil = Number(valeur);
    else if (nom === '--racine' && valeur) options.racine = valeur;
    else throw new Error(`option inexploitable : ${nom ?? ''} ${valeur ?? ''}`.trim());
  }
  return options;
}

function principal(arguments_) {
  let options;
  try {
    options = lireOptions(arguments_);
  } catch (erreur) {
    console.error(erreur.message);
    return CODE_OPTION_INVALIDE;
  }
  const dossier = resolve(RACINE_DEPOT, options.racine);
  const longs = fichiersLongs(fichiersJava(dossier), (fichier) => readFileSync(fichier, 'utf8'), options.seuil);
  for (const { fichier, lignes } of longs) {
    const chemin = relative(RACINE_DEPOT, fichier).split(sep).join('/');
    console.log(`::warning file=${chemin}::${lignes} lignes (au-delà de ${options.seuil}) : fichier à scinder`);
  }
  console.log(`${longs.length} fichier(s) Java de plus de ${options.seuil} lignes (signalés, non bloquants).`);
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exit(principal(process.argv.slice(2)));
}
