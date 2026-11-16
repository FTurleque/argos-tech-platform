# Tâches : formaliser la chaîne sécurité du POC et la personnalisation de la veille

<!-- Le code de la chaîne vit dans argos-server/ (paquets io.argos.ingestion, signal, vulnintel, scm, inventory, impact, workspace, audit, api) ; les tests sont sous argos-server/src/test/java/io/argos/chain/ et architecture/. Preuve commune : `mvn -B -f argos-server/pom.xml verify` (PostgreSQL 18 jetable via Dev Services, sources externes simulées), 82 tests, 0 échec, le 2026-11-16. -->

## 1. Arbitrages bloquants

- [x] 1.1 Faire définir les règles des niveaux `probable` et `à vérifier` (Q-2) et les reporter dans COR-02 par un MODIFIED avant archivage — vérification : décision consignée dans la fiche `correlation-impact` et `openspec validate formaliser-chaine-securite-poc --strict` réussit (décidé le 2026-11-16)
- [x] 1.2 Faire fixer le seuil d'obsolescence d'une SBOM (Q-3) — couvre INV-03, COR-06 — vérification : valeur et source inscrites dans la fiche `inventaire-sbom` (décidé le 2026-11-16)
- [x] 1.3 Faire fixer le nombre maximal de tentatives et le seuil `DEGRADED` (Q-4) — couvre COL-06, COL-07 — vérification : valeurs inscrites dans la fiche `collecte-avis-securite` (décidé le 2026-11-16)
- [x] 1.4 Faire décider qui crée un abonnement `TEAM` et si un `TEAM` obligatoire peut être quitté (Q-5) — couvre ABO-03 — vérification : décision inscrite dans la fiche `abonnements-veille` et, si elle crée une règle, exigence ajoutée au delta (décidé le 2026-11-16)
- [x] 1.5 Faire fixer les versions de schéma CycloneDX acceptées (Q-7) — couvre INV-01, INV-05 — vérification : liste inscrite dans la fiche `inventaire-sbom` (décidé le 2026-11-16)
- [x] 1.6 Faire valider ou rejeter les interprétations listées dans chaque fiche (« Interprétations introduites par un changement ») — vérification : chaque interprétation marquée validée ou retirée du delta (décidé le 2026-11-16)

## 2. Collecte des avis de sécurité

- [x] 2.1 Créer les migrations `SOURCE`, `SIGNAL`, `SIGNAL_VERSION`, `ENRICHMENT`, `SYNC_CURSOR`, `INGESTION_FAILURE` avec la contrainte d'unicité (source, external_id) — couvre COL-02, COL-03 — vérification : test d'intégration PostgreSQL d'unicité passe
- [x] 2.2 Implémenter l'adaptateur OSV et la normalisation en signal dans `source-ingestion` / `signal` — couvre COL-01-S1, COL-01-S2, COL-02-S1 — vérification : test de contrat sur réponses OSV enregistrées passe
- [x] 2.3 Implémenter idempotence et historisation — couvre COL-03-S1, COL-03-S2, COL-03-S3 — vérification : tests d'intégration rejeu, modification et concurrence passent
- [x] 2.4 Implémenter l'enrichissement NVD / KEV / EPSS / CERT-FR dans `vuln-intel` — couvre COL-04-S1 à COL-04-S3 — vérification : tests de contrat, dont source secondaire indisponible, passent
- [x] 2.5 Implémenter curseur durable, retry borné, `Retry-After`, erreurs permanentes et état `DEGRADED` — couvre COL-05, COL-06, COL-07 — vérification : tests de résilience (429, 5xx, timeout, arrêt forcé) passent ; QS-03 et QS-07 exécutés
- [x] 2.6 Implémenter la zone d'échec — couvre COL-02-S2, COL-08-S1 — vérification : test d'un lot contenant un avis malformé passe
- [x] 2.7 Mettre à jour la fiche `collecte-avis-securite` (preuves, statuts) et le glossaire (« Signal de sécurité ») — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Inventaire SBOM

- [x] 3.1 Créer les migrations `INVENTORY`, `COMPONENT` — couvre INV-01, INV-02 — vérification : test d'intégration PostgreSQL passe
- [ ] 3.2 Implémenter la récupération des SBOM et manifestes dans `scm-connector` — couvre INV-01, INV-04 — vérification : test de contrat GitLab/GitHub enregistré passe
- [x] 3.3 Implémenter validation et import CycloneDX, transitivité et remplacement — couvre INV-01-S1, INV-01-S2, INV-02-S1, INV-05-S1 — vérification : tests avec SBOM valides, transitives et invalides passent
- [x] 3.4 Implémenter âge, absence de SBOM et repli manifestes — couvre INV-03-S1, INV-03-S2, INV-04-S1, INV-04-S2 — vérification : tests d'intégration passent
- [x] 3.5 Mettre à jour la fiche `inventaire-sbom` et le glossaire (« Inventaire », « Composant ») — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Corrélation d'impact

- [x] 4.1 Créer la migration `PROJECT_IMPACT` avec unicité (signal, projet, composant) et politique RLS par espace — couvre COR-03, COR-07, COR-08 — vérification : test d'intégration unicité et RLS passe
- [x] 4.2 Implémenter la règle PURL + plage de versions dans `impact-engine` — couvre COR-01-S1 à COR-01-S4 — vérification : tests unitaires des règles passent
- [x] 4.3 Implémenter niveaux et confiance réduite selon les règles fixées en 1.1 et 1.2 — couvre COR-02-S1, COR-06-S1 — vérification : tests unitaires passent
- [x] 4.4 Implémenter provenance et contrôle d'intégrité — couvre COR-03-S1, COR-03-S2 — vérification : test d'intégrité « aucun impact orphelin » passe ; QS-01 exécuté
- [x] 4.5 Garantir l'indépendance vis-à-vis de l'IA (décision D3) — couvre COR-04-S1, COR-05-S1, COR-05-S2 — vérification : tests avec `ai-gateway` simulé indisponible ou plafonné passent ; QS-10 exécuté
- [x] 4.6 Implémenter la consultation filtrée et la journalisation des refus — couvre COR-08-S1, COR-08-S2 — vérification : tests inter-espaces REST passent
- [ ] 4.7 Constituer le jeu de référence annoté et mesurer précision et rappel — couvre COR-01, COR-02 — vérification : rapport de mesure versionné (baseline v2.3 §6 critère 3)
- [x] 4.8 Mettre à jour la fiche `correlation-impact` (preuves, niveaux définis) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Abonnements de veille

- [x] 5.1 Créer la migration `SUBSCRIPTION` et la validation des attributs obligatoires — couvre ABO-01-S1, ABO-02-S1, ABO-02-S2 — vérification : tests d'API passent
- [x] 5.2 Implémenter l'héritage du socle `TEAM` — couvre ABO-03-S1 — vérification : test d'intégration passe
- [x] 5.3 Implémenter les abonnements `PERSONAL` et la restitution de la veille sous RLS — couvre ABO-04-S1, ABO-05-S1, ABO-05-S2 — vérification : tests inter-espaces passent
- [x] 5.4 Ajouter la règle ArchUnit interdisant à `workspace` l'accès aux rôles (INV-D7) — couvre ABO-05 — vérification : test d'architecture passe
- [x] 5.5 Mettre à jour la fiche `abonnements-veille` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Documentation d'architecture et décisions

- [ ] 6.1 Renommer ou annoter les participants des diagrammes D-06 à D-09 selon la table de correspondance de arc42 §6, après décision — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 6.2 Produire D-11 (déploiement cible), D-12 (frontières de confiance) et D-13 (modèle physique) à partir du schéma réellement migré, ou les retirer de l'inventaire — vérification : inventaire `/docs/architecture/diagrams/README.md` conforme aux fichiers
- [ ] 6.3 Rédiger les ADR candidats signalés en D4 (planification/jobs, outil de migration) au statut Proposé et les indexer en arc42 §9 — vérification : index ADR et arc42 §9 synchronisés

## 7. Vérifications d'intégration

- [ ] 7.1 Exécuter la chaîne avis critique de test → signal → impact sur les deux projets pilotes — couvre baseline v2.3 §6 critères 1 à 3 — vérification : scénario E2E consigné avec ses résultats
- [x] 7.2 Valider le changement — vérification : `openspec validate formaliser-chaine-securite-poc --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur

## Matrice de traçabilité

État au 2026-11-16, code intégré avec `gerer-comptes-et-acces` et `personnaliser-profils-et-affichage` : preuves obtenues par `mvn -B -f argos-server/pom.xml verify` (164 tests, 0 échec), avec des jetons réels du Keycloak des Dev Services, des comptes et des espaces réels (tables `compte`, `espace`) et la RLS appliquée sous le rôle non super-utilisateur `argos_app`. Chemins de test relatifs à `argos-server/src/test/java/io/argos/`. « passe » signifie exécuté avec succès ce jour-là.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| COL-01 | COL-01-S1, COL-01-S2 | DF-COL-1, DC-1 | 2.2 | `chain/DocumentParsingTest#contratOsvSurReponseEnregistree` (réponse OSV réelle GHSA-jfh8-c2jp-5v3q enregistrée), `chain/CollecteTest#col01S1EtCol02S1…`, `#col01S2AucunNouvelAvis` | passe |
| COL-02 | COL-02-S1, COL-02-S2 | DF-COL-2, DC-2 | 2.1, 2.2, 2.6 | `chain/CollecteTest#col01S1EtCol02S1…` (REST : source, identifiant, date, contenu brut), `chain/DocumentParsingTest#col02S2…`, `chain/CollecteTest#col08S1EtCol02S2…` | passe |
| COL-03 | COL-03-S1, COL-03-S2, COL-03-S3 | DF-COL-2, design « Transactions » | 2.1, 2.3 | `chain/CollecteTest#migrationImposeLUniciteSourceEtIdentifiantExterne`, `#col03S1EtS2…`, `#col03S3CollectesConcurrentesDuMemeAvis` (4 fils) | passe |
| COL-04 | COL-04-S1, COL-04-S2, COL-04-S3 | DF-COL-1 | 2.4 | `chain/EnrichmentTest` (réponses NVD, KEV, EPSS, CERT-FR enregistrées ; EPSS 503 puis rattrapage) | passe |
| COL-05 | COL-05-S1, COL-05-S2 | DF-COL-1, DC-3 | 2.5 | `chain/ResilienceTest#col05S1…`, `#col05S2ArretBrutalDuBackendPuisReprise` (QS-07 : arrêt simulé, reprise du même traitement depuis le curseur), `#traitementRunningExpireRepris`, `#erreurInattendueTermineLeTraitementEnEchec` (aucun traitement laissé `RUNNING`) ; QS-03 exécuté sur la source OSV simulée (429, 5xx, délai dépassé) | passe |
| COL-06 | COL-06-S1, COL-06-S2, COL-06-S3 | DC-3 | 1.3, 2.5 | `chain/ResilienceTest#col06S1…` (Retry-After 7 s respecté), `#retryAfterBorneParLeDelaiMaximal` (Retry-After de 24 h borné à 5 min), `#col06S2…` (5 tentatives, curseur inchangé), `#delaiDepasse…`, `#col06S3…` | passe |
| COL-07 | COL-07-S1, COL-07-S2 | DF-COL-3 | 1.3, 2.5 | `chain/ResilienceTest#col07S1EtS2…` (DEGRADED après 3 échecs, événement daté, REST exploitation) | passe |
| COL-08 | COL-08-S1 | DF-COL-2 | 2.6 | `chain/CollecteTest#col08S1EtCol02S2…` | passe |
| INV-01 | INV-01-S1, INV-01-S2 | DF-INV-1, DC-2 | 1.5, 3.1, 3.2, 3.3 | `chain/InventoryTest#inv01S1EtInv02S1…`, `#inv01S2…`, `#migrationIdentiteStableDesComposants`, `chain/DocumentParsingTest#sbomValideAvecTransitivite` | passe ; récupération GitLab/GitHub testée sur réponses simulées seulement (3.2) |
| INV-02 | INV-02-S1 | DF-INV-1 | 3.3 | `chain/InventoryTest#inv01S1EtInv02S1…` (REST : transitif) | passe |
| INV-03 | INV-03-S1, INV-03-S2, INV-03-S3 | DF-INV-2 | 1.2, 3.4 | `chain/InventoryTest#inv03S1EtS2…`, `#sbomDePlusDeTrenteJoursObsolete` | passe |
| INV-04 | INV-04-S1, INV-04-S2 | DF-INV-2 | 3.2, 3.4 | `chain/InventoryTest#inv04S1EtS2…` (manifeste puis SBOM via GitLab simulé) | passe |
| INV-05 | INV-05-S1 | DF-INV-1 | 3.3 | `chain/InventoryTest#inv05S1…`, `chain/DocumentParsingTest#sbomNonConformeRefuseeAvecMotif` | passe |
| COR-01 | COR-01-S1 à COR-01-S4 | DF-COR-1, DF-COR-2 | 4.2, 4.7 | `chain/MatchRulesTest` (dont `#postReleasesPyPi` et `#qualificatifInconnuNonClasseCommePreVersion`), `chain/CorrelationTest#avisRetireSansImpact` (avis OSV `withdrawn`), `chain/CorrelationTest#cor01S1EtS2EtS4…`, `#cor01S3DependanceTransitive` | passe ; jeu de référence annoté (4.7) absent |
| COR-02 | COR-02-S1, COR-02-S2, COR-02-S3 | DF-COR-3 | 1.1, 4.3 | `chain/MatchRulesTest`, `chain/CorrelationTest#cor02S1EtCor06S1…`, `#aVerifierPourUnAvisSansPurl` | passe |
| COR-03 | COR-03-S1, COR-03-S2 | DF-COR-1, DC-2 | 4.1, 4.4 | `chain/CorrelationTest#cor03S1EtS2…` (QS-01 : provenance par REST, contrôle d'intégrité) | passe |
| COR-04 | COR-04-S1 | DF-COR-2, décision D3 | 4.5 | `chain/CorrelationTest#cor04S1…` (suggestion IA simulée sans impact créé) | passe ; règles d'alerte hors lot (alerting) |
| COR-05 | COR-05-S1, COR-05-S2 | DF-COR-2, décision D3 | 4.5 | `chain/CorrelationTest#cor05S1…`, `#cor05S2…` (QS-10 : IA simulée indisponible ou plafonnée) | passe |
| COR-06 | COR-06-S1 | DF-COR-3, DF-INV-2 | 1.2, 4.3 | `chain/CorrelationTest#cor02S1EtCor06S1…` (manifeste et SBOM de 40 jours : `probable`) | passe |
| COR-07 | COR-07-S1 | DC-2, design « Transactions » | 4.1 | `chain/CorrelationTest#cor07S1…`, `#migrationUniciteDuTriplet` | passe |
| COR-08 | COR-08-S1, COR-08-S2 | DF-ABO-2, design « Sécurité » | 4.1, 4.6 | `chain/CorrelationTest#cor08S1EtS2…` (REST, refus journalisé dans `refus_acces`), `#rlsGardeFouEnBase` (politique sous `argos_app`), `#rlsAppliqueeSurLeCheminApplicatif` (requêtes de l'application exécutées sous `argos_app` : un périmètre erroné ne révèle rien), `chain/InventoryTest#inventaireDUnAutreEspace…` | passe |
| ABO-01 | ABO-01-S1 | DF-ABO-1 | 5.1 | `chain/SubscriptionTest#abo01S1…` | passe |
| ABO-02 | ABO-02-S1, ABO-02-S2 | DF-ABO-1, DC-2 | 5.1 | `chain/SubscriptionTest#abo02S1…`, `#abo02S2…` | passe |
| ABO-03 | ABO-03-S1 | DF-ABO-1 | 1.4, 5.2 | `chain/SubscriptionTest#abo03S1…` | passe |
| ABO-04 | ABO-04-S1 | DF-ABO-2 | 5.3 | `chain/SubscriptionTest#abo04S1…` | passe |
| ABO-05 | ABO-05-S1, ABO-05-S2 | DF-ABO-1, DF-ABO-2 | 5.3, 5.4 | `chain/SubscriptionTest#abo05S1EtS2…` (appartenances réelles, restitution et impacts lus sous `argos_app`), `architecture/SubscriptionIsolationTest` (INV-D7) | passe |
| ABO-06 | ABO-06-S1, ABO-06-S2, ABO-06-S3 | DF-ABO-1 | 1.4, 5.1, 5.2 | `chain/SubscriptionTest#abo06Seul…`, `#abo06TeamObligatoire…` | passe |

Limites connues, non couvertes par une tâche : les niveaux d'impact sont enregistrés à la corrélation et ne sont pas recalculés quand une SBOM dépasse 30 jours sans nouvelle corrélation ; la première synchronisation OSV sans curseur n'est pas bornée en volume (fiches `correlation-impact` et `collecte-avis-securite`, « Écarts constatés »).

Tâches non réalisées et raison :

- 3.2 : adapters GitLab et GitHub implémentés (`io.argos.scm.RepositoryFiles`) et testés sur réponses simulées (`chain/InventoryTest#contratGitLabEtGitHub`) ; aucune réponse réelle de forge n'est enregistrée faute de dépôt pilote accessible.
- 4.7 : le jeu de référence annoté exige les inventaires des projets pilotes (baseline v2.3 §6 critère 3).
- 6.1 : la décision de renommer ou d'annoter les participants de D-06 à D-09 n'est pas prise.
- 6.2 : D-11 et D-12 relèvent du déploiement et des frontières de confiance, hors du code de ce lot ; D-13 reste à produire depuis les migrations V2 à V7.
- 6.3 : les ADR candidats (planification des traitements, outil de migration) ne sont pas rédigés ; Flyway reste le choix provisoire d'`argos-server/pom.xml` et aucune planification automatique n'est installée.
- 7.1 : la chaîne de bout en bout sur les deux projets pilotes exige ces projets.

## Workflow follow-up

- Basculer dans les quatre fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] (après validation des ADR et arbitrages) ou en [EXISTANT] (après preuve par test).
- Archiver le changement uniquement sur demande explicite, après revue et levée des arbitrages du groupe 1 (`openspec archive formaliser-chaine-securite-poc`).
- Après archivage, vérifier que la section Purpose des quatre specs principales contient le lien vers leur fiche durable.
- Après archivage, remplacer dans les fiches et dans `/docs/specifications/README.md` les liens vers `/openspec/changes/formaliser-chaine-securite-poc/` par les specs principales ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
