# Tâches : optimiser la restitution de « Ma veille »

<!--
Changement dédié à l'optimisation de « Ma veille » (décision K2 du porteur du projet, 2026-11-19) ; jeu de référence de la décision K3. Branche `lot19/optimisation`, créée depuis `lot19/base`.
Règles : aucune requête vers Internet dans les tests ; aucune dépendance nouvelle (ADR-0021) ; ADR inchangés ; aucune instrumentation temporaire conservée ; contenu restitué inchangé (PRF-07-S2).
-->

## 1. Diagnostic

- [x] 1.1 Reproduire le dépassement avec le jeu K3 et mesurer chaque phase de la restitution (journal temporaire), puis la requête des impacts récents par `EXPLAIN ANALYZE` sous le rôle `argos_app` — couvre PRF-07 — vérification : diagnostic consigné dans design.md (« Diagnostic mesuré »), instrumentation retirée (`git diff` sans journal temporaire)

## 2. Correction

- [x] 2.1 Lire les signaux des avis en une requête groupée (`SignalQueries.findAll`) dans `VeilleProfilsResource`, `SignauxAbonnements` (critère `PROJECT`) et `SignalQueries.search` — couvre PRF-07-S1, PRF-07-S2 — vérification : `architecture/LectureGroupeeSignauxTest` et `api/veille/VeilleLectureGroupeeTest` passent
- [x] 2.2 Créer la migration V21 (`project_impact_recents (updated_at DESC, id)`) justifiée par `EXPLAIN ANALYZE` — couvre PRF-07-S1 — vérification : plan par `Index Scan using project_impact_recents`, 7 à 8 ms ; `mvn -B -f argos-server/pom.xml verify` passe
- [x] 2.3 Tests du groupe : équivalence du contenu restitué (calcul de référence en base et lecture unitaire), lecture groupée égale à la lecture unitaire (INV-1), absence de lecture d'un signal par avis (ArchUnit) — couvre PRF-07-S1, PRF-07-S2 — vérification : `mvn -B -f argos-server/pom.xml verify` passe

## 3. Documentation durable

- [x] 3.1 Fiche `profils-veille` (PRF-07, traçabilité), registre `/docs/specifications/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 3.2 arc42 §8.7 (V21, prochaine migration V22, retour arrière) et D-13 (liste des migrations) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement — vérification : `openspec validate optimiser-ma-veille --strict` et `openspec validate --all --strict` réussissent
- [ ] 4.2 Relancer la campagne `npm --prefix e2e run test:performance` sous le verrou de la pile E2E, versionner le rapport régénéré et mettre à jour la fiche `performance-api-mcp` (écarts constatés) — couvre PRF-07-S1 — vérification : rapport `/docs/architecture/quality/mesure-performance-api-mcp.md`, `GET /api/v1/me/veille` dans la cible

Preuves, 2026-11-20 (branche `lot19/optimisation`) :

- 1.1 : pile de diagnostic (PostgreSQL 18 jetable sur le port 55433, `quarkus:dev` sur le port 8090, Keycloak des Dev Services) chargée par `e2e/performance/donnees-performance.sql` (jeu K3) ; `lot19/base` : p95 = 531,6 et 536,4 ms ; phases : `forWorkspaces` ≈ 275 ms, lectures unitaires des signaux ≈ 220 ms, abonnements ≈ 20 ms, préférences ≈ 6 ms ; `EXPLAIN ANALYZE` de `forWorkspaces` : 241 ms sans index adapté, 219 ms avec `(workspace_id, updated_at DESC, id)`, 7 ms avec `(updated_at DESC, id)`. Instrumentation retirée.
- 2.1 : `SignalQueries#findAll` ; `VeilleProfilsResource#avis`, `SignauxAbonnements#pour`, `SignalQueries#search` ; lecture groupée seule : p95 = 332,8 ms.
- 2.2 : `V21__index_impacts_recents.sql`, appliquée par Flyway sur la pile de diagnostic (version 21) ; plan `Index Scan using project_impact_recents`, 7,7 à 8,1 ms ; lecture groupée et index : p95 = 60,1 à 82,7 ms ; réponse JSON identique, octet pour octet, à celle de `lot19/base` (191 éléments).
- 2.3 : `api/veille/VeilleLectureGroupeeTest` (PRF-07-S2, INV-1), `architecture/LectureGroupeeSignauxTest` (PRF-07-S1, absence de N+1) ; `VeilleProfilsTest`, `VeilleAbonnementsPersonnelsTest`, `ProfilDemonstrationTest` toujours verts ; `mvn -B -f argos-server/pom.xml verify` passe (653 tests, 0 échec, 1 ignoré).
- 4.2 (non faite, 2026-11-20) : la campagne ne démarre pas, le port 55432 de `e2e/compose.yaml` étant occupé sur le poste par un processus extérieur au dépôt (`docker compose up` : « ports are not available ») ; le rapport versionné reste celui du 2026-11-19. La fiche `performance-api-mcp` consigne la mesure de diagnostic et l'écart. À relancer sous le verrou de la pile E2E dès que le port est libre.
- 3.1, 3.2 et 4.1 : `node scripts/verifier-docs.mjs` sans erreur ; `openspec validate --all --strict` valide.

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| PRF-07 | PRF-07-S1 | design.md D1, D2, « Performance et stratégie de mesure » ; DC-1 | 1.1, 2.1, 2.2, 4.2 | campagne `npm --prefix e2e run test:performance`, `/docs/architecture/quality/mesure-performance-api-mcp.md` ; `architecture/LectureGroupeeSignauxTest` | tests exécutés le 2026-11-20 ; mesure de diagnostic du 2026-11-20 (p95 = 60,1 à 82,7 ms) ; campagne de référence non relancée (tâche 4.2) |
| PRF-07 | PRF-07-S2 | design.md D1, « Sécurité et isolation » ; DC-1 | 2.1, 2.3 | `api/veille/VeilleLectureGroupeeTest` | exécuté le 2026-11-20 |

## Workflow follow-up

- Basculer dans la fiche `profils-veille` le statut [PROPOSITION] de PRF-07 selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive optimiser-ma-veille`).
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/optimiser-ma-veille/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
