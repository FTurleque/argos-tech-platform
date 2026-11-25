# Conception : injecter l'horloge et les exécuteurs

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

**Niveau de conception** : allégée — refactoring sans comportement nouveau ; seules les sections utiles sont remplies.

## Contexte technique

- Heure : 63 appels Java à `now()` (37 fichiers), 47 lignes `now()` SQL, aucun `Clock` ; horloges locales remplaçables dans `CollecteWeb` (`Supplier<Instant>`, `:81`) et `LimiteurDebit` (`LongSupplier`, `:31`) ; motif déjà présent « point d'entrée planifié → méthode recevant maintenant » (`MoteurAlertes.java:140` `evaluer(evenement, Instant.now())`, `RappelsRadar`) [EXISTANT — proposal.md, « Sources et preuves »].
- Fuseau : `argos.alerting.fuseau` (défaut `Europe/Paris`, `application.properties:65`), repris par `argos.ia.fuseau` (`:158`) et par les tâches planifiées (`timeZone = "{argos.alerting.fuseau}"`, ADR-0031) [EXISTANT].
- Exécuteurs : `MoteurAlertes.EXECUTEUR_IA` (`ThreadPoolExecutor(2, 2, ...)`, soumis `:321`), `SyntheseActivite.EXECUTEUR_IA` (`ThreadPoolExecutor(1, 2, ...)`, soumis `:91`), `RecuperateurWeb.MINUTERIE` (`newSingleThreadScheduledExecutor`, coupure d'un corps de réponse trop lent `:139`) ; tous `static final`, jamais arrêtés [EXISTANT].
- Tests concernés : voir proposal.md (quatre cas d'AUD-TST-06, plus `ImportPlanifieTest:326` et `StubServer:143`).

## Exigences couvertes

Non applicable — `skip_specs: true` ; constats traités : AUD-QUA-10, AUD-TST-06, AUD-QUA-11 (matrice de `tasks.md`). Les comportements temporels existants (expirations, fraîcheur, échéances, délais IA) restent ceux des specs principales, inchangés.

## Objectifs et non-objectifs

**Objectifs :**
- Une seule source de l'heure côté Java, remplaçable en test.
- Aucun test ne dort ni n'attend l'horloge réelle pour décider d'une assertion.
- Chaque fil créé par le serveur appartient à un bean CDI et s'arrête avec l'application.

**Non-objectifs :**
- Remplacer les `now()` SQL d'horodatage pur (Q-2).
- Modifier un délai, une fréquence ou une échéance.

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| Producteur d'horloge (nouveau, paquet transverse à choisir) | produire le `Clock` au fuseau configuré | dépendre d'un module métier | nouveau (technique) |
| alerting, project-intel | soumettre au bean d'exécuteur ; recevoir le `Clock` | posséder un exécuteur statique | modifié |
| source-ingestion (`RecuperateurWeb`) | minuterie de coupure fournie par un bean | — | modifié |
| catalog, mcp-server, workspace, API & Security, autres lecteurs de l'heure | `now(clock)` | appeler `now()` sans `Clock` | modifié |

Emplacement du producteur : paquet technique de premier niveau `io.argos.horloge` (Q-5, défaut appliqué : `extraire-noyau-commun` n'est pas fusionné), module transverse de `ModuleBoundariesTest` (table `TRANSVERSES`), sans dépendance métier et accessible à tous. L'exécuteur borné commun est dans le module transverse `io.argos.execution`, qui propage la corrélation des journaux d'`io.argos.observabilite`.

## Traitements et interactions

### DC-1 — Résumé IA d'une alerte avec exécuteur géré et horloge injectée

Question : qui possède l'exécuteur et où l'horloge intervient-elle dans l'attente bornée du résumé ? Type : UML sequenceDiagram. Vue cible (l'existant diffère par l'exécuteur statique et `Instant.now()`). Constats : AUD-QUA-10, AUD-QUA-11, AUD-TST-06.

```mermaid
sequenceDiagram
    participant moteur as MoteurAlertes (alerting)
    participant executeurs as Bean d'exécuteur IA (CDI)
    participant resumeur as Résumé IA (ai-gateway, contrat)
    participant horloge as Clock (producteur CDI)
    moteur->>horloge: instant()
    moteur->>executeurs: soumettre(résumer, délai max)
    executeurs->>resumeur: resumer(signal, titre, concernés)
    alt réponse avant le délai
        resumeur-->>executeurs: résumé
        executeurs-->>moteur: résumé disponible
    else délai dépassé
        executeurs-->>moteur: indisponible (tâche annulée)
    end
    Note over executeurs: @PreDestroy : arrêt et annulation des tâches en cours
```

L'attente reste bornée par `argos.alerting.resume-ia.delai-max` ; en test, le résumé simulé « lent » ne dort pas : il attend un signal que seul le changement de mode donne, et l'annulation à l'expiration du délai l'interrompt ; l'assertion porte sur l'issue `INDISPONIBLE` sans mesurer l'horloge réelle. Le même motif sert `SyntheseActivite` : `io.argos.execution.ExecuteurBorne.executer` porte « soumettre, attendre avec délai, annuler » et rend une issue typée (`OBTENU`, `REFUSEE`, `DELAI_DEPASSE`, `ECHEC`, `INTERROMPUE`), la cause étant journalisée avec sa pile par l'appelant.

## Transactions, concurrence et idempotence

- Les fils des exécuteurs n'ouvrent pas de transaction pour le résumé (appel du contrat de la passerelle) ; les beans d'exécuteur conservent ce comportement et ne propagent que la corrélation des journaux.
- `DefenseEnProfondeurTest` : la soumission concurrente est observée par `pg_locks` (verrou non accordé) avec attente bornée (Awaitility), sur le modèle d'`ImportPlanifieTest` ; `pg_stat_activity` ne montre pas l'attente d'une session d'un autre rôle au compte propriétaire des tests.

## Gestion des erreurs et reprise

À l'arrêt, `shutdownNow()` interrompt les résumés en cours : l'attente de `MoteurAlertes` se termine en issue `INTERROMPUE` et l'alerte est enregistrée avec le résumé `INDISPONIBLE`, comme pour un délai dépassé ; une soumission après l'arrêt est `REFUSEE`, résumé indisponible.

## Compatibilité, migration et retour arrière

Aucun contrat ni schéma modifié ; tailles d'exécuteur par défaut égales aux valeurs codées en dur (2/2, 1/2, 1) ; retour arrière par revert. La non-régression est prouvée par la suite existante (654 tests) et la couverture inchangée ou en hausse (seuil de `mettre-en-place-integration-continue`).

## Décisions, alternatives et compromis

### D1 — `java.time.Clock` produit par CDI

- **Statut** : [PROPOSITION] ; fuseau [DÉCISION — porteur du projet, 2026-11-24 (Q-1, défaut) : fuseau configuré]
- **Choix** : `@Produces @ApplicationScoped Clock` = `Clock.system(ZoneId.of(argos.alerting.fuseau))` (`io.argos.horloge.ProducteurHorloge`) ; en test, l'horloge réglable `testsupport.HorlogeReglable` installée par `QuarkusMock` ; `CollecteWeb` reçoit le `Clock` injecté. Les durées mesurées utilisent l'horloge monotone : `LimiteurDebit` (fenêtre d'une minute) et `RecuperateurWeb` (échéance d'une récupération) comptent en `System.nanoTime()`, qui n'est pas l'heure murale ; les forges (`scm.ForgeHttp`) reçoivent le `Clock` par leur constructeur.
- **Justification** : action d'AUD-QUA-10 ; type standard du JDK, aucune dépendance nouvelle.
- **Alternatives écartées** : `Supplier<Instant>` par classe (état actuel, dispersé) ; interface maison `Horloge` (réinvente `Clock`).
- **Compromis** : les classes non CDI (records, utilitaires statiques) reçoivent l'instant en paramètre au lieu du `Clock`.
- **ADR** : non : convention consignée dans arc42 §8.16.

### D2 — Garde de régression par test d'architecture

- **Statut** : [PROPOSITION]
- **Choix** : règle ArchUnit « aucune classe de production n'appelle `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `OffsetDateTime.now()`, `ZonedDateTime.now()` sans argument, ni `System.currentTimeMillis()` » (`HorlogeInjecteeTest` ; le producteur crée le `Clock` par `Clock.system` et n'en a pas besoin) ; règle « aucun `Thread.sleep` dans les tests » (`TestsSansSommeilTest`), conversion stricte ([DÉCISION] — défaut de Q-3 appliqué : les deux sommeils « sains » d'`ImportPlanifieTest` et `StubServer` sont convertis).
- **Justification** : critère de sortie du sprint 4 (« une recherche … ne trouve que le producteur du Clock ») rendu exécutable.
- **Alternatives écartées** : `grep` dans le workflow (moins précis : commentaires, chaînes).
- **Compromis** : la règle doit autoriser explicitement le producteur.
- **ADR** : non.

### D3 — Exécuteurs possédés par des beans CDI

- **Statut** : [DÉCISION — porteur du projet, 2026-11-24 (Q-4) : beans `@ApplicationScoped` et `@PreDestroy`]
- **Choix** : un bean `@ApplicationScoped` par usage — `alerting.ExecuteurResumeIa`, `projectintel.ExecuteurSyntheseIa` (sous-classes d'`execution.ExecuteurBorne`) et `ingestion.web.MinuterieCoupure` —, créant son exécuteur à l'initialisation (`@PostConstruct`) avec des tailles configurables et l'arrêtant par `@PreDestroy`.
- **Justification** : action d'AUD-QUA-11 ; arrêt propre, configuration, aucune dépendance nouvelle.
- **Alternatives écartées** : `ManagedExecutor` (propagation de contexte inutile ici) ; exécuteur Vert.x/worker pool Quarkus partagé (un résumé lent occuperait les fils des requêtes) ; conserver les statiques avec un `shutdown hook` (hors cycle de vie CDI, rechargement à chaud non couvert).
- **Compromis** : trois petits beans et une classe de base commune.
- **ADR** : non : décision locale.

## Risques

- Oubli d'un `now()` → règle ArchUnit de D2.
- Changement involontaire d'un instant SQL (paramètre Java au lieu de `now()` de la transaction) → limité aux règles qui comparent déjà avec Java ; chaque remplacement couvert par un test existant ou ajouté.
- Contexte de sécurité ou RLS absent d'un fil géré différemment → tests d'isolation existants (`SubscriptionIsolationTest`, etc.) et tests d'alerte rejoués.

## Écarts code / tests / specs / documentation

Voir proposal.md, « Écarts constatés ».

## Stratégie de vérification

| Constat | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| AUD-QUA-10 | architecture (ArchUnit) | `architecture/HorlogeInjecteeTest` ; la recherche de `(Instant\|LocalDate\|LocalDateTime\|OffsetDateTime\|ZonedDateTime)\.now\(` dans `argos-server/src/main/java` ne trouve que le paquet du producteur (`io.argos.horloge.LectureHeure`) : les beans lisent `horloge.instant()` et `LectureHeure.jour(horloge)` | vert |
| AUD-QUA-10 | unitaire et intégration | `horloge/ProducteurHorlogeTest` (fuseau) ; `mcp/HorlogeMcpTest` (date du jour au fuseau configuré à 23:30 UTC) ; tests existants d'expiration d'invitation, de fraîcheur et du radar | vert |
| AUD-TST-06 | intégration et architecture | `AlertesTest`, `DefenseEnProfondeurTest`, `CollecteWebTest`, `PasserelleIaTest`, `ImportPlanifieTest`, `StubServer` sans sommeil ni mesure de l'horloge réelle ; `architecture/TestsSansSommeilTest` | vert |
| AUD-QUA-11 | unitaire | `execution/ExecuteurBorneTest` (issues, arrêt à la destruction du bean), `ingestion/web/RecuperateurWebTest.laMinuterieSArreteALaDestructionDuBean` ; aucun `static final .*Executor` dans `src/main/java` | vert |
| Non-régression | suite complète | `mvn -B -f argos-server/pom.xml -P integration-continue clean verify` vert, couverture au-dessus du seuil | voir tasks.md 4.2 |

## Documentation durable impactée

- arc42 §8.5 (exécuteurs gérés par CDI, arrêt, tailles configurables), §8.11 (tests du temps sans attente réelle, Awaitility), §8.16 (horloge injectée, aucun `now()` direct).
- arc42 §5 (paragraphe du socle) et table de `ModuleBoundariesTest` si un paquet technique du producteur est créé (Q-5).
- Glossaire arc42 §12 : entrée « Horloge commune ».

## Questions ouvertes

Aucune. Q-4 tranchée par le porteur du projet le 2026-11-24 ; défauts documentés appliqués pour Q-1 (fuseau configuré), Q-2 (`now()` SQL d'horodatage pur conservés), Q-3 (conversion stricte) et Q-5 (paquet technique `io.argos.horloge`). Awaitility est géré par le BOM Quarkus (4.3.0, Apache-2.0) et ajouté en dépendance de test.
