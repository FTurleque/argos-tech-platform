# Tâches : suivre les évolutions réglementaires avec validation par un expert

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Arbitrages

- [x] 1.1 Faire vérifier les conditions d'usage de l'API Légifrance pour un produit diffusé (Q-1) — couvre REG-01 — vérification : conclusion inscrite dans la fiche `veille-reglementaire` (décidé le 2026-11-17 : LEGI/JORF sous Licence Ouverte)

## 2. Collecte et qualification

- [ ] 2.1 Créer le connecteur Légifrance avec repli LEGI/JORF et l'unicité par identifiant officiel — couvre REG-01-S1, REG-01-S2 — vérification : tests de contrat et de rejeu passent
- [ ] 2.2 Créer le dictionnaire métier par espace et la qualification déterministe — couvre REG-02-S1 — vérification : tests unitaires passent
- [ ] 2.3 Proposer un domaine par l'IA en cas de doute, marqué — couvre REG-02-S2 — vérification : test d'intégration passe
- [ ] 2.4 Extraire les dates de publication et d'effet — couvre REG-03-S1, REG-03-S2 — vérification : tests unitaires passent

## 3. Fiches, validation et suivi

- [ ] 3.1 Préparer la fiche via la passerelle IA — couvre REG-04-S1 — vérification : test d'intégration passe
- [ ] 3.2 Implémenter la validation, l'historique et la visibilité — couvre REG-05-S1, REG-05-S2 — vérification : tests d'intégration passent
- [ ] 3.3 Implémenter le cycle de vie et ses transitions — couvre REG-06-S1, REG-06-S2 — vérification : tests unitaires passent
- [ ] 3.4 Calendrier, rappels et lien vers un outil de suivi — couvre REG-07-S1, REG-08-S1 — vérification : tests d'intégration passent

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour la fiche `veille-reglementaire`, les sections arc42 §6, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Mettre à jour ADR-0018 (expert métier, domaines par espace) par un nouvel ADR au statut Proposé — vérification : index `/docs/architecture/adr/README.md` et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate suivre-veille-reglementaire --strict` réussit
- [ ] 5.2 Parcours : décret de test collecté, rattaché au domaine, fiche préparée, corrigée et validée par l'expert, visible d'un utilisateur du domaine et via MCP — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| REG-01 | REG-01-S1 | DC-1 | 1.1, 2.1 | test à écrire | à écrire |
| REG-01 | REG-01-S2 | DC-1 | 1.1, 2.1 | test à écrire | à écrire |
| REG-02 | REG-02-S1 | DC-1 | 2.2 | test à écrire | à écrire |
| REG-02 | REG-02-S2 | DC-1 | 2.3 | test à écrire | à écrire |
| REG-03 | REG-03-S1 | DC-1 | 2.4 | test à écrire | à écrire |
| REG-03 | REG-03-S2 | DC-1 | 2.4 | test à écrire | à écrire |
| REG-04 | REG-04-S1 | DC-1 | 3.1 | test à écrire | à écrire |
| REG-05 | REG-05-S1 | DC-1, DC-2 | 3.2 | test à écrire | à écrire |
| REG-05 | REG-05-S2 | DC-1, DC-2 | 3.2 | test à écrire | à écrire |
| REG-06 | REG-06-S1 | DC-2 | 3.3 | test à écrire | à écrire |
| REG-06 | REG-06-S2 | DC-2 | 3.3 | test à écrire | à écrire |
| REG-07 | REG-07-S1 | DC-1 | 3.4 | test à écrire | à écrire |
| REG-08 | REG-08-S1 | DC-2 | 3.4 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive suivre-veille-reglementaire`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/suivre-veille-reglementaire/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
