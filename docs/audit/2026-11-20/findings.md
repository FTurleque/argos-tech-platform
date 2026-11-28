# Constats de l'audit — mise à jour du 2026-11-20

Base vérifiée : `origin/main` à 54add78 (PR #21, lot 18). **82 constats restent à traiter sur 132.** Tri : sévérité puis axe, les constats Documentation en dernier. Chaque constat porte son statut au 20/11, ce qui a changé depuis le 17/11, le verdict de vérification, son sprint et ses dépendances. Ordre d'exécution : [sprints.md](sprints.md).

## Élevée (7)

### AUD-ARC-02 — L'IHM et le desktop joignent désormais le jeton OIDC, mais l'IHM embarquée dans l'image Docker reste derrière « /* authenticated » et ne peut pas se charger

- **Statut** : Partiel · **Sévérité** : Élevée · **Axes** : Architecture, Sécurité · **Effort** : M · **Sprint** : 5
- **Fusionne** : AUD-SEC-02
- **Bloque** : AUD-DOC-03

**Vérification du 20/11 (54add78)** — La partie client est corrigée et testée ; le serveur exige toujours une session sur /* y compris sur les ressources statiques de l'IHM embarquée dans l'image Docker, qui ne peut donc pas se charger pour lancer la connexion.

*Changement depuis le 17/11* : IHM : point d'appel unique appelerApi qui joint « Authorization: Bearer » à tout /api/v1 sauf /system/info (argos-ui/src/api/client.ts:81-118, api/autorisation.ts:14-35), session Authorization Code + PKCE S256 (argos-ui/src/session/, commit 9f138cb). Desktop : écoute de bouclage 127.0.0.1 et navigateur du système (argos-desktop/src/connexion.js:10,135, commit 07252ed). ADR-0033 accepté.

```text
argos-server/src/main/resources/application.properties:292-295 « public.paths=/api/v1/system/info,/q/health,/q/health/* » puis « authentifie.paths=/* … policy=authenticated » (inchangé) ; Dockerfile:13 « ENV VITE_ARGOS_API_URL= » et :36 « COPY --from=ui /src/argos-ui/dist ./src/main/resources/META-INF/resources » ; client.ts:15 (URL vide => même origine). Tests clients : argos-ui/tests/autorisation.test.mjs, oidc.test.mjs, session.test.mjs ; argos-server/src/test/java/io/argos/api/comptes/ClientIhmOidcTest.java. L'E2E sert l'IHM par Vite sur une autre origine (e2e/playwright.config.ts:103-109, URL_IHM=http://localhost:5173), jamais depuis l'image.
```

*Réserve* : Comportement 401 sur /index.html déduit de la configuration (aucun build ni image possible ici) ; à confirmer par « docker run » puis « curl -i http://localhost:8080/ ». docs/deploiement/synology-et-pc.md:59 dit encore « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place ».

**Preuve d'origine**

```text
argos-server/src/main/resources/application.properties:200 « quarkus.oidc.application-type=service », :217-218 « authentifie.paths=/* … policy=authenticated » ; argos-ui/src/api/client.ts:36-39 « fetch(`${apiBaseUrl}${path}`, { headers: { Accept: 'application/json' } … » (aucun en-tête Authorization) ; grep -rniE 'authorization|bearer|oidc|token' argos-ui/src argos-desktop/src -> aucun résultat pertinent. Lacune connue : docs/specifications/notifications-poste.md:123 « le serveur répond 401 ».
[AUD-SEC-02] argos-server/src/main/resources/application.properties:217-218 `quarkus.http.auth.permission.authentifie.paths=/*` / `policy=authenticated` (seuls /api/v1/system/info et /q/health* en permit) ; argos-ui/src/api/client.ts:36,87,173 `fetch(`${apiBaseUrl}${path}`, {...})` sans en-tête Authorization ; `grep -rniE 'authorization|bearer|token|oidc|pkce' argos-ui/src argos-desktop/src` : aucun résultat. Le Dockerfile copie l'IHM dans META-INF/resources, couverte par /* (doc Quarkus : un chemin en /* est un préfixe appliqué à toutes les requêtes HTTP).
[À 9d96406] application.properties:235 (application-type=service), :252-253 (/* authenticated) ; client.ts:36-39 inchangé.
```

**Impact** — En image Docker, même index.html et l'écran de statut reçoivent 401 : l'IHM web est inutilisable ; seul le desktop, qui sert l'IHM depuis app://argos, affiche l'écran de statut, et toutes ses autres vues reçoivent 401. Le parcours de bout en bout IHM ↔ serveur n'a jamais pu être exécuté (openspec/changes/notifier-sur-le-poste/tasks.md:28).

**Action** — Implémenter Authorization Code + PKCE (client public argos-ui, ADR-0027) dans argos-ui (ex. oidc-client-ts) et le protocole app://argos du desktop, injecter le jeton dans un wrapper fetch unique de client.ts, puis lancer un E2E navigateur contre le royaume Keycloak de test.

### AUD-QUA-01 — La création de projet, l'ajout de dépôt et l'import manuel (SBOM, manifestes) n'ont toujours aucun point d'entrée en production ; seul l'import planifié des dépôts déjà configurés est branché

- **Statut** : Partiel · **Sévérité** : Élevée · **Axes** : Qualité · **Effort** : M · **Sprint** : 6

**Vérification du 20/11 (54add78)** — Partiel, mais sans effet en production : la tâche ImportPlanifie (77181e9, testée) importe chaque nuit les dépôts déjà rattachés, or aucun point d'entrée ne crée de projet ni ne rattache de dépôt — elle n'a donc rien à parcourir. Désaccord écrit avec inventaire-sbom.md:33, qui affirme que « l'import manuel existant reste possible ».

*Changement depuis le 17/11* : Commit 77181e9 (volet B) : nouvelle tâche planifiée inventory/ImportPlanifie.java:52-56 (cron argos.inventory.import.cron, 03:00) qui appelle InventoryService.importerProjet pour chaque projet ayant au moins un dépôt (ProjectDirectory.avecDepots) ; V19 ajoute project_repository (multi-dépôts). Testé par chain/ImportPlanifieTest.java:131 et RecorrelationImportTest. La création de projet, l'ajout/détachement de dépôt et l'import manuel SBOM/manifestes ne sont toujours branchés à rien.

```text
grep -rn "ProjectDirectory\|InventoryService\|importSbom\|importManifests\|ajouterDepot\|detacherDepot" argos-server/src/main hors paquet inventory -> seulement projectintel/CollecteActivite.java:49,81,112 (lecture : withRepository/find). ProjectDirectory.java:56 create et :91 ajouterDepot, InventoryService.java:91 importSbom, :108 importManifests, :127 refresh, :174 detacherDepot : appelés uniquement depuis src/test (ex. chain/ImportPlanifieTest.java:127, chain/InventoryTest.java:93). api/ProjectResource.java:44,61 et api/InventoryResource.java:57-104 : uniquement des @GET. La pile E2E insère les projets en SQL (e2e/donnees/chaine.sql:10,15 ; e2e/donnees/inventaire.sql:11).
```

*Réserve* : Désaccord avec docs/specifications/inventaire-sbom.md:33 et openspec/changes/automatiser-chaine-veille/proposal.md:19 qui affirment que « l'import manuel existant reste possible » : aucun point d'entrée (REST, MCP, CLI) n'appelle importSbom/importManifests à 54add78. La création de projet reste non documentée comme limite dans le README.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/inventory/InventoryService.java:49 (importSbom), :64 (importManifests), :91 (refresh) ; inventory/ProjectDirectory.java:37 (create). Commande : grep -rn "InventoryService\|projects.create\|importSbom\|\.refresh(" argos-server/src/main -> aucune référence hors du paquet inventory ; références uniquement dans src/test (chain/InventoryTest.java:23, alerting/AlertesTest.java:44). api/InventoryResource.java:22-52 n'expose que deux GET (inventaire, échecs). Aucun @Scheduled, outil MCP ni ressource REST n'appelle InventoryService ou ProjectDirectory.create.
```

**Impact** — En exploitation, aucun projet ni composant ne peut être créé : la corrélation (CorrelationEngine.onInventoryReplaced), le radar de dette de version et les alertes d'impact restent sans données réelles alors que les tests passent. Environ 600 lignes (InventoryService, InventoryStore, CycloneDxParser, ManifestParser, RepositoryFiles) sont du code mort pour la production.

**Action** — Exposer l'import (POST /api/v1/espaces/{id}/projets, POST /projects/{id}/inventory pour SBOM, POST .../refresh) avec contrôle de rôle via Access, ou un déclenchement planifié de refresh() pour les projets ayant un dépôt ; ajouter un test de bout en bout REST qui crée un projet, importe une SBOM et observe un impact. À défaut, documenter explicitement la limite dans le README.

### AUD-QUA-02 — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Qualité, Dépendances · **Effort** : S · **Sprint** : 2
- **Fusionne** : AUD-DEP-02
- **Bloque** : AUD-DEP-03, AUD-SEC-07

**Vérification du 20/11 (54add78)** — Pipeline sans pipefail, rotation sans contrôle de validité et message de succès inconditionnel toujours présents.

*Changement depuis le 17/11* : Aucun : fichier inchangé depuis 9d96406 (git log 9d96406..HEAD -- scripts/sauvegarder-base.sh -> vide).

```text
scripts/sauvegarder-base.sh:5 « set -eu » ; :22 « $COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE" » ; :26-31 rotation « for OLD in $(ls -1t ...) ... rm -f » ; :33 « echo "Sauvegarde écrite : $FILE" ».
```

**Preuve d'origine**

```text
scripts/sauvegarder-base.sh:5 « set -eu » (sans pipefail) ; :22 « $COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE" » ; :26-31 rotation « for OLD in $(ls -1t "$DEST"/argos-*.sql.gz) ... rm -f » sans contrôle de taille ni de validité ; :33 affiche toujours « Sauvegarde écrite ».
[AUD-DEP-02] scripts/sauvegarder-base.sh:22 `$COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE"` (set -eu seul, ligne 5) ; :26-30 rotation par nombre de fichiers sans contrôle de taille ni de validité. Commande exécutée : `sh -c 'set -eu; false | gzip > /tmp/t.gz; echo ...'` -> « sortie OK malgré échec pg_dump, taille=20 ».
```

**Impact** — Le code de retour d'un pipeline sh est celui de gzip : si postgres est arrêté ou pg_dump échoue, le script sort en 0 avec un .sql.gz vide ou tronqué. La tâche planifiée DSM ne signale rien ; après ARGOS_BACKUP_KEEP (14) échecs consécutifs, toutes les sauvegardes valides ont été supprimées par la rotation : perte de données possible lors d'une restauration.

**Action** — Écrire le dump dans un fichier temporaire puis vérifier le statut de pg_dump (ex. « pg_dump ... > tmp || exit 1 », ou bash avec « set -o pipefail »), contrôler « gzip -t » et une taille minimale, renommer seulement en cas de succès, et n'appliquer la rotation qu'après une sauvegarde valide. Sortir en code non nul pour que DSM notifie l'échec.

### AUD-TST-01 — Aucune intégration continue : aucun test (mvn verify, npm test, ModuleBoundariesTest) n'est exécuté automatiquement

- **Statut** : Ouvert · **Sévérité** : Élevée (était Moyenne le 17/11) · **Axes** : Tests, Dépendances · **Effort** : M · **Sprint** : 1
- **Fusionne** : AUD-DEP-04
- **Bloque** : AUD-DEP-20, AUD-DEP-22, AUD-DOC-10, AUD-TST-02, AUD-TST-12, AUD-TST-19, AUD-TST-20

**Vérification du 20/11 (54add78)** — Ouvert : toujours aucun workflow CI (.github absent) ni hook ; le Dockerfile contrôle désormais les licences mais construit toujours avec -DskipTests. 158 commits et 14 lots ont été intégrés sans gate automatique ; sept constats en dépendent. Sévérité relevée à Élevée (dette qui bloque la vérification des chantiers en cours).

*Changement depuis le 17/11* : Aucun ajout de CI dans le delta (aucun commit ci/workflow/pipeline). Le Dockerfile exécute désormais le contrôle des licences (profil « licences », mvn -P licences validate) mais construit toujours sans tests. Le volume à protéger a fortement augmenté : 597 méthodes de test serveur (364 à 9d96406), 206 tests argos-ui (25), 54 tests argos-desktop (22), 34 tests Playwright E2E et 29 contrôles d'accessibilité, tous lancés à la main.

```text
ls -a /home/claude/work/argos/.github -> No such file or directory ; find -maxdepth 3 (.gitlab-ci.yml, Jenkinsfile, .husky, .circleci, .drone.yml, .woodpecker*, lefthook*, .pre-commit-config.yaml) -> aucun résultat ; Dockerfile:17 « ils se lancent hors de l'image avec « mvn verify » » ; Dockerfile:37 RUN mvn -B package -DskipTests ; README.md:50 « tests : mvn verify (Docker requis : base jetable) »
```

*Réserve* : Les rapports versionnés dans e2e/resultats/ (34/34 le 2026-11-19) montrent que le mainteneur lance les suites avant intégration : le risque est atténué par la discipline, pas par un automate.

**Preuve d'origine**

```text
ls -a /home/claude/work/argos/.github -> No such file or directory ; aucun autre fichier de pipeline. Dockerfile:15 « Les tests demandent Docker (base jetable) : ils se lancent hors de l'image avec « mvn verify » » ; Dockerfile:22 RUN mvn -B package -DskipTests
[AUD-DEP-04] Absence de .github/ (ls -la à la racine) et de tout autre fichier de CI ; .git/hooks ne contient que des *.sample (ls -la .git/hooks), core.hooksPath non défini ; .archgate/config.json `"domains": {}` et .archgate/adrs/GEN-001-example.md (« This is an example ADR ... Replace this ») ; Dockerfile:22 `RUN mvn -B package -DskipTests` ; README.md:49 `tests : mvn verify (Docker requis)` ; scripts/verifier-docs.mjs (contrôle de liens/Mermaid) lancé à la main.
[À 9d96406] Toujours aucune CI ; 363 méthodes de test serveur (décompte statique), 416 tests exécutés (mesure du mainteneur).
```

**Impact** — Les 321 méthodes de test serveur, les tests d'architecture et les 47 tests JS ne protègent que si le développeur pense à les lancer ; une image peut être construite et déployée (NAS puis service ouvert au public) avec des tests en échec ou une frontière de module violée.

**Action** — Ajouter un workflow (GitHub Actions ou équivalent) déclenché sur push/PR : mvn -B verify (runner avec Docker), npm ci && npm test && npm run typecheck dans argos-ui, npm ci && npm run check dans argos-desktop ; rendre la branche principale protégée par ce statut.

### AUD-PER-02 — L'enrichissement NVD/EPSS et la corrélation sont exécutés de façon synchrone pour chaque signal créé ou modifié, dans le fil de l'ingestion

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Performance, Qualité · **Effort** : M · **Sprint** : 7
- **Fusionne** : AUD-QUA-05

**Vérification du 20/11 (54add78)** — L'enrichissement reste synchrone (observateur AFTER_SUCCESS, une requête HTTP par CVE et par source) et retryMissing n'a toujours aucun appelant en production.

*Changement depuis le 17/11* : Aucun changement dans vulnintel/Enricher.java (git diff 9d96406 HEAD vide). Le volet A ajoute seulement l'invalidation des caches CERT-FR et KEV à chaque échéance (vulnintel/RafraichissementIndex.java:29-33), sans appel réseau.

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:57-60 (onSignalChanged AFTER_SUCCESS -> requiringNew().run(enrich)) ; :67-85 (boucle CVE × source, source.lookup(cve)) ; :88 retryMissing(int) ; commande « grep -rn retryMissing argos-server/src » -> Enricher.java:35 (javadoc), :88 (définition) et test/java/io/argos/chain/EnrichmentTest.java:95 uniquement ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:93-99 (corrélation au même événement, même fil).
```

*Réserve* : Aggravation de fait : depuis l'empreinte normalisée (SignalRecorder.java:106-110, avis inchangé = UNCHANGED, sans SignalChanged), une resynchronisation horaire ne relance plus l'enrichissement d'un avis inchangé. Un enrichissement refusé par NVD (débit sans clé) est donc définitivement perdu, faute d'appelant pour retryMissing. Le javadoc d'Enricher.java:35 annonce une reprise qui n'existe pas. Le test EnrichmentTest:95 prouve que la méthode marche, pas qu'elle est appelée.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:57-60 « void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) { ... QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())); » ; Enricher.java:70-76 (une requête HTTP par CVE et par source : NvdSource « ?cveId= », EpssSource « ?cve= », sans cache ni limite de débit) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:76-78 (corrélation au même événement) ; Enricher.java:88 retryMissing(int) n'a aucun appelant (grep -rn retryMissing main : seule la définition).
[AUD-QUA-05] vulnintel/Enricher.java:57-60 « QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())) » ; :67-83 enrich() boucle sur chaque CVE × chaque EnrichmentSource (NVD, EPSS, KEV, CERT-FR) avec source.lookup(cve) puis save() ; application.properties:28 « argos.sources.http-timeout=PT30S » ; aucun quarkus.transaction-manager.default-transaction-timeout (défaut 60 s). L'observateur est synchrone (TransactionPhase.AFTER_SUCCESS) sur le fil de l'ingestion, comme CorrelationEngine.java:76 -> MoteurAlertes (attente IA, envoi).
```

**Impact** — Chaque avis ingéré ajoute jusqu'à 2 appels HTTP bloquants (timeout PT30S chacun) par CVE, en plus de la corrélation et des alertes : le débit de l'ingestion est borné par la latence de NVD. Sans clé d'API, NVD limite fortement le débit : les refus deviennent des enrichissements manqués, qui ne sont jamais rattrapés, puisque retryMissing n'est appelée nulle part.

**Action** — D'abord planifier retryMissing (une dizaine de lignes, @Scheduled) pour ne plus perdre d'enrichissement ; puis remplacer l'observateur synchrone par une file traitée par lots avec une limite de débit par source (NVD : 5 requêtes / 30 s sans clé) et l'API EPSS par lot.

### AUD-DEP-01 — compose.yaml ne transmet pas au conteneur 28 des 37 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et 27 manquent dans .env.example : e-mails vers localhost:587, liens vers localhost:5173

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Dépendances · **Effort** : S · **Sprint** : 2
- **Bloque** : AUD-DOC-03

**Vérification du 20/11 (54add78)** — Aggravé : 28 variables lues en production absentes du service argos (au lieu de 23), 27 absentes de .env.example (au lieu de 22) ; doc toujours en retard.

*Changement depuis le 17/11* : Aucun changement de compose.yaml ni de .env.example depuis 9d96406 (git log 9d96406..HEAD -- compose.yaml .env.example : vide). Le delta ajoute 5 variables lues en prod/commun et non transmises (ARGOS_INGESTION_PLANIFICATION_CADENCE, ARGOS_INVENTORY_IMPORT_CRON, ARGOS_JORF_URL, ARGOS_REGLEMENTAIRE_COLLECTE_CRON, ARGOS_REGLEMENTAIRE_RAPPELS_CRON) : aggravation. La doc de déploiement n'a reçu que les sections licences/images/MCP (diff 9d96406..HEAD -- docs/deploiement).

```text
Recompte par script /home/claude/audit-maj/scripts-deps/compter-env.mjs (bloc environment du service argos, ${ARGOS_*} des lignes non commentées en %prod ou sans profil) : 37 variables lues, 10 clés transmises (compose.yaml:41-54), 28 absentes dont ARGOS_SMTP_HOST/PORT/USER/PASSWORD/STARTTLS/TIMEOUT (application.properties:79-86), ARGOS_MAIL_FROM (:78), ARGOS_PUBLIC_URL (:63), ARGOS_GITLAB_TOKEN/ARGOS_GITHUB_TOKEN (:58-59), ARGOS_OIDC_AUDIENCE (:276), ARGOS_IA_PLAFOND_JOUR/QUOTA_ESPACE_JOUR/QUOTA_COMPTE_JOUR (:150-153, documentées en commentaire .env.example:37-39 mais non transmises) ; aucun env_file. Même script sur 9d96406 : 32 lues, 23 absentes de compose, 22 de .env.example. docs/deploiement/synology-et-pc.md:5 « Deux conteneurs démarrent », :59 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place », :88-97 tableau sans OIDC/SMTP/PUBLIC_URL : inchangés.
```

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

### AUD-DEP-21 — L'image Docker fige l'émetteur OIDC de l'IHM sur http://localhost:8180/realms/argos (aucun ARG au build) et le desktop installé borne sa CSP et son émetteur à localhost faute de ARGOS_API_URL/ARGOS_OIDC_URL : connexion impossible hors du poste de développement

- **Statut** : Ouvert · **Sévérité** : Élevée · **Axes** : Dépendances, Architecture · **Effort** : S · **Sprint** : 5
- **Nouveau** le 20/11
- **Fusionne** : AUD-ARC-16
- **Bloque** : AUD-DOC-03, AUD-DOC-04

**Preuve**

```text
Dockerfile:13 `ENV VITE_ARGOS_API_URL=` seule variable de build de l'IHM (ni ARG ni ENV VITE_ARGOS_OIDC_ISSUER / VITE_ARGOS_OIDC_CLIENT_ID) ; argos-ui/src/session/session.ts:12 `EMETTEUR_PAR_DEFAUT = 'http://localhost:8180/realms/argos'`, :114 `emetteur: import.meta.env.VITE_ARGOS_OIDC_ISSUER || EMETTEUR_PAR_DEFAUT` ; compose.yaml:35 `build: .` sans args. Rejoué : `VITE_ARGOS_API_URL= npx vite build` dans /home/claude/audit-maj/build-deps/ui-build -> dist/assets/index-*.js contient `localhost:8180/realms/argos`. docs/deploiement/synology-et-pc.md ne cite jamais VITE_ARGOS_OIDC_ISSUER ; :144 pour le desktop ne demande que VITE_ARGOS_API_URL alors que argos-desktop/src/main.js:31 lit ARGOS_OIDC_URL à l'exécution et connexion.js:65-79/:172 refuse une URL d'autorisation qui ne correspond pas à cet émetteur.
[AUD-ARC-16] argos-ui/src/session/session.ts:12 « EMETTEUR_PAR_DEFAUT = 'http://localhost:8180/realms/argos' », :114 « emetteur: import.meta.env.VITE_ARGOS_OIDC_ISSUER || EMETTEUR_PAR_DEFAUT » (valeur lue à la compilation par Vite) ; Dockerfile:7-14 ne définit que « ENV VITE_ARGOS_API_URL= » avant « npm run build » ; compose.yaml:45-47 et .env.example:15-18 ne configurent l'émetteur que pour le serveur (ARGOS_OIDC_URL) ; docs/deploiement/synology-et-pc.md:59 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » (obsolète) et :144 ne décrit que VITE_ARGOS_API_URL pour le desktop.
```

**Impact** — Toute instance construite selon la doc (NAS ou PC hors quarkus:dev) affiche une IHM qui envoie l'utilisateur vers un Keycloak sur localhost:8180 de sa propre machine : connexion impossible, donc portail inutilisable (toutes les routes non publiques exigent la session, ADR-0033), alors que l'API, elle, est configurée par ARGOS_OIDC_URL. Le client desktop visant le NAS échoue de même faute de documentation des deux réglages cohérents. Les tests E2E ne le voient pas : ils tournent contre quarkus:dev et le Keycloak des Dev Services sur 8180.

**Action** — Ne plus figer l'émetteur au build : l'IHM lit l'émetteur et le client_id au démarrage depuis le serveur (par exemple GET /api/v1/system/info exposant quarkus.oidc.auth-server-url public), ou à défaut ajouter `ARG VITE_ARGOS_OIDC_ISSUER`/`VITE_ARGOS_OIDC_CLIENT_ID` au Dockerfile, les passer par `build.args` dans compose.yaml depuis ARGOS_OIDC_URL, et refuser le build si l'émetteur vaut localhost. Documenter les réglages du desktop (VITE_ARGOS_OIDC_ISSUER au build, ARGOS_API_URL et ARGOS_OIDC_URL à l'exécution). Ajouter un test de l'image construite (ou un contrôle du bundle) qui échoue si `localhost:8180` y figure.

## Moyenne (18)

### AUD-ARC-04 — Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Architecture, Qualité · **Effort** : S · **Sprint** : 4
- **Fusionne** : AUD-QUA-03
- **Dépend de** : AUD-ARC-05

**Vérification du 20/11 (54add78)** — Le protocole RLS est toujours écrit quatre fois ; les deux variantes partielles de workspace ne réinitialisent toujours ni sujet_courant ni email_courant.

*Changement depuis le 17/11* : Aucun changement des quatre copies ; RlsSession gagne un appelant (impact/TraitementImpacts.java:106, V20, commit fbb04c9) ; regintel utilise ContexteBase via une nouvelle exception.

```text
grep -rn "SET LOCAL ROLE argos_app" -> workspace/ContexteBase.java:78, impact/RlsSession.java:24, workspace/subscription/SubscriptionService.java:335, workspace/preference/PreferencesRepository.java:116 ; SubscriptionService.java:338 et PreferencesRepository.java:119 « set_config('argos.compte_courant', ?, true), set_config('argos.espace_courant', '', true) » ; PreferencesRepository.java:120 « compte.toString() » (NPE si nul) ; RlsSession appelée 9 fois dans impact (ImpactQueries x5, RechercheVeille, RapprochementActualites, TraitementImpacts, RadarProjets).
```

**Preuve d'origine**

```text
grep -rn "SET LOCAL ROLE argos_app" -> workspace/ContexteBase.java:78, impact/RlsSession.java:24, workspace/subscription/SubscriptionService.java:269, workspace/preference/PreferencesRepository.java:116. impact/RlsSession.java:14-15 « Même protocole que … ContexteBase#appliquer, que impact ne peut pas utiliser directement (ModuleBoundariesTest) ». SubscriptionService.java:272 et PreferencesRepository.java:119 ne positionnent que compte_courant et espace_courant (pas sujet_courant ni email_courant, lus par les politiques de V2__comptes_et_acces.sql).
[AUD-QUA-03] workspace/ContexteBase.java:76-91 (4 paramètres : compte, sujet, email, espace) ; impact/RlsSession.java:22-32 (copie assumée : « que impact ne peut pas utiliser directement (ModuleBoundariesTest) ») ; workspace/subscription/SubscriptionService.java:266-275 et workspace/preference/PreferencesRepository.java:114-123 (2 paramètres seulement, sujet/email non réinitialisés ; PreferencesRepository:121 « compte.toString() » lève NullPointerException si compte est nul, là où les autres copies passent "").
```

**Impact** — Toute évolution des variables ou du rôle RLS (migration V2) doit être répercutée à quatre endroits ; les deux variantes partielles laissent sujet/email hérités de la transaction. La règle de frontière a poussé à copier du code de sécurité plutôt qu'à le partager.

**Action** — Faire de ContexteBase.appliquer le seul point d'application (voir AUD-ARC-05 : le déplacer dans un module transverse autorisé à tous), supprimer RlsSession et les deux copies de workspace, et ajouter une règle ArchUnit interdisant la chaîne « set_config('argos. » hors de cette classe (test de source).

### AUD-ARC-05 — Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où cinq règles ArchUnit d'exception au cas par cas

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Architecture · **Effort** : M · **Sprint** : 4
- **Dépend de** : AUD-ARC-14
- **Bloque** : AUD-ARC-04, AUD-DOC-02, AUD-QUA-06

**Vérification du 20/11 (54add78)** — Le noyau commun implicite s'étend : chaque nouveau module (regintel) a obtenu sa propre exception au lieu d'un paquet transverse.

*Changement depuis le 17/11* : Aggravé : une 5e règle d'exception (regintelNEmprunteQueRlsEtErreurs, ModuleBoundariesTest.java:187-200, commit 3f2fdc7) ajoute AccesRefuse et MotifRefus à la liste ; catalog.dictionnaire rejoint catalog.standard dans l'exception catalog ; LimiteurDebit passe de 3 à 6 instanciations.

```text
ModuleBoundariesTest.java : signalNEmprunteQueRlsEtErreurs, projectintelNEmprunteQueRlsEtErreurs, catalogNEmprunteQueRlsEtErreurs (:161-180, avec catalog.dictionnaire :174), regintelNEmprunteQueRlsEtErreurs (:188), mcpNeLitQueLaSecuriteDeLApi (:93) ; imports hors workspace : DonneeInvalide 17, ContexteBase 12 (+9 ContexteBase.Contexte), RegleViolee 10, Introuvable 10, AccesRefuse 6, MotifRefus 5 ; « new LimiteurDebit » : api/VeilleWebResource.java:92, api/reglementaire/ReglementaireResource.java:137, api/ActiviteResource.java:60, api/artiste/ProfilArtisteResource.java:152, mcp/GardeAppels.java:74, mcp/ControleInitialisation.java:50.
```

**Preuve d'origine**

```text
Classes de workspace importées hors workspace : DonneeInvalide, RegleViolee, Introuvable, AccesRefuse, ContexteBase (catalog/standard/ReferentielEspace.java:23-26, signal/web/SourcesWeb.java:26-29, projectintel/ActiviteQueries.java:26-27, inventory/InventoryQueries.java:24, mcp, api). ModuleBoundariesTest.java contient 3 règles d'exception dédiées (signalNEmprunteQueRlsEtErreurs, projectintelNEmprunteQueRlsEtErreurs, catalogNEmprunteQueRlsEtErreurs) et une 4e pour mcp->api.security (mcpNeLitQueLaSecuriteDeLApi : Caller, CallerResolver, LimiteurDebit, mcp/GardeAppels.java:20-22). LimiteurDebit est instancié dans api/ActiviteResource.java:61, api/VeilleWebResource.java:90 et mcp/GardeAppels.java:74.
```

**Impact** — Chaque nouveau module qui a besoin de la RLS ou d'une erreur commune doit soit dépendre de tout workspace, soit obtenir une exception, soit dupliquer (cas impact, AUD-ARC-04). Le sens réel « workspace = cœur métier des espaces » se brouille avec « workspace = socle technique ».

**Action** — Extraire un paquet transverse io.argos.commun (ContexteBase, DonneeInvalide, RegleViolee, Introuvable, AccesRefuse) et io.argos.commun.securite (Caller, LimiteurDebit) autorisé pour tous les modules ; retirer les 4 règles d'exception et les entrées workspace/api devenues inutiles dans ALLOWED.

### AUD-ARC-06 — Couplage par le schéma SQL : 13 tables lues par plusieurs modules, dont trois lectures qu'aucune dépendance autorisée ne couvre (impact → compte, catalog → rôles d'appartenance, alerting → préférences)

- **Statut** : Ouvert · **Sévérité** : Moyenne (était Faible le 17/11) · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 4
- **Fusionne** : AUD-QUA-07, AUD-ARC-17
- **Bloque** : AUD-DOC-02

**Vérification du 20/11 (54add78)** — Le couplage par le schéma s'étend et crée désormais des dépendances absentes de la table ALLOWED ou plus larges que la règle d'exception.

*Changement depuis le 17/11* : Aggravé : 4 nouvelles lectures croisées depuis 9d96406 (catalog.dictionnaire -> appartenance/compte, impact -> compte, alerting -> project_impact et workspace_preference_utilisateur, regintel -> domaine_metier, inventory -> espace). La phrase d'origine « toutes ces arêtes SQL doublent une arête Java déjà autorisée » n'est plus vraie. D-13 (nouveau modèle physique) ne désigne pas de module propriétaire par table.

```text
python3 -I /home/claude/audit-maj/scripts-arch/tables2.py <io/argos> (sortie tables-54add78.json) : 13 tables lues par plusieurs modules contre 9 à 9d96406. impact/ImpactQueries.java:322 « LEFT JOIN compte co » (impact n'a aucune arête autorisée vers workspace, ModuleBoundariesTest.java:46) ; catalog/dictionnaire/DictionnaireMetier.java:190-191 « FROM appartenance … role IN ('CONTRIBUTEUR', 'ADMINISTRATEUR') », :256-258, :371 (la règle catalogNEmprunteQueRlsEtErreurs ne permet que ContexteBase et les erreurs) ; alerting/AlertesSansObjet.java:42,44 « FROM project_impact i JOIN appartenance ap » ; regintel/FichesEnBase.java:186, regintel/ExpertiseFiches.java:249 « FROM domaine_metier » ; inventory/InventoryQueries.java:242 « LEFT JOIN espace e ». Inchangés : impact -> signal/actualite/project/component, vulnintel/Enricher -> signal_alias, alerting/Destinataires.java:40,64 -> appartenance/compte.
```

**Preuve d'origine**

```text
python3 -I /home/claude/audit-tmp/arch/tables.py -> signal : impact(3 fichiers), signal_package/signal_version : impact ; actualite : impact(2) ; project : impact(5) ; component : impact(2) ; appartenance/compte : alerting ; signal_alias : vulnintel. Ex. impact/ImpactQueries.java:56-58 « JOIN signal s … JOIN project p … JOIN component c », alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c », vulnintel/Enricher.java:91 « FROM signal_alias a », impact/RechercheVeille.java:218,238 « FROM signal », « FROM actualite ».
[AUD-QUA-07] impact/ImpactQueries.java:56 « JOIN signal s » ; impact/AnalyseComplementaireIa.java:114 (signal, signal_package) ; impact/RechercheVeille.java:218 (signal, actualite) ; impact/RapprochementActualites.java (actualite, component, project) ; alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c » et :60 (tables du module workspace) ; vulnintel/Enricher.java:89-93 (signal_alias). Commande : grep -rlE "(FROM|JOIN|INTO|UPDATE)\s+<table>" par table -> signal lue par 3 modules, project par 2, appartenance par workspace et alerting.
[AUD-ARC-17] argos-server/src/main/java/io/argos/alerting/Destinataires.java:61-66 « membresAvecProfil … JOIN workspace_preference_utilisateur p ON p.compte_id = c.id WHERE a.espace_id = ? AND ? = ANY (p.profils_actifs) » (ajouté par 3f2fdc7, appelé par RappelsReglementaires) ; argos-server/src/test/java/io/argos/architecture/PreferencesIsolationTest.java « seulsApiEtMcpLisentLesPreferences » ne contrôle que les dépendances Java vers io.argos.workspace.preference ; docs/architecture/arc42/05-vue-blocs.md §5.4.1 « Les préférences ne sont lues que par api et mcp (PreferencesIsolationTest) », contredit par §5.4.3 « aux membres au profil expert-metier actif ».
```

**Impact** — La propriété des tables n'est documentée nulle part ; renommer une colonne de signal ou d'inventory casse impact, alerting ou vulnintel sans qu'aucun test d'architecture ne le signale. Toutes ces arêtes SQL doublent une arête Java déjà autorisée (pas de dépendance cachée nouvelle), mais elles fixent le schéma comme contrat public.

**Action** — Documenter en arc42 §5.3 le module propriétaire de chaque table ; pour les lectures étrangères, passer par les services Query du propriétaire (SignalQueries, InventoryQueries, EspacesService) ou par des vues SQL nommées comme contrat ; ajouter un test qui vérifie que chaque module ne cite que ses tables ou des vues publiées.

### AUD-QUA-04 — Deux modèles transactionnels coexistent (89 @Transactional/23 QuarkusTransaction et setAutoCommit/commit manuels recopiés six fois)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 4

**Vérification du 20/11 (54add78)** — Les deux modèles transactionnels coexistent toujours et le modèle manuel s'est étendu au nouveau module regintel.

*Changement depuis le 17/11* : Aggravé : un sixième site de transaction manuelle a été ajouté par 3f2fdc7 (regintel/PreparationFiches.java:124, même squelette setAutoCommit(false)/commit/rollback) ; le nombre d'annotations @Transactional passe de 69 à 89, plus 23 appels QuarkusTransaction. Aucun utilitaire commun ni règle ArchUnit.

```text
grep -rn "setAutoCommit(false)" argos-server/src/main/java -> aigateway/QuotasIa.java:86, workspace/ContexteBase.java:55, impact/RadarProjets.java:207, impact/RapprochementActualites.java:142, releaseradar/CyclesDeVie.java:101, regintel/PreparationFiches.java:124 ; grep -rn "@Transactional" | wc -l -> 89 ; grep -rn "QuarkusTransaction\." | wc -l -> 23.
```

**Preuve d'origine**

```text
grep -rn "@Transactional" argos-server/src/main/java | wc -l -> 69 ; grep -rn "setAutoCommit" -> ContexteBase.java:55, QuotasIa.java:70, impact/RadarProjets.java:207 (dettes), impact/RapprochementActualites.java:142 (pour), releaseradar/CyclesDeVie.java:101. Même bloc recopié : « boolean autoCommit = c.getAutoCommit(); c.setAutoCommit(false); try { ... c.commit(); } catch (SQLException | RuntimeException e) { c.rollback(); throw e; } finally { c.setAutoCommit(autoCommit); } ».
[À 9d96406] aigateway/QuotasIa.java:86 (au lieu de :70) ; toujours 5 setAutoCommit(false), 69 @Transactional ; nouvelle méthode liberer() en autocommit (QuotasIa.java, portees).
```

**Impact** — Les méthodes à transaction manuelle ne fonctionnent que si aucun appelant n'ouvre de transaction JTA : avec Agroal, commit() sur une connexion enrôlée lève une SQLException. Ajouter un @Transactional sur une ressource ou un service appelant (régression facile) casse ces chemins à l'exécution seulement. Les cinq copies du même squelette augmentent le coût de maintenance.

**Action** — Choisir un modèle unique : soit tout en JTA (QuarkusTransaction.requiringNew() + ContexteRls.appliquer), soit un seul utilitaire de transaction manuelle (ContexteBase.executer généralisé et partagé) utilisé par les cinq sites ; documenter la règle dans arc42 §8 et l'outiller par un test ArchUnit (interdire setAutoCommit hors de l'utilitaire).

### AUD-QUA-06 — Le code JDBC brut est répété sans utilitaire commun : 273 prepareStatement, 130 catch SQLException, 11 copies du même convertisseur de date et 7 de la troncature

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 4
- **Dépend de** : AUD-ARC-05

**Vérification du 20/11 (54add78)** — Le squelette JDBC continue d'être recopié ; aucune factorisation.

*Changement depuis le 17/11* : Aggravé par le delta (regintel, inventaire multi-dépôts, opportunités) : 198 -> 273 prepareStatement (45 -> 54 fichiers), 101 -> 130 catch SQLException (111 suivis de throw new IllegalStateException), convertisseur instant(ResultSet) recopié 11 fois (nouvelle copie signal/opportunite/SourcesProfil.java:234). Les utilitaires de troncature sont désormais recopiés 7 fois. Aucun utilitaire JDBC ni Jdbi (grep "RowMapper\|jdbi" -> 0).

```text
grep -rn prepareStatement argos-server/src/main/java | wc -l -> 273 (54 fichiers) ; grep -rn "catch (SQLException" | wc -l -> 130 ; instant(ResultSet) : ReferentielEspace.java:336, SignalQueries.java:268, SourcesProfil.java:234, SourcesWeb.java:239, ActualitesWeb.java:162, ImpactQueries.java:346, ConsultationAlertes.java:143, SyncStore.java:280, RechercheVeille.java:312, InventoryQueries.java:552, CyclesDeVie.java:163 ; troncature : couper() StockActualites.java:336, SourcesWeb.java:244, LecteurDataCulture.java:145, LecteurFlux.java:104 ; cut() SignalRecorder.java:331, InventoryStore.java:367 ; borner() LecteurJorf.java:141 ; utc() SignalRecorder.java:327, SyncStore.java:285.
```

**Preuve d'origine**

```text
Commandes : grep -rn prepareStatement argos-server/src/main/java | wc -l -> 184 (41 fichiers) ; grep -rn "catch (SQLException" | wc -l -> 93, dont 78 suivis de « throw new IllegalStateException » ; « private static Instant instant(ResultSet rs, int ...) » recopié dans SignalQueries.java:268, ReferentielEspace.java:336, SourcesWeb.java:239, ActualitesWeb.java:162, ImpactQueries.java:245, RechercheVeille.java:312, ConsultationAlertes.java:143, SyncStore.java:280, InventoryQueries.java:232, CyclesDeVie.java:163 ; couper() x3 (LecteurFlux:104, StockActualites:336, SourcesWeb:244) ; utc() x2 (SignalRecorder:226, SyncStore:285).
[À 9d96406] 198 prepareStatement (45 fichiers), 101 catch SQLException ; 10 copies de instant(ResultSet).
```

**Impact** — Environ 30 à 40 % des lignes des classes *Queries/*Store sont du squelette (ouverture, paramètres positionnels, mapping par index de colonne, réemballage) : mapping fragile aux changements de colonnes, gestion d'erreur hétérogène (IllegalStateException ici, QuarkusTransactionException ailleurs, ex. ResumesPeriodiques.java:120-151), revue coûteuse.

**Action** — Introduire un petit utilitaire partagé (ex. Jdbc.query(ds, sql, binder, rowMapper) / update(...) et des convertisseurs Instant/OffsetDateTime communs) ou adopter un client léger (Jdbi, déjà compatible Quarkus) ; migrer d'abord les modules cœur (signal, impact, alerting). Garder le SQL explicite, conforme au choix actuel.

### AUD-QUA-17 — Fonctionnalités atteintes seulement par les tests : suggestions IA du radar jamais produites alors que leur décision est exposée en REST, collecte immédiate d'activité, trois méthodes sans appelant

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : S · **Sprint** : 6
- **Nouveau** le 20/11

**Preuve**

```text
catalog/standard/ReferentielEspace.java:136 suggerer(...) (seul producteur de suggestions, RAD-02-S2) : aucun appel dans argos-server/src/main (grep -rn "suggerer(" -> uniquement la déclaration et src/test), alors que api/RadarResource.java:152-159 expose decider() sur ces suggestions ; projectintel/CollecteActivite.java:111 collecterProjet : 0 appel en production, 13 dans src/test ; sans aucun appelant (ni main ni test) : impact/EtatImpact.java:49 peutAller, regintel/TextesOfficiels.java:116 parIdentifiant, inventory/InventoryService.java:58 ImportProjet.importe. suggerer et collecterProjet existaient déjà à 9d96406 (git grep 9d96406) : angle mort de l'audit initial.
```

**Impact** — La fonction « suggestion de l'IA sur le référentiel d'espace » est annoncée (RAD-02-S2) et sa validation par un administrateur est exposée, mais aucune suggestion ne peut naître en exploitation : écran et API vides sans que les tests le révèlent. Le code mort entretient la confusion sur ce qui est livré et alourdit la maintenance (suggerer porte en plus le LocalDate.now() sans fuseau d'AUD-QUA-10).

**Action** — Brancher suggerer sur un producteur réel (tâche IA planifiée ou outil MCP en écriture contrôlée) ou retirer la fonctionnalité et l'endpoint decider ; exposer collecterProjet (bouton « collecter maintenant ») ou le supprimer ; supprimer peutAller, parIdentifiant et importe ; ajouter au contrôle ArchUnit/CI une détection de méthodes publiques sans appelant en production.

### AUD-SEC-01 — Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : M · **Sprint** : 2
- **Bloque** : AUD-ARC-10

**Vérification du 20/11 (54add78)** — La résolution vérifiée et la résolution de connexion restent distinctes ; aucun épinglage d'adresse ni test de rebinding.

*Changement depuis le 17/11* : Aucun changement dans ingestion/web sur le delta (`git diff --stat 9d96406 HEAD -- argos-server/src/main/java/io/argos/ingestion/web` : vide).

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:103 `adresses = resolveur.resoudre(hote);` ; RecuperateurWeb.java:89 (verifier) puis :189 `client.send(...)` ; `grep -rn rebind argos-server/src/test` : rien. Appelants inchangés : CollecteWeb, SourcesWeb, CollecteOpportunites.
```

*Réserve* : À repasser Élevée avant l'ouverture au public (plan de déploiement NAS puis service public).

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:103 `adresses = resolveur.resoudre(hote);` puis RecuperateurWeb.java:89 `URI courante = garde.verifier(...)` et RecuperateurWeb.java:189 `client.send(requete, ...)` : HttpClient résout de nouveau le nom lui-même (aucune connexion à l'IP validée). Seul le cache DNS par défaut de la JVM (30 s, java.security : `#networkaddress.cache.ttl=-1` non défini) réduit la fenêtre. Aucun test de rebinding (grep 'rebind' dans src/test : rien).
```

**Impact** — Un compte authentifié contrôlant un DNS à TTL court peut, en déclenchant l'ajout ou la collecte d'une source (10/min), tenter de faire viser par Argos une adresse privée sur les ports 80/443 : interface DSM du NAS, réseau local, ou 169.254.169.254 en hébergement en ligne. La fenêtre est étroite : GardeAdresses et HttpClient partagent le cache DNS de la JVM (30 s), il faut que l'expiration tombe entre la vérification et la connexion. À repasser Élevée à l'ouverture au public.

**Action** — Épingler l'adresse validée : résoudre une fois, vérifier toutes les IP, puis se connecter à cette IP (socket/SSLSocket avec SNI et Host d'origine, ou client HTTP avec résolveur personnalisé type OkHttp Dns / Apache DnsResolver) ; refaire de même à chaque redirection. Ajouter un test unitaire avec un résolveur qui change de réponse entre deux appels. En hébergement en ligne, bloquer aussi en sortie (pare-feu/egress) les plages privées et 169.254.0.0/16.

### AUD-SEC-03 — Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité, Performance · **Effort** : S · **Sprint** : 8
- **Fusionne** : AUD-PER-08
- **Bloque** : AUD-PER-18

**Vérification du 20/11 (54add78)** — Aucune purge ni bornage des journaux ; la surface d'écriture sans compte Argos s'élargit.

*Changement depuis le 17/11* : Rien de purgé ; nouvelles sources d'écriture : refus des négociations MCP par sujet OIDC sans compte (ControleInitialisation, lot MCP) et synchronisation des sources d'avis devenue horaire par défaut (application.properties:46, ADR-0031) qui accroît sync_run/sync_attempt/signal_version.

```text
argos-server/src/main/java/io/argos/api/GestionErreurs.java:51 (refus INSCRIPTION_SUR_INVITATION, inchangé) ; argos-server/src/main/java/io/argos/mcp/ControleInitialisation.java:59-61 (clé = sujet OIDC, `journalRefus.enregistrer(null, sujet, ...)`) ; `grep -rn 'DELETE FROM' argos-server/src/main/java` : aucune suppression sur refus_acces, mcp_appel, ia_appel, sync_run, signal_version, fiche_historique ; seule tâche de purge : projectintel/PurgeActivite.java:32.
```

*Réserve* : La limitation de ControleInitialisation (60/min/sujet) borne le débit d'écriture d'un sujet mais pas le volume cumulé ni le nombre de sujets (inscription Keycloak libre).

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/GestionErreurs.java:51 `journal.enregistrer(null, utilisateur.sujet(), ressource(requete), "INSCRIPTION_SUR_INVITATION");` ; V2__comptes_et_acces.sql:68 `CREATE TABLE refus_acces (... sujet_oidc text ...)` ; tables mcp_appel (V9) et ia_appel (V13) de même ; `grep -rniE 'DELETE FROM (refus|mcp_appel|ia_appel)|purge' src/main` : seule PurgeActivite existe (activité des projets). Le limiteur de débit est par compte Argos : un sujet sans compte n'est pas limité.
[AUD-PER-08] Seule purge planifiée : argos-server/src/main/java/io/argos/projectintel/PurgeActivite.java:32 -> ActiviteStore.java:157-161 (activite_compteur, activite_jalon). grep -rln 'DELETE FROM' main : aucune suppression sur mcp_appel, refus_acces, ia_appel, alerte, sync_run, sync_attempt, ops_event, ingestion_failure, signal_version ni actualite. Inserts : argos-server/src/main/java/io/argos/audit/JournalAppelsMcp.java:44, JournalRefus.java:37, JournalAppelsIa.java:44 ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:100 « raw_content text NOT NULL » (JSON brut de chaque version de chaque avis) et :60 (ingestion_failure.raw_content).
[À 9d96406] Inchangé pour refus_acces/mcp_appel/ia_appel ; s'y ajoute opportunite (V15__profil_artiste.sql), sans aucun DELETE FROM opportunite.
```

**Impact** — En service ouvert (inscription Keycloak libre, mode invitation côté Argos), n'importe qui peut faire croître sans limite refus_acces (déni de service par stockage sur le NAS) ; les journaux conservent indéfiniment des identifiants personnels (sujet OIDC), contraire à la minimisation RGPD.

**Action** — Purge planifiée des journaux (ex. 90 jours à 1 an, paramétrable) ; limiter les écritures de refus par sujet (fenêtre glissante, comme LimiteurDebit) ; limiter aussi en amont par IP au reverse proxy.

### AUD-SEC-22 — Demande d'évolution : tout compte peut faire interroger GitHub/GitLab avec les jetons du serveur avant tout contrôle d'expertise (oracle d'existence sur dépôts privés, 10 appels/min/compte)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : S · **Sprint** : 5
- **Nouveau** le 20/11

**Preuve**

```text
argos-server/src/main/java/io/argos/api/reglementaire/ReglementaireResource.java:315-320 (`access.authenticated()` seul, puis `expertise.associerDemande`) ; argos-server/src/main/java/io/argos/regintel/ExpertiseFiches.java:175-186 : `demandes.etat(d)` est appelé avant `travailler(...)` (:188, contrôle de visibilité et d'expertise à :218-223) ; le contrôle préalable :178 `fiches.detail(compte, id).filter(FicheAnalyse::peutExpertiser).ifPresent(...)` ne lève rien pour une fiche invisible, d'un non-expert ou inexistante ; argos-server/src/main/java/io/argos/scm/DemandesEvolution.java:119 (jeton ARGOS_GITHUB_TOKEN/ARGOS_GITLAB_TOKEN envoyé), :121-123 (404 forge -> vide) -> ExpertiseFiches.java:181-182 réponse 400 DEMANDE_INTROUVABLE, alors qu'une demande existante poursuit vers 404/403. Aucun test du cas non-expert (`grep -rn demande-evolution argos-server/src/test` : seulement VeilleReglementaireTest.java:326-341, parcours de l'expert).
```

**Impact** — Avec un UUID quelconque, n'importe quel compte Argos (service public : inscription sur invitation mais comptes nombreux) distingue « ticket/MR existant et lisible par le jeton du serveur » (404) de « inexistant ou inaccessible » (400) sur toute forge configurée : énumération de dépôts privés et de numéros de tickets accessibles au jeton du mainteneur. Les appels consomment aussi le quota d'API du jeton partagé avec les imports d'inventaire et la collecte d'activité (QuotaEpuise reporte ces collectes) : 10/min/compte, cumulables entre comptes.

**Action** — Dans associerDemande, exiger la fiche visible, l'expertise et l'état « impact produit » (lecture sous ContexteBase, refus journalisé) AVANT tout appel à la forge ; ne pas différencier la réponse selon le résultat de la forge pour un appelant non autorisé ; ajouter un test : non-membre et membre non expert -> 404/403 sans requête vers le StubServer de forge.

### AUD-TST-03 — Pyramide inversée : 71 % des méthodes de test serveur (425 sur 597) sont des @QuarkusTest exigeant Docker (PostgreSQL + Keycloak Dev Services)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : L · **Sprint** : 9

**Vérification du 20/11 (54add78)** — Pyramide toujours inversée, à 71 %.

*Changement depuis le 17/11* : Le ratio est inchangé malgré le doublement du nombre de tests : 425 méthodes dans 69 classes @QuarkusTest sur 597 (71,2 %), contre 259/363 à 9d96406. Quelques extractions unitaires (MatchRulesTest 24, CycloneDxCaracterisationTest 5, QualificationEtDatesTest 8, scm/* 12), mais aucune des classes désignées n'a de test unitaire pur : MoteurAlertes, TraductionCriteres et ValidationSortie ne sont nommées dans aucun test ; CorrelationEngine n'est exercé que par 6 classes @QuarkusTest.

```text
scripts-test/inventaire.sh (décompte @Test/@ParameterizedTest/@TestFactory/@RepeatedTest) -> 597 méthodes, 425 dans 69 classes @QuarkusTest, 172 dans 33 classes unitaires ; grep -rlw MoteurAlertes|TraductionCriteres|ValidationSortie src/test/java -> 0 ; grep -rlw CorrelationEngine src/test/java -> 6 fichiers, tous @QuarkusTest
```

**Preuve d'origine**

```text
grep -rl @QuarkusTest src/test/java | wc -l -> 45 classes ; décompte @Test/@ParameterizedTest : 239 dans les classes @QuarkusTest, 82 dans 18 classes unitaires (total 321). README.md:49 « tests : mvn verify (Docker requis : base jetable) »
[À 9d96406] 259 méthodes @QuarkusTest sur 363 = 71 % (48 classes).
```

**Impact** — Boucle de retour lente, impossible sans Docker ; la logique métier (corrélation, règles d'alerte, impacts) n'est vérifiable qu'à travers la pile complète, ce qui rend les échecs plus coûteux à diagnostiquer.

**Action** — Extraire en tests unitaires purs la logique déterministe déjà isolable (CorrelationEngine, MoteurAlertes, règles de RegleAlerte, RechercheVeille/TraductionCriteres, ValidationSortie) ; garder les @QuarkusTest pour les parcours, la RLS et la sécurité.

### AUD-TST-05 — Autorisations par appels impératifs (80 appels access.*() pour 96 opérations) sans test matriciel point d'entrée × rôle ; POST /ops/sources/{id}/synchronizations jamais testé

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : M · **Sprint** : 5

**Vérification du 20/11 (54add78)** — Contrôle de rôle impératif toujours sans matrice, sur une surface en hausse de 37 %.

*Changement depuis le 17/11* : Aucun test matriciel ; la surface a augmenté : 96 opérations JAX-RS dans 23 ressources (70 dans 20 à 9d96406), 80 appels access.*() (47), pour 26 assertions statusCode(403) dans les tests (16). POST /api/v1/ops/sources/{id}/synchronizations n'est toujours appelé par aucun test. Les tests OpenAPI (OpenApiContractTest, ContratOpenApiTest) ne vérifient que la présence d'opérations et de champs, pas les rôles.

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-61 ; grep -rn synchronizations src/test/java -> aucun appel de ce chemin ; chain/ResilienceTest.java:151 (seul 403 « exploitant ») ; api/comptes/AuthentificationTest.java:21-26 (401 sur 7 chemins) ; grep -rhE '@(GET|POST|PUT|DELETE|PATCH)' src/main/java | wc -l -> 96 ; scripts-test/endpoints.py
```

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-61 (@POST @Path("/{id}/synchronizations") … access.operator();) ; grep -rn synchronizations src/test/java -> 0 ; seul refus 403 « exploitation » vérifié : chain/ResilienceTest.java:151 Api.en("jules").when().get("/api/v1/ops/sources").then().statusCode(403); AuthentificationTest.java:21-22 ne teste le 401 que sur 6 chemins
[À 9d96406] SourceOperationsResource.java:57-63 toujours non testé ; PUT /api/v1/profils/artiste/sources/{id}/conditions-usage sans test de 403 (ProfilArtisteTest.java:306-308 ne couvre que POST/GET sources et POST collectes).
```

**Impact** — L'oubli d'un appel access.*() sur un nouveau point d'entrée (ou sur un existant non testé) donnerait une élévation de privilège qu'aucun test ne détecte ; le 401 global est assuré par quarkus.http.auth.permission (application.properties:217-218) mais pas le contrôle de rôle.

**Action** — Ajouter un test paramétré qui énumère toutes les opérations du document OpenAPI (/q/openapi) et vérifie pour chaque rôle (anonyme, membre lecteur, administrateur d'espace, exploitant, autre espace) le code attendu ; échec si une opération n'est pas déclarée dans la matrice.

### AUD-PER-01 — La synchronisation est planifiée, mais le lancement manuel reste synchrone dans la requête HTTP et OSV est toujours lu avis par avis, reprises comprises sur le fil qui synchronise

- **Statut** : Partiel · **Sévérité** : Moyenne (était Élevée le 17/11) · **Axes** : Performance · **Effort** : M · **Sprint** : 7

**Vérification du 20/11 (54add78)** — La collecte régulière existe et ne passe plus par une requête HTTP ; en revanche le lancement manuel reste synchrone, OSV est toujours lu avis par avis et les reprises dorment toujours sur le fil qui synchronise.

*Changement depuis le 17/11* : Volet A d'automatiser-chaine-veille (commit 9b8aec8, décision J1, ADR-0031) : tâche @Scheduled « signal-synchronisation-sources » (every argos.ingestion.planification.cadence = 1h, SKIP) qui synchronise chaque source depuis son curseur ; exclusion avec le lancement manuel par sync_run_one_running et battement de cœur. Le POST d'exploitation et le connecteur OSV sont inchangés.

```text
argos-server/src/main/java/io/argos/signal/SynchronisationPlanifiee.java:46-50 (@Scheduled … SKIP) et :63-65 (boucle synchronizer.synchronize par source) ; argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-63 inchangé (« Lance une synchronisation de la source et attend son résultat ») ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:89 « sleeper.sleep(delay) », :122-123 « for (FeedEntry entry : feed.changesSince(position)) { RawAdvisory raw = feed.fetch(entry); » ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:60 (index modified_id.csv complet par écosystème à chaque passage) et :89 (GET /v1/vulns/<id> par avis) ; application.properties:46 (cadence 1h).
```

*Réserve* : Reste à faire : POST /api/v1/ops/sources/{id}/synchronizations répond toujours après la fin du traitement (le reverse proxy DSM coupe la requête sans arrêter le travail) ; l'amorçage OSV fait encore un appel HTTP séquentiel par avis (Maven, npm, PyPI), donc le premier passage planifié dure des heures sur le fil du planificateur, et chaque passage horaire retélécharge l'index complet de chaque écosystème. Aucune mesure du débit d'ingestion (la campagne p95 l'exclut explicitement : docs/architecture/quality/mesure-performance-api-mcp.md, « Opérations exclues »).

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:58-63 (« Lance une synchronisation de la source et attend son résultat » -> operations.synchronize(id)) ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:110-111 « for (FeedEntry entry : feed.changesSince(position)) { RawAdvisory raw = feed.fetch(entry); » et :77 « sleeper.sleep(delay); » ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:60 (index modified_id.csv complet par écosystème) et :89 (GET /v1/vulns/<id> par avis). Aucun @Scheduled pour l'ingestion (grep @Scheduled : seuls CollecteWeb, CollecteActivite, PurgeActivite, EnvoiAlertes, RappelsRadar, ResumesPeriodiques).
```

**Impact** — Au premier passage, chaque avis de l'index de Maven, npm et PyPI donne un appel HTTP séquentiel à api.osv.dev, suivi (même fil, observateurs AFTER_SUCCESS) de l'enrichissement, de la corrélation et des alertes. Le fil de travail HTTP reste occupé pendant des heures ; les reprises (backoff jusqu'à PT5M, 5 tentatives) dorment sur ce fil. Le reverse proxy DSM ou le client coupe la requête bien avant la fin, sans que le traitement s'arrête. Sur le NAS, cela empêche aussi de planifier une collecte régulière.

**Action** — Lancer la synchronisation en tâche de fond (@Scheduled avec concurrentExecution=SKIP, ou ManagedExecutor) et faire répondre 202 avec l'identifiant du sync_run ; amorcer OSV avec l'archive par écosystème plutôt qu'un GET par avis. Le découplage de l'enrichissement relève d'AUD-PER-02.

### AUD-PER-03 — Corrélation par projet en N+1 aggravé : une transaction et une relecture du signal par signal, puis 3 à 5 instructions SQL par composant correspondant (rattachement, état précédent, upsert, transitions)

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Vérification du 20/11 (54add78)** — Le N+1 tient toujours et s'est aggravé : une transaction et une relecture du signal par signal, puis au moins 3 instructions préparées dans la boucle pour chaque composant correspondant, au lieu d'une seule.

*Changement depuis le 17/11* : Volet C (commits fbb04c9, ceb0f50, merge 71f28f5) : la corrélation par projet recorrèle aussi les signaux qui portent un impact actif, résout les impacts devenus sans objet et rattache l'impact d'un composant revenu. Le N+1 n'est pas traité ; le nombre de requêtes par ligne augmente.

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:116-126 (correlateProject : boucle correlate(signalId) par signal) ; :141 (requiringNew par signal) ; :201 (signals.find) ; :224 (componentsMatching par signal) ; dans la boucle par composant : :230 reattach (UPDATE, :326-345), :231 precedent (SELECT avec EXISTS sur project_impact_transition, :302-319), :233-243 prepareStatement INSERT … ON CONFLICT … RETURNING, :273-275 TraitementImpacts.resoluParLeSysteme puis systeme si l'impact était RESOLU ; :376-391 resolveStale (packagePresent puis TraitementImpacts.systeme, par impact périmé) ; :133-136 (signaux en plus via signalsWithActiveImpacts).
```

*Réserve* : La corrélation par projet s'exécute désormais aussi chaque nuit pour chaque projet à dépôt (ImportPlanifie, voir AUD-PER-19), toujours de façon synchrone sur le fil de l'appelant (observateur AFTER_SUCCESS, :101-107). Une atténuation partielle : seuls les impacts changés sont publiés aux alertes (:284-287), ce qui réduit le travail de MoteurAlertes, pas celui de la corrélation.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:94-99 « List<UUID> signalIds = ...signals.signalsAffecting(...); for (UUID signalId : signalIds) { reports.add(correlate(signalId, Optional.of(projectId))); » ; :105 requiringNew par signal ; :143 signals.find ; :161 inventories.componentsMatching par signal ; :167 prepareStatement(INSERT INTO project_impact ...) dans la boucle.
```

**Impact** — Pour un inventaire npm ou Maven qui touche des centaines de paquets à avis, cela fait des centaines de transactions et au moins 4 allers-retours SQL par signal, plus une évaluation des alertes par signal (MoteurAlertes). Le tout s'exécute de façon synchrone après la validation de InventoryStore.replace (observateur AFTER_SUCCESS), donc dans le fil de l'appelant de l'import.

**Action** — Charger en une requête les signaux et leurs paquets affectés (signal + signal_package par package_key = ANY), et les composants du projet une seule fois ; préparer l'INSERT hors boucle avec addBatch ; regrouper les ImpactsRecorded par projet ; exécuter la corrélation d'inventaire en tâche de fond.

### AUD-PER-04 — Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Vérification du 20/11 (54add78)** — Toujours 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, puis l'envoi SMTP, sur le fil de corrélation.

*Changement depuis le 17/11* : MoteurAlertes.java : +9/-3 lignes (volet C, ALR-07) : alerteExiste ignore les alertes ANNULEE et l'INSERT réactive une alerte annulée. La structure par destinataire est inchangée. Nouveau AlertesSansObjet.java : annulation ensembliste en une requête par signal (pas de N+1).

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:125 (surImpacts, observateur synchrone) ; :142 « for (Destinataires.Destinataire d : candidats) » ; :174 espacesDe, :175-176 impacts.forSignals, :180-181 abonnements.activeFor (chacun dans QuarkusTransaction.requiringNew()) ; :146 et :162 alerteExiste par canal ; :263 « demande.get(delaiResumeIa…) » ; :169 envoi.envoyer(creees, maintenant).
```

*Réserve* : Les numéros de ligne ont glissé d'une unité depuis l'audit initial. Atténuation indirecte : une recorrélation sans changement ne publie plus d'ImpactsRecorded (CorrelationEngine.java:284), mais chaque avis nouveau ou modifié de la synchronisation horaire suit toujours ce chemin.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:141 « for (Destinataires.Destinataire d : candidats) { plan(e, d, paquets)... » ; :173 espacesDe, :175 impacts.forSignals, :180 abonnements.activeFor (chacun dans QuarkusTransaction.requiringNew()) ; :145 et :161 alerteExiste par plan et canal ; :262 « demande.get(delaiResumeIa...) » (attente IA jusqu'à PT10S) ; :168 envoi.envoyer(creees, ...) (SMTP synchrone, timeout PT10S par message).
```

**Impact** — Pour un espace de M membres, chaque signal corrélé coûte environ 4×M transactions courtes. S'y ajoutent jusqu'à 10 s d'attente de l'IA et l'envoi SMTP séquentiel, dans le fil qui ingère. En service multi-utilisateurs, avec des espaces nombreux ou peuplés, cela multiplie la durée d'ingestion et la charge du pool de connexions.

**Action** — Charger en une requête par signal les membres, leurs espaces et leurs abonnements actifs (jointure appartenance + subscription), ainsi que les alertes existantes (signal_id, compte_id) ; ne pas appeler envoi.envoyer dans l'observateur et laisser le job EnvoiAlertes (1 min) diffuser ; rendre l'observateur asynchrone (@ObservesAsync, ou file).

### AUD-PER-06 — « Ma veille » hors cible (p95 533,8 ms mesuré, cible 500 ms) : réponse non paginée, signaux relus un par un et signal_package parcouru par des expressions non indexées

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 8
- **Fusionne** : AUD-PER-21

**Vérification du 20/11 (54add78)** — Même requête sur expressions non indexées et même relecture signal par signal, maintenant exécutées par deux routes, dont « Ma veille », mesurée hors cible.

*Changement depuis le 17/11* : Refactorisation (commit a8de7d8, Q-3) : la boucle de WatchResource est déplacée dans api/SignauxAbonnements.java, désormais partagée par GET /api/v1/me/watch et GET /api/v1/me/veille. La requête SignalQueries.search et la relecture unitaire sont inchangées.

```text
argos-server/src/main/java/io/argos/signal/SignalQueries.java:210-218 (SELECT DISTINCT … LEFT JOIN signal_package … lower(p.ecosystem) = ANY … OR split_part(…) … OR lower(regexp_replace(…)) … ORDER BY s.modified_at DESC LIMIT ?) et :231 « ids.stream().map(this::find) » ; argos-server/src/main/java/io/argos/api/SignauxAbonnements.java:66 (find par signal d'un filtre PROJECT) et :78-79 (search par abonnement, PAR_ABONNEMENT = 100) ; WatchResource.java:62 et VeilleProfilsResource.java:187 (appelants) ; aucun nouvel index sur lower(ecosystem) ni signal.modified_at (grep CREATE INDEX V16–V20).
```

*Réserve* : Le jeu de la campagne p95 ne contient que 1 000 signaux et 1 000 lignes signal_package (e2e/performance/donnees-performance.sql:52-54) : il ne fait pas ressortir le parcours complet de signal ⋈ signal_package sur un corpus OSV réel. Réponse toujours non paginée.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/signal/SignalQueries.java:209-217 « SELECT DISTINCT s.id, s.modified_at FROM signal s LEFT JOIN signal_package p ... WHERE lower(p.ecosystem) = ANY (?) OR split_part(substr(p.package_key, 5), '/', 1) = ANY (?) OR lower(regexp_replace(...)) = ANY (?) OR ... ORDER BY s.modified_at DESC LIMIT ? » ; :231 « ids.stream().map(this::find) » (1 requête par signal, jusqu'à 100) ; argos-server/src/main/java/io/argos/api/WatchResource.java:79 (signals.find par signal d'un filtre PROJECT) et :91 (search par abonnement). Index : signal_package_key (package_key) seul (V3:122), aucun index sur lower(ecosystem) ni sur signal.modified_at (signal_date porte sur coalesce(published_at, modified_at), V11:42).
[AUD-PER-21] docs/architecture/quality/mesure-performance-api-mcp.md : « GET /api/v1/me/veille | REST | 200 | 509.3 | 533.8 | 542.3 | 0 | **hors cible** », « Jeu de données | 20 projets × 100 composants, 1000 avis, 20000 impacts (un espace) », « Système | win32 10.0.19045 x64, 16 cœurs, 48 Gio », serveur quarkus:dev, révision 7c5b9d7 (aucune modification d'argos-server ni d'argos-ui entre 7c5b9d7 et 54add78 : « git diff --stat 7c5b9d7 HEAD -- argos-server argos-ui » vide) ; e2e/resultats/performance/rapport-2026-11-19.json (même valeur, verdict HORS_CIBLE) ; code : argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:139-170 (liste complète triée en mémoire, sans page), :212-213 (impacts.forWorkspaces, LIMITE_IMPACTS = 200), :220 (signaux.find par signal), :187 (SignauxAbonnements, requête non indexée d'AUD-PER-06) ; argos-server/src/main/java/io/argos/impact/ImpactQueries.java:244-245 (« WHERE i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.updated_at DESC, i.id LIMIT ? » ; index disponibles : project_impact_workspace (workspace_id), V5__impacts_projet.sql:23, aucun sur (workspace_id, updated_at)) ; décision K2 (openspec/changes/automatiser-chaine-veille/proposal.md:136 et :166, P-18 : « consigné sans correction […] son optimisation relève d'un changement ultérieur »).
```

**Impact** — Chaque GET /api/v1/me/watch coûte, par abonnement, un parcours complet de signal ⋈ signal_package (les OR d'expressions empêchent l'usage d'un index), un tri, puis jusqu'à 100 lectures unitaires. Le coût croît avec la taille du corpus OSV et le nombre d'abonnements, sans pagination côté API.

**Action** — Faire porter ce fil par RechercheVeille (déjà paginée et indexée) ou ajouter des colonnes normalisées indexées (ecosystem_lower, package_name) ; remplacer find() en boucle par une lecture « WHERE id = ANY (?) » ; paginer la réponse.

### AUD-PER-18 — Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Performance, Qualité · **Effort** : S · **Sprint** : 8
- **Dépend de** : AUD-SEC-03

**Vérification du 20/11 (54add78)** — Les opportunités sans date limite sont toujours relues et renvoyées sans LIMIT, sans pagination et sans purge.

*Changement depuis le 17/11* : Aucun changement dans signal/opportunite, RappelsOpportunites.java ni api/artiste (git diff vide).

```text
argos-server/src/main/java/io/argos/signal/opportunite/CollecteOpportunites.java:171-172 (élément de flux avec dateLimite null) ; signal/opportunite/Opportunites.java:42-43 (« AND (o.date_limite IS NULL OR o.date_limite >= ?) ORDER BY … » sans LIMIT) ; commande « grep -rn 'DELETE FROM opportunite' argos-server/src/main » -> aucun résultat ; alerting/RappelsOpportunites.java:93,110.
```

**Preuve d'origine**

```text
signal/opportunite/CollecteOpportunites.java:171-172 (élément de flux enregistré avec dateLimite null) ; signal/opportunite/Opportunites.java:42-43 « AND (o.date_limite IS NULL OR o.date_limite >= ?) ORDER BY … » sans LIMIT ; grep 'DELETE FROM opportunite' src/main -> aucun résultat ; api/artiste/ProfilArtisteResource.java:218 (liste complète renvoyée) ; alerting/RappelsOpportunites.java:93,110 (relecture complète pour chaque suiveur).
```

**Impact** — Tout élément de flux jamais collecté reste une opportunité « ouverte » pour toujours. GET /opportunites et la tâche quotidienne de rappels relisent et renvoient toute la table pour chaque utilisateur : le fil se remplit d'éléments périmés et la charge croît linéairement avec l'historique et le nombre de suiveurs (N+1 par suiveur).

**Action** — Donner aux éléments de flux une échéance (date de publication + durée configurée) ou les exclure au-delà d'un âge maximal ; paginer GET /opportunites (LIMIT/curseur) ; purge planifiée des opportunités échues, à intégrer à la rétention de SEC-03 ; calculer les rappels en une requête qui joint suivi_profil et opportunite.

### AUD-DEP-03 — Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : S · **Sprint** : 2
- **Dépend de** : AUD-QUA-02

**Vérification du 20/11 (54add78)** — La procédure de restauration reste un psql sans ON_ERROR_STOP sur un dump texte sans --clean, non testée.

*Changement depuis le 17/11* : Aucun : scripts/sauvegarder-base.sh et la section Restauration de la doc sont inchangés.

```text
docs/deploiement/synology-et-pc.md:70-76 (même commande `gunzip -c … | … psql -U … -d …`, « À tester au moins une fois ») ; scripts/sauvegarder-base.sh:22 `pg_dump … --no-owner` ; application.properties:23 migrate-at-start ; docs/deploiement/synology-et-pc.md:186 « Non vérifié : le démarrage réel sur le NAS ».
```

**Preuve d'origine**

```text
docs/deploiement/synology-et-pc.md:72-76 `gunzip -c ... | docker compose exec -T postgres sh -c 'psql -U ... -d ...'` puis « À tester au moins une fois après la mise en service » ; scripts/sauvegarder-base.sh:22 `pg_dump ... --no-owner` (format texte, sans --clean) ; application.properties:23 `quarkus.flyway.migrate-at-start=true`.
```

**Impact** — Sur une base où Argos a déjà démarré (Flyway a créé le schéma), la restauration produit des erreurs « already exists » / clés dupliquées que psql ignore : restauration partielle silencieuse. Aucun exercice de restauration n'a été réalisé (doc:76, doc:138-140).

**Action** — Documenter la séquence complète : arrêter le service argos, recréer la base (dropdb/createdb ou pg_dump --clean --if-exists), restaurer avec `psql -v ON_ERROR_STOP=1` ou `pg_restore` (format -Fc), puis redémarrer ; ajouter un test de restauration (script ou test Testcontainers) exécuté périodiquement.

### AUD-DEP-07 — Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : M · **Sprint** : 3

**Vérification du 20/11 (54add78)** — Toujours ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier.

*Changement depuis le 17/11* : Aucun changement d'observabilité dans le delta.

```text
argos-server/pom.xml:49-173 : seule quarkus-smallrye-health (:59-62), pas de quarkus-logging-json/micrometer/opentelemetry ; application.properties : aucune clé quarkus.log.* (grep), seul :292 rend /q/health public ; grep -rlE '@Readiness|@Liveness|HealthCheck' argos-server/src/main/java -> aucun résultat ; aucun MDC ; 85 appels LOG.* texte.
```

**Preuve d'origine**

```text
argos-server/pom.xml:43-151 : seule quarkus-smallrye-health (l.53-56) ; pas de quarkus-logging-json, quarkus-micrometer ni quarkus-opentelemetry ; application.properties : aucune clé quarkus.log.* ; grep -rlE '@Readiness|@Liveness|HealthCheck' argos-server/src/main/java -> aucun résultat ; journaux texte via LOG.*f (27 errorf, 16 infof, 11 warnf, 1 debugf), aucun MDC.
```

**Impact** — En exploitation publique, impossible de suivre les taux d'erreur, la latence, l'état DEGRADED des sources, la file d'envoi des e-mails ou la disponibilité d'Ollama autrement qu'en lisant les journaux Docker ; /q/health ne reflète que la base (contrôle par défaut de la datasource).

**Action** — Ajouter quarkus-logging-json (profil prod), quarkus-micrometer-registry-prometheus (endpoint protégé), des checks @Readiness pour ingestion/envoi e-mail/IA (dégradé non bloquant), et un identifiant de corrélation en MDC ; OpenTelemetry quand un collecteur existe.

## Faible (48)

### AUD-ARC-07 — La table ALLOWED autorise 7 dépendances sur 57 que le code n'utilise pas et ne contraint pas un paquet io.argos non déclaré

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture, Tests · **Effort** : S · **Sprint** : 1
- **Fusionne** : AUD-TST-11

**Vérification du 20/11 (54add78)** — La table reste plus large que le code et ne contraint toujours pas un paquet io.argos non déclaré.

*Changement depuis le 17/11* : Les autorisations inutilisées passent de 10 à 7, mais seulement parce que le code regintel/api utilise désormais api->regintel, regintel->aigateway et regintel->catalog ; aucune autorisation n'a été retirée et 4 nouvelles ont été ajoutées (toutes utilisées). Aucune règle n'impose qu'un paquet de premier niveau figure dans la table.

```text
compare.py -> « autorisées inutilisées: catalog->audit, impact->vulnintel, mcp->projectintel, regintel->signal, releaseradar->signal, vulnintel->catalog, workspace->audit » (7 sur 57) ; ModuleBoundariesTest.java:73-82 : règles générées pour ALLOWED.keySet() seulement, interdits calculés depuis les clés, allowEmptyShould(true). Aujourd'hui les 16 paquets de premier niveau sont tous dans la table (ls io/argos).
```

**Preuve d'origine**

```text
compare.py -> « autorisées inutilisées: api->regintel, catalog->audit, impact->vulnintel, mcp->projectintel, regintel->{aigateway,catalog,signal}, releaseradar->signal, vulnintel->catalog, workspace->audit » (10 sur 54). ModuleBoundariesTest.java:71-73 : la liste des paquets interdits est calculée à partir de ALLOWED.keySet() ; un nouveau paquet io.argos.x absent de la table n'est ni contraint ni protégé.
[AUD-TST-11] argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java:31-57 (table ALLOWED, 16 entrées) et :69-80 (règle générée uniquement pour ALLOWED.keySet(), interdits calculés à partir des seules clés, allowEmptyShould(true))
```

**Impact** — Des dépendances peuvent apparaître sans revue (déjà autorisées) ; un module ajouté sans entrée dans la table échappe entièrement au contrôle.

**Action** — Retirer les 10 autorisations inutilisées (les rajouter le jour où le code en a besoin) ; ajouter une règle « toute classe de io.argos réside dans un paquet de premier niveau listé dans ALLOWED ».

### AUD-ARC-09 — Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 7

**Vérification du 20/11 (54add78)** — Les deux styles coexistent toujours sans règle ; la chaîne StandardModifie -> RadarProjets -> AnnonceStandard -> RappelsRadar reste synchrone dans le PUT /standards, exceptions avalées.

*Changement depuis le 17/11* : Un nouvel observateur transactionnel (alerting/AlertesSansObjet.java:55 AFTER_SUCCESS ImpactsTraites, V20) ; les observateurs synchrones et ReferentielEspace inchangés ; aucune règle écrite en arc42 §8.

```text
catalog/standard/ReferentielEspace.java:104,218 « r.changement().ifPresent(modifications::fire) » ; impact/RadarProjets.java:187 « @Observes StandardModifie », :198-199 « LOG.errorf(e, … en échec) » ; alerting/RappelsRadar.java:130, alerting/MoteurAlertes.java:125 (synchrones) ; grep "AFTER_SUCCESS|ObservesAsync" docs/architecture/arc42/08-concepts-transverses.md -> aucun résultat.
```

**Preuve d'origine**

```text
Observateurs transactionnels : impact/CorrelationEngine.java:76,84, vulnintel/Enricher.java:57, impact/RapprochementActualites.java:64 « @Observes(during = TransactionPhase.AFTER_SUCCESS) ». Observateurs synchrones : alerting/MoteurAlertes.java:124, impact/RadarProjets.java:187, alerting/RappelsRadar.java:130, api/preference/ActivationProfilParDefaut.java:34. catalog/standard/ReferentielEspace.java:104 « r.changement().ifPresent(modifications::fire); » après commit, dans le thread du PUT /standards.
```

**Impact** — Un PUT sur le référentiel déclenche synchroniquement RadarProjets puis RappelsRadar (lecture des inventaires, émission d'alertes) avant de répondre ; les exceptions sont avalées et journalisées (RadarProjets.java:196-198) : latence et échecs silencieux côté utilisateur. La règle de choix entre les deux styles n'est écrite nulle part.

**Action** — Documenter la règle en arc42 §8 (événement inter-modules = AFTER_SUCCESS ou @ObservesAsync) et l'appliquer à StandardModifie, AnnonceStandard et ImpactsRecorded.

### AUD-ARC-10 — La collecte HTTP est dispersée dans sept clients répartis sur cinq modules

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 7
- **Fusionne** : AUD-QUA-12
- **Dépend de** : AUD-SEC-01

**Vérification du 20/11 (54add78)** — La dispersion s'accroît ; aucune fabrique commune.

*Changement depuis le 17/11* : Un 7e client HTTP s'ajoute : ingestion/legi/ArchivesJorf.java:79 (collecte JORF, commit 3f2fdc7), avec la même configuration recopiée.

```text
grep -rn "HttpClient.newBuilder" -> vulnintel/EnrichmentHttp.java:36, scm/ForgeHttp.java:46, ingestion/web/RecuperateurWeb.java:180, aigateway/ollama/FournisseurOllama.java:59, ingestion/HttpSourceClient.java:33, ingestion/legi/ArchivesJorf.java:79, releaseradar/ClientEndoflife.java:72 (chaque fois connectTimeout + Redirect.NEVER) ; GardeAdresses toujours limitée à ingestion/web, signal/web, signal/opportunite.
```

**Preuve d'origine**

```text
grep -rln "HttpClient.newBuilder" -> ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, scm/ForgeHttp.java:36, vulnintel/EnrichmentHttp.java:36, releaseradar/ClientEndoflife.java:72, aigateway/ollama/FournisseurOllama.java:59 ; tous « followRedirects(NEVER) » et connectTimeout. arc42 §5.3 (05-vue-blocs.md:119) attribue la collecte à source-ingestion ; la garde d'adresses (ingestion/web/GardeAdresses.java) n'est utilisée que par la veille web.
[AUD-QUA-12] HttpClient.newBuilder() dans vulnintel/EnrichmentHttp.java:36, scm/ForgeHttp.java:36, releaseradar/ClientEndoflife.java:72, ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, aigateway/ollama/FournisseurOllama.java:59 (chaque fois connectTimeout + Redirect.NEVER, limites de taille et gestion d'InterruptedException réécrites). scm/RepositoryActivity.java:150-162 boucle de pagination manuelle alors que pages() (:326-337) fait la même chose.
[À 9d96406] scm/ForgeHttp.java:44 (au lieu de :36) ; 05-vue-blocs.md:120 ; toujours 6 HttpClient.newBuilder ; la collecte artiste réutilise RecuperateurWeb.
```

**Impact** — Configuration homogène aujourd'hui, mais chaque évolution (proxy, User-Agent, limites de taille, garde SSRF) doit être faite six fois ; vulnintel et releaseradar sont à la fois métier et adaptateurs.

**Action** — Fournir une fabrique de clients HTTP commune (timeouts, taille maximale, User-Agent, garde d'adresses optionnelle) dans le module transverse proposé en AUD-ARC-05 ; mettre à jour §5.3.

### AUD-ARC-11 — Aucun objet-dieu (max. 556 lignes, InventoryQueries) ; impact (2 905 l.) porte aussi la recherche plein texte que le package-info attribue à signal

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 9

**Vérification du 20/11 (54add78)** — Toujours aucun objet-dieu au sens strict ; la responsabilité recherche reste mal attribuée et impact concentre corrélation, traitement des impacts (V20), recherche, radar, opportunités et IA.

*Changement depuis le 17/11* : Le package-info de signal annonce toujours la recherche, portée par impact/RechercheVeille. impact (20 fichiers / 2 905 l.) redevient le plus gros module métier devant signal (22 / 2 744). Plus gros fichier : inventory/InventoryQueries.java 556 l. (236 à 9d96406 ; 12 méthodes publiques, 12 records, utilisé par api, mcp, impact) ; moyenne 106 l./fichier sur 269 fichiers.

```text
find io/argos -name '*.java' | xargs wc -l | sort -n | tail -> InventoryQueries.java 556, mcp/OutilsSecurite.java 458, impact/CorrelationEngine.java 440, scm/RepositoryActivity.java 439, regintel/CollecteReglementaire.java 411 ; signal/package-info.java:4 « Signaux normalisés, taxonomie, déduplication, recherche » ; impact/RechercheVeille.java:213 (lit project_impact, project, signal).
```

**Preuve d'origine**

```text
find io/argos -name '*.java' | xargs wc -l | sort -n | tail -> plus gros fichier scm/RepositoryActivity.java 394 lignes, puis workspace/EspacesService.java 383, mcp/OutilsSecurite.java 383 ; moyenne 87 lignes/fichier. impact = 1 919 lignes/13 fichiers (CorrelationEngine, RadarProjets, RechercheVeille, RapprochementActualites, AnalyseComplementaireIa). signal/package-info.java : « Signaux normalisés, taxonomie, déduplication, recherche » alors que la recherche est impact/RechercheVeille.java (lit les tables signal et actualite, l.218, 238).
[À 9d96406] signal 20 fichiers / 2 550 l. > impact 16 / 2 159 l. : la partie « impact le plus dense » ne tient plus.
```

**Impact** — Taille des classes saine ; les responsabilités d'impact débordent toutefois (corrélation, recherche, radar, IA), ce qui en fait le point de convergence le plus exposé aux changements de signal et d'inventory.

**Action** — Corriger le package-info de signal (ou déplacer la recherche dans un sous-paquet dédié) et consigner en §5.3 que impact porte la recherche plein texte.

### AUD-ARC-12 — Le contrat client de l'IHM est écrit à la main et partiel (11 racines REST sur 23), sans génération ni test de contrat ; le point d'appel HTTP est désormais unique

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 5
- **Fusionne** : AUD-QUA-13

**Vérification du 20/11 (54add78)** — La divergence de gestion d'erreur est corrigée ; le contrat reste écrit à la main, sans génération ni test de contrat côté IHM, et couvre 11 racines @Path sur 23.

*Changement depuis le 17/11* : Fonctions HTTP factorisées dans un seul appelerApi (lecture du corps Erreur, délai, annulation, jeton) : getJson/postJson n'en sont plus que des alias (client.ts:81-134, commits 9f138cb, e4908ad). Le client couvre davantage de ressources (recherche, vues, abonnements, impacts, inventaire), réparties dans api/*.ts.

```text
argos-ui/src/api/client.ts:81 « appelerApi », :128-134 alias ; types manuels : client.ts:198-202 « interface AlerteVue … nature: string … etat: string », contenu/alertes-poste.ts:25 « interface AlerteRecue » (doublon), api/veille.ts:9 « schéma OpenAPI PreferencesEffectives » recopié ; aucune occurrence de « openapi » dans argos-ui/package.json ni argos-ui/tests ; grep '@Path("/api/v1' -> 23 racines, dont non appelées par l'IHM : espaces, invitations, me/watch, ops/sources, preferences/rubriques, profils/artiste, projects/{id}/activite, radar, reglementaire, veille-web ; message(e) toujours recopiée (views/AffichageView.vue:22, views/AlertesView.vue:44).
```

*Réserve* : Les tests OpenApiContractTest et ContratOpenApiTest (serveur, déjà présents à 9d96406) figent quelques chemins, pas les types de l'IHM.

**Preuve d'origine**

```text
argos-ui/src/api/client.ts : 8 fonctions exportées pour 18 classes @Path("/api/v1…") côté serveur ; types recopiés « schéma OpenAPI PreferencesEffectives » (l.58) sans génération ; getJson (l.32), putJson (l.83), postJson (l.169) dupliquées, seul putJson lit le corps Erreur (l.96). Chemins utilisés vérifiés présents côté serveur (api/AlertesResource.java:32,45-46,52,60).
[AUD-QUA-13] argos-ui/src/api/client.ts:32-50 getJson, :83-109 putJson, :169-188 postJson (même minuterie, même try/catch) ; seul putJson (:93-100) lit le message d'erreur du serveur. Types : client.ts:150 « interface AlerteVue » et contenu/alertes-poste.ts:22 « interface AlerteRecue » décrivent le même schéma AlerteVue ; « nature: string », « etat: string » au lieu d'unions. Fonction message(e) recopiée dans views/AffichageView.vue:27 et views/AlertesView.vue:36.
[À 9d96406] client.ts : 8 fonctions, inchangé ; 20 classes @Path (ajout de ProfilArtisteResource.java:66 et RubriquesResource.java:27).
```

**Impact** — Une évolution d'un record Java (AlerteVue, PreferencesEffectives) ne casse aucune compilation côté IHM ; dérive silencieuse du contrat dès que l'IHM s'étoffera.

**Action** — Générer les types depuis /q/openapi (openapi-typescript) en build, ou ajouter un test de contrat ; factoriser une seule fonction request() (méthode, corps, lecture d'Erreur, futur jeton OIDC).

### AUD-ARC-14 — Le socle d'architecture repose sur des ADR fondateurs restés « Proposé » (14 sur 33, dont 0001, 0013, 0014, 0016) alors que le code les applique

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Architecture, Documentation · **Effort** : S · **Sprint** : 4
- **Fusionne** : AUD-DOC-09
- **Bloque** : AUD-ARC-05, AUD-DOC-01, AUD-DOC-08

**Vérification du 20/11 (54add78)** — Les ADR récents sont actés, mais les ADR fondateurs que le code applique depuis le début restent « Proposé ».

*Changement depuis le 17/11* : Commit 18bb454 « docs(adr): accepter ADR-0023 à ADR-0033 » : 0026 à 0029 acceptés, 0004 et 0006 marqués Remplacé (par 0029 et 0028), 0018 remplacé par 0030 ; 0030 à 0033 créés et acceptés.

```text
sed -n 1,8p docs/architecture/adr/0*.md : 15 Accepté, 4 Remplacé, 14 Proposé sur 33, dont 0001-monolithe-modulaire.md:3 (cité par ModuleBoundariesTest.java:21), 0013-correlation-deterministe, 0014-mcp-readonly-streamable-http, 0016-workspace-rbac-rls, 0008-iam-oidc, 0022-client-desktop-electron (desktop livré). Aucun ADR du noyau commun (prérequis d'AUD-ARC-05).
```

**Preuve d'origine**

```text
grep -m1 Statut docs/architecture/adr/0*.md -> 22 ADR sur 29 « Proposé », dont 0001-monolithe-modulaire.md:3 (cité en tête de ModuleBoundariesTest.java:21) et 0016-workspace-rbac-rls.md:3. 0028-collecteur-veille-web-integre.md:6 et 0029-passerelle-ia-ollama-local.md:6 remplacent 0006 et 0004 « à l'acceptation », alors que io.argos.ingestion.web et io.argos.aigateway.ollama sont livrés.
[AUD-DOC-09] docs/architecture/adr/0021-double-licence.md:3 (Statut : Proposé) et :16 (« aucune dépendance n'est encore déclarée dans le dépôt ») ; argos-server/README.md:19 et :32 (contrôle bloquant « ADR-0021 point 4 »), :59 (194 dépendances inventoriées) ; Dockerfile:24-25 (mvn -P licences au build) ; e2e/README.md:112 et docs/architecture/quality/audit-accessibilite-mises-en-page.md:15 (axe-core écarté « ADR-0021 ») ; ADR-0025:40, :50 et :62 demandent de revoir ADR-0009 et ADR-0021, sans ADR de suite. Cas particulier, apparu avec le lot licences, de la famille décrite par AUD-ARC-14.
```

**Impact** — Les décisions effectivement en production ne sont pas actées ; ADR-0004 (Claude) et ADR-0006 (FreshRSS séparé) restent formellement en vigueur et contredisent le code, ce qui alimente l'écart documentaire (AUD-ARC-03).

**Action** — Passer en « Accepté » les ADR implémentés (0001, 0013, 0014, 0016, 0027, 0028, 0029…), marquer 0004 et 0006 « Remplacé », et mettre à jour adr/README.md. Rédiger l'ADR du noyau commun (prérequis d'AUD-ARC-05).

### AUD-ARC-15 — Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 6

**Vérification du 20/11 (54add78)** — Toujours aucun client pour les points d'entrée du profil artiste ; le lien de rappel vers une page inexistante subsiste et n'est pas consigné.

*Changement depuis le 17/11* : Aucun ajout dans argos-ui pour /profils/artiste ni /preferences/rubriques ; la capacité est désormais déclarée « livrée » (docs/specifications/profil-artiste.md:8) ; l'écart « Pas d'écran du profil artiste » était déjà consigné à 9d96406.

```text
grep -rn "profils/artiste\|preferences/rubriques" argos-ui/src -> aucun résultat ; alerting/RappelsOpportunites.java:133 « lienBase … + "/profils/artiste/opportunites/" + r.id() » ; argos-ui/src/router.ts : aucune route « opportunites » ; profil-artiste.md:133,138 (écarts consignés, sans le lien mort).
```

*Réserve* : L'absence d'écran est consignée dans la fiche (écart), mais pas arbitrée par un ADR ; le lien mort ne l'est pas.

**Preuve d'origine**

```text
git diff --stat f58a68c HEAD : aucun fichier sous argos-ui/ ni argos-desktop/ ; api/artiste/ProfilArtisteResource.java:157-291 (GET listes, GET/PUT suivis, GET opportunites, GET resume, sources, collectes), api/preference/RubriquesResource.java:27,49 ; argos-ui/src/api/client.ts : 8 fonctions, aucune pour /profils/artiste.
```

**Impact** — La fonction n'est utilisable que par l'API avec un jeton : comme ARC-02, aucune preuve de bout en bout. L'écart entre contrat serveur et client (ARC-12) se creuse. Le lien des rappels sans lien officiel vise une page /profils/artiste/opportunites/<id> qui n'existe pas (RappelsOpportunites.java, contenu()).

**Action** — Ajouter les vues artiste (suivis, opportunités) dans argos-ui une fois ARC-02 traité, ou consigner l'écart dans docs/specifications/profil-artiste.md (« Écarts constatés »).

### AUD-ARC-19 — L'événement AlerteEmise est publié en cinq points sans aucun observateur

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 7
- **Nouveau** le 20/11

**Preuve**

```text
emises.fire(new AlerteEmise(…)) dans alerting/MoteurAlertes.java:159, alerting/RappelsRadar.java:244, alerting/ResumesPeriodiques.java:109, alerting/RappelsReglementaires.java:112 (nouveau), alerting/RappelsOpportunites.java:124 ; grep -rn "@Observes.*AlerteEmise" argos-server/src -> aucun résultat ; alerting/AlerteEmise.java:5-8 (javadoc) « Le changement notifier-sur-le-poste peut l'observer » alors que le poste lit les alertes par interrogation (05-vue-blocs.md:46). Déjà présent (4 émissions) à 9d96406, non relevé par l'audit initial.
```

**Impact** — Point d'extension mort recopié à chaque nouveau type de rappel ; il laisse croire qu'une diffusion en temps réel existe et coûte une résolution d'observateurs CDI à chaque alerte.

**Action** — Supprimer l'événement et ses cinq émissions, ou le brancher (diffusion SSE au desktop) et le documenter dans alertes-digests.md ; à défaut, consigner qu'il est réservé.

### AUD-QUA-08 — Le typage entre modules repose sur des chaînes et des tableaux : niveau d'impact en String alors que l'énumération existe, String[] comme couple de retour, clé composite découpée par split(":")

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 4

**Vérification du 20/11 (54add78)** — Niveau d'impact, statuts d'import et résumé IA restent typés par chaînes et tableaux ; nouveau switch sur chaînes avec défaut silencieux.

*Changement depuis le 17/11* : Inchangé sur les preuves d'origine (seuls les numéros de ligne ont bougé) ; le motif s'étend au nouveau code : inventory/ImportPlanifie.java:81-87 classe les statuts d'import par switch sur chaînes avec « default -> conserves++ » (un statut inconnu ou renommé est compté comme conservé sans bruit) ; regintel/CollecteReglementaire.java:151 et :159 comparent « "COLLECTE" » et « "ABANDONNE" » en chaînes. Point positif : impact/EtatImpact.java est une vraie énumération (V20).

```text
impact/MatchRules.java:27 « enum Level » vs impact/ImpactQueries.java:46 « String level » ; alerting/MoteurAlertes.java:147, :246-278 « new String[] {..., "INDISPONIBLE"} », :164 « cle.split(":") », :192 « "A_VERIFIER".equals », :235-236 « case "EXACT"/"PROBABLE" » ; impact/CorrelationEngine.java:150-162 « "NONE"/"DONE"/"UNAVAILABLE" » ; inventory/InventoryService.java:42 « String status » ; inventory/ImportPlanifie.java:81-87.
```

**Preuve d'origine**

```text
impact/MatchRules.java:23 « public enum Level { EXACT, PROBABLE, A_VERIFIER } » mais impact/ImpactQueries.java:34 « String level » ; alerting/MoteurAlertes.java:191 « !"A_VERIFIER".equals(i.level()) », :233-236 « case "EXACT" -> 0; case "PROBABLE" -> 1; » ; :146 et :262-271 « new String[] {texte, "DISPONIBLE"} » ; :163 « String[] fc = cle.split(":") » ; impact/CorrelationEngine.java:106-121 statuts « "NONE" », « "DONE" », « "UNAVAILABLE" » ; inventory/InventoryService.java:30 « String status » (IMPORTED, REJECTED...).
```

**Impact** — Une faute de frappe ou un nouveau niveau n'est détecté qu'à l'exécution ; le classement rangNiveau() renvoie 2 par défaut pour toute valeur inconnue, ce qui masquerait silencieusement un renommage.

**Action** — Exposer MatchRules.Level (ou un type partagé) dans ImpactView, remplacer String[] par un record ResumeIa(texte, etat) et la clé « fréquence:canal » par un record ; convertir les statuts en enums sérialisés par Jackson.

### AUD-QUA-09 — Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 9

**Vérification du 20/11 (54add78)** — Mélange français/anglais inchangé et étendu aux nouvelles tables et classes.

*Changement depuis le 17/11* : Aucune convention écrite (aucun ADR de nommage : docs/architecture/adr s'arrête à 0033, grep "nommage|langue du code" -> 0). Le delta prolonge le mélange : V19 crée project_repository, repository_inventory, component_occurrence, V20 project_impact_transition, tandis que V16 crée texte_officiel, fiche_reglementaire, domaine_metier, reg_source, reg_parametres ; ProjectDirectory mêle avecDepots()/ajouterDepot()/depots() et withRepository()/find()/create() ; ImportPlanifie (classe française) injecte projects, inventories, queries, store.

```text
argos-server/src/main/resources/db/migration/V16__veille_reglementaire.sql, V19__depots_projet_et_occurrences.sql, V20__etat_traitement_impacts.sql (CREATE TABLE) ; inventory/ProjectDirectory.java:56,91,98,122,140,157 ; inventory/ImportPlanifie.java:40-50.
```

**Preuve d'origine**

```text
Tables (migrations) : « espace », « appartenance » mais « workspace_vue », « workspace_preference_utilisateur », « subscription », « project_impact » ; colonnes d.workspace_id vs a.espace_id (RadarProjets.java:215, Destinataires.java:41). Classes : impact/ComplementaryAnalysis.java et impact/AnalyseComplementaireIa.java ; ImpactQueries, CorrelationEngine, SyncStore, InventoryService à côté de RechercheVeille, MoteurAlertes, EspacesService ; MoteurAlertes.java:175 « impacts.forSignals(...) » / « abonnements.activeFor(...) ». Aucune convention de langue dans docs/architecture (grep -rli "nommage|langue du code" -> aucun ADR).
```

**Impact** — Recherche dans le code et dans le schéma plus difficile (espace ou workspace ? projet ou project ?), risque de doublons conceptuels, coût d'entrée pour un contributeur.

**Action** — Écrire une courte règle (ADR ou arc42 §8) : domaine en français, termes techniques et API externes en anglais par exemple ; appliquer la règle au nouveau code et renommer progressivement les identifiants ambigus (espace/workspace) lors des migrations déjà prévues.

### AUD-QUA-10 — Horloge injectable seulement dans CollecteWeb et LimiteurDebit : 63 appels à now() côté Java (37 fichiers) sans Clock commun, mêlés à 47 now() SQL, et deux LocalDate.now() sans fuseau

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 3
- **Bloque** : AUD-TST-06

**Vérification du 20/11 (54add78)** — Horloge toujours non injectée ; dispersion en hausse.

*Changement depuis le 17/11* : Aggravé : 37 -> 63 appels Java à now() (23 -> 37 fichiers), toujours aucun java.time.Clock ; 45 -> 47 now() SQL ; les deux LocalDate.now() sans fuseau sont toujours là (OutilsReferentiel.java:98, ReferentielEspace.java:138). Les nouveaux points d'entrée planifiés suivent le bon motif « instant passé en paramètre » (ImportPlanifie.java:55, CollecteReglementaire.executer(..., maintenant)).

```text
grep -rnE "(Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime)\.now\(" argos-server/src/main/java | wc -l -> 63 (37 fichiers) ; grep -rnE "\bClock\b" -> 0 ; mcp/OutilsReferentiel.java:98 et catalog/standard/ReferentielEspace.java:138 « LocalDate.now() » ; horloges injectables seulement signal/web/CollecteWeb.java:81 et api/security/LimiteurDebit.java:31.
```

**Preuve d'origine**

```text
grep -rnE "(Instant|LocalDate|OffsetDateTime)\.now\(\)" argos-server/src/main/java | wc -l -> 31 (19 fichiers), grep "Clock" -> 0. Décisions métier prises avec l'heure système : workspace/EspacesService.java:254 (expiration d'invitation), inventory/InventoryQueries.java:214 (SBOM périmée), vulnintel/KevSource.java:65 (cache), api/recherche/TraductionCriteres.java:117. catalog/standard/ReferentielEspace.java:138 et mcp/OutilsReferentiel.java:100 « LocalDate.now() » (fuseau JVM) alors que RappelsRadar.java:93 utilise « LocalDate.now(ZoneId.of(fuseau)) » (argos.alerting.fuseau). 42 « now() » SQL (ex. StockActualites.java:88, :250) côtoient Instant.now() Java dans la même mise à jour (StockActualites.java:248-252).
[À 9d96406] 37 now() Java (23 fichiers), 45 now() SQL ; LocalDate.now() sans fuseau inchangés (ReferentielEspace.java:138, OutilsReferentiel.java:100) ; le titre est à corriger (CollecteWeb.java:81).
```

**Impact** — Tests dépendants du temps réel pour ces chemins ; deux horloges (JVM et PostgreSQL) dans une même règle ; la date « du jour » dépend de la variable TZ du conteneur (compose.yaml:54) et non du fuseau configuré. Le schéma déjà présent « point d'entrée planifié -> méthode recevant maintenant » (MoteurAlertes.java:126, RappelsRadar.java:93) limite le problème.

**Action** — Produire un bean java.time.Clock (fuseau argos.alerting.fuseau) et l'injecter à la place de Instant.now()/LocalDate.now() ; passer l'instant en paramètre SQL plutôt que now() là où la règle compare avec une valeur calculée en Java.

### AUD-QUA-11 — Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 3

**Vérification du 20/11 (54add78)** — Les trois exécuteurs statiques hors CDI existent toujours, sans arrêt.

*Changement depuis le 17/11* : Inchangé (SyntheseActivite passe de (2,2) à (1,2) fils, même motif statique).

```text
alerting/MoteurAlertes.java:76 « private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(2, 2, ...) » ; projectintel/SyntheseActivite.java:46 (ThreadPoolExecutor(1, 2, ...)) ; ingestion/web/RecuperateurWeb.java:51 « Executors.newSingleThreadScheduledExecutor » ; grep -rnE "@PreDestroy|shutdown|ManagedExecutor" argos-server/src/main/java -> 0.
```

**Preuve d'origine**

```text
alerting/MoteurAlertes.java:75 « private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(2, 2, ...) » ; projectintel/SyntheseActivite.java:46 (même motif recopié) ; ingestion/web/RecuperateurWeb.java:51 « Executors.newSingleThreadScheduledExecutor » ; grep -rn "shutdown\|@PreDestroy\|ManagedExecutor" -> aucun résultat.
```

**Impact** — Les fils cœur ne s'arrêtent jamais : fuite de fils à chaque rechargement à chaud en développement ; tâches perdues sans trace à l'arrêt ; contexte CDI et métriques Quarkus absents dans ces fils ; dimensionnement codé en dur et dupliqué.

**Action** — Utiliser un ManagedExecutor (SmallRye Context Propagation) ou un bean @ApplicationScoped qui possède l'exécuteur avec @PreDestroy shutdownNow(), taille configurable ; factoriser le motif « soumettre + get(délai) + annuler » partagé par MoteurAlertes et SyntheseActivite.

### AUD-QUA-18 — La taille et la complexité du serveur progressent : 19 fichiers Java de plus de 300 lignes (9 à 9d96406), 10 méthodes de complexité cyclomatique ≥ 15 (8), et deux méthodes longues encore allongées malgré les commits de découpage

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : M · **Sprint** : 9
- **Nouveau** le 20/11

**Preuve**

```text
find argos-server/src/main/java -name '*.java' | xargs wc -l : 28 436 lignes / 269 fichiers (20 574 / 228 à 9d96406), plus gros inventory/InventoryQueries.java 556, mcp/OutilsSecurite.java 458, impact/CorrelationEngine.java 440. Script /home/claude/audit-maj/build-QUAL/cc.py (dérivé de qual-scripts/longmeth.py) : catalog/profil/ValidateurManifeste.java:24 CC≈32 / 74 l. ; scm/SbomDocument.java:63 reconnaitre CC≈28 / 88 l. (nouvelle) ; workspace/subscription/SubscriptionService.java:239 CC≈25 ; api/recherche/TraductionCriteres.java:71 CC≈24 ; regintel/CollecteReglementaire.java:132 executer CC≈20 / 81 l. (nouvelle) ; regintel/ExpertiseFiches.java:72 CC≈20. Allongées : impact/CorrelationEngine.java:200 deterministic 80 -> 96 l., signal/SignalRecorder.java:64 record 67 -> 86 l. ; alerting/ResumesPeriodiques.java:119 composerPour toujours 100 l. Point positif : scm/RepositoryActivity github/gitlab découpées (606dcc8).
```

**Impact** — Les zones critiques (corrélation, enregistrement des signaux, reconnaissance des SBOM, collecte réglementaire) concentrent la logique dans des méthodes longues et très ramifiées : revue et tests plus coûteux, risque de régression sur les branches rares.

**Action** — Découper en étapes nommées SbomDocument.reconnaitre (une méthode par format), CollecteReglementaire.executer (index, boucle par jour, bilan), CorrelationEngine.deterministic et SignalRecorder.record ; fixer un seuil indicatif (ex. 60 lignes, CC 15) vérifié en CI (Checkstyle/PMD ou ArchUnit) pour éviter la dérive.

### AUD-QUA-19 — Les 24 fichiers TypeScript de e2e/ (3 485 lignes) ne sont soumis à aucune vérification de types : pas de tsconfig ni de dépendance typescript ; un tsc --strict révèle 6 erreurs

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 1
- **Nouveau** le 20/11

**Preuve**

```text
ls e2e -> aucun tsconfig*.json ; e2e/package.json : devDependencies limitées à @playwright/test 1.61.1, scripts test/test:seul/test:accessibilite/test:performance sans tsc (Playwright transpile sans vérifier les types). Commande sur copie (/home/claude/audit-maj/build-QUAL/e2e, tsconfig.audit.json strict, moduleResolution Bundler) : npx tsc -p tsconfig.audit.json -> 6 erreurs : support/donnees.ts:9 (TS7016, import de ./docker.mjs sans déclaration), support/electron.ts:299, :319, :326 (TS7006, paramètres implicitement any). 17 « as unknown as » (16 dans e2e/support/electron.ts:151-293, 1 dans e2e/desktop/connexion.spec.ts:146).
```

**Impact** — Une erreur de typage dans les aides ou les parcours E2E (signature de fixture, forme d'une réponse d'API) n'apparaît qu'à l'exécution de la campagne, longue (pile Docker, Keycloak, Electron) ; les doubles Electron reposent sur des conversions forcées non vérifiées.

**Action** — Ajouter e2e/tsconfig.json (strict, noEmit) et typescript en devDependency, un script « typecheck » exécuté avant « test » ; déclarer docker.mjs (docker.d.mts) et typer les callbacks webRequest ; regrouper les conversions __argosE2E derrière une seule fonction typée.

### AUD-SEC-04 — Alertes e-mail envoyées à l'adresse du jeton même non vérifiée

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 5

**Vérification du 20/11 (54add78)** — L'adresse brute du jeton reste enregistrée et sert désormais aussi aux rappels réglementaires.

*Changement depuis le 17/11* : Inchangé à la création du compte ; un nouveau chemin d'envoi réutilise compte.email : Destinataires.membresAvecProfil (lot réglementaire), appelé par RappelsReglementaires.

```text
argos-server/src/main/java/io/argos/workspace/ComptesService.java:74 `ps.setString(4, identite.email());` (alors que :54 calcule emailVerifieNormalise) ; argos-server/src/main/java/io/argos/alerting/Destinataires.java:40 et :64 `SELECT ... c.email FROM appartenance a JOIN compte c` ; argos-server/src/main/java/io/argos/alerting/RappelsReglementaires.java:93.
```

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/workspace/ComptesService.java:74 `ps.setString(4, identite.email());` (adresse brute, alors que seule emailVerifieNormalise() sert aux invitations) ; alerting/Destinataires.java:40 `SELECT DISTINCT c.id, c.email FROM appartenance a JOIN compte c ...` utilisé par CanalEmail.
```

**Impact** — Un utilisateur dont l'e-mail n'est pas vérifié par Keycloak (modifiable par l'utilisateur selon la configuration du royaume) peut faire envoyer les alertes de ses espaces (titres, noms de projets) à un tiers : relais de courriels non sollicités au nom du NAS.

**Action** — N'enregistrer comme adresse d'envoi que l'e-mail vérifié (emailVerifieNormalise) et le mettre à jour à la connexion ; ignorer le canal EMAIL tant qu'aucune adresse vérifiée n'est connue.

### AUD-SEC-05 — Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 3

**Vérification du 20/11 (54add78)** — Toujours aucune CSP, nosniff, Referrer-Policy ni frame-ancestors servis par Quarkus.

*Changement depuis le 17/11* : Aucun en-tête ajouté côté serveur. L'IHM web tient désormais des jetons porteurs en mémoire JavaScript (ADR-0033) : une injection de script y vaudrait vol de jeton.

```text
`grep -rn 'quarkus.http.header\|Content-Security-Policy\|ContainerResponseFilter' argos-server/src/main` : rien. Seules CSP : argos-desktop/src/ui-protocol.js:16-33 et la page de retour du desktop (argos-desktop/src/connexion.js:26-32).
```

*Réserve* : Risque toujours borné par l'absence de v-html (AUD-SEC-19) ; à traiter avant que le portail web servi par l'image soit utilisable (voir angle mort sur la règle /*).

**Preuve d'origine**

```text
argos-server/src/main/resources/application.properties : `grep -n 'quarkus.http.header' application.properties` ne renvoie rien ; aucune CSP, X-Content-Type-Options, Referrer-Policy, frame-ancestors ni HSTS côté serveur (la CSP n'existe que dans le desktop, argos-desktop/src/ui-protocol.js).
```

**Impact** — L'IHM web n'a pas de défense en profondeur contre l'injection de script ni le clickjacking ; en accès direct (ARGOS_BIND=0.0.0.0) rien n'impose HTTPS. Risque borné : aucun v-html dans l'IHM (voir constat Info).

**Action** — Ajouter via quarkus.http.header.* (ou filtre) : Content-Security-Policy alignée sur celle du desktop, X-Content-Type-Options: nosniff, Referrer-Policy: no-referrer, frame-ancestors 'none' ; HSTS au reverse proxy DSM.

### AUD-SEC-06 — Mot de passe de base de données par défaut « argos » si .env absent

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité, Dépendances · **Effort** : S · **Sprint** : 2
- **Fusionne** : AUD-DEP-10

**Vérification du 20/11 (54add78)** — Le repli « argos » subsiste pour la base et l'application.

*Changement depuis le 17/11* : Aucun commit sur compose.yaml dans le delta qui touche ce repli (`git log 9d96406..HEAD -- compose.yaml` : uniquement documentation/licences).

```text
compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` et :44 `ARGOS_DB_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}`.
```

**Preuve d'origine**

```text
compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` (et :44 pour l'application) ; .env.example:6 valeur d'exemple à changer.
[AUD-DEP-10] compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` et :44 idem ; docs/deploiement/synology-et-pc.md:92 « défaut argos ».
```

**Impact** — Si le fichier .env est oublié, la base démarre avec un mot de passe trivial. Risque borné : port publié sur 127.0.0.1 seulement (compose.yaml:23) et réseau interne au projet.

**Action** — Remplacer le repli par `${ARGOS_DB_PASSWORD:?ARGOS_DB_PASSWORD obligatoire}` pour refuser le démarrage sans mot de passe explicite.

### AUD-SEC-07 — Sauvegardes complètes de la base écrites en clair avec les permissions par défaut

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 2
- **Dépend de** : AUD-QUA-02

**Vérification du 20/11 (54add78)** — Pas d'umask, pas de chiffrement.

*Changement depuis le 17/11* : Script inchangé (aucun commit sur scripts/sauvegarder-base.sh dans le delta).

```text
scripts/sauvegarder-base.sh:19 `mkdir -p "$DEST"`, :22 `pg_dump ... | gzip > "$FILE"`.
```

**Preuve d'origine**

```text
scripts/sauvegarder-base.sh:19 `mkdir -p "$DEST"` et :22 `pg_dump ... | gzip > "$FILE"` exécutés en root sur le NAS (commentaire l.4), sans umask ni chiffrement ; copie hors NAS recommandée (docs/deploiement/synology-et-pc.md:68).
```

**Impact** — Les dumps (comptes, e-mails, projets, journaux) sont lisibles par d'autres comptes du NAS selon l'umask et voyagent en clair vers le stockage de sauvegarde.

**Action** — `umask 077` en tête du script, dossier en 700 ; chiffrer le dump (age/gpg) ou activer le chiffrement côté Hyper Backup.

### AUD-SEC-09 — Recherche plein texte et veille personnelle (/me/veille) sans limite de débit

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 5

**Vérification du 20/11 (54add78)** — Toujours aucune limite sur la recherche plein texte ; périmètre élargi à la veille personnelle.

*Changement depuis le 17/11* : La recherche n'a toujours pas de limiteur ; la nouvelle route GET /api/v1/me/veille (union des profils, abonnements personnels et fiches) n'en a pas non plus. LimiteurDebit n'est utilisé que par ActiviteResource, VeilleWebResource, ProfilArtisteResource, ReglementaireResource et MCP.

```text
argos-server/src/main/java/io/argos/api/recherche/RechercheResource.java:57 `rechercher(...)` ; api/recherche/VuesResource.java:115 `resultats(...)` ; argos-server/src/main/java/io/argos/api/veille/VeilleProfilsResource.java:139 `veille()` ; `grep -rln LimiteurDebit argos-server/src/main/java` : ni recherche ni veille.
```

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/recherche/RechercheResource.java:48 `rechercher(...)` et VuesResource `/{id}/resultats` : aucun LimiteurDebit (présent seulement sur veille web, synthèse d'activité et MCP).
```

**Impact** — Un compte peut enchaîner des requêtes plein texte coûteuses sur le processeur du NAS (dégradation pour les autres utilisateurs).

**Action** — Appliquer LimiteurDebit (ex. 60/min/compte) aux routes de recherche, ou une limite globale au reverse proxy.

### AUD-SEC-23 — Desktop : l'écoute de bouclage 127.0.0.1 accepte le premier GET /argos/retour sans comparer le state et se ferme (interruption de connexion par un processus local)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 5
- **Nouveau** le 20/11

**Preuve**

```text
argos-desktop/src/connexion.js:99-102 `retourAttendu` ne teste que méthode, chemin et écoute active ; :142-150 toute requête conforme reçoit la page de retour puis `fermer(url.search...)` ; le state est pourtant connu du processus principal (URL validée à :172, paramètre `state` recopié à :69-72) ; la vérification du state n'a lieu que dans l'IHM (argos-ui/src/session/oidc.ts:148), qui rejette alors le retour et abandonne la tentative.
```

**Impact** — Pendant la fenêtre de connexion (5 min au plus, port éphémère), un processus local ou toute source capable d'émettre un GET vers 127.0.0.1 (balayage de ports) fait échouer la connexion en cours. Aucun vol de jeton : state vérifié côté IHM et code inutilisable sans le vérificateur PKCE. Déni de service ponctuel seulement.

**Action** — Mémoriser le state de l'URL autorisée dans l'écoute et ne clore l'écoute que sur un retour portant ce state (comparaison en temps constant) ; répondre 400 aux autres sans fermer ; ajouter le cas à argos-desktop/test/connexion.test.js.

### AUD-SEC-24 — Campagne de performance e2e : serveur lancé par « mvn.cmd » via le PATH avec shell: true sous Windows, contrairement à la règle des chemins absolus

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 1
- **Nouveau** le 20/11

**Preuve**

```text
e2e/performance/mesurer-p95.mjs:117-120 `const mvn = platform() === 'win32' ? 'mvn.cmd' : 'mvn'` puis `spawn(mvn, ['-B','-q','-f', join(RACINE, 'argos-server','pom.xml'), ...], { ..., shell: platform() === 'win32' })` ; commentaire :77-78 « Exécutables appelés par leur chemin absolu ... la campagne ne dépend pas du PATH » ; docker, git et taskkill le sont (support/docker.mjs:5-19, mesurer-p95.mjs:79-84,95-96).
```

**Impact** — Poste du développeur uniquement : un mvn.cmd placé plus tôt dans le PATH est exécuté ; avec shell: true, le chemin du dépôt (RACINE) est concaténé sans échappement dans la ligne de cmd.exe : un chemin contenant espaces, & ou ^ casse la commande ou injecte une commande. Pas d'impact sur le serveur déployé.

**Action** — Résoudre mvn comme docker/git (emplacements fixes + variable ARGOS_E2E_MVN) et l'appeler sans shell (sous Windows : `cmd.exe /d /s /c` avec arguments échappés, ou lancer directement java/quarkus-run) ; même revue pour le webServer de playwright.config.ts:89.

### AUD-SEC-25 — Image : code de l'application modifiable par l'utilisateur d'exécution et conteneurs sans durcissement (read_only, cap_drop, no-new-privileges)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 2
- **Nouveau** le 20/11

**Preuve**

```text
Dockerfile:48 `COPY --from=server --chown=1001:0 /src/argos-server/target/quarkus-app/ ./` puis :51 `USER 1001` (jars et lib appartiennent à l'utilisateur qui exécute la JVM ; présent dès 9d96406, non relevé) ; compose.yaml:34-59 (service argos) sans `read_only`, `cap_drop`, `security_opt: [no-new-privileges:true]`.
```

**Impact** — Une exécution de code dans la JVM (dépendance vulnérable) pourrait réécrire durablement les jars de l'application dans le conteneur ; défense en profondeur absente avant l'exposition publique. Risque d'hygiène, pas de vecteur direct identifié.

**Action** — Copier quarkus-app sans --chown (root, lecture seule pour 1001), comme déjà fait pour /opt/argos/licenses (Dockerfile:43-50) ; ajouter au service argos `read_only: true` + tmpfs /tmp, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`.

### AUD-TST-02 — Pas de seuil de couverture : JaCoCo absent du pom, la couverture (85,5 % des lignes, 71,7 % des branches à 54add78) n'est mesurable qu'à la main

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01
- **Bloque** : AUD-TST-04

**Vérification du 20/11 (54add78)** — Aucun seuil ni rapport de couverture dans le build.

*Changement depuis le 17/11* : Aucun changement : pas de JaCoCo ni de quarkus-jacoco ; seul maven-surefire-plugin (configuration java.util.logging.manager).

```text
argos-server/pom.xml:199-207 (maven-surefire-plugin seul) ; grep -n jacoco argos-server/pom.xml -> aucun résultat
```

*Réserve* : La couverture à 54add78 dépend de la mesure JaCoCo que l'orchestrateur fait lancer sur le poste du mainteneur.

**Preuve d'origine**

```text
argos-server/pom.xml:177-185 (seul plugin de test : maven-surefire-plugin, configuration limitée à java.util.logging.manager) ; grep -n jacoco pom.xml -> aucun résultat
```

**Impact** — Aucune alerte si la couverture recule sur un module critique ; la mesure de cet audit a dû être lancée par une commande ajoutée à la main.

**Action** — Ajouter jacoco-maven-plugin (prepare-agent + report, compatible @QuarkusTest via quarkus-jacoco) et un check avec seuils par paquet (au moins api.security, mcp, workspace, alerting) une fois la CI en place.

### AUD-TST-04 — Couverture inégale : cinq modules sans classe de test dédiée, dont workspace (26 fichiers, 1 759 lignes) et inventory (11 fichiers, 1 863 lignes) ; scm désormais testé

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Tests · **Effort** : M · **Sprint** : 6
- **Dépend de** : AUD-TST-02

**Vérification du 20/11 (54add78)** — Partiel : scm est corrigé (4 classes unitaires et 2 de contrat) et sa couverture passe de 26,9 % à 51,8 % des lignes (41,3 % des branches) d'après la mesure JaCoCo du 20/11 ; cinq modules restent sans classe de test dédiée (workspace, inventory, vulnintel, releaseradar, audit) mais sont couverts à 81–89 % des lignes par les parcours.

*Changement depuis le 17/11* : scm est désormais testé de façon ciblée : 4 classes unitaires (AccesForgesTest, DemandesEvolutionTest, DepotGithubTest, SbomDocumentTest ; 12 méthodes) et 2 classes de contrat (chain/ScmContractTest 12, chain/ScmContratEnregistreTest 13) couvrant 404, 403 quota épuisé, limite de débit principale et secondaire, redirection non suivie, taille maximale, jeton absent des messages (commits d22f5f4, a6d303c, d9ec9e3). regintel a 2 classes (15 méthodes, commit 3f2fdc7). En revanche, cinq modules restent sans classe de test dédiée : workspace (26 fichiers, 1 759 l.), inventory (11 fichiers, 1 863 l., plus du double de 9d96406 ; couvert seulement par chain/InventoryTest, ConsultationInventaireTest, ImportPlanifieTest, ProjetMultiDepotsTest), vulnintel (10/562), releaseradar (5/530), audit (4/186). SubscriptionService, VuesEnregistrees, MoteurAlertes, GardeAppels et JournalRefus ne sont toujours nommées dans aucun test.

```text
scripts-test/inventaire.sh à 54add78 (colonne testCl = 0 pour workspace, inventory, vulnintel, releaseradar, audit) ; argos-server/src/test/java/io/argos/scm/*.java ; chain/ScmContratEnregistreTest.java (méthodes githubLimiteDeDebitPrincipaleEtSecondaire, gitlabLimiteDeDebitInventaireConserve, githubJetonRefuse…) ; grep -rlw 'VuesEnregistrees|MoteurAlertes|GardeAppels|JournalRefus' src/test/java -> 0
```

*Réserve* : Taux de couverture de scm et global non remesurés ici (Maven bloqué) : à reprendre de la mesure JaCoCo de l'orchestrateur.

**Preuve d'origine**

```text
Comptage main/test par paquet : workspace 24/0, inventory 10/0, scm 8/0, vulnintel 9/0, releaseradar 5/0, regintel 3/0, audit 4/0 ; impact 13 fichiers (1 919 l.) pour 1 test (30 l.). Classes jamais nommées dans src/test : workspace/subscription/SubscriptionService, workspace/vue/VuesEnregistrees, alerting/MoteurAlertes, mcp/GardeAppels, mcp/OutilsSecurite, audit/JournalRefus, releaseradar/* (0/4)
[À 9d96406] Six modules sans test dédié : workspace (26/1 693 l.), inventory, vulnintel, releaseradar, regintel, audit ; scm a DepotGithubTest.
[Mesure] mvn clean verify + JaCoCo 0.8.13 sur le poste du mainteneur : scm 26,9 % lignes / 8,9 % branches (290 l.) ; total 83,8 % / 68,6 %.
```

**Impact** — La couverture globale mesurée est bonne (83,8 % des lignes, 68,6 % des branches), mais scm, qui lit les dépôts GitHub et GitLab des utilisateurs, est presque entièrement hors tests ; les six modules sans test dédié ne sont couverts que par les parcours, sans cas limites ciblés.

**Action** — Ajouter des tests ciblés de scm (pagination, quotas, erreurs de forge) jusqu'au seuil de couverture, puis des tests dédiés des cas limites de workspace (rôles, dernier administrateur, invitations).

### AUD-TST-06 — Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s, marge de 900 ms sur le délai IA)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 3
- **Dépend de** : AUD-QUA-10

**Vérification du 20/11 (54add78)** — Délais réels et horloge réelle toujours utilisés dans les assertions.

*Changement depuis le 17/11* : Les trois cas cités sont inchangés. S'y ajoute un cas déjà présent à 9d96406 et non relevé : PasserelleIaTest attend un appel borné par argos.ia.delai=PT2S contre un bouchon qui répond en 3 s et exige moins de 2,9 s, soit 900 ms de marge. Le bon modèle existe désormais dans le dépôt : ImportPlanifieTest attend le verrou en observant pg_locks (et non par sleep), exactement ce que recommandait l'action, mais DefenseEnProfondeurTest n'a pas été aligné.

```text
alerting/AlertesTest.java:314-323 + testsupport/SimulatedResumeIa.java:73 Thread.sleep(5_000) + application.properties:75 %test…resume-ia.delai-max=PT1S ; api/comptes/DefenseEnProfondeurTest.java:158-159 Thread.sleep(1000); assertFalse(retrait.isDone()) ; signal/web/CollecteWebTest.java:119-127 ; aigateway/PasserelleIaTest.java:266-269 avec testsupport/StubServer.java:91 argos.ia.delai=PT2S ; contre-exemple sain : chain/ImportPlanifieTest.java:319-328 (pg_locks advisory NOT granted)
```

**Preuve d'origine**

```text
alerting/AlertesTest.java:314-323 (Instant.now() avant/après publier(), assertTrue(attente < 4 s)) avec testsupport/SimulatedResumeIa.java:73 Thread.sleep(5_000) et application.properties:68 %test…resume-ia.delai-max=PT1S ; api/comptes/DefenseEnProfondeurTest.java:158 Thread.sleep(1000); assertFalse(retrait.isDone()) ; signal/web/CollecteWebTest.java:120-125 boucle System.currentTimeMillis()+5000 / Thread.sleep(10)
```

**Impact** — Marge de 3 s sur machine lente ou chargée (runner CI partagé, NAS) : échecs intermittents possibles ; le test de verrou (sleep 1 s) peut passer à vide si l'ordonnancement retarde la soumission.

**Action** — Injecter une horloge/un délai simulé dans la passerelle de résumé (comme CollecteWeb.horloge) au lieu de dormir ; remplacer le sleep de DefenseEnProfondeurTest par une observation du verrou (pg_locks / pg_stat_activity wait_event) ; utiliser Awaitility pour les attentes actives.

### AUD-TST-08 — Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 6

**Vérification du 20/11 (54add78)** — Isolation toujours fondée sur deux listes codées en dur, sans contrôle d'exhaustivité.

*Changement depuis le 17/11* : Mécanisme inchangé : la liste TRUNCATE de TestDb est passée à 50 tables et a été tenue à jour à la main pour V16 à V20 (aucune table nouvelle oubliée ; project_impact_transition est vidée par CASCADE via project_impact), plus la seconde liste d'ArtisteFixtures (4 tables). Les mêmes six tables restent non vidées par convention (appartenance, compte, espace, invitation, workspace_preference_utilisateur, ia_prompt). Le dépôt sait pourtant interroger le catalogue : CouvertureRlsTest (commit 9fe3af5) parcourt pg_class pour vérifier la RLS de toute table.

```text
argos-server/src/test/java/io/argos/testsupport/TestDb.java:49-56 ; testsupport/ArtisteFixtures.java:24 ; comparaison avec les 63 CREATE TABLE de V1 à V20 (script inline) -> non vidées : appartenance, compte, espace, ia_prompt, invitation, workspace_preference_utilisateur (+ reg_source et source remises à zéro par UPDATE/DELETE) ; api/comptes/CouvertureRlsTest.java:71
```

*Réserve* : Aucune fuite constatée à ce jour : la liste a été tenue pour cinq migrations successives.

**Preuve d'origine**

```text
testsupport/TestDb.java:49-56 (TRUNCATE dette_version, … sync_cursor CASCADE) ; comparaison avec les CREATE TABLE des 14 migrations -> non vidées : appartenance, compte, espace, invitation, workspace_preference_utilisateur, ia_prompt (source partiellement : DELETE … WHERE id <> 'OSV')
[À 9d96406] TestDb.java:49-56 inchangé ; les 4 tables de V15 sont vidées hors TestDb par ArtisteFixtures.java:24 (seconde liste codée en dur).
```

**Impact** — Isolation par convention (chaque test crée ses espaces, cf. Javadoc TestDb) ; toute nouvelle table oubliée dans la liste fait fuir de l'état entre classes et rend les résultats dépendants de l'ordre d'exécution.

**Action** — Générer la liste à partir de information_schema (toutes les tables du schéma sauf flyway_schema_history et référentiels explicitement conservés) ou ajouter un test qui échoue si une table n'est ni vidée ni déclarée conservée.

### AUD-TST-09 — IHM : la logique et 9 composants sur 21 (rendu SSR) sont testés ; ni le client API effectif (appelerApi) ni les 10 vues

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Tests · **Effort** : M · **Sprint** : 6

**Vérification du 20/11 (54add78)** — Logique et rendu statique largement couverts ; client REST effectif et vues toujours hors tests unitaires.

*Changement depuis le 17/11* : Les tests IHM sont passés de 25 à 206 (25 fichiers). Sont maintenant testés : la logique extraite du client REST (api/autorisation.ts : jeton porteur, refus ; api/statuts-http.ts ; recherche-criteres, inventaire-modele, impacts-modele), la session OIDC (session/oidc.ts, gestionnaire.ts, garde.ts) et, par rendu SSR Vite + vue/server-renderer (tests/support/rendu-ssr.mjs, commit c938508), 9 des 21 composants .vue : MiseEnPage, les 4 mises en page, Titre, MentionsObligatoires, ComposantsIgnores, ComposantsInventories. Restent non testés : appelerApi de src/api/client.ts (234 l. ; délai, annulation, branchement du jeton, erreurs), qui n'est vérifié que par lecture du texte (recherche.test.mjs:168-206) ; les 10 vues (views/*.vue), App.vue et TraitementImpacts.vue ; aucune interaction (clic, saisie) puisque le rendu est SSR. Ni vitest ni @vue/test-utils.

```text
argos-ui/package.json « test »: node --test "tests/**/*.test.mjs" ; grep des imports de tests/*.mjs : aucun import de src/api/client.ts ni de src/views/ ; tests/composants-ignores.test.mjs:119, tests/inventaire-listes.test.mjs:201 (rendreComposant) ; tests/recherche.test.mjs:177-178 (client.ts lu en texte) ; npm test -> 206/206
```

*Réserve* : Les vues sont exercées dans un vrai navigateur par la campagne E2E (e2e/tests/*.spec.ts), mais seulement à la main (voir AUD-TST-20).

**Preuve d'origine**

```text
argos-ui/package.json « test »: node --test "tests/**/*.test.mjs" (pas de vitest ni @vue/test-utils) ; imports des tests : src/contenu/{alertes-poste,resolution,resume-ia,liens}.ts uniquement ; resume-ia.test.mjs:26-29 lit AlertesView.vue en texte et vérifie l'absence de v-html ; src/api/client.ts (207 l.) et les 9 composants .vue non testés
```

**Impact** — Le client API (jetons, erreurs, formats) et le rendu des vues peuvent régresser sans détection ; les contrôles par expression régulière sur le source ne garantissent pas le comportement rendu.

**Action** — Introduire vitest + @vue/test-utils (environnement happy-dom) : tests du client API avec fetch simulé (401, erreurs, sérialisation) et tests de rendu des vues Alertes/Affichage/Réglages.

### AUD-TST-10 — Desktop : main.js (248 l.), qui porte la configuration de sécurité Electron, n'est pas testé ; preload.js l'est désormais

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 5

**Vérification du 20/11 (54add78)** — Surface preload/IPC protégée ; webPreferences de main.js toujours sans test.

*Changement depuis le 17/11* : preload.js (55 l.) est désormais testé : test/preload.test.js (9 tests, commit f7ed2fc) l'exécute avec un Electron factice qui refuse tout module autre qu'electron, vérifie la liste exacte des 10 fonctions exposées par contextBridge, la conversion des arguments et la correspondance avec les canaux déclarés (src/canaux.js, testé par canaux.test.js) ; connexion.js (OIDC desktop) a connexion.test.js. main.js (248 l.) reste non testé : contextIsolation/nodeIntegration/sandbox ne sont vérifiés par aucun test unitaire.

```text
argos-desktop/test/preload.test.js:27-35 (liste des API exposées), :37-42 (seul module electron) ; argos-desktop/src/main.js:94-102 (webPreferences) ; grep -n 'contextIsolation|sandbox|nodeIntegration' argos-desktop/test/*.js -> aucun résultat
```

*Réserve* : La campagne E2E desktop (e2e/desktop/*.spec.ts) lance le vrai main.js, mais sans vérifier les webPreferences et seulement à la main.

**Preuve d'origine**

```text
argos-desktop/test/*.test.js ne requièrent que ../src/{preferences,notifications,poste,ui-protocol} ; argos-desktop/src/main.js:92-94 (contextIsolation: true, nodeIntegration: false, sandbox: true) non vérifié par un test
```

**Impact** — Une régression sur les webPreferences, la CSP ou la surface exposée par preload (contextBridge) ouvrirait l'exécution de code distant dans le client sans qu'aucun test ne le signale.

**Action** — Extraire la construction des options de BrowserWindow et la liste des API exposées par preload dans des fonctions pures testées (contextIsolation/sandbox à true, nodeIntegration à false, liste blanche des canaux IPC).

### AUD-TST-12 — Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : M · **Sprint** : 9
- **Dépend de** : AUD-TST-01

**Vérification du 20/11 (54add78)** — Aucun test sur l'artefact empaqueté.

*Changement depuis le 17/11* : Inchangé. La campagne E2E démarre le serveur par mvn quarkus:dev (profil dev), pas le jar ni l'image.

```text
argos-server/pom.xml:34 <skipITs>true</skipITs> ; find . -name '*IT.java' -> aucun résultat ; grep -rn QuarkusIntegrationTest argos-server/src -> aucun ; Dockerfile:37 mvn -B package -DskipTests ; e2e/playwright.config.ts:89 'mvn -B -q -f ../argos-server/pom.xml quarkus:dev'
```

**Preuve d'origine**

```text
argos-server/pom.xml:28 <skipITs>true</skipITs> ; find . -name "*IT.java" -> aucun résultat ; Dockerfile:22 mvn -B package -DskipTests
```

**Impact** — Le jar/l'image réellement déployés (configuration %prod, Flyway au démarrage, chargement des ressources) ne sont jamais démarrés par un test ; les écarts entre mode test et mode prod ne sont pas détectés avant déploiement.

**Action** — Ajouter un smoke test @QuarkusIntegrationTest (santé, /api/v1/system/info, 401 sans jeton) ou un test compose (docker compose up + curl) dans la future CI.

### AUD-TST-19 — Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests, Dépendances · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01

**Vérification du 20/11 (54add78)** — Pas de garde de version de JDK pour les tests.

*Changement depuis le 17/11* : Aucun changement : ni maven-enforcer ni toolchains ; release 17 ; image d'exécution en JRE 17. L'étage de build Docker utilise maven:3.9-eclipse-temurin-17 mais n'exécute pas les tests.

```text
argos-server/pom.xml:16 maven.compiler.release=17 ; grep -n 'enforcer|toolchain' argos-server/pom.xml -> aucun résultat ; Dockerfile:18 FROM maven:3.9-eclipse-temurin-17, :37 -DskipTests, :45 FROM eclipse-temurin:17-jre
```

*Réserve* : L'aspect « build incrémental » repose sur une mesure du mainteneur, non rejouée ici.

**Preuve d'origine**

```text
Mesure communiquée par le mainteneur (non rejouée ici) : premier `mvn verify` sans clean -> 0 test lancé, « TestEngine archunit failed to discover tests », LimiteDebitMcpTest.class absente de target/test-classes (le source existe : src/test/java/io/argos/mcp/LimiteDebitMcpTest.java:27) ; `mvn clean verify` -> 416 tests, BUILD SUCCESS. pom.xml:16 maven.compiler.release=17 ; Dockerfile:25 eclipse-temurin:17-jre ; aucun maven-enforcer ni toolchains dans pom.xml.
```

**Impact** — Un build local peut échouer ou, pire, passer sur des classes de test périmées. Les tests passent sur JDK 24 alors que l'image tourne sur JRE 17 : une différence de comportement de la JVM (TLS, HttpClient, GC) n'est pas couverte. L'échec observé était bruyant (engine en erreur), donc pas silencieux.

**Action** — En CI : toujours `mvn -B clean verify` sur un JDK 17 (setup-java 17, ou maven-toolchains). Faire écrire l'IDE dans un répertoire distinct de target/ (ex. VS Code Java : bin/). Ajouter maven-enforcer (requireJavaVersion [17,18) en CI).

### AUD-TST-20 — Campagne E2E principale (34 tests, 14 fichiers) rejouable seulement sur un poste complet et jamais automatisée ; reuseExistingServer: true réutilise sans contrôle un serveur ou un Vite déjà lancés

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests · **Effort** : M · **Sprint** : 9
- **Nouveau** le 20/11
- **Dépend de** : AUD-TST-01
- **Bloque** : AUD-TST-23

**Preuve**

```text
e2e/playwright.config.ts:89-100 (serveur par 'mvn … quarkus:dev', reuseExistingServer: true, variables SANS_COLLECTE_EXTERNE et CHAINE_SIMULEE passées seulement au serveur que Playwright démarre) et :103-106 (Vite, reuseExistingServer: true) ; e2e/README.md « Lancer » (prérequis : Docker, JDK 17, Maven 3.9 avec accès aux dépôts, binaire Electron 45.3.2, Chromium révision 1228) ; e2e/support/electron.ts:20-31 (binaire Electron local, échec explicite s'il manque) ; npx playwright test --list -> « Total: 34 tests in 14 files » (30 parcours + 3 préparations + 1 construction) ; dernier rapport de référence e2e/resultats/chaine-automatisee/rapport.json : 34 attendus, 0 échec, 391 s, 2026-11-19 ; application.properties:46, :54, :134, :223, :246 (tâches planifiées actives par défaut, cadence 1 h pour la synchronisation des avis)
```

**Impact** — Les parcours les plus proches de l'usage réel (connexion OIDC web et desktop, notifications du poste, chaîne de veille automatisée, protocole MCP) ne protègent que lorsqu'ils sont lancés à la main sur un poste équipé ; ils n'ont pas pu être rejoués ici (Maven Central inaccessible, binaire Electron non téléchargé). Si un serveur quarkus:dev tourne déjà sur 8090, il est réutilisé sans les variables de la campagne : ses tâches planifiées gardent leurs adresses réelles (la garde réseau ne couvre que le navigateur et Electron) et le parcours de la chaîne automatisée échoue ou mesure autre chose.

**Action** — Dans la future CI (AUD-TST-01) : job E2E sur runner Docker avec Xvfb pour Electron, binaire Electron et Chromium en cache, reuseExistingServer: !process.env.CI pour le serveur et Vite ; en local, faire vérifier par la préparation que le serveur réutilisé expose la configuration attendue (par exemple une sonde de configuration de test) ou refuser la réutilisation quand CHAINE_SIMULEE est requis.

### AUD-TST-23 — L'audit d'accessibilité n'a pas suivi les écrans livrés après le 17/11 : vue d'inventaire, actions sur les abonnements et traitement des impacts sans mesure de contraste ni revue

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Tests, Documentation · **Effort** : M · **Sprint** : 9
- **Nouveau** le 20/11
- **Dépend de** : AUD-TST-20

**Preuve**

```text
docs/architecture/quality/audit-accessibilite-mises-en-page.md:13 (hors périmètre : écrans autres que les mises en page) et :14 (date 2026-11-17) ; argos-ui/src/views/InventaireView.vue créé par f5fff1f (2026-11-19 : tableaux, pagination, région live) ; fea6eca (2026-11-19, actions suspendre/réactiver/supprimer dans VeilleView.vue) ; traitement des impacts dans AlertesView.vue (automatiser-chaine-veille/tasks.md:65, tâche 3.5) ; les mesures de contraste e2e/support/mesures.ts ne sont utilisées que par e2e/accessibilite/ et e2e/tests/veille.spec.ts (0 occurrence de « mesures » ou « contraste » dans e2e/tests/inventaire.spec.ts et chaine-automatisee.spec.ts), qui vérifient seulement clavier, focus et annonce.
```

**Impact** — Les seuls écrans de formulaire et de tableau de l'application échappent au référentiel RGAA retenu (décision Q-4) ; la conclusion de l'audit (« avant de déclarer la conformité RGAA de l'application ») s'éloigne.

**Action** — Étendre le rapport (ou en ouvrir un second) aux vues Inventaire, Alertes et aux actions de Ma veille, en réutilisant e2e/support/mesures.ts dans leurs parcours.

### AUD-PER-05 — Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Vérification du 20/11 (54add78)** — Le radar charge toujours les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant.

*Changement depuis le 17/11* : Aucun changement (git diff 9d96406 HEAD vide pour RadarProjets.java, CalendrierEndoflife.java, RappelsRadar.java) ; componentsMatching n'a toujours pas de filtre par espace.

```text
argos-server/src/main/java/io/argos/impact/RadarProjets.java:252-253 (componentsMatching(…, Optional.empty()).stream().filter(espace)) et :190 ; argos-server/src/main/java/io/argos/inventory/InventoryQueries.java:329 (signature componentsMatching(Collection<String>, Optional<UUID> projectId), aucun paramètre d'espace) ; CalendrierEndoflife.java:119 et :127 ; RappelsRadar.java:114-117.
```

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/RadarProjets.java:252-253 « return inventaires.componentsMatching(parCle.keySet(), Optional.empty()).stream().filter(m -> espace.equals(m.workspaceId())).toList(); » ; :131 boucle par espace ; :148 calendrier.finDeSupport(...) par composant -> argos-server/src/main/java/io/argos/releaseradar/CalendrierEndoflife.java:119 (referentiel.technologies) + :127 (requête cycle_vie), donc 2 requêtes par composant ; :150 enregistrerDette = 1 connexion + 1 INSERT par dette ; argos-server/src/main/java/io/argos/alerting/RappelsRadar.java:114-117 : espace × jours de rappel (jusqu'à 10) × echeancesTouchant, qui rappelle composants().
```

**Impact** — Le coût est en O(espaces × composants de toute la plateforme correspondant aux clés), auquel s'ajoutent 2 requêtes par composant et jusqu'à 10 rescans par espace pour les rappels. Sur le NAS mono-utilisateur, c'est négligeable. En ligne, avec des espaces nombreux suivant les mêmes technologies (spring, node…), la tâche quotidienne grossit de façon quadratique.

**Action** — Ajouter un paramètre workspaceId à componentsMatching (filtre SQL p.workspace_id = ?) ; charger le calendrier (cycles des produits suivis) une fois par espace, puis évaluer en mémoire ; insérer les dettes par lot ; calculer les échéances de tous les jours de rappel en une passe (intervalle [min, max]).

### AUD-PER-07 — Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Vérification du 20/11 (54add78)** — La sélection reste matérialisée, classée et comptée en entier à chaque page. Les seules mesures à grand volume restent celles de QS-13 (poste de développement).

*Changement depuis le 17/11* : Aucun changement dans RechercheVeille.java ni V11 ; mesure-qs13-recherche.md : une ligne modifiée (lien), mêmes chiffres. Nouvelle mesure de la campagne p95 sur un petit jeu.

```text
argos-server/src/main/java/io/argos/impact/RechercheVeille.java:136-138 (WITH r AS MATERIALIZED … (SELECT count(*) FROM r) … LIMIT ? OFFSET ?) ; docs/architecture/quality/mesure-qs13-recherche.md : « aucun filtre (fil complet) 120 000 … p95 272 » et « mot présent dans tous les éléments 120 000 … p95 3 722 » ; docs/architecture/quality/mesure-performance-api-mcp.md : « GET /api/v1/recherche … p95 35.2 ms » (jeu de 1 000 avis, quarkus:dev, poste 16 cœurs, 48 Gio).
```

*Réserve* : Les 35,2 ms de la campagne du 2026-11-19 ne contredisent pas le constat : avec 1 000 avis, le classement porte sur peu de lignes. Aucune mesure sur le NAS ni sur un corpus OSV réel.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/impact/RechercheVeille.java:136-138 « WITH r AS MATERIALIZED (SELECT * FROM argos_rechercher_veille(...)) SELECT r.type, r.id, r.rang, (SELECT count(*) FROM r) FROM r ORDER BY r.rang DESC, ... LIMIT ? OFFSET ? » ; argos-server/src/main/resources/db/migration/V11__recherche_veille.sql:101-105 (sans mots, la branche signal renvoie tous les signaux, ts_rank sur chaque correspondance) ; docs/architecture/quality/mesure-qs13-recherche.md : « aucun filtre (fil complet) 120 000 … p95 272 ms » et « mot présent dans tous les éléments 120 000 … p95 3 722 ms » (poste de dev, 20 000 signaux).
```

**Impact** — La latence croît avec le nombre de correspondances, pas avec la taille de la page : ts_rank détoaste le vecteur de chaque ligne (toast_tuple_target=256) et le count(*) est exact. La mesure QS-13 a été prise sur un PC de développement avec 20 000 signaux. Sur le Ryzen R1600 du NAS, avec le corpus OSV réel, un mot fréquent ou le fil sans filtre dépassera la cible Q-05 de 500 ms (risque R-16 déjà identifié). OFFSET rend aussi les pages profondes coûteuses.

**Action** — Borner la sélection (par exemple LIMIT 1 000 candidats triés par date avant ts_rank, ou ts_rank seulement si le nombre de correspondances est sous un seuil) ; remplacer le total exact par un total plafonné (« plus de 1 000 ») ; fil sans mots : tri par date avec l'index signal_date/actualite_date et pagination par curseur (keyset) ; refaire la mesure QS-13 sur le NAS avec un corpus OSV réel.

### AUD-PER-09 — Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 6

**Vérification du 20/11 (54add78)** — Les index manquants le sont toujours ; SET NULL demande la même recherche par component_id que CASCADE.

*Changement depuis le 17/11* : V20 remplace la clé étrangère project_impact.component_id ON DELETE CASCADE par ON DELETE SET NULL (V20__etat_traitement_impacts.sql:49-52) et ajoute project_impact (project_id, state) et (signal_id, state) (:55-56). Aucun index sur component_id ni sur les colonnes d'exploitation citées.

```text
argos-server/src/main/resources/db/migration/V20__etat_traitement_impacts.sql:51-52 (FOREIGN KEY (component_id) REFERENCES component (id) ON DELETE SET NULL) ; commande « grep -n -i 'index' V*.sql | grep -i 'component_id\|ingestion_failure\|ops_event\|sync_attempt\|inventory_import_failure' » -> aucun index sur project_impact(component_id), dette_version(component_id), ingestion_failure(source_id), ops_event(source_id), sync_attempt(run_id), inventory_import_failure(project_id) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:281-285 (DELETE FROM component … NOT EXISTS occurrence) ; SyncStore.java:215 et :234 ; InventoryQueries.java:303.
```

*Réserve* : Atténuation : depuis le volet B, les composants gardent leur identifiant d'un import à l'autre (upsert InventoryStore.java:236-240) ; seuls les composants vraiment retirés sont supprimés. Mais chaque suppression déclenche un parcours de project_impact (SET NULL, puis le déclencheur de V20:40-43) et de dette_version (CASCADE), et inventory_import_failure grossit maintenant chaque nuit pour les dépôts en erreur.

**Preuve d'origine**

```text
argos-server/src/main/resources/db/migration/V5__impacts_projet.sql:11 « component_id uuid NOT NULL REFERENCES component (id) ON DELETE CASCADE » (seul index contenant component_id : project_impact_triplet_uk (signal_id, project_id, component_id), en 3e position) ; V12__radar_et_standards.sql:107 (dette_version.component_id, en 2e position dans dette_version_uk) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:72 « DELETE FROM component WHERE project_id = ? AND import_id <> ? » ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:215 et :234 (WHERE source_id = ? ORDER BY ...) sans index sur ingestion_failure(source_id) ni ops_event(source_id) (V3:55-72) ; sync_attempt(run_id) non indexé (V3:43-52) ; InventoryQueries.java:112 sans index sur inventory_import_failure(project_id).
```

**Impact** — Chaque composant retiré d'un inventaire déclenche une recherche en cascade dans project_impact et dette_version : faute d'index adapté, c'est un parcours séquentiel par ligne supprimée. Une réimportation qui retire beaucoup de composants devient alors quadratique quand project_impact grossit. Les listes d'exploitation font des parcours complets, sans gravité tant que les volumes restent faibles.

**Action** — Migration V15 : CREATE INDEX ON project_impact (component_id) ; ON dette_version (component_id) ; ON ingestion_failure (source_id, received_at) ; ON ops_event (source_id, occurred_at) ; ON sync_attempt (run_id) ; ON inventory_import_failure (project_id, received_at).

### AUD-PER-10 — Listes renvoyées en entier sans pagination : listes d'exploitation (sources rejetées, événements, échecs d'import) et impacts d'un projet (GET /projects/{id}/impacts)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 6
- **Fusionne** : AUD-PER-22

**Vérification du 20/11 (54add78)** — Les listes d'exploitation restent sans LIMIT. Leur croissance est désormais automatique : synchronisation horaire et import quotidien.

*Changement depuis le 17/11* : Aucun changement dans SourceOperationsResource (rejected, events) ni dans SyncStore ; InventoryResource change (pagination des composants : commits bdd31d6 et a4f3ad3) mais /failures reste une liste complète.

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:39-55 ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:215 et :234 (ORDER BY sans LIMIT) ; argos-server/src/main/java/io/argos/api/InventoryResource.java:101-104 -> InventoryQueries.java:303 « … FROM inventory_import_failure WHERE project_id = ? ORDER BY received_at » (sans LIMIT) ; grep « DELETE FROM » src/main : aucune purge de ingestion_failure, ops_event, sync_run, sync_attempt ni inventory_import_failure.
```

*Réserve* : À relier à la rétention (AUD-SEC-03) : ImportPlanifie enregistre un échec par dépôt en erreur et par nuit (InventoryService.java:209-217), et la tâche horaire ajoute un sync_run par heure et par source. Ces routes sont réservées à l'exploitation (ou aux membres du projet pour /failures).

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:40-55 (rejected, events -> List complète) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:213-216 et :234 (aucun LIMIT) ; argos-server/src/main/java/io/argos/api/InventoryResource.java:50-52 -> InventoryQueries.java:112 (aucun LIMIT). À l'inverse, recherche (RechercheVeille, TAILLE_MAX=100), impacts (ImpactQueries.java:82 LIMIT/OFFSET), alertes (ConsultationAlertes.java:58 LIMIT) et actualités (ActualitesWeb.java:83 LIMIT) sont bornées.
[AUD-PER-22] argos-server/src/main/java/io/argos/api/ProjectResource.java:61-73 (renvoie List<ImpactQueries.ImpactView>, paramètre etat seul, ni page ni size) -> argos-server/src/main/java/io/argos/impact/ImpactQueries.java:218-219 (« WHERE i.project_id = ? AND i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.created_at, i.id », sans LIMIT) ; V20__etat_traitement_impacts.sql:54 (« les impacts résolus s'accumulent ») ; même lecture sans borne via un filtre d'abonnement PROJECT : api/SignauxAbonnements.java:95-96 (forProject) puis :66 (signals.find par signal). À l'inverse, GET /api/v1/signals/{id}/impacts est paginé (SignalResource.java:80-88, ImpactQueries.java:132).
```

**Impact** — Ces tables ne sont jamais purgées (voir la purge ci-dessus) : la réponse et la mémoire allouée grossissent à chaque synchronisation ou import en échec. Les endpoints sont réservés à l'exploitation, ce qui limite l'exposition.

**Action** — Ajouter limit/offset (ou un curseur sur l'horodatage), avec un maximum de 100 à 500 éléments, et trier du plus récent au plus ancien.

### AUD-PER-11 — Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 7

**Vérification du 20/11 (54add78)** — Les réponses OSV et KEV sont toujours lues en entier en String, sans plafond, et l'index OSV est maintenant relu à chaque passage horaire.

*Changement depuis le 17/11* : Aucun changement dans HttpSourceClient.java ni EnrichmentHttp.java. Le nouveau client JORF (ArchivesJorf) est, lui, plafonné.

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:59 « client.send(request, HttpResponse.BodyHandlers.ofString()) » ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:60-61 (csv complet puis csv.split("\\R")) ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:42-43 (ofString) ; application.properties:31 (Maven,npm,PyPI) et :46 (cadence 1h).
```

*Réserve* : Avant, l'exploitant lançait la collecte à la main ; désormais le pic mémoire (index de chaque écosystème en String, tableau de lignes, liste de FeedEntry) revient chaque heure, sous le même tas d'environ 768 Mio (AUD-PER-12).

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:59 « client.send(request, HttpResponse.BodyHandlers.ofString()) » (index modified_id.csv complet de chaque écosystème, OsvAdvisoryFeed.java:60, puis csv.split("\\R") et une liste de FeedEntry) ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:43 (idem, catalogue KEV complet). À l'inverse, ForgeHttp (scm), FournisseurOllama (1 Mio), ClientEndoflife et RecuperateurWeb plafonnent la taille lue.
```

**Impact** — Sous un tas d'environ 768 Mo (mem_limit 1g × MaxRAMPercentage 75), l'index npm complet est conservé à la fois en String, en tableau de lignes et en liste d'entrées. Une réponse anormale (proxy, erreur) pourrait faire monter la mémoire jusqu'à l'OOM, alors que les autres clients HTTP sont bornés.

**Action** — Lire en flux (BodyHandlers.ofInputStream + BufferedReader ligne à ligne) avec un plafond configurable, sur le modèle de ForgeHttp.get(..., tailleMax).

### AUD-PER-12 — Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 2

**Vérification du 20/11 (54add78)** — Les réglages JVM, le pool et Postgres sont toujours aux valeurs par défaut.

*Changement depuis le 17/11* : Dockerfile modifié pour les licences (G2, H1, H2) mais JAVA_OPTS inchangé ; aucune clé de pool ni de compression ajoutée dans application.properties ; compose.yaml sans réglage Postgres.

```text
Dockerfile:53 « ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" » (sans ExitOnOutOfMemoryError) ; compose.yaml:27 (postgres mem_limit 512m), :59 (argos 1g), :76 (ollama 3g) ; commande « grep -n 'datasource.jdbc\|enable-compression\|max-size' application.properties » -> seules les URL (:10, :15).
```

*Réserve* : La charge en tâche de fond a augmenté depuis l'audit initial (13 tâches planifiées, dont la synchronisation horaire et l'import quotidien, chacun avec des connexions tenues pendant des boucles longues), donc le dimensionnement du pool par défaut (20) n'est plus seulement théorique.

**Preuve d'origine**

```text
Dockerfile : « ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" » ; compose.yaml : « mem_limit: ${ARGOS_MEM_LIMIT:-1g} » (argos) et « mem_limit: ${ARGOS_DB_MEM_LIMIT:-512m} » (postgres, sans réglage shared_buffers ni work_mem) ; argos-server/src/main/resources/application.properties : aucune clé quarkus.datasource.jdbc.max-size/min-size/acquisition-timeout ni quarkus.http.enable-compression (grep sans résultat).
```

**Impact** — Il reste environ 256 Mio hors tas pour le metaspace, le code cache, les piles de fils, les tampons directs de Netty et du client HTTP JDK. Un dépassement provoque l'arrêt du conteneur (OOM-kill), sans heap dump ni message JVM. Le pool par défaut (20 connexions), combiné aux connexions tenues pendant les appels distants (verrou consultatif de collecte web : StockActualites.java:127-141, connexion dédiée pendant la récupération HTTP), n'est ni dimensionné ni documenté. Sans compression HTTP, le bundle de 111 kB est servi non compressé hors reverse proxy.

**Action** — JAVA_OPTS : MaxRAMPercentage=60 à 65, -XX:+ExitOnOutOfMemoryError, éventuellement -XX:MaxMetaspaceSize ; expliciter quarkus.datasource.jdbc.max-size (ex. 10 sur le NAS) et acquisition-timeout ; activer quarkus.http.enable-compression=true ; régler shared_buffers (~128 Mo) et work_mem pour la limite de 512 Mo de Postgres.

### AUD-PER-13 — Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 7

**Vérification du 20/11 (54add78)** — La collecte d'activité lit toujours l'échéance projet par projet et appelle les forges en séquence.

*Changement depuis le 17/11* : Aucun changement dans CollecteActivite.java (git diff vide).

```text
argos-server/src/main/java/io/argos/projectintel/CollecteActivite.java:69-70 (@Scheduled) et :81-83 (boucle projets.withRepository() puis store.prochaineCollecte par projet).
```

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/projectintel/CollecteActivite.java:81-83 « for (ProjectDirectory.Project projet : projets.withRepository()) { ... store.prochaineCollecte(projet.id()); » (1 requête par projet, alors que l'index activite_collecte_prochaine existe : V14:47) ; argos.scm.activite.pages-max=10 et argos.sources.http-timeout=PT30S (application.properties).
```

**Impact** — Chaque passage horaire charge tous les projets à dépôt et interroge la base pour chacun, puis enchaîne jusqu'à 10 pages × 30 s par dépôt. Le chevauchement est évité (SKIP), mais en ligne la durée d'un passage devient proportionnelle au nombre total de projets.

**Action** — Sélectionner en SQL les seuls projets échus (JOIN activite_collecte WHERE prochaine_collecte <= now() ORDER BY prochaine_collecte LIMIT n), borner la durée d'un passage comme CollecteWeb (duree-max-passage), et paralléliser modérément par forge.

### AUD-PER-19 — L'import quotidien réécrit et recorrèle chaque nuit tout l'inventaire de chaque projet à dépôt, même si la SBOM ou les manifestes n'ont pas changé

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 8
- **Nouveau** le 20/11
- **Bloque** : AUD-PER-20

**Preuve**

```text
argos-server/src/main/java/io/argos/inventory/ImportPlanifie.java:52-56 (cron argos.inventory.import.cron, 03:00) et :77-80 (inventories.importerProjet pour chaque projet) ; argos-server/src/main/java/io/argos/inventory/InventoryService.java:186-188 et :230 (SBOM trouvée -> « IMPORTED » sans comparaison avec l'import précédent) et :206 (manifestes -> « IMPORTED » à chaque lecture) puis :166 store.remplacer ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:226-231 (DELETE de toutes les occurrences de l'emplacement), :236-240 (« ON CONFLICT … DO UPDATE SET purl = excluded.purl, import_id = excluded.import_id », donc une nouvelle version de ligne par composant et par nuit), :320 (« recorrelation_pending = true »), :84 (InventoryReplaced) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:101-126 (correlateProject synchrone de tous les signaux qui affectent l'inventaire, plus ceux qui portent un impact actif). Aucune empreinte de document dans V19 (repository_inventory : V19__depots_projet_et_occurrences.sql:30-43).
```

**Impact** — Chaque nuit, le coût est en O(projets × signaux affectant l'inventaire × composants correspondants), avec le N+1 d'AUD-PER-03 (3 à 5 instructions par composant), même quand rien n'a changé. La table component prend une version morte par ligne et par nuit, component_occurrence est vidée puis réécrite (autovacuum et gonflement de la table sur le NAS). Le commit ceb0f50 empêche les alertes en double, pas le travail. En multi-utilisateurs, la durée du passage croît avec le nombre total de projets, et SKIP fait sauter le passage suivant si celui-ci dépasse 24 h.

**Action** — Calculer une empreinte (SHA-256) du document lu (SBOM ou manifestes concaténés) et la stocker dans repository_inventory ; répondre « KEPT » si elle est identique, sans écrire ni publier InventoryReplaced ; dans l'upsert, n'écrire que si purl a changé (WHERE component.purl IS DISTINCT FROM excluded.purl) ; ne recorréler que les package_key dont la présence ou la version a changé (différence entre l'ancien et le nouvel ensemble).

### AUD-PER-20 — Régression de l'import d'inventaire : les composants sont insérés un par un (un aller-retour SQL par composant), alors qu'ils l'étaient par lot à l'audit initial

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 8
- **Nouveau** le 20/11
- **Dépend de** : AUD-PER-19

**Preuve**

```text
argos-server/src/main/java/io/argos/inventory/InventoryStore.java:235-262 (« INSERT INTO component … ON CONFLICT … RETURNING id » puis « try (ResultSet rs = composant.executeQuery()) » dans la boucle sur r.parsed().components()) ; seule l'occurrence est par lot (:268-270 addBatch/executeBatch) ; à 9d96406 : « git show 9d96406:argos-server/src/main/java/io/argos/inventory/InventoryStore.java » lignes 51-70 (ps.addBatch() puis ps.executeBatch()) ; introduit par le commit 77181e9 (volet B) ; argos.inventory.max-document-bytes=20971520.
```

**Impact** — Une SBOM npm ou Maven de plusieurs milliers de composants donne autant d'allers-retours SQL dans la transaction qui tient le verrou consultatif du projet (InventoryStore.java:167). L'import manuel et l'import planifié du même projet attendent d'autant, et le coût revient chaque nuit (AUD-PER-19). La protection Info d'AUD-PER-17 ne tient plus.

**Action** — Revenir à un INSERT par lot : soit INSERT … SELECT FROM unnest(?::text[], ?::text[], …) ON CONFLICT … RETURNING id, package_key, version, en une requête, soit addBatch puis relecture des identifiants par (project_id, package_key, version) avec « = ANY ».

### AUD-DEP-05 — Aucune veille automatisée des dépendances (Renovate/Dependabot, CVE Maven) ; Electron 45.3.2 hors de la mineure supportée (45.4.1 publiée), Playwright 1.61.1 (1.65.1 publiée)

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 1
- **Fusionne** : AUD-DEP-14

**Vérification du 20/11 (54add78)** — Toujours aucune veille automatisée ; le retard d'Electron s'est creusé (45.3.2 alors que 45.3.3, 45.3.4 et 45.4.1 sont publiées).

*Changement depuis le 17/11* : CycloneDX introduit (pom.xml:31, :220-275, commits d9ba8c4, 34dbb94) mais uniquement pour l'inventaire des licences dans le profil « licences » : bom.json n'est ni publié ni confronté aux CVE. Toujours ni Renovate ni Dependabot. Electron non mis à jour.

```text
find à la racine : ni .github/ (absent) ni renovate.json ni dependabot.yml ; argos-desktop/package.json:18 `"electron": "^45.3.2"`, package-lock 45.3.2 ; `npm view electron time` : 45.3.3 (16/11), 45.3.4 (17/11), 45.4.1 (19/11), dist-tag latest 45.4.1. Context7 /websites/electronjs (electron-timelines) : « We only support the latest minor release for each stable release series » : la ligne 45.3 n'est plus supportée. e2e/package.json:19 `@playwright/test` épinglé exactement à 1.61.1 (23/06/2026) alors que 1.65.1 est publiée (13/11/2026), outillage de test seulement.
```

*Réserve* : Une SBOM CycloneDX du serveur est désormais produite au build de l'image (target/bom.json), ce qui rend le « dogfooding » de l'action proche ; elle n'est pas conservée dans l'image.

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

**Vérification du 20/11 (54add78)** — Non corrigé mais arbitré explicitement dans la doc de déploiement et le Dockerfile.

*Changement depuis le 17/11* : Décision H2 du porteur du projet (2026-11-19), commits c67f027/f2b37c1 : images de base épinglées par tag, sans digest, assumé avec sa contrepartie (deux constructions à des dates différentes partent d'images différentes).

```text
Dockerfile:4-5 (« Images de base épinglées par tag, sans empreinte (digest) : décision H2 ») ; :8 node:22-slim, :18 maven:3.9-eclipse-temurin-17, :45 eclipse-temurin:17-jre ; compose.yaml:15 postgres:18 ; docs/deploiement/synology-et-pc.md:130-140 (tableau des images + [DÉCISION … (H2)]).
```

*Réserve* : La décision ne couvre pas le retour arrière : l'image argos reste étiquetée argos:${ARGOS_VERSION:-local} (compose.yaml:36), donc écrasée à chaque `up --build` par défaut. e2e/compose.yaml:10 postgres:18 n'est pas listé dans le tableau H2 (outillage de test, sans enjeu).

**Preuve d'origine**

```text
Dockerfile:6 `FROM node:22-slim AS ui`, :16 `FROM maven:3.9-eclipse-temurin-17 AS server`, :25 `FROM eclipse-temurin:17-jre` ; compose.yaml:15 `image: postgres:18`. (Ollama est épinglé : compose.yaml:66 `ollama/ollama:${ARGOS_OLLAMA_VERSION:-0.12.6}`.)
[AUD-TST-07] grep image-name application.properties src/test/resources -> aucun résultat ; seules lignes devservices : application.properties:14 (%dev…enabled=false), 207-211 (realm Keycloak) ; compose.yaml:15 image: postgres:18
```

**Impact** — Build non reproductible : la procédure de mise à jour (doc:80-84, `docker compose up -d --build`) reconstruit avec la base du jour ; une régression d'image amont ou un changement de comportement de PostgreSQL 18.x arrive sans trace ni retour arrière possible vers l'image précédente (image locale `argos:local` écrasée).

**Action** — Épingler chaque FROM et l'image postgres par version précise + digest (`eclipse-temurin:17.0.x_y-jre@sha256:...`), laisser Renovate proposer les mises à jour, et étiqueter l'image argos par version (ARGOS_VERSION) pour permettre le retour arrière.

### AUD-DEP-08 — Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 2

**Vérification du 20/11 (54add78)** — Toujours aucun healthcheck pour argos/ollama ni rotation des journaux.

*Changement depuis le 17/11* : Aucun : compose.yaml inchangé ; le Dockerfile a été réécrit (licences) sans HEALTHCHECK.

```text
compose.yaml:34-59 (service argos sans healthcheck), :65-76 (ollama idem), aucune clé `logging:` ; Dockerfile:45-54 sans HEALTHCHECK.
```

**Preuve d'origine**

```text
compose.yaml:35-59 (service argos : restart, depends_on, mem_limit mais pas de healthcheck), :65-76 (ollama idem) ; Dockerfile:25-32 sans HEALTHCHECK ; aucune clé `logging:` dans compose.yaml (grep).
```

**Impact** — Un Argos bloqué (JVM vivante mais /q/health/ready en échec) reste « running » pour Container Manager ; les journaux json-file croissent sans limite sur le volume du NAS.

**Action** — Ajouter un healthcheck sur /q/health/ready (outil présent dans l'image ou petite commande Java), `logging: {driver: json-file, options: {max-size: 10m, max-file: '3'}}` sur chaque service.

### AUD-DEP-15 — Client desktop distribué sans signature de code ni mise à jour automatique

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : M · **Sprint** : 9

**Vérification du 20/11 (54add78)** — Toujours ni signature de code ni mise à jour automatique.

*Changement depuis le 17/11* : Aucun changement de la configuration de build du client desktop (le delta ajoute la connexion OIDC, 07252ed).

```text
argos-desktop/package.json:21-55 (nsis/AppImage/dmg sans certificat, identity, notarize ni publish) ; devDependencies limitées à electron et electron-builder ; grep -riE 'autoUpdater|electron-updater' argos-desktop/src -> aucun résultat ; docs/deploiement/synology-et-pc.md:144 `npm run dist` manuel.
```

*Réserve* : Le retard d'Electron (AUD-DEP-05) illustre l'impact : un poste installé resterait sur 45.3.2.

**Preuve d'origine**

```text
argos-desktop/package.json, bloc build : win nsis, mac dmg, linux AppImage sans certificateFile/identity/notarize ni publish ; aucune dépendance electron-updater ; grep -iE 'autoUpdater|electron-updater' argos-desktop/src/*.js -> aucun résultat ; docs/deploiement/synology-et-pc.md:101 construction manuelle `npm run dist`.
```

**Impact** — Pour une diffusion « tous publics » : avertissements SmartScreen/Gatekeeper (macOS refuse un dmg non notarisé), authenticité de l'installeur invérifiable, et les postes restent indéfiniment sur le Chromium embarqué de leur installation (failles navigateur non corrigées).

**Action** — Signer (Authenticode, Apple Developer ID + notarisation) dans la CI, publier via electron-builder `publish` et intégrer electron-updater (vérification de signature) ; à défaut, restreindre officiellement le client desktop à l'usage personnel.

### AUD-DEP-19 — Licences : contrôle automatisé et avis livrés pour le serveur ; IHM livrée (vue, vue-router) sans avis MIT dans l'image ni l'installeur, aucun contrôle npm

- **Statut** : Partiel · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 9

**Vérification du 20/11 (54add78)** — Le serveur est conforme et contrôlé ; l'IHM livrée (vue, vue-router, MIT) part dans l'image et l'installeur sans avis, et aucun contrôle npm n'est automatisé.

*Changement depuis le 17/11* : Corrigé côté serveur : profil Maven « licences » (CycloneDX + scripts/verifier-licences-serveur.mjs) exécuté au build de l'image, qui échoue sur licence absente ou non admise, et image livrant NOTICE, THIRD-PARTY.txt et textes de licence (commits d9ba8c4, 34dbb94, fca5213, f2b37c1). Fichiers NOTICE ajoutés pour argos-server, argos-ui et e2e. Reste ouvert côté npm.

```text
Dockerfile:32-34 (`mvn -B -P licences validate`), :49-50 (NOTICE, THIRD-PARTY.txt, licenses/ dans /opt/argos/licenses) ; pom.xml:220-275 ; rejeu du script sur inventaires factices : rc=1 sur GPL-3.0-only et sur licence absente, rc=2 sur BOM absent, rc=0 sur Apache+MIT avec archives (textes Apache-2.0.txt et MIT.txt copiés). Côté npm : argos-ui/NOTICE rédigé à la main (seules les polices sont testées, tests/polices.test.mjs) ; aucun NOTICE dans argos-desktop ; `vite build` rejoué : `grep -iE '@license|Evan You' dist/assets/*.js` -> aucun résultat, seul dist/licences-polices.txt ; docs/deploiement/synology-et-pc.md:115 « les avis npm de l'IHM ne sont pas ajoutés » (décision H1).
```

*Réserve* : La partie npm est écartée par la décision H1 du porteur (doc:115) sans motiver l'obligation d'attribution MIT de vue/vue-router, présents dans le bundle livré. Le contrôle serveur n'est exécuté qu'au build de l'image (voir AUD-DEP-22).

**Preuve d'origine**

```text
ADR-0021:59 « La licence de chaque dépendance est enregistrée au moment de son introduction » : respecté par commentaires dans pom.xml (l.57-150) mais rien pour argos-ui/argos-desktop ; aucun fichier THIRD-PARTY/NOTICE dans le dépôt ; Dockerfile:30 copie seulement target/quarkus-app.
```

**Impact** — Les obligations d'attribution (MIT/Apache/BSD) ne sont pas matérialisées dans l'image distribuée ou l'installeur ; une dépendance transitive à licence incompatible pourrait entrer sans être vue.

**Action** — Générer en CI un rapport de licences (license-maven-plugin / cyclonedx, `npx license-checker --production`) avec liste d'autorisation conforme à ADR-0021, et l'embarquer dans l'image (THIRD-PARTY-NOTICES).

### AUD-DEP-20 — Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée

- **Statut** : Partiel · **Sévérité** : Faible (était Moyenne le 17/11) · **Axes** : Dépendances, Qualité · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01

**Vérification du 20/11 (54add78)** — Règle documentée et respectée dans le delta, mais rien ne la fait respecter mécaniquement (pas de CI ni de hook).

*Changement depuis le 17/11* : La règle est écrite : ADR-0032 (Accepté le 18/11, commit 18bb454) « un fichier V<n>__<sujet>.sql par évolution, jamais modifié après sa fusion ». Le delta la respecte : V16–V20 ajoutées (A) ; V16 modifiée deux fois (3f2fdc7 puis 7e6363f) mais les deux commits entrent dans main par la même fusion 0292acf. Aucun contrôle automatique.

```text
docs/architecture/adr/0032-migrations-flyway.md:3 (Accepté), :55, :80 (« revue : … aucune migration fusionnée modifiée ») ; `git diff --name-status 9d96406 HEAD -- argos-server/src/main/resources/db/migration` -> 5 lignes A ; .github/ absent ; aucun hook (.husky, lefthook, pre-commit absents).
```

*Réserve* : Contrôle laissé à la revue humaine ; le garde CI proposé dépend de AUD-TST-01.

**Preuve d'origine**

```text
git log -- db/migration/V13__passerelle_ia.sql -> 4549bd7 (2026-11-16, création) puis 230f395 (2026-11-17, ajout de 'QUOTA_COMPTE' au CHECK de ia_appel.issue, l.30) ; git ls-tree -r HEAD^1 (c4a35bd, main avant la PR #7) : V13 absente, donc jamais en production. application.properties:14-17 : %dev utilise la base persistante de compose (devservices désactivé) ; :23 migrate-at-start=true. Aucune CI, aucun test ni hook ne refuse la modification d'un V*__*.sql existant.
```

**Impact** — Toute base de développement migrée entre les deux commits (base compose du poste) refuse de démarrer : somme de contrôle Flyway différente à la validation. Même règle en production : une fois le NAS en service, la réécriture d'une migration déjà fusionnée empêche Argos de démarrer, ou, après un « flyway repair », laisse un schéma divergent (ici, le CHECK sans QUOTA_COMPTE ferait échouer l'écriture du journal IA).

**Action** — Règle écrite (ADR ou CONTRIBUTING) : une migration fusionnée est immuable et toute correction passe par V(n+1). Contrôle en CI : échec si `git diff --name-status origin/main...HEAD -- argos-server/src/main/resources/db/migration` contient autre chose que des ajouts (A). Option : quarkus.flyway.validate-on-migrate explicite et test qui migre une base depuis le schéma de main.

### AUD-DEP-22 — Le script de contrôle des licences (404 lignes) n'a aucun test automatisé et ne s'exécute qu'au build de l'image, hors de « mvn verify »

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 1
- **Nouveau** le 20/11
- **Dépend de** : AUD-TST-01

**Preuve**

```text
argos-server/pom.xml:211-219 (profil « licences » hors du cycle mvn verify, makeBom « requiresOnline ») ; Dockerfile:34 seul appel ; scripts/verifier-licences-serveur.mjs (404 lignes) : `grep -rl verifier-licences-serveur` hors du script lui-même -> aucun test ; docs/deploiement/synology-et-pc.md:185 vérification manuelle du 19/11 (inventaire factice). .github/ absent.
```

**Impact** — Une dépendance à licence non admise introduite dans une branche n'est vue qu'au prochain `docker compose up --build` sur le NAS, où le build échoue alors en production plutôt qu'en revue ; une régression du script (expressions de ADMISES, lecture zip) n'est détectée par aucun test.

**Action** — Ajouter des tests node:test du script sur des inventaires factices (cas rejoués dans cet audit : GPL, licence absente, BOM absent, conforme), et exécuter `mvn -P licences validate` dans la CI à créer (AUD-TST-01) sur chaque PR qui touche pom.xml.

## Info (43)

### AUD-ARC-01 — Graphe inter-modules acyclique (51 arêtes) et intégralement conforme à la table ModuleBoundariesTest

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — La protection tient : graphe toujours acyclique et intégralement conforme à ALLOWED ; les 16 package-info concordent avec la table.

*Changement depuis le 17/11* : Le graphe passe de 44 à 51 arêtes (7 nouvelles, toutes issues de regintel et d'alerting -> regintel, lot veille réglementaire, commit 3f2fdc7) ; aucune disparue. ALLOWED et les package-info ont été élargis avant le code (regintel +ingestion, scm, workspace ; alerting +regintel ; nouvelle règle regintelNEmprunteQueRlsEtErreurs).

```text
python3 -I /home/claude/audit-maj/scripts-arch/graphe.py argos-server/src/main/java/io/argos -> 51 arêtes, « SCC [] » ; compare.py -> « modules ALLOWED: 16 », « hors table: [] » ; ModuleBoundariesTest.java:32-58 ; regintel/package-info.java:8-10 ; alerting/package-info.java ; la seule référence impact->workspace reste la javadoc impact/RlsSession.java:14-15.
```

*Réserve* : Contrôle textuel (imports et noms qualifiés) : ModuleBoundariesTest n'a pas pu être exécuté ici (Maven Central bloqué) ; la conformité Java ne couvre pas les dépendances par le schéma SQL (voir AUD-ARC-06, AUD-ARC-17).

**Preuve d'origine**

```text
python3 -I /home/claude/audit-tmp/arch/graphe.py argos-server/src/main/java/io/argos -> 44 arêtes, « SCC [] » (aucun cycle) ; compare.py -> « modules ALLOWED: 16 », « hors table: [] ». La seule référence impact->workspace est une javadoc (impact/RlsSession.java:14). Les dépendances déclarées dans les 16 package-info.java sont identiques à ALLOWED (ModuleBoundariesTest.java:30-56).
```

**Impact** — Les frontières déclarées sont respectées par le code ; les sources de vérité code (ALLOWED, package-info, arc42 §5.6) concordent.

**Action** — Aucune ; conserver la règle « table d'abord, puis arc42 » (javadoc du test).

### AUD-ARC-08 — Le module api reste une couche d'exposition mince, sans accès SQL

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — La protection tient : api ne contient toujours aucun accès JDBC/SQL ; les nouvelles ressources délèguent aux modules.

*Changement depuis le 17/11* : api passe de 35 fichiers/2 997 l. à 42/4 389 l. (api.reglementaire, api.veille, extensions artiste) sans accès SQL.

```text
grep -rnE "DataSource|prepareStatement|createStatement|EntityManager|SELECT .*FROM" argos-server/src/main/java/io/argos/api -> aucun résultat ; plus grosse ressource api/reglementaire/ReglementaireResource.java (397 l., 17 opérations) dont les méthodes privées ne sont que des conversions (vue :375, etatValidation :379, date :387).
```

*Réserve* : Aucune règle ArchUnit ne fige encore « api ne dépend pas de javax.sql ».

**Preuve d'origine**

```text
grep -rn "DataSource|prepareStatement|createStatement|EntityManager|SELECT " api -> aucun résultat (règle arc42 §5.5-4 respectée). Plus gros contrôleur : api/RadarResource.java (245 lignes) dont la seule orchestration est le calcul d'horizon et la jointure des libellés (RadarResource.java:171-196). Contrôle d'accès en tête de chaque méthode (membre/administrateur, RadarResource.java:230-244).
```

**Impact** — Les règles métier restent dans les modules ; l'API REST et le serveur MCP peuvent partager les mêmes services.

**Action** — Aucune ; optionnellement une règle ArchUnit « api ne dépend pas de javax.sql » pour figer §5.5-4, et déplacer le calcul d'horizon de RadarResource dans releaseradar.

### AUD-ARC-13 — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — La protection tient : la couche mise en page n'importe que vue, les polices et ses propres fichiers ; le processus principal desktop n'appelle pas l'API.

*Changement depuis le 17/11* : Test d'architecture de l'IHM étendu (architecture.test.mjs : polices @fontsource, interdiction d'appel réseau) ; desktop : le processus principal gagne l'écoute OIDC de bouclage mais n'appelle toujours pas l'API.

```text
grep -rh '^import' argos-ui/src/mises-en-page -> vue, @fontsource/*, fichiers locaux, ../../contenu/MentionsObligatoires.vue ; argos-ui/tests/architecture.test.mjs:21-60 (règle et détection fetch/XMLHttpRequest/WebSocket) ; argos-desktop/src/main.js:76 seul net.fetch, vers un fichier local ; connexion.js:135 serveur http local de retour OIDC, échange du code par l'IHM (05-vue-blocs.md:48).
```

**Preuve d'origine**

```text
grep import argos-ui/src/mises-en-page -> uniquement ../../contenu/MentionsObligatoires.vue et le type contenu/modeles ; grep -rln api/client argos-ui/src -> views/AffichageView.vue, views/AlertesView.vue, views/StatusView.vue, contenu/suivi-poste.ts ; test argos-ui/tests/architecture.test.mjs. argos-desktop/src/main.js:72 : seul net.fetch, vers un fichier local (pas d'appel API), conforme à 05-vue-blocs.md:44.
```

**Impact** — La règle « une mise en page n'appelle pas l'API » (arc42 §5.4.1) est tenue et testée.

**Action** — Aucune.

### AUD-ARC-18 — Les quatre nouveaux sous-paquets et les nouvelles arêtes du lot réglementaire sont couverts par ModuleBoundariesTest, déclarés « table d'abord »

- **Statut** : Info · **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
Nouveaux paquets à 54add78 (git ls-tree, comparaison avec 9d96406) : api/reglementaire, api/veille, catalog/dictionnaire, ingestion/legi, tous sous un module de premier niveau présent dans ALLOWED, donc soumis à chaqueModuleRespecteSesDependances (ModuleBoundariesTest.java:72-85) ; catalog.dictionnaire explicitement ajouté à l'exception catalog (:174) ; ingestion (Set.of(), :57) garantit que ingestion.legi reste sans dépendance métier ; regintel -> ingestion/scm/workspace et alerting -> regintel ajoutés à la table (:42-49, entrées :44 et :49), aux package-info (regintel/package-info.java:8-10) et à arc42 §5.4.3 dans le même commit 3f2fdc7 que le code ; aucun paquet de premier niveau nouveau.
```

**Impact** — La discipline « table, puis arc42, puis code » tient sur le plus gros lot du delta ; le graphe reste acyclique.

**Action** — Aucune ; le trou résiduel est la règle « tout paquet de premier niveau doit figurer dans ALLOWED » (AUD-ARC-07).

### AUD-QUA-14 — La gestion d'erreurs côté serveur est saine : aucun catch vide, aucun printStackTrace, interruptions rétablies, messages exposés assainis

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Les règles de gestion d'erreurs et de journalisation tiennent, y compris dans regintel, inventaire et l'IHM.

*Changement depuis le 17/11* : La protection tient sur le delta : 0 printStackTrace/System.out, 0 TODO/FIXME/XXX/HACK (serveur, UI, desktop, scripts, e2e), 51 catch (Runtime)Exception aux frontières, interruption rétablie aux 10 catch InterruptedException ; les 10 catch sans journalisation directe sont commentés et légitimes (ex. regintel/DatesTexte.java:72, ingestion/web/RecuperateurWeb.java:173). 36 loggers.

```text
grep -rnE "printStackTrace|System\.(out|err)" argos-server/src/main/java -> 0 ; grep -rnE "\b(TODO|FIXME|XXX|HACK)\b" argos-server/src argos-ui/src argos-desktop/src scripts e2e -> 0 ; grep -rn "currentThread().interrupt()" -> 10 = grep -rn "catch (InterruptedException" -> 10 ; UI : catch avec message utilisateur (argos-ui/src/contenu/TraitementImpacts.vue:30, session/gestionnaire.ts:139-219).
```

*Réserve* : Des messages d'exception de domaine sont désormais persistés tels quels (regintel/CollecteReglementaire.java:153-154 marquerEchec(jour, e.getMessage(), ...), inventory/InventoryService.java:252 recordFailure(..., e.getMessage())) au lieu du seul nom de classe ; contenu maîtrisé (messages construits par ARGOS) mais la règle n'est toujours pas écrite dans arc42 §8.

**Preuve d'origine**

```text
grep -rnE "printStackTrace|System\.(out|err)" argos-server/src/main/java -> 0 ; grep -rnE "TODO|FIXME|XXX|HACK" sur argos-server, argos-ui, argos-desktop, scripts -> 0 ; 36 catch (Runtime)Exception, tous placés aux frontières (observateurs, tâches planifiées, boucles par espace/compte) avec LOG.errorf(e, ...) et identifiant, ex. CorrelationEngine.java:79-80, RappelsRadar.java:121-122 ; InterruptedException traitée avec Thread.currentThread().interrupt() en 8 endroits (PasserelleIa.java:129-131, MoteurAlertes.java:264-267, SourceSynchronizer.java:78-79...) ; messages persistés ou renvoyés réduits au nom de classe (SourceSynchronizer.java:86-87, CanalEmail.java:35, EnvoiAlertes.java:146) ; GestionErreurs.java ne mappe que des exceptions de domaine. Journaux : 24 loggers, seuls des identifiants sont journalisés (aucun e-mail, jeton ni prompt trouvé par grep).
```

**Impact** — Robustesse correcte des traitements de fond : une erreur sur un élément n'interrompt pas les autres et laisse une trace exploitable ; pas de fuite de détails techniques vers le client.

**Action** — Conserver ces règles ; les inscrire dans arc42 §8 (gestion des erreurs et journalisation) pour qu'elles survivent aux contributions futures.

### AUD-QUA-15 — Les ressources JDBC sont systématiquement fermées (try-with-resources), y compris le cas particulier du verrou consultatif

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Fermeture systématique des ressources JDBC confirmée à 54add78.

*Changement depuis le 17/11* : Tient : 130 getConnection(), 129 dans un try-with-resources ; le seul autre reste signal/web/StockActualites.java:127-156 avec transfert explicite à Verrou (:57-77).

```text
grep -rn "getConnection()" argos-server/src/main/java | wc -l -> 130 ; grep -v "try (" -> seulement StockActualites.java:130.
```

**Preuve d'origine**

```text
grep -rn "getConnection()" argos-server/src/main/java -> 93 appels, 92 dans un try-with-resources ; le seul autre, signal/web/StockActualites.java:127-156, ferme la connexion dans un finally sauf transfert explicite de propriété à Verrou (AutoCloseable, :57-77) qui libère le verrou et rend la connexion.
```

**Impact** — Pas de fuite de connexions du pool identifiée par lecture.

**Action** — Aucune action ; conserver le motif et le reprendre dans l'utilitaire JDBC proposé par AUD-QUA-06.

### AUD-QUA-16 — Typage TypeScript strict tenu dans argos-ui (vue-tsc sans erreur, un seul contournement)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Le typage strict tient ; l'affirmation « taille maîtrisée, pas de fichier dieu » ne tient plus qu'en partie.

*Changement depuis le 17/11* : Partie TypeScript tenue : argos-ui strict + noUnused*, vue-tsc --noEmit passe (rc=0) sur une copie à 54add78 ; un seul contournement introduit (argos-ui/src/mises-en-page/contraste.ts:45 « as unknown as readonly [number, number, number] »). Partie taille régressée : l'UI passe de 2 261 à 8 732 lignes (plus gros fichier argos-ui/src/views/RechercheView.vue 488 l.) et le serveur de 20 574 à 28 436 lignes ; voir nouveau constat AUD-QUA-18.

```text
cd build-QUAL/argos-ui && npx vue-tsc --noEmit -> rc=0 ; grep -rnE ": any|as any|as unknown|@ts-ignore|eslint-disable" argos-ui/src argos-desktop/src -> 1 (contraste.ts:45) ; find argos-server/src/main/java -name '*.java' | xargs wc -l | sort -rn -> InventoryQueries.java 556.
```

*Réserve* : Régression de la protection « taille » : 19 fichiers Java > 300 lignes (9 à 9d96406), méthodes longues allongées ; proposé en AUD-QUA-18.

**Preuve d'origine**

```text
Plus gros fichier Java : scm/RepositoryActivity.java (394 lignes) ; méthode la plus longue : alerting/ResumesPeriodiques.java:118 composerPour (100 lignes), puis CorrelationEngine.java:142 (80) (script /home/claude/audit-tmp/qual-scripts/longmeth.py). UI : plus gros fichier contenu/alertes-poste.ts (277 lignes), desktop src/main.js (260). argos-ui/tsconfig : « strict: true », « noUnusedLocals », « noUnusedParameters » ; grep -rnE ": any|as any|as unknown|@ts-ignore|eslint-disable" argos-ui/src argos-desktop/src -> 0. Le module pur alertes-poste.ts injecte ses dépendances (DependancesSuivi) et est testable sans DOM.
```

**Impact** — Base lisible et testable ; pas de fichier « dieu ». Seules ResumesPeriodiques.composerPour et CorrelationEngine.deterministic gagneraient à être découpées.

**Action** — Aucune action obligatoire ; découper composerPour (lecture, filtrage RLS, écriture) lors de la prochaine évolution des résumés.

### AUD-SEC-10 — Aucun secret réel versionné ni dans l'historique git

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : aucun secret réel versionné ni dans l'historique du delta.

*Changement depuis le 17/11* : Delta : secrets de test seulement (client argos-server/secret-de-test du royaume de test dans e2e, jetons « jeton-fictif-e2e », mot de passe argos de la base e2e jetable).

```text
`git grep -nIiE '(password|secret|token|api[_-]?key)\s*[:=]' HEAD` : application.properties:17,283,286 (dev/test), .env.example:6, e2e/support/donnees.ts:16, e2e/performance/mesurer-p95.mjs:39, e2e/playwright.config.ts:39-40, e2e/compose.yaml:14 (valeurs de test) ; `git log -p 9d96406..HEAD | grep -E 'ghp_|github_pat_|glpat-|AKIA|BEGIN .*PRIVATE KEY|sk-'` : rien ; .env et e2e/.env ignorés (.gitignore:17) ; e2e/resultats (33 fichiers versionnés) sans jeton (grep eyJ…/Bearer/access_token : rien).
```

*Réserve* : Le client argos-server du royaume de test autorise le flux par mot de passe (directAccessGrantsEnabled) : à ne jamais reproduire dans le royaume de production.

**Preuve d'origine**

```text
`git grep -nIiE '(password|secret|token|api[_-]?key)\s*[:=]'` : seulement des références d'environnement (application.properties:12,51,52,79), les valeurs de développement/test (application.properties:17,209,212 ; src/test/resources/keycloak/argos-realm.json:13,95, comptes de test) et .env.example:6 (exemple) ; `git log --all -p | grep -E 'ghp_|github_pat_|glpat-|AKIA|BEGIN .*PRIVATE KEY|sk-'` : uniquement des jeux d'essai factices des tests du filtre IA. .env et .env locaux ignorés (.gitignore, .dockerignore).
```

**Impact** — Pas de rotation nécessaire. Les secrets de test (secret-de-test, mots de passe des comptes de test) ne doivent jamais servir hors profils %dev/%test.

**Action** — Aucune ; conserver la règle « tout secret par variable d'environnement ».

### AUD-SEC-11 — Requêtes SQL paramétrées ; les seules concaténations portent sur des constantes

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient.

*Changement depuis le 17/11* : Nouvelles requêtes (inventaire, réglementaire, impacts) : seules des constantes sont concaténées.

```text
argos-server/src/main/java/io/argos/regintel/ExpertiseFiches.java:232 `"UPDATE fiche_reglementaire SET " + affectations` : affectations toujours littérales (appels l.89-163) ; inventory/InventoryQueries.java:273 (constante COMPONENT_NAME) ; regintel/FichesEnBase.java:132 (COLONNES_FICHE) ; impact/ImpactQueries.java:156 (SELECT constant) ; aucune construction de SQL à partir d'un paramètre de requête trouvée (grep des prepareStatement à concaténation dans inventory, regintel, impact, api, catalog, signal).
```

**Preuve d'origine**

```text
workspace/EspacesService.java:325 `... WHERE " + filtre` : filtre toujours littéral (appels l.199, 206, 230, 248), valeur liée par setObject ; impact/RechercheVeille.java:136 selection() constante ; V11__recherche_veille.sql:102-129 `RETURN QUERY EXECUTE $q$ ... $q$ USING ...` (texte constant, saisies en paramètres) ; requête utilisateur via websearch_to_tsquery (V11:29).
```

**Impact** — Pas d'injection SQL identifiée dans les *Queries, *Store, RechercheVeille, SubscriptionService.

**Action** — Ne pas transformer lireInvitations(filtre) en API recevant un texte externe ; garder EXECUTE ... USING.

### AUD-SEC-12 — Authentification : jeton OIDC obligatoire partout, audience vérifiée, aucun mode sans authentification

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : règle /* authenticated, seules /api/v1/system/info et /q/health* publiques, aucun résolveur sans authentification.

*Changement depuis le 17/11* : Caller étendu (contributorWorkspaces) ; ReglementaireResource ajoute @Authenticated (redondant avec /*).

```text
argos-server/src/main/resources/application.properties:274-277 (service, audience, auth-server-url sans défaut en prod), :292-295 (public / authentifie=/*) ; api/security/OidcCallerResolver.java seul résolveur de production ; `find argos-server/src -name 'HeaderCallerResolver*' -o -name 'Unauthenticated*'` : rien en main ; AuthentificationTest.java présent.
```

*Réserve* : La règle /* couvre aussi l'IHM copiée dans META-INF/resources (Dockerfile:36) : le portail web de l'image reste inaccessible (constat d'architecture issu de AUD-SEC-02, hors de cet axe).

**Preuve d'origine**

```text
application.properties:200-218 (application-type=service, token.audience=argos-server, auth-server-url sans valeur par défaut en prod, /* authenticated) ; seul CallerResolver de production : api/security/OidcCallerResolver.java ; UnauthenticatedCallerResolver supprimé de l'historique ; HeaderCallerResolver uniquement dans src/test ; tests AuthentificationTest (401 sur /api/v1/espaces, /q/openapi).
```

**Impact** — Socle d'authentification sain ; l'administrateur plateforme n'est désigné que par l'e-mail vérifié ARGOS_ADMIN_EMAIL à la création du compte (ComptesService).

**Action** — Aucune ; ne pas ajouter de règle permit élargie (voir AUD-SEC-02).

### AUD-SEC-13 — Autorisation par espace vérifiée dans chaque ressource REST, doublée par la RLS PostgreSQL (pas d'IDOR trouvé)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : pas d'IDOR inter-espaces trouvé sur les routes du delta.

*Changement depuis le 17/11* : Nouvelles routes : projets et inventaire (memberOfProject), impacts du projet, transition d'impact (contributorOf + politique RLS project_impact_etat), abonnements suspension/réactivation/suppression (authorizedManager), réglementaire (member/administrator, RLS V16 sur fiches et domaines), veille personnelle (filtre PROJECT borné aux espaces de l'appelant).

```text
argos-server/src/main/java/io/argos/api/security/Access.java:71-78 (memberOfProject) ; api/InventoryResource.java:109-111 ; api/ProjectResource.java:184 ; api/ImpactResource.java (transition : visible() puis contributorOf) ; api/SubscriptionResource.java (authorizedManager) ; api/reglementaire/ReglementaireResource.java:148,162,171,183,194,202,213 ; regintel/ExpertiseFiches.java:216-227 ; api/SignauxAbonnements.java:88-97 ; V20__etat_traitement_impacts.sql:96-113 (SECURITY DEFINER, search_path fixé) ; V18:28-38 ; V19:92-101.
```

*Réserve* : Exception de nature différente (pas un IDOR) : la route demande-evolution appelle la forge avant le contrôle d'expertise (AUD-SEC-22).

**Preuve d'origine**

```text
Contrôles memberOf/adminOf : RadarResource (membre/administrateur), InventoryResource.authorize, ActiviteResource.autoriser, VeilleWebResource.sourceGeree/exigerAdministrateur, SubscriptionResource (TEAM réservé à l'administrateur), EspacesService.exiger ; alertes filtrées par compte_id (ConsultationAlertes:95,119) ; RLS argos_app NOBYPASSRLS (V2:176) avec politiques V2/V6/V10/V11/V12/V14 ; hors périmètre = 404 journalisé (GestionErreurs). Signaux = données publiques (lecture par tout compte authentifié, voulu).
```

**Impact** — Pas d'accès inter-espaces identifié par remontée des flux.

**Action** — Aucune ; tout nouvel endpoint doit appeler access.* et passer par ContexteBase.

### AUD-SEC-14 — Garde SSRF de la veille web complète hors rebinding (plages, IPv6 embarquées, ports, redirections, tailles)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient hors rebinding (AUD-SEC-01) ; les nouveaux flux ne visent que des origines de configuration.

*Changement depuis le 17/11* : Nouveaux flux sortants : archives JORF (URL de configuration, sans redirection, nom d'archive filtré par regex), demandes d'évolution (origine exacte de la forge configurée, chemin validé, sans redirection), imports planifiés (dépôts définis hors API, URL de forge configurée).

```text
argos-server/src/main/java/io/argos/ingestion/web/RecuperateurWeb.java:100 (redirections revérifiées, inchangé) ; argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:47 (`JORF_(\d{8})-(\d{6})\.tar\.gz`), :79 (Redirect.NEVER) ; argos-server/src/main/java/io/argos/scm/DemandesEvolution.java:35-36,84-101,140-143 (même origine, regex de chemin) ; argos-server/src/main/java/io/argos/scm/ForgeHttp.java:301 (Redirect.NEVER) ; inventory/ProjectDirectory.java:56,91 (create/ajouterDepot sans appelant REST).
```

**Preuve d'origine**

```text
ingestion/web/GardeAdresses.java (plages IPv4/IPv6, NAT64/6to4/mappées, ports 80/443, identifiants refusés, messages sans nom d'hôte) ; RecuperateurWeb.java:100 chaque redirection revérifiée, redirections suivies à la main (Redirect.NEVER), délai et taille bornés ; liens d'articles jamais récupérés ; sources d'avis et forges sans redirection (HttpSourceClient.java:35, ForgeHttp.java:36) et à URL de configuration uniquement ; aucun endpoint de création de projet (ProjectDirectory.create sans appelant REST), dépôt GitHub validé par regex.
```

**Impact** — Protection à conserver ; seul le défaut de AUD-SEC-01 reste ouvert.

**Action** — Ne pas assouplir ; renseigner argos.veille-web.exceptions uniquement en dev/test.

### AUD-SEC-15 — XXE neutralisé dans la lecture RSS/Atom

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient et s'étend au nouveau parseur réglementaire.

*Changement depuis le 17/11* : Ajout du lecteur XML JORF (StAX) avec DTD et entités externes désactivées, DOCTYPE et références d'entité refusés, et test dédié.

```text
argos-server/src/main/java/io/argos/ingestion/legi/LecteurJorf.java:63-68 (SUPPORT_DTD=false, IS_SUPPORTING_EXTERNAL_ENTITIES=false, IS_REPLACING_ENTITY_REFERENCES=false), :85-86 (DTD/ENTITY_REFERENCE refusés) ; test LecteurJorfTest.java avec fixtures/jorf/xxe.xml ; LecteursTest.java (RSS) toujours présent ; `grep -rlnE 'DocumentBuilderFactory|SAXParser|ObjectInputStream|activateDefaultTyping'` : rien.
```

**Preuve d'origine**

```text
ingestion/web/LecteurFlux.java (ROME 2.1.0, pom.xml:24, DOCTYPE refusé par défaut) ; test LecteursTest.java:69-74 avec `<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>` refusé ; contenu réduit en texte par Jsoup ; aucune autre analyse XML ni désérialisation Java (grep ObjectInputStream/activateDefaultTyping/DocumentBuilderFactory : rien).
```

**Impact** — Pas d'XXE ni de désérialisation dangereuse.

**Action** — Garder le test ; ne pas activer setAllowDoctypes(true).

### AUD-SEC-16 — Serveur MCP : authentification, limitation et balisage du contenu externe en place (8 outils)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : authentification, limitation et balisage en place sur les 8 outils.

*Changement depuis le 17/11* : 8 outils (ajout de upcoming_releases et get_regulatory_changes notamment), tous derrière garde.appeler ; versions de protocole antérieures acceptées (sessions en mémoire, inactivité 30 min) et négociations limitées par ControleInitialisation.

```text
argos-server/src/main/java/io/argos/mcp/OutilsSecurite.java:157,249,350,381 ; OutilsProjets.java:62 ; OutilsReferentiel.java:64,85 ; OutilsReglementaires.java:67 ; mcp/ControleInitialisation.java:54-64 ; application.properties `quarkus.mcp.server.connection-idle-timeout=30M`.
```

*Réserve* : Les sessions MCP ouvertes par négociation ne sont bornées qu'en débit par sujet (60/min), pas en nombre total ; chaque refus alimente refus_acces (AUD-SEC-03).

**Preuve d'origine**

```text
Les 7 outils (OutilsSecurite/Projets/Referentiel/Reglementaires) passent par garde.appeler ; mcp/GardeAppels.java:74 limiteur 60/min/compte ; refus journalisés ; /mcp couvert par la règle /* authenticated ; contenu externe marqué CONTENU_EXTERNE_NON_FIABLE et tronqué (BalisageExterne) ; outils en lecture seule.
```

**Impact** — Surface MCP maîtrisée ; injection indirecte vers l'assistant signalée, pas supprimable.

**Action** — Aucune.

### AUD-SEC-17 — Passerelle IA : données filtrées, sortie validée, fournisseur non pilotable par l'utilisateur

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : l'IA ne déclenche aucune action sans validation humaine.

*Changement depuis le 17/11* : Nouvelles tâches IA réglementaires (résumé de fiche, proposition de domaine) : texte officiel en entrée « publique » balisée, domaine proposé borné à la liste de l'espace, validation par un expert obligatoire avant diffusion.

```text
argos-server/src/main/java/io/argos/aigateway/PasserelleIa.java:99 (classes), :106 (secrets) ; argos-server/src/main/java/io/argos/regintel/AnalyseIa.java:58-75 (rang hors liste ou confiance hors 1..100 ignorés) ; regintel/ExpertiseFiches.java:121-139 (validation par expert).
```

*Réserve* : AnalyseIa.java:69-70 appelle Integer.parseInt sur des champs validés par schéma : robustesse à garder sous test.

**Preuve d'origine**

```text
aigateway/PasserelleIa.java:99 refus des classes non transmissibles, :105 refus si secret détecté, :160 chevrons neutralisés dans les données ; ValidationSortie par schéma ; URL Ollama de configuration seulement (application.properties argos.ia.ollama.url) ; analyse de corrélation (noms de projets) désactivée par défaut ; Ollama sans port publié (compose.yaml). Sujets d'e-mail nettoyés des CR/LF (CanalEmail.sujet) et e-mail en texte brut.
```

**Impact** — L'injection de prompt par un flux externe ne peut produire qu'un texte affiché comme « produit par l'IA », sans action ni outil.

**Action** — Aucune ; garder l'IA sans outils ni actions.

### AUD-SEC-18 — Client Electron durci

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient ; le défaut résiduel de l'écoute locale fait l'objet de AUD-SEC-23.

*Changement depuis le 17/11* : Ajout de la connexion OIDC par navigateur système et écoute 127.0.0.1 (connexion.js) et de deux canaux IPC, gardés par depuisFenetre ; CSP étendue à la seule origine de l'émetteur.

```text
argos-desktop/src/main.js:96-98 (contextIsolation, nodeIntegration false, sandbox), :110-120 (openExternal http(s) seulement, navigation bloquée), :241-243 (permissions) ; argos-desktop/src/canaux.js:34-36,63-83 (garde sur les 9 canaux dont argos:login-*) ; argos-desktop/src/ui-protocol.js:16-33,39-58 ; argos-desktop/src/preload.js:53-54 (valeurs simples).
```

*Réserve* : main.js:115 compare toujours `url.startsWith(DEV_URL)` en mode --dev (action d'origine non faite).

**Preuve d'origine**

```text
argos-desktop/src/main.js:92-94 contextIsolation true, nodeIntegration false, sandbox true ; :107,114 openExternal seulement pour http(s), navigation hors app://argos bloquée ; :205 IPC acceptée seulement depuis la fenêtre principale ; permissions limitées aux notifications ; ui-protocol.js:51 traversée de chemin refusée et CSP stricte (script-src 'self', object-src 'none') ; preload n'expose que des valeurs simples.
```

**Impact** — Pas de vecteur d'exécution de code depuis le contenu affiché.

**Action** — Aucune ; pour le dev, comparer l'origine (new URL(url).origin === DEV_URL) plutôt que startsWith.

### AUD-SEC-19 — IHM Vue sans rendu HTML brut des contenus de veille

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : aucun v-html/innerHTML/eval.

*Changement depuis le 17/11* : Nouvelles vues (recherche, inventaire, veille, mises en page) : liens externes toujours via urlSure.

```text
`grep -rnE 'v-html|innerHTML|eval\(' argos-ui/src` : rien ; argos-ui/src/views/RechercheView.vue:386, views/AlertesView.vue:88, contenu/MentionsObligatoires.vue:13,30 (urlSure, rel=noopener noreferrer) ; côté serveur VeilleProfilsResource.java:252-258 (lienHttp).
```

*Réserve* : Pas de règle de lint interdisant v-html (action d'origine non faite).

**Preuve d'origine**

```text
`grep -rnE 'v-html|innerHTML|eval\(' argos-ui/src` : aucun résultat ; liens externes filtrés par urlSure (argos-ui/src/contenu/liens.ts, http/https seulement) avec rel=noopener noreferrer (MentionsObligatoires.vue:32, AlertesView.vue:76) ; serveur réduit les flux en texte (LecteurFlux.texte).
```

**Impact** — XSS par contenus externes non identifiée.

**Action** — Interdire v-html par règle de lint.

### AUD-SEC-20 — Conteneurs et exposition réseau raisonnables ; CSRF non applicable

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient.

*Changement depuis le 17/11* : Dockerfile restructuré (contrôle des licences, avis en 0444 root) ; utilisateur 1001 et expositions inchangés.

```text
Dockerfile:47,51 (useradd 1001, USER 1001) ; compose.yaml:23 (PostgreSQL 127.0.0.1), :58 (Argos 127.0.0.1 par défaut), :65-76 (Ollama sans port) ; application.properties:266 (CORS app://argos), :274 (application-type=service, jeton porteur sans cookie).
```

*Réserve* : Les fichiers de l'application restent propriété de l'utilisateur d'exécution (Dockerfile:48) : voir AUD-SEC-25.

**Preuve d'origine**

```text
Dockerfile:27-29 utilisateur système 1001 (non root) ; compose.yaml:23 PostgreSQL sur 127.0.0.1, :58 Argos sur 127.0.0.1 par défaut, Ollama sans port ; application.properties:192 CORS limité à app://argos en prod ; authentification par jeton porteur sans cookie (application-type=service) : pas de surface CSRF.
```

**Impact** — Surface réseau minimale par défaut.

**Action** — Aucune.

### AUD-SEC-21 — Delta du lot 4 conforme : RLS sur les suivis, sources réservées à l'administrateur et collectées après vérification des conditions, hôtes data.culture filtrés, liens externes assainis

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité, Architecture · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient.

*Changement depuis le 17/11* : Aucun fichier du lot 4 modifié dans le delta (`git diff --stat 9d96406 HEAD` sur api/artiste, signal/opportunite, workspace/suivi, V15 : vide).

```text
argos-server/src/main/java/io/argos/api/artiste/ProfilArtisteResource.java:243,259,274,287-288 ; signal/opportunite/CollecteOpportunites.java:112,121,124 ; tests SuiviIsolationTest.java et ProfilArtisteTest.java présents.
```

**Preuve d'origine**

```text
V15__profil_artiste.sql : ENABLE ROW LEVEL SECURITY + politiques compte_id = argos_compte_courant() sur suivi_profil et suivi_rappels ; workspace/suivi/SuivisProfil.java:71,80 (ContexteBase) ; ProfilArtisteResource.java:243,259,274,287 access.operator() et :288 limiteur ; CollecteOpportunites.java:112 (conditions vérifiées), :121 (hôte admis), :124 (RecuperateurWeb + GardeAdresses) ; CorrespondanceListes.java:71-80 (http/https sans userinfo) ; ResumesIaActualites.java:87 (contrôle d'accès avant le cache partagé) ; aucune nouvelle arête inter-modules (comparaison des imports f58a68c/HEAD) ; SuiviIsolationTest ; ProfilArtisteTest.java:306-308 (403).
```

**Impact** — Aucun IDOR trouvé sur les nouvelles routes. Les opportunités sont des données publiques non cloisonnées, à dessein (GET /opportunites/{id}/resume accessible à tout compte dont le profil artiste est actif).

**Action** — Aucune.

### AUD-SEC-26 — Flux OIDC Authorization Code + PKCE de l'IHM et du desktop conforme (ADR-0033) : PKCE S256, state vérifié en temps constant, iss contrôlé, jetons en mémoire, redirections internes filtrées

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
argos-ui/src/session/oidc.ts:58-68 (aléa crypto 32 octets, S256), :74-91 (émetteur de découverte = émetteur configuré), :121-126,148 (state en temps constant), :150-153 (iss RFC 9207), :169-185 (code/state retirés de l'adresse, cheminInterne anti-redirection ouverte), :227-229 (token_type bearer) ; argos-ui/src/session/gestionnaire.ts:84 (jetons en champ privé), :257-264 (credentials omit) ; argos-ui/src/session/session.ts:50-71 (sessionStorage : demande seule, effacée avant vérification) ; argos-ui/src/api/autorisation.ts:9-32 (Bearer seulement vers /api/v1, jamais dans l'URL) ; argos-desktop/src/connexion.js:65-91 (URL d'autorisation reconstruite depuis l'émetteur configuré, PKCE S256 et redirect_uri de l'écoute exigés), :26-32 (page de retour statique, CSP default-src 'none', no-store) ; royaume de test argos-ui : implicite désactivé, directAccessGrants false, PKCE S256 imposé, redirections http://localhost:5173/* et http://127.0.0.1/argos/retour ; tests ClientIhmOidcTest.java (Keycloak réel), argos-ui/tests/oidc.test.mjs, session.test.mjs, argos-desktop/test/connexion.test.js. Pas de nonce : non requis ici (OIDC Core 3.1.2.1, nonce facultatif en flux code ; l'id_token n'est utilisé que comme id_token_hint, ADR-0033 option B ; PKCE couvre l'injection de code, RFC 9700).
```

**Impact** — Aucun jeton dans un stockage persistant, une adresse ou un journal ; pas de CSRF de connexion ni de redirection ouverte identifiés.

**Action** — Aucune ; dans le royaume de production, ne déclarer que les redirections exactes du portail (pas de joker) et imposer HTTPS sur l'émetteur.

### AUD-SEC-27 — Veille réglementaire et inventaire : archives JORF bornées sans XXE ni écriture disque, routes réglementaires et impacts doublés par la RLS, aucune route d'import de SBOM

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java:41-87 (entrées, taille par fichier 5 Mio, volume décompressé bornés ; liens ignorés ; rien écrit sur disque) ; ArchivesJorf.java:47,79 ; LecteurJorf.java:63-68,85-86 ; application.properties (argos.reglementaire.jorf.taille-max-octets, entrees-max, decompresse-max-octets) ; V16__veille_reglementaire.sql:181-220 (fonctions SECURITY DEFINER avec search_path fixé, EXECUTE révoqué de PUBLIC), :235-335 (RLS fiches, domaines, experts, historique) ; api/InventoryResource.java:25-30 (lecture seule, aucune route d'import) ; inventory/CycloneDxParser.java:51-53 et application.properties argos.inventory.max-document-bytes=20971520 (JSON seulement, taille bornée) ; scm/RepositoryFiles.java:115-136,172,214 (segments encodés, taille bornée).
```

**Impact** — Pas de XXE, de bombe d'archive, de traversée de chemin ni d'IDOR sur ces nouveaux flux.

**Action** — Aucune ; conserver le test xxe.xml et ajouter un test d'archive hors bornes si absent.

### AUD-SEC-28 — Outillage e2e confiné à la boucle locale : secrets de test seulement, sources simulées sur 127.0.0.1/::1 sans traversée, docker/git/taskkill par chemin absolu

- **Statut** : Info · **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
e2e/support/sources-simulees.mjs:30-35 (fichier() refuse toute sortie de donnees/chaine), :43-57 (corps borné à 64 Kio), :102-105 (écoute 127.0.0.1 et ::1) ; e2e/compose.yaml:15-16 (port 127.0.0.1:55432, tmpfs) ; e2e/support/docker.mjs:5-19 ; e2e/performance/mesurer-p95.mjs:79-84,95-96,242 (git/taskkill absolus, execFileSync sans shell), :250-251 (verifierHoteLocal sur l'API et l'émetteur) ; e2e/support/donnees.ts:53-59 (psql via execFileSync, arguments en tableau).
```

**Impact** — Les campagnes ne visent aucun hôte distant et n'exposent aucun service hors du poste ; seule réserve : AUD-SEC-24.

**Action** — Aucune.

### AUD-TST-13 — argos-ui : npm test passe (206/206) et la vérification de types vue-tsc passe

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — npm test passe (206/206) et vue-tsc passe.

*Changement depuis le 17/11* : Les tests sont passés de 25 à 206.

```text
cd build-tests/argos-ui && npm ci (4,1 s) && npm test -> « # tests 206 # pass 206 # fail 0 # skipped 0 # duration_ms 6685 » ; npm run typecheck (vue-tsc --noEmit) -> code 0 (5,1 s)
```

**Preuve d'origine**

```text
cd build-tests/argos-ui && npm ci && npm test -> « # tests 25 # pass 25 # fail 0 # skipped 0 # duration_ms 524.0763 » (real 3,1 s avec npm ci) ; npm run typecheck (vue-tsc --noEmit) -> code de sortie 0
```

**Impact** — Les tests existants sont sains et rapides ; le test d'architecture IHM (tests/architecture.test.mjs, couche mise en page sans réseau) est exécuté.

**Action** — Aucune ; les brancher dans la CI (AUD-TST-01).

### AUD-TST-14 — argos-desktop : npm test passe (54/54) et check:syntax passe

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — npm test passe (54/54) et check:syntax passe sur les 8 fichiers source.

*Changement depuis le 17/11* : Les tests sont passés de 22 à 54 (8 fichiers ; nouveaux : canaux, chaine-poste, connexion, preload).

```text
cd build-tests/argos-desktop && ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci && npm test -> « # tests 54 # pass 54 # fail 0 # duration_ms 4453 » ; npm run check:syntax -> code 0
```

**Preuve d'origine**

```text
cd build-tests/argos-desktop && ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci && npm test -> « # tests 22 # pass 22 # fail 0 # duration_ms 205.05 » ; npm run check:syntax -> OK
```

**Impact** — Les modules purs du client (préférences, notifications, poste, protocole UI/CSP) sont testés et passent.

**Action** — Aucune ; utiliser npm run check dans la CI.

### AUD-TST-15 — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Aucun test désactivé ni focalisé.

*Changement depuis le 17/11* : Toujours un seul test serveur conditionnel. Côté IHM, un skip conditionnel documenté (polices.test.mjs:66-67, sans node_modules) ne se déclenche pas après npm ci (skipped 0). E2E : forbidOnly: true et retries: 0 dans les deux configurations Playwright.

```text
grep @Disabled|@EnabledIf|@DisabledIf|assume* argos-server/src/test/java -> seul api/recherche/MesurePerformanceRechercheTest.java:48 ; grep .only(|.skip(|fixme( dans argos-ui/tests, argos-desktop/test, e2e -> seul argos-ui/tests/polices.test.mjs:67 (t.skip conditionnel) ; e2e/playwright.config.ts:49-50 ; e2e/accessibilite.config.ts:15-16
```

**Preuve d'origine**

```text
grep @Disabled|@DisabledIf|assume* src/test/java -> seul api/recherche/MesurePerformanceRechercheTest.java:48 @EnabledIfSystemProperty(named = "argos.performance", matches = "true") (campagne de 100 000 actualités, lancement documenté :45) ; grep .only(|.skip(|todo( dans argos-ui/tests et argos-desktop/test -> aucun résultat
```

**Impact** — Pas de dette de tests ignorés ; la mesure de performance Q-05 est à la demande.

**Action** — Aucune ; éventuellement planifier la campagne de performance (job nocturne).

### AUD-TST-16 — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Ports aléatoires, sources simulées, courrier simulé et planificateur désactivé tiennent.

*Changement depuis le 17/11* : Inchangé sur le fond ; numéros de ligne déplacés.

```text
application.properties:20 %test.quarkus.http.test-port=0, :90 %test.quarkus.mailer.mock=true, :92 %test.quarkus.scheduler.enabled=false ; testsupport/StubServer.java:62 HttpServer.create(new InetSocketAddress("localhost", 0), 0) ; signal/web/CollecteWebTest.java:141 horloge injectée ; grep @TestMethodOrder|@Order( src/test/java -> aucun
```

**Preuve d'origine**

```text
application.properties:20 %test.quarkus.http.test-port=0 ; testsupport/StubServer.java:53 HttpServer.create(new InetSocketAddress("localhost", 0), 0) et Javadoc :22-26 « aucun test n'appelle de service externe ni de modèle réel » ; signal/web/CollecteWebTest.java:146-153 horloge injectée (instance.horloge) ; testsupport/RecordingSleeper.java ; application.properties:83 %test.quarkus.mailer.mock=true, :85 scheduler désactivé ; aucun @TestMethodOrder/@Order
```

**Impact** — Tests reproductibles et parallélisables, sans dépendance réseau ; densité d'assertions correcte (pas d'assertTrue(true), pas de catch vide).

**Action** — Aucune.

### AUD-TST-17 — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux (MCP 42, comptes 38, alerting 35)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Couverture ciblée de la sécurité, du MCP et de l'alerting maintenue et étendue.

*Changement depuis le 17/11* : Les zones à risque ont gagné des tests : mcp 10 classes/42 méthodes (8/31), api/comptes 11/38 (9/28, dont CouvertureRlsTest et ClientIhmOidcTest), alerting 7/35 (5/28), ingestion/web 3/19 (GardeAdressesTest 9).

```text
décompte grep -rhE '@(Test|ParameterizedTest|TestFactory)' par dossier ; api/comptes/CouvertureRlsTest.java:71 touteTableAccessibleAuRoleApplicatifEstSousRls ; ingestion/web/GardeAdressesTest.java (9)
```

**Preuve d'origine**

```text
mcp : 8 classes @QuarkusTest, 31 tests (AuthentificationMcpTest 5, CloisonnementMcpTest 5, ContenuExterneMcpTest 2, ContratOutilsMcpTest 5, LimiteDebitMcpTest 2, OutilsConsultationMcpTest 7, RechercheMcpTest 3, ReglementaireMcpTest 2) ; api/comptes : 9 classes, 28 tests (Cloisonnement, DefenseEnProfondeur, JournalRefus…) ; alerting : 5 classes, 28 tests ; ingestion/web : 19 tests unitaires (GardeAdressesTest 9 = SSRF) + chain/CollecteTest, ResilienceTest, EnrichmentTest
```

**Impact** — Les zones à risque (cloisonnement par espace et RLS, authentification MCP et flux d'appareil, garde d'adresses SSRF, échec SMTP, IA indisponible) sont couvertes par des scénarios reliés aux exigences.

**Action** — Aucune ; compléter par la matrice d'autorisations (AUD-TST-05).

### AUD-TST-18 — ModuleBoundariesTest couvre les 16 modules et 6 règles fines ; il est éligible à surefire (nom *Test, JUnit 5)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Frontières des 16 modules vérifiées par ArchUnit ; exécution non constatée ici.

*Changement depuis le 17/11* : Toujours 16 modules, une règle fine de plus (regintelNEmprunteQueRlsEtErreurs : 6 règles fines au total) ; les nouvelles dépendances (regintel → scm, ingestion ; inventory → scm) sont déclarées.

```text
architecture/ModuleBoundariesTest.java:32-63 (16 entrées ALLOWED = 16 paquets de src/main/java/io/argos) ; :74-87 @TestFactory ; règles fines :93, :108, :125, :143, :162, :188
```

*Réserve* : Exécution effective à confirmer par le build Maven de l'orchestrateur ; un module absent de ALLOWED ne fait pas échouer le test (voir AUD-TST-11).

**Preuve d'origine**

```text
architecture/ModuleBoundariesTest.java:31-57 (16 modules, identiques aux paquets io.argos.*) ; :67-80 @TestFactory un test dynamique par module ; :88-174 mcpNeLitQueLaSecuriteDeLApi, seuleLaPasserelleConnaitLeFournisseur, signal/projectintel/catalogNEmprunteQueRlsEtErreurs ; pom.xml:145-150 archunit-junit5 1.5.1 en scope test ; surefire 3.5.3 sans <includes>/<excludes>
```

**Impact** — Les frontières documentées (arc42 §5) sont vérifiées dès qu'un mvn test est lancé ; exécution effective non constatée ici (build Maven impossible, cf. angles morts) et jamais automatisée (AUD-TST-01).

**Action** — Aucune ; voir AUD-TST-11 pour les modules futurs.

### AUD-TST-21 — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 35 s, sans serveur ni base

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
cd build-tests/e2e && npm ci && npx playwright test -c accessibilite-chrome-local.config.ts (copie d'audit : même configuration avec channel 'chrome', Google Chrome du conteneur au lieu du Chromium 1228 de Playwright) -> « 29 passed (34.7s) » ; e2e/accessibilite.config.ts:25-32 (Vite seul sur 127.0.0.1:5174) ; rapport de référence e2e/resultats/accessibilite/rapport-2026-11-17.json (29 attendus, 0 échec)
```

**Impact** — Contrastes (RGAA 3.2), ordre de tabulation et focus, 320 px, réduction des animations et arbre d'accessibilité des quatre mises en page sont vérifiés par une campagne légère, rejouable dans n'importe quel environnement disposant de Chromium ; c'est le meilleur candidat pour un premier job E2E en CI.

**Action** — Aucune ; la brancher en premier dans la CI (AUD-TST-01).

### AUD-TST-22 — Nouveaux tests structurants dans le delta : couverture RLS générique, contrats SCM enregistrés, migrations, pont preload et fonctions pures de la campagne de performance

- **Statut** : Info · **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
api/comptes/CouvertureRlsTest.java:71-141 (toute table accessible au rôle applicatif est sous RLS, rôle sans privilège de contournement, aucune ligne sans contexte ; requêtes sur pg_class) ; chain/ScmContratEnregistreTest.java (13 cas GitHub/GitLab enregistrés : jeton refusé, limites de débit, artefact absent) ; chain/MigrationV18Test.java, chain/MigrationDepotsTest.java ; argos-desktop/test/preload.test.js (9) et chaine-poste.test.js (6, chaîne preload → IPC → poste → notification factice) ; node --test e2e/performance/*.test.mjs -> 7/7
```

**Impact** — Les invariants de sécurité (RLS sur tout le schéma) et les contrats avec les forges sont vérifiés de façon générique, ce qui protège aussi les tables et appels futurs.

**Action** — Aucune ; réutiliser l'approche par catalogue de CouvertureRlsTest pour TestDb (AUD-TST-08).

### AUD-PER-14 — Les tâches planifiées sont protégées contre le chevauchement et idempotentes en base

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — La protection tient : les 13 @Scheduled déclarent SKIP, et l'exclusion repose sur la base (index uniques, verrous consultatifs).

*Changement depuis le 17/11* : Deux tâches planifiées ajoutées par le delta, qui en compte maintenant 13 au lieu de 7 : SynchronisationPlanifiee, ImportPlanifie, CollecteReglementaire, RappelsReglementaires, CollecteOpportunites, RappelsOpportunites, toutes en SKIP. ADR-0031 (Accepté) formalise la règle.

```text
Commande « grep -rn -A2 '@Scheduled' argos-server/src/main/java | grep -c SKIP » -> 13 ; signal/SynchronisationPlanifiee.java:46-47 ; inventory/ImportPlanifie.java:52-53 ; inventory/InventoryStore.java:167 (pg_advisory_xact_lock par projet, partagé par l'import manuel et l'import planifié) ; impact/CorrelationEngine.java:220-223 (verrou par signal) ; docs/architecture/adr/0031-planification-quarkus-scheduler.md (option A, passage multi-instance = réouverture).
```

*Réserve* : ADR-0031 décrit encore la synchronisation des avis comme « déclenchée par l'API » et compte 11 tâches (§Contexte, §Conséquences neutres) : le document n'a pas suivi le volet A. Pour un service à plusieurs instances, le verrou de cluster reste à faire, comme l'ADR le prévoit.

**Preuve d'origine**

```text
concurrentExecution = Scheduled.ConcurrentExecution.SKIP sur les 7 @Scheduled (CollecteWeb.java:83-84, CollecteActivite.java:69-70, PurgeActivite.java:32-33, EnvoiAlertes.java:72-73, RappelsRadar.java:90-91, ResumesPeriodiques.java:70-77) ; garde-fous en base : sync_run_one_running (V3:40), alerte_immediate_uk / alerte_resume_uk (V8:36-38), alerte_radar_uk (V12:134), verrou consultatif par source web (StockActualites.java:82), réservation avec bail dans EnvoiAlertes (BAIL = 5 min, LIMIT 500).
```

**Impact** — Pas de double exécution sur une instance. Sur plusieurs instances (service en ligne), les doublons de messages sont déjà évités par les index uniques et les verrous ; seul le travail est fait en double.

**Action** — Aucune action immédiate ; avant de passer à plusieurs instances, ajouter un verrou de cluster (quarkus-quartz en mode JDBC, ou pg_try_advisory_lock en tête de chaque tâche).

### AUD-PER-15 — Les appels IA sont bornés : exécuteurs dédiés à file bornée, délais maximaux, taille de réponse plafonnée, Ollama configuré à un modèle et une requête à la fois

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — La protection tient : files bornées, délais, taille de réponse plafonnée, quotas.

*Changement depuis le 17/11* : Exécuteurs IA inchangés (MoteurAlertes, SyntheseActivite) ; Ollama toujours limité à 1 modèle et 1 requête. Nouveau : l'analyse IA réglementaire (regintel/AnalyseIa) appelle la passerelle de façon synchrone dans la collecte planifiée, plafonnée à argos.reglementaire.ia.propositions-max=20.

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:76-81 (ThreadPoolExecutor(2, 2, …, ArrayBlockingQueue<>(16), AbortPolicy)) ; projectintel/SyntheseActivite.java:46-51 ; compose.yaml:71-72 (OLLAMA_MAX_LOADED_MODELS=1, OLLAMA_NUM_PARALLEL=1) ; regintel/AnalyseIa.java:59 et :73 (passerelle.demander, synchrone) ; application.properties (argos.reglementaire.ia.propositions-max=20).
```

*Réserve* : La collecte réglementaire quotidienne peut attendre l'IA jusqu'à 20 × argos.ia.delai (PT60S), soit environ 20 minutes, sur le fil du planificateur. Ce n'est pas bloquant (SKIP) mais cela s'ajoute à la file unique d'Ollama.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:75-80 (ThreadPoolExecutor(2, 2, ..., ArrayBlockingQueue<>(16), AbortPolicy)) ; argos-server/src/main/java/io/argos/projectintel/SyntheseActivite.java:46 (1-2 fils, file 8) ; argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java:37 (TAILLE_MAX_REPONSE 1 Mio), :60 (connectTimeout ≤ 5 s), :82 (timeout argos.ia.delai=PT60S) ; compose.yaml : OLLAMA_MAX_LOADED_MODELS=1, OLLAMA_NUM_PARALLEL=1, mem_limit 3g ; quotas argos.ia.plafond-instance-jour / quota-espace-jour.
```

**Impact** — L'indisponibilité ou la lenteur du modèle sur le CPU du DS1522+ n'épuise ni les fils ni la mémoire de l'application. Les demandes au-delà de la file passent en « indisponible ».

**Action** — Aucune ; à surveiller : la somme des fils IA (MoteurAlertes 2 + SyntheseActivite 2 + autres) dépasse OLLAMA_NUM_PARALLEL=1, donc les demandes s'empilent côté Ollama jusqu'à leur délai.

### AUD-PER-16 — Clients HTTP sortants : délais de connexion et de requête configurés partout, sans redirection ; caches KEV et CERT-FR avec durée de vie

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — La protection tient : les 7 clients HttpClient déclarent un délai de connexion et refusent les redirections (sauf la passerelle IA, locale), et les caches KEV et CERT-FR gardent leur durée de vie.

*Changement depuis le 17/11* : Nouveau client HTTP ArchivesJorf : connectTimeout, Redirect.NEVER, taille plafonnée. Les autres clients sont inchangés.

```text
Commande « grep -rn 'HttpClient.newBuilder' argos-server/src/main/java » -> 7 clients ; ingestion/legi/ArchivesJorf.java:79 (connectTimeout(delai), Redirect.NEVER) et :148-157 (timeout de requête, ofInputStream, readNBytes(max + 1)) ; vulnintel/RafraichissementIndex.java:29-33 (invalidation à l'échéance horaire, sans appel réseau).
```

*Réserve* : Pour ArchivesJorf, comme pour tout corps lu par ofInputStream, HttpRequest.timeout ne borne que l'arrivée des en-têtes : la lecture d'un corps lent (jusqu'à 50 Mio) n'a pas d'échéance globale, contrairement à RecuperateurWeb (minuterie). Le limiteur de débit reste propre à l'instance.

**Preuve d'origine**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java (connectTimeout + timeout = argos.sources.http-timeout PT30S, Redirect.NEVER) ; vulnintel/EnrichmentHttp.java:36 ; scm/ForgeHttp.java:36,46 (+ taille plafonnée, gestion de quota 429/403) ; releaseradar/ClientEndoflife.java:72,82 (PT10S) ; ingestion/web/RecuperateurWeb.java:51,140 (échéance globale imposée par une minuterie, en plus du timeout) ; vulnintel/KevSource.java:64-73 et CertFrSource.java:63-90 (cache synchronisé, argos.sources.certfr.cache-ttl PT6H) ; LimiteurDebit en mémoire avec nettoyage (api/security/LimiteurDebit.java:28-76).
```

**Impact** — Aucun appel distant ne peut bloquer indéfiniment un fil. Les catalogues volumineux (KEV) ne sont pas retéléchargés à chaque signal.

**Action** — Aucune ; noter que le limiteur de débit en mémoire ne vaut que pour une seule instance (à partager, en base ou dans Redis, en cas de mise à l'échelle horizontale).

### AUD-PER-17 — Bundle de l'IHM encore léger : 201,6 kB, dont 68,4 kB gzippé (41,9 kB le 17/11)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Bundle toujours léger (201,61 kB, 68,41 kB gzippé, sous le seuil de 300 kB) ; en revanche la seconde moitié de la protection, l'import par lot, a régressé (nouveau constat AUD-PER-20).

*Changement depuis le 17/11* : Le bundle a grossi (lots IHM : mises en page, OIDC, inventaire, traitement des impacts ; 12 familles @fontsource ajoutées). L'import par lot n'est plus vrai : le volet B (commit 77181e9) remplace l'INSERT component par lot par une requête par composant.

```text
Commande : cp -r argos-ui /home/claude/audit-maj/build-perf/ && npm ci --ignore-scripts && npx vite build -> « dist/assets/index-Pw7Lubh4.js 201.61 kB │ gzip: 68.41 kB », « dist/assets/index-BWIRo49O.css 31.37 kB │ gzip: 6.18 kB », 46 fichiers de police (woff/woff2) dans dist/assets, dist de 1,6 Mo ; à l'audit initial : 111,25 kB / 41,87 kB ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:259 (composant.executeQuery() par composant) contre addBatch/executeBatch à 9d96406 (git show 9d96406:…/InventoryStore.java:68-70).
```

*Réserve* : Le bundle a crû de 81 % en trois jours : surveiller le seuil des 300 kB. Les polices ne sont téléchargées que si une mise en page les utilise. Une seule protection tient encore.

**Preuve d'origine**

```text
Commande : cp -r argos-ui /home/claude/audit-tmp/build-perf/ && npm ci && npx vite build -> « dist/assets/index-DXBFwvE8.js 111.25 kB │ gzip: 41.87 kB », « dist/assets/index-Ds4gtZKj.css 5.18 kB » (2 dépendances d'exécution : vue, vue-router) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java (INSERT component par addBatch/executeBatch) ; argos.inventory.max-document-bytes=20971520.
```

**Impact** — Le chargement initial reste négligeable, y compris sur le réseau domestique ou en ligne ; l'archive Electron est dominée par le runtime Electron, pas par l'IHM.

**Action** — Aucune ; découper par route (import() dynamique) seulement si le bundle dépasse ~300 kB.

### AUD-PER-23 — L'empreinte du contenu normalisé évite les versions et les événements superflus : une resynchronisation horaire d'un avis inchangé ne relance ni l'enrichissement, ni la corrélation, ni les alertes

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
argos-server/src/main/java/io/argos/signal/SignalRecorder.java:101-110 (comparaison de normalized_hash ; inchangé -> Outcome.UNCHANGED, sans changes.fire) et :144 (SignalChanged seulement après modification) ; argos-server/src/main/resources/db/migration/V18__automatisation_collecte_et_abonnements.sql:9-15 (colonnes normalized_hash, index unique signal_version_normalized_uk) ; ingestion/SourceSynchronizer.java:135 (UNCHANGED et IGNORED_OLDER comptés sans traitement) ; test cité par openspec : chain/CollecteTest#col10ContenuRevenuAUneVersionAnterieure (non exécuté ici).
```

**Impact** — La synchronisation horaire ne réécrit pas les signaux et ne remet pas en route la chaîne aval pour les avis qui n'ont pas changé. Pour les signaux antérieurs à V18, l'empreinte est calculée une fois, au premier passage, en renormalisant le contenu brut de la version courante (SignalRecorder.java:104 et :224), sans nouvelle version.

**Action** — Aucune ; garder en tête l'effet de bord sur AUD-PER-02 : un enrichissement manqué ne sera plus jamais retenté par une resynchronisation, d'où l'urgence de planifier retryMissing.

### AUD-PER-24 — La collecte JORF est bornée : archive de 50 Mio au plus, décompression en flux plafonnée à 500 Mio et 50 000 entrées, fichier XML de 5 Mio au plus, débit limité à 10 requêtes par minute

- **Statut** : Info · **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/legi/ArchivesJorf.java:78 (new ArchiveTar(entreesMax, 5 Mio, decompresseMax)) et :155-161 (readNBytes(max + 1), refus au-delà) ; argos-server/src/main/java/io/argos/ingestion/legi/ArchiveTar.java:41-80 (GZIPInputStream lu bloc par bloc, total et nombre d'entrées contrôlés, fichiers non XML sautés) ; application.properties : argos.reglementaire.jorf.taille-max-octets=52428800, decompresse-max-octets=524288000, entrees-max=50000, argos.reglementaire.debit.requetes-par-minute=10.
```

**Impact** — Une archive JORF anormale (bombe de décompression, archive géante) est refusée sans épuiser le tas d'environ 768 Mio. Réserve : l'archive compressée est conservée entière en mémoire (jusqu'à 50 Mio, plus la copie interne de readNBytes) pendant sa lecture, et la lecture du corps n'a pas d'échéance globale (AUD-PER-16).

**Action** — Aucune ; lire éventuellement l'archive directement en flux (ofInputStream -> GZIPInputStream) pour éviter le tampon de 50 Mio.

### AUD-DEP-11 — npm audit argos-desktop : 6 vulnérabilités modérées (minimatch GHSA-g3hv-75wg-m249, déni de service) via electron-builder, outil de build uniquement

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build).

*Changement depuis le 17/11* : Aucun : mêmes versions (electron-builder 26.18.0, dernière publiée).

```text
Copie /home/claude/audit-maj/build-deps/argos-desktop, `npm audit --json` -> {moderate:6, high:0, critical:0} ; minimatch 7.0.0-11.0.0 GHSA-g3hv-75wg-m249 via @electron/universal, app-builder-lib, dmg-builder, electron-builder-squirrel-windows ; fixAvailable = electron-builder 24.13.3 (rétrogradation majeure) ; `npm view electron-builder version` -> 26.18.0.
```

*Réserve* : L'acceptation explicite du risque recommandée n'est toujours pas écrite (aucun document ne la mentionne).

**Preuve d'origine**

```text
Copie dans /home/claude/audit-tmp/build-deps/argos-desktop, `npm audit --json` -> {moderate: 6, high: 0, critical: 0} ; chaîne : electron-builder 26.18.0 -> app-builder-lib -> @electron/universal -> minimatch 9.0.9 (plage vulnérable >=7.0.0 <11.0.1) ; fixAvailable proposé = electron-builder 24.13.3 (rétrogradation majeure, non pertinente).
```

**Impact** — Aucun impact en exécution : electron-builder ne sert qu'à l'empaquetage local sur un poste de confiance ; la DoS suppose des motifs glob malveillants.

**Action** — Ne pas appliquer `npm audit fix --force` ; suivre la prochaine version d'electron-builder et accepter le risque explicitement (documenté) d'ici là.

### AUD-DEP-12 — argos-ui : aucune vulnérabilité connue et dépendances à jour (Vue 3.5.45, Vite 8.3.8, vue-router 5.5.1), lockfiles v3 présents et utilisés par npm ci

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : 0 vulnérabilité, toutes les dépendances directes à la dernière version sauf TypeScript (tilde délibéré).

*Changement depuis le 17/11* : Aucun changement de versions npm de l'IHM ; ajout de argos-ui/NOTICE.

```text
Copie /home/claude/audit-maj/build-deps/argos-ui, `npm audit --json` -> total 0 ; comparaison lockfile/`npm view` (script /tmp/outd.js) : vue 3.5.45, vue-router 5.5.1, vite 8.3.8, @vitejs/plugin-vue 6.0.11, vue-tsc 3.3.14, 12 @fontsource/* = dernières ; typescript ~6.0.3 contre 7.0.2 ; lockfileVersion 3 ; Dockerfile:10-11 `npm ci`. `npm ci` + `vite build` rejoués dans build-deps/ui-build : rc=0.
```

**Preuve d'origine**

```text
Copie /home/claude/audit-tmp/build-deps/argos-ui, `npm audit --json` -> total 0 ; `npm view <pkg> version` : vite 8.3.8, vue 3.5.45, @vitejs/plugin-vue 6.0.11, vue-tsc 3.3.14 = versions résolues dans package-lock.json (lockfileVersion 3) ; Dockerfile:7-8 `COPY ... package-lock.json` + `npm ci`. Seul écart : typescript ~6.0.3 alors que 7.0.2 est publié (choix délibéré par le tilde).
```

**Impact** — Chaîne front reproductible et saine.

**Action** — Aucune ; planifier la montée TypeScript 7 quand vue-tsc la prend en charge.

### AUD-DEP-13 — Quarkus 3.40.1.2 sur le flux LTS courant (3.40) ; Java 17 compatible jusqu'à la fin des 3.x

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : Quarkus 3.40.1.2 sur la LTS 3.40, Java 17 compatible.

*Changement depuis le 17/11* : Aucun.

```text
argos-server/pom.xml:14-21 ; Context7 /quarkusio/quarkusio.github.io _data/versions.yaml : version 3.40.1.1, flux maintenus 3.40, 3.33, 3.27, 3.20, 3.15, 3.8, jdk « 17+ ».
```

**Preuve d'origine**

```text
argos-server/pom.xml:14-21 `<quarkus.platform.version>3.40.1.2</quarkus.platform.version>` (commentaire : LTS jusqu'au 30/09/2027). Context7 /quarkusio/quarkusio.github.io, _data/versions.yaml : version courante 3.40.1.1, flux maintenus « 3.40, 3.33, 3.27, ... », jdk « 17+ » ; politique LTS : support 12 mois, nouvelle LTS tous les 6 mois.
```

**Impact** — Plateforme serveur à jour ; la sortie de Quarkus 4 imposera Java 21 (Dockerfile:16 et :25 à faire évoluer).

**Action** — Aucune ; anticiper le passage à Java 21 (images temurin-21) avant la fin de support de la LTS 3.40.

### AUD-DEP-16 — Conteneurisation et assemblage Compose solides : multi-étapes, JRE seul, utilisateur non root, mem_limit, restart, healthcheck PostgreSQL, ports locaux par défaut

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient et s'améliore : multi-étapes, JRE seul, UID 1001, mem_limit, healthcheck PostgreSQL, ports locaux.

*Changement depuis le 17/11* : Dockerfile réécrit (fca5213, f49a566, 09ee4d7, f2b37c1) : étage de contrôle des licences, avis en lecture seule propriété de root ; .dockerignore passé en liste d'autorisation.

```text
Dockerfile:8/18/45 trois étapes, :47 `useradd --system --uid 1001`, :51 `USER 1001`, :49-50 avis 0444 ; .dockerignore:1-9 (`*` puis exceptions, **/.env exclu) ; compose.yaml:16/37/68 restart, :27/59/76 mem_limit, :28-33 healthcheck, :38-40 service_healthy, :22-23/:55-58 127.0.0.1 ; `docker compose -f compose.yaml config -q` et `-f e2e/compose.yaml config -q` -> rc=0.
```

**Preuve d'origine**

```text
Dockerfile:6/16/25 trois étapes, :27-29 `useradd --system --uid 1001` + `USER 1001` ; .dockerignore exclut **/.env ; compose.yaml:16/37/68 `restart: unless-stopped`, :27/59/76 mem_limit, :28-33 healthcheck pg_isready + :38-40 depends_on service_healthy, :22-23 et :55-58 publication sur 127.0.0.1 ; ollama sans port publié et profil opt-in. `docker compose -f compose.yaml config -q` -> rc=0.
```

**Impact** — Base d'exploitation adaptée au NAS (mémoire bornée, surface réseau minimale).

**Action** — Aucune.

### AUD-DEP-17 — Configuration par profils correcte : prod sans valeurs par défaut pour la base et l'OIDC, mailer simulé seulement en dev/test, STARTTLS exigé, aucun secret réel versionné

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé, aucun secret réel versionné.

*Changement depuis le 17/11* : Numéros de ligne décalés ; règles inchangées.

```text
application.properties:10-12 `%prod.quarkus.datasource.*=${ARGOS_DB_*}`, :277 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`, :87-90 mock false en prod / true en dev et test, :82 start-tls REQUIRED, :283/:286 secret client limité à %test/%dev (valeur de test) ; `git grep` des motifs ghp_/glpat-/AKIA/clé privée/JWT : seuls des jeux d'essai dans FiltreDonneesTest.java:20-24 ; aucun JWT versionné dans e2e/resultats ; .gitignore exclut .env et sauvegardes/.
```

**Preuve d'origine**

```text
application.properties:10-12 `%prod.quarkus.datasource.*=${ARGOS_DB_*}` sans défaut, :203 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`, :80-83 mock=false en prod, :75 `start-tls=${ARGOS_SMTP_STARTTLS:REQUIRED}`, :209/:212 secret client limité à %test/%dev (valeur de test, non reproduite) ; recherche de motifs de secrets (ghp_, glpat-, AKIA, clés privées) hors tests : seules les expressions de filtrage de FiltreDonnees.java:32-33 ; .gitignore exclut .env et sauvegardes/.
```

**Impact** — Démarrage en échec franc si la base ou l'OIDC manque ; pas de fuite de secret dans le dépôt. Aucun pipeline, donc aucun secret de pipeline à gérer.

**Action** — Aucune.

### AUD-DEP-18 — Licences des dépendances livrées compatibles avec ADR-0021 (MIT/Apache-2.0/BSD) ; licences restrictives uniquement dans l'outillage de build

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**

**Vérification du 20/11 (54add78)** — Tient : aucune dépendance copyleft forte ni non commerciale livrée ; le seul copyleft faible (EPL-2.0) est nommé et décidé.

*Changement depuis le 17/11* : Ajout d'une liste blanche de licences appliquée au build de l'image ; EPL-2.0 admise par décision G3 pour neuf API Jakarta nommées.

```text
scripts/verifier-licences-serveur.mjs:66-112 (ADMISES, SOUS_RESERVE limité aux composants nommés) ; argos-server/NOTICE ; ADR docs/architecture/adr/0021-double-licence.md ; répartition des licences des lockfiles : argos-ui MIT 107, MPL-2.0 12 (lightningcss, outillage), OFL-1.1 12 (polices, décision du 18/11), Apache-2.0 2, BSD/ISC/0BSD ; argos-desktop tout en dev (MIT 213, ISC 35, BlueOak 9, BSD 14, Python-2.0 1, WTFPL 1) ; e2e Apache-2.0 3, MIT 1.
```

**Preuve d'origine**

```text
LICENSE : PolyForm Internal Use 1.0.0 ; ADR docs/architecture/adr/0021-double-licence.md:57 (permissives acceptées). Analyse du package-lock.json : bundle argos-ui = vue, vue-router (MIT) ; MPL-2.0 (lightningcss 1.33.0), BSD-3, ISC, 0BSD côté Vite ; argos-desktop : MIT 213, ISC 35, BlueOak-1.0.0 9, Python-2.0 1 (argparse), WTFPL 1, tous dans la chaîne electron-builder ; pom.xml:44-150 commente la licence de chaque dépendance (Apache-2.0, MIT, JDOM).
```

**Impact** — Aucune dépendance copyleft forte ni non commerciale n'entre dans le produit distribué.

**Action** — Aucune.

### AUD-DEP-23 — Le script de contrôle des licences rejoué sur des inventaires factices donne les bons codes de sortie (1 GPL ou licence absente, 2 BOM manquant, 0 conforme)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
Copie `git archive HEAD` dans /home/claude/audit-maj/build-deps/repo : `node scripts/verifier-docs.mjs` -> rc=0, « Fichiers Markdown contrôlés : 120 », « Blocs Mermaid : 73 », « Résultat : aucune erreur » ; verifier-licences-serveur.mjs sur inventaires factices (/tmp/lic) : GPL-3.0-only et licence absente -> rc=1 avec liste des écarts, BOM absent -> rc=2, Apache-2.0 + MIT avec archives -> rc=0 « Aucune licence absente ni non admise ». Le vrai bom.json n'a pas pu être produit (Maven Central bloqué).
```

**Impact** — Les liens et ancres de la documentation sont cohérents à 54add78 ; le garde-fou de licences se comporte comme documenté.

**Action** — Aucune (voir AUD-DEP-22 pour l'automatisation).

## Documentation — Moyenne (4)

### AUD-DOC-01 — Les points d'entrée (README racine, docs/architecture/README.md, openspec/config.yaml) décrivent encore le cadrage de septembre : socle « sans fonctionnalité métier », Claude derrière un AI Gateway, questions ouvertes déjà tranchées par des ADR acceptés

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11
- **Dépend de** : AUD-ARC-14

**Preuve**

```text
README.md:13 (« socle applicatif sans fonctionnalité métier »), README.md:90-91 et 106-107 (MKP, Claude/AI Gateway, Claude API facturée), README.md:133 (« outil de migration à confirmer par ADR ») alors qu'ADR-0032 est Accepté, README.md:136 et 138 (connecter OSV, prototyper le MCP : à faire) alors que argos-server/src/main/java/io/argos/ingestion/osv/ et io/argos/mcp/ (8 @Tool) existent, README.md:144 (licence « interne à une entreprise ») sans le renvoi d'ADR-0025:50 qui la juge inadaptée à un produit ouvert ; docs/architecture/README.md:10 (« capacités du lot 1 » seulement), :20-21 et :25 (Claude/AI Gateway, FreshRSS séparés, validation MKP) contredits par ADR-0029, ADR-0028 et ADR-0030 acceptés et par argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java, :41-42 (budget Claude API), :51-56 (4 ADR acceptés cités sur 15), :84 (« Priorités avant le premier commit applicatif »), :99-102 (IdP, Claude, FreshRSS, Flyway : tranchés par ADR-0027, 0029, 0028, 0032) ; openspec/config.yaml:6-7 (« ARGOS est en phase de conception… socle applicatif sans fonctionnalité métier »), contexte injecté dans chaque artefact OpenSpec ; README.md:13 et docs/architecture/README.md:5 déclarent « active » la baseline v2.4, dont baseline-v2.4.md:4 dit « proposition mise à jour à valider » et :14 prévoit la VM interne abandonnée (ADR-0026).
```

**Impact** — Un lecteur (ou un agent OpenSpec) qui part des points d'entrée se fait une image fausse du produit : pas d'IA locale, pas de collecte intégrée, rien de livré. openspec/config.yaml oriente chaque nouvel artefact vers « rien n'est existant ».

**Action** — Réécrire Statut, Mission, IA, Prochaines étapes et Licence du README racine ; dans docs/architecture/README.md, remplacer la synthèse, la liste des ADR acceptés et les questions ouvertes par des renvois à arc42 §9 ; mettre à jour le contexte d'openspec/config.yaml ; marquer baseline-v2.4 comme historique (datée) au lieu d'« active ».

### AUD-DOC-02 — arc42 (§1, §2, §3 D-01, §5.2, §7, §10, §11) et les deux registres de risques divergents ne suivent pas les ADR 0025 à 0033 ; §3 et §7 citent encore Claude API et l'IdP d'entreprise

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Documentation, Architecture · **Effort** : M · **Sprint** : 10
- **Nouveau** le 20/11
- **Fusionne** : AUD-ARC-03
- **Dépend de** : AUD-ARC-05, AUD-ARC-06

**Preuve**

```text
docs/architecture/arc42/01-introduction-objectifs.md:11 et :15 (« synthèse assistée par Claude », « seules les capacités du lot 1 sont implémentées ») ; 02-contraintes.md:24-29 (migrations, Claude, FreshRSS, changedetection : « Hypothèse à valider ») contre ADR-0032, 0029, 0028 acceptés ; 03-contexte-perimetre.md:23-30, :47-52 et diagramme D-01 :68-89 (ARGOS → Claude en HTTPS, FreshRSS, changedetection), dernier commit bfb8d3a du 2026-09-24 ; 07-vue-deploiement.md:24, :27 et :53 (« Claude API », conteneur FreshRSS) alors que diagrams/d-11-deploiement-cible.md:20 et :27 montrent Ollama et Keycloak, et compose.yaml:66 le service ollama ; 10-exigences-qualite.md:83 (« Q-06 — Maîtrise des coûts Claude ») ; 11-risques-dette.md:7-16 (R-01 à R-10, dernier commit 4451e06 du 2026-09-24) et risks/register.md:7-24 (R-01 à R-18, libellés différents, tous « Ouvert », dernier commit 5009650 du 2026-09-24) : R-01, R-05, R-08 portent sur Claude ; R-07 (licences), R-11 (injection), R-12 (SSRF) ont des mitigations livrées (scripts/verifier-licences-serveur.mjs, marquage CONTENU_EXTERNE_NON_FIABLE, garde SSRF de io/argos/ingestion/web) jamais reportées. Les §5.1/§5.2 relèvent d'AUD-ARC-03 et ne sont pas repris ici.
[AUD-ARC-03] docs/architecture/arc42/05-vue-blocs.md:85 « INGEST --> SIGNAL » alors que signal importe ingestion 18 fois (signal/web/CollecteWeb.java:18-21) ; :86 « SIGNAL --> VULN » vs vulnintel/Enricher.java:27 import io.argos.signal.SignalChanged ; :92 « IMPACT --> ALERT » et :104 « WORK --> ALERT » vs alerting->impact (5) et alerting->workspace (7) ; :98 « SCM --> INVENTORY » vs inventory->scm (4). Absentes du diagramme : api->catalog (13), api->impact (8), api->alerting, mcp->10 modules dont api, catalog->workspace, impact->catalog, signal->catalog, releaseradar->catalog, inventory->catalog. :19-20 « Claude / Anthropic », « IdP entreprise » alors que le code appelle Ollama (aigateway/ollama/FournisseurOllama.java) et Keycloak (ADR-0027) ; idem 03-contexte-perimetre.md:52-53,73-74.
```

**Impact** — Les vues de contexte, de contraintes, de déploiement et de risques — celles qu'un relecteur sécurité ou un nouvel arrivant lit en premier — décrivent un autre système ; le registre des risques ne sert plus d'outil de pilotage et deux registres portent les mêmes identifiants avec des libellés différents.

**Action** — Aligner §1, §2, §3 (D-01), §7.2/§7.3 (D-10) et §10 Q-06 sur ADR-0026 à ADR-0033 ; fusionner §11.1 et risks/register.md en un seul registre (arc42 §11 renvoyant au registre), réviser chaque risque (statut, preuve de mitigation, risques clos gardés avec leur preuve) et ajouter les risques constatés (performance /me/veille, IdP auto-hébergé).

### AUD-DOC-03 — Le guide de déploiement mène à une installation qui ne démarre pas : ARGOS_OIDC_URL et ARGOS_ADMIN_EMAIL, obligatoires, absents des étapes et du tableau des réglages, aucune procédure Keycloak, et le guide affirme que l'authentification « n'est pas en place »

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Documentation, Dépendances, Sécurité · **Effort** : M · **Sprint** : 10
- **Nouveau** le 20/11
- **Fusionne** : AUD-DEP-09, AUD-SEC-08
- **Dépend de** : AUD-DEP-01, AUD-DEP-21, AUD-ARC-02

**Preuve**

```text
docs/deploiement/synology-et-pc.md:21 et :37 (copier .env.example, « changez au minimum ARGOS_DB_PASSWORD »), :88-97 (tableau « Réglages » : 6 lignes, ni ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL), :59 (« Tant que l'authentification (OIDC, ADR-0008) n'est pas en place ») ; or .env.example:15-20 (« Fournisseur d'identité OIDC … — obligatoire », ARGOS_OIDC_URL vide en :17, ARGOS_ADMIN_EMAIL vide en :20) et argos-server/src/main/resources/application.properties:277 (%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}, sans défaut) ; diagrams/d-11-deploiement-cible.md:58 reconnaît que le déploiement de Keycloak n'est décrit nulle part ; même parcours dans README.md:74-77. Sur les 37 variables ARGOS_* lues par application.properties, une seule (ARGOS_DB_PASSWORD) figure dans le guide (comptage : doc-scripts, commande de la section B). Complète AUD-DEP-01 (SMTP, ARGOS_PUBLIC_URL, jetons de forge, quotas), non vérifié ici.
```

**Impact** — Une installation conforme au guide échoue au démarrage en profil prod (émetteur OIDC vide) ; l'utilisateur n'apprend ni qu'il doit fournir un royaume Keycloak, ni comment le créer (clients argos-ui, argos-server, argos-mcp), ni qui est administrateur au premier démarrage. La phrase de la ligne 59 laisse croire à tort qu'Argos est sans authentification.

**Action** — Ajouter au guide une étape « Fournisseur d'identité » (royaume, clients, audience, en s'appuyant sur argos-server/src/test/resources/keycloak/argos-realm.json), compléter le tableau des réglages à partir de .env.example et de compose.yaml, supprimer la ligne 59, et générer (ou contrôler par script) la liste des variables documentées contre application.properties. Correctif immédiat possible, en quelques lignes et sans attendre le sprint 10 : supprimer la phrase de synology-et-pc.md:59 (« tant que l'authentification n'est pas en place ») et ajouter ARGOS_OIDC_URL et ARGOS_ADMIN_EMAIL au tableau des réglages. Ajouter aussi la déclaration des redirections du client argos-ui pour l'adresse du portail déployé (le royaume de test n'autorise que http://localhost:5173/*).

### AUD-DOC-04 — Client desktop : la procédure « viser le NAS » ne règle que VITE_ARGOS_API_URL, alors que l'application installée limite sa CSP à localhost:8080 et son émetteur OIDC à localhost:8180 sans ARGOS_API_URL et ARGOS_OIDC_URL, variables documentées nulle part pour l'utilisateur

- **Statut** : Ouvert · **Sévérité** : Moyenne · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11
- **Dépend de** : AUD-DEP-21

**Preuve**

```text
docs/deploiement/synology-et-pc.md:144 (« construisez l'IHM avec … VITE_ARGOS_API_URL=http://adresse-du-nas:8080, puis npm run dist ») ; argos-desktop/src/main.js:27 (API_URL = process.env.ARGOS_API_URL || 'http://localhost:8080', « sert uniquement à la politique de sécurité du contenu »), :31 (OIDC_URL = process.env.ARGOS_OIDC_URL || 'http://localhost:8180/realms/argos', « les deux doivent coïncider » avec VITE_ARGOS_OIDC_ISSUER), :70 (contentSecurityPolicy(API_URL, OIDC_URL)) ; argos-ui/.env.example:5 (VITE_ARGOS_OIDC_ISSUER) absent du guide ; seules mentions de ARGOS_API_URL/ARGOS_OIDC_URL : e2e/README.md:91 (lancement de test) ; aucun README dans argos-desktop/ ni argos-ui/.
```

**Impact** — Un client desktop construit selon le guide ne peut pas joindre le serveur du NAS (requêtes bloquées par sa CSP) ni s'authentifier auprès du Keycloak réel ; le diagnostic n'est pas documenté.

**Action** — Documenter dans le guide les quatre réglages du desktop (VITE_ARGOS_API_URL, VITE_ARGOS_OIDC_ISSUER au build ; ARGOS_API_URL, ARGOS_OIDC_URL à l'exécution) et leur obligation de cohérence, ou faire dériver les valeurs d'exécution de celles du build ; ajouter un README court à argos-desktop/.

## Documentation — Faible (6)

### AUD-DOC-05 — README racine, démarrage local : Node.js 22.12 annoncé contre ^22.18.0 exigé, Docker présenté comme requis pour les seuls tests alors que quarkus:dev démarre Keycloak, aucune mention de la connexion (royaume de test, comptes), e2e réduit au « portail web »

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11

**Preuve**

```text
README.md:42 (« Node.js 22.12 ou plus ») contre argos-ui/package.json:8 et e2e/package.json:9 ("node": "^22.18.0 || >=23.6.0") et e2e/README.md:7 (« Node.js 22.18 ou plus ») ; README.md:50 (« tests : mvn verify (Docker requis : base jetable) ») alors que application.properties:284-289 démarre en %dev un Keycloak des Dev Services (port 8180, royaume src/test/resources/keycloak/argos-realm.json) ; aucune mention dans README.md:40-68 de la page de connexion, du compte alice (application.properties:305) ni de VITE_ARGOS_OIDC_ISSUER (argos-ui/.env.example:5) ; README.md:35 et :68 (« Tests de bout en bout du portail web ») alors que e2e/playwright.config.ts:65-77 définit aussi les projets desktop et mcp, et e2e/package.json les campagnes test:accessibilite et test:performance.
```

**Impact** — Un développeur sur Node 22.12 à 22.17 voit npm refuser l'installation ; sans Docker, quarkus:dev échoue sans que le README l'annonce ; l'IHM démarrée affiche un écran de connexion dont les identifiants ne sont documentés que dans e2e/README.md:33.

**Action** — Corriger la version de Node, signaler Docker pour quarkus:dev, ajouter un paragraphe « Se connecter en local » (royaume de test, comptes, mot de passe = identifiant) et décrire e2e comme « portail web, desktop, MCP, campagnes accessibilité et performance ».

### AUD-DOC-06 — Le diagramme D-19 (arc42 §6.6, veille réglementaire) ne se rend pas : un « ; » dans un message de séquence casse l'analyse Mermaid, et le contrôle documentaire passe parce que le rendu est facultatif

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11

**Preuve**

```text
docs/architecture/arc42/06-vue-execution.md:290 (« Reg->>Persist: jour EN_ECHEC, source DEGRADE ; repris … ») ; `node scripts/verifier-docs.mjs --rendu` dans une copie à 54add78 (mmdc 11.16.0) : « ERREUR docs/architecture/arc42/06-vue-execution.md:273 rendu Mermaid en échec : Error: Parse error on line 17 » puis « Résultat : 1 erreur(s) » ; le bloc extrait se rend après remplacement du « ; » par une virgule ; sans l'option : « Résultat : aucune erreur » ; ADR-0020:66 et openspec/config.yaml:36 ne prescrivent que l'exécution sans --rendu. Introduit par e73ec4d (2026-11-18).
```

**Impact** — Le seul diagramme d'exécution de la veille réglementaire s'affiche en erreur sur GitHub et dans tout rendu Mermaid, alors qu'ADR-0005 fait de Mermaid la source de vérité des diagrammes.

**Action** — Remplacer le « ; » de la ligne 290 ; faire de --rendu le mode prescrit par ADR-0020 et par les tâches type du schéma lorsque mmdc est disponible (ou ajouter au script la détection des « ; » dans les messages de sequenceDiagram).

### AUD-DOC-07 — Documents de suivi incohérents entre eux : statuts de tâches et de décisions, inventaire des diagrammes (D-01 à D-13 contre 19), migrations de D-13 et statuts de capacités ne concordent pas

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11

**Preuve**

```text
docs/specifications/README.md:199 (« Tâche 3.1 décochée : la contrainte INV-C3 n'est pas créée ([PROPOSITION] P-13, à valider) », « [PROPOSITION] P-18 ») contre openspec/changes/automatiser-chaine-veille/tasks.md:61 (« - [x] 3.1 ») et proposal.md:161 (« P-13 ([DÉCISION] K1) ») et :166 (« P-18 ([DÉCISION] K2) ») ; docs/specifications/README.md:92 (« D-01 à D-13 ») contre docs/architecture/diagrams/README.md:24-42 (D-01 à D-19) ; diagrams/README.md:36 (D-13 « V1 à V17 et V20 ») contre d-13-modele-physique.md:3 (« V1__socle.sql à V20 ») et :136, :189 (colonnes V18, V19) ; docs/specifications/profils-veille.md:8 et :88 (« pas de capacité regintel ») contre veille-reglementaire.md:8 (« livrée ») et argos-server/src/main/java/io/argos/alerting/RappelsReglementaires.java:41 (PROFIL = "expert-metier") ; docs/specifications/README.md:38-41 et :48 (COL, INV, COR, ABO, MCP « proposée ») contre :47 (NOT « implémentée, non archivée ») alors que les fiches (collecte-avis-securite.md:8, serveur-mcp.md:8…) disent le code implémenté et testé, et que leur ligne 6 dit « capacité non livrée ».
```

**Impact** — Le registre de couverture, qui fait foi pour l'avancement, donne pour certaines exigences un état contraire à celui du changement OpenSpec ; un lecteur ne sait pas si INV-C3 est décidé ni si la MCP est livrée.

**Action** — Corriger les cinq passages ; ajouter au vocabulaire du modèle de fiche (_modele-fiche-capacite.md:17) un statut « implémentée, non archivée » et l'appliquer aux cinq capacités concernées ; faire vérifier par verifier-docs que le statut d'une tâche citée dans le registre correspond à sa case dans tasks.md.

### AUD-DOC-08 — Neuf ADR acceptés (0023, 0024, 0027 à 0033) gardent une décision rédigée comme une proposition, et ADR-0029 se dit encore subordonné à sa propre acceptation

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11
- **Dépend de** : AUD-ARC-14

**Preuve**

```text
docs/architecture/adr/0027-keycloak-idp-auto-heberge.md:44, 0028:43, 0029:43, 0030:43, 0033:55 (« **Proposition : option B** »), 0031:66 et 0032:53 (« Proposition : **option A** »), 0023:56 et 0024:63 (« Option A, **proposée** ») avec « Statut : Accepté » en ligne 3 ; 0029-passerelle-ia-ollama-local.md:11 (« ADR-0004 (Proposé) »), :45 et :81 (« ADR-0004 reste la référence tant que le présent ADR n'est pas accepté ») alors qu'ADR-0004 est « Remplacé par ADR-0029 » ; relevé par doc-scripts/statuts-adr.mjs. Distinct d'AUD-ARC-14 (ADR restés « Proposé »).
```

**Impact** — Le corps de l'ADR, seul lu par qui ouvre le fichier, contredit son en-tête ; ADR-0029 laisse croire qu'ADR-0004 fait encore référence.

**Action** — À l'acceptation, remplacer « Proposition : » par « Décision : » et retirer les clauses conditionnelles devenues caduques, en notant la date d'acceptation (modification d'acceptation, pas réécriture rétroactive au sens de arc42 §9.2).

### AUD-DOC-10 — Le contrôle documentaire laisse passer des chemins périmés : références en code en ligne vers trois changements archivés (ADR-0027, ADR-0028), README d'argos-server et d'e2e hors périmètre, aucune exécution automatique

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11
- **Dépend de** : AUD-TST-01

**Preuve**

```text
docs/architecture/adr/0027-keycloak-idp-auto-heberge.md:74 (`openspec/changes/gerer-comptes-et-acces/specs/comptes-acces/spec.md`), 0028-collecteur-veille-web-integre.md:78 et :80 (`openspec/changes/collecter-veille-rss-web/…`) : dossiers déplacés sous openspec/changes/archive/2026-11-1x-… ; scripts/verifier-docs.mjs:19-24 (DOSSIERS = docs, openspec ; FICHIERS_RACINE = README.md : argos-server/README.md et e2e/README.md, 190 lignes, ne sont pas contrôlés) et :52-57 (le code entre accents graves est retiré avant le contrôle des liens) ; aucun dossier .github/ ni autre CI (voir AUD-TST-01). Contrôle complémentaire doc-scripts/liens.mjs sur les 201 .md : 0 lien cassé ; doc-scripts/chemins.mjs : ces trois chemins sont les seuls chemins de dépôt périmés hors faux positifs.
```

**Impact** — Les renvois des ADR vers les exigences qu'ils tracent sont morts ; la règle « repointer les liens après archivage » (guide-openspec-detailed-uml.md:74) n'est pas outillée pour les chemins écrits en code.

**Action** — Repointer les trois chemins vers openspec/specs/… ; étendre verifier-docs aux README de sous-projets et aux chemins de dépôt écrits en code en ligne ; l'exécuter dans la CI dès qu'elle existe.

### AUD-DOC-11 — Reliquat d'outillage Archgate : un second dossier d'ADR avec l'exemple non rempli GEN-001 et une documentation de règles de lint renvoyant à un fichier absent

- **Statut** : Ouvert · **Sévérité** : Faible · **Axes** : Documentation · **Effort** : S · **Sprint** : 10
- **Nouveau** le 20/11

**Preuve**

```text
.archgate/adrs/GEN-001-example.md:1-12 (« Example Architecture Decision … Replace this with a real architecture decision ») ; .archgate/lint/README.md:15 (« Create .archgate/lint/oxlint.js ») : fichier absent ; .archgate/config.json ; un seul commit, 7e4eb35 (2026-11-15, « initialiser OpenSpec et Archgate ») ; aucune mention d'Archgate dans docs/ ni dans les ADR.
```

**Impact** — Deux emplacements d'ADR coexistent dont l'un est un gabarit anglais vide ; confusion pour un contributeur ou un agent qui chercherait les décisions.

**Action** — Supprimer .archgate/ ou documenter son rôle par rapport à docs/architecture/adr/ (ADR-0020) et y retirer l'exemple.

## Documentation — Info (6)

### AUD-DOC-13 — Liens et ancres sains dans tout le dépôt ; le contrôle documentaire passe à 54add78 sans l'option --rendu (avec : 1 erreur, AUD-DOC-06)

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
`node scripts/verifier-docs.mjs` dans une copie de 54add78 (/home/claude/audit-maj/build-doc) : « Fichiers Markdown contrôlés : 120 », « Blocs Mermaid : 73 (flowchart, classDiagram, sequenceDiagram, erDiagram, stateDiagram-v2) », « Rendu Mermaid : non demandé (option --rendu) », « Résultat : aucune erreur », code de sortie 0 (avec --rendu : 1 erreur, voir AUD-DOC-06) ; /home/claude/audit-maj/doc-scripts/liens.mjs sur les 201 fichiers .md hors node_modules (archives OpenSpec, .claude, .archgate, README de sous-projets compris) : 491 liens relatifs, 0 lien cassé, 0 ancre introuvable.
```

**Impact** — Navigation documentaire fiable ; les identifiants d'exigences cités sont tous définis (contrôle du script).

**Action** — Conserver ; voir AUD-DOC-10 pour les angles non couverts par le script.

### AUD-DOC-14 — Statuts des 33 ADR identiques dans les en-têtes, l'index adr/README.md et arc42 §9.1 ; §9.3 reporte les questions tranchées par ADR-0031 et ADR-0032

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
/home/claude/audit-maj/doc-scripts/statuts-adr.mjs : 33/33 statuts concordants (fichier, docs/architecture/adr/README.md, docs/architecture/arc42/09-decisions.md:11-43) ; 09-decisions.md:57 et :59 (scheduling et Flyway « tranché par ADR-0031/0032 »).
```

**Impact** — La règle 5 de l'index (synchronisation avec arc42 §9) est tenue.

**Action** — Conserver ; corriger la rédaction des décisions acceptées (AUD-DOC-08).

### AUD-DOC-15 — Schéma et tâches planifiées documentés à jour : migrations V1 à V20 listées par arc42 §8.7 et D-13, treize tâches @Scheduled décrites

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
argos-server/src/main/resources/db/migration/ (V1 à V20) = docs/architecture/arc42/08-concepts-transverses.md:161-186 (« état au 2026-11-19 », retour arrière V18-V20 :163) = diagrams/d-13-modele-physique.md:3 ; écart daté d'ADR-0032 (V1-V16) expliqué en inventaire-sbom.md:281 ; 13 @Scheduled dans argos-server/src/main/java : les 11 d'ADR-0031:16-26 (daté du 17/11) plus signal-synchronisation-sources et inventory-import-quotidien, ajoutés en 08-concepts-transverses.md:133-134.
```

**Impact** — Les documents vivants absorbent les ajouts du lot 18 sans réécrire les ADR datés.

**Action** — Conserver.

### AUD-DOC-16 — e2e/README.md et argos-server/README.md exacts par rapport au code

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
e2e/README.md:22-25 = projets de e2e/playwright.config.ts:65-77 (preparation, portail-web, construction-desktop, desktop, mcp) ; versions citées = lockfiles (@playwright/test 1.61.1 dans e2e/package-lock.json, electron 45.3.2 dans argos-desktop/package-lock.json) ; ports 8090, 5173, 8180, 55432, 5174 = playwright.config.ts:98-103, e2e/compose.yaml:16, accessibilite.config.ts:7 ; tous les fichiers de tests, données et support cités existent ; scripts test:accessibilite et test:performance présents (e2e/package.json) ; argos-server/README.md:27-28 = argos-server/pom.xml:31 et :33 (cyclonedx 2.9.3, exec 3.6.4), tableaux ADMISES, SOUS_RESERVE, CHOIX, TEXTES, SANS_TEXTE, TITULAIRES = scripts/verifier-licences-serveur.mjs:66-132, 7 textes de argos-server/licenses/ = tableau :43-51.
```

**Impact** — La documentation développeur des tests et des licences est fiable et reproductible.

**Action** — Conserver ; l'inclure dans le périmètre de verifier-docs (AUD-DOC-10).

### AUD-DOC-17 — Contrats cités cohérents : routes REST, outils MCP, chiffres de performance et inventaire OpenSpec

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
Chaque route `GET|POST|PUT|DELETE|PATCH /api/v1/…` citée dans README, docs/, e2e/, argos-server/ et les changements actifs a tous ses segments dans un @Path du serveur (seul écart : /api/v1/products/{produit}, API externe endoflife.date, 08-concepts-transverses.md:267) ; huit outils de serveur-mcp.md:26 = huit @Tool(name=…) dans io/argos/mcp ; p95 de GET /api/v1/me/veille = 533,8 ms dans quality/mesure-performance-api-mcp.md:32, arc42 §10 (10-exigences-qualite.md:80), quality/scenarios.md:12, docs/specifications/README.md:199, tasks.md:132 et proposal.md:166 ; docs/specifications/README.md:102-104 (11 specs, 4 changements actifs, 11 archivés) = dossiers openspec/specs, openspec/changes, openspec/changes/archive.
```

**Impact** — Les chiffres et noms techniques partagés entre documents sont tenus.

**Action** — Conserver.

### AUD-DOC-18 — Traçabilité OpenSpec transparente : les tâches non cochées des changements actifs portent leur motif et une décision datée

- **Statut** : Info · **Sévérité** : Info · **Axes** : Documentation · **Effort** : S · **Hors sprint**
- **Nouveau** le 20/11

**Preuve**

```text
openspec/changes/exposer-serveur-mcp/tasks.md:32 (5.2, Claude Code réel) ; formaliser-chaine-securite-poc/tasks.md:27-28 (3.2, « [DÉCISION] … capture sur un dépôt pilote réel »), :64-65 (4.7), :90 (7.1) ; notifier-sur-le-poste/tasks.md:12 et :53 (1.2 non cochée tant que les vérifications manuelles sous Windows ne sont pas faites) ; automatiser-chaine-veille/tasks.md : 25/25 cochées avec preuves datées.
```

**Impact** — L'état d'avancement réel se lit dans tasks.md ; seuls les documents de synthèse divergent (AUD-DOC-07).

**Action** — Conserver.
