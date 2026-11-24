# Tâches : injecter l'horloge et les exécuteurs

<!--
Refactoring sans changement de comportement (skip_specs: true), audit du 2026-11-21, sprint 4 (/docs/audit/2026-11-21/sprints.md).
Ordre interne de l'audit : AUD-QUA-10, puis AUD-TST-06, puis AUD-QUA-11.
Règles : aucun délai, échéance ni fréquence modifiés ; aucune migration ; dépendances de test sous licence permissive (ADR-0021, Proposé) ;
aucun identifiant d'une ou deux lettres (arc42 §8.16) ; non-régression prouvée par la suite complète en intégration continue.
-->

## 1. Horloge commune

- [x] 1.1 Créer le producteur CDI de `java.time.Clock` au fuseau retenu (Q-1) dans le paquet retenu (Q-5), et un `Clock` réglable pour les tests — couvre AUD-QUA-10 — vérification : test unitaire du producteur (fuseau de `argos.alerting.fuseau`) ; `ModuleBoundariesTest` vert
- [x] 1.2 Remplacer les appels `now()` sans argument de `argos-server/src/main/java` par `now(clock)` ou un instant reçu en paramètre ; raccorder `CollecteWeb` et `LimiteurDebit` — couvre AUD-QUA-10 — vérification : `grep -rnE "(Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime)\.now\(" argos-server/src/main/java` ne trouve que le producteur ; `mvn -B -f argos-server/pom.xml clean verify` vert
- [x] 1.3 Corriger les deux `LocalDate.now()` sans fuseau (`catalog/standard/ReferentielEspace.java:138`, `mcp/OutilsReferentiel.java:98`) — couvre AUD-QUA-10 — vérification : test avec un `Clock` réglé à une heure où le fuseau configuré et UTC donnent deux dates différentes
- [x] 1.4 Passer en paramètre SQL l'instant Java là où une même règle compare `now()` SQL et une valeur calculée en Java (exemples : `signal/web/StockActualites.java:249-252`, `workspace/EspacesService.java:230` et `:254`), selon Q-2 — couvre AUD-QUA-10 — vérification : liste des requêtes modifiées consignée dans ce fichier ; tests existants verts
- [x] 1.5 Ajouter la règle ArchUnit « aucun `now()` sans `Clock` ni `System.currentTimeMillis()` en production » (design.md D2) — couvre AUD-QUA-10 — vérification : règle verte ; un appel introduit sur une branche de test la fait échouer
- [x] 1.6 Documentation du groupe : arc42 §8.16 (horloge injectée), arc42 §5 et table des frontières si un paquet technique est créé — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Tests indépendants du temps réel

- [x] 2.1 `AlertesTest` / `SimulatedResumeIa` : remplacer le `Thread.sleep(5_000)` et la mesure `Instant.now()` par un résumé simulé qui ne répond pas avant le délai et une assertion sur l'issue `INDISPONIBLE` — couvre AUD-TST-06 — vérification : `AlertesTest` vert, sans sommeil ni mesure de l'horloge réelle
- [x] 2.2 `DefenseEnProfondeurTest` : remplacer `Thread.sleep(1000)` par l'observation du verrou (`pg_locks` / `pg_stat_activity`) avec attente bornée, sur le modèle d'`ImportPlanifieTest` — couvre AUD-TST-06 — vérification : test vert ; échoue si le verrou n'est pas pris (contrôle par mutation locale)
- [x] 2.3 `CollecteWebTest` : remplacer la boucle `System.currentTimeMillis()` + `Thread.sleep(10)` par Awaitility (dépendance de test, vérifier le BOM Quarkus) ou par l'horloge simulée de `CollecteWeb` — couvre AUD-TST-06 — vérification : test vert
- [x] 2.4 `PasserelleIaTest` : vérifier le respect de `argos.ia.delai` sans mesurer l'horloge réelle (délai simulé côté stub ou horloge de la passerelle) ; `StubServer` sans `Thread.sleep` et `ImportPlanifieTest` converti, selon Q-3 — couvre AUD-TST-06 — vérification : `grep -rn "Thread.sleep" argos-server/src/test/java` vide ou motivé ; tests verts
- [x] 2.5 Documentation du groupe : arc42 §8.11 (tests du temps sans attente réelle, observation des verrous, Awaitility) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Exécuteurs gérés par CDI

- [x] 3.1 Créer les beans d'exécuteur (ou `ManagedExecutor`, Q-4) pour le résumé d'alerte, la synthèse d'activité et la minuterie de coupure web, avec tailles configurables (valeurs actuelles par défaut) et arrêt par `@PreDestroy` — couvre AUD-QUA-11 — vérification : `grep -rnE "static final .*Executor" argos-server/src/main/java` vide ; test d'arrêt de l'exécuteur à la destruction du bean
- [x] 3.2 Factoriser le motif « soumettre, attendre avec délai, annuler » de `MoteurAlertes` et `SyntheseActivite` (design.md DC-1) — couvre AUD-QUA-11 — vérification : tests d'alerte et de synthèse d'activité verts ; aucune duplication du motif (revue)
- [x] 3.3 Déclarer les propriétés de taille dans `application.properties` avec leur commentaire — couvre AUD-QUA-11 — vérification : démarrage en `%dev` et suite de tests verts avec les valeurs par défaut
- [x] 3.4 Documentation du groupe : arc42 §8.5 (exécuteurs, arrêt, configuration) et glossaire arc42 §12 si un terme nouveau est introduit — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement — vérification : `openspec validate injecter-horloge-et-executeurs --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Non-régression : `mvn -B -f argos-server/pom.xml clean verify` en intégration continue (nombre de tests au moins égal à celui de la base), couverture au moins égale au seuil JaCoCo ; `node scripts/verifier-nommage.mjs` vert — vérification : exécution du workflow liée dans ce fichier

## Requêtes modifiées (tâche 1.4)

Règles qui comparent une date en base à une valeur calculée en Java : l'instant Java est passé en paramètre SQL.

| Fichier | Requête | Règle |
|---|---|---|
| `signal/web/StockActualites.java` | `echue` et `echues` : `prochaine_collecte_le <= ?` | échéance d'une source web, posée en Java par `succes` et `echec` |
| `signal/web/StockActualites.java` | `succes` : `derniere_collecte_le`, `dernier_succes_le`, `prochaine_collecte_le` d'un même instant | prochaine collecte = instant de la collecte + fréquence |
| `signal/web/StockActualites.java` | `echec` : `derniere_collecte_le`, `prochaine_collecte_le` d'un même instant | prochaine tentative = instant de l'échec + attente |
| `workspace/EspacesService.java` | `mesInvitations` : `i.expire_le > ?` | expiration d'une invitation, contrôlée en Java par `accepter` |
| `workspace/ComptesService.java` | `invitationEnAttente` : `expire_le > ?` | même règle d'expiration, à l'inscription sur invitation |

Les autres `now()` SQL horodatent une écriture (`cree_le`, `updated_at`, `finished_at`, battements de cœur, valeurs par défaut) sans comparaison à une valeur Java : ils restent (Q-2).

## Matrice de traçabilité

| Constat | Exigence / scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AUD-QUA-10 | aucune (`skip_specs`) | design.md D1, D2, DC-1 | 1.1 à 1.6 | `architecture/HorlogeInjecteeTest` ; `horloge/ProducteurHorlogeTest` ; `mcp/HorlogeMcpTest` (date du jour au fuseau configuré à 23:30 UTC) ; recherche de `.now(` dans `src/main` limitée à `io.argos.horloge` | vert en local (2026-11-24) |
| AUD-TST-06 | aucune | design.md DC-1, « Transactions, concurrence et idempotence » | 2.1 à 2.5 | `AlertesTest`, `DefenseEnProfondeurTest`, `CollecteWebTest`, `PasserelleIaTest`, `ImportPlanifieTest`, `ResilienceTest`, `VeilleWebTest` sans sommeil ni mesure de l'horloge réelle ; `StubServer` à réponse retenue ; `architecture/TestsSansSommeilTest` | vert en local (2026-11-24) |
| AUD-QUA-11 | aucune | design.md D3, DC-1 | 3.1 à 3.4 | `execution/ExecuteurBorneTest`, `ingestion/web/RecuperateurWebTest.laMinuterieSArreteALaDestructionDuBean` ; aucun `static final .*Executor` dans `src/main` | vert en local (2026-11-24) |
| Non-régression | aucune | design.md « Compatibilité, migration et retour arrière » | 4.2 | `mvn -B clean verify` en intégration continue | non exécuté |

## Workflow follow-up

- Après la fusion, signaler au coordinateur de l'audit le passage d'AUD-QUA-10, AUD-TST-06 et AUD-QUA-11 à « Corrigé » selon les preuves.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive injecter-horloge-et-executeurs`).
- Après archivage, remplacer dans la documentation les liens vers `/openspec/changes/injecter-horloge-et-executeurs/` par le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
