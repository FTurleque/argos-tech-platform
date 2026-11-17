// Registre de la couche mise en page (ADR-0023 point 1).
//
// Une mise en page est un composant qui reçoit `ProprietesMiseEnPage` (modele.ts) et affiche
// chaque élément avec `MentionsObligatoires`. Elle n'importe ni le client API ni aucune logique de
// droits : `tests/architecture.test.mjs` le vérifie. Le composant affiché est choisi par
// `MiseEnPage.vue` (selection.ts).
// Ajouter une mise en page = ajouter un dossier (composant, palette contrôlée par
// tests/contrastes.test.mjs, polices embarquées enregistrées dans argos-ui/NOTICE) et une entrée
// ici, puis la publier au catalogue du serveur après l'audit d'accessibilité (AFF-05).
import type { Component } from 'vue'
import MiseEnPageClassique from './classique/MiseEnPageClassique.vue'
import MiseEnPageFantaisie from './fantaisie/MiseEnPageFantaisie.vue'
import MiseEnPageGazette from './gazette/MiseEnPageGazette.vue'
import MiseEnPageJournal from './journal/MiseEnPageJournal.vue'

export const MISES_EN_PAGE: Readonly<Record<string, Component>> = {
  classique: MiseEnPageClassique,
  journal: MiseEnPageJournal,
  fantaisie: MiseEnPageFantaisie,
  gazette: MiseEnPageGazette,
}

export const IDENTIFIANTS_MISES_EN_PAGE: readonly string[] = Object.keys(MISES_EN_PAGE)
