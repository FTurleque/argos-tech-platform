# Tâches : collecter la veille RSS/Atom et les pages web surveillées

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Arbitrages

- [x] 1.1 Faire trancher services séparés ou collecteur intégré (Q-1) — couvre WEB-01, WEB-02 — vérification : choix inscrit dans la fiche `veille-rss-web` et ADR rédigé (décidé le 2026-11-16)

## 2. Sources et sécurité

- [ ] 2.1 Valider les adresses (flux valide, plages interdites, redirections, délais) — couvre WEB-01-S2, WEB-03-S1, WEB-03-S2 — vérification : tests de sécurité passent
- [ ] 2.2 Créer les sources d'espace et personnelles — couvre WEB-01-S1 — vérification : tests d'intégration passent

## 3. Collecte et restitution

- [ ] 3.1 Collecter flux et pages surveillées — couvre WEB-01-S1, WEB-02-S1 — vérification : tests d'intégration passent
- [ ] 3.2 Dédupliquer et citer toutes les sources — couvre WEB-05-S1 — vérification : test d'intégration passe
- [ ] 3.3 Marquer le contenu externe et tester l'injection — couvre WEB-04-S1 — vérification : test de l'article piégé passe
- [ ] 3.4 Rapprocher par nom au niveau « à vérifier » — couvre WEB-06-S1 — vérification : test d'intégration passe

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour la fiche `veille-rss-web`, les sections arc42 §8.3, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Rédiger l'ADR du choix de collecteur (statut Proposé) — vérification : index `/docs/architecture/adr/README.md` et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate collecter-veille-rss-web --strict` réussit
- [ ] 5.2 Parcours : ajout d'un flux, refus d'une adresse privée, article reçu par deux flux restitué une fois, article piégé sans effet — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| WEB-01 | WEB-01-S1 | DC-1 | 1.1, 2.2, 3.1 | test à écrire | à écrire |
| WEB-01 | WEB-01-S2 | DC-1 | 1.1, 2.1 | test à écrire | à écrire |
| WEB-02 | WEB-02-S1 | DC-1 | 1.1, 3.1 | test à écrire | à écrire |
| WEB-03 | WEB-03-S1 | DC-1 | 2.1 | test à écrire | à écrire |
| WEB-03 | WEB-03-S2 | DC-1 | 2.1 | test à écrire | à écrire |
| WEB-04 | WEB-04-S1 | DC-1 | 3.3 | test à écrire | à écrire |
| WEB-05 | WEB-05-S1 | DC-1 | 3.2 | test à écrire | à écrire |
| WEB-06 | WEB-06-S1 | DC-1 | 3.4 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive collecter-veille-rss-web`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/collecter-veille-rss-web/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
