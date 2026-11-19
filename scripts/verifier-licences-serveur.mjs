#!/usr/bin/env node
// Licences des dépendances livrées avec argos-server (décision F3 du porteur du projet, 2026-11-19 ;
// ADR-0021 points 4 et 6).
// Prérequis : mvn -B -f argos-server/pom.xml -P licences validate  (produit argos-server/target/bom.json)
// Usage     : node scripts/verifier-licences-serveur.mjs [chemin/vers/bom.json] [--depot <dépôt Maven local>]
//             (dépôt par défaut : MAVEN_REPO_LOCAL, sinon ~/.m2/repository)
// Effets    : écrit argos-server/target/THIRD-PARTY.txt : pour chaque dépendance, version, licences
//             déclarées et licence retenue, puis les avis NOTICE (META-INF/NOTICE*) de chaque archive,
//             lus dans le dépôt Maven local (Apache License 2.0, section 4 d) ;
//             échoue si une dépendance n'a aucune licence déclarée, n'a que des licences interdites
//             ou si son archive est absente du dépôt local.
// Une dépendance proposée sous plusieurs licences au choix (par exemple « EPL-2.0 | GPL-2.0 avec
// exception Classpath ») est acceptée si l'une d'elles n'est pas interdite : c'est la licence retenue.
// Aucun accès réseau : le script ne lit que le fichier CycloneDX et les archives du dépôt Maven local.
// Code de sortie : 0 sans écart, 1 en cas d'écart, 2 si le fichier CycloneDX est absent ou illisible.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve, join } from 'node:path';
import { homedir } from 'node:os';
import { inflateRawSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const iDepot = args.indexOf('--depot');
const DEPOT = resolve(iDepot >= 0 ? args[iDepot + 1]
  : (process.env.MAVEN_REPO_LOCAL ?? join(homedir(), '.m2', 'repository')));
const positionnels = args.filter((_, i) => iDepot < 0 || (i !== iDepot && i !== iDepot + 1));
const BOM = resolve(positionnels[0] ?? join(RACINE, 'argos-server', 'target', 'bom.json'));
const SORTIE = join(dirname(BOM), 'THIRD-PARTY.txt');

// Licences interdites (ADR-0021 point 4 : copyleft et licences restreignant l'usage commercial).
// Reconnues par identifiant SPDX ou par nom usuel déclaré dans les POM.
const INTERDITES = [
  { motif: /\b(A|L)?GPL\b|GPL-\d/i, nom: 'GPL, LGPL ou AGPL' },
  { motif: /GNU (Lesser |Library |Affero )?General Public/i, nom: 'GPL, LGPL ou AGPL' },
  { motif: /\bMPL\b|MPL-\d|Mozilla Public/i, nom: 'MPL' },
  { motif: /\bSSPL\b|Server Side Public/i, nom: 'SSPL' },
  { motif: /\bBUSL\b|BSL-1\.1|Business Source/i, nom: 'BUSL' },
  { motif: /\bCDDL\b|Common Development and Distribution/i, nom: 'CDDL' },
  { motif: /Commons Clause|Elastic License|Sustainable Use/i, nom: 'licence à usage commercial restreint' },
];

function interdite(licence) {
  return INTERDITES.find(({ motif }) => motif.test(licence))?.nom ?? null;
}

// Licences déclarées d'un composant CycloneDX : identifiant SPDX, nom, ou branches « OR » d'une expression.
function licencesDe(composant) {
  const resultat = [];
  for (const entree of composant.licenses ?? []) {
    if (entree.license) resultat.push(entree.license.id ?? entree.license.name ?? '');
    else if (entree.expression) resultat.push(...entree.expression.split(/\s+OR\s+/i));
  }
  return resultat.map((l) => l.trim()).filter(Boolean);
}

// Lecture minimale d'une archive ZIP (répertoire central, entrées stockées ou compressées par
// « deflate ») : renvoie le contenu texte des entrées dont le nom satisfait le filtre.
function lireEntrees(cheminArchive, filtre) {
  const zip = readFileSync(cheminArchive);
  let fin = -1;
  for (let i = zip.length - 22; i >= Math.max(0, zip.length - 65557); i--) {
    if (zip.readUInt32LE(i) === 0x06054b50) { fin = i; break; }
  }
  if (fin < 0) throw new Error('fin du répertoire central introuvable');
  const nombre = zip.readUInt16LE(fin + 10);
  let pos = zip.readUInt32LE(fin + 16);
  const entrees = [];
  for (let n = 0; n < nombre; n++) {
    if (zip.readUInt32LE(pos) !== 0x02014b50) throw new Error('répertoire central invalide');
    const methode = zip.readUInt16LE(pos + 10);
    const tailleCompressee = zip.readUInt32LE(pos + 20);
    const lgNom = zip.readUInt16LE(pos + 28);
    const lgExtra = zip.readUInt16LE(pos + 30);
    const lgCommentaire = zip.readUInt16LE(pos + 32);
    const local = zip.readUInt32LE(pos + 42);
    const nom = zip.toString('utf8', pos + 46, pos + 46 + lgNom);
    if (filtre(nom)) {
      const debut = local + 30 + zip.readUInt16LE(local + 26) + zip.readUInt16LE(local + 28);
      const donnees = zip.subarray(debut, debut + tailleCompressee);
      const contenu = methode === 0 ? donnees : methode === 8 ? inflateRawSync(donnees) : null;
      if (!contenu) throw new Error(`méthode de compression ${methode} non prise en charge (${nom})`);
      entrees.push({ nom, texte: contenu.toString('utf8').trim() });
    }
    pos += 46 + lgNom + lgExtra + lgCommentaire;
  }
  return entrees.sort((a, b) => a.nom.localeCompare(b.nom));
}

const EST_NOTICE = (nom) => /^META-INF\/NOTICE(\.txt|\.md)?$/i.test(nom);

if (!existsSync(BOM)) {
  console.error(`Fichier CycloneDX absent : ${BOM}`);
  console.error('Lancer d\'abord : mvn -B -f argos-server/pom.xml -P licences validate');
  process.exit(2);
}

let bom;
try {
  bom = JSON.parse(readFileSync(BOM, 'utf8'));
} catch (erreur) {
  console.error(`Fichier CycloneDX illisible (${BOM}) : ${erreur.message}`);
  process.exit(2);
}

const lignes = [];
const avis = [];
const ecarts = [];
const composants = [...(bom.components ?? [])].sort((a, b) =>
  `${a.group}:${a.name}:${a.version}`.localeCompare(`${b.group}:${b.name}:${b.version}`));

for (const c of composants) {
  const coordonnees = `${c.group ? `${c.group}:` : ''}${c.name}:${c.version ?? '?'}`;
  const licences = licencesDe(c);
  const retenue = licences.find((l) => !interdite(l));
  if (licences.length === 0) {
    ecarts.push(`${coordonnees} : aucune licence déclarée`);
  } else if (!retenue) {
    ecarts.push(`${coordonnees} : licence interdite (${licences.map(interdite).join(', ')}) — ${licences.join(' | ')}`);
  }
  if (c.group && c.version) {
    const archive = join(DEPOT, ...c.group.split('.'), c.name, c.version, `${c.name}-${c.version}.jar`);
    if (!existsSync(archive)) {
      ecarts.push(`${coordonnees} : archive absente du dépôt Maven local (${archive})`);
    } else {
      try {
        for (const { nom, texte } of lireEntrees(archive, EST_NOTICE)) {
          avis.push(`--- ${coordonnees} (${nom}) ---\n${texte}`);
        }
      } catch (erreur) {
        ecarts.push(`${coordonnees} : archive illisible (${erreur.message})`);
      }
    }
  }
  lignes.push(`${coordonnees}\n    licences déclarées : ${licences.join(' | ') || '(aucune)'}\n    licence retenue    : ${retenue ?? '(aucune admissible)'}`);
}

const entete = [
  'Serveur d\'ARGOS (argos-server) — dépendances tierces livrées et leurs licences',
  '===========================================================================',
  '',
  'Fichier généré, ne pas modifier : node scripts/verifier-licences-serveur.mjs, à partir de',
  'l\'inventaire CycloneDX produit par « mvn -B -f argos-server/pom.xml -P licences validate ».',
  'Portée : dépendances compile et runtime de argos-server, directes et transitives (sans les tests).',
  'Avis propres au serveur : argos-server/NOTICE ; textes de licence : argos-server/licenses/.',
  `Composants : ${composants.length} ; avis NOTICE reproduits : ${avis.length}.`,
  '',
  '1. Dépendances et licences',
  '--------------------------',
  '',
];
const partieAvis = [
  '',
  '2. Avis NOTICE des dépendances (Apache License 2.0, section 4 d)',
  '----------------------------------------------------------------',
  '',
  'Contenu des fichiers META-INF/NOTICE* des archives livrées, reproduit sans modification.',
  '',
];
writeFileSync(SORTIE,
  `${entete.join('\n')}${lignes.join('\n')}\n${partieAvis.join('\n')}${avis.join('\n\n')}\n`, 'utf8');
console.log(`${composants.length} dépendances inventoriées, ${avis.length} avis NOTICE reproduits : ${SORTIE}`);

if (ecarts.length > 0) {
  console.error(`\n${ecarts.length} dépendance(s) non conforme(s) à ADR-0021 :`);
  for (const e of ecarts) console.error(`  - ${e}`);
  process.exit(1);
}
console.log('Aucune licence interdite ni absente.');
