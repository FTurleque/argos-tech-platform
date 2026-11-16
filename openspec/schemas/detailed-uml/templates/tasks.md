# Tâches : <!-- titre du changement -->

<!--
Format suivi par apply : groupes « ## <n>. <nom> », tâches « - [ ] <n>.<m> <description> ».
Chaque tâche cite les exigences ou scénarios couverts et dit comment vérifier son achèvement.
Chaque groupe livre ses propres tests et sa documentation ; le dernier groupe ne contient que
des vérifications d'intégration.
-->

## 1. <!-- Groupe : fondations ou préparation -->

- [ ] 1.1 <!-- tâche — couvre PRÉFIXE-NN — vérification : commande, test ou artefact -->

## 2. <!-- Groupe : comportement principal -->

- [ ] 2.1 <!-- implémentation — couvre PRÉFIXE-NN-S1, -S2 — vérification : test … passe -->
- [ ] 2.2 <!-- tests du groupe — vérification : … -->
- [ ] 2.3 <!-- documentation durable du groupe : /docs/specifications/<capability-path>.md, diagrammes — vérification : node scripts/verifier-docs.mjs sans erreur -->

## 3. Documentation durable et décisions

- [ ] 3.1 Mettre à jour les sections arc42 et diagrammes concernés, puis l'inventaire /docs/architecture/diagrams/README.md — vérification : node scripts/verifier-docs.mjs sans erreur
- [ ] 3.2 <!-- ADR à rédiger ou mettre à jour (statut Proposé), index arc42 §9 — ou supprimer la tâche si aucune décision structurante -->

## 4. Vérifications d'intégration

- [ ] 4.1 Valider le changement — vérification : openspec validate <!-- change --> --strict réussit
- [ ] 4.2 <!-- vérification transverse (E2E, scénario qualité QS-xx) -->

## Matrice de traçabilité

<!-- Une ligne par scénario. Références réelles uniquement ; écrire « à écrire », « non réalisée », « preuve manquante » quand c'est le cas. -->

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| <!-- PRÉFIXE-NN --> | <!-- PRÉFIXE-NN-S1 --> | <!-- design.md § / DC-n --> | <!-- 2.1 --> | <!-- chemin du test ou commande --> | <!-- à écrire / exécuté le … --> |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (openspec archive <!-- change -->).
- Après archivage, vérifier que la section Purpose des nouvelles specs principales contient le lien vers la fiche durable.
- Après archivage, remplacer dans les fiches les liens vers /openspec/changes/<!-- change -->/ par la spec principale ou le dossier d'archive, puis exécuter node scripts/verifier-docs.mjs (il signale ces liens cassés).
