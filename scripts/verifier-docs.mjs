#!/usr/bin/env node
// Contrôle documentaire ARGOS : liens internes et ancres, chemins du dépôt cités en code en ligne, blocs Mermaid,
// identifiants d'exigences OpenSpec cités dans la documentation.
// Fichiers contrôlés : README.md de la racine et de chaque sous-projet (dossier de premier niveau qui en a un),
// Markdown de docs/ et openspec/ (hors modèles du schéma et changements archivés).
// Usage : node scripts/verifier-docs.mjs [--rendu]
//   --rendu : rend chaque bloc Mermaid avec Mermaid CLI (mmdc) ; mmdc absent est une erreur. La configuration de
//   Puppeteer (scripts/mermaid-puppeteer.json) lance le navigateur sans bac à sable, ce qu'exigent les exécuteurs Linux
//   de l'intégration continue ; PUPPETEER_EXECUTABLE_PATH désigne un navigateur déjà installé.
// Code de sortie : 0 sans erreur, 1 en cas d'erreur.

import { readFileSync, readdirSync, statSync, existsSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { join, dirname, resolve, relative, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const AVEC_RENDU = process.argv.includes('--rendu');
const CONFIGURATION_PUPPETEER = join(RACINE, 'scripts', 'mermaid-puppeteer.json');

// Dossiers parcourus et exclusions. Les modèles du schéma contiennent des
// liens fictifs (<capability-path>) et la copie officielle est en anglais.
const DOSSIERS = ['docs', 'openspec'];
const FICHIERS_RACINE = ['README.md'];
// Dossiers de premier niveau sans README de sous-projet à contrôler.
const HORS_SOUS_PROJETS = new Set(['docs', 'openspec', 'node_modules']);
// Chemins cités entre accents graves : seuls ceux qui commencent par un dossier du dépôt et désignent un fichier (nom
// avec extension) ou un dossier (« / » final) sont contrôlés ; un chemin généré (sortie de build, rapports), un motif
// (joker, gabarit) ou un nom de dépôt de forge (`e2e/chaine-front`) ne l'est pas. Le plan d'audit de docs/audit/ est
// conservé hors du dépôt par le porteur du projet : ses citations ne sont pas contrôlées.
const DOSSIERS_CITES = ['docs', 'openspec', 'argos-server', 'argos-ui', 'argos-desktop', 'e2e', 'scripts', 'deploiement',
  '.github', '.archgate'];
const CHEMIN_GENERE = /(^|\/)(target|node_modules|release|resultats|dist|test-results|playwright-report)(\/|$)/;
const CHEMIN_MOTIF = /[<>*{}$|\s]|\.\.\./;
const CHEMINS_HORS_DEPOT = ['docs/audit/'];
const EXCLUS = [
  join('openspec', 'schemas'),
  join('openspec', 'changes', 'archive'),
];
const TYPES_MERMAID = [
  'flowchart', 'graph', 'sequenceDiagram', 'classDiagram', 'stateDiagram-v2',
  'stateDiagram', 'erDiagram', 'C4Context', 'C4Container', 'C4Component',
  'C4Dynamic', 'C4Deployment', 'gantt', 'timeline', 'mindmap', 'journey',
  'pie', 'gitGraph', 'quadrantChart', 'requirementDiagram', 'xychart-beta',
  'block-beta', 'architecture-beta', 'kanban',
];
const CLOTURE = /^\s*(```|~~~)\s*([\w-]*)/;

const erreurs = [];
const avertissements = [];
const rel = (fichier) => relative(RACINE, fichier).split(sep).join('/');

function listerMarkdown(dossier) {
  const resultat = [];
  if (!existsSync(dossier)) return resultat;
  for (const nom of readdirSync(dossier)) {
    const chemin = join(dossier, nom);
    if (EXCLUS.some((exclu) => relative(RACINE, chemin).startsWith(exclu))) continue;
    if (statSync(chemin).isDirectory()) resultat.push(...listerMarkdown(chemin));
    else if (nom.endsWith('.md')) resultat.push(chemin);
  }
  return resultat;
}

// Lignes hors blocs de code, numéros de ligne conservés. Avec `sansCodeEnLigne`,
// le code entre accents graves est aussi retiré (liens d'exemple non contrôlés).
function lignesHorsCode(texte, sansCodeEnLigne) {
  let dansBloc = false;
  return texte.split('\n').map((ligne) => {
    if (CLOTURE.test(ligne)) { dansBloc = !dansBloc; return ''; }
    if (dansBloc) return '';
    return sansCodeEnLigne ? ligne.replace(/`[^`]*`/g, '') : ligne;
  });
}

// Ancre GitHub : minuscules, ponctuation retirée, espaces remplacés par des tirets.
function ancre(titre) {
  return titre
    .replace(/<[^>]+>/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\p{M}\s_-]/gu, '')
    .replace(/\s/g, '-');
}

const cacheAncres = new Map();
function ancresDe(fichier) {
  if (cacheAncres.has(fichier)) return cacheAncres.get(fichier);
  const occurrences = new Map();
  const ancres = new Set();
  for (const ligne of lignesHorsCode(readFileSync(fichier, 'utf8'), false)) {
    const titre = /^#{1,6}\s+(.*?)\s*#*\s*$/.exec(ligne);
    if (!titre) continue;
    const base = ancre(titre[1]);
    const rang = occurrences.get(base) ?? 0;
    ancres.add(rang === 0 ? base : `${base}-${rang}`);
    occurrences.set(base, rang + 1);
  }
  cacheAncres.set(fichier, ancres);
  return ancres;
}

// Décode un segment d'URL ; un encodage invalide est signalé et lève une erreur.
function decoder(valeur, emplacement, cible) {
  try {
    return decodeURIComponent(valeur);
  } catch {
    erreurs.push(`${emplacement} lien illisible (encodage) : ${cible}`);
    throw new URIError(cible);
  }
}

function resoudreCible(fichier, chemin) {
  if (!chemin) return fichier;
  return chemin.startsWith('/') ? join(RACINE, chemin) : resolve(dirname(fichier), chemin);
}

function verifierAncre(cibleFichier, fragment, emplacement, cible) {
  if (!cibleFichier.endsWith('.md') || !statSync(cibleFichier).isFile()) return;
  if (!ancresDe(cibleFichier).has(fragment)) erreurs.push(`${emplacement} ancre introuvable : ${cible}`);
}

function verifierLien(fichier, emplacement, cible) {
  if (/^(https?:|mailto:)/.test(cible)) return;
  const [cheminBrut, fragmentBrut] = cible.split('#');
  try {
    const cibleFichier = resoudreCible(fichier, decoder(cheminBrut, emplacement, cible));
    if (!existsSync(cibleFichier)) {
      erreurs.push(`${emplacement} lien cassé : ${cible}`);
      return;
    }
    if (fragmentBrut) verifierAncre(cibleFichier, decoder(fragmentBrut, emplacement, cible), emplacement, cible);
  } catch (erreur) {
    if (!(erreur instanceof URIError)) throw erreur;
  }
}

function verifierLiens(fichier, texte) {
  lignesHorsCode(texte, true).forEach((ligne, index) => {
    for (const lien of ligne.matchAll(/!?\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
      verifierLien(fichier, `${rel(fichier)}:${index + 1}`, lien[1]);
    }
  });
}

/**
 * Chemins du dépôt cités entre accents graves dans une ligne : début par un dossier du dépôt (« / » initial admis),
 * ancre et numéro de ligne retirés ; motifs, chemins générés et noms sans extension ni « / » final exclus.
 */
export function cheminsCites(ligne) {
  const chemins = [];
  for (const code of ligne.matchAll(/`([^`]+)`/g)) {
    const brut = code[1].trim().replace(/^\//, '').replace(/#.*$/, '').replace(/:\d+(-\d+)?$/, '');
    const premier = brut.split('/')[0];
    if (!brut.includes('/') || !DOSSIERS_CITES.includes(premier)) continue;
    if (CHEMIN_MOTIF.test(brut) || CHEMIN_GENERE.test(brut)) continue;
    if (CHEMINS_HORS_DEPOT.some((prefixe) => brut.startsWith(prefixe))) continue;
    const dossier = brut.endsWith('/');
    if (!dossier && !/\.[A-Za-z0-9]+$/.test(brut.split('/').pop())) continue;
    chemins.push(dossier ? brut.slice(0, -1) : brut);
  }
  return chemins;
}

function verifierCheminsCites(fichier, texte) {
  lignesHorsCode(texte, false).forEach((ligne, index) => {
    for (const chemin of cheminsCites(ligne)) {
      // Un README de sous-projet cite aussi ses fichiers depuis son propre dossier (`scripts/lancer.mjs` dans e2e/).
      const trouve = existsSync(join(RACINE, chemin)) || existsSync(join(dirname(fichier), chemin));
      if (!trouve) erreurs.push(`${rel(fichier)}:${index + 1} chemin cité introuvable : ${chemin}`);
    }
  });
}

/** README.md des dossiers de premier niveau (sous-projets), triés. */
export function readmeSousProjets(racine) {
  return readdirSync(racine, { withFileTypes: true })
    .filter((entree) => entree.isDirectory() && !entree.name.startsWith('.') && !HORS_SOUS_PROJETS.has(entree.name))
    .map((entree) => join(racine, entree.name, 'README.md'))
    .filter(existsSync)
    .sort();
}

const blocsMermaid = [];
function controlerBloc(fichier, langage, ligneDebut, corps) {
  const emplacement = `${rel(fichier)}:${ligneDebut + 1}`;
  if (['plantuml', 'puml'].includes(langage)) {
    erreurs.push(`${emplacement} bloc PlantUML interdit (ADR-0005)`);
    return;
  }
  if (langage !== 'mermaid') return;
  const premiere = corps.map((ligne) => ligne.trim()).find((ligne) => ligne && !ligne.startsWith('%%'));
  const type = premiere?.split(/\s+/)[0];
  if (!type) erreurs.push(`${emplacement} bloc Mermaid vide`);
  else if (!TYPES_MERMAID.includes(type)) erreurs.push(`${emplacement} type Mermaid inconnu : ${type}`);
  else blocsMermaid.push({ emplacement, type, source: corps.join('\n') });
}

function verifierBlocs(fichier, texte) {
  const lignes = texte.split('\n');
  let debut = -1;
  let langage = null;
  lignes.forEach((ligne, index) => {
    const cloture = CLOTURE.exec(ligne);
    if (!cloture) return;
    if (debut < 0) { debut = index; langage = cloture[2]; return; }
    controlerBloc(fichier, langage, debut, lignes.slice(debut + 1, index));
    debut = -1;
    langage = null;
  });
  if (debut >= 0) erreurs.push(`${rel(fichier)}:${debut + 1} bloc de code non fermé`);
}

// Préfixes déclarés dans le registre des capacités de docs/specifications/README.md.
function prefixesDeclares() {
  const registre = join(RACINE, 'docs', 'specifications', 'README.md');
  if (!existsSync(registre)) return [];
  const lignes = readFileSync(registre, 'utf8').matchAll(/^\|\s*`[a-z0-9/-]+`\s*\|\s*`([A-Z]{2,5})`\s*\|/gm);
  return [...lignes].map((ligne) => ligne[1]);
}

// Une capacité peut avoir une spec principale et des deltas : même dossier de capacité.
const capaciteDe = (fichierSpec) => dirname(fichierSpec).split(sep).pop();

function identifiantsDefinis(fichiersSpec) {
  const exigences = new Map();
  const scenarios = new Set();
  for (const fichier of fichiersSpec) {
    const texte = readFileSync(fichier, 'utf8');
    for (const entete of texte.matchAll(/^###\s+Requirement:\s+([A-Z]{2,5}-\d{2})\b/gm)) {
      const id = entete[1];
      const existant = exigences.get(id);
      if (existant && capaciteDe(existant) !== capaciteDe(fichier)) {
        erreurs.push(`identifiant ${id} défini dans deux capacités : ${rel(existant)} et ${rel(fichier)}`);
      }
      exigences.set(id, fichier);
    }
    for (const entete of texte.matchAll(/^####\s+Scenario:\s+([A-Z]{2,5}-\d{2}-S\d+)\b/gm)) scenarios.add(entete[1]);
  }
  return { exigences, scenarios };
}

function verifierCitations(fichiers, prefixes, { exigences, scenarios }) {
  const motif = new RegExp(`\\b(?:${prefixes.join('|')})-\\d{2}(?:-S\\d+)?\\b`, 'g');
  for (const fichier of fichiers) {
    lignesHorsCode(readFileSync(fichier, 'utf8'), false).forEach((ligne, index) => {
      for (const citation of ligne.matchAll(motif)) {
        const id = citation[0];
        const defini = /-S\d+$/.test(id) ? scenarios.has(id) : exigences.has(id);
        if (!defini) erreurs.push(`${rel(fichier)}:${index + 1} identifiant cité mais non défini : ${id}`);
      }
    });
  }
}

function verifierPrefixesUtilises(prefixes, exigences) {
  for (const prefixe of prefixes) {
    if (![...exigences.keys()].some((id) => id.startsWith(`${prefixe}-`))) {
      avertissements.push(`préfixe ${prefixe} déclaré sans exigence définie`);
    }
  }
}

// Chaque exigence d'un changement actif figure dans la matrice de son tasks.md.
function verifierMatrices(exigences) {
  const changements = join(RACINE, 'openspec', 'changes');
  if (!existsSync(changements)) return;
  for (const nom of readdirSync(changements)) {
    const dossier = join(changements, nom);
    if (nom === 'archive' || !statSync(dossier).isDirectory()) continue;
    const ids = [...exigences.entries()].filter(([, fichier]) => fichier.startsWith(dossier + sep)).map(([id]) => id);
    if (ids.length > 0) verifierMatrice(nom, join(dossier, 'tasks.md'), ids);
  }
}

function verifierMatrice(nomChangement, taches, ids) {
  if (!existsSync(taches)) {
    avertissements.push(`${nomChangement} : tasks.md absent, traçabilité non vérifiable`);
    return;
  }
  const contenu = readFileSync(taches, 'utf8');
  for (const id of ids) {
    if (!new RegExp(`^\\|\\s*${id}\\s*\\|`, 'm').test(contenu)) {
      erreurs.push(`${rel(taches)} exigence ${id} absente de la matrice de traçabilité`);
    }
  }
}

function verifierIdentifiants(fichiers) {
  const prefixes = prefixesDeclares();
  if (prefixes.length === 0) return;
  const fichiersSpec = fichiers.filter((fichier) => /[\\/]specs[\\/].+[\\/]spec\.md$/.test(fichier));
  const definis = identifiantsDefinis(fichiersSpec);
  verifierCitations(fichiers, prefixes, definis);
  verifierPrefixesUtilises(prefixes, definis.exigences);
  verifierMatrices(definis.exigences);
}

// mmdc est un script .cmd sous Windows : l'appel passe par le shell, chemins cités.
function executerMmdc(args) {
  const commande = ['mmdc', ...args.map((arg) => `"${arg}"`)].join(' ');
  return spawnSync(commande, { shell: true, encoding: 'utf8' });
}

// Un lancement de mmdc par fichier Markdown : mmdc rend tous les blocs Mermaid d'une entrée .md dans le même
// navigateur. Si le fichier échoue, ses blocs sont rendus un à un pour nommer le bloc en cause.
function rendreFichier(dossierRendu, nom, blocs) {
  const entree = join(dossierRendu, `${nom}.md`);
  writeFileSync(entree, blocs.map((bloc) => `\`\`\`mermaid\n${bloc.source}\n\`\`\`\n`).join('\n'));
  if (executerMmdc(['-q', '-p', CONFIGURATION_PUPPETEER, '-i', entree, '-o', join(dossierRendu, `${nom}-rendu.md`)]).status === 0) {
    return;
  }
  blocs.forEach((bloc, rang) => {
    const source = join(dossierRendu, `${nom}-${rang}.mmd`);
    writeFileSync(source, bloc.source);
    const rendu = executerMmdc(['-q', '-p', CONFIGURATION_PUPPETEER, '-i', source, '-o', join(dossierRendu, `${nom}-${rang}.svg`)]);
    if (rendu.status !== 0) {
      erreurs.push(`${bloc.emplacement} rendu Mermaid en échec : ${(rendu.stderr || rendu.stdout).trim().split('\n')[0]}`);
    }
  });
}

function rendreMermaid() {
  if (executerMmdc(['--version']).status !== 0) {
    erreurs.push('rendu demandé, Mermaid CLI (mmdc) introuvable — installation : npm install -g @mermaid-js/mermaid-cli');
    return;
  }
  const parFichier = new Map();
  for (const bloc of blocsMermaid) {
    const fichier = bloc.emplacement.replace(/:\d+$/, '');
    parFichier.set(fichier, [...(parFichier.get(fichier) ?? []), bloc]);
  }
  const dossierRendu = mkdtempSync(join(tmpdir(), 'argos-mermaid-'));
  try {
    [...parFichier.values()].forEach((blocs, rang) => rendreFichier(dossierRendu, `f${rang}`, blocs));
  } finally {
    rmSync(dossierRendu, { recursive: true, force: true });
  }
}

// Le rapport s'écrit sur la sortie standard : c'est la sortie attendue de cet outil en ligne de commande.
function afficherRapport(nombreFichiers) {
  const types = [...new Set(blocsMermaid.map((bloc) => bloc.type))].join(', ');
  const lignes = [
    `Fichiers Markdown contrôlés : ${nombreFichiers}`,
    `Blocs Mermaid : ${blocsMermaid.length} (${types})`,
    `Rendu Mermaid : ${AVEC_RENDU ? 'demandé' : 'non demandé (option --rendu)'}`,
    ...avertissements.map((message) => `AVERTISSEMENT ${message}`),
    ...erreurs.map((message) => `ERREUR ${message}`),
    erreurs.length === 0 ? 'Résultat : aucune erreur' : `Résultat : ${erreurs.length} erreur(s)`,
  ];
  process.stdout.write(`${lignes.join('\n')}\n`);
}

function controler() {
  const fichiers = [
    ...FICHIERS_RACINE.map((fichier) => join(RACINE, fichier)).filter(existsSync),
    ...readmeSousProjets(RACINE),
    ...DOSSIERS.flatMap((dossier) => listerMarkdown(join(RACINE, dossier))),
  ];
  for (const fichier of fichiers) {
    const texte = readFileSync(fichier, 'utf8');
    verifierLiens(fichier, texte);
    verifierCheminsCites(fichier, texte);
    verifierBlocs(fichier, texte);
  }
  verifierIdentifiants(fichiers);
  if (AVEC_RENDU) rendreMermaid();
  afficherRapport(fichiers.length);
  process.exit(erreurs.length === 0 ? 0 : 1);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) controler();
