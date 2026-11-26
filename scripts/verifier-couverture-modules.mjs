#!/usr/bin/env node
// Couverture de code du serveur par module (AUD-TST-04, design D10 du changement alimenter-inventaire-et-completer-ihm).
// Lit le rapport CSV de JaCoCo produit par `mvn verify`, agrège les lignes et les branches par module (premier segment
// du paquet après « io.argos. ») et compare chaque module aux seuils. Par défaut, les seuils sont les seuils globaux
// du pom.xml du serveur (propriétés argos.couverture.lignes et argos.couverture.branches).
// Usage : node scripts/verifier-couverture-modules.mjs [--rapport <jacoco.csv>] [--pom <pom.xml>]
//                                                      [--lignes <0..1>] [--branches <0..1>]
// Code de sortie : 0 si chaque module atteint les seuils ; 1 sinon (une ligne par écart sur la sortie d'erreur) ;
// 2 si le rapport, le pom ou une option est inexploitable.

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');

export const RAPPORT_PAR_DEFAUT = 'argos-server/target/site/jacoco/jacoco.csv';
export const POM_PAR_DEFAUT = 'argos-server/pom.xml';
export const PREFIXE_PAQUETS = 'io.argos.';
export const PROPRIETE_LIGNES = 'argos.couverture.lignes';
export const PROPRIETE_BRANCHES = 'argos.couverture.branches';

const CODE_ECART = 1;
const CODE_ENTREE_INVALIDE = 2;
const POURCENTAGE = 100;
const DECIMALES_AFFICHEES = 1;
const COLONNES_REQUISES = ['PACKAGE', 'LINE_MISSED', 'LINE_COVERED', 'BRANCH_MISSED', 'BRANCH_COVERED'];
const OPTIONS_CONNUES = new Set(['--rapport', '--pom', '--lignes', '--branches']);

/** Erreur d'entrée (rapport, pom ou option inexploitable), distincte d'un écart de couverture. */
export class EntreeInvalide extends Error {}

/** Module d'un paquet Java : premier segment après « io.argos. », sinon le paquet lui-même. */
export function moduleDuPaquet(paquet) {
  if (!paquet.startsWith(PREFIXE_PAQUETS)) return paquet;
  return paquet.slice(PREFIXE_PAQUETS.length).split('.')[0];
}

function entier(valeur, colonne, numeroLigne) {
  const nombre = Number(valeur);
  if (!Number.isInteger(nombre) || nombre < 0) {
    throw new EntreeInvalide(`ligne ${numeroLigne} : ${colonne} « ${valeur} » n'est pas un entier positif`);
  }
  return nombre;
}

/** Compteurs agrégés par module, triés par nom, à partir du texte d'un rapport jacoco.csv. */
export function agregerParModule(texteCsv) {
  const lignes = texteCsv.split(/\r?\n/).filter((ligne) => ligne.trim() !== '');
  if (lignes.length === 0) throw new EntreeInvalide('rapport vide');
  const entetes = lignes[0].split(',');
  const index = Object.fromEntries(COLONNES_REQUISES.map((colonne) => [colonne, entetes.indexOf(colonne)]));
  const absentes = COLONNES_REQUISES.filter((colonne) => index[colonne] < 0);
  if (absentes.length > 0) throw new EntreeInvalide(`colonnes absentes du rapport : ${absentes.join(', ')}`);

  const modules = new Map();
  lignes.slice(1).forEach((ligne, position) => {
    const numeroLigne = position + 2;
    const champs = ligne.split(',');
    if (champs.length !== entetes.length) {
      throw new EntreeInvalide(`ligne ${numeroLigne} : ${champs.length} champs, ${entetes.length} attendus`);
    }
    const module = moduleDuPaquet(champs[index.PACKAGE]);
    const cumul = modules.get(module) ?? { lignesManquees: 0, lignesCouvertes: 0, branchesManquees: 0, branchesCouvertes: 0 };
    cumul.lignesManquees += entier(champs[index.LINE_MISSED], 'LINE_MISSED', numeroLigne);
    cumul.lignesCouvertes += entier(champs[index.LINE_COVERED], 'LINE_COVERED', numeroLigne);
    cumul.branchesManquees += entier(champs[index.BRANCH_MISSED], 'BRANCH_MISSED', numeroLigne);
    cumul.branchesCouvertes += entier(champs[index.BRANCH_COVERED], 'BRANCH_COVERED', numeroLigne);
    modules.set(module, cumul);
  });
  return new Map([...modules.entries()].sort(([premier], [second]) => premier.localeCompare(second)));
}

/** Taux couvert / total, ou null sans élément mesuré (module sans branche, par exemple). */
export function taux(couverts, manques) {
  const total = couverts + manques;
  return total === 0 ? null : couverts / total;
}

function enPourcentage(valeur) {
  return valeur === null ? 'n/a' : `${(valeur * POURCENTAGE).toFixed(DECIMALES_AFFICHEES)} %`;
}

/** Taux de lignes et de branches de chaque module. */
export function tauxParModule(agregats) {
  return [...agregats.entries()].map(([module, cumul]) => ({
    module,
    lignes: taux(cumul.lignesCouvertes, cumul.lignesManquees),
    branches: taux(cumul.branchesCouvertes, cumul.branchesManquees),
  }));
}

/** Écarts aux seuils, un message par module et par compteur ; un compteur sans élément mesuré n'est pas en écart. */
export function ecartsAuxSeuils(agregats, seuils) {
  const ecarts = [];
  for (const { module, lignes, branches } of tauxParModule(agregats)) {
    if (lignes !== null && lignes < seuils.lignes) {
      ecarts.push(`module ${module} : lignes ${enPourcentage(lignes)} sous le seuil de ${enPourcentage(seuils.lignes)}`);
    }
    if (branches !== null && branches < seuils.branches) {
      ecarts.push(`module ${module} : branches ${enPourcentage(branches)} sous le seuil de ${enPourcentage(seuils.branches)}`);
    }
  }
  return ecarts;
}

function ratio(valeur, origine) {
  const nombre = Number(valeur);
  if (valeur === undefined || valeur === '' || Number.isNaN(nombre) || nombre < 0 || nombre > 1) {
    throw new EntreeInvalide(`${origine} : « ${valeur ?? ''} » n'est pas un taux entre 0 et 1`);
  }
  return nombre;
}

/** Seuils globaux déclarés par les propriétés du pom.xml. */
export function seuilsDuPom(textePom) {
  const lire = (propriete) => {
    const echappee = propriete.replaceAll('.', '\\.');
    const trouve = new RegExp(`<${echappee}>\\s*([^<]*?)\\s*</${echappee}>`).exec(textePom);
    if (trouve === null) throw new EntreeInvalide(`propriété ${propriete} absente du pom`);
    return ratio(trouve[1], propriete);
  };
  return { lignes: lire(PROPRIETE_LIGNES), branches: lire(PROPRIETE_BRANCHES) };
}

/** Options de la ligne de commande ; chaque option attend une valeur. */
export function lireOptions(argumentsLigne) {
  const options = {};
  for (let position = 0; position < argumentsLigne.length; position += 2) {
    const nom = argumentsLigne[position];
    const valeur = argumentsLigne[position + 1];
    if (!OPTIONS_CONNUES.has(nom)) throw new EntreeInvalide(`option inconnue : ${nom}`);
    if (valeur === undefined) throw new EntreeInvalide(`option ${nom} sans valeur`);
    options[nom.slice(2)] = valeur;
  }
  return options;
}

/** Seuils effectifs : ceux du pom, remplacés par les options --lignes et --branches. */
export function seuilsEffectifs(options, textePom) {
  const duPom = seuilsDuPom(textePom);
  return {
    lignes: options.lignes === undefined ? duPom.lignes : ratio(options.lignes, '--lignes'),
    branches: options.branches === undefined ? duPom.branches : ratio(options.branches, '--branches'),
  };
}

function lireFichier(chemin) {
  try {
    return readFileSync(chemin, 'utf8');
  } catch (erreur) {
    throw new EntreeInvalide(`${chemin} illisible : ${erreur.message}`);
  }
}

/** Contrôle complet ; renvoie le code de sortie. */
export function verifier(argumentsLigne, racine = RACINE, sortie = console) {
  try {
    const options = lireOptions(argumentsLigne);
    const seuils = seuilsEffectifs(options, lireFichier(resolve(racine, options.pom ?? POM_PAR_DEFAUT)));
    const agregats = agregerParModule(lireFichier(resolve(racine, options.rapport ?? RAPPORT_PAR_DEFAUT)));
    for (const { module, lignes, branches } of tauxParModule(agregats)) {
      sortie.log(`${module.padEnd(16)} lignes ${enPourcentage(lignes).padStart(8)}   branches ${enPourcentage(branches).padStart(8)}`);
    }
    const ecarts = ecartsAuxSeuils(agregats, seuils);
    for (const ecart of ecarts) sortie.error(ecart);
    return ecarts.length === 0 ? 0 : CODE_ECART;
  } catch (erreur) {
    if (!(erreur instanceof EntreeInvalide)) throw erreur;
    sortie.error(`couverture par module : ${erreur.message}`);
    return CODE_ENTREE_INVALIDE;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exit(verifier(process.argv.slice(2)));
}
