#!/usr/bin/env node
// Licences des dépendances livrées avec argos-server (décisions F3 révisée par G1, G2 et G3 du porteur du
// projet, 2026-11-19 ; ADR-0021 points 4 et 6).
// Commande : mvn -B -f argos-server/pom.xml -P licences validate
//            (le profil « licences » produit argos-server/target/bom.json puis lance ce script).
//            Le build de l'image Docker (Dockerfile, étage « server ») exécute la même commande et
//            échoue sur un écart ; l'image embarque NOTICE et THIRD-PARTY.txt dans /opt/argos/licenses/.
// Usage direct : node scripts/verifier-licences-serveur.mjs [chemin/vers/bom.json] [--depot <dépôt Maven local>]
//                (dépôt par défaut : MAVEN_REPO_LOCAL, sinon ~/.m2/repository)
// Effets : écrit THIRD-PARTY.txt à côté du fichier CycloneDX : pour chaque dépendance, version, licences
//          déclarées et licence retenue, puis les avis NOTICE (META-INF/NOTICE*) de chaque archive, lus dans
//          le dépôt Maven local (Apache License 2.0, section 4 d).
// Règles (ADR-0021 point 4 : licences permissives admises d'office, tout copyleft sur décision explicite) :
//  - liste blanche : seules les licences de ADMISES sont acceptées ; une licence inconnue est un écart ;
//  - plusieurs licences déclarées s'appliquent ensemble (ET) : chacune doit être admise ; seule une
//    expression SPDX « OR », ou un composant de CHOIX dont le projet amont publie une double licence au
//    choix, permet de retenir une seule licence ;
//  - une licence soumise à décision (SOUS_RESERVE, par exemple EPL-2.0) n'est acceptée que pour les
//    composants nommés dans sa liste.
// Aucun accès réseau : le script ne lit que le fichier CycloneDX et les archives du dépôt Maven local.
// Code de sortie : 0 sans écart, 1 en cas d'écart, 2 si les arguments ou le fichier CycloneDX sont invalides.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve, join } from 'node:path';
import { homedir } from 'node:os';
import { inflateRawSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function arreter(message) {
  console.error(message);
  process.exit(2);
}

const args = process.argv.slice(2);
const iDepot = args.indexOf('--depot');
if (iDepot >= 0 && (args[iDepot + 1] === undefined || args[iDepot + 1].startsWith('--'))) {
  arreter('Option --depot sans valeur : indiquer le chemin du dépôt Maven local.');
}
const DEPOT = resolve(iDepot >= 0 ? args[iDepot + 1]
  : (process.env.MAVEN_REPO_LOCAL ?? join(homedir(), '.m2', 'repository')));
const positionnels = args.filter((_, i) => iDepot < 0 || (i !== iDepot && i !== iDepot + 1));
const BOM = resolve(positionnels[0] ?? join(RACINE, 'argos-server', 'target', 'bom.json'));
const SORTIE = join(dirname(BOM), 'THIRD-PARTY.txt');

// Licences permissives admises d'office (ADR-0021 point 4), par identifiant SPDX ou nom déclaré dans les POM.
const ADMISES = [
  { motif: /^(Apache-2\.0|Apache License,? (Version )?2(\.0)?|The Apache (Software )?License,? Version 2\.0)$/i, nom: 'Apache-2.0' },
  { motif: /^(MIT|MIT-0|The MIT License( \(MIT\))?|MIT License|MIT No Attribution)$/i, nom: 'MIT' },
  { motif: /^(BSD-2-Clause|BSD-3-Clause|0BSD|The (New )?BSD License|BSD (2|3)-Clause License|EDL-1\.0|Eclipse Distribution License.*)$/i, nom: 'BSD' },
  { motif: /^(ISC|Zlib|UPL-1\.0|Unlicense|CC0-1\.0|Public Domain)$/i, nom: 'permissive' },
  // Licence de JDOM : licence de type Apache sans clause de mention publicitaire.
  { motif: /^Similar to Apache License but with the acknowledgment clause removed$/i, nom: 'JDOM (type Apache)' },
];

// Licences soumises à décision, acceptées seulement pour les composants nommés (groupe:artefact).
// [DÉCISION — porteur du projet, 2026-11-19 (G3)] EPL-2.0, copyleft faible au niveau du fichier, admise
// pour les neuf API Jakarta nommées ci-dessous que Quarkus embarque, livrées sans modification, et pour
// elles seules : toute autre dépendance sous EPL fait échouer le contrôle (voir argos-server/README.md).
const SOUS_RESERVE = [
  {
    motif: /^(EPL-2\.0|Eclipse Public License( -)? v?(ersion )?2\.0)$/i,
    nom: 'EPL-2.0',
    composants: new Set([
      'jakarta.annotation:jakarta.annotation-api', 'jakarta.el:jakarta.el-api',
      'jakarta.interceptor:jakarta.interceptor-api', 'jakarta.json:jakarta.json-api',
      'jakarta.resource:jakarta.resource-api', 'jakarta.transaction:jakarta.transaction-api',
      'jakarta.ws.rs:jakarta.ws.rs-api', 'org.eclipse.parsson:parsson', 'org.glassfish.expressly:expressly',
    ]),
  },
];

// Composants dont le projet amont publie une double licence au choix, alors que le POM les déclare comme
// une simple liste : la licence retenue est la première admise. Clé : groupe:artefact, ou groupe:* .
const CHOIX = new Map([
  ['io.vertx:*', 'Eclipse Vert.x : double licence au choix, EPL ou Apache-2.0'],
  ['jakarta.annotation:jakarta.annotation-api', 'Jakarta EE : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['jakarta.el:jakarta.el-api', 'Jakarta EE : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['jakarta.interceptor:jakarta.interceptor-api', 'Jakarta EE : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['jakarta.json:jakarta.json-api', 'Jakarta EE : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['jakarta.resource:jakarta.resource-api', 'Jakarta EE : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['jakarta.transaction:jakarta.transaction-api', 'Jakarta EE : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['jakarta.ws.rs:jakarta.ws.rs-api', 'Jakarta EE : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['org.eclipse.parsson:parsson', 'Eclipse Parsson : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
  ['org.glassfish.expressly:expressly', 'Eclipse Expressly : EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0'],
]);

function choixDe(composant) {
  return CHOIX.get(`${composant.group}:${composant.name}`) ?? CHOIX.get(`${composant.group}:*`) ?? null;
}

// Statut d'une licence pour un composant : 'admise', 'sous réserve' (acceptée par décision) ou null.
function statut(licence, composant) {
  if (ADMISES.some(({ motif }) => motif.test(licence))) return 'admise';
  const reserve = SOUS_RESERVE.find(({ motif }) => motif.test(licence));
  if (reserve?.composants.has(`${composant.group}:${composant.name}`)) return `sous réserve (${reserve.nom})`;
  return null;
}

// Licences déclarées d'un composant CycloneDX. Chaque entrée « license » s'ajoute aux autres (ET) ; une
// expression SPDX se découpe en alternatives (OR) dont chacune est une liste cumulative (AND).
// Résultat : liste d'alternatives, chacune étant la liste des licences qui s'appliquent ensemble.
function alternativesDe(composant) {
  const cumul = [];
  let alternatives = null;
  for (const entree of composant.licenses ?? []) {
    if (entree.license) {
      cumul.push(entree.license.id ?? entree.license.name ?? '');
    } else if (entree.expression) {
      const branches = entree.expression.replaceAll(/[()]/g, ' ').split(/\s+OR\s+/i)
        .map((b) => b.split(/\s+AND\s+/i).map((l) => l.trim()).filter(Boolean));
      alternatives = alternatives ?? [[]];
      alternatives = alternatives.flatMap((a) => branches.map((b) => [...a, ...b]));
    }
  }
  const declarees = cumul.map((l) => l.trim()).filter(Boolean);
  if (alternatives) return alternatives.map((a) => [...declarees, ...a]);
  if (declarees.length > 1 && choixDe(composant)) return declarees.map((l) => [l]);
  return declarees.length > 0 ? [declarees] : [];
}

// Lecture minimale d'une archive ZIP (répertoire central, entrées stockées ou compressées par
// « deflate ») : renvoie le contenu texte des entrées dont le nom satisfait le filtre. Le format ZIP64
// n'est pas pris en charge : une telle archive est signalée comme illisible.
function lireEntrees(cheminArchive, filtre) {
  const zip = readFileSync(cheminArchive);
  let fin = -1;
  for (let i = zip.length - 22; i >= Math.max(0, zip.length - 65557); i--) {
    if (zip.readUInt32LE(i) === 0x06054b50) { fin = i; break; }
  }
  if (fin < 0) throw new Error('fin du répertoire central introuvable');
  const nombre = zip.readUInt16LE(fin + 10);
  let pos = zip.readUInt32LE(fin + 16);
  if (nombre === 0xffff || pos === 0xffffffff) throw new Error('archive ZIP64 non prise en charge');
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
      let contenu = null;
      if (methode === 0) contenu = donnees;
      else if (methode === 8) contenu = inflateRawSync(donnees);
      if (!contenu) throw new Error(`méthode de compression ${methode} non prise en charge (${nom})`);
      entrees.push({ nom, texte: contenu.toString('utf8').trim() });
    }
    pos += 46 + lgNom + lgExtra + lgCommentaire;
  }
  return entrees.sort((a, b) => a.nom.localeCompare(b.nom));
}

const EST_NOTICE = (nom) => /^META-INF\/NOTICE(\.txt|\.md)?$/i.test(nom);

// Nom de l'archive dans le dépôt Maven local, d'après le type et le classifieur du PURL (jar par défaut).
function cheminArchive(composant) {
  let type = 'jar';
  let classifieur = '';
  const requete = composant.purl?.split('?')[1]?.split('#')[0];
  for (const paire of requete?.split('&') ?? []) {
    const [cle, valeur = ''] = paire.split('=');
    if (cle === 'type' && valeur) type = decodeURIComponent(valeur);
    if (cle === 'classifier' && valeur) classifieur = `-${decodeURIComponent(valeur)}`;
  }
  const fichier = `${composant.name}-${composant.version}${classifieur}.${type}`;
  return join(DEPOT, ...composant.group.split('.'), composant.name, composant.version, fichier);
}

function coordonneesDe(composant) {
  const groupe = composant.group ? `${composant.group}:` : '';
  return `${groupe}${composant.name}:${composant.version ?? '?'}`;
}

if (!existsSync(BOM)) {
  arreter(`Fichier CycloneDX absent : ${BOM}\nLancer : mvn -B -f argos-server/pom.xml -P licences validate`);
}

let bom;
try {
  bom = JSON.parse(readFileSync(BOM, 'utf8'));
} catch (erreur) {
  arreter(`Fichier CycloneDX illisible (${BOM}) : ${erreur.message}`);
}

const lignes = [];
const avis = [];
const ecarts = [];
const composants = [...(bom.components ?? [])].sort((a, b) =>
  `${a.group}:${a.name}:${a.version}`.localeCompare(`${b.group}:${b.name}:${b.version}`));

for (const c of composants) {
  const coordonnees = coordonneesDe(c);
  const alternatives = alternativesDe(c);
  const retenue = alternatives.find((a) => a.every((l) => statut(l, c)));
  const declarees = alternatives.map((a) => a.join(' ET ')).join(' OU ');
  if (alternatives.length === 0) {
    ecarts.push(`${coordonnees} : aucune licence déclarée`);
  } else if (!retenue) {
    const refusees = [...new Set(alternatives.flat().filter((l) => !statut(l, c)))];
    ecarts.push(`${coordonnees} : licence non admise sans décision explicite (${refusees.join(', ')}) — ${declarees}`);
  }
  if (c.group && c.version) {
    const archive = cheminArchive(c);
    if (existsSync(archive)) {
      try {
        for (const { nom, texte } of lireEntrees(archive, EST_NOTICE)) {
          avis.push(`--- ${coordonnees} (${nom}) ---\n${texte}`);
        }
      } catch (erreur) {
        ecarts.push(`${coordonnees} : archive illisible (${erreur.message})`);
      }
    } else {
      ecarts.push(`${coordonnees} : archive absente du dépôt Maven local (${archive})`);
    }
  }
  const choix = alternatives.length > 1 && choixDe(c) ? `\n    choix              : ${choixDe(c)}` : '';
  const texteRetenue = retenue
    ? retenue.map((l) => (statut(l, c) === 'admise' ? l : `${l} [${statut(l, c)}]`)).join(' ET ')
    : '(aucune admissible)';
  lignes.push(`${coordonnees}\n    licences déclarées : ${declarees || '(aucune)'}${choix}\n    licence retenue    : ${texteRetenue}`);
}

const entete = [
  'Serveur d\'ARGOS (argos-server) — dépendances tierces livrées et leurs licences',
  '===========================================================================',
  '',
  'Fichier généré, ne pas modifier : « mvn -B -f argos-server/pom.xml -P licences validate »',
  '(inventaire CycloneDX, puis node scripts/verifier-licences-serveur.mjs).',
  'Portée : dépendances compile et runtime de argos-server, directes et transitives (sans les tests).',
  'Avis propres au serveur : argos-server/NOTICE ; textes de licence : argos-server/licenses/',
  '(dans l\'image Docker : NOTICE et Apache-2.0.txt, à côté de ce fichier dans /opt/argos/licenses/).',
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
console.log('Aucune licence absente ni non admise.');
