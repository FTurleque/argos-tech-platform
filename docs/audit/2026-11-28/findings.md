# Constats de l'audit — mise à jour du 2026-11-28

Base vérifiée : `origin/main` à 01fea6d (PR #79), complété à ae4a2da (PR #82) pour AUD-ARC-11, AUD-QUA-09 et AUD-QUA-18. **44 constats restent à traiter sur 192.** Tri : sévérité puis axe, les constats Documentation en dernier. Chaque constat porte son statut au 20/11, ce qui a changé depuis le 17/11, le verdict de vérification, son sprint et ses dépendances. Ordre d'exécution : [sprints.md](sprints.md).

## Élevée (11)

### AUD-ARC-02 — L'IHM et le desktop joignent le jeton OIDC et l'IHM embarquée est servie sans session (chemins statiques publics), sans test qui le prouve

- **Statut** : Fermé · **Sévérité** : Élevée · **Axes** : Architecture, Sécurité · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-SEC-02

**Vérification du 28/11 (01fea6d)** — L'IHM embarquée se charge sans jeton et ouvre sa session ; le comportement est maintenant protégé par un test serveur et par l'E2E sur l'image.

*Changement depuis le 21/11* : La réserve du 21/11 (aucun test) est levée : CheminsPublicsTest (commit 50867d3) vérifie GET /, /index.html, /assets/*, /licences-polices.txt sans session -> 200 et 401 sur l'API, /q/openapi, /q/metrics et les traversées /assets/../api ; la campagne E2E de la CI tourne sur l'image construite (e2e/compose.image.yaml).

```text
argos-server/src/main/resources/application.properties:432-433 (chemins publics, permit) et :438-439 (/* authenticated) ; argos-server/src/test/java/io/argos/api/comptes/CheminsPublicsTest.java:36-41 ressourcesDeLIhmSansSession, :55-61 routesProtegeesRefuseesSansSession, :69 aucuneRouteDApiOuTechniquePubliqueAuDelaDeIdentiteEtSante ; .github/workflows/integration-continue.yml, job e2e (« docker build -t argos:e2e . » puis campagne sur l'image) ; grep « Tant que l'authentification » docs/deploiement -> aucun résultat.
```

*Au 20/11* : Partiel (Élevée) — La partie client est corrigée et testée ; le serveur exige toujours une session sur /* y compris sur les ressources statiques de l'IHM embarquée dans l'image Docker, qui ne peut donc pas se charger pour lancer la connexion.

*Au 21/11* : Fermé avec réserve (Élevée) — La cause du constat disparaît : avec la règle « longest path wins » de Quarkus, la page, ses ressources Vite et l'avis de licence des polices sont servis sans jeton, l'API reste protégée ; l'IHM embarquée peut donc se charger et ouvrir sa session. Accord avec le message de commit, mais aucune exécution ni aucun test ne le prouve, et le parcours de connexion par défaut de l'image reste cassé pour d'autres raisons (AUD-DEP-25, AUD-DEP-21).

**Preuve d'origine**

```text
argos-server/src/main/resources/application.properties:200 « quarkus.oidc.application-type=service », :217-218 « authentifie.paths=/* … policy=authenticated » ; argos-ui/src/api/client.ts:36-39 « fetch(`${apiBaseUrl}${path}`, { headers: { Accept: 'application/json' } … » (aucun en-tête Authorization) ; grep -rniE 'authorization|bearer|oidc|token' argos-ui/src argos-desktop/src -> aucun résultat pertinent. Lacune connue : docs/specifications/notifications-poste.md:123 « le serveur répond 401 ».
[AUD-SEC-02] argos-server/src/main/resources/application.properties:217-218 `quarkus.http.auth.permission.authentifie.paths=/*` / `policy=authenticated` (seuls /api/v1/system/info et /q/health* en permit) ; argos-ui/src/api/client.ts:36,87,173 `fetch(`${apiBaseUrl}${path}`, {...})` sans en-tête Authorization ; `grep -rniE 'authorization|bearer|token|oidc|pkce' argos-ui/src argos-desktop/src` : aucun résultat. Le Dockerfile copie l'IHM dans META-INF/resources, couverte par /* (doc Quarkus : un chemin en /* est un préfixe appliqué à toutes les requêtes HTTP).
[À 9d96406] application.properties:235 (application-type=service), :252-253 (/* authenticated) ; client.ts:36-39 inchangé.
```

**Impact** — En image Docker, même index.html et l'écran de statut reçoivent 401 : l'IHM web est inutilisable ; seul le desktop, qui sert l'IHM depuis app://argos, affiche l'écran de statut, et toutes ses autres vues reçoivent 401. Le parcours de bout en bout IHM ↔ serveur n'a jamais pu être exécuté (openspec/changes/notifier-sur-le-poste/tasks.md:28).

**Action** — Implémenter Authorization Code + PKCE (client public argos-ui, ADR-0027) dans argos-ui (ex. oidc-client-ts) et le protocole app://argos du desktop, injecter le jeton dans un wrapper fetch unique de client.ts, puis lancer un E2E navigateur contre le royaume Keycloak de test.

### AUD-QUA-01 — La création de projet, l'ajout de dépôt et l'import manuel (SBOM, manifestes) n'ont toujours aucun point d'entrée en production ; seul l'import planifié des dépôts déjà configurés est branché

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée · **Axes** : Qualité · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La preuve d'origine a disparu : le projet naît par l'API avec contrôle de rôle, l'import SBOM/manifestes/relance est exposé, et des tests d'intégration couvrent les parcours et les refus.

*Changement depuis le 21/11* : Routes REST de création de projet, de rattachement/détachement de dépôt et d'import manuel ajoutées (sprint 7, commit 1ce7ff4, puis 95473df) ; IHM branchée (argos-ui/src/api/projets.ts:113).

```text
argos-server/src/main/java/io/argos/api/ProjectResource.java:100-108 (POST création, access.administrator), :118-129 (POST /{projectId}/repositories), :136-144 (DELETE dépôt) ; api/InventoryResource.java:172-180 (POST /sbom), :185-195 (POST /manifests), :200-209 (POST /refresh), :227-228 (access.contributor) ; tests : argos-server/src/test/java/io/argos/api/inventaire/AdministrationProjetsTest.java:66-129 (INV-12-S1 à S5, dont 403/404), api/inventaire/ImportManuelTest.java ; argos-ui/src/api/projets.ts:113.
```

*Réserve* : Relecture du 28/11 : les routes créées ouvrent la délégation confuse d'AUD-SEC-34.

*Au 20/11* : Partiel (Élevée) — Partiel, mais sans effet en production : la tâche ImportPlanifie (77181e9, testée) importe chaque nuit les dépôts déjà rattachés, or aucun point d'entrée ne crée de projet ni ne rattache de dépôt — elle n'a donc rien à parcourir. Désaccord écrit avec inventaire-sbom.md:33, qui affirme que « l'import manuel existant reste possible ».

*Au 21/11* : Partiel (Élevée) — Inchangé depuis le 20/11 : l'import planifié (ImportPlanifie) parcourt les projets ayant un dépôt, mais rien en production ne crée de projet ni ne rattache de dépôt ; l'import manuel SBOM/manifestes n'est appelé que par les tests.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/inventory/InventoryService.java:49 (importSbom), :64 (importManifests), :91 (refresh) ; inventory/ProjectDirectory.java:37 (create). Commande : grep -rn "InventoryService\|projects.create\|importSbom\|\.refresh(" argos-server/src/main -> aucune référence hors du paquet inventory ; références uniquement dans src/test (chain/InventoryTest.java:23, alerting/AlertesTest.java:44). api/InventoryResource.java:22-52 n'expose que deux GET (inventaire, échecs). Aucun @Scheduled, outil MCP ni ressource REST n'appelle InventoryService ou ProjectDirectory.create.
```

**Impact** — En exploitation, aucun projet ni composant ne peut être créé : la corrélation (CorrelationEngine.onInventoryReplaced), le radar de dette de version et les alertes d'impact restent sans données réelles alors que les tests passent. Environ 600 lignes (InventoryService, InventoryStore, CycloneDxParser, ManifestParser, RepositoryFiles) sont du code mort pour la production.

**Action** — Exposer l'import (POST /api/v1/espaces/{id}/projets, POST /projects/{id}/inventory pour SBOM, POST .../refresh) avec contrôle de rôle via Access, ou un déclenchement planifié de refresh() pour les projets ayant un dépôt ; ajouter un test de bout en bout REST qui crée un projet, importe une SBOM et observe un impact. À défaut, documenter explicitement la limite dans le README.

### AUD-QUA-02 — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée · **Axes** : Qualité, Dépendances · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-DEP-02

**Vérification du 28/11 (01fea6d)** — Correction réelle : un pg_dump en échec ou une archive incomplète font sortir le script en code 1 sans toucher aux sauvegardes existantes (essai reproduit avec un faux docker). La protection n'est vérifiée que par un essai manuel qui exige Docker, absent de la CI.

*Changement depuis le 21/11* : Script réécrit (commit a2922ed) : export vers un fichier .partiel sans pipeline (pg_dump --compress dans le conteneur), contrôle gzip -t et de la ligne de fin d'export, renommage seulement en cas de succès, rotation après succès de toutes les bases, sortie non nulle nommant la base.

```text
scripts/sauvegarder-base.sh:55-58 (code de retour de « docker compose exec » contrôlé hors pipeline), :59-61 (archive_valide), :62 (mv seulement après contrôle), :86-88 (rotation après succès) ; scripts/sauvegarde-commun.sh:37-41 (gzip -t puis recherche de « -- PostgreSQL database dump complete ») ; essai : copie dans un dossier temporaire avec un faux « docker » qui renvoie 1 après avoir écrit un gzip partiel -> « Sauvegarde en échec : export de la base argos », rc=1, ancienne archive conservée ; même essai avec rc=0 mais export incomplet -> « archive de la base argos illisible ou incomplète », rc=1.
```

*Réserve* : Test de non-régression uniquement manuel : scripts/essai-sauvegarde-restauration.sh (pile Docker jetable, « réussi le 2026-11-24 » selon openspec/changes/archive/2026-11-24-fiabiliser-sauvegarde-et-roles-base/tasks.md:47) n'est appelé par aucun job de .github/workflows/integration-continue.yml (grep « essai-sauvegarde » -> 0). La rotation garde un pipeline « ls | sort | tail | while » (sauvegarder-base.sh:71) sans conséquence, car elle n'est atteinte qu'après succès.

*Au 20/11* : Ouvert (Élevée) — Pipeline sans pipefail, rotation sans contrôle de validité et message de succès inconditionnel toujours présents.

*Au 21/11* : Ouvert (Élevée) — Pipeline sans pipefail, rotation sans contrôle de validité et message de succès inconditionnel toujours présents.

**Preuve d'origine**

```text
scripts/sauvegarder-base.sh:5 « set -eu » (sans pipefail) ; :22 « $COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE" » ; :26-31 rotation « for OLD in $(ls -1t "$DEST"/argos-*.sql.gz) ... rm -f » sans contrôle de taille ni de validité ; :33 affiche toujours « Sauvegarde écrite ».
[AUD-DEP-02] scripts/sauvegarder-base.sh:22 `$COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE"` (set -eu seul, ligne 5) ; :26-30 rotation par nombre de fichiers sans contrôle de taille ni de validité. Commande exécutée : `sh -c 'set -eu; false | gzip > /tmp/t.gz; echo ...'` -> « sortie OK malgré échec pg_dump, taille=20 ».
```

**Impact** — Le code de retour d'un pipeline sh est celui de gzip : si postgres est arrêté ou pg_dump échoue, le script sort en 0 avec un .sql.gz vide ou tronqué. La tâche planifiée DSM ne signale rien ; après ARGOS_BACKUP_KEEP (14) échecs consécutifs, toutes les sauvegardes valides ont été supprimées par la rotation : perte de données possible lors d'une restauration.

**Action** — Écrire le dump dans un fichier temporaire puis vérifier le statut de pg_dump (ex. « pg_dump ... > tmp || exit 1 », ou bash avec « set -o pipefail »), contrôler « gzip -t » et une taille minimale, renommer seulement en cas de succès, et n'appliquer la rotation qu'après une sauvegarde valide. Sortir en code non nul pour que DSM notifie l'échec.

### AUD-QUA-20 — Les captures aux frontières isolent puis relancent les erreurs imprévues : un PURL mal encodé est déclaré illisible et la synchronisation continue (testé)

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée · **Axes** : Qualité, Tests, Architecture · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-TST-24, AUD-ARC-23, AUD-QUA-22

**Vérification du 28/11 (01fea6d)** — Un PURL mal encodé n'arrête plus la synchronisation : il est indexé par écosystème et nom, la source suivante est synchronisée, et des tests le démontrent ; les trois chaînes d'origine sont corrigées.

*Changement depuis le 21/11* : Commit 46a293b (changement isoler-erreurs-aux-frontieres) : PackageKey.decode rend « illisible » sur encodage invalide ; boucles de SourceSynchronizer, SynchronisationPlanifiee, ImportPlanifie, radar, fiches, collecte d'activité, enrichissement et diffusion : panne journalisée, erreur imprévue journalisée, retenue puis relancée (ErreursImprevues) ; observateur StandardModifie passé en @ObservesAsync.

```text
argos-server/src/main/java/io/argos/catalog/PackageKey.java:141-147 (catch IllegalArgumentException -> Optional.empty) ; ingestion/SourceSynchronizer.java:107-116 (SourceException, IllegalStateException, puis RuntimeException -> échec permanent puis relance) ; signal/SynchronisationPlanifiee.java:91-97 ; inventory/ImportPlanifie.java:89-95, :116-121 ; impact/RadarProjets.java:235 « surStandardModifie(@ObservesAsync StandardModifie …) » ; tests : argos-server/src/test/java/io/argos/catalog/PackageKeyDecodageTest.java:19-20 (pkg:npm/%zz@1.0.0 et 4 autres) ; chain/FrontieresCollecteTest.java:63-77 col11S1AvisAuPurlMalEncode (les deux sources SUCCEEDED, aucun run RUNNING) ; inventory/ParseursInventaireTest.java:145 inv11S1PurlMalEncodeEcarteLeResteLu ; inventory/CheminsEchecInventaireTest.java:77-130 (erreur imprévue retenue puis relancée) ; mcp/ParametresTest.java:13.
```

*Réserve* : Six boucles planifiées restent hors de la règle, déclarées « à aligner » (docs/architecture/arc42/08-concepts-transverses.md:204) : voir AUD-QUA-25. L'inventaire des frontières cite ImportPlanifieTest pour l'import planifié alors que le test d'erreur imprévue est CheminsEchecInventaireTest ; la file d'enrichissement, inventoriée avec EnrichmentTest, n'a aucun test d'erreur imprévue (règle 6, :184) et ne reporte pas un signal en erreur (AUD-QUA-24).

*Au 21/11* : Ouvert (Élevée) — —

**Preuve d'origine**

```text
Commits c4c8eb7, c341b49, 926d486 (lot23) : ingestion/SourceSynchronizer.java:95 « catch (IllegalStateException panne) » (au lieu de RuntimeException, qui menait à FAILED/PERMANENT) ; signal/SynchronisationPlanifiee.java:67 et inventory/ImportPlanifie.java:66,89 « catch (IllegalStateException erreur) » par source / par projet. Chaîne démontrée : catalog/PackageKey.java:47,60,61 decode() -> :126 URLDecoder.decode lève IllegalArgumentException sur « % » mal formé (essai java sur copie : PackageKey.parse("pkg:npm/%zz@1.0.0") -> java.lang.IllegalArgumentException, non IllegalStateException). Côté avis : signal/SignalRecorder.java:307 toAffected -> record (@Transactional MANDATORY, :64) -> ingestion/AdvisoryPersister.java:23 -> SourceSynchronizer.runOnce:137, qui ne capture que AdvisoryRejectedException (:132). Côté inventaire : inventory/CycloneDxParser.java:125 -> InventoryService.lireSbom (:233 ne capture que InvalidDocumentException) -> ImportPlanifie:89. Le test a été renommé pour suivre la capture : chain/ResilienceTest.java:216-223 « panneAccesDonneesTermineLeTraitementEnEchec » simule une IllegalStateException (ancien « erreurInattendueTermineLeTraitementEnEchec ») ; aucun test d'une autre RuntimeException. Même rétrécissement dans les autres frontières : alerting/MoteurAlertes.java:141 (observateur synchrone de ImpactsRecorded, appelé par CorrelationEngine.publish), alerting/EnvoiAlertes.java:121, RappelsRadar.java:114-158, impact/RadarProjets.java:110-134, releaseradar/CyclesDeVie.java:56, regintel/PreparationFiches.java:108, projectintel/CollecteActivite.java:110 ; aigateway/PasserelleIa.java:169-182 ne capture plus que IOException/InterruptedException autour du fournisseur (avant : Exception). docs/specifications/radar-standards.md:42 promet encore « Une erreur sur un produit ou un espace est journalisée sans interrompre les autres ».
[AUD-TST-24] git grep -c 'catch (RuntimeException' 54add78 -- argos-server/src/main/java -> 47 (plus 4 catch (Exception)), à b142695 -> 0 ; git diff -U0 54add78 HEAD -- argos-server/src/main/java (lignes « - … catch (RuntimeException|Exception ») -> 51 captures dans 29 fichiers (CorrelationEngine 5, RadarProjets 4, RappelsRadar 4, PasserelleIa 4…) ; git show --name-only des 23 commits fix/refactor du lot 22-23 -> seul c4c8eb7 (ingestion) modifie src/test. Exemples : mcp/GardeAppels.java (a7fbf21) ne capture plus que IllegalStateException, une erreur imprévue n'est plus journalisée en Issue.ERREUR ; grep -rn INDISPONIBLE src/test/java/io/argos/mcp -> 0 ; inventory/ImportPlanifie.java:66 et :89 catch (IllegalStateException) dans les boucles par projet, Bilan.enEchec jamais vérifié (grep -rn enEchec src/test/java -> 0) ; 9 des 29 classes ne sont nommées dans aucun test (CyclesDeVie, MoteurAlertes, CollecteOpportunites, PreparationFiches, GardeAppels, HttpSourceClient, ActivationProfilParDefaut, AlertesSansObjet, ValidationSortie).
[AUD-ARC-23] impact/RadarProjets.java:198 « catch (IllegalStateException erreur) » (54add78 : « catch (RuntimeException e) », git show 54add78:argos-server/src/main/java/io/argos/impact/RadarProjets.java:198) ; alerting/RappelsRadar.java:158 idem ; observateurs synchrones RadarProjets.java:187 et RappelsRadar.java:147 ; déclenchement après base.executer dans catalog/standard/ReferentielEspace.java:101-104 et :219. Commit 13baff7 « fix(impact): captures d'exception précises et causes journalisées ». Aucun test ne couvre StandardModifie ou AnnonceStandard en échec (grep -rln "StandardModifie\|AnnonceStandard" argos-server/src/test -> aucun résultat).
[AUD-QUA-22] Commit a7fbf21 : mcp/GardeAppels.java:117 « catch (IllegalStateException erreur) » remplace « catch (RuntimeException e) » ; seule cette branche écrit journalAppels.enregistrer(..., Issue.ERREUR) (:118-121). Aucun @WrapBusinessError dans argos-server (grep -> 0) : d'après la documentation quarkus-mcp-server 2.x (guides-error-handling), une exception non enveloppée devient une erreur de protocole JSON-RPC. Aucun test du chemin INDISPONIBLE ni de Issue.ERREUR (grep -rn "INDISPONIBLE\|Issue.ERREUR" argos-server/src/test/java/io/argos/mcp -> 0). La Javadoc :44-45 a été réécrite pour ne promettre INDISPONIBLE qu'en cas de « panne d'accès aux données ».
```

**Impact** — Un seul avis OSV ou une seule SBOM de dépôt portant un PURL mal encodé (donnée externe) lève une exception non capturée : la tâche planifiée s'interrompt, les sources (ou projets) suivants ne sont pas traités, le traitement reste RUNNING (curseur non avancé) jusqu'à l'expiration de 30 min puis bute sur le même avis à chaque échéance. La source n'atteint jamais DEGRADED (COL-07) : l'exploitation ne voit qu'une trace d'erreur du planificateur. Avant le delta, la même donnée menait à un échec PERMANENT visible et isolé. Plus généralement, tout défaut (NPE, IllegalArgumentException, DateTimeException…) dans une étape d'une boucle par espace/produit/message arrête désormais la boucle entière.

**Action** — Rétablir un filet aux frontières de boucle et d'observateur (catch RuntimeException journalisé avec cause, comptage en échec, état FAILED de la source) en gardant les captures précises à l'intérieur ; corriger PackageKey.decode pour renvoyer Optional.empty() sur IllegalArgumentException ; introduire un type dédié (ex. PanneDonnees extends IllegalStateException) si la distinction est voulue ; écrire la règle dans arc42 §8 ; ajouter des tests : avis avec PURL « %zz » (source FAILED, sources suivantes synchronisées), SBOM avec PURL mal encodé (projet refusé, projets suivants importés).

### AUD-SEC-34 — Inventaire et activité de projet : tout compte peut créer un espace, y rattacher n'importe quel dépôt GitHub/GitLab et en lire manifestes, SBOM, jalons et versions avec les jetons du serveur

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Sécurité · **Effort** : M · **Sprint** : 1
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/api/EspacesResource.java:71-72 (tout compte authentifié crée un espace dont il devient administrateur ; matrice : POST /api/v1/espaces atteint la validation pour tous les rôles) ; api/ProjectResource.java:108-113 (POST /api/v1/projects, access.administrator(espace) seul) et :128-129 (rattacher, POST …/repositories) ; api/SaisieProjet.java:85-90 (seule la forme owner/repo est validée, aucune liste de propriétaires admis) ; api/InventoryResource.java:209-214 (refresh) et import planifié ARGOS_INVENTORY_IMPORT_CRON (compose.yaml:219) ; scm/RepositoryFiles.java:114, :121 (jeton ARGOS_GITHUB_TOKEN/ARGOS_GITLAB_TOKEN envoyé pour lire package.json, requirements.txt, SBOM) ; api/ActiviteResource.java:123-129 et scm/RepositoryActivity.java:41-49 (jalons, versions, compteurs de tickets, PR, pipelines) ; application.properties:108-109 ; le design du changement openspec/changes/archive/2026-11-26-alimenter-inventaire-et-completer-ihm/design.md:142-147 ne traite pas la portée des jetons.
```

**Impact** — Dès que le serveur est configuré avec un jeton qui voit des dépôts privés (cas d'usage du jeton), un utilisateur invité, ou tout inscrit sur un service ouvert, lit l'inventaire de dépendances, les titres de jalons, les versions et les volumes d'activité de dépôts privés auxquels il n'a aucun droit, et sonde l'existence de dépôts (échecs d'import listés par …/inventory/failures). Fuite de données sous condition d'un compte : délégation confuse du jeton du serveur.

**Action** — Restreindre les dépôts rattachables : liste blanche de propriétaires/groupes par configuration (argos.scm.proprietaires-admis), ou rattachement réservé à l'administrateur plateforme ; à défaut, n'utiliser le jeton du serveur que pour des dépôts déclarés par l'exploitant et lire les autres sans jeton (dépôts publics seulement). Test : un administrateur d'espace qui rattache un dépôt hors liste reçoit 400 et la forge simulée ne reçoit aucune requête.

### AUD-TST-01 — Intégration continue en place (7 jobs sur push main et pull request) ; protection de main et exécutions non vérifiables depuis l'audit

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée (était Moyenne le 17/11) · **Axes** : Tests, Dépendances · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-DEP-04

**Vérification du 28/11 (01fea6d)** — Corrigé : chaque gate déclaré appelle une commande réelle du dépôt ; les gates Node rejoués dans une copie d'audit passent tous ; le gate Maven n'est pas rejouable ici (Maven Central bloqué) et la protection de main n'est pas vérifiable.

*Changement depuis le 21/11* : dd9998b (PR #32, 2026-11-24) crée .github/workflows/integration-continue.yml, complété par 2c4b0b5, 957af23, 6bc8003, 36d8d54 et, le 2026-11-28, par le job e2e sur l'image (ecf45f5, 7ea49a0, 9a0d3e6, dffbe06). Déclencheurs : push sur main et toute pull request ; jeton contents: read ; actions épinglées par empreinte. Sept jobs : serveur (JDK 17, mvn -P integration-continue clean verify, seuil JaCoCo, couverture par module), ihm (npm ci, test, typecheck, build, licences, npm audit high), desktop (npm run check = syntaxe + tsc + tests, licences, audit), outils (node --test scripts + performance, verifier-docs --rendu, nommage, configuration, royaume, typecheck et audit de e2e), e2e (docker build, campagne Playwright contre l'image, audit d'accessibilité du banc), changements (garde des migrations ADR-0032), licences (conditionnel, mvn -P licences validate).

```text
.github/workflows/integration-continue.yml:6-9 (push main, pull_request), :11-12 (contents: read), :49 mvn -B -f argos-server/pom.xml -P integration-continue clean verify, :55 node scripts/verifier-couverture-modules.mjs, :86-94, :111-117, :131-158, :177 docker build, :204 npm test (e2e sur ARGOS_E2E_IMAGE), :207 npm run test:accessibilite, :240-249 garde des migrations ; copie build-tests à 01fea6d : argos-ui npm test 218+58/276 verts, typecheck 0, build OK ; argos-desktop npm run check 92/92 ; node --test scripts/*.test.mjs e2e/performance/*.test.mjs 92/92 ; verifier-nommage, verifier-configuration, verifier-royaume, verifier-docs (sans --rendu) code 0 ; e2e npm run typecheck 0 ; npm audit --audit-level=high code 0 pour les trois paquets
```

*Réserve* : Protection de main déclarée active par le mainteneur (openspec/changes/archive/2026-11-24-mettre-en-place-integration-continue/tasks.md:79) mais invérifiable ici (API GitHub bloquée) ; aucun résultat d'exécution des jobs serveur et e2e n'a pu être consulté ; le Dockerfile construit toujours avec -DskipTests (Dockerfile:42) : une image construite sur le NAS à partir d'un commit hors main n'est pas vérifiée ; la publication du client desktop sur étiquette ne dépend d'aucun gate (voir AUD-DEP-30). Mesure du 28/11 sur le poste du mainteneur (worktree de 01fea6d, JDK 24, sans le profil integration-continue qui exige le JDK 17) : 1 251 tests, 1 échec propre au poste Windows (AUD-TST-32). Aucune exécution de la CI n'a pu être observée ni la protection de la branche main vérifiée (API GitHub inaccessible).

*Au 20/11* : Ouvert (Élevée) — Ouvert : toujours aucun workflow CI (.github absent) ni hook ; le Dockerfile contrôle désormais les licences mais construit toujours avec -DskipTests. 158 commits et 14 lots ont été intégrés sans gate automatique ; sept constats en dépendent. Sévérité relevée à Élevée (dette qui bloque la vérification des chantiers en cours).

*Au 21/11* : Ouvert (Élevée) — Ouvert : toujours aucun workflow ni hook ; le volume non protégé continue de croître (602 méthodes serveur, 206 tests IHM, 54 desktop, 31 tests de scripts, 34 E2E, 29 d'accessibilité).

**Preuve d'origine**

```text
ls -a /home/claude/work/argos/.github -> No such file or directory ; aucun autre fichier de pipeline. Dockerfile:15 « Les tests demandent Docker (base jetable) : ils se lancent hors de l'image avec « mvn verify » » ; Dockerfile:22 RUN mvn -B package -DskipTests
[AUD-DEP-04] Absence de .github/ (ls -la à la racine) et de tout autre fichier de CI ; .git/hooks ne contient que des *.sample (ls -la .git/hooks), core.hooksPath non défini ; .archgate/config.json `"domains": {}` et .archgate/adrs/GEN-001-example.md (« This is an example ADR ... Replace this ») ; Dockerfile:22 `RUN mvn -B package -DskipTests` ; README.md:49 `tests : mvn verify (Docker requis)` ; scripts/verifier-docs.mjs (contrôle de liens/Mermaid) lancé à la main.
[À 9d96406] Toujours aucune CI ; 363 méthodes de test serveur (décompte statique), 416 tests exécutés (mesure du mainteneur).
```

**Impact** — Les 321 méthodes de test serveur, les tests d'architecture et les 47 tests JS ne protègent que si le développeur pense à les lancer ; une image peut être construite et déployée (NAS puis service ouvert au public) avec des tests en échec ou une frontière de module violée.

**Action** — Ajouter un workflow (GitHub Actions ou équivalent) déclenché sur push/PR : mvn -B verify (runner avec Docker), npm ci && npm test && npm run typecheck dans argos-ui, npm ci && npm run check dans argos-desktop ; rendre la branche principale protégée par ce statut.

### AUD-PER-30 — L'amorçage OSV écrit jusqu'à trois archives all.zip (1 Gio chacune au plus) dans /tmp, un tmpfs compté dans le plafond mémoire de 1 Gio du conteneur argos, dont le tas peut prendre jusqu'à 60 %

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Performance · **Effort** : S · **Sprint** : 1
- **Nouveau** le 28/11
- **Bloque** : AUD-PER-32

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/osv/LectureOsv.java:117-118 (Files.createTempFile("argos-osv-", ".zip") puis telechargerVers, répertoire temporaire par défaut de la JVM) ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:64-69 (une archive par écosystème au-delà du seuil, gardées jusqu'à la fermeture de la lecture) ; argos-server/src/main/resources/application.properties:55 (Maven,npm,PyPI), :79-84 (seuil-archive=1000, archive-taille-max-octets=1073741824, « Chaque archive reste sur disque jusqu'à la fin de la synchronisation ») ; compose.yaml:246 (mem_limit ${ARGOS_MEM_LIMIT:-1g}) et :251-253 (tmpfs: /tmp, sans taille) ; Dockerfile:62 (-XX:MaxRAMPercentage=60). Le tmpfs (aa91d2c, 24/11) précède l'archive (7408b6c, 26/11) ; ni le changement rendre-ingestion-asynchrone (design.md:217, « fichier temporaire du répertoire temporaire de la JVM ») ni docs/deploiement ne mentionnent l'interaction.
```

**Impact** — Les pages d'un tmpfs sont imputées au groupe de contrôle mémoire du conteneur. Au premier passage (curseur vide), les trois écosystèmes dépassent le seuil de 1 000 avis : les archives s'ajoutent aux ~600 Mio de tas et à la mémoire hors tas. Si leur somme dépasse la marge, le noyau tue la JVM (OOM killer, pas ExitOnOutOfMemoryError). Le traitement reste RUNNING 30 minutes, puis la synchronisation horaire le reprend, retélécharge et peut retomber : l'amorçage n'aboutit pas, l'API est coupée à chaque arrêt, en silence côté application. Taille réelle des archives non mesurée ici (export OSV inaccessible par le proxy) ; seul le plafond configuré (3 × 1 Gio) est prouvé.

**Action** — Écrire les archives hors tmpfs : un volume nommé (par exemple /var/lib/argos/tmp) passé par -Djava.io.tmpdir ou une propriété argos.sources.osv.repertoire-archives ; à défaut, donner au tmpfs une taille (tmpfs: /tmp:size=…) et relever mem_limit en conséquence. Lire et supprimer chaque archive écosystème par écosystème plutôt que de garder les trois. Ajouter au guide de déploiement l'espace disque nécessaire à l'amorçage et faire l'amorçage une fois sur le NAS, relevé docker stats à l'appui. Relever docker stats pendant un amorçage sur le NAS pour confirmer l'ordre de grandeur (l'arrêt est probable mais pas certain si Docker autorise du swap).

### AUD-DEP-01 — compose.yaml ne transmet pas au conteneur 28 des 37 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et 27 manquent dans .env.example : e-mails vers localhost:587, liens vers localhost:5173

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Parité atteinte et gardée : 42 variables ${ARGOS_*} lues en %prod ou sans profil, 0 absente du service argos, 0 absente de .env.example ; le contrôle échoue sur toute régression (mutation vérifiée).

*Changement depuis le 21/11* : Bloc environment du service argos complété (42 variables) et .env.example aligné ; contrôle scripts/verifier-configuration.mjs (+ test) branché en CI (changement completer-configuration-conteneurs, PR #44 ; dernier ajustement 39a122f).

```text
node /home/claude/audit-maj/scripts-deps/compter-env.mjs /home/claude/work/argos -> lues_prod_ou_commun=42, absentes_compose=0, absentes_env_example=0 ; compose.yaml:183-241 ; node scripts/verifier-configuration.mjs -> rc=0 ; mutations dans une copie : suppression de ARGOS_SMTP_HOST de compose.yaml -> rc=1 « absente du bloc environment du service argos » ; ajout de ${ARGOS_NOUVELLE} dans application.properties -> rc=1 (3 fichiers nommés) ; tests scripts/verifier-configuration.test.mjs:57-70,172 (EXP-07-S2/S3) ; CI .github/workflows/integration-continue.yml:145-146 ; aucune lecture getenv dans argos-server/src/main/java.
```

*Réserve* : Envoi réel d'un e-mail par un relais SMTP non exécuté (matrice tasks.md du changement : « partielle (PC) ») ; le changement est encore actif et la fiche exploitation-plateforme.md:70 garde le statut [PROPOSITION].

*Au 20/11* : Ouvert (Élevée) — Aggravé : 28 variables lues en production absentes du service argos (au lieu de 23), 27 absentes de .env.example (au lieu de 22) ; doc toujours en retard.

*Au 21/11* : Ouvert (Élevée) — Inchangé : 37 variables lues en production ou sans profil, 10 clés transmises, 28 absentes du service argos, 27 absentes de .env.example (mêmes chiffres qu'à 54add78).

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

### AUD-DEP-21 — L'image Docker ne fige plus l'émetteur OIDC (lu dans /api/v1/system/info) ; le desktop résout apiUrl/oidcUrl à l'emballage, mais l'adresse API de son IHM reste figée au build (AUD-DEP-29)

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée · **Axes** : Dépendances, Architecture, Documentation · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-ARC-16, AUD-ARC-21, AUD-DOC-22

**Vérification du 28/11 (01fea6d)** — Image Docker corrigée : plus aucun émetteur figé dans le bundle ; desktop configurable et documenté, mais l'installeur publié par la CI reste figé sur localhost (AUD-DEP-29).

*Changement depuis le 21/11* : ADR-0038 mise en œuvre (50867d3) : l'IHM lit l'émetteur et le client dans GET /api/v1/system/info (argos.ui.oidc.emetteur = ${ARGOS_OIDC_ISSUER_PUBLIC:${ARGOS_OIDC_URL}}), sans valeur de repli ; le desktop résout apiUrl/oidcUrl (variable, package.json emballé, défaut) et le guide documente les deux adresses.

```text
argos-ui/src/session/configuration.ts:6, :28-52 ; application.properties:423-426 ; SystemInfoResource.java:38-41 ; compose.yaml:198 ; rejeu `VITE_ARGOS_API_URL= npm run build` (copie) puis `grep -l localhost:8180 dist/assets/*.js` -> aucun ; tests argos-ui/tests/session.test.mjs, AuthentificationTest.java (oidcIssuer), argos-desktop/test/reglages-emballes.test.js ; docs/deploiement/synology-et-pc.md:318-345.
```

*Réserve* : Connexion depuis un second poste et emballage pour un serveur distant non vérifiés (synology-et-pc.md:583) ; l'adresse API de l'IHM du desktop reste fixée au build (argos-ui/src/api/client.ts:20).

*Au 20/11* : Ouvert (Élevée) — —

*Au 21/11* : Partiel (Élevée) — Le desktop devient configurable à l'emballage ; l'IHM de l'image Docker reste figée sur l'émetteur http://localhost:8180/realms/argos : l'installation locale fonctionne parce que tout est sur localhost, la connexion reste impossible hors du poste (NAS, réseau local).

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

- **Statut** : Fermé avec réserve · **Sévérité** : Élevée · **Axes** : Dépendances, Sécurité, Qualité · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-SEC-31, AUD-QUA-21

**Vérification du 28/11 (01fea6d)** — Base keycloak sauvegardée et restaurée avant tout réimport ; scénarios couverts par l'essai scripté.

*Changement depuis le 21/11* : La sauvegarde exporte argos et, si elle existe, keycloak (même horodatage, archive vérifiée puis renommée, rotation après succès) ; la restauration recharge les deux bases avant le redémarrage de Keycloak ; guide mis à jour.

```text
scripts/sauvegarder-base.sh:128-136 ; scripts/sauvegarde-commun.sh:9, :37-52 ; scripts/restaurer-base.sh:185-194, :217-220, :221-222 ; docs/deploiement/synology-et-pc.md:69 ; scripts/essai-sauvegarde-restauration.sh:142, :212-215.
```

*Réserve* : Essai non rejoué ici (Docker Hub bloqué) et absent de la CI ; l'essai simule Keycloak par une table « utilisateur », sans Keycloak réel ; une sauvegarde sans archive keycloak restaure argos seule (base keycloak laissée en l'état, restaurer-base.sh:193).

*Au 21/11* : Ouvert (Élevée) — —

**Preuve d'origine**

```text
compose.yaml:27-28 monte deploiement/postgres/creer-base-keycloak.sh (:4 `CREATE DATABASE keycloak`) dans le même volume argos-pgdata ; compose.yaml:49 `KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak` ; scripts/sauvegarder-base.sh:22 `pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"` (base argos seule) ; docs/deploiement/synology-et-pc.md:94 restauration dans $POSTGRES_DB seulement, :29-37 guide idp-local sans mot sur la sauvegarde ; argos-server/src/main/resources/db/migration/V2__comptes_et_acces.sql:26 `sujet_oidc text NOT NULL UNIQUE` ; ComptesService.java:69-70 et :106 (compte retrouvé par sujet_oidc) ; Context7 /keycloak/keycloak (importExport) : --import-realm recrée le royaume s'il n'existe pas.
[AUD-SEC-31] scripts/sauvegarder-base.sh:22 `pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"` (base argos seule) ; compose.yaml:27-28 et :49 (base keycloak dans le même volume PostgreSQL, profil idp-local) ; deploiement/postgres/creer-base-keycloak.sh:4 ; workspace/ComptesService.java:106 (compte retrouvé par `sujet_oidc`) ; docs/deploiement/synology-et-pc.md (section « Avec Keycloak sur le même poste ») ne mentionne pas la sauvegarde de Keycloak.
[AUD-QUA-21] Commit e7ed7f3 (#28) : deploiement/postgres/creer-base-keycloak.sh:4 « CREATE DATABASE keycloak » ; compose.yaml:28 (script d'init monté) et :48-49 « KC_DB: postgres », « KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak ». scripts/sauvegarder-base.sh:22 « pg_dump ... -d "$POSTGRES_DB" » (une seule base) ; docs/deploiement/synology-et-pc.md:84 et :94 ne décrivent que la sauvegarde/restauration de la base Argos ; les comptes Argos sont rattachés au sujet OIDC (argos-server/src/main/resources/db/migration/V2__comptes_et_acces.sql:26 « sujet_oidc text NOT NULL UNIQUE »).
```

**Impact** — Après perte du volume ou migration de poste, la restauration documentée rend une base Argos intacte mais un Keycloak vide : le royaume est réimporté avec un seul compte administrateur, au nouvel identifiant sub. Tous les autres utilisateurs ont disparu du fournisseur d'identité et, même recréés, obtiennent un nouveau compte Argos (sub différent) : appartenances aux espaces, préférences et abonnements de l'ancien compte deviennent inaccessibles. L'exploitant croit être protégé par la tâche planifiée.

**Action** — Sauvegarder aussi la base keycloak (second pg_dump -d keycloak, ou pg_dumpall), documenter sa restauration (base keycloak recréée avant le premier démarrage de Keycloak pour éviter le réimport) et l'ajouter au guide idp-local ; à défaut, exporter le royaume (kc.sh export) avec la sauvegarde. Traiter en même temps AUD-QUA-02 (pipefail).

### AUD-DEP-29 — L'installeur desktop publié par la CI est construit sans adresse de serveur (IHM figée sur http://localhost:8080) et remplace par mise à jour automatique tout client emballé pour un serveur distant ; ARGOS_API_URL à l'exécution, présenté comme parade, ne change que la CSP

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Dépendances · **Effort** : M · **Sprint** : 2
- **Nouveau** le 28/11
- **Bloque** : AUD-DOC-04

**Preuve**

```text
.github/workflows/publication-desktop.yml:97 `npm --prefix argos-ui run build` sans VITE_ARGOS_API_URL et :115 electron-builder sans -c.extraMetadata.argos.apiUrl/oidcUrl ; argos-ui/src/api/client.ts:6 DEFAULT_API_URL 'http://localhost:8080', :14 variable absente -> localhost, :20 apiBaseUrl fixé au build, utilisé par tous les appels (:95) et par la lecture de l'émetteur (session.ts:143) ; argos-desktop/src/reglages-emballes.js:7-8, :17-20 et main.js:35-38, :68 : ARGOS_API_URL ne sert qu'à contentSecurityPolicy (ui-protocol.js:16-31), rien ne le transmet à l'IHM ; package.json:57-65 même appId et même canal GitHub pour tous les emballages ; docs/deploiement/synology-et-pc.md:345 « `ARGOS_API_URL` et `ARGOS_OIDC_URL` à l'exécution gardent alors l'adresse du serveur ».
```

**Impact** — À la première étiquette desktop-v*, chaque client emballé pour le NAS (cas nominal du guide, synology-et-pc.md:336-341) télécharge silencieusement l'installeur générique et l'installe à la fermeture : l'IHM appelle alors http://localhost:8080, bloqué par la CSP qui vise le NAS ; le client ne se connecte plus. La parade documentée ne fonctionne pas ; seule ARGOS_MISE_A_JOUR=off évite la casse, au prix des correctifs de sécurité.

**Action** — Faire lire l'adresse du serveur à l'exécution par l'IHM du desktop (pont preload exposant apiUrl résolu par reglages-emballes.js, ou réglage utilisateur persistant) au lieu de VITE_ARGOS_API_URL ; à défaut, désactiver la mise à jour automatique des emballages personnalisés (champ argos.miseAJour=false à l'emballage) ; corriger synology-et-pc.md:345 ; ajouter un test du desktop prouvant que l'IHM appelle l'adresse configurée.

## Moyenne (28)

### AUD-ARC-04 — Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes

- **Statut** : Fermé · **Sévérité** : Moyenne · **Axes** : Architecture, Qualité · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-QUA-03

**Vérification du 28/11 (01fea6d)** — Accord avec le suivi (tâche 3.1 cochée) : le protocole RLS n'est plus écrit qu'à un endroit, les variantes partielles et la NullPointerException ont disparu, et un test de source empêche une nouvelle copie.

*Changement depuis le 21/11* : fa11d93 « un seul point d'application du contexte RLS » : impact/RlsSession supprimée ; SubscriptionService et PreferencesRepository appellent ContexteBase.appliquer(…, Contexte.duCompte(…)) ; les quatre paramètres sont posés partout, une valeur nulle vaut la chaîne vide.

```text
grep -rn "SET LOCAL ROLE\|set_config('argos" argos-server/src/main/java -> seulement commun/ContexteBase.java:76 et :79-82 ; workspace/subscription/SubscriptionService.java:82,223,236,269,367 et workspace/preference/PreferencesRepository.java:50,77,108 « ContexteBase.appliquer(…, ContexteBase.Contexte.duCompte(…)) » ; commun/ContexteBase.java:84-87 et :91-93 (null -> "") ; tests : architecture/NoyauCommunSourceTest.java:47-51 seulLeNoyauEcritLeProtocoleRls (avec test d'exemple :55-61), commun/ContexteBaseTest.java:70-91 (quatre paramètres, compte nul).
```

*Au 20/11* : Ouvert (Moyenne) — Le protocole RLS est toujours écrit quatre fois ; les deux variantes partielles de workspace ne réinitialisent toujours ni sujet_courant ni email_courant.

*Au 21/11* : Ouvert (Moyenne) — Le protocole RLS reste écrit quatre fois ; les deux variantes de workspace ne réinitialisent toujours ni sujet_courant ni email_courant, et PreferencesRepository peut toujours lever une NullPointerException.

**Preuve d'origine**

```text
grep -rn "SET LOCAL ROLE argos_app" -> workspace/ContexteBase.java:78, impact/RlsSession.java:24, workspace/subscription/SubscriptionService.java:269, workspace/preference/PreferencesRepository.java:116. impact/RlsSession.java:14-15 « Même protocole que … ContexteBase#appliquer, que impact ne peut pas utiliser directement (ModuleBoundariesTest) ». SubscriptionService.java:272 et PreferencesRepository.java:119 ne positionnent que compte_courant et espace_courant (pas sujet_courant ni email_courant, lus par les politiques de V2__comptes_et_acces.sql).
[AUD-QUA-03] workspace/ContexteBase.java:76-91 (4 paramètres : compte, sujet, email, espace) ; impact/RlsSession.java:22-32 (copie assumée : « que impact ne peut pas utiliser directement (ModuleBoundariesTest) ») ; workspace/subscription/SubscriptionService.java:266-275 et workspace/preference/PreferencesRepository.java:114-123 (2 paramètres seulement, sujet/email non réinitialisés ; PreferencesRepository:121 « compte.toString() » lève NullPointerException si compte est nul, là où les autres copies passent "").
```

**Impact** — Toute évolution des variables ou du rôle RLS (migration V2) doit être répercutée à quatre endroits ; les deux variantes partielles laissent sujet/email hérités de la transaction. La règle de frontière a poussé à copier du code de sécurité plutôt qu'à le partager.

**Action** — Faire de ContexteBase.appliquer le seul point d'application (voir AUD-ARC-05 : le déplacer dans un module transverse autorisé à tous), supprimer RlsSession et les deux copies de workspace, et ajouter une règle ArchUnit interdisant la chaîne « set_config('argos. » hors de cette classe (test de source).

### AUD-ARC-05 — Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où cinq règles ArchUnit d'exception au cas par cas

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Architecture · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Accord avec le suivi : le noyau implicite est devenu un module transverse déclaré, sans dépendance (règle NC-3 testée), et les exceptions au cas par cas ont disparu.

*Changement depuis le 21/11* : 95473df « créer le noyau commun et retirer les règles d'exception des frontières » : paquet io.argos.commun (ContexteBase, Jdbc, erreurs du domaine, Page/Pagination) et io.argos.commun.securite (Caller, CallerResolver, LimiteurDebit) ; les cinq règles d'exception ont disparu de ModuleBoundariesTest ; ALLOWED ne donne plus workspace à signal, regintel, projectintel ni api à mcp ; ADR-0037 « Accepté ».

```text
argos-server/src/main/java/io/argos/commun/ (18 fichiers) ; ModuleBoundariesTest.java:42-80 (ALLOWED, aucune règle *NEmprunteQueRlsEtErreurs ni mcpNeLitQueLaSecuriteDeLApi), :82 TRANSVERSES, :103-110 regleNoyauSansModule, :113 leNoyauCommunNeDependDAucunModule, :119 test d'exemple ; grep -rl "import io.argos.api.security" argos-server/src/main/java/io/argos/mcp -> 0 ; docs/architecture/adr/0037-noyau-commun-du-serveur.md:10 « Statut : Accepté ». Imports restants de workspace hors workspace : métier (alerting -> SubscriptionService/PreferencesService/SuivisProfil, catalog -> LectureMembres, mcp -> InscriptionRefusee) ou api.
```

*Réserve* : catalog/package-info.java:10 cite encore la règle supprimée catalogNEmprunteQueRlsEtErreurs (AUD-ARC-25).

*Au 20/11* : Ouvert (Moyenne) — Le noyau commun implicite s'étend : chaque nouveau module (regintel) a obtenu sa propre exception au lieu d'un paquet transverse.

*Au 21/11* : Ouvert (Moyenne) — Le noyau commun implicite reste logé dans workspace et api.security, avec cinq règles d'exception au cas par cas.

**Preuve d'origine**

```text
Classes de workspace importées hors workspace : DonneeInvalide, RegleViolee, Introuvable, AccesRefuse, ContexteBase (catalog/standard/ReferentielEspace.java:23-26, signal/web/SourcesWeb.java:26-29, projectintel/ActiviteQueries.java:26-27, inventory/InventoryQueries.java:24, mcp, api). ModuleBoundariesTest.java contient 3 règles d'exception dédiées (signalNEmprunteQueRlsEtErreurs, projectintelNEmprunteQueRlsEtErreurs, catalogNEmprunteQueRlsEtErreurs) et une 4e pour mcp->api.security (mcpNeLitQueLaSecuriteDeLApi : Caller, CallerResolver, LimiteurDebit, mcp/GardeAppels.java:20-22). LimiteurDebit est instancié dans api/ActiviteResource.java:61, api/VeilleWebResource.java:90 et mcp/GardeAppels.java:74.
```

**Impact** — Chaque nouveau module qui a besoin de la RLS ou d'une erreur commune doit soit dépendre de tout workspace, soit obtenir une exception, soit dupliquer (cas impact, AUD-ARC-04). Le sens réel « workspace = cœur métier des espaces » se brouille avec « workspace = socle technique ».

**Action** — Extraire un paquet transverse io.argos.commun (ContexteBase, DonneeInvalide, RegleViolee, Introuvable, AccesRefuse) et io.argos.commun.securite (Caller, LimiteurDebit) autorisé pour tous les modules ; retirer les 4 règles d'exception et les entrées workspace/api devenues inutiles dans ALLOWED.

### AUD-ARC-22 — Les liens des alertes (signaux, radar, opportunités) visent /signaux/…, /espaces/…/radar et /profils/artiste/… : routes absentes de l'IHM (historique hash) et chemins non publics (401) ; aucune vue n'est atteignable depuis un courriel

- **Statut** : Fermé · **Sévérité** : Moyenne · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les liens visent des routes existantes, servies sous / (chemin public) : la page se charge puis ouvre la session. Concordance serveur/IHM testée des deux côtés.

*Changement depuis le 21/11* : 1ce7ff4 : alerting/LiensIhm construit tous les liens au format de l'historique hash (/#/signaux/{id}, /#/espaces/{id}/radar, /#/profils/artiste/opportunites/{id}) ; vues SignalView, RadarView, ProfilArtisteView et leurs routes ajoutées ; ARGOS_PUBLIC_URL relayé par compose.yaml et .env.example ; avertissement au démarrage si l'adresse reste localhost.

```text
argos-server/src/main/java/io/argos/alerting/LiensIhm.java:14, :17, :20 ; MoteurAlertes.java:320, RappelsOpportunites.java:152 « LiensIhm.lien(lienBase, …) » ; MoteurAlertes.java:135-137 (avertissement) ; argos-ui/src/router.ts:43-45 ; compose.yaml:149, :204 ; .env.example:34 ; tests : argos-server/src/test/java/io/argos/alerting/LiensIhmTest.java:16-30, argos-ui/tests/liens-alertes.test.mjs (chaque gabarit de LiensIhm.java a une route de router.ts).
```

*Au 21/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
alerting/MoteurAlertes.java:286 « lienBase … + "/signaux/" + signal.signalId() », alerting/RappelsRadar.java:252 « "/espaces/" + espaceId + "/radar" », alerting/RappelsOpportunites.java:153 « "/profils/artiste/opportunites/" » ; argos-ui/src/router.ts:25-40 (aucune route /signaux, /espaces/:id/radar ni /profils/…) et :52 « createWebHashHistory() » (les vues sont sous /#/…) ; application.properties:63 « argos.alerting.lien-base=${ARGOS_PUBLIC_URL:http://localhost:5173} » ; git grep ARGOS_PUBLIC_URL -> absent de compose.yaml et de .env.example ; application.properties:296-297 (/signaux/… n'est pas public : 401 sans jeton). Écart partiellement consigné : docs/specifications/alertes-digests.md:127 « la page correspondante de l'IHM reste à créer ». Tests qui figent le format : AlertesTest.java:196, :406.
```

**Impact** — Tous les liens des courriels et des alertes du poste sont morts en déploiement Docker : ils visent le serveur de développement Vite ; même avec la bonne adresse, ils tomberaient sur une réponse 401 du serveur (chemin non public, pas de repli SPA) au lieu d'une vue.

**Action** — Relayer ARGOS_PUBLIC_URL dans compose.yaml et .env.example ; construire les liens au format de l'historique hash (/#/…) vers des vues existantes (alertes/:id à défaut d'une vue signal), ou créer ces vues ; aligner AlertesTest sur ce format.

### AUD-QUA-04 — Un seul modèle transactionnel JTA : plus aucun setAutoCommit en production (six au 21/11), 99 @Transactional et 21 QuarkusTransaction, verrouillé par TransactionsJtaTest

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Un seul modèle transactionnel (JTA déclaratif ou programmatique) ; plus aucune gestion manuelle de l'autocommit en production, et une règle d'architecture l'interdit.

*Changement depuis le 21/11* : Six sites manuels migrés en JTA (commits fa11d93, efb8dba, abc05d5, 367eb19, 6217d3b) ; ContexteBase.executer ouvre une transaction QuarkusTransaction.requiringNew() et applique le contexte RLS ; règle ArchUnit ajoutée (50f7af3).

```text
grep -rn "setAutoCommit" argos-server/src/main/java -> 0 ; grep -rnE "\.(commit|rollback)\(\)" argos-server/src/main/java -> 0 ; grep -rn "@Transactional" | wc -l -> 99 ; grep -rn "QuarkusTransaction\." | wc -l -> 21 ; argos-server/src/main/java/io/argos/commun/ContexteBase.java:59-68 ; test : argos-server/src/test/java/io/argos/architecture/TransactionsJtaTest.java:32-44 (setAutoCommit, commit, rollback, rollback(Savepoint) interdits) avec exemple de violation architecture/exemples/TransactionManuelle.java.
```

*Réserve* : La gestion manuelle subsiste dans sept classes de test (ex. src/test/java/io/argos/testsupport/JeuRecherche.java:200-208, alerting/AlertesTest.java:527-541, chain/CorrelationTest.java:443-453), recopiée à l'identique : signalé à l'axe Tests.

*Au 20/11* : Ouvert (Moyenne) — Les deux modèles transactionnels coexistent toujours et le modèle manuel s'est étendu au nouveau module regintel.

*Au 21/11* : Ouvert (Moyenne) — Deux modèles transactionnels coexistent toujours ; les six copies ne sont même pas identiques (PreparationFiches ne restaure pas l'autocommit).

**Preuve d'origine**

```text
grep -rn "@Transactional" argos-server/src/main/java | wc -l -> 69 ; grep -rn "setAutoCommit" -> ContexteBase.java:55, QuotasIa.java:70, impact/RadarProjets.java:207 (dettes), impact/RapprochementActualites.java:142 (pour), releaseradar/CyclesDeVie.java:101. Même bloc recopié : « boolean autoCommit = c.getAutoCommit(); c.setAutoCommit(false); try { ... c.commit(); } catch (SQLException | RuntimeException e) { c.rollback(); throw e; } finally { c.setAutoCommit(autoCommit); } ».
[À 9d96406] aigateway/QuotasIa.java:86 (au lieu de :70) ; toujours 5 setAutoCommit(false), 69 @Transactional ; nouvelle méthode liberer() en autocommit (QuotasIa.java, portees).
```

**Impact** — Les méthodes à transaction manuelle ne fonctionnent que si aucun appelant n'ouvre de transaction JTA : avec Agroal, commit() sur une connexion enrôlée lève une SQLException. Ajouter un @Transactional sur une ressource ou un service appelant (régression facile) casse ces chemins à l'exécution seulement. Les cinq copies du même squelette augmentent le coût de maintenance.

**Action** — Choisir un modèle unique : soit tout en JTA (QuarkusTransaction.requiringNew() + ContexteRls.appliquer), soit un seul utilitaire de transaction manuelle (ContexteBase.executer généralisé et partagé) utilisé par les cinq sites ; documenter la règle dans arc42 §8 et l'outiller par un test ArchUnit (interdire setAutoCommit hors de l'utilitaire).

### AUD-QUA-17 — Fonctionnalités atteintes seulement par les tests : suggestions IA du radar jamais produites alors que leur décision est exposée en REST, collecte immédiate d'activité, trois méthodes sans appelant

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les cinq éléments sont atteints en production ou supprimés, et une règle d'architecture empêche le retour d'une méthode publique atteinte seulement par les tests.

*Changement depuis le 21/11* : Commit 1ce7ff4 : SuggestionsRadar produit les suggestions IA dans la tâche quotidienne du radar ; collecte immédiate exposée en REST ; méthodes sans appelant supprimées ; règle ArchUnit MethodesAtteintesEnProductionTest.

```text
catalog/standard/ReferentielEspace.java:148 suggerer <- impact/SuggestionsRadar.java:117 <- alerting/RappelsRadar.java:106,121 (@Scheduled argos-radar-quotidien) ; projectintel/CollecteActivite.java:199 collecterProjet <- api/ActiviteResource.java:129 ; grep -rn "peutAller\|\.parIdentifiant(\|\.importe()" argos-server/src/main/java -> 0 (supprimées) ; tests : argos-server/src/test/java/io/argos/architecture/MethodesAtteintesEnProductionTest.java:117-129, impact/SuggestionsRadarTest.java, projectintel/CollecteImmediateTest.java.
```

*Réserve* : La règle ne couvre que les méthodes publiques et compte comme « atteinte » une méthode appelée par une autre méthode elle-même morte (pas d'analyse transitive). Recherche par nom (/home/claude/audit-maj3/build-QUAL/testonly.py) : 5 méthodes de portée paquet appelées seulement par les tests, accesseurs de test sans risque (aigateway/RegistrePrompts.java:137 tous, commun/securite/LimiteurDebit.java:82 comptesSuivis, ingestion/web/MinuterieCoupure.java:43 arretee, observabilite/EvaluationTache.java:40 degrade, execution/ExecuteurBorne.java:130 arrete).

*Au 20/11* : Ouvert (Moyenne) — —

*Au 21/11* : Ouvert (Moyenne) — Les cinq éléments restent atteints seulement par les tests ou sans appelant.

**Preuve d'origine**

```text
catalog/standard/ReferentielEspace.java:136 suggerer(...) (seul producteur de suggestions, RAD-02-S2) : aucun appel dans argos-server/src/main (grep -rn "suggerer(" -> uniquement la déclaration et src/test), alors que api/RadarResource.java:152-159 expose decider() sur ces suggestions ; projectintel/CollecteActivite.java:111 collecterProjet : 0 appel en production, 13 dans src/test ; sans aucun appelant (ni main ni test) : impact/EtatImpact.java:49 peutAller, regintel/TextesOfficiels.java:116 parIdentifiant, inventory/InventoryService.java:58 ImportProjet.importe. suggerer et collecterProjet existaient déjà à 9d96406 (git grep 9d96406) : angle mort de l'audit initial.
```

**Impact** — La fonction « suggestion de l'IA sur le référentiel d'espace » est annoncée (RAD-02-S2) et sa validation par un administrateur est exposée, mais aucune suggestion ne peut naître en exploitation : écran et API vides sans que les tests le révèlent. Le code mort entretient la confusion sur ce qui est livré et alourdit la maintenance (suggerer porte en plus le LocalDate.now() sans fuseau d'AUD-QUA-10).

**Action** — Brancher suggerer sur un producteur réel (tâche IA planifiée ou outil MCP en écriture contrôlée) ou retirer la fonctionnalité et l'endpoint decider ; exposer collecterProjet (bouton « collecter maintenant ») ou le supprimer ; supprimer peutAller, parIdentifiant et importe ; ajouter au contrôle ArchUnit/CI une détection de méthodes publiques sans appelant en production.

### AUD-QUA-24 — La file d'enrichissement ne reporte pas un signal en erreur : il reste en tête de file (ORDER BY prochain_essai_le, lot de 5 toutes les 30 s), sans tentative comptée, et cinq signaux en échec persistant bloquent tout l'enrichissement KEV/NVD

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : S · **Sprint** : 3
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/vulnintel/TraitementEnrichissements.java:103-111 (boucle sur file.echus(maintenant, lot)), :122-129 (IllegalStateException/QuarkusTransactionException et RuntimeException -> Issue.EN_ERREUR sans appel à reporter() ni à file.terminer() ; seul le cas « sources indisponibles » passe par reporter(), :134) ; vulnintel/FileEnrichissement.java:47-50 « WHERE prochain_essai_le <= ? ORDER BY prochain_essai_le, signal_id LIMIT ? » ; argos-server/src/main/resources/application.properties:87-89 (cadence 30s, lot 5, tentatives-max 8, jamais atteint pour un signal en erreur) ; introduit par 7408b6c (ingestion asynchrone, V25__file_enrichissement.sql) ; argos-server/src/test/java/io/argos/chain/EnrichmentTest.java:65-126 : aucun cas EN_ERREUR ni erreur imprévue, alors qu'arc42 §8.5 (08-concepts-transverses.md:184, :200) déclare la frontière testée par EnrichmentTest.
```

**Impact** — Un signal dont l'enrichissement échoue de façon persistante (donnée inattendue, erreur de programmation) garde la même échéance : il est repris à chaque passage, occupe une des cinq places du lot, relance l'erreur toutes les 30 s et n'est jamais abandonné. Avec cinq tels signaux, plus aucun avis n'est enrichi (exploitation avérée KEV, score NVD) ; l'alerte reste dans les journaux mais la file ne se rétablit pas seule.

**Action** — Sur EN_ERREUR, appeler reporter(attente, maintenant, motif) (tentative comptée, délai croissant, abandon à tentatives-max) au lieu de laisser l'échéance intacte ; ajouter à EnrichmentTest un cas qui simule une erreur imprévue et une panne sur un signal et vérifie que les signaux suivants sont enrichis et que le fautif est reporté puis abandonné.

### AUD-QUA-25 — Six boucles planifiées restent hors de la règle des frontières d'erreur (seule la panne d'accès aux données y est capturée) : une erreur imprévue à l'intégration d'une source web la laisse échue et en tête de file, si bien qu'elle interrompt chaque passage

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 3
- **Nouveau** le 28/11
- **Dépend de** : AUD-QUA-26
- **Bloque** : AUD-DOC-25

**Preuve**

```text
docs/architecture/arc42/08-concepts-transverses.md:204 « Frontières à aligner … RappelsReglementaires, RappelsOpportunites, ResumesPeriodiques, CollecteOpportunites, CollecteWeb, CollecteReglementaire », en contradiction avec la règle 6 (:184, « Chaque frontière a un test qui simule une erreur imprévue ») ; argos-server/src/main/java/io/argos/signal/web/CollecteWeb.java:103-118 (boucle sur stock.echues), :153-162 (consignerEchecs ne capture que RecuperationRefusee et IllegalStateException ; une autre RuntimeException sort sans stock.echec, donc sans report) ; signal/web/StockActualites.java:164 « WHERE prochaine_collecte_le <= ? ORDER BY prochaine_collecte_le, id LIMIT ? » ; captures limitées à IllegalStateException : alerting/RappelsReglementaires.java:94,107 ; alerting/RappelsOpportunites.java:107,120 ; alerting/ResumesPeriodiques.java:111 ; signal/opportunite/CollecteOpportunites.java:141 ; regintel/CollecteReglementaire.java:260.
```

**Impact** — Une source web dont l'intégration (integrer, stock), une erreur de programmation ou une donnée inattendue lève une erreur non prévue garde son échéance et repasse première à chaque passage : le passage s'interrompt à chaque fois et les sources suivantes ne sont plus collectées (une erreur d'analyse de flux, elle, est déjà convertie en refus par LecteurFlux.java:48-49). Même interruption, sans blocage durable, pour les rappels réglementaires, les rappels du profil artiste, les résumés périodiques et les collectes d'opportunités et réglementaire.

**Action** — Appliquer à ces six boucles le motif déjà factorisé (capture RuntimeException par unité, LOG.errorf, comptage, ErreursImprevues puis relance) ; pour CollecteWeb, marquer la source en échec (stock.echec) avant de retenir l'erreur ; ajouter le test exigé par la règle 6 pour chacune et retirer la ligne « à aligner ».

### AUD-QUA-26 — La frontière « panne d'accès aux données » capture IllegalStateException (44 sites) et non ErreurBase : 64 traductions de SQLException lèvent encore une IllegalStateException brute, et des erreurs de programmation de même type sont absorbées sans relance

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 3
- **Nouveau** le 28/11
- **Dépend de** : AUD-QUA-06
- **Bloque** : AUD-QUA-25

**Preuve**

```text
argos-server/src/main/java/io/argos/commun/ErreurBase.java:4 « class ErreurBase extends IllegalStateException » ; grep -rn "catch (IllegalStateException" argos-server/src/main/java -> 35, plus 9 « catch (IllegalStateException | QuarkusTransactionException » ; grep -rn -A1 "catch (SQLException" | grep "throw new" -> 64 IllegalStateException contre 5 ErreurBase ; grep -rn "catch (ErreurBase" -> 2 (signal/opportunite/SourcesProfil.java:118, signal/web/SourcesWeb.java:132) ; exemples d'IllegalStateException étrangères à la base, traitées comme « panne » aux frontières : signal/SignalRecorder.java:246 (sérialisation JSON), signal/SignalQueries.java:215 (paquets affectés illisibles), signal/SignalRecorder.java:143 (signal attendu introuvable), signal/web/StockActualites.java:256 ; règle arc42 §8.5 point 2 (08-concepts-transverses.md:180 : « une erreur imprévue n'est jamais absorbée »).
```

**Impact** — La distinction prévue/imprévue, cœur de la règle « isoler puis relancer », repose sur un type JDK générique : une incohérence de données ou une erreur de programmation levée en IllegalStateException est journalisée comme panne, comptée en échec et absorbée, sans échec visible de la tâche planifiée. Inversement, rien n'empêche un nouveau site de lever une autre exception pour une vraie panne.

**Action** — Faire lever ErreurBase par toutes les traductions de SQLException (poursuivre la migration Jdbc) et ne capturer que ErreurBase (et QuarkusTransactionException) aux frontières ; écrire la convention dans arc42 §8.5 et l'outiller (ArchUnit : aucun « catch (IllegalStateException » dans io.argos hors exceptions listées).

### AUD-SEC-01 — Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion)

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Épinglage réel : plus aucune résolution par le client HTTP, à chaque saut ; tests de rebinding actifs. Le filtrage de sortie en hébergement en ligne reste non fait (Q-2, hors code).

*Changement depuis le 21/11* : Changement epingler-adresse-veille-web (PR sprint 3, ex. 72f7d94) : GardeAdresses rend une DestinationControlee (IP de l'unique résolution) ; nouveau TransportEpingle (client HTTP Vert.x) se connecte à ces IP, nom d'origine pour Host, SNI et vérification du certificat ; redirections recontrôlées.

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:122 (seule résolution) et :116 ; RecuperateurWeb.java:138, :149 (redirection recontrôlée), :164 ; TransportEpingle.java:144-151 (setServer(InetSocketAddress(IP)), setHost(nom), setSsl), :66-69 (setVerifyHost, setForceSni) ; tests TransportEpingleTest#rebindingSansEffetSurLaConnexion, #rebindingApresRedirectionSansEffet, #aucuneResolutionParLeTransport, #certificatVerifiePourLeNomDHote (hors tests-docker.txt, donc exécutés par la CI).
```

*Réserve* : Les tests construisent des InetAddress littérales (sans nom) alors qu'en production InetAddress.getAllByName rend des adresses portant le nom : l'absence de résolution repose sur Vert.x, qui se connecte à l'IP d'une SocketAddress résolue ; aucun test avec une InetAddress nommée. Filtrage de sortie (169.254.0.0/16, plages privées) à prévoir au déploiement public (proposal.md:37, Q-2 non tranchée).

*Au 20/11* : Ouvert (Moyenne) — La résolution vérifiée et la résolution de connexion restent distinctes ; aucun épinglage d'adresse ni test de rebinding.

*Au 21/11* : Ouvert (Moyenne) — La résolution vérifiée et la résolution de connexion restent distinctes ; pas d'épinglage d'adresse ni de test de rebinding.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:103 `adresses = resolveur.resoudre(hote);` puis RecuperateurWeb.java:89 `URI courante = garde.verifier(...)` et RecuperateurWeb.java:189 `client.send(requete, ...)` : HttpClient résout de nouveau le nom lui-même (aucune connexion à l'IP validée). Seul le cache DNS par défaut de la JVM (30 s, java.security : `#networkaddress.cache.ttl=-1` non défini) réduit la fenêtre. Aucun test de rebinding (grep 'rebind' dans src/test : rien).
```

**Impact** — Un compte authentifié contrôlant un DNS à TTL court peut, en déclenchant l'ajout ou la collecte d'une source (10/min), tenter de faire viser par Argos une adresse privée sur les ports 80/443 : interface DSM du NAS, réseau local, ou 169.254.169.254 en hébergement en ligne. La fenêtre est étroite : GardeAdresses et HttpClient partagent le cache DNS de la JVM (30 s), il faut que l'expiration tombe entre la vérification et la connexion. À repasser Élevée à l'ouverture au public.

**Action** — Épingler l'adresse validée : résoudre une fois, vérifier toutes les IP, puis se connecter à cette IP (socket/SSLSocket avec SNI et Host d'origine, ou client HTTP avec résolveur personnalisé type OkHttp Dns / Apache DnsResolver) ; refaire de même à chaque redirection. Ajouter un test unitaire avec un résolveur qui change de réponse entre deux appels. En hébergement en ligne, bloquer aussi en sortie (pare-feu/egress) les plages privées et 169.254.0.0/16.

### AUD-SEC-03 — Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Sécurité, Performance · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-PER-08

**Vérification du 28/11 (01fea6d)** — Rétention et bornage en place pour les trois journaux, testés.

*Changement depuis le 21/11* : Sprint 9 (e7629dd, 7f93d3f) : purge quotidienne PurgesConservation avec règles refus_acces, mcp_appel, ia_appel (180 j) ; JournalRefus borné par auteur (30/min, sujet OIDC haché), surplus compté par une métrique.

```text
argos-server/src/main/java/io/argos/audit/ConservationJournaux.java:28,35,42 ; conservation/PurgesConservation.java:73 (@Scheduled argos-purge-conservation) ; application.properties:257-259 (P180D) et :269 (argos.audit.refus.par-minute=30) ; audit/JournalRefus.java:66 (limiteur.admettre avant INSERT :74) ; tests conservation/ConservationTest#journauxDeLAudit, audit/JournalRefusTest, architecture/ConservationCompleteTest.
```

*Réserve* : ADR-0041 encore au statut « Proposé » (docs/architecture/adr/0041-conservation-des-donnees-journalisees.md:10) alors que le code est livré. Le nombre de sujets distincts reste non borné si le royaume ouvre l'inscription (registrationAllowed=false dans le royaume livré). Relecture du 28/11 : le reste d'AUD-PER-08 (fusionné) réapparaît en AUD-PER-35 (alerte et signal_version hors conservation).

*Au 20/11* : Ouvert (Moyenne) — Aucune purge ni bornage des journaux ; la surface d'écriture sans compte Argos s'élargit.

*Au 21/11* : Ouvert (Moyenne) — Toujours aucune rétention ni bornage cumulé des journaux de refus, d'appels MCP et d'appels IA.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/GestionErreurs.java:51 `journal.enregistrer(null, utilisateur.sujet(), ressource(requete), "INSCRIPTION_SUR_INVITATION");` ; V2__comptes_et_acces.sql:68 `CREATE TABLE refus_acces (... sujet_oidc text ...)` ; tables mcp_appel (V9) et ia_appel (V13) de même ; `grep -rniE 'DELETE FROM (refus|mcp_appel|ia_appel)|purge' src/main` : seule PurgeActivite existe (activité des projets). Le limiteur de débit est par compte Argos : un sujet sans compte n'est pas limité.
[AUD-PER-08] Seule purge planifiée : argos-server/src/main/java/io/argos/projectintel/PurgeActivite.java:32 -> ActiviteStore.java:157-161 (activite_compteur, activite_jalon). grep -rln 'DELETE FROM' main : aucune suppression sur mcp_appel, refus_acces, ia_appel, alerte, sync_run, sync_attempt, ops_event, ingestion_failure, signal_version ni actualite. Inserts : argos-server/src/main/java/io/argos/audit/JournalAppelsMcp.java:44, JournalRefus.java:37, JournalAppelsIa.java:44 ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:100 « raw_content text NOT NULL » (JSON brut de chaque version de chaque avis) et :60 (ingestion_failure.raw_content).
[À 9d96406] Inchangé pour refus_acces/mcp_appel/ia_appel ; s'y ajoute opportunite (V15__profil_artiste.sql), sans aucun DELETE FROM opportunite.
```

**Impact** — En service ouvert (inscription Keycloak libre, mode invitation côté Argos), n'importe qui peut faire croître sans limite refus_acces (déni de service par stockage sur le NAS) ; les journaux conservent indéfiniment des identifiants personnels (sujet OIDC), contraire à la minimisation RGPD.

**Action** — Purge planifiée des journaux (ex. 90 jours à 1 an, paramétrable) ; limiter les écritures de refus par sujet (fenêtre glissante, comme LimiteurDebit) ; limiter aussi en amont par IP au reverse proxy.

### AUD-SEC-05 — Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne (était Faible le 20/11) · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — En-têtes posés sur l'IHM et l'API, testés.

*Changement depuis le 21/11* : Sprint 6 (rendre-portail-deployable, dab815c) : filtre Vert.x EnTetesSecurite (priorité 9000, couvre statiques et 401) posant CSP, nosniff, Referrer-Policy no-referrer, frame-ancestors 'none' ; HSTS documenté au reverse proxy DSM.

```text
argos-server/src/main/java/io/argos/api/security/EnTetesSecurite.java:42-52, :68-78 ; tests api/security/EnTetesSecuriteTest et EnTetesSecuriteUnitaireTest ; docs/deploiement/synology-et-pc.md:196 (HSTS au proxy).
```

*Réserve* : CSP sans form-action (non couvert par default-src) et avec style-src 'unsafe-inline' (aligné sur le desktop) ; HSTS dépend d'une configuration manuelle de DSM.

*Au 20/11* : Ouvert (Faible) — Toujours aucune CSP, nosniff, Referrer-Policy ni frame-ancestors servis par Quarkus.

*Au 21/11* : Ouvert (Moyenne) — Toujours aucune CSP, nosniff, Referrer-Policy ni frame-ancestors servis par Quarkus, alors que le portail web est désormais livré.

**Preuve d'origine**

```text
argos-server/src/main/resources/application.properties : `grep -n 'quarkus.http.header' application.properties` ne renvoie rien ; aucune CSP, X-Content-Type-Options, Referrer-Policy, frame-ancestors ni HSTS côté serveur (la CSP n'existe que dans le desktop, argos-desktop/src/ui-protocol.js).
```

**Impact** — L'IHM web n'a pas de défense en profondeur contre l'injection de script ni le clickjacking ; en accès direct (ARGOS_BIND=0.0.0.0) rien n'impose HTTPS. Risque borné : aucun v-html dans l'IHM (voir constat Info).

**Action** — Ajouter via quarkus.http.header.* (ou filtre) : Content-Security-Policy alignée sur celle du desktop, X-Content-Type-Options: nosniff, Referrer-Policy: no-referrer, frame-ancestors 'none' ; HSTS au reverse proxy DSM.

### AUD-SEC-22 — Demande d'évolution : tout compte peut faire interroger GitHub/GitLab avec les jetons du serveur avant tout contrôle d'expertise (oracle d'existence sur dépôts privés, 10 appels/min/compte)

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Un non-expert ne déclenche plus aucune requête vers la forge ; testé.

*Changement depuis le 21/11* : Sprint 6 (dab815c) : controlerAvantForge (visibilité, expertise, état IMPACT_PRODUIT) avant tout appel à la forge, nouveau contrôle dans la transaction d'écriture.

```text
argos-server/src/main/java/io/argos/regintel/ExpertiseFiches.java:181 (controlerAvantForge) puis :184 (demandes.etat), :229-244 (404/403 journalisés) ; api/reglementaire/ReglementaireResource.java:319-323 ; test MatriceRolesTest#demandeDEvolutionSansRequeteVersLaForgePourLesAutres (forge simulée : 0 requête hors experte).
```

*Réserve* : Un expert légitime garde un oracle d'existence de tickets privés avec le jeton du serveur (DEMANDE_INTROUVABLE, ExpertiseFiches.java:184-185), borné par le limiteur ; le même défaut de principe, aggravé, existe sur les dépôts de projet : AUD-SEC-34.

*Au 20/11* : Ouvert (Moyenne) — —

*Au 21/11* : Ouvert (Moyenne) — La forge est toujours interrogée avec le jeton du serveur avant le contrôle de visibilité et d'expertise ; réponse différenciée DEMANDE_INTROUVABLE.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/reglementaire/ReglementaireResource.java:315-320 (`access.authenticated()` seul, puis `expertise.associerDemande`) ; argos-server/src/main/java/io/argos/regintel/ExpertiseFiches.java:175-186 : `demandes.etat(d)` est appelé avant `travailler(...)` (:188, contrôle de visibilité et d'expertise à :218-223) ; le contrôle préalable :178 `fiches.detail(compte, id).filter(FicheAnalyse::peutExpertiser).ifPresent(...)` ne lève rien pour une fiche invisible, d'un non-expert ou inexistante ; argos-server/src/main/java/io/argos/scm/DemandesEvolution.java:119 (jeton ARGOS_GITHUB_TOKEN/ARGOS_GITLAB_TOKEN envoyé), :121-123 (404 forge -> vide) -> ExpertiseFiches.java:181-182 réponse 400 DEMANDE_INTROUVABLE, alors qu'une demande existante poursuit vers 404/403. Aucun test du cas non-expert (`grep -rn demande-evolution argos-server/src/test` : seulement VeilleReglementaireTest.java:326-341, parcours de l'expert).
```

**Impact** — Avec un UUID quelconque, n'importe quel compte Argos (service public : inscription sur invitation mais comptes nombreux) distingue « ticket/MR existant et lisible par le jeton du serveur » (404) de « inexistant ou inaccessible » (400) sur toute forge configurée : énumération de dépôts privés et de numéros de tickets accessibles au jeton du mainteneur. Les appels consomment aussi le quota d'API du jeton partagé avec les imports d'inventaire et la collecte d'activité (QuotaEpuise reporte ces collectes) : 10/min/compte, cumulables entre comptes.

**Action** — Dans associerDemande, exiger la fiche visible, l'expertise et l'état « impact produit » (lecture sous ContexteBase, refus journalisé) AVANT tout appel à la forge ; ne pas différencier la réponse selon le résultat de la forge pour un appelant non autorisé ; ajouter un test : non-membre et membre non expert -> 404/403 sans requête vers le StubServer de forge.

### AUD-SEC-29 — Royaume Keycloak livré pour l'installation (PR #28) sans protection contre la force brute ni politique de mot de passe, avec sslRequired « none » et des redirections à joker en http

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Royaume livré durci et contrôlé en CI ; TLS « all » seulement par une étape manuelle documentée.

*Changement depuis le 21/11* : Sprint 6 (rendre-portail-deployable, 50867d3) : force brute (5 échecs, 60 s incrémental, 15 min max), politique length(12)+notUsername+notEmail+passwordHistory(3), sslRequired « external », redirections exactes ${ARGOS_PUBLIC_URL}/ substituées à l'import, contrôle CI du fichier.

```text
deploiement/keycloak/argos-realm.json:7 (external), :9-13 (force brute), :14 (passwordPolicy), :45-48 et :55 (sans joker) ; scripts/verifier-royaume.mjs:67-68 (job outils « Royaume Keycloak livré ») ; docs/deploiement/synology-et-pc.md:196, :232 (passer à « all » avant d'exposer).
```

*Réserve* : « external » ne protège rien derrière le reverse proxy DSM : Keycloak voit l'adresse privée du proxy, et compose.yaml:130-149 ne pose pas KC_PROXY_HEADERS ; seule la consigne manuelle (synology-et-pc.md:196) l'évite. Tests d'intégration Keycloak (blocage, politique) « non exécuté » (openspec/changes/archive/2026-11-25-rendre-portail-deployable/tasks.md:57-59). Un royaume déjà importé n'est pas mis à jour (import au premier démarrage, compose.yaml:126).

*Au 21/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
deploiement/keycloak/argos-realm.json:1-108 : aucune clé bruteForceProtected ni passwordPolicy (`grep -n 'bruteForce\|passwordPolicy' deploiement/keycloak/argos-realm.json` : rien), alors que la documentation Keycloak 26.7.5 (Context7) indique que la détection de force brute est « désactivée par défaut » et qu'un royaume créé n'a aucune politique de mot de passe ; :7 `"sslRequired": "none"` (option que la documentation réserve au développement) ; :40 `"http://localhost:8088/*"` et :49 `post.logout.redirect.uris` en `http://localhost:8088/*` (joker, http) ; :96-104 seul compte, l'administrateur plateforme (ARGOS_ADMIN_EMAIL), mot de passe provisoire `temporary: true`, donc mot de passe définitif choisi à la première connexion sans contrainte ; compose.yaml:43 `start --import-realm` (import au premier démarrage seulement : ces réglages persistent ensuite), :52 `KC_HTTP_ENABLED: "true"`, :54 `KC_HOSTNAME: http://localhost:...`. ADR-0027:70 prévoit justement une « revue de sécurité de la configuration du royaume (durées des jetons, MFA, politique de mot de passe) », non faite. Aucun test ne charge ce fichier (`grep -rn deploiement/keycloak` dans les sources de test : rien).
```

**Impact** — En installation locale telle que livrée, Keycloak n'écoute que sur 127.0.0.1 (compose.yaml:64) et le risque reste local. Dès que le royaume est rendu joignable pour le NAS (reverse proxy DSM, VPN) puis pour le service public, ce qui est la cible déclarée, le compte administrateur plateforme d'Argos, qui crée les espaces et invite les comptes, peut être attaqué par essais de mots de passe sans limite ni verrouillage, avec un mot de passe éventuellement trivial. sslRequired=none laisse aussi Keycloak accepter des connexions en clair depuis des adresses publiques (identifiants et cookies de session SSO en clair si le proxy est mal réglé). Les redirections à joker sont à faible risque tant qu'elles visent localhost, mais elles seraient recopiées telles quelles pour le portail. À repasser Élevée à l'ouverture au public.

**Action** — Dans argos-realm.json : `"bruteForceProtected": true` (avec failureFactor, waitIncrementSeconds, maxFailureWaitSeconds), `"passwordPolicy": "length(12) and notUsername and notEmail and passwordHistory(3)"` (au minimum), `"sslRequired": "external"` ; redirections exactes (`http://localhost:8080/`, port aligné sur ARGOS_PORT) sans joker, de même pour post.logout ; documenter dans synology-et-pc.md le passage à KC_HOSTNAME en https et `sslRequired: all` derrière le reverse proxy ; ajouter un test (ou un script de contrôle) qui lit le fichier et vérifie ces clés ; envisager l'OTP obligatoire pour l'administrateur plateforme.

### AUD-SEC-30 — Keycloak et Argos partagent le même compte PostgreSQL, superutilisateur de l'instance : la compromission de l'un donne la base de l'autre, clés de signature du royaume comprises, et la RLS d'Argos ne tient qu'au SET LOCAL ROLE

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Sécurité, Dépendances · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-DEP-26

**Vérification du 28/11 (01fea6d)** — Comptes distincts et non superutilisateurs, testés par RolesBaseTest.

*Changement depuis le 21/11* : Sprint 2 (6cc2a4f, ADR-0035) : superutilisateur d'administration séparé ; rôle keycloak propriétaire de sa seule base ; propriétaire argos NOSUPERUSER pour Flyway ; rôle de connexion NOSUPERUSER NOBYPASSRLS, argos_app sans héritage ; CONNECT retiré à PUBLIC.

```text
deploiement/postgres/initialiser-bases.sh:32-47, :56-63 ; compose.yaml:60-68, :133-134, :187-190 ; application.properties:15-16 (Flyway sous ARGOS_DB_OWNER) ; test api/comptes/RolesBaseTest (connexionsCroiseesRefusees, aucunSuperutilisateurApplicatif, abandonDuRoleRestreintSansEffet, schemaHorsDePorteeDuRoleDeConnexion).
```

*Réserve* : Le conteneur argos reçoit aussi le mot de passe du propriétaire (compose.yaml:189-190) : une exécution de code dans Argos donne le propriétaire, qui échappe à la RLS et peut la désactiver (ALTER TABLE … DISABLE ROW LEVEL SECURITY) ; la base Keycloak reste hors d'atteinte. ADR-0035 au statut « Proposé » (docs/architecture/adr/0035-comptes-et-isolation-des-bases.md:10). Installations existantes : migration depuis le compte partagé non outillée.

*Au 21/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
compose.yaml:19 `POSTGRES_USER: ${ARGOS_DB_USER:-argos}` (l'image officielle postgres crée ce rôle en superutilisateur), :50-51 `KC_DB_USERNAME: ${ARGOS_DB_USER:-argos}` / `KC_DB_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` (PR #28, e7ed7f3), :91-92 même compte pour Argos ; deploiement/postgres/creer-base-keycloak.sh:4 `CREATE DATABASE keycloak` exécuté par ce même rôle (propriétaire) ; V2__comptes_et_acces.sql:170-178 (« L'utilisateur de connexion (souvent superutilisateur ou propriétaire, donc exempté de RLS) bascule sur argos_app par SET LOCAL ROLE ») ; workspace/ContexteBase.java:78 et impact/RlsSession.java:24 `SET LOCAL ROLE argos_app`. La connexion d'Argos en superutilisateur existait avant le delta mais n'avait pas été relevée ; le partage avec Keycloak est nouveau.
[AUD-DEP-26] compose.yaml:19-20 POSTGRES_USER/POSTGRES_PASSWORD = ARGOS_DB_USER/ARGOS_DB_PASSWORD (superutilisateur créé par l'image postgres) ; compose.yaml:50-51 `KC_DB_USERNAME: ${ARGOS_DB_USER:-argos}`, `KC_DB_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` ; deploiement/postgres/creer-base-keycloak.sh:4 `CREATE DATABASE keycloak` sans OWNER ni rôle dédié ; V2__comptes_et_acces.sql:171-176 (Argos s'appuie sur la RLS via SET LOCAL ROLE argos_app, qu'un superutilisateur contourne).
```

**Impact** — Défense en profondeur rompue entre le fournisseur d'identité et l'application : une injection SQL ou une exécution de code dans Argos (ou dans Keycloak) ouvre la base de l'autre. Depuis Argos, un attaquant lirait les tables de Keycloak, dont les empreintes de mots de passe et les clés privées de signature du royaume (stockées en base), et pourrait alors forger des jetons valides pour n'importe quel compte, administrateur plateforme compris. Une session superutilisateur peut aussi annuler le rôle (RESET ROLE) et contourner toute la RLS, ou exécuter des commandes dans le conteneur PostgreSQL (COPY ... TO PROGRAM). Le constat suppose une première faille : aucune injection n'est connue à ce jour (AUD-SEC-11).

**Action** — Créer dans creer-base-keycloak.sh un rôle dédié `keycloak` (LOGIN, NOSUPERUSER, mot de passe propre ARGOS_KEYCLOAK_DB_PASSWORD obligatoire), propriétaire de la seule base keycloak, avec `REVOKE CONNECT ON DATABASE keycloak FROM PUBLIC` ; pour Argos, séparer le propriétaire des migrations (Flyway) d'un rôle de connexion applicatif NOSUPERUSER NOBYPASSRLS membre de argos_app ; ou, plus simple, donner à Keycloak sa propre instance PostgreSQL.

### AUD-SEC-33 — Mise à jour automatique du desktop sans authenticité : installeurs non signés, vérification Authenticode sautée faute de publisherName, empreinte SHA-512 servie par la même release

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : M · **Sprint** : 2
- **Nouveau** le 28/11

**Preuve**

```text
argos-desktop/src/mise-a-jour.js:52-53 (autoDownload, autoInstallOnAppQuit) et :60 (checkForUpdates au démarrage, Windows et Linux) ; argos-desktop/package.json « build.win » sans sign/publisherName ni verifyUpdateCodeSignature, « publish » GitHub ; .github/workflows/publication-desktop.yml:108-115 (sans ARGOS_CSC_LINK : CSC_IDENTITY_AUTO_DISCOVERY=false, installeur non signé) ; d'après la documentation electron-builder (Context7, « How the updater checks the certificate ») : « If app-update.yml has no publisherName at all, verification is skipped » ; latest.yml (SHA-512) est déposé dans la même release que l'installeur (publication-desktop.yml:115) ; l'attestation Sigstore (:121-124) n'est vérifiée par aucun code du client ; ADR-0042:96 affirme « une mise à jour altérée est refusée par le client (empreinte SHA-512) ». Job « installeurs » : contents: write et id-token: write pour toutes ses étapes (:74-78), dont la construction de l'IHM par ses dépendances npm (:97).
```

**Impact** — Quiconque peut écrire une release du dépôt (compte ou jeton du mainteneur compromis, dépendance de build compromise exécutée dans le job qui détient contents: write) remplace l'installeur et son latest.yml : chaque poste Windows et Linux télécharge et installe ce code à la fermeture d'ARGOS, sans avertissement. L'empreinte SHA-512 ne protège que contre la corruption en transit, pas contre une release altérée ; l'ADR-0042 décrit une garantie que le code n'apporte pas.

**Action** — Signer les installeurs Windows (certificat, ou SignPath si éligible) pour que publisherName soit écrit et vérifié ; d'ici là, ne pas installer automatiquement (autoInstallOnAppQuit=false, notification seule) ou vérifier dans le client une signature détachée (clé publique embarquée) des fichiers latest*.yml. Séparer dans le workflow la construction (contents: read, sans id-token) de la publication et de l'attestation ; protéger les étiquettes desktop-v* et utiliser un environnement GitHub avec approbation. Corriger la phrase de l'ADR-0042:96.

### AUD-SEC-35 — Import de SBOM et de manifestes sans limite de débit ni plafond de composants : corps de 24 Mio lu en mémoire et analysé en arbre JSON à chaque appel, recorrélation déclenchée

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : S · **Sprint** : 1
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/api/InventoryResource.java:180-183 (importerSbom, byte[] entier) et :195-197 (importerManifestes) : aucun LimiteurDebit, contrairement à refresh (:211) ; application.properties:97 (argos.inventory.max-document-bytes=20971520) et :100 (quarkus.http.limits.max-body-size=24M) ; inventory/CycloneDxParser.java:57 (MAPPER.readTree du document entier) ; inventory/ManifestParser.java:50 ; aucun plafond du nombre de composants (`grep -n -i max inventory/EcritureComposants.java inventory/InventoryStore.java` : rien) ; impact/CorrelationEngine.java:90 (recorrélation après chaque remplacement) ; tout compte peut devenir contributeur d'un espace qu'il crée (EspacesResource.java:71) ; compose.yaml:246 (mem_limit 1g) et Dockerfile:62 (ExitOnOutOfMemoryError).
```

**Impact** — Un compte authentifié peut enchaîner en parallèle des imports de 20 Mio (arbre Jackson plusieurs fois plus gros que le document, insertion de dizaines de milliers de composants, recorrélation) et pousser la JVM du NAS (1 Gio) vers l'arrêt sur OutOfMemoryError ou saturer la base. Chaque refus écrit en outre une ligne d'échec (conservée 180 jours). Exploitation non démontrée ici (pas d'exécution), risque d'indisponibilité sous condition d'un compte.

**Action** — Appliquer un LimiteurDebit par compte aux routes sbom et manifests (comme refresh) ; borner le nombre de composants et la profondeur (StreamReadConstraints, ou lecture en flux) ; abaisser max-body-size au besoin réel ; limiter le nombre de projets et de dépôts par espace.

### AUD-TST-05 — Matrice point d'entrée × rôle versionnée (103 opérations, 6 rôles) tirée de /q/openapi

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : matrice exhaustive et fermée ; POST /ops/sources/{id}/synchronizations et PUT …/conditions-usage ont désormais leur refus 403 vérifié pour tous les rôles non exploitants.

*Changement depuis le 21/11* : d6ea442 (changement 2026-11-25-durcir-autorisations-et-debits, tâche 5.1) : MatriceRolesTest énumère les opérations de /q/openapi et appelle chacune avec 6 rôles (ANONYME, LECTEUR, ADMIN_ESPACE, EXPLOITANT, AUTRE_ESPACE, EXPERT), codes attendus versionnés dans src/test/resources/securite/matrice-roles.tsv (103 opérations) ; une opération absente ou une ligne orpheline fait échouer le test ; aucune réponse 5xx admise.

```text
api/comptes/MatriceRolesTest.java:48-66, :203 (GET /q/openapi), :221-222 (opérations manquantes ou disparues), :226 (@TestFactory), :244 (code < 500) ; argos-server/src/test/resources/securite/matrice-roles.tsv:35 (POST /api/v1/ops/sources/{id}/synchronizations 401 403 403 404 403 403) ; tests-docker.txt (classe @QuarkusTest, exécutée par mvn verify en CI)
```

*Réserve* : 19 des 103 opérations reçoivent 404 pour les cinq rôles authentifiés (ressource inexistante) : la matrice ne prouve pour elles que le 401 anonyme, pas le cloisonnement (voir AUD-SEC-37) ; la matrice de suivi indique encore « non exécuté » (openspec/changes/archive/2026-11-25-durcir-autorisations-et-debits/tasks.md:67) ; le point d'entrée /mcp, hors OpenAPI, n'est pas dans la matrice (couvert par les tests MCP).

*Au 20/11* : Ouvert (Moyenne) — Contrôle de rôle impératif toujours sans matrice, sur une surface en hausse de 37 %.

*Au 21/11* : Ouvert (Moyenne) — Contrôle de rôle impératif toujours sans matrice ; 5 des 11 opérations d'exploitation sans test de refus.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-61 (@POST @Path("/{id}/synchronizations") … access.operator();) ; grep -rn synchronizations src/test/java -> 0 ; seul refus 403 « exploitation » vérifié : chain/ResilienceTest.java:151 Api.en("jules").when().get("/api/v1/ops/sources").then().statusCode(403); AuthentificationTest.java:21-22 ne teste le 401 que sur 6 chemins
[À 9d96406] SourceOperationsResource.java:57-63 toujours non testé ; PUT /api/v1/profils/artiste/sources/{id}/conditions-usage sans test de 403 (ProfilArtisteTest.java:306-308 ne couvre que POST/GET sources et POST collectes).
```

**Impact** — L'oubli d'un appel access.*() sur un nouveau point d'entrée (ou sur un existant non testé) donnerait une élévation de privilège qu'aucun test ne détecte ; le 401 global est assuré par quarkus.http.auth.permission (application.properties:217-218) mais pas le contrôle de rôle.

**Action** — Ajouter un test paramétré qui énumère toutes les opérations du document OpenAPI (/q/openapi) et vérifie pour chaque rôle (anonyme, membre lecteur, administrateur d'espace, exploitant, autre espace) le code attendu ; échec si une opération n'est pas déclarée dans la matrice.

### AUD-PER-01 — Lancement manuel en tâche de fond (202) et amorçage OSV par archive ; les trois index modified_id.csv restent relus chaque heure

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne (était Élevée le 17/11) · **Axes** : Performance · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Le lancement manuel ne retient plus le fil HTTP et l'amorçage OSV passe par l'archive ; les attentes de reprise dorment désormais sur le fil de fond (ou du planificateur), plus sur un fil de requête.

*Changement depuis le 21/11* : Changement rendre-ingestion-asynchrone (7408b6c, 26/11) : le POST répond 202 avec l'identifiant du traitement, poursuivi sur l'exécuteur borné LancementSynchronisation ; 409 SYNCHRONISATION_EN_COURS ou LANCEMENT_SATURE ; OSV lu par l'archive all.zip au-delà de 1 000 avis par écosystème, repli API.

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:74-84 (@ResponseStatus(202), operations.lancer) ; argos-server/src/main/java/io/argos/signal/SourceOperations.java:75-84 ; argos-server/src/main/java/io/argos/ingestion/LancementSynchronisation.java:40-52 ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:60-79 (seuilArchive -> lecture.archiver) ; argos-server/src/main/java/io/argos/ingestion/osv/LectureOsv.java:72-78, :113-130 ; argos-server/src/main/resources/application.properties:73-75, :83-84. Tests : argos-server/src/test/java/io/argos/chain/LancementSynchronisationTest.java:73 (col12S1LancementAccepteAvantLaFinDuTraitement), :92 (col12S2), :108 (col12S3AttenteDeRepriseExclusive) ; argos-server/src/test/java/io/argos/ingestion/osv/OsvAdvisoryFeedTest.java:108, :127, :138.
```

*Réserve* : 1) L'archive est écrite dans /tmp, un tmpfs compté dans le plafond mémoire du conteneur : nouveau constat AUD-PER-30 (Élevée). 2) Le traitement n'est pas rafraîchi pendant la lecture des index et le téléchargement des archives (contrainte seulement écrite en commentaire, argos-server/src/main/resources/application.properties:79-82) : AUD-PER-32. 3) Les trois index modified_id.csv complets sont relus chaque heure (lecture en flux, bornée à 64 Mio chacun). 4) tasks.md:29 du changement archivé cite un test OsvArchiveTest qui n'existe pas ; le test réel est OsvAdvisoryFeedTest.

*Au 20/11* : Partiel (Moyenne) — La collecte régulière existe et ne passe plus par une requête HTTP ; en revanche le lancement manuel reste synchrone, OSV est toujours lu avis par avis et les reprises dorment toujours sur le fil qui synchronise.

*Au 21/11* : Partiel (Moyenne) — La collecte planifiée tient ; le lancement manuel reste synchrone dans la requête HTTP, OSV est toujours lu avis par avis et les attentes de reprise dorment toujours sur le fil qui synchronise.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:58-63 (« Lance une synchronisation de la source et attend son résultat » -> operations.synchronize(id)) ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:110-111 « for (FeedEntry entry : feed.changesSince(position)) { RawAdvisory raw = feed.fetch(entry); » et :77 « sleeper.sleep(delay); » ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:60 (index modified_id.csv complet par écosystème) et :89 (GET /v1/vulns/<id> par avis). Aucun @Scheduled pour l'ingestion (grep @Scheduled : seuls CollecteWeb, CollecteActivite, PurgeActivite, EnvoiAlertes, RappelsRadar, ResumesPeriodiques).
```

**Impact** — Au premier passage, chaque avis de l'index de Maven, npm et PyPI donne un appel HTTP séquentiel à api.osv.dev, suivi (même fil, observateurs AFTER_SUCCESS) de l'enrichissement, de la corrélation et des alertes. Le fil de travail HTTP reste occupé pendant des heures ; les reprises (backoff jusqu'à PT5M, 5 tentatives) dorment sur ce fil. Le reverse proxy DSM ou le client coupe la requête bien avant la fin, sans que le traitement s'arrête. Sur le NAS, cela empêche aussi de planifier une collecte régulière.

**Action** — Lancer la synchronisation en tâche de fond (@Scheduled avec concurrentExecution=SKIP, ou ManagedExecutor) et faire répondre 202 avec l'identifiant du sync_run ; amorcer OSV avec l'archive par écosystème plutôt qu'un GET par avis. Le découplage de l'enrichissement relève d'AUD-PER-02.

### AUD-PER-03 — Corrélation par projet en N+1 aggravé : une transaction et une relecture du signal par signal, puis 3 à 5 instructions SQL par composant correspondant (rattachement, état précédent, upsert, transitions)

- **Statut** : Fermé · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Le N+1 a disparu : verrou, rattachement, lecture des existants, upsert, réouverture et résolution sont chacun une instruction par lot. Un test d'intégration compare le nombre d'instructions pour 2 et 20 signaux.

*Changement depuis le 21/11* : 331f1d9 puis 9beac79 (sprint 10) : CorrelationDeterministe corrèle un lot de signaux en un nombre fixe d'instructions ensemblistes (unnest) ; correlateProject découpe en lots de argos.correlation.taille-lot = 500, une transaction par lot.

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:106-118 ; argos-server/src/main/java/io/argos/impact/CorrelationDeterministe.java:99-123 ; argos-server/src/main/java/io/argos/impact/ImpactsEnLot.java:58-66, :73-101, :104-122, :131-176, :179-193 ; argos-server/src/main/resources/application.properties:111 ; test argos-server/src/test/java/io/argos/chain/RequetesConstantesTest.java:133-145 (cor13S1CorrelationEnNombreFixeDInstructions, compteur argos-server/src/test/java/io/argos/testsupport/CompteurInstructions.java) exécuté par mvn verify dans .github/workflows/integration-continue.yml (job serveur).
```

*Au 20/11* : Ouvert (Moyenne) — Le N+1 tient toujours et s'est aggravé : une transaction et une relecture du signal par signal, puis au moins 3 instructions préparées dans la boucle pour chaque composant correspondant, au lieu d'une seule.

*Au 21/11* : Ouvert (Moyenne) — Le N+1 tient : une transaction, un verrou et une relecture du signal pour chaque signal, puis 3 instructions préparées (jusqu'à 5) pour chaque composant correspondant. L'import nocturne le relance pour chaque projet à dépôt.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:94-99 « List<UUID> signalIds = ...signals.signalsAffecting(...); for (UUID signalId : signalIds) { reports.add(correlate(signalId, Optional.of(projectId))); » ; :105 requiringNew par signal ; :143 signals.find ; :161 inventories.componentsMatching par signal ; :167 prepareStatement(INSERT INTO project_impact ...) dans la boucle.
```

**Impact** — Pour un inventaire npm ou Maven qui touche des centaines de paquets à avis, cela fait des centaines de transactions et au moins 4 allers-retours SQL par signal, plus une évaluation des alertes par signal (MoteurAlertes). Le tout s'exécute de façon synchrone après la validation de InventoryStore.replace (observateur AFTER_SUCCESS), donc dans le fil de l'appelant de l'import.

**Action** — Charger en une requête les signaux et leurs paquets affectés (signal + signal_package par package_key = ANY), et les composants du projet une seule fois ; préparer l'INSERT hors boucle avec addBatch ; regrouper les ImpactsRecorded par projet ; exécuter la corrélation d'inventaire en tâche de fond.

### AUD-PER-04 — Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Plus de transaction ni de requête par membre ou par canal, plus d'envoi SMTP dans l'observateur ; test du nombre fixe de requêtes.

*Changement depuis le 21/11* : 331f1d9 : MoteurAlertes lit en une transaction membres, espaces, impacts actifs, abonnements et alertes existantes ; écrit en une transaction ; l'observateur est @ObservesAsync et ne diffuse plus (la tâche EnvoiAlertes envoie).

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:141 (@ObservesAsync), :159-179 (une lecture, une écriture), :182-196, :199-208 (alertes existantes en une requête) ; aucune occurrence de envoi.envoyer dans MoteurAlertes (grep) ; test argos-server/src/test/java/io/argos/chain/RequetesConstantesTest.java:172 (alr09S1EvaluationEnNombreFixeDeRequetes).
```

*Réserve* : L'attente du résumé IA (argos.alerting.resume-ia.delai-max = PT10S, MoteurAlertes.java:355) reste dans l'observateur, désormais asynchrone.

*Au 20/11* : Ouvert (Moyenne) — Toujours 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, puis l'envoi SMTP, sur le fil de corrélation.

*Au 21/11* : Ouvert (Moyenne) — Toujours 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, l'attente IA et l'envoi SMTP, sur le fil de corrélation.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:141 « for (Destinataires.Destinataire d : candidats) { plan(e, d, paquets)... » ; :173 espacesDe, :175 impacts.forSignals, :180 abonnements.activeFor (chacun dans QuarkusTransaction.requiringNew()) ; :145 et :161 alerteExiste par plan et canal ; :262 « demande.get(delaiResumeIa...) » (attente IA jusqu'à PT10S) ; :168 envoi.envoyer(creees, ...) (SMTP synchrone, timeout PT10S par message).
```

**Impact** — Pour un espace de M membres, chaque signal corrélé coûte environ 4×M transactions courtes. S'y ajoutent jusqu'à 10 s d'attente de l'IA et l'envoi SMTP séquentiel, dans le fil qui ingère. En service multi-utilisateurs, avec des espaces nombreux ou peuplés, cela multiplie la durée d'ingestion et la charge du pool de connexions.

**Action** — Charger en une requête par signal les membres, leurs espaces et leurs abonnements actifs (jointure appartenance + subscription), ainsi que les alertes existantes (signal_id, compte_id) ; ne pas appeler envoi.envoyer dans l'observateur et laisser le job EnvoiAlertes (1 min) diffuser ; rendre l'observateur asynchrone (@ObservesAsync, ou file).

### AUD-PER-06 — « Ma veille » paginée et indexée : p95 185,5 ms sur le jeu avec abonnements et trois espaces (campagne du 2026-11-27) ; la page est extraite en mémoire d'une union bornée

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-PER-21

**Vérification du 28/11 (01fea6d)** — Les trois réserves du 21/11 sont levées : expressions indexées, réponse paginée, chemin des abonnements mesuré. Mesure versionnée : Ma veille p95 185,5 ms (cible 500 ms) sur un jeu avec abonnements PERSONAL et trois espaces.

*Changement depuis le 21/11* : 331f1d9 et 496add9 : index d'expressions V27 (signal_package_ecosysteme, _type, _nom, signal_modifie) ; SignalQueries.search en UNION de sélections indexables ; Ma veille paginée au format ADR-0040 (PRF-09).

```text
argos-server/src/main/java/io/argos/signal/SignalQueries.java:188-193 (UNION par expression) ; argos-server/src/main/resources/db/migration/V27__index_requetes_chaudes.sql:52-55 ; argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:183-221 (Page.extraire), :266-267 (forWorkspaces, LIMITE_IMPACTS = 200) ; tests argos-server/src/test/java/io/argos/api/veille/VeillePagineeTest.java:101, :115, :133 ; argos-server/src/test/java/io/argos/chain/IndexRequetesChaudesTest.java:40-44. Mesures : docs/architecture/quality/mesure-performance-api-mcp.md (campagne du 2026-11-27, révision 496add9, « GET /api/v1/me/veille | REST | 200 | 153 | 185.5 | 243.8 | 0 | dans la cible ») ; e2e/resultats/performance/rapport-2026-11-27.json (jeuDeDonnees : 20 000 impacts actifs, 500 000 résolus ou écartés dans un espace volumineux, 150 dans un petit espace, « abonnements PERSONAL par paquet et par technologie »).
```

*Réserve* : 1) La page est extraite en mémoire d'une union composée en entier et bornée (200 impacts + 100 avis par abonnement) : au-delà, les éléments plus anciens ne sont pas atteignables et le total décrit l'union bornée. 2) Le p95 a doublé entre le 2026-11-25 (72,2 ms, un espace) et le 2026-11-27 (185,5 ms, trois espaces et abonnements) : jeu plus lourd, toujours sous la cible. 3) Aucun test ne vérifie par EXPLAIN que les index d'expressions sont employés (IndexRequetesChaudesTest ne vérifie que leur présence). 4) Mesure en quarkus:dev sur un poste win32 de 16 cœurs, pas sur le NAS.

*Au 20/11* : Ouvert (Moyenne) — Même requête sur expressions non indexées et même relecture signal par signal, maintenant exécutées par deux routes, dont « Ma veille », mesurée hors cible.

*Au 21/11* : Partiel (Moyenne) — Les deux causes mesurées sont corrigées et testées : relecture groupée des signaux et index V21. « Ma veille » passe sous la cible sur le jeu K3. En revanche, la recherche des abonnements porte toujours sur des expressions non indexées, la réponse n'est toujours pas paginée, et la campagne n'exerce pas ce chemin.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/signal/SignalQueries.java:209-217 « SELECT DISTINCT s.id, s.modified_at FROM signal s LEFT JOIN signal_package p ... WHERE lower(p.ecosystem) = ANY (?) OR split_part(substr(p.package_key, 5), '/', 1) = ANY (?) OR lower(regexp_replace(...)) = ANY (?) OR ... ORDER BY s.modified_at DESC LIMIT ? » ; :231 « ids.stream().map(this::find) » (1 requête par signal, jusqu'à 100) ; argos-server/src/main/java/io/argos/api/WatchResource.java:79 (signals.find par signal d'un filtre PROJECT) et :91 (search par abonnement). Index : signal_package_key (package_key) seul (V3:122), aucun index sur lower(ecosystem) ni sur signal.modified_at (signal_date porte sur coalesce(published_at, modified_at), V11:42).
[AUD-PER-21] docs/architecture/quality/mesure-performance-api-mcp.md : « GET /api/v1/me/veille | REST | 200 | 509.3 | 533.8 | 542.3 | 0 | **hors cible** », « Jeu de données | 20 projets × 100 composants, 1000 avis, 20000 impacts (un espace) », « Système | win32 10.0.19045 x64, 16 cœurs, 48 Gio », serveur quarkus:dev, révision 7c5b9d7 (aucune modification d'argos-server ni d'argos-ui entre 7c5b9d7 et 54add78 : « git diff --stat 7c5b9d7 HEAD -- argos-server argos-ui » vide) ; e2e/resultats/performance/rapport-2026-11-19.json (même valeur, verdict HORS_CIBLE) ; code : argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:139-170 (liste complète triée en mémoire, sans page), :212-213 (impacts.forWorkspaces, LIMITE_IMPACTS = 200), :220 (signaux.find par signal), :187 (SignauxAbonnements, requête non indexée d'AUD-PER-06) ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:244-245 (« WHERE i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.updated_at DESC, i.id LIMIT ? » ; index disponibles : project_impact_workspace (workspace_id), V5__impacts_projet.sql:23, aucun sur (workspace_id, updated_at)) ; décision K2 (openspec/changes/automatiser-chaine-veille/proposal.md:136 et :166, P-18 : « consigné sans correction […] son optimisation relève d'un changement ultérieur »).
```

**Impact** — Chaque GET /api/v1/me/watch coûte, par abonnement, un parcours complet de signal ⋈ signal_package (les OR d'expressions empêchent l'usage d'un index), un tri, puis jusqu'à 100 lectures unitaires. Le coût croît avec la taille du corpus OSV et le nombre d'abonnements, sans pagination côté API.

**Action** — Faire porter ce fil par RechercheVeille (déjà paginée et indexée) ou ajouter des colonnes normalisées indexées (ecosystem_lower, package_name) ; remplacer find() en boucle par une lecture « WHERE id = ANY (?) » ; paginer la réponse.

### AUD-PER-18 — Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Performance, Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les opportunités sans date limite ne sont plus restituées indéfiniment, sont purgées et la liste est paginée.

*Changement depuis le 21/11* : e7629dd : âge maximal des opportunités sans date limite (argos.profil-artiste.opportunites.age-max = P90D), purge quotidienne (argos.conservation.opportunites = P180D), API paginée.

```text
argos-server/src/main/java/io/argos/signal/opportunite/Opportunites.java:39-53 ; argos-server/src/main/java/io/argos/signal/opportunite/ConservationOpportunites.java:36-44 ; argos-server/src/main/java/io/argos/api/artiste/ProfilArtisteResource.java:230 ; argos-server/src/main/resources/application.properties:267, :346 ; test argos-server/src/test/java/io/argos/architecture/ConservationCompleteTest.java:44.
```

*Réserve* : RappelsOpportunites relit toujours les opportunités pour chaque suiveur (argos-server/src/main/java/io/argos/alerting/RappelsOpportunites.java:93), volume désormais borné par l'âge maximal (choix écrit dans openspec/changes/archive/2026-11-27-retenir-importer-et-paginer/design.md:306).

*Au 20/11* : Ouvert (Moyenne) — Les opportunités sans date limite sont toujours relues et renvoyées sans LIMIT, sans pagination et sans purge.

*Au 21/11* : Ouvert (Moyenne) — Les opportunités sans date limite sont toujours relues et renvoyées sans LIMIT, sans pagination ni purge.

**Preuve d'origine**

```text
signal/opportunite/CollecteOpportunites.java:171-172 (élément de flux enregistré avec dateLimite null) ; signal/opportunite/Opportunites.java:42-43 « AND (o.date_limite IS NULL OR o.date_limite >= ?) ORDER BY … » sans LIMIT ; grep 'DELETE FROM opportunite' src/main -> aucun résultat ; api/artiste/ProfilArtisteResource.java:218 (liste complète renvoyée) ; alerting/RappelsOpportunites.java:93,110 (relecture complète pour chaque suiveur).
```

**Impact** — Tout élément de flux jamais collecté reste une opportunité « ouverte » pour toujours. GET /opportunites et la tâche quotidienne de rappels relisent et renvoient toute la table pour chaque utilisateur : le fil se remplit d'éléments périmés et la charge croît linéairement avec l'historique et le nombre de suiveurs (N+1 par suiveur).

**Action** — Donner aux éléments de flux une échéance (date de publication + durée configurée) ou les exclure au-delà d'un âge maximal ; paginer GET /opportunites (LIMIT/curseur) ; purge planifiée des opportunités échues, à intégrer à la rétention de SEC-03 ; calculer les rappels en une requête qui joint suivi_profil et opportunite.

### AUD-PER-25 — La corrélation réécrit chaque impact à chaque passage (updated_at = now() sans condition) : chaque nuit, mises à jour non HOT dans les 8 index de project_impact, et ordre « récents » de Ma veille faussé

- **Statut** : Fermé · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Un impact inchangé n'est plus réécrit et garde son updated_at ; testé.

*Changement depuis le 21/11* : 331f1d9 : l'upsert ne réécrit que si une valeur change (WHERE … IS DISTINCT FROM) ou si l'impact est redevenu actif ; component_id n'est plus dans le SET ; project_impact_recents remplacé par un index partiel.

```text
argos-server/src/main/java/io/argos/impact/ImpactsEnLot.java:136-156 ; argos-server/src/main/resources/db/migration/V27__index_requetes_chaudes.sql:44-46 ; test argos-server/src/test/java/io/argos/chain/CorrelationTest.java:287-302 (cor07S1CorrelationRejoueeSansDoublon : « COR-07-S2 : la date de mise à jour d'un impact inchangé ne bouge pas »).
```

*Au 21/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:303-315 (« ON CONFLICT (signal_id, project_id, component_id) DO UPDATE SET signal_version_id = …, provenance = excluded.provenance, component_id = excluded.component_id, updated_at = now() RETURNING id, (xmax = 0), state », sans clause WHERE), appelé pour chaque composant correspondant (:263), y compris les impacts RESOLU et ECARTE ; la détection de changement (:262-264, aChange) ne sert qu'à la publication, pas à l'écriture ; argos-server/src/main/resources/db/migration/V21__index_impacts_recents.sql:16 (index sur updated_at, donc pas de mise à jour HOT dès que updated_at change) ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:41-43 (déclencheur BEFORE INSERT OR UPDATE OF component_id, une lecture de component par ligne, déclenchée parce que component_id figure dans le SET) ; index de project_impact : PK et triplet_uk (V5:6, :20), project_impact_signal et project_impact_workspace (V5:22-23), project_impact_project (V11:49), project_impact_projet_etat et project_impact_signal_etat (V20:55-56), project_impact_recents (V21:16) ; déclenchement chaque nuit : argos-server/src/main/java/io/argos/inventory/ImportPlanifie.java:52-56 -> InventoryStore.java:84 -> CorrelationEngine.java:102-126 (AUD-PER-19) ; lecture qui s'appuie sur cet ordre : argos-server/src/main/java/io/argos/impact/ImpactQueries.java:244-245 (ORDER BY i.updated_at DESC, i.id LIMIT 200) et argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:218-219.
```

**Impact** — Sur le NAS, chaque nuit, chaque impact de chaque projet à dépôt est réécrit : une nouvelle version de ligne, 8 entrées d'index, une exécution du déclencheur et une ligne de journal WAL. Avec le jeu K3, cela fait 20 000 réécritures par nuit (estimation déduite du code, non mesurée), donc un gonflement de la table et des index, et un travail d'autovacuum qui croît avec le nombre d'impacts, résolus compris. Côté fonction, après l'import de 03:00, les impacts sont rangés par ordre de recorrélation, non par nouveauté. Pour un espace qui a plus de 200 impacts actifs, « Ma veille » et l'outil MCP get_security_alerts retiennent donc un sous-ensemble arbitraire, et un impact apparu la veille peut sortir de la fenêtre des 200.

**Action** — N'écrire que si une valeur change : ajouter à l'upsert « WHERE (project_impact.signal_version_id, level, method, transitive, inventory_origin, inventory_date, component_id) IS DISTINCT FROM (excluded.…) » et ne remettre updated_at à now() qu'à cette condition (RETURNING ne renvoie alors rien pour une ligne inchangée : relire l'identifiant dans ce cas). Retirer component_id du SET quand il ne change pas. Mesurer ensuite n_tup_hot_upd / n_tup_upd (pg_stat_user_tables) après deux nuits d'import. Signaler aux axes Qualité et Architecture l'effet sur l'ordre de « Ma veille ».

### AUD-PER-31 — Débit de la file d'enrichissement fixé à 5 signaux toutes les 30 s (14 400 par jour), tous signaux confondus, CVE ou non : un amorçage OSV met des jours à s'écouler et retarde d'autant l'enrichissement (KEV, EPSS) des avis nouveaux

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 3
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:71-78 (chaque SignalChanged met le signal en file, sans condition) et :86-89 (un signal sans alias CVE n'appelle aucune source mais occupe une place du lot) ; argos-server/src/main/java/io/argos/vulnintel/FileEnrichissement.java:47-53 (ORDER BY prochain_essai_le, signal_id LIMIT ?, premier arrivé premier servi) ; argos-server/src/main/java/io/argos/vulnintel/TraitementEnrichissements.java:80-83, :103 ; argos-server/src/main/resources/application.properties:87-88 (cadence=30s, lot=5) ; NVD sans clé : une requête par CVE (vulnintel/NvdSource.java), donc un lot de 5 signaux à plusieurs CVE dépasse les 5 requêtes / 30 s visées (commentaire argos-server/src/main/resources/application.properties:85-86).
```

**Impact** — À l'amorçage, tous les avis Maven, npm et PyPI créés entrent en file. À 600 signaux par heure, chaque tranche de 10 000 avis demande environ 17 h, avant que les avis du jour soient enrichis : un avis critique nouvellement exploité n'est marqué KEV qu'après l'écoulement du stock. La file grossit aussi à chaque vague de modifications OSV. Les signaux à plusieurs CVE déclenchent des refus NVD (403/429), puis des reports de 5 min à 6 h.

**Action** — Ne mettre en file que les signaux à alias CVE (même condition que rattraper()) ; régler le débit par appels NVD et non par signaux (jeton par requête, clé d'API NVD facultative à 50 requêtes / 30 s) ; servir d'abord les signaux récents ou porteurs d'impacts ; KEV et CERT-FR, en cache, peuvent être appliqués à tous les signaux en un passage sans attendre NVD ; exposer la profondeur de la file en métrique.

### AUD-DEP-03 — Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Restauration désormais idempotente (DROP DATABASE … WITH (FORCE) puis CREATE), arrêtée à la première erreur, documentée et rejouée par un script d'essai ; mais l'essai n'est exécuté ni en CI ni périodiquement.

*Changement depuis le 21/11* : Nouveau scripts/restaurer-base.sh (recréation de la base vide par initialiser-bases.sh, psql -v ON_ERROR_STOP=1 --single-transaction sous le propriétaire, arrêt d'argos et keycloak) et essai automatisé scripts/essai-sauvegarde-restauration.sh (changement archivé 2026-11-24-fiabiliser-sauvegarde-et-roles-base ; derniers commits 6cc2a4f, 736a560).

```text
scripts/restaurer-base.sh:196-197 (arrêt, postgres --wait), :205-206 recréation, :208-213 `psql -X -q -v ON_ERROR_STOP=1 --single-transaction … SET ROLE` ; deploiement/postgres/initialiser-bases.sh:52-60 ; scripts/essai-sauvegarde-restauration.sh:212-217 (restauration rejouée, contenu égal, objets sous le propriétaire) ; docs/deploiement/synology-et-pc.md:252-265 ; `grep essai-sauvegarde .github/workflows/*` -> aucun résultat.
```

*Réserve* : Essai non rejoué ici (Docker Hub bloqué par le proxy : `docker pull postgres:18` -> Forbidden) ; preuves du mainteneur sur PC Windows (1 contrôle de permissions non vérifié) ; non exécuté en CI alors que l'exécuteur ubuntu-latest dispose de Docker ; périodicité de l'exercice non fixée (risks/register.md:16, Q-4).

*Au 20/11* : Ouvert (Moyenne) — La procédure de restauration reste un psql sans ON_ERROR_STOP sur un dump texte sans --clean, non testée.

*Au 21/11* : Ouvert (Moyenne) — La restauration reste un psql sans ON_ERROR_STOP sur un dump texte sans --clean, non testée ; elle ignore en plus la base keycloak introduite par #28 (voir AUD-DEP-24).

**Preuve d'origine**

```text
docs/deploiement/synology-et-pc.md:72-76 `gunzip -c ... | docker compose exec -T postgres sh -c 'psql -U ... -d ...'` puis « À tester au moins une fois après la mise en service » ; scripts/sauvegarder-base.sh:22 `pg_dump ... --no-owner` (format texte, sans --clean) ; application.properties:23 `quarkus.flyway.migrate-at-start=true`.
```

**Impact** — Sur une base où Argos a déjà démarré (Flyway a créé le schéma), la restauration produit des erreurs « already exists » / clés dupliquées que psql ignore : restauration partielle silencieuse. Aucun exercice de restauration n'a été réalisé (doc:76, doc:138-140).

**Action** — Documenter la séquence complète : arrêter le service argos, recréer la base (dropdb/createdb ou pg_dump --clean --if-exists), restaurer avec `psql -v ON_ERROR_STOP=1` ou `pg_restore` (format -Fc), puis redémarrer ; ajouter un test de restauration (script ou test Testcontainers) exécuté périodiquement.

### AUD-DEP-07 — Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Journaux structurés, métriques et santé métier présents et testés ; traçage distribué différé par décision.

*Changement depuis le 21/11* : ADR-0036 acceptée et mise en œuvre (changement instrumenter-observabilite) : quarkus-logging-json (JSON en %prod), MDC de corrélation, filtre masquage-jetons, Micrometer/Prometheus sur /q/metrics réservé à admin-plateforme, contrôles @Readiness par tâche et dépendance.

```text
argos-server/pom.xml:81 quarkus-logging-json, :85 quarkus-micrometer-registry-prometheus ; application.properties:292-305, :436-437 ; argos-server/src/main/java/io/argos/observabilite/ (FiltreCorrelation, FiltreJetons, SanteTaches, IntercepteurTaches) ; SanteSources, SanteEnvoiAlertes, SantePasserelleIa ; tests argos-server/src/test/java/io/argos/observabilite/JournauxJsonTest, MetriquesEtSanteTest, BaseInjoignableTest, FiltreJetonsTest, CorrelationTest ; docs/architecture/adr/0036-pile-observabilite-serveur.md:64-74.
```

*Réserve* : Traçage OpenTelemetry différé (ADR-0036 point 4, risque assumé) ; aucun collecteur ni alerte : les métriques ne sont lues qu'à la demande ; suite Maven non exécutée ici (lancée par l'orchestrateur).

*Au 20/11* : Ouvert (Moyenne) — Toujours ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier.

*Au 21/11* : Ouvert (Moyenne) — Toujours ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier.

**Preuve d'origine**

```text
argos-server/pom.xml:43-151 : seule quarkus-smallrye-health (l.53-56) ; pas de quarkus-logging-json, quarkus-micrometer ni quarkus-opentelemetry ; application.properties : aucune clé quarkus.log.* ; grep -rlE '@Readiness|@Liveness|HealthCheck' argos-server/src/main/java -> aucun résultat ; journaux texte via LOG.*f (27 errorf, 16 infof, 11 warnf, 1 debugf), aucun MDC.
```

**Impact** — En exploitation publique, impossible de suivre les taux d'erreur, la latence, l'état DEGRADED des sources, la file d'envoi des e-mails ou la disponibilité d'Ollama autrement qu'en lisant les journaux Docker ; /q/health ne reflète que la base (contrôle par défaut de la datasource).

**Action** — Ajouter quarkus-logging-json (profil prod), quarkus-micrometer-registry-prometheus (endpoint protégé), des checks @Readiness pour ingestion/envoi e-mail/IA (dégradé non bloquant), et un identifiant de corrélation en MDC ; OpenTelemetry quand un collecteur existe.

### AUD-DEP-25 — Le royaume Keycloak importé n'autorise que http://localhost:8088 en redirection et en origine, alors qu'Argos est publié par défaut sur le port 8080 : connexion refusée avec la configuration par défaut

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Dépendances, Architecture, Documentation · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-ARC-20, AUD-DOC-21

**Vérification du 28/11 (01fea6d)** — Plus de port 8088 dans le royaume livré : la configuration par défaut est cohérente.

*Changement depuis le 21/11* : Le royaume livré prend ${ARGOS_PUBLIC_URL} (transmis au service keycloak, défaut http://localhost:${ARGOS_PORT:-8080}) pour les redirections, origines et fins de session, sans joker (50867d3) ; verifier-royaume.mjs en CI.

```text
deploiement/keycloak/argos-realm.json:45-55 ; compose.yaml:149 ; `grep -rn localhost:8088 deploiement compose.yaml docs/deploiement` -> aucun ; integration-continue.yml:148-149 ; scripts/verifier-royaume.test.mjs.
```

*Réserve* : La substitution de ${ARGOS_PUBLIC_URL} à l'import n'est testée par aucun test automatisé : l'e2e importe le royaume de test (e2e/compose.image.yaml:23), pas deploiement/keycloak/argos-realm.json ; connexion OIDC complète non exécutée par le mainteneur (tasks.md du changement completer-configuration-conteneurs, « Preuves »).

*Au 21/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
deploiement/keycloak/argos-realm.json:39-42 redirectUris `http://localhost:8088/*` et la boucle locale du desktop, :43-46 webOrigins `http://localhost:8088`, :49 post.logout.redirect.uris 8088 ; compose.yaml:106 `${ARGOS_PORT:-8080}` ; .env.example:13 `ARGOS_PORT=8080` ; docs/deploiement/synology-et-pc.md:25 « Argos répond sur http://localhost:8080 », :37 « Les adresses de retour de argos-ui visent http://localhost:8088 : avec un autre ARGOS_PORT, adaptez-les dans la console » ; argos-ui/src/session/session.ts:40-46 (redirect_uri = origine de la page servie).
[AUD-ARC-20] deploiement/keycloak/argos-realm.json:39-41 « "redirectUris": ["http://localhost:8088/*", "http://127.0.0.1/argos/retour"] », :43-45 webOrigins « http://localhost:8088 », :49 post.logout.redirect.uris « http://localhost:8088/* » ; .env.example:13 « ARGOS_PORT=8080 » ; compose.yaml:106 « ${ARGOS_BIND:-127.0.0.1}:${ARGOS_PORT:-8080}:8080 » ; argos-ui/src/session/session.ts:40-41 (adresse de retour = origine de la page). docs/deploiement/synology-et-pc.md:37 « Les adresses de retour de argos-ui visent http://localhost:8088 : avec un autre ARGOS_PORT, adaptez-les » laisse croire que 8088 est la valeur par défaut. Ajouté par e7ed7f3 (PR #28).
[AUD-DOC-21] deploiement/keycloak/argos-realm.json:39-49 (client argos-ui : redirectUris « http://localhost:8088/* », webOrigins « http://localhost:8088 », post.logout.redirect.uris idem) ; .env.example:13 (ARGOS_PORT=8080) ; compose.yaml:106 (« ${ARGOS_PORT:-8080}:8080 ») ; docs/deploiement/synology-et-pc.md:37 (« Les adresses de retour de argos-ui visent http://localhost:8088 : avec un autre ARGOS_PORT, adaptez-les dans la console Keycloak ») : le guide présente 8088 comme la norme sans dire de régler ARGOS_PORT=8088, et :25 annonce Argos sur http://localhost:8080 ; argos-ui/src/session/gestionnaire.ts:46 (portail web : « retour sur la même adresse »). Introduit par e7ed7f3 (#28).
```

**Impact** — En suivant le guide avec `cp .env.example .env`, le portail servi sur http://localhost:8080 envoie redirect_uri=http://localhost:8080/ que Keycloak refuse (« Invalid parameter: redirect_uri ») ; l'échange de jeton depuis l'origine 8080 est aussi bloqué par CORS. Le réglage local du mainteneur (8088) a été figé dans le royaume, et le royaume n'étant importé qu'une fois, la correction se fait à la main dans la console.

**Action** — Aligner le royaume sur le défaut (8080) ou, mieux, paramétrer redirectUris/webOrigins par espace réservé (`${ARGOS_PUBLIC_URL}/*`, résolu par Keycloak à l'import) en transmettant la variable au conteneur keycloak ; ajouter une recette ou un test d'intégration du profil idp-local.

### AUD-DEP-30 — Publication du client desktop sans aucun gate : sur une étiquette desktop-v* posée sur n'importe quel commit, l'installeur est construit et publié comme dernière version sans tests, sans npm audit ni vérification que le commit est sur main

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Dépendances, Tests · **Effort** : S · **Sprint** : 2
- **Nouveau** le 28/11
- **Fusionne** : AUD-TST-28

**Preuve**

```text
.github/workflows/publication-desktop.yml:13-15 (déclencheur push tags seul), :42-48 (seule vérification : étiquette = version du package.json), :90-115 (npm ci, build, electron-builder --publish always), :132-143 (publication --latest) ; aucun `needs` vers integration-continue.yml, aucun `npm run check` ni `npm audit` ; ADR-0042:74-77 : electron-updater installe la release « dernière version » sur tous les postes.
[AUD-TST-28] .github/workflows/publication-desktop.yml:13-15 (on: push: tags: ['desktop-v*'], seul déclencheur) ; :42-48 (seul contrôle : étiquette = version du package.json) ; :56 gh release create … --verify-tag (vérifie l'existence de l'étiquette, pas son origine) ; grep -nE 'npm (run )?(test|check)|typecheck' .github/workflows/publication-desktop.yml -> aucun résultat ; :90-99 seuls npm ci, build de l'IHM et licences avant electron-builder ; :143 gh release edit --draft=false --latest ; argos-desktop/src/mise-a-jour.js et argos-desktop/test/main.test.js:139-148 (mise à jour recherchée au démarrage sous Windows et Linux).
```

**Impact** — Une régression ou une dépendance vulnérable (seuil high de la CI non appliqué) part directement chez tous les utilisateurs par la mise à jour automatique ; une étiquette posée sur une branche non relue est publiée comme version officielle. Sans signature, rien d'autre ne distingue une version légitime.

**Action** — Dans le job brouillon : vérifier que le commit étiqueté est un ancêtre de origin/main (git merge-base --is-ancestor), exécuter `npm run check` (desktop), `npm test` (IHM) et `npm audit --audit-level=high` avant electron-builder ; protéger les étiquettes desktop-v* (règle de dépôt) ; envisager un environnement GitHub avec approbation pour la publication.

## Faible (74)

### AUD-ARC-06 — Couplage par le schéma SQL : 12 tables partagées et 13 lectures étrangères directes admises par ProprieteTablesTest, contre la règle d'ADR-0037 (service de lecture ou vue) ; l'écriture réservée au propriétaire n'est pas vérifiée

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 21/11) · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 4
- **Fusionne** : AUD-QUA-07, AUD-ARC-17

**Vérification du 28/11 (01fea6d)** — Les trois lectures non couvertes ont disparu et un test rend visible tout nouveau couplage par le schéma (tâches 5.1 à 5.3 : accord). Désaccord sur le fond : ProprieteTablesTest admet toute lecture directe d'une table d'un module dont le lecteur dépend, si bien que 13 lectures étrangères directes subsistent sur 12 tables partagées, alors qu'ADR-0037 (Accepté) dit qu'un propriétaire « en publie les lectures étrangères par un service de lecture ou une vue SQL nommée ». Et la règle « lui seul l'écrit » d'arc42 §5.3.1 n'est pas vérifiée : le test autorise aussi un INSERT ou un UPDATE d'un module dépendant.

*Changement depuis le 21/11* : 637d8e8 « propriété des tables, vue publiée du nom de compte et lectures par le propriétaire » : table propriétaire -> tables dans arc42 §5.3.1 et ProprieteTablesTest ; impact lit le nom des comptes par la vue compte_nom_public (V23), catalog lit les appartenances par workspace.LectureMembres, alerting lit les préférences par PreferencesService ; PreferencesIsolationTest corrigé.

```text
python3 -I /home/claude/audit-maj3/scripts-arch/tables3.py (lecture du fichier entier, propriétaires lus dans ProprieteTablesTest) -> 12 tables multi-modules, 14 lectures étrangères dont 1 par vue publiée ; aucune écriture étrangère aujourd'hui ; ex. impact/ImpactQueries.java:107-109 « JOIN signal … JOIN project … JOIN component », alerting/Destinataires.java:41 « FROM appartenance a JOIN compte c », alerting/AlertesSansObjet.java:40 « FROM project_impact », catalog/dictionnaire/DictionnaireMetier.java:367-368 « FROM expert_domaine x LEFT JOIN compte co », inventory/InventoryQueries.java:281 « JOIN espace », vulnintel/FileEnrichissement.java:82 « FROM signal_alias », regintel/FichesEnBase.java:187 « JOIN domaine_metier » ; ProprieteTablesTest.java:99-105 lectureAutorisee (dépendance = autorisation, sans distinguer lecture et écriture) ; docs/architecture/adr/0037-noyau-commun-du-serveur.md:61 ; docs/architecture/arc42/05-vue-blocs.md:155 « lui seul l'écrit », :176-180 (lectures croisées listées). Sortie : /home/claude/audit-maj3/scripts-arch/tables-01fea6d.json.
```

*Réserve* : Le test ne voit pas le SQL construit dynamiquement (ProprieteTablesTest.java:31, dit lui-même). Pour fermer : soit amender ADR-0037:61 pour admettre la lecture directe par un module dépendant (décision documentée), soit publier des services ou des vues pour ces 13 lectures ; dans les deux cas, faire refuser par ProprieteTablesTest les INTO/UPDATE/DELETE hors du propriétaire.

*Au 20/11* : Ouvert (Moyenne) — Le couplage par le schéma s'étend et crée désormais des dépendances absentes de la table ALLOWED ou plus larges que la règle d'exception.

*Au 21/11* : Ouvert (Moyenne) — Le couplage par le schéma est inchangé, y compris les trois lectures que la table ALLOWED ou les règles d'exception ne couvrent pas.

**Preuve d'origine**

```text
python3 -I /home/claude/audit-tmp/arch/tables.py -> signal : impact(3 fichiers), signal_package/signal_version : impact ; actualite : impact(2) ; project : impact(5) ; component : impact(2) ; appartenance/compte : alerting ; signal_alias : vulnintel. Ex. impact/ImpactQueries.java:56-58 « JOIN signal s … JOIN project p … JOIN component c », alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c », vulnintel/Enricher.java:91 « FROM signal_alias a », impact/RechercheVeille.java:218,238 « FROM signal », « FROM actualite ».
[AUD-QUA-07] impact/ImpactQueries.java:56 « JOIN signal s » ; impact/AnalyseComplementaireIa.java:114 (signal, signal_package) ; impact/RechercheVeille.java:218 (signal, actualite) ; impact/RapprochementActualites.java (actualite, component, project) ; alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c » et :60 (tables du module workspace) ; vulnintel/Enricher.java:89-93 (signal_alias). Commande : grep -rlE "(FROM|JOIN|INTO|UPDATE)\s+<table>" par table -> signal lue par 3 modules, project par 2, appartenance par workspace et alerting.
[AUD-ARC-17] argos-server/src/main/java/io/argos/alerting/Destinataires.java:61-66 « membresAvecProfil … JOIN workspace_preference_utilisateur p ON p.compte_id = c.id WHERE a.espace_id = ? AND ? = ANY (p.profils_actifs) » (ajouté par 3f2fdc7, appelé par RappelsReglementaires) ; argos-server/src/test/java/io/argos/architecture/PreferencesIsolationTest.java « seulsApiEtMcpLisentLesPreferences » ne contrôle que les dépendances Java vers io.argos.workspace.preference ; docs/architecture/arc42/05-vue-blocs.md §5.4.1 « Les préférences ne sont lues que par api et mcp (PreferencesIsolationTest) », contredit par §5.4.3 « aux membres au profil expert-metier actif ».
```

**Impact** — La propriété des tables n'est documentée nulle part ; renommer une colonne de signal ou d'inventory casse impact, alerting ou vulnintel sans qu'aucun test d'architecture ne le signale. Toutes ces arêtes SQL doublent une arête Java déjà autorisée (pas de dépendance cachée nouvelle), mais elles fixent le schéma comme contrat public.

**Action** — Documenter en arc42 §5.3 le module propriétaire de chaque table ; pour les lectures étrangères, passer par les services Query du propriétaire (SignalQueries, InventoryQueries, EspacesService) ou par des vues SQL nommées comme contrat ; ajouter un test qui vérifie que chaque module ne cite que ses tables ou des vues publiées.

### AUD-ARC-07 — La table ALLOWED est fermée (règle du module déclaré testée) ; une autorisation sur 46 (inventory → workspace) ne sert qu'à une lecture SQL non commentée

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Architecture, Tests · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-TST-11

**Vérification du 28/11 (01fea6d)** — L'action est faite et testée : un paquet io.argos non déclaré fait échouer le test. Reste une autorisation sans usage Java, que la documentation présente pourtant comme utilisée.

*Changement depuis le 21/11* : 8a93c84 : les 7 autorisations inutilisées sont retirées de ALLOWED ; règle chaqueClasseResideDansUnModuleDeclare avec test d'exemple laRegleRefuseUnModuleNonDeclare (classe factice io.argos.architecture.exemples.ClasseHorsModule). Les nouveaux paquets de premier niveau sont tous déclarés.

```text
ModuleBoundariesTest.java:94-100 regleModuleDeclare, :126 et :132-134 ; compare.py -> « autorisées inutilisées: [('inventory', 'workspace')] » ; ModuleBoundariesTest.java:46 « inventory, Set.of("workspace", …) » sans commentaire ; seule la lecture SQL inventory/InventoryQueries.java:281 « JOIN espace » s'appuie sur cette entrée (ProprieteTablesTest.java:104) ; find argos-server/src/main/java/io/argos -maxdepth 1 -type f -> vide.
```

*Réserve* : inventory -> workspace n'est utilisé que par le SQL, sans le dire, alors que la javadoc (ModuleBoundariesTest.java:31) affirme « Elle n'autorise que des dépendances que le code utilise » et qu'arc42 05-vue-blocs.md:270 annonce « 50 dépendances autorisées, toutes utilisées » (la table en compte 46 entre modules métier). Aucun test ne détecte une autorisation inutilisée : la table peut de nouveau dériver.

*Au 20/11* : Ouvert (Faible) — La table reste plus large que le code et ne contraint toujours pas un paquet io.argos non déclaré.

*Au 21/11* : Ouvert (Faible) — La table autorise toujours 7 dépendances inutilisées sur 57 et ne contraint toujours pas un paquet io.argos de premier niveau absent de la table.

**Preuve d'origine**

```text
compare.py -> « autorisées inutilisées: api->regintel, catalog->audit, impact->vulnintel, mcp->projectintel, regintel->{aigateway,catalog,signal}, releaseradar->signal, vulnintel->catalog, workspace->audit » (10 sur 54). ModuleBoundariesTest.java:71-73 : la liste des paquets interdits est calculée à partir de ALLOWED.keySet() ; un nouveau paquet io.argos.x absent de la table n'est ni contraint ni protégé.
[AUD-TST-11] argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java:31-57 (table ALLOWED, 16 entrées) et :69-80 (règle générée uniquement pour ALLOWED.keySet(), interdits calculés à partir des seules clés, allowEmptyShould(true))
```

**Impact** — Des dépendances peuvent apparaître sans revue (déjà autorisées) ; un module ajouté sans entrée dans la table échappe entièrement au contrôle.

**Action** — Retirer les 10 autorisations inutilisées (les rajouter le jour où le code en a besoin) ; ajouter une règle « toute classe de io.argos réside dans un paquet de premier niveau listé dans ALLOWED ».

### AUD-ARC-09 — Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La chaîne radar sort de la requête HTTP : ReferentielEspace publie après la validation de sa transaction (requiringNew), RadarProjets et RappelsRadar observent en asynchrone ; la règle est écrite et protégée par un test.

*Changement depuis le 21/11* : 7f27b94 « événements asynchrones… » : PublicationAsynchrone (fireAsync sur un exécuteur borné) ; StandardModifie, AnnonceStandard et ImpactsRecorded sont publiés en asynchrone et observés par @ObservesAsync ; règle des trois styles écrite en arc42 §8 « Événements entre modules » et dans ADR-0039 ; liste fermée des observateurs synchrones inter-modules testée.

```text
catalog/standard/ReferentielEspace.java:111-113 (base.executer puis publication.publier) ; impact/RadarProjets.java:235 « @ObservesAsync StandardModifie », :241 publication d'AnnonceStandard ; alerting/RappelsRadar.java:186 « @ObservesAsync AnnonceStandard » ; alerting/MoteurAlertes.java:141 « @ObservesAsync ImpactsRecorded » ; execution/PublicationAsynchrone.java:34-45 ; docs/architecture/arc42/08-concepts-transverses.md:285-291 ; test : architecture/ObservateursEvenementsTest.java lesObservateursSynchronesInterModulesSontDeclares (+ test d'exemple laRegleRefuseUnObservateurSynchroneNonDeclare), deux synchrones admis motivés.
```

*Réserve* : ADR-0039, qui porte la règle, est toujours « Proposé » (adr/0039…md:10) : voir AUD-ARC-14. Quand l'exécuteur est saturé, ExecuteurBorne.lancerOuExecuter (execution/ExecuteurBorne.java:163-166) exécute l'observateur dans le fil appelant : la chaîne radar repasse alors dans la requête PUT /standards (repli assumé, non documenté dans §8).

*Au 20/11* : Ouvert (Faible) — Les deux styles coexistent toujours sans règle ; la chaîne StandardModifie -> RadarProjets -> AnnonceStandard -> RappelsRadar reste synchrone dans le PUT /standards, exceptions avalées.

*Au 21/11* : Ouvert (Faible) — La chaîne StandardModifie → RadarProjets → AnnonceStandard → RappelsRadar reste synchrone dans la requête HTTP ; elle n'avale plus que les pannes d'accès aux données.

**Preuve d'origine**

```text
Observateurs transactionnels : impact/CorrelationEngine.java:76,84, vulnintel/Enricher.java:57, impact/RapprochementActualites.java:64 « @Observes(during = TransactionPhase.AFTER_SUCCESS) ». Observateurs synchrones : alerting/MoteurAlertes.java:124, impact/RadarProjets.java:187, alerting/RappelsRadar.java:130, api/preference/ActivationProfilParDefaut.java:34. catalog/standard/ReferentielEspace.java:104 « r.changement().ifPresent(modifications::fire); » après commit, dans le thread du PUT /standards.
```

**Impact** — Un PUT sur le référentiel déclenche synchroniquement RadarProjets puis RappelsRadar (lecture des inventaires, émission d'alertes) avant de répondre ; les exceptions sont avalées et journalisées (RadarProjets.java:196-198) : latence et échecs silencieux côté utilisateur. La règle de choix entre les deux styles n'est écrite nulle part.

**Action** — Documenter la règle en arc42 §8 (événement inter-modules = AFTER_SUCCESS ou @ObservesAsync) et l'appliquer à StandardModifie, AnnonceStandard et ImpactsRecorded.

### AUD-ARC-10 — La collecte HTTP est dispersée dans sept clients répartis sur cinq modules

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-QUA-12

**Vérification du 28/11 (01fea6d)** — L'action (fabrique commune dans le module transverse) est faite et testée ; tous les clients lisent leurs réponses par LectureBornee.

*Changement depuis le 21/11* : 7f27b94 / 7408b6c : fabrique io.argos.commun.http.ClientsHttp (redirections refusées, User-Agent, délai) et LectureBornee ; les six clients java.net.http l'utilisent ; ClientsHttpCommunsTest interdit HttpClient.newBuilder/newHttpClient hors de la fabrique.

```text
grep -rn "HttpClient.newBuilder" argos-server/src/main/java -> seulement commun/http/ClientsHttp.java:43 ; ClientsHttp.client/clientSansProxy dans vulnintel/EnrichmentHttp.java:45, scm/ForgeHttp.java:53, ingestion/legi/ArchivesJorf.java:82, releaseradar/ClientEndoflife.java:80, aigateway/ollama/FournisseurOllama.java:66, ingestion/HttpSourceClient.java:60 ; LectureBornee dans chacun (ex. EnrichmentHttp.java:60, ForgeHttp.java:76) ; test : architecture/ClientsHttpCommunsTest.java seuleLaFabriqueCommuneConstruitUnClientHttp + laRegleRefuseUnClientConstruitAilleurs.
```

*Réserve* : Le test ne vérifie que la construction : un BodyHandlers.ofString() non borné sur un client de la fabrique passerait. La veille web garde son propre transport Vert.x hors fabrique (ingestion/web/TransportEpingle.java:22, :65, exclusion écrite dans la javadoc du test), et la garde d'adresses reste propre aux sources web.

*Au 20/11* : Ouvert (Faible) — La dispersion s'accroît ; aucune fabrique commune.

*Au 21/11* : Ouvert (Faible) — La collecte HTTP reste dispersée, sans fabrique commune ; la garde d'adresses reste propre à la veille web.

**Preuve d'origine**

```text
grep -rln "HttpClient.newBuilder" -> ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, scm/ForgeHttp.java:36, vulnintel/EnrichmentHttp.java:36, releaseradar/ClientEndoflife.java:72, aigateway/ollama/FournisseurOllama.java:59 ; tous « followRedirects(NEVER) » et connectTimeout. arc42 §5.3 (05-vue-blocs.md:119) attribue la collecte à source-ingestion ; la garde d'adresses (ingestion/web/GardeAdresses.java) n'est utilisée que par la veille web.
[AUD-QUA-12] HttpClient.newBuilder() dans vulnintel/EnrichmentHttp.java:36, scm/ForgeHttp.java:36, releaseradar/ClientEndoflife.java:72, ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, aigateway/ollama/FournisseurOllama.java:59 (chaque fois connectTimeout + Redirect.NEVER, limites de taille et gestion d'InterruptedException réécrites). scm/RepositoryActivity.java:150-162 boucle de pagination manuelle alors que pages() (:326-337) fait la même chose.
[À 9d96406] scm/ForgeHttp.java:44 (au lieu de :36) ; 05-vue-blocs.md:120 ; toujours 6 HttpClient.newBuilder ; la collecte artiste réutilise RecuperateurWeb.
```

**Impact** — Configuration homogène aujourd'hui, mais chaque évolution (proxy, User-Agent, limites de taille, garde SSRF) doit être faite six fois ; vulnintel et releaseradar sont à la fois métier et adaptateurs.

**Action** — Fournir une fabrique de clients HTTP commune (timeouts, taille maximale, User-Agent, garde d'adresses optionnelle) dans le module transverse proposé en AUD-ARC-05 ; mettre à jour §5.3.

### AUD-ARC-11 — Aucun objet-dieu (max. 586 lignes, InventoryQueries) ; impact (3 434 l.) porte aussi la recherche plein texte que le package-info attribue à signal

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Fermé : la recherche plein texte réside dans le module signal, comme l'annonce son package-info.

*Changement depuis le 21/11* : PR #80 (468bf74) : RechercheVeille déplacée dans io.argos.signal.recherche ; doublure de test alignée (361405a).

```text
ae4a2da : argos-server/src/main/java/io/argos/signal/recherche/RechercheVeille.java ; signal/package-info.java:4 « recherche plein texte de la veille ({@code signal.recherche}) ».
```

*Au 20/11* : Ouvert (Faible) — Toujours aucun objet-dieu au sens strict ; la responsabilité recherche reste mal attribuée et impact concentre corrélation, traitement des impacts (V20), recherche, radar, opportunités et IA.

*Au 21/11* : Ouvert (Faible) — Toujours aucun objet-dieu au sens strict ; la recherche reste portée par impact alors que le package-info l'attribue à signal.

**Preuve d'origine**

```text
find io/argos -name '*.java' | xargs wc -l | sort -n | tail -> plus gros fichier scm/RepositoryActivity.java 394 lignes, puis workspace/EspacesService.java 383, mcp/OutilsSecurite.java 383 ; moyenne 87 lignes/fichier. impact = 1 919 lignes/13 fichiers (CorrelationEngine, RadarProjets, RechercheVeille, RapprochementActualites, AnalyseComplementaireIa). signal/package-info.java : « Signaux normalisés, taxonomie, déduplication, recherche » alors que la recherche est impact/RechercheVeille.java (lit les tables signal et actualite, l.218, 238).
[À 9d96406] signal 20 fichiers / 2 550 l. > impact 16 / 2 159 l. : la partie « impact le plus dense » ne tient plus.
```

**Impact** — Taille des classes saine ; les responsabilités d'impact débordent toutefois (corrélation, recherche, radar, IA), ce qui en fait le point de convergence le plus exposé aux changements de signal et d'inventory.

**Action** — Corriger le package-info de signal (ou déplacer la recherche dans un sous-paquet dédié) et consigner en §5.3 que impact porte la recherche plein texte.

### AUD-ARC-12 — Le contrat client de l'IHM est vérifié contre l'OpenAPI servi (instantané et test des appels), mais ses types restent écrits à la main ; 5 classes REST sur 23 sans appel de l'IHM

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Architecture, Qualité, Tests · **Effort** : M · **Sprint** : 4
- **Fusionne** : AUD-QUA-13, AUD-TST-30

**Vérification du 28/11 (01fea6d)** — Un test de contrat existe des deux côtés et la CI l'exécute (job serveur « mvn … clean verify » avec Docker, job ihm « npm test ») ; il ne reste que 5 classes REST sur 23 sans appel de l'IHM (au lieu de 10).

*Changement depuis le 21/11* : 1ce7ff4 / 95166bb : instantané argos-ui/src/api/openapi.json comparé au document servi par /q/openapi (ContratClientIhmTest) ; argos-ui/tests/contrat-api.test.mjs vérifie que chaque chemin d'API écrit dans src/api existe dans l'instantané et que chaque appel direct utilise une méthode déclarée ; le client est découpé en modules (artiste.ts, radar.ts, signaux.ts…).

```text
argos-server/src/test/java/io/argos/api/ContratClientIhmTest.java:30 (instantané ../argos-ui/src/api/openapi.json), :55-64 ; argos-ui/tests/contrat-api.test.mjs:76-104 ; argos-ui/package.json:15 « node --test tests/**/*.test.mjs && vitest run » ; .github/workflows/integration-continue.yml jobs serveur et ihm ; ContratClientIhmTest listé dans argos-server/src/test/tests-docker.txt:13 (exclu du profil sans-docker, pas du profil integration-continue) ; classes sans appel IHM : veille-web, reglementaire, ops/sources, me/watch, invitations (grep sur argos-ui/src/api/*.ts).
```

*Réserve* : Les types TypeScript restent écrits à la main (aucune génération openapi-typescript dans argos-ui/package.json) ; le test de l'IHM vérifie chemins et méthodes, pas la forme des corps ; doublon de schéma toujours présent : argos-ui/src/api/client.ts:198 « interface AlerteVue » / contenu/alertes-poste.ts:25 « interface AlerteRecue ». Le test serveur n'est vérifiable qu'avec Docker. Relecture du 28/11 : les types de requête et de réponse de l'IHM sont toujours écrits à la main (fusion d'AUD-TST-30).

*Au 20/11* : Partiel (Faible) — La divergence de gestion d'erreur est corrigée ; le contrat reste écrit à la main, sans génération ni test de contrat côté IHM, et couvre 11 racines @Path sur 23.

*Au 21/11* : Partiel (Faible) — Le point d'appel unique tient ; le contrat reste manuel, sans génération ni test de contrat côté IHM, et 10 classes @Path sur 23 ne sont appelées par aucun client.

**Preuve d'origine**

```text
argos-ui/src/api/client.ts : 8 fonctions exportées pour 18 classes @Path("/api/v1…") côté serveur ; types recopiés « schéma OpenAPI PreferencesEffectives » (l.58) sans génération ; getJson (l.32), putJson (l.83), postJson (l.169) dupliquées, seul putJson lit le corps Erreur (l.96). Chemins utilisés vérifiés présents côté serveur (api/AlertesResource.java:32,45-46,52,60).
[AUD-QUA-13] argos-ui/src/api/client.ts:32-50 getJson, :83-109 putJson, :169-188 postJson (même minuterie, même try/catch) ; seul putJson (:93-100) lit le message d'erreur du serveur. Types : client.ts:150 « interface AlerteVue » et contenu/alertes-poste.ts:22 « interface AlerteRecue » décrivent le même schéma AlerteVue ; « nature: string », « etat: string » au lieu d'unions. Fonction message(e) recopiée dans views/AffichageView.vue:27 et views/AlertesView.vue:36.
[À 9d96406] client.ts : 8 fonctions, inchangé ; 20 classes @Path (ajout de ProfilArtisteResource.java:66 et RubriquesResource.java:27).
[AUD-TST-30] argos-ui/tests/contrat-api.test.mjs:1-4, :17-19 (littéraux /api/v1 et appels appelerApi/getJson/postJson), :69 et :82 (chemin existant, méthode déclarée) ; aucune lecture de components.schemas ; argos-ui/package.json : aucun générateur de types OpenAPI ; argos-ui/src/api/veille.ts:10, :21, :28, :38, :68 (interfaces écrites à la main) ; api/ContratClientIhmTest.java (identité instantané / /q/openapi seulement).
```

**Impact** — Une évolution d'un record Java (AlerteVue, PreferencesEffectives) ne casse aucune compilation côté IHM ; dérive silencieuse du contrat dès que l'IHM s'étoffera.

**Action** — Générer les types depuis /q/openapi (openapi-typescript) en build, ou ajouter un test de contrat ; factoriser une seule fonction request() (méthode, corps, lecture d'Erreur, futur jeton OIDC).

### AUD-ARC-14 — Six ADR appliqués par le code et cités par ses tests restent « Proposé » (0034, 0035, 0038 à 0041, sur 42) ; les ADR fondateurs sont acceptés

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Architecture, Documentation · **Effort** : S · **Sprint** : 5
- **Fusionne** : AUD-DOC-09

**Vérification du 28/11 (01fea6d)** — Les ADR fondateurs cités par le constat sont acceptés : accord avec les tâches 2.1 à 2.6 cochées. Mais le même écart se reforme sur les ADR du sprint : six ADR que le code applique et que ses tests citent comme règle restent « Proposé », alors que l'index les définit comme « pas encore validée ».

*Changement depuis le 21/11* : 5900ffc « accepter les ADR appliqués et rédiger l'ADR-0037 » : ADR-0001, 0003, 0008, 0010, 0011, 0013, 0014, 0015, 0016, 0020, 0021, 0022 passés « Accepté » (décision du porteur du 2026-11-24) ; ADR-0037 rédigé et accepté ; index adr/README.md et arc42/09-decisions.md synchronisés.

```text
grep -m1 Statut docs/architecture/adr/0*.md -> 42 ADR : 30 Accepté, 4 Remplacé, 8 Proposé ; Proposé et appliqués : 0034 (.github/workflows/integration-continue.yml), 0035 (rôles de base V22, cité par 4 fichiers de code), 0038 (configuration IHM à l'exécution), 0039 (21 fichiers de code le citent, dont ObservateursEvenementsTest, ClientsHttpCommunsTest, AppelsReseauHorsTransactionTest:26), 0040 (pagination, 14 fichiers, commun/Pagination.java), 0041 (conservation, 17 fichiers, module io.argos.conservation, ConservationCompleteTest:21) — ligne « Statut : Proposé » à :10 de chacun ; docs/architecture/adr/README.md (« Proposé : décision préparée mais pas encore validée »), :103-105, :109 ; arc42/08-concepts-transverses.md:287 « ADR-0039, Proposé ». 0007 et 0012 restent Proposé à juste titre (non appliqués).
```

*Réserve* : Pour fermer : décision du porteur sur 0034, 0035 et 0038 à 0041 (Accepté, ou écart consigné), et règle d'acceptation au moment de la fusion du changement qui applique l'ADR.

*Au 20/11* : Partiel (Faible) — Les ADR récents sont actés, mais les ADR fondateurs que le code applique depuis le début restent « Proposé ».

*Au 21/11* : Partiel (Faible) — Inchangé depuis le 20/11 : les ADR récents sont actés, les ADR fondateurs que le code applique restent « Proposé ».

**Preuve d'origine**

```text
grep -m1 Statut docs/architecture/adr/0*.md -> 22 ADR sur 29 « Proposé », dont 0001-monolithe-modulaire.md:3 (cité en tête de ModuleBoundariesTest.java:21) et 0016-workspace-rbac-rls.md:3. 0028-collecteur-veille-web-integre.md:6 et 0029-passerelle-ia-ollama-local.md:6 remplacent 0006 et 0004 « à l'acceptation », alors que io.argos.ingestion.web et io.argos.aigateway.ollama sont livrés.
[AUD-DOC-09] docs/architecture/adr/0021-double-licence.md:3 (Statut : Proposé) et :16 (« aucune dépendance n'est encore déclarée dans le dépôt ») ; argos-server/README.md:19 et :32 (contrôle bloquant « ADR-0021 point 4 »), :59 (194 dépendances inventoriées) ; Dockerfile:24-25 (mvn -P licences au build) ; e2e/README.md:112 et docs/architecture/quality/audit-accessibilite-mises-en-page.md:15 (axe-core écarté « ADR-0021 ») ; ADR-0025:40, :50 et :62 demandent de revoir ADR-0009 et ADR-0021, sans ADR de suite. Cas particulier, apparu avec le lot licences, de la famille décrite par AUD-ARC-14.
```

**Impact** — Les décisions effectivement en production ne sont pas actées ; ADR-0004 (Claude) et ADR-0006 (FreshRSS séparé) restent formellement en vigueur et contredisent le code, ce qui alimente l'écart documentaire (AUD-DOC-02).

**Action** — Passer en « Accepté » les ADR implémentés (0001, 0013, 0014, 0016, 0027, 0028, 0029…), marquer 0004 et 0006 « Remplacé », et mettre à jour adr/README.md. Rédiger l'ADR du noyau commun (prérequis d'AUD-ARC-05).

### AUD-ARC-15 — Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 4

**Vérification du 28/11 (01fea6d)** — Les points d'entrée utilisateur du profil artiste et la rubrique des préférences ont un client et une vue, avec un test de composant.

*Changement depuis le 21/11* : 95166bb « vues signal, radar et profil artiste… » : argos-ui/src/views/ProfilArtisteView.vue, routes /profils/artiste et /profils/artiste/opportunites/:id, client argos-ui/src/api/artiste.ts (listes, suivis GET/PUT, opportunités, résumé, sources, rubriques).

```text
argos-ui/src/router.ts:45 ; argos-ui/src/api/artiste.ts:104-135 ; argos-ui/tests/vues/profil-artiste.spec.ts ; serveur api/artiste/ProfilArtisteResource.java:176-305.
```

*Réserve* : Trois points d'entrée d'administration restent sans client IHM : POST /sources (:269-270), PUT /sources/{id}/conditions-usage (:288-289), POST /collectes (:304-305) ; à consigner comme écart voulu dans docs/specifications/profil-artiste.md s'ils sont réservés à l'exploitation. Relecture du 28/11 : trois des six points d'entrée du titre n'ont toujours aucun client dans l'IHM.

*Au 20/11* : Ouvert (Faible) — Toujours aucun client pour les points d'entrée du profil artiste ; le lien de rappel vers une page inexistante subsiste et n'est pas consigné.

*Au 21/11* : Ouvert (Faible) — Toujours aucun client ; le lien de rappel vise un chemin qui n'existe ni comme route de l'IHM ni comme ressource publique du serveur.

**Preuve d'origine**

```text
git diff --stat f58a68c HEAD : aucun fichier sous argos-ui/ ni argos-desktop/ ; api/artiste/ProfilArtisteResource.java:157-291 (GET listes, GET/PUT suivis, GET opportunites, GET resume, sources, collectes), api/preference/RubriquesResource.java:27,49 ; argos-ui/src/api/client.ts : 8 fonctions, aucune pour /profils/artiste.
```

**Impact** — La fonction n'est utilisable que par l'API avec un jeton : comme ARC-02, aucune preuve de bout en bout. L'écart entre contrat serveur et client (ARC-12) se creuse. Le lien des rappels sans lien officiel vise une page /profils/artiste/opportunites/<id> qui n'existe pas (RappelsOpportunites.java, contenu()).

**Action** — Ajouter les vues artiste (suivis, opportunités) dans argos-ui une fois ARC-02 traité, ou consigner l'écart dans docs/specifications/profil-artiste.md (« Écarts constatés »).

### AUD-ARC-19 — L'événement AlerteEmise est publié en cinq points sans aucun observateur

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Accord : l'événement sans observateur n'existe plus.

*Changement depuis le 21/11* : 7f27b94 : l'événement AlerteEmise et ses cinq émissions sont supprimés.

```text
grep -rn "AlerteEmise" argos-server/src -> aucun résultat ; git log -S AlerteEmise b142695..01fea6d -> 7f27b94. La règle des styles d'événements est protégée par ObservateursEvenementsTest (AUD-ARC-09).
```

*Réserve* : Aucun test n'empêche de republier un événement sans observateur ; risque faible.

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — AlerteEmise est toujours publié en cinq points sans aucun observateur.

**Preuve d'origine**

```text
emises.fire(new AlerteEmise(…)) dans alerting/MoteurAlertes.java:159, alerting/RappelsRadar.java:244, alerting/ResumesPeriodiques.java:109, alerting/RappelsReglementaires.java:112 (nouveau), alerting/RappelsOpportunites.java:124 ; grep -rn "@Observes.*AlerteEmise" argos-server/src -> aucun résultat ; alerting/AlerteEmise.java:5-8 (javadoc) « Le changement notifier-sur-le-poste peut l'observer » alors que le poste lit les alertes par interrogation (05-vue-blocs.md:46). Déjà présent (4 émissions) à 9d96406, non relevé par l'audit initial.
```

**Impact** — Point d'extension mort recopié à chaque nouveau type de rappel ; il laisse croire qu'une diffusion en temps réel existe et coûte une résolution d'observateurs CDI à chaque alerte.

**Action** — Supprimer l'événement et ses cinq émissions, ou le brancher (diffusion SSE au desktop) et le documenter dans alertes-digests.md ; à défaut, consigner qu'il est réservé.

### AUD-ARC-25 — Neuf package-info sur 16 décrivent encore l'ancienne table ALLOWED (dont une règle d'exception supprimée) et les quatre modules transverses execution, horloge, observabilite et commun n'en ont pas

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 5
- **Nouveau** le 28/11

**Preuve**

```text
Les sprints 4 et 5 ont retiré 11 autorisations de ModuleBoundariesTest.java:42-80 sans toucher aux package-info (git diff --stat b142695 01fea6d -- argos-server/src/main/java/io/argos/{catalog,impact,vulnintel,releaseradar,workspace}/package-info.java -> vide). Écarts : catalog/package-info.java:8-10 (audit ; workspace « règle catalogNEmprunteQueRlsEtErreurs », règle supprimée ; table : workspace seul), impact/package-info.java:6 (vulnintel, absent de la table), mcp/package-info.java:12 (projectintel, absent), projectintel/package-info.java:8 (workspace, absent), regintel/package-info.java:8 (signal et workspace, absents), releaseradar/package-info.java:9 (signal, absent), signal/package-info.java:6 (workspace, absent), vulnintel/package-info.java:6 (catalog, absent), workspace/package-info.java:6 (audit ; table : aucun). ls argos-server/src/main/java/io/argos/{commun,execution,horloge,observabilite}/package-info.java -> absents (conservation/package-info.java:8 existe). Chacun de ces package-info annonce « Vérifiées par ModuleBoundariesTest ». arc42 05-vue-blocs.md:272-288, lui, concorde avec la table.
```

**Impact** — La documentation au plus près du code contredit la règle vérifiée : un développeur ou un agent qui lit le package-info croit autorisées des dépendances que le test refuse (ou cherche une règle qui n'existe plus). La concordance package-info/table, relevée comme protection en AUD-ARC-01 et AUD-ARC-18, a régressé.

**Action** — Aligner les neuf package-info sur ALLOWED, créer ceux de commun, execution, horloge et observabilite (rôle, « modules techniques transverses seulement », « aucun module » pour commun), et ajouter à ModuleBoundariesTest (ou à scripts/verifier-docs.mjs) un contrôle qui compare la ligne « Dépendances autorisées » de chaque package-info à ALLOWED.

### AUD-QUA-06 — Le code JDBC brut reste majoritaire hors des modules cœur : 153 prepareStatement (31 fichiers) et 79 catch SQLException hors de l'utilitaire commun Jdbc, adopté par 35 fichiers ; 4 copies du convertisseur de date et 4 de la troncature subsistent

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 21/11) · **Axes** : Qualité · **Effort** : M · **Sprint** : 3
- **Bloque** : AUD-QUA-26

**Vérification du 28/11 (01fea6d)** — La factorisation est réelle pour les modules cœur, mais l'adoption reste partielle (« progressive », ADR-0037:30) : 31 fichiers hors du noyau gardent le squelette JDBC brut et quatre copies du convertisseur de date subsistent à côté de Conversions.instant.

*Changement depuis le 21/11* : Utilitaire io.argos.commun.Jdbc (lister/unique/modifier, une traduction SQLException -> ErreurBase) et Conversions (instant, utc, couper) introduits (287c38f, ADR-0037) ; modules cœur signal, impact, alerting migrés (cb8266d et suivants) ; tests JdbcTest, JdbcUnitaireTest, ConversionsTest.

```text
grep -rn prepareStatement argos-server/src/main/java | wc -l -> 157 (153 hors io/argos/commun, 31 fichiers, dont workspace/EspacesService.java 17, inventory/InventoryStore.java 14, catalog/standard/ReferentielEspace.java 11, catalog/dictionnaire/DictionnaireMetier.java 11) ; grep -rn "catch (SQLException" | wc -l -> 86 (79 hors noyau), suivis de « throw new IllegalStateException » 64 fois contre 5 « throw new ErreurBase » ; 35 fichiers utilisent Jdbc (133 appels lister/unique/modifier) ; copies de instant(ResultSet) : catalog/standard/ReferentielEspace.java:369, inventory/InventoryQueries.java:582, ingestion/SyncStore.java:301, releaseradar/CyclesDeVie.java:170 ; troncature : ingestion/culture/LecteurDataCulture.java:145 couper, ingestion/web/LecteurFlux.java:104 couper, ingestion/legi/LecteurJorf.java:141 borner, inventory/InventoryStore.java:364 cut ; utc() : ingestion/SyncStore.java:306.
```

*Réserve* : Duplication résiduelle autour de l'utilitaire : traduction des violations de contrainte par SQLSTATE recopiée en 8 endroits (« 23505 »/« 23503 » : catalog/standard/ReferentielEspace.java:274, signal/opportunite/SourcesProfil.java:58, signal/web/SourcesWeb.java:58, workspace/vue/VuesEnregistrees.java:118, workspace/subscription/SubscriptionService.java:95, inventory/ProjectDirectory.java:77,167,170) ; Jdbc.Tableau employé 9 fois contre 67 « createArrayOf » manuels.

*Au 20/11* : Ouvert (Moyenne) — Le squelette JDBC continue d'être recopié ; aucune factorisation.

*Au 21/11* : Ouvert (Moyenne) — Le squelette JDBC reste recopié ; le lot superviseur n'a introduit aucune factorisation.

**Preuve d'origine**

```text
Commandes : grep -rn prepareStatement argos-server/src/main/java | wc -l -> 184 (41 fichiers) ; grep -rn "catch (SQLException" | wc -l -> 93, dont 78 suivis de « throw new IllegalStateException » ; « private static Instant instant(ResultSet rs, int ...) » recopié dans SignalQueries.java:268, ReferentielEspace.java:336, SourcesWeb.java:239, ActualitesWeb.java:162, ImpactQueries.java:245, RechercheVeille.java:312, ConsultationAlertes.java:143, SyncStore.java:280, InventoryQueries.java:232, CyclesDeVie.java:163 ; couper() x3 (LecteurFlux:104, StockActualites:336, SourcesWeb:244) ; utc() x2 (SignalRecorder:226, SyncStore:285).
[À 9d96406] 198 prepareStatement (45 fichiers), 101 catch SQLException ; 10 copies de instant(ResultSet).
```

**Impact** — Environ 30 à 40 % des lignes des classes *Queries/*Store sont du squelette (ouverture, paramètres positionnels, mapping par index de colonne, réemballage) : mapping fragile aux changements de colonnes, gestion d'erreur hétérogène (IllegalStateException ici, QuarkusTransactionException ailleurs, ex. ResumesPeriodiques.java:120-151), revue coûteuse.

**Action** — Introduire un petit utilitaire partagé (ex. Jdbc.query(ds, sql, binder, rowMapper) / update(...) et des convertisseurs Instant/OffsetDateTime communs) ou adopter un client léger (Jdbi, déjà compatible Quarkus) ; migrer d'abord les modules cœur (signal, impact, alerting). Garder le SQL explicite, conforme au choix actuel.

### AUD-QUA-08 — Le typage entre modules repose encore sur des chaînes : niveau d'impact en String alors que l'énumération existe, clé composite découpée par split(":"), statuts d'import et de corrélation en chaînes avec défaut silencieux

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les quatre preuves d'origine sont corrigées et une valeur inconnue lue en base lève une erreur explicite ; d'autres valeurs fermées de même nature restent des chaînes.

*Changement depuis le 21/11* : Commit 758abc3 : MatchRules.Level circule jusqu'aux vues REST/MCP/alertes, StatutImport et StatutAnalyse en énumérations, clé de résumé en record, switch sans default ; règle écrite en arc42 §8.16 (« Valeurs fermées »).

```text
argos-server/src/main/java/io/argos/impact/ImpactQueries.java:51 (MatchRules.Level level), :367 niveau(String) ; impact/StatutAnalyse.java:4 ; inventory/StatutImport.java:4 ; inventory/InventoryService.java:47 (ImportResult(StatutImport status, ...)) ; inventory/ImportPlanifie.java:130 categorie(StatutImport) ; grep -rn "split(\":\")" argos-server/src/main/java/io/argos/alerting -> 0 ; grep "\"A_VERIFIER\".equals" -> 0 ; test : argos-server/src/test/java/io/argos/impact/NiveauImpactTest.java.
```

*Réserve* : Restent typés par chaînes : le type de forge (switch « GITHUB »/« GITLAB » avec default qui lève, recopié 3 fois : scm/RepositoryFiles.java:96-106, :111-117, scm/RepositoryActivity.java:81-85), l'origine d'inventaire « SBOM »/« MANIFEST » comparée par equals (inventory/InventoryQueries.java:406, :431, :540) et l'état « AVAILABLE »/« NO_SBOM » des vues (api/InventoryResource.java:58, :121-122, :136-137), contraires à la règle d'arc42 §8.16 qui exige une énumération pour toute valeur fermée échangée entre modules.

*Au 20/11* : Ouvert (Faible) — Niveau d'impact, statuts d'import et résumé IA restent typés par chaînes et tableaux ; nouveau switch sur chaînes avec défaut silencieux.

*Au 21/11* : Partiel (Faible) — Une des quatre preuves est corrigée (String[] -> record) ; niveau, clé composite et statuts restent typés par chaînes, dont le switch à défaut silencieux de l'import planifié.

**Preuve d'origine**

```text
impact/MatchRules.java:23 « public enum Level { EXACT, PROBABLE, A_VERIFIER } » mais impact/ImpactQueries.java:34 « String level » ; alerting/MoteurAlertes.java:191 « !"A_VERIFIER".equals(i.level()) », :233-236 « case "EXACT" -> 0; case "PROBABLE" -> 1; » ; :146 et :262-271 « new String[] {texte, "DISPONIBLE"} » ; :163 « String[] fc = cle.split(":") » ; impact/CorrelationEngine.java:106-121 statuts « "NONE" », « "DONE" », « "UNAVAILABLE" » ; inventory/InventoryService.java:30 « String status » (IMPORTED, REJECTED...).
```

**Impact** — Une faute de frappe ou un nouveau niveau n'est détecté qu'à l'exécution ; le classement rangNiveau() renvoie 2 par défaut pour toute valeur inconnue, ce qui masquerait silencieusement un renommage.

**Action** — Exposer MatchRules.Level (ou un type partagé) dans ImpactView, remplacer String[] par un record ResumeIa(texte, etat) et la clé « fréquence:canal » par un record ; convertir les statuts en enums sérialisés par Jackson.

### AUD-QUA-09 — Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Fermé avec réserve : une langue par concept dans les identifiants Java, vérifiée par le contrôle de nommage.

*Changement depuis le 21/11* : PR #80 (0b09f4c) : « espace » dans les identifiants Java, contrôle de nommage étendu (verifier-nommage.mjs) ; décision consignée dans arc42 §8.16.

```text
ae4a2da : commit 0b09f4c (36 fichiers) ; scripts/verifier-nommage.mjs et son test ; arc42 §8.16.
```

*Réserve* : Par décision du porteur (arc42 §8.16), le module io.argos.workspace, les colonnes SQL (103 occurrences de workspace_id dans src/main) et les noms JSON gardent « workspace ».

*Au 20/11* : Ouvert (Faible) — Mélange français/anglais inchangé et étendu aux nouvelles tables et classes.

*Au 21/11* : Partiel (Faible) — Une convention est désormais écrite et outillée, mais elle entérine le mélange par fichier au lieu de fixer une langue par concept ; tables et classes mêlent toujours espace/workspace.

**Preuve d'origine**

```text
Tables (migrations) : « espace », « appartenance » mais « workspace_vue », « workspace_preference_utilisateur », « subscription », « project_impact » ; colonnes d.workspace_id vs a.espace_id (RadarProjets.java:215, Destinataires.java:41). Classes : impact/ComplementaryAnalysis.java et impact/AnalyseComplementaireIa.java ; ImpactQueries, CorrelationEngine, SyncStore, InventoryService à côté de RechercheVeille, MoteurAlertes, EspacesService ; MoteurAlertes.java:175 « impacts.forSignals(...) » / « abonnements.activeFor(...) ». Aucune convention de langue dans docs/architecture (grep -rli "nommage|langue du code" -> aucun ADR).
```

**Impact** — Recherche dans le code et dans le schéma plus difficile (espace ou workspace ? projet ou project ?), risque de doublons conceptuels, coût d'entrée pour un contributeur.

**Action** — Écrire une courte règle (ADR ou arc42 §8) : domaine en français, termes techniques et API externes en anglais par exemple ; appliquer la règle au nouveau code et renommer progressivement les identifiants ambigus (espace/workspace) lors des migrations déjà prévues.

### AUD-QUA-10 — Horloge commune injectée : plus aucun now() Java hors du paquet horloge (63 au 21/11), règle ArchUnit HorlogeInjecteeTest ; une expiration d'invitation reste calculée par now() SQL et comparée à l'horloge Java

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Plus aucune lecture de l'heure système hors du paquet horloge, verrouillée par un test d'architecture ; les LocalDate.now() sans fuseau ont disparu. Un site contredit encore la règle Q-2 (« une règle qui compare une date en base à une valeur calculée en Java reçoit l'instant Java »).

*Changement depuis le 21/11* : Commit 7f33aa8 : Clock unique produit par io.argos.horloge.ProducteurHorloge au fuseau argos.alerting.fuseau, injecté (51 fichiers) ; LectureHeure.jour/dateHeure ; règle ArchUnit HorlogeInjecteeTest ; règle SQL écrite (arc42 §8.16, décision Q-2).

```text
grep -rnE "(Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime)\.now\(" argos-server/src/main/java -> 2, tous deux dans horloge/LectureHeure.java:19,24 (now(horloge)) ; grep -rnE "Clock\.(system|systemUTC|systemDefaultZone)" -> seulement horloge/ProducteurHorloge.java:27 ; grep -rnw Clock -> 108 références dans 51 fichiers ; test : argos-server/src/test/java/io/argos/architecture/HorlogeInjecteeTest.java:28-48 (now() sans argument et System.currentTimeMillis interdits, exemple de violation) ; 43 now() SQL restants, d'horodatage pur pour la plupart (règle Q-2, arc42 08-concepts-transverses.md:429).
```

*Réserve* : workspace/EspacesService.java:202 calcule l'expiration d'invitation par « now() + make_interval(days => ?) » côté SQL, alors que :266 la compare à l'horloge Java (LectureHeure.dateHeure(horloge)) : une horloge de test décalée de la base rend l'expiration incohérente. La règle ArchUnit ne vise que les surcharges sans argument : LocalDate.now(ZoneId) passerait (aucun usage actuel).

*Au 20/11* : Ouvert (Faible) — Horloge toujours non injectée ; dispersion en hausse.

*Au 21/11* : Ouvert (Faible) — Horloge toujours non injectée ; chiffres du titre confirmés.

**Preuve d'origine**

```text
grep -rnE "(Instant|LocalDate|OffsetDateTime)\.now\(\)" argos-server/src/main/java | wc -l -> 31 (19 fichiers), grep "Clock" -> 0. Décisions métier prises avec l'heure système : workspace/EspacesService.java:254 (expiration d'invitation), inventory/InventoryQueries.java:214 (SBOM périmée), vulnintel/KevSource.java:65 (cache), api/recherche/TraductionCriteres.java:117. catalog/standard/ReferentielEspace.java:138 et mcp/OutilsReferentiel.java:100 « LocalDate.now() » (fuseau JVM) alors que RappelsRadar.java:93 utilise « LocalDate.now(ZoneId.of(fuseau)) » (argos.alerting.fuseau). 42 « now() » SQL (ex. StockActualites.java:88, :250) côtoient Instant.now() Java dans la même mise à jour (StockActualites.java:248-252).
[À 9d96406] 37 now() Java (23 fichiers), 45 now() SQL ; LocalDate.now() sans fuseau inchangés (ReferentielEspace.java:138, OutilsReferentiel.java:100) ; le titre est à corriger (CollecteWeb.java:81).
```

**Impact** — Tests dépendants du temps réel pour ces chemins ; deux horloges (JVM et PostgreSQL) dans une même règle ; la date « du jour » dépend de la variable TZ du conteneur (compose.yaml:54) et non du fuseau configuré. Le schéma déjà présent « point d'entrée planifié -> méthode recevant maintenant » (MoteurAlertes.java:126, RappelsRadar.java:93) limite le problème.

**Action** — Produire un bean java.time.Clock (fuseau argos.alerting.fuseau) et l'injecter à la place de Instant.now()/LocalDate.now() ; passer l'instant en paramètre SQL plutôt que now() là où la règle compare avec une valeur calculée en Java.

### AUD-QUA-11 — Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Plus aucun exécuteur statique ; chaque exécuteur appartient à un bean, est arrêté à la destruction et le motif d'attente est factorisé et testé.

*Changement depuis le 21/11* : Commit 7f33aa8 : classe ExecuteurBorne (bean CDI, @PostConstruct/@PreDestroy shutdownNow, tailles configurées, motif « soumettre, attendre avec délai, annuler » factorisé) étendue par ExecuteurResumeIa, ExecuteurSyntheseIa, ExecuteurCollecteActivite, ExecuteurEvenements, LancementSynchronisation ; MinuterieCoupure remplace l'exécuteur statique de RecuperateurWeb.

```text
grep -rnE "Executors\.|new ThreadPoolExecutor|new ScheduledThreadPoolExecutor" argos-server/src/main/java -> execution/ExecuteurBorne.java:112 et ingestion/web/MinuterieCoupure.java:29 seulement ; @PreDestroy : execution/ExecuteurBorne.java:121-127, ingestion/web/MinuterieCoupure.java:37-39, ingestion/web/RecuperateurWeb.java:120 ; tests : argos-server/src/test/java/io/argos/execution/ExecuteurBorneTest.java:60-155 (délai, annulation, file pleine, interruption, « lExecuteurSArreteALaDestructionDuBeanEtRefuseEnsuite »).
```

*Réserve* : lancerOuExecuter (ExecuteurBorne.java:163-167), utilisé par PublicationAsynchrone.java:36, exécute l'observateur sur le fil émetteur quand la file est pleine : l'émetteur attend alors, contrairement à la Javadoc de PublicationAsynchrone (« l'émetteur n'attend pas leur fin »). Choix assumé dans la Javadoc de lancerOuExecuter, à mentionner dans arc42.

*Au 20/11* : Ouvert (Faible) — Les trois exécuteurs statiques hors CDI existent toujours, sans arrêt.

*Au 21/11* : Ouvert (Faible) — Les trois exécuteurs statiques hors CDI existent toujours, sans arrêt.

**Preuve d'origine**

```text
alerting/MoteurAlertes.java:75 « private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(2, 2, ...) » ; projectintel/SyntheseActivite.java:46 (même motif recopié) ; ingestion/web/RecuperateurWeb.java:51 « Executors.newSingleThreadScheduledExecutor » ; grep -rn "shutdown\|@PreDestroy\|ManagedExecutor" -> aucun résultat.
```

**Impact** — Les fils cœur ne s'arrêtent jamais : fuite de fils à chaque rechargement à chaud en développement ; tâches perdues sans trace à l'arrêt ; contexte CDI et métriques Quarkus absents dans ces fils ; dimensionnement codé en dur et dupliqué.

**Action** — Utiliser un ManagedExecutor (SmallRye Context Propagation) ou un bean @ApplicationScoped qui possède l'exécuteur avec @PreDestroy shutdownNow(), taille configurable ; factoriser le motif « soumettre + get(délai) + annuler » partagé par MoteurAlertes et SyntheseActivite.

### AUD-QUA-18 — La taille du serveur progresse encore (22 fichiers Java de plus de 300 lignes, 21 au 21/11, 34 337 lignes) ; 7 méthodes de complexité ≥ 15, dont ValidateurManifeste.erreurs (CC 32, 74 lignes) inchangée, et aucun seuil vérifié

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Fermé avec réserve : au plus 60 instructions par méthode et une complexité cyclomatique inférieure à 15 font échouer « mvn verify » (donc la CI).

*Changement depuis le 21/11* : PR #80 (0daa465, 2302e9b) : maven-pmd-plugin lié à la phase verify (pmd-regles.xml : NcssCount 61, CyclomaticComplexity 15) ; script signaler-fichiers-longs.mjs.

```text
ae4a2da : argos-server/pom.xml:249-271 (maven-pmd-plugin, goal check en phase verify, includeTests false) ; pmd-regles.xml:11-21.
```

*Réserve* : La taille des fichiers n'a pas de seuil bloquant (classReportLevel 100000) : scripts/signaler-fichiers-longs.mjs la signale seulement.

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Partiel (Faible) — Les méthodes les plus complexes ont réellement été découpées, sauf SignalRecorder.record ; la taille des fichiers continue de croître et aucun seuil n'est vérifié. Correction de mesure : SbomDocument.reconnaitre (« CC≈28 / 88 l. » au 20/11) était un artefact du script, qui comptait l'accolade du littéral '{' (SbomDocument.java:69) ; la méthode fait 15 lignes, CC 6, inchangée depuis 9d96406.

**Preuve d'origine**

```text
find argos-server/src/main/java -name '*.java' | xargs wc -l : 28 436 lignes / 269 fichiers (20 574 / 228 à 9d96406), plus gros inventory/InventoryQueries.java 556, mcp/OutilsSecurite.java 458, impact/CorrelationEngine.java 440. Script /home/claude/audit-maj/build-QUAL/cc.py (dérivé de qual-scripts/longmeth.py) : catalog/profil/ValidateurManifeste.java:24 CC≈32 / 74 l. ; scm/SbomDocument.java:63 reconnaitre CC≈28 / 88 l. (nouvelle) ; workspace/subscription/SubscriptionService.java:239 CC≈25 ; api/recherche/TraductionCriteres.java:71 CC≈24 ; regintel/CollecteReglementaire.java:132 executer CC≈20 / 81 l. (nouvelle) ; regintel/ExpertiseFiches.java:72 CC≈20. Allongées : impact/CorrelationEngine.java:200 deterministic 80 -> 96 l., signal/SignalRecorder.java:64 record 67 -> 86 l. ; alerting/ResumesPeriodiques.java:119 composerPour toujours 100 l. Point positif : scm/RepositoryActivity github/gitlab découpées (606dcc8).
```

**Impact** — Les zones critiques (corrélation, enregistrement des signaux, reconnaissance des SBOM, collecte réglementaire) concentrent la logique dans des méthodes longues et très ramifiées : revue et tests plus coûteux, risque de régression sur les branches rares.

**Action** — Découper en étapes nommées SbomDocument.reconnaitre (une méthode par format), CollecteReglementaire.executer (index, boucle par jour, bilan), CorrelationEngine.deterministic et SignalRecorder.record ; fixer un seuil indicatif (ex. 60 lignes, CC 15) vérifié en CI (Checkstyle/PMD ou ArchUnit) pour éviter la dérive.

### AUD-QUA-19 — Les fichiers TypeScript de e2e/ sont vérifiés en mode strict par la CI (tsc sans erreur, 3 « as unknown as » contre 17)

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les six erreurs ont disparu et la vérification de types est un gate atteignable de la CI (job « outils », déclenché sur chaque pull request et push sur main, sans filtre de chemin).

*Changement depuis le 21/11* : Commit 8a93c84 : typescript et @types/node en dépendances de développement de e2e, e2e/tsconfig.json strict et noEmit, script typecheck exécuté avant « npm test », déclaration .d.mts des modules JavaScript ; étape de CI ajoutée.

```text
e2e/tsconfig.json (strict, noEmit, include **/*.ts et **/*.d.mts) ; e2e/package.json : « typecheck »: « tsc -p tsconfig.json », « test »: « npm run typecheck && node scripts/lancer.mjs » ; .github/workflows/integration-continue.yml:150-155 (npm ci puis npm run typecheck dans e2e) et :204 (npm test du job e2e) ; copie /home/claude/audit-maj3/build-QUAL/e2e : npm ci --ignore-scripts puis npx tsc -p tsconfig.json -> rc=0 ; 26 fichiers .ts (3 852 lignes) ; grep "as unknown as" e2e --include=*.ts -> 3 (17 au 21/11) : desktop/connexion.spec.ts:132, support/electron.ts:384, :403.
```

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Les six erreurs de types relevées le 20/11 sont toujours présentes.

**Preuve d'origine**

```text
ls e2e -> aucun tsconfig*.json ; e2e/package.json : devDependencies limitées à @playwright/test 1.61.1, scripts test/test:seul/test:accessibilite/test:performance sans tsc (Playwright transpile sans vérifier les types). Commande sur copie (/home/claude/audit-maj/build-QUAL/e2e, tsconfig.audit.json strict, moduleResolution Bundler) : npx tsc -p tsconfig.audit.json -> 6 erreurs : support/donnees.ts:9 (TS7016, import de ./docker.mjs sans déclaration), support/electron.ts:299, :319, :326 (TS7006, paramètres implicitement any). 17 « as unknown as » (16 dans e2e/support/electron.ts:151-293, 1 dans e2e/desktop/connexion.spec.ts:146).
```

**Impact** — Une erreur de typage dans les aides ou les parcours E2E (signature de fixture, forme d'une réponse d'API) n'apparaît qu'à l'exécution de la campagne, longue (pile Docker, Keycloak, Electron) ; les doubles Electron reposent sur des conversions forcées non vérifiées.

**Action** — Ajouter e2e/tsconfig.json (strict, noEmit) et typescript en devDependency, un script « typecheck » exécuté avant « test » ; déclarer docker.mjs (docker.d.mts) et typer les callbacks webRequest ; regrouper les conversions __argosE2E derrière une seule fonction typée.

### AUD-QUA-23 — Le résumé IA indisponible est journalisé sans sa pile : MoteurAlertes passe la cause en argument de format, à l'inverse de la règle suivie par le reste du lot superviseur

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La cause est désormais passée comme Throwable : la pile est journalisée.

*Changement depuis le 21/11* : Commit 7f33aa8 (passage à ExecuteurBorne) : l'issue manquée porte sa cause, journalisée en premier argument.

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:372 « LOG.infof(issue.cause().orElseThrow(), "résumé IA indisponible pour le signal %s (%s)", signalId, issue.etat()) » ; grep -rnE "LOG\.[a-z]+f?\([^)]*getCause\(\)" argos-server/src/main/java -> seulement ingestion/web/TransportEpingle.java:91 (cause en premier argument, conforme).
```

*Réserve* : Aucun test ne vérifie le format de journalisation (règle de §8.6 non outillée).

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
alerting/MoteurAlertes.java:342 « LOG.infof("résumé IA indisponible pour le signal %s : %s", signalId, erreur.getCause()) » (cause en %s, sans Throwable en premier argument) ; comparer projectintel/SyntheseActivite.java:104 « LOG.infof(erreur, ...) » et impact/CorrelationEngine.java:160 « LOG.infof(erreur, ...) ».
```

**Impact** — Une panne du fournisseur IA lors des alertes ne laisse que le toString de la cause, sans pile ni cause imbriquée : diagnostic plus long.

**Action** — Passer erreur.getCause() comme premier argument (LOG.infof(erreur.getCause(), ...)) et l'inclure dans une règle écrite de journalisation (arc42 §8).

### AUD-QUA-27 — Le client desktop est vérifié par tsc en checkJs non strict : le mode strict révèle 125 erreurs (paramètres implicitement any) dans 1 172 lignes de JavaScript

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : M · **Sprint** : 2
- **Nouveau** le 28/11

**Preuve**

```text
argos-desktop/tsconfig.json (« allowJs »: true, « checkJs »: true, « strict »: false) ; décision « strictness progressive » en arc42 §8.16 (08-concepts-transverses.md, paragraphe « Typage ») ; copie /home/claude/audit-maj3/build-QUAL/desktop : npx tsc -p tsconfig.json -> rc=0 ; même configuration avec « strict »: true (tsconfig.audit.json) -> 125 « error TS », dont src/connexion.js 33 TS7006, src/canaux.js 15, src/preload.js 13, src/main.js 9 TS7005.
```

**Impact** — Les paramètres des gestionnaires IPC, de connexion et du preload (frontière de sécurité Electron) ne sont pas typés : une erreur de forme de message entre processus n'est pas détectée à la compilation. La « progressivité » décidée n'a ni échéance ni compteur.

**Action** — Annoter en JSDoc les paramètres de preload.js, canaux.js et connexion.js puis activer « noImplicitAny » ; suivre le nombre d'erreurs strictes par un seuil décroissant dans « npm run check ».

### AUD-QUA-28 — Une erreur imprévue d'une tâche planifiée est journalisée au moins deux fois avec sa pile : dans la boucle (LOG.errorf par unité) puis par IntercepteurTaches, qui la journalise à nouveau avant de la relancer au planificateur

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 3
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/observabilite/IntercepteurTaches.java:46-48 « catch (RuntimeException erreur) { LOG.errorf(erreur, "exécution de la tâche planifiée %s en échec", tache); throw erreur; } » ; même erreur déjà journalisée à la frontière, ex. inventory/ImportPlanifie.java:92-95 puis relancée par :49 (relancerErreurImprevue) ; règle « journalisée avec sa cause » d'arc42 §8.5 (08-concepts-transverses.md:180).
```

**Impact** — Doublons de piles dans les journaux JSON et comptage d'erreurs gonflé dans un agrégateur ; la relance au planificateur Quarkus peut ajouter une troisième trace (non vérifié ici).

**Action** — Dans IntercepteurTaches, journaliser l'échec sans la pile (identifiant de tâche, type d'erreur) puisque la frontière l'a déjà fait, ou ne journaliser la pile que si l'erreur ne provient pas d'ErreursImprevues ; documenter le choix en §8.6.

### AUD-QUA-29 — Deux traductions d'IOException en erreur de source abandonnent la cause : ScmException(…, null) pour une réponse de forge illisible et SourceIndisponible sans constructeur à cause pour endoflife.date

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 3
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/scm/RepositoryActivity.java:396-399 « catch (IOException illisible) { throw new ScmException("réponse illisible de " + uri.getHost(), null); } » ; releaseradar/ClientEndoflife.java:117-120 « catch (IOException illisible) { throw new SourceIndisponible("réponse illisible"); } » ; :55 SourceIndisponible(String message) seul constructeur ; présents à b142695 (même liste de 15 relances sans cause, script catches.py) : angle mort des audits précédents, qui classaient ces relances comme traductions de domaine.
```

**Impact** — Quand une forge ou endoflife.date change le format de sa réponse, le journal ne dit ni la position ni la nature de l'erreur d'analyse JSON : le diagnostic exige de rejouer l'appel.

**Action** — Passer l'IOException en cause (ScmException(message, illisible), constructeur SourceIndisponible(String, Throwable)) ; la règle de §8.6 sur la cause journalisée s'applique alors.

### AUD-SEC-04 — Alertes e-mail envoyées à l'adresse du jeton même non vérifiée

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Plus aucune adresse non vérifiée enregistrée ni utilisée ; le canal e-mail refuse un destinataire sans adresse.

*Changement depuis le 21/11* : Sprint 6 (durcir-autorisations-et-debits, migration V24 aa2e5b8) : seule l'adresse vérifiée normalisée est enregistrée, mise à jour à chaque connexion, adresses existantes effacées.

```text
argos-server/src/main/java/io/argos/workspace/ComptesService.java:62, :87, :101-107 (INSERT avec adresseVerifiee), compteAvecAdresseAJour ; workspace/IdentiteOidc.java:14-15 (emailVerifie exigé) ; api/UtilisateurCourant.java:38-41 (claim email_verified) ; db/migration/V24__adresse_verifiee_des_comptes.sql (UPDATE compte SET email = NULL) ; alerting/CanalEmail.java:32-33 ; test alerting/AdresseVerifieeTest (alr08S1 à S3).
```

*Au 20/11* : Ouvert (Faible) — L'adresse brute du jeton reste enregistrée et sert désormais aussi aux rappels réglementaires.

*Au 21/11* : Ouvert (Faible) — L'adresse brute du jeton est toujours enregistrée et sert aux alertes, résumés et rappels réglementaires.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/workspace/ComptesService.java:74 `ps.setString(4, identite.email());` (adresse brute, alors que seule emailVerifieNormalise() sert aux invitations) ; alerting/Destinataires.java:40 `SELECT DISTINCT c.id, c.email FROM appartenance a JOIN compte c ...` utilisé par CanalEmail.
```

**Impact** — Un utilisateur dont l'e-mail n'est pas vérifié par Keycloak (modifiable par l'utilisateur selon la configuration du royaume) peut faire envoyer les alertes de ses espaces (titres, noms de projets) à un tiers : relais de courriels non sollicités au nom du NAS.

**Action** — N'enregistrer comme adresse d'envoi que l'e-mail vérifié (emailVerifieNormalise) et le mettre à jour à la connexion ; ignorer le canal EMAIL tant qu'aucune adresse vérifiée n'est connue.

### AUD-SEC-06 — Mot de passe de base de données par défaut « argos » si .env absent

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Sécurité, Dépendances · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-DEP-10

**Vérification du 28/11 (01fea6d)** — Plus aucun repli « argos » : Compose refuse de démarrer sans mot de passe (vide compris).

*Changement depuis le 21/11* : Sprint 2 (fiabiliser-sauvegarde-et-roles-base) : quatre mots de passe de base obligatoires par ${VAR:?…} ; .env.example sans valeur.

```text
compose.yaml:61, :64, :66, :68 (${…:?… obligatoire}) ; .env.example:12, :15, :18, :21 (valeurs vides) ; deploiement/postgres/initialiser-bases.sh:90-91.
```

*Réserve* : Vérification par `docker compose config` seulement (tasks.md:62-63, exécutée à la main) ; scripts/verifier-configuration.mjs (CI) contrôle la parité des variables, pas la forme :?.

*Au 20/11* : Ouvert (Faible) — Le repli « argos » subsiste pour la base et l'application.

*Au 21/11* : Ouvert (Faible) — Le repli « argos » subsiste pour la base, l'application et désormais Keycloak.

**Preuve d'origine**

```text
compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` (et :44 pour l'application) ; .env.example:6 valeur d'exemple à changer.
[AUD-DEP-10] compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` et :44 idem ; docs/deploiement/synology-et-pc.md:92 « défaut argos ».
```

**Impact** — Si le fichier .env est oublié, la base démarre avec un mot de passe trivial. Risque borné : port publié sur 127.0.0.1 seulement (compose.yaml:23) et réseau interne au projet.

**Action** — Remplacer le repli par `${ARGOS_DB_PASSWORD:?ARGOS_DB_PASSWORD obligatoire}` pour refuser le démarrage sans mot de passe explicite.

### AUD-SEC-07 — Sauvegardes complètes de la base non chiffrées (permissions 0600/0700 désormais posées)

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 1

**Vérification du 28/11 (01fea6d)** — Permissions corrigées ; archives toujours en clair (toutes les données, comptes Keycloak compris).

*Changement depuis le 21/11* : Sprint 2 (6cc2a4f) : umask 077 et dossier en 0700, archives 0600 ; la base keycloak est aussi sauvegardée. Chiffrement non fait : question Q-2 du porteur ouverte.

```text
scripts/sauvegarder-base.sh:15 (umask 077), :45-46 (mkdir puis chmod 700), :55-56 (pg_dump compressé) ; docs/deploiement/synology-et-pc.md:242 « leur chiffrement reste à décider (question Q-2) » ; openspec/changes/archive/2026-11-24-fiabiliser-sauvegarde-et-roles-base/proposal.md:108.
```

*Réserve* : Aucun test automatisé : seul scripts/essai-sauvegarde-restauration.sh, manuel, absent des workflows ; le risque concerne surtout les copies hors NAS (Hyper Backup).

*Au 20/11* : Ouvert (Faible) — Pas d'umask, pas de chiffrement.

*Au 21/11* : Ouvert (Faible) — Pas d'umask, pas de chiffrement.

**Preuve d'origine**

```text
scripts/sauvegarder-base.sh:19 `mkdir -p "$DEST"` et :22 `pg_dump ... | gzip > "$FILE"` exécutés en root sur le NAS (commentaire l.4), sans umask ni chiffrement ; copie hors NAS recommandée (docs/deploiement/synology-et-pc.md:68).
```

**Impact** — Les dumps (comptes, e-mails, projets, journaux) sont lisibles par d'autres comptes du NAS selon l'umask et voyagent en clair vers le stockage de sauvegarde.

**Action** — `umask 077` en tête du script, dossier en 700 ; chiffrer le dump (age/gpg) ou activer le chiffrement côté Hyper Backup.

### AUD-SEC-09 — Recherche plein texte et veille personnelle (/me/veille) sans limite de débit

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Limites de débit en place avant tout travail, testées.

*Changement depuis le 21/11* : Sprint 6 (f8b39b5) : LimiteRecherche commune à /recherche et /vues/{id}/resultats (60/min/compte) ; limiteur distinct sur /me/veille (60/min).

```text
argos-server/src/main/java/io/argos/api/recherche/RechercheResource.java:67 ; api/recherche/VuesResource.java:129 ; api/veille/VeilleProfilsResource.java:186 ; tests api/recherche/LimiteRechercheTest, LimiteDebitRechercheVeilleTest.
```

*Au 20/11* : Ouvert (Faible) — Toujours aucune limite sur la recherche plein texte ; périmètre élargi à la veille personnelle.

*Au 21/11* : Ouvert (Faible) — Toujours aucune limite sur la recherche plein texte ni sur la veille personnelle.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/recherche/RechercheResource.java:48 `rechercher(...)` et VuesResource `/{id}/resultats` : aucun LimiteurDebit (présent seulement sur veille web, synthèse d'activité et MCP).
```

**Impact** — Un compte peut enchaîner des requêtes plein texte coûteuses sur le processeur du NAS (dégradation pour les autres utilisateurs).

**Action** — Appliquer LimiteurDebit (ex. 60/min/compte) aux routes de recherche, ou une limite globale au reverse proxy.

### AUD-SEC-23 — Desktop : l'écoute de bouclage 127.0.0.1 accepte le premier GET /argos/retour sans comparer le state et se ferme (interruption de connexion par un processus local)

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé et testé.

*Changement depuis le 21/11* : Sprint 6 (dab815c) : l'écoute mémorise le state de l'URL validée, compare en temps constant, répond 400 sans fermer.

```text
argos-desktop/src/connexion.js:115-119 (stateAttendu, timingSafeEqual), :143 (refus 400 sans fermer), :151 (fermer seulement si state égal) ; test argos-desktop/test/connexion.test.js:177 (ACC-14-S1), exécuté par `npm run check` dans le job desktop de la CI.
```

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — L'écoute accepte toujours le premier GET /argos/retour sans comparer le state, puis se ferme.

**Preuve d'origine**

```text
argos-desktop/src/connexion.js:99-102 `retourAttendu` ne teste que méthode, chemin et écoute active ; :142-150 toute requête conforme reçoit la page de retour puis `fermer(url.search...)` ; le state est pourtant connu du processus principal (URL validée à :172, paramètre `state` recopié à :69-72) ; la vérification du state n'a lieu que dans l'IHM (argos-ui/src/session/oidc.ts:148), qui rejette alors le retour et abandonne la tentative.
```

**Impact** — Pendant la fenêtre de connexion (5 min au plus, port éphémère), un processus local ou toute source capable d'émettre un GET vers 127.0.0.1 (balayage de ports) fait échouer la connexion en cours. Aucun vol de jeton : state vérifié côté IHM et code inutilisable sans le vérificateur PKCE. Déni de service ponctuel seulement.

**Action** — Mémoriser le state de l'URL autorisée dans l'écoute et ne clore l'écoute que sur un retour portant ce state (comparaison en temps constant) ; répondre 400 aux autres sans fermer ; ajouter le cas à argos-desktop/test/connexion.test.js.

### AUD-SEC-24 — Campagne de performance e2e : serveur lancé par « mvn.cmd » via le PATH avec shell: true sous Windows, contrairement à la règle des chemins absolus

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Plus de résolution par le PATH ni de shell: true dans la campagne.

*Changement depuis le 21/11* : Sprint 1 (verifier-types-et-frontieres-modules, 4a7b8d2) : support/maven.mjs résout Maven par ARGOS_E2E_MVN, MAVEN_HOME/M2_HOME puis emplacements fixes ; sous Windows, cmd.exe par chemin absolu, sans shell: true ; arguments interprétables refusés.

```text
e2e/support/maven.mjs:44-50, :59-64, :90-98 ; e2e/performance/mesurer-p95.mjs:135-138 ; e2e/playwright.config.ts:39 ; test e2e/performance/maven.test.mjs (job outils : `node --test ... "e2e/performance/*.test.mjs"`).
```

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — mvn toujours résolu par le PATH avec shell: true sous Windows.

**Preuve d'origine**

```text
e2e/performance/mesurer-p95.mjs:117-120 `const mvn = platform() === 'win32' ? 'mvn.cmd' : 'mvn'` puis `spawn(mvn, ['-B','-q','-f', join(RACINE, 'argos-server','pom.xml'), ...], { ..., shell: platform() === 'win32' })` ; commentaire :77-78 « Exécutables appelés par leur chemin absolu ... la campagne ne dépend pas du PATH » ; docker, git et taskkill le sont (support/docker.mjs:5-19, mesurer-p95.mjs:79-84,95-96).
```

**Impact** — Poste du développeur uniquement : un mvn.cmd placé plus tôt dans le PATH est exécuté ; avec shell: true, le chemin du dépôt (RACINE) est concaténé sans échappement dans la ligne de cmd.exe : un chemin contenant espaces, & ou ^ casse la commande ou injecte une commande. Pas d'impact sur le serveur déployé.

**Action** — Résoudre mvn comme docker/git (emplacements fixes + variable ARGOS_E2E_MVN) et l'appeler sans shell (sous Windows : `cmd.exe /d /s /c` avec arguments échappés, ou lancer directement java/quarkus-run) ; même revue pour le webServer de playwright.config.ts:89.

### AUD-SEC-25 — Image : code de l'application modifiable par l'utilisateur d'exécution et conteneurs sans durcissement (read_only, cap_drop, no-new-privileges)

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Code non modifiable par l'utilisateur d'exécution, conteneurs durcis ; parité contrôlée en CI.

*Changement depuis le 21/11* : Changement completer-configuration-conteneurs (encore actif dans openspec/changes, code fusionné) et durcir-conteneurs-de-service : quarkus-app copié sans --chown (propriété root) ; fragment x-durcissement (read_only, cap_drop ALL, no-new-privileges) sur les quatre services ; image Keycloak optimisée.

```text
Dockerfile:54 (COPY sans --chown), :58 (USER 1001) ; compose.yaml:29-34 (x-durcissement), :38, :90, :251, :260 ; deploiement/keycloak/Dockerfile (COPY --chown=0:0) ; scripts/verifier-configuration.mjs et .test.mjs (contrôle read_only/cap_drop/no-new-privileges, job outils).
```

*Réserve* : Ollama reste root dans son conteneur (sans capacité, compose.yaml:264) ; changements OpenSpec non archivés.

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Code de l'application toujours propriété de l'utilisateur d'exécution ; conteneurs sans read_only, cap_drop ni no-new-privileges.

**Preuve d'origine**

```text
Dockerfile:48 `COPY --from=server --chown=1001:0 /src/argos-server/target/quarkus-app/ ./` puis :51 `USER 1001` (jars et lib appartiennent à l'utilisateur qui exécute la JVM ; présent dès 9d96406, non relevé) ; compose.yaml:34-59 (service argos) sans `read_only`, `cap_drop`, `security_opt: [no-new-privileges:true]`.
```

**Impact** — Une exécution de code dans la JVM (dépendance vulnérable) pourrait réécrire durablement les jars de l'application dans le conteneur ; défense en profondeur absente avant l'exposition publique. Risque d'hygiène, pas de vecteur direct identifié.

**Action** — Copier quarkus-app sans --chown (root, lecture seule pour 1001), comme déjà fait pour /opt/argos/licenses (Dockerfile:43-50) ; ajouter au service argos `read_only: true` + tmpfs /tmp, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`.

### AUD-SEC-36 — Contrôles de santé publics sans session : état et date de dernière synchronisation de chaque source, file d'envoi des alertes et état du fournisseur IA exposés

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 1
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/resources/application.properties:432-433 (/q/health et /q/health/* en permit) ; ingestion/SanteSources.java:70-73 (withData <source>.etat et <source>.derniere-synchronisation-reussie) ; alerting/SanteEnvoiAlertes.java:79-82 (en-attente, en-echec-24h) ; aigateway/SantePasserelleIa.java:33-35 (fournisseur, état) ; fichiers ajoutés dans le delta (d990fe5, 2026-11-24).
```

**Impact** — Sur un service public, n'importe qui lit l'inventaire des sources collectées, leurs pannes et la charge d'envoi des alertes : aide à la reconnaissance et au choix du moment d'une attaque. Aucune donnée personnelle.

**Action** — Ne garder public que /q/health/live et /q/health/ready sans données (quarkus.smallrye-health : désactiver les détails hors session, ou interface de gestion sur un port non publié) ; réserver le détail à la politique admin-plateforme comme /q/metrics.

### AUD-SEC-37 — MatriceRolesTest ne vérifie pas l'isolation par objet : 49 opérations sur 103 rendent le même code à tous les rôles authentifiés, dont 19 appelées sur un identifiant inexistant (404 pour tous)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité, Tests · **Effort** : M · **Sprint** : 4
- **Nouveau** le 28/11
- **Fusionne** : AUD-TST-29

**Preuve**

```text
argos-server/src/test/java/io/argos/api/comptes/MatriceRolesTest.java:285-301 ({id}, {compteId}, {invitationId}, {repositoryId}, {versionId} remplacés par 00000000-0000-0000-0000-00000000dead hors fiches) ; src/test/resources/securite/matrice-roles.tsv : 19 lignes à 404 pour LECTEUR, ADMIN_ESPACE, EXPLOITANT, AUTRE_ESPACE et EXPERT (GET /api/v1/impacts/{id}, POST /api/v1/impacts/{id}/transitions, GET /api/v1/signals/{id}, /vues/{id}, /alertes/{id}, DELETE /api/v1/subscriptions/{id}, /veille-web/sources/{id}…) ; MCP (8 outils) hors OpenAPI, donc hors matrice (MatriceRolesTest.java:202-210).
[AUD-TST-29] argos-server/src/test/resources/securite/matrice-roles.tsv : lignes dont les colonnes LECTEUR à EXPERT valent toutes 404 -> GET/POST /api/v1/alertes/{id}[/remise], GET /api/v1/impacts/{id}, POST /api/v1/impacts/{id}/transitions, POST /api/v1/invitations/{invitationId}/acceptation, GET /api/v1/signals/{id}[/impacts|/versions/{versionId}/raw], DELETE et POST /api/v1/subscriptions/{id}/{leave,reactivation,suspension}, GET /api/v1/veille-web/actualites/{id}, POST …/resume-ia, DELETE /api/v1/veille-web/sources/{id}, POST …/collectes, GET/DELETE /api/v1/vues/{id}, GET /api/v1/vues/{id}/resultats (script inline, 19/103) ; api/comptes/MatriceRolesTest.java:59-62 (« opérations à effet appelées sur des ressources inexistantes »).
```

**Impact** — La matrice garantit qu'aucune opération n'est oubliée et le contrôle de rôle au niveau de l'espace et du projet, mais pas qu'un membre d'un autre espace ou un autre compte ne peut lire ou modifier un objet existant : une régression d'IDOR sur ces 19 opérations passerait la matrice (elle reste couverte, de façon dispersée, par d'autres tests d'isolation).

**Action** — Créer pour chaque opération par identifiant un objet réel appartenant à l'espace E (ou au compte LECTEUR) et vérifier 404 pour AUTRE_ESPACE et les autres comptes ; ajouter une matrice des outils MCP.

### AUD-TST-02 — Seuil de couverture JaCoCo bloquant en CI (85 % des lignes, 70 % des branches, global et par module)

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : seuil global bloquant et seuil par module en CI ; configuration conforme au guide Quarkus (agent + quarkus-jacoco sur le même fichier).

*Changement depuis le 21/11* : dd9998b : jacoco-maven-plugin 0.8.15 (prepare-agent avec exclClassLoaders *QuarkusClassLoader, destFile jacoco-quarkus.exec, append ; report et check en phase verify) et extension quarkus-jacoco ; check BUNDLE bloquant LINE 0,85 / BRANCH 0,70 ; rapport publié comme artefact du job serveur. 957af23 ajoute un contrôle par module (scripts/verifier-couverture-modules.mjs) aux mêmes seuils, qui va au-delà du seuil global retenu par le porteur.

```text
argos-server/pom.xml:41-47 (seuils 0.85/0.70), :191-196 (quarkus-jacoco), :288-340 (prepare-agent, report, check BUNDLE) ; .github/workflows/integration-continue.yml:48-62 ; scripts/verifier-couverture-modules.mjs:17-21, :94-105 ; scripts/verifier-couverture-modules.test.mjs (dans les 75 tests de scripts, verts)
```

*Réserve* : Taux effectifs à 01fea6d à reprendre de la mesure JaCoCo de l'orchestrateur ; la matrice de suivi du mainteneur reste à « non exécuté » pour ce constat (openspec/changes/archive/2026-11-24-mettre-en-place-integration-continue/tasks.md:81, design.md:155) alors que les tâches 3.1 et 3.2 sont cochées et que tasks.md:63 cite « All coverage checks have been met » ; le profil sans-docker écarte le seuil (pom.xml:356-359).

*Au 20/11* : Ouvert (Faible) — Aucun seuil ni rapport de couverture dans le build.

*Au 21/11* : Ouvert (Faible) — Aucun seuil ni rapport de couverture dans le build.

**Preuve d'origine**

```text
argos-server/pom.xml:177-185 (seul plugin de test : maven-surefire-plugin, configuration limitée à java.util.logging.manager) ; grep -n jacoco pom.xml -> aucun résultat
```

**Impact** — Aucune alerte si la couverture recule sur un module critique ; la mesure de cet audit a dû être lancée par une commande ajoutée à la main.

**Action** — Ajouter jacoco-maven-plugin (prepare-agent + report, compatible @QuarkusTest via quarkus-jacoco) et un check avec seuils par paquet (au moins api.security, mcp, workspace, alerting) une fois la CI en place.

### AUD-TST-03 — Pyramide redressée : 49,8 % des méthodes de test serveur (507 sur 1 018) exigent encore Docker ; profil sans-docker non exécuté en CI

- **Statut** : Fermé avec réserve · **Sévérité** : Faible (était Moyenne le 21/11) · **Axes** : Tests · **Effort** : L · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Pyramide redressée : 507 méthodes sur 1 018 (49,8 %) restent dans des classes exigeant Docker, contre 71 % au 21/11 ; la majorité tient de justesse (511 contre 507).

*Changement depuis le 21/11* : Sprint 11 (PR #79 : 4fb8983, 7b82ace, 1d7bbfa, b29e815, 1608a63, 35c7dac) : serveur HTTP simulé à instance (testsupport.ServeurHttpSimule), doublures et horloge fixe ; nombreux tests unitaires ajoutés (scm 69, ingestion 73, inventory 32, catalog 32, impact 21 dont CorrelationDeterministeTest, alerting 23 dont RegleAlerteTest, api 42 dont TraductionCriteresTest, aigateway 21 dont PasserelleIaUnitaireTest qui couvre la validation des sorties) ; profil Maven sans-docker excluant les classes de src/test/tests-docker.txt, liste tenue exacte par ListeTestsDockerTest.

```text
scripts-test/inventaire.py sur build-tests à 01fea6d -> 1 018 méthodes (@Test, @ParameterizedTest, @TestFactory, @RepeatedTest) dans 219 classes ; 105 classes listées par argos-server/src/test/tests-docker.txt (109 lignes) = 507 méthodes ; 114 classes sans Docker = 511 méthodes ; argos-server/pom.xml:356-376 (profil sans-docker, excludesFile) ; architecture/ListeTestsDockerTest.java:31-32, :60-65
```

*Réserve* : MoteurAlertes n'est toujours exercé que par des @QuarkusTest (alerting/AlertesTest.java, chain/RequetesConstantesTest.java) ; ValidationSortie n'est nommée dans aucun test (couverte indirectement par PasserelleIaUnitaireTest) ; le profil sans-docker n'est exécuté ni par la CI ni par cet audit (pas de Maven) : rien ne garantit qu'il reste vert ; ListeTestsDockerTest ne détecte que @QuarkusTest/@QuarkusIntegrationTest en début de ligne et un import direct de Testcontainers (ListeTestsDockerTest.java:31-32).

*Au 20/11* : Ouvert (Moyenne) — Pyramide toujours inversée, à 71 %.

*Au 21/11* : Ouvert (Moyenne) — Pyramide toujours inversée, à 71 % ; les découpages du lot 22 n'ont pas été mis à profit pour des tests unitaires.

**Preuve d'origine**

```text
grep -rl @QuarkusTest src/test/java | wc -l -> 45 classes ; décompte @Test/@ParameterizedTest : 239 dans les classes @QuarkusTest, 82 dans 18 classes unitaires (total 321). README.md:49 « tests : mvn verify (Docker requis : base jetable) »
[À 9d96406] 259 méthodes @QuarkusTest sur 363 = 71 % (48 classes).
```

**Impact** — Boucle de retour lente, impossible sans Docker ; la logique métier (corrélation, règles d'alerte, impacts) n'est vérifiable qu'à travers la pile complète, ce qui rend les échecs plus coûteux à diagnostiquer.

**Action** — Extraire en tests unitaires purs la logique déterministe déjà isolable (CorrelationEngine, MoteurAlertes, règles de RegleAlerte, RechercheVeille/TraductionCriteres, ValidationSortie) ; garder les @QuarkusTest pour les parcours, la RLS et la sécurité.

### AUD-TST-04 — Couverture par module contrôlée en CI ; tous les modules ont des classes de test dédiées

- **Statut** : Fermé avec réserve · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Tests · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : plus aucun module sans classe de test ; un module sous 85 % / 70 % fait échouer le job serveur.

*Changement depuis le 21/11* : Changement 2026-11-26-alimenter-inventaire-et-completer-ihm (§7) et sprint 11 : chaque module a désormais des classes de test dédiées (workspace 5, inventory 5 dont CheminsEchecInventaireTest d311291, vulnintel 6, releaseradar 4, audit 4 dont JournalRefusTest) ; contrôle bloquant de la couverture par module en CI (957af23).

```text
scripts-test/inventaire.py -> classes_test par module : audit 4, inventory 5, releaseradar 4, vulnintel 6, workspace 5, scm 8 (69 méthodes unitaires) ; .github/workflows/integration-continue.yml:53-55 ; scripts/verifier-couverture-modules.mjs:94-105
```

*Réserve* : Taux par module à 01fea6d à confirmer par la mesure JaCoCo de l'orchestrateur ; VuesEnregistrees et GardeAppels ne sont toujours nommées dans aucun test (couverture indirecte par les parcours et ErreursOutilsMcpTest).

*Au 20/11* : Partiel (Faible) — Partiel : scm est corrigé (4 classes unitaires et 2 de contrat) et sa couverture passe de 26,9 % à 51,8 % des lignes (41,3 % des branches) d'après la mesure JaCoCo du 20/11 ; cinq modules restent sans classe de test dédiée (workspace, inventory, vulnintel, releaseradar, audit) mais sont couverts à 81–89 % des lignes par les parcours.

*Au 21/11* : Partiel (Faible) — Partiel : scm corrigé ; cinq modules toujours sans classe de test dédiée, désormais modifiés sur leurs chemins d'échec sans test.

**Preuve d'origine**

```text
Comptage main/test par paquet : workspace 24/0, inventory 10/0, scm 8/0, vulnintel 9/0, releaseradar 5/0, regintel 3/0, audit 4/0 ; impact 13 fichiers (1 919 l.) pour 1 test (30 l.). Classes jamais nommées dans src/test : workspace/subscription/SubscriptionService, workspace/vue/VuesEnregistrees, alerting/MoteurAlertes, mcp/GardeAppels, mcp/OutilsSecurite, audit/JournalRefus, releaseradar/* (0/4)
[À 9d96406] Six modules sans test dédié : workspace (26/1 693 l.), inventory, vulnintel, releaseradar, regintel, audit ; scm a DepotGithubTest.
[Mesure] mvn clean verify + JaCoCo 0.8.13 sur le poste du mainteneur : scm 26,9 % lignes / 8,9 % branches (290 l.) ; total 83,8 % / 68,6 %.
```

**Impact** — La couverture globale mesurée est bonne (83,8 % des lignes, 68,6 % des branches), mais scm, qui lit les dépôts GitHub et GitLab des utilisateurs, est presque entièrement hors tests ; les six modules sans test dédié ne sont couverts que par les parcours, sans cas limites ciblés.

**Action** — Ajouter des tests ciblés de scm (pagination, quotas, erreurs de forge) jusqu'au seuil de couverture, puis des tests dédiés des cas limites de workspace (rôles, dernier administrateur, invitations).

### AUD-TST-06 — Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s, marge de 900 ms sur le délai IA)

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé et protégé : plus aucun Thread.sleep dans argos-server/src/test, règle d'architecture avec exemple négatif.

*Changement depuis le 21/11* : Changement 2026-11-25-injecter-horloge-et-executeurs (tâches 2.1 à 2.4) : SimulatedResumeIa répond par un CountDownLatch au lieu de dormir 5 s ; DefenseEnProfondeurTest observe pg_locks avec Awaitility ; CollecteWebTest utilise Awaitility et une horloge qui avance ; PasserelleIaTest ne mesure plus l'horloge réelle. 7f33aa8 ajoute la règle ArchUnit TestsSansSommeilTest qui interdit Thread.sleep dans les tests.

```text
grep -rnE 'Thread\.sleep' argos-server/src/test/java -> 0 hors règle ; testsupport/SimulatedResumeIa.java:33, :78 (reponseLente.await()) ; api/comptes/DefenseEnProfondeurTest.java:166-168 (Awaitility + pg_locks WHERE NOT granted) ; signal/web/CollecteWebTest.java:98, :165-173 ; architecture/TestsSansSommeilTest.java:20-45
```

*Réserve* : Usages résiduels mineurs de l'horloge réelle : alerting/RelaisMuetTest.java:121-123 (borne réelle de 30 s), impact/SuggestionsRadarTest.java:46 (LocalDate.now(), sensible au passage de minuit).

*Au 20/11* : Ouvert (Faible) — Délais réels et horloge réelle toujours utilisés dans les assertions.

*Au 21/11* : Ouvert (Faible) — Délais réels et horloge réelle toujours utilisés dans les assertions.

**Preuve d'origine**

```text
alerting/AlertesTest.java:314-323 (Instant.now() avant/après publier(), assertTrue(attente < 4 s)) avec testsupport/SimulatedResumeIa.java:73 Thread.sleep(5_000) et application.properties:68 %test…resume-ia.delai-max=PT1S ; api/comptes/DefenseEnProfondeurTest.java:158 Thread.sleep(1000); assertFalse(retrait.isDone()) ; signal/web/CollecteWebTest.java:120-125 boucle System.currentTimeMillis()+5000 / Thread.sleep(10)
```

**Impact** — Marge de 3 s sur machine lente ou chargée (runner CI partagé, NAS) : échecs intermittents possibles ; le test de verrou (sleep 1 s) peut passer à vide si l'ordonnancement retarde la soumission.

**Action** — Injecter une horloge/un délai simulé dans la passerelle de résumé (comme CollecteWeb.horloge) au lieu de dormir ; remplacer le sleep de DefenseEnProfondeurTest par une observation du verrou (pg_locks / pg_stat_activity wait_event) ; utiliser Awaitility pour les attentes actives.

### AUD-TST-08 — Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : nettoyage dérivé du schéma, exhaustivité testée.

*Changement depuis le 21/11* : 0575db8 (changement 2026-11-26-alimenter-inventaire-et-completer-ihm, §7) : TestDb.reset() lit les tables dans pg_tables et les vide toutes en un TRUNCATE … CASCADE, sauf deux listes motivées (TABLES_CONSERVEES, TABLES_REMISES_A_ZERO) ; NettoyageBaseTest vérifie la partition, l'absence de clé étrangère d'une table conservée vers une table vidée et l'état après remise à zéro ; la seconde liste d'ArtisteFixtures a disparu.

```text
testsupport/TestDb.java:27-31, :41-48, :93-96, :112-140 ; testsupport/NettoyageBaseTest.java:36-49, :54-65, :69-89 ; testsupport/ArtisteFixtures.java:28 (DELETE ciblé par utilisateur, plus de TRUNCATE)
```

*Réserve* : Les tables conservées (compte, espace, appartenance, invitation…) s'accumulent d'une classe à l'autre ; MatriceRolesTest doit retirer ses espaces dans un @AfterAll pour ne pas rendre d'autres classes dépendantes de l'ordre (api/comptes/MatriceRolesTest.java:166-179).

*Au 20/11* : Ouvert (Faible) — Isolation toujours fondée sur deux listes codées en dur, sans contrôle d'exhaustivité.

*Au 21/11* : Ouvert (Faible) — Isolation toujours fondée sur deux listes codées en dur, sans contrôle d'exhaustivité.

**Preuve d'origine**

```text
testsupport/TestDb.java:49-56 (TRUNCATE dette_version, … sync_cursor CASCADE) ; comparaison avec les CREATE TABLE des 14 migrations -> non vidées : appartenance, compte, espace, invitation, workspace_preference_utilisateur, ia_prompt (source partiellement : DELETE … WHERE id <> 'OSV')
[À 9d96406] TestDb.java:49-56 inchangé ; les 4 tables de V15 sont vidées hors TestDb par ArtisteFixtures.java:24 (seconde liste codée en dur).
```

**Impact** — Isolation par convention (chaque test crée ses espaces, cf. Javadoc TestDb) ; toute nouvelle table oubliée dans la liste fait fuir de l'état entre classes et rend les résultats dépendants de l'ordre d'exécution.

**Action** — Générer la liste à partir de information_schema (toutes les tables du schéma sauf flyway_schema_history et référentiels explicitement conservés) ou ajouter un test qui échoue si une table n'est ni vidée ni déclarée conservée.

### AUD-TST-09 — IHM : client API (10 tests) et 13 vues sur 13 testés en rendu (Vitest + happy-dom), en plus de 218 tests node --test

- **Statut** : Fermé avec réserve · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Tests · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : client REST et 13 vues sur 13 testés en rendu.

*Changement depuis le 21/11* : 95166bb et changement 2026-11-26-alimenter-inventaire-et-completer-ihm (§6) : Vitest 5 + happy-dom (argos-ui/vitest.config.ts) ; 14 fichiers tests/vues/*.spec.ts, 58 tests : client.spec.ts (10 tests de appelerApi avec fetch simulé : jeton, JSON, 204, ApiError, corps non JSON, 401/403, délai, annulation, serveur injoignable) et une spec par vue pour les 13 vues ; npm test enchaîne node --test et vitest run ; contrat de chemins avec l'OpenAPI (contrat-api.test.mjs).

```text
argos-ui/package.json « test »: node --test "tests/**/*.test.mjs" && vitest run ; argos-ui/vitest.config.ts:8-14 ; tests/vues/client.spec.ts:31-99 ; ls src/views -> 13 vues, ls tests/vues -> 13 specs de vue + client ; npm test -> 218/218 (node --test) et 58/58 (Vitest)
```

*Réserve* : La plupart des vues n'ont que 2 ou 3 tests (grep -c test( tests/vues/*.spec.ts) ; le contrat ne vérifie que chemins et méthodes, pas les schémas (voir AUD-ARC-12).

*Au 20/11* : Partiel (Faible) — Logique et rendu statique largement couverts ; client REST effectif et vues toujours hors tests unitaires.

*Au 21/11* : Partiel (Faible) — Logique et rendu statique couverts ; client REST effectif et vues toujours hors tests unitaires.

**Preuve d'origine**

```text
argos-ui/package.json « test »: node --test "tests/**/*.test.mjs" (pas de vitest ni @vue/test-utils) ; imports des tests : src/contenu/{alertes-poste,resolution,resume-ia,liens}.ts uniquement ; resume-ia.test.mjs:26-29 lit AlertesView.vue en texte et vérifie l'absence de v-html ; src/api/client.ts (207 l.) et les 9 composants .vue non testés
```

**Impact** — Le client API (jetons, erreurs, formats) et le rendu des vues peuvent régresser sans détection ; les contrôles par expression régulière sur le source ne garantissent pas le comportement rendu.

**Action** — Introduire vitest + @vue/test-utils (environnement happy-dom) : tests du client API avec fetch simulé (401, erreurs, sérialisation) et tests de rendu des vues Alertes/Affichage/Réglages.

### AUD-TST-10 — Desktop : main.js, fenêtre et réglages d'emballage testés (main.test.js, fenetre.test.js, reglages-emballes.test.js)

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : main.js et sa configuration de sécurité sont testés.

*Changement depuis le 21/11* : 1ce7ff4 (changement 2026-11-26-alimenter-inventaire-et-completer-ihm, §5) : la construction de la fenêtre est extraite dans src/fenetre.js et les réglages d'emballage dans src/reglages-emballes.js, testés en fonctions pures ; main.test.js charge le main.js livré avec un Electron factice et vérifie webPreferences, CSP selon les réglages emballés et l'environnement, permissions, navigation, ouverture de fenêtres, mise à jour.

```text
argos-desktop/test/main.test.js:43-50 (contextIsolation true, nodeIntegration false, sandbox true), :63-101 (CSP et réglages emballés), :103-128 ; test/fenetre.test.js:28-113 ; test/reglages-emballes.test.js:8-33 ; src/fenetre.js:34-36 ; npm run check -> 92/92
```

*Au 20/11* : Partiel (Faible) — Surface preload/IPC protégée ; webPreferences de main.js toujours sans test.

*Au 21/11* : Partiel (Faible) — Surface preload/IPC protégée ; main.js, dont la configuration de sécurité vient de gagner une nouvelle source de réglages, toujours sans test.

**Preuve d'origine**

```text
argos-desktop/test/*.test.js ne requièrent que ../src/{preferences,notifications,poste,ui-protocol} ; argos-desktop/src/main.js:92-94 (contextIsolation: true, nodeIntegration: false, sandbox: true) non vérifié par un test
```

**Impact** — Une régression sur les webPreferences, la CSP ou la surface exposée par preload (contextBridge) ouvrirait l'exécution de code distant dans le client sans qu'aucun test ne le signale.

**Action** — Extraire la construction des options de BrowserWindow et la liste des API exposées par preload dans des fonctions pures testées (contextIsolation/sandbox à true, nodeIntegration à false, liste blanche des canaux IPC).

### AUD-TST-12 — Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest)

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : le jar et l'image sont démarrés par des tests.

*Changement depuis le 21/11* : ecf45f5 (2026-11-27) : skipITs passe à false, maven-failsafe-plugin exécute ArtefactEmpaqueteIT (@QuarkusIntegrationTest, 4 tests : santé avec la base, /api/v1/system/info, 401 avec en-têtes de sécurité, /mcp 401) sur le jar ; job e2e de la CI qui construit l'image par le Dockerfile et y joue la campagne Playwright (e2e/compose.image.yaml).

```text
argos-server/pom.xml:34-36 (skipITs false), :245-262 (failsafe, profil test) ; artefact/ArtefactEmpaqueteIT.java (4 tests) ; .github/workflows/integration-continue.yml:176-177, :199-204 ; e2e/compose.image.yaml:38-92 ; e2e/playwright.config.ts:21-34, :112
```

*Réserve* : Le jar est lancé sous le profil test (pom.xml:261), pas sous la configuration de production ; seule l'exécution e2e en CI démarre l'image en profil prod, et elle n'a pas pu être observée ; le job e2e a été ajouté le jour même de la base (9a0d3e6, dffbe06 du 2026-11-28).

*Au 20/11* : Ouvert (Faible) — Aucun test sur l'artefact empaqueté.

*Au 21/11* : Ouvert (Faible) — Aucun test sur l'artefact empaqueté ; le parcours « IHM servie par le serveur » introduit par la PR #28 n'est exercé nulle part.

**Preuve d'origine**

```text
argos-server/pom.xml:28 <skipITs>true</skipITs> ; find . -name "*IT.java" -> aucun résultat ; Dockerfile:22 mvn -B package -DskipTests
```

**Impact** — Le jar/l'image réellement déployés (configuration %prod, Flyway au démarrage, chargement des ressources) ne sont jamais démarrés par un test ; les écarts entre mode test et mode prod ne sont pas détectés avant déploiement.

**Action** — Ajouter un smoke test @QuarkusIntegrationTest (santé, /api/v1/system/info, 401 sans jeton) ou un test compose (docker compose up + curl) dans la future CI.

### AUD-TST-19 — Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image)

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests, Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : la CI teste sur le JDK de production et toujours après clean.

*Changement depuis le 21/11* : dd9998b : job serveur sur Temurin 17 avec clean verify ; maven-enforcer (garde-jdk) : [17,) par défaut, [17,18) sous le profil integration-continue activé par la CI ; README consigne clean et la sortie de l'IDE hors de target/.

```text
.github/workflows/integration-continue.yml:39-49 ; argos-server/pom.xml:37-40, :264-283, :349-355 ; README.md:105
```

*Réserve* : Refus de l'enforcer sous JDK 21/24 non démontré (matrice du mainteneur : « non exécuté », openspec/changes/archive/2026-11-24-mettre-en-place-integration-continue/tasks.md:80) ; en local, un JDK 21+ reste accepté par défaut.

*Au 20/11* : Ouvert (Faible) — Pas de garde de version de JDK pour les tests.

*Au 21/11* : Ouvert (Faible) — Pas de garde de version de JDK pour les tests.

**Preuve d'origine**

```text
Mesure communiquée par le mainteneur (non rejouée ici) : premier `mvn verify` sans clean -> 0 test lancé, « TestEngine archunit failed to discover tests », LimiteDebitMcpTest.class absente de target/test-classes (le source existe : src/test/java/io/argos/mcp/LimiteDebitMcpTest.java:27) ; `mvn clean verify` -> 416 tests, BUILD SUCCESS. pom.xml:16 maven.compiler.release=17 ; Dockerfile:25 eclipse-temurin:17-jre ; aucun maven-enforcer ni toolchains dans pom.xml.
```

**Impact** — Un build local peut échouer ou, pire, passer sur des classes de test périmées. Les tests passent sur JDK 24 alors que l'image tourne sur JRE 17 : une différence de comportement de la JVM (TLS, HttpClient, GC) n'est pas couverte. L'échec observé était bruyant (engine en erreur), donc pas silencieux.

**Action** — En CI : toujours `mvn -B clean verify` sur un JDK 17 (setup-java 17, ou maven-toolchains). Faire écrire l'IDE dans un répertoire distinct de target/ (ex. VS Code Java : bin/). Ajouter maven-enforcer (requireJavaVersion [17,18) en CI).

### AUD-TST-20 — Campagne E2E (37 tests, 15 fichiers) automatisée en CI contre l'image construite ; aucune preuve d'exécution consultable

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé dans la configuration : campagne automatisée à chaque pull request, plus de réutilisation silencieuse d'un serveur.

*Changement depuis le 21/11* : ecf45f5, 7ea49a0, 9a0d3e6, dffbe06 : job e2e de la CI (Docker, Electron, Chromium, xvfb + fluxbox + dunst) qui joue npm test de e2e contre l'image (ARGOS_E2E_IMAGE) ; reuseExistingServer devient opt-in (ARGOS_E2E_REUTILISER=1), false pour l'image et les sources simulées. Campagne passée à 37 tests dans 15 fichiers (accessibilite-ecrans.spec.ts ajouté).

```text
.github/workflows/integration-continue.yml:160-214 ; e2e/support/pile.mjs:4-5, :15 ; e2e/playwright.config.ts:21-34 (reuseExistingServer: false), :43, :61, :112 ; e2e/scripts/lancer.mjs:16, :25-34 ; npx playwright test --list -> « Total: 37 tests in 15 files » (avec ou sans ARGOS_E2E_IMAGE)
```

*Réserve* : Aucune exécution observable : les rapports ne sont publiés qu'en cas d'échec (integration-continue.yml:208-214) et le seul rapport versionné reste e2e/resultats/chaine-automatisee/rapport.json du 2026-11-19 (34 tests, f433989), antérieur à la campagne actuelle de 37 ; non rejouable ici (docker build exige Maven Central).

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Campagne principale toujours manuelle, et non rejouée (ou non consignée) après les lots 20 à 23 et la PR #28.

**Preuve d'origine**

```text
e2e/playwright.config.ts:89-100 (serveur par 'mvn … quarkus:dev', reuseExistingServer: true, variables SANS_COLLECTE_EXTERNE et CHAINE_SIMULEE passées seulement au serveur que Playwright démarre) et :103-106 (Vite, reuseExistingServer: true) ; e2e/README.md « Lancer » (prérequis : Docker, JDK 17, Maven 3.9 avec accès aux dépôts, binaire Electron 45.3.2, Chromium révision 1228) ; e2e/support/electron.ts:20-31 (binaire Electron local, échec explicite s'il manque) ; npx playwright test --list -> « Total: 34 tests in 14 files » (30 parcours + 3 préparations + 1 construction) ; dernier rapport de référence e2e/resultats/chaine-automatisee/rapport.json : 34 attendus, 0 échec, 391 s, 2026-11-19 ; application.properties:46, :54, :134, :223, :246 (tâches planifiées actives par défaut, cadence 1 h pour la synchronisation des avis)
```

**Impact** — Les parcours les plus proches de l'usage réel (connexion OIDC web et desktop, notifications du poste, chaîne de veille automatisée, protocole MCP) ne protègent que lorsqu'ils sont lancés à la main sur un poste équipé ; ils n'ont pas pu être rejoués ici (Maven Central inaccessible, binaire Electron non téléchargé). Si un serveur quarkus:dev tourne déjà sur 8090, il est réutilisé sans les variables de la campagne : ses tâches planifiées gardent leurs adresses réelles (la garde réseau ne couvre que le navigateur et Electron) et le parcours de la chaîne automatisée échoue ou mesure autre chose.

**Action** — Dans la future CI (AUD-TST-01) : job E2E sur runner Docker avec Xvfb pour Electron, binaire Electron et Chromium en cache, reuseExistingServer: !process.env.CI pour le serveur et Vite ; en local, faire vérifier par la préparation que le serveur réutilisé expose la configuration attendue (par exemple une sonde de configuration de test) ou refuser la réutilisation quand CHAINE_SIMULEE est requis.

### AUD-TST-23 — L'audit d'accessibilité n'a pas suivi les écrans livrés après le 17/11 : vue d'inventaire, actions sur les abonnements et traitement des impacts sans mesure de contraste ni revue

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests, Documentation · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé dans le code : les trois écrans sont mesurés à chaque exécution du job e2e.

*Changement depuis le 21/11* : ecf45f5, 9a0d3e6, e83faa1, 1c9abb9 : e2e/tests/accessibilite-ecrans.spec.ts (3 tests) audite sur la pile réelle la vue d'inventaire, « Mes abonnements » (avec confirmation de suppression) et le traitement des impacts (historique, formulaire de transition) : contrastes, clavier, focus, noms accessibles, 320 px (support/audit-accessibilite.ts) ; 9a0d3e6 corrige des défauts relevés ; arc42 §8.11 l'annonce.

```text
e2e/tests/accessibilite-ecrans.spec.ts:1-49 ; grep -l 'mesures|contraste' e2e/tests/*.ts -> accessibilite-ecrans.spec.ts, veille.spec.ts ; docs/architecture/arc42/08-concepts-transverses.md:313 ; .github/workflows/integration-continue.yml:200-204
```

*Réserve* : Le rapport d'audit docs/architecture/quality/audit-accessibilite-mises-en-page.md reste daté du 2026-11-17 avec les écrans hors périmètre (lignes 13-14) et aucun résultat de ces mesures n'est versionné ; non rejouable ici (pile complète).

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Écrans livrés après le 17/11 toujours sans mesure de contraste.

**Preuve d'origine**

```text
docs/architecture/quality/audit-accessibilite-mises-en-page.md:13 (hors périmètre : écrans autres que les mises en page) et :14 (date 2026-11-17) ; argos-ui/src/views/InventaireView.vue créé par f5fff1f (2026-11-19 : tableaux, pagination, région live) ; fea6eca (2026-11-19, actions suspendre/réactiver/supprimer dans VeilleView.vue) ; traitement des impacts dans AlertesView.vue (automatiser-chaine-veille/tasks.md:65, tâche 3.5) ; les mesures de contraste e2e/support/mesures.ts ne sont utilisées que par e2e/accessibilite/ et e2e/tests/veille.spec.ts (0 occurrence de « mesures » ou « contraste » dans e2e/tests/inventaire.spec.ts et chaine-automatisee.spec.ts), qui vérifient seulement clavier, focus et annonce.
```

**Impact** — Les seuls écrans de formulaire et de tableau de l'application échappent au référentiel RGAA retenu (décision Q-4) ; la conclusion de l'audit (« avant de déclarer la conformité RGAA de l'application ») s'éloigne.

**Action** — Étendre le rapport (ou en ouvrir un second) aux vues Inventaire, Alertes et aux actions de Ma veille, en réutilisant e2e/support/mesures.ts dans leurs parcours.

### AUD-TST-25 — PR #28 : quatre chemins publics ajoutés à la politique d'accès HTTP (/, /index.html, /assets/*, /licences-polices.txt) sans aucun test, ni de l'ouverture ni du maintien du 401 sur l'API

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé et testé.

*Changement depuis le 21/11* : 50867d3 (changement 2026-11-25-rendre-portail-deployable) : CheminsPublicsTest vérifie les quatre chemins publics de l'IHM (200 sans session, ressources de test sous src/test/resources/META-INF/resources), le 401 sur 11 routes protégées dont deux tentatives de traversée (/assets/../api/v1/moi), et qu'aucune route /api/ ou /q/ hors identité et santé ne figure dans la permission publique.

```text
api/comptes/CheminsPublicsTest.java:16-20, :23-29, :41-48, :55-61 ; argos-server/src/test/resources/META-INF/resources/{index.html,assets,licences-polices.txt} ; application.properties:432 ; artefact/ArtefactEmpaqueteIT.java (401 sur le jar)
```

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-server/src/main/resources/application.properties:294 quarkus.http.auth.permission.public.paths=…,/,/index.html,/assets/*,/licences-polices.txt (e7ed7f3) ; git show --stat e7ed7f3 -> aucun fichier de test ; grep -rnE '"/index.html"|"/assets|licences-polices|get\("/"\)' argos-server/src/test/java -> 0 ; api/comptes/AuthentificationTest.java:16 Javadoc « authentification obligatoire, sauf santé et identité du serveur » et :21-27 (401 sur 6 chemins d'API seulement) ; ls argos-server/src/main/resources/META-INF -> absent (l'IHM n'est copiée que dans l'étage Docker, Dockerfile:36) ; e2e/playwright.config.ts:103 (IHM servie par Vite, pas par Quarkus).
```

**Impact** — La PR corrige à la main un défaut découvert en recette (page servie répondant 401, IHM inutilisable) ; aucun test n'empêche son retour, ni n'alerterait si un futur élargissement des chemins publics (motif /assets/* ou nouvelle entrée) exposait une route de données. La politique d'accès est la barrière globale du 401 (voir AUD-TST-05).

**Action** — Ajouter à AuthentificationTest un test paramétré des chemins publics (réponse différente de 401 sans session, avec une ressource statique de test sous src/test/resources/META-INF/resources) et un test qui vérifie qu'aucune route /api/ ni /q/ autre que la santé ne figure dans quarkus.http.auth.permission.public.paths ; mettre à jour la Javadoc ACC-01. Compléter par le smoke test sur l'image (AUD-TST-12).

### AUD-TST-26 — argos-desktop sans vérification de types : check:syntax ne contrôle que la syntaxe ; un tsc --checkJs non strict relève 8 erreurs, dont 2 annotations JSDoc invalides sur les dépendances des canaux IPC

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : tsc --checkJs passe sans erreur et fait partie de npm run check (job desktop de la CI).

*Changement depuis le 21/11* : 8a93c84 (changement 2026-11-24-verifier-types-et-frontieres-modules, tâches 3.1-3.2) : typescript en dépendance de développement, argos-desktop/tsconfig.json (allowJs, checkJs, strict false), script typecheck inclus dans check, les 8 erreurs corrigées.

```text
argos-desktop/package.json « check »: npm run check:syntax && npm run typecheck && npm test ; argos-desktop/tsconfig.json:2-16 ; .github/workflows/integration-continue.yml:112 ; build-tests : npm run check -> code 0
```

*Réserve* : Mode non strict (strict: false) : premier palier de la « strictness progressive » choisie, sans échéance pour le palier suivant.

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-desktop/package.json « check »: npm run check:syntax && npm test (aucun tsconfig ni typecheck) ; scripts/verifier-syntaxe.mjs ; build-tests/argos-desktop : npx tsc --noEmit --allowJs --checkJs --strict false --module nodenext --types node --skipLibCheck src/*.js -> 8 erreurs : src/canaux.js:57 et :60 TS2694 (ReturnType<import('./poste').creerPoste> au lieu de ReturnType<typeof import(…)>), src/connexion.js:149-151 TS2339 (type object trop large), src/main.js:27 TS2339 (champ argos absent du package.json, renseigné seulement à l'emballage).
```

**Impact** — Les annotations JSDoc, seule documentation des contrats entre main, canaux, poste et connexion OIDC, ne sont pas vérifiées et sont déjà fausses par endroits ; une erreur de type sur ces chemins (IPC, CSP) ne serait vue qu'à l'exécution.

**Action** — Ajouter un tsconfig (checkJs, strict progressif) et un script typecheck à argos-desktop, inclus dans « check » ; corriger les 8 erreurs (typeof import, typedef de l'écoute, déclaration du champ argos).

### AUD-TST-32 — ContratClientIhmTest échoue sur le poste du mainteneur : core.autocrlf=true extrait l'instantané openapi.json en CRLF, et .gitattributes ne fixe les fins de ligne que pour *.sh

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 2
- **Nouveau** le 28/11

**Preuve**

```text
mvn -B clean verify sur un worktree de 01fea6d (Windows, JDK 24) : 1 251 tests, 1 échec — ContratClientIhmTest.lInstantaneDeLIhmEstLeContratServi:62. Les documents attendu et obtenu sont identiques octet pour octet une fois décodés (comparaison scriptée du rapport surefire) ; git config core.autocrlf = true sur le poste ; .gitattributes:1 « *.sh text eol=lf » est la seule règle ; ContratClientIhmTest.java:62 compare servi (\n) et Files.readString(INSTANTANE).
```

**Impact** — La suite ne passe pas sur le poste de développement Windows : un développeur prend l'habitude d'ignorer un échec, ou régénère l'instantané en CRLF et casse la CI.

**Action** — Ajouter à .gitattributes « *.json text eol=lf » (ou au moins argos-ui/src/api/openapi.json), ou normaliser \r\n dans la lecture de l'instantané ; renormaliser le dépôt (git add --renormalize).

### AUD-PER-02 — L'enrichissement passe par une file persistante traitée hors transaction et rattrapée, mais la corrélation reste synchrone, signal par signal, sur le fil de l'ingestion

- **Statut** : Partiel · **Sévérité** : Faible (était Élevée le 21/11) · **Axes** : Performance, Qualité · **Effort** : M · **Sprint** : 3
- **Fusionne** : AUD-QUA-05

**Vérification du 28/11 (01fea6d)** — Désaccord partiel avec le suivi : l'enrichissement est réellement découplé et rattrapé, mais la corrélation, qui figure dans le titre du constat, reste synchrone, signal par signal, sur le fil de l'ingestion.

*Changement depuis le 21/11* : 7408b6c : l'observateur AFTER_SUCCESS ne fait plus que mettre le signal en file (enrichissement_attente, V25) ; la tâche vulnintel-enrichissement (30 s, lot de 5) enrichit hors transaction avec délai croissant ; retryMissing est enfin appelé par la tâche vulnintel-rattrapage (6 h, 500 signaux).

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:71-78 (onSignalChanged -> file.demander), :84-102 (aucune transaction pendant source.lookup), :105-107 ; argos-server/src/main/java/io/argos/vulnintel/TraitementEnrichissements.java:79-94 (@Scheduled enrichissement et rattrapage, SKIP) ; argos-server/src/main/java/io/argos/vulnintel/FileEnrichissement.java:79-89 ; argos-server/src/main/resources/db/migration/V25__file_enrichissement.sql:7-14 ; test argos-server/src/test/java/io/argos/chain/EnrichmentTest.java:113 (col04S4EnregistrementSansAttenteDesSourcesSecondaires). Reste : argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:82-88 (@Observes AFTER_SUCCESS SignalChanged -> correlate, même fil que SourceSynchronizer) et :136-139 (analyseSystematique = true : analyse IA complémentaire synchrone si ARGOS_IA_ANALYSE_CORRELATION=true).
```

*Réserve* : La corrélation d'un signal est désormais en nombre fixe d'instructions (COR-13), ce qui réduit l'impact ; mais à l'amorçage OSV chaque avis enregistré ouvre encore sa transaction de corrélation et son verrou consultatif sur le fil de synchronisation. Le débit de la file d'enrichissement fait l'objet d'AUD-PER-31.

*Au 20/11* : Ouvert (Élevée) — L'enrichissement reste synchrone (observateur AFTER_SUCCESS, une requête HTTP par CVE et par source) et retryMissing n'a toujours aucun appelant en production.

*Au 21/11* : Ouvert (Élevée) — Désaccord avec le message de f6b93dd : l'enrichissement n'est « différé » que dans le texte du journal. Rien ne le reprend : retryMissing n'a toujours aucun appelant en production, et l'enrichissement reste synchrone, une requête HTTP par CVE et par source.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:57-60 « void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) { ... QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())); » ; Enricher.java:70-76 (une requête HTTP par CVE et par source : NvdSource « ?cveId= », EpssSource « ?cve= », sans cache ni limite de débit) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:76-78 (corrélation au même événement) ; Enricher.java:88 retryMissing(int) n'a aucun appelant (grep -rn retryMissing main : seule la définition).
[AUD-QUA-05] vulnintel/Enricher.java:57-60 « QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())) » ; :67-83 enrich() boucle sur chaque CVE × chaque EnrichmentSource (NVD, EPSS, KEV, CERT-FR) avec source.lookup(cve) puis save() ; application.properties:28 « argos.sources.http-timeout=PT30S » ; aucun quarkus.transaction-manager.default-transaction-timeout (défaut 60 s). L'observateur est synchrone (TransactionPhase.AFTER_SUCCESS) sur le fil de l'ingestion, comme CorrelationEngine.java:76 -> MoteurAlertes (attente IA, envoi).
```

**Impact** — Chaque avis ingéré ajoute jusqu'à 2 appels HTTP bloquants (timeout PT30S chacun) par CVE, en plus de la corrélation et des alertes : le débit de l'ingestion est borné par la latence de NVD. Sans clé d'API, NVD limite fortement le débit : les refus deviennent des enrichissements manqués, qui ne sont jamais rattrapés, puisque retryMissing n'est appelée nulle part.

**Action** — D'abord planifier retryMissing (une dizaine de lignes, @Scheduled) pour ne plus perdre d'enrichissement ; puis remplacer l'observateur synchrone par une file traitée par lots avec une limite de débit par source (NVD : 5 requêtes / 30 s sans clé) et l'API EPSS par lot.

### AUD-PER-05 — Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Le radar travaille en un nombre fixe de requêtes par espace ; test dédié.

*Changement depuis le 21/11* : 331f1d9 : componentsMatching reçoit l'espace (filtre SQL), calendrier lu une fois par espace (CalendrierSorties.finsDeSupport), dettes insérées en une instruction unnest, échéances de tous les jours de rappel en une lecture.

```text
argos-server/src/main/java/io/argos/impact/RadarProjets.java:171-189 (enregistrerDettes), :197-205 (echeancesTouchant), :276 (componentsMatching(…, Optional.of(espace))), :320-324 (INSERT … FROM unnest) ; argos-server/src/main/java/io/argos/alerting/RappelsRadar.java:168-171 (un appel echeancesTouchant pour tous les jours) ; test argos-server/src/test/java/io/argos/chain/RequetesConstantesTest.java:212 (rad08RadarEnNombreFixeDeRequetes).
```

*Au 20/11* : Ouvert (Faible) — Le radar charge toujours les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant.

*Au 21/11* : Ouvert (Faible) — Le radar charge toujours, pour chaque espace, les composants de tous les espaces avant de filtrer en Java, et il lit le calendrier composant par composant.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/RadarProjets.java:252-253 « return inventaires.componentsMatching(parCle.keySet(), Optional.empty()).stream().filter(m -> espace.equals(m.workspaceId())).toList(); » ; :131 boucle par espace ; :148 calendrier.finDeSupport(...) par composant -> argos-server/src/main/java/io/argos/releaseradar/CalendrierEndoflife.java:119 (referentiel.technologies) + :127 (requête cycle_vie), donc 2 requêtes par composant ; :150 enregistrerDette = 1 connexion + 1 INSERT par dette ; argos-server/src/main/java/io/argos/alerting/RappelsRadar.java:114-117 : espace × jours de rappel (jusqu'à 10) × echeancesTouchant, qui rappelle composants().
```

**Impact** — Le coût est en O(espaces × composants de toute la plateforme correspondant aux clés), auquel s'ajoutent 2 requêtes par composant et jusqu'à 10 rescans par espace pour les rappels. Sur le NAS mono-utilisateur, c'est négligeable. En ligne, avec des espaces nombreux suivant les mêmes technologies (spring, node…), la tâche quotidienne grossit de façon quadratique.

**Action** — Ajouter un paramètre workspaceId à componentsMatching (filtre SQL p.workspace_id = ?) ; charger le calendrier (cycles des produits suivis) une fois par espace, puis évaluer en mémoire ; insérer les dettes par lot ; calculer les échéances de tous les jours de rappel en une passe (intervalle [min, max]).

### AUD-PER-07 — Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La sélection est bornée (1 001 candidats), la pertinence n'est calculée que sur les 1 000 plus récents et le total est plafonné ; un test couvre les deux côtés du plafond.

*Changement depuis le 21/11* : 331f1d9 et 9beac79 : V28 remplace argos_rechercher_veille par argos_selectionner_veille, qui renvoie au plus p_plafond candidats par type, les plus récents ; RechercheVeille ne calcule ts_rank que sur 1 000 candidats et plafonne le total (totalPlafonne).

```text
argos-server/src/main/resources/db/migration/V28__recherche_bornee.sql:14-18, :40-67 (ORDER BY coalesce(published_at, modified_at) DESC LIMIT $13), :101 ; argos-server/src/main/java/io/argos/impact/RechercheVeille.java:78 (PLAFOND = 1000), :127-146, :226-228 ; tests argos-server/src/test/java/io/argos/api/recherche/RechercheBorneeTest.java:74 (rch07S1SelectionPlusGrandeQueLePlafond), :92 (rch07S2) ; mesure docs/architecture/quality/mesure-performance-api-mcp.md (« GET /api/v1/recherche … 46.5 | 58.1 », 1 000 avis).
```

*Réserve* : La mesure QS-13 (docs/architecture/quality/mesure-qs13-recherche.md, 120 000 éléments, p95 3 722 ms pour un mot présent partout) n'a pas été refaite après V28 : le gain n'est pas mesuré sur un gros corpus ; la campagne du 27/11 ne compte que 1 000 avis. Le mot présent partout produit encore un bitmap complet avant le tri des 1 001 plus récents.

*Au 20/11* : Ouvert (Faible) — La sélection reste matérialisée, classée et comptée en entier à chaque page. Les seules mesures à grand volume restent celles de QS-13 (poste de développement).

*Au 21/11* : Ouvert (Faible) — La sélection est toujours matérialisée, classée et comptée en entier à chaque page.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/RechercheVeille.java:136-138 « WITH r AS MATERIALIZED (SELECT * FROM argos_rechercher_veille(...)) SELECT r.type, r.id, r.rang, (SELECT count(*) FROM r) FROM r ORDER BY r.rang DESC, ... LIMIT ? OFFSET ? » ; argos-server/src/main/resources/db/migration/V11__recherche_veille.sql:101-105 (sans mots, la branche signal renvoie tous les signaux, ts_rank sur chaque correspondance) ; docs/architecture/quality/mesure-qs13-recherche.md : « aucun filtre (fil complet) 120 000 … p95 272 ms » et « mot présent dans tous les éléments 120 000 … p95 3 722 ms » (poste de dev, 20 000 signaux).
```

**Impact** — La latence croît avec le nombre de correspondances, pas avec la taille de la page : ts_rank détoaste le vecteur de chaque ligne (toast_tuple_target=256) et le count(*) est exact. La mesure QS-13 a été prise sur un PC de développement avec 20 000 signaux. Sur le Ryzen R1600 du NAS, avec le corpus OSV réel, un mot fréquent ou le fil sans filtre dépassera la cible Q-05 de 500 ms (risque R-16 déjà identifié). OFFSET rend aussi les pages profondes coûteuses.

**Action** — Borner la sélection (par exemple LIMIT 1 000 candidats triés par date avant ts_rank, ou ts_rank seulement si le nombre de correspondances est sous un seuil) ; remplacer le total exact par un total plafonné (« plus de 1 000 ») ; fil sans mots : tri par date avec l'index signal_date/actualite_date et pagination par curseur (keyset) ; refaire la mesure QS-13 sur le NAS avec un corpus OSV réel.

### AUD-PER-09 — Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les index cités par le constat existent et un test vérifie leur présence.

*Changement depuis le 21/11* : 331f1d9 : V27 crée les index des clés étrangères component_id (project_impact, dette_version) et des listes d'exploitation (ingestion_failure, ops_event, sync_attempt, inventory_import_failure), plus actualite(recu_le) et ingestion_failure(run_id).

```text
argos-server/src/main/resources/db/migration/V27__index_requetes_chaudes.sql:12-30 ; test argos-server/src/test/java/io/argos/chain/IndexRequetesChaudesTest.java:22-44.
```

*Réserve* : Les purges de conservation filtrent sur des colonnes d'horodatage non menées par un index (ingestion_failure.received_at, ops_event.occurred_at, inventory_import_failure.received_at, opportunite.recu_le) : nouveau constat AUD-PER-34.

*Au 20/11* : Ouvert (Faible) — Les index manquants le sont toujours ; SET NULL demande la même recherche par component_id que CASCADE.

*Au 21/11* : Ouvert (Faible) — Les index manquants le sont toujours.

**Preuve d'origine**

```text
argos-server/src/main/resources/db/migration/V5__impacts_projet.sql:11 « component_id uuid NOT NULL REFERENCES component (id) ON DELETE CASCADE » (seul index contenant component_id : project_impact_triplet_uk (signal_id, project_id, component_id), en 3e position) ; V12__radar_et_standards.sql:107 (dette_version.component_id, en 2e position dans dette_version_uk) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:72 « DELETE FROM component WHERE project_id = ? AND import_id <> ? » ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:215 et :234 (WHERE source_id = ? ORDER BY ...) sans index sur ingestion_failure(source_id) ni ops_event(source_id) (V3:55-72) ; sync_attempt(run_id) non indexé (V3:43-52) ; InventoryQueries.java:112 sans index sur inventory_import_failure(project_id).
```

**Impact** — Chaque composant retiré d'un inventaire déclenche une recherche en cascade dans project_impact et dette_version : faute d'index adapté, c'est un parcours séquentiel par ligne supprimée. Une réimportation qui retire beaucoup de composants devient alors quadratique quand project_impact grossit. Les listes d'exploitation font des parcours complets, sans gravité tant que les volumes restent faibles.

**Action** — Migration V15 : CREATE INDEX ON project_impact (component_id) ; ON dette_version (component_id) ; ON ingestion_failure (source_id, received_at) ; ON ops_event (source_id, occurred_at) ; ON sync_attempt (run_id) ; ON inventory_import_failure (project_id, received_at).

### AUD-PER-10 — Listes renvoyées en entier sans pagination : listes d'exploitation (sources rejetées, événements, échecs d'import) et impacts d'un projet (GET /projects/{id}/impacts)

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 4
- **Fusionne** : AUD-PER-22
- **Bloque** : AUD-PER-37

**Vérification du 28/11 (01fea6d)** — Les listes citées sont paginées.

*Changement depuis le 21/11* : e7629dd et 496add9 (sprint 9) : format commun page/size/{items,page,size,total} (ADR-0040, Proposé) pour avis mis à l'écart, événements, échecs d'import, impacts d'un projet, composants, opportunités et Ma veille.

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:48-72 (Page<…>, DemandePage) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:245-263 (LIMIT ? OFFSET ?) ; argos-server/src/main/java/io/argos/api/ProjectResource.java:83-97 ; argos-server/src/main/java/io/argos/api/InventoryResource.java:111-171 ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:237-259 ; test argos-server/src/test/java/io/argos/api/veille/VeillePagineeTest.java:101-133.
```

*Réserve* : Restent non bornées des listes qui croissent avec les données importées : GET /api/v1/radar/espaces/{id}/dettes, GET /api/v1/me/watch et ImpactQueries.forProject sans page appelé par trois chemins (MCP get_project_context, get_security_alerts filtré par projet, abonnements PROJECT) : nouveau constat AUD-PER-33. ADR-0040 est encore au statut « Proposé ». Relecture du 28/11 : la preuve d'AUD-PER-22 (fusionné) tient encore, abonnements PROJECT sans borne (SignauxAbonnements.java:60-62) ; voir aussi AUD-PER-33.

*Au 20/11* : Ouvert (Faible) — Les listes d'exploitation restent sans LIMIT. Leur croissance est désormais automatique : synchronisation horaire et import quotidien.

*Au 21/11* : Ouvert (Faible) — Les listes d'exploitation, les échecs d'import et les impacts d'un projet sont toujours renvoyés sans LIMIT.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:40-55 (rejected, events -> List complète) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:213-216 et :234 (aucun LIMIT) ; argos-server/src/main/java/io/argos/api/InventoryResource.java:50-52 -> InventoryQueries.java:112 (aucun LIMIT). À l'inverse, recherche (RechercheVeille, TAILLE_MAX=100), impacts (ImpactQueries.java:82 LIMIT/OFFSET), alertes (ConsultationAlertes.java:58 LIMIT) et actualités (ActualitesWeb.java:83 LIMIT) sont bornées.
[AUD-PER-22] argos-server/src/main/java/io/argos/api/ProjectResource.java:61-73 (renvoie List<ImpactQueries.ImpactView>, paramètre etat seul, ni page ni size) -> argos-server/src/main/java/io/argos/impact/ImpactQueries.java:218-219 (« WHERE i.project_id = ? AND i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.created_at, i.id », sans LIMIT) ; V20__etat_traitement_impacts.sql:54 (« les impacts résolus s'accumulent ») ; même lecture sans borne via un filtre d'abonnement PROJECT : api/SignauxAbonnements.java:95-96 (forProject) puis :66 (signals.find par signal). À l'inverse, GET /api/v1/signals/{id}/impacts est paginé (SignalResource.java:80-88, ImpactQueries.java:132).
```

**Impact** — Ces tables ne sont jamais purgées (voir la purge ci-dessus) : la réponse et la mémoire allouée grossissent à chaque synchronisation ou import en échec. Les endpoints sont réservés à l'exploitation, ce qui limite l'exposition.

**Action** — Ajouter limit/offset (ou un curseur sur l'horodatage), avec un maximum de 100 à 500 éléments, et trier du plus récent au plus ancien.

### AUD-PER-11 — Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les réponses OSV et KEV ne sont plus lues sans plafond.

*Changement depuis le 21/11* : 7408b6c : LectureBornee (commun.http) ; HttpSourceClient et EnrichmentHttp lisent en flux sous argos.sources.taille-max-octets = 64 Mio ; index OSV lu ligne à ligne.

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:63-78 ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:49-63 ; argos-server/src/main/resources/application.properties:76-78 ; test argos-server/src/test/java/io/argos/commun/http/LectureBorneeTest.java.
```

*Réserve* : Les trois index modified_id.csv complets sont toujours téléchargés à chaque synchronisation horaire (OsvAdvisoryFeed.java:97-108), en flux et sous le plafond.

*Au 20/11* : Ouvert (Faible) — Les réponses OSV et KEV sont toujours lues en entier en String, sans plafond, et l'index OSV est maintenant relu à chaque passage horaire.

*Au 21/11* : Ouvert (Faible) — Les réponses OSV et KEV sont toujours lues en entier dans une String, sans plafond, et l'index OSV complet est relu chaque heure.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:59 « client.send(request, HttpResponse.BodyHandlers.ofString()) » (index modified_id.csv complet de chaque écosystème, OsvAdvisoryFeed.java:60, puis csv.split("\\R") et une liste de FeedEntry) ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:43 (idem, catalogue KEV complet). À l'inverse, ForgeHttp (scm), FournisseurOllama (1 Mio), ClientEndoflife et RecuperateurWeb plafonnent la taille lue.
```

**Impact** — Sous un tas d'environ 768 Mo (mem_limit 1g × MaxRAMPercentage 75), l'index npm complet est conservé à la fois en String, en tableau de lignes et en liste d'entrées. Une réponse anormale (proxy, erreur) pourrait faire monter la mémoire jusqu'à l'OOM, alors que les autres clients HTTP sont bornés.

**Action** — Lire en flux (BodyHandlers.ofInputStream + BufferedReader ligne à ligne) avec un plafond configurable, sur le modèle de ForgeHttp.get(..., tailleMax).

### AUD-PER-12 — Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les réglages demandés sont présents et la compression est testée.

*Changement depuis le 21/11* : aa91d2c (sprint 3, 24/11) : JAVA_OPTS -XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError ; quarkus.http.enable-compression=true ; pool max-size 10 et acquisition-timeout PT5S ; postgres max_connections 40, shared_buffers 128MB, work_mem 4MB.

```text
Dockerfile:62 ; argos-server/src/main/resources/application.properties:19-20, :308 ; compose.yaml:49-56, :77, :192, :246 ; test argos-server/src/test/java/io/argos/api/CompressionReponsesTest.java.
```

*Réserve* : La tâche 5.1 du changement actif completer-configuration-conteneurs (openspec/changes/completer-configuration-conteneurs/tasks.md:32, « Démarrer la pile complète … sur le PC puis sur le NAS ») n'est pas cochée : aucun relevé docker stats sur le NAS. Le plafond de 1 Gio ne tient pas compte de l'archive OSV écrite dans le tmpfs /tmp (AUD-PER-30).

*Au 20/11* : Ouvert (Faible) — Les réglages JVM, le pool et Postgres sont toujours aux valeurs par défaut.

*Au 21/11* : Ouvert (Faible) — Les réglages JVM, le pool et Postgres sont toujours aux valeurs par défaut. Depuis #28, l'absence de compression HTTP touche aussi l'IHM, désormais servie par Quarkus.

**Preuve d'origine**

```text
Dockerfile : « ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" » ; compose.yaml : « mem_limit: ${ARGOS_MEM_LIMIT:-1g} » (argos) et « mem_limit: ${ARGOS_DB_MEM_LIMIT:-512m} » (postgres, sans réglage shared_buffers ni work_mem) ; argos-server/src/main/resources/application.properties : aucune clé quarkus.datasource.jdbc.max-size/min-size/acquisition-timeout ni quarkus.http.enable-compression (grep sans résultat).
```

**Impact** — Il reste environ 256 Mio hors tas pour le metaspace, le code cache, les piles de fils, les tampons directs de Netty et du client HTTP JDK. Un dépassement provoque l'arrêt du conteneur (OOM-kill), sans heap dump ni message JVM. Le pool par défaut (20 connexions), combiné aux connexions tenues pendant les appels distants (verrou consultatif de collecte web : StockActualites.java:127-141, connexion dédiée pendant la récupération HTTP), n'est ni dimensionné ni documenté. Sans compression HTTP, le bundle de 111 kB est servi non compressé hors reverse proxy.

**Action** — JAVA_OPTS : MaxRAMPercentage=60 à 65, -XX:+ExitOnOutOfMemoryError, éventuellement -XX:MaxMetaspaceSize ; expliciter quarkus.datasource.jdbc.max-size (ex. 10 sur le NAS) et acquisition-timeout ; activer quarkus.http.enable-compression=true ; régler shared_buffers (~128 Mo) et work_mem pour la limite de 512 Mo de Postgres.

### AUD-PER-13 — Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Plus de lecture d'échéance par projet ; les forges sont collectées en parallèle ; test dédié.

*Changement depuis le 21/11* : 7f27b94 : échéances lues en une requête (prochainesCollectes), une tâche par forge sur ExecuteurCollecteActivite, passage borné par argos.activite.duree-max-passage.

```text
argos-server/src/main/java/io/argos/projectintel/CollecteActivite.java:112-131 ; argos-server/src/main/resources/application.properties:243-245 ; test argos-server/src/test/java/io/argos/projectintel/ActiviteProjetsTest.java:175 (lesForgesSontCollecteesEnParalleleEtLesProjetsNonEchusSautes).
```

*Au 20/11* : Ouvert (Faible) — La collecte d'activité lit toujours l'échéance projet par projet et appelle les forges en séquence.

*Au 21/11* : Ouvert (Faible) — La collecte d'activité lit toujours l'échéance projet par projet et appelle les forges en séquence.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/projectintel/CollecteActivite.java:81-83 « for (ProjectDirectory.Project projet : projets.withRepository()) { ... store.prochaineCollecte(projet.id()); » (1 requête par projet, alors que l'index activite_collecte_prochaine existe : V14:47) ; argos.scm.activite.pages-max=10 et argos.sources.http-timeout=PT30S (application.properties).
```

**Impact** — Chaque passage horaire charge tous les projets à dépôt et interroge la base pour chacun, puis enchaîne jusqu'à 10 pages × 30 s par dépôt. Le chevauchement est évité (SKIP), mais en ligne la durée d'un passage devient proportionnelle au nombre total de projets.

**Action** — Sélectionner en SQL les seuls projets échus (JOIN activite_collecte WHERE prochaine_collecte <= now() ORDER BY prochaine_collecte LIMIT n), borner la durée d'un passage comme CollecteWeb (duree-max-passage), et paralléliser modérément par forge.

### AUD-PER-19 — L'import quotidien réécrit et recorrèle chaque nuit tout l'inventaire de chaque projet à dépôt, même si la SBOM ou les manifestes n'ont pas changé

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Un import nocturne sans changement n'écrit plus et ne recorrèle plus ; tests dédiés.

*Changement depuis le 21/11* : e7629dd : empreinte SHA-256 du document du dernier import par dépôt (V26) ; un document inchangé est conservé (KEPT) sans écriture ni recorrélation.

```text
argos-server/src/main/resources/db/migration/V26__empreinte_dernier_import_depot.sql:4-6 ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:79, :115 ; argos-server/src/main/java/io/argos/inventory/InventoryService.java:152-155 ; tests argos-server/src/test/java/io/argos/chain/ImportPlanifieTest.java:192 (inv14S1DocumentInchangeConserve), :212 (inv14S2), :232 (inv14S3).
```

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Chaque nuit, l'import réécrit et recorrèle encore tout l'inventaire de chaque projet à dépôt, même sans changement de la SBOM ou des manifestes.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/inventory/ImportPlanifie.java:52-56 (cron argos.inventory.import.cron, 03:00) et :77-80 (inventories.importerProjet pour chaque projet) ; argos-server/src/main/java/io/argos/inventory/InventoryService.java:186-188 et :230 (SBOM trouvée -> « IMPORTED » sans comparaison avec l'import précédent) et :206 (manifestes -> « IMPORTED » à chaque lecture) puis :166 store.remplacer ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:226-231 (DELETE de toutes les occurrences de l'emplacement), :236-240 (« ON CONFLICT … DO UPDATE SET purl = excluded.purl, import_id = excluded.import_id », donc une nouvelle version de ligne par composant et par nuit), :320 (« recorrelation_pending = true »), :84 (InventoryReplaced) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:101-126 (correlateProject synchrone de tous les signaux qui affectent l'inventaire, plus ceux qui portent un impact actif). Aucune empreinte de document dans V19 (repository_inventory : V19__depots_projet_et_occurrences.sql:30-43).
```

**Impact** — Chaque nuit, le coût est en O(projets × signaux affectant l'inventaire × composants correspondants), avec le N+1 d'AUD-PER-03 (3 à 5 instructions par composant), même quand rien n'a changé. La table component prend une version morte par ligne et par nuit, component_occurrence est vidée puis réécrite (autovacuum et gonflement de la table sur le NAS). Le commit ceb0f50 empêche les alertes en double, pas le travail. En multi-utilisateurs, la durée du passage croît avec le nombre total de projets, et SKIP fait sauter le passage suivant si celui-ci dépasse 24 h.

**Action** — Calculer une empreinte (SHA-256) du document lu (SBOM ou manifestes concaténés) et la stocker dans repository_inventory ; répondre « KEPT » si elle est identique, sans écrire ni publier InventoryReplaced ; dans l'upsert, n'écrire que si purl a changé (WHERE component.purl IS DISTINCT FROM excluded.purl) ; ne recorréler que les package_key dont la présence ou la version a changé (différence entre l'ancien et le nouvel ensemble).

### AUD-PER-20 — Régression de l'import d'inventaire : les composants sont insérés un par un (un aller-retour SQL par composant), alors qu'ils l'étaient par lot à l'audit initial

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — L'insertion composant par composant a disparu.

*Changement depuis le 21/11* : e7629dd : EcritureComposants écrit composants, PURL modifiés et occurrences en trois instructions unnest, après dédoublonnage.

```text
argos-server/src/main/java/io/argos/inventory/EcritureComposants.java:27-50, :61-73 ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:275 ; test argos-server/src/test/java/io/argos/chain/ImportPlanifieTest.java:247 (inv14S4GrosDocumentDedoublonne).
```

*Réserve* : Aucun test ne compte les instructions de l'import (RequetesConstantesTest couvre corrélation, alertes et radar seulement).

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Les composants sont toujours insérés un par un, avec un aller-retour SQL par composant ; seule l'occurrence passe par lot.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/inventory/InventoryStore.java:235-262 (« INSERT INTO component … ON CONFLICT … RETURNING id » puis « try (ResultSet rs = composant.executeQuery()) » dans la boucle sur r.parsed().components()) ; seule l'occurrence est par lot (:268-270 addBatch/executeBatch) ; à 9d96406 : « git show 9d96406:argos-server/src/main/java/io/argos/inventory/InventoryStore.java » lignes 51-70 (ps.addBatch() puis ps.executeBatch()) ; introduit par le commit 77181e9 (volet B) ; argos.inventory.max-document-bytes=20971520.
```

**Impact** — Une SBOM npm ou Maven de plusieurs milliers de composants donne autant d'allers-retours SQL dans la transaction qui tient le verrou consultatif du projet (InventoryStore.java:167). L'import manuel et l'import planifié du même projet attendent d'autant, et le coût revient chaque nuit (AUD-PER-19). La protection Info d'AUD-PER-17 ne tient plus.

**Action** — Revenir à un INSERT par lot : soit INSERT … SELECT FROM unnest(?::text[], ?::text[], …) ON CONFLICT … RETURNING id, package_key, version, en une requête, soit addBatch puis relecture des identifiants par (project_id, package_key, version) avec « = ANY ».

### AUD-PER-26 — L'index V21 (updated_at DESC, id) ne porte ni l'espace ni l'état : pour un appelant qui a peu d'impacts actifs, « Ma veille » parcourt les impacts de tous les espaces, résolus compris (22,9 ms mesurés pour 40 000 lignes écartées, coût croissant avec la table)

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Un petit espace ne parcourt plus les impacts des autres espaces ; mesuré.

*Changement depuis le 21/11* : 331f1d9 : index partiel project_impact_actifs_recents (workspace_id, updated_at DESC, id) WHERE state IN ('OUVERT','CONFIRME') ; forWorkspaces lit espace par espace (LATERAL … LIMIT).

```text
argos-server/src/main/resources/db/migration/V27__index_requetes_chaudes.sql:44-46 ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:119-128 ; test argos-server/src/test/java/io/argos/chain/IndexRequetesChaudesTest.java:47-54 ; mesure docs/architecture/quality/mesure-performance-api-mcp.md (« GET /api/v1/me/veille (petit espace) | REST | 200 | 56.7 | 67.8 », espace volumineux de 500 000 impacts résolus ou écartés).
```

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-server/src/main/resources/db/migration/V21__index_impacts_recents.sql:12-16 (« La colonne workspace_id n'est pas en tête », CREATE INDEX project_impact_recents ON project_impact (updated_at DESC, id)) ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:244-247 (« WHERE i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.updated_at DESC, i.id LIMIT ? », états actifs OUVERT et CONFIRME : EtatImpact.java:19) ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:54 (« les impacts résolus s'accumulent ») ; mesures versionnées : openspec/changes/archive/2026-11-20-optimiser-ma-veille/design.md:182 (« 40 000 lignes des autres espaces lues et écartées par le filtre sur workspace_id (Rows Removed by Filter: 40000, 38 323 tampons), soit 22,9 ms ») et :184 (ensemble d'espaces non lisibles : 335 ms avec l'index contre 175 ms sans) ; tasks.md, preuve 4.3 (5 espaces de 10 000 impacts, 50 000 au total) ; le parcours s'arrête aux 200 premières lignes retenues, ce qui suppose que l'espace de l'appelant en contienne au moins 200 actives.
```

**Impact** — La mesure de la PR #27 couvre 5 espaces et 50 000 impacts, et un appelant dont l'espace contient 10 000 impacts. Elle ne couvre pas le cas fréquent en multi-utilisateurs : un petit espace (moins de 200 impacts actifs) au milieu d'espaces volumineux. Dans ce cas, le parcours de l'index ne trouve jamais ses 200 lignes et lit toute la table project_impact, impacts RESOLU et ECARTE compris, à chaque affichage de « Ma veille » et à chaque appel MCP get_security_alerts. En extrapolant linéairement les 22,9 ms pour 40 000 lignes (poste 16 cœurs), on obtient environ 0,5 s par million d'impacts, davantage sur le Ryzen R1600 du DS1522+ (estimation, non mesurée). La cible p95 < 500 ms serait alors dépassée à partir d'un volume que plusieurs dizaines d'espaces atteignent.

**Action** — Remplacer V21 par un index partiel sur les seuls états actifs, en tête l'espace : CREATE INDEX … ON project_impact (workspace_id, updated_at DESC, id) WHERE state IN ('OUVERT','CONFIRME'). Lire par espace (UNION ALL ou LATERAL sur unnest(?) avec LIMIT 200 par branche, puis fusion triée) ; la variante LATERAL déjà mesurée l'a été sans index partiel ni filtre d'état. Refaire la mesure de la tâche 4.3 avec un espace appelant de moins de 200 impacts actifs dans une table d'au moins 500 000 lignes, dont une majorité RESOLU, sur le NAS.

### AUD-PER-27 — Pendant l'attente de reprise (jusqu'à PT5M, 5 tentatives), le run de synchronisation passe RETRYABLE et sort de l'exclusion sync_run_one_running : un lancement manuel ou l'échéance horaire le reprend pendant que le premier fil dort, puis les deux fils synchronisent la même source sur le même run

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La faille d'exclusion pendant l'attente est corrigée et testée.

*Changement depuis le 21/11* : 7408b6c : l'attente de reprise garde le traitement RUNNING (next_attempt_at) ; reprendre() vérifie qu'il l'est toujours ; startOrResume refuse un second lancement.

```text
argos-server/src/main/java/io/argos/ingestion/SyncStore.java:91-118, :129-131, :148-153 ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:141-154 ; test argos-server/src/test/java/io/argos/chain/LancementSynchronisationTest.java:108 (col12S3AttenteDeRepriseExclusive).
```

*Réserve* : succeed, fail et advanceCursor n'exigent pas l'état RUNNING et le traitement n'est pas rafraîchi pendant un téléchargement : AUD-PER-32.

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:86-94 (store.markRetryable(runId, …) puis sleeper.sleep(delay) puis store.resume(runId), sans vérifier que le run est toujours à lui) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:123-128 (markRetryable : state = 'RETRYABLE') ; :79-95 (startOrResume : en l'absence de RUNNING, reprend le premier RETRYABLE, « resume(id); return Optional.of(id); ») ; :109-111 (resume : state = 'RUNNING' sans condition sur l'état courant) ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:40 (index unique sync_run_one_running … WHERE state = 'RUNNING' seulement) ; deux déclencheurs indépendants : argos-server/src/main/java/io/argos/signal/SynchronisationPlanifiee.java:47 (horaire ; SKIP ne protège que contre lui-même) et argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-63 (manuel) ; application.properties:37-39 (max-attempts 5, backoff jusqu'à PT5M).
```

**Impact** — Il suffit d'une panne transitoire d'OSV (429 ou 5xx) pendant une synchronisation : le premier fil dort, et un second fil (requête d'exploitation, ou échéance horaire si le premier est le lancement manuel) reprend le même run depuis le même curseur. Deux boucles séquentielles téléchargent alors les mêmes avis, ce qui double les appels à api.osv.dev (et le risque de 429) ainsi que l'enrichissement, la corrélation et les alertes en aval. L'enregistrement reste idempotent sous verrou (CollecteTest#col03S3CollectesConcurrentesDuMemeAvis), mais l'état du run devient incohérent : le premier fil remet RUNNING un run que l'autre a peut-être déjà terminé, et les compteurs et tentatives se mélangent. Non couvert par un test.

**Action** — Garder le run sous l'exclusion pendant l'attente : laisser RUNNING avec next_attempt_at et battement de cœur (et non RETRYABLE), ou bien faire reprendre par resume() seulement si l'état n'a pas changé (« UPDATE … SET state = 'RUNNING' WHERE id = ? AND state = 'RETRYABLE' AND next_attempt_at = ? » et contrôle du nombre de lignes). À terme, sortir l'attente du fil (re-planification, AUD-PER-01). Ajouter un test : un second synchronize() pendant l'attente doit répondre « déjà en cours ».

### AUD-PER-28 — Profil idp-local : Keycloak ajoute un conteneur de 1 Gio et partage le PostgreSQL plafonné à 512 Mio, non réglé (max_connections par défaut). La somme des plafonds atteint 5,5 Gio avec le profil ia (2,5 Gio sans) sur un DS1522+ livré avec 8 Go

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Les réglages et le budget de connexions sont explicites.

*Changement depuis le 21/11* : aa91d2c : Keycloak 768m et KC_DB_POOL_MAX_SIZE=10 ; postgres réglé (max_connections 40 > pools 10 + 10) ; Ollama 2 560m ; total documenté 4,75 Gio.

```text
compose.yaml:49-56, :77, :136, :156, :192, :246, :275 ; openspec/changes/completer-configuration-conteneurs/tasks.md:50-51.
```

*Réserve* : Pas de relevé sur le NAS (tâche 5.1 non cochée, tasks.md:32) ; le plafond d'Argos ignore le tmpfs /tmp (AUD-PER-30).

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
compose.yaml:39-65 (service keycloak, command ["start", "--import-realm"], KC_DB_URL jdbc:postgresql://postgres:5432/keycloak, mem_limit ${ARGOS_KEYCLOAK_MEM_LIMIT:-1g}, sans KC_DB_POOL_MAX_SIZE) ; compose.yaml:24-29 (base Keycloak créée dans le même conteneur postgres par deploiement/postgres/creer-base-keycloak.sh, mem_limit 512m, sans command ni réglage shared_buffers, work_mem ni max_connections) ; compose.yaml:107 (argos 1g) et :124 (ollama 3g) ; argos-server/src/main/resources/application.properties : aucune clé quarkus.datasource.jdbc.max-size (AUD-PER-12) ; introduit par e7ed7f3 (#28).
```

**Impact** — Sur le NAS cible, les plafonds cumulés (0,5 + 1 + 1 + 3 = 5,5 Gio) laissent environ 2,5 Go au DSM et au cache de fichiers, sur 8 Go livrés. Deux bases se partagent un PostgreSQL à 512 Mio, sans réglage. Les pools par défaut (20 connexions côté Quarkus/Agroal, jusqu'à 100 côté Keycloak, valeurs par défaut des produits) peuvent dépasser le max_connections de 100 de l'image PostgreSQL lors d'un pic de connexions OIDC. « start » sans --optimized refait la construction de Keycloak à chaque démarrage, plus lent sur le Ryzen R1600 (non mesuré ; start_period de 40 s et 20 essais de 10 s, compose.yaml:59-65).

**Action** — Documenter un dimensionnement NAS (ex. : ARGOS_OLLAMA_MEM_LIMIT ou IA désactivée par défaut sur 8 Go) ; fixer KC_DB_POOL_MAX_SIZE (ex. 10), quarkus.datasource.jdbc.max-size (ex. 10) et passer à postgres un command: avec max_connections, shared_buffers (~128 Mo) et work_mem adaptés aux 512 Mio ; construire une image Keycloak optimisée (kc.sh build) et démarrer en --optimized.

### AUD-PER-29 — project_impact porte 8 index, dont 2 redondants : project_impact_signal (signal_id) et project_impact_project (project_id) sont couverts par project_impact_signal_etat et project_impact_projet_etat, ajoutés par V20 sans retirer les anciens

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Index redondants supprimés ; testé.

*Changement depuis le 21/11* : 331f1d9 : V27 supprime project_impact_signal et project_impact_project.

```text
argos-server/src/main/resources/db/migration/V27__index_requetes_chaudes.sql:35-36 ; test argos-server/src/test/java/io/argos/chain/IndexRequetesChaudesTest.java:28-29, :42-44.
```

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
argos-server/src/main/resources/db/migration/V5__impacts_projet.sql:22 (CREATE INDEX project_impact_signal ON project_impact (signal_id)) ; argos-server/src/main/resources/db/migration/V11__recherche_veille.sql:49 (CREATE INDEX project_impact_project ON project_impact (project_id)) ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:55-56 (project_impact_projet_etat (project_id, state), project_impact_signal_etat (signal_id, state)) ; triplet_uk (signal_id, project_id, component_id) couvre aussi signal_id en tête (V5:20) ; aucun DROP INDEX dans V20 ni V21 (« grep -n -i 'drop index' argos-server/src/main/resources/db/migration/V*.sql » -> aucun résultat).
```

**Impact** — Chaque écriture de project_impact (création, réécriture nocturne d'AUD-PER-25, transitions) met à jour deux index inutiles, ce qui coûte en écritures, en WAL, en cache et en espace sur le NAS. À l'inverse, la seule clé étrangère vraiment utile, component_id, n'a pas d'index (AUD-PER-09).

**Action** — Migration V22 : DROP INDEX project_impact_signal, project_impact_project (après vérification par pg_stat_user_indexes.idx_scan sur une base de recette) ; CREATE INDEX ON project_impact (component_id) dans la même migration ; inventaire dans arc42 §8.7.

### AUD-PER-32 — Le traitement de synchronisation n'est pas rafraîchi pendant la lecture des index et le téléchargement des archives, et ses écritures finales n'exigent pas l'état RUNNING : au-delà de 30 minutes, un second lancement peut reprendre la même source en parallèle

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 3
- **Nouveau** le 28/11
- **Dépend de** : AUD-PER-30

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:197-203 (feed.lire(position) avant le premier avis : index et archives, sans rafraîchissement) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:60-61 (running-expiry PT30M), :93-96 (startOrResume reclasse RETRYABLE un RUNNING dont heartbeat_at est échu, puis le reprend), :70-75 (advanceCursor : UPDATE … WHERE id = ? sans condition d'état), :164-177 (succeed et fail : WHERE id = ?) ; argos-server/src/main/resources/application.properties:79-82 (la contrainte « son téléchargement doit durer moins que argos.ingestion.running-expiry » n'est qu'un commentaire).
```

**Impact** — Sur un lien lent, le téléchargement d'une archive de plusieurs centaines de Mio, ou des trois index, dépasse 30 minutes. La synchronisation horaire ou un lancement manuel reprend alors le traitement : deux fils lisent et enregistrent la même source, se disputent le curseur, et le premier peut marquer SUCCEEDED un traitement repris par l'autre. Aucun avis n'est perdu (empreinte, upsert), mais le travail est doublé et l'état d'exploitation devient incohérent.

**Action** — Rafraîchir heartbeat_at pendant les téléchargements (rappel de progression de LectureBornee.copier, ou tâche périodique tant que la lecture est ouverte) ; ajouter « AND state = 'RUNNING' » à succeed, fail et à la mise à jour du rafraîchissement dans advanceCursor, et arrêter le fil qui n'est plus propriétaire (comme reprendre()).

### AUD-PER-33 — Listes encore non bornées malgré ADR-0040 : dettes de version d'un espace, GET /api/v1/me/watch, et ImpactQueries.forProject sans page, qui charge tous les impacts actifs d'un projet pour les compter ou en garder 50 en Java (MCP get_project_context, get_security_alerts, abonnements PROJECT)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 4
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/api/RadarResource.java:197-205 -> argos-server/src/main/java/io/argos/impact/RadarProjets.java:253-256 (« … FROM dette_version d … WHERE d.workspace_id = ? ORDER BY … », sans LIMIT) ; argos-server/src/main/java/io/argos/api/WatchResource.java:58-77 (List<WatchItem> sans page) ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:273-285 (forProject sans LIMIT) appelé par argos-server/src/main/java/io/argos/mcp/OutilsProjets.java:101-103 (groupingBy en Java pour compter par niveau), argos-server/src/main/java/io/argos/mcp/OutilsSecurite.java:380-381 (.limit(nombreMax) après lecture complète) et argos-server/src/main/java/io/argos/api/SignauxAbonnements.java:60-62, :116-117 (tous les signaux d'un projet, non bornés par PAR_ABONNEMENT = 100) ; docs/architecture/adr/0040-pagination-des-listes-de-l-api.md (« Aucune liste non bornée renvoyée par l'API » ; « Périmètre : toute liste dont la taille croît avec les données … importées »).
```

**Impact** — Sur le jeu de mesure (1 000 impacts actifs par projet), chaque appel MCP get_project_context relit 1 000 lignes jointes pour produire un histogramme (p95 124,6 ms mesuré, docs/architecture/quality/mesure-performance-api-mcp.md). Les dettes d'un espace et la veille TEAM grossissent avec les inventaires. Un abonnement PROJECT ramène tous les avis du projet dans Ma veille et /me/watch.

**Action** — Remplacer le décompte par un SELECT level, count(*) … GROUP BY ; passer la limite à la requête (LIMIT ?) dans get_security_alerts ; borner les signaux d'un filtre PROJECT par PAR_ABONNEMENT ; paginer /dettes et /me/watch au format ADR-0040, ou documenter qu'ils sont bornés et par quoi.

### AUD-PER-34 — Les purges de conservation parcourent des colonnes d'horodatage non indexées (ingestion_failure.received_at, ops_event.occurred_at, inventory_import_failure.received_at, opportunite.recu_le) : chaque lot de 5 000 relance un parcours de table

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 4
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/conservation/RegleParHorodatage.java:18 (« DELETE FROM %1$s WHERE id IN (SELECT id FROM %1$s WHERE %2$s < ? LIMIT ?) ») ; argos-server/src/main/java/io/argos/conservation/SuppressionParLots.java:40-43 (boucle tant qu'un lot est plein) ; argos-server/src/main/java/io/argos/ingestion/ConservationCollecte.java:70, :79 (ingestion_failure.received_at, ops_event.occurred_at) ; argos-server/src/main/java/io/argos/inventory/ConservationEchecsImport.java:15 ; argos-server/src/main/java/io/argos/signal/opportunite/ConservationOpportunites.java:43-44 (date_limite < ? OR (date_limite IS NULL AND recu_le < ?)) ; index existants : argos-server/src/main/resources/db/migration/V27__index_requetes_chaudes.sql:18-22 (source_id, project_id ou repository_id en tête), argos-server/src/main/resources/db/migration/V15__profil_artiste.sql:95 (date_limite seule) ; à l'inverse refus_acces, mcp_appel, ia_appel (V2:76, V9:20, V13:39) et actualite (V27:29) ont leur index.
```

**Impact** — La première purge, qui rattrape des mois de journaux, et chaque purge quotidienne parcourent ces tables en entier à chaque lot. Les tuples morts des lots précédents restent dans la table jusqu'au VACUUM, ce qui alourdit les parcours suivants. ingestion_failure garde le contenu brut des avis rejetés (raw_content) : c'est la plus lourde.

**Action** — Créer ingestion_failure(received_at), ops_event(occurred_at), inventory_import_failure(received_at) et opportunite(recu_le) WHERE date_limite IS NULL, ou réécrire les purges sur les index existants. Ajouter au test de chaque règle un EXPLAIN qui exclut le parcours séquentiel.

### AUD-PER-35 — La liste fermée de la conservation exclut des tables qui croissent sans limite : toutes les versions d'avis (signal_version), les alertes (alerte) et les impacts résolus ou écartés (project_impact, 500 000 lignes dans le jeu de mesure)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 4
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/test/java/io/argos/architecture/ConservationCompleteTest.java:47 (signal_version : « la version précédente reste consultable », alors que toutes les versions sont gardées), :48 (alerte : « idempotence »), :56-63 (project_impact rangé en données de référence) ; argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:54 (« les impacts résolus s'accumulent ») ; e2e/resultats/performance/rapport-2026-11-27.json (Espace volumineux : RESOLU 450 000, ECARTE 50 000) ; docs/architecture/adr/0041-conservation-des-donnees-journalisees.md (statut « Proposé » ; contexte : « versions d'avis … grossissent sans limite »).
```

**Impact** — Sur le NAS, la base croît avec chaque modification OSV (une version par modification de contenu normalisé), chaque alerte émise et chaque impact résolu. Les index partiels V27 protègent les lectures chaudes, mais pas la taille de la sauvegarde ni la durée des VACUUM et des restaurations.

**Action** — Conserver les N dernières versions d'un avis, ou celles de moins de X mois en plus de la version courante ; purger les alertes envoyées ou annulées au-delà d'une durée qui dépasse la fenêtre de recorrélation ; archiver ou purger les impacts RESOLU par le système depuis plus de X mois. Faire accepter ADR-0041 par le porteur, avec ces exclusions motivées.

### AUD-PER-37 — « Ma veille » tronque en silence au-delà de 200 impacts et annonce un total faux

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 4
- **Nouveau** le 28/11
- **Dépend de** : AUD-PER-10

**Preuve**

```text
Relevé par la relecture contradictoire du 28/11 dans la réserve d'AUD-PER-06 : la lecture groupée borne la sélection à 200 impacts avant le calcul du total renvoyé à l'IHM (SignauxAbonnements / lecture « Ma veille » à 01fea6d).
```

**Impact** — Un utilisateur qui suit beaucoup de projets ne voit pas les impacts au-delà du 200e et croit en avoir moins qu'en réalité.

**Action** — Paginer réellement la réponse (ADR-0040) ou annoncer la troncature dans la réponse et l'IHM ; corriger le total ; ajouter un test au-delà de 200 impacts.

### AUD-DEP-05 — Veille automatisée par Renovate en place ; Electron 45.4.1 (45.5.0 publiée le 24/11), aucun scan CVE des dépendances Maven

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Hors sprint**
- **Fusionne** : AUD-DEP-14

**Vérification du 28/11 (01fea6d)** — Veille automatisée en place et effective ; Playwright à jour ; Electron 45.4.1 alors que 45.5.0 (publiée le 24/11) est la seule mineure supportée ; aucun scan CVE des dépendances Maven.

*Changement depuis le 21/11* : renovate.json ajouté (dd9998b) : managers maven, npm, dockerfile, docker-compose, github-actions et regex (Keycloak, Ollama), helpers:pinGitHubActionDigests, minimumReleaseAge 3 jours ; Renovate actif (10 commits renovate[bot], ex. adeed23 electron 45.4.1, 8177e58 quarkus-mcp-server 2.1.0). Playwright 1.65.1.

```text
renovate.json:4-7, :8-40 ; `git log --format=%an b142695..01fea6d | grep renovate` -> 10 ; argos-desktop/package-lock.json electron 45.4.1 ; `npm view electron dist-tags` latest/45-x-y 45.5.0, time 45.5.0 = 2026-11-24 ; e2e/package-lock.json @playwright/test 1.65.1 = `npm view` ; aucun outil OSV/dependency-check dans pom.xml ni dans les workflows (npm audit seul, integration-continue.yml:93-94,116-117,156-158).
```

*Réserve* : Pas d'alerte de vulnérabilité Maven (seules les montées de version de Renovate) ; e2e/compose.image.yaml:17 (Keycloak) échappe à Renovate (voir AUD-DEP-31) ; activation des alertes de vulnérabilité GitHub invérifiable (API bloquée).

*Au 20/11* : Ouvert (Faible) — Toujours aucune veille automatisée ; le retard d'Electron s'est creusé (45.3.2 alors que 45.3.3, 45.3.4 et 45.4.1 sont publiées).

*Au 21/11* : Ouvert (Faible) — Toujours aucune veille automatisée ; Electron 45.3.2 sur une mineure non supportée (45.4.1 publiée le 19/11), Playwright 1.61.1 (1.65.1 publiée), Keycloak 26.7.4 alors que 26.7.5 et 26.8.1 existent.

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

**Vérification du 28/11 (01fea6d)** — Toujours arbitré par la décision H2 (tags sans digest), désormais complète pour toutes les images.

*Changement depuis le 21/11* : Le tableau de la décision H2 couvre désormais l'image Keycloak (base de l'image construite localement, deploiement/keycloak/Dockerfile) et Ollama 0.40.17 ; Renovate suit les étiquettes (regex Keycloak/Ollama) ; les actions GitHub sont épinglées par empreinte.

```text
Dockerfile:4-5, :8, :23, :51 ; compose.yaml:39, :94-98, :261 ; docs/deploiement/synology-et-pc.md:527-538 [DÉCISION H2] ; renovate.json:8-39.
```

*Réserve* : Retour arrière toujours non couvert : image argos:${ARGOS_VERSION:-local} (compose.yaml:173) écrasée à chaque build.

*Au 20/11* : Décision documentée (Faible) — Non corrigé mais arbitré explicitement dans la doc de déploiement et le Dockerfile.

*Au 21/11* : Décision documentée (Faible) — Toujours arbitré par la décision H2 pour les images de base ; la nouvelle image Keycloak n'est pas couverte par le tableau de la décision.

**Preuve d'origine**

```text
Dockerfile:6 `FROM node:22-slim AS ui`, :16 `FROM maven:3.9-eclipse-temurin-17 AS server`, :25 `FROM eclipse-temurin:17-jre` ; compose.yaml:15 `image: postgres:18`. (Ollama est épinglé : compose.yaml:66 `ollama/ollama:${ARGOS_OLLAMA_VERSION:-0.12.6}`.)
[AUD-TST-07] grep image-name application.properties src/test/resources -> aucun résultat ; seules lignes devservices : application.properties:14 (%dev…enabled=false), 207-211 (realm Keycloak) ; compose.yaml:15 image: postgres:18
```

**Impact** — Build non reproductible : la procédure de mise à jour (doc:80-84, `docker compose up -d --build`) reconstruit avec la base du jour ; une régression d'image amont ou un changement de comportement de PostgreSQL 18.x arrive sans trace ni retour arrière possible vers l'image précédente (image locale `argos:local` écrasée).

**Action** — Épingler chaque FROM et l'image postgres par version précise + digest (`eclipse-temurin:17.0.x_y-jre@sha256:...`), laisser Renovate proposer les mises à jour, et étiqueter l'image argos par version (ARGOS_VERSION) pour permettre le retour arrière.

### AUD-DEP-08 — Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tous les services ont un contrôle de santé et des journaux bornés ; aucune garde automatique n'empêche leur retrait.

*Changement depuis le 21/11* : HEALTHCHECK dans le Dockerfile (/q/health/ready via /dev/tcp), healthcheck ollama (`ollama list`), rotation json-file 10m×3 sur les quatre services par le fragment x-journaux (completer-configuration-conteneurs).

```text
Dockerfile:65-68 ; compose.yaml:19-24 (x-journaux), :78, :157, :247, :276 ; :278-283 (ollama) ; `docker compose config --format json` (copie, .env factice) : logging max-size 10m / max-file 3 sur argos et postgres, healthcheck de argos fourni par l'image ; `grep -c 'logging|healthcheck' scripts/verifier-configuration.mjs` -> 0.
```

*Réserve* : Ni verifier-configuration.mjs ni un test ne contrôlent healthcheck et rotation (contrairement au durcissement) ; validation unhealthy/healthy faite à la main sur PC (tasks.md, EXP-09-S2), NAS non relevé ; le healthcheck Keycloak teste toujours `grep -q UP` (AUD-DEP-28).

*Au 20/11* : Ouvert (Faible) — Toujours aucun healthcheck pour argos/ollama ni rotation des journaux.

*Au 21/11* : Ouvert (Faible) — Inchangé pour argos et ollama ; seul le nouveau service Keycloak est surveillé.

**Preuve d'origine**

```text
compose.yaml:35-59 (service argos : restart, depends_on, mem_limit mais pas de healthcheck), :65-76 (ollama idem) ; Dockerfile:25-32 sans HEALTHCHECK ; aucune clé `logging:` dans compose.yaml (grep).
```

**Impact** — Un Argos bloqué (JVM vivante mais /q/health/ready en échec) reste « running » pour Container Manager ; les journaux json-file croissent sans limite sur le volume du NAS.

**Action** — Ajouter un healthcheck sur /q/health/ready (outil présent dans l'image ou petite commande Java), `logging: {driver: json-file, options: {max-size: 10m, max-file: '3'}}` sur chaque service.

### AUD-DEP-15 — Client desktop distribué sans signature de code ni mise à jour automatique

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : M · **Sprint** : 2

**Vérification du 28/11 (01fea6d)** — Mise à jour automatique réelle ; signature absente mais assumée par ADR-0042 (option A), branchable par deux secrets ; mais le canal de mise à jour livre un client figé sur localhost (AUD-DEP-29) et la publication n'a aucun gate (AUD-DEP-30).

*Changement depuis le 21/11* : Mise à jour automatique par electron-updater (argos-desktop/src/mise-a-jour.js, tests mise-a-jour.test.js), publication GitHub Releases par .github/workflows/publication-desktop.yml avec empreintes SHA-256 et attestation de provenance (36d8d54, 776ab43, b37a5e0) ; absence de signature acceptée par ADR-0042.

```text
argos-desktop/package.json:57-65 (publish github, tagNamePrefix desktop-v), dependencies electron-updater 6.8.12 ; publication-desktop.yml:100-124 ; docs/architecture/adr/0042-distribution-et-mise-a-jour-du-client-desktop.md:69-90 (Décision) ; `npm run check` dans une copie -> 92 tests, 0 échec.
```

*Réserve* : Aucune étiquette desktop-v* dans le clone : première publication et mise à jour réelle d'un poste non prouvées (méthode de validation de l'ADR-0042) ; sans signature, electron-updater ne vérifie que l'empreinte SHA-512 lue dans la même release (intégrité, pas authenticité) ; macOS sans mise à jour. Relecture du 28/11 : installeurs non signés ; l'ADR-0042 (ligne 96) qui accepte ce choix s'appuie sur une garantie fausse (voir AUD-SEC-33).

*Au 20/11* : Ouvert (Faible) — Toujours ni signature de code ni mise à jour automatique.

*Au 21/11* : Ouvert (Faible) — Toujours ni signature de code ni mise à jour automatique ; l'absence de signature est désormais écrite dans la doc, sans décision ni restriction d'usage.

**Preuve d'origine**

```text
argos-desktop/package.json, bloc build : win nsis, mac dmg, linux AppImage sans certificateFile/identity/notarize ni publish ; aucune dépendance electron-updater ; grep -iE 'autoUpdater|electron-updater' argos-desktop/src/*.js -> aucun résultat ; docs/deploiement/synology-et-pc.md:101 construction manuelle `npm run dist`.
```

**Impact** — Pour une diffusion « tous publics » : avertissements SmartScreen/Gatekeeper (macOS refuse un dmg non notarisé), authenticité de l'installeur invérifiable, et les postes restent indéfiniment sur le Chromium embarqué de leur installation (failles navigateur non corrigées).

**Action** — Signer (Authenticode, Apple Developer ID + notarisation) dans la CI, publier via electron-builder `publish` et intégrer electron-updater (vérification de signature) ; à défaut, restreindre officiellement le client desktop à l'usage personnel.

### AUD-DEP-19 — Licences : contrôle automatisé et avis livrés pour le serveur ; IHM livrée (vue, vue-router) sans avis MIT dans l'image ni l'installeur, aucun contrôle npm

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Avis MIT (vue, vue-router) et OFL livrés dans l'image et l'installeur ; contrôle npm automatisé et testé.

*Changement depuis le 21/11* : scripts/verifier-licences-npm.mjs (+ test) : avis THIRD-PARTY-npm.txt avec textes, produit au build de l'image (Dockerfile:17-19, copié en 0444 :57) et à la publication du desktop (ressource licences/) ; contrôle en CI (36d8d54).

```text
Dockerfile:17-19, :57 ; argos-desktop/package.json:15 (licences), :40-43 extraResources licences-tierces → licences ; publication-desktop.yml:95-99 ; integration-continue.yml:91-92, :114-115 ; rejeu : avis de 3076 lignes, 83 dépendances, bloc « === @babel/helper-string-parser 7.29.14 — MIT » avec texte ; tests scripts/verifier-licences-npm.test.mjs:22-67 (6 tests verts) ; docs/architecture/adr/0042…md:86-90.
```

*Au 20/11* : Partiel (Faible) — Le serveur est conforme et contrôlé ; l'IHM livrée (vue, vue-router, MIT) part dans l'image et l'installeur sans avis, et aucun contrôle npm n'est automatisé.

*Au 21/11* : Partiel (Faible) — Serveur contrôlé et avis livrés ; IHM (vue, vue-router, MIT) livrée dans l'image et l'installeur sans avis ; aucun contrôle npm automatisé ; aucun NOTICE dans argos-desktop.

**Preuve d'origine**

```text
ADR-0021:59 « La licence de chaque dépendance est enregistrée au moment de son introduction » : respecté par commentaires dans pom.xml (l.57-150) mais rien pour argos-ui/argos-desktop ; aucun fichier THIRD-PARTY/NOTICE dans le dépôt ; Dockerfile:30 copie seulement target/quarkus-app.
```

**Impact** — Les obligations d'attribution (MIT/Apache/BSD) ne sont pas matérialisées dans l'image distribuée ou l'installeur ; une dépendance transitive à licence incompatible pourrait entrer sans être vue.

**Action** — Générer en CI un rapport de licences (license-maven-plugin / cyclonedx, `npx license-checker --production`) avec liste d'autorisation conforme à ADR-0021, et l'embarquer dans l'image (THIRD-PARTY-NOTICES).

### AUD-DEP-20 — Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée

- **Statut** : Fermé avec réserve · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Dépendances, Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Garde atteignable sur chaque pull request et push sur main ; renommage détecté (D+A).

*Changement depuis le 21/11* : Job « changements » de la CI : garde des migrations (git diff --no-renames --name-status, échec sur tout statut autre que A) (dd9998b).

```text
.github/workflows/integration-continue.yml:216-249 (déclencheurs :6-9, base :225-237) ; MIGRATIONS = argos-server/src/main/resources/db/migration (chemin réel).
```

*Réserve* : Efficace seulement si le job est requis par la protection de branche (invérifiable, API GitHub bloquée) ; logique en shell inline sans test ; repli sur HEAD~1 qui ne contrôle que le dernier commit d'un push forcé.

*Au 20/11* : Partiel (Faible) — Règle documentée et respectée dans le delta, mais rien ne la fait respecter mécaniquement (pas de CI ni de hook).

*Au 21/11* : Partiel (Faible) — Règle ADR-0032 respectée dans le delta, mais toujours ni CI ni hook ni test d'empreinte pour la faire respecter.

**Preuve d'origine**

```text
git log -- db/migration/V13__passerelle_ia.sql -> 4549bd7 (2026-11-16, création) puis 230f395 (2026-11-17, ajout de 'QUOTA_COMPTE' au CHECK de ia_appel.issue, l.30) ; git ls-tree -r HEAD^1 (c4a35bd, main avant la PR #7) : V13 absente, donc jamais en production. application.properties:14-17 : %dev utilise la base persistante de compose (devservices désactivé) ; :23 migrate-at-start=true. Aucune CI, aucun test ni hook ne refuse la modification d'un V*__*.sql existant.
```

**Impact** — Toute base de développement migrée entre les deux commits (base compose du poste) refuse de démarrer : somme de contrôle Flyway différente à la validation. Même règle en production : une fois le NAS en service, la réécriture d'une migration déjà fusionnée empêche Argos de démarrer, ou, après un « flyway repair », laisse un schéma divergent (ici, le CHECK sans QUOTA_COMPTE ferait échouer l'écriture du journal IA).

**Action** — Règle écrite (ADR ou CONTRIBUTING) : une migration fusionnée est immuable et toute correction passe par V(n+1). Contrôle en CI : échec si `git diff --name-status origin/main...HEAD -- argos-server/src/main/resources/db/migration` contient autre chose que des ajouts (A). Option : quarkus.flyway.validate-on-migrate explicite et test qui migre une base depuis le schéma de main.

### AUD-DEP-22 — Le script de contrôle des licences (440 lignes, refactorisé deux fois dans le delta) n'a aucun test automatisé et ne s'exécute qu'au build de l'image, hors de « mvn verify »

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Script testé et exécuté en CI.

*Changement depuis le 21/11* : Test scripts/verifier-licences-serveur.test.mjs (GPL refusée, licence absente, BOM absent, inventaire conforme), exécuté par la CI (node --test scripts/*.test.mjs) ; contrôle Maven en CI quand pom.xml ou le script changent, et à chaque build d'image (job e2e).

```text
scripts/verifier-licences-serveur.test.mjs:44-62 ; integration-continue.yml:130-131 (tests), :250-276 (job licences conditionnel), :177 (docker build → Dockerfile:39 `mvn -B -P licences validate`) ; rejeu `node --test scripts/*.test.mjs` (copie avec git init) -> 75 tests, 0 échec.
```

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Toujours aucun test automatisé du script de licences, toujours hors de « mvn verify » et sans CI.

**Preuve d'origine**

```text
argos-server/pom.xml:211-219 (profil « licences » hors du cycle mvn verify, makeBom « requiresOnline ») ; Dockerfile:34 seul appel ; scripts/verifier-licences-serveur.mjs (404 lignes) : `grep -rl verifier-licences-serveur` hors du script lui-même -> aucun test ; docs/deploiement/synology-et-pc.md:185 vérification manuelle du 19/11 (inventaire factice). .github/ absent.
```

**Impact** — Une dépendance à licence non admise introduite dans une branche n'est vue qu'au prochain `docker compose up --build` sur le NAS, où le build échoue alors en production plutôt qu'en revue ; une régression du script (expressions de ADMISES, lecture zip) n'est détectée par aucun test.

**Action** — Ajouter des tests node:test du script sur des inventaires factices (cas rejoués dans cet audit : GPL, licence absente, BOM absent, conforme), et exécuter `mvn -P licences validate` dans la CI à créer (AUD-TST-01) sur chaque PR qui touche pom.xml.

### AUD-DEP-27 — Profil idp-local sans garde sur ses variables obligatoires : mots de passe initial et d'administration Keycloak vides par défaut et seulement commentés dans .env.example, 3 variables Keycloak de compose.yaml absentes de .env.example, import du royaume exécuté une seule fois

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : variables obligatoires gardées, toutes les interpolations de compose.yaml décrites dans .env.example.

*Changement depuis le 21/11* : Garde dans le point d'entrée du service keycloak (refus avant import si ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL ou ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE manque) ; .env.example complet ; parité compose.yaml ↔ .env.example contrôlée ; import unique documenté.

```text
compose.yaml:106-126 ; .env.example:60-61 ; scripts/verifier-configuration.test.mjs:70 (variable interpolée absente du modèle) ; node scripts/verifier-configuration.mjs -> rc=0 ; docs/deploiement/synology-et-pc.md:55.
```

*Réserve* : Garde du point d'entrée vérifiée à la main seulement (PC, EXP-08-S1), sans test automatisé.

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
compose.yaml:58 `KC_BOOTSTRAP_ADMIN_PASSWORD: ${ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE:-}`, :59-60 ARGOS_ADMIN_EMAIL et ARGOS_ADMIN_MOT_DE_PASSE_INITIAL `:-` vides ; .env.example:25-27 ces variables commentées ; comparaison par script (variables ${…} interpolées par compose.yaml vs .env.example) : ARGOS_KEYCLOAK_VERSION, ARGOS_KEYCLOAK_ADMIN, ARGOS_KEYCLOAK_MEM_LIMIT absentes (avec ARGOS_VERSION, ARGOS_OLLAMA_URL, ARGOS_OLLAMA_KEEP_ALIVE déjà absentes) ; deploiement/keycloak/argos-realm.json:95-104 ; Context7 /keycloak/keycloak (importExport) : « If a realm already exists in the server, the import operation is skipped ».
```

**Impact** — Un premier `docker compose --profile idp-local up` lancé sans ces valeurs importe un royaume dont l'unique compte a un nom ou un mot de passe vide (comportement exact non vérifié ici, registre quay.io inaccessible), et l'import n'est jamais rejoué : il faut supprimer le royaume ou le volume pour corriger. Pas de message clair à l'exploitant.

**Action** — Utiliser `${VAR:?message}` pour ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL et ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE dans le service keycloak (l'erreur ne survient que si le profil est actif) ; compléter .env.example ; étendre le test de parité demandé par AUD-DEP-01 aux variables de compose.yaml.

### AUD-DEP-31 — La version de Keycloak de l'e2e (e2e/compose.image.yaml) échappe à Renovate : la CI teste 26.7.4 en dur pendant que compose.yaml et .env.example suivront les montées de version

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 2
- **Nouveau** le 28/11

**Preuve**

```text
e2e/compose.image.yaml:17 `image: quay.io/keycloak/keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}` ; renovate.json:12 et :22 managerFilePatterns limités à `(^|/)compose\.yaml$` et `^\.env\.example$` ; le manager docker-compose ignore une étiquette contenant une variable.
```

**Impact** — Après une pull request Renovate sur Keycloak, la campagne e2e continue de valider l'ancienne version : la régression d'une montée de Keycloak (connexion OIDC, import) n'est pas détectée par la CI.

**Action** — Ajouter `/(^|/)compose\.image\.yaml$/` aux managerFilePatterns de la règle Keycloak de renovate.json, ou faire lire la version par l'e2e depuis une source unique (.env.example).

### AUD-DEP-32 — Le Dockerfile installe les dépendances de l'IHM avec les scripts d'installation des paquets (npm ci sans --ignore-scripts), alors que toute la CI et la publication les désactivent

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 1
- **Nouveau** le 28/11

**Preuve**

```text
Dockerfile:11 `RUN npm ci --no-audit --no-fund` ; .github/workflows/integration-continue.yml:24 « Les installations npm se font avec --ignore-scripts », :86, :111, :180-182 ; publication-desktop.yml:92-93 ; rejeu `npm ci --ignore-scripts && npm run build` dans une copie d'argos-ui -> rc=0 (les scripts ne sont pas nécessaires au build).
```

**Impact** — L'image livrée (seul artefact construit sur le NAS) exécute les scripts postinstall de toute la chaîne de build de l'IHM : surface de compromission de la chaîne d'approvisionnement que la CI a explicitement fermée ailleurs.

**Action** — Remplacer par `npm ci --ignore-scripts --no-audit --no-fund` (rejoué ici sans effet sur le build) et l'aligner sur le commentaire de la CI.

### AUD-DEP-33 — Les deux changements OpenSpec « actifs » sont déjà fusionnés dans le code (toutes les tâches cochées sauf la pile sur le NAS) mais la fiche exploitation-plateforme garde EXP-07 à EXP-12 et EXP-16 en [PROPOSITION]

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 5
- **Nouveau** le 28/11

**Preuve**

```text
openspec/changes/completer-configuration-conteneurs/tasks.md : 1.1-4.2 et 5.2 cochées, 5.1 (pile complète PC puis NAS) non cochée ; openspec/changes/durcir-conteneurs-de-service/tasks.md : tout coché sauf 5.2 (NAS) ; code présent à 01fea6d : compose.yaml:19-34 (x-journaux, x-durcissement), :106-126 (garde idp-local), :183-241 (variables), Dockerfile:62-68 (ExitOnOutOfMemoryError, HEALTHCHECK), deploiement/keycloak/Dockerfile, scripts/verifier-configuration.mjs ; docs/specifications/exploitation-plateforme.md:70-72 « [PROPOSITION — `completer-configuration-conteneurs`] ».
```

**Impact** — L'état documenté contredit le code livré : un relecteur croit la parité, les contrôles de santé et le durcissement encore à faire ; les preuves NAS manquantes (seules réserves réelles) sont noyées dans un statut global.

**Action** — Basculer EXP-07 à EXP-12 et EXP-16 en [EXISTANT] avec la réserve « non vérifié sur le NAS » (recette R-8), puis archiver les deux changements après fiabiliser-sauvegarde-et-roles-base comme prévu par leurs « Workflow follow-up ».

## Info (54)

### AUD-ARC-01 — Graphe inter-modules acyclique (45 arêtes métier, 46 vers les cinq modules transverses) et conforme à la table ModuleBoundariesTest

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Le code respecte la table : graphe acyclique, aucune arête hors table sauf vers les modules transverses, que la table admet explicitement. La concordance entre package-info et table, qui faisait partie de cette protection, ne tient plus (AUD-ARC-25).

*Changement depuis le 21/11* : Cinq paquets de premier niveau créés (commun, execution, horloge, observabilite, conservation), tous dans ALLOWED et dans TRANSVERSES (ModuleBoundariesTest.java:42-80, :82) ; six arêtes métier disparues (signal/regintel/projectintel/impact/inventory -> workspace, mcp -> api), aucune arête métier ajoutée ; 46 arêtes nouvelles, toutes vers les transverses (commits 95473df, fa11d93, 287c38f, 7f27b94).

```text
python3 -I /home/claude/audit-maj3/scripts-arch/graphe.py argos-server/src/main/java/io/argos -> 91 arêtes (45 entre modules métier, 46 vers des transverses), « SCC [] » ; compare.py (copie /home/claude/audit-maj3/scripts-arch/) -> « modules ALLOWED: 21 » ; les arêtes absentes de ALLOWED vont toutes vers TRANSVERSES (filtre ModuleBoundariesTest.java:143) ; violations réelles : aucune. Détail : /home/claude/audit-maj3/ARCH-graphe.json.
```

*Réserve* : Contrôle textuel ; ModuleBoundariesTest n'a pas été exécuté ici (Maven Central bloqué). Neuf package-info décrivent encore l'ancienne table et quatre transverses n'en ont pas : nouveau constat AUD-ARC-25.

*Au 20/11* : Info (Info) — La protection tient : graphe toujours acyclique et intégralement conforme à ALLOWED ; les 16 package-info concordent avec la table.

*Au 21/11* : Info (Info) — La protection tient : graphe acyclique, intégralement conforme à ALLOWED, et les package-info concordent toujours avec la table.

**Preuve d'origine**

```text
python3 -I /home/claude/audit-tmp/arch/graphe.py argos-server/src/main/java/io/argos -> 44 arêtes, « SCC [] » (aucun cycle) ; compare.py -> « modules ALLOWED: 16 », « hors table: [] ». La seule référence impact->workspace est une javadoc (impact/RlsSession.java:14). Les dépendances déclarées dans les 16 package-info.java sont identiques à ALLOWED (ModuleBoundariesTest.java:30-56).
```

**Impact** — Les frontières déclarées sont respectées par le code ; les sources de vérité code (ALLOWED, package-info, arc42 §5.6) concordent.

**Action** — Aucune ; conserver la règle « table d'abord, puis arc42 » (javadoc du test).

### AUD-ARC-08 — Le module api reste une couche d'exposition mince, sans accès SQL

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient : api ne contient toujours aucun accès JDBC ou SQL.

*Changement depuis le 21/11* : Aucun accès SQL ajouté dans api.

```text
grep -rnE "DataSource|prepareStatement|createStatement|EntityManager|SELECT .*FROM|javax.sql|java.sql|Jdbc\b" argos-server/src/main/java/io/argos/api -> aucun résultat ; plus grosses ressources : api/reglementaire/ReglementaireResource.java (400 l.), api/artiste/ProfilArtisteResource.java (389 l.).
```

*Réserve* : Toujours aucune règle ArchUnit « api ne dépend ni de java.sql ni de io.argos.commun.Jdbc ».

*Au 20/11* : Info (Info) — La protection tient : api ne contient toujours aucun accès JDBC/SQL ; les nouvelles ressources délèguent aux modules.

*Au 21/11* : Info (Info) — La protection tient : api ne contient toujours aucun accès JDBC/SQL.

**Preuve d'origine**

```text
grep -rn "DataSource|prepareStatement|createStatement|EntityManager|SELECT " api -> aucun résultat (règle arc42 §5.5-4 respectée). Plus gros contrôleur : api/RadarResource.java (245 lignes) dont la seule orchestration est le calcul d'horizon et la jointure des libellés (RadarResource.java:171-196). Contrôle d'accès en tête de chaque méthode (membre/administrateur, RadarResource.java:230-244).
```

**Impact** — Les règles métier restent dans les modules ; l'API REST et le serveur MCP peuvent partager les mêmes services.

**Action** — Aucune ; optionnellement une règle ArchUnit « api ne dépend pas de javax.sql » pour figer §5.5-4, et déplacer le calcul d'horizon de RadarResource dans releaseradar.

### AUD-ARC-13 — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient : la couche mise en page n'importe que vue, les polices et ses fichiers (plus MentionsObligatoires) ; le processus principal du desktop n'appelle toujours pas l'API.

*Changement depuis le 21/11* : Aucun changement de couche : nouvelles vues (SignalView, RadarView, ProfilArtisteView) dans views ; nouveau mise-a-jour.js côté desktop.

```text
grep -rh '^import' argos-ui/src/mises-en-page | sort -u -> vue, @fontsource/*, fichiers locaux, ../../contenu/MentionsObligatoires.vue ; argos-ui/tests/architecture.test.mjs présent ; grep -n "fetch\|/api/v1" argos-desktop/src/*.js -> seulement main.js:74 net.fetch d'un fichier local.
```

*Réserve* : La mise à jour automatique (argos-desktop/src/mise-a-jour.js, ADR-0042) est un accès réseau du processus principal, vers GitHub Releases et non vers l'API : hors du périmètre de la règle.

*Au 20/11* : Info (Info) — La protection tient : la couche mise en page n'importe que vue, les polices et ses propres fichiers ; le processus principal desktop n'appelle pas l'API.

*Au 21/11* : Info (Info) — La protection tient : la couche mise en page n'importe que vue, les polices et ses propres fichiers (plus MentionsObligatoires) ; le processus principal desktop n'appelle toujours pas l'API.

**Preuve d'origine**

```text
grep import argos-ui/src/mises-en-page -> uniquement ../../contenu/MentionsObligatoires.vue et le type contenu/modeles ; grep -rln api/client argos-ui/src -> views/AffichageView.vue, views/AlertesView.vue, views/StatusView.vue, contenu/suivi-poste.ts ; test argos-ui/tests/architecture.test.mjs. argos-desktop/src/main.js:72 : seul net.fetch, vers un fichier local (pas d'appel API), conforme à 05-vue-blocs.md:44.
```

**Impact** — La règle « une mise en page n'appelle pas l'API » (arc42 §5.4.1) est tenue et testée.

**Action** — Aucune.

### AUD-ARC-18 — Les quatre nouveaux sous-paquets et les nouvelles arêtes du lot réglementaire sont couverts par ModuleBoundariesTest, déclarés « table d'abord »

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient et s'étend : tout paquet est sous un module de la table, et un paquet non déclaré fait échouer le test. En revanche, les package-info ne suivent plus la table (AUD-ARC-25).

*Changement depuis le 21/11* : Les sous-paquets ajoutés depuis b142695 (ex. commun.http, commun.securite, ingestion.osv) sont sous des modules déclarés ; la règle chaqueClasseResideDansUnModuleDeclare couvre désormais aussi tout nouveau paquet de premier niveau.

```text
find argos-server/src/main/java/io/argos -maxdepth 1 -type d -> 21 paquets, tous clés de ALLOWED (ModuleBoundariesTest.java:42-80) ; règle :94-100, :126, test d'exemple :132-134.
```

*Réserve* : La règle « table d'abord, puis arc42 » est suivie pour arc42 §5.6 (05-vue-blocs.md:270-288 identique à ALLOWED), pas pour les package-info.

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — La protection tient : tous les sous-paquets restent sous un module de premier niveau présent dans ALLOWED et les package-info concordent avec la table.

**Preuve d'origine**

```text
Nouveaux paquets à 54add78 (git ls-tree, comparaison avec 9d96406) : api/reglementaire, api/veille, catalog/dictionnaire, ingestion/legi, tous sous un module de premier niveau présent dans ALLOWED, donc soumis à chaqueModuleRespecteSesDependances (ModuleBoundariesTest.java:72-85) ; catalog.dictionnaire explicitement ajouté à l'exception catalog (:174) ; ingestion (Set.of(), :57) garantit que ingestion.legi reste sans dépendance métier ; regintel -> ingestion/scm/workspace et alerting -> regintel ajoutés à la table (:42-49, entrées :44 et :49), aux package-info (regintel/package-info.java:8-10) et à arc42 §5.4.3 dans le même commit 3f2fdc7 que le code ; aucun paquet de premier niveau nouveau.
```

**Impact** — La discipline « table, puis arc42, puis code » tient sur le plus gros lot du delta ; le graphe reste acyclique.

**Action** — Aucune ; le trou résiduel est la règle « tout paquet de premier niveau doit figurer dans ALLOWED » (AUD-ARC-07).

### AUD-ARC-24 — L'ouverture de l'IHM servie se limite à quatre chemins statiques ; l'API et /q/openapi restent protégés

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient et est testée : seuls les ressources statiques, /api/v1/system/info et la santé sont publics ; toute route d'API ou technique ajoutée aux chemins publics fait échouer le test.

*Changement depuis le 21/11* : Chemins publics inchangés (application.properties:432) ; la protection est désormais testée (CheminsPublicsTest, commit 50867d3).

```text
argos-server/src/main/resources/application.properties:432-439 ; argos-server/src/test/java/io/argos/api/comptes/CheminsPublicsTest.java:36-41, :55-61 (dont /q/openapi, /q/metrics, /assets/../api/v1/moi -> 401), :69-80.
```

*Au 21/11* : Info (Info) — —

**Preuve d'origine**

```text
application.properties:294 chemins publics « /api/v1/system/info,/q/health,/q/health/*,/,/index.html,/assets/*,/licences-polices.txt » ; aucun point d'entrée REST sous / ou /assets (grep -rhoE '@Path("/api/v1/[^"]*")' : tout sous /api/v1) ; AuthentificationTest.java:21-27 vérifie 401 sur /api/v1/moi, /api/v1/espaces, /api/v1/invitations, /api/v1/veille, /q/openapi.
```

**Impact** — La correction d'AUD-ARC-02 n'élargit pas la surface : seules des ressources statiques sans donnée deviennent publiques.

**Action** — Aucune ; ajouter au test d'authentification un cas « GET / et /assets/* sans jeton → 200 » pour figer le comportement.

### AUD-ARC-26 — Le sprint 5 a ajouté cinq garde-fous d'architecture testés : protocole RLS unique, propriété des tables, styles d'événements, fabrique HTTP unique et contrat client IHM

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/test/java/io/argos/architecture/NoyauCommunSourceTest.java:47-51 ; ProprieteTablesTest.java:116-150 (lectures, tables sans propriétaire, propriétaires déclarés) ; ObservateursEvenementsTest.java (liste fermée de 2 synchrones inter-modules) ; ClientsHttpCommunsTest.java ; ModuleBoundariesTest.java:113 (noyau sans dépendance) et :126 (module déclaré) ; argos-server/src/test/java/io/argos/api/ContratClientIhmTest.java + argos-ui/tests/contrat-api.test.mjs ; argos-ui/tests/liens-alertes.test.mjs. Chaque règle a un test d'exemple qui prouve qu'elle détecte une violation (classes de io.argos.architecture.exemples ou @TempDir). Les tests sans Docker tournent dans le profil sans-docker (tests-docker.txt ne les liste pas) ; la CI exécute « mvn -B -f argos-server/pom.xml -P integration-continue clean verify ».
```

**Impact** — Les corrections d'architecture des sprints 4 à 7 ont chacune un test qui échoue en cas de régression ; leur résultat n'a pas pu être observé ici (Maven bloqué, API GitHub bloquée).

**Action** — Aucune ; compléter ProprieteTablesTest par la règle d'écriture (AUD-ARC-06) et ajouter le contrôle des package-info (AUD-ARC-25).

### AUD-QUA-14 — La gestion d'erreurs côté serveur est saine : aucun catch vide, aucun printStackTrace, interruptions rétablies, messages exposés assainis

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient : aucun catch vide, aucun printStackTrace, aucune exception nouvellement avalée sur le delta ; la régression signalée le 21/11 (isolation limitée à IllegalStateException) est corrigée pour les frontières inventoriées.

*Changement depuis le 21/11* : La règle des frontières d'erreur est écrite (arc42 §8.5, « isoler puis relancer », commit 46a293b) avec un inventaire des frontières et leurs tests ; ErreursImprevues factorise la relance.

```text
grep -rnE "printStackTrace|System\.(out|err)" argos-server/src/main/java -> 0 ; grep -rnE "\b(TODO|FIXME|XXX|HACK)\b" argos-server/src argos-ui/src argos-desktop/src scripts e2e -> 0 ; 10 catch (InterruptedException) : 9 rétablissent l'indicateur, ingestion/web/TransportEpingle.java:194-196 relance l'exception ; script /home/claude/audit-maj3/build-QUAL/catches.py (b142695 -> 01fea6d) : relances sans cause 15 -> 15 (mêmes sites), variables de capture inutilisées 28 -> 23, captures sans journal ni retour 9 -> 7, aucune nouvelle ; 17 catch (RuntimeException) tous à une frontière avec LOG.errorf(e, …) puis retenue/relance (ex. inventory/ImportPlanifie.java:92-95, signal/SynchronisationPlanifiee.java:94) ; 51 loggers ; docs/architecture/arc42/08-concepts-transverses.md:175-204.
```

*Réserve* : Trois points nouveaux : six frontières restent explicitement « à aligner » (08-concepts-transverses.md:204, voir AUD-QUA-25) ; la distinction « panne d'accès aux données » repose sur IllegalStateException et non sur ErreurBase (AUD-QUA-26) ; la file d'enrichissement ne reporte pas un signal en erreur (AUD-QUA-24).

*Au 20/11* : Info (Info) — Les règles de gestion d'erreurs et de journalisation tiennent, y compris dans regintel, inventaire et l'IHM.

*Au 21/11* : Info (Info) — Régression partielle de la protection : aucun catch vide ni printStackTrace, interruptions rétablies, causes mieux journalisées, aucune relance sans cause nouvelle ; mais l'isolation aux frontières ne couvre plus que IllegalStateException — toute autre RuntimeException traverse désormais les boucles par source, par projet, par espace et la garde MCP (voir AUD-QUA-20 et AUD-QUA-20).

**Preuve d'origine**

```text
grep -rnE "printStackTrace|System\.(out|err)" argos-server/src/main/java -> 0 ; grep -rnE "TODO|FIXME|XXX|HACK" sur argos-server, argos-ui, argos-desktop, scripts -> 0 ; 36 catch (Runtime)Exception, tous placés aux frontières (observateurs, tâches planifiées, boucles par espace/compte) avec LOG.errorf(e, ...) et identifiant, ex. CorrelationEngine.java:79-80, RappelsRadar.java:121-122 ; InterruptedException traitée avec Thread.currentThread().interrupt() en 8 endroits (PasserelleIa.java:129-131, MoteurAlertes.java:264-267, SourceSynchronizer.java:78-79...) ; messages persistés ou renvoyés réduits au nom de classe (SourceSynchronizer.java:86-87, CanalEmail.java:35, EnvoiAlertes.java:146) ; GestionErreurs.java ne mappe que des exceptions de domaine. Journaux : 24 loggers, seuls des identifiants sont journalisés (aucun e-mail, jeton ni prompt trouvé par grep).
```

**Impact** — Robustesse correcte des traitements de fond : une erreur sur un élément n'interrompt pas les autres et laisse une trace exploitable ; pas de fuite de détails techniques vers le client.

**Action** — Conserver ces règles ; les inscrire dans arc42 §8 (gestion des erreurs et journalisation) pour qu'elles survivent aux contributions futures.

### AUD-QUA-15 — Les ressources JDBC sont systématiquement fermées (try-with-resources), y compris le cas particulier du verrou consultatif

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Fermeture systématique des ressources JDBC confirmée.

*Changement depuis le 21/11* : Aucun changement de principe ; l'utilitaire Jdbc ouvre et ferme ses connexions en try-with-resources.

```text
grep -rn "getConnection()" argos-server/src/main/java | wc -l -> 104, dont 81 « requete.getConnection() » (connexion de la requête, non possédée, pour createArrayOf) ; hors try-with-resources, seul signal/web/StockActualites.java:131 (transfert explicite au Verrou AutoCloseable, inchangé) ; commun/Jdbc.java:77, :100, :120 et commun/ContexteBase.java:61 en try-with-resources.
```

*Au 20/11* : Info (Info) — Fermeture systématique des ressources JDBC confirmée à 54add78.

*Au 21/11* : Info (Info) — Fermeture systématique des ressources JDBC confirmée à b142695.

**Preuve d'origine**

```text
grep -rn "getConnection()" argos-server/src/main/java -> 93 appels, 92 dans un try-with-resources ; le seul autre, signal/web/StockActualites.java:127-156, ferme la connexion dans un finally sauf transfert explicite de propriété à Verrou (AutoCloseable, :57-77) qui libère le verrou et rend la connexion.
```

**Impact** — Pas de fuite de connexions du pool identifiée par lecture.

**Action** — Aucune action ; conserver le motif et le reprendre dans l'utilitaire JDBC proposé par AUD-QUA-06.

### AUD-QUA-16 — Typage TypeScript strict tenu dans argos-ui (vue-tsc sans erreur, un seul contournement)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Typage strict tenu dans argos-ui : vue-tsc sans erreur, toujours un seul contournement réel.

*Changement depuis le 21/11* : Le typage strict s'étend à e2e/ (tsconfig strict) ; argos-desktop vérifié par tsc en checkJs non strict.

```text
Copie /home/claude/audit-maj3/build-QUAL/ui (01fea6d) : npm ci --ignore-scripts puis npx vue-tsc --noEmit -p tsconfig.json -> rc=0 ; argos-ui/tsconfig : strict, noUnusedLocals, noUnusedParameters ; grep -rnE ": any\b|as any|as unknown|@ts-ignore|@ts-expect-error|eslint-disable" argos-ui/src argos-desktop/src -> 2 : argos-ui/src/mises-en-page/contraste.ts:45 (« as unknown as readonly [number, number, number] », inchangé) et argos-ui/src/contenu/alimentation.ts:63 (JSON.parse(...) as unknown : élargissement sûr, pas un contournement).
```

*Réserve* : La partie « taille » reste suivie par AUD-QUA-18 ; argos-ui/src compte 7 fichiers de plus de 300 lignes (views/RechercheView.vue 488, mises-en-page/fantaisie/MiseEnPageFantaisie.vue 469). Le desktop reste non strict (AUD-QUA-27).

*Au 20/11* : Info (Info) — Le typage strict tient ; l'affirmation « taille maîtrisée, pas de fichier dieu » ne tient plus qu'en partie.

*Au 21/11* : Info (Info) — Typage strict tenu : vue-tsc sans erreur, toujours un seul contournement.

**Preuve d'origine**

```text
Plus gros fichier Java : scm/RepositoryActivity.java (394 lignes) ; méthode la plus longue : alerting/ResumesPeriodiques.java:118 composerPour (100 lignes), puis CorrelationEngine.java:142 (80) (script /home/claude/audit-tmp/qual-scripts/longmeth.py). UI : plus gros fichier contenu/alertes-poste.ts (277 lignes), desktop src/main.js (260). argos-ui/tsconfig : « strict: true », « noUnusedLocals », « noUnusedParameters » ; grep -rnE ": any|as any|as unknown|@ts-ignore|eslint-disable" argos-ui/src argos-desktop/src -> 0. Le module pur alertes-poste.ts injecte ses dépendances (DependancesSuivi) et est testable sans DOM.
```

**Impact** — Base lisible et testable ; pas de fichier « dieu ». Seules ResumesPeriodiques.composerPour et CorrelationEngine.deterministic gagneraient à être découpées.

**Action** — Aucune action obligatoire ; découper composerPour (lecture, filtrage RLS, écriture) lors de la prochaine évolution des résumés.

### AUD-QUA-30 — Ce qui tient sur le delta : aucune exception nouvellement avalée ni cause nouvellement perdue sur 232 commits, horloge, transactions, exécuteurs et code mort verrouillés par des tests d'architecture, typage strict de argos-ui et e2e en CI

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**
- **Nouveau** le 28/11

**Preuve**

```text
Script /home/claude/audit-maj3/build-QUAL/catches.py sur b142695 puis 01fea6d : relances sans cause 15 -> 15 (mêmes sites), variables de capture inutilisées 28 -> 23, captures sans journal ni retour 9 -> 7 ; tests d'architecture argos-server/src/test/java/io/argos/architecture/HorlogeInjecteeTest.java, TransactionsJtaTest.java, MethodesAtteintesEnProductionTest.java, TachesObserveesTest.java, ObservateursEvenementsTest.java, chacun avec un exemple de violation qui doit échouer ; .github/workflows/integration-continue.yml:88 (typecheck argos-ui), :112 (npm run check du desktop), :155 (typecheck e2e) ; détection de blocs dupliqués (/home/claude/audit-maj3/build-QUAL/dup.py, fenêtres de 6 lignes normalisées) : 6 fenêtres à b142695, 2 à 01fea6d (workspace/subscription/SubscriptionService.java:120 et :190, deux requêtes qui ne diffèrent que par le filtre d'état).
```

**Impact** — Les corrections des sprints 4, 5, 7 et 11 sont protégées contre la régression par des règles exécutées à chaque build (sous réserve que la suite Maven passe, non exécutée ici).

**Action** — Conserver ; étendre les règles d'architecture aux points encore ouverts (AUD-QUA-26 : capture d'IllegalStateException ; AUD-QUA-25 : frontières restantes).

### AUD-SEC-10 — Aucun secret réel versionné ni dans l'historique git

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : aucun secret réel versionné ni dans l'historique du delta.

*Changement depuis le 21/11* : Aucun secret ajouté dans le delta.

```text
`git log -p b142695..HEAD | grep -E 'ghp_[A-Za-z0-9]{20}|github_pat_|glpat-|AKIA|BEGIN .*PRIVATE KEY|sk-'` : seul le jeu d'essai factice du filtre IA (test PasserelleIa) ; .env.example:12-84 (variables de secret sans valeur) ; application.properties:411, :414 (secret client des profils %test/%dev, valeur de test) ; .github/workflows/publication-desktop.yml:104-105 (secrets lus depuis secrets.*, aucun en clair) ; `git ls-files | grep -iE '\.env$|\.pem$|\.p12$|\.pfx$'` : rien.
```

*Au 20/11* : Info (Info) — Tient : aucun secret réel versionné ni dans l'historique du delta.

*Au 21/11* : Info (Info) — Tient : aucun secret réel versionné dans le delta.

**Preuve d'origine**

```text
`git grep -nIiE '(password|secret|token|api[_-]?key)\s*[:=]'` : seulement des références d'environnement (application.properties:12,51,52,79), les valeurs de développement/test (application.properties:17,209,212 ; src/test/resources/keycloak/argos-realm.json:13,95, comptes de test) et .env.example:6 (exemple) ; `git log --all -p | grep -E 'ghp_|github_pat_|glpat-|AKIA|BEGIN .*PRIVATE KEY|sk-'` : uniquement des jeux d'essai factices des tests du filtre IA. .env et .env locaux ignorés (.gitignore, .dockerignore).
```

**Impact** — Pas de rotation nécessaire. Les secrets de test (secret-de-test, mots de passe des comptes de test) ne doivent jamais servir hors profils %dev/%test.

**Action** — Aucune ; conserver la règle « tout secret par variable d'environnement ».

### AUD-SEC-11 — Requêtes SQL paramétrées ; les seules concaténations portent sur des constantes

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : Nouveau gabarit de suppression par lots (conservation) formaté avec des noms de table et de colonne constants.

```text
argos-server/src/main/java/io/argos/conservation/RegleParHorodatage.java:18 (GABARIT_SUPPRESSION, %1$s/%2$s alimentés par des littéraux : audit/ConservationJournaux.java:28-46) ; commun/ContexteBase.java:75-86 (SET LOCAL ROLE constant, set_config paramétré) ; `grep -rnE 'prepareStatement\([^)]*\+' argos-server/src/main/java` : seulement des constantes (COLONNES, COLONNES_FICHE).
```

*Au 20/11* : Info (Info) — Tient.

*Au 21/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
workspace/EspacesService.java:325 `... WHERE " + filtre` : filtre toujours littéral (appels l.199, 206, 230, 248), valeur liée par setObject ; impact/RechercheVeille.java:136 selection() constante ; V11__recherche_veille.sql:102-129 `RETURN QUERY EXECUTE $q$ ... $q$ USING ...` (texte constant, saisies en paramètres) ; requête utilisateur via websearch_to_tsquery (V11:29).
```

**Impact** — Pas d'injection SQL identifiée dans les *Queries, *Store, RechercheVeille, SubscriptionService.

**Action** — Ne pas transformer lireInvitations(filtre) en API recevant un texte externe ; garder EXECUTE ... USING.

### AUD-SEC-12 — Authentification : jeton OIDC obligatoire partout, audience vérifiée, aucun mode sans authentification

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : jeton OIDC obligatoire, audience vérifiée, aucun mode sans authentification.

*Changement depuis le 21/11* : Chemins publics étendus à /api/v1/system/info et /q/health/* ; /q/metrics réservé à l'administrateur plateforme.

```text
argos-server/src/main/resources/application.properties:402 (service), :404 (audience), :405 (auth-server-url sans défaut en prod), :432-439 (public exact, metriques admin-plateforme, /* authenticated) ; tests api/comptes/CheminsPublicsTest, observabilite/MetriquesEtSanteTest.
```

*Réserve* : Les données exposées sans session par /q/health/* : voir nouveau constat AUD-SEC-36.

*Au 20/11* : Info (Info) — Tient : règle /* authenticated, seules /api/v1/system/info et /q/health* publiques, aucun résolveur sans authentification.

*Au 21/11* : Info (Info) — Tient : chemins publics exacts ou limités aux ressources statiques ; /api, /mcp et /q/openapi restent sous `/*` authenticated. D'après la documentation Quarkus (Context7), un chemin sans `/*` final est une correspondance exacte et le chemin le plus long l'emporte.

**Preuve d'origine**

```text
application.properties:200-218 (application-type=service, token.audience=argos-server, auth-server-url sans valeur par défaut en prod, /* authenticated) ; seul CallerResolver de production : api/security/OidcCallerResolver.java ; UnauthenticatedCallerResolver supprimé de l'historique ; HeaderCallerResolver uniquement dans src/test ; tests AuthentificationTest (401 sur /api/v1/espaces, /q/openapi).
```

**Impact** — Socle d'authentification sain ; l'administrateur plateforme n'est désigné que par l'e-mail vérifié ARGOS_ADMIN_EMAIL à la création du compte (ComptesService).

**Action** — Aucune ; ne pas ajouter de règle permit élargie (voir AUD-ARC-02).

### AUD-SEC-13 — Autorisation par espace vérifiée dans chaque ressource REST, doublée par la RLS PostgreSQL (pas d'IDOR trouvé)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : contrôle par espace dans chaque ressource ; pas d'IDOR trouvé sur les nouvelles routes (détachement de dépôt borné par project_id).

*Changement depuis le 21/11* : Nouvelles routes d'écriture d'inventaire et de projet contrôlées par Access avant toute écriture ; MatriceRolesTest ajouté (103 opérations × 6 rôles).

```text
argos-server/src/main/java/io/argos/api/InventoryResource.java:221-229 (memberOfProject, contributor) ; api/ProjectResource.java:110, :129 (administrator), :144 ; inventory/InventoryStore.java:142 (`DELETE FROM project_repository WHERE id = ? AND project_id = ?`) ; test api/comptes/MatriceRolesTest.
```

*Réserve* : Les écritures d'inventaire s'exécutent hors contexte RLS (design du changement alimenter-inventaire-et-completer-ihm, « Sécurité et isolation ») : seul Access les garde. La matrice ne prouve pas l'isolation par objet : voir AUD-SEC-37.

*Au 20/11* : Info (Info) — Tient : pas d'IDOR inter-espaces trouvé sur les routes du delta.

*Au 21/11* : Info (Info) — Tient : pas d'IDOR ; les contrôles d'accès sont inchangés en nombre dans chaque fichier refactorisé.

**Preuve d'origine**

```text
Contrôles memberOf/adminOf : RadarResource (membre/administrateur), InventoryResource.authorize, ActiviteResource.autoriser, VeilleWebResource.sourceGeree/exigerAdministrateur, SubscriptionResource (TEAM réservé à l'administrateur), EspacesService.exiger ; alertes filtrées par compte_id (ConsultationAlertes:95,119) ; RLS argos_app NOBYPASSRLS (V2:176) avec politiques V2/V6/V10/V11/V12/V14 ; hors périmètre = 404 journalisé (GestionErreurs). Signaux = données publiques (lecture par tout compte authentifié, voulu).
```

**Impact** — Pas d'accès inter-espaces identifié par remontée des flux.

**Action** — Aucune ; tout nouvel endpoint doit appeler access.* et passer par ContexteBase.

### AUD-SEC-14 — Garde SSRF de la veille web complète hors rebinding (plages, IPv6 embarquées, ports, redirections, tailles)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient pour la veille web ; mais une prémisse de la preuve d'origine est tombée : il existe désormais une route de création de projet et de rattachement de dépôt (POST /api/v1/projects, /repositories).

*Changement depuis le 21/11* : Garde SSRF inchangée sur le fond, complétée par l'épinglage (AUD-SEC-01).

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:101-122 ; RecuperateurWeb.java:149 ; commun/http/ClientsHttp.java:43 (Redirect.NEVER pour tous les autres clients) ; api/ProjectResource.java:108-113.
```

*Réserve* : Les forges restent à URL de configuration (pas de SSRF), mais tout administrateur d'espace peut viser n'importe quel dépôt avec les jetons du serveur : nouveau constat AUD-SEC-34.

*Au 20/11* : Info (Info) — Tient hors rebinding (AUD-SEC-01) ; les nouveaux flux ne visent que des origines de configuration.

*Au 21/11* : Info (Info) — Tient hors rebinding (AUD-SEC-01).

**Preuve d'origine**

```text
ingestion/web/GardeAdresses.java (plages IPv4/IPv6, NAT64/6to4/mappées, ports 80/443, identifiants refusés, messages sans nom d'hôte) ; RecuperateurWeb.java:100 chaque redirection revérifiée, redirections suivies à la main (Redirect.NEVER), délai et taille bornés ; liens d'articles jamais récupérés ; sources d'avis et forges sans redirection (HttpSourceClient.java:35, ForgeHttp.java:36) et à URL de configuration uniquement ; aucun endpoint de création de projet (ProjectDirectory.create sans appelant REST), dépôt GitHub validé par regex.
```

**Impact** — Protection à conserver ; seul le défaut de AUD-SEC-01 reste ouvert.

**Action** — Ne pas assouplir ; renseigner argos.veille-web.exceptions uniquement en dev/test.

### AUD-SEC-15 — XXE neutralisé dans la lecture RSS/Atom

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : Aucun nouvel analyseur XML ; manifestes limités à package.json et requirements.txt.

```text
argos-server/src/main/java/io/argos/ingestion/legi/LecteurJorf.java:63-68 (SUPPORT_DTD, entités externes désactivés) ; inventory/ManifestParser.java:36 ; `grep -rlnE 'DocumentBuilderFactory|SAXParser|ObjectInputStream|activateDefaultTyping|TransformerFactory' argos-server/src/main/java` : rien ; tests LecteurJorfTest, ingestion/web/LecteursTest.
```

*Au 20/11* : Info (Info) — Tient et s'étend au nouveau parseur réglementaire.

*Au 21/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
ingestion/web/LecteurFlux.java (ROME 2.1.0, pom.xml:24, DOCTYPE refusé par défaut) ; test LecteursTest.java:69-74 avec `<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>` refusé ; contenu réduit en texte par Jsoup ; aucune autre analyse XML ni désérialisation Java (grep ObjectInputStream/activateDefaultTyping/DocumentBuilderFactory : rien).
```

**Impact** — Pas d'XXE ni de désérialisation dangereuse.

**Action** — Garder le test ; ne pas activer setAllowDoctypes(true).

### AUD-SEC-16 — Serveur MCP : authentification, limitation et balisage du contenu externe en place (8 outils)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : 8 outils, 8 appels garde.appeler.

*Changement depuis le 21/11* : Aucun changement de fond.

```text
argos-server/src/main/java/io/argos/mcp : `grep -c '@Tool('` = 8 (OutilsSecurite 4, OutilsReferentiel 2, OutilsProjets 1, OutilsReglementaires 1) ; `grep -n garde.appeler` = 8 ; GardeAppels.java (LimiteurDebit).
```

*Réserve* : Les outils MCP ne passent pas par OpenAPI et sont absents de MatriceRolesTest.

*Au 20/11* : Info (Info) — Tient : authentification, limitation et balisage en place sur les 8 outils.

*Au 21/11* : Info (Info) — Tient : les 8 outils passent par garde.appeler.

**Preuve d'origine**

```text
Les 7 outils (OutilsSecurite/Projets/Referentiel/Reglementaires) passent par garde.appeler ; mcp/GardeAppels.java:74 limiteur 60/min/compte ; refus journalisés ; /mcp couvert par la règle /* authenticated ; contenu externe marqué CONTENU_EXTERNE_NON_FIABLE et tronqué (BalisageExterne) ; outils en lecture seule.
```

**Impact** — Surface MCP maîtrisée ; injection indirecte vers l'assistant signalée, pas supprimable.

**Action** — Aucune.

### AUD-SEC-17 — Passerelle IA : données filtrées, sortie validée, fournisseur non pilotable par l'utilisateur

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : classes non transmissibles et secrets refusés avant transmission, sortie validée.

*Changement depuis le 21/11* : Renommages et utilitaire HTTP commun (ClientsHttp.clientSansProxy).

```text
argos-server/src/main/java/io/argos/aigateway/PasserelleIa.java:126 (classes), :130 et :173 (secrets), :177 (bornage) ; aigateway/ollama/FournisseurOllama.java:66 (URL de configuration).
```

*Au 20/11* : Info (Info) — Tient : l'IA ne déclenche aucune action sans validation humaine.

*Au 21/11* : Info (Info) — Tient : refus des classes non transmissibles et des secrets avant toute transmission, chevrons neutralisés, réservation de quota atomique, sortie validée par schéma et journalisée sans le texte du modèle.

**Preuve d'origine**

```text
aigateway/PasserelleIa.java:99 refus des classes non transmissibles, :105 refus si secret détecté, :160 chevrons neutralisés dans les données ; ValidationSortie par schéma ; URL Ollama de configuration seulement (application.properties argos.ia.ollama.url) ; analyse de corrélation (noms de projets) désactivée par défaut ; Ollama sans port publié (compose.yaml). Sujets d'e-mail nettoyés des CR/LF (CanalEmail.sujet) et e-mail en texte brut.
```

**Impact** — L'injection de prompt par un flux externe ne peut produire qu'un texte affiché comme « produit par l'IA », sans action ni outil.

**Action** — Aucune ; garder l'IA sans outils ni actions.

### AUD-SEC-18 — Client Electron durci

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : Options de fenêtre déplacées dans fenetre.js, testées (fenetre.test.js, main.test.js).

```text
argos-desktop/src/fenetre.js:34-36 (contextIsolation, nodeIntegration false, sandbox), :64-66 (navigation), :94-97 (permissions) ; main.js:99, :236.
```

*Réserve* : En mode --dev, navigationAutorisee compare toujours par startsWith(devUrl) (fenetre.js:65), pas par origine analysée.

*Au 20/11* : Info (Info) — Tient ; le défaut résiduel de l'écoute locale fait l'objet de AUD-SEC-23.

*Au 21/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
argos-desktop/src/main.js:92-94 contextIsolation true, nodeIntegration false, sandbox true ; :107,114 openExternal seulement pour http(s), navigation hors app://argos bloquée ; :205 IPC acceptée seulement depuis la fenêtre principale ; permissions limitées aux notifications ; ui-protocol.js:51 traversée de chemin refusée et CSP stricte (script-src 'self', object-src 'none') ; preload n'expose que des valeurs simples.
```

**Impact** — Pas de vecteur d'exécution de code depuis le contenu affiché.

**Action** — Aucune ; pour le dev, comparer l'origine (new URL(url).origin === DEV_URL) plutôt que startsWith.

### AUD-SEC-19 — IHM Vue sans rendu HTML brut des contenus de veille

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : aucun v-html, innerHTML, eval ni new Function.

*Changement depuis le 21/11* : Aucun.

```text
`grep -rnE 'v-html|innerHTML|eval\(|new Function' argos-ui/src` : rien.
```

*Réserve* : Toujours pas de règle de lint interdisant v-html.

*Au 20/11* : Info (Info) — Tient : aucun v-html/innerHTML/eval.

*Au 21/11* : Info (Info) — Tient : aucun v-html/innerHTML/eval.

**Preuve d'origine**

```text
`grep -rnE 'v-html|innerHTML|eval\(' argos-ui/src` : aucun résultat ; liens externes filtrés par urlSure (argos-ui/src/contenu/liens.ts, http/https seulement) avec rel=noopener noreferrer (MentionsObligatoires.vue:32, AlertesView.vue:76) ; serveur réduit les flux en texte (LecteurFlux.texte).
```

**Impact** — XSS par contenus externes non identifiée.

**Action** — Interdire v-html par règle de lint.

### AUD-SEC-20 — Conteneurs et exposition réseau raisonnables ; CSRF non applicable

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : ports sur 127.0.0.1 par défaut, utilisateur non root, CORS limité, jeton porteur sans cookie.

*Changement depuis le 21/11* : Durcissement ajouté (voir AUD-SEC-25).

```text
compose.yaml:71 (PostgreSQL), :155 (Keycloak), :245 (Argos) ; Dockerfile:58 (USER 1001) ; application.properties:393-394 (CORS app://argos), :402.
```

*Réserve* : Ollama tourne en root (sans capacité) : compose.yaml:264.

*Au 20/11* : Info (Info) — Tient.

*Au 21/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
Dockerfile:27-29 utilisateur système 1001 (non root) ; compose.yaml:23 PostgreSQL sur 127.0.0.1, :58 Argos sur 127.0.0.1 par défaut, Ollama sans port ; application.properties:192 CORS limité à app://argos en prod ; authentification par jeton porteur sans cookie (application-type=service) : pas de surface CSRF.
```

**Impact** — Surface réseau minimale par défaut.

**Action** — Aucune.

### AUD-SEC-21 — Delta du lot 4 conforme : RLS sur les suivis, sources réservées à l'administrateur et collectées après vérification des conditions, hôtes data.culture filtrés, liens externes assainis

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité, Architecture · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : Aucun changement de fond.

```text
argos-server/src/main/java/io/argos/api/artiste/ProfilArtisteResource.java:265, :281 (access.operator), :167-171 (limiteur) ; tests SuiviIsolationTest et ProfilArtisteTest présents.
```

*Au 20/11* : Info (Info) — Tient.

*Au 21/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
V15__profil_artiste.sql : ENABLE ROW LEVEL SECURITY + politiques compte_id = argos_compte_courant() sur suivi_profil et suivi_rappels ; workspace/suivi/SuivisProfil.java:71,80 (ContexteBase) ; ProfilArtisteResource.java:243,259,274,287 access.operator() et :288 limiteur ; CollecteOpportunites.java:112 (conditions vérifiées), :121 (hôte admis), :124 (RecuperateurWeb + GardeAdresses) ; CorrespondanceListes.java:71-80 (http/https sans userinfo) ; ResumesIaActualites.java:87 (contrôle d'accès avant le cache partagé) ; aucune nouvelle arête inter-modules (comparaison des imports f58a68c/HEAD) ; SuiviIsolationTest ; ProfilArtisteTest.java:306-308 (403).
```

**Impact** — Aucun IDOR trouvé sur les nouvelles routes. Les opportunités sont des données publiques non cloisonnées, à dessein (GET /opportunites/{id}/resume accessible à tout compte dont le profil artiste est actif).

**Action** — Aucune.

### AUD-SEC-26 — Flux OIDC Authorization Code + PKCE de l'IHM et du desktop conforme (ADR-0033) : PKCE S256, state vérifié en temps constant, iss contrôlé, jetons en mémoire, redirections internes filtrées

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : Aucun changement de fond côté IHM ; desktop durci (AUD-SEC-23).

```text
argos-ui/src/session/oidc.ts:60 (aléa crypto), :115 (S256), :152-153 (iss RFC 9207), :174 et :182 (cheminInterne) ; argos-desktop/src/connexion.js:115-119.
```

*Réserve* : Les jokers http du royaume ont disparu (AUD-SEC-29).

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient côté clients (IHM et desktop) : PKCE S256, state en temps constant, iss contrôlé, jetons en mémoire.

**Preuve d'origine**

```text
argos-ui/src/session/oidc.ts:58-68 (aléa crypto 32 octets, S256), :74-91 (émetteur de découverte = émetteur configuré), :121-126,148 (state en temps constant), :150-153 (iss RFC 9207), :169-185 (code/state retirés de l'adresse, cheminInterne anti-redirection ouverte), :227-229 (token_type bearer) ; argos-ui/src/session/gestionnaire.ts:84 (jetons en champ privé), :257-264 (credentials omit) ; argos-ui/src/session/session.ts:50-71 (sessionStorage : demande seule, effacée avant vérification) ; argos-ui/src/api/autorisation.ts:9-32 (Bearer seulement vers /api/v1, jamais dans l'URL) ; argos-desktop/src/connexion.js:65-91 (URL d'autorisation reconstruite depuis l'émetteur configuré, PKCE S256 et redirect_uri de l'écoute exigés), :26-32 (page de retour statique, CSP default-src 'none', no-store) ; royaume de test argos-ui : implicite désactivé, directAccessGrants false, PKCE S256 imposé, redirections http://localhost:5173/* et http://127.0.0.1/argos/retour ; tests ClientIhmOidcTest.java (Keycloak réel), argos-ui/tests/oidc.test.mjs, session.test.mjs, argos-desktop/test/connexion.test.js. Pas de nonce : non requis ici (OIDC Core 3.1.2.1, nonce facultatif en flux code ; l'id_token n'est utilisé que comme id_token_hint, ADR-0033 option B ; PKCE couvre l'injection de code, RFC 9700).
```

**Impact** — Aucun jeton dans un stockage persistant, une adresse ou un journal ; pas de CSRF de connexion ni de redirection ouverte identifiés.

**Action** — Aucune ; dans le royaume de production, ne déclarer que les redirections exactes du portail (pas de joker) et imposer HTTPS sur l'émetteur.

### AUD-SEC-27 — Veille réglementaire et inventaire : archives JORF bornées sans XXE ni écriture disque, routes réglementaires et impacts doublés par la RLS, aucune route d'import de SBOM

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient pour les archives JORF et la RLS réglementaire ; la mention « aucune route d'import de SBOM » n'est plus vraie.

*Changement depuis le 21/11* : Prémisse modifiée : il existe désormais des routes d'import (POST …/inventory/sbom, …/manifests, …/refresh).

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java (bornes inchangées) ; LecteurJorf.java:63-68 ; api/InventoryResource.java:172-219 (nouvelles routes) ; application.properties:97, :100.
```

*Réserve* : Les nouvelles routes d'import ne sont limitées ni en débit ni en nombre de composants : nouveau constat AUD-SEC-35.

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java:41-87 (entrées, taille par fichier 5 Mio, volume décompressé bornés ; liens ignorés ; rien écrit sur disque) ; ArchivesJorf.java:47,79 ; LecteurJorf.java:63-68,85-86 ; application.properties (argos.reglementaire.jorf.taille-max-octets, entrees-max, decompresse-max-octets) ; V16__veille_reglementaire.sql:181-220 (fonctions SECURITY DEFINER avec search_path fixé, EXECUTE révoqué de PUBLIC), :235-335 (RLS fiches, domaines, experts, historique) ; api/InventoryResource.java:25-30 (lecture seule, aucune route d'import) ; inventory/CycloneDxParser.java:51-53 et application.properties argos.inventory.max-document-bytes=20971520 (JSON seulement, taille bornée) ; scm/RepositoryFiles.java:115-136,172,214 (segments encodés, taille bornée).
```

**Impact** — Pas de XXE, de bombe d'archive, de traversée de chemin ni d'IDOR sur ces nouveaux flux.

**Action** — Aucune ; conserver le test xxe.xml et ajouter un test d'archive hors bornes si absent.

### AUD-SEC-28 — Outillage e2e confiné à la boucle locale : secrets de test seulement, sources simulées sur 127.0.0.1/::1 sans traversée, docker/git/taskkill par chemin absolu

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient, exception AUD-SEC-24 levée.

*Changement depuis le 21/11* : Maven désormais résolu par chemin absolu (AUD-SEC-24).

```text
e2e/support/sources-simulees.mjs ; e2e/support/docker.mjs ; e2e/support/maven.mjs:44-50.
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient.

**Preuve d'origine**

```text
e2e/support/sources-simulees.mjs:30-35 (fichier() refuse toute sortie de donnees/chaine), :43-57 (corps borné à 64 Kio), :102-105 (écoute 127.0.0.1 et ::1) ; e2e/compose.yaml:15-16 (port 127.0.0.1:55432, tmpfs) ; e2e/support/docker.mjs:5-19 ; e2e/performance/mesurer-p95.mjs:79-84,95-96,242 (git/taskkill absolus, execFileSync sans shell), :250-251 (verifierHoteLocal sur l'API et l'émetteur) ; e2e/support/donnees.ts:53-59 (psql via execFileSync, arguments en tableau).
```

**Impact** — Les campagnes ne visent aucun hôte distant et n'exposent aucun service hors du poste ; seule réserve : AUD-SEC-24.

**Action** — Aucune.

### AUD-SEC-32 — Installation locale Keycloak (PR #28, #30) : aucun mot de passe par défaut pour l'administration Keycloak ni pour le compte initial, inscription libre désactivée, ports publiés sur 127.0.0.1, interface de gestion non publiée, règle publique Quarkus limitée aux fichiers statiques

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : aucun mot de passe par défaut, inscription désactivée, ports sur 127.0.0.1, gestion 9000 non publiée.

*Changement depuis le 21/11* : Garde des variables obligatoires du profil idp-local dans le point d'entrée ; règle publique étendue à /api/v1/system/info et /q/health/*.

```text
compose.yaml:109-126 (refus sans ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL, KC_BOOTSTRAP_ADMIN_PASSWORD), :155, :163 ; deploiement/keycloak/argos-realm.json:4, :19-21, :40-43, :105-110 ; application.properties:432.
```

*Réserve* : La règle publique n'est plus « limitée aux fichiers statiques » : /q/health/* expose des données d'état sans session (AUD-SEC-36).

*Au 21/11* : Info (Info) — —

**Preuve d'origine**

```text
compose.yaml:57-60 (KC_BOOTSTRAP_ADMIN_PASSWORD, ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL : repli vide, aucune valeur par défaut ; d'après la documentation Keycloak 26.7.5, le nom d'administrateur d'amorçage n'est utilisé que si le mot de passe est fourni), :64 (Keycloak sur 127.0.0.1), :71 (contrôle de santé sur 9000 en interne, port non publié) ; deploiement/keycloak/argos-realm.json:4 (registrationAllowed false), :12-15 (argos-server bearerOnly, standardFlow désactivé), :34-37 (argos-ui public, directAccessGrants et implicite désactivés), :48 (PKCE S256), :66-76 (argos-mcp : flux d'appareil seul), :102-103 (mot de passe initial temporaire) ; argos-server/src/main/resources/application.properties:294-297 (chemins exacts `/`, `/index.html`, `/licences-polices.txt` et préfixe `/assets/*` seulement).
```

**Impact** — Pas d'administrateur Keycloak ni de compte à mot de passe connu tant que le .env n'est pas renseigné ; aucune API ni outil MCP rendu public par la règle de l'IHM.

**Action** — Aucune ; faire échouer le démarrage plutôt que de lancer Keycloak avec des valeurs vides (`${VAR:?message}`) pour éviter un royaume importé sans administrateur.

### AUD-SEC-38 — Workflows GitHub Actions conformes : jeton en lecture seule par défaut, 7 actions toutes épinglées par empreinte, aucun pull_request_target, aucune expression d'événement dans un script

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**
- **Nouveau** le 28/11

**Preuve**

```text
.github/workflows/integration-continue.yml:6-12 (push main, pull_request ; permissions contents: read), :225 (seule expression d'événement, passée par env) ; publication-desktop.yml:13-18 (étiquettes desktop-v*, contents: read au niveau du workflow), :39-41 et :80-82 (persist-credentials: false), :42-48 (étiquette égale à la version) ; `grep -n 'uses:' .github/workflows/*.yml | grep -v '@[0-9a-f]\{40\}'` : rien ; renovate.json (helpers:pinGitHubActionDigests, minimumReleaseAge 3 days) ; npm ci --ignore-scripts partout ; gate « Royaume Keycloak livré » atteignable (scripts/verifier-royaume.mjs présent, job outils).
```

**Impact** — Une pull request de fork ne reçoit ni secret ni jeton en écriture ; les actions ne changent pas sous le dépôt.

**Action** — Aucune ; voir AUD-SEC-33 pour le job de publication. Vérifier côté GitHub (non visible ici) la protection des étiquettes desktop-v* et de main.

### AUD-SEC-39 — Configuration de l'IHM à l'exécution (ADR-0038) sans surface d'injection : émetteur et client lus en JSON depuis /api/v1/system/info, valeurs de l'exploitant, aucun rendu HTML

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/main/java/io/argos/api/SystemInfoResource.java:28-47 (record JSON, valeurs de configuration) ; application.properties:424-427 ; argos-ui/src/session/configuration.ts:29-51 (JSON, chaînes non vides exigées, aucun repli) ; argos-ui/src/session/oidc.ts:77 (émetteur de découverte comparé à l'émetteur configuré) ; api/security/EnTetesSecurite.java:82-89 (origine de l'émetteur extraite par URI pour connect-src) ; argos-desktop/src/reglages-emballes.js:15-21 (environnement puis package.json emballé).
```

**Impact** — Aucune donnée d'utilisateur n'atteint cette configuration ; une valeur hostile supposerait la maîtrise de l'environnement du serveur.

**Action** — Aucune. ADR-0038 encore « Proposé » (docs/architecture/adr/0038-configuration-ihm-a-l-execution.md:10).

### AUD-TST-13 — argos-ui : npm test passe (276/276 : 218 node --test, 58 Vitest), vue-tsc et le build passent

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : npm test 276/276, vue-tsc 0 erreur, vite build OK, contrôle des licences OK.

*Changement depuis le 21/11* : argos-ui ajoute Vitest (58 tests, 14 fichiers) à node --test (218 tests, 28 fichiers).

```text
build-tests/argos-ui : npm ci --ignore-scripts && npm test -> « # pass 218 # fail 0 » puis Vitest « Tests 58 passed (58) » ; npm run typecheck -> code 0 ; npm run build -> code 0 ; node ../scripts/verifier-licences-npm.mjs --paquet . -> 83 dépendances livrées, code 0 ; npm audit --audit-level=high -> code 0 (5 modérées)
```

*Au 20/11* : Info (Info) — npm test passe (206/206) et vue-tsc passe.

*Au 21/11* : Info (Info) — npm test passe (206/206), vue-tsc passe, vite build passe.

**Preuve d'origine**

```text
cd build-tests/argos-ui && npm ci && npm test -> « # tests 25 # pass 25 # fail 0 # skipped 0 # duration_ms 524.0763 » (real 3,1 s avec npm ci) ; npm run typecheck (vue-tsc --noEmit) -> code de sortie 0
```

**Impact** — Les tests existants sont sains et rapides ; le test d'architecture IHM (tests/architecture.test.mjs, couche mise en page sans réseau) est exécuté.

**Action** — Aucune ; les brancher dans la CI (AUD-TST-01).

### AUD-TST-14 — argos-desktop : npm run check passe (syntaxe, types, 92/92)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : npm run check vert (syntaxe 11 fichiers, typecheck 0 erreur, 92/92).

*Changement depuis le 21/11* : npm run check enchaîne désormais syntaxe, tsc -p tsconfig.json (checkJs) et tests ; 12 fichiers de test.

```text
build-tests/argos-desktop : ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci --ignore-scripts && npm run check -> « syntaxe valide : 11 fichiers de src/ », tsc 0 erreur, « # tests 92 # pass 92 # fail 0 » ; npm audit --audit-level=high -> code 0 (6 modérées, chaîne electron-builder)
```

*Au 20/11* : Info (Info) — npm test passe (54/54) et check:syntax passe sur les 8 fichiers source.

*Au 21/11* : Info (Info) — npm test passe (54/54) et check:syntax passe sur les 8 fichiers source.

**Preuve d'origine**

```text
cd build-tests/argos-desktop && ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci && npm test -> « # tests 22 # pass 22 # fail 0 # duration_ms 205.05 » ; npm run check:syntax -> OK
```

**Impact** — Les modules purs du client (préférences, notifications, poste, protocole UI/CSP) sont testés et passent.

**Action** — Aucune ; utiliser npm run check dans la CI.

### AUD-TST-15 — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : aucun test désactivé ni focalisé ; un seul test conditionnel serveur.

*Changement depuis le 21/11* : Inchangé.

```text
grep -rnE '@Disabled|@EnabledIf|@DisabledIf|assume[A-Z]' argos-server/src/test/java -> seul api/recherche/MesurePerformanceRechercheTest.java:48 ; grep '.only(|.skip(|fixme(|todo(' dans argos-ui/tests (Vitest compris), argos-desktop/test, e2e/{tests,desktop,mcp,accessibilite,performance}, scripts -> seul argos-ui/tests/polices.test.mjs:67 (skip conditionnel, skipped 0) ; e2e/playwright.config.ts:73 et e2e/accessibilite.config.ts:15 forbidOnly: true
```

*Au 20/11* : Info (Info) — Aucun test désactivé ni focalisé.

*Au 21/11* : Info (Info) — Aucun test désactivé ni focalisé.

**Preuve d'origine**

```text
grep @Disabled|@DisabledIf|assume* src/test/java -> seul api/recherche/MesurePerformanceRechercheTest.java:48 @EnabledIfSystemProperty(named = "argos.performance", matches = "true") (campagne de 100 000 actualités, lancement documenté :45) ; grep .only(|.skip(|todo( dans argos-ui/tests et argos-desktop/test -> aucun résultat
```

**Impact** — Pas de dette de tests ignorés ; la mesure de performance Q-05 est à la demande.

**Action** — Aucune ; éventuellement planifier la campagne de performance (job nocturne).

### AUD-TST-16 — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : StubServer remplacé par ServeurHttpSimule à instance (ecf45f5) ; ports aléatoires conservés.

```text
argos-server/src/main/resources/application.properties:44 %test.quarkus.http.test-port=0, :152 mailer.mock=true, :154 scheduler.enabled=false ; testsupport/ServeurHttpSimule.java:79 HttpServer.create(new InetSocketAddress("localhost", 0), 0) ; grep -rnE '@TestMethodOrder|@Order\(' -> aucun
```

*Réserve* : ingestion/web/TransportEpingleTest.java:76-77 lie 127.0.0.2, adresse absente par défaut sous macOS : test unitaire non portable sur ce système.

*Au 20/11* : Info (Info) — Ports aléatoires, sources simulées, courrier simulé et planificateur désactivé tiennent.

*Au 21/11* : Info (Info) — Ports aléatoires, sources simulées, courrier simulé et planificateur désactivé tiennent.

**Preuve d'origine**

```text
application.properties:20 %test.quarkus.http.test-port=0 ; testsupport/StubServer.java:53 HttpServer.create(new InetSocketAddress("localhost", 0), 0) et Javadoc :22-26 « aucun test n'appelle de service externe ni de modèle réel » ; signal/web/CollecteWebTest.java:146-153 horloge injectée (instance.horloge) ; testsupport/RecordingSleeper.java ; application.properties:83 %test.quarkus.mailer.mock=true, :85 scheduler désactivé ; aucun @TestMethodOrder/@Order
```

**Impact** — Tests reproductibles et parallélisables, sans dépendance réseau ; densité d'assertions correcte (pas d'assertTrue(true), pas de catch vide).

**Action** — Aucune.

### AUD-TST-17 — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux (MCP 47, comptes 59, alerting 56, ingestion web 41)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient, et la réserve du 21/11 (INDISPONIBLE non testé) est levée.

*Changement depuis le 21/11* : Volumes en hausse : mcp 14 classes / 47 méthodes, api/comptes 16 / 59, alerting 13 / 56, ingestion/web 5 / 41 ; l'issue INDISPONIBLE de GardeAppels est désormais testée (mcp/ErreursOutilsMcpTest.java:49-61).

```text
grep -rhE '^\s*@(Test|ParameterizedTest|TestFactory|RepeatedTest)' par dossier à 01fea6d ; mcp/ErreursOutilsMcpTest.java:27, :49-61 ; api/comptes/MatriceRolesTest.java
```

*Au 20/11* : Info (Info) — Couverture ciblée de la sécurité, du MCP et de l'alerting maintenue et étendue.

*Au 21/11* : Info (Info) — Couverture ciblée de la sécurité, du MCP et de l'alerting maintenue.

**Preuve d'origine**

```text
mcp : 8 classes @QuarkusTest, 31 tests (AuthentificationMcpTest 5, CloisonnementMcpTest 5, ContenuExterneMcpTest 2, ContratOutilsMcpTest 5, LimiteDebitMcpTest 2, OutilsConsultationMcpTest 7, RechercheMcpTest 3, ReglementaireMcpTest 2) ; api/comptes : 9 classes, 28 tests (Cloisonnement, DefenseEnProfondeur, JournalRefus…) ; alerting : 5 classes, 28 tests ; ingestion/web : 19 tests unitaires (GardeAdressesTest 9 = SSRF) + chain/CollecteTest, ResilienceTest, EnrichmentTest
```

**Impact** — Les zones à risque (cloisonnement par espace et RLS, authentification MCP et flux d'appareil, garde d'adresses SSRF, échec SMTP, IA indisponible) sont couvertes par des scénarios reliés aux exigences.

**Action** — Aucune ; compléter par la matrice d'autorisations (AUD-TST-05).

### AUD-TST-18 — ModuleBoundariesTest couvre les 21 modules avec liste fermée ; exécuté par la CI (mvn verify)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : 21 paquets io.argos, 21 entrées dans la table, exécuté par mvn verify en CI.

*Changement depuis le 21/11* : ModuleBoundariesTest couvre 21 modules (commun, conservation, execution, horloge, observabilite ajoutés) avec liste fermée (chaqueClasseResideDansUnModuleDeclare) et règle du noyau commun, chacune prouvée contre un exemple négatif ; 17 classes de règles dans architecture/.

```text
ls argos-server/src/main/java/io/argos -> 21 paquets ; architecture/ModuleBoundariesTest.java:41-76 (table), :112-135 (noyau, module déclaré, exemples négatifs), :137 @TestFactory ; .github/workflows/integration-continue.yml:49
```

*Réserve* : Exécution non constatée ici (pas de Maven).

*Au 20/11* : Info (Info) — Frontières des 16 modules vérifiées par ArchUnit ; exécution non constatée ici.

*Au 21/11* : Info (Info) — Frontières des 16 modules vérifiées par ArchUnit ; exécution non constatée ici.

**Preuve d'origine**

```text
architecture/ModuleBoundariesTest.java:31-57 (16 modules, identiques aux paquets io.argos.*) ; :67-80 @TestFactory un test dynamique par module ; :88-174 mcpNeLitQueLaSecuriteDeLApi, seuleLaPasserelleConnaitLeFournisseur, signal/projectintel/catalogNEmprunteQueRlsEtErreurs ; pom.xml:145-150 archunit-junit5 1.5.1 en scope test ; surefire 3.5.3 sans <includes>/<excludes>
```

**Impact** — Les frontières documentées (arc42 §5) sont vérifiées dès qu'un mvn test est lancé ; exécution effective non constatée ici (build Maven impossible, cf. angles morts) et jamais automatisée (AUD-TST-01).

**Action** — Aucune ; voir AUD-ARC-07 pour les modules futurs.

### AUD-TST-21 — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 50 s, sans serveur ni base ; lancée par la CI

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : 29/29 rejoués à 01fea6d.

*Changement depuis le 21/11* : Inchangé ; la campagne est aussi lancée par le job e2e de la CI (npm run test:accessibilite).

```text
cd build-tests/e2e && npm ci --ignore-scripts && node node_modules/@playwright/test/cli.js test -c accessibilite-chrome-local.config.ts (même configuration avec channel 'chrome', Google Chrome for Testing 153.0.8010.12) -> « 29 passed (49.6s) » ; .github/workflows/integration-continue.yml:205-207
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 30,8 s, sans serveur ni base.

**Preuve d'origine**

```text
cd build-tests/e2e && npm ci && npx playwright test -c accessibilite-chrome-local.config.ts (copie d'audit : même configuration avec channel 'chrome', Google Chrome du conteneur au lieu du Chromium 1228 de Playwright) -> « 29 passed (34.7s) » ; e2e/accessibilite.config.ts:25-32 (Vite seul sur 127.0.0.1:5174) ; rapport de référence e2e/resultats/accessibilite/rapport-2026-11-17.json (29 attendus, 0 échec)
```

**Impact** — Contrastes (RGAA 3.2), ordre de tabulation et focus, 320 px, réduction des animations et arbre d'accessibilité des quatre mises en page sont vérifiés par une campagne légère, rejouable dans n'importe quel environnement disposant de Chromium ; c'est le meilleur candidat pour un premier job E2E en CI.

**Action** — Aucune ; la brancher en premier dans la CI (AUD-TST-01).

### AUD-TST-22 — Nouveaux tests structurants dans le delta : couverture RLS générique, contrats SCM enregistrés, migrations, pont preload et fonctions pures de la campagne de performance

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : Tests toujours présents.

```text
api/comptes/CouvertureRlsTest.java (4), chain/ScmContratEnregistreTest.java (5), chain/MigrationV18Test.java (1), chain/MigrationDepotsTest.java (1), argos-desktop/test/preload.test.js (9), chaine-poste.test.js (6) ; node --test e2e/performance/*.test.mjs -> 17/17
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tests structurants du 20/11 toujours en place.

**Preuve d'origine**

```text
api/comptes/CouvertureRlsTest.java:71-141 (toute table accessible au rôle applicatif est sous RLS, rôle sans privilège de contournement, aucune ligne sans contexte ; requêtes sur pg_class) ; chain/ScmContratEnregistreTest.java (13 cas GitHub/GitLab enregistrés : jeton refusé, limites de débit, artefact absent) ; chain/MigrationV18Test.java, chain/MigrationDepotsTest.java ; argos-desktop/test/preload.test.js (9) et chaine-poste.test.js (6, chaîne preload → IPC → poste → notification factice) ; node --test e2e/performance/*.test.mjs -> 7/7
```

**Impact** — Les invariants de sécurité (RLS sur tout le schéma) et les contrats avec les forges sont vérifiés de façon générique, ce qui protège aussi les tables et appels futurs.

**Action** — Aucune ; réutiliser l'approche par catalogue de CouvertureRlsTest pour TestDb (AUD-TST-08).

### AUD-TST-27 — Tests du delta du 21/11 toujours actifs : règle ArchUnit de lecture groupée, comparaison de « Ma veille » à un calcul de référence, contrôle de nommage testé (75 tests de scripts)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient.

*Changement depuis le 21/11* : Tests toujours présents ; le contrôle de nommage passe sur 803 fichiers.

```text
architecture/LectureGroupeeSignauxTest.java (3) ; api/veille/VeilleLectureGroupeeTest.java (2) ; node --test scripts/*.test.mjs -> 75/75 (clone git, verifier-nommage.test.mjs exige git ls-files) ; node scripts/verifier-nommage.mjs -> « 803 fichiers analysés, 0 violation(s) »
```

*Au 21/11* : Info (Info) — —

**Preuve d'origine**

```text
argos-server/src/test/java/io/argos/architecture/LectureGroupeeSignauxTest.java:40-79 (3 tests, dont laRegleDetecteUneReferenceDeMethode sur architecture/exemples/LectureUnitaireParReference.java) ; api/veille/VeilleLectureGroupeeTest.java:198-237 (contenu identique au calcul de référence en base et à la lecture unitaire) ; node --test scripts/*.test.mjs "e2e/performance/*.test.mjs" -> 31/31 (copie d'audit initialisée en dépôt git : verifier-nommage.test.mjs exige git ls-files).
```

**Impact** — La correction de performance PRF-07 est protégée contre sa régression (retour à la lecture unitaire) et son résultat fonctionnel est vérifié ; la règle se teste elle-même contre un faux positif.

**Action** — Aucune ; brancher ces commandes dans la CI (AUD-TST-01).

### AUD-TST-31 — Les gates déclarés par la CI sont atteignables : chaque étape appelle une commande ou un script versionné, et tous les gates Node rejoués hors du poste du mainteneur passent

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**
- **Nouveau** le 28/11

**Preuve**

```text
.github/workflows/integration-continue.yml:49, :55, :86-94, :111-117, :131-158, :177-207, :240-257, :276 ; scripts présents : scripts/verifier-{couverture-modules,docs,nommage,configuration,royaume,licences-npm,licences-serveur}.mjs, e2e/scripts/lancer.mjs, e2e/compose.image.yaml ; argos-ui/package.json, argos-desktop/package.json, e2e/package.json (scripts test, typecheck, check, test:accessibilite) ; copie build-tests à 01fea6d : ui 276/276, typecheck et build OK ; desktop check 92/92 ; scripts + performance 92/92 ; nommage, configuration, royaume, docs (sans rendu) code 0 ; e2e typecheck 0 ; npm audit high code 0 ×3 ; accessibilité 29/29 ; git diff --name-status b142695...01fea6d -- db/migration -> 7 ajouts (V22 à V28), aucun « M » ni « D » : la garde ADR-0032 aurait laissé passer le delta.
```

**Impact** — La CI protège réellement ce qu'elle annonce ; seuls le job serveur (Maven) et le job e2e (docker build) n'ont pas pu être rejoués ici.

**Action** — Aucune ; publier le rapport Playwright et le rapport JaCoCo à chaque exécution (pas seulement en échec) pour garder une preuve consultable.

### AUD-PER-14 — Les tâches planifiées sont protégées contre le chevauchement et idempotentes en base

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient : les 16 @Scheduled déclarent SKIP ; l'attente de reprise reste RUNNING, donc sous sync_run_one_running.

*Changement depuis le 21/11* : Trois tâches de plus (vulnintel-enrichissement, vulnintel-rattrapage, argos-purge-conservation) ; exclusion de la synchronisation réparée (AUD-PER-27).

```text
commande « grep -rn '@Scheduled(' argos-server/src/main/java » : 16 occurrences, 16 avec ConcurrentExecution.SKIP ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:148-153 (attendreReprise : state reste RUNNING) ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:40.
```

*Réserve* : Le rafraîchissement du traitement pendant un téléchargement long n'est pas assuré (AUD-PER-32).

*Au 20/11* : Info (Info) — La protection tient : les 13 @Scheduled déclarent SKIP, et l'exclusion repose sur la base (index uniques, verrous consultatifs).

*Au 21/11* : Info (Info) — La protection tient : les 13 @Scheduled (12 classes) déclarent SKIP, et l'exclusion repose sur la base (index uniques, verrous consultatifs).

**Preuve d'origine**

```text
concurrentExecution = Scheduled.ConcurrentExecution.SKIP sur les 7 @Scheduled (CollecteWeb.java:83-84, CollecteActivite.java:69-70, PurgeActivite.java:32-33, EnvoiAlertes.java:72-73, RappelsRadar.java:90-91, ResumesPeriodiques.java:70-77) ; garde-fous en base : sync_run_one_running (V3:40), alerte_immediate_uk / alerte_resume_uk (V8:36-38), alerte_radar_uk (V12:134), verrou consultatif par source web (StockActualites.java:82), réservation avec bail dans EnvoiAlertes (BAIL = 5 min, LIMIT 500).
```

**Impact** — Pas de double exécution sur une instance. Sur plusieurs instances (service en ligne), les doublons de messages sont déjà évités par les index uniques et les verrous ; seul le travail est fait en double.

**Action** — Aucune action immédiate ; avant de passer à plusieurs instances, ajouter un verrou de cluster (quarkus-quartz en mode JDBC, ou pg_try_advisory_lock en tête de chaque tâche).

### AUD-PER-15 — Les appels IA sont bornés : exécuteurs dédiés à file bornée, délais maximaux, taille de réponse plafonnée, Ollama configuré à un modèle et une requête à la fois

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient : files bornées, délais, réponse IA plafonnée à 1 Mio.

*Changement depuis le 21/11* : Exécuteurs factorisés dans execution.ExecuteurBorne (file bornée, AbortPolicy) ; Ollama par ClientsHttp.clientSansProxy.

```text
argos-server/src/main/java/io/argos/execution/ExecuteurBorne.java:112-117 ; argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java:41, :66, :94 ; argos-server/src/main/resources/application.properties:130-137, :212.
```

*Réserve* : Non revérifié : l'attente IA de la collecte réglementaire sur le fil du planificateur (réserve du 20/11).

*Au 20/11* : Info (Info) — La protection tient : files bornées, délais, taille de réponse plafonnée, quotas.

*Au 21/11* : Info (Info) — La protection tient : files bornées, délais, taille de réponse plafonnée, quotas.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:75-80 (ThreadPoolExecutor(2, 2, ..., ArrayBlockingQueue<>(16), AbortPolicy)) ; argos-server/src/main/java/io/argos/projectintel/SyntheseActivite.java:46 (1-2 fils, file 8) ; argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java:37 (TAILLE_MAX_REPONSE 1 Mio), :60 (connectTimeout ≤ 5 s), :82 (timeout argos.ia.delai=PT60S) ; compose.yaml : OLLAMA_MAX_LOADED_MODELS=1, OLLAMA_NUM_PARALLEL=1, mem_limit 3g ; quotas argos.ia.plafond-instance-jour / quota-espace-jour.
```

**Impact** — L'indisponibilité ou la lenteur du modèle sur le CPU du DS1522+ n'épuise ni les fils ni la mémoire de l'application. Les demandes au-delà de la file passent en « indisponible ».

**Action** — Aucune ; à surveiller : la somme des fils IA (MoteurAlertes 2 + SyntheseActivite 2 + autres) dépasse OLLAMA_NUM_PARALLEL=1, donc les demandes s'empilent côté Ollama jusqu'à leur délai.

### AUD-PER-16 — Clients HTTP sortants : délais de connexion et de requête configurés partout, sans redirection ; caches KEV et CERT-FR avec durée de vie

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient et s'est renforcée : six usages passent par la fabrique commune ; caches KEV et CERT-FR de 6 h.

*Changement depuis le 21/11* : Clients HTTP construits par io.argos.commun.http.ClientsHttp (connectTimeout, Redirect.NEVER), test ClientsHttpCommunsTest ; corps lus par LectureBornee.

```text
argos-server/src/main/java/io/argos/commun/http/ClientsHttp.java:28-43 ; argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:60 ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:45 ; argos-server/src/main/java/io/argos/scm/ForgeHttp.java:53 ; argos-server/src/main/java/io/argos/releaseradar/ClientEndoflife.java:80 ; argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:82 ; argos-server/src/main/java/io/argos/vulnintel/KevSource.java:37, :68-69 ; argos-server/src/main/java/io/argos/vulnintel/CertFrSource.java:47, :67.
```

*Au 20/11* : Info (Info) — La protection tient : les 7 clients HttpClient déclarent un délai de connexion et refusent les redirections (sauf la passerelle IA, locale), et les caches KEV et CERT-FR gardent leur durée de vie.

*Au 21/11* : Info (Info) — La protection tient : les 7 clients HttpClient déclarent un délai de connexion et refusent les redirections, et les caches KEV et CERT-FR gardent leur durée de vie.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java (connectTimeout + timeout = argos.sources.http-timeout PT30S, Redirect.NEVER) ; vulnintel/EnrichmentHttp.java:36 ; scm/ForgeHttp.java:36,46 (+ taille plafonnée, gestion de quota 429/403) ; releaseradar/ClientEndoflife.java:72,82 (PT10S) ; ingestion/web/RecuperateurWeb.java:51,140 (échéance globale imposée par une minuterie, en plus du timeout) ; vulnintel/KevSource.java:64-73 et CertFrSource.java:63-90 (cache synchronisé, argos.sources.certfr.cache-ttl PT6H) ; LimiteurDebit en mémoire avec nettoyage (api/security/LimiteurDebit.java:28-76).
```

**Impact** — Aucun appel distant ne peut bloquer indéfiniment un fil. Les catalogues volumineux (KEV) ne sont pas retéléchargés à chaque signal.

**Action** — Aucune ; noter que le limiteur de débit en mémoire ne vaut que pour une seule instance (à partager, en base ou dans Redis, en cas de mise à l'échelle horizontale).

### AUD-PER-17 — Bundle de l'IHM encore léger : 240,6 kB, dont 79,0 kB gzippé, servi compressé

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Le bundle reste sous le seuil de 300 kB : 240,59 kB, 78,98 kB gzippé, transféré compressé.

*Changement depuis le 21/11* : IHM enrichie (mises en page, inventaire, veille paginée) : +38,9 kB brut depuis le 21/11 ; compression serveur désormais active.

```text
Commande : cp -r /home/claude/work/argos/argos-ui /home/claude/audit-maj3/build-perf/ && npm ci --ignore-scripts && npm run build -> « dist/assets/index-B83XCYuT.js 240.59 kB │ gzip: 78.98 kB », « dist/assets/index-B9F0JsXt.css 34.22 kB │ gzip: 6.68 kB », un seul bloc JS ; argos-server/src/main/resources/application.properties:308.
```

*Réserve* : Un seul bloc JS (aucune route chargée à la demande, argos-ui/src/router.ts:50-51) ; 13 familles de polices (598 kB en woff2) chargées seulement à l'usage par le navigateur. Croissance de 19 % en une semaine.

*Au 20/11* : Info (Info) — Bundle toujours léger (201,61 kB, 68,41 kB gzippé, sous le seuil de 300 kB) ; en revanche la seconde moitié de la protection, l'import par lot, a régressé (nouveau constat AUD-PER-20).

*Au 21/11* : Info (Info) — Le bundle reste léger : 201,74 kB, 68,43 kB gzippé, sous le seuil de 300 kB.

**Preuve d'origine**

```text
Commande : cp -r argos-ui /home/claude/audit-tmp/build-perf/ && npm ci && npx vite build -> « dist/assets/index-DXBFwvE8.js 111.25 kB │ gzip: 41.87 kB », « dist/assets/index-Ds4gtZKj.css 5.18 kB » (2 dépendances d'exécution : vue, vue-router) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java (INSERT component par addBatch/executeBatch) ; argos.inventory.max-document-bytes=20971520.
```

**Impact** — Le chargement initial reste négligeable, y compris sur le réseau domestique ou en ligne ; l'archive Electron est dominée par le runtime Electron, pas par l'IHM.

**Action** — Aucune ; découper par route (import() dynamique) seulement si le bundle dépasse ~300 kB.

### AUD-PER-23 — L'empreinte du contenu normalisé évite les versions et les événements superflus : une resynchronisation horaire d'un avis inchangé ne relance ni l'enrichissement, ni la corrélation, ni les alertes

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient : un avis d'empreinte normalisée identique renvoie UNCHANGED sans publier SignalChanged, donc sans mise en file d'enrichissement ni corrélation.

*Changement depuis le 21/11* : Aucun changement de fond.

```text
argos-server/src/main/java/io/argos/signal/SignalRecorder.java:84, :98, :123, :194.
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — La protection tient : un avis inchangé (empreinte normalisée identique) renvoie UNCHANGED sans publier SignalChanged.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/signal/SignalRecorder.java:101-110 (comparaison de normalized_hash ; inchangé -> Outcome.UNCHANGED, sans changes.fire) et :144 (SignalChanged seulement après modification) ; argos-server/src/main/resources/db/migration/V18__automatisation_collecte_et_abonnements.sql:9-15 (colonnes normalized_hash, index unique signal_version_normalized_uk) ; ingestion/SourceSynchronizer.java:135 (UNCHANGED et IGNORED_OLDER comptés sans traitement) ; test cité par openspec : chain/CollecteTest#col10ContenuRevenuAUneVersionAnterieure (non exécuté ici).
```

**Impact** — La synchronisation horaire ne réécrit pas les signaux et ne remet pas en route la chaîne aval pour les avis qui n'ont pas changé. Pour les signaux antérieurs à V18, l'empreinte est calculée une fois, au premier passage, en renormalisant le contenu brut de la version courante (SignalRecorder.java:104 et :224), sans nouvelle version.

**Action** — Aucune ; garder en tête l'effet de bord sur AUD-PER-02 : un enrichissement manqué ne sera plus jamais retenté par une resynchronisation, d'où l'urgence de planifier retryMissing.

### AUD-PER-24 — La collecte JORF est bornée : archive de 50 Mio au plus, décompression en flux plafonnée à 500 Mio et 50 000 entrées, fichier XML de 5 Mio au plus, débit limité à 10 requêtes par minute

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — La protection tient : 50 Mio, 500 Mio décompressés, 50 000 entrées, 5 Mio par fichier, 10 requêtes par minute.

*Changement depuis le 21/11* : Client construit par ClientsHttp (ArchivesJorf.java:82).

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:76-82 ; argos-server/src/main/resources/application.properties:365-367, :388.
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — La protection tient : archive de 50 Mio au plus, décompression plafonnée à 500 Mio et 50 000 entrées, fichier de 5 Mio au plus, 10 requêtes par minute.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:78 (new ArchiveTar(entreesMax, 5 Mio, decompresseMax)) et :155-161 (readNBytes(max + 1), refus au-delà) ; argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java:41-80 (GZIPInputStream lu bloc par bloc, total et nombre d'entrées contrôlés, fichiers non XML sautés) ; application.properties : argos.reglementaire.jorf.taille-max-octets=52428800, decompresse-max-octets=524288000, entrees-max=50000, argos.reglementaire.debit.requetes-par-minute=10.
```

**Impact** — Une archive JORF anormale (bombe de décompression, archive géante) est refusée sans épuiser le tas d'environ 768 Mio. Réserve : l'archive compressée est conservée entière en mémoire (jusqu'à 50 Mio, plus la copie interne de readNBytes) pendant sa lecture, et la lecture du corps n'a pas d'échéance globale (AUD-PER-16).

**Action** — Aucune ; lire éventuellement l'archive directement en flux (ofInputStream -> GZIPInputStream) pour éviter le tampon de 50 Mio.

### AUD-PER-36 — Garde-fous de performance actifs : nombre d'instructions SQL constant vérifié en CI pour la corrélation, les alertes et le radar, et campagne p95 versionnée sur un jeu à trois espaces, 500 000 impacts résolus et abonnements, entièrement dans la cible

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**
- **Nouveau** le 28/11

**Preuve**

```text
argos-server/src/test/java/io/argos/chain/RequetesConstantesTest.java:133, :172, :212 avec argos-server/src/test/java/io/argos/testsupport/CompteurInstructions.java (trace FE=> Execute du pilote, argos-server/src/main/resources/application.properties:293-294), exécutés par « mvn -B -f argos-server/pom.xml -P integration-continue clean verify » (.github/workflows/integration-continue.yml, job serveur, push sur main et pull_request) ; docs/architecture/quality/mesure-performance-api-mcp.md (campagne du 2026-11-27, révision 496add9, 17 opérations, p95 maximal 185,5 ms pour Ma veille, verdict « toutes les opérations dans la cible ») ; e2e/resultats/performance/rapport-2026-11-19.json à rapport-2026-11-27.json (série versionnée ; jeu à un espace jusqu'au 25/11, à trois espaces et abonnements depuis le 26/11).
```

**Impact** — Une régression N+1 sur ces trois chemins casse la CI. La série de campagnes permet de suivre l'évolution. Limites : la campagne n'est pas lancée par la CI (seuls ses tests unitaires, job outils) ; elle tourne en quarkus:dev sur un poste win32 de 16 cœurs, jamais sur le NAS ; elle exclut le POST de synchronisation et les traitements de fond (ingestion, enrichissement, purges) ; la révision 496add9 précède la tête 01fea6d de 47 commits, dont 19 fichiers de argos-server/src/main modifiés (refactorisations).

**Action** — Conserver ; ajouter une mesure du traitement de fond (amorçage OSV sur archive locale, débit de la file d'enrichissement, durée de la purge) et une exécution de référence sur le NAS.

### AUD-DEP-11 — npm audit argos-desktop : 6 vulnérabilités modérées (minimatch GHSA-g3hv-75wg-m249, déni de service) via electron-builder, outil de build uniquement

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build), non bloquantes par décision.

*Changement depuis le 21/11* : Aucun changement de electron-builder (26.18.0, dernière publiée) ; niveau d'échec npm audit fixé à high par le porteur (integration-continue.yml:29-30).

```text
Copie /home/claude/audit-maj3/build-deps/audit-argos-desktop, `npm audit --json` -> moderate 6, high 0 (@electron/universal, app-builder-lib, dmg-builder, electron-builder, electron-builder-squirrel-windows, minimatch GHSA-g3hv-75wg-m249) ; dépendance d'exécution electron-updater 6.8.12 hors de la chaîne ; `npm view electron-builder version` -> 26.18.0.
```

*Réserve* : Acceptation consignée seulement dans openspec/changes/archive/2026-11-24-mettre-en-place-integration-continue/tasks.md:67.

*Au 20/11* : Info (Info) — Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build).

*Au 21/11* : Info (Info) — Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build).

**Preuve d'origine**

```text
Copie dans /home/claude/audit-tmp/build-deps/argos-desktop, `npm audit --json` -> {moderate: 6, high: 0, critical: 0} ; chaîne : electron-builder 26.18.0 -> app-builder-lib -> @electron/universal -> minimatch 9.0.9 (plage vulnérable >=7.0.0 <11.0.1) ; fixAvailable proposé = electron-builder 24.13.3 (rétrogradation majeure, non pertinente).
```

**Impact** — Aucun impact en exécution : electron-builder ne sert qu'à l'empaquetage local sur un poste de confiance ; la DoS suppose des motifs glob malveillants.

**Action** — Ne pas appliquer `npm audit fix --force` ; suivre la prochaine version d'electron-builder et accepter le risque explicitement (documenté) d'ici là.

### AUD-DEP-12 — argos-ui : aucune vulnérabilité dans les dépendances livrées (vue 3.5.46, vue-router 5.5.1) ; 5 modérées dans l'outillage de test (@vue/test-utils → glob/minimatch)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient pour le code livré (vue, vue-router : 0 vulnérabilité) mais npm audit passe de 0 à 5 vulnérabilités modérées, toutes dans l'outillage de test ; vite 8.4.0 et vue-router 5.5.3 publiées le 26/11 (dans le délai minimumReleaseAge de Renovate).

*Changement depuis le 21/11* : Lockfile de l'IHM mis à jour (vue 3.5.46) ; @vue/test-utils 2.5.1 (dépendance de développement) tire js-beautify → glob/minimatch vulnérables.

```text
Copie /home/claude/audit-maj3/build-deps/audit-argos-ui, `npm audit --json` -> moderate 5 (@vue/test-utils, js-beautify, editorconfig, glob GHSA-3g3m-2fjr-6pw4, minimatch GHSA-g3hv-75wg-m249), correctif proposé = rétrogradation majeure @vue/test-utils 2.4.11 ; `npm ci --ignore-scripts && npm run build` rejoués -> rc=0 ; versions : vue 3.5.46, vite 8.3.8, vue-router 5.5.1.
```

*Réserve* : Non bloquant en CI (seuil high) ; aucune trace écrite de l'acceptation de ces 5 modérées.

*Au 20/11* : Info (Info) — Tient : 0 vulnérabilité, toutes les dépendances directes à la dernière version sauf TypeScript (tilde délibéré).

*Au 21/11* : Info (Info) — Tient : 0 vulnérabilité ; dépendances directes à jour à une exception de un jour près (vue 3.5.46 publiée le 20/11) et TypeScript (tilde délibéré).

**Preuve d'origine**

```text
Copie /home/claude/audit-tmp/build-deps/argos-ui, `npm audit --json` -> total 0 ; `npm view <pkg> version` : vite 8.3.8, vue 3.5.45, @vitejs/plugin-vue 6.0.11, vue-tsc 3.3.14 = versions résolues dans package-lock.json (lockfileVersion 3) ; Dockerfile:7-8 `COPY ... package-lock.json` + `npm ci`. Seul écart : typescript ~6.0.3 alors que 7.0.2 est publié (choix délibéré par le tilde).
```

**Impact** — Chaîne front reproductible et saine.

**Action** — Aucune ; planifier la montée TypeScript 7 quand vue-tsc la prend en charge.

### AUD-DEP-13 — Quarkus 3.40.1.2 sur le flux LTS courant (3.40) ; Java 17 compatible jusqu'à la fin des 3.x

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : Quarkus 3.40.1.2 sur la LTS 3.40 (support 12 mois), Java 17.

*Changement depuis le 21/11* : Aucun (quarkus.platform.version inchangée).

```text
argos-server/pom.xml:16 release 17, :21 3.40.1.2 ; Context7 /quarkusio/quarkusio.github.io : billets 2026-09-30 (3.40 LTS, « supported for 12 months ») et 2026-10-19 (3.40.1.1) ; profil integration-continue : requireJavaVersion [17,18) (pom.xml:276-279).
```

*Au 20/11* : Info (Info) — Tient : Quarkus 3.40.1.2 sur la LTS 3.40, Java 17 compatible.

*Au 21/11* : Info (Info) — Tient : Quarkus 3.40.1.2 sur la LTS 3.40, Java 17 compatible.

**Preuve d'origine**

```text
argos-server/pom.xml:14-21 `<quarkus.platform.version>3.40.1.2</quarkus.platform.version>` (commentaire : LTS jusqu'au 30/09/2027). Context7 /quarkusio/quarkusio.github.io, _data/versions.yaml : version courante 3.40.1.1, flux maintenus « 3.40, 3.33, 3.27, ... », jdk « 17+ » ; politique LTS : support 12 mois, nouvelle LTS tous les 6 mois.
```

**Impact** — Plateforme serveur à jour ; la sortie de Quarkus 4 imposera Java 21 (Dockerfile:16 et :25 à faire évoluer).

**Action** — Aucune ; anticiper le passage à Java 21 (images temurin-21) avant la fin de support de la LTS 3.40.

### AUD-DEP-16 — Conteneurisation et assemblage Compose solides : multi-étapes, JRE seul, utilisateur non root, mem_limit, restart, healthcheck PostgreSQL, ports locaux par défaut

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient et s'améliore ; durcissement gardé par verifier-configuration.mjs.

*Changement depuis le 21/11* : Renforcé : fragment x-durcissement (read_only, cap_drop ALL, no-new-privileges) sur les quatre services, code applicatif propriété de root, postgres sous l'utilisateur postgres, Keycloak en image optimisée.

```text
Dockerfile:8/23/51 trois étapes, :53 useradd 1001, :58 USER 1001 ; compose.yaml:29-34, :38/:90/:251/:260 fusion, :77/:156/:246/:275 mem_limit ; `docker compose config -q` et `--profile idp-local --profile ia config -q` -> rc=0 (copie, .env factice) ; scripts/verifier-configuration.test.mjs:107-128.
```

*Réserve* : Le Dockerfile exécute `npm ci` sans --ignore-scripts (AUD-DEP-32).

*Au 20/11* : Info (Info) — Tient et s'améliore : multi-étapes, JRE seul, UID 1001, mem_limit, healthcheck PostgreSQL, ports locaux.

*Au 21/11* : Info (Info) — Tient : multi-étapes, JRE seul, UID 1001, mem_limit, healthcheck PostgreSQL, ports locaux ; le nouveau service Keycloak respecte le même modèle.

**Preuve d'origine**

```text
Dockerfile:6/16/25 trois étapes, :27-29 `useradd --system --uid 1001` + `USER 1001` ; .dockerignore exclut **/.env ; compose.yaml:16/37/68 `restart: unless-stopped`, :27/59/76 mem_limit, :28-33 healthcheck pg_isready + :38-40 depends_on service_healthy, :22-23 et :55-58 publication sur 127.0.0.1 ; ollama sans port publié et profil opt-in. `docker compose -f compose.yaml config -q` -> rc=0.
```

**Impact** — Base d'exploitation adaptée au NAS (mémoire bornée, surface réseau minimale).

**Action** — Aucune.

### AUD-DEP-17 — Configuration par profils correcte : prod sans valeurs par défaut pour la base et l'OIDC, mailer simulé seulement en dev/test, STARTTLS exigé, aucun secret réel versionné

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé par défaut, aucun secret réel versionné.

*Changement depuis le 21/11* : Lignes déplacées ; contenu inchangé.

```text
application.properties:12-14, :405, :144, :149-152, :411/:414 (secret de test limité à %test/%dev) ; `git grep` des motifs ghp_/glpat-/AKIA/clé privée à 01fea6d -> seuls FiltreDonneesTest.java:20-24 et PasserelleIaUnitaireTest.java:280 (jeux d'essai) ; .env.example : secrets vides (lignes 12,15,18,21,60,61,80,83,84).
```

*Au 20/11* : Info (Info) — Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé, aucun secret réel versionné.

*Au 21/11* : Info (Info) — Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé, aucun secret réel versionné.

**Preuve d'origine**

```text
application.properties:10-12 `%prod.quarkus.datasource.*=${ARGOS_DB_*}` sans défaut, :203 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`, :80-83 mock=false en prod, :75 `start-tls=${ARGOS_SMTP_STARTTLS:REQUIRED}`, :209/:212 secret client limité à %test/%dev (valeur de test, non reproduite) ; recherche de motifs de secrets (ghp_, glpat-, AKIA, clés privées) hors tests : seules les expressions de filtrage de FiltreDonnees.java:32-33 ; .gitignore exclut .env et sauvegardes/.
```

**Impact** — Démarrage en échec franc si la base ou l'OIDC manque ; pas de fuite de secret dans le dépôt. Aucun pipeline, donc aucun secret de pipeline à gérer.

**Action** — Aucune.

### AUD-DEP-18 — Licences des dépendances livrées compatibles avec ADR-0021 (MIT/Apache-2.0/BSD) ; licences restrictives uniquement dans l'outillage de build

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : aucune licence copyleft forte ni non commerciale livrée ; 99 dépendances npm livrées admises.

*Changement depuis le 21/11* : Nouvelle dépendance livrée electron-updater (MIT) et ses 16 dépendances d'exécution ; contrôle npm ajouté (AUD-DEP-19).

```text
node scripts/verifier-licences-npm.mjs --paquet argos-ui --paquet argos-desktop (copie, après npm ci) -> rc=0, 83 + 16 dépendances, liste LICENCES_ADMISES (scripts/verifier-licences-npm.mjs:21-24) ; scripts/verifier-licences-serveur.mjs inchangé dans ses listes.
```

*Réserve* : Profil Maven « licences » non rejoué (Maven Central bloqué).

*Au 20/11* : Info (Info) — Tient : aucune dépendance copyleft forte ni non commerciale livrée ; le seul copyleft faible (EPL-2.0) est nommé et décidé.

*Au 21/11* : Info (Info) — Tient : aucune dépendance copyleft forte ni non commerciale livrée ; EPL-2.0 nommée et décidée.

**Preuve d'origine**

```text
LICENSE : PolyForm Internal Use 1.0.0 ; ADR docs/architecture/adr/0021-double-licence.md:57 (permissives acceptées). Analyse du package-lock.json : bundle argos-ui = vue, vue-router (MIT) ; MPL-2.0 (lightningcss 1.33.0), BSD-3, ISC, 0BSD côté Vite ; argos-desktop : MIT 213, ISC 35, BlueOak-1.0.0 9, Python-2.0 1 (argparse), WTFPL 1, tous dans la chaîne electron-builder ; pom.xml:44-150 commente la licence de chaque dépendance (Apache-2.0, MIT, JDOM).
```

**Impact** — Aucune dépendance copyleft forte ni non commerciale n'entre dans le produit distribué.

**Action** — Aucune.

### AUD-DEP-23 — Le script de contrôle des licences rejoué sur des inventaires factices donne les bons codes de sortie (1 GPL ou licence absente, 2 BOM manquant, 0 conforme)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : codes de sortie 1/2/0 vérifiés par les tests ; contrôle documentaire sans erreur.

*Changement depuis le 21/11* : Le rejeu manuel est remplacé par un test automatisé (AUD-DEP-22).

```text
node --test scripts/verifier-licences-serveur.test.mjs (dans la suite : 75/75) ; `node scripts/verifier-docs.mjs` (copie git archive 01fea6d) -> rc=0, « Fichiers Markdown contrôlés : 123 », « Blocs Mermaid : 70 », « aucune erreur » ; verifier-royaume.mjs rc=0 ; verifier-nommage.mjs 803 fichiers, 0 violation.
```

*Réserve* : Rendu Mermaid (--rendu, exécuté en CI) non rejoué ; vrai bom.json non produit (Maven Central bloqué).

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient après refactorisation : codes de sortie 1 (GPL, licence absente), 2 (BOM absent), 0 (conforme, textes copiés).

**Preuve d'origine**

```text
Copie `git archive HEAD` dans /home/claude/audit-maj/build-deps/repo : `node scripts/verifier-docs.mjs` -> rc=0, « Fichiers Markdown contrôlés : 120 », « Blocs Mermaid : 73 », « Résultat : aucune erreur » ; verifier-licences-serveur.mjs sur inventaires factices (/tmp/lic) : GPL-3.0-only et licence absente -> rc=1 avec liste des écarts, BOM absent -> rc=2, Apache-2.0 + MIT avec archives -> rc=0 « Aucune licence absente ni non admise ». Le vrai bom.json n'a pas pu être produit (Maven Central bloqué).
```

**Impact** — Les liens et ancres de la documentation sont cohérents à 54add78 ; le garde-fou de licences se comporte comme documenté.

**Action** — Aucune (voir AUD-DEP-22 pour l'automatisation).

### AUD-DEP-28 — Profil Keycloak local bien assemblé : image épinglée par version exacte, healthcheck conforme à la recette officielle (bash /dev/tcp sur le port de gestion 9000), port lié à 127.0.0.1, mémoire bornée, dépendance facultative d'Argos, scripts montés forcés en LF

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : version exacte 26.7.4 suivie par Renovate, healthcheck port 9000, port lié à 127.0.0.1 par défaut, mémoire bornée, dépendance facultative.

*Changement depuis le 21/11* : Keycloak passe en image construite localement (deploiement/keycloak/Dockerfile, kc.sh build, start --optimized), durcie (read_only, tmpfs) ; healthcheck inchangé.

```text
compose.yaml:94-99, :109-126, :155 (ARGOS_KEYCLOAK_BIND défaut 127.0.0.1), :156, :158-169 ; deploiement/keycloak/Dockerfile:8-19 ; renovate.json:19-30 ; `docker compose --profile idp-local config -q` -> rc=0.
```

*Réserve* : Healthcheck toujours sur `grep -q UP` (compose.yaml:165) plutôt que le statut HTTP 200 ; Keycloak de l'e2e non suivi par Renovate (AUD-DEP-31).

*Au 21/11* : Info (Info) — —

**Preuve d'origine**

```text
compose.yaml:40 keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}, :41 profiles idp-local, :44-46 depends_on postgres healthy, :56 KC_HEALTH_ENABLED, :64 `127.0.0.1:${ARGOS_KEYCLOAK_PORT:-8180}:8080`, :65 mem_limit, :66-77 healthcheck (/dev/tcp/localhost/9000, GET /health/ready, start_period 40s), :86-88 `required: false` ; .gitattributes:1 `*.sh text eol=lf` ; Context7 /keycloak/keycloak 26.8.1 docs/guides/observability/health.adoc (même technique, l'image n'embarque pas de client HTTP) ; `docker compose -f compose.yaml --profile idp-local config -q` -> rc=0.
```

**Impact** — Argos n'attend Keycloak que si le profil est actif, et un Keycloak non prêt ne fait pas démarrer Argos sur une découverte OIDC en échec.

**Action** — Aucune ; à terme, tester `HTTP/1.1 200` plutôt que `grep -q UP` (un corps DOWN listant un contrôle UP passerait) et vérifier que la version de Compose de Container Manager accepte `required: false` (Compose >= 2.20).

### AUD-DEP-34 — CI et Renovate en place et atteignables : actions épinglées par empreinte, jeton en lecture seule, bash -eo pipefail, gates réels (mvn clean verify avec JaCoCo, tests IHM/desktop/scripts, npm audit high, licences npm et serveur, parité de configuration, royaume, migrations, e2e sur l'image)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**
- **Nouveau** le 28/11

**Preuve**

```text
.github/workflows/integration-continue.yml:6-12 (push main, pull_request, contents: read), :14-17 (shell: bash ⇒ -eo pipefail), :38-276 (toutes les actions en @<sha> # vX) ; commandes vérifiées dans le dépôt : scripts npm test/typecheck/build (argos-ui), check (argos-desktop), typecheck/test/test:accessibilite (e2e), profils Maven integration-continue (pom.xml:350) et licences (:385) ; renovate.json:4 helpers:pinGitHubActionDigests ; rejeu local : 75/75 tests de scripts, 92/92 tests desktop, verifier-configuration/royaume/nommage/docs rc=0.
```

**Impact** — Les régressions de configuration, de licences, de migrations et de dépendances (seuil high) sont désormais bloquées avant fusion, sous réserve que les jobs soient requis par la protection de branche.

**Action** — Rendre les jobs requis par une règle de protection de main (non vérifiable ici) ; ajouter scripts/essai-sauvegarde-restauration.sh à la CI (Docker disponible sur ubuntu-latest).

## Documentation — Moyenne (4)

### AUD-DOC-02 — arc42 (§1, §2, §3 D-01, §5.2, §7, §10, §11) et les deux registres de risques divergents ne suivent pas les ADR 0025 à 0033 ; §3 et §7 citent encore Claude API et l'IdP d'entreprise

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Documentation, Architecture · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-ARC-03

**Vérification du 28/11 (01fea6d)** — Corrigé : plus aucune mention de Claude API, FreshRSS, changedetection, IdP d'entreprise ni « lot 1 » dans §1-§3, §5, §7, §10, §11 ; un seul registre des risques, renvoyé par §11.1.

*Changement depuis le 21/11* : 1e96b80 (#74) réécrit arc42 §1, §2, §3 (D-01 : Keycloak, Ollama, DILA, relais SMTP, assistant IA MCP), §4, §5 (D-03 : Ollama, Keycloak), §7 (D-10 en deux vues, D-20), §9, §10 (Q-05 mesurée, Q-06 « Maîtrise de la charge IA »), §11 (renvoi au registre unique) ; risks/register.md devient le registre unique (R-01 à R-18, R-08 clos par ADR-0029, R-18 « Keycloak auto-hébergé » ajouté).

```text
`grep -n -i 'claude\|freshrss\|changedetection\|idp entreprise\|lot 1' docs/architecture/arc42/0{1,2,3,5,7}*.md docs/architecture/arc42/1{0,1}*.md docs/architecture/risks/register.md` : rien (seules restent les lignes historiques d'ADR-0004/0006 en §4 et §9) ; 03-contexte-perimetre.md (D-01 : IDP Keycloak, LLM Ollama) ; 07-vue-deploiement.md:41-77 ; 10-exigences-qualite.md:80, :83 ; 11-risques-dette.md:5 ; risks/register.md:3, :14 (R-08 clos), :24 (R-18).
```

*Réserve* : Révision des risques incomplète : risks/register.md:13 (R-07 licences, mitigation « SBOM, inventaire licences, revue juridique », « Ouvert ») ne cite pas les contrôles livrés scripts/verifier-licences-serveur.mjs et verifier-licences-npm.mjs ; :17 (R-11 injection) ne cite pas le marquage CONTENU_EXTERNE_NON_FIABLE (argos-server/src/main/java/io/argos/mcp/TexteExterne.java:20). Hors des sections visées, arc42 §8 garde des statuts périmés : 08-concepts-transverses.md:381 (« changement `construire-radar-et-standards` non archivé », archivé le 2026-11-16) et :114 ([PROPOSITION — `collecter-veille-rss-web`] alors qu'ADR-0028 est Accepté et le changement archivé).

*Au 20/11* : Ouvert (Moyenne) — —

*Au 21/11* : Ouvert (Moyenne) — Ouvert : arc42 §1-§3, §5.2, §7, §10 Q-06, §11 et le registre des risques restent au cadrage de septembre.

**Preuve d'origine**

```text
docs/architecture/arc42/01-introduction-objectifs.md:11 et :15 (« synthèse assistée par Claude », « seules les capacités du lot 1 sont implémentées ») ; 02-contraintes.md:24-29 (migrations, Claude, FreshRSS, changedetection : « Hypothèse à valider ») contre ADR-0032, 0029, 0028 acceptés ; 03-contexte-perimetre.md:23-30, :47-52 et diagramme D-01 :68-89 (ARGOS → Claude en HTTPS, FreshRSS, changedetection), dernier commit bfb8d3a du 2026-09-24 ; 07-vue-deploiement.md:24, :27 et :53 (« Claude API », conteneur FreshRSS) alors que diagrams/d-11-deploiement-cible.md:20 et :27 montrent Ollama et Keycloak, et compose.yaml:66 le service ollama ; 10-exigences-qualite.md:83 (« Q-06 — Maîtrise des coûts Claude ») ; 11-risques-dette.md:7-16 (R-01 à R-10, dernier commit 4451e06 du 2026-09-24) et risks/register.md:7-24 (R-01 à R-18, libellés différents, tous « Ouvert », dernier commit 5009650 du 2026-09-24) : R-01, R-05, R-08 portent sur Claude ; R-07 (licences), R-11 (injection), R-12 (SSRF) ont des mitigations livrées (scripts/verifier-licences-serveur.mjs, marquage CONTENU_EXTERNE_NON_FIABLE, garde SSRF de io/argos/ingestion/web) jamais reportées. Les §5.1/§5.2 relèvent d'AUD-ARC-03 et ne sont pas repris ici.
[AUD-ARC-03] docs/architecture/arc42/05-vue-blocs.md:85 « INGEST --> SIGNAL » alors que signal importe ingestion 18 fois (signal/web/CollecteWeb.java:18-21) ; :86 « SIGNAL --> VULN » vs vulnintel/Enricher.java:27 import io.argos.signal.SignalChanged ; :92 « IMPACT --> ALERT » et :104 « WORK --> ALERT » vs alerting->impact (5) et alerting->workspace (7) ; :98 « SCM --> INVENTORY » vs inventory->scm (4). Absentes du diagramme : api->catalog (13), api->impact (8), api->alerting, mcp->10 modules dont api, catalog->workspace, impact->catalog, signal->catalog, releaseradar->catalog, inventory->catalog. :19-20 « Claude / Anthropic », « IdP entreprise » alors que le code appelle Ollama (aigateway/ollama/FournisseurOllama.java) et Keycloak (ADR-0027) ; idem 03-contexte-perimetre.md:52-53,73-74.
```

**Impact** — Les vues de contexte, de contraintes, de déploiement et de risques — celles qu'un relecteur sécurité ou un nouvel arrivant lit en premier — décrivent un autre système ; le registre des risques ne sert plus d'outil de pilotage et deux registres portent les mêmes identifiants avec des libellés différents.

**Action** — Aligner §1, §2, §3 (D-01), §7.2/§7.3 (D-10) et §10 Q-06 sur ADR-0026 à ADR-0033 ; fusionner §11.1 et risks/register.md en un seul registre (arc42 §11 renvoyant au registre), réviser chaque risque (statut, preuve de mitigation, risques clos gardés avec leur preuve) et ajouter les risques constatés (performance /me/veille, IdP auto-hébergé).

### AUD-DOC-03 — Le guide de déploiement mène encore à une installation qui ne démarre pas hors du profil idp-local : parcours Synology et parcours PC de base sans étape d'identité, tableau des réglages sans ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL, phrase « l'authentification n'est pas en place » maintenue

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Documentation, Dépendances, Sécurité · **Effort** : M · **Hors sprint**
- **Fusionne** : AUD-DEP-09, AUD-SEC-08

**Vérification du 28/11 (01fea6d)** — Corrigé : les deux parcours imposent l'étape d'identité, la phrase « l'authentification n'est pas en place » a disparu, ARGOS_OIDC_URL et ARGOS_ADMIN_EMAIL figurent au tableau, la parité guide/configuration est gardée par un contrôle testé.

*Changement depuis le 21/11* : 400fa3c (#77) réécrit docs/deploiement/synology-et-pc.md (+326/-131) : section « Choisir le fournisseur d'identité » obligatoire pour les deux parcours, blocs Keycloak fourni / existant, « Première connexion », « Ajouter des utilisateurs » (flux d'appareil, espace, compte Keycloak, invitation, acceptation), tableaux de réglages complets ; scripts/verifier-configuration.mjs contrôle désormais que toute variable ARGOS_* lue en production (serveur ou compose.yaml) est citée par le guide, exécuté en CI.

```text
docs/deploiement/synology-et-pc.md:5, :29-80, :94, :111-155, :208-212 (Synology : mots de passe, bloc d'identité, adresses du NAS), :375-386 ; `grep -rn "n'est pas en place\|Deux conteneurs" docs README.md` : rien ; `node scripts/verifier-configuration.mjs` : code 0 ; 68 des 69 variables ARGOS_* de application.properties et compose.yaml citées par le guide (la 69e, ARGOS_CONSERVATION_REFUS_ACCES, n'apparaît que dans un commentaire d'exemple, application.properties:254) ; tests : scripts/verifier-configuration.test.mjs (node --test, 21 tests verts avec verifier-docs.test.mjs) ; CI : .github/workflows/integration-continue.yml:145-146.
```

*Réserve* : Rejoué seulement sur un PC Windows déjà équipé (synology-et-pc.md:582) : machine vierge, NAS (dont la prise en compte de COMPOSE_PROFILES par Container Manager, :222 « À vérifier sur votre NAS ») et second poste restent à faire (:583, recette R-8 de recette-poste.md:14). « Passer aux comptes de base distincts » impose `--profile idp-local` (:305, :313, :316) même à une installation sur Keycloak existant, où il démarrerait un conteneur keycloak sans ses variables obligatoires.

*Au 20/11* : Ouvert (Moyenne) — —

*Au 21/11* : Partiel (Moyenne) — Partiel : un parcours Keycloak existe désormais pour le PC, mais le parcours principal du PC (:18-23), tout le parcours Synology (:58, aucune étape d'identité), le tableau des réglages (:111-118, 6 lignes, ni ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL) et la phrase « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » (:80) sont restés tels quels ; README.md:85-92 donne toujours `cp .env.example .env` puis `docker compose up -d --build` sans OIDC.

**Preuve d'origine**

```text
docs/deploiement/synology-et-pc.md:21 et :37 (copier .env.example, « changez au minimum ARGOS_DB_PASSWORD »), :88-97 (tableau « Réglages » : 6 lignes, ni ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL), :59 (« Tant que l'authentification (OIDC, ADR-0008) n'est pas en place ») ; or .env.example:15-20 (« Fournisseur d'identité OIDC … — obligatoire », ARGOS_OIDC_URL vide en :17, ARGOS_ADMIN_EMAIL vide en :20) et argos-server/src/main/resources/application.properties:277 (%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}, sans défaut) ; diagrams/d-11-deploiement-cible.md:58 reconnaît que le déploiement de Keycloak n'est décrit nulle part ; même parcours dans README.md:74-77. Sur les 37 variables ARGOS_* lues par application.properties, une seule (ARGOS_DB_PASSWORD) figure dans le guide (comptage : doc-scripts, commande de la section B). Complète AUD-DEP-01 (SMTP, ARGOS_PUBLIC_URL, jetons de forge, quotas), non vérifié ici.
```

**Impact** — Une installation conforme au guide échoue au démarrage en profil prod (émetteur OIDC vide) ; l'utilisateur n'apprend ni qu'il doit fournir un royaume Keycloak, ni comment le créer (clients argos-ui, argos-server, argos-mcp), ni qui est administrateur au premier démarrage. La phrase de la ligne 59 laisse croire à tort qu'Argos est sans authentification.

**Action** — Ajouter au guide une étape « Fournisseur d'identité » (royaume, clients, audience, en s'appuyant sur argos-server/src/test/resources/keycloak/argos-realm.json), compléter le tableau des réglages à partir de .env.example et de compose.yaml, supprimer la ligne 59, et générer (ou contrôler par script) la liste des variables documentées contre application.properties. Correctif immédiat possible, en quelques lignes et sans attendre le sprint 10 : supprimer la phrase de synology-et-pc.md:59 (« tant que l'authentification n'est pas en place ») et ajouter ARGOS_OIDC_URL et ARGOS_ADMIN_EMAIL au tableau des réglages. Ajouter aussi la déclaration des redirections du client argos-ui pour l'adresse du portail déployé (le royaume de test n'autorise que http://localhost:5173/*).

### AUD-DOC-04 — Client desktop : le guide documente désormais l'URL du serveur fixée à l'emballage (argos.apiUrl), mais ni l'émetteur OIDC (VITE_ARGOS_OIDC_ISSUER au build, argos.oidcUrl ou ARGOS_OIDC_URL à l'exécution) ni leur obligation de cohérence ; la section « viser le NAS » est inchangée

- **Statut** : Partiel · **Sévérité** : Moyenne · **Axes** : Documentation · **Effort** : S · **Sprint** : 6
- **Dépend de** : AUD-DEP-29

**Vérification du 28/11 (01fea6d)** — Corrigé : VITE_ARGOS_API_URL, argos.apiUrl/ARGOS_API_URL, argos.oidcUrl/ARGOS_OIDC_URL et la valeur attendue (ARGOS_OIDC_ISSUER_PUBLIC) sont documentés, ainsi que la différence de sens d'ARGOS_OIDC_URL entre poste et serveur.

*Changement depuis le 21/11* : 400fa3c (#77) : section « Client desktop » du guide réécrite avec un tableau des deux adresses (build, emballage, exécution), leur obligation de coïncidence et un exemple d'emballage pour le NAS fixant apiUrl et oidcUrl. VITE_ARGOS_OIDC_ISSUER n'existe plus dans le code (émetteur lu dans GET /api/v1/system/info, ADR-0038).

```text
docs/deploiement/synology-et-pc.md:318-345 (tableau :322-325, coïncidence :329-332, emballage NAS :336-341) ; argos-desktop/src/main.js:28-37 (resoudreReglages : variable, puis champ `argos` du package.json) ; argos-desktop/package.json:16 (dist construit argos-ui) ; `grep -rn VITE_ARGOS_OIDC argos-ui` : rien ; argos-ui/src/api/client.ts:20.
```

*Réserve* : Toujours aucun README dans argos-desktop/ (`ls argos-desktop`), action demandée non faite ; emballage pour une adresse distante et connexion depuis un second poste non rejoués (synology-et-pc.md:583, R-8) ; un installeur emballé pour une autre adresse reçoit des mises à jour emballées pour les adresses par défaut (:345, limite documentée). Relecture du 28/11 : le guide (synology-et-pc.md:318, :345) présente l'adresse du serveur comme remplaçable à l'exécution alors que client.ts:20 la fige au build ; argos-desktop/ n'a toujours pas de README.

*Au 20/11* : Ouvert (Moyenne) — —

*Au 21/11* : Partiel (Moyenne) — Partiel : la cohérence API/CSP est documentée pour le cas « autre port », mais la section « Client desktop » (:165) ne règle toujours que VITE_ARGOS_API_URL pour viser le NAS, et ni VITE_ARGOS_OIDC_ISSUER, ni `argos.oidcUrl`, ni ARGOS_OIDC_URL ne sont mentionnés : un client visant le Keycloak du NAS garde l'émetteur et la CSP sur http://localhost:8180/realms/argos.

**Preuve d'origine**

```text
docs/deploiement/synology-et-pc.md:144 (« construisez l'IHM avec … VITE_ARGOS_API_URL=http://adresse-du-nas:8080, puis npm run dist ») ; argos-desktop/src/main.js:27 (API_URL = process.env.ARGOS_API_URL || 'http://localhost:8080', « sert uniquement à la politique de sécurité du contenu »), :31 (OIDC_URL = process.env.ARGOS_OIDC_URL || 'http://localhost:8180/realms/argos', « les deux doivent coïncider » avec VITE_ARGOS_OIDC_ISSUER), :70 (contentSecurityPolicy(API_URL, OIDC_URL)) ; argos-ui/.env.example:5 (VITE_ARGOS_OIDC_ISSUER) absent du guide ; seules mentions de ARGOS_API_URL/ARGOS_OIDC_URL : e2e/README.md:91 (lancement de test) ; aucun README dans argos-desktop/ ni argos-ui/.
```

**Impact** — Un client desktop construit selon le guide ne peut pas joindre le serveur du NAS (requêtes bloquées par sa CSP) ni s'authentifier auprès du Keycloak réel ; le diagnostic n'est pas documenté.

**Action** — Documenter dans le guide les quatre réglages du desktop (VITE_ARGOS_API_URL, VITE_ARGOS_OIDC_ISSUER au build ; ARGOS_API_URL, ARGOS_OIDC_URL à l'exécution) et leur obligation de cohérence, ou faire dériver les valeurs d'exécution de celles du build ; ajouter un README court à argos-desktop/.

### AUD-DOC-19 — Après l'archivage des cinq derniers changements (#29), le registre des spécifications et sept fiches décrivent encore ces capacités comme « proposées », « non archivées », sans spec de référence ; l'inventaire OpenSpec annonce 11 specs, 5 changements actifs et 11 archivés contre 18, 0 et 16

- **Statut** : Fermé avec réserve · **Sévérité** : Moyenne · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : plus aucune fiche « spec absente », « non archivée » ni « Changement en cours » pour une capacité archivée ; inventaire exact.

*Changement depuis le 21/11* : 1e96b80 (#74) : registre et fiches présentent les capacités archivées comme livrées, specs de référence renseignées, inventaire OpenSpec recompté.

```text
`grep -n 'absente\|capacité non livrée\|Changement en cours\|non archivé' docs/specifications/*.md` hors exploitation-plateforme (deux changements réellement actifs) : seuls des emplois sans rapport (« adresse e-mail absente »…) ; docs/specifications/README.md:38-56 (spec de référence de chaque capacité), :108-111.
```

*Réserve* : Les capacités dont la recette réelle reste à faire se disent « livrée » sans renvoi à R-1 à R-4 : notifications-poste.md:8 (R-1, R-2), serveur-mcp.md:8 (R-3), inventaire-sbom.md:8 et collecte-avis-securite.md:8 (R-4) ; le modèle de fiche prévoit « livrée partiellement » (_modele-fiche-capacite.md:17). Les consignes post-archivage réécrites des tasks.md archivés n'ont pas été rétablies (documents archivés : non retenu).

*Au 21/11* : Ouvert (Moyenne) — —

**Preuve d'origine**

```text
docs/specifications/README.md:38-42 et :47-48 (COL, INV, COR, ABO, PERF, NOT, MCP : spec « absente », statut « proposée » ou « implémentée, non archivée »), :44 (PRF-07 « implémentée, non archivée »), :102-104 (« Specs de référence 11 », « Changements actifs 5 », « Changements archivés 11 ») contre `ls openspec/specs` (18 dossiers, dont abonnements-veille, collecte-avis-securite, correlation-impact, inventaire-sbom, notifications-poste, performance-api-mcp, serveur-mcp créés par 8882fb3), `ls openspec/changes` (archive seul), `ls openspec/changes/archive` (16) ; fiches : collecte-avis-securite.md:6-8, inventaire-sbom.md:6-8, correlation-impact.md:6-8, abonnements-veille.md:6-8 (« absente : capacité non livrée », « changement non archivé »), serveur-mcp.md:6-8, notifications-poste.md:6-8, performance-api-mcp.md:6-8 (« absente tant qu'aucun changement n'est archivé »), profils-veille.md:8 (« Changements en cours … non archivé ») — alors que leur lien « Changement en cours » pointe déjà vers openspec/changes/archive/2026-11-20-… ; les consignes post-archivage ont été réécrites par la même substitution au lieu d'être exécutées : 2026-11-20-automatiser-chaine-veille/tasks.md:145, 2026-11-20-exposer-serveur-mcp/tasks.md:77, 2026-11-20-notifier-sur-le-poste/tasks.md:80 (« remplacer … les liens vers `/openspec/changes/archive/2026-11-20-…/` par la spec principale ou le dossier d'archive »). Le contrôle documentaire ne détecte rien (liens valides) : `node scripts/verifier-docs.mjs` « aucune erreur ».
```

**Impact** — Le registre de couverture, qui fait foi pour l'avancement, donne pour sept des dix-huit capacités un état contraire au dépôt : un lecteur (ou un agent OpenSpec qui lit les fiches) croit la collecte, l'inventaire, la corrélation, les abonnements, les notifications, la MCP et la performance encore en cours et sans spec de référence, et peut rouvrir un changement sur une capacité déjà archivée.

**Action** — Pour les sept capacités : renseigner la spec de référence (/openspec/specs/<capacité>/spec.md), passer le statut à « livrée » (ou « livrée partiellement » avec renvoi aux recettes R-1 à R-5), remplacer « Changement en cours » par « Changements archivés » ; corriger l'inventaire (18, 0, 16) ; dans les tasks.md archivés, rétablir le texte des consignes post-archivage ; ajouter à verifier-docs une règle « une fiche qui dit spec absente alors que openspec/specs/<capacité>/spec.md existe est une erreur ».

## Documentation — Faible (15)

### AUD-DOC-01 — Les points d'entrée (README racine, docs/architecture/README.md, openspec/config.yaml) décrivent encore le cadrage de septembre : socle « sans fonctionnalité métier », Claude derrière un AI Gateway, questions ouvertes déjà tranchées par des ADR acceptés

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 21/11) · **Axes** : Documentation · **Effort** : S · **Sprint** : 6

**Vérification du 28/11 (01fea6d)** — Corrigé pour l'essentiel : plus de « socle sans fonctionnalité métier », de Claude/AI Gateway, de FreshRSS séparé ni de baseline « active » ; seul le paragraphe Licence du README n'a pas été touché.

*Changement depuis le 21/11* : 400fa3c (#77) réécrit Statut, Stack, Arborescence, Démarrage local et Mission du README racine ; 1e96b80 (#74) réécrit docs/architecture/README.md (synthèse « POC fonctionnel », choix fixés par ADR acceptés, ADR proposés listés, questions ouvertes réelles, baselines présentées comme documents datés) et le contexte d'openspec/config.yaml.

```text
README.md:11 (« POC fonctionnel », ADR-0025), :15-17, :21-27 (Keycloak, Ollama, Flyway ADR-0032) ; docs/architecture/README.md:5 (baselines « documents datés »), :9-30, :70-76 ; openspec/config.yaml:7-9 (« POC fonctionnel … rien n'est [EXISTANT] sans preuve versionnée ») ; `grep -rn 'baseline.*active' docs README.md openspec` : rien hors archives.
```

*Réserve* : README.md:157-159 décrit toujours la licence comme permettant « l'utilisation personnelle et interne à une entreprise » sans renvoi à ADR-0025:57 (« elle ne convient pas telle quelle à un produit ouvert à des tiers ») ni à ADR-0021 : l'action demandée sur la section Licence n'est pas faite. baseline-v2.4.md:4 garde « Statut : proposition mise à jour à valider » (document daté, présenté comme tel par l'index : non retenu comme constat). Relecture du 28/11 : la section Licence du README (README.md:157-159) n'est pas corrigée.

*Au 20/11* : Ouvert (Moyenne) — —

*Au 21/11* : Ouvert (Moyenne) — Ouvert, légèrement aggravé : les cadrages de septembre subsistent mot pour mot et une affirmation de plus est devenue fausse après #29.

**Preuve d'origine**

```text
README.md:13 (« socle applicatif sans fonctionnalité métier »), README.md:90-91 et 106-107 (MKP, Claude/AI Gateway, Claude API facturée), README.md:133 (« outil de migration à confirmer par ADR ») alors qu'ADR-0032 est Accepté, README.md:136 et 138 (connecter OSV, prototyper le MCP : à faire) alors que argos-server/src/main/java/io/argos/ingestion/osv/ et io/argos/mcp/ (8 @Tool) existent, README.md:144 (licence « interne à une entreprise ») sans le renvoi d'ADR-0025:50 qui la juge inadaptée à un produit ouvert ; docs/architecture/README.md:10 (« capacités du lot 1 » seulement), :20-21 et :25 (Claude/AI Gateway, FreshRSS séparés, validation MKP) contredits par ADR-0029, ADR-0028 et ADR-0030 acceptés et par argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java, :41-42 (budget Claude API), :51-56 (4 ADR acceptés cités sur 15), :84 (« Priorités avant le premier commit applicatif »), :99-102 (IdP, Claude, FreshRSS, Flyway : tranchés par ADR-0027, 0029, 0028, 0032) ; openspec/config.yaml:6-7 (« ARGOS est en phase de conception… socle applicatif sans fonctionnalité métier »), contexte injecté dans chaque artefact OpenSpec ; README.md:13 et docs/architecture/README.md:5 déclarent « active » la baseline v2.4, dont baseline-v2.4.md:4 dit « proposition mise à jour à valider » et :14 prévoit la VM interne abandonnée (ADR-0026).
```

**Impact** — Un lecteur (ou un agent OpenSpec) qui part des points d'entrée se fait une image fausse du produit : pas d'IA locale, pas de collecte intégrée, rien de livré. openspec/config.yaml oriente chaque nouvel artefact vers « rien n'est existant ».

**Action** — Réécrire Statut, Mission, IA, Prochaines étapes et Licence du README racine ; dans docs/architecture/README.md, remplacer la synthèse, la liste des ADR acceptés et les questions ouvertes par des renvois à arc42 §9 ; mettre à jour le contexte d'openspec/config.yaml ; marquer baseline-v2.4 comme historique (datée) au lieu d'« active ».

### AUD-DOC-05 — README racine, démarrage local : Node.js 22.12 annoncé contre ^22.18.0 exigé, Docker présenté comme requis pour les seuls tests alors que quarkus:dev démarre Keycloak, aucune mention de la connexion (royaume de test, comptes), e2e réduit au « portail web »

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : Node.js 22.18 (^22.18.0 || >=23.6.0), Docker présenté comme requis par quarkus:dev (Keycloak des Dev Services), paragraphe « Connexion en développement » (royaume de test, alice administratrice, mot de passe = identifiant), e2e décrit comme portail web, client desktop et serveur MCP.

*Changement depuis le 21/11* : 400fa3c (#77) : Démarrage local du README réécrit.

```text
README.md:50, :59-61, :77, :81 ; argos-ui/package.json:8 et e2e/package.json:9 ("^22.18.0 || >=23.6.0").
```

*Réserve* : README.md:40 cite la campagne de performance mais pas celle d'accessibilité (test:accessibilite) : détail.

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Ouvert : Node.js 22.12 annoncé, Docker toujours présenté comme requis pour les seuls tests, pas de paragraphe de connexion, e2e réduit au portail web.

**Preuve d'origine**

```text
README.md:42 (« Node.js 22.12 ou plus ») contre argos-ui/package.json:8 et e2e/package.json:9 ("node": "^22.18.0 || >=23.6.0") et e2e/README.md:7 (« Node.js 22.18 ou plus ») ; README.md:50 (« tests : mvn verify (Docker requis : base jetable) ») alors que application.properties:284-289 démarre en %dev un Keycloak des Dev Services (port 8180, royaume src/test/resources/keycloak/argos-realm.json) ; aucune mention dans README.md:40-68 de la page de connexion, du compte alice (application.properties:305) ni de VITE_ARGOS_OIDC_ISSUER (argos-ui/.env.example:5) ; README.md:35 et :68 (« Tests de bout en bout du portail web ») alors que e2e/playwright.config.ts:65-77 définit aussi les projets desktop et mcp, et e2e/package.json les campagnes test:accessibilite et test:performance.
```

**Impact** — Un développeur sur Node 22.12 à 22.17 voit npm refuser l'installation ; sans Docker, quarkus:dev échoue sans que le README l'annonce ; l'IHM démarrée affiche un écran de connexion dont les identifiants ne sont documentés que dans e2e/README.md:33.

**Action** — Corriger la version de Node, signaler Docker pour quarkus:dev, ajouter un paragraphe « Se connecter en local » (royaume de test, comptes, mot de passe = identifiant) et décrire e2e comme « portail web, desktop, MCP, campagnes accessibilité et performance ».

### AUD-DOC-06 — Le diagramme D-19 (arc42 §6.6, veille réglementaire) ne se rend pas : un « ; » dans un message de séquence casse l'analyse Mermaid, et le contrôle documentaire passe parce que le rendu est facultatif

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : D-19 se rend, et le rendu est désormais un gate de CI atteignable.

*Changement depuis le 21/11* : 6bc8003 (#75) : « ; » de D-19 remplacé (arc42 06-vue-execution.md:291), D-01 et le diagramme de veille-reglementaire.md corrigés ; verifier-docs --rendu échoue sans mmdc et rend fichier par fichier ; la CI installe Mermaid CLI 12.2.0 et exécute `node scripts/verifier-docs.mjs --rendu` ; le README le prescrit.

```text
docs/architecture/arc42/06-vue-execution.md:291 (« source DEGRADE, repris … ») ; `node scripts/verifier-docs.mjs --rendu` dans /home/claude/audit-maj3/build-doc (copie de 01fea6d, mmdc 11.16.0) : « Fichiers Markdown contrôlés : 123 », « Blocs Mermaid : 70 », « Rendu Mermaid : demandé », « Résultat : aucune erreur », code 0 ; .github/workflows/integration-continue.yml:135-140 (job déclenché sur toute pull request et tout push sur main, :6-9) ; README.md:97, :103 ; test : scripts/verifier-docs.test.mjs.
```

*Réserve* : Les prescriptions n'ont pas suivi : ADR-0020:73, openspec/config.yaml:38 et :43, openspec/schemas/detailed-uml/templates/tasks.md:18 et :22 et arc42 08-concepts-transverses.md:332 (ligne « Outils » de la CI) écrivent `node scripts/verifier-docs.mjs` sans --rendu. Aucune règle statique contre le « ; » : la même erreur subsiste dans quatre diagrammes archivés (AUD-DOC-28).

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Ouvert : le rendu Mermaid de D-19 échoue toujours.

**Preuve d'origine**

```text
docs/architecture/arc42/06-vue-execution.md:290 (« Reg->>Persist: jour EN_ECHEC, source DEGRADE ; repris … ») ; `node scripts/verifier-docs.mjs --rendu` dans une copie à 54add78 (mmdc 11.16.0) : « ERREUR docs/architecture/arc42/06-vue-execution.md:273 rendu Mermaid en échec : Error: Parse error on line 17 » puis « Résultat : 1 erreur(s) » ; le bloc extrait se rend après remplacement du « ; » par une virgule ; sans l'option : « Résultat : aucune erreur » ; ADR-0020:66 et openspec/config.yaml:36 ne prescrivent que l'exécution sans --rendu. Introduit par e73ec4d (2026-11-18).
```

**Impact** — Le seul diagramme d'exécution de la veille réglementaire s'affiche en erreur sur GitHub et dans tout rendu Mermaid, alors qu'ADR-0005 fait de Mermaid la source de vérité des diagrammes.

**Action** — Remplacer le « ; » de la ligne 290 ; faire de --rendu le mode prescrit par ADR-0020 et par les tâches type du schéma lorsque mmdc est disponible (ou ajouter au script la détection des « ; » dans les messages de sequenceDiagram).

### AUD-DOC-07 — Le registre des spécifications (paragraphe du lot 18) et la fiche profils-veille contredisent encore la livraison : « Tâche 3.1 décochée … P-13 à valider » contre une tâche cochée, « pas de capacité regintel » contre une veille réglementaire livrée qui sert le profil expert-metier

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6

**Vérification du 28/11 (01fea6d)** — Partiel : trois des cinq passages sont corrigés ; le paragraphe du lot 18 du registre et la fiche profils-veille contredisent toujours le code et les tâches cochées.

*Changement depuis le 21/11* : 1e96b80 (#74) : inventaire des diagrammes renvoyé à D-01 à D-22 (docs/specifications/README.md:98), D-13 et diagrams/README.md:36 à V1-V28, statuts des capacités du registre passés à « livrée ».

```text
Corrigés : docs/specifications/README.md:98 (D-01 à D-22), docs/architecture/diagrams/README.md:36 (V1 à V28) = d-13-modele-physique.md:3 et `ls db/migration` (28 fichiers), registre :38-56 (statuts « livrée »). Restants : docs/specifications/README.md:206 (« Tâche 3.1 décochée : la contrainte INV-C3 n'est pas créée ([PROPOSITION] P-13, à valider) », « [PROPOSITION] P-18 ») contre openspec/changes/archive/2026-11-20-automatiser-chaine-veille/tasks.md:62 (« - [x] 3.1 ») ; docs/specifications/profils-veille.md:10 et :90 (« pas de capacité `regintel` ») contre veille-reglementaire.md:10 (« livrée ») et argos-server/src/main/java/io/argos/alerting/RappelsReglementaires.java:43 (PROFIL = "expert-metier").
```

*Réserve* : Aucun contrôle automatique de la concordance tâche citée / case de tasks.md (action non faite).

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Ouvert, aggravé sur l'inventaire des diagrammes.

**Preuve d'origine**

```text
docs/specifications/README.md:199 (« Tâche 3.1 décochée : la contrainte INV-C3 n'est pas créée ([PROPOSITION] P-13, à valider) », « [PROPOSITION] P-18 ») contre openspec/changes/automatiser-chaine-veille/tasks.md:61 (« - [x] 3.1 ») et proposal.md:161 (« P-13 ([DÉCISION] K1) ») et :166 (« P-18 ([DÉCISION] K2) ») ; docs/specifications/README.md:92 (« D-01 à D-13 ») contre docs/architecture/diagrams/README.md:24-42 (D-01 à D-19) ; diagrams/README.md:36 (D-13 « V1 à V17 et V20 ») contre d-13-modele-physique.md:3 (« V1__socle.sql à V20 ») et :136, :189 (colonnes V18, V19) ; docs/specifications/profils-veille.md:8 et :88 (« pas de capacité regintel ») contre veille-reglementaire.md:8 (« livrée ») et argos-server/src/main/java/io/argos/alerting/RappelsReglementaires.java:41 (PROFIL = "expert-metier") ; docs/specifications/README.md:38-41 et :48 (COL, INV, COR, ABO, MCP « proposée ») contre :47 (NOT « implémentée, non archivée ») alors que les fiches (collecte-avis-securite.md:8, serveur-mcp.md:8…) disent le code implémenté et testé, et que leur ligne 6 dit « capacité non livrée ».
```

**Impact** — Le registre de couverture, qui fait foi pour l'avancement, donne pour certaines exigences un état contraire à celui du changement OpenSpec ; un lecteur ne sait pas si INV-C3 est décidé ni si la MCP est livrée.

**Action** — Corriger les cinq passages ; ajouter au vocabulaire du modèle de fiche (_modele-fiche-capacite.md:17) un statut « implémentée, non archivée » et l'appliquer aux cinq capacités concernées ; faire vérifier par verifier-docs que le statut d'une tâche citée dans le registre correspond à sa case dans tasks.md.

### AUD-DOC-08 — Neuf ADR acceptés (0023, 0024, 0027 à 0033) gardent une décision rédigée comme une proposition, et ADR-0029 se dit encore subordonné à sa propre acceptation

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6

**Vérification du 28/11 (01fea6d)** — Corrigé dans les sections Décision ; deux notes conditionnelles caduques subsistent.

*Changement depuis le 21/11* : 1e96b80 (#74) : décisions des ADR 0023, 0024, 0027 à 0033 rédigées « Option X retenue » ; en-têtes et statuts cités à jour ; ADR-0029:13-18 cite ADR-0004 comme « Remplacé par le présent ADR ».

```text
`node /home/claude/audit-maj/doc-scripts/statuts-adr.mjs /home/claude/audit-maj3/build-doc` : plus aucune ligne DECISION_REDIGEE_COMME_PROPOSITION pour un ADR accepté (restent ADR-0004/0006 remplacés et 0007/0040/0041 proposés, attendus) ; docs/architecture/adr/0023-…md:63, 0027-…md:51, 0029-…md:50, 0033-…md:62 (« Option … retenue »).
```

*Réserve* : docs/architecture/adr/0029-passerelle-ia-ollama-local.md:88 (« ADR-0004 reste la référence tant que le présent ADR n'est pas accepté ; l'acceptation le marquera Remplacé ») et 0030-veille-reglementaire-expert-metier.md:95 (même phrase pour ADR-0018) : clauses caduques que l'action demandait de retirer. Relecture du 28/11 : les clauses conditionnelles caduques restent dans ADR-0029:88 et ADR-0030:95.

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Ouvert : neuf ADR acceptés gardent « Proposition » ; ADR-0029 se dit toujours subordonné à sa propre acceptation.

**Preuve d'origine**

```text
docs/architecture/adr/0027-keycloak-idp-auto-heberge.md:44, 0028:43, 0029:43, 0030:43, 0033:55 (« **Proposition : option B** »), 0031:66 et 0032:53 (« Proposition : **option A** »), 0023:56 et 0024:63 (« Option A, **proposée** ») avec « Statut : Accepté » en ligne 3 ; 0029-passerelle-ia-ollama-local.md:11 (« ADR-0004 (Proposé) »), :45 et :81 (« ADR-0004 reste la référence tant que le présent ADR n'est pas accepté ») alors qu'ADR-0004 est « Remplacé par ADR-0029 » ; relevé par doc-scripts/statuts-adr.mjs. Distinct d'AUD-ARC-14 (ADR restés « Proposé »).
```

**Impact** — Le corps de l'ADR, seul lu par qui ouvre le fichier, contredit son en-tête ; ADR-0029 laisse croire qu'ADR-0004 fait encore référence.

**Action** — À l'acceptation, remplacer « Proposition : » par « Décision : » et retirer les clauses conditionnelles devenues caduques, en notant la date d'acceptation (modification d'acceptation, pas réécriture rétroactive au sens de arc42 §9.2).

### AUD-DOC-10 — Le contrôle documentaire laisse passer des chemins périmés : références en code en ligne vers trois changements archivés (ADR-0027, ADR-0028), README d'argos-server et d'e2e hors périmètre, aucune exécution automatique

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : périmètre étendu (102 → 123 fichiers, argos-server/README.md et e2e/README.md compris), chemins cités vérifiés (essai : un chemin inexistant injecté dans docs/architecture/README.md est signalé « chemin cité introuvable »), gate de CI atteignable.

*Changement depuis le 21/11* : 6bc8003 et d247150 (#75) : verifier-docs contrôle les README de chaque sous-projet et les chemins du dépôt cités en code en ligne ; chemins d'ADR-0027:74 et ADR-0028:78-80 repointés ; exécution en CI (avec --rendu).

```text
scripts/verifier-docs.mjs:4-5, :26-36, :184 ; `node scripts/verifier-docs.mjs` : « Fichiers Markdown contrôlés : 123 », aucune erreur ; `grep -rnoE 'openspec/changes/[a-z0-9-]+'` hors archives et changements actifs : rien ; .github/workflows/integration-continue.yml:137-140 ; test : scripts/verifier-docs.test.mjs (+34 lignes dans 6bc8003).
```

*Réserve* : Deux trous assumés mais non signalés comme tels au lecteur : l'ancre d'un chemin cité (`Classe.java#methode`) est retirée avant contrôle (scripts/verifier-docs.mjs:157), ce qui laisse passer une méthode de test supprimée (AUD-DOC-27) ; tout chemin sous docs/audit/ est exempté (CHEMINS_HORS_DEPOT, :36), alors que 20 lignes « Source » de 10 specs principales le citent (AUD-DOC-29). Les changements archivés restent hors périmètre (EXCLUS, :37-40).

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Ouvert : mêmes trous de contrôle ; le périmètre réellement contrôlé a diminué avec l'archivage.

**Preuve d'origine**

```text
docs/architecture/adr/0027-keycloak-idp-auto-heberge.md:74 (`openspec/changes/gerer-comptes-et-acces/specs/comptes-acces/spec.md`), 0028-collecteur-veille-web-integre.md:78 et :80 (`openspec/changes/collecter-veille-rss-web/…`) : dossiers déplacés sous openspec/changes/archive/2026-11-1x-… ; scripts/verifier-docs.mjs:19-24 (DOSSIERS = docs, openspec ; FICHIERS_RACINE = README.md : argos-server/README.md et e2e/README.md, 190 lignes, ne sont pas contrôlés) et :52-57 (le code entre accents graves est retiré avant le contrôle des liens) ; aucun dossier .github/ ni autre CI (voir AUD-TST-01). Contrôle complémentaire doc-scripts/liens.mjs sur les 201 .md : 0 lien cassé ; doc-scripts/chemins.mjs : ces trois chemins sont les seuls chemins de dépôt périmés hors faux positifs.
```

**Impact** — Les renvois des ADR vers les exigences qu'ils tracent sont morts ; la règle « repointer les liens après archivage » (guide-openspec-detailed-uml.md:74) n'est pas outillée pour les chemins écrits en code.

**Action** — Repointer les trois chemins vers openspec/specs/… ; étendre verifier-docs aux README de sous-projets et aux chemins de dépôt écrits en code en ligne ; l'exécuter dans la CI dès qu'elle existe.

### AUD-DOC-11 — Reliquat d'outillage Archgate : un second dossier d'ADR avec l'exemple non rempli GEN-001 et une documentation de règles de lint renvoyant à un fichier absent

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé : plus d'exemple non rempli ni de renvoi à un fichier absent ; rôle d'Archgate documenté.

*Changement depuis le 21/11* : 1e96b80 (#74) : .archgate/adrs/GEN-001-example.md et .archgate/lint/ supprimés ; .archgate/config.json pointe sur docs/architecture/adr (paths.adrs) ; en-tête YAML ajouté aux ADR ; outillage documenté dans l'index des ADR et dans .archgate/adrs/README.md.

```text
`ls -R .archgate` : config.json, adrs/README.md seuls ; .archgate/config.json:3 ("adrs": "docs/architecture/adr") ; docs/architecture/adr/README.md:115-133 (« Outillage Archgate ») ; contrôle par script : les 42 ADR ont un en-tête `id: ADR-NNNN` et un `title` identiques à leur titre (aucun écart).
```

*Réserve* : Le commit annonce « en-tête YAML ajouté aux 41 ADR » ; ADR-0042, ajouté ensuite par 36d8d54, l'a aussi. Aucun test ne vérifie la concordance en-tête / titre.

*Au 20/11* : Ouvert (Faible) — —

*Au 21/11* : Ouvert (Faible) — Ouvert.

**Preuve d'origine**

```text
.archgate/adrs/GEN-001-example.md:1-12 (« Example Architecture Decision … Replace this with a real architecture decision ») ; .archgate/lint/README.md:15 (« Create .archgate/lint/oxlint.js ») : fichier absent ; .archgate/config.json ; un seul commit, 7e4eb35 (2026-11-15, « initialiser OpenSpec et Archgate ») ; aucune mention d'Archgate dans docs/ ni dans les ADR.
```

**Impact** — Deux emplacements d'ADR coexistent dont l'un est un gabarit anglais vide ; confusion pour un contributeur ou un agent qui chercherait les décisions.

**Action** — Supprimer .archgate/ ou documenter son rôle par rapport à docs/architecture/adr/ (ADR-0020) et y retirer l'exemple.

### AUD-DOC-20 — arc42 §10 (Q-05) et le scénario QS-06 donnent encore GET /api/v1/me/veille hors cible (p95 = 533,8 ms, 15 opérations sur 16) alors que le rapport de campagne du 2026-11-20 mesure 85 ms et 16 opérations sur 16 dans la cible

- **Statut** : Fermé avec réserve · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé dans arc42 §10 et les scénarios ; chiffres concordants avec le rapport généré.

*Changement depuis le 21/11* : b8af729 (campagne du 2026-11-27) et 1e96b80 (#74) : Q-05 et QS-06 reprennent la campagne du 2026-11-27 (17 opérations sur 17, Ma veille p95 = 185,5 ms).

```text
docs/architecture/arc42/10-exigences-qualite.md:80 et docs/architecture/quality/scenarios.md:12 (17/17, p95 185,5 ms) = docs/architecture/quality/mesure-performance-api-mcp.md:9-20, :35-51 (17 lignes, « toutes les opérations dans la cible »).
```

*Réserve* : La phrase du lot 18 reste au présent : docs/specifications/README.md:206 (« `GET /api/v1/me/veille` reste hors cible (p95 = 533,8 ms …) ») — action non faite.

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
docs/architecture/arc42/10-exigences-qualite.md:80 (« **hors cible** : GET /api/v1/me/veille, p95 = 533,8 ms … [EXISTANT] ») et docs/architecture/quality/scenarios.md:12 (« GET /api/v1/me/veille hors cible (p95 = 533,8 ms) ») contre docs/architecture/quality/mesure-performance-api-mcp.md:12 (révision 7e5e36d) et :20 (« toutes les opérations dans la cible ») et :32 (p95 = 85 ms), et docs/specifications/README.md (« Couverture du lot 19 » : « p95 = 85 ms, seize opérations sur seize ») ; même paragraphe du lot 18 resté au présent : docs/specifications/README.md:199 (« reste hors cible »). Introduit par afcb8f6 (campagne du 2026-11-20), qui a mis à jour le rapport et la fiche mais pas arc42 ni les scénarios.
```

**Impact** — La vue qualité d'arc42, lue par un relecteur, signale un dépassement corrigé et marqué [EXISTANT] ; le chiffre contredit le rapport généré et la fiche de capacité.

**Action** — Mettre à jour 10-exigences-qualite.md:80 et scenarios.md:12 avec la campagne du 2026-11-20 (révision, 16/16, p95 de « Ma veille » 85 ms) en gardant la mesure du 2026-11-19 comme historique ; écrire au passé la phrase du lot 18 dans docs/specifications/README.md:199.

### AUD-DOC-23 — La PR #28 n'est pas reportée dans l'architecture : D-11 dit Keycloak hors de compose.yaml, D-11 et le guide (« Deux conteneurs démarrent ») ignorent keycloak, le README ne cite ni deploiement/ ni le profil idp-local

- **Statut** : Fermé · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Corrigé.

*Changement depuis le 21/11* : 400fa3c et 1e96b80 : D-11 décrit le conteneur keycloak du profil idp-local, §7 et D-10 aussi ; le guide remplace « Deux conteneurs » par le tableau des quatre services et profils ; le README cite deploiement/ et le profil idp-local.

```text
docs/architecture/diagrams/d-11-deploiement-cible.md:20, :28, :59 ; docs/architecture/arc42/07-vue-deploiement.md:46-51, :76 ; docs/deploiement/synology-et-pc.md:7-12 ; README.md:41, :43, :109-118.
```

*Au 21/11* : Ouvert (Faible) — —

**Preuve d'origine**

```text
docs/architecture/diagrams/d-11-deploiement-cible.md:55 (« Conteneurs : argos, postgres, ollama ») et :58 (« Keycloak lu par ARGOS_OIDC_URL ; son déploiement sur le NAS n'est pas décrit dans compose.yaml ») contre compose.yaml:39-77 (service keycloak, profil idp-local, base créée par deploiement/postgres/creer-base-keycloak.sh) ; docs/deploiement/synology-et-pc.md:5-10 (« Deux conteneurs démarrent », tableau postgres/argos) ; README.md:28-38 (arborescence sans deploiement/) et :85-92 (Docker sans idp-local) ; docs/architecture/arc42/07-vue-deploiement.md inchangé depuis 54add78.
```

**Impact** — Un lecteur de la vue de déploiement conclut qu'il doit fournir lui-même Keycloak ; l'option livrée par #28 n'est découvrable que dans une sous-section du guide.

**Action** — Mettre à jour D-11 (:55, :58) et §7 avec le service keycloak du profil idp-local ; dans le guide, remplacer « Deux conteneurs » par le tableau des services et profils (postgres, argos, keycloak/idp-local, ollama/ia) ; ajouter deploiement/ à l'arborescence du README et un renvoi au profil idp-local dans « Argos complet sous Docker ».

### AUD-DOC-24 — ADR-0042 (Accepté, distribution du client desktop) manque à arc42 §9.1 : 41 lignes pour 42 ADR

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6
- **Nouveau** le 28/11

**Preuve**

```text
`node /home/claude/audit-maj/doc-scripts/statuts-adr.mjs /home/claude/audit-maj3/build-doc` : « ADR-0042 fichier=accepté index=accepté arc42=(aucun) <-- ECART » ; docs/architecture/arc42/09-decisions.md:51 (dernière ligne ADR-0041) ; docs/architecture/adr/README.md:63 (ADR-0042 « Accepté ») ; ajouté par 36d8d54 (#78) sans mise à jour de §9 ; verifier-docs ne compare pas les statuts.
```

**Impact** — La vue des décisions d'arc42 omet la décision qui régit la publication et la mise à jour automatique du client desktop ; régression de la protection décrite par AUD-DOC-14.

**Action** — Ajouter la ligne ADR-0042 à 09-decisions.md ; intégrer statuts-adr (statut fichier = index = §9.1) à verifier-docs.

### AUD-DOC-25 — arc42 §8 a pris du retard sur le code : tableau des migrations sans V25 ni V26 (« état au 2026-11-24 »), « 15 tâches » planifiées annoncées pour 16

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6
- **Nouveau** le 28/11
- **Dépend de** : AUD-QUA-25

**Preuve**

```text
docs/architecture/arc42/08-concepts-transverses.md:242 (« état au 2026-11-24 … la prochaine migration porte le numéro V29 »), :271-273 (lignes V24, V27, V28 : V25__file_enrichissement.sql et V26__empreinte_dernier_import_depot.sql absentes) contre `ls argos-server/src/main/resources/db/migration` (28 fichiers) ; :148 (« 15 tâches ») contre le tableau :150-167 (16 lignes) et 16 identités @Scheduled (dont vulnintel-enrichissement et vulnintel-rattrapage, io/argos/vulnintel/TraitementEnrichissements.java).
```

**Impact** — L'inventaire que l'arc42 déclare tenir à jour à la place d'ADR-0032 et d'ADR-0031 (décision du porteur du 2026-11-19) est incomplet : retour arrière et propriété de la file d'enrichissement (V25) non documentés ; régression d'AUD-DOC-15.

**Action** — Ajouter les lignes V25 et V26 (contenu, changement, retour arrière), dater l'état, corriger le décompte ; contrôler par script que chaque fichier de db/migration a sa ligne dans §8.7 et chaque identité @Scheduled la sienne.

### AUD-DOC-26 — Versions citées par les README de sous-projets dépassées par Renovate : Electron 45.3.2 et Playwright 1.61.1 (e2e), exec-maven-plugin 3.6.4 (serveur) contre 45.4.1, 1.65.1 et 3.7.0

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6
- **Nouveau** le 28/11

**Preuve**

```text
e2e/README.md:7 (« binaire Electron 45.3.2 ») contre argos-desktop/package-lock.json:1450-1451 (45.4.1, adeed23) ; e2e/README.md:11 (« Playwright 1.61.1 (Chromium révision 1228) ») contre e2e/package-lock.json:20-21 (1.65.1, 578d3a4) ; argos-server/README.md:28 (« exec-maven-plugin 3.6.4 ») contre argos-server/pom.xml:33 (3.7.0) ; renovate.json ne touche pas les README et verifier-docs ne compare aucune version.
```

**Impact** — Le prérequis du lancement E2E désigne un binaire Electron qui n'est plus installé ; la documentation de la chaîne de licences cite une version de plugin qui n'est pas celle exécutée. Chaque mise à jour Renovate rouvrira l'écart (régression d'AUD-DOC-16).

**Action** — Retirer les numéros de version des README (renvoyer aux fichiers de verrouillage et au pom) ou les faire contrôler par un script (version citée = version verrouillée).

### AUD-DOC-27 — La fiche comptes-acces cite comme preuve de QS-02 une méthode de test supprimée par la PR #79 (ParcoursIaTest#iag02S1DonneeConfidentielleRefuseeEtJournalisee) ; verifier-docs ne contrôle pas les ancres des chemins cités

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6
- **Nouveau** le 28/11

**Preuve**

```text
docs/specifications/comptes-acces.md:198 (`argos-server/src/test/java/io/argos/chain/ParcoursIaTest.java#iag02S1DonneeConfidentielleRefuseeEtJournalisee`) ; `grep -rn iag02S1 argos-server/src/test` : seule PasserelleIaUnitaireTest.java:219 ; méthode retirée par b29e815 (`git log -S`) ; scripts/verifier-docs.mjs:157 (« ancre et numéro de ligne retirés ») ; essai : une ancre `ParcoursIaTest.java#methodeInexistante` injectée dans docs/architecture/README.md n'est pas signalée. Contrôle des 18 références `Classe#méthode` de docs/, openspec/specs et des README : celle-ci est la seule rompue.
```

**Impact** — Une preuve de couverture du scénario QS-02 pointe vers un test qui n'existe plus ; le passage de tests vers des tests sans Docker (sprint 11) peut en rompre d'autres sans alerte.

**Action** — Repointer la référence (ou la retirer) ; dans verifier-docs, pour un chemin cité `*.java#nom`, vérifier que le fichier contient la méthode `nom`.

### AUD-DOC-28 — Quatre diagrammes de conception de changements archivés les 25-27 novembre ne se rendent pas, par le même « ; » que D-19 : le correctif n'a pas été généralisé et l'archive échappe au contrôle

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6
- **Nouveau** le 28/11

**Preuve**

```text
`node /home/claude/audit-maj2/doc-scripts2/rendu-hors-perimetre.mjs /home/claude/audit-maj3/build-doc /tmp/rhp` (mmdc 11.16.0) : « blocs hors périmètre=63 erreurs=4 » : openspec/changes/archive/2026-11-25-durcir-autorisations-et-debits/design.md:57, 2026-11-26-extraire-noyau-commun/design.md:137 (« SET LOCAL ROLE argos_app ; set_config × 4 »), 2026-11-27-accelerer-requetes-chaudes/design.md:89, 2026-11-27-retenir-importer-et-paginer/design.md:102 (« DELETE occurrences du dépôt ; INSERT … ») ; scripts/verifier-docs.mjs:37-40 (archives exclues) ; docs/architecture/diagrams/README.md:59 (« les diagrammes de conception propres à un changement (DC-*) restent dans son design.md »).
```

**Impact** — Les diagrammes DC, dont l'inventaire désigne le design.md comme seul emplacement, ne s'affichent pas pour quatre changements récents ; ils ont été archivés avant que le rendu ne devienne un gate (6bc8003, 27/11 22:37).

**Action** — Remplacer les « ; » des messages de séquence dans ces quatre blocs (correction de forme d'un document archivé) ; ajouter à verifier-docs une règle statique (« ; » dans un message de sequenceDiagram) qui couvre aussi les archives, ou y étendre le rendu.

### AUD-DOC-29 — Vingt lignes « Source » de dix spécifications principales (et neuf passages des changements actifs) citent le rapport d'audit docs/audit/2026-11-21/, absent du dépôt et exempté du contrôle documentaire

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 6
- **Nouveau** le 28/11

**Preuve**

```text
`grep -rn 'docs/audit/' openspec/specs | wc -l` = 20 dans 10 specs (ex. openspec/specs/exploitation-plateforme/spec.md:10, :33 « Source : AUD-QUA-02 (`/docs/audit/2026-11-21/findings.md`) ») ; 9 occurrences dans openspec/changes/completer-configuration-conteneurs et durcir-conteneurs-de-service ; `ls docs/audit` : absent, `git log --all -- docs/audit` : vide ; scripts/verifier-docs.mjs:30-31 (« conservé hors du dépôt par le porteur du projet ») et :36 (CHEMINS_HORS_DEPOT).
```

**Impact** — La source d'exigences normatives n'est pas consultable depuis le dépôt d'un produit ouvert à tous les publics (ADR-0025) ; un contributeur ne peut pas vérifier l'origine d'une exigence.

**Action** — Versionner un extrait des constats cités (identifiant, titre, preuve) sous docs/ ou remplacer le chemin par l'identifiant seul avec une phrase de contexte dans la spec ; garder l'exemption explicite dans le registre des spécifications.

## Documentation — Info (6)

### AUD-DOC-13 — Liens et ancres sains dans tout le dépôt (213 fichiers, 512 liens) ; le contrôle documentaire passe à b142695 sans l'option --rendu (avec : 1 erreur, AUD-DOC-06)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : liens et ancres sains dans tout le dépôt ; verifier-docs passe sans et avec --rendu.

*Changement depuis le 21/11* : Périmètre de verifier-docs étendu (#75) ; nombre de fichiers et de liens accru par le delta.

```text
`node /home/claude/audit-maj/doc-scripts/liens.mjs /home/claude/audit-maj3/build-doc` : « fichiers=310 liens relatifs=813 erreurs=0 » ; `node scripts/verifier-docs.mjs` : 123 fichiers, 70 blocs Mermaid, aucune erreur, code 0 ; avec --rendu (mmdc 11.16.0) : aucune erreur, code 0.
```

*Réserve* : Les blocs Mermaid des changements archivés, hors périmètre, ne se rendent pas tous : `node /home/claude/audit-maj2/doc-scripts2/rendu-hors-perimetre.mjs` : « blocs hors périmètre=63 erreurs=4 » (AUD-DOC-28).

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient : liens et ancres sains dans tout le dépôt ; verifier-docs sans --rendu sans erreur.

**Preuve d'origine**

```text
`node scripts/verifier-docs.mjs` dans une copie de 54add78 (/home/claude/audit-maj/build-doc) : « Fichiers Markdown contrôlés : 120 », « Blocs Mermaid : 73 (flowchart, classDiagram, sequenceDiagram, erDiagram, stateDiagram-v2) », « Rendu Mermaid : non demandé (option --rendu) », « Résultat : aucune erreur », code de sortie 0 (avec --rendu : 1 erreur, voir AUD-DOC-06) ; /home/claude/audit-maj/doc-scripts/liens.mjs sur les 201 fichiers .md hors node_modules (archives OpenSpec, .claude, .archgate, README de sous-projets compris) : 491 liens relatifs, 0 lien cassé, 0 ancre introuvable.
```

**Impact** — Navigation documentaire fiable ; les identifiants d'exigences cités sont tous définis (contrôle du script).

**Action** — Conserver ; voir AUD-DOC-10 pour les angles non couverts par le script.

### AUD-DOC-14 — Statuts des 33 ADR identiques dans les en-têtes, l'index adr/README.md et arc42 §9.1 ; §9.3 reporte les questions tranchées par ADR-0031 et ADR-0032

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Ne tient plus qu'en partie : 41/42 statuts concordants ; ADR-0042 absent d'arc42 §9.1 (nouveau constat AUD-DOC-24).

*Changement depuis le 21/11* : ADR-0034 à ADR-0042 ajoutés dans le delta ; ADR-0042 (Accepté) n'a pas été reporté dans arc42 §9.1.

```text
`node /home/claude/audit-maj/doc-scripts/statuts-adr.mjs /home/claude/audit-maj3/build-doc` : « ADR-0042 fichier=accepté index=accepté arc42=(aucun) <-- ECART », 41 autres lignes concordantes ; docs/architecture/arc42/09-decisions.md:51 (dernière ligne : ADR-0041) ; `grep -c '^| ADR-' 09-decisions.md` = 41.
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient : 33/33 statuts concordants entre en-têtes, index et arc42 §9.1.

**Preuve d'origine**

```text
/home/claude/audit-maj/doc-scripts/statuts-adr.mjs : 33/33 statuts concordants (fichier, docs/architecture/adr/README.md, docs/architecture/arc42/09-decisions.md:11-43) ; 09-decisions.md:57 et :59 (scheduling et Flyway « tranché par ADR-0031/0032 »).
```

**Impact** — La règle 5 de l'index (synchronisation avec arc42 §9) est tenue.

**Action** — Conserver ; corriger la rédaction des décisions acceptées (AUD-DOC-08).

### AUD-DOC-15 — Schéma et tâches planifiées documentés à jour : migrations V1 à V21 listées par arc42 §8.7 et D-13, treize tâches @Scheduled décrites

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Ne tient plus qu'en partie : D-13 couvre V1-V28, mais le tableau d'arc42 §8.7 saute V25 et V26 et §8.6 annonce 15 tâches pour 16 (nouveau constat AUD-DOC-25).

*Changement depuis le 21/11* : Migrations V22 à V28 et trois tâches planifiées ajoutées dans le delta ; D-13 et diagrams/README.md suivis, arc42 §8 en partie.

```text
`ls argos-server/src/main/resources/db/migration` : V1 à V28 ; d-13-modele-physique.md:3 (« V1__socle.sql à V28__recherche_bornee.sql ») ; docs/architecture/arc42/08-concepts-transverses.md:271-273 (V24 puis V27, V28) et :242 (« état au 2026-11-24 ») ; :148 (« 15 tâches ») contre 16 identités @Scheduled dans argos-server/src/main/java, toutes présentes au tableau :150-167.
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient : migrations V1 à V21 et 13 tâches planifiées documentées à jour.

**Preuve d'origine**

```text
argos-server/src/main/resources/db/migration/ (V1 à V20) = docs/architecture/arc42/08-concepts-transverses.md:161-186 (« état au 2026-11-19 », retour arrière V18-V20 :163) = diagrams/d-13-modele-physique.md:3 ; écart daté d'ADR-0032 (V1-V16) expliqué en inventaire-sbom.md:281 ; 13 @Scheduled dans argos-server/src/main/java : les 11 d'ADR-0031:16-26 (daté du 17/11) plus signal-synchronisation-sources et inventory-import-quotidien, ajoutés en 08-concepts-transverses.md:133-134.
```

**Impact** — Les documents vivants absorbent les ajouts du lot 18 sans réécrire les ADR datés.

**Action** — Conserver.

### AUD-DOC-16 — e2e/README.md et argos-server/README.md exacts par rapport au code

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Ne tient plus : trois versions citées par e2e/README.md et argos-server/README.md ne correspondent plus aux fichiers de verrouillage (nouveau constat AUD-DOC-26). Projets Playwright et textes de licence toujours concordants.

*Changement depuis le 21/11* : Renovate a mis à jour @playwright/test (578d3a4, 1.65.1), electron (adeed23, 45.4.1) et exec-maven-plugin (3.7.0) sans les README.

```text
e2e/README.md:7 (Electron 45.3.2) contre argos-desktop/package-lock.json:1450-1451 (45.4.1) ; e2e/README.md:11 (Playwright 1.61.1, Chromium 1228) contre e2e/package-lock.json:20-21 (1.65.1) ; argos-server/README.md:28 (exec-maven-plugin 3.6.4) contre argos-server/pom.xml:33 (3.7.0) ; e2e/playwright.config.ts:89-101 (preparation, portail-web, construction-desktop, desktop, mcp) = e2e/README.md:22-25 ; `ls argos-server/licenses` : 7 textes = argos-server/README.md:43-51.
```

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient : versions, projets, tableaux du script et textes de licence concordent.

**Preuve d'origine**

```text
e2e/README.md:22-25 = projets de e2e/playwright.config.ts:65-77 (preparation, portail-web, construction-desktop, desktop, mcp) ; versions citées = lockfiles (@playwright/test 1.61.1 dans e2e/package-lock.json, electron 45.3.2 dans argos-desktop/package-lock.json) ; ports 8090, 5173, 8180, 55432, 5174 = playwright.config.ts:98-103, e2e/compose.yaml:16, accessibilite.config.ts:7 ; tous les fichiers de tests, données et support cités existent ; scripts test:accessibilite et test:performance présents (e2e/package.json) ; argos-server/README.md:27-28 = argos-server/pom.xml:31 et :33 (cyclonedx 2.9.3, exec 3.6.4), tableaux ADMISES, SOUS_RESERVE, CHOIX, TEXTES, SANS_TEXTE, TITULAIRES = scripts/verifier-licences-serveur.mjs:66-132, 7 textes de argos-server/licenses/ = tableau :43-51.
```

**Impact** — La documentation développeur des tests et des licences est fiable et reproductible.

**Action** — Conserver ; l'inclure dans le périmètre de verifier-docs (AUD-DOC-10).

### AUD-DOC-17 — Contrats cités : outils MCP cohérents ; chiffres de performance et inventaire OpenSpec devenus incohérents (AUD-DOC-19, AUD-DOC-20)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient de nouveau : 8 outils MCP = « huit outils » ; inventaire OpenSpec (19 specs, 165 exigences, 2 changements actifs, 32 archivés) = dépôt ; chiffres de performance identiques entre rapport, arc42 §10 et QS-06.

*Changement depuis le 21/11* : Inventaire OpenSpec et chiffres de performance réalignés par 1e96b80 et b8af729.

```text
`grep -c '@Tool(' io/argos/mcp` = 8 = docs/specifications/serveur-mcp.md:26 ; docs/specifications/README.md:108-111 = `ls openspec/specs` (19), `grep -c '### Requirement' openspec/specs/*/spec.md` (165), `ls openspec/changes` (2 actifs), `ls openspec/changes/archive` (32) ; tâches 11/12 et 13/14 des changements actifs = cases de leurs tasks.md.
```

*Réserve* : Concordance exhaustive des routes REST citées non rejouée ; routes du guide « Ajouter des utilisateurs » vérifiées (EspacesResource.java:36, :124 ; InvitationsResource.java:24, :45).

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Ne tient plus qu'à moitié : outils MCP (8 @Tool = « huit outils ») toujours cohérents ; les chiffres de performance et l'inventaire OpenSpec ne concordent plus (nouveaux constats AUD-DOC-19 et AUD-DOC-20).

**Preuve d'origine**

```text
Chaque route `GET|POST|PUT|DELETE|PATCH /api/v1/…` citée dans README, docs/, e2e/, argos-server/ et les changements actifs a tous ses segments dans un @Path du serveur (seul écart : /api/v1/products/{produit}, API externe endoflife.date, 08-concepts-transverses.md:267) ; huit outils de serveur-mcp.md:26 = huit @Tool(name=…) dans io/argos/mcp ; p95 de GET /api/v1/me/veille = 533,8 ms dans quality/mesure-performance-api-mcp.md:32, arc42 §10 (10-exigences-qualite.md:80), quality/scenarios.md:12, docs/specifications/README.md:199, tasks.md:132 et proposal.md:166 ; docs/specifications/README.md:102-104 (11 specs, 4 changements actifs, 11 archivés) = dossiers openspec/specs, openspec/changes, openspec/changes/archive.
```

**Impact** — Les chiffres et noms techniques partagés entre documents sont tenus.

**Action** — Conserver.

### AUD-DOC-18 — Traçabilité OpenSpec transparente : les cinq tâches ouvertes des changements archivés le 2026-11-20 portent une décision datée et une recette R-1 à R-5

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**

**Vérification du 28/11 (01fea6d)** — Tient : les tâches ouvertes archivées renvoient toujours aux recettes R-1 à R-5, toutes « à faire ».

*Changement depuis le 21/11* : Aucun changement des tâches archivées concernées ; recettes R-6 à R-8 ajoutées.

```text
docs/deploiement/recette-poste.md:7-11 (R-1 à R-5 « à faire »), :12-14 (R-6, R-7 réussies sur l'hôte, R-8 partielle).
```

*Réserve* : Les fiches des capacités concernées se disent « livrée » sans renvoi à ces recettes (voir réserve d'AUD-DOC-19).

*Au 20/11* : Info (Info) — —

*Au 21/11* : Info (Info) — Tient : aucune tâche ouverte n'est archivée sans motif ni renvoi ; la correspondance tâche → recette est exacte.

**Preuve d'origine**

```text
openspec/changes/exposer-serveur-mcp/tasks.md:32 (5.2, Claude Code réel) ; formaliser-chaine-securite-poc/tasks.md:27-28 (3.2, « [DÉCISION] … capture sur un dépôt pilote réel »), :64-65 (4.7), :90 (7.1) ; notifier-sur-le-poste/tasks.md:12 et :53 (1.2 non cochée tant que les vérifications manuelles sous Windows ne sont pas faites) ; automatiser-chaine-veille/tasks.md : 25/25 cochées avec preuves datées.
```

**Impact** — L'état d'avancement réel se lit dans tasks.md ; seuls les documents de synthèse divergent (AUD-DOC-07).

**Action** — Conserver.
