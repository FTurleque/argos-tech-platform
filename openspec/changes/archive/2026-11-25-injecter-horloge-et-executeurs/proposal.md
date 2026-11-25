# Proposition : injecter l'horloge et les exécuteurs

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le serveur lit l'heure système partout et crée ses propres fils hors du conteneur CDI, d'après l'audit du 2026-11-21 (`/docs/audit/2026-11-21/findings.md`) :

- 63 appels Java à `now()` dans 37 fichiers, sans `java.time.Clock` commun ; seuls `CollecteWeb` et `LimiteurDebit` ont une horloge remplaçable ; deux `LocalDate.now()` dépendent du fuseau de la JVM et non du fuseau configuré (AUD-QUA-10) ;
- en conséquence, quatre tests dépendent de l'horloge réelle et de délais fixes : résumé simulé qui dort 5 s contre une attente bornée à 4 s, `Thread.sleep(1000)` avant une assertion de verrou, attente active de 5 s, borne de 2 900 ms sur l'horloge réelle pour un délai de passerelle IA de 2 s (marge de 900 ms) (AUD-TST-06, dépend d'AUD-QUA-10) ;
- trois exécuteurs statiques créés hors du cycle de vie CDI ne sont jamais arrêtés (AUD-QUA-11).

Ces constats de sévérité faible relèvent du sprint 4 de l'audit (« Observabilité et frontières d'erreur », `/docs/audit/2026-11-21/sprints.md`) ; ils deviennent coûteux dès que l'intégration continue s'exécute sur un exécuteur partagé, où les marges de temps réel donnent des échecs intermittents. Le changement est un refactoring sans changement de comportement observable.

## What Changes — Changements

- Un producteur CDI unique de `java.time.Clock`, au fuseau `argos.alerting.fuseau` ; les appels `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `OffsetDateTime.now()`, `ZonedDateTime.now()` de `argos-server/src/main` passent par ce `Clock` (`now(clock)`) ; `CollecteWeb` et `LimiteurDebit` s'y raccordent.
- Les deux `LocalDate.now()` sans fuseau (référentiel d'espace, outil MCP du référentiel) utilisent le fuseau configuré, comme le radar.
- Là où une même règle compare un `now()` SQL et un instant calculé en Java, l'instant Java est passé en paramètre SQL ; les `now()` SQL purement d'horodatage restent (Q-2).
- Tests : horloge et délai simulés au lieu de dormir (résumé IA lent), observation du verrou PostgreSQL au lieu d'un `Thread.sleep(1000)`, attente bornée par Awaitility au lieu des boucles actives, assertion de délai de la passerelle IA sans marge sur l'horloge réelle.
- Exécuteurs : un bean CDI `@ApplicationScoped` (ou `ManagedExecutor`) possède chaque exécuteur, l'arrête à l'arrêt de l'application et lit sa taille de la configuration ; le motif « soumettre, attendre avec délai, annuler » commun à l'alerte et à la synthèse d'activité est factorisé.
- Aucun comportement visible modifié, aucune rupture de contrat (**BREAKING** : aucun) ; voir « Écarts constatés » pour le seul cas limite (fuseau de la JVM différent du fuseau configuré).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

Aucune — refactoring sans changement de comportement spécifié : `skip_specs: true` dans `.openspec.yaml`.

## Périmètre

### Inclus

- `argos-server/src/main/java` : producteur du `Clock`, remplacement des `now()` Java, paramètres SQL là où la règle compare avec Java, exécuteurs gérés par CDI.
- `argos-server/src/test/java` : `alerting/AlertesTest`, `testsupport/SimulatedResumeIa`, `api/comptes/DefenseEnProfondeurTest`, `signal/web/CollecteWebTest`, `aigateway/PasserelleIaTest`, `testsupport/StubServer`, et tout test qui remplace l'horloge.
- `application.properties` : tailles des exécuteurs (valeurs actuelles reprises).
- Arc42 §8.5 (tâches planifiées, exécuteurs), §8.11 (tests indépendants du temps réel), §8.16 (conventions : horloge injectée).

### Exclu

- Les `now()` SQL d'horodatage pur (`created_at`, `updated_at`, colonnes par défaut) : même résultat, aucune règle comparée à Java.
- Métriques des exécuteurs et contrôle de santé des tâches planifiées : changement `instrumenter-observabilite` (même sprint).
- Isolation des erreurs imprévues aux frontières : changement `isoler-erreurs-aux-frontieres`.
- Tout changement d'échéance, de fréquence ou de délai métier.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Développeur (humain ou agent) | écrit des tests du temps sans attente réelle ; lit une seule source de l'heure | [PROPOSITION] — AUD-QUA-10, AUD-TST-06 |
| Exploitant | arrêt propre du serveur (fils arrêtés), tailles d'exécuteur configurables | [PROPOSITION] — AUD-QUA-11 |
| Porteur du projet | tranche le fuseau du `Clock` et le sort des `now()` SQL | [PROPOSITION] — Q-1, Q-2 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| 63 appels `(Instant\|LocalDate\|LocalDateTime\|OffsetDateTime\|ZonedDateTime).now(` dans 37 fichiers, aucun `Clock` | [EXISTANT] | `grep -rnE` sur `argos-server/src/main/java` à `b142695` (63 lignes, 37 fichiers, 0 `Clock`) |
| Horloges remplaçables existantes | [EXISTANT] | `signal/web/CollecteWeb.java:81` (`Supplier<Instant> horloge`) ; `api/security/LimiteurDebit.java:31` (`LongSupplier horloge`) |
| `LocalDate.now()` sans fuseau | [EXISTANT] | `catalog/standard/ReferentielEspace.java:138` ; `mcp/OutilsReferentiel.java:98` |
| Fuseau configuré et usage par le radar | [EXISTANT] | `application.properties:65` (`argos.alerting.fuseau`, défaut `Europe/Paris`) ; `alerting/RappelsRadar.java:86-90` |
| `now()` SQL et `Instant.now()` Java dans la même mise à jour | [EXISTANT] | `signal/web/StockActualites.java:249-252` |
| Décision métier sur l'heure système (invitation expirée) | [EXISTANT] | `workspace/EspacesService.java:254` (Java) et `:230` (SQL `now()`) |
| Tests sur l'horloge réelle et délais fixes | [EXISTANT] | `alerting/AlertesTest.java:314-323` + `testsupport/SimulatedResumeIa.java:73` (`Thread.sleep(5_000)`) + `application.properties:75` (`PT1S` en test) ; `api/comptes/DefenseEnProfondeurTest.java:160` ; `signal/web/CollecteWebTest.java:120-125` ; `aigateway/PasserelleIaTest.java:266-269` |
| Contre-exemple sain : observation de `pg_locks` | [EXISTANT] | `chain/ImportPlanifieTest.java:320-328` |
| Exécuteurs statiques jamais arrêtés | [EXISTANT] | `alerting/MoteurAlertes.java:80` ; `projectintel/SyntheseActivite.java:49` ; `ingestion/web/RecuperateurWeb.java:51` ; aucun `@PreDestroy`, `shutdown` ni `ManagedExecutor` dans `src/main` |
| Planification par Quarkus Scheduler | [DÉCISION] | ADR-0031 (Accepté) |
| Stack Java 17 et Quarkus | [DÉCISION] | ADR-0019 (Accepté) |
| Critère de sortie du sprint 4 (part de ce changement) | [PROPOSITION] | `/docs/audit/2026-11-21/sprints.md`, « Sprint 4 » |

## Écarts constatés

- Preuves de l'audit inchangées à `b142695` (décomptes 63 / 37 / 0, lignes citées). `OutilsReferentiel.java:98` (l'audit cite 98, et 100 dans la preuve d'origine) ; `DefenseEnProfondeurTest.java:160` dans la plage 159-161 de l'audit.
- `PasserelleIaTest.java:266-269` : le stub répond en 3 000 ms, le délai configuré est `PT2S` (`testsupport/StubServer.java:91`) et l'assertion exige moins de 2 900 ms sur `System.nanoTime()` : 900 ms de marge au-dessus du délai, 100 ms sous la réponse du stub, mesurés sur l'horloge réelle. Constat conforme à l'audit.
- Fuseau : passer les deux `LocalDate.now()` au fuseau configuré change la date « du jour » seulement si le fuseau de la JVM diffère de `argos.alerting.fuseau` ; dans l'image, `TZ` vaut `Europe/Paris` par défaut (`compose.yaml:102`), comme le fuseau configuré. Le comportement est identique dans la configuration livrée ; il devient cohérent dans les autres (Q-1).
- Le critère de sortie du sprint 4 (« aucun `Thread.sleep` dans `src/test` ») vise aussi `chain/ImportPlanifieTest.java:326`, boucle d'observation de `pg_locks` citée comme contre-exemple sain, et `testsupport/StubServer.java:143`, qui simule un serveur lent. Les deux sont convertis (Awaitility, délai simulé sans sommeil) ou motivés (Q-3).

## Impact

- **Composants** (noms de arc42 §5) : `alerting`, `project-intel`, `source-ingestion` (récupérateur web), `catalog`, `mcp-server`, `workspace`, `API & Security` (`LimiteurDebit`) et tout composant qui lit l'heure ; aucun nouveau composant métier.
- **Contrats d'interface** : aucun ; OpenAPI et MCP identiques.
- **Données et persistance** : aucune migration ; certaines requêtes reçoivent l'instant en paramètre au lieu de `now()`.
- **Sécurité, confidentialité, droits** : aucun changement ; le contexte de sécurité et la RLS restent établis dans les fils d'exécution comme aujourd'hui (à vérifier par les tests existants).
- **Exploitation et observabilité** : arrêt propre des fils ; deux à trois propriétés de taille d'exécuteur, valeurs actuelles par défaut.
- **Dépendances entre changes** : sa vérification sans changement de comportement s'appuie sur `mettre-en-place-integration-continue` (suite complète et couverture à chaque pull request). `instrumenter-observabilite` (même sprint) peut instrumenter les exécuteurs une fois gérés par CDI : ordre recommandé, ce changement d'abord.
- **Documentation durable à mettre à jour** : arc42 §8.5 (exécuteurs gérés par CDI, arrêt), §8.11 (tests du temps sans attente réelle), §8.16 (horloge injectée, aucun `now()` direct), glossaire si « horloge du serveur » y entre. Aucune fiche de capacité ni registre `/docs/specifications/README.md` : aucun comportement spécifié.
- **ADR** : ADR-0031 (Accepté) et ADR-0019 (Accepté) inchangés. Aucune décision candidate à un nouvel ADR (conventions locales consignées dans arc42 §8.16).

## Conception requise

allégée — refactoring transverse mais mécanique ; la conception fixe le producteur du `Clock`, la règle de remplacement, le modèle de bean d'exécuteur et la conversion de chaque test.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Le `Clock` porte-t-il le fuseau `argos.alerting.fuseau` (action d'AUD-QUA-10), ou UTC avec conversion explicite aux seuls endroits qui calculent une date du jour ? | non : fuseau configuré par défaut | porteur du projet |
| Q-2 | Les `now()` SQL qui ne comparent rien à une valeur Java restent-ils (47 lignes) ou passent-ils tous en paramètre ? | non : restent par défaut | porteur du projet |
| Q-3 | Les deux `Thread.sleep` « sains » (`ImportPlanifieTest`, `StubServer`) sont-ils convertis pour satisfaire le critère strict du sprint 4, ou conservés avec motivation ? | non : conversion par défaut | porteur du projet |
| Q-4 | `ManagedExecutor` de SmallRye Context Propagation (propagation du contexte CDI) ou bean `@ApplicationScoped` possédant un `ThreadPoolExecutor` avec `@PreDestroy` ? | non : D3 | porteur du projet |
