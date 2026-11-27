#!/usr/bin/env node
// Licences des dépendances npm livrées (AUD-DEP-19 ; ADR-0021 points 4 et 6) : IHM argos-ui (image Docker et client
// desktop) et dépendances d'exécution du client desktop. Pour chaque paquet nommé, les dépendances livrées sont celles
// atteintes depuis le champ `dependencies` de son package.json, résolues dans son package-lock.json (les dépendances de
// développement ne sont pas livrées). Chaque licence doit être admise ; l'avis produit reproduit, pour chaque
// dépendance, son nom, sa version, sa licence et le texte de licence du paquet installé (node_modules), ce qu'exigent
// notamment les licences MIT de vue et vue-router. Un paquet qui ne fournit aucun texte reçoit le texte de référence
// de sa licence, versionné dans argos-server/licenses/, avec l'auteur que déclare son package.json.
// Usage : node scripts/verifier-licences-npm.mjs --paquet <dossier> [--paquet <dossier>…] [--sortie <fichier>]
// Code de sortie : 0 si toutes les licences sont admises et leurs textes trouvés ; 1 sinon (liste sur la sortie
// d'erreur) ; 2 si une option ou un fichier est inexploitable.

import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

/** Textes de référence des licences, partagés avec le contrôle des licences du serveur. */
export const TEXTES_DE_REFERENCE = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'argos-server', 'licenses');

/** Licences admises (identifiants SPDX), toutes permissives ou de polices libres. */
export const LICENCES_ADMISES = new Set([
  'MIT', 'MIT-0', 'ISC', '0BSD', 'BSD-2-Clause', 'BSD-3-Clause', 'Apache-2.0', 'OFL-1.1', 'BlueOak-1.0.0', 'CC0-1.0',
  'Zlib', 'Python-2.0', 'Unicode-3.0',
]);
const FICHIER_TEXTE = /^(licen[cs]e|copying|ofl)(\.|-|$)/i;
const SEPARATEUR = '\n\n';
const CODE_REFUS = 1;
const CODE_ENTREE_INVALIDE = 2;

/** Erreur d'entrée (option ou fichier inexploitable), distincte d'un refus de licence. */
export class EntreeInvalide extends Error {}

/**
 * Vrai si l'expression SPDX n'emploie que des licences admises (un choix OR suffit, tout terme AND compte). Une licence
 * déclarée dans l'ancien format (objet ou tableau) n'est pas admise : elle est signalée telle quelle.
 */
export function licenceAdmise(expression) {
  if (typeof expression !== 'string' || expression === '') return false;
  const choix = [[]];
  for (const jeton of expression.replaceAll('(', ' ').replaceAll(')', ' ').split(' ')) {
    const operateur = jeton.toUpperCase();
    if (jeton === '' || operateur === 'AND') continue;
    if (operateur === 'OR') choix.push([]);
    else choix[choix.length - 1].push(jeton);
  }
  return choix.some((termes) => termes.length > 0 && termes.every((terme) => LICENCES_ADMISES.has(terme)));
}

/** Emplacement, dans le package-lock, de la dépendance `nom` demandée depuis l'emplacement `depuis`. */
export function resoudre(paquets, depuis, nom) {
  let base = depuis;
  for (;;) {
    const candidat = base ? `${base}/node_modules/${nom}` : `node_modules/${nom}`;
    if (paquets[candidat]) return candidat;
    if (!base) return undefined;
    const position = base.lastIndexOf('/node_modules/');
    base = position < 0 ? '' : base.slice(0, position);
  }
}

/**
 * Dépendances livrées d'un package-lock (version 2 ou 3) : [{ nom, version, licence, emplacement }], triées par nom.
 * Une dépendance facultative absente du verrou (binaire d'une autre plateforme) est ignorée.
 */
export function dependancesLivrees(verrou) {
  const paquets = verrou.packages ?? {};
  const racine = paquets[''] ?? {};
  const vus = new Map();
  const aVisiter = Object.keys(racine.dependencies ?? {}).map((nom) => ({ depuis: '', nom, facultative: false }));
  while (aVisiter.length > 0) {
    const { depuis, nom, facultative } = aVisiter.pop();
    const emplacement = resoudre(paquets, depuis, nom);
    if (!emplacement) {
      if (facultative) continue;
      throw new EntreeInvalide(`dépendance ${nom} introuvable dans le package-lock (depuis « ${depuis || 'racine'} »)`);
    }
    const description = paquets[emplacement];
    // Une dépendance facultative ou pair que seul le développement installe (outil de build) n'est pas livrée.
    if (vus.has(emplacement) || (facultative && (description.dev || description.devOptional))) continue;
    vus.set(emplacement, { nom, version: description.version, licence: description.license, emplacement });
    for (const suivante of Object.keys(description.dependencies ?? {})) {
      aVisiter.push({ depuis: emplacement, nom: suivante, facultative: false });
    }
    // Dépendances facultatives et pairs : livrées quand npm les a installées (présentes dans le verrou).
    const facultatives = { ...description.optionalDependencies, ...description.peerDependencies };
    for (const suivante of Object.keys(facultatives)) {
      aVisiter.push({ depuis: emplacement, nom: suivante, facultative: true });
    }
  }
  return [...vus.values()].sort((premier, second) => premier.nom.localeCompare(second.nom)
    || premier.version.localeCompare(second.version));
}

/**
 * Texte de licence du paquet installé dans `dossierPaquet` ; à défaut, texte de référence de sa licence (`licence`)
 * pris dans `textes`, précédé de l'auteur déclaré ; chaîne vide si aucun des deux n'existe.
 */
export function texteLicence(dossierPaquet, licence, textes = TEXTES_DE_REFERENCE) {
  if (!existsSync(dossierPaquet)) return '';
  const fichiers = readdirSync(dossierPaquet, { withFileTypes: true })
    .filter((entree) => entree.isFile() && FICHIER_TEXTE.test(entree.name))
    .map((entree) => entree.name)
    .sort();
  if (fichiers.length > 0) {
    return fichiers.map((fichier) => readFileSync(join(dossierPaquet, fichier), 'utf8').trim()).join(SEPARATEUR);
  }
  const reference = join(textes, `${licence}.txt`);
  if (!licence || !existsSync(reference)) return '';
  const auteur = auteurDeclare(join(dossierPaquet, 'package.json'));
  return `Le paquet ne fournit pas de texte de licence ; texte de référence ${licence}, auteur déclaré : ${auteur}.`
    + SEPARATEUR + readFileSync(reference, 'utf8').trim();
}

/** Auteur déclaré par un package.json (chaîne ou objet `{ name }`), ou « non déclaré ». */
function auteurDeclare(cheminPackage) {
  if (!existsSync(cheminPackage)) return 'non déclaré';
  const { author } = JSON.parse(readFileSync(cheminPackage, 'utf8'));
  return (typeof author === 'string' ? author : author?.name) || 'non déclaré';
}

/** Bilan d'un paquet npm : dépendances livrées avec leur texte, licences refusées, textes manquants. */
export function examiner(dossier) {
  const verrouChemin = join(dossier, 'package-lock.json');
  if (!existsSync(verrouChemin)) throw new EntreeInvalide(`package-lock.json absent de ${dossier}`);
  const dependances = dependancesLivrees(JSON.parse(readFileSync(verrouChemin, 'utf8')))
    .map((dependance) => ({ ...dependance, texte: texteLicence(join(dossier, dependance.emplacement), dependance.licence) }));
  return {
    dependances,
    refusees: dependances.filter((dependance) => !licenceAdmise(dependance.licence)),
    sansTexte: dependances.filter((dependance) => dependance.texte === ''),
  };
}

/** Avis des dépendances tierces : un bloc par dépendance, dédoublonné sur le nom et la version. */
export function avis(dependances) {
  const uniques = new Map(dependances.map((dependance) => [`${dependance.nom}@${dependance.version}`, dependance]));
  const blocs = [...uniques.values()].map((dependance) => [
    `=== ${dependance.nom} ${dependance.version} — ${dependance.licence}`,
    '',
    dependance.texte,
    '',
  ].join('\n'));
  return ['Dépendances npm livrées avec ARGOS (portail web et client desktop) et leurs licences.',
    `Avis produit par scripts/verifier-licences-npm.mjs ; ${uniques.size} dépendances.`, '', ...blocs].join('\n');
}

function lireOptions(arguments_) {
  const options = { paquets: [], sortie: undefined };
  for (let rang = 0; rang < arguments_.length; rang += 2) {
    const [nom, valeur] = [arguments_[rang], arguments_[rang + 1]];
    if (!valeur) throw new EntreeInvalide(`valeur manquante pour ${nom}`);
    if (nom === '--paquet') options.paquets.push(resolve(valeur));
    else if (nom === '--sortie') options.sortie = resolve(valeur);
    else throw new EntreeInvalide(`option inconnue : ${nom}`);
  }
  if (options.paquets.length === 0) throw new EntreeInvalide('au moins un --paquet est attendu');
  return options;
}

function principal(arguments_) {
  let bilans;
  let options;
  try {
    options = lireOptions(arguments_);
    bilans = options.paquets.map((dossier) => ({ dossier, ...examiner(dossier) }));
  } catch (erreur) {
    if (!(erreur instanceof EntreeInvalide) && !(erreur instanceof SyntaxError)) throw erreur;
    console.error(`Entrée inexploitable : ${erreur.message}`);
    return CODE_ENTREE_INVALIDE;
  }
  let refus = 0;
  for (const { dossier, dependances, refusees, sansTexte } of bilans) {
    console.log(`${dossier} : ${dependances.length} dépendances livrées.`);
    for (const { nom, version, licence } of refusees) {
      console.error(`ERREUR ${nom} ${version} : licence non admise (${licence === undefined ? 'absente' : JSON.stringify(licence)})`);
    }
    for (const { nom, version } of sansTexte) {
      console.error(`ERREUR ${nom} ${version} : aucun texte de licence dans le paquet installé (npm ci exécuté ?)`);
    }
    refus += refusees.length + sansTexte.length;
  }
  if (options.sortie && refus === 0) {
    mkdirSync(dirname(options.sortie), { recursive: true });
    writeFileSync(options.sortie, avis(bilans.flatMap((bilan) => bilan.dependances)), 'utf8');
    console.log(`Avis écrit : ${options.sortie}`);
  }
  return refus === 0 ? 0 : CODE_REFUS;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exit(principal(process.argv.slice(2)));
}
