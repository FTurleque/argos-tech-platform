# Tâches : Alimenter l'inventaire et compléter l'IHM

<!--
Sprint 7 du plan d'audit du 2026-11-21. Ordre des constats : AUD-QUA-01 → AUD-QUA-17 → AUD-ARC-15 → AUD-ARC-22 →
AUD-TST-10 → AUD-ARC-12 → AUD-TST-09 → AUD-TST-04 → AUD-TST-08. Décisions du porteur du 2026-11-26 : droits
(administrateur pour projets et dépôts, contributeur pour imports), producteur planifié des suggestions, test de
contrat, Vitest. Aucun changement sur la synchronisation des sources (sprint 8 en parallèle).
-->

## 1. Inventaire alimenté par l'API et l'IHM (AUD-QUA-01)

- [x] 1.1 Garde `Access.contributor(espace)` (contributeur ou administrateur, refus journalisés) — couvre INV-13-S6, PIN-06-S3 — vérification : test unitaire de la garde
- [x] 1.2 Routes `POST /api/v1/projects`, `POST|DELETE /api/v1/projects/{id}/repositories[/{repositoryId}]` (administrateur), validation des saisies, 409 pour un dépôt déjà rattaché — couvre INV-12-S1 à S6 — vérification : `AdministrationProjetsTest` vert
- [x] 1.3 Routes `POST /api/v1/projects/{id}/inventory/{sbom,manifests,refresh}` (contributeur), 409 sans dépôt — couvre INV-13-S1 à S6 — vérification : `ImportManuelTest` vert
- [x] 1.4 IHM : formulaires de création de projet, de rattachement et détachement de dépôt, d'import de SBOM et de manifestes et de relance dans la vue Inventaire, par un module `src/api/projets.ts` — couvre INV-12, INV-13 — vérification : tests Vitest de la vue (groupe 6) ; `npm run typecheck`
- [x] 1.5 Documentation : fiche `/docs/specifications/inventaire-sbom.md` (routes, droits, écart levé), Javadoc d'`InventoryResource`, arc42 §6 (séquence d'import manuel) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Fonctions atteintes seulement par les tests (AUD-QUA-17)

- [x] 2.1 Prompt `suggestion-standard` et producteur `impact.SuggestionsRadar` appelé par la tâche radar après l'actualisation ; une suggestion en attente n'est pas dupliquée ; erreurs isolées par espace — couvre RAD-08-S1 à S4 — vérification : `SuggestionsRadarTest` vert
- [x] 2.2 Route `POST /api/v1/projects/{id}/activite/collecte` (contributeur) et bouton « Collecter maintenant » — couvre PIN-06-S1 à S3 — vérification : `CollecteImmediateTest` vert
- [x] 2.3 Supprimer `EtatImpact.peutAller`, `TextesOfficiels.parIdentifiant`, `ImportProjet.importe` ; test d'architecture `MethodesAtteintesEnProductionTest` avec ses exemptions testées — vérification : test vert, et rouge sur une classe d'exemple sans appelant
- [x] 2.4 Documentation : fiches `radar-standards.md`, `activite-projets.md`, arc42 §5 (`SuggestionsRadar`) — vérification : `node scripts/verifier-docs.mjs`

## 3. Profil artiste dans l'IHM (AUD-ARC-15)

- [x] 3.1 Vue `ProfilArtisteView` (suivis, opportunités, résumé, sources vérifiées, rubriques) aux adresses `/profils/artiste` et `/profils/artiste/opportunites/:id`, module `src/api/artiste.ts` — couvre ART-07-S1 à S3 — vérification : tests Vitest (groupe 6)
- [x] 3.2 Documentation : fiche `profil-artiste.md` (écart de l'IHM levé) — vérification : `node scripts/verifier-docs.mjs`

## 4. Liens des alertes (AUD-ARC-22)

- [x] 4.1 `alerting.LiensIhm` et adoption par `MoteurAlertes`, `RappelsRadar`, `RappelsOpportunites` (format `<base>/#/…`) ; tests d'alertes alignés — couvre ALR-09-S1, S2 — vérification : `AlertesTest` et tests des rappels verts
- [x] 4.2 Vues `SignalView` (`/signaux/:id`) et `RadarView` (`/espaces/:id/radar`, décision réservée à l'administrateur) — couvre ALR-09-S1, RAD-09-S1, S2 — vérification : tests Vitest
- [x] 4.3 Test de l'IHM qui lit les gabarits de `LiensIhm.java` et vérifie une route correspondante dans `router.ts` — couvre ALR-09-S3 — vérification : `npm test` vert, rouge si un gabarit n'a pas de route
- [x] 4.4 Documentation : fiche `alertes-digests.md` — vérification : `node scripts/verifier-docs.mjs`

## 5. Tests de `main.js` du desktop (AUD-TST-10)

- [x] 5.1 Extraire dans `src/fenetre.js` les options de `BrowserWindow`, la règle de permissions et la règle de navigation ; tests unitaires (`contextIsolation`, `sandbox` vrais, `nodeIntegration` faux, CSP, seules les notifications permises, navigation hors origine refusée) — vérification : `npm run check` vert
- [x] 5.2 Test de chargement de `main.js` avec l'`electron` factice : fenêtre créée avec ces options, protocole enregistré avec la CSP, gestionnaire de permissions posé — vérification : `npm test` vert

## 6. Contrat et tests de l'IHM (AUD-ARC-12, AUD-TST-09)

- [x] 6.1 Instantané `argos-ui/src/api/openapi.json` et test serveur `ContratClientIhmTest` (comparaison normalisée, régénération par `-Dargos.contrat.regenerer=true`) — vérification : test vert, rouge après modification d'une route
- [x] 6.2 Test de l'IHM `tests/contrat-api.test.mjs` : chaque appel du client (méthode, chemin) existe dans l'instantané — vérification : `npm test` vert, rouge pour un chemin inconnu
- [x] 6.3 Vitest, `@vue/test-utils`, happy-dom ; tests de `appelerApi` (jeton, 204, erreurs `Erreur`, 401/403, délai, annulation) et des vues (Statut, Alertes, Affichage, Réglages, Inventaire, Recherche, Veille, Profils, Signal, Radar, Profil artiste) — vérification : `npm test` vert ; notices de licences mises à jour
- [x] 6.4 CI : `npm test` couvre Vitest (aucune modification de workflow si le script l'enchaîne) — vérification : lecture du workflow

## 7. Couverture par module et isolation des tests (AUD-TST-04, AUD-TST-08)

- [x] 7.1 Classes de test dédiées pour `workspace`, `inventory`, `vulnintel`, `releaseradar`, `audit` (cas limites : dernier administrateur, invitations, parseurs, cycles de vie, journal des refus) et tests de `scm` (pagination, quotas, erreurs de forge) — vérification : `mvn verify` vert
- [x] 7.2 Script `scripts/verifier-couverture-modules.mjs` (agrégation de `jacoco.csv` par module, seuil) avec ses tests, appelé par la CI — vérification : `node --test scripts/*.test.mjs` vert ; chaque module au seuil
- [x] 7.3 `TestDb.reset` dérivé de `pg_tables` avec liste de tables conservées ; suppression de la liste d'`ArtisteFixtures` ; test d'exhaustivité — vérification : `NettoyageBaseTest` vert
- [x] 7.4 Documentation : arc42 §8 (stratégie de tests : Vitest, contrat, couverture par module, nettoyage) — vérification : `node scripts/verifier-docs.mjs`

## 8. Vérifications d'intégration

- [x] 8.1 Valider le changement — vérification : `openspec validate alimenter-inventaire-et-completer-ihm --strict` réussit
- [x] 8.2 CI locale : `mvn -B -f argos-server/pom.xml -P integration-continue clean verify`, `npm test`, `npm run typecheck`, `npm run build` (argos-ui), `npm run check` (argos-desktop), scripts de `outils` — vérification : tout vert
- [x] 8.3 `archgate check` et relecture par le superviseur de code ; remontées corrigées

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| INV-12 | INV-12-S1 à S6 | D1, D2 | 1.2, 1.4 | `api/inventaire/AdministrationProjetsTest` | exécuté le 2026-11-26 |
| INV-13 | INV-13-S1 à S6 | D1, D2, DC-1 | 1.1, 1.3, 1.4 | `api/inventaire/ImportManuelTest` | exécuté le 2026-11-26 |
| RAD-08 | RAD-08-S1 à S4 | D4, DC-2 | 2.1 | `impact/SuggestionsRadarTest` | exécuté le 2026-11-26 |
| RAD-09 | RAD-09-S1, S2 | D6 | 4.2 | `argos-ui/tests/vues/radar.spec.ts` | exécuté le 2026-11-26 |
| PIN-06 | PIN-06-S1 à S3 | D3 | 2.2 | `projectintel/CollecteImmediateTest` | exécuté le 2026-11-26 |
| ART-07 | ART-07-S1 à S3 | D6 | 3.1 | `argos-ui/tests/vues/profil-artiste.spec.ts` | exécuté le 2026-11-26 |
| ALR-09 | ALR-09-S1, S2 | D5 | 4.1 | `alerting/AlertesTest`, `alerting/LiensIhmTest` | exécuté le 2026-11-26 |
| ALR-09 | ALR-09-S3 | D5 | 4.3 | `argos-ui/tests/liens-alertes.test.mjs` | exécuté le 2026-11-26 |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (openspec archive alimenter-inventaire-et-completer-ihm).
- Après archivage, remplacer dans les fiches les liens vers /openspec/changes/alimenter-inventaire-et-completer-ihm/ par la spec principale ou le dossier d'archive, puis exécuter node scripts/verifier-docs.mjs.
