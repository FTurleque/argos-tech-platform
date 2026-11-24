#!/usr/bin/env node
// Contrôle de parité de la configuration de déploiement (EXP-07, changement completer-configuration-conteneurs) :
//   - toute variable ${ARGOS_*} lue par argos-server/src/main/resources/application.properties en production
//     (lignes sans profil ou en %prod.) est transmise par le bloc environment du service argos de compose.yaml
//     et figure dans .env.example ;
//   - toute variable interpolée par compose.yaml (${NOM}, ${NOM:-défaut}, ${NOM:?message}) figure dans .env.example.
// Une variable figure dans .env.example si une ligne « NOM=… » ou « # NOM=… » la déclare (réglage facultatif commenté).
// Contrôle du durcissement (EXP-12, EXP-16, changement durcir-conteneurs-de-service) :
//   - le fragment x-durcissement de compose.yaml déclare read_only: true, cap_drop: ALL et no-new-privileges:true ;
//   - chaque service le fusionne (« <<: *durcissement ») et ne déclare ni cap_add, ni read_only, ni cap_drop, ni
//     security_opt qui le contrediraient ;
//   - chaque option de construction KC_* du Dockerfile de l'image Keycloak a la même valeur dans le bloc environment
//     du service keycloak (« start --optimized » refuse ou signale une option d'exécution qui la contredit).
// Usage : node scripts/verifier-configuration.mjs
// Code de sortie : 0 sans écart, sans sortie ; 1 sinon, une ligne par écart sur la sortie d'erreur, nommant la variable
// et le fichier où elle manque, le service et la clé de durcissement en cause, ou l'option de Keycloak divergente.

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');

export const FICHIER_PROPRIETES = 'argos-server/src/main/resources/application.properties';
export const FICHIER_COMPOSE = 'compose.yaml';
export const FICHIER_MODELE = '.env.example';
export const SERVICE_APPLICATIF = 'argos';

const VARIABLE_ARGOS = /\$\{(ARGOS_[A-Z0-9_]+)/g;
// « $${NOM} » est un dollar échappé (variable lue par le conteneur, pas par Compose) : il est ignoré.
const INTERPOLATION_COMPOSE = /(?<!\$)\$\{([A-Za-z_]\w*)/g;
const PROFIL_HORS_PRODUCTION = /^%(?!prod\.)[^.]+\./;
const DECLARATION_MODELE = /^#?\s*([A-Za-z_]\w*)=/;
const INDENTATION_SERVICE = 2;
const INDENTATION_CLE_SERVICE = 4;
const INDENTATION_VARIABLE = 6;
export const FRAGMENT_DURCISSEMENT = 'x-durcissement';
const FUSION_DURCISSEMENT = '<<: *durcissement';
const DURCISSEMENT_ATTENDU = [
  { cle: 'read_only', valeur: 'true', libelle: 'read_only: true' },
  { cle: 'cap_drop', element: 'ALL', libelle: 'cap_drop: [ALL]' },
  { cle: 'security_opt', element: 'no-new-privileges:true', libelle: 'security_opt: [no-new-privileges:true]' },
];
export const FICHIER_IMAGE_KEYCLOAK = 'deploiement/keycloak/Dockerfile';
export const SERVICE_KEYCLOAK = 'keycloak';
// Options de construction de Keycloak figées par « kc.sh build » : ENV KC_*=valeur, sur une ou plusieurs lignes.
const OPTION_CONSTRUCTION_KEYCLOAK = /\b(KC_[A-Z0-9_]+)=(\S+)/g;
const CLES_INTERDITES_DANS_SERVICE = new Set(['cap_add', 'read_only', 'cap_drop', 'security_opt']);

function lignesUtiles(texte) {
  return texte.split(/\r?\n/).filter((ligne) => ligne.trim() !== '' && !ligne.trim().startsWith('#'));
}

function indentation(ligne) {
  return ligne.length - ligne.trimStart().length;
}

/** Variables ${ARGOS_*} lues par application.properties sans profil ou en profil %prod. */
export function variablesProduction(proprietes) {
  const variables = new Set();
  for (const ligne of lignesUtiles(proprietes)) {
    if (PROFIL_HORS_PRODUCTION.test(ligne.trim())) continue;
    for (const correspondance of ligne.matchAll(VARIABLE_ARGOS)) variables.add(correspondance[1]);
  }
  return variables;
}

/** Clés du bloc environment d'un service de compose.yaml (forme « CLE: valeur »). */
export function variablesService(compose, service) {
  return new Set(environnementService(compose, service).keys());
}

/** Bloc environment d'un service de compose.yaml : valeur de chaque clé, guillemets retirés. */
export function environnementService(compose, service) {
  const variables = new Map();
  let dansService = false;
  let dansEnvironnement = false;
  for (const ligne of lignesUtiles(compose)) {
    const decalage = indentation(ligne);
    const contenu = ligne.trim();
    if (decalage <= INDENTATION_SERVICE) {
      dansService = decalage === INDENTATION_SERVICE && contenu === `${service}:`;
      dansEnvironnement = false;
      continue;
    }
    if (!dansService) continue;
    if (decalage === INDENTATION_CLE_SERVICE) {
      dansEnvironnement = contenu === 'environment:';
      continue;
    }
    const cle = cleEtValeur(contenu);
    if (dansEnvironnement && decalage === INDENTATION_VARIABLE && cle) variables.set(cle.nom, cle.valeur);
  }
  return variables;
}

/** Ligne « cle: valeur » découpée sans expression à retour arrière : nom de la clé et valeur sans guillemets. */
function cleEtValeur(contenu) {
  const cle = /^[A-Za-z_]\w*:/.exec(contenu);
  if (!cle) return null;
  return { nom: cle[0].slice(0, -1), valeur: sansGuillemets(contenu.slice(cle[0].length)) };
}

function sansGuillemets(valeur) {
  return valeur.trim().replace(/^(["'])(.*)\1$/, '$2');
}

/** Variables interpolées par compose.yaml. */
export function variablesInterpolees(compose) {
  const variables = new Set();
  for (const ligne of lignesUtiles(compose)) {
    for (const correspondance of ligne.matchAll(INTERPOLATION_COMPOSE)) variables.add(correspondance[1]);
  }
  return variables;
}

/** Variables déclarées par .env.example, réglages commentés compris. */
export function variablesModele(modele) {
  const variables = new Set();
  for (const ligne of modele.split(/\r?\n/)) {
    const declaration = DECLARATION_MODELE.exec(ligne.trim());
    if (declaration) variables.add(declaration[1]);
  }
  return variables;
}

/** Lignes utiles d'un bloc de premier niveau de compose.yaml (« nom: » en colonne 0), sans sa ligne de titre. */
function blocPremierNiveau(compose, nom) {
  const bloc = [];
  let dansBloc = false;
  for (const ligne of lignesUtiles(compose)) {
    if (indentation(ligne) === 0) {
      dansBloc = ligne.trim().startsWith(`${nom}:`);
      continue;
    }
    if (dansBloc) bloc.push(ligne);
  }
  return bloc;
}

/** Clés de premier niveau (indentation 4) de chaque service de compose.yaml, par nom de service. */
export function clesServices(compose) {
  const services = new Map();
  let courant = null;
  for (const ligne of blocPremierNiveau(compose, 'services')) {
    const decalage = indentation(ligne);
    const contenu = ligne.trim();
    if (decalage === INDENTATION_SERVICE) {
      courant = contenu.replace(/:$/, '');
      services.set(courant, []);
    } else if (decalage === INDENTATION_CLE_SERVICE && courant !== null) {
      services.get(courant).push(contenu);
    }
  }
  return services;
}

/**
 * Clés d'un fragment de premier niveau : valeur scalaire (« cle: valeur ») et éléments de liste (« - element »)
 * placés sous chaque clé.
 */
export function structureFragment(compose, nom) {
  const cles = new Map();
  let courante = null;
  for (const ligne of blocPremierNiveau(compose, nom)) {
    const contenu = ligne.trim();
    const cle = cleEtValeur(contenu);
    if (contenu.startsWith('-') && courante !== null) {
      cles.get(courante).elements.push(sansGuillemets(contenu.slice(1)));
    } else if (cle && indentation(ligne) === INDENTATION_SERVICE) {
      courante = cle.nom;
      cles.set(courante, { valeur: cle.valeur, elements: [] });
    }
  }
  return cles;
}

/** Écarts de durcissement, triés : fragment incomplet, service sans fusion, clé qui contredit le fragment. */
export function ecartsDurcissement(compose) {
  const ecarts = [];
  const fragment = structureFragment(compose, FRAGMENT_DURCISSEMENT);
  for (const { cle, valeur, element, libelle } of DURCISSEMENT_ATTENDU) {
    const declaree = fragment.get(cle);
    const conforme = declaree !== undefined
      && (valeur === undefined || declaree.valeur === valeur)
      && (element === undefined || declaree.elements.includes(element));
    if (!conforme) ecarts.push(`${FRAGMENT_DURCISSEMENT} de ${FICHIER_COMPOSE} : « ${libelle} » absent`);
  }
  for (const [service, cles] of clesServices(compose)) {
    if (!cles.includes(FUSION_DURCISSEMENT)) {
      ecarts.push(`service ${service} de ${FICHIER_COMPOSE} : « ${FUSION_DURCISSEMENT} » absent`);
    }
    for (const cle of cles) {
      const nom = /^([A-Za-z_]\w*):/.exec(cle)?.[1];
      if (CLES_INTERDITES_DANS_SERVICE.has(nom)) {
        ecarts.push(`service ${service} de ${FICHIER_COMPOSE} : « ${nom} » redéfini, le durcissement vient de `
          + FRAGMENT_DURCISSEMENT);
      }
    }
  }
  return ecarts.sort((premier, second) => premier.localeCompare(second, 'fr'));
}

/** Options de construction KC_* déclarées par les instructions ENV du Dockerfile de l'image Keycloak. */
export function optionsConstructionKeycloak(dockerfile) {
  const options = new Map();
  const instructions = dockerfile.replace(/\\\r?\n/g, ' ').split(/\r?\n/);
  for (const instruction of instructions) {
    if (!/^\s*ENV\s/.test(instruction)) continue;
    for (const option of instruction.matchAll(OPTION_CONSTRUCTION_KEYCLOAK)) options.set(option[1], option[2]);
  }
  return options;
}

/**
 * Écarts, triés, entre les options de construction de l'image Keycloak et le bloc environment du service keycloak :
 * « start --optimized » échoue ou avertit si l'exécution les contredit.
 */
export function ecartsOptionsKeycloak(dockerfile, compose) {
  const execution = environnementService(compose, SERVICE_KEYCLOAK);
  const ecarts = [];
  for (const [option, valeur] of optionsConstructionKeycloak(dockerfile)) {
    if (execution.get(option) !== valeur) {
      ecarts.push(`${option} : ${valeur} dans ${FICHIER_IMAGE_KEYCLOAK}, `
        + `${execution.get(option) ?? 'absente'} dans le service ${SERVICE_KEYCLOAK} de ${FICHIER_COMPOSE}`);
    }
  }
  return ecarts.sort((premier, second) => premier.localeCompare(second, 'fr'));
}

/** Écarts de parité, triés : une phrase par variable manquante, avec le fichier où elle manque. */
export function ecartsParite({ proprietes, compose, modele }) {
  const transmises = variablesService(compose, SERVICE_APPLICATIF);
  const documentees = variablesModele(modele);
  const ecarts = [];
  for (const variable of variablesProduction(proprietes)) {
    if (!transmises.has(variable)) {
      ecarts.push(`${variable} : lue par ${FICHIER_PROPRIETES}, absente du bloc environment du service `
        + `${SERVICE_APPLICATIF} de ${FICHIER_COMPOSE}`);
    }
    if (!documentees.has(variable)) {
      ecarts.push(`${variable} : lue par ${FICHIER_PROPRIETES}, absente de ${FICHIER_MODELE}`);
    }
  }
  for (const variable of variablesInterpolees(compose)) {
    if (!documentees.has(variable)) {
      ecarts.push(`${variable} : interpolée par ${FICHIER_COMPOSE}, absente de ${FICHIER_MODELE}`);
    }
  }
  return [...new Set(ecarts)].sort((premier, second) => premier.localeCompare(second, 'fr'));
}

/** Écarts de parité, de durcissement puis d'options de Keycloak des fichiers du dépôt. */
export function ecartsDepot(racine = RACINE) {
  const lire = (fichier) => readFileSync(join(racine, fichier), 'utf8');
  const compose = lire(FICHIER_COMPOSE);
  return [
    ...ecartsParite({ proprietes: lire(FICHIER_PROPRIETES), compose, modele: lire(FICHIER_MODELE) }),
    ...ecartsDurcissement(compose),
    ...ecartsOptionsKeycloak(lire(FICHIER_IMAGE_KEYCLOAK), compose),
  ];
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  const ecarts = ecartsDepot();
  for (const ecart of ecarts) console.error(ecart);
  process.exit(ecarts.length === 0 ? 0 : 1);
}
