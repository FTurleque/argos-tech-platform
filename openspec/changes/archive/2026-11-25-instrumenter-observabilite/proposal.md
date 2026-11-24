# Proposition : instrumenter l'observabilité du serveur

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

L'audit du 2026-11-21 (révision `b142695`) relève une observabilité minimale (AUD-DEP-07) : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier ; `/q/health` ne reflète que la base [EXISTANT — `/docs/audit/2026-11-21/findings.md`]. Le sprint 4 le traite en premier parce que les sprints 8 à 10 déplacent la chaîne d'ingestion vers un traitement asynchrone : sans métriques ni journaux structurés, une régression ne se verrait pas [PROPOSITION — `/docs/audit/2026-11-21/sprints.md`, « Sprint 4 »]. arc42 §8.6 prévoit déjà « logs structurés, métriques et traces lorsque pertinent » [PROPOSITION — `/docs/architecture/arc42/08-concepts-transverses.md`].

## What Changes — Changements

- En production, le serveur écrit des journaux structurés (une ligne JSON par événement), avec un identifiant de corrélation par requête et par exécution de tâche planifiée, et la cause complète des erreurs (EXP-13).
- Le serveur expose des métriques techniques et métier sur `/q/metrics`, réservées à l'exploitation (EXP-14).
- Le contrôle de santé rend compte de chaque tâche planifiée et des dépendances facultatives (sources, envoi d'e-mails, IA) sans rendre le serveur indisponible pour une dépendance dégradée (EXP-15).
- Aucune rupture de contrat d'API (**BREAKING** : aucun) ; le format des journaux de production change (texte → JSON), ce qui concerne l'exploitant qui les lit.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune : la capacité `exploitation-plateforme` (préfixe `EXP`) est créée par `fiabiliser-sauvegarde-et-roles-base`.

### Modified Capabilities — Capacités modifiées

- `exploitation-plateforme` : exigences ajoutées EXP-13 à EXP-15. Delta ADDED, sans Purpose.

## Périmètre

### Inclus

- Journalisation JSON en production, identifiant de corrélation (requêtes HTTP, appels MCP, exécutions planifiées).
- Registre de métriques, endpoint `/q/metrics` protégé, métriques métier listées en design.md.
- Contrôles de santé : un par tâche planifiée (inventaire arc42 §8.5), sources d'avis, envoi d'e-mails, passerelle IA.

### Exclu

- Le traçage distribué (OpenTelemetry) : aucun collecteur n'existe ; l'audit le renvoie à plus tard (« quand un collecteur existe ») [PROPOSITION — action d'AUD-DEP-07].
- Une pile de collecte ou de tableaux de bord (Prometheus, Grafana) : hors dépôt ; [QUESTION] Q-3.
- L'isolation des erreurs aux frontières (AUD-QUA-20, AUD-QUA-23) : changement `isoler-erreurs-aux-frontieres` ; ce changement fournit seulement le format qui transporte la cause.
- Les contrôles de santé des conteneurs : changement `completer-configuration-conteneurs` (EXP-09), qui s'appuie sur `/q/health/ready`.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Exploitant | lit journaux, métriques et état de santé | [PROPOSITION] — EXP-13 à EXP-15 |
| Développeur des sprints 8 à 10 | compare les métriques avant et après la refonte de l'ingestion | [PROPOSITION] — `/docs/audit/2026-11-21/sprints.md` |
| Porteur du projet | tranche Q-1 à Q-4 | [QUESTION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Seule l'extension de santé est présente ; aucune extension de journaux JSON, Micrometer ou OpenTelemetry | [EXISTANT] | `argos-server/pom.xml:61` ; recherche à `b142695` |
| Aucune clé `quarkus.log.*` | [EXISTANT] | `argos-server/src/main/resources/application.properties` (recherche à `b142695`) |
| Aucun contrôle `@Readiness` ou `@Liveness` dans le code | [EXISTANT] | recherche dans `argos-server/src/main/java` à `b142695` |
| `/q/health` et `/q/health/*` publics | [EXISTANT] | `application.properties:294` |
| 13 tâches planifiées, sans chevauchement, désactivables par `off` | [DÉCISION] | ADR-0031 (Accepté) ; arc42 §8.5 « Tâches planifiées » |
| État `DEGRADED` d'une source d'avis | [EXIGENCE] | fiche `/docs/specifications/collecte-avis-securite.md` (DF-COL-3) |
| Métriques clés visées (fraîcheur des sources, alertes, latence API/MCP, coût IA…) | [PROPOSITION] | arc42 §8.6 |
| Critère de sortie : `/q/metrics`, journaux JSON, contrôle de santé par tâche planifiée | [PROPOSITION] | `/docs/audit/2026-11-21/sprints.md`, « Sprint 4 » |
| Dépendances sous licences permissives | [DÉCISION] | ADR-0021 |

## Écarts constatés

- Comparaison des preuves de l'audit avec `b142695` : identiques (l'audit cite `pom.xml:61` à la vérification du 21/11 ; la « preuve d'origine » citait `:53-56`).
- arc42 §8.5 annonce « health/readiness distincts » et §8.6 des métriques et journaux structurés : rien n'est implémenté.
- arc42 §11 parle d'une « observabilité minimale mais structurée » : les journaux sont en texte libre.

## Impact

- **Composants** (noms de arc42 §5) : Quarkus Backend — API & Security (protection de `/q/metrics`), source-ingestion, alerting, ai-gateway, mcp-server (identifiant de corrélation, métriques), et chaque module porteur d'une tâche planifiée.
- **Contrats d'interface** : nouvel endpoint d'exploitation `/q/metrics` (non versionné sous `/api/v1`) ; réponse de `/q/health/ready` enrichie de contrôles nommés. Aucun changement OpenAPI ni MCP.
- **Données et persistance** : aucune donnée nouvelle ; lectures d'état (dernière exécution, file d'envoi) pour la santé et les métriques.
- **Sécurité, confidentialité, droits** : aucune donnée personnelle (e-mail, contenu d'avis privé, jeton) dans les journaux ni dans les étiquettes de métriques ; `/q/metrics` non public.
- **Exploitation et observabilité** : objet du changement.
- **Documentation durable à mettre à jour** : fiche `/docs/specifications/exploitation-plateforme.md` ; arc42 §8.5, §8.6, §11 ; `/docs/deploiement/synology-et-pc.md` (lecture des journaux et des métriques).
- **ADR** : ADR-0031 (Accepté), ADR-0021 (Accepté). Décision candidate à un ADR : choix de la pile d'observabilité (Micrometer + Prometheus, OpenTelemetry différé).
- **Dépendances entre changements** : dépend de `fiabiliser-sauvegarde-et-roles-base` (création de la capacité `exploitation-plateforme` et de sa fiche). Complète `completer-configuration-conteneurs` (le contrôle de santé du conteneur lit `/q/health/ready`). Prépare `isoler-erreurs-aux-frontieres` (journal de la cause) et les sprints 8 à 10 (ingestion asynchrone).

## Conception requise

complète — nouvelles dépendances, nouvel endpoint d'exploitation à protéger, convention transverse (corrélation, nommage des métriques) touchant tous les modules.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Comment protéger `/q/metrics` : port de gestion séparé non publié, rôle d'administration plateforme, ou jeton d'exploitation ? | non : EXP-14 impose le refus d'un appel anonyme, le moyen se règle en conception | porteur du projet |
| Q-2 | Au-delà de quel retard une tâche planifiée est-elle signalée en retard (par exemple deux échéances manquées) ? | non : EXP-15 se teste avec une valeur configurée | porteur du projet |
| Q-3 | Une pile de collecte (Prometheus, Grafana, ou la supervision de DSM) est-elle prévue sur le NAS, et dans quel changement ? | non | porteur du projet |
| Q-4 | Le serveur accepte-t-il un identifiant de corrélation transmis par l'appelant (en-tête), ou en crée-t-il toujours un ? | non : EXP-13 garantit un identifiant par requête | porteur du projet |
