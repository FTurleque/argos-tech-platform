# Proposition : automatiser la chaîne de veille sécurité

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

La chaîne « avis de sécurité → inventaire SBOM → corrélation » est implémentée dans `argos-server` (changement [`formaliser-chaine-securite-poc`](/openspec/changes/archive/2026-11-20-formaliser-chaine-securite-poc/proposal.md), non archivé), mais elle ne tourne pas seule : la synchronisation des sources d'avis se lance à la main (`POST /api/v1/ops/sources/{id}/synchronizations`) et le rafraîchissement d'un inventaire depuis sa forge (`InventoryService.refresh`) n'a aucun déclencheur en production [EXISTANT — `argos-server/src/main/java/io/argos/api/SourceOperationsResource.java`, `argos-server/src/main/java/io/argos/inventory/InventoryService.java` ; ADR-0031 (Accepté), contexte]. Les fiches laissent ouvertes les questions qui bloquaient cette automatisation (Q-COL-1, Q-COL-2, Q-INV-1, Q-INV-2, Q-COR-2, Q-ABO-1, termes ambigus « projet » et « impact » du glossaire).

Le porteur du projet a tranché ces questions le 2026-11-19, en réponse explicite à des questions posées dans la conversation (décisions J1 à J8 ci-dessous). Ce changement les traduit en exigences testables, en conception et en tâches, découpées en trois volets indépendants. Il ne contient aucune implémentation : rien de ce qu'il décrit n'est livré.

## Décisions du porteur du projet (2026-11-19)

Décisions citées telles qu'elles ont été prises ; elles font foi [DÉCISION — porteur du projet, 2026-11-19].

| Réf. | Question levée | Décision |
|---|---|---|
| J1 | Q-COL-1 | Les sources d'avis de sécurité (OSV, CERT-FR et autres collecteurs existants) sont synchronisées toutes les heures, par la planification existante (`quarkus-scheduler`, ADR-0031), avec le curseur delta existant ; fréquence configurable. |
| J2 | Q-COL-2 | Un avis déjà connu est jugé inchangé si l'empreinte (hash) de son contenu normalisé est identique ; sinon il est mis à jour. |
| J3 | Q-INV-1 | Les inventaires SBOM sont importés par interrogation périodique (quotidienne, configurable) des dépôts GitHub/GitLab configurés, en réutilisant la récupération existante du module `scm` ; l'import manuel existant reste possible ; aucun point d'entrée entrant (pas de webhook). |
| J4 | Q-INV-2 | À chaque import d'inventaire d'un projet, les avis déjà connus sont recorrélés pour ce projet (nouveaux impacts créés, impacts devenus sans objet traités selon J5). |
| J5 | Q-COR-2 | Deux axes distincts : le niveau de confiance de la corrélation (`exact`, `probable`, `à vérifier`, ADR-0013) et l'état de traitement d'un impact (ouvert, confirmé, écarté, résolu) ; le glossaire et les fiches sont alignés ; les transitions d'état sont spécifiées et faites par un utilisateur autorisé (RBAC existant), sauf « résolu » qui peut aussi résulter de J4 (composant retiré ou mis à jour hors plage). |
| J6 | glossaire (« projet ») | Un projet peut regrouper plusieurs dépôts ; son inventaire est l'union des inventaires de ses dépôts. |
| J7 | Q-ABO-1 | Un abonnement est actif ou suspendu ; suspendre conserve les critères et arrête les notifications ; la suppression reste distincte. |
| J8 | performance | Une mesure automatisée et gratuite (outil sous licence permissive : script Node, autocannon MIT, Gatling Apache-2.0… ; jamais k6 AGPL) vérifie la cible arc42 « API et MCP p95 < 500 ms hors appels externes et IA » sur la pile de test locale et produit un rapport versionné ; commande dédiée, hors build par défaut. |

## What Changes — Changements

- **Collecte** : synchronisation planifiée de chaque source d'avis et d'enrichissement, toutes les heures par défaut, depuis le curseur persisté (J1) ; détection d'un avis modifié par l'empreinte de son contenu normalisé, et non plus du contenu brut (J2).
- **Inventaire** : import quotidien, par défaut, des inventaires depuis les dépôts configurés de chaque projet (J3) ; recorrélation des avis connus à chaque import (J4) ; projet à plusieurs dépôts dont l'inventaire est l'union des inventaires de ses dépôts (J6). Aucun point d'entrée entrant n'est ajouté.
- **Corrélation** : état de traitement d'un impact (`ouvert`, `confirmé`, `écarté`, `résolu`), distinct du niveau de confiance ; transitions faites par un utilisateur autorisé et historisées ; résolution automatique quand un import retire le composant ou le met à jour hors plage (J5). Un impact devenu sans objet n'est plus supprimé : il est résolu et reste consultable.
- **Alertes** : aucune alerte immédiate ni mention dans un résumé pour un impact écarté ou résolu (conséquence de J5 sur `alertes-digests`).
- **Abonnements** : état `actif` ou `suspendu` ; suspension et réactivation sans perte des critères ; suppression distincte (J7).
- **Performance** : campagne de mesure automatisée et gratuite de la latence p95 de l'API et du serveur MCP sur la pile de test locale, rapport versionné, commande dédiée hors build par défaut (J8).
- **BREAKING** : 
  - l'état `INACTIVE` d'un abonnement devient `SUSPENDED` (migration des données ; les clients qui lisent `state` voient la nouvelle valeur) ;
  - un impact devenu sans objet passe à l'état `résolu` au lieu d'être supprimé : le nombre d'impacts conservés augmente et les listes d'impacts filtrent par état ;
  - la première synchronisation après la migration recalcule les empreintes sans créer de version (aucune rupture visible attendue, voir design.md D2).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `performance-api-mcp` (préfixe `PERF`) : latence p95 des consultations de l'API REST et des outils du serveur MCP, mesurée par une campagne automatisée et gratuite dont le rapport est versionné (J8).

### Modified Capabilities — Capacités modifiées

Les quatre capacités de la chaîne sécurité n'ont pas encore de spec de référence (`openspec list --specs` : elles sont portées par le changement actif `formaliser-chaine-securite-poc`). Leurs deltas ne contiennent que des exigences ajoutées (`## ADDED Requirements`), sans réécrire les exigences COL-01 à ABO-06 de ce changement ; voir design.md « Compatibilité » pour l'ordre d'archivage.

- `collecte-avis-securite` : ajout de COL-09 (synchronisation planifiée, J1) et COL-10 (empreinte du contenu normalisé, J2).
- `inventaire-sbom` : ajout d'INV-07 (import périodique, J3), INV-08 (import manuel et imports concurrents, J3), INV-09 (recorrélation à l'import, J4) et INV-10 (projet à plusieurs dépôts, J6).
- `correlation-impact` : ajout de COR-09 (deux axes, J5), COR-10 (transitions par un utilisateur autorisé, J5), COR-11 (résolution automatique, J4/J5) et COR-12 (historique et filtre par état, J5).
- `abonnements-veille` : ajout d'ABO-07 (actif ou suspendu, J7) et ABO-08 (suppression distincte, J7).
- `alertes-digests` (spec de référence existante) : ajout d'ALR-07 (aucune alerte pour un impact écarté ou résolu, conséquence de J5).

## Périmètre

### Inclus

- Planification de la synchronisation des sources d'avis (OSV) et du rafraîchissement des sources d'enrichissement (NVD, CISA KEV, FIRST EPSS, CERT-FR) par `quarkus-scheduler`.
- Empreinte SHA-256 du contenu normalisé d'un avis et transition depuis les empreintes du contenu brut.
- Planification quotidienne de l'import d'inventaire de chaque dépôt configuré ; rattachement de plusieurs dépôts à un projet ; inventaire du projet = union ; recorrélation à chaque import.
- État de traitement des impacts, transitions, historique, résolution automatique, effet sur les alertes.
- État des abonnements, suspension, réactivation, suppression.
- Campagne de mesure p95 de l'API et du MCP, rapport versionné dans `docs/architecture/quality/`.
- Mise à jour des fiches, du glossaire (arc42 §12), du registre `docs/specifications/README.md`, d'arc42 §6, §8.7 et §10, de l'inventaire des diagrammes, dans les volets qui les touchent.

### Exclu

- Point d'entrée entrant (webhook de forge ou de CI) : exclu par J3.
- Nouveau point d'accès REST d'import manuel d'inventaire : l'import manuel existant (service `InventoryService.importSbom` / `importManifests`) reste possible, mais aucune nouvelle surface n'est ouverte ([PROPOSITION] P-1).
- Administration des projets et de leurs dépôts par l'IHM ou l'API (création, rattachement d'un dépôt) : les dépôts restent configurés en base comme aujourd'hui ([PROPOSITION] P-7).
- Collecteurs hors chaîne sécurité (veille web, réglementaire, profil artiste, activité des projets) : ils ont déjà leur planification (ADR-0031) et ne changent pas.
- Activité des projets (`activite-projets`) sur plusieurs dépôts : elle continue de lire le dépôt principal du projet ([PROPOSITION] P-7).
- Exposition de l'état de traitement par les outils MCP : inchangée dans ce changement (les outils restent en lecture seule, ADR-0014).
- Tâches ouvertes du changement `formaliser-chaine-securite-poc` (3.2, 4.7, 7.1 : projets pilotes) : non traitées ici.
- Outils sous licence non permissive (k6, AGPL) et tout service payant : exclus par J8 et ADR-0021.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Planificateur du serveur (`quarkus-scheduler`) | déclenche la synchronisation horaire et l'import quotidien | [DÉCISION] — J1, J3 ; ADR-0031 (Accepté) |
| OSV, NVD, CISA KEV, FIRST EPSS, CERT-FR | sources interrogées périodiquement | [PROPOSITION] — ADR-0010 (Proposé) ; [DÉCISION] J1 |
| GitHub, GitLab (via `scm-connector`) | forges interrogées quotidiennement, en lecture seule | [DÉCISION] — J3 |
| Membre `CONTRIBUTEUR` ou `ADMINISTRATEUR` d'un espace | change l'état de traitement des impacts de ses projets | [DÉCISION] J5 (RBAC existant) ; rôles retenus [PROPOSITION] P-5 |
| Membre `LECTEUR` d'un espace | consulte les impacts et leur état, sans les modifier | [PROPOSITION] P-5 |
| Propriétaire d'un abonnement `PERSONAL`, administrateur d'espace pour un `TEAM` | suspend, réactive, supprime | [DÉCISION] J7 ; répartition des droits [PROPOSITION] P-8 |
| Exploitant | règle les cadences, lit les rapports de performance | [PROPOSITION] — arc42 §8.6 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Décisions J1 à J8 | [DÉCISION] | porteur du projet, 2026-11-19 (conversation) |
| Planification par `quarkus-scheduler`, identité stable, cadence en configuration, `concurrentExecution = SKIP`, état et exclusion en base | [DÉCISION] | `/docs/architecture/adr/0031-planification-quarkus-scheduler.md` (Accepté) |
| Migrations Flyway versionnées, liste à jour en arc42 §8.7, prochaine migration V18 | [DÉCISION] | `/docs/architecture/adr/0032-migrations-flyway.md` (Accepté) ; `/docs/architecture/arc42/08-concepts-transverses.md` §8.7 |
| Niveaux `exact` / `probable` / `à vérifier` | [PROPOSITION] | `/docs/architecture/adr/0013-correlation-deterministe.md` (Proposé) ; décision Q-2 du 2026-11-16 |
| Cloisonnement RBAC + RLS, rôles `LECTEUR`, `CONTRIBUTEUR`, `ADMINISTRATEUR` | [EXISTANT] | migration `V2__comptes_et_acces.sql` ; `io.argos.workspace.Role` ; ADR-0016 (Proposé) |
| Cible « API/MCP p95 < 500 ms hors appels externes/IA » | [PROPOSITION] | arc42 §8 (cibles initiales) ; arc42 §10.2 Q-05 ; QS-06, QS-13 |
| Licences permissives seulement, dépendances enregistrées | [DÉCISION] | `/docs/architecture/adr/0021-double-licence.md` |
| Synchronisation manuelle seulement, rafraîchissement d'inventaire sans déclencheur | [EXISTANT] | `SourceOperationsResource`, `InventoryService.refresh` ; fiches `collecte-avis-securite` et `inventaire-sbom`, « Écarts constatés » |
| Empreinte actuelle calculée sur le contenu brut | [EXISTANT] | `io.argos.ingestion.NormalizedAdvisory#contentHash` (« empreinte SHA-256 du contenu brut ») ; `io.argos.signal.SignalRecorder` |
| Recorrélation d'un projet à chaque remplacement d'inventaire | [EXISTANT] | `io.argos.impact.CorrelationEngine#onInventoryReplaced` (événement `InventoryReplaced`) |
| Impact devenu sans objet supprimé ; composant supprimé en cascade avec ses impacts | [EXISTANT] | `CorrelationEngine#deterministic` (`DELETE FROM project_impact …`) ; `V5__impacts_projet.sql` (`ON DELETE CASCADE`) |
| Un seul dépôt par projet | [EXISTANT] | `V4__projets_et_inventaires.sql` (colonnes `scm_*` de `project`) ; `io.argos.inventory.ProjectDirectory` |
| État d'abonnement `ACTIVE` / `INACTIVE`, aucune suppression | [EXISTANT] | `V6__abonnements_veille.sql` ; `io.argos.workspace.subscription.SubscriptionService#leave` |

## Écarts constatés

- **Import manuel** : J3 évoque « l'import manuel existant » ; aucun point d'accès REST, outil MCP ni écran ne déclenche aujourd'hui un import d'inventaire. Seules les méthodes de service `InventoryService.importSbom`, `importManifests` et `refresh` existent, appelées par les tests. L'écart est consigné ; ce changement conserve ces méthodes sans ouvrir de nouvelle surface ([PROPOSITION] P-1).
- **Empreinte** : COL-03 et le code comparent l'empreinte du contenu brut ; J2 impose le contenu normalisé. Une réorganisation du JSON par la source crée aujourd'hui une version sans changement de contenu.
- **Impacts sans objet** : le code supprime un impact qui n'est plus vérifié (et la suppression d'un composant supprime ses impacts en cascade) ; J5 impose un état `résolu` traçable.
- **Abonnement** : l'état `INACTIVE` résulte aujourd'hui du retrait d'un abonnement `PERSONAL` par son propriétaire (`POST /api/v1/subscriptions/{id}/leave`) ; aucune réactivation ni suppression n'existe.
- **Glossaire** : « impact probable, confirmé, résolu » mêle les deux axes de J5 ; « projet » est listé parmi les termes ambigus ; « Repository » est défini comme « dépôt associé à un projet », compatible avec J6.
- **Fiches** : les fiches `collecte-avis-securite` et `inventaire-sbom` citent « aucune planification, tâche 6.3 » alors que la tâche 6.3 de `formaliser-chaine-securite-poc` a produit ADR-0031 ; la planification de la chaîne sécurité relève de ce changement.

## Impact

- **Composants** (arc42 §5) : `source-ingestion`, `signal`, `vuln-intel` (volet A) ; `workspace` (abonnements, volet A) ; `inventory`, `scm-connector`, `impact-engine` (déclenchement, volet B) ; `impact-engine`, `alerting`, `API & Security` (volet C) ; `argos-ui` (volets A, B, C) ; `e2e/` (volet C, mesure).
- **Contrats d'interface** (OpenAPI) : ajout de `POST /api/v1/impacts/{id}/transitions` et du filtre `etat` des listes d'impacts (volet C) ; ajout de `POST /api/v1/subscriptions/{id}/suspension`, `POST /api/v1/subscriptions/{id}/reactivation` et `DELETE /api/v1/subscriptions/{id}` ; liste des abonnements de l'utilisateur, suspendus compris (volet A) ; dépôts du projet dans la synthèse d'inventaire (volet B). Aucun webhook, aucun point d'accès entrant non authentifié.
- **Données et persistance** : migrations V18 (volet A : empreinte normalisée, état `SUSPENDED`), V19 (volet B : `project_repository`, occurrences de composant par dépôt, marqueur de recorrélation), V20 (volet C : état de traitement, historique des transitions, rattachement d'un impact à un composant disparu), ajoutées au tableau d'arc42 §8.7.
- **Sécurité, confidentialité, droits** : transitions d'état sous RBAC existant et RLS (`argos_role_dans`) ; refus journalisés dans `refus_acces` ; tâches planifiées exécutées sous l'utilisateur de connexion, comme la corrélation actuelle ; aucun jeton de forge dans les journaux.
- **Exploitation et observabilité** : nouvelles cadences réglables (`argos.ingestion.planification.cadence`, `argos.inventory.import.cron`), bilans journalisés par exécution, échecs visibles (`sync_run`, `inventory_import_failure`) ; rapport de performance versionné.
- **Documentation durable à mettre à jour** : fiches `collecte-avis-securite`, `inventaire-sbom`, `correlation-impact`, `abonnements-veille`, `alertes-digests`, nouvelle fiche `performance-api-mcp` ; registre `docs/specifications/README.md` ; glossaire (arc42 §12) ; arc42 §6 (chaîne automatisée), §8.7 (migrations V18 à V20), §10 (Q-05 mesuré) ; `docs/architecture/quality/` (rapport de mesure) ; `docs/architecture/diagrams/README.md` si un diagramme d'architecture change.
- **ADR** : ADR-0031 et ADR-0032 (Acceptés) couvrent la planification et les migrations ; ADR-0013, ADR-0016 (Proposés) restent la source des niveaux et du cloisonnement ; ADR-0021 encadre les licences. Aucun nouvel ADR n'est requis : aucune décision d'architecture durable nouvelle (design.md, « Décisions »). Aucun ADR n'est modifié.

## Conception requise

Complète : le changement touche plusieurs modules, modifie le modèle de données (trois migrations), introduit deux cycles de vie (impact, abonnement), des traitements planifiés avec concurrence et reprise, des droits et une mesure de performance.

## Décisions du porteur du projet sur les propositions (2026-11-19)

| ID | Décision | Propositions confirmées |
|---|---|---|
| K1 | INV-C3 est un invariant au repos, vérifié par le contrôle d'intégrité et gardé par le refus 409 des transitions qui le violeraient ; aucune contrainte en base. | P-13, PI-3 |
| K2 | Le dépassement de la cible p95 par `GET /api/v1/me/veille` est consigné ; son optimisation fait l'objet d'un changement dédié. | P-18 |
| K3 | Jeu de référence de la campagne de performance : 20 projets × 100 composants, 1 000 avis, 4 clients simultanés (réponse à Q-PERF-1 et Q-PERF-2). | P-9 |
| K4 | Un impact qui n'est plus vérifié après la modification ou le retrait d'un avis passe à `résolu`, motif `AVIS_MODIFIE` ou `AVIS_RETIRE`. | P-3 |
| K5 | Dans la pile E2E, la synchronisation et l'import planifiés tournent toutes les 5 secondes contre les sources et forges simulées. | PI-1 |
| K6 | L'IHM ne propose suspendre, réactiver et supprimer que sur les abonnements `PERSONAL` ; un abonnement `TEAM` y figure avec son état, sans action. | PA-1 (tasks.md) |

## Propositions retenues faute de pouvoir poser la question

Options les plus prudentes, à confirmer par le porteur du projet ; chacune est reprise dans design.md.

| ID | Proposition | Justification de prudence |
|---|---|---|
| P-1 | L'import manuel reste celui des méthodes de service existantes ; aucun nouveau point d'accès REST d'import n'est ouvert. | J3 exclut tout point d'entrée entrant et dit « existant » : n'ouvrir aucune surface. |
| P-2 | Contenu normalisé = champs du signal normalisé (identifiant, titre, résumé, gravité, alias, paquets et plages, date de publication, retrait), hors date de modification déclarée par la source et date de collecte ; JSON canonique (clés triées) haché en SHA-256. Les signaux déjà enregistrés reçoivent leur empreinte normalisée au premier passage, sans nouvelle version. | Une date de modification seule ne change pas ce que la corrélation lit ; la transition n'invente pas de versions. |
| P-3 ([DÉCISION] K4) | Un impact qui n'est plus vérifié après la modification ou le retrait d'un avis passe aussi à `résolu` (motif `AVIS_MODIFIE` ou `AVIS_RETIRE`) au lieu d'être supprimé. | J5 ne cite que J4 ; supprimer effacerait la trace d'un impact confirmé (arc42 §4.2 P3). |
| P-4 | Un abonnement suspendu ne contribue plus à « Ma veille » (comme l'état `INACTIVE` actuel) ; sa réactivation ne rejoue pas les éléments parus pendant la suspension. | Prolonge le comportement existant ; évite une rafale de notifications. |
| P-5 | Transitions d'état réservées aux rôles `CONTRIBUTEUR` et `ADMINISTRATEUR` de l'espace du projet ; `LECTEUR` refusé (403, journalisé) ; motif obligatoire pour `écarté`. | RBAC existant ; écarter un risque doit être justifié et traçable. |
| P-6 | Un impact `écarté` n'est jamais résolu ni rouvert par le système ; un impact `résolu` par le système et de nouveau vérifié par une corrélation repasse `ouvert` (transition système historisée) ; un impact `résolu` par un utilisateur autorisé n'est jamais rouvert par le système (précision de l'intégration, 2026-11-19, pour appliquer J5). | Respecte la décision humaine d'écarter ; ne laisse pas un risque redevenu réel caché sous « résolu ». |
| P-7 | Dépôts d'un projet dans une table `project_repository` alimentée depuis les colonnes `scm_*` existantes (dépôt « principal »), conservées en lecture jusqu'à un nettoyage ultérieur ; un composant présent dans plusieurs dépôts apparaît une fois, avec ses dépôts ; son niveau d'impact suit la meilleure preuve parmi ses occurrences ; `activite-projets` lit le dépôt principal. | Migration sans perte, sans changement pour les modules qui lisent déjà un seul dépôt. |
| P-8 | Suspension, réactivation et suppression : par le propriétaire d'un `PERSONAL`, par un administrateur de l'espace pour un `TEAM` ; le retrait existant d'un `PERSONAL` (`/leave`) devient une suspension. | Mêmes droits que la création (ABO-06, décision Q-5) ; aucune perte de critères. |
| P-9 ([DÉCISION] K3) | Mesure J8 par un script Node sans dépendance (`fetch`, `perf_hooks`) dans `e2e/performance/`, commande `npm --prefix e2e run test:performance` ; volume de données et concurrence : valeurs par défaut documentées dans le rapport, en attendant Q-PERF-1 et Q-PERF-2. | Aucune dépendance ni licence à ajouter ; autocannon (MIT) reste l'alternative. |
| P-10 | Un échec de recorrélation après un import laisse l'inventaire importé, est journalisé et la recorrélation est relancée au passage planifié suivant (marqueur persisté). | Pas d'inventaire refusé pour une panne de corrélation ; aucune recorrélation perdue. |
| P-11 | Numéros de migration réservés par volet : V18 (A), V19 (B), V20 (C). | Les volets avancent en parallèle sans conflit de numéro. |
| P-12 | Cadences par défaut : synchronisation des sources toutes les heures (`PT1H`), import d'inventaire à 03:00 (Europe/Paris) chaque jour. | Respecte J1 et J3 ; l'heure de nuit évite les heures de travail et les tâches existantes de 04:30 à 08:00. |
| P-B1 | Volet B (2026-11-19) : l'import manuel remplace l'inventaire du dépôt principal du projet ; un projet sans dépôt principal reçoit un inventaire « sans dépôt », remplacé par le premier import d'un de ses dépôts. Dans la synthèse, origine et dates restent celles du dépôt principal ; le dernier échec d'un dépôt n'est restitué que s'il suit son dernier import réussi ; un quota épuisé suspend la forge concernée jusqu'au passage suivant. | Garde les méthodes d'import manuel sans paramètre de dépôt (P-1) et les champs existants de la synthèse inchangés pour un projet à un dépôt ; n'affiche pas un échec déjà rattrapé. |
| P-13 ([DÉCISION] K1) | INV-C3 (impact sans composant ⇒ `résolu` ou `écarté`) est un invariant au repos vérifié par le contrôle d'intégrité, non une contrainte `CHECK` (volet C, 2026-11-19). | Une contrainte ferait échouer l'import qui retire un composant : la recorrélation qui résout l'impact s'exécute après la validation de l'import. |
| P-14 | Un impact au composant retiré retrouve ce composant s'il revient à la même version : un impact `résolu` repasse `ouvert` (`DE_NOUVEAU_VERIFIE`), un impact `écarté` reste écarté (volet C). | Aucun doublon pour le même signal, projet, paquet et version (INV-C4) ; la décision humaine d'écarter est respectée. |
| P-15 | Les listes d'impacts (signal, projet, espaces, veille, outils MCP) restituent par défaut les seuls impacts `ouvert` et `confirmé` ; la recherche (`argos_rechercher_veille`, V11) n'est pas modifiée et rattache un avis à un projet par tout impact (volet C). | Conserve le comportement antérieur, où un impact sans objet était supprimé ; ne touche pas une fonction de recherche d'un autre changement. |
| P-16 | Un avis qui ne déclare plus aucun paquet affecté est traité comme retiré (motif `AVIS_RETIRE`), sinon `AVIS_MODIFIE` (volet C). | Le normaliseur OSV vide la liste des paquets d'un avis `withdrawn` et ne conserve pas d'indicateur de retrait. |
| P-17 | Une alerte immédiate en attente dont tous les impacts visibles du destinataire sont écartés ou résolus passe `ANNULEE` (nouvel état, V20), reste consultable et est remise en attente si l'impact redevient ouvert (volet C). | ALR-07-S1 vise une alerte « en attente d'envoi » ; supprimer la ligne effacerait la trace, et la clé d'idempotence ALR-03 est conservée. |
| P-18 ([DÉCISION] K2) | Le dépassement mesuré de `GET /api/v1/me/veille` (p95 = 618 ms puis 533,8 ms sur la branche intégrée, le 2026-11-19) est consigné sans correction dans ce changement ; son optimisation relève d'un changement ultérieur (volet C). | J8 demande une mesure et un rapport ; optimiser « Ma veille » sort du périmètre du volet. |
| PI-1 ([DÉCISION] K5) | Intégration (2026-11-19) : dans la pile E2E, la synchronisation des sources d'avis et l'import des inventaires restent actifs, à une cadence de 5 secondes, et n'interrogent que le serveur local de sources et de forges simulées (`e2e/support/sources-simulees.mjs`, réponses enregistrées de `e2e/donnees/chaine/`, jetons fictifs) ; les autres tâches qui joindraient une source externe restent désactivées. | Seule façon de vérifier de bout en bout les échéances planifiées (COL-09-S1, INV-07-S1) sans route de déclenchement ajoutée ni appel à Internet ; la campagne de performance garde les deux tâches désactivées. |
| PI-2 | Intégration : `GET /api/v1/projects/{id}/impacts` restitue les impacts d'un projet, filtrés par le paramètre répétable `etat` (par défaut `ouvert` et `confirmé`), sous la RLS et avec le refus journalisé d'un projet hors périmètre. | COR-12-S2 vise la liste des impacts d'un projet, que seule la liste d'un signal filtrait ; lecture seule, aucun point d'accès d'import (P-1). |
| PI-3 ([DÉCISION] K1) | Intégration : une transition utilisateur vers `ouvert` ou `confirmé` d'un impact dont le composant a quitté l'inventaire est refusée (409, transitions possibles nommées) et n'est pas proposée dans `transitionsPossibles`. | Garde INV-C3 et J5 sans contrainte `CHECK` (P-13) ; évite un va-et-vient avec la résolution système suivante. |
| PI-4 | Intégration : une corrélation ne publie aux alertes que les impacts nouveaux, rouverts (par le système, ou par un utilisateur depuis la corrélation précédente), dont le niveau change ou portés par une nouvelle version de l'avis ; la recorrélation d'un projet sans changement (import quotidien, J4) ne publie rien. | Sans cela, l'import quotidien, qui recorrèle chaque projet, produisait après la réactivation d'un abonnement une alerte pour les signaux corrélés pendant la suspension, contrairement à ABO-07-S2 et J7 ; la clé d'idempotence ALR-03 reste inchangée. |

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 (formaliser-chaine-securite-poc) | Correspondance avec les exigences `F01`–`F16` des ADR | non | Product Owner / architecte |
