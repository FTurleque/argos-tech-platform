# Tâches : rechercher et filtrer la veille, et enregistrer des vues

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Recherche

- [ ] 1.1 Créer l'index plein texte et la requête de recherche sous RLS — couvre RCH-01-S1, RCH-01-S2, RCH-03-S1 — vérification : tests d'intégration passent
- [ ] 1.2 Ajouter les filtres combinables — couvre RCH-02-S1 — vérification : tests d'intégration passent
- [ ] 1.3 Brancher l'outil MCP `search_signals` sur la même requête — couvre RCH-01, RCH-03 — vérification : test de contrat MCP passe

## 2. Vues et performance

- [ ] 2.1 Enregistrer, rappeler et supprimer des vues personnelles — couvre RCH-04-S1, RCH-04-S2 — vérification : tests d'intégration passent
- [ ] 2.2 Mesurer le temps de réponse sur le volume cible — couvre RCH-05-S1 — vérification : rapport QS-13 versionné

## 3. Documentation durable et décisions

- [ ] 3.1 Mettre à jour la fiche `recherche-veille`, les sections arc42 §8.10, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [ ] 4.1 Valider le changement — vérification : `openspec validate rechercher-et-filtrer --strict` réussit
- [ ] 4.2 Parcours : recherche « Oracle » filtrée sur un projet et 30 jours, enregistrée en vue, rappelée depuis le desktop — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| RCH-01 | RCH-01-S1 | Composants | 1.1, 1.3 | test à écrire | à écrire |
| RCH-01 | RCH-01-S2 | Composants | 1.1, 1.3 | test à écrire | à écrire |
| RCH-02 | RCH-02-S1 | Composants | 1.2 | test à écrire | à écrire |
| RCH-03 | RCH-03-S1 | Composants | 1.1, 1.3 | test à écrire | à écrire |
| RCH-04 | RCH-04-S1 | Composants | 2.1 | test à écrire | à écrire |
| RCH-04 | RCH-04-S2 | Composants | 2.1 | test à écrire | à écrire |
| RCH-05 | RCH-05-S1 | Performance | 2.2 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive rechercher-et-filtrer`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/rechercher-et-filtrer/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
