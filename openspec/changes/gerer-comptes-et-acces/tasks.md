# Tâches : gérer les comptes, les espaces et le contrôle d'accès

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Arbitrages

- [x] 1.1 Faire choisir le fournisseur d'identité (Q-1) — couvre ACC-01, ACC-02 — vérification : choix inscrit dans la fiche `comptes-acces` (décidé le 2026-11-16)
- [x] 1.2 Faire décider l'inscription ouverte ou sur invitation et le profil par défaut (Q-2, Q-3) — couvre ACC-02 — vérification : décisions inscrites dans la fiche (décidé le 2026-11-16)

## 2. Authentification et comptes

- [ ] 2.1 Brancher la validation OIDC dans `API & Security` et protéger toutes les routes sauf santé et identité du serveur — couvre ACC-01-S1, ACC-01-S2 — vérification : tests d'intégration passent
- [ ] 2.2 Créer les migrations `compte` et la création à la première connexion — couvre ACC-02-S1, ACC-02-S2 — vérification : tests d'intégration avec IdP de test passent

## 3. Espaces, rôles et cloisonnement

- [ ] 3.1 Créer les migrations `espace`, `appartenance`, `invitation` et l'API associée — couvre ACC-03, ACC-05 — vérification : test de contrat OpenAPI passe
- [ ] 3.2 Implémenter rôles, invitations et révocation — couvre ACC-03-S1, ACC-03-S2, ACC-05-S1, ACC-05-S2 — vérification : tests d'intégration passent
- [ ] 3.3 Activer la RLS et la transmission de l'espace courant ; test sans contexte — couvre ACC-04-S1, ACC-04-S2 — vérification : tests inter-espaces et RLS passent ; QS-02 exécuté
- [ ] 3.4 Journaliser les refus dans `audit` — couvre ACC-06-S1 — vérification : test d'intégration passe

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour la fiche `comptes-acces`, les sections arc42 §8.1, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Rédiger un ADR complétant ADR-0008 pour un fournisseur d'identité auto-hébergé (statut Proposé) — vérification : index `/docs/architecture/adr/README.md` et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate gerer-comptes-et-acces --strict` réussit
- [ ] 5.2 Parcours : première connexion, création d'un espace, invitation d'un lecteur, refus d'accès à un autre espace journalisé — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| ACC-01 | ACC-01-S1 | DC-1 | 1.1, 2.1 | test à écrire | à écrire |
| ACC-01 | ACC-01-S2 | DC-1 | 1.1, 2.1 | test à écrire | à écrire |
| ACC-02 | ACC-02-S1 | DC-1 | 1.1, 1.2, 2.2 | test à écrire | à écrire |
| ACC-02 | ACC-02-S2 | DC-1 | 1.1, 1.2, 2.2 | test à écrire | à écrire |
| ACC-03 | ACC-03-S1 | DC-1 | 3.1, 3.2 | test à écrire | à écrire |
| ACC-03 | ACC-03-S2 | DC-1 | 3.1, 3.2 | test à écrire | à écrire |
| ACC-04 | ACC-04-S1 | DC-1, DC-2 | 3.3 | test à écrire | à écrire |
| ACC-04 | ACC-04-S2 | DC-1, DC-2 | 3.3 | test à écrire | à écrire |
| ACC-05 | ACC-05-S1 | DC-1 | 3.1, 3.2 | test à écrire | à écrire |
| ACC-05 | ACC-05-S2 | DC-1 | 3.1, 3.2 | test à écrire | à écrire |
| ACC-06 | ACC-06-S1 | DC-2 | 3.4 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive gerer-comptes-et-acces`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/gerer-comptes-et-acces/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
