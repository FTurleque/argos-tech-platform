# Tâches : rechercher et filtrer la veille, et enregistrer des vues

<!-- Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). Une tâche n'est cochée que si sa vérification a réussi. -->

## 1. Recherche

- [x] 1.1 Créer l'index plein texte et la requête de recherche sous RLS — couvre RCH-01-S1, RCH-01-S2, RCH-03-S1 — vérification : tests d'intégration passent
- [x] 1.2 Ajouter les filtres combinables — couvre RCH-02-S1 — vérification : tests d'intégration passent
- [x] 1.3 Brancher l'outil MCP `search_signals` sur la même requête — couvre RCH-01, RCH-03 — vérification : test de contrat MCP passe

## 2. Vues et performance

- [x] 2.1 Enregistrer, rappeler et supprimer des vues personnelles — couvre RCH-04-S1, RCH-04-S2 — vérification : tests d'intégration passent
- [x] 2.2 Mesurer le temps de réponse sur le volume cible — couvre RCH-05-S1 — vérification : rapport QS-13 versionné

## 3. Documentation durable et décisions

- [x] 3.1 Mettre à jour la fiche `recherche-veille`, les sections arc42 §8.10, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement — vérification : `openspec validate rechercher-et-filtrer --strict` réussit
- [ ] 4.2 Parcours : recherche « Oracle » filtrée sur un projet et 30 jours, enregistrée en vue, rappelée depuis le desktop — vérification : scénario E2E consigné avec ses résultats

Tâche 4.2 non réalisée : `argos-ui` (partagée par le client desktop) n'a ni écran de recherche ni connexion OIDC. Le même parcours par l'API REST, client du portail comme du desktop, est vérifié par `RechercheVeilleTest.rch04S1VueRappelee` (recherche « Oracle », projet, 30 jours, vue enregistrée puis rappelée : mêmes résultats que la recherche directe).

## Preuves

- `mvn -B -f argos-server/pom.xml verify` du 2026-11-16 : BUILD SUCCESS, 318 tests, 0 échec, 1 ignoré (`MesurePerformanceRechercheTest`, exécuté à la demande).
- `RechercheVeilleTest` (10 tests) et `RechercheMcpTest` (3 tests) passent ; `OutilsConsultationMcpTest`, `ContenuExterneMcpTest` et `ContratOutilsMcpTest` passent avec `search_signals` branché sur la recherche.
- `MesurePerformanceRechercheTest -Dargos.performance=true` du 2026-11-16 : campagne p95 = 250 ms (cible Q-05 : 500 ms) ; [rapport QS-13](/docs/architecture/quality/mesure-qs13-recherche.md).
- `node scripts/verifier-docs.mjs` : aucune erreur ; `openspec validate rechercher-et-filtrer --strict` : valide.

## Matrice de traçabilité

État au 2026-11-16.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| RCH-01 | RCH-01-S1 | Composants, D1, DF-RCH-1 | 1.1, 1.3 | `RechercheVeilleTest.rch01S1MotTrouve`, `rch01RechercheBilingue` ; `RechercheMcpTest.rch01MotsEtPertinence` | passe |
| RCH-01 | RCH-01-S2 | Composants | 1.1, 1.3 | `RechercheVeilleTest.rch01S2AucunResultat` ; `RechercheMcpTest.rch01MotsEtPertinence` | passe |
| RCH-02 | RCH-02-S1 | Contrats d'interface, D4, D5 | 1.2 | `RechercheVeilleTest.rch02S1FiltresCombines` | passe |
| RCH-03 | RCH-03-S1 | Sécurité et isolation, D2 | 1.1, 1.3 | `RechercheVeilleTest.rch03S1MotPresentDansUnAutreEspace`, `rch03CloisonnementSousArgosApp` ; `RechercheMcpTest.rch03MotPresentDansUnAutreEspace` | passe |
| RCH-04 | RCH-04-S1 | Persistance et intégrité | 2.1 | `RechercheVeilleTest.rch04S1VueRappelee` | passe |
| RCH-04 | RCH-04-S2 | Sécurité et isolation | 2.1 | `RechercheVeilleTest.rch04S2VueDUnAutreUtilisateur` | passe |
| RCH-05 | RCH-05-S1 | Performance et stratégie de mesure | 2.2 | `MesurePerformanceRechercheTest` ; [rapport QS-13](/docs/architecture/quality/mesure-qs13-recherche.md) | passe (p95 = 250 ms) |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive rechercher-et-filtrer`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/rechercher-et-filtrer/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
