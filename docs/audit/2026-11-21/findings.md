# Constats de l'audit — mise à jour du 2026-11-21

Base vérifiée : `origin/main` à b142695 (PR #30). **99 constats restent à traiter sur 154.** Tri : sévérité puis axe, les constats Documentation en dernier. Chaque constat porte son statut au 20/11, ce qui a changé depuis le 17/11, le verdict de vérification, son sprint et ses dépendances. Ordre d'exécution : [sprints.md](sprints.md).

## Élevée (9)

### AUD-ARC-02 — L'IHM et le desktop joignent le jeton OIDC et l'IHM embarquée est servie sans session (chemins statiques publics), sans test qui le prouve

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée · **Axes** : Architecture, Sécurité · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-SEC-02

**Vérification du 21/11 (b142695)** — La cause du constat disparaît : avec la règle « longest path wins » de Quarkus, la page, ses ressources Vite et l'avis de licence des polices sont servis sans jeton, l'API reste protégée ; l'IHM embarquée peut donc se charger et ouvrir sa session. Accord avec le message de commit, mais aucune exécution ni aucun test ne le prouve, et le parcours de connexion par défaut de l'image reste cassé pour d'autres raisons (AUD-DEP-25, AUD-DEP-21).

*Changement depuis le 20/11* : PR #28 (e7ed7f3, « installation locale … IHM servie ») : application.properties:294 ajoute « /,/index.html,/assets/*,/licences-polices.txt » aux chemins publics (policy=permit, :295), « authentifie.paths=/* » restant en dessous (:296-297). La correction côté client (appelerApi + PKCE, desktop par bouclage) était déjà vérifiée le 20/11.

```text
argos-server/src/main/resources/application.properties:294-297 ; Dockerfile:13 « ENV VITE_ARGOS_API_URL= », :36 « COPY --from=ui /src/argos-ui/dist ./src/main/resources/META-INF/resources » ; argos-ui/vite.config.ts:8 « base: './' » et argos-ui/public/ ne contient que licences-polices.txt (tout le reste du build sort sous assets/) ; argos-ui/src/router.ts:52 « createWebHashHistory() » (les vues ne sont jamais demandées au serveur) ; argos-ui/src/session/session.ts:40-41 adresse de retour OIDC = origin + pathname, soit « / », chemin public. Règle de priorité : documentation Quarkus « Authorization of web endpoints » (« Matching is always done on the "longest path wins" basis »).
```

*Réserve* : Aucun test : grep -rn "licences-polices\|index.html\|/assets/" argos-server/src/test e2e -> aucun test du GET / sans jeton ; AuthentificationTest.java:21-27 ne teste que des routes refusées ; l'E2E sert toujours l'IHM par Vite (e2e/playwright.config.ts). La documentation Quarkus ne donne pas d'exemple « chemin exact » contre « /* » : à confirmer par « docker compose up » puis « curl -i http://localhost:8080/ » et « curl -i http://localhost:8080/assets/<fichier> ». docs/deploiement/synology-et-pc.md:80 dit encore « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place ». Les liens des alertes vers des chemins non publics (AUD-ARC-22) et le royaume par défaut (AUD-DEP-25) empêchent encore un parcours complet. Le navigateur demande /favicon.ico (aucun lien dans index.html) et reçoit 401 : cosmétique. Le test manquant de la règle publique fait l'objet d'AUD-TST-25.

*Au 20/11* : Partiel (Élevée) — La partie client est corrigée et testée ; le serveur exige toujours une session sur /* y compris sur les ressources statiques de l'IHM embarquée dans l'image Docker, qui ne peut donc pas se charger pour lancer la connexion.

**Preuve d'origine**

```text
argos-server/src/main/resources/application.properties:200 « quarkus.oidc.application-type=service », :217-218 « authentifie.paths=/* … policy=authenticated » ; argos-ui/src/api/client.ts:36-39 « fetch(`${apiBaseUrl}${path}`, { headers: { Accept: 'application/json' } … » (aucun en-tête Authorization) ; grep -rniE 'authorization|bearer|oidc|token' argos-ui/src argos-desktop/src -> aucun résultat pertinent. Lacune connue : docs/specifications/notifications-poste.md:123 « le serveur répond 401 ».
[AUD-SEC-02] argos-server/src/main/resources/application.properties:217-218 `quarkus.http.auth.permission.authentifie.paths=/*` / `policy=authenticated` (seuls /api/v1/system/info et /q/health* en permit) ; argos-ui/src/api/client.ts:36,87,173 `fetch(`${apiBaseUrl}${path}`, {...})` sans en-tête Authorization ; `grep -rniE 'authorization|bearer|token|oidc|pkce' argos-ui/src argos-desktop/src` : aucun résultat. Le Dockerfile copie l'IHM dans META-INF/resources, couverte par /* (doc Quarkus : un chemin en /* est un préfixe appliqué à toutes les requêtes HTTP).
[À 9d96406] application.properties:235 (application-type=service), :252-253 (/* authenticated) ; client.ts:36-39 inchangé.
```

**Impact** — En image Docker, même index.html et l'écran de statut reçoivent 401 : l'IHM web est inutilisable ; seul le desktop, qui sert l'IHM depuis app://argos, affiche l'écran de statut, et toutes ses autres vues reçoivent 401. Le parcours de bout en bout IHM ↔ serveur n'a jamais pu être exécuté (openspec/changes/notifier-sur-le-poste/tasks.md:28).

**Action** — Implémenter Authorization Code + PKCE (client public argos-ui, ADR-0027) dans argos-ui (ex. oidc-client-ts) et le protocole app://argos du desktop, injecter le jeton dans un wrapper fetch unique de client.ts, puis lancer un E2E navigateur contre le royaume Keycloak de test.

### AUD-QUA-01 — La création de projet, l'ajout de dépôt et l'import manuel (SBOM, manifestes) n'ont toujours aucun point d'entrée en production ; seul l'import planifié des dépôts déjà configurés est branché

- **Statut** : Partiel · **Sévérité** : Élevée · **Axes** : Qualité · **Effort** : M · **Sprint** : 7

**Vérification du 21/11 (b142695)** — Inchangé depuis le 20/11 : l'import planifié (ImportPlanifie) parcourt les projets ayant un dépôt, mais rien en production ne crée de projet ni ne rattache de dépôt ; l'import manuel SBOM/manifestes n'est appelé que par les tests.

*Changement depuis le 20/11* : Aucun changement fonctionnel sur le delta 54add78..b142695 : seuls des renommages (lot20, 5287ef9) et l'import planifié déjà présent. Aucun point d'entrée de création de projet, de rattachement de dépôt ni d'import manuel n'a été ajouté.

```text
grep -rn "ProjectDirectory\|InventoryService\|importSbom\|importManifests\|ajouterDepot" argos-server/src/main/java hors paquet inventory -> seulement projectintel/CollecteActivite.java:19,49 (lecture) ; inventory/InventoryService.java:91 importSbom, :108 importManifests, :127 refresh, :174 detacherDepot sans appelant en production ; api/ProjectResource.java et api/InventoryResource.java : aucun @POST/@PUT/@DELETE (grep -> 0) ; 143 références dans src/test.
```

*Réserve* : Désaccord maintenu avec docs/specifications/inventaire-sbom.md:33 (« l'import manuel existant reste possible ») ; README.md toujours muet sur la limite.

*Au 20/11* : Partiel (Élevée) — Partiel, mais sans effet en production : la tâche ImportPlanifie (77181e9, testée) importe chaque nuit les dépôts déjà rattachés, or aucun point d'entrée ne crée de projet ni ne rattache de dépôt — elle n'a donc rien à parcourir. Désaccord écrit avec inventaire-sbom.md:33, qui affirme que « l'import manuel existant reste possible ».

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/inventory/InventoryService.java:49 (importSbom), :64 (importManifests), :91 (refresh) ; inventory/ProjectDirectory.java:37 (create). Commande : grep -rn "InventoryService\|projects.create\|importSbom\|\.refresh(" argos-server/src/main -> aucune référence hors du paquet inventory ; références uniquement dans src/test (chain/InventoryTest.java:23, alerting/AlertesTest.java:44). api/InventoryResource.java:22-52 n'expose que deux GET (inventaire, échecs). Aucun @Scheduled, outil MCP ni ressource REST n'appelle InventoryService ou ProjectDirectory.create.
```

**Impact** — En exploitation, aucun projet ni composant ne peut être créé : la corrélation (CorrelationEngine.onInventoryReplaced), le radar de dette de version et les alertes d'impact restent sans données réelles alors que les tests passent. Environ 600 lignes (InventoryService, InventoryStore, CycloneDxParser, ManifestParser, RepositoryFiles) sont du code mort pour la production.

**Action** — Exposer l'import (POST /api/v1/espaces/{id}/projets, POST /projects/{id}/inventory pour SBOM, POST .../refresh) avec contrôle de rôle via Access, ou un déclenchement planifié de refresh() pour les projets ayant un dépôt ; ajouter un test de bout en bout REST qui crée un projet, importe une SBOM et observe un impact. À défaut, documenter explicitement la limite dans le README.

### AUD-QUA-02 — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Qualité, Dépendances · **Effort** : S · **Sprint** : 2
- **Fusionne** : AUD-DEP-02
- **Bloque** : AUD-DEP-03

**Vérification du 21/11 (b142695)** — Pipeline sans pipefail, rotation sans contrôle de validité et message de succès inconditionnel toujours présents.

*Changement depuis le 20/11* : Aucun : git log 54add78..HEAD -- scripts/sauvegarder-base.sh -> vide. Aggravation indirecte : depuis e7ed7f3 (#28) le même PostgreSQL héberge une seconde base « keycloak » que le script ne sauvegarde pas (nouveau constat AUD-DEP-24).

```text
scripts/sauvegarder-base.sh:5 « set -eu » ; :22 « $COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$FILE" » ; :26-31 rotation par « ls -1t » puis rm -f ; :33 « echo "Sauvegarde écrite : $FILE" ».
```

*Au 20/11* : Ouvert (Élevée) — Pipeline sans pipefail, rotation sans contrôle de validité et message de succès inconditionnel toujours présents.

**Preuve d'origine**

```text
scripts/sauvegarder-base.sh:5 « set -eu » (sans pipefail) ; :22 « $COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE" » ; :26-31 rotation « for OLD in $(ls -1t "$DEST"/argos-*.sql.gz) ... rm -f » sans contrôle de taille ni de validité ; :33 affiche toujours « Sauvegarde écrite ».
[AUD-DEP-02] scripts/sauvegarder-base.sh:22 `$COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE"` (set -eu seul, ligne 5) ; :26-30 rotation par nombre de fichiers sans contrôle de taille ni de validité. Commande exécutée : `sh -c 'set -eu; false | gzip > /tmp/t.gz; echo ...'` -> « sortie OK malgré échec pg_dump, taille=20 ».
```

**Impact** — Le code de retour d'un pipeline sh est celui de gzip : si postgres est arrêté ou pg_dump échoue, le script sort en 0 avec un .sql.gz vide ou tronqué. La tâche planifiée DSM ne signale rien ; après ARGOS_BACKUP_KEEP (14) échecs consécutifs, toutes les sauvegardes valides ont été supprimées par la rotation : perte de données possible lors d'une restauration.

**Action** — Écrire le dump dans un fichier temporaire puis vérifier le statut de pg_dump (ex. « pg_dump ... > tmp || exit 1 », ou bash avec « set -o pipefail »), contrôler « gzip -t » et une taille minimale, renommer seulement en cas de succès, et n'appliquer la rotation qu'après une sauvegarde valide. Sortir en code non nul pour que DSM notifie l'échec.

### AUD-QUA-20 — Le resserrement des captures d'exception du lot 23 laisse remonter les erreurs imprévues : un PURL mal encodé bloque la synchronisation des sources suivantes et l'import nocturne, et la chaîne radar fait échouer PUT /standards après commit

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Qualité, Tests, Architecture · **Effort** : M · **Sprint** : 4
- **Nouveau** le 21/11
- **Fusionne** : AUD-TST-24, AUD-ARC-23, AUD-QUA-22

**Preuve**

```text
Commits c4c8eb7, c341b49, 926d486 (lot23) : ingestion/SourceSynchronizer.java:95 « catch (IllegalStateException panne) » (au lieu de RuntimeException, qui menait à FAILED/PERMANENT) ; signal/SynchronisationPlanifiee.java:67 et inventory/ImportPlanifie.java:66,89 « catch (IllegalStateException erreur) » par source / par projet. Chaîne démontrée : catalog/PackageKey.java:47,60,61 decode() -> :126 URLDecoder.decode lève IllegalArgumentException sur « % » mal formé (essai java sur copie : PackageKey.parse("pkg:npm/%zz@1.0.0") -> java.lang.IllegalArgumentException, non IllegalStateException). Côté avis : signal/SignalRecorder.java:307 toAffected -> record (@Transactional MANDATORY, :64) -> ingestion/AdvisoryPersister.java:23 -> SourceSynchronizer.runOnce:137, qui ne capture que AdvisoryRejectedException (:132). Côté inventaire : inventory/CycloneDxParser.java:125 -> InventoryService.lireSbom (:233 ne capture que InvalidDocumentException) -> ImportPlanifie:89. Le test a été renommé pour suivre la capture : chain/ResilienceTest.java:216-223 « panneAccesDonneesTermineLeTraitementEnEchec » simule une IllegalStateException (ancien « erreurInattendueTermineLeTraitementEnEchec ») ; aucun test d'une autre RuntimeException. Même rétrécissement dans les autres frontières : alerting/MoteurAlertes.java:141 (observateur synchrone de ImpactsRecorded, appelé par CorrelationEngine.publish), alerting/EnvoiAlertes.java:121, RappelsRadar.java:114-158, impact/RadarProjets.java:110-134, releaseradar/CyclesDeVie.java:56, regintel/PreparationFiches.java:108, projectintel/CollecteActivite.java:110 ; aigateway/PasserelleIa.java:169-182 ne capture plus que IOException/InterruptedException autour du fournisseur (avant : Exception). docs/specifications/radar-standards.md:42 promet encore « Une erreur sur un produit ou un espace est journalisée sans interrompre les autres ».
[AUD-TST-24] git grep -c 'catch (RuntimeException' 54add78 -- argos-server/src/main/java -> 47 (plus 4 catch (Exception)), à b142695 -> 0 ; git diff -U0 54add78 HEAD -- argos-server/src/main/java (lignes « - … catch (RuntimeException|Exception ») -> 51 captures dans 29 fichiers (CorrelationEngine 5, RadarProjets 4, RappelsRadar 4, PasserelleIa 4…) ; git show --name-only des 23 commits fix/refactor du lot 22-23 -> seul c4c8eb7 (ingestion) modifie src/test. Exemples : mcp/GardeAppels.java (a7fbf21) ne capture plus que IllegalStateException, une erreur imprévue n'est plus journalisée en Issue.ERREUR ; grep -rn INDISPONIBLE src/test/java/io/argos/mcp -> 0 ; inventory/ImportPlanifie.java:66 et :89 catch (IllegalStateException) dans les boucles par projet, Bilan.enEchec jamais vérifié (grep -rn enEchec src/test/java -> 0) ; 9 des 29 classes ne sont nommées dans aucun test (CyclesDeVie, MoteurAlertes, CollecteOpportunites, PreparationFiches, GardeAppels, HttpSourceClient, ActivationProfilParDefaut, AlertesSansObjet, ValidationSortie).
[AUD-ARC-23] impact/RadarProjets.java:198 « catch (IllegalStateException erreur) » (54add78 : « catch (RuntimeException e) », git show 54add78:argos-server/src/main/java/io/argos/impact/RadarProjets.java:198) ; alerting/RappelsRadar.java:158 idem ; observateurs synchrones RadarProjets.java:187 et RappelsRadar.java:147 ; déclenchement après base.executer dans catalog/standard/ReferentielEspace.java:101-104 et :219. Commit 13baff7 « fix(impact): captures d'exception précises et causes journalisées ». Aucun test ne couvre StandardModifie ou AnnonceStandard en échec (grep -rln "StandardModifie\|AnnonceStandard" argos-server/src/test -> aucun résultat).
[AUD-QUA-22] Commit a7fbf21 : mcp/GardeAppels.java:117 « catch (IllegalStateException erreur) » remplace « catch (RuntimeException e) » ; seule cette branche écrit journalAppels.enregistrer(..., Issue.ERREUR) (:118-121). Aucun @WrapBusinessError dans argos-server (grep -> 0) : d'après la documentation quarkus-mcp-server 2.x (guides-error-handling), une exception non enveloppée devient une erreur de protocole JSON-RPC. Aucun test du chemin INDISPONIBLE ni de Issue.ERREUR (grep -rn "INDISPONIBLE\|Issue.ERREUR" argos-server/src/test/java/io/argos/mcp -> 0). La Javadoc :44-45 a été réécrite pour ne promettre INDISPONIBLE qu'en cas de « panne d'accès aux données ».
```

**Impact** — Un seul avis OSV ou une seule SBOM de dépôt portant un PURL mal encodé (donnée externe) lève une exception non capturée : la tâche planifiée s'interrompt, les sources (ou projets) suivants ne sont pas traités, le traitement reste RUNNING (curseur non avancé) jusqu'à l'expiration de 30 min puis bute sur le même avis à chaque échéance. La source n'atteint jamais DEGRADED (COL-07) : l'exploitation ne voit qu'une trace d'erreur du planificateur. Avant le delta, la même donnée menait à un échec PERMANENT visible et isolé. Plus généralement, tout défaut (NPE, IllegalArgumentException, DateTimeException…) dans une étape d'une boucle par espace/produit/message arrête désormais la boucle entière.

**Action** — Rétablir un filet aux frontières de boucle et d'observateur (catch RuntimeException journalisé avec cause, comptage en échec, état FAILED de la source) en gardant les captures précises à l'intérieur ; corriger PackageKey.decode pour renvoyer Optional.empty() sur IllegalArgumentException ; introduire un type dédié (ex. PanneDonnees extends IllegalStateException) si la distinction est voulue ; écrire la règle dans arc42 §8 ; ajouter des tests : avis avec PURL « %zz » (source FAILED, sources suivantes synchronisées), SBOM avec PURL mal encodé (projet refusé, projets suivants importés).

### AUD-TST-01 — Aucune intégration continue : aucun test (mvn verify, npm test, ModuleBoundariesTest) n'est exécuté automatiquement

- **Statut** : Ouvert · **Sévérité** : Élevée (était Moyenne le 17/11) · **Axes** : Tests, Dépendances · **Effort** : M · **Sprint** : 1
- **Fusionne** : AUD-DEP-04
- **Bloque** : AUD-DEP-20, AUD-DEP-22, AUD-DOC-10, AUD-TST-02, AUD-TST-12, AUD-TST-19, AUD-TST-20

**Vérification du 21/11 (b142695)** — Ouvert : toujours aucun workflow ni hook ; le volume non protégé continue de croître (602 méthodes serveur, 206 tests IHM, 54 desktop, 31 tests de scripts, 34 E2E, 29 d'accessibilité).

*Changement depuis le 20/11* : Aucun changement : aucun commit de CI dans les 110 commits du delta (PR #22 à #30). La section « Vérifications locales » du README liste désormais six commandes à lancer à la main (mvn verify, npm test et build de l'IHM, npm test du desktop, node --test des scripts et de la campagne de performance, verifier-docs, verifier-nommage). Le Dockerfile construit toujours avec -DskipTests. Les lots 20 à 23 (renommages massifs dans 157 fichiers de test, 51 captures d'exception resserrées) et la PR #28 ont été fusionnés sans aucune barrière automatique.

```text
ls -a /home/claude/work/argos/.github -> No such file or directory ; find -maxdepth 3 (.gitlab-ci.yml, Jenkinsfile, .husky, .circleci, .drone.yml, .woodpecker*, lefthook*, .pre-commit-config.yaml, azure-pipelines.yml, bitbucket-pipelines.yml) -> aucun résultat ; git config core.hooksPath -> vide, .git/hooks ne contient que des *.sample ; Dockerfile:17 « ils se lancent hors de l'image avec « mvn verify » » ; Dockerfile:37 RUN mvn -B package -DskipTests ; README.md:72-81 (« Vérifications locales », six commandes manuelles)
```

*Réserve* : La discipline du mainteneur reste le seul garde-fou ; aucune trace versionnée d'exécution des suites après les lots 20 à 23 (voir AUD-TST-20).

*Au 20/11* : Ouvert (Élevée) — Ouvert : toujours aucun workflow CI (.github absent) ni hook ; le Dockerfile contrôle désormais les licences mais construit toujours avec -DskipTests. 158 commits et 14 lots ont été intégrés sans gate automatique ; sept constats en dépendent. Sévérité relevée à Élevée (dette qui bloque la vérification des chantiers en cours).

**Preuve d'origine**

```text
ls -a /home/claude/work/argos/.github -> No such file or directory ; aucun autre fichier de pipeline. Dockerfile:15 « Les tests demandent Docker (base jetable) : ils se lancent hors de l'image avec « mvn verify » » ; Dockerfile:22 RUN mvn -B package -DskipTests
[AUD-DEP-04] Absence de .github/ (ls -la à la racine) et de tout autre fichier de CI ; .git/hooks ne contient que des *.sample (ls -la .git/hooks), core.hooksPath non défini ; .archgate/config.json `"domains": {}` et .archgate/adrs/GEN-001-example.md (« This is an example ADR ... Replace this ») ; Dockerfile:22 `RUN mvn -B package -DskipTests` ; README.md:49 `tests : mvn verify (Docker requis)` ; scripts/verifier-docs.mjs (contrôle de liens/Mermaid) lancé à la main.
[À 9d96406] Toujours aucune CI ; 363 méthodes de test serveur (décompte statique), 416 tests exécutés (mesure du mainteneur).
```

**Impact** — Les 321 méthodes de test serveur, les tests d'architecture et les 47 tests JS ne protègent que si le développeur pense à les lancer ; une image peut être construite et déployée (NAS puis service ouvert au public) avec des tests en échec ou une frontière de module violée.

**Action** — Ajouter un workflow (GitHub Actions ou équivalent) déclenché sur push/PR : mvn -B verify (runner avec Docker), npm ci && npm test && npm run typecheck dans argos-ui, npm ci && npm run check dans argos-desktop ; rendre la branche principale protégée par ce statut.

### AUD-PER-02 — L'enrichissement NVD/EPSS et la corrélation sont exécutés de façon synchrone pour chaque signal créé ou modifié, dans le fil de l'ingestion

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Performance, Qualité · **Effort** : M · **Sprint** : 8
- **Fusionne** : AUD-QUA-05

**Vérification du 21/11 (b142695)** — Désaccord avec le message de f6b93dd : l'enrichissement n'est « différé » que dans le texte du journal. Rien ne le reprend : retryMissing n'a toujours aucun appelant en production, et l'enrichissement reste synchrone, une requête HTTP par CVE et par source.

*Changement depuis le 20/11* : Commit f6b93dd « enrichissement différé sur panne précise » : seule la capture change (catch RuntimeException remplacé par catch IllegalStateException | QuarkusTransactionException, cause journalisée), Enricher.java:62-63. Aucun appelant n'a été ajouté à retryMissing et aucun rattrapage n'est planifié.

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:57-64 (onSignalChanged AFTER_SUCCESS -> requiringNew().run(enrich), catch IllegalStateException | QuarkusTransactionException, LOG « différé ») ; :67-84 (boucle CVE × source, source.lookup(cve)) ; :89 retryMissing(int) ; commande « grep -rn retryMissing argos-server/src » -> Enricher.java:36 (javadoc « retenté par retryMissing »), :89 (définition), test/java/io/argos/chain/EnrichmentTest.java:95 uniquement ; « grep -rn '@Scheduled' argos-server/src/main/java » : aucune tâche vulnintel ; NvdSource.java et application.properties:32 : ni clé d'API NVD ni limite de débit ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:94 (corrélation au même événement, même fil).
```

*Réserve* : Le resserrement de la capture a un effet de bord : une exception d'exécution autre que IllegalStateException sort désormais de l'observateur transactionnel (son traitement dépend du conteneur, non testé). Comme une resynchronisation d'un avis inchangé ne publie plus SignalChanged (AUD-PER-23), un enrichissement refusé par NVD (débit sans clé) reste perdu. Le javadoc d'Enricher.java:36 décrit toujours une reprise qui n'existe pas.

*Au 20/11* : Ouvert (Élevée) — L'enrichissement reste synchrone (observateur AFTER_SUCCESS, une requête HTTP par CVE et par source) et retryMissing n'a toujours aucun appelant en production.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:57-60 « void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) { ... QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())); » ; Enricher.java:70-76 (une requête HTTP par CVE et par source : NvdSource « ?cveId= », EpssSource « ?cve= », sans cache ni limite de débit) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:76-78 (corrélation au même événement) ; Enricher.java:88 retryMissing(int) n'a aucun appelant (grep -rn retryMissing main : seule la définition).
[AUD-QUA-05] vulnintel/Enricher.java:57-60 « QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())) » ; :67-83 enrich() boucle sur chaque CVE × chaque EnrichmentSource (NVD, EPSS, KEV, CERT-FR) avec source.lookup(cve) puis save() ; application.properties:28 « argos.sources.http-timeout=PT30S » ; aucun quarkus.transaction-manager.default-transaction-timeout (défaut 60 s). L'observateur est synchrone (TransactionPhase.AFTER_SUCCESS) sur le fil de l'ingestion, comme CorrelationEngine.java:76 -> MoteurAlertes (attente IA, envoi).
```

**Impact** — Chaque avis ingéré ajoute jusqu'à 2 appels HTTP bloquants (timeout PT30S chacun) par CVE, en plus de la corrélation et des alertes : le débit de l'ingestion est borné par la latence de NVD. Sans clé d'API, NVD limite fortement le débit : les refus deviennent des enrichissements manqués, qui ne sont jamais rattrapés, puisque retryMissing n'est appelée nulle part.

**Action** — D'abord planifier retryMissing (une dizaine de lignes, @Scheduled) pour ne plus perdre d'enrichissement ; puis remplacer l'observateur synchrone par une file traitée par lots avec une limite de débit par source (NVD : 5 requêtes / 30 s sans clé) et l'API EPSS par lot.

### AUD-DEP-01 — compose.yaml ne transmet pas au conteneur 28 des 37 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et 27 manquent dans .env.example : e-mails vers localhost:587, liens vers localhost:5173

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Dépendances · **Effort** : S · **Sprint** : 3
- **Bloque** : AUD-ARC-22, AUD-DEP-27, AUD-DOC-03

**Vérification du 21/11 (b142695)** — Inchangé : 37 variables lues en production ou sans profil, 10 clés transmises, 28 absentes du service argos, 27 absentes de .env.example (mêmes chiffres qu'à 54add78).

*Changement depuis le 20/11* : Aucun changement du bloc environment du service argos ni des variables applicatives de .env.example. La PR #28 (e7ed7f3) ajoute à .env.example:22-27 trois variables du seul profil idp-local (commentées) et à compose.yaml le service keycloak, sans transmettre aucune des 28 variables manquantes.

```text
node /home/claude/audit-maj2/build-deps/compter-env.mjs (copie du script de la MAJ précédente) sur `git archive b142695` : env_file absent ; lues_prod_ou_commun=37 ; transmises = ARGOS_DB_URL/USER/PASSWORD, ARGOS_OIDC_URL, ARGOS_OIDC_CLIENT_ID, ARGOS_ADMIN_EMAIL, ARGOS_IA_ACTIVE, ARGOS_OLLAMA_URL, ARGOS_IA_MODELE, TZ (compose.yaml:89-102) ; absentes_compose=28 dont ARGOS_SMTP_HOST/PORT/STARTTLS/TIMEOUT/USER/PASSWORD (application.properties:79-86), ARGOS_MAIL_FROM (:78), ARGOS_PUBLIC_URL (:63, défaut http://localhost:5173), ARGOS_GITLAB_TOKEN/ARGOS_GITHUB_TOKEN (:58-59), ARGOS_OIDC_AUDIENCE (:276) ; absentes_env_example=27 ; ARGOS_IA_PLAFOND_JOUR/QUOTA_ESPACE_JOUR/QUOTA_COMPTE_JOUR documentées (.env.example:44-46) mais non transmises. Même script sur 54add78 : 37/28/27. docs/deploiement/synology-et-pc.md:5 « Deux conteneurs démarrent » et :80 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » toujours présents.
```

*Réserve* : Aggravation qualitative par #28 : l'installation locale guidée (synology-et-pc.md:29-47) sert l'IHM sur ARGOS_PORT (8080 ou 8088) mais ARGOS_PUBLIC_URL non transmise garde le défaut http://localhost:5173 : les liens des e-mails pointent vers le serveur de développement Vite même sur cette installation.

*Au 20/11* : Ouvert (Élevée) — Aggravé : 28 variables lues en production absentes du service argos (au lieu de 23), 27 absentes de .env.example (au lieu de 22) ; doc toujours en retard.

**Preuve d'origine**

```text
compose.yaml:41-54 (bloc environment du service argos : seules ARGOS_DB_*, ARGOS_OIDC_*, ARGOS_ADMIN_EMAIL, ARGOS_IA_*, ARGOS_OLLAMA_URL, TZ) ; argos-server/src/main/resources/application.properties:56 `argos.alerting.lien-base=${ARGOS_PUBLIC_URL:http://localhost:5173}`, :72 `quarkus.mailer.host=${ARGOS_SMTP_HOST:localhost}`, :51-52 jetons ARGOS_GITLAB_TOKEN/ARGOS_GITHUB_TOKEN. Comparaison exécutée (grep des ${ARGOS_*} de application.properties vs compose.yaml) : 20 variables absentes de compose.yaml, dont ARGOS_SMTP_HOST/PORT/USER/PASSWORD/STARTTLS/TIMEOUT, ARGOS_MAIL_FROM, ARGOS_PUBLIC_URL, ARGOS_OIDC_AUDIENCE, ARGOS_GITHUB_TOKEN, ARGOS_GITLAB_TOKEN.
[À 9d96406] compose.yaml:41-54 ; 23 variables absentes (au lieu de 20), dont ARGOS_IA_QUOTA_COMPTE_JOUR, ARGOS_ARTISTE_COLLECTE_CRON, ARGOS_ARTISTE_RAPPELS_CRON ; ARGOS_IA_QUOTA_COMPTE_JOUR documentée dans .env.example:39 mais non transmise.
[AUD-DEP-09] docs/deploiement/synology-et-pc.md:5-10 « Deux conteneurs démarrent » (le service ollama existe, compose.yaml:65) ; :59 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » alors que compose.yaml:46 indique qu'Argos refuse de démarrer sans ARGOS_OIDC_URL ; :88-97 tableau des réglages sans ARGOS_OIDC_URL, ARGOS_ADMIN_EMAIL, SMTP ni ARGOS_PUBLIC_URL ; :140 « Non vérifié : la construction de l'image ». Comparaison exécutée : 20 variables de application.properties absentes de .env.example (dont ARGOS_SMTP_*, ARGOS_PUBLIC_URL, ARGOS_GITHUB_TOKEN).
[AUD-SEC-08] docs/deploiement/synology-et-pc.md:59 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place, Argos ne doit pas être exposé sur Internet » alors que l'OIDC est obligatoire (application.properties:203 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`) ; compose.yaml ne contient pas de service Keycloak.
[À 9d96406] application.properties:238 (au lieu de :203) ; 22 variables absentes de .env.example (au lieu de 20).
```

**Impact** — Même si l'exploitant renseigne ces valeurs dans .env (comme le suggère compose.yaml:9), elles n'atteignent pas le conteneur : aucune alerte e-mail ne part (échecs comptés puis abandon après 5 tentatives), les liens des messages sont faux, l'activité des projets privés ne se collecte pas. Seul un avertissement au démarrage existe pour ARGOS_PUBLIC_URL (MoteurAlertes.java:117-121).

**Action** — Transmettre au service argos de compose.yaml toutes les variables ${ARGOS_*} lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et les compléter dans .env.example ; ajouter un test de parité qui compare application.properties, compose.yaml et .env.example et échoue sur tout écart. La mise à jour du guide de déploiement relève d'AUD-DOC-03.

### AUD-DEP-21 — L'image Docker fige l'émetteur OIDC de l'IHM sur http://localhost:8180/realms/argos (aucun ARG au build) : connexion web impossible hors du poste ; le desktop est désormais configurable à l'emballage (apiUrl, oidcUrl) mais sans documentation de l'émetteur ni test

- **Statut** : Partiel · **Sévérité** : Élevée · **Axes** : Dépendances, Architecture, Documentation · **Effort** : S · **Sprint** : 6
- **Fusionne** : AUD-ARC-16, AUD-ARC-21, AUD-DOC-22
- **Bloque** : AUD-DOC-03, AUD-DOC-04, AUD-SEC-05

**Vérification du 21/11 (b142695)** — Le desktop devient configurable à l'emballage ; l'IHM de l'image Docker reste figée sur l'émetteur http://localhost:8180/realms/argos : l'installation locale fonctionne parce que tout est sur localhost, la connexion reste impossible hors du poste (NAS, réseau local).

*Changement depuis le 20/11* : #28 (e7ed7f3) : le desktop accepte apiUrl et oidcUrl fixés à l'emballage (argos-desktop/src/main.js:25-34, champ `argos` du package.json via -c.extraMetadata), ce qui lève la borne localhost de sa CSP et de son émetteur sans variable d'environnement. Rien côté image : Dockerfile inchangé, aucun ARG VITE_ARGOS_OIDC_ISSUER/CLIENT_ID, compose.yaml:80 `build: .` sans args ; le profil idp-local fige lui-même KC_HOSTNAME sur http://localhost (compose.yaml:54).

```text
Dockerfile:13 `ENV VITE_ARGOS_API_URL=` seule variable de build ; argos-ui/src/session/session.ts:12 EMETTEUR_PAR_DEFAUT localhost:8180, :114-115 ; rejoué : `VITE_ARGOS_API_URL= npx vite build` dans /home/claude/audit-maj2/build-deps/ui-build -> dist/assets/index-D3UthyCm.js contient `localhost:8180/realms/argos` ; compose.yaml:54 `KC_HOSTNAME: http://localhost:${ARGOS_KEYCLOAK_PORT:-8180}`, :64 port 127.0.0.1 ; docs/deploiement/synology-et-pc.md:39-42 n'emballe le desktop qu'avec VITE_ARGOS_API_URL et apiUrl (ni VITE_ARGOS_OIDC_ISSUER ni oidcUrl) ; `grep -rln 'REGLAGES_EMBALLES|extraMetadata|oidcUrl' argos-desktop/test` -> aucun test.
```

*Réserve* : Changer ARGOS_KEYCLOAK_PORT casse silencieusement la connexion web (émetteur de l'IHM figé sur 8180, compose.yaml:54 suit la variable). Aucun contrôle du bundle ni de l'image contre `localhost:8180`.

*Au 20/11* : Ouvert (Élevée) — —

**Preuve d'origine**

```text
Dockerfile:13 `ENV VITE_ARGOS_API_URL=` seule variable de build de l'IHM (ni ARG ni ENV VITE_ARGOS_OIDC_ISSUER / VITE_ARGOS_OIDC_CLIENT_ID) ; argos-ui/src/session/session.ts:12 `EMETTEUR_PAR_DEFAUT = 'http://localhost:8180/realms/argos'`, :114 `emetteur: import.meta.env.VITE_ARGOS_OIDC_ISSUER || EMETTEUR_PAR_DEFAUT` ; compose.yaml:35 `build: .` sans args. Rejoué : `VITE_ARGOS_API_URL= npx vite build` dans /home/claude/audit-maj/build-deps/ui-build -> dist/assets/index-*.js contient `localhost:8180/realms/argos`. docs/deploiement/synology-et-pc.md ne cite jamais VITE_ARGOS_OIDC_ISSUER ; :144 pour le desktop ne demande que VITE_ARGOS_API_URL alors que argos-desktop/src/main.js:31 lit ARGOS_OIDC_URL à l'exécution et connexion.js:65-79/:172 refuse une URL d'autorisation qui ne correspond pas à cet émetteur.
[AUD-ARC-16] argos-ui/src/session/session.ts:12 « EMETTEUR_PAR_DEFAUT = 'http://localhost:8180/realms/argos' », :114 « emetteur: import.meta.env.VITE_ARGOS_OIDC_ISSUER || EMETTEUR_PAR_DEFAUT » (valeur lue à la compilation par Vite) ; Dockerfile:7-14 ne définit que « ENV VITE_ARGOS_API_URL= » avant « npm run build » ; compose.yaml:45-47 et .env.example:15-18 ne configurent l'émetteur que pour le serveur (ARGOS_OIDC_URL) ; docs/deploiement/synology-et-pc.md:59 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » (obsolète) et :144 ne décrit que VITE_ARGOS_API_URL pour le desktop.
[AUD-ARC-21] argos-ui/src/session/session.ts:12 « EMETTEUR_PAR_DEFAUT = 'http://localhost:8180/realms/argos' », :114 « import.meta.env.VITE_ARGOS_OIDC_ISSUER || EMETTEUR_PAR_DEFAUT » (valeur lue au build Vite) ; Dockerfile:13 ne fixe que « ENV VITE_ARGOS_API_URL= » (aucun ARG pour l'émetteur ni le client) ; compose.yaml:54 « KC_HOSTNAME: http://localhost:${ARGOS_KEYCLOAK_PORT:-8180} »,   :64 Keycloak publié sur « 127.0.0.1:… » ; ADR-0026 (hébergement Synology) : cible NAS consultée depuis un PC ; docs/deploiement/synology-et-pc.md:50-80 (section Synology) ne traite ni Keycloak ni l'émetteur.
[AUD-DOC-22] Dockerfile:7-14 (seul ENV VITE_ARGOS_API_URL= ; aucun ARG/ENV VITE_ARGOS_OIDC_ISSUER) ; argos-ui/src/session/session.ts:12 (EMETTEUR_PAR_DEFAUT = 'http://localhost:8180/realms/argos') et :114 (import.meta.env.VITE_ARGOS_OIDC_ISSUER || EMETTEUR_PAR_DEFAUT, résolu au build) ; aucune configuration d'exécution lue par l'IHM (`grep -rn 'config.json\|window.__ARGOS' argos-ui/src` : rien) ; compose.yaml:54 (KC_HOSTNAME http://localhost:${ARGOS_KEYCLOAK_PORT}) et :64 (port Keycloak lié à 127.0.0.1) ; docs/deploiement/synology-et-pc.md:31 et .env.example:15-17 présentent ARGOS_OIDC_URL comme le seul réglage du fournisseur d'identité (« https://auth.exemple.fr/realms/argos »), :76 propose ARGOS_BIND=0.0.0.0 pour le réseau local.
```

**Impact** — Toute instance construite selon la doc (NAS ou PC hors quarkus:dev) affiche une IHM qui envoie l'utilisateur vers un Keycloak sur localhost:8180 de sa propre machine : connexion impossible, donc portail inutilisable (toutes les routes non publiques exigent la session, ADR-0033), alors que l'API, elle, est configurée par ARGOS_OIDC_URL. Le client desktop visant le NAS échoue de même faute de documentation des deux réglages cohérents. Les tests E2E ne le voient pas : ils tournent contre quarkus:dev et le Keycloak des Dev Services sur 8180.

**Action** — Ne plus figer l'émetteur au build : l'IHM lit l'émetteur et le client_id au démarrage depuis le serveur (par exemple GET /api/v1/system/info exposant quarkus.oidc.auth-server-url public), ou à défaut ajouter `ARG VITE_ARGOS_OIDC_ISSUER`/`VITE_ARGOS_OIDC_CLIENT_ID` au Dockerfile, les passer par `build.args` dans compose.yaml depuis ARGOS_OIDC_URL, et refuser le build si l'émetteur vaut localhost. Documenter les réglages du desktop (VITE_ARGOS_OIDC_ISSUER au build, ARGOS_API_URL et ARGOS_OIDC_URL à l'exécution). Ajouter un test de l'image construite (ou un contrôle du bundle) qui échoue si `localhost:8180` y figure.

### AUD-DEP-24 — La base keycloak ajoutée dans le même PostgreSQL n'est ni sauvegardée ni restaurée : après une reprise, le royaume réimporté émet de nouveaux sujets OIDC et les comptes Argos (sujet_oidc UNIQUE) deviennent orphelins

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Dépendances, Sécurité, Qualité · **Effort** : S · **Sprint** : 2
- **Nouveau** le 21/11
- **Fusionne** : AUD-SEC-31, AUD-QUA-21
- **Bloque** : AUD-DEP-03

**Preuve**

```text
compose.yaml:27-28 monte deploiement/postgres/creer-base-keycloak.sh (:4 `CREATE DATABASE keycloak`) dans le même volume argos-pgdata ; compose.yaml:49 `KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak` ; scripts/sauvegarder-base.sh:22 `pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"` (base argos seule) ; docs/deploiement/synology-et-pc.md:94 restauration dans $POSTGRES_DB seulement, :29-37 guide idp-local sans mot sur la sauvegarde ; argos-server/src/main/resources/db/migration/V2__comptes_et_acces.sql:26 `sujet_oidc text NOT NULL UNIQUE` ; ComptesService.java:69-70 et :106 (compte retrouvé par sujet_oidc) ; Context7 /keycloak/keycloak (importExport) : --import-realm recrée le royaume s'il n'existe pas.
[AUD-SEC-31] scripts/sauvegarder-base.sh:22 `pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"` (base argos seule) ; compose.yaml:27-28 et :49 (base keycloak dans le même volume PostgreSQL, profil idp-local) ; deploiement/postgres/creer-base-keycloak.sh:4 ; workspace/ComptesService.java:106 (compte retrouvé par `sujet_oidc`) ; docs/deploiement/synology-et-pc.md (section « Avec Keycloak sur le même poste ») ne mentionne pas la sauvegarde de Keycloak.
[AUD-QUA-21] Commit e7ed7f3 (#28) : deploiement/postgres/creer-base-keycloak.sh:4 « CREATE DATABASE keycloak » ; compose.yaml:28 (script d'init monté) et :48-49 « KC_DB: postgres », « KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak ». scripts/sauvegarder-base.sh:22 « pg_dump ... -d "$POSTGRES_DB" » (une seule base) ; docs/deploiement/synology-et-pc.md:84 et :94 ne décrivent que la sauvegarde/restauration de la base Argos ; les comptes Argos sont rattachés au sujet OIDC (argos-server/src/main/resources/db/migration/V2__comptes_et_acces.sql:26 « sujet_oidc text NOT NULL UNIQUE »).
```

**Impact** — Après perte du volume ou migration de poste, la restauration documentée rend une base Argos intacte mais un Keycloak vide : le royaume est réimporté avec un seul compte administrateur, au nouvel identifiant sub. Tous les autres utilisateurs ont disparu du fournisseur d'identité et, même recréés, obtiennent un nouveau compte Argos (sub différent) : appartenances aux espaces, préférences et abonnements de l'ancien compte deviennent inaccessibles. L'exploitant croit être protégé par la tâche planifiée.

**Action** — Sauvegarder aussi la base keycloak (second pg_dump -d keycloak, ou pg_dumpall), documenter sa restauration (base keycloak recréée avant le premier démarrage de Keycloak pour éviter le réimport) et l'ajouter au guide idp-local ; à défaut, exporter le royaume (kc.sh export) avec la sauvegarde. Traiter en même temps AUD-QUA-02 (pipefail).

## Moyenne (24)

### AUD-ARC-04 — Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Architecture, Qualité · **Effort** : S · **Sprint** : 5
- **Fusionne** : AUD-QUA-03
- **Dépend de** : AUD-ARC-05

**Vérification du 21/11 (b142695)** — Le protocole RLS reste écrit quatre fois ; les deux variantes de workspace ne réinitialisent toujours ni sujet_courant ni email_courant, et PreferencesRepository peut toujours lever une NullPointerException.

*Changement depuis le 20/11* : Aucun changement des quatre copies (lot23 n'a touché ni RlsSession ni les deux variantes de workspace).

```text
grep -rn "SET LOCAL ROLE argos_app" argos-server/src/main/java -> workspace/ContexteBase.java:78, impact/RlsSession.java:24, workspace/subscription/SubscriptionService.java:335, workspace/preference/PreferencesRepository.java:116 ; SubscriptionService.java:338 et PreferencesRepository.java:119 « set_config('argos.compte_courant', ?, true), set_config('argos.espace_courant', '', true) » ; PreferencesRepository.java:120 « compte.toString() » ; RlsSession utilisée par impact/ImpactQueries, RechercheVeille, RapprochementActualites, TraitementImpacts, RadarProjets.
```

*Au 20/11* : Ouvert (Moyenne) — Le protocole RLS est toujours écrit quatre fois ; les deux variantes partielles de workspace ne réinitialisent toujours ni sujet_courant ni email_courant.

**Preuve d'origine**

```text
grep -rn "SET LOCAL ROLE argos_app" -> workspace/ContexteBase.java:78, impact/RlsSession.java:24, workspace/subscription/SubscriptionService.java:269, workspace/preference/PreferencesRepository.java:116. impact/RlsSession.java:14-15 « Même protocole que … ContexteBase#appliquer, que impact ne peut pas utiliser directement (ModuleBoundariesTest) ». SubscriptionService.java:272 et PreferencesRepository.java:119 ne positionnent que compte_courant et espace_courant (pas sujet_courant ni email_courant, lus par les politiques de V2__comptes_et_acces.sql).
[AUD-QUA-03] workspace/ContexteBase.java:76-91 (4 paramètres : compte, sujet, email, espace) ; impact/RlsSession.java:22-32 (copie assumée : « que impact ne peut pas utiliser directement (ModuleBoundariesTest) ») ; workspace/subscription/SubscriptionService.java:266-275 et workspace/preference/PreferencesRepository.java:114-123 (2 paramètres seulement, sujet/email non réinitialisés ; PreferencesRepository:121 « compte.toString() » lève NullPointerException si compte est nul, là où les autres copies passent "").
```

**Impact** — Toute évolution des variables ou du rôle RLS (migration V2) doit être répercutée à quatre endroits ; les deux variantes partielles laissent sujet/email hérités de la transaction. La règle de frontière a poussé à copier du code de sécurité plutôt qu'à le partager.

**Action** — Faire de ContexteBase.appliquer le seul point d'application (voir AUD-ARC-05 : le déplacer dans un module transverse autorisé à tous), supprimer RlsSession et les deux copies de workspace, et ajouter une règle ArchUnit interdisant la chaîne « set_config('argos. » hors de cette classe (test de source).

### AUD-ARC-05 — Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où cinq règles ArchUnit d'exception au cas par cas

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Architecture · **Effort** : M · **Sprint** : 5
- **Dépend de** : AUD-ARC-14
- **Bloque** : AUD-ARC-04, AUD-DOC-02, AUD-QUA-06

**Vérification du 21/11 (b142695)** — Le noyau commun implicite reste logé dans workspace et api.security, avec cinq règles d'exception au cas par cas.

*Changement depuis le 20/11* : Aucun changement de structure : les cinq règles d'exception subsistent (seuls leurs lambdas sont renommés), aucun paquet commun n'est créé, aucun ADR du noyau commun.

```text
ModuleBoundariesTest.java:93 mcpNeLitQueLaSecuriteDeLApi, :125 signalNEmprunteQueRlsEtErreurs, :144 projectintelNEmprunteQueRlsEtErreurs, :164 catalogNEmprunteQueRlsEtErreurs (catalog.dictionnaire :177), :191 regintelNEmprunteQueRlsEtErreurs ; fichiers hors workspace important io.argos.workspace.<X> (grep -rl, sous-classes comprises) : DonneeInvalide 16, ContexteBase 16, RegleViolee 11, Introuvable 11, AccesRefuse 7, MotifRefus 6 ; « new LimiteurDebit » : api/ActiviteResource.java:60, api/artiste/ProfilArtisteResource.java:158, api/VeilleWebResource.java:92, api/reglementaire/ReglementaireResource.java:137, mcp/GardeAppels.java:75, mcp/ControleInitialisation.java:50.
```

*Au 20/11* : Ouvert (Moyenne) — Le noyau commun implicite s'étend : chaque nouveau module (regintel) a obtenu sa propre exception au lieu d'un paquet transverse.

**Preuve d'origine**

```text
Classes de workspace importées hors workspace : DonneeInvalide, RegleViolee, Introuvable, AccesRefuse, ContexteBase (catalog/standard/ReferentielEspace.java:23-26, signal/web/SourcesWeb.java:26-29, projectintel/ActiviteQueries.java:26-27, inventory/InventoryQueries.java:24, mcp, api). ModuleBoundariesTest.java contient 3 règles d'exception dédiées (signalNEmprunteQueRlsEtErreurs, projectintelNEmprunteQueRlsEtErreurs, catalogNEmprunteQueRlsEtErreurs) et une 4e pour mcp->api.security (mcpNeLitQueLaSecuriteDeLApi : Caller, CallerResolver, LimiteurDebit, mcp/GardeAppels.java:20-22). LimiteurDebit est instancié dans api/ActiviteResource.java:61, api/VeilleWebResource.java:90 et mcp/GardeAppels.java:74.
```

**Impact** — Chaque nouveau module qui a besoin de la RLS ou d'une erreur commune doit soit dépendre de tout workspace, soit obtenir une exception, soit dupliquer (cas impact, AUD-ARC-04). Le sens réel « workspace = cœur métier des espaces » se brouille avec « workspace = socle technique ».

**Action** — Extraire un paquet transverse io.argos.commun (ContexteBase, DonneeInvalide, RegleViolee, Introuvable, AccesRefuse) et io.argos.commun.securite (Caller, LimiteurDebit) autorisé pour tous les modules ; retirer les 4 règles d'exception et les entrées workspace/api devenues inutiles dans ALLOWED.

### AUD-ARC-06 — Couplage par le schéma SQL : 13 tables lues par plusieurs modules, dont trois lectures qu'aucune dépendance autorisée ne couvre (impact → compte, catalog → rôles d'appartenance, alerting → préférences)

- **Statut** : Ouvert · **Sévérité** : Moyenne (était Faible le 17/11) · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 5
- **Fusionne** : AUD-QUA-07, AUD-ARC-17
- **Bloque** : AUD-DOC-02

**Vérification du 21/11 (b142695)** — Le couplage par le schéma est inchangé, y compris les trois lectures que la table ALLOWED ou les règles d'exception ne couvrent pas.

*Changement depuis le 20/11* : Aucun changement : mêmes 13 tables lues par plusieurs modules qu'à 54add78 (diff scripté des sorties tables2.py vide) ; seule migration du delta V21__index_impacts_recents.sql (index, aucune table). DictionnaireMetier.java réécrit par lot20/lot23 (245 lignes modifiées) sans retirer ses lectures d'appartenance et de compte.

```text
python3 -I /home/claude/audit-maj2/scripts-arch/tables2.py argos-server/src/main/java/io/argos (sortie tables-b142695.json) : 13 tables multi-lecteurs ; impact/ImpactQueries.java:322 « LEFT JOIN compte co » ; catalog/dictionnaire/DictionnaireMetier.java:191 « FROM appartenance », :257, :259, :372 ; alerting/Destinataires.java:51-52 « JOIN workspace_preference_utilisateur p » ; alerting/AlertesSansObjet.java:43,45 « FROM project_impact i JOIN appartenance ap » ; regintel/FichesEnBase.java:187, regintel/ExpertiseFiches.java:249 « domaine_metier » ; inventory/InventoryQueries.java:242 « LEFT JOIN espace e » ; arc42 05-vue-blocs.md:162 affirme toujours « Les préférences ne sont lues que par api et mcp ».
```

*Au 20/11* : Ouvert (Moyenne) — Le couplage par le schéma s'étend et crée désormais des dépendances absentes de la table ALLOWED ou plus larges que la règle d'exception.

**Preuve d'origine**

```text
python3 -I /home/claude/audit-tmp/arch/tables.py -> signal : impact(3 fichiers), signal_package/signal_version : impact ; actualite : impact(2) ; project : impact(5) ; component : impact(2) ; appartenance/compte : alerting ; signal_alias : vulnintel. Ex. impact/ImpactQueries.java:56-58 « JOIN signal s … JOIN project p … JOIN component c », alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c », vulnintel/Enricher.java:91 « FROM signal_alias a », impact/RechercheVeille.java:218,238 « FROM signal », « FROM actualite ».
[AUD-QUA-07] impact/ImpactQueries.java:56 « JOIN signal s » ; impact/AnalyseComplementaireIa.java:114 (signal, signal_package) ; impact/RechercheVeille.java:218 (signal, actualite) ; impact/RapprochementActualites.java (actualite, component, project) ; alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c » et :60 (tables du module workspace) ; vulnintel/Enricher.java:89-93 (signal_alias). Commande : grep -rlE "(FROM|JOIN|INTO|UPDATE)\s+<table>" par table -> signal lue par 3 modules, project par 2, appartenance par workspace et alerting.
[AUD-ARC-17] argos-server/src/main/java/io/argos/alerting/Destinataires.java:61-66 « membresAvecProfil … JOIN workspace_preference_utilisateur p ON p.compte_id = c.id WHERE a.espace_id = ? AND ? = ANY (p.profils_actifs) » (ajouté par 3f2fdc7, appelé par RappelsReglementaires) ; argos-server/src/test/java/io/argos/architecture/PreferencesIsolationTest.java « seulsApiEtMcpLisentLesPreferences » ne contrôle que les dépendances Java vers io.argos.workspace.preference ; docs/architecture/arc42/05-vue-blocs.md §5.4.1 « Les préférences ne sont lues que par api et mcp (PreferencesIsolationTest) », contredit par §5.4.3 « aux membres au profil expert-metier actif ».
```

**Impact** — La propriété des tables n'est documentée nulle part ; renommer une colonne de signal ou d'inventory casse impact, alerting ou vulnintel sans qu'aucun test d'architecture ne le signale. Toutes ces arêtes SQL doublent une arête Java déjà autorisée (pas de dépendance cachée nouvelle), mais elles fixent le schéma comme contrat public.

**Action** — Documenter en arc42 §5.3 le module propriétaire de chaque table ; pour les lectures étrangères, passer par les services Query du propriétaire (SignalQueries, InventoryQueries, EspacesService) ou par des vues SQL nommées comme contrat ; ajouter un test qui vérifie que chaque module ne cite que ses tables ou des vues publiées.

### AUD-ARC-22 — Les liens des alertes (signaux, radar, opportunités) visent /signaux/…, /espaces/…/radar et /profils/artiste/… : routes absentes de l'IHM (historique hash) et chemins non publics (401) ; aucune vue n'est atteignable depuis un courriel

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Architecture · **Effort** : S · **Sprint** : 7
- **Nouveau** le 21/11
- **Dépend de** : AUD-DEP-01

**Preuve**

```text
alerting/MoteurAlertes.java:286 « lienBase … + "/signaux/" + signal.signalId() », alerting/RappelsRadar.java:252 « "/espaces/" + espaceId + "/radar" », alerting/RappelsOpportunites.java:153 « "/profils/artiste/opportunites/" » ; argos-ui/src/router.ts:25-40 (aucune route /signaux, /espaces/:id/radar ni /profils/…) et :52 « createWebHashHistory() » (les vues sont sous /#/…) ; application.properties:63 « argos.alerting.lien-base=${ARGOS_PUBLIC_URL:http://localhost:5173} » ; git grep ARGOS_PUBLIC_URL -> absent de compose.yaml et de .env.example ; application.properties:296-297 (/signaux/… n'est pas public : 401 sans jeton). Écart partiellement consigné : docs/specifications/alertes-digests.md:127 « la page correspondante de l'IHM reste à créer ». Tests qui figent le format : AlertesTest.java:196, :406.
```

**Impact** — Tous les liens des courriels et des alertes du poste sont morts en déploiement Docker : ils visent le serveur de développement Vite ; même avec la bonne adresse, ils tomberaient sur une réponse 401 du serveur (chemin non public, pas de repli SPA) au lieu d'une vue.

**Action** — Relayer ARGOS_PUBLIC_URL dans compose.yaml et .env.example ; construire les liens au format de l'historique hash (/#/…) vers des vues existantes (alertes/:id à défaut d'une vue signal), ou créer ces vues ; aligner AlertesTest sur ce format.

### AUD-QUA-04 — Deux modèles transactionnels coexistent (90 @Transactional et 23 QuarkusTransaction, contre six transactions manuelles setAutoCommit/commit recopiées)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 5

**Vérification du 21/11 (b142695)** — Deux modèles transactionnels coexistent toujours ; les six copies ne sont même pas identiques (PreparationFiches ne restaure pas l'autocommit).

*Changement depuis le 20/11* : Inchangé sur le fond : les six sites setAutoCommit(false) subsistent (seules les variables ont été renommées c -> connexion, lot20) ; une annotation @Transactional de plus (signal/SignalQueries.java, 7 -> 8). Aucun utilitaire commun ni règle ArchUnit.

```text
grep -rn "setAutoCommit(false)" argos-server/src/main/java -> workspace/ContexteBase.java:55, aigateway/QuotasIa.java:86, impact/RadarProjets.java:207, impact/RapprochementActualites.java:143, regintel/PreparationFiches.java:154, releaseradar/CyclesDeVie.java:101 ; grep -rn "@Transactional" | wc -l -> 90 ; grep -rn "QuarkusTransaction\." | wc -l -> 23.
```

*Réserve* : regintel/PreparationFiches.java:153-214 : setAutoCommit(false) sans « finally setAutoCommit(autoCommit) » contrairement aux cinq autres copies (QuotasIa.java:106, ContexteBase.java:65, RapprochementActualites.java:169, RadarProjets.java:233, CyclesDeVie.java:140) ; sans conséquence tant que le pool rétablit l'autocommit au retour, mais illustre la dérive des copies.

*Au 20/11* : Ouvert (Moyenne) — Les deux modèles transactionnels coexistent toujours et le modèle manuel s'est étendu au nouveau module regintel.

**Preuve d'origine**

```text
grep -rn "@Transactional" argos-server/src/main/java | wc -l -> 69 ; grep -rn "setAutoCommit" -> ContexteBase.java:55, QuotasIa.java:70, impact/RadarProjets.java:207 (dettes), impact/RapprochementActualites.java:142 (pour), releaseradar/CyclesDeVie.java:101. Même bloc recopié : « boolean autoCommit = c.getAutoCommit(); c.setAutoCommit(false); try { ... c.commit(); } catch (SQLException | RuntimeException e) { c.rollback(); throw e; } finally { c.setAutoCommit(autoCommit); } ».
[À 9d96406] aigateway/QuotasIa.java:86 (au lieu de :70) ; toujours 5 setAutoCommit(false), 69 @Transactional ; nouvelle méthode liberer() en autocommit (QuotasIa.java, portees).
```

**Impact** — Les méthodes à transaction manuelle ne fonctionnent que si aucun appelant n'ouvre de transaction JTA : avec Agroal, commit() sur une connexion enrôlée lève une SQLException. Ajouter un @Transactional sur une ressource ou un service appelant (régression facile) casse ces chemins à l'exécution seulement. Les cinq copies du même squelette augmentent le coût de maintenance.

**Action** — Choisir un modèle unique : soit tout en JTA (QuarkusTransaction.requiringNew() + ContexteRls.appliquer), soit un seul utilitaire de transaction manuelle (ContexteBase.executer généralisé et partagé) utilisé par les cinq sites ; documenter la règle dans arc42 §8 et l'outiller par un test ArchUnit (interdire setAutoCommit hors de l'utilitaire).

### AUD-QUA-06 — Le code JDBC brut est répété sans utilitaire commun : 272 prepareStatement (54 fichiers), 130 catch SQLException, 11 copies du même convertisseur de date et 7 de la troncature

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 5
- **Dépend de** : AUD-ARC-05

**Vérification du 21/11 (b142695)** — Le squelette JDBC reste recopié ; le lot superviseur n'a introduit aucune factorisation.

*Changement depuis le 20/11* : Inchangé hors renommages lot20 (ps -> requete, rs -> resultat) : 273 -> 272 prepareStatement (54 fichiers ; ConsultationAlertes et Destinataires −1 chacun, SignalQueries +1), 130 catch (SQLException) dont 111 suivis de throw new IllegalStateException, 11 copies de instant(ResultSet), mêmes utilitaires de troncature.

```text
grep -rn prepareStatement argos-server/src/main/java | wc -l -> 272 (grep -rln -> 54 fichiers) ; grep -rn "catch (SQLException" | wc -l -> 130 ; grep -A1 ... | grep -c "throw new IllegalStateException" -> 111 ; instant(ResultSet) : catalog/standard/ReferentielEspace.java:340, signal/web/SourcesWeb.java:239, signal/web/ActualitesWeb.java:163, signal/SignalQueries.java:307, signal/opportunite/SourcesProfil.java:235, impact/ImpactQueries.java:347, impact/RechercheVeille.java:313, inventory/InventoryQueries.java:553, ingestion/SyncStore.java:280, alerting/ConsultationAlertes.java:139, releaseradar/CyclesDeVie.java:163 ; troncature : couper() StockActualites.java:336, SourcesWeb.java:244, LecteurDataCulture.java:145, LecteurFlux.java:104 ; cut() SignalRecorder.java:332, InventoryStore.java:368 ; borner() LecteurJorf.java:141 ; utc() SignalRecorder.java:328, SyncStore.java:285.
```

*Au 20/11* : Ouvert (Moyenne) — Le squelette JDBC continue d'être recopié ; aucune factorisation.

**Preuve d'origine**

```text
Commandes : grep -rn prepareStatement argos-server/src/main/java | wc -l -> 184 (41 fichiers) ; grep -rn "catch (SQLException" | wc -l -> 93, dont 78 suivis de « throw new IllegalStateException » ; « private static Instant instant(ResultSet rs, int ...) » recopié dans SignalQueries.java:268, ReferentielEspace.java:336, SourcesWeb.java:239, ActualitesWeb.java:162, ImpactQueries.java:245, RechercheVeille.java:312, ConsultationAlertes.java:143, SyncStore.java:280, InventoryQueries.java:232, CyclesDeVie.java:163 ; couper() x3 (LecteurFlux:104, StockActualites:336, SourcesWeb:244) ; utc() x2 (SignalRecorder:226, SyncStore:285).
[À 9d96406] 198 prepareStatement (45 fichiers), 101 catch SQLException ; 10 copies de instant(ResultSet).
```

**Impact** — Environ 30 à 40 % des lignes des classes *Queries/*Store sont du squelette (ouverture, paramètres positionnels, mapping par index de colonne, réemballage) : mapping fragile aux changements de colonnes, gestion d'erreur hétérogène (IllegalStateException ici, QuarkusTransactionException ailleurs, ex. ResumesPeriodiques.java:120-151), revue coûteuse.

**Action** — Introduire un petit utilitaire partagé (ex. Jdbc.query(ds, sql, binder, rowMapper) / update(...) et des convertisseurs Instant/OffsetDateTime communs) ou adopter un client léger (Jdbi, déjà compatible Quarkus) ; migrer d'abord les modules cœur (signal, impact, alerting). Garder le SQL explicite, conforme au choix actuel.

### AUD-QUA-17 — Fonctionnalités atteintes seulement par les tests : suggestions IA du radar jamais produites alors que leur décision est exposée en REST, collecte immédiate d'activité, trois méthodes sans appelant

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : S · **Sprint** : 7

**Vérification du 21/11 (b142695)** — Les cinq éléments restent atteints seulement par les tests ou sans appelant.

*Changement depuis le 20/11* : Inchangé : e65ebd1 extrait collecterSiEchu dans CollecteActivite mais laisse collecterProjet sans appelant en production ; aucun producteur de suggestions radar.

```text
grep -rn "suggerer(\|collecterProjet(\|peutAller\|\.parIdentifiant(\|\.importe()" argos-server/src/main/java -> déclarations seules : catalog/standard/ReferentielEspace.java:136, projectintel/CollecteActivite.java:117, impact/EtatImpact.java:49, regintel/TextesOfficiels.java:119, inventory/InventoryService.java:58 ; api/RadarResource.java:152-159 expose toujours decider ; src/test : 13 appels à collecterProjet, suggerer dans chain/RadarStandardsTest.java ; peutAller, parIdentifiant, importe : 0 appel, ni main ni test.
```

*Au 20/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
catalog/standard/ReferentielEspace.java:136 suggerer(...) (seul producteur de suggestions, RAD-02-S2) : aucun appel dans argos-server/src/main (grep -rn "suggerer(" -> uniquement la déclaration et src/test), alors que api/RadarResource.java:152-159 expose decider() sur ces suggestions ; projectintel/CollecteActivite.java:111 collecterProjet : 0 appel en production, 13 dans src/test ; sans aucun appelant (ni main ni test) : impact/EtatImpact.java:49 peutAller, regintel/TextesOfficiels.java:116 parIdentifiant, inventory/InventoryService.java:58 ImportProjet.importe. suggerer et collecterProjet existaient déjà à 9d96406 (git grep 9d96406) : angle mort de l'audit initial.
```

**Impact** — La fonction « suggestion de l'IA sur le référentiel d'espace » est annoncée (RAD-02-S2) et sa validation par un administrateur est exposée, mais aucune suggestion ne peut naître en exploitation : écran et API vides sans que les tests le révèlent. Le code mort entretient la confusion sur ce qui est livré et alourdit la maintenance (suggerer porte en plus le LocalDate.now() sans fuseau d'AUD-QUA-10).

**Action** — Brancher suggerer sur un producteur réel (tâche IA planifiée ou outil MCP en écriture contrôlée) ou retirer la fonctionnalité et l'endpoint decider ; exposer collecterProjet (bouton « collecter maintenant ») ou le supprimer ; supprimer peutAller, parIdentifiant et importe ; ajouter au contrôle ArchUnit/CI une détection de méthodes publiques sans appelant en production.

### AUD-SEC-01 — Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : M · **Sprint** : 3

**Vérification du 21/11 (b142695)** — La résolution vérifiée et la résolution de connexion restent distinctes ; pas d'épinglage d'adresse ni de test de rebinding.

*Changement depuis le 20/11* : Renommages seuls dans GardeAdresses.java et RecuperateurWeb.java (lot20/lot21, ex. 6dc6024) ; aucune logique modifiée.

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:103 `adresses = resolveur.resoudre(hote);` ; RecuperateurWeb.java:89 et :100 (garde.verifier), :189 `client.send(...)` ; `grep -rn rebind argos-server/src/test` : rien.
```

*Réserve* : À repasser Élevée avant l'ouverture au public (cible : NAS puis service public).

*Au 20/11* : Ouvert (Moyenne) — La résolution vérifiée et la résolution de connexion restent distinctes ; aucun épinglage d'adresse ni test de rebinding.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:103 `adresses = resolveur.resoudre(hote);` puis RecuperateurWeb.java:89 `URI courante = garde.verifier(...)` et RecuperateurWeb.java:189 `client.send(requete, ...)` : HttpClient résout de nouveau le nom lui-même (aucune connexion à l'IP validée). Seul le cache DNS par défaut de la JVM (30 s, java.security : `#networkaddress.cache.ttl=-1` non défini) réduit la fenêtre. Aucun test de rebinding (grep 'rebind' dans src/test : rien).
```

**Impact** — Un compte authentifié contrôlant un DNS à TTL court peut, en déclenchant l'ajout ou la collecte d'une source (10/min), tenter de faire viser par Argos une adresse privée sur les ports 80/443 : interface DSM du NAS, réseau local, ou 169.254.169.254 en hébergement en ligne. La fenêtre est étroite : GardeAdresses et HttpClient partagent le cache DNS de la JVM (30 s), il faut que l'expiration tombe entre la vérification et la connexion. À repasser Élevée à l'ouverture au public.

**Action** — Épingler l'adresse validée : résoudre une fois, vérifier toutes les IP, puis se connecter à cette IP (socket/SSLSocket avec SNI et Host d'origine, ou client HTTP avec résolveur personnalisé type OkHttp Dns / Apache DnsResolver) ; refaire de même à chaque redirection. Ajouter un test unitaire avec un résolveur qui change de réponse entre deux appels. En hébergement en ligne, bloquer aussi en sortie (pare-feu/egress) les plages privées et 169.254.0.0/16.

### AUD-SEC-03 — Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité, Performance · **Effort** : S · **Sprint** : 9
- **Fusionne** : AUD-PER-08
- **Bloque** : AUD-PER-18

**Vérification du 21/11 (b142695)** — Toujours aucune rétention ni bornage cumulé des journaux de refus, d'appels MCP et d'appels IA.

*Changement depuis le 20/11* : Aucune purge ajoutée (renommages dans JournalRefus, JournalAppelsMcp, JournalAppelsIa). Le royaume livré par la PR #28 désactive l'inscription libre (deploiement/keycloak/argos-realm.json:4), ce qui borne le nombre de sujets en installation locale, mais pas avec un Keycloak existant.

```text
argos-server/src/main/java/io/argos/api/GestionErreurs.java:51 ; argos-server/src/main/java/io/argos/mcp/ControleInitialisation.java:61 ; `grep -rn 'DELETE FROM' argos-server/src/main/java | grep -iE 'refus|mcp_appel|ia_appel'` : rien ; seule purge planifiée : projectintel/PurgeActivite.java:32.
```

*Réserve* : Avec le profil idp-local, registrationAllowed=false limite les sujets OIDC aux comptes créés par l'administrateur ; le risque reste entier avec un royaume à inscription libre.

*Au 20/11* : Ouvert (Moyenne) — Aucune purge ni bornage des journaux ; la surface d'écriture sans compte Argos s'élargit.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/GestionErreurs.java:51 `journal.enregistrer(null, utilisateur.sujet(), ressource(requete), "INSCRIPTION_SUR_INVITATION");` ; V2__comptes_et_acces.sql:68 `CREATE TABLE refus_acces (... sujet_oidc text ...)` ; tables mcp_appel (V9) et ia_appel (V13) de même ; `grep -rniE 'DELETE FROM (refus|mcp_appel|ia_appel)|purge' src/main` : seule PurgeActivite existe (activité des projets). Le limiteur de débit est par compte Argos : un sujet sans compte n'est pas limité.
[AUD-PER-08] Seule purge planifiée : argos-server/src/main/java/io/argos/projectintel/PurgeActivite.java:32 -> ActiviteStore.java:157-161 (activite_compteur, activite_jalon). grep -rln 'DELETE FROM' main : aucune suppression sur mcp_appel, refus_acces, ia_appel, alerte, sync_run, sync_attempt, ops_event, ingestion_failure, signal_version ni actualite. Inserts : argos-server/src/main/java/io/argos/audit/JournalAppelsMcp.java:44, JournalRefus.java:37, JournalAppelsIa.java:44 ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:100 « raw_content text NOT NULL » (JSON brut de chaque version de chaque avis) et :60 (ingestion_failure.raw_content).
[À 9d96406] Inchangé pour refus_acces/mcp_appel/ia_appel ; s'y ajoute opportunite (V15__profil_artiste.sql), sans aucun DELETE FROM opportunite.
```

**Impact** — En service ouvert (inscription Keycloak libre, mode invitation côté Argos), n'importe qui peut faire croître sans limite refus_acces (déni de service par stockage sur le NAS) ; les journaux conservent indéfiniment des identifiants personnels (sujet OIDC), contraire à la minimisation RGPD.

**Action** — Purge planifiée des journaux (ex. 90 jours à 1 an, paramétrable) ; limiter les écritures de refus par sujet (fenêtre glissante, comme LimiteurDebit) ; limiter aussi en amont par IP au reverse proxy.

### AUD-SEC-05 — Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus

- **Statut** : Ouvert · **Sévérité** : Moyenne (était Faible le 20/11) · **Axes** : Sécurité · **Effort** : S · **Sprint** : 6
- **Dépend de** : AUD-DEP-21

**Vérification du 21/11 (b142695)** — Toujours aucune CSP, nosniff, Referrer-Policy ni frame-ancestors servis par Quarkus, alors que le portail web est désormais livré.

*Changement depuis le 20/11* : Aucun en-tête ajouté. La PR #28 (e7ed7f3) rend l'IHM servie par l'image utilisable : `/`, `/index.html`, `/assets/*` sont publics (application.properties:294) et la page tient des jetons porteurs en mémoire (ADR-0033).

```text
`grep -rn 'quarkus.http.header\|Content-Security-Policy\|ContainerResponseFilter' argos-server/src/main` : rien ; argos-server/src/main/resources/application.properties:294 ; Dockerfile:36 (dist de l'IHM copiée dans META-INF/resources) ; seules CSP : argos-desktop/src/ui-protocol.js:16-33 et connexion.js:28-34.
```

*Réserve* : Toujours aucun v-html (AUD-SEC-19), d'où une exploitation qui demande d'abord une injection de script.

*Au 20/11* : Ouvert (Faible) — Toujours aucune CSP, nosniff, Referrer-Policy ni frame-ancestors servis par Quarkus.

**Preuve d'origine**

```text
argos-server/src/main/resources/application.properties : `grep -n 'quarkus.http.header' application.properties` ne renvoie rien ; aucune CSP, X-Content-Type-Options, Referrer-Policy, frame-ancestors ni HSTS côté serveur (la CSP n'existe que dans le desktop, argos-desktop/src/ui-protocol.js).
```

**Impact** — L'IHM web n'a pas de défense en profondeur contre l'injection de script ni le clickjacking ; en accès direct (ARGOS_BIND=0.0.0.0) rien n'impose HTTPS. Risque borné : aucun v-html dans l'IHM (voir constat Info).

**Action** — Ajouter via quarkus.http.header.* (ou filtre) : Content-Security-Policy alignée sur celle du desktop, X-Content-Type-Options: nosniff, Referrer-Policy: no-referrer, frame-ancestors 'none' ; HSTS au reverse proxy DSM.

### AUD-SEC-22 — Demande d'évolution : tout compte peut faire interroger GitHub/GitLab avec les jetons du serveur avant tout contrôle d'expertise (oracle d'existence sur dépôts privés, 10 appels/min/compte)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : S · **Sprint** : 6

**Vérification du 21/11 (b142695)** — La forge est toujours interrogée avec le jeton du serveur avant le contrôle de visibilité et d'expertise ; réponse différenciée DEMANDE_INTROUVABLE.

*Changement depuis le 20/11* : Renommages seuls (a04f03f, 555a149) ; logique identique, malgré le découpage lot21/serveur-veille.

```text
argos-server/src/main/java/io/argos/api/reglementaire/ReglementaireResource.java:316-320 (access.authenticated() puis associerDemande) ; regintel/ExpertiseFiches.java:178 (contrôle préalable sans levée), :181-182 (`demandes.etat(demande)` puis 400 DEMANDE_INTROUVABLE), :188 puis :218-223 (visibilité et expertise) ; scm/DemandesEvolution.java:122 (jeton envoyé), :124-125 (404 -> vide) ; seul test : chain/VeilleReglementaireTest.java:326-341 (parcours de l'expert).
```

*Au 20/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/reglementaire/ReglementaireResource.java:315-320 (`access.authenticated()` seul, puis `expertise.associerDemande`) ; argos-server/src/main/java/io/argos/regintel/ExpertiseFiches.java:175-186 : `demandes.etat(d)` est appelé avant `travailler(...)` (:188, contrôle de visibilité et d'expertise à :218-223) ; le contrôle préalable :178 `fiches.detail(compte, id).filter(FicheAnalyse::peutExpertiser).ifPresent(...)` ne lève rien pour une fiche invisible, d'un non-expert ou inexistante ; argos-server/src/main/java/io/argos/scm/DemandesEvolution.java:119 (jeton ARGOS_GITHUB_TOKEN/ARGOS_GITLAB_TOKEN envoyé), :121-123 (404 forge -> vide) -> ExpertiseFiches.java:181-182 réponse 400 DEMANDE_INTROUVABLE, alors qu'une demande existante poursuit vers 404/403. Aucun test du cas non-expert (`grep -rn demande-evolution argos-server/src/test` : seulement VeilleReglementaireTest.java:326-341, parcours de l'expert).
```

**Impact** — Avec un UUID quelconque, n'importe quel compte Argos (service public : inscription sur invitation mais comptes nombreux) distingue « ticket/MR existant et lisible par le jeton du serveur » (404) de « inexistant ou inaccessible » (400) sur toute forge configurée : énumération de dépôts privés et de numéros de tickets accessibles au jeton du mainteneur. Les appels consomment aussi le quota d'API du jeton partagé avec les imports d'inventaire et la collecte d'activité (QuotaEpuise reporte ces collectes) : 10/min/compte, cumulables entre comptes.

**Action** — Dans associerDemande, exiger la fiche visible, l'expertise et l'état « impact produit » (lecture sous ContexteBase, refus journalisé) AVANT tout appel à la forge ; ne pas différencier la réponse selon le résultat de la forge pour un appelant non autorisé ; ajouter un test : non-membre et membre non expert -> 404/403 sans requête vers le StubServer de forge.

### AUD-SEC-29 — Royaume Keycloak livré pour l'installation (PR #28) sans protection contre la force brute ni politique de mot de passe, avec sslRequired « none » et des redirections à joker en http

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : S · **Sprint** : 6
- **Nouveau** le 21/11
- **Dépend de** : AUD-DEP-25

**Preuve**

```text
deploiement/keycloak/argos-realm.json:1-108 : aucune clé bruteForceProtected ni passwordPolicy (`grep -n 'bruteForce\|passwordPolicy' deploiement/keycloak/argos-realm.json` : rien), alors que la documentation Keycloak 26.7.5 (Context7) indique que la détection de force brute est « désactivée par défaut » et qu'un royaume créé n'a aucune politique de mot de passe ; :7 `"sslRequired": "none"` (option que la documentation réserve au développement) ; :40 `"http://localhost:8088/*"` et :49 `post.logout.redirect.uris` en `http://localhost:8088/*` (joker, http) ; :96-104 seul compte, l'administrateur plateforme (ARGOS_ADMIN_EMAIL), mot de passe provisoire `temporary: true`, donc mot de passe définitif choisi à la première connexion sans contrainte ; compose.yaml:43 `start --import-realm` (import au premier démarrage seulement : ces réglages persistent ensuite), :52 `KC_HTTP_ENABLED: "true"`, :54 `KC_HOSTNAME: http://localhost:...`. ADR-0027:70 prévoit justement une « revue de sécurité de la configuration du royaume (durées des jetons, MFA, politique de mot de passe) », non faite. Aucun test ne charge ce fichier (`grep -rn deploiement/keycloak` dans les sources de test : rien).
```

**Impact** — En installation locale telle que livrée, Keycloak n'écoute que sur 127.0.0.1 (compose.yaml:64) et le risque reste local. Dès que le royaume est rendu joignable pour le NAS (reverse proxy DSM, VPN) puis pour le service public, ce qui est la cible déclarée, le compte administrateur plateforme d'Argos, qui crée les espaces et invite les comptes, peut être attaqué par essais de mots de passe sans limite ni verrouillage, avec un mot de passe éventuellement trivial. sslRequired=none laisse aussi Keycloak accepter des connexions en clair depuis des adresses publiques (identifiants et cookies de session SSO en clair si le proxy est mal réglé). Les redirections à joker sont à faible risque tant qu'elles visent localhost, mais elles seraient recopiées telles quelles pour le portail. À repasser Élevée à l'ouverture au public.

**Action** — Dans argos-realm.json : `"bruteForceProtected": true` (avec failureFactor, waitIncrementSeconds, maxFailureWaitSeconds), `"passwordPolicy": "length(12) and notUsername and notEmail and passwordHistory(3)"` (au minimum), `"sslRequired": "external"` ; redirections exactes (`http://localhost:8080/`, port aligné sur ARGOS_PORT) sans joker, de même pour post.logout ; documenter dans synology-et-pc.md le passage à KC_HOSTNAME en https et `sslRequired: all` derrière le reverse proxy ; ajouter un test (ou un script de contrôle) qui lit le fichier et vérifie ces clés ; envisager l'OTP obligatoire pour l'administrateur plateforme.

### AUD-SEC-30 — Keycloak et Argos partagent le même compte PostgreSQL, superutilisateur de l'instance : la compromission de l'un donne la base de l'autre, clés de signature du royaume comprises, et la RLS d'Argos ne tient qu'au SET LOCAL ROLE

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité, Dépendances · **Effort** : M · **Sprint** : 2
- **Nouveau** le 21/11
- **Fusionne** : AUD-DEP-26
- **Bloque** : AUD-DEP-03

**Preuve**

```text
compose.yaml:19 `POSTGRES_USER: ${ARGOS_DB_USER:-argos}` (l'image officielle postgres crée ce rôle en superutilisateur), :50-51 `KC_DB_USERNAME: ${ARGOS_DB_USER:-argos}` / `KC_DB_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` (PR #28, e7ed7f3), :91-92 même compte pour Argos ; deploiement/postgres/creer-base-keycloak.sh:4 `CREATE DATABASE keycloak` exécuté par ce même rôle (propriétaire) ; V2__comptes_et_acces.sql:170-178 (« L'utilisateur de connexion (souvent superutilisateur ou propriétaire, donc exempté de RLS) bascule sur argos_app par SET LOCAL ROLE ») ; workspace/ContexteBase.java:78 et impact/RlsSession.java:24 `SET LOCAL ROLE argos_app`. La connexion d'Argos en superutilisateur existait avant le delta mais n'avait pas été relevée ; le partage avec Keycloak est nouveau.
[AUD-DEP-26] compose.yaml:19-20 POSTGRES_USER/POSTGRES_PASSWORD = ARGOS_DB_USER/ARGOS_DB_PASSWORD (superutilisateur créé par l'image postgres) ; compose.yaml:50-51 `KC_DB_USERNAME: ${ARGOS_DB_USER:-argos}`, `KC_DB_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` ; deploiement/postgres/creer-base-keycloak.sh:4 `CREATE DATABASE keycloak` sans OWNER ni rôle dédié ; V2__comptes_et_acces.sql:171-176 (Argos s'appuie sur la RLS via SET LOCAL ROLE argos_app, qu'un superutilisateur contourne).
```

**Impact** — Défense en profondeur rompue entre le fournisseur d'identité et l'application : une injection SQL ou une exécution de code dans Argos (ou dans Keycloak) ouvre la base de l'autre. Depuis Argos, un attaquant lirait les tables de Keycloak, dont les empreintes de mots de passe et les clés privées de signature du royaume (stockées en base), et pourrait alors forger des jetons valides pour n'importe quel compte, administrateur plateforme compris. Une session superutilisateur peut aussi annuler le rôle (RESET ROLE) et contourner toute la RLS, ou exécuter des commandes dans le conteneur PostgreSQL (COPY ... TO PROGRAM). Le constat suppose une première faille : aucune injection n'est connue à ce jour (AUD-SEC-11).

**Action** — Créer dans creer-base-keycloak.sh un rôle dédié `keycloak` (LOGIN, NOSUPERUSER, mot de passe propre ARGOS_KEYCLOAK_DB_PASSWORD obligatoire), propriétaire de la seule base keycloak, avec `REVOKE CONNECT ON DATABASE keycloak FROM PUBLIC` ; pour Argos, séparer le propriétaire des migrations (Flyway) d'un rôle de connexion applicatif NOSUPERUSER NOBYPASSRLS membre de argos_app ; ou, plus simple, donner à Keycloak sa propre instance PostgreSQL.

### AUD-TST-03 — Pyramide inversée : 71 % des méthodes de test serveur (427 sur 602) sont des @QuarkusTest exigeant Docker (PostgreSQL + Keycloak Dev Services)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : L · **Sprint** : 11

**Vérification du 21/11 (b142695)** — Pyramide toujours inversée, à 71 % ; les découpages du lot 22 n'ont pas été mis à profit pour des tests unitaires.

*Changement depuis le 20/11* : Ratio inchangé : 427 méthodes dans 70 classes @QuarkusTest sur 602 (70,9 %), contre 425/597 à 54add78 ; les seuls ajouts du delta sont VeilleLectureGroupeeTest (@QuarkusTest, 2) et LectureGroupeeSignauxTest (ArchUnit, 3). Les commits du lot 22 annoncent des découpages qui rendent la logique isolable (12c541f « moteur d'alertes découpé », 88d3a41 « validation de sortie découpée », 867cd28 « traduction des critères découpée par filtre », 686c47f « corrélation déterministe découpée en étapes nommées ») mais aucun n'ajoute de test unitaire : MoteurAlertes, TraductionCriteres et ValidationSortie ne sont toujours nommées dans aucun test, CorrelationEngine n'est exercé que par 6 classes @QuarkusTest.

```text
scripts-test/inventaire.sh à b142695 -> 602 méthodes, 427 dans 70 classes @QuarkusTest, 175 dans 34 classes unitaires ; grep -rlw 'MoteurAlertes|TraductionCriteres|ValidationSortie' argos-server/src/test/java -> 0 ; grep -rlw CorrelationEngine src/test/java -> 6 fichiers, tous @QuarkusTest ; git show --name-only 12c541f 88d3a41 867cd28 686c47f -> aucun fichier src/test
```

*Au 20/11* : Ouvert (Moyenne) — Pyramide toujours inversée, à 71 %.

**Preuve d'origine**

```text
grep -rl @QuarkusTest src/test/java | wc -l -> 45 classes ; décompte @Test/@ParameterizedTest : 239 dans les classes @QuarkusTest, 82 dans 18 classes unitaires (total 321). README.md:49 « tests : mvn verify (Docker requis : base jetable) »
[À 9d96406] 259 méthodes @QuarkusTest sur 363 = 71 % (48 classes).
```

**Impact** — Boucle de retour lente, impossible sans Docker ; la logique métier (corrélation, règles d'alerte, impacts) n'est vérifiable qu'à travers la pile complète, ce qui rend les échecs plus coûteux à diagnostiquer.

**Action** — Extraire en tests unitaires purs la logique déterministe déjà isolable (CorrelationEngine, MoteurAlertes, règles de RegleAlerte, RechercheVeille/TraductionCriteres, ValidationSortie) ; garder les @QuarkusTest pour les parcours, la RLS et la sécurité.

### AUD-TST-05 — Autorisations par appels impératifs (80 appels access.*() pour 96 opérations) sans test matriciel point d'entrée × rôle ; POST /ops/sources/{id}/synchronizations jamais testé

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : M · **Sprint** : 6

**Vérification du 21/11 (b142695)** — Contrôle de rôle impératif toujours sans matrice ; 5 des 11 opérations d'exploitation sans test de refus.

*Changement depuis le 20/11* : Aucun changement : 96 opérations JAX-RS dans 23 ressources, 80 appels access.*(), 26 assertions statusCode(403) et 17 statusCode(401) dans les tests, chiffres identiques à 54add78. POST /api/v1/ops/sources/{id}/synchronizations n'est toujours appelé par aucun test. Sur les 11 opérations réservées à l'exploitant, 6 ont un test de refus 403 ; ops/impacts/integrity, ops/sources/{id}/rejected, ops/sources/{id}/events, ops/sources/{id}/synchronizations et PUT profils/artiste/sources/{id}/conditions-usage n'en ont pas. La PR #28 a modifié la politique d'accès HTTP (4 chemins publics ajoutés) sans toucher aux tests d'authentification (voir AUD-TST-25).

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-63 ; grep -rn synchronizations argos-server/src/test/java -> 0 ; chain/ResilienceTest.java:151 (seul 403 sur /ops) ; api/comptes/AuthentificationTest.java:21-27 ; scripts-test2/refus.py (liste des assertions 401/403) et scripts-test/endpoints.py -> TEST-mesures.json « matrice_points_entree »
```

*Au 20/11* : Ouvert (Moyenne) — Contrôle de rôle impératif toujours sans matrice, sur une surface en hausse de 37 %.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-61 (@POST @Path("/{id}/synchronizations") … access.operator();) ; grep -rn synchronizations src/test/java -> 0 ; seul refus 403 « exploitation » vérifié : chain/ResilienceTest.java:151 Api.en("jules").when().get("/api/v1/ops/sources").then().statusCode(403); AuthentificationTest.java:21-22 ne teste le 401 que sur 6 chemins
[À 9d96406] SourceOperationsResource.java:57-63 toujours non testé ; PUT /api/v1/profils/artiste/sources/{id}/conditions-usage sans test de 403 (ProfilArtisteTest.java:306-308 ne couvre que POST/GET sources et POST collectes).
```

**Impact** — L'oubli d'un appel access.*() sur un nouveau point d'entrée (ou sur un existant non testé) donnerait une élévation de privilège qu'aucun test ne détecte ; le 401 global est assuré par quarkus.http.auth.permission (application.properties:217-218) mais pas le contrôle de rôle.

**Action** — Ajouter un test paramétré qui énumère toutes les opérations du document OpenAPI (/q/openapi) et vérifie pour chaque rôle (anonyme, membre lecteur, administrateur d'espace, exploitant, autre espace) le code attendu ; échec si une opération n'est pas déclarée dans la matrice.

### AUD-PER-01 — La synchronisation est planifiée, mais le lancement manuel reste synchrone dans la requête HTTP et OSV est toujours lu avis par avis, reprises comprises sur le fil qui synchronise

- **Statut** : Partiel · **Sévérité** : Moyenne (était Élevée le 17/11) · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Vérification du 21/11 (b142695)** — La collecte planifiée tient ; le lancement manuel reste synchrone dans la requête HTTP, OSV est toujours lu avis par avis et les attentes de reprise dorment toujours sur le fil qui synchronise.

*Changement depuis le 20/11* : Aucun changement de fond entre 54add78 et b142695 : SourceOperationsResource.java ne change que d'un nom de variable (lot21) ; SourceSynchronizer.java et SynchronisationPlanifiee.java sont renommés et leurs captures resserrées (commit c4c8eb7, lot23) ; OsvAdvisoryFeed.java : 4 lignes de mise en forme.

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-63 (@POST /{id}/synchronizations, « Lance une synchronisation de la source et attend son résultat », return operations.synchronize(id)) ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:89 (sleeper.sleep(delay)) et :127-128 (for (FeedEntry entry : feed.changesSince(position)) { RawAdvisory raw = feed.fetch(entry);) ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:59-61 (modified_id.csv complet par écosystème à chaque passage) et :89 (GET /v1/vulns/<id> par avis) ; argos-server/src/main/java/io/argos/signal/SynchronisationPlanifiee.java:47 (@Scheduled … SKIP) et :59-61 ; application.properties:31 (Maven,npm,PyPI) et :46 (cadence 1h).
```

*Réserve* : Le POST d'exploitation répond toujours après la fin du traitement. Pendant une attente de reprise, le run passe RETRYABLE et n'est plus protégé par sync_run_one_running : voir le nouveau constat AUD-PER-27 (deux fils sur le même run). Le débit d'ingestion n'est toujours pas mesuré (docs/architecture/quality/mesure-performance-api-mcp.md, « Opérations exclues »).

*Au 20/11* : Partiel (Moyenne) — La collecte régulière existe et ne passe plus par une requête HTTP ; en revanche le lancement manuel reste synchrone, OSV est toujours lu avis par avis et les reprises dorment toujours sur le fil qui synchronise.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:58-63 (« Lance une synchronisation de la source et attend son résultat » -> operations.synchronize(id)) ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:110-111 « for (FeedEntry entry : feed.changesSince(position)) { RawAdvisory raw = feed.fetch(entry); » et :77 « sleeper.sleep(delay); » ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:60 (index modified_id.csv complet par écosystème) et :89 (GET /v1/vulns/<id> par avis). Aucun @Scheduled pour l'ingestion (grep @Scheduled : seuls CollecteWeb, CollecteActivite, PurgeActivite, EnvoiAlertes, RappelsRadar, ResumesPeriodiques).
```

**Impact** — Au premier passage, chaque avis de l'index de Maven, npm et PyPI donne un appel HTTP séquentiel à api.osv.dev, suivi (même fil, observateurs AFTER_SUCCESS) de l'enrichissement, de la corrélation et des alertes. Le fil de travail HTTP reste occupé pendant des heures ; les reprises (backoff jusqu'à PT5M, 5 tentatives) dorment sur ce fil. Le reverse proxy DSM ou le client coupe la requête bien avant la fin, sans que le traitement s'arrête. Sur le NAS, cela empêche aussi de planifier une collecte régulière.

**Action** — Lancer la synchronisation en tâche de fond (@Scheduled avec concurrentExecution=SKIP, ou ManagedExecutor) et faire répondre 202 avec l'identifiant du sync_run ; amorcer OSV avec l'archive par écosystème plutôt qu'un GET par avis. Le découplage de l'enrichissement relève d'AUD-PER-02.

### AUD-PER-03 — Corrélation par projet en N+1 aggravé : une transaction et une relecture du signal par signal, puis 3 à 5 instructions SQL par composant correspondant (rattachement, état précédent, upsert, transitions)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 10

**Vérification du 21/11 (b142695)** — Le N+1 tient : une transaction, un verrou et une relecture du signal pour chaque signal, puis 3 instructions préparées (jusqu'à 5) pour chaque composant correspondant. L'import nocturne le relance pour chaque projet à dépôt.

*Changement depuis le 20/11* : Refactorisation 686c47f « corrélation déterministe découpée en étapes nommées » (+138/−85) : deterministic() délègue à parPaquet, verrouillerSignal et correlerComposant. Le message annonce « Ordre des requêtes et transaction inchangés » : c'est exact, et le N+1 est donc conservé tel quel.

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:117-126 (correlateProject : boucle correlate(signalId)) ; :142 (requiringNew par signal) ; :195 (signals.find) ; :202 (componentsMatching par signal) ; :229-233 (pg_advisory_xact_lock par signal) ; par composant, dans correlerComposant (:258-287) : :261 reattach (UPDATE, :375-399), :262 precedent (SELECT, :350-373), :263 upsert (INSERT … ON CONFLICT … RETURNING, :303-322), :272 et :295-299 (TraitementImpacts.resoluParLeSysteme puis systeme) ; :401-445 resolveStale avec :446 packagePresent par impact périmé ; :102-107 (onInventoryReplaced AFTER_SUCCESS, synchrone sur le fil d'ImportPlanifie).
```

*Réserve* : Ordre de grandeur, estimé (non mesuré) : avec le jeu K3 (20 projets, 1 000 avis, 20 000 impacts), une nuit d'import représente environ 20 000 transactions par signal et plus de 60 000 instructions par composant. De plus, l'upsert réécrit chaque impact à chaque passage (nouveau constat AUD-PER-25).

*Au 20/11* : Ouvert (Moyenne) — Le N+1 tient toujours et s'est aggravé : une transaction et une relecture du signal par signal, puis au moins 3 instructions préparées dans la boucle pour chaque composant correspondant, au lieu d'une seule.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:94-99 « List<UUID> signalIds = ...signals.signalsAffecting(...); for (UUID signalId : signalIds) { reports.add(correlate(signalId, Optional.of(projectId))); » ; :105 requiringNew par signal ; :143 signals.find ; :161 inventories.componentsMatching par signal ; :167 prepareStatement(INSERT INTO project_impact ...) dans la boucle.
```

**Impact** — Pour un inventaire npm ou Maven qui touche des centaines de paquets à avis, cela fait des centaines de transactions et au moins 4 allers-retours SQL par signal, plus une évaluation des alertes par signal (MoteurAlertes). Le tout s'exécute de façon synchrone après la validation de InventoryStore.replace (observateur AFTER_SUCCESS), donc dans le fil de l'appelant de l'import.

**Action** — Charger en une requête les signaux et leurs paquets affectés (signal + signal_package par package_key = ANY), et les composants du projet une seule fois ; préparer l'INSERT hors boucle avec addBatch ; regrouper les ImpactsRecorded par projet ; exécuter la corrélation d'inventaire en tâche de fond.

### AUD-PER-04 — Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 10

**Vérification du 21/11 (b142695)** — Toujours 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, l'attente IA et l'envoi SMTP, sur le fil de corrélation.

*Changement depuis le 20/11* : Découpage du moteur d'alertes (commits 12c541f, 9f689fc, 8cc887a, 60dc8c1, lot22) : plan() et lecture des destinataires factorisés, captures précises. La structure par destinataire est inchangée.

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:129 (surImpacts, @Observes synchrone) ; :152-155 (candidats puis boucle par destinataire) ; :206 espacesDe, :207-208 impacts.forSignals, :212-213 abonnements.activeFor (chacun dans QuarkusTransaction.requiringNew()) ; :173 et :194 alerteExiste (:352-353, requiringNew par appel) ; :332 « demande.get(delaiResumeIa…) » ; :166 envoi.envoyer(creees, maintenant) -> EnvoiAlertes.java:105 et :160-162 (diffusion synchrone).
```

*Réserve* : Seuls les numéros de ligne ont changé. Les recorrélations nocturnes sans changement ne publient pas d'ImpactsRecorded (CorrelationEngine.java:281-285), donc la charge vient des avis nouveaux ou modifiés de la synchronisation horaire.

*Au 20/11* : Ouvert (Moyenne) — Toujours 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, puis l'envoi SMTP, sur le fil de corrélation.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:141 « for (Destinataires.Destinataire d : candidats) { plan(e, d, paquets)... » ; :173 espacesDe, :175 impacts.forSignals, :180 abonnements.activeFor (chacun dans QuarkusTransaction.requiringNew()) ; :145 et :161 alerteExiste par plan et canal ; :262 « demande.get(delaiResumeIa...) » (attente IA jusqu'à PT10S) ; :168 envoi.envoyer(creees, ...) (SMTP synchrone, timeout PT10S par message).
```

**Impact** — Pour un espace de M membres, chaque signal corrélé coûte environ 4×M transactions courtes. S'y ajoutent jusqu'à 10 s d'attente de l'IA et l'envoi SMTP séquentiel, dans le fil qui ingère. En service multi-utilisateurs, avec des espaces nombreux ou peuplés, cela multiplie la durée d'ingestion et la charge du pool de connexions.

**Action** — Charger en une requête par signal les membres, leurs espaces et leurs abonnements actifs (jointure appartenance + subscription), ainsi que les alertes existantes (signal_id, compte_id) ; ne pas appeler envoi.envoyer dans l'observateur et laisser le job EnvoiAlertes (1 min) diffuser ; rendre l'observateur asynchrone (@ObservesAsync, ou file).

### AUD-PER-06 — « Ma veille » dans la cible sur le jeu K3 (p95 85 ms mesuré, contre 533,8 ms), mais la recherche des abonnements porte encore sur des expressions non indexées, n'est pas mesurée, et la réponse n'est pas paginée

- **Statut** : Partiel · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 10
- **Fusionne** : AUD-PER-21

**Vérification du 21/11 (b142695)** — Les deux causes mesurées sont corrigées et testées : relecture groupée des signaux et index V21. « Ma veille » passe sous la cible sur le jeu K3. En revanche, la recherche des abonnements porte toujours sur des expressions non indexées, la réponse n'est toujours pas paginée, et la campagne n'exerce pas ce chemin.

*Changement depuis le 20/11* : Changement optimiser-ma-veille (PR #22, lot19), archivé dans openspec/changes/archive/2026-11-20-optimiser-ma-veille/ : commit 2fbd2f1 (SignalQueries.findAll, une requête « id = ANY (?) » au lieu d'un find par avis, utilisée par VeilleProfilsResource, SignauxAbonnements et SignalQueries.search) ; commit f3b1cfc (V21, index project_impact_recents (updated_at DESC, id)) ; commit afcb8f6 (nouvelle campagne p95) ; PR #27, commit c1430eb (mesure à plusieurs espaces, aucun changement de code). Tests : VeilleLectureGroupeeTest, LectureGroupeeSignauxTest (ArchUnit).

```text
Preuves mesurées et versionnées (poste de développement win32, 16 cœurs, 48 Gio, quarkus:dev, jeu K3) : docs/architecture/quality/mesure-performance-api-mcp.md, campagne du 2026-11-20, révision 7e5e36d : « GET /api/v1/me/veille | REST | 200 | 63.3 | 85 | 108.3 | 0 | dans la cible » (533,8 ms au 2026-11-19) ; e2e/resultats/performance/rapport-2026-11-20.json (p50Ms 63.3, p95Ms 85, DANS_LA_CIBLE) ; openspec/changes/archive/2026-11-20-optimiser-ma-veille/tasks.md (phases avant correction : forWorkspaces ≈ 275 ms, lectures unitaires ≈ 220 ms ; EXPLAIN de forWorkspaces : 241 ms sans index, 7 ms avec V21 ; lecture groupée seule : p95 = 332,8 ms) et design.md:176-186 (D2, 5 espaces et 50 000 impacts : 22,9 ms avec V21 contre 255 ms sans, pour l'appelant membre du seul espace le plus ancien). Code : argos-server/src/main/java/io/argos/signal/SignalQueries.java:124-150 (findAll) ; argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:218-224 (forWorkspaces puis findAll) ; argos-server/src/main/resources/db/migration/V21__index_impacts_recents.sql:16 ; reste : argos-server/src/main/java/io/argos/signal/SignalQueries.java:248-256 (SELECT DISTINCT … LEFT JOIN signal_package … lower(p.ecosystem) = ANY … OR split_part(…) … OR lower(regexp_replace(…)) … ORDER BY s.modified_at DESC LIMIT ?) ; argos-server/src/main/java/io/argos/api/SignauxAbonnements.java:59-81 (search par abonnement, PAR_ABONNEMENT) et :117-118 (forProject sans borne) ; VeilleProfilsResource.java:137-171 (liste complète triée en mémoire) ; aucun index sur lower(ecosystem) ni sur signal.modified_at (seuls V3:122 signal_package_key et V11:42 signal_date).
```

*Réserve* : Le jeu K3 ne contient aucun abonnement : commande « grep -n -i subscription e2e/performance/donnees-performance.sql » -> aucun résultat. Les 85 ms ne couvrent donc pas avisDesAbonnementsPersonnels (VeilleProfilsResource.java:182-193), donc pas la requête non indexée de SignalQueries.search, qui parcourt signal ⋈ signal_package pour chaque abonnement PERSONAL et à chaque affichage. Sur un corpus OSV réel (npm, PyPI, Maven), ce parcours porte sur des centaines de milliers de lignes (estimation, non mesurée). Aucune mesure sur le NAS ni avec plusieurs utilisateurs concurrents d'espaces différents ; le passage à l'échelle de V21 fait l'objet d'AUD-PER-26.

*Au 20/11* : Ouvert (Moyenne) — Même requête sur expressions non indexées et même relecture signal par signal, maintenant exécutées par deux routes, dont « Ma veille », mesurée hors cible.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/signal/SignalQueries.java:209-217 « SELECT DISTINCT s.id, s.modified_at FROM signal s LEFT JOIN signal_package p ... WHERE lower(p.ecosystem) = ANY (?) OR split_part(substr(p.package_key, 5), '/', 1) = ANY (?) OR lower(regexp_replace(...)) = ANY (?) OR ... ORDER BY s.modified_at DESC LIMIT ? » ; :231 « ids.stream().map(this::find) » (1 requête par signal, jusqu'à 100) ; argos-server/src/main/java/io/argos/api/WatchResource.java:79 (signals.find par signal d'un filtre PROJECT) et :91 (search par abonnement). Index : signal_package_key (package_key) seul (V3:122), aucun index sur lower(ecosystem) ni sur signal.modified_at (signal_date porte sur coalesce(published_at, modified_at), V11:42).
[AUD-PER-21] docs/architecture/quality/mesure-performance-api-mcp.md : « GET /api/v1/me/veille | REST | 200 | 509.3 | 533.8 | 542.3 | 0 | **hors cible** », « Jeu de données | 20 projets × 100 composants, 1000 avis, 20000 impacts (un espace) », « Système | win32 10.0.19045 x64, 16 cœurs, 48 Gio », serveur quarkus:dev, révision 7c5b9d7 (aucune modification d'argos-server ni d'argos-ui entre 7c5b9d7 et 54add78 : « git diff --stat 7c5b9d7 HEAD -- argos-server argos-ui » vide) ; e2e/resultats/performance/rapport-2026-11-19.json (même valeur, verdict HORS_CIBLE) ; code : argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:139-170 (liste complète triée en mémoire, sans page), :212-213 (impacts.forWorkspaces, LIMITE_IMPACTS = 200), :220 (signaux.find par signal), :187 (SignauxAbonnements, requête non indexée d'AUD-PER-06) ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:244-245 (« WHERE i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.updated_at DESC, i.id LIMIT ? » ; index disponibles : project_impact_workspace (workspace_id), V5__impacts_projet.sql:23, aucun sur (workspace_id, updated_at)) ; décision K2 (openspec/changes/automatiser-chaine-veille/proposal.md:136 et :166, P-18 : « consigné sans correction […] son optimisation relève d'un changement ultérieur »).
```

**Impact** — Chaque GET /api/v1/me/watch coûte, par abonnement, un parcours complet de signal ⋈ signal_package (les OR d'expressions empêchent l'usage d'un index), un tri, puis jusqu'à 100 lectures unitaires. Le coût croît avec la taille du corpus OSV et le nombre d'abonnements, sans pagination côté API.

**Action** — Faire porter ce fil par RechercheVeille (déjà paginée et indexée) ou ajouter des colonnes normalisées indexées (ecosystem_lower, package_name) ; remplacer find() en boucle par une lecture « WHERE id = ANY (?) » ; paginer la réponse.

### AUD-PER-18 — Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance, Qualité · **Effort** : S · **Sprint** : 9
- **Dépend de** : AUD-SEC-03

**Vérification du 21/11 (b142695)** — Les opportunités sans date limite sont toujours relues et renvoyées sans LIMIT, sans pagination ni purge.

*Changement depuis le 20/11* : Renommages dans CollecteOpportunites, Opportunites et RappelsOpportunites (lot21, lot22).

```text
argos-server/src/main/java/io/argos/signal/opportunite/CollecteOpportunites.java:165-171 (élément de flux enregistré avec dateLimite null) ; argos-server/src/main/java/io/argos/signal/opportunite/Opportunites.java:42-43 (« AND (o.date_limite IS NULL OR o.date_limite >= ?) ORDER BY … », sans LIMIT) ; commande « grep -rn 'DELETE FROM opportunite' argos-server/src/main » -> aucun résultat ; argos-server/src/main/java/io/argos/api/artiste/ProfilArtisteResource.java:210-222 ; argos-server/src/main/java/io/argos/alerting/RappelsOpportunites.java:91 et :129.
```

*Au 20/11* : Ouvert (Moyenne) — Les opportunités sans date limite sont toujours relues et renvoyées sans LIMIT, sans pagination et sans purge.

**Preuve d'origine**

```text
signal/opportunite/CollecteOpportunites.java:171-172 (élément de flux enregistré avec dateLimite null) ; signal/opportunite/Opportunites.java:42-43 « AND (o.date_limite IS NULL OR o.date_limite >= ?) ORDER BY … » sans LIMIT ; grep 'DELETE FROM opportunite' src/main -> aucun résultat ; api/artiste/ProfilArtisteResource.java:218 (liste complète renvoyée) ; alerting/RappelsOpportunites.java:93,110 (relecture complète pour chaque suiveur).
```

**Impact** — Tout élément de flux jamais collecté reste une opportunité « ouverte » pour toujours. GET /opportunites et la tâche quotidienne de rappels relisent et renvoient toute la table pour chaque utilisateur : le fil se remplit d'éléments périmés et la charge croît linéairement avec l'historique et le nombre de suiveurs (N+1 par suiveur).

**Action** — Donner aux éléments de flux une échéance (date de publication + durée configurée) ou les exclure au-delà d'un âge maximal ; paginer GET /opportunites (LIMIT/curseur) ; purge planifiée des opportunités échues, à intégrer à la rétention de SEC-03 ; calculer les rappels en une requête qui joint suivi_profil et opportunite.

### AUD-PER-25 — La corrélation réécrit chaque impact à chaque passage (updated_at = now() sans condition) : chaque nuit, mises à jour non HOT dans les 8 index de project_impact, et ordre « récents » de Ma veille faussé

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : S · **Sprint** : 9
- **Nouveau** le 21/11

**Preuve**

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:303-315 (« ON CONFLICT (signal_id, project_id, component_id) DO UPDATE SET signal_version_id = …, provenance = excluded.provenance, component_id = excluded.component_id, updated_at = now() RETURNING id, (xmax = 0), state », sans clause WHERE), appelé pour chaque composant correspondant (:263), y compris les impacts RESOLU et ECARTE ; la détection de changement (:262-264, aChange) ne sert qu'à la publication, pas à l'écriture ; argos-server/src/main/resources/db/migration/V21__index_impacts_recents.sql:16 (index sur updated_at, donc pas de mise à jour HOT dès que updated_at change) ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:41-43 (déclencheur BEFORE INSERT OR UPDATE OF component_id, une lecture de component par ligne, déclenchée parce que component_id figure dans le SET) ; index de project_impact : PK et triplet_uk (V5:6, :20), project_impact_signal et project_impact_workspace (V5:22-23), project_impact_project (V11:49), project_impact_projet_etat et project_impact_signal_etat (V20:55-56), project_impact_recents (V21:16) ; déclenchement chaque nuit : argos-server/src/main/java/io/argos/inventory/ImportPlanifie.java:52-56 -> InventoryStore.java:84 -> CorrelationEngine.java:102-126 (AUD-PER-19) ; lecture qui s'appuie sur cet ordre : argos-server/src/main/java/io/argos/impact/ImpactQueries.java:244-245 (ORDER BY i.updated_at DESC, i.id LIMIT 200) et argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:218-219.
```

**Impact** — Sur le NAS, chaque nuit, chaque impact de chaque projet à dépôt est réécrit : une nouvelle version de ligne, 8 entrées d'index, une exécution du déclencheur et une ligne de journal WAL. Avec le jeu K3, cela fait 20 000 réécritures par nuit (estimation déduite du code, non mesurée), donc un gonflement de la table et des index, et un travail d'autovacuum qui croît avec le nombre d'impacts, résolus compris. Côté fonction, après l'import de 03:00, les impacts sont rangés par ordre de recorrélation, non par nouveauté. Pour un espace qui a plus de 200 impacts actifs, « Ma veille » et l'outil MCP get_security_alerts retiennent donc un sous-ensemble arbitraire, et un impact apparu la veille peut sortir de la fenêtre des 200.

**Action** — N'écrire que si une valeur change : ajouter à l'upsert « WHERE (project_impact.signal_version_id, level, method, transitive, inventory_origin, inventory_date, component_id) IS DISTINCT FROM (excluded.…) » et ne remettre updated_at à now() qu'à cette condition (RETURNING ne renvoie alors rien pour une ligne inchangée : relire l'identifiant dans ce cas). Retirer component_id du SET quand il ne change pas. Mesurer ensuite n_tup_hot_upd / n_tup_upd (pg_stat_user_tables) après deux nuits d'import. Signaler aux axes Qualité et Architecture l'effet sur l'ordre de « Ma veille ».

### AUD-DEP-03 — Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : S · **Sprint** : 2
- **Dépend de** : AUD-QUA-02, AUD-DEP-24, AUD-SEC-30

**Vérification du 21/11 (b142695)** — La restauration reste un psql sans ON_ERROR_STOP sur un dump texte sans --clean, non testée ; elle ignore en plus la base keycloak introduite par #28 (voir AUD-DEP-24).

*Changement depuis le 20/11* : Aucun : scripts/sauvegarder-base.sh et la commande de restauration sont inchangés ; la section Restauration s'est seulement décalée (synology-et-pc.md:91-96).

```text
docs/deploiement/synology-et-pc.md:94 `gunzip -c … | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`, :96 « À tester au moins une fois » ; scripts/sauvegarder-base.sh:22 `pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner` ; application.properties:23 migrate-at-start=true ; synology-et-pc.md:207 « Non vérifié : le démarrage réel sur le NAS ».
```

*Réserve* : Le pipefail manquant de la sauvegarde relève d'AUD-QUA-02 (toujours présent : sauvegarder-base.sh:5 `set -eu`, :22 pipeline vers gzip, :26-31 rotation, :33 message de succès inconditionnel).

*Au 20/11* : Ouvert (Moyenne) — La procédure de restauration reste un psql sans ON_ERROR_STOP sur un dump texte sans --clean, non testée.

**Preuve d'origine**

```text
docs/deploiement/synology-et-pc.md:72-76 `gunzip -c ... | docker compose exec -T postgres sh -c 'psql -U ... -d ...'` puis « À tester au moins une fois après la mise en service » ; scripts/sauvegarder-base.sh:22 `pg_dump ... --no-owner` (format texte, sans --clean) ; application.properties:23 `quarkus.flyway.migrate-at-start=true`.
```

**Impact** — Sur une base où Argos a déjà démarré (Flyway a créé le schéma), la restauration produit des erreurs « already exists » / clés dupliquées que psql ignore : restauration partielle silencieuse. Aucun exercice de restauration n'a été réalisé (doc:76, doc:138-140).

**Action** — Documenter la séquence complète : arrêter le service argos, recréer la base (dropdb/createdb ou pg_dump --clean --if-exists), restaurer avec `psql -v ON_ERROR_STOP=1` ou `pg_restore` (format -Fc), puis redémarrer ; ajouter un test de restauration (script ou test Testcontainers) exécuté périodiquement.

### AUD-DEP-07 — Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : M · **Sprint** : 4

**Vérification du 21/11 (b142695)** — Toujours ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier.

*Changement depuis le 20/11* : Aucun changement d'observabilité dans le delta (pom.xml non modifié entre 54add78 et b142695).

```text
argos-server/pom.xml:61 seule quarkus-smallrye-health, `grep -nE 'logging-json|micrometer|opentelemetry' pom.xml` -> aucun résultat ; `grep -n quarkus.log application.properties` -> aucun ; `grep -rlE '@Readiness|@Liveness|HealthCheck' src/main/java` -> aucun ; aucun MDC ; 91 appels LOG.* texte (85 à 54add78).
```

*Au 20/11* : Ouvert (Moyenne) — Toujours ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier.

**Preuve d'origine**

```text
argos-server/pom.xml:43-151 : seule quarkus-smallrye-health (l.53-56) ; pas de quarkus-logging-json, quarkus-micrometer ni quarkus-opentelemetry ; application.properties : aucune clé quarkus.log.* ; grep -rlE '@Readiness|@Liveness|HealthCheck' argos-server/src/main/java -> aucun résultat ; journaux texte via LOG.*f (27 errorf, 16 infof, 11 warnf, 1 debugf), aucun MDC.
```

**Impact** — En exploitation publique, impossible de suivre les taux d'erreur, la latence, l'état DEGRADED des sources, la file d'envoi des e-mails ou la disponibilité d'Ollama autrement qu'en lisant les journaux Docker ; /q/health ne reflète que la base (contrôle par défaut de la datasource).

**Action** — Ajouter quarkus-logging-json (profil prod), quarkus-micrometer-registry-prometheus (endpoint protégé), des checks @Readiness pour ingestion/envoi e-mail/IA (dégradé non bloquant), et un identifiant de corrélation en MDC ; OpenTelemetry quand un collecteur existe.

### AUD-DEP-25 — Le royaume Keycloak importé n'autorise que http://localhost:8088 en redirection et en origine, alors qu'Argos est publié par défaut sur le port 8080 : connexion refusée avec la configuration par défaut

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Dépendances, Architecture, Documentation · **Effort** : S · **Sprint** : 6
- **Nouveau** le 21/11
- **Fusionne** : AUD-ARC-20, AUD-DOC-21
- **Bloque** : AUD-DOC-03, AUD-SEC-29

**Preuve**

```text
deploiement/keycloak/argos-realm.json:39-42 redirectUris `http://localhost:8088/*` et la boucle locale du desktop, :43-46 webOrigins `http://localhost:8088`, :49 post.logout.redirect.uris 8088 ; compose.yaml:106 `${ARGOS_PORT:-8080}` ; .env.example:13 `ARGOS_PORT=8080` ; docs/deploiement/synology-et-pc.md:25 « Argos répond sur http://localhost:8080 », :37 « Les adresses de retour de argos-ui visent http://localhost:8088 : avec un autre ARGOS_PORT, adaptez-les dans la console » ; argos-ui/src/session/session.ts:40-46 (redirect_uri = origine de la page servie).
[AUD-ARC-20] deploiement/keycloak/argos-realm.json:39-41 « "redirectUris": ["http://localhost:8088/*", "http://127.0.0.1/argos/retour"] », :43-45 webOrigins « http://localhost:8088 », :49 post.logout.redirect.uris « http://localhost:8088/* » ; .env.example:13 « ARGOS_PORT=8080 » ; compose.yaml:106 « ${ARGOS_BIND:-127.0.0.1}:${ARGOS_PORT:-8080}:8080 » ; argos-ui/src/session/session.ts:40-41 (adresse de retour = origine de la page). docs/deploiement/synology-et-pc.md:37 « Les adresses de retour de argos-ui visent http://localhost:8088 : avec un autre ARGOS_PORT, adaptez-les » laisse croire que 8088 est la valeur par défaut. Ajouté par e7ed7f3 (PR #28).
[AUD-DOC-21] deploiement/keycloak/argos-realm.json:39-49 (client argos-ui : redirectUris « http://localhost:8088/* », webOrigins « http://localhost:8088 », post.logout.redirect.uris idem) ; .env.example:13 (ARGOS_PORT=8080) ; compose.yaml:106 (« ${ARGOS_PORT:-8080}:8080 ») ; docs/deploiement/synology-et-pc.md:37 (« Les adresses de retour de argos-ui visent http://localhost:8088 : avec un autre ARGOS_PORT, adaptez-les dans la console Keycloak ») : le guide présente 8088 comme la norme sans dire de régler ARGOS_PORT=8088, et :25 annonce Argos sur http://localhost:8080 ; argos-ui/src/session/gestionnaire.ts:46 (portail web : « retour sur la même adresse »). Introduit par e7ed7f3 (#28).
```

**Impact** — En suivant le guide avec `cp .env.example .env`, le portail servi sur http://localhost:8080 envoie redirect_uri=http://localhost:8080/ que Keycloak refuse (« Invalid parameter: redirect_uri ») ; l'échange de jeton depuis l'origine 8080 est aussi bloqué par CORS. Le réglage local du mainteneur (8088) a été figé dans le royaume, et le royaume n'étant importé qu'une fois, la correction se fait à la main dans la console.

**Action** — Aligner le royaume sur le défaut (8080) ou, mieux, paramétrer redirectUris/webOrigins par espace réservé (`${ARGOS_PUBLIC_URL}/*`, résolu par Keycloak à l'import) en transmettant la variable au conteneur keycloak ; ajouter une recette ou un test d'intégration du profil idp-local.

## Faible (55)

### AUD-ARC-07 — La table ALLOWED autorise 7 dépendances sur 57 que le code n'utilise pas et ne contraint pas un paquet io.argos non déclaré

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture, Tests · **Effort** : S · **Sprint** : 1
- **Fusionne** : AUD-TST-11

**Vérification du 21/11 (b142695)** — La table autorise toujours 7 dépendances inutilisées sur 57 et ne contraint toujours pas un paquet io.argos de premier niveau absent de la table.

*Changement depuis le 20/11* : Aucun changement de la table ni de la génération des règles.

```text
compare.py -> « autorisées inutilisées: catalog->audit, impact->vulnintel, mcp->projectintel, regintel->signal, releaseradar->signal, vulnintel->catalog, workspace->audit » ; ModuleBoundariesTest.java:73-82 (règles générées pour ALLOWED.keySet(), interdits calculés depuis les seules clés, allowEmptyShould(true) :82). Les 16 paquets de premier niveau actuels figurent tous dans la table, et aucune classe ne réside directement dans io.argos.
```

*Au 20/11* : Ouvert (Faible) — La table reste plus large que le code et ne contraint toujours pas un paquet io.argos non déclaré.

**Preuve d'origine**

```text
compare.py -> « autorisées inutilisées: api->regintel, catalog->audit, impact->vulnintel, mcp->projectintel, regintel->{aigateway,catalog,signal}, releaseradar->signal, vulnintel->catalog, workspace->audit » (10 sur 54). ModuleBoundariesTest.java:71-73 : la liste des paquets interdits est calculée à partir de ALLOWED.keySet() ; un nouveau paquet io.argos.x absent de la table n'est ni contraint ni protégé.
[AUD-TST-11] argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java:31-57 (table ALLOWED, 16 entrées) et :69-80 (règle générée uniquement pour ALLOWED.keySet(), interdits calculés à partir des seules clés, allowEmptyShould(true))
```

**Impact** — Des dépendances peuvent apparaître sans revue (déjà autorisées) ; un module ajouté sans entrée dans la table échappe entièrement au contrôle.

**Action** — Retirer les 10 autorisations inutilisées (les rajouter le jour où le code en a besoin) ; ajouter une règle « toute classe de io.argos réside dans un paquet de premier niveau listé dans ALLOWED ».

### AUD-ARC-09 — Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 8

**Vérification du 21/11 (b142695)** — La chaîne StandardModifie → RadarProjets → AnnonceStandard → RappelsRadar reste synchrone dans la requête HTTP ; elle n'avale plus que les pannes d'accès aux données.

*Changement depuis le 20/11* : Les deux styles coexistent toujours sans règle écrite. Le lot23 (13baff7 « captures d'exception précises ») a remplacé « catch (RuntimeException e) » par « catch (IllegalStateException erreur) » dans l'observateur synchrone RadarProjets.surStandardModifie et dans RappelsRadar.annoncerOuJournaliser : désormais une autre RuntimeException remonte au PUT /standards après la validation de la modification (voir AUD-QUA-20).

```text
catalog/standard/ReferentielEspace.java:101-104 et :219 « bilan.changement().ifPresent(modifications::fire) » après base.executer ; impact/RadarProjets.java:187 « @Observes StandardModifie », :198 « catch (IllegalStateException erreur) » (54add78 : « catch (RuntimeException e) ») ; alerting/RappelsRadar.java:147 « @Observes AnnonceStandard », :158 ; alerting/MoteurAlertes.java:129 « @Observes ImpactsRecorded » ; transactionnels : impact/CorrelationEngine.java:94,102, vulnintel/Enricher.java:58, impact/RapprochementActualites.java:65, alerting/AlertesSansObjet.java:56 ; grep "AFTER_SUCCESS|ObservesAsync|synchrone" docs/architecture/arc42/08-concepts-transverses.md -> aucun résultat.
```

*Au 20/11* : Ouvert (Faible) — Les deux styles coexistent toujours sans règle ; la chaîne StandardModifie -> RadarProjets -> AnnonceStandard -> RappelsRadar reste synchrone dans le PUT /standards, exceptions avalées.

**Preuve d'origine**

```text
Observateurs transactionnels : impact/CorrelationEngine.java:76,84, vulnintel/Enricher.java:57, impact/RapprochementActualites.java:64 « @Observes(during = TransactionPhase.AFTER_SUCCESS) ». Observateurs synchrones : alerting/MoteurAlertes.java:124, impact/RadarProjets.java:187, alerting/RappelsRadar.java:130, api/preference/ActivationProfilParDefaut.java:34. catalog/standard/ReferentielEspace.java:104 « r.changement().ifPresent(modifications::fire); » après commit, dans le thread du PUT /standards.
```

**Impact** — Un PUT sur le référentiel déclenche synchroniquement RadarProjets puis RappelsRadar (lecture des inventaires, émission d'alertes) avant de répondre ; les exceptions sont avalées et journalisées (RadarProjets.java:196-198) : latence et échecs silencieux côté utilisateur. La règle de choix entre les deux styles n'est écrite nulle part.

**Action** — Documenter la règle en arc42 §8 (événement inter-modules = AFTER_SUCCESS ou @ObservesAsync) et l'appliquer à StandardModifie, AnnonceStandard et ImpactsRecorded.

### AUD-ARC-10 — La collecte HTTP est dispersée dans sept clients répartis sur cinq modules

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 8
- **Fusionne** : AUD-QUA-12

**Vérification du 21/11 (b142695)** — La collecte HTTP reste dispersée, sans fabrique commune ; la garde d'adresses reste propre à la veille web.

*Changement depuis le 20/11* : Aucun changement : toujours sept HttpClient.newBuilder dans cinq modules (lignes décalées par lot20/lot23).

```text
grep -rn "HttpClient.newBuilder" argos-server/src/main/java -> aigateway/ollama/FournisseurOllama.java:60, vulnintel/EnrichmentHttp.java:36, scm/ForgeHttp.java:46, ingestion/web/RecuperateurWeb.java:180, ingestion/legi/ArchivesJorf.java:79, ingestion/HttpSourceClient.java:37, releaseradar/ClientEndoflife.java:72 ; GardeAdresses utilisée par ingestion/web/RecuperateurWeb, signal/web/SourcesWeb, signal/opportunite/SourcesProfil.
```

*Au 20/11* : Ouvert (Faible) — La dispersion s'accroît ; aucune fabrique commune.

**Preuve d'origine**

```text
grep -rln "HttpClient.newBuilder" -> ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, scm/ForgeHttp.java:36, vulnintel/EnrichmentHttp.java:36, releaseradar/ClientEndoflife.java:72, aigateway/ollama/FournisseurOllama.java:59 ; tous « followRedirects(NEVER) » et connectTimeout. arc42 §5.3 (05-vue-blocs.md:119) attribue la collecte à source-ingestion ; la garde d'adresses (ingestion/web/GardeAdresses.java) n'est utilisée que par la veille web.
[AUD-QUA-12] HttpClient.newBuilder() dans vulnintel/EnrichmentHttp.java:36, scm/ForgeHttp.java:36, releaseradar/ClientEndoflife.java:72, ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, aigateway/ollama/FournisseurOllama.java:59 (chaque fois connectTimeout + Redirect.NEVER, limites de taille et gestion d'InterruptedException réécrites). scm/RepositoryActivity.java:150-162 boucle de pagination manuelle alors que pages() (:326-337) fait la même chose.
[À 9d96406] scm/ForgeHttp.java:44 (au lieu de :36) ; 05-vue-blocs.md:120 ; toujours 6 HttpClient.newBuilder ; la collecte artiste réutilise RecuperateurWeb.
```

**Impact** — Configuration homogène aujourd'hui, mais chaque évolution (proxy, User-Agent, limites de taille, garde SSRF) doit être faite six fois ; vulnintel et releaseradar sont à la fois métier et adaptateurs.

**Action** — Fournir une fabrique de clients HTTP commune (timeouts, taille maximale, User-Agent, garde d'adresses optionnelle) dans le module transverse proposé en AUD-ARC-05 ; mettre à jour §5.3.

### AUD-ARC-11 — Aucun objet-dieu (max. 557 lignes, InventoryQueries) ; impact (2 960 l.) porte aussi la recherche plein texte que le package-info attribue à signal

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 11

**Vérification du 21/11 (b142695)** — Toujours aucun objet-dieu au sens strict ; la recherche reste portée par impact alors que le package-info l'attribue à signal.

*Changement depuis le 20/11* : Découpages de méthodes du lot23 (686c47f corrélation en étapes nommées, 8303378, 4559838) sans changement de répartition entre modules ; CorrelationEngine passe de 440 à 490 lignes.

```text
find io/argos -name '*.java' | xargs wc -l | sort -n | tail -> inventory/InventoryQueries.java 557, impact/CorrelationEngine.java 490, mcp/OutilsSecurite.java 468, regintel/CollecteReglementaire.java 458, scm/RepositoryActivity.java 442 ; moyenne 108 l./fichier (29 114 l., 269 fichiers) ; impact 20 fichiers / 2 960 l., signal 22 / 2 790 ; signal/package-info.java:4 « Signaux normalisés, taxonomie, déduplication, recherche » ; impact/RechercheVeille.java:125 « rechercher », appelée par api/recherche/TraductionCriteres.java:47.
```

*Au 20/11* : Ouvert (Faible) — Toujours aucun objet-dieu au sens strict ; la responsabilité recherche reste mal attribuée et impact concentre corrélation, traitement des impacts (V20), recherche, radar, opportunités et IA.

**Preuve d'origine**

```text
find io/argos -name '*.java' | xargs wc -l | sort -n | tail -> plus gros fichier scm/RepositoryActivity.java 394 lignes, puis workspace/EspacesService.java 383, mcp/OutilsSecurite.java 383 ; moyenne 87 lignes/fichier. impact = 1 919 lignes/13 fichiers (CorrelationEngine, RadarProjets, RechercheVeille, RapprochementActualites, AnalyseComplementaireIa). signal/package-info.java : « Signaux normalisés, taxonomie, déduplication, recherche » alors que la recherche est impact/RechercheVeille.java (lit les tables signal et actualite, l.218, 238).
[À 9d96406] signal 20 fichiers / 2 550 l. > impact 16 / 2 159 l. : la partie « impact le plus dense » ne tient plus.
```

**Impact** — Taille des classes saine ; les responsabilités d'impact débordent toutefois (corrélation, recherche, radar, IA), ce qui en fait le point de convergence le plus exposé aux changements de signal et d'inventory.

**Action** — Corriger le package-info de signal (ou déplacer la recherche dans un sous-paquet dédié) et consigner en §5.3 que impact porte la recherche plein texte.

### AUD-ARC-12 — Le contrat client de l'IHM est écrit à la main et partiel (10 classes REST sur 23 sans appel), sans génération ni test de contrat ; le point d'appel HTTP est unique

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 7
- **Fusionne** : AUD-QUA-13

**Vérification du 21/11 (b142695)** — Le point d'appel unique tient ; le contrat reste manuel, sans génération ni test de contrat côté IHM, et 10 classes @Path sur 23 ne sont appelées par aucun client.

*Changement depuis le 20/11* : Aucun changement de nature : le client reste écrit à la main (lot20/lot21 renomment et déplacent des fonctions dans argos-ui/src/api, 35 fichiers de argos-ui/src modifiés).

```text
argos-ui/src/api/client.ts:81 « appelerApi », :129 et :133 alias ; grep -rlE '@Path("/api/v1' argos-server/src/main/java -> 23 classes ; sans appel IHM : espaces, invitations, me/watch, ops/sources, preferences/rubriques, profils/artiste, projects/{projectId}/activite, radar/espaces/{espaceId}, reglementaire, veille-web ; doublon de types client.ts:198 « interface AlerteVue » / contenu/alertes-poste.ts:25 « interface AlerteRecue » ; message(e) recopiée : views/AffichageView.vue:22, views/AlertesView.vue:44 (variante views/ProfilsVeilleView.vue:26) ; grep openapi argos-ui/package.json argos-ui/tests -> aucun résultat.
```

*Réserve* : Le comptage du 20/11 (« 11 racines ») retenait les racines appelées ; le décompte ci-dessus compte les classes sans appel, ImpactResource (@Path("/api/v1")) et l'inventaire étant appelés.

*Au 20/11* : Partiel (Faible) — La divergence de gestion d'erreur est corrigée ; le contrat reste écrit à la main, sans génération ni test de contrat côté IHM, et couvre 11 racines @Path sur 23.

**Preuve d'origine**

```text
argos-ui/src/api/client.ts : 8 fonctions exportées pour 18 classes @Path("/api/v1…") côté serveur ; types recopiés « schéma OpenAPI PreferencesEffectives » (l.58) sans génération ; getJson (l.32), putJson (l.83), postJson (l.169) dupliquées, seul putJson lit le corps Erreur (l.96). Chemins utilisés vérifiés présents côté serveur (api/AlertesResource.java:32,45-46,52,60).
[AUD-QUA-13] argos-ui/src/api/client.ts:32-50 getJson, :83-109 putJson, :169-188 postJson (même minuterie, même try/catch) ; seul putJson (:93-100) lit le message d'erreur du serveur. Types : client.ts:150 « interface AlerteVue » et contenu/alertes-poste.ts:22 « interface AlerteRecue » décrivent le même schéma AlerteVue ; « nature: string », « etat: string » au lieu d'unions. Fonction message(e) recopiée dans views/AffichageView.vue:27 et views/AlertesView.vue:36.
[À 9d96406] client.ts : 8 fonctions, inchangé ; 20 classes @Path (ajout de ProfilArtisteResource.java:66 et RubriquesResource.java:27).
```

**Impact** — Une évolution d'un record Java (AlerteVue, PreferencesEffectives) ne casse aucune compilation côté IHM ; dérive silencieuse du contrat dès que l'IHM s'étoffera.

**Action** — Générer les types depuis /q/openapi (openapi-typescript) en build, ou ajouter un test de contrat ; factoriser une seule fonction request() (méthode, corps, lecture d'Erreur, futur jeton OIDC).

### AUD-ARC-14 — Le socle d'architecture repose sur des ADR fondateurs restés « Proposé » (14 sur 33, dont 0001, 0013, 0014, 0016) alors que le code les applique

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Architecture, Documentation · **Effort** : S · **Sprint** : 5
- **Fusionne** : AUD-DOC-09
- **Bloque** : AUD-ARC-05, AUD-DOC-01, AUD-DOC-08

**Vérification du 21/11 (b142695)** — Inchangé depuis le 20/11 : les ADR récents sont actés, les ADR fondateurs que le code applique restent « Proposé ».

*Changement depuis le 20/11* : PR #29 (8882fb3) ne touche que le corps de 0022, 0031 et 0032 (liens d'archive), sans changer de statut.

```text
grep -m1 -i statut docs/architecture/adr/0*.md : 15 Accepté, 4 Remplacé, 14 Proposé sur 33 ; Proposé notamment 0001-monolithe-modulaire.md (cité par ModuleBoundariesTest.java:21), 0008-iam-oidc.md, 0013-correlation-deterministe.md, 0014-mcp-readonly-streamable-http.md, 0016-workspace-rbac-rls.md, 0021-double-licence.md, 0022-client-desktop-electron.md (desktop livré et emballé par PR #28) ; aucun ADR du noyau commun.
```

*Au 20/11* : Partiel (Faible) — Les ADR récents sont actés, mais les ADR fondateurs que le code applique depuis le début restent « Proposé ».

**Preuve d'origine**

```text
grep -m1 Statut docs/architecture/adr/0*.md -> 22 ADR sur 29 « Proposé », dont 0001-monolithe-modulaire.md:3 (cité en tête de ModuleBoundariesTest.java:21) et 0016-workspace-rbac-rls.md:3. 0028-collecteur-veille-web-integre.md:6 et 0029-passerelle-ia-ollama-local.md:6 remplacent 0006 et 0004 « à l'acceptation », alors que io.argos.ingestion.web et io.argos.aigateway.ollama sont livrés.
[AUD-DOC-09] docs/architecture/adr/0021-double-licence.md:3 (Statut : Proposé) et :16 (« aucune dépendance n'est encore déclarée dans le dépôt ») ; argos-server/README.md:19 et :32 (contrôle bloquant « ADR-0021 point 4 »), :59 (194 dépendances inventoriées) ; Dockerfile:24-25 (mvn -P licences au build) ; e2e/README.md:112 et docs/architecture/quality/audit-accessibilite-mises-en-page.md:15 (axe-core écarté « ADR-0021 ») ; ADR-0025:40, :50 et :62 demandent de revoir ADR-0009 et ADR-0021, sans ADR de suite. Cas particulier, apparu avec le lot licences, de la famille décrite par AUD-ARC-14.
```

**Impact** — Les décisions effectivement en production ne sont pas actées ; ADR-0004 (Claude) et ADR-0006 (FreshRSS séparé) restent formellement en vigueur et contredisent le code, ce qui alimente l'écart documentaire (AUD-DOC-02).

**Action** — Passer en « Accepté » les ADR implémentés (0001, 0013, 0014, 0016, 0027, 0028, 0029…), marquer 0004 et 0006 « Remplacé », et mettre à jour adr/README.md. Rédiger l'ADR du noyau commun (prérequis d'AUD-ARC-05).

### AUD-ARC-15 — Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 7

**Vérification du 21/11 (b142695)** — Toujours aucun client ; le lien de rappel vise un chemin qui n'existe ni comme route de l'IHM ni comme ressource publique du serveur.

*Changement depuis le 20/11* : Aucun client ajouté pour /profils/artiste ni /preferences/rubriques.

```text
grep -rn "profils/artiste\|preferences/rubriques" argos-ui/src argos-desktop/src -> aucun résultat ; alerting/RappelsOpportunites.java:153 « lienBase … + "/profils/artiste/opportunites/" + restitution.id() » ; argos-ui/src/router.ts:25-40 (aucune route d'opportunité, historique hash :52) ; docs/specifications/profil-artiste.md:133 et :139 (écarts consignés, sans le lien mort).
```

*Réserve* : Absence d'écran consignée comme écart, non arbitrée par un ADR ; le lien est traité de façon transverse dans AUD-ARC-22.

*Au 20/11* : Ouvert (Faible) — Toujours aucun client pour les points d'entrée du profil artiste ; le lien de rappel vers une page inexistante subsiste et n'est pas consigné.

**Preuve d'origine**

```text
git diff --stat f58a68c HEAD : aucun fichier sous argos-ui/ ni argos-desktop/ ; api/artiste/ProfilArtisteResource.java:157-291 (GET listes, GET/PUT suivis, GET opportunites, GET resume, sources, collectes), api/preference/RubriquesResource.java:27,49 ; argos-ui/src/api/client.ts : 8 fonctions, aucune pour /profils/artiste.
```

**Impact** — La fonction n'est utilisable que par l'API avec un jeton : comme ARC-02, aucune preuve de bout en bout. L'écart entre contrat serveur et client (ARC-12) se creuse. Le lien des rappels sans lien officiel vise une page /profils/artiste/opportunites/<id> qui n'existe pas (RappelsOpportunites.java, contenu()).

**Action** — Ajouter les vues artiste (suivis, opportunités) dans argos-ui une fois ARC-02 traité, ou consigner l'écart dans docs/specifications/profil-artiste.md (« Écarts constatés »).

### AUD-ARC-19 — L'événement AlerteEmise est publié en cinq points sans aucun observateur

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 8

**Vérification du 21/11 (b142695)** — AlerteEmise est toujours publié en cinq points sans aucun observateur.

*Changement depuis le 20/11* : Aucun observateur ajouté ; le changement notifier-sur-le-poste a été archivé (PR #29) sans l'utiliser, ce qui rend la javadoc obsolète.

```text
emises.fire(new AlerteEmise(…)) : alerting/MoteurAlertes.java:185, RappelsRadar.java:271, ResumesPeriodiques.java:119, RappelsReglementaires.java:135, RappelsOpportunites.java:144 ; grep -rn "AlerteEmise" argos-server/src | grep -i observes -> aucun résultat ; alerting/AlerteEmise.java:6-8 « Le changement notifier-sur-le-poste peut l'observer » ; openspec/changes/archive/2026-11-20-notifier-sur-le-poste/.
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
emises.fire(new AlerteEmise(…)) dans alerting/MoteurAlertes.java:159, alerting/RappelsRadar.java:244, alerting/ResumesPeriodiques.java:109, alerting/RappelsReglementaires.java:112 (nouveau), alerting/RappelsOpportunites.java:124 ; grep -rn "@Observes.*AlerteEmise" argos-server/src -> aucun résultat ; alerting/AlerteEmise.java:5-8 (javadoc) « Le changement notifier-sur-le-poste peut l'observer » alors que le poste lit les alertes par interrogation (05-vue-blocs.md:46). Déjà présent (4 émissions) à 9d96406, non relevé par l'audit initial.
```

**Impact** — Point d'extension mort recopié à chaque nouveau type de rappel ; il laisse croire qu'une diffusion en temps réel existe et coûte une résolution d'observateurs CDI à chaque alerte.

**Action** — Supprimer l'événement et ses cinq émissions, ou le brancher (diffusion SSE au desktop) et le documenter dans alertes-digests.md ; à défaut, consigner qu'il est réservé.

### AUD-QUA-08 — Le typage entre modules repose encore sur des chaînes : niveau d'impact en String alors que l'énumération existe, clé composite découpée par split(":"), statuts d'import et de corrélation en chaînes avec défaut silencieux

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 5

**Vérification du 21/11 (b142695)** — Une des quatre preuves est corrigée (String[] -> record) ; niveau, clé composite et statuts restent typés par chaînes, dont le switch à défaut silencieux de l'import planifié.

*Changement depuis le 20/11* : Commit 12c541f (refactor(alerting): moteur d'alertes découpé) : le couple String[] {texte, état} du résumé IA est remplacé par le record ResumeObtenu (alerting/MoteurAlertes.java:302). Le reste tient : niveau d'impact en String, clé « fréquence:canal » découpée par split, statuts d'import et de corrélation en chaînes.

```text
grep -rn "new String\[\]" argos-server/src/main/java/io/argos/alerting -> 0 ; alerting/MoteurAlertes.java:302 « private record ResumeObtenu(String texte, String etat) » ; restent : impact/MatchRules.java:27 « enum Level » vs impact/ImpactQueries.java:46 « String level » ; alerting/MoteurAlertes.java:199 « cle.split(":") », :250 « "A_VERIFIER".equals(impact.level()) », :291 « case "EXACT" -> 0 » ; impact/CorrelationEngine.java:147-159 « "NONE"/"DONE"/"UNAVAILABLE" » ; inventory/InventoryService.java:42 « String status » ; inventory/ImportPlanifie.java:81-86 « default -> conserves++ ».
```

*Réserve* : Pas de test qui verrouille le nouveau record ; le défaut silencieux de ImportPlanifie.java:86 reste le point le plus risqué.

*Au 20/11* : Ouvert (Faible) — Niveau d'impact, statuts d'import et résumé IA restent typés par chaînes et tableaux ; nouveau switch sur chaînes avec défaut silencieux.

**Preuve d'origine**

```text
impact/MatchRules.java:23 « public enum Level { EXACT, PROBABLE, A_VERIFIER } » mais impact/ImpactQueries.java:34 « String level » ; alerting/MoteurAlertes.java:191 « !"A_VERIFIER".equals(i.level()) », :233-236 « case "EXACT" -> 0; case "PROBABLE" -> 1; » ; :146 et :262-271 « new String[] {texte, "DISPONIBLE"} » ; :163 « String[] fc = cle.split(":") » ; impact/CorrelationEngine.java:106-121 statuts « "NONE" », « "DONE" », « "UNAVAILABLE" » ; inventory/InventoryService.java:30 « String status » (IMPORTED, REJECTED...).
```

**Impact** — Une faute de frappe ou un nouveau niveau n'est détecté qu'à l'exécution ; le classement rangNiveau() renvoie 2 par défaut pour toute valeur inconnue, ce qui masquerait silencieusement un renommage.

**Action** — Exposer MatchRules.Level (ou un type partagé) dans ImpactView, remplacer String[] par un record ResumeIa(texte, etat) et la clé « fréquence:canal » par un record ; convertir les statuts en enums sérialisés par Jackson.

### AUD-QUA-09 — Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 11

**Vérification du 21/11 (b142695)** — Une convention est désormais écrite et outillée, mais elle entérine le mélange par fichier au lieu de fixer une langue par concept ; tables et classes mêlent toujours espace/workspace.

*Changement depuis le 20/11* : Commits 607ebc2 et e3793a6 : arc42 §8.16 « Conventions de code » écrit une règle de nommage (aucun identifiant d'une ou deux lettres, « en français quand le fichier est en français »), outillée par scripts/verifier-nommage.mjs (0 violation sur 538 fichiers). La règle tranche la longueur des noms, pas la langue d'un concept : le lot20 a renommé en anglais dans les fichiers anglais (connection/statement dans inventory/InventoryQueries, signal/SignalRecorder) et en français ailleurs (connexion/requete).

```text
docs/architecture/arc42/08-concepts-transverses.md:283-289 (§8.16) ; node scripts/verifier-nommage.mjs -> « 538 fichiers analysés, 0 violation(s), 5 exception(s) en liste blanche » ; mélange : V19__depots_projet_et_occurrences.sql (project_repository, component_occurrence) vs V16__veille_reglementaire.sql (texte_officiel, fiche_reglementaire) ; inventory/ProjectDirectory.java (avecDepots/ajouterDepot vs withRepository/find/create) ; git diff 54add78 HEAD -- argos-server/src/main/java | grep "Connection connection = " (InventoryQueries) vs "Connection connexion = ".
```

*Réserve* : Aucun ADR ni règle sur le choix espace/workspace ; le contrôle ne porte pas sur la langue.

*Au 20/11* : Ouvert (Faible) — Mélange français/anglais inchangé et étendu aux nouvelles tables et classes.

**Preuve d'origine**

```text
Tables (migrations) : « espace », « appartenance » mais « workspace_vue », « workspace_preference_utilisateur », « subscription », « project_impact » ; colonnes d.workspace_id vs a.espace_id (RadarProjets.java:215, Destinataires.java:41). Classes : impact/ComplementaryAnalysis.java et impact/AnalyseComplementaireIa.java ; ImpactQueries, CorrelationEngine, SyncStore, InventoryService à côté de RechercheVeille, MoteurAlertes, EspacesService ; MoteurAlertes.java:175 « impacts.forSignals(...) » / « abonnements.activeFor(...) ». Aucune convention de langue dans docs/architecture (grep -rli "nommage|langue du code" -> aucun ADR).
```

**Impact** — Recherche dans le code et dans le schéma plus difficile (espace ou workspace ? projet ou project ?), risque de doublons conceptuels, coût d'entrée pour un contributeur.

**Action** — Écrire une courte règle (ADR ou arc42 §8) : domaine en français, termes techniques et API externes en anglais par exemple ; appliquer la règle au nouveau code et renommer progressivement les identifiants ambigus (espace/workspace) lors des migrations déjà prévues.

### AUD-QUA-10 — Horloge injectable seulement dans CollecteWeb et LimiteurDebit : 63 appels à now() côté Java (37 fichiers) sans Clock commun, mêlés à 47 now() SQL, et deux LocalDate.now() sans fuseau

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 4
- **Bloque** : AUD-TST-06

**Vérification du 21/11 (b142695)** — Horloge toujours non injectée ; chiffres du titre confirmés.

*Changement depuis le 20/11* : Inchangé : 63 appels Java à now() (37 fichiers), 47 lignes now() SQL, aucun java.time.Clock ; les deux LocalDate.now() sans fuseau restent. Le lot superviseur n'a pas touché à l'horloge.

```text
grep -rnE "(Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime)\.now\(" argos-server/src/main/java | wc -l -> 63 (grep -l -> 37 fichiers) ; grep -rnE "[^.A-Za-z_]now\(\)" | wc -l -> 47 ; grep -rnE "\bClock\b" -> 0 ; catalog/standard/ReferentielEspace.java:138 et mcp/OutilsReferentiel.java:98 « LocalDate.now() » ; horloges injectables seulement signal/web/CollecteWeb.java:81 et api/security/LimiteurDebit.java:31.
```

*Au 20/11* : Ouvert (Faible) — Horloge toujours non injectée ; dispersion en hausse.

**Preuve d'origine**

```text
grep -rnE "(Instant|LocalDate|OffsetDateTime)\.now\(\)" argos-server/src/main/java | wc -l -> 31 (19 fichiers), grep "Clock" -> 0. Décisions métier prises avec l'heure système : workspace/EspacesService.java:254 (expiration d'invitation), inventory/InventoryQueries.java:214 (SBOM périmée), vulnintel/KevSource.java:65 (cache), api/recherche/TraductionCriteres.java:117. catalog/standard/ReferentielEspace.java:138 et mcp/OutilsReferentiel.java:100 « LocalDate.now() » (fuseau JVM) alors que RappelsRadar.java:93 utilise « LocalDate.now(ZoneId.of(fuseau)) » (argos.alerting.fuseau). 42 « now() » SQL (ex. StockActualites.java:88, :250) côtoient Instant.now() Java dans la même mise à jour (StockActualites.java:248-252).
[À 9d96406] 37 now() Java (23 fichiers), 45 now() SQL ; LocalDate.now() sans fuseau inchangés (ReferentielEspace.java:138, OutilsReferentiel.java:100) ; le titre est à corriger (CollecteWeb.java:81).
```

**Impact** — Tests dépendants du temps réel pour ces chemins ; deux horloges (JVM et PostgreSQL) dans une même règle ; la date « du jour » dépend de la variable TZ du conteneur (compose.yaml:54) et non du fuseau configuré. Le schéma déjà présent « point d'entrée planifié -> méthode recevant maintenant » (MoteurAlertes.java:126, RappelsRadar.java:93) limite le problème.

**Action** — Produire un bean java.time.Clock (fuseau argos.alerting.fuseau) et l'injecter à la place de Instant.now()/LocalDate.now() ; passer l'instant en paramètre SQL plutôt que now() là où la règle compare avec une valeur calculée en Java.

### AUD-QUA-11 — Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 4

**Vérification du 21/11 (b142695)** — Les trois exécuteurs statiques hors CDI existent toujours, sans arrêt.

*Changement depuis le 20/11* : Inchangé (déplacement de lignes seulement).

```text
alerting/MoteurAlertes.java:80 « private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(2, 2, ...) » ; projectintel/SyntheseActivite.java:49 « new ThreadPoolExecutor(1, 2, ...) » ; ingestion/web/RecuperateurWeb.java:51 « Executors.newSingleThreadScheduledExecutor » ; grep -rnE "@PreDestroy|\.shutdown|ManagedExecutor" argos-server/src/main/java -> 0.
```

*Au 20/11* : Ouvert (Faible) — Les trois exécuteurs statiques hors CDI existent toujours, sans arrêt.

**Preuve d'origine**

```text
alerting/MoteurAlertes.java:75 « private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(2, 2, ...) » ; projectintel/SyntheseActivite.java:46 (même motif recopié) ; ingestion/web/RecuperateurWeb.java:51 « Executors.newSingleThreadScheduledExecutor » ; grep -rn "shutdown\|@PreDestroy\|ManagedExecutor" -> aucun résultat.
```

**Impact** — Les fils cœur ne s'arrêtent jamais : fuite de fils à chaque rechargement à chaud en développement ; tâches perdues sans trace à l'arrêt ; contexte CDI et métriques Quarkus absents dans ces fils ; dimensionnement codé en dur et dupliqué.

**Action** — Utiliser un ManagedExecutor (SmallRye Context Propagation) ou un bean @ApplicationScoped qui possède l'exécuteur avec @PreDestroy shutdownNow(), taille configurable ; factoriser le motif « soumettre + get(délai) + annuler » partagé par MoteurAlertes et SyntheseActivite.

### AUD-QUA-18 — La taille du serveur progresse encore (21 fichiers Java de plus de 300 lignes, 19 à 54add78, 9 à 9d96406) ; les méthodes de complexité ≥ 15 reculent à 6 (9) après les découpages du lot23, mais SignalRecorder.record (86 lignes) et PreparationFiches.preparer (74) restent d'un bloc

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : M · **Sprint** : 11

**Vérification du 21/11 (b142695)** — Les méthodes les plus complexes ont réellement été découpées, sauf SignalRecorder.record ; la taille des fichiers continue de croître et aucun seuil n'est vérifié. Correction de mesure : SbomDocument.reconnaitre (« CC≈28 / 88 l. » au 20/11) était un artefact du script, qui comptait l'accolade du littéral '{' (SbomDocument.java:69) ; la méthode fait 15 lignes, CC 6, inchangée depuis 9d96406.

*Changement depuis le 20/11* : Commits 686c47f (CorrelationEngine.deterministic découpée), 8303378 (CollecteReglementaire.executer), 12c541f/eb777a2 (alerting, dont ResumesPeriodiques.composerPour), 867cd28 (TraductionCriteres.traduire), 6d13eb3/88d3a41 (PasserelleIa.demander). Les méthodes de CC ≥ 15 passent de 9 à 6 et les méthodes de 60 lignes ou plus de 8 à 5. SignalRecorder.record (86 l.) n'est pas découpée. En revanche le découpage a grossi les fichiers : 19 -> 21 fichiers de plus de 300 lignes (CorrelationEngine 440 -> 490, MoteurAlertes 361 -> 430, CollecteReglementaire 411 -> 458) ; serveur 28 436 -> 29 114 lignes (269 fichiers). Aucun seuil en CI.

```text
Script corrigé /home/claude/audit-maj2/build-QUAL/cc3.py (retire commentaires, chaînes, blocs de texte et littéraux caractère ; gère les signatures multilignes, ignore les en-têtes de record), exécuté sur les deux versions : 54add78 -> 1 267 méthodes, 9 de CC ≥ 15 ; b142695 -> 1 347 méthodes, 6 de CC ≥ 15 : catalog/profil/ValidateurManifeste.java:24 erreurs (CC 32, 74 l.), ingestion/web/GardeAdresses.java:187 ipv4Interdite (32, 19 l.), workspace/subscription/SubscriptionService.java:239 validate (25, 56 l.), regintel/ExpertiseFiches.java:72 corriger (20, 47 l.), scm/DemandesEvolution.java:70 reconnaitre (18, 37 l.), ingestion/legi/ArchiveTar.java:41 lire (16, 47 l.). Top 15 des méthodes longues à b142695 : SignalRecorder.java:65 record 86 l. ; ValidateurManifeste.java:24 erreurs 74 ; regintel/PreparationFiches.java:146 preparer 74 ; projectintel/ActiviteStore.java:57 enregistrer 66 ; catalog/standard/ReferentielEspace.java:229 appliquer 60 ; impact/RechercheVeille.java:206 restituer 56 ; SubscriptionService.java:239 validate 56 ; impact/AnalyseComplementaireIa.java:111 lire 55 ; impact/RapprochementActualites.java:77 rapprocher 55 ; catalog/dictionnaire/DictionnaireMetier.java:340 lire 50 ; regintel/ExpertiseFiches.java:244 remplacerDomaines 50 ; signal/opportunite/SourcesProfil.java:78 declarer 50 ; ingestion/SourceSynchronizer.java:58 synchronize 49 ; inventory/InventoryStore.java:225 ecrireOccurrences 49 ; regintel/FichesEnBase.java:178 completer 49. Fichiers > 300 l. : find argos-server/src/main/java -name '*.java' -exec wc -l {} + | awk '$1>300' -> 21 (InventoryQueries 557, CorrelationEngine 490, OutilsSecurite 468, CollecteReglementaire 458, RepositoryActivity 442, MoteurAlertes 430…).
```

*Réserve* : PreparationFiches.preparer (74 l.) n'a pas raccourci malgré 4559838 « préparation des fiches découpée par espace » : c'est l'appelant qui a été découpé, la méthode d'écriture reste d'un bloc. Pas de seuil CI.

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
find argos-server/src/main/java -name '*.java' | xargs wc -l : 28 436 lignes / 269 fichiers (20 574 / 228 à 9d96406), plus gros inventory/InventoryQueries.java 556, mcp/OutilsSecurite.java 458, impact/CorrelationEngine.java 440. Script /home/claude/audit-maj/build-QUAL/cc.py (dérivé de qual-scripts/longmeth.py) : catalog/profil/ValidateurManifeste.java:24 CC≈32 / 74 l. ; scm/SbomDocument.java:63 reconnaitre CC≈28 / 88 l. (nouvelle) ; workspace/subscription/SubscriptionService.java:239 CC≈25 ; api/recherche/TraductionCriteres.java:71 CC≈24 ; regintel/CollecteReglementaire.java:132 executer CC≈20 / 81 l. (nouvelle) ; regintel/ExpertiseFiches.java:72 CC≈20. Allongées : impact/CorrelationEngine.java:200 deterministic 80 -> 96 l., signal/SignalRecorder.java:64 record 67 -> 86 l. ; alerting/ResumesPeriodiques.java:119 composerPour toujours 100 l. Point positif : scm/RepositoryActivity github/gitlab découpées (606dcc8).
```

**Impact** — Les zones critiques (corrélation, enregistrement des signaux, reconnaissance des SBOM, collecte réglementaire) concentrent la logique dans des méthodes longues et très ramifiées : revue et tests plus coûteux, risque de régression sur les branches rares.

**Action** — Découper en étapes nommées SbomDocument.reconnaitre (une méthode par format), CollecteReglementaire.executer (index, boucle par jour, bilan), CorrelationEngine.deterministic et SignalRecorder.record ; fixer un seuil indicatif (ex. 60 lignes, CC 15) vérifié en CI (Checkstyle/PMD ou ArchUnit) pour éviter la dérive.

### AUD-QUA-19 — Les 24 fichiers TypeScript de e2e/ (3 500 lignes) ne sont soumis à aucune vérification de types : pas de tsconfig ni de dépendance typescript ; un tsc --strict révèle 6 erreurs

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 1

**Vérification du 21/11 (b142695)** — Les six erreurs de types relevées le 20/11 sont toujours présentes.

*Changement depuis le 20/11* : Inchangé : e2e/package.json sans typescript ni script de vérification, aucun tsconfig ; f7dd640 (lot20) n'a fait que renommer. 24 fichiers TypeScript, 3 500 lignes (3 485 à 54add78).

```text
Copie /home/claude/audit-maj2/build-QUAL/e2e (b142695) + tsconfig.audit.json strict : npx tsc -p tsconfig.audit.json -> rc=2, 6 erreurs : support/donnees.ts:9 (TS7016), support/electron.ts:299 (×2), :319 (×2), :326 (TS7006) ; grep -rn "as unknown as" e2e --include=*.ts -> 17 ; ls e2e/tsconfig* -> aucun.
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
ls e2e -> aucun tsconfig*.json ; e2e/package.json : devDependencies limitées à @playwright/test 1.61.1, scripts test/test:seul/test:accessibilite/test:performance sans tsc (Playwright transpile sans vérifier les types). Commande sur copie (/home/claude/audit-maj/build-QUAL/e2e, tsconfig.audit.json strict, moduleResolution Bundler) : npx tsc -p tsconfig.audit.json -> 6 erreurs : support/donnees.ts:9 (TS7016, import de ./docker.mjs sans déclaration), support/electron.ts:299, :319, :326 (TS7006, paramètres implicitement any). 17 « as unknown as » (16 dans e2e/support/electron.ts:151-293, 1 dans e2e/desktop/connexion.spec.ts:146).
```

**Impact** — Une erreur de typage dans les aides ou les parcours E2E (signature de fixture, forme d'une réponse d'API) n'apparaît qu'à l'exécution de la campagne, longue (pile Docker, Keycloak, Electron) ; les doubles Electron reposent sur des conversions forcées non vérifiées.

**Action** — Ajouter e2e/tsconfig.json (strict, noEmit) et typescript en devDependency, un script « typecheck » exécuté avant « test » ; déclarer docker.mjs (docker.d.mts) et typer les callbacks webRequest ; regrouper les conversions __argosE2E derrière une seule fonction typée.

### AUD-QUA-23 — Le résumé IA indisponible est journalisé sans sa pile : MoteurAlertes passe la cause en argument de format, à l'inverse de la règle suivie par le reste du lot superviseur

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 4
- **Nouveau** le 21/11

**Preuve**

```text
alerting/MoteurAlertes.java:342 « LOG.infof("résumé IA indisponible pour le signal %s : %s", signalId, erreur.getCause()) » (cause en %s, sans Throwable en premier argument) ; comparer projectintel/SyntheseActivite.java:104 « LOG.infof(erreur, ...) » et impact/CorrelationEngine.java:160 « LOG.infof(erreur, ...) ».
```

**Impact** — Une panne du fournisseur IA lors des alertes ne laisse que le toString de la cause, sans pile ni cause imbriquée : diagnostic plus long.

**Action** — Passer erreur.getCause() comme premier argument (LOG.infof(erreur.getCause(), ...)) et l'inclure dans une règle écrite de journalisation (arc42 §8).

### AUD-SEC-04 — Alertes e-mail envoyées à l'adresse du jeton même non vérifiée

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 6

**Vérification du 21/11 (b142695)** — L'adresse brute du jeton est toujours enregistrée et sert aux alertes, résumés et rappels réglementaires.

*Changement depuis le 20/11* : Inchangé (renommages). Le royaume livré ne restreint pas la modification de l'adresse par l'utilisateur dans la console de compte.

```text
argos-server/src/main/java/io/argos/workspace/ComptesService.java:74 `requete.setString(4, identite.email());` (alors que :54 calcule emailVerifieNormalise) ; alerting/Destinataires.java:39 et :51 (`SELECT ... c.email FROM appartenance a JOIN compte c`) ; alerting/RappelsReglementaires.java:115 (membresAvecProfil) ; alerting/CanalEmail.java:36.
```

*Au 20/11* : Ouvert (Faible) — L'adresse brute du jeton reste enregistrée et sert désormais aussi aux rappels réglementaires.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/workspace/ComptesService.java:74 `ps.setString(4, identite.email());` (adresse brute, alors que seule emailVerifieNormalise() sert aux invitations) ; alerting/Destinataires.java:40 `SELECT DISTINCT c.id, c.email FROM appartenance a JOIN compte c ...` utilisé par CanalEmail.
```

**Impact** — Un utilisateur dont l'e-mail n'est pas vérifié par Keycloak (modifiable par l'utilisateur selon la configuration du royaume) peut faire envoyer les alertes de ses espaces (titres, noms de projets) à un tiers : relais de courriels non sollicités au nom du NAS.

**Action** — N'enregistrer comme adresse d'envoi que l'e-mail vérifié (emailVerifieNormalise) et le mettre à jour à la connexion ; ignorer le canal EMAIL tant qu'aucune adresse vérifiée n'est connue.

### AUD-SEC-06 — Mot de passe de base de données par défaut « argos » si .env absent

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité, Dépendances · **Effort** : S · **Sprint** : 2
- **Fusionne** : AUD-DEP-10

**Vérification du 21/11 (b142695)** — Le repli « argos » subsiste pour la base, l'application et désormais Keycloak.

*Changement depuis le 20/11* : Aggravé par la PR #28 (e7ed7f3) : le même repli protège maintenant aussi la base de Keycloak (comptes, empreintes de mots de passe, clés de signature du royaume).

```text
compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}`, :51 `KC_DB_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}`, :92 `ARGOS_DB_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}`.
```

*Réserve* : Risque toujours borné : port PostgreSQL publié sur 127.0.0.1 seulement (compose.yaml:23). Voir aussi AUD-SEC-30 (compte partagé).

*Au 20/11* : Ouvert (Faible) — Le repli « argos » subsiste pour la base et l'application.

**Preuve d'origine**

```text
compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` (et :44 pour l'application) ; .env.example:6 valeur d'exemple à changer.
[AUD-DEP-10] compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` et :44 idem ; docs/deploiement/synology-et-pc.md:92 « défaut argos ».
```

**Impact** — Si le fichier .env est oublié, la base démarre avec un mot de passe trivial. Risque borné : port publié sur 127.0.0.1 seulement (compose.yaml:23) et réseau interne au projet.

**Action** — Remplacer le repli par `${ARGOS_DB_PASSWORD:?ARGOS_DB_PASSWORD obligatoire}` pour refuser le démarrage sans mot de passe explicite.

### AUD-SEC-07 — Sauvegardes complètes de la base écrites en clair avec les permissions par défaut

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 2

**Vérification du 21/11 (b142695)** — Pas d'umask, pas de chiffrement.

*Changement depuis le 20/11* : Script inchangé.

```text
scripts/sauvegarder-base.sh:19 `mkdir -p "$DEST"`, :22 `pg_dump ... | gzip > "$FILE"`.
```

*Réserve* : Le script ne sauvegarde que la base d'Argos et pas la base keycloak du profil idp-local : voir AUD-DEP-24.

*Au 20/11* : Ouvert (Faible) — Pas d'umask, pas de chiffrement.

**Preuve d'origine**

```text
scripts/sauvegarder-base.sh:19 `mkdir -p "$DEST"` et :22 `pg_dump ... | gzip > "$FILE"` exécutés en root sur le NAS (commentaire l.4), sans umask ni chiffrement ; copie hors NAS recommandée (docs/deploiement/synology-et-pc.md:68).
```

**Impact** — Les dumps (comptes, e-mails, projets, journaux) sont lisibles par d'autres comptes du NAS selon l'umask et voyagent en clair vers le stockage de sauvegarde.

**Action** — `umask 077` en tête du script, dossier en 700 ; chiffrer le dump (age/gpg) ou activer le chiffrement côté Hyper Backup.

### AUD-SEC-09 — Recherche plein texte et veille personnelle (/me/veille) sans limite de débit

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 6

**Vérification du 21/11 (b142695)** — Toujours aucune limite sur la recherche plein texte ni sur la veille personnelle.

*Changement depuis le 20/11* : Inchangé (renommages dans RechercheResource, VuesResource, VeilleProfilsResource).

```text
argos-server/src/main/java/io/argos/api/recherche/RechercheResource.java:57 ; api/recherche/VuesResource.java:115 ; api/veille/VeilleProfilsResource.java:139 ; `grep -rln LimiteurDebit argos-server/src/main/java` : ActiviteResource, VeilleWebResource, ProfilArtisteResource, ReglementaireResource, mcp/GardeAppels, mcp/ControleInitialisation seulement.
```

*Au 20/11* : Ouvert (Faible) — Toujours aucune limite sur la recherche plein texte ; périmètre élargi à la veille personnelle.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/recherche/RechercheResource.java:48 `rechercher(...)` et VuesResource `/{id}/resultats` : aucun LimiteurDebit (présent seulement sur veille web, synthèse d'activité et MCP).
```

**Impact** — Un compte peut enchaîner des requêtes plein texte coûteuses sur le processeur du NAS (dégradation pour les autres utilisateurs).

**Action** — Appliquer LimiteurDebit (ex. 60/min/compte) aux routes de recherche, ou une limite globale au reverse proxy.

### AUD-SEC-23 — Desktop : l'écoute de bouclage 127.0.0.1 accepte le premier GET /argos/retour sans comparer le state et se ferme (interruption de connexion par un processus local)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 6

**Vérification du 21/11 (b142695)** — L'écoute accepte toujours le premier GET /argos/retour sans comparer le state, puis se ferme.

*Changement depuis le 20/11* : Découpage de connexion.js (92985ae, 5d2d0ad) sans changement de comportement.

```text
argos-desktop/src/connexion.js:101-104 (retourAttendu : méthode, chemin, écoute active), :119-130 (page de retour puis fermer(url.search)) ; state connu à :72-73 ; argos-desktop/test/connexion.test.js : aucun cas de retour à state étranger.
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-desktop/src/connexion.js:99-102 `retourAttendu` ne teste que méthode, chemin et écoute active ; :142-150 toute requête conforme reçoit la page de retour puis `fermer(url.search...)` ; le state est pourtant connu du processus principal (URL validée à :172, paramètre `state` recopié à :69-72) ; la vérification du state n'a lieu que dans l'IHM (argos-ui/src/session/oidc.ts:148), qui rejette alors le retour et abandonne la tentative.
```

**Impact** — Pendant la fenêtre de connexion (5 min au plus, port éphémère), un processus local ou toute source capable d'émettre un GET vers 127.0.0.1 (balayage de ports) fait échouer la connexion en cours. Aucun vol de jeton : state vérifié côté IHM et code inutilisable sans le vérificateur PKCE. Déni de service ponctuel seulement.

**Action** — Mémoriser le state de l'URL autorisée dans l'écoute et ne clore l'écoute que sur un retour portant ce state (comparaison en temps constant) ; répondre 400 aux autres sans fermer ; ajouter le cas à argos-desktop/test/connexion.test.js.

### AUD-SEC-24 — Campagne de performance e2e : serveur lancé par « mvn.cmd » via le PATH avec shell: true sous Windows, contrairement à la règle des chemins absolus

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 1

**Vérification du 21/11 (b142695)** — mvn toujours résolu par le PATH avec shell: true sous Windows.

*Changement depuis le 20/11* : Retouches de style (339f333, 974e077) ; appel inchangé.

```text
e2e/performance/mesurer-p95.mjs:117-120 ; e2e/playwright.config.ts:89 (`command: 'mvn ...'`).
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
e2e/performance/mesurer-p95.mjs:117-120 `const mvn = platform() === 'win32' ? 'mvn.cmd' : 'mvn'` puis `spawn(mvn, ['-B','-q','-f', join(RACINE, 'argos-server','pom.xml'), ...], { ..., shell: platform() === 'win32' })` ; commentaire :77-78 « Exécutables appelés par leur chemin absolu ... la campagne ne dépend pas du PATH » ; docker, git et taskkill le sont (support/docker.mjs:5-19, mesurer-p95.mjs:79-84,95-96).
```

**Impact** — Poste du développeur uniquement : un mvn.cmd placé plus tôt dans le PATH est exécuté ; avec shell: true, le chemin du dépôt (RACINE) est concaténé sans échappement dans la ligne de cmd.exe : un chemin contenant espaces, & ou ^ casse la commande ou injecte une commande. Pas d'impact sur le serveur déployé.

**Action** — Résoudre mvn comme docker/git (emplacements fixes + variable ARGOS_E2E_MVN) et l'appeler sans shell (sous Windows : `cmd.exe /d /s /c` avec arguments échappés, ou lancer directement java/quarkus-run) ; même revue pour le webServer de playwright.config.ts:89.

### AUD-SEC-25 — Image : code de l'application modifiable par l'utilisateur d'exécution et conteneurs sans durcissement (read_only, cap_drop, no-new-privileges)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 3

**Vérification du 21/11 (b142695)** — Code de l'application toujours propriété de l'utilisateur d'exécution ; conteneurs sans read_only, cap_drop ni no-new-privileges.

*Changement depuis le 20/11* : Dockerfile inchangé sur ce point ; le nouveau service keycloak (compose.yaml:39-77) n'a pas non plus de durcissement.

```text
Dockerfile:48 `COPY --from=server --chown=1001:0 ...` puis :51 `USER 1001` ; compose.yaml:79-107 (argos) et :39-77 (keycloak) : `grep -n 'read_only\|cap_drop\|no-new-privileges' compose.yaml` : rien.
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
Dockerfile:48 `COPY --from=server --chown=1001:0 /src/argos-server/target/quarkus-app/ ./` puis :51 `USER 1001` (jars et lib appartiennent à l'utilisateur qui exécute la JVM ; présent dès 9d96406, non relevé) ; compose.yaml:34-59 (service argos) sans `read_only`, `cap_drop`, `security_opt: [no-new-privileges:true]`.
```

**Impact** — Une exécution de code dans la JVM (dépendance vulnérable) pourrait réécrire durablement les jars de l'application dans le conteneur ; défense en profondeur absente avant l'exposition publique. Risque d'hygiène, pas de vecteur direct identifié.

**Action** — Copier quarkus-app sans --chown (root, lecture seule pour 1001), comme déjà fait pour /opt/argos/licenses (Dockerfile:43-50) ; ajouter au service argos `read_only: true` + tmpfs /tmp, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`.

### AUD-TST-02 — Pas de seuil de couverture : JaCoCo absent du pom ; la couverture (85,8 % des lignes, 72,0 % des branches à b142695, scm à 51,8 % / 41,3 %) n'est mesurée qu'à la main

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01
- **Bloque** : AUD-TST-04

**Vérification du 21/11 (b142695)** — Aucun seuil ni rapport de couverture dans le build.

*Changement depuis le 20/11* : Aucun changement : ni jacoco-maven-plugin ni quarkus-jacoco ; seul maven-surefire-plugin 3.5.3 (configuration java.util.logging.manager).

```text
argos-server/pom.xml:199-207 (maven-surefire-plugin seul) ; grep -n jacoco argos-server/pom.xml -> aucun résultat
```

*Réserve* : Les taux du titre sont ceux de 54add78 ; la couverture à b142695 dépend de la mesure JaCoCo lancée par l'orchestrateur sur le poste du mainteneur.

*Au 20/11* : Ouvert (Faible) — Aucun seuil ni rapport de couverture dans le build.

**Preuve d'origine**

```text
argos-server/pom.xml:177-185 (seul plugin de test : maven-surefire-plugin, configuration limitée à java.util.logging.manager) ; grep -n jacoco pom.xml -> aucun résultat
```

**Impact** — Aucune alerte si la couverture recule sur un module critique ; la mesure de cet audit a dû être lancée par une commande ajoutée à la main.

**Action** — Ajouter jacoco-maven-plugin (prepare-agent + report, compatible @QuarkusTest via quarkus-jacoco) et un check avec seuils par paquet (au moins api.security, mcp, workspace, alerting) une fois la CI en place.

### AUD-TST-04 — Couverture inégale : cinq modules sans classe de test dédiée, dont workspace (26 fichiers, 1 761 lignes) et inventory (11 fichiers, 1 865 lignes) ; scm doté de tests mais module le moins couvert (51,8 % des lignes)

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Tests · **Effort** : M · **Sprint** : 7
- **Dépend de** : AUD-TST-02

**Vérification du 21/11 (b142695)** — Partiel : scm corrigé ; cinq modules toujours sans classe de test dédiée, désormais modifiés sur leurs chemins d'échec sans test.

*Changement depuis le 20/11* : Aucun changement depuis le 20/11 sur la partie restante : workspace (26 fichiers, 1 761 l.), inventory (11, 1 865 l.), vulnintel (10, 563 l.), releaseradar (5, 530 l.) et audit (4, 186 l.) n'ont toujours aucune classe de test dédiée. Ces modules ont pourtant changé de comportement sur leurs chemins d'échec au lot 23 sans test associé : 926d486 (inventory/ImportPlanifie), f6b93dd (vulnintel/Enricher), eba8b62 (releaseradar/CyclesDeVie) ne touchent aucun fichier de test (voir AUD-QUA-20). VuesEnregistrees, MoteurAlertes, GardeAppels et JournalRefus ne sont nommées dans aucun test ; SubscriptionService ne l'est que par le test d'architecture SubscriptionIsolationTest. scm reste testé (4 classes unitaires, 2 de contrat).

```text
scripts-test/inventaire.sh à b142695 (testCl = 0 pour audit, inventory, releaseradar, vulnintel, workspace) ; grep -rlw 'VuesEnregistrees|MoteurAlertes|GardeAppels|JournalRefus' argos-server/src/test/java -> 0 ; git show --name-only 926d486 f6b93dd eba8b62 -> aucun fichier src/test
```

*Réserve* : Taux de couverture par module à reprendre de la mesure JaCoCo de l'orchestrateur à b142695.

*Au 20/11* : Partiel (Faible) — Partiel : scm est corrigé (4 classes unitaires et 2 de contrat) et sa couverture passe de 26,9 % à 51,8 % des lignes (41,3 % des branches) d'après la mesure JaCoCo du 20/11 ; cinq modules restent sans classe de test dédiée (workspace, inventory, vulnintel, releaseradar, audit) mais sont couverts à 81–89 % des lignes par les parcours.

**Preuve d'origine**

```text
Comptage main/test par paquet : workspace 24/0, inventory 10/0, scm 8/0, vulnintel 9/0, releaseradar 5/0, regintel 3/0, audit 4/0 ; impact 13 fichiers (1 919 l.) pour 1 test (30 l.). Classes jamais nommées dans src/test : workspace/subscription/SubscriptionService, workspace/vue/VuesEnregistrees, alerting/MoteurAlertes, mcp/GardeAppels, mcp/OutilsSecurite, audit/JournalRefus, releaseradar/* (0/4)
[À 9d96406] Six modules sans test dédié : workspace (26/1 693 l.), inventory, vulnintel, releaseradar, regintel, audit ; scm a DepotGithubTest.
[Mesure] mvn clean verify + JaCoCo 0.8.13 sur le poste du mainteneur : scm 26,9 % lignes / 8,9 % branches (290 l.) ; total 83,8 % / 68,6 %.
```

**Impact** — La couverture globale mesurée est bonne (83,8 % des lignes, 68,6 % des branches), mais scm, qui lit les dépôts GitHub et GitLab des utilisateurs, est presque entièrement hors tests ; les six modules sans test dédié ne sont couverts que par les parcours, sans cas limites ciblés.

**Action** — Ajouter des tests ciblés de scm (pagination, quotas, erreurs de forge) jusqu'au seuil de couverture, puis des tests dédiés des cas limites de workspace (rôles, dernier administrateur, invitations).

### AUD-TST-06 — Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s, marge de 900 ms sur le délai IA)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 4
- **Dépend de** : AUD-QUA-10

**Vérification du 21/11 (b142695)** — Délais réels et horloge réelle toujours utilisés dans les assertions.

*Changement depuis le 20/11* : Aucun changement de fond (renommages seulement) ; les quatre cas restent : attente réelle bornée à 4 s contre un résumé simulé qui dort 5 s, sleep 1 s avant assertFalse(isDone) dans DefenseEnProfondeurTest (non aligné sur le modèle pg_locks d'ImportPlanifieTest), attente active de 5 s, marge de 900 ms dans PasserelleIaTest.

```text
alerting/AlertesTest.java:314-323 + testsupport/SimulatedResumeIa.java:73 Thread.sleep(5_000) + application.properties:75 %test…resume-ia.delai-max=PT1S ; api/comptes/DefenseEnProfondeurTest.java:159-161 ; signal/web/CollecteWebTest.java:119-127 ; aigateway/PasserelleIaTest.java:266-269 avec testsupport/StubServer.java:91 argos.ia.delai=PT2S ; contre-exemple sain : chain/ImportPlanifieTest.java:320-328 (pg_locks)
```

*Au 20/11* : Ouvert (Faible) — Délais réels et horloge réelle toujours utilisés dans les assertions.

**Preuve d'origine**

```text
alerting/AlertesTest.java:314-323 (Instant.now() avant/après publier(), assertTrue(attente < 4 s)) avec testsupport/SimulatedResumeIa.java:73 Thread.sleep(5_000) et application.properties:68 %test…resume-ia.delai-max=PT1S ; api/comptes/DefenseEnProfondeurTest.java:158 Thread.sleep(1000); assertFalse(retrait.isDone()) ; signal/web/CollecteWebTest.java:120-125 boucle System.currentTimeMillis()+5000 / Thread.sleep(10)
```

**Impact** — Marge de 3 s sur machine lente ou chargée (runner CI partagé, NAS) : échecs intermittents possibles ; le test de verrou (sleep 1 s) peut passer à vide si l'ordonnancement retarde la soumission.

**Action** — Injecter une horloge/un délai simulé dans la passerelle de résumé (comme CollecteWeb.horloge) au lieu de dormir ; remplacer le sleep de DefenseEnProfondeurTest par une observation du verrou (pg_locks / pg_stat_activity wait_event) ; utiliser Awaitility pour les attentes actives.

### AUD-TST-08 — Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 7

**Vérification du 21/11 (b142695)** — Isolation toujours fondée sur deux listes codées en dur, sans contrôle d'exhaustivité.

*Changement depuis le 20/11* : Aucun changement : V21 n'ajoute qu'un index (project_impact_recents), la liste TRUNCATE de TestDb et celle d'ArtisteFixtures sont inchangées ; les six mêmes tables restent non vidées (appartenance, compte, espace, ia_prompt, invitation, workspace_preference_utilisateur), project_impact_transition l'est par CASCADE, source et reg_source par DELETE/UPDATE.

```text
argos-server/src/test/java/io/argos/testsupport/TestDb.java:51-55 ; testsupport/ArtisteFixtures.java:24 ; comparaison avec les 63 CREATE TABLE de V1 à V21 (script inline) -> mêmes 6 tables non vidées ; db/migration/V21__index_impacts_recents.sql (CREATE INDEX seul)
```

*Réserve* : Aucune fuite constatée : aucune table créée depuis le 20/11.

*Au 20/11* : Ouvert (Faible) — Isolation toujours fondée sur deux listes codées en dur, sans contrôle d'exhaustivité.

**Preuve d'origine**

```text
testsupport/TestDb.java:49-56 (TRUNCATE dette_version, … sync_cursor CASCADE) ; comparaison avec les CREATE TABLE des 14 migrations -> non vidées : appartenance, compte, espace, invitation, workspace_preference_utilisateur, ia_prompt (source partiellement : DELETE … WHERE id <> 'OSV')
[À 9d96406] TestDb.java:49-56 inchangé ; les 4 tables de V15 sont vidées hors TestDb par ArtisteFixtures.java:24 (seconde liste codée en dur).
```

**Impact** — Isolation par convention (chaque test crée ses espaces, cf. Javadoc TestDb) ; toute nouvelle table oubliée dans la liste fait fuir de l'état entre classes et rend les résultats dépendants de l'ordre d'exécution.

**Action** — Générer la liste à partir de information_schema (toutes les tables du schéma sauf flyway_schema_history et référentiels explicitement conservés) ou ajouter un test qui échoue si une table n'est ni vidée ni déclarée conservée.

### AUD-TST-09 — IHM : la logique et 9 composants sur 21 (rendu SSR) sont testés ; ni le client API effectif (appelerApi) ni les 10 vues

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Tests · **Effort** : M · **Sprint** : 7

**Vérification du 21/11 (b142695)** — Logique et rendu statique couverts ; client REST effectif et vues toujours hors tests unitaires.

*Changement depuis le 20/11* : Aucun changement de périmètre : 206 tests (25 fichiers), dont rendu SSR de 9 composants sur 21 ; client.ts (234 l.) n'est toujours lu qu'en texte (recherche.test.mjs:177, :204) et les 10 vues ne sont vérifiées que par expressions régulières sur leur source (abonnements.test.mjs:101, inventaire-depots.test.mjs:136, veille-profils.test.mjs:135-149, resume-ia.test.mjs:26). Les modifications du delta dans argos-ui/tests sont des renommages (25f6f1b, a371248).

```text
argos-ui/package.json « test »: node --test "tests/**/*.test.mjs" ; grep des imports de argos-ui/tests : aucun import de src/api/client.ts ni de src/views/ ; npm test -> 206/206 (build-tests, 2026-11-21)
```

*Réserve* : Les vues sont exercées dans un vrai navigateur par la campagne E2E, mais celle-ci n'a pas été rejouée depuis le 19/11 (voir AUD-TST-20).

*Au 20/11* : Partiel (Faible) — Logique et rendu statique largement couverts ; client REST effectif et vues toujours hors tests unitaires.

**Preuve d'origine**

```text
argos-ui/package.json « test »: node --test "tests/**/*.test.mjs" (pas de vitest ni @vue/test-utils) ; imports des tests : src/contenu/{alertes-poste,resolution,resume-ia,liens}.ts uniquement ; resume-ia.test.mjs:26-29 lit AlertesView.vue en texte et vérifie l'absence de v-html ; src/api/client.ts (207 l.) et les 9 composants .vue non testés
```

**Impact** — Le client API (jetons, erreurs, formats) et le rendu des vues peuvent régresser sans détection ; les contrôles par expression régulière sur le source ne garantissent pas le comportement rendu.

**Action** — Introduire vitest + @vue/test-utils (environnement happy-dom) : tests du client API avec fetch simulé (401, erreurs, sérialisation) et tests de rendu des vues Alertes/Affichage/Réglages.

### AUD-TST-10 — Desktop : main.js (256 l.), qui porte la configuration de sécurité Electron et désormais les réglages d'emballage (PR #28), n'est pas testé ; preload.js l'est

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 7

**Vérification du 21/11 (b142695)** — Surface preload/IPC protégée ; main.js, dont la configuration de sécurité vient de gagner une nouvelle source de réglages, toujours sans test.

*Changement depuis le 20/11* : preload.js reste protégé (preload.test.js, 9 tests). main.js passe de 248 à 256 lignes : la PR #28 (e7ed7f3) y ajoute la lecture des réglages d'emballage (champ argos du package.json : apiUrl, oidcUrl), qui alimentent la CSP et la seule destination OIDC acceptée, sans test ; webPreferences (contextIsolation, nodeIntegration, sandbox) toujours non vérifiées.

```text
argos-desktop/src/main.js:25-34 (REGLAGES_EMBALLES, API_URL, OIDC_URL), :104-106 (webPreferences) ; grep -n 'contextIsolation|nodeIntegration|REGLAGES_EMBALLES|apiUrl' argos-desktop/test/*.js -> aucun résultat ; argos-desktop/test/preload.test.js:27-42
```

*Au 20/11* : Partiel (Faible) — Surface preload/IPC protégée ; webPreferences de main.js toujours sans test.

**Preuve d'origine**

```text
argos-desktop/test/*.test.js ne requièrent que ../src/{preferences,notifications,poste,ui-protocol} ; argos-desktop/src/main.js:92-94 (contextIsolation: true, nodeIntegration: false, sandbox: true) non vérifié par un test
```

**Impact** — Une régression sur les webPreferences, la CSP ou la surface exposée par preload (contextBridge) ouvrirait l'exécution de code distant dans le client sans qu'aucun test ne le signale.

**Action** — Extraire la construction des options de BrowserWindow et la liste des API exposées par preload dans des fonctions pures testées (contextIsolation/sandbox à true, nodeIntegration à false, liste blanche des canaux IPC).

### AUD-TST-12 — Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : M · **Sprint** : 11
- **Dépend de** : AUD-TST-01

**Vérification du 21/11 (b142695)** — Aucun test sur l'artefact empaqueté ; le parcours « IHM servie par le serveur » introduit par la PR #28 n'est exercé nulle part.

*Changement depuis le 20/11* : Inchangé. La PR #28 rend le besoin plus concret : l'IHM est désormais servie par Quarkus depuis META-INF/resources (copiée seulement dans l'étage Docker) avec des chemins publics dédiés, et Keycloak est fourni par le profil compose idp-local ; ni l'image, ni le jar, ni compose ne sont démarrés par un test. La campagne E2E reste sur mvn quarkus:dev + Vite.

```text
argos-server/pom.xml:34 <skipITs>true</skipITs> ; find . -name '*IT.java' -> aucun ; grep -rn QuarkusIntegrationTest argos-server/src -> aucun ; Dockerfile:36 COPY … META-INF/resources, :37 -DskipTests ; ls argos-server/src/main/resources/META-INF -> absent ; e2e/playwright.config.ts:89 et :103
```

*Réserve* : docker compose --profile idp-local --profile ia config -q passe (syntaxe seulement, copie d'audit).

*Au 20/11* : Ouvert (Faible) — Aucun test sur l'artefact empaqueté.

**Preuve d'origine**

```text
argos-server/pom.xml:28 <skipITs>true</skipITs> ; find . -name "*IT.java" -> aucun résultat ; Dockerfile:22 mvn -B package -DskipTests
```

**Impact** — Le jar/l'image réellement déployés (configuration %prod, Flyway au démarrage, chargement des ressources) ne sont jamais démarrés par un test ; les écarts entre mode test et mode prod ne sont pas détectés avant déploiement.

**Action** — Ajouter un smoke test @QuarkusIntegrationTest (santé, /api/v1/system/info, 401 sans jeton) ou un test compose (docker compose up + curl) dans la future CI.

### AUD-TST-19 — Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests, Dépendances · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01

**Vérification du 21/11 (b142695)** — Pas de garde de version de JDK pour les tests.

*Changement depuis le 20/11* : Aucun changement : ni maven-enforcer ni toolchains.

```text
argos-server/pom.xml:16 maven.compiler.release=17 ; grep -n 'enforcer|toolchain' argos-server/pom.xml -> aucun ; Dockerfile:18 FROM maven:3.9-eclipse-temurin-17, :37 -DskipTests, :45 FROM eclipse-temurin:17-jre
```

*Réserve* : L'aspect « build incrémental » repose sur une mesure du mainteneur, non rejouée ici.

*Au 20/11* : Ouvert (Faible) — Pas de garde de version de JDK pour les tests.

**Preuve d'origine**

```text
Mesure communiquée par le mainteneur (non rejouée ici) : premier `mvn verify` sans clean -> 0 test lancé, « TestEngine archunit failed to discover tests », LimiteDebitMcpTest.class absente de target/test-classes (le source existe : src/test/java/io/argos/mcp/LimiteDebitMcpTest.java:27) ; `mvn clean verify` -> 416 tests, BUILD SUCCESS. pom.xml:16 maven.compiler.release=17 ; Dockerfile:25 eclipse-temurin:17-jre ; aucun maven-enforcer ni toolchains dans pom.xml.
```

**Impact** — Un build local peut échouer ou, pire, passer sur des classes de test périmées. Les tests passent sur JDK 24 alors que l'image tourne sur JRE 17 : une différence de comportement de la JVM (TLS, HttpClient, GC) n'est pas couverte. L'échec observé était bruyant (engine en erreur), donc pas silencieux.

**Action** — En CI : toujours `mvn -B clean verify` sur un JDK 17 (setup-java 17, ou maven-toolchains). Faire écrire l'IDE dans un répertoire distinct de target/ (ex. VS Code Java : bin/). Ajouter maven-enforcer (requireJavaVersion [17,18) en CI).

### AUD-TST-20 — Campagne E2E principale (34 tests, 14 fichiers) rejouable seulement sur un poste complet, jamais automatisée et sans preuve de passage depuis le 19/11 ; reuseExistingServer: true réutilise sans contrôle un serveur ou un Vite déjà lancés

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : M · **Sprint** : 11
- **Dépend de** : AUD-TST-01
- **Bloque** : AUD-TST-23

**Vérification du 21/11 (b142695)** — Campagne principale toujours manuelle, et non rejouée (ou non consignée) après les lots 20 à 23 et la PR #28.

*Changement depuis le 20/11* : Configuration inchangée : 34 tests dans 14 fichiers (3 préparations, 18 portail web, 1 construction desktop, 9 desktop, 3 MCP), serveur par mvn quarkus:dev, IHM par Vite, reuseExistingServer: true pour les deux. La PR #28 ne touche pas e2e/ : la campagne ne teste ni l'image ni l'IHM servie par Quarkus. Fait nouveau : aucune exécution n'est consignée depuis le rapport du 2026-11-19 (f433989, antérieur à 54add78), alors que les 110 commits du delta modifient le code exercé et 16 fichiers d'e2e/ (f7dd640, 974e077 : specs et support). Les 30 parcours n'ont donc aucune preuve de passage à b142695.

```text
e2e/playwright.config.ts:89-106 (quarkus:dev, Vite, reuseExistingServer: true) ; npx playwright test --list -> « Total: 34 tests in 14 files » ; e2e/resultats/chaine-automatisee/rapport.json (startTime 2026-11-19T20:02, 34 attendus, 0 échec, 392 s ; git log -> f433989) ; git log 54add78..HEAD -- e2e/tests e2e/desktop e2e/support -> f7dd640, 974e077 ; git show --stat e7ed7f3 -> aucun fichier e2e/
```

*Réserve* : Le mainteneur peut avoir rejoué la campagne sans versionner de rapport ; seul l'absence de trace est constatée.

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
e2e/playwright.config.ts:89-100 (serveur par 'mvn … quarkus:dev', reuseExistingServer: true, variables SANS_COLLECTE_EXTERNE et CHAINE_SIMULEE passées seulement au serveur que Playwright démarre) et :103-106 (Vite, reuseExistingServer: true) ; e2e/README.md « Lancer » (prérequis : Docker, JDK 17, Maven 3.9 avec accès aux dépôts, binaire Electron 45.3.2, Chromium révision 1228) ; e2e/support/electron.ts:20-31 (binaire Electron local, échec explicite s'il manque) ; npx playwright test --list -> « Total: 34 tests in 14 files » (30 parcours + 3 préparations + 1 construction) ; dernier rapport de référence e2e/resultats/chaine-automatisee/rapport.json : 34 attendus, 0 échec, 391 s, 2026-11-19 ; application.properties:46, :54, :134, :223, :246 (tâches planifiées actives par défaut, cadence 1 h pour la synchronisation des avis)
```

**Impact** — Les parcours les plus proches de l'usage réel (connexion OIDC web et desktop, notifications du poste, chaîne de veille automatisée, protocole MCP) ne protègent que lorsqu'ils sont lancés à la main sur un poste équipé ; ils n'ont pas pu être rejoués ici (Maven Central inaccessible, binaire Electron non téléchargé). Si un serveur quarkus:dev tourne déjà sur 8090, il est réutilisé sans les variables de la campagne : ses tâches planifiées gardent leurs adresses réelles (la garde réseau ne couvre que le navigateur et Electron) et le parcours de la chaîne automatisée échoue ou mesure autre chose.

**Action** — Dans la future CI (AUD-TST-01) : job E2E sur runner Docker avec Xvfb pour Electron, binaire Electron et Chromium en cache, reuseExistingServer: !process.env.CI pour le serveur et Vite ; en local, faire vérifier par la préparation que le serveur réutilisé expose la configuration attendue (par exemple une sonde de configuration de test) ou refuser la réutilisation quand CHAINE_SIMULEE est requis.

### AUD-TST-23 — L'audit d'accessibilité n'a pas suivi les écrans livrés après le 17/11 : vue d'inventaire, actions sur les abonnements et traitement des impacts sans mesure de contraste ni revue

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests, Documentation · **Effort** : M · **Sprint** : 11
- **Dépend de** : AUD-TST-20

**Vérification du 21/11 (b142695)** — Écrans livrés après le 17/11 toujours sans mesure de contraste.

*Changement depuis le 20/11* : Aucun changement : le rapport d'accessibilité est inchangé depuis le 17/11 ; inventaire.spec.ts et chaine-automatisee.spec.ts n'utilisent toujours pas e2e/support/mesures.ts.

```text
docs/architecture/quality/audit-accessibilite-mises-en-page.md:13-14 (hors périmètre, date 2026-11-17), git diff 54add78 HEAD sur ce fichier -> vide ; grep -l 'mesures|contraste' e2e/tests/*.ts e2e/desktop/*.ts -> seul e2e/tests/veille.spec.ts
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
docs/architecture/quality/audit-accessibilite-mises-en-page.md:13 (hors périmètre : écrans autres que les mises en page) et :14 (date 2026-11-17) ; argos-ui/src/views/InventaireView.vue créé par f5fff1f (2026-11-19 : tableaux, pagination, région live) ; fea6eca (2026-11-19, actions suspendre/réactiver/supprimer dans VeilleView.vue) ; traitement des impacts dans AlertesView.vue (automatiser-chaine-veille/tasks.md:65, tâche 3.5) ; les mesures de contraste e2e/support/mesures.ts ne sont utilisées que par e2e/accessibilite/ et e2e/tests/veille.spec.ts (0 occurrence de « mesures » ou « contraste » dans e2e/tests/inventaire.spec.ts et chaine-automatisee.spec.ts), qui vérifient seulement clavier, focus et annonce.
```

**Impact** — Les seuls écrans de formulaire et de tableau de l'application échappent au référentiel RGAA retenu (décision Q-4) ; la conclusion de l'audit (« avant de déclarer la conformité RGAA de l'application ») s'éloigne.

**Action** — Étendre le rapport (ou en ouvrir un second) aux vues Inventaire, Alertes et aux actions de Ma veille, en réutilisant e2e/support/mesures.ts dans leurs parcours.

### AUD-TST-25 — PR #28 : quatre chemins publics ajoutés à la politique d'accès HTTP (/, /index.html, /assets/*, /licences-polices.txt) sans aucun test, ni de l'ouverture ni du maintien du 401 sur l'API

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 6
- **Nouveau** le 21/11

**Preuve**

```text
argos-server/src/main/resources/application.properties:294 quarkus.http.auth.permission.public.paths=…,/,/index.html,/assets/*,/licences-polices.txt (e7ed7f3) ; git show --stat e7ed7f3 -> aucun fichier de test ; grep -rnE '"/index.html"|"/assets|licences-polices|get\("/"\)' argos-server/src/test/java -> 0 ; api/comptes/AuthentificationTest.java:16 Javadoc « authentification obligatoire, sauf santé et identité du serveur » et :21-27 (401 sur 6 chemins d'API seulement) ; ls argos-server/src/main/resources/META-INF -> absent (l'IHM n'est copiée que dans l'étage Docker, Dockerfile:36) ; e2e/playwright.config.ts:103 (IHM servie par Vite, pas par Quarkus).
```

**Impact** — La PR corrige à la main un défaut découvert en recette (page servie répondant 401, IHM inutilisable) ; aucun test n'empêche son retour, ni n'alerterait si un futur élargissement des chemins publics (motif /assets/* ou nouvelle entrée) exposait une route de données. La politique d'accès est la barrière globale du 401 (voir AUD-TST-05).

**Action** — Ajouter à AuthentificationTest un test paramétré des chemins publics (réponse différente de 401 sans session, avec une ressource statique de test sous src/test/resources/META-INF/resources) et un test qui vérifie qu'aucune route /api/ ni /q/ autre que la santé ne figure dans quarkus.http.auth.permission.public.paths ; mettre à jour la Javadoc ACC-01. Compléter par le smoke test sur l'image (AUD-TST-12).

### AUD-TST-26 — argos-desktop sans vérification de types : check:syntax ne contrôle que la syntaxe ; un tsc --checkJs non strict relève 8 erreurs, dont 2 annotations JSDoc invalides sur les dépendances des canaux IPC

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 1
- **Nouveau** le 21/11

**Preuve**

```text
argos-desktop/package.json « check »: npm run check:syntax && npm test (aucun tsconfig ni typecheck) ; scripts/verifier-syntaxe.mjs ; build-tests/argos-desktop : npx tsc --noEmit --allowJs --checkJs --strict false --module nodenext --types node --skipLibCheck src/*.js -> 8 erreurs : src/canaux.js:57 et :60 TS2694 (ReturnType<import('./poste').creerPoste> au lieu de ReturnType<typeof import(…)>), src/connexion.js:149-151 TS2339 (type object trop large), src/main.js:27 TS2339 (champ argos absent du package.json, renseigné seulement à l'emballage).
```

**Impact** — Les annotations JSDoc, seule documentation des contrats entre main, canaux, poste et connexion OIDC, ne sont pas vérifiées et sont déjà fausses par endroits ; une erreur de type sur ces chemins (IPC, CSP) ne serait vue qu'à l'exécution.

**Action** — Ajouter un tsconfig (checkJs, strict progressif) et un script typecheck à argos-desktop, inclus dans « check » ; corriger les 8 erreurs (typeof import, typedef de l'écoute, déclaration du champ argos).

### AUD-PER-05 — Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 10

**Vérification du 21/11 (b142695)** — Le radar charge toujours, pour chaque espace, les composants de tous les espaces avant de filtrer en Java, et il lit le calendrier composant par composant.

*Changement depuis le 20/11* : RadarProjets.java, CalendrierEndoflife.java et RappelsRadar.java : renommages et découpage (lot21, lot22), sans changement des lectures.

```text
argos-server/src/main/java/io/argos/impact/RadarProjets.java:131 (boucle par espace), :144-150 (finDeSupport puis enregistrerDette par composant), :252-253 (componentsMatching(…, Optional.empty()).stream().filter(espace)) et :190-191 ; argos-server/src/main/java/io/argos/inventory/InventoryQueries.java:330 (componentsMatching(Collection<String>, Optional<UUID> projectId), sans paramètre d'espace) ; argos-server/src/main/java/io/argos/releaseradar/CalendrierEndoflife.java:75, :119 (referentiel.technologies) et :133 (SELECT … FROM cycle_vie) ; argos-server/src/main/java/io/argos/alerting/RappelsRadar.java:136-137 (espace × jours × echeancesTouchant).
```

*Au 20/11* : Ouvert (Faible) — Le radar charge toujours les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/RadarProjets.java:252-253 « return inventaires.componentsMatching(parCle.keySet(), Optional.empty()).stream().filter(m -> espace.equals(m.workspaceId())).toList(); » ; :131 boucle par espace ; :148 calendrier.finDeSupport(...) par composant -> argos-server/src/main/java/io/argos/releaseradar/CalendrierEndoflife.java:119 (referentiel.technologies) + :127 (requête cycle_vie), donc 2 requêtes par composant ; :150 enregistrerDette = 1 connexion + 1 INSERT par dette ; argos-server/src/main/java/io/argos/alerting/RappelsRadar.java:114-117 : espace × jours de rappel (jusqu'à 10) × echeancesTouchant, qui rappelle composants().
```

**Impact** — Le coût est en O(espaces × composants de toute la plateforme correspondant aux clés), auquel s'ajoutent 2 requêtes par composant et jusqu'à 10 rescans par espace pour les rappels. Sur le NAS mono-utilisateur, c'est négligeable. En ligne, avec des espaces nombreux suivant les mêmes technologies (spring, node…), la tâche quotidienne grossit de façon quadratique.

**Action** — Ajouter un paramètre workspaceId à componentsMatching (filtre SQL p.workspace_id = ?) ; charger le calendrier (cycles des produits suivis) une fois par espace, puis évaluer en mémoire ; insérer les dettes par lot ; calculer les échéances de tous les jours de rappel en une passe (intervalle [min, max]).

### AUD-PER-07 — Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 10

**Vérification du 21/11 (b142695)** — La sélection est toujours matérialisée, classée et comptée en entier à chaque page.

*Changement depuis le 20/11* : RechercheVeille.java : renommages (lot21), même requête.

```text
argos-server/src/main/java/io/argos/impact/RechercheVeille.java:136-138 (WITH r AS MATERIALIZED … (SELECT count(*) FROM r) … LIMIT ? OFFSET ?) et :150 ; docs/architecture/quality/mesure-qs13-recherche.md (« mot présent dans tous les éléments 120 000 … p95 3 722 ») ; docs/architecture/quality/mesure-performance-api-mcp.md (« GET /api/v1/recherche … 43.8 | 63.8 », jeu de 1 000 avis).
```

*Réserve* : Les 63,8 ms de la campagne du 2026-11-20 ne portent que sur 1 000 avis et ne contredisent pas le constat.

*Au 20/11* : Ouvert (Faible) — La sélection reste matérialisée, classée et comptée en entier à chaque page. Les seules mesures à grand volume restent celles de QS-13 (poste de développement).

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/RechercheVeille.java:136-138 « WITH r AS MATERIALIZED (SELECT * FROM argos_rechercher_veille(...)) SELECT r.type, r.id, r.rang, (SELECT count(*) FROM r) FROM r ORDER BY r.rang DESC, ... LIMIT ? OFFSET ? » ; argos-server/src/main/resources/db/migration/V11__recherche_veille.sql:101-105 (sans mots, la branche signal renvoie tous les signaux, ts_rank sur chaque correspondance) ; docs/architecture/quality/mesure-qs13-recherche.md : « aucun filtre (fil complet) 120 000 … p95 272 ms » et « mot présent dans tous les éléments 120 000 … p95 3 722 ms » (poste de dev, 20 000 signaux).
```

**Impact** — La latence croît avec le nombre de correspondances, pas avec la taille de la page : ts_rank détoaste le vecteur de chaque ligne (toast_tuple_target=256) et le count(*) est exact. La mesure QS-13 a été prise sur un PC de développement avec 20 000 signaux. Sur le Ryzen R1600 du NAS, avec le corpus OSV réel, un mot fréquent ou le fil sans filtre dépassera la cible Q-05 de 500 ms (risque R-16 déjà identifié). OFFSET rend aussi les pages profondes coûteuses.

**Action** — Borner la sélection (par exemple LIMIT 1 000 candidats triés par date avant ts_rank, ou ts_rank seulement si le nombre de correspondances est sous un seuil) ; remplacer le total exact par un total plafonné (« plus de 1 000 ») ; fil sans mots : tri par date avec l'index signal_date/actualite_date et pagination par curseur (keyset) ; refaire la mesure QS-13 sur le NAS avec un corpus OSV réel.

### AUD-PER-09 — Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 9
- **Bloque** : AUD-PER-29

**Vérification du 21/11 (b142695)** — Les index manquants le sont toujours.

*Changement depuis le 20/11* : Une seule migration ajoutée, V21 (project_impact_recents). Aucun index sur les clés et filtres cités.

```text
Commande « ls argos-server/src/main/resources/db/migration/ » -> V21 est la dernière migration ; « grep -n -i 'create.*index' argos-server/src/main/resources/db/migration/V*.sql | grep -i 'component_id\|ingestion_failure\|ops_event\|sync_attempt\|inventory_import_failure' » -> aucun résultat ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:51-52 (FK component_id ON DELETE SET NULL) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:283-284 (DELETE FROM component … NOT EXISTS occurrence) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:215 et :234 ; argos-server/src/main/java/io/argos/inventory/InventoryQueries.java:304.
```

*Réserve* : À l'inverse, project_impact porte maintenant 8 index, dont 2 redondants (nouveau constat AUD-PER-29).

*Au 20/11* : Ouvert (Faible) — Les index manquants le sont toujours ; SET NULL demande la même recherche par component_id que CASCADE.

**Preuve d'origine**

```text
argos-server/src/main/resources/db/migration/V5__impacts_projet.sql:11 « component_id uuid NOT NULL REFERENCES component (id) ON DELETE CASCADE » (seul index contenant component_id : project_impact_triplet_uk (signal_id, project_id, component_id), en 3e position) ; V12__radar_et_standards.sql:107 (dette_version.component_id, en 2e position dans dette_version_uk) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:72 « DELETE FROM component WHERE project_id = ? AND import_id <> ? » ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:215 et :234 (WHERE source_id = ? ORDER BY ...) sans index sur ingestion_failure(source_id) ni ops_event(source_id) (V3:55-72) ; sync_attempt(run_id) non indexé (V3:43-52) ; InventoryQueries.java:112 sans index sur inventory_import_failure(project_id).
```

**Impact** — Chaque composant retiré d'un inventaire déclenche une recherche en cascade dans project_impact et dette_version : faute d'index adapté, c'est un parcours séquentiel par ligne supprimée. Une réimportation qui retire beaucoup de composants devient alors quadratique quand project_impact grossit. Les listes d'exploitation font des parcours complets, sans gravité tant que les volumes restent faibles.

**Action** — Migration V15 : CREATE INDEX ON project_impact (component_id) ; ON dette_version (component_id) ; ON ingestion_failure (source_id, received_at) ; ON ops_event (source_id, occurred_at) ; ON sync_attempt (run_id) ; ON inventory_import_failure (project_id, received_at).

### AUD-PER-10 — Listes renvoyées en entier sans pagination : listes d'exploitation (sources rejetées, événements, échecs d'import) et impacts d'un projet (GET /projects/{id}/impacts)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 9
- **Fusionne** : AUD-PER-22

**Vérification du 21/11 (b142695)** — Les listes d'exploitation, les échecs d'import et les impacts d'un projet sont toujours renvoyés sans LIMIT.

*Changement depuis le 20/11* : Aucun changement dans SourceOperationsResource (rejected, events), SyncStore, InventoryResource /failures ni ProjectResource /impacts (renommages seulement).

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:40-55 ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:215 et :234 (ORDER BY sans LIMIT) ; argos-server/src/main/java/io/argos/api/InventoryResource.java:102-106 -> argos-server/src/main/java/io/argos/inventory/InventoryQueries.java:304 (sans LIMIT) ; argos-server/src/main/java/io/argos/api/ProjectResource.java:62-73 -> argos-server/src/main/java/io/argos/impact/ImpactQueries.java:210-219 (« ORDER BY i.created_at, i.id », sans LIMIT) ; argos-server/src/main/java/io/argos/api/SignauxAbonnements.java:117-118 (forProject sans borne pour un filtre PROJECT).
```

*Réserve* : Angle mort des audits précédents : GET /api/v1/projects/{id}/inventory (InventoryResource.java:57-64) renvoie aussi tout l'inventaire, sans page (InventoryQueries.java:511-523, « … FROM component WHERE project_id = ? ORDER BY package_key, version »), alors qu'un document peut atteindre 20 Mio (application.properties:50). L'IHM n'utilise que /summary et /components, qui est paginé (argos-ui/src/api/inventaire-modele.ts:83-92). Il n'y a toujours de purge que pour l'activité des projets (PurgeActivite) : voir AUD-SEC-03, qui absorbe l'ancien AUD-SEC-03.

*Au 20/11* : Ouvert (Faible) — Les listes d'exploitation restent sans LIMIT. Leur croissance est désormais automatique : synchronisation horaire et import quotidien.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:40-55 (rejected, events -> List complète) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:213-216 et :234 (aucun LIMIT) ; argos-server/src/main/java/io/argos/api/InventoryResource.java:50-52 -> InventoryQueries.java:112 (aucun LIMIT). À l'inverse, recherche (RechercheVeille, TAILLE_MAX=100), impacts (ImpactQueries.java:82 LIMIT/OFFSET), alertes (ConsultationAlertes.java:58 LIMIT) et actualités (ActualitesWeb.java:83 LIMIT) sont bornées.
[AUD-PER-22] argos-server/src/main/java/io/argos/api/ProjectResource.java:61-73 (renvoie List<ImpactQueries.ImpactView>, paramètre etat seul, ni page ni size) -> argos-server/src/main/java/io/argos/impact/ImpactQueries.java:218-219 (« WHERE i.project_id = ? AND i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.created_at, i.id », sans LIMIT) ; V20__etat_traitement_impacts.sql:54 (« les impacts résolus s'accumulent ») ; même lecture sans borne via un filtre d'abonnement PROJECT : api/SignauxAbonnements.java:95-96 (forProject) puis :66 (signals.find par signal). À l'inverse, GET /api/v1/signals/{id}/impacts est paginé (SignalResource.java:80-88, ImpactQueries.java:132).
```

**Impact** — Ces tables ne sont jamais purgées (voir la purge ci-dessus) : la réponse et la mémoire allouée grossissent à chaque synchronisation ou import en échec. Les endpoints sont réservés à l'exploitation, ce qui limite l'exposition.

**Action** — Ajouter limit/offset (ou un curseur sur l'horodatage), avec un maximum de 100 à 500 éléments, et trier du plus récent au plus ancien.

### AUD-PER-11 — Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 8

**Vérification du 21/11 (b142695)** — Les réponses OSV et KEV sont toujours lues en entier dans une String, sans plafond, et l'index OSV complet est relu chaque heure.

*Changement depuis le 20/11* : HttpSourceClient.java (+29 lignes) et EnrichmentHttp.java (+22 lignes) : renommages et captures précises (lot21, lot23). Le mode de lecture ne change pas.

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:63 (client.send(request, HttpResponse.BodyHandlers.ofString())) ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:59-61 (csv complet puis csv.split("\\R")) et :57 (liste de tous les FeedEntry des trois écosystèmes avant tri) ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:42-43 (ofString) ; application.properties:31 et :46.
```

*Au 20/11* : Ouvert (Faible) — Les réponses OSV et KEV sont toujours lues en entier en String, sans plafond, et l'index OSV est maintenant relu à chaque passage horaire.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:59 « client.send(request, HttpResponse.BodyHandlers.ofString()) » (index modified_id.csv complet de chaque écosystème, OsvAdvisoryFeed.java:60, puis csv.split("\\R") et une liste de FeedEntry) ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:43 (idem, catalogue KEV complet). À l'inverse, ForgeHttp (scm), FournisseurOllama (1 Mio), ClientEndoflife et RecuperateurWeb plafonnent la taille lue.
```

**Impact** — Sous un tas d'environ 768 Mo (mem_limit 1g × MaxRAMPercentage 75), l'index npm complet est conservé à la fois en String, en tableau de lignes et en liste d'entrées. Une réponse anormale (proxy, erreur) pourrait faire monter la mémoire jusqu'à l'OOM, alors que les autres clients HTTP sont bornés.

**Action** — Lire en flux (BodyHandlers.ofInputStream + BufferedReader ligne à ligne) avec un plafond configurable, sur le modèle de ForgeHttp.get(..., tailleMax).

### AUD-PER-12 — Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 3

**Vérification du 21/11 (b142695)** — Les réglages JVM, le pool et Postgres sont toujours aux valeurs par défaut. Depuis #28, l'absence de compression HTTP touche aussi l'IHM, désormais servie par Quarkus.

*Changement depuis le 20/11* : Dockerfile, application.properties et compose.yaml modifiés par l'installation locale (#28) : IHM copiée dans META-INF/resources (Dockerfile:36), chemins statiques publics (application.properties:294), service Keycloak ajouté. JAVA_OPTS, pool et réglages Postgres inchangés.

```text
Dockerfile:53 (ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 …", sans ExitOnOutOfMemoryError) ; Dockerfile:45 (eclipse-temurin:17-jre) ; compose.yaml:29 (postgres 512m), :107 (argos 1g), :124 (ollama 3g) ; commande « grep -n 'datasource.jdbc\|compression\|max-size' argos-server/src/main/resources/application.properties » -> seules les URL (:10, :15) ; Dockerfile:36 (dist de l'IHM dans META-INF/resources).
```

*Réserve* : Sans quarkus.http.enable-compression, le bundle principal part non compressé : 201,74 kB au lieu de 68,43 kB gzippé (mesure d'AUD-PER-17). Le Postgres de 512 Mio non réglé porte maintenant aussi la base Keycloak (AUD-PER-28).

*Au 20/11* : Ouvert (Faible) — Les réglages JVM, le pool et Postgres sont toujours aux valeurs par défaut.

**Preuve d'origine**

```text
Dockerfile : « ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" » ; compose.yaml : « mem_limit: ${ARGOS_MEM_LIMIT:-1g} » (argos) et « mem_limit: ${ARGOS_DB_MEM_LIMIT:-512m} » (postgres, sans réglage shared_buffers ni work_mem) ; argos-server/src/main/resources/application.properties : aucune clé quarkus.datasource.jdbc.max-size/min-size/acquisition-timeout ni quarkus.http.enable-compression (grep sans résultat).
```

**Impact** — Il reste environ 256 Mio hors tas pour le metaspace, le code cache, les piles de fils, les tampons directs de Netty et du client HTTP JDK. Un dépassement provoque l'arrêt du conteneur (OOM-kill), sans heap dump ni message JVM. Le pool par défaut (20 connexions), combiné aux connexions tenues pendant les appels distants (verrou consultatif de collecte web : StockActualites.java:127-141, connexion dédiée pendant la récupération HTTP), n'est ni dimensionné ni documenté. Sans compression HTTP, le bundle de 111 kB est servi non compressé hors reverse proxy.

**Action** — JAVA_OPTS : MaxRAMPercentage=60 à 65, -XX:+ExitOnOutOfMemoryError, éventuellement -XX:MaxMetaspaceSize ; expliciter quarkus.datasource.jdbc.max-size (ex. 10 sur le NAS) et acquisition-timeout ; activer quarkus.http.enable-compression=true ; régler shared_buffers (~128 Mo) et work_mem pour la limite de 512 Mo de Postgres.

### AUD-PER-13 — Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 8

**Vérification du 21/11 (b142695)** — La collecte d'activité lit toujours l'échéance projet par projet et appelle les forges en séquence.

*Changement depuis le 20/11* : Commit e65ebd1 « collecte d'un projet échu extraite du passage » : simple découpage de méthode.

```text
argos-server/src/main/java/io/argos/projectintel/CollecteActivite.java:69-70 (@Scheduled), :81 (boucle projets.withRepository()) et :98 (store.prochaineCollecte(projet.id()) par projet).
```

*Au 20/11* : Ouvert (Faible) — La collecte d'activité lit toujours l'échéance projet par projet et appelle les forges en séquence.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/projectintel/CollecteActivite.java:81-83 « for (ProjectDirectory.Project projet : projets.withRepository()) { ... store.prochaineCollecte(projet.id()); » (1 requête par projet, alors que l'index activite_collecte_prochaine existe : V14:47) ; argos.scm.activite.pages-max=10 et argos.sources.http-timeout=PT30S (application.properties).
```

**Impact** — Chaque passage horaire charge tous les projets à dépôt et interroge la base pour chacun, puis enchaîne jusqu'à 10 pages × 30 s par dépôt. Le chevauchement est évité (SKIP), mais en ligne la durée d'un passage devient proportionnelle au nombre total de projets.

**Action** — Sélectionner en SQL les seuls projets échus (JOIN activite_collecte WHERE prochaine_collecte <= now() ORDER BY prochaine_collecte LIMIT n), borner la durée d'un passage comme CollecteWeb (duree-max-passage), et paralléliser modérément par forge.

### AUD-PER-19 — L'import quotidien réécrit et recorrèle chaque nuit tout l'inventaire de chaque projet à dépôt, même si la SBOM ou les manifestes n'ont pas changé

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 9
- **Bloque** : AUD-PER-20

**Vérification du 21/11 (b142695)** — Chaque nuit, l'import réécrit et recorrèle encore tout l'inventaire de chaque projet à dépôt, même sans changement de la SBOM ou des manifestes.

*Changement depuis le 20/11* : InventoryService.java, InventoryStore.java et ImportPlanifie.java : renommages et capture précise (commit 926d486). Aucune empreinte de document ajoutée.

```text
argos-server/src/main/java/io/argos/inventory/ImportPlanifie.java:52-56 (cron 03:00) et :77-80 (importerProjet pour chaque projet avecDepots) ; argos-server/src/main/java/io/argos/inventory/InventoryService.java:186-188 et :228-232 (SBOM -> IMPORTED, sans comparaison), :206-208 (manifestes -> IMPORTED), :166 (store.remplacer) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:68-85 (remplacer : seul un import sans remplacement est neutre, puis replaced.fire), :236-240 (« DO UPDATE SET purl = excluded.purl, import_id = excluded.import_id »), :321 (recorrelation_pending = true) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:102-126 ; aucune colonne d'empreinte dans argos-server/src/main/resources/db/migration/V19__depots_projet_et_occurrences.sql.
```

*Réserve* : La recorrélation nocturne réécrit en plus chaque impact et remet updated_at à l'heure de l'import (AUD-PER-25).

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/inventory/ImportPlanifie.java:52-56 (cron argos.inventory.import.cron, 03:00) et :77-80 (inventories.importerProjet pour chaque projet) ; argos-server/src/main/java/io/argos/inventory/InventoryService.java:186-188 et :230 (SBOM trouvée -> « IMPORTED » sans comparaison avec l'import précédent) et :206 (manifestes -> « IMPORTED » à chaque lecture) puis :166 store.remplacer ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:226-231 (DELETE de toutes les occurrences de l'emplacement), :236-240 (« ON CONFLICT … DO UPDATE SET purl = excluded.purl, import_id = excluded.import_id », donc une nouvelle version de ligne par composant et par nuit), :320 (« recorrelation_pending = true »), :84 (InventoryReplaced) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:101-126 (correlateProject synchrone de tous les signaux qui affectent l'inventaire, plus ceux qui portent un impact actif). Aucune empreinte de document dans V19 (repository_inventory : V19__depots_projet_et_occurrences.sql:30-43).
```

**Impact** — Chaque nuit, le coût est en O(projets × signaux affectant l'inventaire × composants correspondants), avec le N+1 d'AUD-PER-03 (3 à 5 instructions par composant), même quand rien n'a changé. La table component prend une version morte par ligne et par nuit, component_occurrence est vidée puis réécrite (autovacuum et gonflement de la table sur le NAS). Le commit ceb0f50 empêche les alertes en double, pas le travail. En multi-utilisateurs, la durée du passage croît avec le nombre total de projets, et SKIP fait sauter le passage suivant si celui-ci dépasse 24 h.

**Action** — Calculer une empreinte (SHA-256) du document lu (SBOM ou manifestes concaténés) et la stocker dans repository_inventory ; répondre « KEPT » si elle est identique, sans écrire ni publier InventoryReplaced ; dans l'upsert, n'écrire que si purl a changé (WHERE component.purl IS DISTINCT FROM excluded.purl) ; ne recorréler que les package_key dont la présence ou la version a changé (différence entre l'ancien et le nouvel ensemble).

### AUD-PER-20 — Régression de l'import d'inventaire : les composants sont insérés un par un (un aller-retour SQL par composant), alors qu'ils l'étaient par lot à l'audit initial

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 9
- **Dépend de** : AUD-PER-19

**Vérification du 21/11 (b142695)** — Les composants sont toujours insérés un par un, avec un aller-retour SQL par composant ; seule l'occurrence passe par lot.

*Changement depuis le 20/11* : InventoryStore.java : renommages (lot21). Même boucle.

```text
argos-server/src/main/java/io/argos/inventory/InventoryStore.java:236-241 (INSERT INTO component … ON CONFLICT … RETURNING id) et :251-262 (composant.executeQuery() dans la boucle sur remplacement.parsed().components()) ; :268-271 (occurrence.addBatch / executeBatch) ; application.properties:50 (document de 20 Mio au plus).
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/inventory/InventoryStore.java:235-262 (« INSERT INTO component … ON CONFLICT … RETURNING id » puis « try (ResultSet rs = composant.executeQuery()) » dans la boucle sur r.parsed().components()) ; seule l'occurrence est par lot (:268-270 addBatch/executeBatch) ; à 9d96406 : « git show 9d96406:argos-server/src/main/java/io/argos/inventory/InventoryStore.java » lignes 51-70 (ps.addBatch() puis ps.executeBatch()) ; introduit par le commit 77181e9 (volet B) ; argos.inventory.max-document-bytes=20971520.
```

**Impact** — Une SBOM npm ou Maven de plusieurs milliers de composants donne autant d'allers-retours SQL dans la transaction qui tient le verrou consultatif du projet (InventoryStore.java:167). L'import manuel et l'import planifié du même projet attendent d'autant, et le coût revient chaque nuit (AUD-PER-19). La protection Info d'AUD-PER-17 ne tient plus.

**Action** — Revenir à un INSERT par lot : soit INSERT … SELECT FROM unnest(?::text[], ?::text[], …) ON CONFLICT … RETURNING id, package_key, version, en une requête, soit addBatch puis relecture des identifiants par (project_id, package_key, version) avec « = ANY ».

### AUD-PER-26 — L'index V21 (updated_at DESC, id) ne porte ni l'espace ni l'état : pour un appelant qui a peu d'impacts actifs, « Ma veille » parcourt les impacts de tous les espaces, résolus compris (22,9 ms mesurés pour 40 000 lignes écartées, coût croissant avec la table)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 10
- **Nouveau** le 21/11

**Preuve**

```text
argos-server/src/main/resources/db/migration/V21__index_impacts_recents.sql:12-16 (« La colonne workspace_id n'est pas en tête », CREATE INDEX project_impact_recents ON project_impact (updated_at DESC, id)) ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:244-247 (« WHERE i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.updated_at DESC, i.id LIMIT ? », états actifs OUVERT et CONFIRME : EtatImpact.java:19) ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:54 (« les impacts résolus s'accumulent ») ; mesures versionnées : openspec/changes/archive/2026-11-20-optimiser-ma-veille/design.md:182 (« 40 000 lignes des autres espaces lues et écartées par le filtre sur workspace_id (Rows Removed by Filter: 40000, 38 323 tampons), soit 22,9 ms ») et :184 (ensemble d'espaces non lisibles : 335 ms avec l'index contre 175 ms sans) ; tasks.md, preuve 4.3 (5 espaces de 10 000 impacts, 50 000 au total) ; le parcours s'arrête aux 200 premières lignes retenues, ce qui suppose que l'espace de l'appelant en contienne au moins 200 actives.
```

**Impact** — La mesure de la PR #27 couvre 5 espaces et 50 000 impacts, et un appelant dont l'espace contient 10 000 impacts. Elle ne couvre pas le cas fréquent en multi-utilisateurs : un petit espace (moins de 200 impacts actifs) au milieu d'espaces volumineux. Dans ce cas, le parcours de l'index ne trouve jamais ses 200 lignes et lit toute la table project_impact, impacts RESOLU et ECARTE compris, à chaque affichage de « Ma veille » et à chaque appel MCP get_security_alerts. En extrapolant linéairement les 22,9 ms pour 40 000 lignes (poste 16 cœurs), on obtient environ 0,5 s par million d'impacts, davantage sur le Ryzen R1600 du DS1522+ (estimation, non mesurée). La cible p95 < 500 ms serait alors dépassée à partir d'un volume que plusieurs dizaines d'espaces atteignent.

**Action** — Remplacer V21 par un index partiel sur les seuls états actifs, en tête l'espace : CREATE INDEX … ON project_impact (workspace_id, updated_at DESC, id) WHERE state IN ('OUVERT','CONFIRME'). Lire par espace (UNION ALL ou LATERAL sur unnest(?) avec LIMIT 200 par branche, puis fusion triée) ; la variante LATERAL déjà mesurée l'a été sans index partiel ni filtre d'état. Refaire la mesure de la tâche 4.3 avec un espace appelant de moins de 200 impacts actifs dans une table d'au moins 500 000 lignes, dont une majorité RESOLU, sur le NAS.

### AUD-PER-27 — Pendant l'attente de reprise (jusqu'à PT5M, 5 tentatives), le run de synchronisation passe RETRYABLE et sort de l'exclusion sync_run_one_running : un lancement manuel ou l'échéance horaire le reprend pendant que le premier fil dort, puis les deux fils synchronisent la même source sur le même run

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 8
- **Nouveau** le 21/11

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:86-94 (store.markRetryable(runId, …) puis sleeper.sleep(delay) puis store.resume(runId), sans vérifier que le run est toujours à lui) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:123-128 (markRetryable : state = 'RETRYABLE') ; :79-95 (startOrResume : en l'absence de RUNNING, reprend le premier RETRYABLE, « resume(id); return Optional.of(id); ») ; :109-111 (resume : state = 'RUNNING' sans condition sur l'état courant) ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:40 (index unique sync_run_one_running … WHERE state = 'RUNNING' seulement) ; deux déclencheurs indépendants : argos-server/src/main/java/io/argos/signal/SynchronisationPlanifiee.java:47 (horaire ; SKIP ne protège que contre lui-même) et argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-63 (manuel) ; application.properties:37-39 (max-attempts 5, backoff jusqu'à PT5M).
```

**Impact** — Il suffit d'une panne transitoire d'OSV (429 ou 5xx) pendant une synchronisation : le premier fil dort, et un second fil (requête d'exploitation, ou échéance horaire si le premier est le lancement manuel) reprend le même run depuis le même curseur. Deux boucles séquentielles téléchargent alors les mêmes avis, ce qui double les appels à api.osv.dev (et le risque de 429) ainsi que l'enrichissement, la corrélation et les alertes en aval. L'enregistrement reste idempotent sous verrou (CollecteTest#col03S3CollectesConcurrentesDuMemeAvis), mais l'état du run devient incohérent : le premier fil remet RUNNING un run que l'autre a peut-être déjà terminé, et les compteurs et tentatives se mélangent. Non couvert par un test.

**Action** — Garder le run sous l'exclusion pendant l'attente : laisser RUNNING avec next_attempt_at et battement de cœur (et non RETRYABLE), ou bien faire reprendre par resume() seulement si l'état n'a pas changé (« UPDATE … SET state = 'RUNNING' WHERE id = ? AND state = 'RETRYABLE' AND next_attempt_at = ? » et contrôle du nombre de lignes). À terme, sortir l'attente du fil (re-planification, AUD-PER-01). Ajouter un test : un second synchronize() pendant l'attente doit répondre « déjà en cours ».

### AUD-PER-28 — Profil idp-local : Keycloak ajoute un conteneur de 1 Gio et partage le PostgreSQL plafonné à 512 Mio, non réglé (max_connections par défaut). La somme des plafonds atteint 5,5 Gio avec le profil ia (2,5 Gio sans) sur un DS1522+ livré avec 8 Go

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 3
- **Nouveau** le 21/11

**Preuve**

```text
compose.yaml:39-65 (service keycloak, command ["start", "--import-realm"], KC_DB_URL jdbc:postgresql://postgres:5432/keycloak, mem_limit ${ARGOS_KEYCLOAK_MEM_LIMIT:-1g}, sans KC_DB_POOL_MAX_SIZE) ; compose.yaml:24-29 (base Keycloak créée dans le même conteneur postgres par deploiement/postgres/creer-base-keycloak.sh, mem_limit 512m, sans command ni réglage shared_buffers, work_mem ni max_connections) ; compose.yaml:107 (argos 1g) et :124 (ollama 3g) ; argos-server/src/main/resources/application.properties : aucune clé quarkus.datasource.jdbc.max-size (AUD-PER-12) ; introduit par e7ed7f3 (#28).
```

**Impact** — Sur le NAS cible, les plafonds cumulés (0,5 + 1 + 1 + 3 = 5,5 Gio) laissent environ 2,5 Go au DSM et au cache de fichiers, sur 8 Go livrés. Deux bases se partagent un PostgreSQL à 512 Mio, sans réglage. Les pools par défaut (20 connexions côté Quarkus/Agroal, jusqu'à 100 côté Keycloak, valeurs par défaut des produits) peuvent dépasser le max_connections de 100 de l'image PostgreSQL lors d'un pic de connexions OIDC. « start » sans --optimized refait la construction de Keycloak à chaque démarrage, plus lent sur le Ryzen R1600 (non mesuré ; start_period de 40 s et 20 essais de 10 s, compose.yaml:59-65).

**Action** — Documenter un dimensionnement NAS (ex. : ARGOS_OLLAMA_MEM_LIMIT ou IA désactivée par défaut sur 8 Go) ; fixer KC_DB_POOL_MAX_SIZE (ex. 10), quarkus.datasource.jdbc.max-size (ex. 10) et passer à postgres un command: avec max_connections, shared_buffers (~128 Mo) et work_mem adaptés aux 512 Mio ; construire une image Keycloak optimisée (kc.sh build) et démarrer en --optimized.

### AUD-PER-29 — project_impact porte 8 index, dont 2 redondants : project_impact_signal (signal_id) et project_impact_project (project_id) sont couverts par project_impact_signal_etat et project_impact_projet_etat, ajoutés par V20 sans retirer les anciens

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 10
- **Nouveau** le 21/11
- **Dépend de** : AUD-PER-09

**Preuve**

```text
argos-server/src/main/resources/db/migration/V5__impacts_projet.sql:22 (CREATE INDEX project_impact_signal ON project_impact (signal_id)) ; argos-server/src/main/resources/db/migration/V11__recherche_veille.sql:49 (CREATE INDEX project_impact_project ON project_impact (project_id)) ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:55-56 (project_impact_projet_etat (project_id, state), project_impact_signal_etat (signal_id, state)) ; triplet_uk (signal_id, project_id, component_id) couvre aussi signal_id en tête (V5:20) ; aucun DROP INDEX dans V20 ni V21 (« grep -n -i 'drop index' argos-server/src/main/resources/db/migration/V*.sql » -> aucun résultat).
```

**Impact** — Chaque écriture de project_impact (création, réécriture nocturne d'AUD-PER-25, transitions) met à jour deux index inutiles, ce qui coûte en écritures, en WAL, en cache et en espace sur le NAS. À l'inverse, la seule clé étrangère vraiment utile, component_id, n'a pas d'index (AUD-PER-09).

**Action** — Migration V22 : DROP INDEX project_impact_signal, project_impact_project (après vérification par pg_stat_user_indexes.idx_scan sur une base de recette) ; CREATE INDEX ON project_impact (component_id) dans la même migration ; inventaire dans arc42 §8.7.

### AUD-DEP-05 — Aucune veille automatisée des dépendances (Renovate/Dependabot, CVE Maven) ; Electron 45.3.2 hors de la mineure supportée (45.4.1 publiée), Playwright 1.61.1 (1.65.1 publiée)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 1
- **Fusionne** : AUD-DEP-14

**Vérification du 21/11 (b142695)** — Toujours aucune veille automatisée ; Electron 45.3.2 sur une mineure non supportée (45.4.1 publiée le 19/11), Playwright 1.61.1 (1.65.1 publiée), Keycloak 26.7.4 alors que 26.7.5 et 26.8.1 existent.

*Changement depuis le 20/11* : Aucun : ni Renovate ni Dependabot ; versions npm inchangées (aucun package-lock.json modifié : `git diff --stat 54add78 b142695 -- '*package-lock.json'` vide). #28 ajoute une image Keycloak épinglée à 26.7.4 sans outil pour la suivre.

```text
find à la racine : ni .github/ ni renovate.json ni dependabot.yml ; argos-desktop/package.json `"electron": "^45.3.2"`, lockfile 45.3.2 ; `npm view electron time` : 45.3.3 (16/11), 45.3.4 (17/11), 45.4.1 (19/11), dist-tag latest 45.4.1 ; Context7 /websites/electronjs (electron-timelines) : « We only support the latest minor release for each stable release series » ; e2e/package.json `@playwright/test` 1.61.1, `npm view @playwright/test version` -> 1.65.1 ; compose.yaml:40 `quay.io/keycloak/keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}`, Context7 /keycloak/keycloak liste les versions 26.7.5 et 26.8.1.
```

*Réserve* : Version Keycloak la plus récente déduite de la liste des versions indexées par Context7 : le registre quay.io et l'API GitHub ne sont pas joignables depuis cet environnement.

*Au 20/11* : Ouvert (Faible) — Toujours aucune veille automatisée ; le retard d'Electron s'est creusé (45.3.2 alors que 45.3.3, 45.3.4 et 45.4.1 sont publiées).

**Preuve d'origine**

```text
Absence de .github/dependabot.yml et de renovate.json (find à la racine : aucun) ; argos-server/pom.xml:153-187 sans plugin OWASP dependency-check ni cyclonedx-maven-plugin.
[AUD-DEP-14] argos-desktop/package.json `"electron": "^45.3.2"`, package-lock.json node_modules/electron 45.3.2 ; `npm view electron version` -> 45.3.3. Context7 /websites/electronjs (electron-timelines) : « The latest three stable major versions are supported... We only support the latest minor release for each stable release series ».
```

**Impact** — Les montées de version (Quarkus LTS, Electron, images) et les CVE transitives Maven ne sont détectées que manuellement, alors que le produit est lui-même un outil de veille de vulnérabilités (ADR-0011 SBOM CycloneDX).

**Action** — Ajouter Renovate ou Dependabot (maven, npm x2, docker), générer la SBOM CycloneDX de l'image en CI et l'importer dans Argos lui-même (dogfooding).

### AUD-DEP-06 — Images Docker en tags flottants sans digest : node:22-slim, maven:3.9-eclipse-temurin-17, eclipse-temurin:17-jre, postgres:18

- **Statut** : Décision documentée · **Sévérité** : Faible · **Axes** : Dépendances, Tests · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-TST-07

**Vérification du 21/11 (b142695)** — Toujours arbitré par la décision H2 pour les images de base ; la nouvelle image Keycloak n'est pas couverte par le tableau de la décision.

*Changement depuis le 20/11* : Décision H2 inchangée (Dockerfile:4-5, synology-et-pc.md:151-161). #28 ajoute l'image quay.io/keycloak/keycloak, épinglée par version exacte (26.7.4) sans digest, absente du tableau H2.

```text
Dockerfile:4-5, :8 node:22-slim, :18 maven:3.9-eclipse-temurin-17, :45 eclipse-temurin:17-jre ; compose.yaml:15 postgres:18, :40 keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}, :114 ollama 0.12.6 ; docs/deploiement/synology-et-pc.md:153-159 (tableau sans Keycloak), :161 [DÉCISION H2].
```

*Réserve* : Le retour arrière n'est toujours pas couvert (image argos:${ARGOS_VERSION:-local}, compose.yaml:81, écrasée à chaque build). Le tableau H2 (synology-et-pc.md:153-159) n'indique ni l'image Keycloak, ni sa licence (Apache-2.0), ni comment la mettre à jour.

*Au 20/11* : Décision documentée (Faible) — Non corrigé mais arbitré explicitement dans la doc de déploiement et le Dockerfile.

**Preuve d'origine**

```text
Dockerfile:6 `FROM node:22-slim AS ui`, :16 `FROM maven:3.9-eclipse-temurin-17 AS server`, :25 `FROM eclipse-temurin:17-jre` ; compose.yaml:15 `image: postgres:18`. (Ollama est épinglé : compose.yaml:66 `ollama/ollama:${ARGOS_OLLAMA_VERSION:-0.12.6}`.)
[AUD-TST-07] grep image-name application.properties src/test/resources -> aucun résultat ; seules lignes devservices : application.properties:14 (%dev…enabled=false), 207-211 (realm Keycloak) ; compose.yaml:15 image: postgres:18
```

**Impact** — Build non reproductible : la procédure de mise à jour (doc:80-84, `docker compose up -d --build`) reconstruit avec la base du jour ; une régression d'image amont ou un changement de comportement de PostgreSQL 18.x arrive sans trace ni retour arrière possible vers l'image précédente (image locale `argos:local` écrasée).

**Action** — Épingler chaque FROM et l'image postgres par version précise + digest (`eclipse-temurin:17.0.x_y-jre@sha256:...`), laisser Renovate proposer les mises à jour, et étiqueter l'image argos par version (ARGOS_VERSION) pour permettre le retour arrière.

### AUD-DEP-08 — Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 3

**Vérification du 21/11 (b142695)** — Inchangé pour argos et ollama ; seul le nouveau service Keycloak est surveillé.

*Changement depuis le 20/11* : Le service keycloak ajouté par #28/#30 a un healthcheck (compose.yaml:66-77), mais les services argos et ollama restent sans healthcheck, le Dockerfile sans HEALTHCHECK, et aucune rotation des journaux.

```text
compose.yaml:79-107 (service argos : restart, depends_on, mem_limit, pas de healthcheck), :113-124 (ollama idem) ; `grep -n 'logging:' compose.yaml` -> aucun ; `grep -n HEALTHCHECK Dockerfile` -> aucun ; healthchecks présents seulement en :30 (postgres) et :66 (keycloak).
```

*Réserve* : Rien ne permet à un futur service (proxy, desktop) d'attendre qu'Argos soit prêt : depends_on argos: service_healthy est impossible.

*Au 20/11* : Ouvert (Faible) — Toujours aucun healthcheck pour argos/ollama ni rotation des journaux.

**Preuve d'origine**

```text
compose.yaml:35-59 (service argos : restart, depends_on, mem_limit mais pas de healthcheck), :65-76 (ollama idem) ; Dockerfile:25-32 sans HEALTHCHECK ; aucune clé `logging:` dans compose.yaml (grep).
```

**Impact** — Un Argos bloqué (JVM vivante mais /q/health/ready en échec) reste « running » pour Container Manager ; les journaux json-file croissent sans limite sur le volume du NAS.

**Action** — Ajouter un healthcheck sur /q/health/ready (outil présent dans l'image ou petite commande Java), `logging: {driver: json-file, options: {max-size: 10m, max-file: '3'}}` sur chaque service.

### AUD-DEP-15 — Client desktop distribué sans signature de code ni mise à jour automatique

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : M · **Sprint** : 11

**Vérification du 21/11 (b142695)** — Toujours ni signature de code ni mise à jour automatique ; l'absence de signature est désormais écrite dans la doc, sans décision ni restriction d'usage.

*Changement depuis le 20/11* : #28 documente l'emballage (synology-et-pc.md:39-44) et reconnaît explicitement que l'installateur n'est pas signé (« Windows SmartScreen peut demander une confirmation ») ; aucune signature ni mise à jour automatique ajoutée.

```text
argos-desktop/package.json:21-55 (nsis/AppImage/dmg sans certificat, identity, notarize ni publish) ; devDependencies electron et electron-builder seules ; `grep -riE 'autoUpdater|electron-updater' argos-desktop/src` -> aucun ; docs/deploiement/synology-et-pc.md:44 « Il n'est pas signé ».
```

*Réserve* : Une mention dans un guide n'est pas une décision documentée au sens du barème (pas d'ADR ni de [DÉCISION]).

*Au 20/11* : Ouvert (Faible) — Toujours ni signature de code ni mise à jour automatique.

**Preuve d'origine**

```text
argos-desktop/package.json, bloc build : win nsis, mac dmg, linux AppImage sans certificateFile/identity/notarize ni publish ; aucune dépendance electron-updater ; grep -iE 'autoUpdater|electron-updater' argos-desktop/src/*.js -> aucun résultat ; docs/deploiement/synology-et-pc.md:101 construction manuelle `npm run dist`.
```

**Impact** — Pour une diffusion « tous publics » : avertissements SmartScreen/Gatekeeper (macOS refuse un dmg non notarisé), authenticité de l'installeur invérifiable, et les postes restent indéfiniment sur le Chromium embarqué de leur installation (failles navigateur non corrigées).

**Action** — Signer (Authenticode, Apple Developer ID + notarisation) dans la CI, publier via electron-builder `publish` et intégrer electron-updater (vérification de signature) ; à défaut, restreindre officiellement le client desktop à l'usage personnel.

### AUD-DEP-19 — Licences : contrôle automatisé et avis livrés pour le serveur ; IHM livrée (vue, vue-router) sans avis MIT dans l'image ni l'installeur, aucun contrôle npm

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 11

**Vérification du 21/11 (b142695)** — Serveur contrôlé et avis livrés ; IHM (vue, vue-router, MIT) livrée dans l'image et l'installeur sans avis ; aucun contrôle npm automatisé ; aucun NOTICE dans argos-desktop.

*Changement depuis le 20/11* : Aucun changement : serveur conforme et contrôlé, partie npm toujours écartée par la décision H1.

```text
Dockerfile:32-34, :49-50 ; vite build rejoué (build-deps/ui-build/dist) : racine = index.html, assets/ (48 fichiers), licences-polices.txt seulement ; `ls argos-desktop` -> pas de NOTICE ; docs/deploiement/synology-et-pc.md:136 [DÉCISION H1] « les avis npm de l'IHM ne sont pas ajoutés ».
```

*Réserve* : La décision H1 ne motive pas l'obligation d'attribution MIT de vue/vue-router présents dans le bundle livré ; le contrôle serveur n'est exécuté qu'au build de l'image (AUD-DEP-22).

*Au 20/11* : Partiel (Faible) — Le serveur est conforme et contrôlé ; l'IHM livrée (vue, vue-router, MIT) part dans l'image et l'installeur sans avis, et aucun contrôle npm n'est automatisé.

**Preuve d'origine**

```text
ADR-0021:59 « La licence de chaque dépendance est enregistrée au moment de son introduction » : respecté par commentaires dans pom.xml (l.57-150) mais rien pour argos-ui/argos-desktop ; aucun fichier THIRD-PARTY/NOTICE dans le dépôt ; Dockerfile:30 copie seulement target/quarkus-app.
```

**Impact** — Les obligations d'attribution (MIT/Apache/BSD) ne sont pas matérialisées dans l'image distribuée ou l'installeur ; une dépendance transitive à licence incompatible pourrait entrer sans être vue.

**Action** — Générer en CI un rapport de licences (license-maven-plugin / cyclonedx, `npx license-checker --production`) avec liste d'autorisation conforme à ADR-0021, et l'embarquer dans l'image (THIRD-PARTY-NOTICES).

### AUD-DEP-20 — Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Dépendances, Qualité · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01

**Vérification du 21/11 (b142695)** — Règle ADR-0032 respectée dans le delta, mais toujours ni CI ni hook ni test d'empreinte pour la faire respecter.

*Changement depuis le 20/11* : Une seule migration ajoutée (V21__index_impacts_recents.sql, f3b1cfc, statut A), aucune modifiée ; toujours aucun garde mécanique.

```text
`git diff --name-status 54add78 b142695 -- argos-server/src/main/resources/db/migration` -> 1 ligne A (V21) ; .github/ absent ; aucun .husky/lefthook/pre-commit ; `grep -rliE 'checksum|immuable' argos-server/src/test scripts` -> aucun ; application.properties:22 commente encore « ADR-0032, proposé » alors que l'ADR est Accepté.
```

*Réserve* : Contrôle laissé à la revue humaine ; le garde CI dépend d'AUD-TST-01.

*Au 20/11* : Partiel (Faible) — Règle documentée et respectée dans le delta, mais rien ne la fait respecter mécaniquement (pas de CI ni de hook).

**Preuve d'origine**

```text
git log -- db/migration/V13__passerelle_ia.sql -> 4549bd7 (2026-11-16, création) puis 230f395 (2026-11-17, ajout de 'QUOTA_COMPTE' au CHECK de ia_appel.issue, l.30) ; git ls-tree -r HEAD^1 (c4a35bd, main avant la PR #7) : V13 absente, donc jamais en production. application.properties:14-17 : %dev utilise la base persistante de compose (devservices désactivé) ; :23 migrate-at-start=true. Aucune CI, aucun test ni hook ne refuse la modification d'un V*__*.sql existant.
```

**Impact** — Toute base de développement migrée entre les deux commits (base compose du poste) refuse de démarrer : somme de contrôle Flyway différente à la validation. Même règle en production : une fois le NAS en service, la réécriture d'une migration déjà fusionnée empêche Argos de démarrer, ou, après un « flyway repair », laisse un schéma divergent (ici, le CHECK sans QUOTA_COMPTE ferait échouer l'écriture du journal IA).

**Action** — Règle écrite (ADR ou CONTRIBUTING) : une migration fusionnée est immuable et toute correction passe par V(n+1). Contrôle en CI : échec si `git diff --name-status origin/main...HEAD -- argos-server/src/main/resources/db/migration` contient autre chose que des ajouts (A). Option : quarkus.flyway.validate-on-migrate explicite et test qui migre une base depuis le schéma de main.

### AUD-DEP-22 — Le script de contrôle des licences (440 lignes, refactorisé deux fois dans le delta) n'a aucun test automatisé et ne s'exécute qu'au build de l'image, hors de « mvn verify »

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01

**Vérification du 21/11 (b142695)** — Toujours aucun test automatisé du script de licences, toujours hors de « mvn verify » et sans CI.

*Changement depuis le 20/11* : Le script a été refactorisé (f90e72a, 732ecf5 ; 404 -> 440 lignes) sans ajout de test ; toujours exécuté seulement au build de l'image.

```text
`grep -rl verifier-licences-serveur` hors node_modules -> .dockerignore, Dockerfile, le script, argos-server/pom.xml, argos-server/README.md : aucun test ; `wc -l scripts/verifier-licences-serveur.mjs` -> 440 ; Dockerfile:34 seul appel ; .github/ absent. Le delta montre pourtant la pratique : scripts/verifier-nommage.test.mjs (286 lignes) teste le nouveau script de nommage.
```

*Réserve* : Deux refactorisations non testées d'un garde de build (dont le découpage des expressions SPDX) : rejouées à la main ici (AUD-DEP-23), pas par un test.

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-server/pom.xml:211-219 (profil « licences » hors du cycle mvn verify, makeBom « requiresOnline ») ; Dockerfile:34 seul appel ; scripts/verifier-licences-serveur.mjs (404 lignes) : `grep -rl verifier-licences-serveur` hors du script lui-même -> aucun test ; docs/deploiement/synology-et-pc.md:185 vérification manuelle du 19/11 (inventaire factice). .github/ absent.
```

**Impact** — Une dépendance à licence non admise introduite dans une branche n'est vue qu'au prochain `docker compose up --build` sur le NAS, où le build échoue alors en production plutôt qu'en revue ; une régression du script (expressions de ADMISES, lecture zip) n'est détectée par aucun test.

**Action** — Ajouter des tests node:test du script sur des inventaires factices (cas rejoués dans cet audit : GPL, licence absente, BOM absent, conforme), et exécuter `mvn -P licences validate` dans la CI à créer (AUD-TST-01) sur chaque PR qui touche pom.xml.

### AUD-DEP-27 — Profil idp-local sans garde sur ses variables obligatoires : mots de passe initial et d'administration Keycloak vides par défaut et seulement commentés dans .env.example, 3 variables Keycloak de compose.yaml absentes de .env.example, import du royaume exécuté une seule fois

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 3
- **Nouveau** le 21/11
- **Dépend de** : AUD-DEP-01

**Preuve**

```text
compose.yaml:58 `KC_BOOTSTRAP_ADMIN_PASSWORD: ${ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE:-}`, :59-60 ARGOS_ADMIN_EMAIL et ARGOS_ADMIN_MOT_DE_PASSE_INITIAL `:-` vides ; .env.example:25-27 ces variables commentées ; comparaison par script (variables ${…} interpolées par compose.yaml vs .env.example) : ARGOS_KEYCLOAK_VERSION, ARGOS_KEYCLOAK_ADMIN, ARGOS_KEYCLOAK_MEM_LIMIT absentes (avec ARGOS_VERSION, ARGOS_OLLAMA_URL, ARGOS_OLLAMA_KEEP_ALIVE déjà absentes) ; deploiement/keycloak/argos-realm.json:95-104 ; Context7 /keycloak/keycloak (importExport) : « If a realm already exists in the server, the import operation is skipped ».
```

**Impact** — Un premier `docker compose --profile idp-local up` lancé sans ces valeurs importe un royaume dont l'unique compte a un nom ou un mot de passe vide (comportement exact non vérifié ici, registre quay.io inaccessible), et l'import n'est jamais rejoué : il faut supprimer le royaume ou le volume pour corriger. Pas de message clair à l'exploitant.

**Action** — Utiliser `${VAR:?message}` pour ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL et ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE dans le service keycloak (l'erreur ne survient que si le profil est actif) ; compléter .env.example ; étendre le test de parité demandé par AUD-DEP-01 aux variables de compose.yaml.

## Info (47)

### AUD-ARC-01 — Graphe inter-modules acyclique (50 arêtes, plus une référence de javadoc) et intégralement conforme à la table ModuleBoundariesTest

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : graphe acyclique, intégralement conforme à ALLOWED, et les package-info concordent toujours avec la table.

*Changement depuis le 20/11* : Aucune arête ajoutée ni retirée entre 54add78 et b142695 (51 aux deux bases) ; seuls les comptes de références changent (alerting→regintel 2→4, alerting→workspace 9→7, signal→ingestion 28→29). ModuleBoundariesTest ne change que par des renommages de variables de lambdas (c→classe, a→autorisee, lot20/lot23) ; ALLOWED et les 16 package-info sont inchangés.

```text
python3 -I /home/claude/audit-maj2/scripts-arch/graphe.py argos-server/src/main/java/io/argos -> 51 arêtes, « SCC [] » ; compare.py -> « modules ALLOWED: 16 », « hors table: [] » ; git diff 54add78 HEAD -- argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java (seulement :96-97, :111-113, :131-133, :149-151, :170-172, :198-200 renommés) ; comparaison scriptée package-info « Dépendances autorisées » / ALLOWED : aucune différence ; seule référence impact→workspace : javadoc impact/RlsSession.java:14. Détail : /home/claude/audit-maj2/ARCH-graphe.json.
```

*Réserve* : Contrôle textuel ; ModuleBoundariesTest n'a pas été exécuté ici (Maven Central bloqué). La conformité Java ne couvre pas le schéma SQL (AUD-ARC-06).

*Au 20/11* : Info (Info) — La protection tient : graphe toujours acyclique et intégralement conforme à ALLOWED ; les 16 package-info concordent avec la table.

**Preuve d'origine**

```text
python3 -I /home/claude/audit-tmp/arch/graphe.py argos-server/src/main/java/io/argos -> 44 arêtes, « SCC [] » (aucun cycle) ; compare.py -> « modules ALLOWED: 16 », « hors table: [] ». La seule référence impact->workspace est une javadoc (impact/RlsSession.java:14). Les dépendances déclarées dans les 16 package-info.java sont identiques à ALLOWED (ModuleBoundariesTest.java:30-56).
```

**Impact** — Les frontières déclarées sont respectées par le code ; les sources de vérité code (ALLOWED, package-info, arc42 §5.6) concordent.

**Action** — Aucune ; conserver la règle « table d'abord, puis arc42 » (javadoc du test).

### AUD-ARC-08 — Le module api reste une couche d'exposition mince, sans accès SQL

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : api ne contient toujours aucun accès JDBC/SQL.

*Changement depuis le 20/11* : api passe à 42 fichiers / 4 499 lignes (4 389 à 54add78), sans accès SQL ajouté.

```text
grep -rnE "DataSource|prepareStatement|createStatement|EntityManager|SELECT .*FROM|javax.sql|java.sql" argos-server/src/main/java/io/argos/api -> aucun résultat ; plus grosses ressources : api/reglementaire/ReglementaireResource.java (397 l.), api/artiste/ProfilArtisteResource.java (372 l.).
```

*Réserve* : Aucune règle ArchUnit ne fige « api ne dépend pas de java.sql/javax.sql ».

*Au 20/11* : Info (Info) — La protection tient : api ne contient toujours aucun accès JDBC/SQL ; les nouvelles ressources délèguent aux modules.

**Preuve d'origine**

```text
grep -rn "DataSource|prepareStatement|createStatement|EntityManager|SELECT " api -> aucun résultat (règle arc42 §5.5-4 respectée). Plus gros contrôleur : api/RadarResource.java (245 lignes) dont la seule orchestration est le calcul d'horizon et la jointure des libellés (RadarResource.java:171-196). Contrôle d'accès en tête de chaque méthode (membre/administrateur, RadarResource.java:230-244).
```

**Impact** — Les règles métier restent dans les modules ; l'API REST et le serveur MCP peuvent partager les mêmes services.

**Action** — Aucune ; optionnellement une règle ArchUnit « api ne dépend pas de javax.sql » pour figer §5.5-4, et déplacer le calcul d'horizon de RadarResource dans releaseradar.

### AUD-ARC-13 — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : la couche mise en page n'importe que vue, les polices et ses propres fichiers (plus MentionsObligatoires) ; le processus principal desktop n'appelle toujours pas l'API.

*Changement depuis le 20/11* : Desktop : réglages fixés à l'emballage (main.js, PR #28) et renommages ; IHM : couche mise en page inchangée sur le plan des imports.

```text
grep -rh '^import' argos-ui/src/mises-en-page | sort -u -> vue, @fontsource/*, fichiers locaux, ../../contenu/MentionsObligatoires.vue ; argos-ui/tests/architecture.test.mjs présent ; argos-desktop/src/main.js:84 seul net.fetch, vers un fichier local ; connexion.js:164 serveur http local de retour OIDC.
```

*Au 20/11* : Info (Info) — La protection tient : la couche mise en page n'importe que vue, les polices et ses propres fichiers ; le processus principal desktop n'appelle pas l'API.

**Preuve d'origine**

```text
grep import argos-ui/src/mises-en-page -> uniquement ../../contenu/MentionsObligatoires.vue et le type contenu/modeles ; grep -rln api/client argos-ui/src -> views/AffichageView.vue, views/AlertesView.vue, views/StatusView.vue, contenu/suivi-poste.ts ; test argos-ui/tests/architecture.test.mjs. argos-desktop/src/main.js:72 : seul net.fetch, vers un fichier local (pas d'appel API), conforme à 05-vue-blocs.md:44.
```

**Impact** — La règle « une mise en page n'appelle pas l'API » (arc42 §5.4.1) est tenue et testée.

**Action** — Aucune.

### AUD-ARC-18 — Les quatre nouveaux sous-paquets et les nouvelles arêtes du lot réglementaire sont couverts par ModuleBoundariesTest, déclarés « table d'abord »

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : tous les sous-paquets restent sous un module de premier niveau présent dans ALLOWED et les package-info concordent avec la table.

*Changement depuis le 20/11* : Aucun paquet ajouté ni retiré entre 54add78 et b142695 ; package-info inchangés.

```text
diff des répertoires de git ls-tree -r 54add78 / HEAD sous argos-server/src/main/java/io/argos -> aucune différence ; git diff --stat 54add78 HEAD -- 'argos-server/src/main/java/io/argos/*/package-info.java' -> vide ; ModuleBoundariesTest.java:73-82, :177.
```

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
Nouveaux paquets à 54add78 (git ls-tree, comparaison avec 9d96406) : api/reglementaire, api/veille, catalog/dictionnaire, ingestion/legi, tous sous un module de premier niveau présent dans ALLOWED, donc soumis à chaqueModuleRespecteSesDependances (ModuleBoundariesTest.java:72-85) ; catalog.dictionnaire explicitement ajouté à l'exception catalog (:174) ; ingestion (Set.of(), :57) garantit que ingestion.legi reste sans dépendance métier ; regintel -> ingestion/scm/workspace et alerting -> regintel ajoutés à la table (:42-49, entrées :44 et :49), aux package-info (regintel/package-info.java:8-10) et à arc42 §5.4.3 dans le même commit 3f2fdc7 que le code ; aucun paquet de premier niveau nouveau.
```

**Impact** — La discipline « table, puis arc42, puis code » tient sur le plus gros lot du delta ; le graphe reste acyclique.

**Action** — Aucune ; le trou résiduel est la règle « tout paquet de premier niveau doit figurer dans ALLOWED » (AUD-ARC-07).

### AUD-ARC-24 — L'ouverture de l'IHM servie se limite à quatre chemins statiques ; l'API et /q/openapi restent protégés

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**
- **Nouveau** le 21/11

**Preuve**

```text
application.properties:294 chemins publics « /api/v1/system/info,/q/health,/q/health/*,/,/index.html,/assets/*,/licences-polices.txt » ; aucun point d'entrée REST sous / ou /assets (grep -rhoE '@Path("/api/v1/[^"]*")' : tout sous /api/v1) ; AuthentificationTest.java:21-27 vérifie 401 sur /api/v1/moi, /api/v1/espaces, /api/v1/invitations, /api/v1/veille, /q/openapi.
```

**Impact** — La correction d'AUD-ARC-02 n'élargit pas la surface : seules des ressources statiques sans donnée deviennent publiques.

**Action** — Aucune ; ajouter au test d'authentification un cas « GET / et /assets/* sans jeton → 200 » pour figer le comportement.

### AUD-QUA-14 — La gestion d'erreurs côté serveur est saine : aucun catch vide, aucun printStackTrace, interruptions rétablies, messages exposés assainis

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Régression partielle de la protection : aucun catch vide ni printStackTrace, interruptions rétablies, causes mieux journalisées, aucune relance sans cause nouvelle ; mais l'isolation aux frontières ne couvre plus que IllegalStateException — toute autre RuntimeException traverse désormais les boucles par source, par projet, par espace et la garde MCP (voir AUD-QUA-20 et AUD-QUA-20).

*Changement depuis le 20/11* : Lot23 superviseur (c4c8eb7, c341b49, 926d486, 13baff7, 60dc8c1, a7fbf21, f235d79, eba8b62, eda9fda, f6b93dd, 488e2fa, 22c7567, 745efb2) : les 47 catch (RuntimeException) placés aux frontières sont remplacés par 31 catch (IllegalStateException), 8 catch (IllegalStateException | QuarkusTransactionException) et 1 catch (QuarkusTransactionException) ; plus aucun catch (RuntimeException|Exception|Throwable) seul dans argos-server/src/main/java. Les causes sont mieux journalisées (LOG.xxxf(erreur, ...) ajouté dans SourceSynchronizer, PasserelleIa, CorrelationEngine, SignalRecorder).

```text
grep -rnE "printStackTrace|System\.(out|err)" argos-server/src/main/java -> 0 ; grep -rnE "\b(TODO|FIXME|XXX|HACK)\b" argos-server/src argos-ui/src argos-desktop/src scripts e2e -> 0 ; catch (InterruptedException) 10 = currentThread().interrupt() 10 ; recensement des types capturés : 54add78 -> 47 « catch (RuntimeException », b142695 -> 0 ; 31 « catch (IllegalStateException », 8 « catch (IllegalStateException | QuarkusTransactionException » ; relances sans cause : 15 à 54add78 comme à b142695 (mêmes sites, exceptions de domaine) ; 38 loggers.
```

*Réserve* : Protection régressée sur l'isolation des pannes aux frontières : nouveaux constats AUD-QUA-20 (collecte, import, tâches planifiées) et AUD-QUA-20 (garde MCP). Règle de gestion d'erreurs toujours absente d'arc42 §8 ; la convention implicite « IllegalStateException = panne d'accès aux données » n'est écrite nulle part (grep -rn "IllegalStateException\|capture précise" docs/architecture -> 0) alors que 35 des 147 « new IllegalStateException( » ne viennent pas d'une SQLException (configuration, empreinte de prompt, algorithme absent).

*Au 20/11* : Info (Info) — Les règles de gestion d'erreurs et de journalisation tiennent, y compris dans regintel, inventaire et l'IHM.

**Preuve d'origine**

```text
grep -rnE "printStackTrace|System\.(out|err)" argos-server/src/main/java -> 0 ; grep -rnE "TODO|FIXME|XXX|HACK" sur argos-server, argos-ui, argos-desktop, scripts -> 0 ; 36 catch (Runtime)Exception, tous placés aux frontières (observateurs, tâches planifiées, boucles par espace/compte) avec LOG.errorf(e, ...) et identifiant, ex. CorrelationEngine.java:79-80, RappelsRadar.java:121-122 ; InterruptedException traitée avec Thread.currentThread().interrupt() en 8 endroits (PasserelleIa.java:129-131, MoteurAlertes.java:264-267, SourceSynchronizer.java:78-79...) ; messages persistés ou renvoyés réduits au nom de classe (SourceSynchronizer.java:86-87, CanalEmail.java:35, EnvoiAlertes.java:146) ; GestionErreurs.java ne mappe que des exceptions de domaine. Journaux : 24 loggers, seuls des identifiants sont journalisés (aucun e-mail, jeton ni prompt trouvé par grep).
```

**Impact** — Robustesse correcte des traitements de fond : une erreur sur un élément n'interrompt pas les autres et laisse une trace exploitable ; pas de fuite de détails techniques vers le client.

**Action** — Conserver ces règles ; les inscrire dans arc42 §8 (gestion des erreurs et journalisation) pour qu'elles survivent aux contributions futures.

### AUD-QUA-15 — Les ressources JDBC sont systématiquement fermées (try-with-resources), y compris le cas particulier du verrou consultatif

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Fermeture systématique des ressources JDBC confirmée à b142695.

*Changement depuis le 20/11* : Tient : renommages seulement.

```text
grep -rn "getConnection()" argos-server/src/main/java | wc -l -> 130 ; grep -v "try (" -> seulement signal/web/StockActualites.java:130 (transfert explicite à Verrou).
```

*Au 20/11* : Info (Info) — Fermeture systématique des ressources JDBC confirmée à 54add78.

**Preuve d'origine**

```text
grep -rn "getConnection()" argos-server/src/main/java -> 93 appels, 92 dans un try-with-resources ; le seul autre, signal/web/StockActualites.java:127-156, ferme la connexion dans un finally sauf transfert explicite de propriété à Verrou (AutoCloseable, :57-77) qui libère le verrou et rend la connexion.
```

**Impact** — Pas de fuite de connexions du pool identifiée par lecture.

**Action** — Aucune action ; conserver le motif et le reprendre dans l'utilitaire JDBC proposé par AUD-QUA-06.

### AUD-QUA-16 — Typage TypeScript strict tenu dans argos-ui (vue-tsc sans erreur, un seul contournement)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Typage strict tenu : vue-tsc sans erreur, toujours un seul contournement.

*Changement depuis le 20/11* : Tient : aucun changement de package.json/tsconfig dans argos-ui ; argos-ui + argos-desktop passent de 8 732 à 8 816 lignes, plus gros fichier inchangé (argos-ui/src/views/RechercheView.vue, 488 l.).

```text
cd build-QUAL/ui (copie de b142695) && npx vue-tsc --noEmit -> rc=0 ; grep -rnE ": any\b|as any|as unknown|@ts-ignore|@ts-expect-error|eslint-disable" argos-ui/src argos-desktop/src -> 1 (argos-ui/src/mises-en-page/contraste.ts:45).
```

*Réserve* : La partie « taille » reste suivie par AUD-QUA-18.

*Au 20/11* : Info (Info) — Le typage strict tient ; l'affirmation « taille maîtrisée, pas de fichier dieu » ne tient plus qu'en partie.

**Preuve d'origine**

```text
Plus gros fichier Java : scm/RepositoryActivity.java (394 lignes) ; méthode la plus longue : alerting/ResumesPeriodiques.java:118 composerPour (100 lignes), puis CorrelationEngine.java:142 (80) (script /home/claude/audit-tmp/qual-scripts/longmeth.py). UI : plus gros fichier contenu/alertes-poste.ts (277 lignes), desktop src/main.js (260). argos-ui/tsconfig : « strict: true », « noUnusedLocals », « noUnusedParameters » ; grep -rnE ": any|as any|as unknown|@ts-ignore|eslint-disable" argos-ui/src argos-desktop/src -> 0. Le module pur alertes-poste.ts injecte ses dépendances (DependancesSuivi) et est testable sans DOM.
```

**Impact** — Base lisible et testable ; pas de fichier « dieu ». Seules ResumesPeriodiques.composerPour et CorrelationEngine.deterministic gagneraient à être découpées.

**Action** — Aucune action obligatoire ; découper composerPour (lecture, filtrage RLS, écriture) lors de la prochaine évolution des résumés.

### AUD-SEC-10 — Aucun secret réel versionné ni dans l'historique git

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : aucun secret réel versionné dans le delta.

*Changement depuis le 20/11* : La PR #28 ajoute deploiement/keycloak/argos-realm.json et des variables Keycloak : aucune valeur de secret, seulement des marqueurs d'environnement.

```text
deploiement/keycloak/argos-realm.json:95 et :102 (marqueurs ${ARGOS_ADMIN_EMAIL} et ${ARGOS_ADMIN_MOT_DE_PASSE_INITIAL}, remplacés à l'import) ; compose.yaml:57-60 (replis vides) ; .env.example:6 (valeur d'exemple de la base), :25-26 (lignes commentées sans valeur) ; `git log -p 54add78..HEAD | grep -E 'ghp_|github_pat_|glpat-|AKIA|BEGIN .*PRIVATE KEY|sk-'` : rien.
```

*Réserve* : Le royaume livré n'a pas de client confidentiel ; le client argos-server du royaume de test (secret de test, directAccessGrants) ne doit jamais servir hors %dev/%test.

*Au 20/11* : Info (Info) — Tient : aucun secret réel versionné ni dans l'historique du delta.

**Preuve d'origine**

```text
`git grep -nIiE '(password|secret|token|api[_-]?key)\s*[:=]'` : seulement des références d'environnement (application.properties:12,51,52,79), les valeurs de développement/test (application.properties:17,209,212 ; src/test/resources/keycloak/argos-realm.json:13,95, comptes de test) et .env.example:6 (exemple) ; `git log --all -p | grep -E 'ghp_|github_pat_|glpat-|AKIA|BEGIN .*PRIVATE KEY|sk-'` : uniquement des jeux d'essai factices des tests du filtre IA. .env et .env locaux ignorés (.gitignore, .dockerignore).
```

**Impact** — Pas de rotation nécessaire. Les secrets de test (secret-de-test, mots de passe des comptes de test) ne doivent jamais servir hors profils %dev/%test.

**Action** — Aucune ; conserver la règle « tout secret par variable d'environnement ».

### AUD-SEC-11 — Requêtes SQL paramétrées ; les seules concaténations portent sur des constantes

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient.

*Changement depuis le 20/11* : Renommages dans les *Queries, *Store et services ; aucune nouvelle construction de SQL à partir d'une saisie.

```text
workspace/EspacesService.java:325 (filtre toujours littéral, appels :199, :206, :230, :248) ; regintel/ExpertiseFiches.java:232 (affectations littérales, appels :89-190) ; impact/RechercheVeille.java:136 et :165-168 (selection() constante) ; `grep -rnE 'prepareStatement\([^)]*\+' argos-server/src/main/java` : seulement des constantes (COLONNES, VUE, SELECT, ANNULER, SANS_OBJET, COLONNES_FICHE).
```

*Au 20/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
workspace/EspacesService.java:325 `... WHERE " + filtre` : filtre toujours littéral (appels l.199, 206, 230, 248), valeur liée par setObject ; impact/RechercheVeille.java:136 selection() constante ; V11__recherche_veille.sql:102-129 `RETURN QUERY EXECUTE $q$ ... $q$ USING ...` (texte constant, saisies en paramètres) ; requête utilisateur via websearch_to_tsquery (V11:29).
```

**Impact** — Pas d'injection SQL identifiée dans les *Queries, *Store, RechercheVeille, SubscriptionService.

**Action** — Ne pas transformer lireInvitations(filtre) en API recevant un texte externe ; garder EXECUTE ... USING.

### AUD-SEC-12 — Authentification : jeton OIDC obligatoire partout, audience vérifiée, aucun mode sans authentification

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : chemins publics exacts ou limités aux ressources statiques ; /api, /mcp et /q/openapi restent sous `/*` authenticated. D'après la documentation Quarkus (Context7), un chemin sans `/*` final est une correspondance exacte et le chemin le plus long l'emporte.

*Changement depuis le 20/11* : PR #28 (e7ed7f3) : règle publique étendue à `/`, `/index.html`, `/assets/*`, `/licences-polices.txt` (fichiers statiques de l'IHM) ; le reste est inchangé.

```text
argos-server/src/main/resources/application.properties:274-277 (service, audience, auth-server-url sans défaut en prod), :294-297 (public / authentifie=/*) ; api/security/OidcCallerResolver.java (renommages seulement) ; AuthentificationTest.java présent.
```

*Réserve* : Aucun test ne fige la nouvelle règle publique (pas de test qui vérifie qu'un chemin hors /assets/* répond 401) ; les liens profonds de l'IHM (hors `/`) répondent 401 au rechargement (fonctionnel, sans risque).

*Au 20/11* : Info (Info) — Tient : règle /* authenticated, seules /api/v1/system/info et /q/health* publiques, aucun résolveur sans authentification.

**Preuve d'origine**

```text
application.properties:200-218 (application-type=service, token.audience=argos-server, auth-server-url sans valeur par défaut en prod, /* authenticated) ; seul CallerResolver de production : api/security/OidcCallerResolver.java ; UnauthenticatedCallerResolver supprimé de l'historique ; HeaderCallerResolver uniquement dans src/test ; tests AuthentificationTest (401 sur /api/v1/espaces, /q/openapi).
```

**Impact** — Socle d'authentification sain ; l'administrateur plateforme n'est désigné que par l'e-mail vérifié ARGOS_ADMIN_EMAIL à la création du compte (ComptesService).

**Action** — Aucune ; ne pas ajouter de règle permit élargie (voir AUD-ARC-02).

### AUD-SEC-13 — Autorisation par espace vérifiée dans chaque ressource REST, doublée par la RLS PostgreSQL (pas d'IDOR trouvé)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : pas d'IDOR ; les contrôles d'accès sont inchangés en nombre dans chaque fichier refactorisé.

*Changement depuis le 20/11* : Aucune route REST ajoutée ni supprimée dans le delta (`git diff 54add78 HEAD -- argos-server/src/main/java/io/argos/api | grep -E '^[+-].*@(Path|GET|POST|PUT|DELETE|PATCH)'` : rien) ; lot21 « serveur api » = renommages et découpages.

```text
Comparaison 54add78/HEAD du nombre d'appels access./limiteur.admettre/autoriser/exiger dans chaque ressource api modifiée, et de AccesRefuse/Introuvable/SET LOCAL ROLE/new Contexte/RlsSession/memberOf/contributorOf/peutExpertiser/garde.appeler dans chaque service modifié : identiques ; api/security/Access.java ; alerting/MoteurAlertes.java:205-208 (impacts filtrés par espaces du destinataire) ; alerting/ConsultationAlertes.java:56,95,115 (compte_id).
```

*Réserve* : Exception de nature différente (pas un IDOR) : AUD-SEC-22.

*Au 20/11* : Info (Info) — Tient : pas d'IDOR inter-espaces trouvé sur les routes du delta.

**Preuve d'origine**

```text
Contrôles memberOf/adminOf : RadarResource (membre/administrateur), InventoryResource.authorize, ActiviteResource.autoriser, VeilleWebResource.sourceGeree/exigerAdministrateur, SubscriptionResource (TEAM réservé à l'administrateur), EspacesService.exiger ; alertes filtrées par compte_id (ConsultationAlertes:95,119) ; RLS argos_app NOBYPASSRLS (V2:176) avec politiques V2/V6/V10/V11/V12/V14 ; hors périmètre = 404 journalisé (GestionErreurs). Signaux = données publiques (lecture par tout compte authentifié, voulu).
```

**Impact** — Pas d'accès inter-espaces identifié par remontée des flux.

**Action** — Aucune ; tout nouvel endpoint doit appeler access.* et passer par ContexteBase.

### AUD-SEC-14 — Garde SSRF de la veille web complète hors rebinding (plages, IPv6 embarquées, ports, redirections, tailles)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient hors rebinding (AUD-SEC-01).

*Changement depuis le 20/11* : Renommages seuls dans GardeAdresses (plages, IPv6 embarquées, ports) et RecuperateurWeb.

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java (ipv4Interdite, plageIpv6Refusee, ipv4Embarquee : mêmes plages, diff de renommage) ; RecuperateurWeb.java:100 ; ingestion/legi/ArchivesJorf.java:47,79 ; scm/ForgeHttp.java:46 ; ingestion/HttpSourceClient.java:39 (Redirect.NEVER).
```

*Au 20/11* : Info (Info) — Tient hors rebinding (AUD-SEC-01) ; les nouveaux flux ne visent que des origines de configuration.

**Preuve d'origine**

```text
ingestion/web/GardeAdresses.java (plages IPv4/IPv6, NAT64/6to4/mappées, ports 80/443, identifiants refusés, messages sans nom d'hôte) ; RecuperateurWeb.java:100 chaque redirection revérifiée, redirections suivies à la main (Redirect.NEVER), délai et taille bornés ; liens d'articles jamais récupérés ; sources d'avis et forges sans redirection (HttpSourceClient.java:35, ForgeHttp.java:36) et à URL de configuration uniquement ; aucun endpoint de création de projet (ProjectDirectory.create sans appelant REST), dépôt GitHub validé par regex.
```

**Impact** — Protection à conserver ; seul le défaut de AUD-SEC-01 reste ouvert.

**Action** — Ne pas assouplir ; renseigner argos.veille-web.exceptions uniquement en dev/test.

### AUD-SEC-15 — XXE neutralisé dans la lecture RSS/Atom

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient.

*Changement depuis le 20/11* : Renommages dans LecteurJorf et LecteurFlux.

```text
argos-server/src/main/java/io/argos/ingestion/legi/LecteurJorf.java:65-67, :85-86 ; tests LecteurJorfTest.java (fixtures/jorf/xxe.xml) et ingestion/web/LecteursTest.java:74 ; `grep -rlnE 'DocumentBuilderFactory|SAXParser|ObjectInputStream|activateDefaultTyping' argos-server/src/main` : rien ; setAllowDoctypes absent.
```

*Au 20/11* : Info (Info) — Tient et s'étend au nouveau parseur réglementaire.

**Preuve d'origine**

```text
ingestion/web/LecteurFlux.java (ROME 2.1.0, pom.xml:24, DOCTYPE refusé par défaut) ; test LecteursTest.java:69-74 avec `<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>` refusé ; contenu réduit en texte par Jsoup ; aucune autre analyse XML ni désérialisation Java (grep ObjectInputStream/activateDefaultTyping/DocumentBuilderFactory : rien).
```

**Impact** — Pas d'XXE ni de désérialisation dangereuse.

**Action** — Garder le test ; ne pas activer setAllowDoctypes(true).

### AUD-SEC-16 — Serveur MCP : authentification, limitation et balisage du contenu externe en place (8 outils)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : les 8 outils passent par garde.appeler.

*Changement depuis le 20/11* : Renommages (OutilsSecurite, GardeAppels, Parametres) ; lot23 a7fbf21 restreint la réponse INDISPONIBLE aux pannes d'accès aux données.

```text
argos-server/src/main/java/io/argos/mcp/OutilsSecurite.java:157,252,357,389 ; OutilsProjets.java:62 ; OutilsReferentiel.java:64,85 ; OutilsReglementaires.java:67 ; GardeAppels.java:75 (LimiteurDebit) ; application.properties:320 (connection-idle-timeout=30M).
```

*Réserve* : Sessions MCP bornées en débit par sujet, pas en nombre total (AUD-SEC-03).

*Au 20/11* : Info (Info) — Tient : authentification, limitation et balisage en place sur les 8 outils.

**Preuve d'origine**

```text
Les 7 outils (OutilsSecurite/Projets/Referentiel/Reglementaires) passent par garde.appeler ; mcp/GardeAppels.java:74 limiteur 60/min/compte ; refus journalisés ; /mcp couvert par la règle /* authenticated ; contenu externe marqué CONTENU_EXTERNE_NON_FIABLE et tronqué (BalisageExterne) ; outils en lecture seule.
```

**Impact** — Surface MCP maîtrisée ; injection indirecte vers l'assistant signalée, pas supprimable.

**Action** — Aucune.

### AUD-SEC-17 — Passerelle IA : données filtrées, sortie validée, fournisseur non pilotable par l'utilisateur

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : refus des classes non transmissibles et des secrets avant toute transmission, chevrons neutralisés, réservation de quota atomique, sortie validée par schéma et journalisée sans le texte du modèle.

*Changement depuis le 20/11* : lot22 ia-api (488e2fa, a85e80d, 22c7567, 88d3a41, 6d13eb3) : refactorisation de PasserelleIa, QuotasIa, ValidationSortie, RegistrePrompts, sans nouvelle route d'IA ; captures d'exception resserrées (IOException/InterruptedException) ; coût en BigDecimal.

```text
argos-server/src/main/java/io/argos/aigateway/PasserelleIa.java:99-105 (classes, secrets), :107-117 (quotas), :124-129 (validation), :213 (chevrons) ; QuotasIa.java:82-112 (INSERT ... ON CONFLICT ... WHERE appels < ? dans une transaction) ; ValidationSortie.java:35-56 et :62-81 ; RegistrePrompts.java:122-157 (empreinte vérifiée au démarrage) ; regintel/AnalyseIa.java:63-66 ; regintel/ExpertiseFiches.java:131 (validation par expert).
```

*Réserve* : FournisseurModele.executer ne déclare plus que IOException et InterruptedException (FournisseurModele.java:30) : une RuntimeException du client HTTP remonterait à l'appelant au lieu d'un refus INDISPONIBLE (robustesse, pas de fuite).

*Au 20/11* : Info (Info) — Tient : l'IA ne déclenche aucune action sans validation humaine.

**Preuve d'origine**

```text
aigateway/PasserelleIa.java:99 refus des classes non transmissibles, :105 refus si secret détecté, :160 chevrons neutralisés dans les données ; ValidationSortie par schéma ; URL Ollama de configuration seulement (application.properties argos.ia.ollama.url) ; analyse de corrélation (noms de projets) désactivée par défaut ; Ollama sans port publié (compose.yaml). Sujets d'e-mail nettoyés des CR/LF (CanalEmail.sujet) et e-mail en texte brut.
```

**Impact** — L'injection de prompt par un flux externe ne peut produire qu'un texte affiché comme « produit par l'IA », sans action ni outil.

**Action** — Aucune ; garder l'IA sans outils ni actions.

### AUD-SEC-18 — Client Electron durci

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient.

*Changement depuis le 20/11* : Renommages (canaux.js, poste.js, notifications.js, ui-protocol.js) ; null remplacé par undefined sans changer les tests de garde ; main.js lit apiUrl/oidcUrl dans le package.json emballé (PR #28).

```text
argos-desktop/src/main.js:104-106 (contextIsolation, nodeIntegration false, sandbox), :118-127 (openExternal http(s) seulement, navigation bloquée), :249-250 (permissions) ; canaux.js:34-36, :64 (garde sur tous les canaux) ; ui-protocol.js:16-33 (default-src 'self' couvre frame-src), :56.
```

*Réserve* : main.js:123 compare toujours `url.startsWith(DEV_URL)` en mode --dev (la doc Electron recommande de comparer l'origine analysée) ; depuisFenetre vérifie event.sender et non event.senderFrame (acceptable : la CSP interdit tout cadre étranger).

*Au 20/11* : Info (Info) — Tient ; le défaut résiduel de l'écoute locale fait l'objet de AUD-SEC-23.

**Preuve d'origine**

```text
argos-desktop/src/main.js:92-94 contextIsolation true, nodeIntegration false, sandbox true ; :107,114 openExternal seulement pour http(s), navigation hors app://argos bloquée ; :205 IPC acceptée seulement depuis la fenêtre principale ; permissions limitées aux notifications ; ui-protocol.js:51 traversée de chemin refusée et CSP stricte (script-src 'self', object-src 'none') ; preload n'expose que des valeurs simples.
```

**Impact** — Pas de vecteur d'exécution de code depuis le contenu affiché.

**Action** — Aucune ; pour le dev, comparer l'origine (new URL(url).origin === DEV_URL) plutôt que startsWith.

### AUD-SEC-19 — IHM Vue sans rendu HTML brut des contenus de veille

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : aucun v-html/innerHTML/eval.

*Changement depuis le 20/11* : Renommages dans les vues et mises en page.

```text
`grep -rnE 'v-html|innerHTML|eval\(|new Function' argos-ui/src` : rien ; argos-ui/src/views/RechercheView.vue:385-386 et contenu/MentionsObligatoires.vue:13 (urlSure).
```

*Réserve* : Toujours pas de règle de lint interdisant v-html.

*Au 20/11* : Info (Info) — Tient : aucun v-html/innerHTML/eval.

**Preuve d'origine**

```text
`grep -rnE 'v-html|innerHTML|eval\(' argos-ui/src` : aucun résultat ; liens externes filtrés par urlSure (argos-ui/src/contenu/liens.ts, http/https seulement) avec rel=noopener noreferrer (MentionsObligatoires.vue:32, AlertesView.vue:76) ; serveur réduit les flux en texte (LecteurFlux.texte).
```

**Impact** — XSS par contenus externes non identifiée.

**Action** — Interdire v-html par règle de lint.

### AUD-SEC-20 — Conteneurs et exposition réseau raisonnables ; CSRF non applicable

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient.

*Changement depuis le 20/11* : PR #28 : nouveau service keycloak publié sur 127.0.0.1 seulement, interface de gestion (9000) non publiée ; expositions existantes inchangées.

```text
Dockerfile:47,51 (utilisateur 1001) ; compose.yaml:23 (PostgreSQL 127.0.0.1), :64 (Keycloak 127.0.0.1), :106 (Argos 127.0.0.1 par défaut), :113-124 (Ollama sans port) ; application.properties:266 (CORS app://argos), :274 (jeton porteur, pas de cookie).
```

*Réserve* : Fichiers de l'application appartenant à l'utilisateur d'exécution (AUD-SEC-25) ; compte PostgreSQL partagé avec Keycloak (AUD-SEC-30).

*Au 20/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
Dockerfile:27-29 utilisateur système 1001 (non root) ; compose.yaml:23 PostgreSQL sur 127.0.0.1, :58 Argos sur 127.0.0.1 par défaut, Ollama sans port ; application.properties:192 CORS limité à app://argos en prod ; authentification par jeton porteur sans cookie (application-type=service) : pas de surface CSRF.
```

**Impact** — Surface réseau minimale par défaut.

**Action** — Aucune.

### AUD-SEC-21 — Delta du lot 4 conforme : RLS sur les suivis, sources réservées à l'administrateur et collectées après vérification des conditions, hôtes data.culture filtrés, liens externes assainis

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité, Architecture · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient.

*Changement depuis le 20/11* : Renommages dans ProfilArtisteResource, CollecteOpportunites, CorrespondanceListes, SuivisProfil ; aucune logique d'accès modifiée.

```text
argos-server/src/main/java/io/argos/api/artiste/ProfilArtisteResource.java:248,264,280,293-294 ; signal/opportunite/CollecteOpportunites.java:112,121,124 ; CorrespondanceListes.java:79 ; signal/web/ResumesIaActualites.java:87 (contrôle avant le cache) ; V15__profil_artiste.sql:31-36 ; tests architecture/SuiviIsolationTest.java et chain/ProfilArtisteTest.java présents.
```

*Au 20/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
V15__profil_artiste.sql : ENABLE ROW LEVEL SECURITY + politiques compte_id = argos_compte_courant() sur suivi_profil et suivi_rappels ; workspace/suivi/SuivisProfil.java:71,80 (ContexteBase) ; ProfilArtisteResource.java:243,259,274,287 access.operator() et :288 limiteur ; CollecteOpportunites.java:112 (conditions vérifiées), :121 (hôte admis), :124 (RecuperateurWeb + GardeAdresses) ; CorrespondanceListes.java:71-80 (http/https sans userinfo) ; ResumesIaActualites.java:87 (contrôle d'accès avant le cache partagé) ; aucune nouvelle arête inter-modules (comparaison des imports f58a68c/HEAD) ; SuiviIsolationTest ; ProfilArtisteTest.java:306-308 (403).
```

**Impact** — Aucun IDOR trouvé sur les nouvelles routes. Les opportunités sont des données publiques non cloisonnées, à dessein (GET /opportunites/{id}/resume accessible à tout compte dont le profil artiste est actif).

**Action** — Aucune.

### AUD-SEC-26 — Flux OIDC Authorization Code + PKCE de l'IHM et du desktop conforme (ADR-0033) : PKCE S256, state vérifié en temps constant, iss contrôlé, jetons en mémoire, redirections internes filtrées

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient côté clients (IHM et desktop) : PKCE S256, state en temps constant, iss contrôlé, jetons en mémoire.

*Changement depuis le 20/11* : Renommages dans oidc.ts, gestionnaire.ts, session.ts. Mais l'action « royaume de production : redirections exactes, HTTPS » n'est pas suivie par le royaume livré dans la PR #28 (AUD-SEC-29).

```text
argos-ui/src/session/oidc.ts:60 (aléa crypto), :77 (émetteur de découverte), :113-115 (state, S256), :120-131 et :150 (temps constant), :152-153 (iss), :182 (cheminInterne), :229 (bearer) ; argos-desktop/src/connexion.js:67-93.
```

*Réserve* : Le royaume livré déclare http://localhost:8088/* (joker), sslRequired=none : voir AUD-SEC-29.

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
argos-ui/src/session/oidc.ts:58-68 (aléa crypto 32 octets, S256), :74-91 (émetteur de découverte = émetteur configuré), :121-126,148 (state en temps constant), :150-153 (iss RFC 9207), :169-185 (code/state retirés de l'adresse, cheminInterne anti-redirection ouverte), :227-229 (token_type bearer) ; argos-ui/src/session/gestionnaire.ts:84 (jetons en champ privé), :257-264 (credentials omit) ; argos-ui/src/session/session.ts:50-71 (sessionStorage : demande seule, effacée avant vérification) ; argos-ui/src/api/autorisation.ts:9-32 (Bearer seulement vers /api/v1, jamais dans l'URL) ; argos-desktop/src/connexion.js:65-91 (URL d'autorisation reconstruite depuis l'émetteur configuré, PKCE S256 et redirect_uri de l'écoute exigés), :26-32 (page de retour statique, CSP default-src 'none', no-store) ; royaume de test argos-ui : implicite désactivé, directAccessGrants false, PKCE S256 imposé, redirections http://localhost:5173/* et http://127.0.0.1/argos/retour ; tests ClientIhmOidcTest.java (Keycloak réel), argos-ui/tests/oidc.test.mjs, session.test.mjs, argos-desktop/test/connexion.test.js. Pas de nonce : non requis ici (OIDC Core 3.1.2.1, nonce facultatif en flux code ; l'id_token n'est utilisé que comme id_token_hint, ADR-0033 option B ; PKCE couvre l'injection de code, RFC 9700).
```

**Impact** — Aucun jeton dans un stockage persistant, une adresse ou un journal ; pas de CSRF de connexion ni de redirection ouverte identifiés.

**Action** — Aucune ; dans le royaume de production, ne déclarer que les redirections exactes du portail (pas de joker) et imposer HTTPS sur l'émetteur.

### AUD-SEC-27 — Veille réglementaire et inventaire : archives JORF bornées sans XXE ni écriture disque, routes réglementaires et impacts doublés par la RLS, aucune route d'import de SBOM

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient.

*Changement depuis le 20/11* : Renommages dans ArchiveTar, ArchivesJorf, LecteurJorf, CycloneDxParser, RepositoryFiles.

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java:54-63 (entrées et volume bornés), :78-81 (fichier régulier .xml borné, sinon sauté) ; ArchivesJorf.java:47,79 ; LecteurJorf.java:65-67,85-86 ; api/InventoryResource.java (aucune route d'import).
```

*Réserve* : Toujours aucun test d'archive hors bornes (`grep -rln ArchiveTar argos-server/src/test` : rien).

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java:41-87 (entrées, taille par fichier 5 Mio, volume décompressé bornés ; liens ignorés ; rien écrit sur disque) ; ArchivesJorf.java:47,79 ; LecteurJorf.java:63-68,85-86 ; application.properties (argos.reglementaire.jorf.taille-max-octets, entrees-max, decompresse-max-octets) ; V16__veille_reglementaire.sql:181-220 (fonctions SECURITY DEFINER avec search_path fixé, EXECUTE révoqué de PUBLIC), :235-335 (RLS fiches, domaines, experts, historique) ; api/InventoryResource.java:25-30 (lecture seule, aucune route d'import) ; inventory/CycloneDxParser.java:51-53 et application.properties argos.inventory.max-document-bytes=20971520 (JSON seulement, taille bornée) ; scm/RepositoryFiles.java:115-136,172,214 (segments encodés, taille bornée).
```

**Impact** — Pas de XXE, de bombe d'archive, de traversée de chemin ni d'IDOR sur ces nouveaux flux.

**Action** — Aucune ; conserver le test xxe.xml et ajouter un test d'archive hors bornes si absent.

### AUD-SEC-28 — Outillage e2e confiné à la boucle locale : secrets de test seulement, sources simulées sur 127.0.0.1/::1 sans traversée, docker/git/taskkill par chemin absolu

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient.

*Changement depuis le 20/11* : Renommages (f7dd640) ; confinement inchangé.

```text
e2e/support/sources-simulees.mjs:21 (64 Kio), :33 (hors donnees/chaine refusé), :102-105 (127.0.0.1 et ::1) ; e2e/compose.yaml:16 ; e2e/support/docker.mjs:5-15 ; e2e/performance/mesurer-p95.mjs:59 (verifierHoteLocal), :158, :242 (execFileSync sans shell).
```

*Réserve* : Exception connue : AUD-SEC-24.

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
e2e/support/sources-simulees.mjs:30-35 (fichier() refuse toute sortie de donnees/chaine), :43-57 (corps borné à 64 Kio), :102-105 (écoute 127.0.0.1 et ::1) ; e2e/compose.yaml:15-16 (port 127.0.0.1:55432, tmpfs) ; e2e/support/docker.mjs:5-19 ; e2e/performance/mesurer-p95.mjs:79-84,95-96,242 (git/taskkill absolus, execFileSync sans shell), :250-251 (verifierHoteLocal sur l'API et l'émetteur) ; e2e/support/donnees.ts:53-59 (psql via execFileSync, arguments en tableau).
```

**Impact** — Les campagnes ne visent aucun hôte distant et n'exposent aucun service hors du poste ; seule réserve : AUD-SEC-24.

**Action** — Aucune.

### AUD-SEC-32 — Installation locale Keycloak (PR #28, #30) : aucun mot de passe par défaut pour l'administration Keycloak ni pour le compte initial, inscription libre désactivée, ports publiés sur 127.0.0.1, interface de gestion non publiée, règle publique Quarkus limitée aux fichiers statiques

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**
- **Nouveau** le 21/11

**Preuve**

```text
compose.yaml:57-60 (KC_BOOTSTRAP_ADMIN_PASSWORD, ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL : repli vide, aucune valeur par défaut ; d'après la documentation Keycloak 26.7.5, le nom d'administrateur d'amorçage n'est utilisé que si le mot de passe est fourni), :64 (Keycloak sur 127.0.0.1), :71 (contrôle de santé sur 9000 en interne, port non publié) ; deploiement/keycloak/argos-realm.json:4 (registrationAllowed false), :12-15 (argos-server bearerOnly, standardFlow désactivé), :34-37 (argos-ui public, directAccessGrants et implicite désactivés), :48 (PKCE S256), :66-76 (argos-mcp : flux d'appareil seul), :102-103 (mot de passe initial temporaire) ; argos-server/src/main/resources/application.properties:294-297 (chemins exacts `/`, `/index.html`, `/licences-polices.txt` et préfixe `/assets/*` seulement).
```

**Impact** — Pas d'administrateur Keycloak ni de compte à mot de passe connu tant que le .env n'est pas renseigné ; aucune API ni outil MCP rendu public par la règle de l'IHM.

**Action** — Aucune ; faire échouer le démarrage plutôt que de lancer Keycloak avec des valeurs vides (`${VAR:?message}`) pour éviter un royaume importé sans administrateur.

### AUD-TST-13 — argos-ui : npm test passe (206/206) et la vérification de types vue-tsc passe

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — npm test passe (206/206), vue-tsc passe, vite build passe.

*Changement depuis le 20/11* : Inchangé en nombre : 206 tests ; vue-tsc et vite build passent.

```text
cd build-tests/argos-ui && npm ci (4,2 s) && npm test -> « # tests 206 # pass 206 # fail 0 # skipped 0 # duration_ms 5368 » ; npm run typecheck (vue-tsc --noEmit) -> code 0 (4,1 s) ; npm run build -> code 0 (4,3 s)
```

*Au 20/11* : Info (Info) — npm test passe (206/206) et vue-tsc passe.

**Preuve d'origine**

```text
cd build-tests/argos-ui && npm ci && npm test -> « # tests 25 # pass 25 # fail 0 # skipped 0 # duration_ms 524.0763 » (real 3,1 s avec npm ci) ; npm run typecheck (vue-tsc --noEmit) -> code de sortie 0
```

**Impact** — Les tests existants sont sains et rapides ; le test d'architecture IHM (tests/architecture.test.mjs, couche mise en page sans réseau) est exécuté.

**Action** — Aucune ; les brancher dans la CI (AUD-TST-01).

### AUD-TST-14 — argos-desktop : npm test passe (54/54) et check:syntax passe

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — npm test passe (54/54) et check:syntax passe sur les 8 fichiers source.

*Changement depuis le 20/11* : Inchangé : 54 tests ; check:syntax est désormais un script dédié (scripts/verifier-syntaxe.mjs, ad468cf). Aucune vérification de types n'existe pour argos-desktop (voir AUD-TST-26).

```text
cd build-tests/argos-desktop && ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci (4,1 s) && npm test -> « # tests 54 # pass 54 # fail 0 # duration_ms 4444 » ; npm run check:syntax -> « syntaxe valide : 8 fichiers de src/ », code 0
```

*Au 20/11* : Info (Info) — npm test passe (54/54) et check:syntax passe sur les 8 fichiers source.

**Preuve d'origine**

```text
cd build-tests/argos-desktop && ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci && npm test -> « # tests 22 # pass 22 # fail 0 # duration_ms 205.05 » ; npm run check:syntax -> OK
```

**Impact** — Les modules purs du client (préférences, notifications, poste, protocole UI/CSP) sont testés et passent.

**Action** — Aucune ; utiliser npm run check dans la CI.

### AUD-TST-15 — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Aucun test désactivé ni focalisé.

*Changement depuis le 20/11* : Inchangé.

```text
grep -rnE '@Disabled|@EnabledIf|@DisabledIf|assume[A-Z]' argos-server/src/test/java -> seul api/recherche/MesurePerformanceRechercheTest.java:48 ; grep '.only(|.skip(|fixme(|todo(' dans argos-ui/tests, argos-desktop/test, e2e -> seul argos-ui/tests/polices.test.mjs:67 (skip conditionnel, non déclenché : skipped 0) ; e2e/playwright.config.ts:49-50 et e2e/accessibilite.config.ts:14-15 (forbidOnly: true, retries: 0)
```

*Au 20/11* : Info (Info) — Aucun test désactivé ni focalisé.

**Preuve d'origine**

```text
grep @Disabled|@DisabledIf|assume* src/test/java -> seul api/recherche/MesurePerformanceRechercheTest.java:48 @EnabledIfSystemProperty(named = "argos.performance", matches = "true") (campagne de 100 000 actualités, lancement documenté :45) ; grep .only(|.skip(|todo( dans argos-ui/tests et argos-desktop/test -> aucun résultat
```

**Impact** — Pas de dette de tests ignorés ; la mesure de performance Q-05 est à la demande.

**Action** — Aucune ; éventuellement planifier la campagne de performance (job nocturne).

### AUD-TST-16 — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Ports aléatoires, sources simulées, courrier simulé et planificateur désactivé tiennent.

*Changement depuis le 20/11* : Inchangé sur le fond.

```text
application.properties:20 %test.quarkus.http.test-port=0, :90 %test.quarkus.mailer.mock=true, :92 %test.quarkus.scheduler.enabled=false ; testsupport/StubServer.java:62 HttpServer.create(new InetSocketAddress("localhost", 0), 0) ; grep -rnE '@TestMethodOrder|@Order\(' src/test/java -> aucun
```

*Au 20/11* : Info (Info) — Ports aléatoires, sources simulées, courrier simulé et planificateur désactivé tiennent.

**Preuve d'origine**

```text
application.properties:20 %test.quarkus.http.test-port=0 ; testsupport/StubServer.java:53 HttpServer.create(new InetSocketAddress("localhost", 0), 0) et Javadoc :22-26 « aucun test n'appelle de service externe ni de modèle réel » ; signal/web/CollecteWebTest.java:146-153 horloge injectée (instance.horloge) ; testsupport/RecordingSleeper.java ; application.properties:83 %test.quarkus.mailer.mock=true, :85 scheduler désactivé ; aucun @TestMethodOrder/@Order
```

**Impact** — Tests reproductibles et parallélisables, sans dépendance réseau ; densité d'assertions correcte (pas d'assertTrue(true), pas de catch vide).

**Action** — Aucune.

### AUD-TST-17 — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux (MCP 42, comptes 38, alerting 35)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Couverture ciblée de la sécurité, du MCP et de l'alerting maintenue.

*Changement depuis le 20/11* : Inchangé en nombre (mcp 10/42, api/comptes 11/38, alerting 7/35, ingestion/web 3/19). Réserve : ces tests couvrent les refus et les cloisonnements, pas les chemins d'erreur imprévue modifiés au lot 23 (aucun test de l'issue INDISPONIBLE de GardeAppels, voir AUD-QUA-20).

```text
décompte grep -rhE '^\s*@(Test|ParameterizedTest|TestFactory|RepeatedTest)' par dossier ; api/comptes/CouvertureRlsTest.java:71 ; ingestion/web/GardeAdressesTest.java (9) ; grep -rn INDISPONIBLE argos-server/src/test/java/io/argos/mcp -> 0
```

*Réserve* : Les tests MCP ne vérifient plus le chemin « erreur inattendue » depuis que GardeAppels ne capture que IllegalStateException (a7fbf21).

*Au 20/11* : Info (Info) — Couverture ciblée de la sécurité, du MCP et de l'alerting maintenue et étendue.

**Preuve d'origine**

```text
mcp : 8 classes @QuarkusTest, 31 tests (AuthentificationMcpTest 5, CloisonnementMcpTest 5, ContenuExterneMcpTest 2, ContratOutilsMcpTest 5, LimiteDebitMcpTest 2, OutilsConsultationMcpTest 7, RechercheMcpTest 3, ReglementaireMcpTest 2) ; api/comptes : 9 classes, 28 tests (Cloisonnement, DefenseEnProfondeur, JournalRefus…) ; alerting : 5 classes, 28 tests ; ingestion/web : 19 tests unitaires (GardeAdressesTest 9 = SSRF) + chain/CollecteTest, ResilienceTest, EnrichmentTest
```

**Impact** — Les zones à risque (cloisonnement par espace et RLS, authentification MCP et flux d'appareil, garde d'adresses SSRF, échec SMTP, IA indisponible) sont couvertes par des scénarios reliés aux exigences.

**Action** — Aucune ; compléter par la matrice d'autorisations (AUD-TST-05).

### AUD-TST-18 — ModuleBoundariesTest couvre les 16 modules et 6 règles fines ; il est éligible à surefire (nom *Test, JUnit 5)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Frontières des 16 modules vérifiées par ArchUnit ; exécution non constatée ici.

*Changement depuis le 20/11* : Renommages seulement (paramètres de lambda) ; 16 modules, 6 règles fines. Le delta ajoute une règle ArchUnit hors de ce fichier : LectureGroupeeSignauxTest (PRF-07, avec exemple négatif vérifié).

```text
ls argos-server/src/main/java/io/argos -> 16 paquets ; architecture/ModuleBoundariesTest.java:33-60 (16 entrées), :71 @TestFactory, règles fines :92, :107, :124, :143, :163, :190 ; pom.xml:22 archunit 1.5.1
```

*Réserve* : Exécution à confirmer par le build Maven de l'orchestrateur.

*Au 20/11* : Info (Info) — Frontières des 16 modules vérifiées par ArchUnit ; exécution non constatée ici.

**Preuve d'origine**

```text
architecture/ModuleBoundariesTest.java:31-57 (16 modules, identiques aux paquets io.argos.*) ; :67-80 @TestFactory un test dynamique par module ; :88-174 mcpNeLitQueLaSecuriteDeLApi, seuleLaPasserelleConnaitLeFournisseur, signal/projectintel/catalogNEmprunteQueRlsEtErreurs ; pom.xml:145-150 archunit-junit5 1.5.1 en scope test ; surefire 3.5.3 sans <includes>/<excludes>
```

**Impact** — Les frontières documentées (arc42 §5) sont vérifiées dès qu'un mvn test est lancé ; exécution effective non constatée ici (build Maven impossible, cf. angles morts) et jamais automatisée (AUD-TST-01).

**Action** — Aucune ; voir AUD-ARC-07 pour les modules futurs.

### AUD-TST-21 — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 35 s, sans serveur ni base

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 30,8 s, sans serveur ni base.

*Changement depuis le 20/11* : Rejouée à nouveau à b142695 : 29/29.

```text
cd build-tests/e2e && npm ci && npx playwright test -c accessibilite-chrome-local.config.ts (même configuration avec channel 'chrome', Google Chrome for Testing 153.0.8010.12 du conteneur) -> « 29 passed (30.8s) » ; e2e/accessibilite.config.ts:25-32
```

*Réserve* : Navigateur différent du Chromium révision 1228 de référence ; reuseExistingServer: true aussi dans cette configuration (accessibilite.config.ts:29).

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
cd build-tests/e2e && npm ci && npx playwright test -c accessibilite-chrome-local.config.ts (copie d'audit : même configuration avec channel 'chrome', Google Chrome du conteneur au lieu du Chromium 1228 de Playwright) -> « 29 passed (34.7s) » ; e2e/accessibilite.config.ts:25-32 (Vite seul sur 127.0.0.1:5174) ; rapport de référence e2e/resultats/accessibilite/rapport-2026-11-17.json (29 attendus, 0 échec)
```

**Impact** — Contrastes (RGAA 3.2), ordre de tabulation et focus, 320 px, réduction des animations et arbre d'accessibilité des quatre mises en page sont vérifiés par une campagne légère, rejouable dans n'importe quel environnement disposant de Chromium ; c'est le meilleur candidat pour un premier job E2E en CI.

**Action** — Aucune ; la brancher en premier dans la CI (AUD-TST-01).

### AUD-TST-22 — Nouveaux tests structurants dans le delta : couverture RLS générique, contrats SCM enregistrés, migrations, pont preload et fonctions pures de la campagne de performance

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tests structurants du 20/11 toujours en place.

*Changement depuis le 20/11* : Tests toujours présents ; le delta en ajoute deux de même nature (voir AUD-TST-27).

```text
api/comptes/CouvertureRlsTest.java:71 ; chain/ScmContratEnregistreTest.java, chain/MigrationV18Test.java, chain/MigrationDepotsTest.java ; argos-desktop/test/preload.test.js (9) et chaine-poste.test.js (6) ; node --test scripts/*.test.mjs "e2e/performance/*.test.mjs" -> 31/31 (copie d'audit avec dépôt git local)
```

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
api/comptes/CouvertureRlsTest.java:71-141 (toute table accessible au rôle applicatif est sous RLS, rôle sans privilège de contournement, aucune ligne sans contexte ; requêtes sur pg_class) ; chain/ScmContratEnregistreTest.java (13 cas GitHub/GitLab enregistrés : jeton refusé, limites de débit, artefact absent) ; chain/MigrationV18Test.java, chain/MigrationDepotsTest.java ; argos-desktop/test/preload.test.js (9) et chaine-poste.test.js (6, chaîne preload → IPC → poste → notification factice) ; node --test e2e/performance/*.test.mjs -> 7/7
```

**Impact** — Les invariants de sécurité (RLS sur tout le schéma) et les contrats avec les forges sont vérifiés de façon générique, ce qui protège aussi les tables et appels futurs.

**Action** — Aucune ; réutiliser l'approche par catalogue de CouvertureRlsTest pour TestDb (AUD-TST-08).

### AUD-TST-27 — Nouveaux tests du delta : règle ArchUnit de lecture groupée des signaux avec exemple négatif auto-vérifié, comparaison de « Ma veille » à un calcul de référence, contrôle de nommage testé (31/31)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**
- **Nouveau** le 21/11

**Preuve**

```text
argos-server/src/test/java/io/argos/architecture/LectureGroupeeSignauxTest.java:40-79 (3 tests, dont laRegleDetecteUneReferenceDeMethode sur architecture/exemples/LectureUnitaireParReference.java) ; api/veille/VeilleLectureGroupeeTest.java:198-237 (contenu identique au calcul de référence en base et à la lecture unitaire) ; node --test scripts/*.test.mjs "e2e/performance/*.test.mjs" -> 31/31 (copie d'audit initialisée en dépôt git : verifier-nommage.test.mjs exige git ls-files).
```

**Impact** — La correction de performance PRF-07 est protégée contre sa régression (retour à la lecture unitaire) et son résultat fonctionnel est vérifié ; la règle se teste elle-même contre un faux positif.

**Action** — Aucune ; brancher ces commandes dans la CI (AUD-TST-01).

### AUD-PER-14 — Les tâches planifiées sont protégées contre le chevauchement et idempotentes en base

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : les 13 @Scheduled (12 classes) déclarent SKIP, et l'exclusion repose sur la base (index uniques, verrous consultatifs).

*Changement depuis le 20/11* : Aucune tâche planifiée ajoutée depuis 54add78. La phrase de l'ADR-0031 relevée le 20/11 n'a pas été corrigée (une ligne de l'ADR modifiée, pour un lien).

```text
Commande « grep -rn -A3 '@Scheduled' argos-server/src/main/java | grep -c SKIP » -> 13 ; argos-server/src/main/java/io/argos/signal/SynchronisationPlanifiee.java:47 ; argos-server/src/main/java/io/argos/inventory/ImportPlanifie.java:52-53 ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:167 (pg_advisory_xact_lock par projet) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:229-233 ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:40 (sync_run_one_running) ; docs/architecture/adr/0031-planification-quarkus-scheduler.md:88 (« la synchronisation des avis reste déclenchée par l'API »).
```

*Réserve* : Faille dans l'exclusion de la synchronisation : sync_run_one_running ne couvre que l'état RUNNING, alors que l'attente de reprise passe le run à RETRYABLE (AUD-PER-27). L'ADR-0031:88 reste obsolète.

*Au 20/11* : Info (Info) — La protection tient : les 13 @Scheduled déclarent SKIP, et l'exclusion repose sur la base (index uniques, verrous consultatifs).

**Preuve d'origine**

```text
concurrentExecution = Scheduled.ConcurrentExecution.SKIP sur les 7 @Scheduled (CollecteWeb.java:83-84, CollecteActivite.java:69-70, PurgeActivite.java:32-33, EnvoiAlertes.java:72-73, RappelsRadar.java:90-91, ResumesPeriodiques.java:70-77) ; garde-fous en base : sync_run_one_running (V3:40), alerte_immediate_uk / alerte_resume_uk (V8:36-38), alerte_radar_uk (V12:134), verrou consultatif par source web (StockActualites.java:82), réservation avec bail dans EnvoiAlertes (BAIL = 5 min, LIMIT 500).
```

**Impact** — Pas de double exécution sur une instance. Sur plusieurs instances (service en ligne), les doublons de messages sont déjà évités par les index uniques et les verrous ; seul le travail est fait en double.

**Action** — Aucune action immédiate ; avant de passer à plusieurs instances, ajouter un verrou de cluster (quarkus-quartz en mode JDBC, ou pg_try_advisory_lock en tête de chaque tâche).

### AUD-PER-15 — Les appels IA sont bornés : exécuteurs dédiés à file bornée, délais maximaux, taille de réponse plafonnée, Ollama configuré à un modèle et une requête à la fois

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : files bornées, délais, taille de réponse plafonnée, quotas.

*Changement depuis le 20/11* : Exécuteurs IA et réglages Ollama inchangés (renommages, lot22).

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:80-85 (ThreadPoolExecutor(2, 2, …, ArrayBlockingQueue<>(16), AbortPolicy)) ; argos-server/src/main/java/io/argos/projectintel/SyntheseActivite.java:49-54 (1 à 2 fils, file 8) ; argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java:38, :61, :83, :90-95 ; compose.yaml:119-120 (OLLAMA_MAX_LOADED_MODELS=1, OLLAMA_NUM_PARALLEL=1) ; argos-server/src/main/java/io/argos/regintel/AnalyseIa.java:59 et :73 ; application.properties:148 (argos.ia.delai PT60S) et :258 (propositions-max=20).
```

*Réserve* : Comme le 20/11 : la collecte réglementaire peut attendre l'IA jusqu'à environ 20 minutes (20 × PT60S) sur le fil du planificateur.

*Au 20/11* : Info (Info) — La protection tient : files bornées, délais, taille de réponse plafonnée, quotas.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:75-80 (ThreadPoolExecutor(2, 2, ..., ArrayBlockingQueue<>(16), AbortPolicy)) ; argos-server/src/main/java/io/argos/projectintel/SyntheseActivite.java:46 (1-2 fils, file 8) ; argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java:37 (TAILLE_MAX_REPONSE 1 Mio), :60 (connectTimeout ≤ 5 s), :82 (timeout argos.ia.delai=PT60S) ; compose.yaml : OLLAMA_MAX_LOADED_MODELS=1, OLLAMA_NUM_PARALLEL=1, mem_limit 3g ; quotas argos.ia.plafond-instance-jour / quota-espace-jour.
```

**Impact** — L'indisponibilité ou la lenteur du modèle sur le CPU du DS1522+ n'épuise ni les fils ni la mémoire de l'application. Les demandes au-delà de la file passent en « indisponible ».

**Action** — Aucune ; à surveiller : la somme des fils IA (MoteurAlertes 2 + SyntheseActivite 2 + autres) dépasse OLLAMA_NUM_PARALLEL=1, donc les demandes s'empilent côté Ollama jusqu'à leur délai.

### AUD-PER-16 — Clients HTTP sortants : délais de connexion et de requête configurés partout, sans redirection ; caches KEV et CERT-FR avec durée de vie

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : les 7 clients HttpClient déclarent un délai de connexion et refusent les redirections, et les caches KEV et CERT-FR gardent leur durée de vie.

*Changement depuis le 20/11* : Clients HTTP renommés et captures précises (lot21, lot23), sans changement des délais.

```text
Commande « grep -rln 'HttpClient.newBuilder' argos-server/src/main/java | xargs grep -L connectTimeout » -> aucun fichier (7 clients) ; argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:79 et :157 ; argos-server/src/main/java/io/argos/vulnintel/KevSource.java:33-34 et CertFrSource.java:43-44, :63-64 (cache-ttl PT6H).
```

*Réserve* : Comme le 20/11 : avec ofInputStream, la lecture du corps n'a pas d'échéance globale (ArchivesJorf) ; le limiteur de débit reste propre à l'instance.

*Au 20/11* : Info (Info) — La protection tient : les 7 clients HttpClient déclarent un délai de connexion et refusent les redirections (sauf la passerelle IA, locale), et les caches KEV et CERT-FR gardent leur durée de vie.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java (connectTimeout + timeout = argos.sources.http-timeout PT30S, Redirect.NEVER) ; vulnintel/EnrichmentHttp.java:36 ; scm/ForgeHttp.java:36,46 (+ taille plafonnée, gestion de quota 429/403) ; releaseradar/ClientEndoflife.java:72,82 (PT10S) ; ingestion/web/RecuperateurWeb.java:51,140 (échéance globale imposée par une minuterie, en plus du timeout) ; vulnintel/KevSource.java:64-73 et CertFrSource.java:63-90 (cache synchronisé, argos.sources.certfr.cache-ttl PT6H) ; LimiteurDebit en mémoire avec nettoyage (api/security/LimiteurDebit.java:28-76).
```

**Impact** — Aucun appel distant ne peut bloquer indéfiniment un fil. Les catalogues volumineux (KEV) ne sont pas retéléchargés à chaque signal.

**Action** — Aucune ; noter que le limiteur de débit en mémoire ne vaut que pour une seule instance (à partager, en base ou dans Redis, en cas de mise à l'échelle horizontale).

### AUD-PER-17 — Bundle de l'IHM encore léger : 201,6 kB, dont 68,4 kB gzippé (41,9 kB le 17/11)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Le bundle reste léger : 201,74 kB, 68,43 kB gzippé, sous le seuil de 300 kB.

*Changement depuis le 20/11* : Bundle stable depuis le 20/11 (+0,13 kB). Désormais servi par Quarkus (#28, Dockerfile:36). L'import par lot reste dégradé (AUD-PER-20).

```text
Commande : cp -r /home/claude/work/argos/argos-ui /home/claude/audit-maj2/build-PERF/ && npm ci --ignore-scripts && npx vite build -> « dist/assets/index-CXNaoLGl.js 201.74 kB │ gzip: 68.43 kB », « dist/assets/index-D9ZFELFh.css 31.37 kB │ gzip: 6.18 kB », 48 fichiers dans dist/assets dont 46 polices woff/woff2, dist de 1,6 Mo ; à l'audit initial : 111,25 kB / 41,87 kB.
```

*Réserve* : La compression n'est pas activée côté serveur (AUD-PER-12), donc le client télécharge les 201,74 kB. Le volet « import par lot » de la protection a régressé (AUD-PER-20).

*Au 20/11* : Info (Info) — Bundle toujours léger (201,61 kB, 68,41 kB gzippé, sous le seuil de 300 kB) ; en revanche la seconde moitié de la protection, l'import par lot, a régressé (nouveau constat AUD-PER-20).

**Preuve d'origine**

```text
Commande : cp -r argos-ui /home/claude/audit-tmp/build-perf/ && npm ci && npx vite build -> « dist/assets/index-DXBFwvE8.js 111.25 kB │ gzip: 41.87 kB », « dist/assets/index-Ds4gtZKj.css 5.18 kB » (2 dépendances d'exécution : vue, vue-router) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java (INSERT component par addBatch/executeBatch) ; argos.inventory.max-document-bytes=20971520.
```

**Impact** — Le chargement initial reste négligeable, y compris sur le réseau domestique ou en ligne ; l'archive Electron est dominée par le runtime Electron, pas par l'IHM.

**Action** — Aucune ; découper par route (import() dynamique) seulement si le bundle dépasse ~300 kB.

### AUD-PER-23 — L'empreinte du contenu normalisé évite les versions et les événements superflus : une resynchronisation horaire d'un avis inchangé ne relance ni l'enrichissement, ni la corrélation, ni les alertes

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : un avis inchangé (empreinte normalisée identique) renvoie UNCHANGED sans publier SignalChanged.

*Changement depuis le 20/11* : SignalRecorder.java : renommages (lot21, +191/−191 lignes), logique d'empreinte inchangée.

```text
argos-server/src/main/java/io/argos/signal/SignalRecorder.java:101-111 (lockCurrent, comparaison de normalized_hash, return Outcome.UNCHANGED) ; :97 et :145 (changes.fire seulement à la création ou après modification) ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:140 (UNCHANGED, IGNORED_OLDER comptés sans traitement).
```

*Réserve* : L'effet de bord sur AUD-PER-02 reste entier : retryMissing n'est toujours pas planifié.

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/signal/SignalRecorder.java:101-110 (comparaison de normalized_hash ; inchangé -> Outcome.UNCHANGED, sans changes.fire) et :144 (SignalChanged seulement après modification) ; argos-server/src/main/resources/db/migration/V18__automatisation_collecte_et_abonnements.sql:9-15 (colonnes normalized_hash, index unique signal_version_normalized_uk) ; ingestion/SourceSynchronizer.java:135 (UNCHANGED et IGNORED_OLDER comptés sans traitement) ; test cité par openspec : chain/CollecteTest#col10ContenuRevenuAUneVersionAnterieure (non exécuté ici).
```

**Impact** — La synchronisation horaire ne réécrit pas les signaux et ne remet pas en route la chaîne aval pour les avis qui n'ont pas changé. Pour les signaux antérieurs à V18, l'empreinte est calculée une fois, au premier passage, en renormalisant le contenu brut de la version courante (SignalRecorder.java:104 et :224), sans nouvelle version.

**Action** — Aucune ; garder en tête l'effet de bord sur AUD-PER-02 : un enrichissement manqué ne sera plus jamais retenté par une resynchronisation, d'où l'urgence de planifier retryMissing.

### AUD-PER-24 — La collecte JORF est bornée : archive de 50 Mio au plus, décompression en flux plafonnée à 500 Mio et 50 000 entrées, fichier XML de 5 Mio au plus, débit limité à 10 requêtes par minute

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — La protection tient : archive de 50 Mio au plus, décompression plafonnée à 500 Mio et 50 000 entrées, fichier de 5 Mio au plus, 10 requêtes par minute.

*Changement depuis le 20/11* : ArchivesJorf.java et ArchiveTar.java : renommages (lot21), mêmes bornes.

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:78 (new ArchiveTar(entreesMax, 5L * 1024 * 1024, decompresseMax)), :79 et :157 (readNBytes(max + 1)) ; application.properties:237-239 et :260.
```

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:78 (new ArchiveTar(entreesMax, 5 Mio, decompresseMax)) et :155-161 (readNBytes(max + 1), refus au-delà) ; argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java:41-80 (GZIPInputStream lu bloc par bloc, total et nombre d'entrées contrôlés, fichiers non XML sautés) ; application.properties : argos.reglementaire.jorf.taille-max-octets=52428800, decompresse-max-octets=524288000, entrees-max=50000, argos.reglementaire.debit.requetes-par-minute=10.
```

**Impact** — Une archive JORF anormale (bombe de décompression, archive géante) est refusée sans épuiser le tas d'environ 768 Mio. Réserve : l'archive compressée est conservée entière en mémoire (jusqu'à 50 Mio, plus la copie interne de readNBytes) pendant sa lecture, et la lecture du corps n'a pas d'échéance globale (AUD-PER-16).

**Action** — Aucune ; lire éventuellement l'archive directement en flux (ofInputStream -> GZIPInputStream) pour éviter le tampon de 50 Mio.

### AUD-DEP-11 — npm audit argos-desktop : 6 vulnérabilités modérées (minimatch GHSA-g3hv-75wg-m249, déni de service) via electron-builder, outil de build uniquement

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build).

*Changement depuis le 20/11* : Aucun : lockfile d'argos-desktop inchangé ; electron-builder 26.18.0 reste la dernière version publiée.

```text
Copie /home/claude/audit-maj2/build-deps/audit-argos-desktop (package.json + package-lock.json), `npm audit --json` -> {moderate:6, high:0, critical:0} : @electron/universal, app-builder-lib, dmg-builder, electron-builder, electron-builder-squirrel-windows, minimatch (GHSA-g3hv-75wg-m249, >=7.0.0 <11.0.1) ; fixAvailable electron-builder 24.13.3 (isSemVerMajor true) ; `npm view electron-builder version` -> 26.18.0.
```

*Réserve* : L'acceptation écrite du risque recommandée n'existe toujours pas.

*Au 20/11* : Info (Info) — Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build).

**Preuve d'origine**

```text
Copie dans /home/claude/audit-tmp/build-deps/argos-desktop, `npm audit --json` -> {moderate: 6, high: 0, critical: 0} ; chaîne : electron-builder 26.18.0 -> app-builder-lib -> @electron/universal -> minimatch 9.0.9 (plage vulnérable >=7.0.0 <11.0.1) ; fixAvailable proposé = electron-builder 24.13.3 (rétrogradation majeure, non pertinente).
```

**Impact** — Aucun impact en exécution : electron-builder ne sert qu'à l'empaquetage local sur un poste de confiance ; la DoS suppose des motifs glob malveillants.

**Action** — Ne pas appliquer `npm audit fix --force` ; suivre la prochaine version d'electron-builder et accepter le risque explicitement (documenté) d'ici là.

### AUD-DEP-12 — argos-ui : aucune vulnérabilité connue et dépendances à jour (Vue 3.5.45, Vite 8.3.8, vue-router 5.5.1), lockfiles v3 présents et utilisés par npm ci

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : 0 vulnérabilité ; dépendances directes à jour à une exception de un jour près (vue 3.5.46 publiée le 20/11) et TypeScript (tilde délibéré).

*Changement depuis le 20/11* : Aucun changement de versions npm de l'IHM.

```text
Copie /home/claude/audit-maj2/build-deps/audit-argos-ui, `npm audit --json` -> total 0 ; versions résolues : vue 3.5.45 (`npm view vue` 3.5.46, publiée 2026-11-20), vue-router 5.5.1, vite 8.3.8, @vitejs/plugin-vue 6.0.11, vue-tsc 3.3.14 = dernières ; typescript ~6.0.3 contre 7.0.2 ; `npm ci` + `vite build` rejoués dans build-deps/ui-build : rc=0 ; Dockerfile:10-11 `npm ci`.
```

*Au 20/11* : Info (Info) — Tient : 0 vulnérabilité, toutes les dépendances directes à la dernière version sauf TypeScript (tilde délibéré).

**Preuve d'origine**

```text
Copie /home/claude/audit-tmp/build-deps/argos-ui, `npm audit --json` -> total 0 ; `npm view <pkg> version` : vite 8.3.8, vue 3.5.45, @vitejs/plugin-vue 6.0.11, vue-tsc 3.3.14 = versions résolues dans package-lock.json (lockfileVersion 3) ; Dockerfile:7-8 `COPY ... package-lock.json` + `npm ci`. Seul écart : typescript ~6.0.3 alors que 7.0.2 est publié (choix délibéré par le tilde).
```

**Impact** — Chaîne front reproductible et saine.

**Action** — Aucune ; planifier la montée TypeScript 7 quand vue-tsc la prend en charge.

### AUD-DEP-13 — Quarkus 3.40.1.2 sur le flux LTS courant (3.40) ; Java 17 compatible jusqu'à la fin des 3.x

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : Quarkus 3.40.1.2 sur la LTS 3.40, Java 17 compatible.

*Changement depuis le 20/11* : Aucun (pom.xml inchangé dans le delta).

```text
argos-server/pom.xml:14-21 `<quarkus.platform.version>3.40.1.2</quarkus.platform.version>`, :16 release 17 ; Context7 /quarkusio/quarkusio.github.io _data/versions.yaml : version 3.40.1.1, flux maintenus 3.40, 3.33, 3.27, 3.20, 3.15, 3.8, jdk « 17+ » ; billet 3.27.6 (30/09/2026) : 3.40 est la LTS suivante.
```

*Au 20/11* : Info (Info) — Tient : Quarkus 3.40.1.2 sur la LTS 3.40, Java 17 compatible.

**Preuve d'origine**

```text
argos-server/pom.xml:14-21 `<quarkus.platform.version>3.40.1.2</quarkus.platform.version>` (commentaire : LTS jusqu'au 30/09/2027). Context7 /quarkusio/quarkusio.github.io, _data/versions.yaml : version courante 3.40.1.1, flux maintenus « 3.40, 3.33, 3.27, ... », jdk « 17+ » ; politique LTS : support 12 mois, nouvelle LTS tous les 6 mois.
```

**Impact** — Plateforme serveur à jour ; la sortie de Quarkus 4 imposera Java 21 (Dockerfile:16 et :25 à faire évoluer).

**Action** — Aucune ; anticiper le passage à Java 21 (images temurin-21) avant la fin de support de la LTS 3.40.

### AUD-DEP-16 — Conteneurisation et assemblage Compose solides : multi-étapes, JRE seul, utilisateur non root, mem_limit, restart, healthcheck PostgreSQL, ports locaux par défaut

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : multi-étapes, JRE seul, UID 1001, mem_limit, healthcheck PostgreSQL, ports locaux ; le nouveau service Keycloak respecte le même modèle.

*Changement depuis le 20/11* : Dockerfile inchangé ; compose.yaml gagne un service keycloak qui suit les mêmes règles (restart, mem_limit, port 127.0.0.1, healthcheck, depends_on postgres healthy) ; .gitattributes force LF sur les scripts montés (*.sh).

```text
Dockerfile:8/18/45 trois étapes, :47 useradd --uid 1001, :51 USER 1001, :49-50 avis 0444 ; compose.yaml:16/42/82/116 restart, :29/65/107/124 mem_limit, :30-34 et :66-77 healthchecks, :44-46 et :83-88 depends_on service_healthy, :23/:64/:106 127.0.0.1 ; .gitattributes:1 `*.sh text eol=lf` ; `docker compose -f compose.yaml config -q` et `--profile idp-local config -q` -> rc=0.
```

*Au 20/11* : Info (Info) — Tient et s'améliore : multi-étapes, JRE seul, UID 1001, mem_limit, healthcheck PostgreSQL, ports locaux.

**Preuve d'origine**

```text
Dockerfile:6/16/25 trois étapes, :27-29 `useradd --system --uid 1001` + `USER 1001` ; .dockerignore exclut **/.env ; compose.yaml:16/37/68 `restart: unless-stopped`, :27/59/76 mem_limit, :28-33 healthcheck pg_isready + :38-40 depends_on service_healthy, :22-23 et :55-58 publication sur 127.0.0.1 ; ollama sans port publié et profil opt-in. `docker compose -f compose.yaml config -q` -> rc=0.
```

**Impact** — Base d'exploitation adaptée au NAS (mémoire bornée, surface réseau minimale).

**Action** — Aucune.

### AUD-DEP-17 — Configuration par profils correcte : prod sans valeurs par défaut pour la base et l'OIDC, mailer simulé seulement en dev/test, STARTTLS exigé, aucun secret réel versionné

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé, aucun secret réel versionné.

*Changement depuis le 20/11* : Lignes inchangées ; #28 rend publics les fichiers statiques de l'IHM (application.properties:294 ajoute /, /index.html, /assets/*, /licences-polices.txt), l'API restant authentifiée (:296-297). Le royaume importé ne contient que des espaces réservés ${…}, aucun secret.

```text
application.properties:10-12 `%prod.quarkus.datasource.*=${ARGOS_DB_*}`, :277 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`, :87/:89-90 mock false en prod / true en dev-test, :82 start-tls REQUIRED, :283/:286 secret client limité à %test/%dev (valeur de test) ; `git grep` des motifs ghp_/glpat-/AKIA/clé privée à b142695 : seuls FiltreDonneesTest.java:20-24 et PasserelleIaTest.java:192 (jeux d'essai) ; deploiement/keycloak/argos-realm.json:95-104 espaces réservés ${ARGOS_ADMIN_EMAIL}/${ARGOS_ADMIN_MOT_DE_PASSE_INITIAL} ; .gitignore:17-18 exclut .env et sauvegardes/.
```

*Réserve* : Mots de passe par défaut faibles hors production applicative : compose.yaml:20/51 `${ARGOS_DB_PASSWORD:-argos}` réutilisé par Keycloak (voir AUD-SEC-30).

*Au 20/11* : Info (Info) — Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé, aucun secret réel versionné.

**Preuve d'origine**

```text
application.properties:10-12 `%prod.quarkus.datasource.*=${ARGOS_DB_*}` sans défaut, :203 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`, :80-83 mock=false en prod, :75 `start-tls=${ARGOS_SMTP_STARTTLS:REQUIRED}`, :209/:212 secret client limité à %test/%dev (valeur de test, non reproduite) ; recherche de motifs de secrets (ghp_, glpat-, AKIA, clés privées) hors tests : seules les expressions de filtrage de FiltreDonnees.java:32-33 ; .gitignore exclut .env et sauvegardes/.
```

**Impact** — Démarrage en échec franc si la base ou l'OIDC manque ; pas de fuite de secret dans le dépôt. Aucun pipeline, donc aucun secret de pipeline à gérer.

**Action** — Aucune.

### AUD-DEP-18 — Licences des dépendances livrées compatibles avec ADR-0021 (MIT/Apache-2.0/BSD) ; licences restrictives uniquement dans l'outillage de build

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : aucune dépendance copyleft forte ni non commerciale livrée ; EPL-2.0 nommée et décidée.

*Changement depuis le 20/11* : Aucun lockfile npm ni pom.xml modifié dans le delta ; le script de licences n'a reçu que des refactorisations (f90e72a découpage SPDX linéaire, 732ecf5 renommages).

```text
`git diff --stat 54add78 b142695 -- '*package-lock.json' argos-server/pom.xml` -> vide ; scripts/verifier-licences-serveur.mjs (ADMISES, SOUS_RESERVE) ; répartition des licences des lockfiles identique au 20/11 ; rejeu du script : voir AUD-DEP-23.
```

*Réserve* : L'image Keycloak (Apache-2.0) est téléchargée par l'exploitant, non redistribuée : hors périmètre ADR-0021, mais non mentionnée dans la doc des images.

*Au 20/11* : Info (Info) — Tient : aucune dépendance copyleft forte ni non commerciale livrée ; le seul copyleft faible (EPL-2.0) est nommé et décidé.

**Preuve d'origine**

```text
LICENSE : PolyForm Internal Use 1.0.0 ; ADR docs/architecture/adr/0021-double-licence.md:57 (permissives acceptées). Analyse du package-lock.json : bundle argos-ui = vue, vue-router (MIT) ; MPL-2.0 (lightningcss 1.33.0), BSD-3, ISC, 0BSD côté Vite ; argos-desktop : MIT 213, ISC 35, BlueOak-1.0.0 9, Python-2.0 1 (argparse), WTFPL 1, tous dans la chaîne electron-builder ; pom.xml:44-150 commente la licence de chaque dépendance (Apache-2.0, MIT, JDOM).
```

**Impact** — Aucune dépendance copyleft forte ni non commerciale n'entre dans le produit distribué.

**Action** — Aucune.

### AUD-DEP-23 — Le script de contrôle des licences rejoué sur des inventaires factices donne les bons codes de sortie (1 GPL ou licence absente, 2 BOM manquant, 0 conforme)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient après refactorisation : codes de sortie 1 (GPL, licence absente), 2 (BOM absent), 0 (conforme, textes copiés).

*Changement depuis le 20/11* : Script refactorisé (f90e72a, 732ecf5) ; comportement rejoué identique.

```text
Copie `git archive b142695` dans /home/claude/audit-maj2/build-deps/repo, inventaires factices dans build-deps/lic : `node scripts/verifier-licences-serveur.mjs t-bom.json/bom.json --depot lic/m2` -> rc=1 (« GPL-3.0-only » non admise, « aucune licence déclarée ») ; BOM absent -> rc=2 « Fichier CycloneDX absent » ; Apache-2.0 + MIT -> rc=0 « 2 textes de licence copiés », « Aucune licence absente ni non admise ». `node scripts/verifier-docs.mjs` -> rc=0, « Fichiers Markdown contrôlés : 102 », « Blocs Mermaid : 59 », « aucune erreur ».
```

*Réserve* : Le vrai bom.json n'a pas pu être produit (Maven Central bloqué). Le nombre de fichiers Markdown contrôlés passe de 120 à 102 et les blocs Mermaid de 73 à 59 entre les deux rejeux : à expliquer par l'axe Documentation (archives openspec déplacées ?).

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
Copie `git archive HEAD` dans /home/claude/audit-maj/build-deps/repo : `node scripts/verifier-docs.mjs` -> rc=0, « Fichiers Markdown contrôlés : 120 », « Blocs Mermaid : 73 », « Résultat : aucune erreur » ; verifier-licences-serveur.mjs sur inventaires factices (/tmp/lic) : GPL-3.0-only et licence absente -> rc=1 avec liste des écarts, BOM absent -> rc=2, Apache-2.0 + MIT avec archives -> rc=0 « Aucune licence absente ni non admise ». Le vrai bom.json n'a pas pu être produit (Maven Central bloqué).
```

**Impact** — Les liens et ancres de la documentation sont cohérents à 54add78 ; le garde-fou de licences se comporte comme documenté.

**Action** — Aucune (voir AUD-DEP-22 pour l'automatisation).

### AUD-DEP-28 — Profil Keycloak local bien assemblé : image épinglée par version exacte, healthcheck conforme à la recette officielle (bash /dev/tcp sur le port de gestion 9000), port lié à 127.0.0.1, mémoire bornée, dépendance facultative d'Argos, scripts montés forcés en LF

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**
- **Nouveau** le 21/11

**Preuve**

```text
compose.yaml:40 keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}, :41 profiles idp-local, :44-46 depends_on postgres healthy, :56 KC_HEALTH_ENABLED, :64 `127.0.0.1:${ARGOS_KEYCLOAK_PORT:-8180}:8080`, :65 mem_limit, :66-77 healthcheck (/dev/tcp/localhost/9000, GET /health/ready, start_period 40s), :86-88 `required: false` ; .gitattributes:1 `*.sh text eol=lf` ; Context7 /keycloak/keycloak 26.8.1 docs/guides/observability/health.adoc (même technique, l'image n'embarque pas de client HTTP) ; `docker compose -f compose.yaml --profile idp-local config -q` -> rc=0.
```

**Impact** — Argos n'attend Keycloak que si le profil est actif, et un Keycloak non prêt ne fait pas démarrer Argos sur une découverte OIDC en échec.

**Action** — Aucune ; à terme, tester `HTTP/1.1 200` plutôt que `grep -q UP` (un corps DOWN listant un contrôle UP passerait) et vérifier que la version de Compose de Container Manager accepte `required: false` (Compose >= 2.20).

## Documentation — Moyenne (5)

### AUD-DOC-01 — Les points d'entrée (README racine, docs/architecture/README.md, openspec/config.yaml) décrivent encore le cadrage de septembre : socle « sans fonctionnalité métier », Claude derrière un AI Gateway, questions ouvertes déjà tranchées par des ADR acceptés

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Documentation · **Effort** : S · **Sprint** : 12
- **Dépend de** : AUD-ARC-14

**Vérification du 21/11 (b142695)** — Ouvert, légèrement aggravé : les cadrages de septembre subsistent mot pour mot et une affirmation de plus est devenue fausse après #29.

*Changement depuis le 20/11* : Aucun des trois points d'entrée n'a été modifié dans le delta (git diff 54add78 HEAD : README.md ne gagne que la section « Vérifications locales », docs/architecture/README.md et openspec/config.yaml inchangés). L'archivage de tous les changements par 8882fb3 (#29) aggrave l'écart : docs/architecture/README.md:10 et arc42 01-introduction-objectifs.md:14 affirment encore « changements OpenSpec non archivés ».

```text
README.md:11-13 (« socle applicatif sans fonctionnalité métier », baseline « active » v2.4), :104 (Claude derrière un AI Gateway), :120 (Claude API facturée), :146 (« outil de migration à confirmer par ADR »), :149-151 (OSV, corrélation, MCP « à faire »), :157 (licence « interne à une entreprise ») ; docs/architecture/README.md:10 (« capacités du lot 1 … changements OpenSpec ne sont pas archivés » alors que `ls openspec/changes` = archive seul), :20, :41-42, :100 ; openspec/config.yaml:6-7 (« phase de conception … socle applicatif sans fonctionnalité métier »).
```

*Au 20/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
README.md:13 (« socle applicatif sans fonctionnalité métier »), README.md:90-91 et 106-107 (MKP, Claude/AI Gateway, Claude API facturée), README.md:133 (« outil de migration à confirmer par ADR ») alors qu'ADR-0032 est Accepté, README.md:136 et 138 (connecter OSV, prototyper le MCP : à faire) alors que argos-server/src/main/java/io/argos/ingestion/osv/ et io/argos/mcp/ (8 @Tool) existent, README.md:144 (licence « interne à une entreprise ») sans le renvoi d'ADR-0025:50 qui la juge inadaptée à un produit ouvert ; docs/architecture/README.md:10 (« capacités du lot 1 » seulement), :20-21 et :25 (Claude/AI Gateway, FreshRSS séparés, validation MKP) contredits par ADR-0029, ADR-0028 et ADR-0030 acceptés et par argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java, :41-42 (budget Claude API), :51-56 (4 ADR acceptés cités sur 15), :84 (« Priorités avant le premier commit applicatif »), :99-102 (IdP, Claude, FreshRSS, Flyway : tranchés par ADR-0027, 0029, 0028, 0032) ; openspec/config.yaml:6-7 (« ARGOS est en phase de conception… socle applicatif sans fonctionnalité métier »), contexte injecté dans chaque artefact OpenSpec ; README.md:13 et docs/architecture/README.md:5 déclarent « active » la baseline v2.4, dont baseline-v2.4.md:4 dit « proposition mise à jour à valider » et :14 prévoit la VM interne abandonnée (ADR-0026).
```

**Impact** — Un lecteur (ou un agent OpenSpec) qui part des points d'entrée se fait une image fausse du produit : pas d'IA locale, pas de collecte intégrée, rien de livré. openspec/config.yaml oriente chaque nouvel artefact vers « rien n'est existant ».

**Action** — Réécrire Statut, Mission, IA, Prochaines étapes et Licence du README racine ; dans docs/architecture/README.md, remplacer la synthèse, la liste des ADR acceptés et les questions ouvertes par des renvois à arc42 §9 ; mettre à jour le contexte d'openspec/config.yaml ; marquer baseline-v2.4 comme historique (datée) au lieu d'« active ».

### AUD-DOC-02 — arc42 (§1, §2, §3 D-01, §5.2, §7, §10, §11) et les deux registres de risques divergents ne suivent pas les ADR 0025 à 0033 ; §3 et §7 citent encore Claude API et l'IdP d'entreprise

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Documentation, Architecture · **Effort** : M · **Sprint** : 12
- **Fusionne** : AUD-ARC-03
- **Dépend de** : AUD-ARC-05, AUD-ARC-06

**Vérification du 21/11 (b142695)** — Ouvert : arc42 §1-§3, §5.2, §7, §10 Q-06, §11 et le registre des risques restent au cadrage de septembre.

*Changement depuis le 20/11* : Aucun fichier visé modifié dans le delta (01, 02, 03, 05, 07, 10, 11 d'arc42 et risks/register.md identiques à 54add78). La PR #28 ajoute un Keycloak conteneurisé (compose.yaml:39-77) que ni §7 ni D-11 ne reportent (voir AUD-DOC-23).

```text
`git diff --quiet 54add78 HEAD -- docs/architecture/arc42/0{1,2,3,5,7}*.md docs/architecture/arc42/1{0,1}*.md docs/architecture/risks/register.md` : code 0 (aucune modification) ; docs/architecture/arc42/01-introduction-objectifs.md:14, 05-vue-blocs.md:85-104 inchangés.
```

*Au 20/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
docs/architecture/arc42/01-introduction-objectifs.md:11 et :15 (« synthèse assistée par Claude », « seules les capacités du lot 1 sont implémentées ») ; 02-contraintes.md:24-29 (migrations, Claude, FreshRSS, changedetection : « Hypothèse à valider ») contre ADR-0032, 0029, 0028 acceptés ; 03-contexte-perimetre.md:23-30, :47-52 et diagramme D-01 :68-89 (ARGOS → Claude en HTTPS, FreshRSS, changedetection), dernier commit bfb8d3a du 2026-09-24 ; 07-vue-deploiement.md:24, :27 et :53 (« Claude API », conteneur FreshRSS) alors que diagrams/d-11-deploiement-cible.md:20 et :27 montrent Ollama et Keycloak, et compose.yaml:66 le service ollama ; 10-exigences-qualite.md:83 (« Q-06 — Maîtrise des coûts Claude ») ; 11-risques-dette.md:7-16 (R-01 à R-10, dernier commit 4451e06 du 2026-09-24) et risks/register.md:7-24 (R-01 à R-18, libellés différents, tous « Ouvert », dernier commit 5009650 du 2026-09-24) : R-01, R-05, R-08 portent sur Claude ; R-07 (licences), R-11 (injection), R-12 (SSRF) ont des mitigations livrées (scripts/verifier-licences-serveur.mjs, marquage CONTENU_EXTERNE_NON_FIABLE, garde SSRF de io/argos/ingestion/web) jamais reportées. Les §5.1/§5.2 relèvent d'AUD-ARC-03 et ne sont pas repris ici.
[AUD-ARC-03] docs/architecture/arc42/05-vue-blocs.md:85 « INGEST --> SIGNAL » alors que signal importe ingestion 18 fois (signal/web/CollecteWeb.java:18-21) ; :86 « SIGNAL --> VULN » vs vulnintel/Enricher.java:27 import io.argos.signal.SignalChanged ; :92 « IMPACT --> ALERT » et :104 « WORK --> ALERT » vs alerting->impact (5) et alerting->workspace (7) ; :98 « SCM --> INVENTORY » vs inventory->scm (4). Absentes du diagramme : api->catalog (13), api->impact (8), api->alerting, mcp->10 modules dont api, catalog->workspace, impact->catalog, signal->catalog, releaseradar->catalog, inventory->catalog. :19-20 « Claude / Anthropic », « IdP entreprise » alors que le code appelle Ollama (aigateway/ollama/FournisseurOllama.java) et Keycloak (ADR-0027) ; idem 03-contexte-perimetre.md:52-53,73-74.
```

**Impact** — Les vues de contexte, de contraintes, de déploiement et de risques — celles qu'un relecteur sécurité ou un nouvel arrivant lit en premier — décrivent un autre système ; le registre des risques ne sert plus d'outil de pilotage et deux registres portent les mêmes identifiants avec des libellés différents.

**Action** — Aligner §1, §2, §3 (D-01), §7.2/§7.3 (D-10) et §10 Q-06 sur ADR-0026 à ADR-0033 ; fusionner §11.1 et risks/register.md en un seul registre (arc42 §11 renvoyant au registre), réviser chaque risque (statut, preuve de mitigation, risques clos gardés avec leur preuve) et ajouter les risques constatés (performance /me/veille, IdP auto-hébergé).

### AUD-DOC-03 — Le guide de déploiement mène encore à une installation qui ne démarre pas hors du profil idp-local : parcours Synology et parcours PC de base sans étape d'identité, tableau des réglages sans ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL, phrase « l'authentification n'est pas en place » maintenue

- **Statut** : Partiel · **Sévérité** : Moyenne · **Axes** : Documentation, Dépendances, Sécurité · **Effort** : M · **Sprint** : 12
- **Fusionne** : AUD-DEP-09, AUD-SEC-08
- **Dépend de** : AUD-DEP-01, AUD-DEP-21, AUD-DEP-25

**Vérification du 21/11 (b142695)** — Partiel : un parcours Keycloak existe désormais pour le PC, mais le parcours principal du PC (:18-23), tout le parcours Synology (:58, aucune étape d'identité), le tableau des réglages (:111-118, 6 lignes, ni ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL) et la phrase « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » (:80) sont restés tels quels ; README.md:85-92 donne toujours `cp .env.example .env` puis `docker compose up -d --build` sans OIDC.

*Changement depuis le 20/11* : e7ed7f3 (#28) ajoute à docs/deploiement/synology-et-pc.md:29-48 une sous-section « Avec Keycloak sur le même poste (profil idp-local) » qui nomme ARGOS_OIDC_URL, ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL et ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE et livre un royaume (deploiement/keycloak/argos-realm.json) ; .env.example:22-27 le commente. Le guide n'a pas été réécrit : le reste est inchangé.

```text
docs/deploiement/synology-et-pc.md:21-22, :58, :80, :111-118 ; .env.example:15-20 (OIDC « obligatoire ») ; compose.yaml:93-94 (« Argos refuse de démarrer sans ») ; application.properties:277 (%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}, sans défaut) ; README.md:88-89.
```

*Réserve* : Le parcours idp-local lui-même a deux défauts propres (port de retour, émetteur de l'IHM servie) : AUD-DEP-25 et AUD-DEP-21. Ni le guide ni le README ne renvoient au profil idp-local depuis le parcours de base.

*Au 20/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
docs/deploiement/synology-et-pc.md:21 et :37 (copier .env.example, « changez au minimum ARGOS_DB_PASSWORD »), :88-97 (tableau « Réglages » : 6 lignes, ni ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL), :59 (« Tant que l'authentification (OIDC, ADR-0008) n'est pas en place ») ; or .env.example:15-20 (« Fournisseur d'identité OIDC … — obligatoire », ARGOS_OIDC_URL vide en :17, ARGOS_ADMIN_EMAIL vide en :20) et argos-server/src/main/resources/application.properties:277 (%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}, sans défaut) ; diagrams/d-11-deploiement-cible.md:58 reconnaît que le déploiement de Keycloak n'est décrit nulle part ; même parcours dans README.md:74-77. Sur les 37 variables ARGOS_* lues par application.properties, une seule (ARGOS_DB_PASSWORD) figure dans le guide (comptage : doc-scripts, commande de la section B). Complète AUD-DEP-01 (SMTP, ARGOS_PUBLIC_URL, jetons de forge, quotas), non vérifié ici.
```

**Impact** — Une installation conforme au guide échoue au démarrage en profil prod (émetteur OIDC vide) ; l'utilisateur n'apprend ni qu'il doit fournir un royaume Keycloak, ni comment le créer (clients argos-ui, argos-server, argos-mcp), ni qui est administrateur au premier démarrage. La phrase de la ligne 59 laisse croire à tort qu'Argos est sans authentification.

**Action** — Ajouter au guide une étape « Fournisseur d'identité » (royaume, clients, audience, en s'appuyant sur argos-server/src/test/resources/keycloak/argos-realm.json), compléter le tableau des réglages à partir de .env.example et de compose.yaml, supprimer la ligne 59, et générer (ou contrôler par script) la liste des variables documentées contre application.properties. Correctif immédiat possible, en quelques lignes et sans attendre le sprint 10 : supprimer la phrase de synology-et-pc.md:59 (« tant que l'authentification n'est pas en place ») et ajouter ARGOS_OIDC_URL et ARGOS_ADMIN_EMAIL au tableau des réglages. Ajouter aussi la déclaration des redirections du client argos-ui pour l'adresse du portail déployé (le royaume de test n'autorise que http://localhost:5173/*).

### AUD-DOC-04 — Client desktop : le guide documente désormais l'URL du serveur fixée à l'emballage (argos.apiUrl), mais ni l'émetteur OIDC (VITE_ARGOS_OIDC_ISSUER au build, argos.oidcUrl ou ARGOS_OIDC_URL à l'exécution) ni leur obligation de cohérence ; la section « viser le NAS » est inchangée

- **Statut** : Partiel · **Sévérité** : Moyenne · **Axes** : Documentation · **Effort** : S · **Sprint** : 12
- **Dépend de** : AUD-DEP-21

**Vérification du 21/11 (b142695)** — Partiel : la cohérence API/CSP est documentée pour le cas « autre port », mais la section « Client desktop » (:165) ne règle toujours que VITE_ARGOS_API_URL pour viser le NAS, et ni VITE_ARGOS_OIDC_ISSUER, ni `argos.oidcUrl`, ni ARGOS_OIDC_URL ne sont mentionnés : un client visant le Keycloak du NAS garde l'émetteur et la CSP sur http://localhost:8180/realms/argos.

*Changement depuis le 20/11* : e7ed7f3 (#28) : argos-desktop/src/main.js:25-34 lit désormais aussi un champ `argos` (apiUrl, oidcUrl) fixé à l'emballage, et le guide (synology-et-pc.md:39-44) montre `-c.extraMetadata.argos.apiUrl=…` avec VITE_ARGOS_API_URL pour un serveur sur un autre port.

```text
docs/deploiement/synology-et-pc.md:42-43 (apiUrl seul), :165 (inchangé) ; argos-desktop/src/main.js:30 et :34 (OIDC_URL = ARGOS_OIDC_URL || argos.oidcUrl || 'http://localhost:8180/realms/argos') ; argos-ui/.env.example:5 ; `ls argos-desktop` : toujours aucun README.
```

*Au 20/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
docs/deploiement/synology-et-pc.md:144 (« construisez l'IHM avec … VITE_ARGOS_API_URL=http://adresse-du-nas:8080, puis npm run dist ») ; argos-desktop/src/main.js:27 (API_URL = process.env.ARGOS_API_URL || 'http://localhost:8080', « sert uniquement à la politique de sécurité du contenu »), :31 (OIDC_URL = process.env.ARGOS_OIDC_URL || 'http://localhost:8180/realms/argos', « les deux doivent coïncider » avec VITE_ARGOS_OIDC_ISSUER), :70 (contentSecurityPolicy(API_URL, OIDC_URL)) ; argos-ui/.env.example:5 (VITE_ARGOS_OIDC_ISSUER) absent du guide ; seules mentions de ARGOS_API_URL/ARGOS_OIDC_URL : e2e/README.md:91 (lancement de test) ; aucun README dans argos-desktop/ ni argos-ui/.
```

**Impact** — Un client desktop construit selon le guide ne peut pas joindre le serveur du NAS (requêtes bloquées par sa CSP) ni s'authentifier auprès du Keycloak réel ; le diagnostic n'est pas documenté.

**Action** — Documenter dans le guide les quatre réglages du desktop (VITE_ARGOS_API_URL, VITE_ARGOS_OIDC_ISSUER au build ; ARGOS_API_URL, ARGOS_OIDC_URL à l'exécution) et leur obligation de cohérence, ou faire dériver les valeurs d'exécution de celles du build ; ajouter un README court à argos-desktop/.

### AUD-DOC-19 — Après l'archivage des cinq derniers changements (#29), le registre des spécifications et sept fiches décrivent encore ces capacités comme « proposées », « non archivées », sans spec de référence ; l'inventaire OpenSpec annonce 11 specs, 5 changements actifs et 11 archivés contre 18, 0 et 16

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Documentation · **Effort** : S · **Sprint** : 12
- **Nouveau** le 21/11

**Preuve**

```text
docs/specifications/README.md:38-42 et :47-48 (COL, INV, COR, ABO, PERF, NOT, MCP : spec « absente », statut « proposée » ou « implémentée, non archivée »), :44 (PRF-07 « implémentée, non archivée »), :102-104 (« Specs de référence 11 », « Changements actifs 5 », « Changements archivés 11 ») contre `ls openspec/specs` (18 dossiers, dont abonnements-veille, collecte-avis-securite, correlation-impact, inventaire-sbom, notifications-poste, performance-api-mcp, serveur-mcp créés par 8882fb3), `ls openspec/changes` (archive seul), `ls openspec/changes/archive` (16) ; fiches : collecte-avis-securite.md:6-8, inventaire-sbom.md:6-8, correlation-impact.md:6-8, abonnements-veille.md:6-8 (« absente : capacité non livrée », « changement non archivé »), serveur-mcp.md:6-8, notifications-poste.md:6-8, performance-api-mcp.md:6-8 (« absente tant qu'aucun changement n'est archivé »), profils-veille.md:8 (« Changements en cours … non archivé ») — alors que leur lien « Changement en cours » pointe déjà vers openspec/changes/archive/2026-11-20-… ; les consignes post-archivage ont été réécrites par la même substitution au lieu d'être exécutées : 2026-11-20-automatiser-chaine-veille/tasks.md:145, 2026-11-20-exposer-serveur-mcp/tasks.md:77, 2026-11-20-notifier-sur-le-poste/tasks.md:80 (« remplacer … les liens vers `/openspec/changes/archive/2026-11-20-…/` par la spec principale ou le dossier d'archive »). Le contrôle documentaire ne détecte rien (liens valides) : `node scripts/verifier-docs.mjs` « aucune erreur ».
```

**Impact** — Le registre de couverture, qui fait foi pour l'avancement, donne pour sept des dix-huit capacités un état contraire au dépôt : un lecteur (ou un agent OpenSpec qui lit les fiches) croit la collecte, l'inventaire, la corrélation, les abonnements, les notifications, la MCP et la performance encore en cours et sans spec de référence, et peut rouvrir un changement sur une capacité déjà archivée.

**Action** — Pour les sept capacités : renseigner la spec de référence (/openspec/specs/<capacité>/spec.md), passer le statut à « livrée » (ou « livrée partiellement » avec renvoi aux recettes R-1 à R-5), remplacer « Changement en cours » par « Changements archivés » ; corriger l'inventaire (18, 0, 16) ; dans les tasks.md archivés, rétablir le texte des consignes post-archivage ; ajouter à verifier-docs une règle « une fiche qui dit spec absente alors que openspec/specs/<capacité>/spec.md existe est une erreur ».

## Documentation — Faible (8)

### AUD-DOC-05 — README racine, démarrage local : Node.js 22.12 annoncé contre ^22.18.0 exigé, Docker présenté comme requis pour les seuls tests alors que quarkus:dev démarre Keycloak, aucune mention de la connexion (royaume de test, comptes), e2e réduit au « portail web »

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12

**Vérification du 21/11 (b142695)** — Ouvert : Node.js 22.12 annoncé, Docker toujours présenté comme requis pour les seuls tests, pas de paragraphe de connexion, e2e réduit au portail web.

*Changement depuis le 20/11* : Aucun changement de la section « Démarrage local » ; la section « Vérifications locales » ajoutée (README.md:72-83) est exacte (`node scripts/verifier-nommage.mjs` : « 538 fichiers analysés, 0 violation(s) »).

```text
README.md:42 (« Node.js 22.12 ou plus ») contre argos-ui/package.json:8 et e2e/package.json:9 ("^22.18.0 || >=23.6.0") ; README.md:50 ; README.md:35 et :68 (« portail web »).
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
README.md:42 (« Node.js 22.12 ou plus ») contre argos-ui/package.json:8 et e2e/package.json:9 ("node": "^22.18.0 || >=23.6.0") et e2e/README.md:7 (« Node.js 22.18 ou plus ») ; README.md:50 (« tests : mvn verify (Docker requis : base jetable) ») alors que application.properties:284-289 démarre en %dev un Keycloak des Dev Services (port 8180, royaume src/test/resources/keycloak/argos-realm.json) ; aucune mention dans README.md:40-68 de la page de connexion, du compte alice (application.properties:305) ni de VITE_ARGOS_OIDC_ISSUER (argos-ui/.env.example:5) ; README.md:35 et :68 (« Tests de bout en bout du portail web ») alors que e2e/playwright.config.ts:65-77 définit aussi les projets desktop et mcp, et e2e/package.json les campagnes test:accessibilite et test:performance.
```

**Impact** — Un développeur sur Node 22.12 à 22.17 voit npm refuser l'installation ; sans Docker, quarkus:dev échoue sans que le README l'annonce ; l'IHM démarrée affiche un écran de connexion dont les identifiants ne sont documentés que dans e2e/README.md:33.

**Action** — Corriger la version de Node, signaler Docker pour quarkus:dev, ajouter un paragraphe « Se connecter en local » (royaume de test, comptes, mot de passe = identifiant) et décrire e2e comme « portail web, desktop, MCP, campagnes accessibilité et performance ».

### AUD-DOC-06 — Le diagramme D-19 (arc42 §6.6, veille réglementaire) ne se rend pas : un « ; » dans un message de séquence casse l'analyse Mermaid, et le contrôle documentaire passe parce que le rendu est facultatif

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12

**Vérification du 21/11 (b142695)** — Ouvert : le rendu Mermaid de D-19 échoue toujours.

*Changement depuis le 20/11* : Ligne inchangée dans le delta.

```text
docs/architecture/arc42/06-vue-execution.md:290 (« … source DEGRADE ; repris … ») ; `node scripts/verifier-docs.mjs --rendu` dans /home/claude/audit-maj2/build-doc (copie de b142695, mmdc 11.16.0) : « ERREUR docs/architecture/arc42/06-vue-execution.md:273 rendu Mermaid en échec : Error: Parse error on line 17 », « Résultat : 1 erreur(s) », code 1.
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
docs/architecture/arc42/06-vue-execution.md:290 (« Reg->>Persist: jour EN_ECHEC, source DEGRADE ; repris … ») ; `node scripts/verifier-docs.mjs --rendu` dans une copie à 54add78 (mmdc 11.16.0) : « ERREUR docs/architecture/arc42/06-vue-execution.md:273 rendu Mermaid en échec : Error: Parse error on line 17 » puis « Résultat : 1 erreur(s) » ; le bloc extrait se rend après remplacement du « ; » par une virgule ; sans l'option : « Résultat : aucune erreur » ; ADR-0020:66 et openspec/config.yaml:36 ne prescrivent que l'exécution sans --rendu. Introduit par e73ec4d (2026-11-18).
```

**Impact** — Le seul diagramme d'exécution de la veille réglementaire s'affiche en erreur sur GitHub et dans tout rendu Mermaid, alors qu'ADR-0005 fait de Mermaid la source de vérité des diagrammes.

**Action** — Remplacer le « ; » de la ligne 290 ; faire de --rendu le mode prescrit par ADR-0020 et par les tâches type du schéma lorsque mmdc est disponible (ou ajouter au script la détection des « ; » dans les messages de sequenceDiagram).

### AUD-DOC-07 — Documents de suivi incohérents entre eux : statuts de tâches et de décisions, inventaire des diagrammes (D-01 à D-13 contre 19), migrations de D-13 et statuts de capacités ne concordent pas

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12

**Vérification du 21/11 (b142695)** — Ouvert, aggravé sur l'inventaire des diagrammes.

*Changement depuis le 20/11* : Les cinq passages relevés sont inchangés ; #29 et c1430eb ajoutent une contradiction (D-13 décrit V1 à V21, l'inventaire des diagrammes dit toujours « V1 à V17 et V20 »). Les incohérences de statut liées à l'archivage font l'objet d'AUD-DOC-19.

```text
docs/specifications/README.md:199 (« Tâche 3.1 décochée … P-13, à valider ») contre openspec/changes/archive/2026-11-20-automatiser-chaine-veille/tasks.md:62 (« - [x] 3.1 ») et proposal.md:161 (« P-13 ([DÉCISION] K1) ») ; docs/specifications/README.md:92 (« D-01 à D-13 ») ; docs/architecture/diagrams/README.md:36 (« V1 à V17 et V20 ») contre d-13-modele-physique.md:3 (« V1__socle.sql à V21__index_impacts_recents.sql ») ; docs/specifications/profils-veille.md:9 et :89 (« pas de capacité regintel »).
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
docs/specifications/README.md:199 (« Tâche 3.1 décochée : la contrainte INV-C3 n'est pas créée ([PROPOSITION] P-13, à valider) », « [PROPOSITION] P-18 ») contre openspec/changes/automatiser-chaine-veille/tasks.md:61 (« - [x] 3.1 ») et proposal.md:161 (« P-13 ([DÉCISION] K1) ») et :166 (« P-18 ([DÉCISION] K2) ») ; docs/specifications/README.md:92 (« D-01 à D-13 ») contre docs/architecture/diagrams/README.md:24-42 (D-01 à D-19) ; diagrams/README.md:36 (D-13 « V1 à V17 et V20 ») contre d-13-modele-physique.md:3 (« V1__socle.sql à V20 ») et :136, :189 (colonnes V18, V19) ; docs/specifications/profils-veille.md:8 et :88 (« pas de capacité regintel ») contre veille-reglementaire.md:8 (« livrée ») et argos-server/src/main/java/io/argos/alerting/RappelsReglementaires.java:41 (PROFIL = "expert-metier") ; docs/specifications/README.md:38-41 et :48 (COL, INV, COR, ABO, MCP « proposée ») contre :47 (NOT « implémentée, non archivée ») alors que les fiches (collecte-avis-securite.md:8, serveur-mcp.md:8…) disent le code implémenté et testé, et que leur ligne 6 dit « capacité non livrée ».
```

**Impact** — Le registre de couverture, qui fait foi pour l'avancement, donne pour certaines exigences un état contraire à celui du changement OpenSpec ; un lecteur ne sait pas si INV-C3 est décidé ni si la MCP est livrée.

**Action** — Corriger les cinq passages ; ajouter au vocabulaire du modèle de fiche (_modele-fiche-capacite.md:17) un statut « implémentée, non archivée » et l'appliquer aux cinq capacités concernées ; faire vérifier par verifier-docs que le statut d'une tâche citée dans le registre correspond à sa case dans tasks.md.

### AUD-DOC-08 — Neuf ADR acceptés (0023, 0024, 0027 à 0033) gardent une décision rédigée comme une proposition, et ADR-0029 se dit encore subordonné à sa propre acceptation

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12
- **Dépend de** : AUD-ARC-14

**Vérification du 21/11 (b142695)** — Ouvert : neuf ADR acceptés gardent « Proposition » ; ADR-0029 se dit toujours subordonné à sa propre acceptation.

*Changement depuis le 20/11* : Aucun ADR 0023-0033 modifié sur ce point (seules 0022, 0031, 0032 changent, pour repointer un lien d'archive).

```text
`node /home/claude/audit-maj/doc-scripts/statuts-adr.mjs /home/claude/audit-maj2/build-doc` : ADR-0023, 0024, 0027 à 0033 « fichier=accepté … DECISION_REDIGEE_COMME_PROPOSITION » (9 lignes) ; docs/architecture/adr/0029-passerelle-ia-ollama-local.md:11 (« ADR-0004 (Proposé) »), :45, :81.
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
docs/architecture/adr/0027-keycloak-idp-auto-heberge.md:44, 0028:43, 0029:43, 0030:43, 0033:55 (« **Proposition : option B** »), 0031:66 et 0032:53 (« Proposition : **option A** »), 0023:56 et 0024:63 (« Option A, **proposée** ») avec « Statut : Accepté » en ligne 3 ; 0029-passerelle-ia-ollama-local.md:11 (« ADR-0004 (Proposé) »), :45 et :81 (« ADR-0004 reste la référence tant que le présent ADR n'est pas accepté ») alors qu'ADR-0004 est « Remplacé par ADR-0029 » ; relevé par doc-scripts/statuts-adr.mjs. Distinct d'AUD-ARC-14 (ADR restés « Proposé »).
```

**Impact** — Le corps de l'ADR, seul lu par qui ouvre le fichier, contredit son en-tête ; ADR-0029 laisse croire qu'ADR-0004 fait encore référence.

**Action** — À l'acceptation, remplacer « Proposition : » par « Décision : » et retirer les clauses conditionnelles devenues caduques, en notant la date d'acceptation (modification d'acceptation, pas réécriture rétroactive au sens de arc42 §9.2).

### AUD-DOC-10 — Le contrôle documentaire laisse passer des chemins périmés : références en code en ligne vers trois changements archivés (ADR-0027, ADR-0028), README d'argos-server et d'e2e hors périmètre, aucune exécution automatique

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12
- **Dépend de** : AUD-TST-01

**Vérification du 21/11 (b142695)** — Ouvert : mêmes trous de contrôle ; le périmètre réellement contrôlé a diminué avec l'archivage.

*Changement depuis le 20/11* : scripts/verifier-docs.mjs inchangé dans le delta ; les trois chemins écrits en code sont toujours périmés ; toujours aucune CI (pas de .github/). L'archivage de #29 retire en outre du périmètre du script 18 fichiers et 14 blocs Mermaid (120 → 102 fichiers, 73 → 59 blocs), puisque openspec/changes/archive est exclu.

```text
docs/architecture/adr/0027-keycloak-idp-auto-heberge.md:74, 0028-collecteur-veille-web-integre.md:78 et :80 (chemins openspec/changes/<nom>/ disparus ; `grep -rnoE 'openspec/changes/[a-z0-9-]+'` hors archive : ces 3 seules occurrences) ; scripts/verifier-docs.mjs:19-24 (DOSSIERS, FICHIERS_RACINE, EXCLUS) ; `node scripts/verifier-docs.mjs` : « Fichiers Markdown contrôlés : 102 », « Blocs Mermaid : 59 » (120 et 73 à 54add78) ; `ls -a` à la racine : aucun .github.
```

*Réserve* : Les 30 blocs Mermaid hors périmètre (archives, README de sous-projets) se rendent tous : doc-scripts2/rendu-hors-perimetre.mjs, « blocs hors périmètre=30 erreurs=0 ».

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
docs/architecture/adr/0027-keycloak-idp-auto-heberge.md:74 (`openspec/changes/gerer-comptes-et-acces/specs/comptes-acces/spec.md`), 0028-collecteur-veille-web-integre.md:78 et :80 (`openspec/changes/collecter-veille-rss-web/…`) : dossiers déplacés sous openspec/changes/archive/2026-11-1x-… ; scripts/verifier-docs.mjs:19-24 (DOSSIERS = docs, openspec ; FICHIERS_RACINE = README.md : argos-server/README.md et e2e/README.md, 190 lignes, ne sont pas contrôlés) et :52-57 (le code entre accents graves est retiré avant le contrôle des liens) ; aucun dossier .github/ ni autre CI (voir AUD-TST-01). Contrôle complémentaire doc-scripts/liens.mjs sur les 201 .md : 0 lien cassé ; doc-scripts/chemins.mjs : ces trois chemins sont les seuls chemins de dépôt périmés hors faux positifs.
```

**Impact** — Les renvois des ADR vers les exigences qu'ils tracent sont morts ; la règle « repointer les liens après archivage » (guide-openspec-detailed-uml.md:74) n'est pas outillée pour les chemins écrits en code.

**Action** — Repointer les trois chemins vers openspec/specs/… ; étendre verifier-docs aux README de sous-projets et aux chemins de dépôt écrits en code en ligne ; l'exécuter dans la CI dès qu'elle existe.

### AUD-DOC-11 — Reliquat d'outillage Archgate : un second dossier d'ADR avec l'exemple non rempli GEN-001 et une documentation de règles de lint renvoyant à un fichier absent

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12

**Vérification du 21/11 (b142695)** — Ouvert.

*Changement depuis le 20/11* : Aucun changement de .archgate/ dans le delta.

```text
.archgate/adrs/GEN-001-example.md:1-3 (« Example Architecture Decision ») ; .archgate/lint/README.md:15 (oxlint.js) ; `ls .archgate/lint` : README.md seul.
```

*Au 20/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
.archgate/adrs/GEN-001-example.md:1-12 (« Example Architecture Decision … Replace this with a real architecture decision ») ; .archgate/lint/README.md:15 (« Create .archgate/lint/oxlint.js ») : fichier absent ; .archgate/config.json ; un seul commit, 7e4eb35 (2026-11-15, « initialiser OpenSpec et Archgate ») ; aucune mention d'Archgate dans docs/ ni dans les ADR.
```

**Impact** — Deux emplacements d'ADR coexistent dont l'un est un gabarit anglais vide ; confusion pour un contributeur ou un agent qui chercherait les décisions.

**Action** — Supprimer .archgate/ ou documenter son rôle par rapport à docs/architecture/adr/ (ADR-0020) et y retirer l'exemple.

### AUD-DOC-20 — arc42 §10 (Q-05) et le scénario QS-06 donnent encore GET /api/v1/me/veille hors cible (p95 = 533,8 ms, 15 opérations sur 16) alors que le rapport de campagne du 2026-11-20 mesure 85 ms et 16 opérations sur 16 dans la cible

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12
- **Nouveau** le 21/11

**Preuve**

```text
docs/architecture/arc42/10-exigences-qualite.md:80 (« **hors cible** : GET /api/v1/me/veille, p95 = 533,8 ms … [EXISTANT] ») et docs/architecture/quality/scenarios.md:12 (« GET /api/v1/me/veille hors cible (p95 = 533,8 ms) ») contre docs/architecture/quality/mesure-performance-api-mcp.md:12 (révision 7e5e36d) et :20 (« toutes les opérations dans la cible ») et :32 (p95 = 85 ms), et docs/specifications/README.md (« Couverture du lot 19 » : « p95 = 85 ms, seize opérations sur seize ») ; même paragraphe du lot 18 resté au présent : docs/specifications/README.md:199 (« reste hors cible »). Introduit par afcb8f6 (campagne du 2026-11-20), qui a mis à jour le rapport et la fiche mais pas arc42 ni les scénarios.
```

**Impact** — La vue qualité d'arc42, lue par un relecteur, signale un dépassement corrigé et marqué [EXISTANT] ; le chiffre contredit le rapport généré et la fiche de capacité.

**Action** — Mettre à jour 10-exigences-qualite.md:80 et scenarios.md:12 avec la campagne du 2026-11-20 (révision, 16/16, p95 de « Ma veille » 85 ms) en gardant la mesure du 2026-11-19 comme historique ; écrire au passé la phrase du lot 18 dans docs/specifications/README.md:199.

### AUD-DOC-23 — La PR #28 n'est pas reportée dans l'architecture : D-11 dit Keycloak hors de compose.yaml, D-11 et le guide (« Deux conteneurs démarrent ») ignorent keycloak, le README ne cite ni deploiement/ ni le profil idp-local

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 12
- **Nouveau** le 21/11

**Preuve**

```text
docs/architecture/diagrams/d-11-deploiement-cible.md:55 (« Conteneurs : argos, postgres, ollama ») et :58 (« Keycloak lu par ARGOS_OIDC_URL ; son déploiement sur le NAS n'est pas décrit dans compose.yaml ») contre compose.yaml:39-77 (service keycloak, profil idp-local, base créée par deploiement/postgres/creer-base-keycloak.sh) ; docs/deploiement/synology-et-pc.md:5-10 (« Deux conteneurs démarrent », tableau postgres/argos) ; README.md:28-38 (arborescence sans deploiement/) et :85-92 (Docker sans idp-local) ; docs/architecture/arc42/07-vue-deploiement.md inchangé depuis 54add78.
```

**Impact** — Un lecteur de la vue de déploiement conclut qu'il doit fournir lui-même Keycloak ; l'option livrée par #28 n'est découvrable que dans une sous-section du guide.

**Action** — Mettre à jour D-11 (:55, :58) et §7 avec le service keycloak du profil idp-local ; dans le guide, remplacer « Deux conteneurs » par le tableau des services et profils (postgres, argos, keycloak/idp-local, ollama/ia) ; ajouter deploiement/ à l'arborescence du README et un renvoi au profil idp-local dans « Argos complet sous Docker ».

## Documentation — Info (6)

### AUD-DOC-13 — Liens et ancres sains dans tout le dépôt (213 fichiers, 512 liens) ; le contrôle documentaire passe à b142695 sans l'option --rendu (avec : 1 erreur, AUD-DOC-06)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : liens et ancres sains dans tout le dépôt ; verifier-docs sans --rendu sans erreur.

*Changement depuis le 20/11* : Le contrôle passe toujours ; son périmètre a diminué avec l'archivage (#29).

```text
`node scripts/verifier-docs.mjs` (copie de b142695) : « Fichiers Markdown contrôlés : 102 », « Blocs Mermaid : 59 », « Résultat : aucune erreur », code 0 ; avec --rendu : 1 erreur (AUD-DOC-06) ; `node /home/claude/audit-maj/doc-scripts/liens.mjs` : « fichiers=213 liens relatifs=512 erreurs=0 ».
```

*Réserve* : Le périmètre de verifier-docs passe de 120 à 102 fichiers (voir AUD-DOC-10) ; la sûreté des liens vers les archives repose sur liens.mjs, hors dépôt.

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
`node scripts/verifier-docs.mjs` dans une copie de 54add78 (/home/claude/audit-maj/build-doc) : « Fichiers Markdown contrôlés : 120 », « Blocs Mermaid : 73 (flowchart, classDiagram, sequenceDiagram, erDiagram, stateDiagram-v2) », « Rendu Mermaid : non demandé (option --rendu) », « Résultat : aucune erreur », code de sortie 0 (avec --rendu : 1 erreur, voir AUD-DOC-06) ; /home/claude/audit-maj/doc-scripts/liens.mjs sur les 201 fichiers .md hors node_modules (archives OpenSpec, .claude, .archgate, README de sous-projets compris) : 491 liens relatifs, 0 lien cassé, 0 ancre introuvable.
```

**Impact** — Navigation documentaire fiable ; les identifiants d'exigences cités sont tous définis (contrôle du script).

**Action** — Conserver ; voir AUD-DOC-10 pour les angles non couverts par le script.

### AUD-DOC-14 — Statuts des 33 ADR identiques dans les en-têtes, l'index adr/README.md et arc42 §9.1 ; §9.3 reporte les questions tranchées par ADR-0031 et ADR-0032

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : 33/33 statuts concordants entre en-têtes, index et arc42 §9.1.

*Changement depuis le 20/11* : Aucun statut d'ADR modifié dans le delta ; 09-decisions.md inchangé.

```text
`node /home/claude/audit-maj/doc-scripts/statuts-adr.mjs /home/claude/audit-maj2/build-doc` : aucune ligne « <-- ECART » ; les 9 lignes en écart sont uniquement de rédaction (AUD-DOC-08).
```

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
/home/claude/audit-maj/doc-scripts/statuts-adr.mjs : 33/33 statuts concordants (fichier, docs/architecture/adr/README.md, docs/architecture/arc42/09-decisions.md:11-43) ; 09-decisions.md:57 et :59 (scheduling et Flyway « tranché par ADR-0031/0032 »).
```

**Impact** — La règle 5 de l'index (synchronisation avec arc42 §9) est tenue.

**Action** — Conserver ; corriger la rédaction des décisions acceptées (AUD-DOC-08).

### AUD-DOC-15 — Schéma et tâches planifiées documentés à jour : migrations V1 à V21 listées par arc42 §8.7 et D-13, treize tâches @Scheduled décrites

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : migrations V1 à V21 et 13 tâches planifiées documentées à jour.

*Changement depuis le 20/11* : f3b1cfc ajoute V21 ; c1430eb/afcb8f6 la reportent dans arc42 §8.7 (ligne V21, retour arrière, « prochaine migration V22 ») et dans D-13:3.

```text
`ls argos-server/src/main/resources/db/migration` : V1 à V21 ; docs/architecture/arc42/08-concepts-transverses.md:161 (« état au 2026-11-20 … V21 … prochaine V22 »), :187 (ligne V21) ; diagrams/d-13-modele-physique.md:3 ; `grep -rn @Scheduled argos-server/src/main/java | wc -l` = 13, identités = tableau de 08-concepts-transverses.md:131-145.
```

*Réserve* : diagrams/README.md:36 n'a pas suivi (V1 à V17 et V20) : reporté dans AUD-DOC-07.

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
argos-server/src/main/resources/db/migration/ (V1 à V20) = docs/architecture/arc42/08-concepts-transverses.md:161-186 (« état au 2026-11-19 », retour arrière V18-V20 :163) = diagrams/d-13-modele-physique.md:3 ; écart daté d'ADR-0032 (V1-V16) expliqué en inventaire-sbom.md:281 ; 13 @Scheduled dans argos-server/src/main/java : les 11 d'ADR-0031:16-26 (daté du 17/11) plus signal-synchronisation-sources et inventory-import-quotidien, ajoutés en 08-concepts-transverses.md:133-134.
```

**Impact** — Les documents vivants absorbent les ajouts du lot 18 sans réécrire les ADR datés.

**Action** — Conserver.

### AUD-DOC-16 — e2e/README.md et argos-server/README.md exacts par rapport au code

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : versions, projets, tableaux du script et textes de licence concordent.

*Changement depuis le 20/11* : e2e/README.md et argos-server/README.md inchangés ; scripts/verifier-licences-serveur.mjs refactoré (renommages, SPDX linéaire), tableaux nommés conservés.

```text
e2e/package-lock.json:18-19 (@playwright/test 1.61.1) et argos-desktop/package-lock.json:1448-1449 (electron 45.3.2) = e2e/README.md:7 et :11 ; scripts/verifier-licences-serveur.mjs:66 (ADMISES), :79 (SOUS_RESERVE), :94 (CHOIX), :114 (TEXTES), :128 (SANS_TEXTE), :132 (TITULAIRES) ; `ls argos-server/licenses` : 7 textes = argos-server/README.md:43-51.
```

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
e2e/README.md:22-25 = projets de e2e/playwright.config.ts:65-77 (preparation, portail-web, construction-desktop, desktop, mcp) ; versions citées = lockfiles (@playwright/test 1.61.1 dans e2e/package-lock.json, electron 45.3.2 dans argos-desktop/package-lock.json) ; ports 8090, 5173, 8180, 55432, 5174 = playwright.config.ts:98-103, e2e/compose.yaml:16, accessibilite.config.ts:7 ; tous les fichiers de tests, données et support cités existent ; scripts test:accessibilite et test:performance présents (e2e/package.json) ; argos-server/README.md:27-28 = argos-server/pom.xml:31 et :33 (cyclonedx 2.9.3, exec 3.6.4), tableaux ADMISES, SOUS_RESERVE, CHOIX, TEXTES, SANS_TEXTE, TITULAIRES = scripts/verifier-licences-serveur.mjs:66-132, 7 textes de argos-server/licenses/ = tableau :43-51.
```

**Impact** — La documentation développeur des tests et des licences est fiable et reproductible.

**Action** — Conserver ; l'inclure dans le périmètre de verifier-docs (AUD-DOC-10).

### AUD-DOC-17 — Contrats cités : outils MCP cohérents ; chiffres de performance et inventaire OpenSpec devenus incohérents (AUD-DOC-19, AUD-DOC-20)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Ne tient plus qu'à moitié : outils MCP (8 @Tool = « huit outils ») toujours cohérents ; les chiffres de performance et l'inventaire OpenSpec ne concordent plus (nouveaux constats AUD-DOC-19 et AUD-DOC-20).

*Changement depuis le 20/11* : Régression partielle : la campagne de performance du 2026-11-20 (afcb8f6, révision 7e5e36d) et l'archivage (#29) ont rendu fausses deux des quatre concordances.

```text
8 `@Tool(` dans argos-server/src/main/java/io/argos/mcp = docs/specifications/serveur-mcp.md:113 et :135 ; docs/architecture/quality/mesure-performance-api-mcp.md:32 (GET /api/v1/me/veille p95 = 85 ms, « toutes les opérations dans la cible ») contre arc42 10-exigences-qualite.md:80 et quality/scenarios.md:12 (533,8 ms, hors cible) ; docs/specifications/README.md:102-104 (11 specs, 5 changements actifs, 11 archivés) contre `ls openspec/specs` (18), `ls openspec/changes` (archive seul), `ls openspec/changes/archive` (16).
```

*Réserve* : Concordance des routes REST non rejouée de façon exhaustive (aucune route ajoutée dans le delta d'après les messages de commit).

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
Chaque route `GET|POST|PUT|DELETE|PATCH /api/v1/…` citée dans README, docs/, e2e/, argos-server/ et les changements actifs a tous ses segments dans un @Path du serveur (seul écart : /api/v1/products/{produit}, API externe endoflife.date, 08-concepts-transverses.md:267) ; huit outils de serveur-mcp.md:26 = huit @Tool(name=…) dans io/argos/mcp ; p95 de GET /api/v1/me/veille = 533,8 ms dans quality/mesure-performance-api-mcp.md:32, arc42 §10 (10-exigences-qualite.md:80), quality/scenarios.md:12, docs/specifications/README.md:199, tasks.md:132 et proposal.md:166 ; docs/specifications/README.md:102-104 (11 specs, 4 changements actifs, 11 archivés) = dossiers openspec/specs, openspec/changes, openspec/changes/archive.
```

**Impact** — Les chiffres et noms techniques partagés entre documents sont tenus.

**Action** — Conserver.

### AUD-DOC-18 — Traçabilité OpenSpec transparente : les cinq tâches ouvertes des changements archivés le 2026-11-20 portent une décision datée et une recette R-1 à R-5

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 21/11 (b142695)** — Tient : aucune tâche ouverte n'est archivée sans motif ni renvoi ; la correspondance tâche → recette est exacte.

*Changement depuis le 20/11* : 8882fb3 (#29) archive les cinq changements avec leurs cinq tâches non cochées ; chacune porte une [DÉCISION] datée du 2026-11-20 qui la convertit en recette R-1 à R-5 de docs/deploiement/recette-poste.md (nouveau).

```text
openspec/changes/archive/2026-11-20-exposer-serveur-mcp/tasks.md:32-34 (5.2 → R-3) ; 2026-11-20-formaliser-chaine-securite-poc/tasks.md:27-29 (3.2 → R-4), :65-67 (4.7 → R-5), :92-93 (7.1 → R-5) ; 2026-11-20-notifier-sur-le-poste/tasks.md:12-17 (1.2 → R-1, R-2) ; docs/deploiement/recette-poste.md:7-11 (R-1 à R-5, « à faire »).
```

*Réserve* : Le message de 8882fb3 parle de « recette réelle consignée » : seules les procédures sont consignées, toutes les recettes sont « à faire ».

*Au 20/11* : Info (Info) — —

**Preuve d'origine**

```text
openspec/changes/exposer-serveur-mcp/tasks.md:32 (5.2, Claude Code réel) ; formaliser-chaine-securite-poc/tasks.md:27-28 (3.2, « [DÉCISION] … capture sur un dépôt pilote réel »), :64-65 (4.7), :90 (7.1) ; notifier-sur-le-poste/tasks.md:12 et :53 (1.2 non cochée tant que les vérifications manuelles sous Windows ne sont pas faites) ; automatiser-chaine-veille/tasks.md : 25/25 cochées avec preuves datées.
```

**Impact** — L'état d'avancement réel se lit dans tasks.md ; seuls les documents de synthèse divergent (AUD-DOC-07).

**Action** — Conserver.
