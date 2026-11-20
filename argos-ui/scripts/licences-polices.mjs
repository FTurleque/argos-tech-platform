#!/usr/bin/env node
// Régénère public/licences-polices.txt : avis de droit d'auteur de chaque police embarquée et texte de
// la SIL Open Font License 1.1, livrés avec l'IHM (le build copie public/ tel quel), comme l'exige
// l'OFL pour toute redistribution des fichiers de police.
// Usage : node scripts/licences-polices.mjs (après npm ci). Contrôlé par tests/polices.test.mjs.
import { readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const FONTSOURCE = join(RACINE, 'node_modules', '@fontsource')
const SEPARATEUR = 'This Font Software is licensed under the SIL Open Font License, Version 1.1.'

const familles = readdirSync(FONTSOURCE).sort()
let texteOfl = null
const avis = familles.map((famille) => {
  const paquet = JSON.parse(readFileSync(join(FONTSOURCE, famille, 'package.json'), 'utf8'))
  const licence = readFileSync(join(FONTSOURCE, famille, 'LICENSE'), 'utf8').replace(/\r\n/g, '\n')
  const position = licence.indexOf(SEPARATEUR)
  if (position < 0 || paquet.license !== 'OFL-1.1') {
    throw new Error(`${paquet.name} : licence SIL OFL 1.1 introuvable`)
  }
  texteOfl ??= licence.slice(position).trim()
  return [`${paquet.name} ${paquet.version} (${paquet.license}) — ${paquet.homepage}`, licence.slice(0, position).trim()].join('\n')
})

const contenu = [
  'Polices embarquées dans l’IHM d’ARGOS (argos-ui) — avis de droit d’auteur et licence',
  '',
  'Fichiers de police fournis par les paquets npm @fontsource/* (sous-ensemble latin), servis par',
  'l’application elle-même : aucun service de polices externe n’est appelé.',
  '',
  ...avis.flatMap((avisPolice) => [avisPolice, '']),
  'Ces polices sont distribuées sous la licence suivante :',
  '',
  texteOfl,
  '',
].join('\n')

writeFileSync(join(RACINE, 'public', 'licences-polices.txt'), contenu, 'utf8')
console.log(`public/licences-polices.txt : ${familles.length} polices`)
