# Tâches : Conserver les journaux pour une durée bornée, n'importer que l'inventaire modifié et paginer les listes

## 1. Format de pagination commun

- [x] 1.1 `commun.Pagination`, `commun.Page<T>`, `api.ParametresPagination.pagination` — couvre PERF-03-S2, S3 — vérification : `PaginationTest` et `ListesPagineesTest` passent
- [x] 1.2 ADR-0040 (Proposé), index `adr/README.md` et arc42 §9, paragraphe arc42 §8.10 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 1.3 Pousser tôt la branche avec ces types et le design, pour l'alignement du sprint 10 — vérification : `git log origin/claude/sprint9-retention-import-3873a9` contient le commit

## 2. Mécanisme de conservation

- [x] 2.1 Module `conservation` : `RegleConservation`, `SuppressionParLots`, `PurgesConservation` (`argos-purge-conservation`, `@TacheObservee`, SKIP), métriques ; ajout à `ModuleBoundariesTest` (transverse) et arc42 §5 — couvre EXP-17-S4 — vérification : `PurgesConservationTest`, `ModuleBoundariesTest` passent
- [x] 2.2 Règles `refus-acces`, `appels-mcp`, `appels-ia` (audit), `quotas-ia` (aigateway) — couvre EXP-17-S1, S2 — vérification : `ConservationTest` passe
- [x] 2.3 Règles `synchronisations`, `avis-mis-a-l-ecart`, `evenements-exploitation` (ingestion), `echecs-import` (inventory) — couvre EXP-17-S1, S3 — vérification : `ConservationTest` passe
- [x] 2.4 Règle `actualites` (signal) — couvre EXP-17-S1 — vérification : `ConservationTest` passe
- [x] 2.5 `ConservationCompleteTest` : liste fermée des tables de journal, d'échec ou d'historique, règle ou raison d'exclusion — couvre EXP-17 — vérification : le test passe et échoue sur une table nouvelle non déclarée
- [x] 2.6 Réglages `argos.conservation.*` dans `application.properties` ; cadence `ARGOS_CONSERVATION_CRON` dans `.env.example` et `compose.yaml` — vérification : `node scripts/verifier-configuration.mjs` sans erreur
- [x] 2.7 ADR-0041 (Proposé), index, fiche `docs/specifications/exploitation-plateforme.md` (EXP-17) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Journal des refus borné (AUD-SEC-03)

- [x] 3.1 Limiteur par auteur dans `JournalRefus`, métrique `argos_refus_non_journalises_total`, réglage `argos.audit.refus.par-minute` — couvre ACC-15-S1, S2 — vérification : `JournalRefusBorneTest` passe
- [x] 3.2 Fiche `docs/specifications/comptes-acces.md` (ACC-15) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Opportunités (AUD-PER-18)

- [x] 4.1 Âge maximal des opportunités sans date limite (`argos.profil-artiste.opportunites.age-max`) dans la restitution — couvre ART-08-S1, S2 — vérification : `ProfilArtisteTest` passe
- [x] 4.2 Règle `opportunites` (échéance + conservation) — couvre EXP-17-S5 — vérification : `ConservationTest` passe
- [x] 4.3 `GET /profils/artiste/opportunites` paginée ; IHM (`argos-ui/src/api/artiste.ts` et vue) adaptée — couvre PERF-03-S5 — vérification : tests serveur et `npm test`, `npm run typecheck` dans `argos-ui`
- [x] 4.4 Fiche `docs/specifications/profil-artiste.md` (ART-08) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Import d'inventaire (AUD-PER-19, AUD-PER-20)

- [x] 5.1 Migration `V26__empreinte_dernier_import_depot.sql` (colonne `empreinte`) — vérification : démarrage Flyway des tests
- [x] 5.2 `EmpreinteDocument` ; empreinte enregistrée par tout import d'un dépôt ; comparaison sous verrou pour une lecture depuis la forge ; `KEPT` sans écriture ni `InventoryReplaced` — couvre INV-14-S1, S2, S3, INV-09-S3 — vérification : `ImportPlanifieTest` (`inv14*`) passe
- [x] 5.3 Écriture par lot (`unnest`), dédoublonnage, PURL réécrit seulement s'il change — couvre INV-14-S4 — vérification : `ImportPlanifieTest.inv14S4…`, `EcritureComposantsTest` et tests d'inventaire existants passent
- [x] 5.4 Fiche `docs/specifications/inventaire-sbom.md` (INV-14, INV-09) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Listes paginées (AUD-PER-10)

- [x] 6.1 `GET /ops/sources/{id}/rejected` et `/events` paginées, du plus récent au plus ancien — couvre PERF-03-S1, S5 — vérification : `ListesPagineesTest` passe
- [x] 6.2 `GET /projects/{id}/inventory/failures` et `GET /projects/{id}/impacts` paginées — couvre PERF-03-S4, S5 — vérification : `ListesPagineesTest` passe
- [x] 6.3 `GET /projects/{id}/inventory` : `components` paginé — couvre PERF-03-S5 — vérification : `ListesPagineesTest` et tests d'inventaire passent
- [x] 6.4 Tests existants adaptés aux réponses paginées ; matrice des rôles inchangée — vérification : `mvn verify`
- [x] 6.5 Fiche `docs/specifications/performance-api-mcp.md` (PERF-03) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 7. Vérifications d'intégration

- [x] 7.1 Valider le changement — vérification : `openspec validate retenir-importer-et-paginer --strict` réussit
- [x] 7.2 CI locale complète (serveur JDK 17 `-P integration-continue clean verify` puis couverture par module, IHM, desktop, outils) et `archgate check` — vérification : tout est vert

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| EXP-17 | EXP-17-S1 | D1, D2, D3 / DC-2 | 2.2, 2.3 | `conservation/ConservationTest` | exécuté le 2026-11-26 |
| EXP-17 | EXP-17-S2 | D3 | 2.2 | `conservation/ConservationTest` | exécuté le 2026-11-26 |
| EXP-17 | EXP-17-S3 | D3, INV-2 | 2.3, 2.4 | `conservation/ConservationTest` | exécuté le 2026-11-26 |
| EXP-17 | EXP-17-S4 | D1 / DC-2 | 2.1 | `conservation/PurgesConservationTest` | exécuté le 2026-11-26 |
| EXP-17 | EXP-17-S5 | D5 | 4.2 | `conservation/ConservationTest` | exécuté le 2026-11-26 |
| EXP-17 | complétude | D4 | 2.5 | `architecture/ConservationCompleteTest` | exécuté le 2026-11-26 |
| ACC-15 | ACC-15-S1 | D7 | 3.1 | `audit/JournalRefusBorneTest` | exécuté le 2026-11-26 |
| ACC-15 | ACC-15-S2 | D7 | 3.1 | `audit/JournalRefusBorneTest` | exécuté le 2026-11-26 |
| ART-08 | ART-08-S1 | D5 | 4.1 | `chain/ProfilArtisteTest` (`art08*`) | exécuté le 2026-11-26 |
| ART-08 | ART-08-S2 | D5 | 4.1 | `chain/ProfilArtisteTest` (`art08*`) | exécuté le 2026-11-26 |
| INV-14 | INV-14-S1 | D8 / DC-3 | 5.2 | `chain/ImportPlanifieTest` (`inv14*`) | exécuté le 2026-11-26 |
| INV-14 | INV-14-S2 | D8 / DC-3 | 5.2 | `chain/ImportPlanifieTest` (`inv14*`) | exécuté le 2026-11-26 |
| INV-14 | INV-14-S3 | D8 | 5.2 | `chain/ImportPlanifieTest` (`inv14*`) | exécuté le 2026-11-26 |
| INV-14 | INV-14-S4 | D9 / DC-3 | 5.3 | `chain/ImportPlanifieTest` (`inv14*`) | exécuté le 2026-11-26 |
| INV-09 | INV-09-S3 | D8 / DC-3 | 5.2 | `chain/ImportPlanifieTest` (`inv14*`) | exécuté le 2026-11-26 |
| PERF-03 | PERF-03-S1 | D6 | 6.1 | `api/ListesPagineesTest` | exécuté le 2026-11-26 |
| PERF-03 | PERF-03-S2 | D6 | 1.1 | `commun/PaginationTest` | exécuté le 2026-11-26 |
| PERF-03 | PERF-03-S3 | D6 | 1.1 | `api/ListesPagineesTest` (`bornesEtValeurNonEntiere`) | exécuté le 2026-11-26 |
| PERF-03 | PERF-03-S4 | D6 | 6.2 | `api/ListesPagineesTest` | exécuté le 2026-11-26 |
| PERF-03 | PERF-03-S5 | D6 | 4.3, 6.1 à 6.3 | `api/ListesPagineesTest` | exécuté le 2026-11-26 |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive retenir-importer-et-paginer`).
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/retenir-importer-et-paginer/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
- Index utiles aux purges, à créer avec AUD-PER-09 (sprint 10) : `actualite (recu_le)`, `ingestion_failure (run_id)`, `sync_attempt (run_id)`.
