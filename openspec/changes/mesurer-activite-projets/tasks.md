# Tâches : mesurer l'activité factuelle des projets (Project Intelligence)

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Collecte

- [ ] 1.1 Lire les API GitHub et GitLab en lecture seule avec gestion des quotas — couvre PIN-05-S1 — vérification : tests de contrat passent
- [ ] 1.2 Agréger par projet et période sans identité de personne — couvre PIN-01-S1, PIN-03-S1 — vérification : tests d'intégration passent ; revue du schéma

## 2. Restitution

- [ ] 2.1 Calculer les avancements avec dénominateur explicite — couvre PIN-02-S1, PIN-02-S2 — vérification : tests unitaires passent
- [ ] 2.2 Produire la synthèse IA marquée — couvre PIN-04-S1 — vérification : test d'intégration passe

## 3. Documentation durable et décisions

- [ ] 3.1 Mettre à jour la fiche `activite-projets`, les sections arc42 §6.2, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [ ] 4.1 Valider le changement — vérification : `openspec validate mesurer-activite-projets --strict` réussit
- [ ] 4.2 Parcours : projet relié à un dépôt de test, indicateurs sur 30 jours, jalon « 27 sur 30 », synthèse marquée, aucun indicateur par personne — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| PIN-01 | PIN-01-S1 | Composants | 1.2 | test à écrire | à écrire |
| PIN-02 | PIN-02-S1 | Composants | 2.1 | test à écrire | à écrire |
| PIN-02 | PIN-02-S2 | Composants | 2.1 | test à écrire | à écrire |
| PIN-03 | PIN-03-S1 | Composants | 1.2 | test à écrire | à écrire |
| PIN-04 | PIN-04-S1 | Composants | 2.2 | test à écrire | à écrire |
| PIN-05 | PIN-05-S1 | Composants | 1.1 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive mesurer-activite-projets`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/mesurer-activite-projets/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
