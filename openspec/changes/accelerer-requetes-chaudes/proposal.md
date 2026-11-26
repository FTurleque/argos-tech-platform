# Proposition : accélérer les requêtes chaudes

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

La corrélation, l'évaluation des alertes et le radar exécutent des requêtes en N+1, « Ma veille » s'appuie sur un index qui ne porte ni l'espace ni l'état, et la recherche plein texte classe et compte toute sa sélection à chaque page, d'après l'audit du 2026-11-21 (`/docs/audit/2026-11-21/findings.md`, sprint 10 « Requêtes chaudes » de `/docs/audit/2026-11-21/sprints.md`, élargi à AUD-PER-09 et AUD-PER-25 du sprint 9 par décision du porteur du projet) :

- index manquants sur les clés étrangères `component_id` (`project_impact`, `dette_version`) et sur les colonnes de filtre des listes d'exploitation (AUD-PER-09, Faible) ;
- la corrélation réécrit chaque impact à chaque passage (`updated_at = now()` sans condition) : mises à jour non HOT dans les index de `project_impact`, ordre « récents » de Ma veille faussé (AUD-PER-25, Moyenne) ;
- l'évaluation des alertes ouvre trois transactions et au moins trois requêtes par membre, plus une par canal, et diffuse les alertes dans l'observateur (AUD-PER-04, Moyenne) ;
- la corrélation d'un projet ouvre une transaction et relit le signal pour chaque signal, puis exécute 3 à 5 instructions par composant (AUD-PER-03, Moyenne) ;
- la recherche des abonnements de Ma veille porte sur des expressions non indexées et n'est pas mesurée ; la réponse n'est pas paginée (AUD-PER-06, Partiel) ;
- l'index V21 `(updated_at DESC, id)` ne porte ni l'espace ni l'état : un petit espace parcourt toute la table (AUD-PER-26, Faible) ;
- `project_impact` porte deux index redondants (AUD-PER-29, Faible) ;
- le radar charge les composants de tous les espaces pour chaque espace et lit le calendrier composant par composant (AUD-PER-05, Faible) ;
- la recherche plein texte matérialise, classe et compte toute la sélection à chaque page (AUD-PER-07, Faible).

Les sprints 1 à 8 sont livrés ; l'évaluation des alertes et la chaîne radar sont déjà des observateurs asynchrones (ADR-0039, Proposé).

## What Changes — Changements

- Migration Flyway : index sur `project_impact (component_id)`, `dette_version (component_id)`, `ingestion_failure (source_id, received_at)`, `ops_event (source_id, occurred_at)`, `sync_attempt (run_id)`, `inventory_import_failure (project_id, received_at)`, `inventory_import_failure (repository_id, received_at)`, et, pour les purges du sprint 9, `actualite (recu_le)` et `ingestion_failure (run_id)` ; suppression de `project_impact_signal` et `project_impact_project` ; remplacement de `project_impact_recents` par un index partiel `(workspace_id, updated_at DESC, id)` restreint aux états `OUVERT` et `CONFIRME` ; index d'expressions sur `signal_package` pour les critères de technologie, et index `signal (modified_at DESC, id)`.
- Corrélation : un impact n'est réécrit que si l'une de ses valeurs change ; `updated_at` ne bouge pas sinon. La corrélation d'un projet traite ses signaux par lots : pour chaque lot, un nombre fixe d'instructions SQL ensemblistes (signaux, composants, verrous, rattachement, upsert, réouvertures, résolutions), quel que soit le nombre de signaux et de composants.
- Alertes : les destinataires, leurs espaces, leurs abonnements actifs, les impacts visibles et les alertes existantes sont lus en un nombre fixe de requêtes par signal ; les alertes et éléments de résumé sont écrits par lot ; l'observateur ne diffuse plus : la tâche planifiée d'envoi diffuse les alertes en attente.
- Ma veille : impacts lus espace par espace sur l'index partiel ; signaux des abonnements cherchés par des sous-requêtes indexées ; la campagne de performance mesure un appelant d'un petit espace dans une table d'au moins 500 000 impacts majoritairement résolus, avec des abonnements et plusieurs espaces de tailles différentes.
- Radar : composants filtrés par espace en SQL ; calendrier lu une fois par espace ; dettes écrites par lot ; rappels de tous les jours de rappel calculés en une passe.
- Recherche : seule la page demandée est classée et matérialisée ; le classement porte au plus sur 1 000 candidats ; le total est plafonné à 1 000, avec l'indicateur `totalPlafonne` (décision du porteur, 2026-11-26).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `correlation-impact` : COR-07 modifiée (aucune réécriture sans changement) ; COR-13 ajoutée (corrélation par lots en un nombre fixe d'instructions).
- `alertes-digests` : ALR-10 ajoutée (évaluation en un nombre fixe de requêtes, diffusion par la tâche d'envoi).
- `radar-standards` : RAD-10 ajoutée (radar en un nombre fixe de requêtes par espace).
- `recherche-veille` : RCH-07 ajoutée (seule la page demandée est classée ; total plafonné).
- `profils-veille` : PRF-07 modifiée (petit espace dans une grande table, abonnements mesurés).
- `performance-api-mcp` : PERF-02 modifiée (jeu de données à plusieurs espaces de tailles différentes, avec abonnements et impacts résolus).

## Périmètre

### Inclus

- `argos-server/src/main/java` : `impact` (`CorrelationEngine`, `TraitementImpacts`, `ImpactQueries`, `RadarProjets`, `RechercheVeille`), `alerting` (`MoteurAlertes`, `Destinataires`, `RappelsRadar`), `signal/SignalQueries`, `api/SignauxAbonnements`, `api/veille/VeilleProfilsResource`, `inventory/InventoryQueries`, `releaseradar/CalendrierEndoflife`, `workspace/subscription/SubscriptionService`.
- Migrations Flyway V27 (index) et V28 (recherche bornée) ; V26 est attribuée au sprint 9.
- Tests : nombre de requêtes de la corrélation, des alertes et du radar (compteur d'instructions JDBC), non-réécriture d'un impact inchangé, index présents et redondants absents, recherche paginée et total plafonné, non-régression des droits (COR-08, ALR-07, RCH-03).
- Campagne de performance : jeu de données à plusieurs espaces, abonnements, impacts résolus en masse.
- Documentation : fiches `correlation-impact`, `alertes-digests`, `radar-standards`, `recherche-veille`, `profils-veille`, `performance-api-mcp` ; arc42 §8.7 (inventaire des index) ; rapport de mesure.

### Exclu

- Rétention et purges, import d'inventaire (réécriture nocturne, insertion par lot), pagination des listes d'exploitation et de `GET /projects/{id}/impacts` : sprint 9, mené en parallèle.
- Exécution de la corrélation d'inventaire en tâche de fond : l'observateur `InventoryReplaced` reste `AFTER_SUCCESS` ; le critère de sortie porte sur le nombre de requêtes, et le déplacer change le contrat de l'import (sprint 9).
- Mesure sur le NAS : la campagne s'exécute sur la pile de test locale (PERF-01).
- Pagination de `GET /api/v1/me/veille` au format commun des listes (ADR-0040, sprint 9) : tâche de suivi, après la fusion du sprint 9, qui porte ce format (décision du porteur du projet, 2026-11-26).

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Membre d'espace | voit Ma veille et la recherche répondre dans la cible, quel que soit le volume des autres espaces | [PROPOSITION] — AUD-PER-06, AUD-PER-26, AUD-PER-07 |
| Destinataire d'alertes | reçoit ses alertes par la tâche d'envoi, sans ralentir l'ingestion | [PROPOSITION] — AUD-PER-04 |
| Exploitant | voit l'import nocturne recorréler sans réécrire les impacts inchangés | [PROPOSITION] — AUD-PER-25, AUD-PER-03 |
| Porteur du projet | a tranché le 2026-11-26 : lecture technique filtrée par appartenance pour les alertes ; total de recherche plafonné à 1 000 ; sprint 10 élargi à AUD-PER-09 et AUD-PER-25 | [DÉCISION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Upsert sans condition, `component_id` dans le `SET` | [EXISTANT] | `impact/CorrelationEngine.java:305-324` |
| Une transaction et une relecture du signal par signal ; 3 à 5 instructions par composant | [EXISTANT] | `impact/CorrelationEngine.java:129-139`, `:196-215`, `:260-288`, `:395-438` |
| Trois transactions et trois requêtes par membre ; diffusion dans l'observateur | [EXISTANT] | `alerting/MoteurAlertes.java:139-155`, `:188-206`, `:327-332` |
| Recherche des abonnements sur expressions non indexées | [EXISTANT] | `signal/SignalQueries.java:184-193` ; index : `V3:122`, `V11:41-42` |
| Index V21 sans espace ni état | [EXISTANT] | `db/migration/V21__index_impacts_recents.sql:16` ; `impact/ImpactQueries.java:227-228` |
| Index redondants | [EXISTANT] | `V5__impacts_projet.sql:22`, `V11__recherche_veille.sql:49`, `V20__etat_traitement_impacts.sql:55-56` |
| Radar : composants de tous les espaces, calendrier par composant | [EXISTANT] | `impact/RadarProjets.java:164-173`, `:247-253` ; `releaseradar/CalendrierEndoflife.java:75-94` ; `alerting/RappelsRadar.java:166` |
| Recherche matérialisée et comptée | [EXISTANT] | `impact/RechercheVeille.java:139-147` |
| RBAC applicatif et RLS en garde-fou ; traitements techniques | [DÉCISION] | ADR-0016 (Accepté) |
| Migrations Flyway, numéro attribué | [DÉCISION] | ADR-0032 (Accepté) |
| Noyau commun, JTA, utilitaire `Jdbc` | [DÉCISION] | ADR-0037 (Accepté) |
| Observateurs asynchrones, tâches de fond | [PROPOSITION] | ADR-0039 (Proposé) |
| Format commun de pagination | [PROPOSITION] | ADR-0040 (sprint 9, en cours) |
| Cible p95 < 500 ms | [PROPOSITION] | PERF-01, PRF-07 |
| Critère de sortie du sprint 10 | [PROPOSITION] | `/docs/audit/2026-11-21/sprints.md`, « Sprint 10 » |

## Écarts constatés

- La campagne de performance crée déjà un abonnement `PERSONAL` (`e2e/performance/mesurer-p95.mjs`, fonction `preparer`), contrairement à la réserve d'AUD-PER-06 rédigée à `b142695` ; elle ne crée en revanche qu'un espace, sans impact résolu.
- L'évaluation des alertes est déjà asynchrone (`@ObservesAsync`, sprint 8) ; AUD-PER-04 reste ouvert pour le N+1 et la diffusion dans l'observateur.
- `PRF-07-S1` décrit un espace unique ; le critère de sortie du sprint exige un appelant de moins de 200 impacts actifs dans une table d'au moins 500 000 lignes : la spec est modifiée.

## Impact

- **Composants** (arc42 §5) : correlation-engine, alerting, release-radar, search, workspace-service (abonnements), API.
- **Contrats d'interface** : OpenAPI — `GET /api/v1/recherche` et outil MCP `search_signals` : champ `totalPlafonne` ajouté, `total` plafonné à 1 000 ; événements CDI inchangés.
- **Données et persistance** : migrations V27 (index) et V28 (fonction de sélection bornée de la recherche) ; aucune donnée ni droit ne change.
- **Sécurité, confidentialité, droits** : l'évaluation des alertes lit les impacts du signal par une lecture technique filtrée explicitement par appartenance (même règle que `argos_espace_lisible`), au lieu d'une relecture sous la RLS de chaque destinataire (décision du porteur, 2026-11-26) ; aucune lecture d'API ne change de rôle.
- **Exploitation et observabilité** : paramètre `argos.correlation.taille-lot` ; les alertes immédiates partent au passage suivant de la tâche d'envoi (cadence existante).
- **Documentation durable à mettre à jour** : fiches citées au périmètre, `docs/specifications/README.md`, arc42 §8.7, `docs/architecture/quality/mesure-performance-api-mcp.md`.
- **ADR** : ADR-0016 (Accepté) appliqué ; aucune décision nouvelle candidate à un ADR (choix locaux à chaque composant).

## Conception requise

complète — plusieurs composants, une migration, un contrat d'API et une lecture technique qui touche le cloisonnement changent ensemble.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Taille d'un lot de corrélation : 500 signaux par défaut ([QUESTION], aucune source ne fixe la valeur). | non | porteur du projet |
| Q-2 | Pagination de Ma veille (tâche de suivi) : taille par défaut de l'ADR-0040 (50) ou page unique de 200 tant que l'IHM ne pagine pas ? | non : tranchée à la fusion du sprint 9 | porteur du projet |
