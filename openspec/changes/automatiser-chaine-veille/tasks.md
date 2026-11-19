# Tâches : automatiser la chaîne de veille sécurité

<!--
Cadrage du 2026-11-19 (branche lot18/cadrage) : proposition, specs, conception et tâches rédigées à partir des décisions J1 à J8 du porteur du projet (2026-11-19) ; questions Q-COL-1, Q-COL-2, Q-INV-1, Q-INV-2, Q-COR-2, Q-ABO-1 levées dans les fiches ; glossaire et registre mis à jour. Preuves du cadrage, 2026-11-19 : `openspec validate automatiser-chaine-veille --strict` (valide), `openspec validate --all --strict`, `node scripts/verifier-docs.mjs` (aucune erreur). Aucune tâche d'implémentation n'est faite : aucune case n'est cochée.

Trois volets indépendants, réalisables en parallèle (design.md, « Compatibilité ») ; migrations réservées : V18 (A), V19 (B), V20 (C). Chaque volet livre ses tests et sa documentation ; le groupe 4 ne contient que des vérifications d'intégration.
Règles communes : aucune requête vers Internet dans les tests (sources et forges simulées par réponses enregistrées, `testsupport/StubServer`) ; aucune dépendance payante ni sous licence non permissive (ADR-0021) ; ADR inchangés.
-->

## 1. Volet A — collecte et abonnements (J1, J2, J7)

Code concerné : `io.argos.signal` (`SynchronisationPlanifiee` nouveau, `SignalRecorder`, `SourceOperations`), `io.argos.ingestion` (`NormalizedAdvisory`, `osv/OsvNormalizer`, `SourceSynchronizer`), `io.argos.vulnintel` (`Enricher`, `CertFrSource`, `KevSource`, `EpssSource`), `io.argos.workspace.subscription` (`SubscriptionService`), `io.argos.api.SubscriptionResource`, `db/migration/V18__automatisation_collecte_et_abonnements.sql`, `application.properties`, `argos-ui/src/views/VeilleView.vue`, `argos-ui/src/api/veille.ts`.

- [ ] 1.1 Créer la migration V18 (empreinte normalisée de `signal` et `signal_version`, index unique partiel ; `subscription.state` `INACTIVE` → `SUSPENDED` ; privilèges et politiques RLS de mise à jour et de suppression) — couvre COL-10, ABO-07, ABO-08 — vérification : `mvn -B -f argos-server/pom.xml verify` passe, test de migration (abonnements `INACTIVE` devenus `SUSPENDED`, critères intacts) et `CouvertureRlsTest` verts
- [ ] 1.2 Planifier la synchronisation des sources d'avis (`signal-synchronisation-sources`, cadence `argos.ingestion.planification.cadence`, `PT1H` par défaut, `SKIP`, méthode testable recevant l'instant, désactivée en test et en E2E) — couvre COL-09-S1 à COL-09-S4 — vérification : `chain/SynchronisationPlanifieeTest` (horloge simulée, OSV simulé par réponses enregistrées) passe ; `ModuleBoundariesTest` vert
- [ ] 1.3 Rafraîchir les index des sources d'enrichissement (CERT-FR, KEV, EPSS) à chaque cadence, sans bloquer la synchronisation — couvre COL-09-S5 — vérification : test d'intégration avec CERT-FR simulé puis indisponible passe
- [ ] 1.4 Calculer l'empreinte SHA-256 du contenu normalisé canonique (P-2), comparer sur elle et rattraper l'empreinte des signaux existants sans nouvelle version — couvre COL-10-S1 à COL-10-S4 — vérification : `ingestion/EmpreinteNormaliseeTest` et cas ajoutés à `chain/CollecteTest` passent ; COL-03-S1 à S3 toujours verts
- [ ] 1.5 Implémenter suspension, réactivation et suppression des abonnements (service, `POST …/suspension`, `POST …/reactivation`, `DELETE /api/v1/subscriptions/{id}`, liste complète de l'appelant, `/leave` d'un `PERSONAL` = suspension, contrat OpenAPI) — couvre ABO-07-S1 à ABO-07-S4, ABO-08-S1 à ABO-08-S3 — vérification : `chain/SubscriptionTest` (jetons réels, RLS sous `argos_app`) et `architecture/SubscriptionIsolationTest` passent ; aucune alerte au titre d'un abonnement suspendu (`alerting` inchangé)
- [ ] 1.6 Afficher l'état des abonnements et les actions suspendre, réactiver, supprimer dans `argos-ui` (confirmation avant suppression) — couvre ABO-07-S4, ABO-08-S1 — vérification : `argos-ui/tests/abonnements.test.mjs` passe ; `npm --prefix argos-ui test && npm --prefix argos-ui run build` réussissent
- [ ] 1.7 Mettre à jour la documentation du volet : fiches `collecte-avis-securite` et `abonnements-veille` (preuves, statuts), glossaire « Abonnement suspendu », arc42 §8.7 (V18), §6.3 (synchronisation planifiée), registre `docs/specifications/README.md` (couverture du volet) et matrice ci-dessous — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Volet B — inventaire (J3, J4, J6)

Code concerné : `io.argos.inventory` (`ImportPlanifie` nouveau, `InventoryService`, `InventoryStore`, `InventoryQueries`, `ProjectDirectory`), `io.argos.scm.RepositoryFiles` (réutilisé sans modification), `io.argos.impact.CorrelationEngine` (`onInventoryReplaced`, `correlateProject`, marqueur), `io.argos.api.InventoryResource`, `db/migration/V19__depots_projet_et_occurrences.sql`, `application.properties`, `argos-ui/src/views/InventaireView.vue`, `argos-ui/src/api/inventaire.ts`, `argos-ui/src/api/inventaire-modele.ts`.

- [ ] 2.1 Créer la migration V19 (`project_repository` alimentée depuis `project.scm_*`, `repository_inventory`, `component_occurrence`, dépôt des échecs d'import, marqueur `recorrelation_pending`, RLS de lecture) — couvre INV-10-S4 — vérification : `mvn -B -f argos-server/pom.xml verify` passe ; test de migration (projet à un dépôt : inventaire et impacts inchangés) et `CouvertureRlsTest` verts
- [ ] 2.2 Constituer l'inventaire d'un projet comme l'union des inventaires de ses dépôts (occurrences par dépôt, composant unique avec ses dépôts, retrait d'un composant sans occurrence, niveau sur la meilleure occurrence, P-7) — couvre INV-10-S1 à INV-10-S3 — vérification : `chain/ProjetMultiDepotsTest` passe ; `chain/InventoryTest` et `ConsultationInventaireTest` toujours verts
- [ ] 2.3 Planifier l'import quotidien (`inventory-import-quotidien`, `cron` `argos.inventory.import.cron`, 03:00 par défaut, fuseau `argos.alerting.fuseau`, `SKIP`, désactivé en test et en E2E) avec verrou consultatif par projet partagé avec l'import manuel, sans route entrante — couvre INV-07-S1 à INV-07-S4, INV-08-S1, INV-08-S2 — vérification : `chain/ImportPlanifieTest` (forges simulées par les réponses enregistrées de `fixtures/scm/enregistrees/`, imports concurrents) passe ; test de routes : aucune route non authentifiée ajoutée
- [ ] 2.4 Recorréler à chaque import réussi : signaux qui affectent l'inventaire et signaux porteurs d'un impact du projet, marqueur posé à l'import et repris au passage suivant en cas d'échec (P-10) — couvre INV-09-S1 à INV-09-S4 — vérification : `chain/RecorrelationImportTest` passe (impact absent des impacts ouverts après retrait, avant comme après le volet C)
- [ ] 2.5 Restituer les dépôts dans la synthèse et la liste des composants d'inventaire (API, contrat OpenAPI) et les afficher dans la vue d'inventaire d'`argos-ui` — couvre INV-10-S1 — vérification : `ConsultationInventaireTest` étendu et `argos-ui/tests/inventaire-depots.test.mjs` passent ; `npm --prefix argos-ui test && npm --prefix argos-ui run build` réussissent
- [ ] 2.6 Mettre à jour la documentation du volet : fiche `inventaire-sbom` (DF-INV-1, DF-INV-4, preuves, statuts), glossaire « Projet » et « Repository », arc42 §8.7 (V19), §6 (import planifié), registre (couverture du volet) et matrice ci-dessous — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Volet C — impacts et performance (J5, J8)

Code concerné : `io.argos.impact` (`TraitementImpacts` nouveau, `CorrelationEngine`, `ImpactQueries`), `io.argos.alerting` (`MoteurAlertes`, `ResumesPeriodiques`), `io.argos.api` (`ImpactResource`, `SignalResource`), `db/migration/V20__etat_traitement_impacts.sql`, `argos-ui/src/views/AlertesView.vue` et son module d'API, `e2e/performance/mesurer-p95.mjs` (nouveau), `e2e/package.json` (script `test:performance`), `docs/architecture/quality/mesure-performance-api-mcp.md` (nouveau).

- [x] 3.1 Créer la migration V20 (`project_impact.state`, instantané paquet/version, `component_id` en `ON DELETE SET NULL`, contrainte INV-C3, `project_impact_transition`, privilèges et politiques RLS `project_impact_etat` et `project_impact_transition_lecture`) — couvre COR-09, COR-10, COR-12 — vérification : `mvn -B -f argos-server/pom.xml verify` passe ; test RLS : un `LECTEUR` ne peut pas modifier `state` sous `argos_app` ; `CouvertureRlsTest` vert — preuve 2026-11-19 (branche `lot18/impacts-performance`) : `mvn -B -f argos-server/pom.xml verify` : 602 tests, 0 échec (BUILD SUCCESS) ; `EtatTraitementImpactTest#rlsLecteurNePeutPasModifierLEtat` (lecteur et non-membre : 0 ligne, insertion d'historique refusée 42501, colonne `level` non modifiable) et `#migrationV20EtatParDefautInstantaneEtRetraitDuComposant` ; `CouvertureRlsTest` vert. Écarts : INV-C3 vérifié par le contrôle d'intégrité et non par une contrainte `CHECK` (P-13) ; colonne `workspace_id` ajoutée à `project_impact_transition` pour sa RLS ; état d'alerte `ANNULEE` (P-17).
- [x] 3.2 Implémenter l'état de traitement et les transitions demandées par un utilisateur autorisé (`TraitementImpacts`, `POST /api/v1/impacts/{id}/transitions`, motif obligatoire pour `écarté`, 400/403/404/409, refus journalisés, historique et filtre `etat`, contrat OpenAPI) — couvre COR-09-S1 à COR-09-S3, COR-10-S1 à COR-10-S5, COR-12-S1, COR-12-S2 — vérification : `chain/EtatTraitementImpactTest` (jetons réels du Keycloak des Dev Services, RLS sous `argos_app`) passe ; `ModuleBoundariesTest` vert (`impact` sans dépendance vers `workspace`) — preuve 2026-11-19 (branche `lot18/impacts-performance`) : `chain/EtatTraitementImpactTest` : 12 tests verts (COR-09-S1 à S3, COR-10-S1 à S5, COR-12-S1, S2, migration, RLS, contrat OpenAPI) ; `ModuleBoundariesTest` vert. Le rôle est porté par `Caller.contributorWorkspaces` (`api`), la RLS le garantit.
- [x] 3.3 Remplacer la suppression des impacts sans objet par leur résolution système motivée, ne jamais toucher un impact `écarté`, rouvrir un impact `résolu` de nouveau vérifié (P-3, P-6) — couvre COR-11-S1 à COR-11-S5 — vérification : cas ajoutés à `chain/CorrelationTest` et `chain/RecorrelationImportTest` passent ; COR-07-S1 et `JeuReferenceCorrelationTest` toujours verts — preuve 2026-11-19 (branche `lot18/impacts-performance`) : `chain/CorrelationTest` : 20 tests verts, dont COR-11-S1 à S5, avis retiré (`AVIS_RETIRE`) et composant revenu (P-14) ; `cor07S1CorrelationRejoueeSansDoublon` et `JeuReferenceCorrelationTest` verts. Écart : `chain/RecorrelationImportTest` appartient au volet B (absent de cette branche) ; les cas d'import sont couverts dans `CorrelationTest` par réimport de SBOM. `correlateProject` parcourt aussi les signaux porteurs d'un impact actif du projet (extension minimale de D4 ; marqueur laissé au volet B).
- [x] 3.4 Exclure les impacts `écarté` et `résolu` des alertes immédiates et des résumés — couvre ALR-07-S1, ALR-07-S2 — vérification : `alerting/AlertesImpactsTraitesTest` passe ; `AlertesTest` toujours vert — preuve 2026-11-19 (branche `lot18/impacts-performance`) : `alerting/AlertesImpactsTraitesTest` : 4 tests verts (ALR-07-S1 poste et e-mail, ALR-07-S2, impact écarté non évalué) ; `AlertesTest` vert.
- [x] 3.5 Afficher l'état, l'historique et les actions de transition d'un impact dans `argos-ui` (motif demandé pour écarter, actions masquées pour un `LECTEUR`, texte externe rendu comme texte) — couvre COR-10-S1, COR-12-S1 — vérification : `argos-ui/tests/traitement-impact.test.mjs` passe ; `npm --prefix argos-ui test && npm --prefix argos-ui run build` réussissent — preuve 2026-11-19 (branche `lot18/impacts-performance`) : `argos-ui/tests/traitement-impact.test.mjs` : 8 tests verts ; `npm --prefix argos-ui test` (192 tests, 0 échec) et `npm --prefix argos-ui run build` réussissent.
- [x] 3.6 Écrire la campagne de mesure p95 sans dépendance (`e2e/performance/mesurer-p95.mjs`, script `test:performance`, garde réseau, opérations retenues et exclues, rapport JSON et Markdown) — couvre PERF-02-S1 à PERF-02-S3, PERF-01-S3 — vérification : test de la garde réseau et du calcul des centiles (`node --test e2e/performance/`) passe ; `npm --prefix e2e test` ne lance pas la campagne — preuve 2026-11-19 (branche `lot18/impacts-performance`) : `node --test "e2e/performance/*.test.mjs"` : 7 tests verts (garde réseau, centiles, verdict, exclusions, rapport, campagne absente de `npm --prefix e2e test`, aucune dépendance). Écart : Node 24 refuse un dossier comme argument de `node --test`, d'où le motif de fichiers.
- [x] 3.7 Exécuter la campagne sur la pile de test locale (verrou E2E du dépôt) et versionner le rapport daté `docs/architecture/quality/mesure-performance-api-mcp.md` avec `e2e/resultats/performance/` ; reporter le résultat dans arc42 §10.2 Q-05 et QS-06 — couvre PERF-01-S1, PERF-01-S2 — vérification : rapport présent, code de sortie consigné, valeurs de Q-PERF-1 et Q-PERF-2 écrites — preuve 2026-11-19 (branche `lot18/impacts-performance`) : campagne exécutée sous le verrou E2E (base, serveur et Keycloak arrêtés ensuite) : rapport `docs/architecture/quality/mesure-performance-api-mcp.md` et `e2e/resultats/performance/rapport-2026-11-19.json`, code de sortie 1 consigné : 15 opérations sur 16 dans la cible, `GET /api/v1/me/veille` hors cible (p95 = 618 ms ; PERF-01-S2 constaté, PERF-01-S1 non atteint, P-18) ; valeurs retenues pour Q-PERF-1 (20 projets × 100 composants, 1 000 avis, 20 000 impacts) et Q-PERF-2 (4 clients simultanés) écrites ; arc42 §10.2 Q-05 et QS-06 mis à jour.
- [x] 3.8 Mettre à jour la documentation du volet : fiches `correlation-impact` (DF-COR-4, preuves), `alertes-digests` (ALR-07), `performance-api-mcp` (preuves), glossaire « État de traitement d'un impact », arc42 §8.7 (V20), registre (couverture du volet) et matrice ci-dessous — vérification : `node scripts/verifier-docs.mjs` sans erreur — preuve 2026-11-19 (branche `lot18/impacts-performance`) : `node scripts/verifier-docs.mjs` sans erreur ; fiches `correlation-impact`, `alertes-digests`, `performance-api-mcp`, glossaire (« Alerte », « Alerte annulée » ; « État de traitement d'un impact » déjà aligné), arc42 §5.2, §8.7 (V20), §10.2, D-13, registre et matrice mis à jour.

## 4. Vérifications d'intégration

- [ ] 4.1 Intégrer les trois volets sur une même branche et appliquer V18 à V20 sur une base neuve — vérification : `mvn -B -f argos-server/pom.xml verify` passe (Flyway, ArchUnit, RLS, tous les tests)
- [ ] 4.2 Vérifier l'IHM intégrée — vérification : `npm --prefix argos-ui test && npm --prefix argos-ui run build` réussissent
- [ ] 4.3 Parcours E2E de la chaîne automatisée sur la pile locale (verrou E2E) : synchronisation planifiée déclenchée avec OSV simulé, import planifié d'un projet à deux dépôts avec forges simulées, impact créé puis confirmé par un contributeur, composant retiré par un nouvel import et impact résolu, abonnement suspendu sans alerte puis réactivé ; aucun hôte non local — couvre COL-09-S1, INV-07-S1, INV-09-S1, INV-10-S1, COR-10-S1, COR-11-S1, ABO-07-S1, ABO-07-S2 — vérification : `e2e/tests/chaine-automatisee.spec.ts` et les parcours existants passent avec `npm --prefix e2e test`, résultats consignés dans `e2e/resultats/`
- [ ] 4.4 Valider la documentation et OpenSpec — vérification : `openspec validate automatiser-chaine-veille --strict`, `openspec validate --all --strict` et `node scripts/verifier-docs.mjs` réussissent ; registre et fiches cohérents avec la matrice

## Matrice de traçabilité

État au 2026-11-19 (cadrage) : aucune implémentation ; tous les tests sont à écrire. Mise à jour du 2026-11-19, volet C (branche `lot18/impacts-performance`) : lignes COR-09 à COR-12, ALR-07, PERF-01 et PERF-02.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| COL-09 | COL-09-S1 | D1, DC-3 | 1.2, 4.3 | `chain/SynchronisationPlanifieeTest` ; `e2e/tests/chaine-automatisee.spec.ts` | à écrire |
| COL-09 | COL-09-S2 | D1, DC-3, Transactions | 1.2 | `chain/SynchronisationPlanifieeTest` | à écrire |
| COL-09 | COL-09-S3 | D1, Contrats (tâches planifiées) | 1.2 | `chain/SynchronisationPlanifieeTest` | à écrire |
| COL-09 | COL-09-S4 | D1, Gestion des erreurs | 1.2 | `chain/SynchronisationPlanifieeTest` | à écrire |
| COL-09 | COL-09-S5 | D1, DC-3 | 1.3 | `chain/SynchronisationPlanifieeTest` | à écrire |
| COL-10 | COL-10-S1 | D2, DC-3 | 1.4 | `ingestion/EmpreinteNormaliseeTest` | à écrire |
| COL-10 | COL-10-S2 | D2 | 1.4 | `ingestion/EmpreinteNormaliseeTest` | à écrire |
| COL-10 | COL-10-S3 | D2, DC-3 | 1.4 | `chain/CollecteTest` | à écrire |
| COL-10 | COL-10-S4 | D2, Persistance (V18) | 1.1, 1.4 | `chain/CollecteTest` | à écrire |
| ABO-07 | ABO-07-S1 | D7, DC-7 | 1.5, 4.3 | `chain/SubscriptionTest` ; `e2e/tests/chaine-automatisee.spec.ts` | à écrire |
| ABO-07 | ABO-07-S2 | D7, DC-7 | 1.5, 4.3 | `chain/SubscriptionTest` | à écrire |
| ABO-07 | ABO-07-S3 | D7, Sécurité | 1.1, 1.5 | `chain/SubscriptionTest`, `architecture/SubscriptionIsolationTest` | à écrire |
| ABO-07 | ABO-07-S4 | D7, Contrats | 1.5, 1.6 | `chain/SubscriptionTest`, `argos-ui/tests/abonnements.test.mjs` | à écrire |
| ABO-08 | ABO-08-S1 | D7, DC-7 | 1.5, 1.6 | `chain/SubscriptionTest`, `argos-ui/tests/abonnements.test.mjs` | à écrire |
| ABO-08 | ABO-08-S2 | D7, Sécurité | 1.1, 1.5 | `architecture/SubscriptionIsolationTest` | à écrire |
| ABO-08 | ABO-08-S3 | D7, Persistance (V18) | 1.5 | `chain/SubscriptionTest` | à écrire |
| INV-07 | INV-07-S1 | D3, DC-4 | 2.3, 4.3 | `chain/ImportPlanifieTest` ; `e2e/tests/chaine-automatisee.spec.ts` | à écrire |
| INV-07 | INV-07-S2 | D3, Gestion des erreurs | 2.3 | `chain/ImportPlanifieTest` | à écrire |
| INV-07 | INV-07-S3 | D3, Contrats | 2.3 | `chain/ImportPlanifieTest` (inventaire des routes) | à écrire |
| INV-07 | INV-07-S4 | D3, Contrats (tâches planifiées) | 2.3 | `chain/ImportPlanifieTest` | à écrire |
| INV-08 | INV-08-S1 | D3, DC-4 | 2.3 | `chain/ImportPlanifieTest` | à écrire |
| INV-08 | INV-08-S2 | D3, Transactions | 2.3 | `chain/ImportPlanifieTest` (imports concurrents) | à écrire |
| INV-09 | INV-09-S1 | D4, DC-4 | 2.4, 4.3 | `chain/RecorrelationImportTest` | à écrire |
| INV-09 | INV-09-S2 | D4, DC-4 | 2.4, 3.3 | `chain/RecorrelationImportTest` | à écrire |
| INV-09 | INV-09-S3 | D4 | 2.4 | `chain/RecorrelationImportTest` | à écrire |
| INV-09 | INV-09-S4 | D4, Transactions, P-10 | 2.4 | `chain/RecorrelationImportTest` | à écrire |
| INV-10 | INV-10-S1 | D6, DC-2, DC-6 | 2.2, 2.5, 4.3 | `chain/ProjetMultiDepotsTest`, `argos-ui/tests/inventaire-depots.test.mjs` | à écrire |
| INV-10 | INV-10-S2 | D6, DC-6 | 2.2 | `chain/ProjetMultiDepotsTest` | à écrire |
| INV-10 | INV-10-S3 | D6, DC-6 | 2.2 | `chain/ProjetMultiDepotsTest` | à écrire |
| INV-10 | INV-10-S4 | D6, Persistance (V19) | 2.1 | test de migration V19 | à écrire |
| COR-09 | COR-09-S1 | D5, DC-2, DC-5 | 3.1, 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| COR-09 | COR-09-S2 | D5, DC-5 | 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| COR-09 | COR-09-S3 | D5, DC-5 | 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| COR-10 | COR-10-S1 | D5, DC-5, Contrats | 3.2, 3.5, 4.3 | `chain/EtatTraitementImpactTest`, `argos-ui/tests/traitement-impact.test.mjs` ; E2E (4.3) à écrire | vérifié 2026-11-19 (hors E2E) |
| COR-10 | COR-10-S2 | D5, Sécurité | 3.1, 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| COR-10 | COR-10-S3 | D5, DC-5, Transactions | 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| COR-10 | COR-10-S4 | D5, Contrats | 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| COR-10 | COR-10-S5 | D5, Sécurité | 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| COR-11 | COR-11-S1 | D4, D5, DC-4, DC-5 | 3.3, 4.3 | `chain/CorrelationTest#cor11S1ComposantRetireDeLInventaire`, `chain/EtatTraitementImpactTest#cor12S1HistoriqueConsulteParUnLecteur` ; E2E (4.3) à écrire | vérifié 2026-11-19 (hors E2E) |
| COR-11 | COR-11-S2 | D4, D5, DC-4 | 3.3 | `chain/CorrelationTest#cor11S2ComposantMisAJourHorsPlage` | vérifié 2026-11-19 |
| COR-11 | COR-11-S3 | D5, P-3 | 3.3 | `chain/CorrelationTest` (avis modifié, avis retiré) | vérifié 2026-11-19 |
| COR-11 | COR-11-S4 | D5, DC-5, P-6 | 3.3 | `chain/CorrelationTest#cor11S4ImpactEcarteJamaisModifieParLeSysteme` | vérifié 2026-11-19 |
| COR-11 | COR-11-S5 | D5, DC-5, P-6 | 3.3 | `chain/CorrelationTest#cor11S3AvisModifieEtCor11S5ImpactResoluDeNouveauVerifie` | vérifié 2026-11-19 |
| COR-12 | COR-12-S1 | D5, Contrats | 3.2, 3.5 | `chain/EtatTraitementImpactTest`, `argos-ui/tests/traitement-impact.test.mjs` | vérifié 2026-11-19 |
| COR-12 | COR-12-S2 | D5, Contrats | 3.2 | `chain/EtatTraitementImpactTest` | vérifié 2026-11-19 |
| ALR-07 | ALR-07-S1 | D5, DC-1, DC-5 | 3.4 | `alerting/AlertesImpactsTraitesTest` | vérifié 2026-11-19 |
| ALR-07 | ALR-07-S2 | D5, DC-5 | 3.4 | `alerting/AlertesImpactsTraitesTest` | vérifié 2026-11-19 |
| PERF-01 | PERF-01-S1 | D8, DC-8 | 3.7 | rapport `docs/architecture/quality/mesure-performance-api-mcp.md` (2026-11-19) | non atteint : `GET /api/v1/me/veille` hors cible (P-18) |
| PERF-01 | PERF-01-S2 | D8, DC-8 | 3.6, 3.7 | `e2e/performance/mesure.test.mjs` ; rapport du 2026-11-19 (code 1) | vérifié 2026-11-19 |
| PERF-01 | PERF-01-S3 | D8, Performance | 3.6 | `e2e/performance/mesure.test.mjs` ; rapport (opérations exclues) | vérifié 2026-11-19 |
| PERF-02 | PERF-02-S1 | D8, DC-8 | 3.6, 3.7 | `e2e/performance/mesure.test.mjs` ; `e2e/resultats/performance/rapport-2026-11-19.json` et rapport Markdown | vérifié 2026-11-19 |
| PERF-02 | PERF-02-S2 | D8 | 3.6 | `e2e/performance/mesure.test.mjs` (scripts, configuration Playwright) ; `mvn verify` et `npm --prefix argos-ui test` sans campagne | vérifié 2026-11-19 |
| PERF-02 | PERF-02-S3 | D8, Sécurité | 3.6 | `e2e/performance/mesure.test.mjs` (garde réseau) | vérifié 2026-11-19 |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue, de préférence après `formaliser-chaine-securite-poc` (`openspec archive automatiser-chaine-veille`).
- Après archivage, vérifier que la section Purpose de la nouvelle spec principale `performance-api-mcp` contient le lien vers la fiche durable.
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/automatiser-chaine-veille/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs` (il signale ces liens cassés).
