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
  - [DÉCISION] La vérification attend une capture sur un dépôt pilote réel ; les réponses enregistrées sur des dépôts publics ne suffisent pas ; la tâche 3.2 reste ouverte — porteur du projet, 2026-11-18.
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
- [ ] 4.7 Constituer le jeu de référence annoté et mesurer précision et rappel — couvre COR-01, COR-02 — vérification : rapport de mesure versionné (baseline v2.3 §6 critère 3) (mesure partielle du 2026-11-18 : [rapport](/docs/architecture/quality/mesure-correlation-jeu-reference.md))
  - [DÉCISION] Le jeu composé ne suffit pas au critère 3 : la mesure se fait sur les inventaires des projets pilotes, avec la tâche 7.1 ; la tâche 4.7 reste ouverte — porteur du projet, 2026-11-18.
- [x] 4.8 Mettre à jour la fiche `correlation-impact` (preuves, niveaux définis) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Abonnements de veille

- [x] 5.1 Créer la migration `SUBSCRIPTION` et la validation des attributs obligatoires — couvre ABO-01-S1, ABO-02-S1, ABO-02-S2 — vérification : tests d'API passent
- [x] 5.2 Implémenter l'héritage du socle `TEAM` — couvre ABO-03-S1 — vérification : test d'intégration passe
- [x] 5.3 Implémenter les abonnements `PERSONAL` et la restitution de la veille sous RLS — couvre ABO-04-S1, ABO-05-S1, ABO-05-S2 — vérification : tests inter-espaces passent
- [x] 5.4 Ajouter la règle ArchUnit interdisant à `workspace` l'accès aux rôles (INV-D7) — couvre ABO-05 — vérification : test d'architecture passe
- [x] 5.5 Mettre à jour la fiche `abonnements-veille` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Documentation d'architecture et décisions

- [x] 6.1 Renommer ou annoter les participants des diagrammes D-06 à D-09 selon la table de correspondance de arc42 §6, après décision — vérification : `node scripts/verifier-docs.mjs` sans erreur
  - [DÉCISION] Les participants de D-06 à D-09 portent les noms des composants d'arc42 §5 ; la table de correspondance d'arc42 §6 est retirée — porteur du projet, 2026-11-18.
  - Le 2026-11-18 : participants de D-06 à D-09 renommés (`source-ingestion`, `signal / vuln-intel / release-radar`, `impact-engine`, `inventory`, `workspace`, `ai-gateway`, `PostgreSQL`, `alerting`, `project-intel`, `scm-connector`, conteneur `Quarkus Backend`) ; une tâche planifiée figure comme un appel du module qui la porte (ADR-0031) et les sources externes comme systèmes externes ; D-16, D-17 et D-19, de la même section, suivent les mêmes noms. Table et phrase d'introduction retirées d'arc42 §6 ; inventaire `docs/architecture/diagrams/README.md` et fiches `collecte-avis-securite`, `correlation-impact` alignés. `node scripts/verifier-docs.mjs` : 111 fichiers, 60 blocs Mermaid, aucune erreur ; rendu par Mermaid CLI non exécuté (`mmdc` absent du poste).
- [x] 6.2 Produire D-11 (déploiement cible), D-12 (frontières de confiance) et D-13 (modèle physique) à partir du schéma réellement migré, ou les retirer de l'inventaire — vérification : inventaire `/docs/architecture/diagrams/README.md` conforme aux fichiers
- [x] 6.3 Rédiger les ADR candidats signalés en D4 (planification/jobs, outil de migration) au statut Proposé et les indexer en arc42 §9 — vérification : index ADR et arc42 §9 synchronisés

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
| INV-01 | INV-01-S1, INV-01-S2 | DF-INV-1, DC-2 | 1.5, 3.1, 3.2, 3.3 | `chain/InventoryTest#inv01S1EtInv02S1…`, `#inv01S2…`, `#migrationIdentiteStableDesComposants`, `chain/DocumentParsingTest#sbomValideAvecTransitivite`, `chain/ScmContractTest#gitlabArtefactCycloneDxDuJobEtJetonPrivateToken`, `#githubContenuBrutAvecJetonBearer`, `#githubSpdxEtCycloneDxXmlReconnus`, `#inventaireDepuisGitlabAvecTransitivite`, `#sbomSpdxRefuseeAvecMotifEtInventaireConserve` (réponses composées de `fixtures/scm/`), `chain/ScmContratEnregistreTest#githubSbomTrouveeEtTransmiseAvecJetonBearer`, `#githubSbomEnregistreeImporteeDansLInventaire`, `#gitlabSbomTrouveeAvecJetonPrivateToken` (réponses enregistrées de `fixtures/scm/enregistrees/`, forges simulées par le serveur HTTP local) | passe le 2026-11-18 ; réponses enregistrées sur des dépôts publics de `api.github.com` et `gitlab.com`, artefact SBOM GitLab composé |
| INV-02 | INV-02-S1 | DF-INV-1 | 3.3 | `chain/InventoryTest#inv01S1EtInv02S1…` (REST : transitif) | passe |
| INV-03 | INV-03-S1, INV-03-S2, INV-03-S3 | DF-INV-2 | 1.2, 3.4 | `chain/InventoryTest#inv03S1EtS2…`, `#sbomDePlusDeTrenteJoursObsolete` | passe |
| INV-04 | INV-04-S1, INV-04-S2 | DF-INV-2 | 3.2, 3.4 | `chain/InventoryTest#inv04S1EtS2…` (manifeste puis SBOM via GitLab simulé), `chain/ScmContractTest#gitlabFichierBrutEt404Absent`, `#github404AbsentEt403QuotaEpuise`, `#sbomSpdxRefuseeAvecMotifEtInventaireConserve` (repli sur `requirements.txt` GitHub), `#redirectionNonSuivieEtJetonAbsentDuMessage`, `#reponseAuDelaDeLaTailleMaximaleRefusee`, `#cheminsHostilesNeSortentPasDuDepot`, `chain/ScmContratEnregistreTest#githubSbomAbsenteRepliSurLeManifeste`, `#gitlabArtefactAbsentRepliSurLeManifeste`, `#githubDepotIntrouvable`, `#githubDepotIntrouvableRafraichissementEnErreurSansInventaire`, `#gitlabProjetIntrouvable`, `#githubJetonRefuse`, `#gitlabJetonRefuseEtArtefactInterdit`, `#githubLimiteDeDebitPrincipaleEtSecondaire`, `#gitlabLimiteDeDebitInventaireConserve` | passe le 2026-11-18 ; limites de débit composées d'après la documentation des forges |
| INV-05 | INV-05-S1 | DF-INV-1 | 3.3 | `chain/InventoryTest#inv05S1…`, `chain/DocumentParsingTest#sbomNonConformeRefuseeAvecMotif` | passe |
| COR-01 | COR-01-S1 à COR-01-S4 | DF-COR-1, DF-COR-2 | 4.2, 4.7 | `chain/MatchRulesTest` (dont `#postReleasesPyPi` et `#qualificatifInconnuNonClasseCommePreVersion`), `chain/CorrelationTest#avisRetireSansImpact` (avis OSV `withdrawn`), `chain/CorrelationTest#cor01S1EtS2EtS4…`, `#cor01S3DependanceTransitive`, `chain/JeuReferenceCorrelationTest` (jeu de référence annoté) | passe ; le 2026-11-18, précision et rappel de 98,1 % tous niveaux ([rapport](/docs/architecture/quality/mesure-correlation-jeu-reference.md)) |
| COR-02 | COR-02-S1, COR-02-S2, COR-02-S3 | DF-COR-3 | 1.1, 4.3, 4.7 | `chain/MatchRulesTest`, `chain/CorrelationTest#cor02S1EtCor06S1…`, `#aVerifierPourUnAvisSansPurl`, `chain/JeuReferenceCorrelationTest` (précision et rappel par niveau) | passe ; le 2026-11-18, `exact` et `probable` 100 %, `à vérifier` 83,3 % |
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

Tâche 3.2 non cochée, preuve partielle du 2026-11-18 : `mvn -B -f argos-server/pom.xml verify` → BUILD SUCCESS, « Tests run: 519, Failures: 0, Errors: 0, Skipped: 1 », dont `chain/ScmContratEnregistreTest` (13 tests), `chain/ScmContractTest` (12 tests) et `scm/SbomDocumentTest` (3 tests). `ScmContratEnregistreTest` rejoue, sur le serveur HTTP local et sans accès à Internet, des réponses de `api.github.com` (dépôt public `CycloneDX/cyclonedx-node-npm`) et de `gitlab.com` (projet public `gitlab-org/security-products/tests/js-npm`) enregistrées le 2026-11-18 avec leurs en-têtes : SBOM CycloneDX 1.6 trouvée et importée, SBOM absente avec repli sur `package.json`, dépôt introuvable, jeton refusé (401), artefact interdit (403). Les limites de débit (403 et 429) et l'artefact SBOM GitLab servi en 200 sont composés d'après la documentation des forges (`argos-server/src/test/resources/fixtures/NOTICE`) ; aucun des points d'accès utilisés n'est paginé. Les réponses sont enregistrées sur des dépôts publics et non sur un dépôt pilote, et le cas central GitLab d'INV-01 (artefact SBOM servi en 200) n'est vérifié que sur une réponse composée : la case attend une capture sur un dépôt pilote réel ([DÉCISION] — porteur du projet, 2026-11-18, consignée dans la fiche `inventaire-sbom`).

Tâche 4.7 non cochée, preuve partielle du 2026-11-18 : `mvn -B -f argos-server/pom.xml test -Dtest=JeuReferenceCorrelationTest` réussit. Le test exécute la chaîne réelle (import des inventaires, collecte OSV sur le serveur HTTP local, corrélation) sur le jeu `argos-server/src/test/resources/fixtures/jeu-reference-correlation/` : 12 projets (11 SBOM CycloneDX Maven, npm et PyPI, un `package.json` de repli), 16 avis OSV (15 avis réels sous CC-BY 4.0, un avis dérivé sans PURL), 52 impacts attendus annotés à la main et 36 négatifs annotés. Résultat : 51 vrais positifs, 1 faux positif, 1 faux négatif, soit une précision et un rappel de 98,1 % tous niveaux, de 100 % pour les niveaux `exact` et `probable` et de 83,3 % pour `à vérifier`. Les deux écarts (qualificatifs Maven inconnus, composant sans PURL) sont analysés dans le [rapport de mesure](/docs/architecture/quality/mesure-correlation-jeu-reference.md) et ne relèvent pas d'un défaut de corrélation. Le jeu est composé d'inventaires de test et d'avis publics, et non des inventaires des projets pilotes. Le texte du critère 3 de la baseline v2.3 §6 demande un jeu de référence qui permet de mesurer vrais et faux positifs et faux négatifs, sans citer les pilotes ; la case attend la mesure sur les inventaires des projets pilotes, avec la tâche 7.1 ([DÉCISION] — porteur du projet, 2026-11-18 : le jeu composé ne suffit pas ; consignée dans la fiche `correlation-impact`).

Preuves des tâches documentaires, le 2026-11-17 :

- 6.2 : D-11 (`docs/architecture/diagrams/d-11-deploiement-cible.md`, depuis `compose.yaml` et `Dockerfile`), D-12 (`d-12-frontieres-confiance.md`) et D-13 (`d-13-modele-physique.md`, cinq `erDiagram` relus sur les migrations V1 à V16) ; l'inventaire `docs/architecture/diagrams/README.md` pointe vers ces trois fichiers, cités en arc42 §7.3, §8.3 et §8.7 ; `node scripts/verifier-docs.mjs` sans erreur.
- 6.3 : ADR-0031 (planification par `quarkus-scheduler`) et ADR-0032 (migrations Flyway), statut Accepté ([DÉCISION] — porteur du projet, 2026-11-18), indexés dans `docs/architecture/adr/README.md` et arc42 §9.1, renvoyés depuis arc42 §9.3 points 1 et 3.

Tâches non réalisées et raison :

- 3.2 : la vérification attend une capture sur un dépôt pilote réel ([DÉCISION] — porteur du projet, 2026-11-18).
- 4.7 : le jeu de référence mesuré ne contient pas les inventaires des projets pilotes, et le jeu composé ne suffit pas au critère 3 ([DÉCISION] — porteur du projet, 2026-11-18) : la mesure se fait avec la tâche 7.1 (preuve partielle ci-dessus).
- 7.1 : la chaîne de bout en bout sur les deux projets pilotes exige ces projets.

## Workflow follow-up

- Basculer dans les quatre fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] (après validation des ADR et arbitrages) ou en [EXISTANT] (après preuve par test).
- Archiver le changement uniquement sur demande explicite, après revue et levée des arbitrages du groupe 1 (`openspec archive formaliser-chaine-securite-poc`).
- Après archivage, vérifier que la section Purpose des quatre specs principales contient le lien vers leur fiche durable.
- Après archivage, remplacer dans les fiches et dans `/docs/specifications/README.md` les liens vers `/openspec/changes/formaliser-chaine-securite-poc/` par les specs principales ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
