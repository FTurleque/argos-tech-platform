#!/usr/bin/env node
// Contrôle de parité de la configuration de déploiement (EXP-07, changement completer-configuration-conteneurs) :
//   - toute variable ${ARGOS_*} lue par argos-server/src/main/resources/application.properties en production
//     (lignes sans profil ou en %prod.) est transmise par le bloc environment du service argos de compose.yaml
//     et figure dans .env.example ;
//   - toute variable interpolée par compose.yaml (${NOM}, ${NOM:-défaut}, ${NOM:?message}) figure dans .env.example.
// Une variable figure dans .env.example si une ligne « NOM=… » ou « # NOM=… » la déclare (réglage facultatif commenté).
// Usage : node scripts/verifier-configuration.mjs
// Code de sortie : 0 à parité, 1 sinon (une ligne par écart, nommant la variable et le fichier où elle manque).

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
const INTERPOLATION_COMPOSE = /(?<!\$)\$\{([A-Za-z_][A-Za-z0-9_]*)/g;
const PROFIL_HORS_PRODUCTION = /^%(?!prod\.)[^.]+\./;
const DECLARATION_MODELE = /^#?\s*([A-Za-z_][A-Za-z0-9_]*)=/;
const INDENTATION_SERVICE = 2;
const INDENTATION_CLE_SERVICE = 4;
const INDENTATION_VARIABLE = 6;

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
  const variables = new Set();
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
    const cle = /^([A-Za-z_][A-Za-z0-9_]*):/.exec(contenu);
    if (dansEnvironnement && decalage === INDENTATION_VARIABLE && cle) variables.add(cle[1]);
  }
  return variables;
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
  return [...new Set(ecarts)].sort();
}

/** Écarts de parité des fichiers du dépôt. */
export function ecartsDepot(racine = RACINE) {
  const lire = (fichier) => readFileSync(join(racine, fichier), 'utf8');
  return ecartsParite({ proprietes: lire(FICHIER_PROPRIETES), compose: lire(FICHIER_COMPOSE), modele: lire(FICHIER_MODELE) });
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  const ecarts = ecartsDepot();
  for (const ecart of ecarts) console.error(ecart);
  if (ecarts.length === 0) console.log('Configuration à parité : application.properties, compose.yaml et .env.example.');
  process.exit(ecarts.length === 0 ? 0 : 1);
}
