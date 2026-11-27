# Tâches : accélérer les requêtes chaudes

## 1. Index (AUD-PER-09, AUD-PER-29, AUD-PER-26, AUD-PER-06)

- [x] 1.1 Écrire `V27__index_requetes_chaudes.sql` (index du tableau « Persistance » de design.md, suppressions comprises) — couvre PRF-07, COR-13 — vérification : `IndexRequetesChaudesTest` (pg_indexes : index créés présents, `project_impact_signal`, `project_impact_project`, `project_impact_recents` absents)
- [x] 1.2 Mettre à jour l'inventaire des index et migrations (arc42 §8.7) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Corrélation (AUD-PER-25, AUD-PER-03)

- [x] 2.1 Upsert conditionnel sans `component_id` dans le `SET` — couvre COR-07-S2, S3 — vérification : `CorrelationTest` (`cor07S1…`, `cor07S3…`)
- [x] 2.2 Corrélation ensembliste par lots (DC-1, D3), transitions système par lot dans `TraitementImpacts`, paramètre `argos.correlation.taille-lot` — couvre COR-13-S1, S2 — vérification : `RequetesConstantesTest` (corrélation) et tests de corrélation existants verts
- [x] 2.3 Compteur d'instructions JDBC de test (`testsupport/CompteurInstructions`) — vérification : utilisé par `RequetesConstantesTest`
- [x] 2.4 Fiche `/docs/specifications/correlation-impact.md` (COR-07, COR-13) — vérification : `node scripts/verifier-docs.mjs`

## 3. Alertes (AUD-PER-04)

- [x] 3.1 Lecture groupée des destinataires, espaces, abonnements, impacts et alertes existantes ; écritures par lot ; plus de diffusion dans l'observateur (DC-2, D4, D5) — couvre ALR-10-S1, S2, S3 — vérification : `RequetesConstantesTest` (alertes), `AlertesTest` adapté
- [x] 3.2 Fiche `/docs/specifications/alertes-digests.md` (ALR-10) — vérification : `node scripts/verifier-docs.mjs`

## 4. Ma veille (AUD-PER-06, AUD-PER-26)

- [x] 4.1 Lecture des impacts récents espace par espace sur l'index partiel (D6) — couvre PRF-07-S2, S3 — vérification : `VeilleLectureGroupeeTest` vert
- [x] 4.2 Recherche des abonnements par sous-requêtes indexées (D7) — couvre PRF-07-S3 — vérification : `IndexRequetesChaudesTest` (plan sans parcours séquentiel de `signal_package`), tests d'abonnements verts
- [x] 4.5 Paginer `GET /api/v1/me/veille` au format ADR-0040 dans l'union composée (D11) — couvre PRF-09-S1 à S3 — vérification : `VeillePagineeTest`, tests de veille existants adaptés, `ContratClientIhmTest`
- [x] 4.6 IHM « Ma veille » paginée (total, position, page précédente et suivante), tests e2e et campagne adaptés à `items` — couvre PRF-09-S4 — vérification : `npm test`, `typecheck`, e2e `typecheck`, `mesure.test.mjs`
- [x] 4.4 Fiche `/docs/specifications/profils-veille.md` (PRF-07) — vérification : `node scripts/verifier-docs.mjs`

## 5. Radar (AUD-PER-05)

- [x] 5.1 Composants par espace en SQL, calendrier par espace, dettes par lot, rappels en une passe (D8) — couvre RAD-10-S1, S2 — vérification : `RequetesConstantesTest` (radar), `RadarStandardsTest` et `ParcoursRadarTest` verts
- [x] 5.2 Fiche `/docs/specifications/radar-standards.md` (RAD-10) — vérification : `node scripts/verifier-docs.mjs`

## 6. Recherche (AUD-PER-07)

- [x] 6.1 Classement borné à 1 000 candidats, total plafonné, champ `totalPlafonne` (API, MCP, IHM) (D9) — couvre RCH-07-S1, S2 — vérification : `RechercheBorneeTest`, tests de recherche existants, tests IHM
- [x] 6.2 Fiche `/docs/specifications/recherche-veille.md` (RCH-07) — vérification : `node scripts/verifier-docs.mjs`

## 7. Campagne de performance (PERF-02, PRF-07)

- [x] 7.1 Jeu à plusieurs espaces, abonnements par paquet et technologie, plus de 500 000 impacts majoritairement résolus ; opération « Ma veille (petit espace) » (D10) — couvre PERF-02-S4, PRF-07-S3 — vérification : `npm --prefix e2e run test:performance` (code 0), tests `mesure.test.mjs`
- [x] 7.2 Fiche `/docs/specifications/performance-api-mcp.md` et rapport `docs/architecture/quality/mesure-performance-api-mcp.md` — vérification : `node scripts/verifier-docs.mjs`

## 8. Vérifications d'intégration

- [ ] 8.1 Valider le changement — vérification : `openspec validate accelerer-requetes-chaudes --strict` réussit
- [ ] 8.2 CI locale complète (JDK 17 : `mvn -B -f argos-server/pom.xml verify`, IHM, e2e selon `.github/workflows/integration-continue.yml`) verte

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| COR-07 | COR-07-S1 | D2 | 2.1 | tests de corrélation existants | passe le 2026-11-26 |
| COR-07 | COR-07-S2 | D2 | 2.1 | `CorrelationTest` (`cor07S1…`, `cor07S3…`) | écrit, passe le 2026-11-26 |
| COR-07 | COR-07-S3 | D2 | 2.1 | `CorrelationTest` (`cor07S1…`, `cor07S3…`) | écrit, passe le 2026-11-26 |
| COR-13 | COR-13-S1 | D3 / DC-1 | 2.2 | `RequetesConstantesTest` | écrit, passe le 2026-11-26 |
| COR-13 | COR-13-S2 | D3 / DC-1 | 2.2 | tests de corrélation existants | passe le 2026-11-26 |
| ALR-10 | ALR-10-S1 | D4 / DC-2 | 3.1 | `RequetesConstantesTest` | écrit, passe le 2026-11-26 |
| ALR-10 | ALR-10-S2 | D5 | 3.1 | `AlertesTest` | écrit, passe le 2026-11-26 |
| ALR-10 | ALR-10-S3 | D4 | 3.1 | `AlertesTest` | écrit, passe le 2026-11-26 |
| PRF-07 | PRF-07-S1 | D6, D7 | 7.1 | campagne de performance | exécuté le 2026-11-27 : p95 185,5 ms |
| PRF-07 | PRF-07-S2 | D6 | 4.1 | `VeilleLectureGroupeeTest` | passe le 2026-11-26 |
| PRF-07 | PRF-07-S3 | D6, D7, D10 | 4.1, 4.2, 7.1 | campagne de performance | exécuté le 2026-11-27 : p95 67,8 ms |
| PRF-09 | PRF-09-S1 à S3 | D11 | 4.5 | `VeillePagineeTest` | écrit, passe le 2026-11-27 |
| PRF-09 | PRF-09-S4 | D11 | 4.6 | `argos-ui/tests/vues/veille.spec.ts`, `tests/veille-affichee.test.mjs` | écrit, passe le 2026-11-27 |
| RAD-10 | RAD-10-S1 | D8 | 5.1 | `RequetesConstantesTest` | écrit, passe le 2026-11-26 |
| RAD-10 | RAD-10-S2 | D8 | 5.1 | `RequetesConstantesTest` | écrit, passe le 2026-11-26 |
| RCH-07 | RCH-07-S1 | D9 | 6.1 | `RechercheBorneeTest` | écrit, passe le 2026-11-26 |
| RCH-07 | RCH-07-S2 | D9 | 6.1 | `RechercheBorneeTest` | écrit, passe le 2026-11-26 |
| PERF-02 | PERF-02-S1 à S3 | D10 | 7.1 | `mesure.test.mjs`, campagne | passe le 2026-11-26 |
| PERF-02 | PERF-02-S4 | D10 | 7.1 | campagne de performance | exécuté le 2026-11-27 : code 0 |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive accelerer-requetes-chaudes`).
- Après archivage, remplacer dans les fiches les liens vers /openspec/changes/accelerer-requetes-chaudes/ par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
- Mesurer la campagne sur le NAS (R-16).
