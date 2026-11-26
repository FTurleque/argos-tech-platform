# Proposition : rendre l'ingestion asynchrone

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

La collecte des avis est planifiée, mais tout ce qui suit l'enregistrement d'un avis s'exécute encore dans le fil appelant, d'après l'audit du 2026-11-21 (`/docs/audit/2026-11-21/findings.md`, sprint 8 « Ingestion asynchrone » de `/docs/audit/2026-11-21/sprints.md`) :

- l'enrichissement NVD, EPSS, KEV et CERT-FR interroge chaque source pour chaque CVE dans le fil d'ingestion, transaction ouverte, et un enrichissement refusé n'est jamais rattrapé : `retryMissing` n'a aucun appelant (AUD-PER-02, Élevée) ;
- le lancement manuel d'une synchronisation attend la fin du traitement dans la requête HTTP, OSV est lu avis par avis et les attentes de reprise dorment sur ce fil (AUD-PER-01, Moyenne, partiel) ;
- pendant une attente de reprise, le traitement passe `RETRYABLE` et sort de l'exclusion `sync_run_one_running` : un second lancement le reprend pendant que le premier fil dort (AUD-PER-27) ;
- la chaîne `StandardModifie → RadarProjets → AnnonceStandard → RappelsRadar` s'exécute dans la requête `PUT /standards`, et la règle de choix entre observateurs synchrones et transactionnels n'est écrite nulle part (AUD-ARC-09) ;
- l'événement `AlerteEmise` est publié en cinq points sans observateur (AUD-ARC-19) ;
- six clients `java.net.http.HttpClient` sont construits séparément dans cinq modules (AUD-ARC-10), et les réponses OSV et KEV sont lues en entier sans plafond (AUD-PER-11) ;
- la collecte d'activité lit l'échéance projet par projet et appelle les forges en séquence (AUD-PER-13).

Les sprints 1 à 6 sont livrés ; le noyau commun (`io.argos.commun`, ADR-0037) et les exécuteurs bornés (`io.argos.execution.ExecuteurBorne`) fournissent les points d'appui.

## What Changes — Changements

- **BREAKING** (API d'exploitation) : `POST /api/v1/ops/sources/{id}/synchronizations` répond `202 Accepted` avec l'identifiant du traitement dès qu'il est démarré, puis la synchronisation se poursuit en tâche de fond par le même traitement que la synchronisation planifiée ; une synchronisation déjà en cours donne `409 Conflict` (code `SYNCHRONISATION_EN_COURS`). Le corps ne porte plus les compteurs du traitement : ils restent lisibles par l'état des sources.
- Un traitement en attente de reprise reste `RUNNING`, avec sa date de prochaine tentative : il reste exclusif ; la reprise vérifie que le traitement est toujours à elle.
- L'enrichissement sort du fil d'ingestion : l'enregistrement d'un signal le place dans une file persistante ; une tâche planifiée la traite par lots, hors transaction pendant les appels aux sources, avec un délai croissant pour une source indisponible ; le rattrapage des signaux sans enrichissement (`retryMissing`) est planifié.
- Amorçage OSV : un écosystème dont l'index annonce plus d'avis que le seuil configuré est lu depuis son archive `all.zip`, téléchargée en flux dans un fichier temporaire borné, au lieu d'un appel par avis.
- Événements : règle des trois styles d'observateurs (dans la transaction de l'émetteur, après validation, asynchrone) ; la chaîne radar et l'évaluation des alertes (`ImpactsRecorded → MoteurAlertes`) deviennent asynchrones ; un échec d'observateur asynchrone est journalisé avec sa cause et compté. `AlerteEmise` est supprimé.
- Clients HTTP : une fabrique commune (`io.argos.commun.http`) fixe délai de connexion, refus des redirections, `User-Agent` et lecture bornée ; chaque client garde sa classification d'erreurs ; les réponses OSV, KEV, NVD et EPSS sont lues en flux sous un plafond configurable.
- Collecte d'activité : les échéances sont lues en une requête ; les forges sont interrogées en parallèle sur un exécuteur borné, les projets d'une même forge en séquence.
- Aucun appel à `EnrichmentSource.lookup` ni à la lecture d'un avis (`AdvisoryFeed`) dans une transaction : règle ArchUnit.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `collecte-avis-securite` : COL-04 (enrichissement différé et rattrapé) modifiée ; COL-12 (lancement par l'exploitant en tâche de fond, exclusivité pendant l'attente de reprise) ajoutée.
- `radar-standards` : RAD-07 modifiée (l'annonce ne s'exécute plus dans la requête ; RAD-07-S3 change).

## Périmètre

### Inclus

- `argos-server/src/main/java` : `ingestion` (`SourceSynchronizer`, `SyncStore`, `HttpSourceClient`, `osv/OsvAdvisoryFeed`, `AdvisoryFeed`), `signal` (`SourceOperations`, `SynchronisationPlanifiee`), `api/SourceOperationsResource`, `vulnintel` (`Enricher`, `EnrichmentHttp`, sources), `impact` (`RadarProjets`, `CorrelationEngine`), `alerting` (`MoteurAlertes`, `RappelsRadar`, `ResumesPeriodiques`, `RappelsReglementaires`, `RappelsOpportunites`, `AlerteEmise`), `catalog/standard/ReferentielEspace`, `projectintel` (`CollecteActivite`, `ActiviteStore`), `scm/ForgeHttp`, `releaseradar/ClientEndoflife`, `aigateway/ollama/FournisseurOllama`, `ingestion/legi/ArchivesJorf`, `commun` (fabrique HTTP), `execution` (soumission groupée), `observabilite` (publication asynchrone comptée).
- Migration Flyway : file d'enrichissement.
- Tests : concurrence pendant l'attente de reprise, 202/409, archive OSV, file d'enrichissement, chaîne radar asynchrone, collecte d'activité, règles ArchUnit (appel réseau hors transaction, observateurs inter-modules).
- Documentation : fiches `collecte-avis-securite` et `radar-standards`, arc42 §5.3, §6, §8, ADR-0039 (Proposé), `application.properties`.

### Exclu

- Diffusion des alertes en temps réel vers le poste (SSE) : `AlerteEmise` est supprimé (décision du porteur du projet, 2026-11-26).
- Le transport épinglé de la veille web (`ingestion/web/TransportEpingle`, Vert.x) : il porte déjà sa garde d'adresses et son plafond (changement `epingler-adresse-veille-web`) ; il garde son transport.
- Clé d'API NVD et limiteur par source à jeton : le débit vers NVD est borné par la taille des lots et la cadence de la file (Q-1).
- Création de projet, de dépôt, import SBOM, IHM et nettoyage entre tests : sprint 7, mené en parallèle.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Exploitant | lance une synchronisation sans attendre sa fin ; ne voit plus deux traitements sur une source | [PROPOSITION] — AUD-PER-01, AUD-PER-27 |
| Administrateur d'espace | modifie le référentiel sans attendre l'annonce | [PROPOSITION] — AUD-ARC-09 |
| Utilisateur des signaux | voit l'enrichissement rattrapé après une indisponibilité de source | [PROPOSITION] — AUD-PER-02 |
| Porteur du projet | a tranché le 2026-11-26 : alertes asynchrones, suppression d'`AlerteEmise`, 409 pour un traitement en cours, ADR-0039 Proposé | [DÉCISION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Enrichissement dans l'observateur AFTER_SUCCESS, en transaction, `retryMissing` sans appelant | [EXISTANT] | `vulnintel/Enricher.java:58-64` (`QuarkusTransaction.requiringNew().run(() -> enrich(...))`), `:89` ; `grep -rn retryMissing src/main` : définition seule |
| Lancement manuel synchrone | [EXISTANT] | `api/SourceOperationsResource.java:57-63` (« attend son résultat ») ; `signal/SourceOperations.java:60-64` |
| OSV lu avis par avis, index lu en `String` | [EXISTANT] | `ingestion/osv/OsvAdvisoryFeed.java:58-61` et `:88-90` ; `ingestion/HttpSourceClient.java:63` (`BodyHandlers.ofString()`) |
| Attente de reprise en `RETRYABLE`, reprise sans condition | [EXISTANT] | `ingestion/SourceSynchronizer.java:86-94` ; `ingestion/SyncStore.java` `markRetryable`, `resume`, `startOrResume` ; index `sync_run_one_running` (V3) sur `state = 'RUNNING'` |
| Chaîne radar synchrone dans le `PUT /standards` | [EXISTANT] | `catalog/standard/ReferentielEspace.java:109,224` (`modifications::fire` après `base.executer`) ; `impact/RadarProjets.java:203` ; `alerting/RappelsRadar.java:176` |
| Alertes évaluées dans le fil de la corrélation | [EXISTANT] | `impact/CorrelationEngine.java:180-186` ; `alerting/MoteurAlertes.java:119` (`@Observes ImpactsRecorded`) |
| `AlerteEmise` sans observateur | [EXISTANT] | cinq `emises.fire(new AlerteEmise(...))` dans `alerting/` ; aucun `@Observes AlerteEmise` |
| Six `HttpClient.newBuilder` | [EXISTANT] | `aigateway/ollama/FournisseurOllama.java:60`, `vulnintel/EnrichmentHttp.java:36`, `scm/ForgeHttp.java:46`, `ingestion/legi/ArchivesJorf.java:79`, `ingestion/HttpSourceClient.java:41`, `releaseradar/ClientEndoflife.java:72` |
| Collecte d'activité projet par projet | [EXISTANT] | `projectintel/CollecteActivite.java:92-99` (`store.prochaineCollecte(projet.id())` dans la boucle) |
| Planification par Quarkus Scheduler | [DÉCISION] | ADR-0031 (Accepté) |
| Noyau commun, modèle transactionnel JTA | [DÉCISION] | ADR-0037 (Accepté) |
| Exécuteurs bornés possédés par un bean CDI | [DÉCISION] | changement archivé `2026-11-25-injecter-horloge-et-executeurs` (D3) |
| Choix des styles d'observateurs, exécuteur de fond, appels réseau hors transaction, fabrique HTTP | [PROPOSITION] | ADR-0039 (Proposé, ce changement) |
| Critère de sortie du sprint 8 | [PROPOSITION] | `/docs/audit/2026-11-21/sprints.md`, « Sprint 8 » |

## Écarts constatés

- L'audit cite sept clients HTTP, dont `ingestion/web/RecuperateurWeb.java:180` ; à `dab09c6`, la veille web passe par `TransportEpingle` (Vert.x) et il reste six `HttpClient.newBuilder` (`grep -rn "HttpClient.newBuilder" argos-server/src/main/java`).
- Le javadoc d'`Enricher` annonce que l'enrichissement manquant « est retenté par `retryMissing` » alors que rien ne l'appelle (constaté par l'audit, toujours vrai à `dab09c6`).
- `RAD-07-S3` exige une réponse en erreur serveur pour une erreur imprévue de l'annonce : avec une annonce asynchrone, la requête est déjà confirmée ; la spec est modifiée en conséquence (décision du porteur, AUD-ARC-09).
- `docs/specifications/collecte-avis-securite.md` (DF-COL-1) montre l'enrichissement dans la séquence de synchronisation ; il est mis à jour.

## Impact

- **Composants** (arc42 §5) : source-ingestion, vulnerability-intelligence, correlation-engine, alerting, release-radar, project-intelligence, scm-connectors, ai-gateway, noyau commun.
- **Contrats d'interface** : OpenAPI — `POST /api/v1/ops/sources/{id}/synchronizations` (202 + `SyncLaunch`, 409) ; événements CDI — `StandardModifie`, `AnnonceStandard`, `ImpactsRecorded` publiés de façon asynchrone ; `AlerteEmise` retiré.
- **Données et persistance** : table `enrichissement_attente` (V-suivante), clé étrangère vers `signal` avec suppression en cascade ; `sync_run` : un traitement en attente de reprise reste `RUNNING` avec `next_attempt_at`.
- **Sécurité, confidentialité, droits** : aucun changement de droits ; lecture bornée de toutes les réponses HTTP sortantes ; les archives OSV sont écrites dans un fichier temporaire supprimé après usage.
- **Exploitation et observabilité** : tâches planifiées `vulnintel-enrichissement` et `vulnintel-rattrapage` (observées par `@TacheObservee`) ; compteur `argos_evenements_asynchrones_echecs_total` ; nouveaux paramètres `argos.http.*`, `argos.enrichissement.*`, `argos.ingestion.lancement.*`, `argos.sources.osv.seuil-archive`, `argos.activite.forges.*`.
- **Documentation durable à mettre à jour** : `docs/specifications/collecte-avis-securite.md` (COL-04, COL-12, DF-COL-1), `docs/specifications/radar-standards.md` (RAD-07), `docs/specifications/README.md` (couverture), arc42 §5.3, §6 (séquences), §8 (événements, appels sortants), ADR README.
- **ADR** : ADR-0031 (Accepté) et ADR-0037 (Accepté) précisés par ADR-0039 (Proposé).

## Conception requise

complète — plusieurs composants, un contrat d'API, une migration et des règles d'architecture nouvelles (événements, transactions) changent ensemble.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Faut-il une clé d'API NVD (débit 50 requêtes / 30 s au lieu de 5) et un limiteur par source ? La file traite par défaut 5 signaux toutes les 30 s. | non : la valeur par défaut respecte le débit sans clé | porteur du projet |
| Q-2 | Seuil de bascule vers l'archive OSV : 1 000 avis par écosystème par défaut ([QUESTION], aucune source ne fixe la valeur). | non | porteur du projet |
