# Tâches : exposer Argos aux assistants IA par un serveur MCP en lecture seule

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Arbitrages

- [ ] 1.1 Faire choisir le mode d'obtention du jeton par les IDE (Q-3) — couvre MCP-03 — vérification : choix inscrit dans la fiche `serveur-mcp`

## 2. Serveur et sécurité

- [ ] 2.1 Ajouter `quarkus-mcp-server-http` (licence enregistrée, ADR-0021) et le transport sans état — couvre MCP-01 — vérification : test de contrat « liste des outils » passe
- [ ] 2.2 Brancher l'authentification et l'espace courant — couvre MCP-03-S1, MCP-04-S1 — vérification : tests d'intégration passent
- [ ] 2.3 Baliser et tronquer le contenu externe — couvre MCP-05-S1 — vérification : test du texte piégé passe
- [ ] 2.4 Limiter le débit et journaliser les appels — couvre MCP-06-S1 — vérification : test d'intégration passe

## 3. Outils

- [ ] 3.1 Implémenter `check_dependency` — couvre MCP-02-S1, MCP-02-S2 — vérification : tests d'intégration passent
- [ ] 3.2 Implémenter `search_signals`, `get_technology_standard`, `get_project_context`, `upcoming_releases`, `get_security_alerts`, `explain_change` — couvre MCP-01-S1, MCP-04 — vérification : tests de contrat par outil passent
- [ ] 3.3 Implémenter `get_regulatory_changes` avec les garde-fous — couvre MCP-07-S1 — vérification : test d'intégration passe
- [ ] 3.4 Rejeter tout outil d'écriture — couvre MCP-01-S2 — vérification : test de contrat passe

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour la fiche `serveur-mcp`, les sections arc42 §5.3, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate exposer-serveur-mcp --strict` réussit
- [ ] 5.2 Parcours : Claude Code configuré avec `.mcp.json`, appel de `check_dependency` sur une version vulnérable de test, réponse citée et sourcée — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| MCP-01 | MCP-01-S1 | D1 | 2.1, 3.2 | test à écrire | à écrire |
| MCP-01 | MCP-01-S2 | D1 | 2.1, 3.4 | test à écrire | à écrire |
| MCP-02 | MCP-02-S1 | DC-1 | 3.1 | test à écrire | à écrire |
| MCP-02 | MCP-02-S2 | DC-1 | 3.1 | test à écrire | à écrire |
| MCP-03 | MCP-03-S1 | DC-1 | 1.1, 2.2 | test à écrire | à écrire |
| MCP-04 | MCP-04-S1 | DC-1 | 2.2, 3.2 | test à écrire | à écrire |
| MCP-05 | MCP-05-S1 | DC-1 | 2.3 | test à écrire | à écrire |
| MCP-06 | MCP-06-S1 | DC-1 | 2.4 | test à écrire | à écrire |
| MCP-07 | MCP-07-S1 | DC-1 | 3.3 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive exposer-serveur-mcp`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/exposer-serveur-mcp/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
