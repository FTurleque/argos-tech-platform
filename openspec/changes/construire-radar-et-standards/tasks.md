# Tâches : tenir le référentiel des standards et le radar des sorties

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Référentiel

- [ ] 1.1 Créer le référentiel par espace, ses validations et son historique — couvre RAD-01-S1, RAD-01-S2 — vérification : tests d'intégration passent
- [ ] 1.2 Appliquer la gouvernance et les suggestions en attente — couvre RAD-02-S1, RAD-02-S2 — vérification : tests d'intégration passent
- [ ] 1.3 Annoncer les changements de standard — couvre RAD-06-S1 — vérification : test d'intégration passe

## 2. Radar

- [ ] 2.1 Créer le connecteur endoflife.date et les échéances par horizon — couvre RAD-03-S1 — vérification : tests de contrat et d'intégration passent
- [ ] 2.2 Envoyer les rappels J-30, J-7, J0 — couvre RAD-04-S1 — vérification : test avec horloge simulée passe
- [ ] 2.3 Créer les impacts « dette de version » — couvre RAD-05-S1 — vérification : test d'intégration passe

## 3. Documentation durable et décisions

- [ ] 3.1 Mettre à jour la fiche `radar-standards`, les sections arc42 §8, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [ ] 4.1 Valider le changement — vérification : `openspec validate construire-radar-et-standards --strict` réussit
- [ ] 4.2 Parcours : référentiel d'un espace, version en fin de support dans l'inventaire, impact « dette de version » et rappel, affichés dans le radar — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| RAD-01 | RAD-01-S1 | Composants | 1.1 | test à écrire | à écrire |
| RAD-01 | RAD-01-S2 | Composants | 1.1 | test à écrire | à écrire |
| RAD-02 | RAD-02-S1 | Composants | 1.2 | test à écrire | à écrire |
| RAD-02 | RAD-02-S2 | Composants | 1.2 | test à écrire | à écrire |
| RAD-03 | RAD-03-S1 | DC-1 | 2.1 | test à écrire | à écrire |
| RAD-04 | RAD-04-S1 | DC-1 | 2.2 | test à écrire | à écrire |
| RAD-05 | RAD-05-S1 | DC-1 | 2.3 | test à écrire | à écrire |
| RAD-06 | RAD-06-S1 | Composants | 1.3 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive construire-radar-et-standards`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/construire-radar-et-standards/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
