# ADR-0036 — Pile d'observabilité du serveur

- **Statut** : Proposé
- **Date** : 2026-11-24
- **Décideurs** : porteur du projet (décisions Q-1, Q-2 et Q-4 du changement `instrumenter-observabilite`, 2026-11-24) ; acceptation de l'ADR à venir
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- Constat AUD-DEP-07 de l'audit du 2026-11-21 (révision `b142695`) : le serveur n'a ni journaux structurés, ni métriques, ni traçage ; `/q/health` ne reflète que la base de données. arc42 §8.6 prévoit « logs structurés, métriques et traces lorsque pertinent ».
- Les sprints 8 à 10 de l'audit déplacent la chaîne d'ingestion vers un traitement asynchrone : sans mesure préalable des exécutions planifiées et de l'état des sources, une régression ne se verrait pas.
- Argos s'exploite sur le Synology du porteur ([ADR-0026](0026-hebergement-synology.md)) avec Docker Compose ; aucun collecteur de métriques ni de traces n'y tourne.
- Les traitements périodiques sont des méthodes `@Scheduled` du serveur ([ADR-0031](0031-planification-quarkus-scheduler.md)) ; un conteneur dont le contrôle de santé échoue est redémarré (changement `completer-configuration-conteneurs`).
- Dépendances sous licences permissives ([ADR-0021](0021-double-licence.md)).

## Critères de décision

1. Journaux exploitables par un outil (une ligne JSON par événement) et corrélés à leur requête ou à leur exécution planifiée.
2. Métriques techniques et métier lisibles sans infrastructure supplémentaire, refusées à un appelant anonyme, sans donnée personnelle dans les étiquettes.
3. Une dépendance externe dégradée ne rend pas le serveur indisponible.
4. Aucune dépendance hors de la plateforme Quarkus ; licences Apache-2.0.
5. Journaux lisibles en développement.

## Options considérées

### Option A — Micrometer avec registre Prometheus, journaux JSON de Quarkus, traçage différé

- avantages : extensions de la plateforme Quarkus (`quarkus-micrometer-registry-prometheus`, `quarkus-logging-json`) ; format Prometheus lisible par une simple requête ou par tout collecteur ultérieur ; métriques HTTP, JVM et pool fournies ;
- inconvénients : un passage ultérieur à OpenTelemetry pour les traces ajoutera une seconde dépendance ;
- risques : cardinalité des étiquettes, tenue par une règle de nommage ;
- coûts/complexité : faibles.

### Option B — OpenTelemetry pour les métriques, les traces et les journaux

- avantages : une seule pile pour les trois signaux ;
- inconvénients : exporte vers un collecteur OTLP absent du NAS ; sans collecteur, rien n'est lisible ;
- risques : un conteneur de plus sur un NAS de 8 Go ;
- coûts/complexité : élevés pour l'usage actuel.

### Option C — Journaux texte enrichis, sans métriques

- avantages : aucune dépendance ;
- inconvénients : non exploitable par un outil ; aucune mesure de référence avant la refonte de l'ingestion ;
- risques : régressions invisibles ;
- coûts/complexité : nuls.

## Décision

Option A :

1. **Journaux** : `quarkus-logging-json` ; une ligne JSON par événement en production (`%prod`), texte en développement et en test. Le MDC porte l'identifiant de corrélation (`correlation`) de chaque requête HTTP (API, MCP, points d'exploitation) et de chaque exécution planifiée, et le nom de la tâche (`tache`). Le serveur crée toujours l'identifiant et le restitue dans l'en-tête de réponse `X-Request-Id` ; aucune valeur reçue n'est reprise (décision Q-4). Une erreur est journalisée avec l'exception en premier argument (type, message, pile, causes). Tout jeton JWT cité dans un message, dans le message d'une exception journalisée ou dans ses causes, est masqué par le filtre de journal `masquage-jetons`.
2. **Métriques** : `quarkus-micrometer-registry-prometheus`, exposition sur `/q/metrics`, réservée à l'administrateur plateforme par la politique HTTP `admin-plateforme` sur le port publié (décision Q-1) ; `/q/health` reste public. Métriques métier préfixées `argos_`, étiquettes bornées (`tache`, `resultat`, `etat`, `issue`), jamais un compte, un espace, un projet, un avis ni une adresse.
3. **Santé** : contrôles `@Readiness` nommés, un par tâche planifiée active (`tache-<identité>`), et un par dépendance facultative (`sources-avis`, `envoi-alertes`, `passerelle-ia`) ; ils restent `UP` et publient leur état dégradé dans leurs données. Seul le contrôle des connexions à la base rend le serveur indisponible. Une tâche est en retard au-delà de deux intervalles de son déclencheur sans exécution terminée (`argos.observabilite.taches.facteur-retard`, décision Q-2).
4. **Traçage distribué** (OpenTelemetry) : différé jusqu'à l'existence d'un collecteur.

## Conséquences positives

- Exécutions planifiées, sources, file d'envoi et appels IA mesurés avant la refonte asynchrone de l'ingestion.
- Un incident se suit d'une ligne de journal à toutes celles de la même requête ou exécution.
- Aucun redémarrage du conteneur pour une panne externe.

## Conséquences négatives

- Les journaux de production passent du texte au JSON : l'exploitant qui lit `docker compose logs` doit s'y adapter (guide de déploiement).
- La lecture des métriques demande un jeton OIDC d'administrateur plateforme ; un collecteur futur devra en obtenir un.
- `/q/health/ready` reste public : chaque appel lit l'état des sources et la file d'envoi en base, et publie les identifiants des sources d'avis (communes à l'instance), les noms des tâches et leurs dernières exécutions, sans donnée d'espace ni donnée personnelle.

## Conséquences neutres / compromis

- L'état des tâches est tenu en mémoire et repart à vide au redémarrage : une tâche est « jamais exécutée » jusqu'à sa première exécution, sans être signalée en retard avant le délai toléré compté depuis le démarrage.
- Jauges des sources et de la file d'envoi relevées à chaque contrôle de santé et après chaque passage planifié, sans requête à la base pendant l'exposition des métriques.

## Méthode de validation

- Tests d'intégration `io.argos.observabilite` : journal JSON de production capturé dans un fichier (`JournauxJsonTest`), métriques et contrôles de santé (`MetriquesEtSanteTest`), base injoignable (`BaseInjoignableTest`).
- Test d'architecture `TachesObserveesTest` : toute méthode `@Scheduled` porte `@TacheObservee`.
- Contrôle des licences : `mvn -B -f argos-server/pom.xml -P licences validate`.
- Campagne de performance PERF-01 rejouée après instrumentation.

## Traçabilité

- **Exigences** : EXP-13, EXP-14, EXP-15 (fiche [`exploitation-plateforme`](../../specifications/exploitation-plateforme.md)).
- **Scénarios qualité** : PERF-01 (absence de régression).
- **Diagrammes** : DF-EXP-3 ; DC-1 et DC-2 du changement `instrumenter-observabilite`.
- **Tickets/PR** : constat AUD-DEP-07 ; changement `instrumenter-observabilite`.
- **Fichiers source** : `argos-server/src/main/java/io/argos/observabilite/`, `argos-server/src/main/java/io/argos/api/security/PolitiqueAdminPlateforme.java`, contrôles `Sante*` des modules `ingestion`, `alerting` et `aigateway`, section « Observabilité » d'`application.properties`.

## Notes

Toute décision remplacée doit rester versionnée et pointer vers son successeur.
