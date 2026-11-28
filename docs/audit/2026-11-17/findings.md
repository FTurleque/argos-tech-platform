# Constats de l'audit — 2026-11-17

Base auditée : `origin/main` à 9d96406 (fusion de la PR #7, lot 4). 94 constats, triés par sévérité puis par axe. Chaque constat actionnable porte son sprint et ses dépendances ; l'ordre d'exécution est dans [sprints.md](sprints.md).

## Élevée (6)

### AUD-ARC-02 — L'IHM et le client desktop n'envoient aucun jeton alors que l'API exige OIDC sur toutes les routes sauf /system/info

- **Sévérité** : Élevée · **Axes** : Architecture, Sécurité · **Effort** : M · **Sprint** : 5
- **Fusionne** : AUD-SEC-02
- **Dépend de** : AUD-ARC-12, AUD-TST-10
- **Bloque** : AUD-ARC-15

**Preuve**

```text
argos-server/src/main/resources/application.properties:200 « quarkus.oidc.application-type=service », :217-218 « authentifie.paths=/* … policy=authenticated » ; argos-ui/src/api/client.ts:36-39 « fetch(`${apiBaseUrl}${path}`, { headers: { Accept: 'application/json' } … » (aucun en-tête Authorization) ; grep -rniE 'authorization|bearer|oidc|token' argos-ui/src argos-desktop/src -> aucun résultat pertinent. Lacune connue : docs/specifications/notifications-poste.md:123 « le serveur répond 401 ».
[AUD-SEC-02] argos-server/src/main/resources/application.properties:217-218 `quarkus.http.auth.permission.authentifie.paths=/*` / `policy=authenticated` (seuls /api/v1/system/info et /q/health* en permit) ; argos-ui/src/api/client.ts:36,87,173 `fetch(`${apiBaseUrl}${path}`, {...})` sans en-tête Authorization ; `grep -rniE 'authorization|bearer|token|oidc|pkce' argos-ui/src argos-desktop/src` : aucun résultat. Le Dockerfile copie l'IHM dans META-INF/resources, couverte par /* (doc Quarkus : un chemin en /* est un préfixe appliqué à toutes les requêtes HTTP).
[À 9d96406] application.properties:235 (application-type=service), :252-253 (/* authenticated) ; client.ts:36-39 inchangé.
```

**Impact** — En image Docker, même index.html et l'écran de statut reçoivent 401 : l'IHM web est inutilisable ; seul le desktop, qui sert l'IHM depuis app://argos, affiche l'écran de statut, et toutes ses autres vues reçoivent 401. Le parcours de bout en bout IHM ↔ serveur n'a jamais pu être exécuté (openspec/changes/notifier-sur-le-poste/tasks.md:28).

**Action** — Implémenter Authorization Code + PKCE (client public argos-ui, ADR-0027) dans argos-ui (ex. oidc-client-ts) et le protocole app://argos du desktop, injecter le jeton dans un wrapper fetch unique de client.ts, puis lancer un E2E navigateur contre le royaume Keycloak de test.

### AUD-QUA-01 — La création de projet et l'import d'inventaire (SBOM, manifestes) n'ont aucun point d'entrée en production : code atteint seulement par les tests

- **Sévérité** : Élevée · **Axes** : Qualité · **Effort** : M · **Sprint** : 6

**Preuve**

```text
argos-server/src/main/java/io/argos/inventory/InventoryService.java:49 (importSbom), :64 (importManifests), :91 (refresh) ; inventory/ProjectDirectory.java:37 (create). Commande : grep -rn "InventoryService\|projects.create\|importSbom\|\.refresh(" argos-server/src/main -> aucune référence hors du paquet inventory ; références uniquement dans src/test (chain/InventoryTest.java:23, alerting/AlertesTest.java:44). api/InventoryResource.java:22-52 n'expose que deux GET (inventaire, échecs). Aucun @Scheduled, outil MCP ni ressource REST n'appelle InventoryService ou ProjectDirectory.create.
```

**Impact** — En exploitation, aucun projet ni composant ne peut être créé : la corrélation (CorrelationEngine.onInventoryReplaced), le radar de dette de version et les alertes d'impact restent sans données réelles alors que les tests passent. Environ 600 lignes (InventoryService, InventoryStore, CycloneDxParser, ManifestParser, RepositoryFiles) sont du code mort pour la production.

**Action** — Exposer l'import (POST /api/v1/espaces/{id}/projets, POST /projects/{id}/inventory pour SBOM, POST .../refresh) avec contrôle de rôle via Access, ou un déclenchement planifié de refresh() pour les projets ayant un dépôt ; ajouter un test de bout en bout REST qui crée un projet, importe une SBOM et observe un impact. À défaut, documenter explicitement la limite dans le README.

### AUD-QUA-02 — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes

- **Sévérité** : Élevée · **Axes** : Qualité, Dépendances · **Effort** : S · **Sprint** : 2
- **Fusionne** : AUD-DEP-02
- **Bloque** : AUD-DEP-03, AUD-SEC-07

**Preuve**

```text
scripts/sauvegarder-base.sh:5 « set -eu » (sans pipefail) ; :22 « $COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE" » ; :26-31 rotation « for OLD in $(ls -1t "$DEST"/argos-*.sql.gz) ... rm -f » sans contrôle de taille ni de validité ; :33 affiche toujours « Sauvegarde écrite ».
[AUD-DEP-02] scripts/sauvegarder-base.sh:22 `$COMPOSE exec -T postgres sh -c 'pg_dump ...' | gzip > "$FILE"` (set -eu seul, ligne 5) ; :26-30 rotation par nombre de fichiers sans contrôle de taille ni de validité. Commande exécutée : `sh -c 'set -eu; false | gzip > /tmp/t.gz; echo ...'` -> « sortie OK malgré échec pg_dump, taille=20 ».
```

**Impact** — Le code de retour d'un pipeline sh est celui de gzip : si postgres est arrêté ou pg_dump échoue, le script sort en 0 avec un .sql.gz vide ou tronqué. La tâche planifiée DSM ne signale rien ; après ARGOS_BACKUP_KEEP (14) échecs consécutifs, toutes les sauvegardes valides ont été supprimées par la rotation : perte de données possible lors d'une restauration.

**Action** — Écrire le dump dans un fichier temporaire puis vérifier le statut de pg_dump (ex. « pg_dump ... > tmp || exit 1 », ou bash avec « set -o pipefail »), contrôler « gzip -t » et une taille minimale, renommer seulement en cas de succès, et n'appliquer la rotation qu'après une sauvegarde valide. Sortir en code non nul pour que DSM notifie l'échec.

### AUD-PER-01 — La synchronisation d'une source d'avis s'exécute en entier dans la requête HTTP d'exploitation, avec un appel distant par avis et des attentes de reprise sur le fil de requête

- **Sévérité** : Élevée · **Axes** : Performance · **Effort** : M · **Sprint** : 7

**Preuve**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:58-63 (« Lance une synchronisation de la source et attend son résultat » -> operations.synchronize(id)) ; argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:110-111 « for (FeedEntry entry : feed.changesSince(position)) { RawAdvisory raw = feed.fetch(entry); » et :77 « sleeper.sleep(delay); » ; argos-server/src/main/java/io/argos/ingestion/osv/OsvAdvisoryFeed.java:60 (index modified_id.csv complet par écosystème) et :89 (GET /v1/vulns/<id> par avis). Aucun @Scheduled pour l'ingestion (grep @Scheduled : seuls CollecteWeb, CollecteActivite, PurgeActivite, EnvoiAlertes, RappelsRadar, ResumesPeriodiques).
```

**Impact** — Au premier passage, chaque avis de l'index de Maven, npm et PyPI donne un appel HTTP séquentiel à api.osv.dev, suivi (même fil, observateurs AFTER_SUCCESS) de l'enrichissement, de la corrélation et des alertes. Le fil de travail HTTP reste occupé pendant des heures ; les reprises (backoff jusqu'à PT5M, 5 tentatives) dorment sur ce fil. Le reverse proxy DSM ou le client coupe la requête bien avant la fin, sans que le traitement s'arrête. Sur le NAS, cela empêche aussi de planifier une collecte régulière.

**Action** — Lancer la synchronisation en tâche de fond (@Scheduled avec concurrentExecution=SKIP, ou ManagedExecutor) et faire répondre 202 avec l'identifiant du sync_run ; amorcer OSV avec l'archive par écosystème plutôt qu'un GET par avis. Le découplage de l'enrichissement relève d'AUD-PER-02.

### AUD-PER-02 — L'enrichissement NVD/EPSS et la corrélation sont exécutés de façon synchrone pour chaque signal créé ou modifié, dans le fil de l'ingestion

- **Sévérité** : Élevée · **Axes** : Performance, Qualité · **Effort** : M · **Sprint** : 7
- **Fusionne** : AUD-QUA-05

**Preuve**

```text
argos-server/src/main/java/io/argos/vulnintel/Enricher.java:57-60 « void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) { ... QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())); » ; Enricher.java:70-76 (une requête HTTP par CVE et par source : NvdSource « ?cveId= », EpssSource « ?cve= », sans cache ni limite de débit) ; argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:76-78 (corrélation au même événement) ; Enricher.java:88 retryMissing(int) n'a aucun appelant (grep -rn retryMissing main : seule la définition).
[AUD-QUA-05] vulnintel/Enricher.java:57-60 « QuarkusTransaction.requiringNew().run(() -> enrich(event.signalId())) » ; :67-83 enrich() boucle sur chaque CVE × chaque EnrichmentSource (NVD, EPSS, KEV, CERT-FR) avec source.lookup(cve) puis save() ; application.properties:28 « argos.sources.http-timeout=PT30S » ; aucun quarkus.transaction-manager.default-transaction-timeout (défaut 60 s). L'observateur est synchrone (TransactionPhase.AFTER_SUCCESS) sur le fil de l'ingestion, comme CorrelationEngine.java:76 -> MoteurAlertes (attente IA, envoi).
```

**Impact** — Chaque avis ingéré ajoute jusqu'à 2 appels HTTP bloquants (timeout PT30S chacun) par CVE, en plus de la corrélation et des alertes : le débit de l'ingestion est borné par la latence de NVD. Sans clé d'API, NVD limite fortement le débit : les refus deviennent des enrichissements manqués, qui ne sont jamais rattrapés, puisque retryMissing n'est appelée nulle part.

**Action** — D'abord planifier retryMissing (une dizaine de lignes, @Scheduled) pour ne plus perdre d'enrichissement ; puis remplacer l'observateur synchrone par une file traitée par lots avec une limite de débit par source (NVD : 5 requêtes / 30 s sans clé) et l'API EPSS par lot.

### AUD-DEP-01 — compose.yaml ne transmet pas au conteneur 23 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, quotas IA) et la doc de déploiement est en retard : e-mails vers localhost:587, liens vers localhost:5173

- **Sévérité** : Élevée · **Axes** : Dépendances, Sécurité · **Effort** : S · **Sprint** : 2
- **Fusionne** : AUD-DEP-09, AUD-SEC-08

**Preuve**

```text
compose.yaml:41-54 (bloc environment du service argos : seules ARGOS_DB_*, ARGOS_OIDC_*, ARGOS_ADMIN_EMAIL, ARGOS_IA_*, ARGOS_OLLAMA_URL, TZ) ; argos-server/src/main/resources/application.properties:56 `argos.alerting.lien-base=${ARGOS_PUBLIC_URL:http://localhost:5173}`, :72 `quarkus.mailer.host=${ARGOS_SMTP_HOST:localhost}`, :51-52 jetons ARGOS_GITLAB_TOKEN/ARGOS_GITHUB_TOKEN. Comparaison exécutée (grep des ${ARGOS_*} de application.properties vs compose.yaml) : 20 variables absentes de compose.yaml, dont ARGOS_SMTP_HOST/PORT/USER/PASSWORD/STARTTLS/TIMEOUT, ARGOS_MAIL_FROM, ARGOS_PUBLIC_URL, ARGOS_OIDC_AUDIENCE, ARGOS_GITHUB_TOKEN, ARGOS_GITLAB_TOKEN.
[À 9d96406] compose.yaml:41-54 ; 23 variables absentes (au lieu de 20), dont ARGOS_IA_QUOTA_COMPTE_JOUR, ARGOS_ARTISTE_COLLECTE_CRON, ARGOS_ARTISTE_RAPPELS_CRON ; ARGOS_IA_QUOTA_COMPTE_JOUR documentée dans .env.example:39 mais non transmise.
[AUD-DEP-09] docs/deploiement/synology-et-pc.md:5-10 « Deux conteneurs démarrent » (le service ollama existe, compose.yaml:65) ; :59 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » alors que compose.yaml:46 indique qu'Argos refuse de démarrer sans ARGOS_OIDC_URL ; :88-97 tableau des réglages sans ARGOS_OIDC_URL, ARGOS_ADMIN_EMAIL, SMTP ni ARGOS_PUBLIC_URL ; :140 « Non vérifié : la construction de l'image ». Comparaison exécutée : 20 variables de application.properties absentes de .env.example (dont ARGOS_SMTP_*, ARGOS_PUBLIC_URL, ARGOS_GITHUB_TOKEN).
[AUD-SEC-08] docs/deploiement/synology-et-pc.md:59 « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place, Argos ne doit pas être exposé sur Internet » alors que l'OIDC est obligatoire (application.properties:203 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`) ; compose.yaml ne contient pas de service Keycloak.
[À 9d96406] application.properties:238 (au lieu de :203) ; 22 variables absentes de .env.example (au lieu de 20).
```

**Impact** — Même si l'exploitant renseigne ces valeurs dans .env (comme le suggère compose.yaml:9), elles n'atteignent pas le conteneur : aucune alerte e-mail ne part (échecs comptés puis abandon après 5 tentatives), les liens des messages sont faux, l'activité des projets privés ne se collecte pas. Seul un avertissement au démarrage existe pour ARGOS_PUBLIC_URL (MoteurAlertes.java:117-121).

**Action** — Ajouter au service argos de compose.yaml les variables ARGOS_PUBLIC_URL, ARGOS_SMTP_*, ARGOS_MAIL_FROM, ARGOS_OIDC_AUDIENCE, ARGOS_GITHUB_TOKEN, ARGOS_GITLAB_TOKEN, ARGOS_IA_PLAFOND_JOUR, ARGOS_IA_QUOTA_ESPACE_JOUR (ou `env_file: .env`), et un test qui compare les ${ARGOS_*} de application.properties à compose.yaml et .env.example. Mettre à jour docs/deploiement (Keycloak prérequis, variables OIDC/SMTP/PUBLIC_URL, profil ia) et compléter .env.example.

## Moyenne (19)

### AUD-ARC-03 — Le diagramme arc42 §5.2 et le contexte §5.1 ne décrivent plus l'architecture réelle (sens inversés, arêtes manquantes, fournisseurs obsolètes)

- **Sévérité** : Moyenne · **Axes** : Architecture · **Effort** : S · **Sprint** : 9
- **Dépend de** : AUD-ARC-05, AUD-ARC-06

**Preuve**

```text
docs/architecture/arc42/05-vue-blocs.md:85 « INGEST --> SIGNAL » alors que signal importe ingestion 18 fois (signal/web/CollecteWeb.java:18-21) ; :86 « SIGNAL --> VULN » vs vulnintel/Enricher.java:27 import io.argos.signal.SignalChanged ; :92 « IMPACT --> ALERT » et :104 « WORK --> ALERT » vs alerting->impact (5) et alerting->workspace (7) ; :98 « SCM --> INVENTORY » vs inventory->scm (4). Absentes du diagramme : api->catalog (13), api->impact (8), api->alerting, mcp->10 modules dont api, catalog->workspace, impact->catalog, signal->catalog, releaseradar->catalog, inventory->catalog. :19-20 « Claude / Anthropic », « IdP entreprise » alors que le code appelle Ollama (aigateway/ollama/FournisseurOllama.java) et Keycloak (ADR-0027) ; idem 03-contexte-perimetre.md:52-53,73-74.
```

**Impact** — Le diagramme mélange flux de données et dépendances de compilation ; un lecteur (ou un nouvel arrivant) en déduit des règles de dépendance fausses. La documentation n'est plus utilisable comme référence de revue d'architecture.

**Action** — Régénérer §5.2 depuis la table ALLOWED (une flèche = « dépend de »), séparer un diagramme de flux de données distinct, et remplacer Claude/IdP entreprise par Ollama local et Keycloak en §3 et §5.1.

### AUD-ARC-04 — Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes

- **Sévérité** : Moyenne · **Axes** : Architecture, Qualité · **Effort** : S · **Sprint** : 4
- **Fusionne** : AUD-QUA-03
- **Dépend de** : AUD-ARC-05

**Preuve**

```text
grep -rn "SET LOCAL ROLE argos_app" -> workspace/ContexteBase.java:78, impact/RlsSession.java:24, workspace/subscription/SubscriptionService.java:269, workspace/preference/PreferencesRepository.java:116. impact/RlsSession.java:14-15 « Même protocole que … ContexteBase#appliquer, que impact ne peut pas utiliser directement (ModuleBoundariesTest) ». SubscriptionService.java:272 et PreferencesRepository.java:119 ne positionnent que compte_courant et espace_courant (pas sujet_courant ni email_courant, lus par les politiques de V2__comptes_et_acces.sql).
[AUD-QUA-03] workspace/ContexteBase.java:76-91 (4 paramètres : compte, sujet, email, espace) ; impact/RlsSession.java:22-32 (copie assumée : « que impact ne peut pas utiliser directement (ModuleBoundariesTest) ») ; workspace/subscription/SubscriptionService.java:266-275 et workspace/preference/PreferencesRepository.java:114-123 (2 paramètres seulement, sujet/email non réinitialisés ; PreferencesRepository:121 « compte.toString() » lève NullPointerException si compte est nul, là où les autres copies passent "").
```

**Impact** — Toute évolution des variables ou du rôle RLS (migration V2) doit être répercutée à quatre endroits ; les deux variantes partielles laissent sujet/email hérités de la transaction. La règle de frontière a poussé à copier du code de sécurité plutôt qu'à le partager.

**Action** — Faire de ContexteBase.appliquer le seul point d'application (voir AUD-ARC-05 : le déplacer dans un module transverse autorisé à tous), supprimer RlsSession et les deux copies de workspace, et ajouter une règle ArchUnit interdisant la chaîne « set_config('argos. » hors de cette classe (test de source).

### AUD-ARC-05 — Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où des exceptions ArchUnit au cas par cas

- **Sévérité** : Moyenne · **Axes** : Architecture · **Effort** : M · **Sprint** : 4
- **Dépend de** : AUD-ARC-14
- **Bloque** : AUD-ARC-03, AUD-ARC-04, AUD-QUA-06

**Preuve**

```text
Classes de workspace importées hors workspace : DonneeInvalide, RegleViolee, Introuvable, AccesRefuse, ContexteBase (catalog/standard/ReferentielEspace.java:23-26, signal/web/SourcesWeb.java:26-29, projectintel/ActiviteQueries.java:26-27, inventory/InventoryQueries.java:24, mcp, api). ModuleBoundariesTest.java contient 3 règles d'exception dédiées (signalNEmprunteQueRlsEtErreurs, projectintelNEmprunteQueRlsEtErreurs, catalogNEmprunteQueRlsEtErreurs) et une 4e pour mcp->api.security (mcpNeLitQueLaSecuriteDeLApi : Caller, CallerResolver, LimiteurDebit, mcp/GardeAppels.java:20-22). LimiteurDebit est instancié dans api/ActiviteResource.java:61, api/VeilleWebResource.java:90 et mcp/GardeAppels.java:74.
```

**Impact** — Chaque nouveau module qui a besoin de la RLS ou d'une erreur commune doit soit dépendre de tout workspace, soit obtenir une exception, soit dupliquer (cas impact, AUD-ARC-04). Le sens réel « workspace = cœur métier des espaces » se brouille avec « workspace = socle technique ».

**Action** — Extraire un paquet transverse io.argos.commun (ContexteBase, DonneeInvalide, RegleViolee, Introuvable, AccesRefuse) et io.argos.commun.securite (Caller, LimiteurDebit) autorisé pour tous les modules ; retirer les 4 règles d'exception et les entrées workspace/api devenues inutiles dans ALLOWED.

### AUD-QUA-04 — Deux modèles transactionnels coexistent (JTA @Transactional/QuarkusTransaction et setAutoCommit/commit manuels recopiés cinq fois)

- **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 4

**Preuve**

```text
grep -rn "@Transactional" argos-server/src/main/java | wc -l -> 69 ; grep -rn "setAutoCommit" -> ContexteBase.java:55, QuotasIa.java:70, impact/RadarProjets.java:207 (dettes), impact/RapprochementActualites.java:142 (pour), releaseradar/CyclesDeVie.java:101. Même bloc recopié : « boolean autoCommit = c.getAutoCommit(); c.setAutoCommit(false); try { ... c.commit(); } catch (SQLException | RuntimeException e) { c.rollback(); throw e; } finally { c.setAutoCommit(autoCommit); } ».
[À 9d96406] aigateway/QuotasIa.java:86 (au lieu de :70) ; toujours 5 setAutoCommit(false), 69 @Transactional ; nouvelle méthode liberer() en autocommit (QuotasIa.java, portees).
```

**Impact** — Les méthodes à transaction manuelle ne fonctionnent que si aucun appelant n'ouvre de transaction JTA : avec Agroal, commit() sur une connexion enrôlée lève une SQLException. Ajouter un @Transactional sur une ressource ou un service appelant (régression facile) casse ces chemins à l'exécution seulement. Les cinq copies du même squelette augmentent le coût de maintenance.

**Action** — Choisir un modèle unique : soit tout en JTA (QuarkusTransaction.requiringNew() + ContexteRls.appliquer), soit un seul utilitaire de transaction manuelle (ContexteBase.executer généralisé et partagé) utilisé par les cinq sites ; documenter la règle dans arc42 §8 et l'outiller par un test ArchUnit (interdire setAutoCommit hors de l'utilitaire).

### AUD-QUA-06 — Le code JDBC brut est répété sans utilitaire commun : 198 prepareStatement, 101 catch SQLException et 10 copies du même convertisseur de date

- **Sévérité** : Moyenne · **Axes** : Qualité · **Effort** : M · **Sprint** : 4
- **Dépend de** : AUD-ARC-05

**Preuve**

```text
Commandes : grep -rn prepareStatement argos-server/src/main/java | wc -l -> 184 (41 fichiers) ; grep -rn "catch (SQLException" | wc -l -> 93, dont 78 suivis de « throw new IllegalStateException » ; « private static Instant instant(ResultSet rs, int ...) » recopié dans SignalQueries.java:268, ReferentielEspace.java:336, SourcesWeb.java:239, ActualitesWeb.java:162, ImpactQueries.java:245, RechercheVeille.java:312, ConsultationAlertes.java:143, SyncStore.java:280, InventoryQueries.java:232, CyclesDeVie.java:163 ; couper() x3 (LecteurFlux:104, StockActualites:336, SourcesWeb:244) ; utc() x2 (SignalRecorder:226, SyncStore:285).
[À 9d96406] 198 prepareStatement (45 fichiers), 101 catch SQLException ; 10 copies de instant(ResultSet).
```

**Impact** — Environ 30 à 40 % des lignes des classes *Queries/*Store sont du squelette (ouverture, paramètres positionnels, mapping par index de colonne, réemballage) : mapping fragile aux changements de colonnes, gestion d'erreur hétérogène (IllegalStateException ici, QuarkusTransactionException ailleurs, ex. ResumesPeriodiques.java:120-151), revue coûteuse.

**Action** — Introduire un petit utilitaire partagé (ex. Jdbc.query(ds, sql, binder, rowMapper) / update(...) et des convertisseurs Instant/OffsetDateTime communs) ou adopter un client léger (Jdbi, déjà compatible Quarkus) ; migrer d'abord les modules cœur (signal, impact, alerting). Garder le SQL explicite, conforme au choix actuel.

### AUD-SEC-01 — Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion)

- **Sévérité** : Moyenne · **Axes** : Sécurité · **Effort** : M · **Sprint** : 2
- **Bloque** : AUD-ARC-10

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:103 `adresses = resolveur.resoudre(hote);` puis RecuperateurWeb.java:89 `URI courante = garde.verifier(...)` et RecuperateurWeb.java:189 `client.send(requete, ...)` : HttpClient résout de nouveau le nom lui-même (aucune connexion à l'IP validée). Seul le cache DNS par défaut de la JVM (30 s, java.security : `#networkaddress.cache.ttl=-1` non défini) réduit la fenêtre. Aucun test de rebinding (grep 'rebind' dans src/test : rien).
```

**Impact** — Un compte authentifié contrôlant un DNS à TTL court peut, en déclenchant l'ajout ou la collecte d'une source (10/min), tenter de faire viser par Argos une adresse privée sur les ports 80/443 : interface DSM du NAS, réseau local, ou 169.254.169.254 en hébergement en ligne. La fenêtre est étroite : GardeAdresses et HttpClient partagent le cache DNS de la JVM (30 s), il faut que l'expiration tombe entre la vérification et la connexion. À repasser Élevée à l'ouverture au public.

**Action** — Épingler l'adresse validée : résoudre une fois, vérifier toutes les IP, puis se connecter à cette IP (socket/SSLSocket avec SNI et Host d'origine, ou client HTTP avec résolveur personnalisé type OkHttp Dns / Apache DnsResolver) ; refaire de même à chaque redirection. Ajouter un test unitaire avec un résolveur qui change de réponse entre deux appels. En hébergement en ligne, bloquer aussi en sortie (pare-feu/egress) les plages privées et 169.254.0.0/16.

### AUD-SEC-03 — Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête

- **Sévérité** : Moyenne · **Axes** : Sécurité, Performance · **Effort** : S · **Sprint** : 8
- **Fusionne** : AUD-PER-08
- **Bloque** : AUD-PER-18

**Preuve**

```text
argos-server/src/main/java/io/argos/api/GestionErreurs.java:51 `journal.enregistrer(null, utilisateur.sujet(), ressource(requete), "INSCRIPTION_SUR_INVITATION");` ; V2__comptes_et_acces.sql:68 `CREATE TABLE refus_acces (... sujet_oidc text ...)` ; tables mcp_appel (V9) et ia_appel (V13) de même ; `grep -rniE 'DELETE FROM (refus|mcp_appel|ia_appel)|purge' src/main` : seule PurgeActivite existe (activité des projets). Le limiteur de débit est par compte Argos : un sujet sans compte n'est pas limité.
[AUD-PER-08] Seule purge planifiée : argos-server/src/main/java/io/argos/projectintel/PurgeActivite.java:32 -> ActiviteStore.java:157-161 (activite_compteur, activite_jalon). grep -rln 'DELETE FROM' main : aucune suppression sur mcp_appel, refus_acces, ia_appel, alerte, sync_run, sync_attempt, ops_event, ingestion_failure, signal_version ni actualite. Inserts : argos-server/src/main/java/io/argos/audit/JournalAppelsMcp.java:44, JournalRefus.java:37, JournalAppelsIa.java:44 ; argos-server/src/main/resources/db/migration/V3__collecte_avis_securite.sql:100 « raw_content text NOT NULL » (JSON brut de chaque version de chaque avis) et :60 (ingestion_failure.raw_content).
[À 9d96406] Inchangé pour refus_acces/mcp_appel/ia_appel ; s'y ajoute opportunite (V15__profil_artiste.sql), sans aucun DELETE FROM opportunite.
```

**Impact** — En service ouvert (inscription Keycloak libre, mode invitation côté Argos), n'importe qui peut faire croître sans limite refus_acces (déni de service par stockage sur le NAS) ; les journaux conservent indéfiniment des identifiants personnels (sujet OIDC), contraire à la minimisation RGPD.

**Action** — Purge planifiée des journaux (ex. 90 jours à 1 an, paramétrable) ; limiter les écritures de refus par sujet (fenêtre glissante, comme LimiteurDebit) ; limiter aussi en amont par IP au reverse proxy.

### AUD-TST-01 — Aucune intégration continue : aucun test (mvn verify, npm test, ModuleBoundariesTest) n'est exécuté automatiquement

- **Sévérité** : Moyenne · **Axes** : Tests, Dépendances · **Effort** : M · **Sprint** : 1
- **Fusionne** : AUD-DEP-04
- **Bloque** : AUD-DEP-20, AUD-TST-02, AUD-TST-19

**Preuve**

```text
ls -a /home/claude/work/argos/.github -> No such file or directory ; aucun autre fichier de pipeline. Dockerfile:15 « Les tests demandent Docker (base jetable) : ils se lancent hors de l'image avec « mvn verify » » ; Dockerfile:22 RUN mvn -B package -DskipTests
[AUD-DEP-04] Absence de .github/ (ls -la à la racine) et de tout autre fichier de CI ; .git/hooks ne contient que des *.sample (ls -la .git/hooks), core.hooksPath non défini ; .archgate/config.json `"domains": {}` et .archgate/adrs/GEN-001-example.md (« This is an example ADR ... Replace this ») ; Dockerfile:22 `RUN mvn -B package -DskipTests` ; README.md:49 `tests : mvn verify (Docker requis)` ; scripts/verifier-docs.mjs (contrôle de liens/Mermaid) lancé à la main.
[À 9d96406] Toujours aucune CI ; 363 méthodes de test serveur (décompte statique), 416 tests exécutés (mesure du mainteneur).
```

**Impact** — Les 321 méthodes de test serveur, les tests d'architecture et les 47 tests JS ne protègent que si le développeur pense à les lancer ; une image peut être construite et déployée (NAS puis service ouvert au public) avec des tests en échec ou une frontière de module violée.

**Action** — Ajouter un workflow (GitHub Actions ou équivalent) déclenché sur push/PR : mvn -B verify (runner avec Docker), npm ci && npm test && npm run typecheck dans argos-ui, npm ci && npm run check dans argos-desktop ; rendre la branche principale protégée par ce statut.

### AUD-TST-03 — Pyramide inversée : 71 % des méthodes de test serveur sont des @QuarkusTest exigeant Docker (PostgreSQL + Keycloak Dev Services)

- **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : L · **Sprint** : 9

**Preuve**

```text
grep -rl @QuarkusTest src/test/java | wc -l -> 45 classes ; décompte @Test/@ParameterizedTest : 239 dans les classes @QuarkusTest, 82 dans 18 classes unitaires (total 321). README.md:49 « tests : mvn verify (Docker requis : base jetable) »
[À 9d96406] 259 méthodes @QuarkusTest sur 363 = 71 % (48 classes).
```

**Impact** — Boucle de retour lente, impossible sans Docker ; la logique métier (corrélation, règles d'alerte, impacts) n'est vérifiable qu'à travers la pile complète, ce qui rend les échecs plus coûteux à diagnostiquer.

**Action** — Extraire en tests unitaires purs la logique déterministe déjà isolable (CorrelationEngine, MoteurAlertes, règles de RegleAlerte, RechercheVeille/TraductionCriteres, ValidationSortie) ; garder les @QuarkusTest pour les parcours, la RLS et la sécurité.

### AUD-TST-04 — Couverture inégale : scm à 27 % des lignes et 9 % des branches, six modules sans classe de test dédiée dont workspace (26 fichiers, 1 693 lignes)

- **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : M · **Sprint** : 6
- **Dépend de** : AUD-TST-02

**Preuve**

```text
Comptage main/test par paquet : workspace 24/0, inventory 10/0, scm 8/0, vulnintel 9/0, releaseradar 5/0, regintel 3/0, audit 4/0 ; impact 13 fichiers (1 919 l.) pour 1 test (30 l.). Classes jamais nommées dans src/test : workspace/subscription/SubscriptionService, workspace/vue/VuesEnregistrees, alerting/MoteurAlertes, mcp/GardeAppels, mcp/OutilsSecurite, audit/JournalRefus, releaseradar/* (0/4)
[À 9d96406] Six modules sans test dédié : workspace (26/1 693 l.), inventory, vulnintel, releaseradar, regintel, audit ; scm a DepotGithubTest.
[Mesure] mvn clean verify + JaCoCo 0.8.13 sur le poste du mainteneur : scm 26,9 % lignes / 8,9 % branches (290 l.) ; total 83,8 % / 68,6 %.
```

**Impact** — La couverture globale mesurée est bonne (83,8 % des lignes, 68,6 % des branches), mais scm, qui lit les dépôts GitHub et GitLab des utilisateurs, est presque entièrement hors tests ; les six modules sans test dédié ne sont couverts que par les parcours, sans cas limites ciblés.

**Action** — Ajouter des tests ciblés de scm (pagination, quotas, erreurs de forge) jusqu'au seuil de couverture, puis des tests dédiés des cas limites de workspace (rôles, dernier administrateur, invitations).

### AUD-TST-05 — Autorisations par appels impératifs (access.operator()…) sans test matriciel point d'entrée × rôle ; POST /ops/sources/{id}/synchronizations jamais testé

- **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : M · **Sprint** : 5

**Preuve**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:57-61 (@POST @Path("/{id}/synchronizations") … access.operator();) ; grep -rn synchronizations src/test/java -> 0 ; seul refus 403 « exploitation » vérifié : chain/ResilienceTest.java:151 Api.en("jules").when().get("/api/v1/ops/sources").then().statusCode(403); AuthentificationTest.java:21-22 ne teste le 401 que sur 6 chemins
[À 9d96406] SourceOperationsResource.java:57-63 toujours non testé ; PUT /api/v1/profils/artiste/sources/{id}/conditions-usage sans test de 403 (ProfilArtisteTest.java:306-308 ne couvre que POST/GET sources et POST collectes).
```

**Impact** — L'oubli d'un appel access.*() sur un nouveau point d'entrée (ou sur un existant non testé) donnerait une élévation de privilège qu'aucun test ne détecte ; le 401 global est assuré par quarkus.http.auth.permission (application.properties:217-218) mais pas le contrôle de rôle.

**Action** — Ajouter un test paramétré qui énumère toutes les opérations du document OpenAPI (/q/openapi) et vérifie pour chaque rôle (anonyme, membre lecteur, administrateur d'espace, exploitant, autre espace) le code attendu ; échec si une opération n'est pas déclarée dans la matrice.

### AUD-TST-09 — IHM : seuls la logique pure et des contrôles textuels de sources sont testés ; aucun test de composant Vue ni du client API

- **Sévérité** : Moyenne · **Axes** : Tests · **Effort** : M · **Sprint** : 6
- **Dépend de** : AUD-ARC-12

**Preuve**

```text
argos-ui/package.json « test »: node --test "tests/**/*.test.mjs" (pas de vitest ni @vue/test-utils) ; imports des tests : src/contenu/{alertes-poste,resolution,resume-ia,liens}.ts uniquement ; resume-ia.test.mjs:26-29 lit AlertesView.vue en texte et vérifie l'absence de v-html ; src/api/client.ts (207 l.) et les 9 composants .vue non testés
```

**Impact** — Le client API (jetons, erreurs, formats) et le rendu des vues peuvent régresser sans détection ; les contrôles par expression régulière sur le source ne garantissent pas le comportement rendu.

**Action** — Introduire vitest + @vue/test-utils (environnement happy-dom) : tests du client API avec fetch simulé (401, erreurs, sérialisation) et tests de rendu des vues Alertes/Affichage/Réglages.

### AUD-PER-03 — Corrélation par projet en N+1 : une transaction, une relecture du signal et une requête de composants par signal, puis un INSERT préparé à chaque ligne

- **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Preuve**

```text
argos-server/src/main/java/io/argos/impact/CorrelationEngine.java:94-99 « List<UUID> signalIds = ...signals.signalsAffecting(...); for (UUID signalId : signalIds) { reports.add(correlate(signalId, Optional.of(projectId))); » ; :105 requiringNew par signal ; :143 signals.find ; :161 inventories.componentsMatching par signal ; :167 prepareStatement(INSERT INTO project_impact ...) dans la boucle.
```

**Impact** — Pour un inventaire npm ou Maven qui touche des centaines de paquets à avis, cela fait des centaines de transactions et au moins 4 allers-retours SQL par signal, plus une évaluation des alertes par signal (MoteurAlertes). Le tout s'exécute de façon synchrone après la validation de InventoryStore.replace (observateur AFTER_SUCCESS), donc dans le fil de l'appelant de l'import.

**Action** — Charger en une requête les signaux et leurs paquets affectés (signal + signal_package par package_key = ANY), et les composants du projet une seule fois ; préparer l'INSERT hors boucle avec addBatch ; regrouper les ImpactsRecorded par projet ; exécuter la corrélation d'inventaire en tâche de fond.

### AUD-PER-04 — Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation

- **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Preuve**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:141 « for (Destinataires.Destinataire d : candidats) { plan(e, d, paquets)... » ; :173 espacesDe, :175 impacts.forSignals, :180 abonnements.activeFor (chacun dans QuarkusTransaction.requiringNew()) ; :145 et :161 alerteExiste par plan et canal ; :262 « demande.get(delaiResumeIa...) » (attente IA jusqu'à PT10S) ; :168 envoi.envoyer(creees, ...) (SMTP synchrone, timeout PT10S par message).
```

**Impact** — Pour un espace de M membres, chaque signal corrélé coûte environ 4×M transactions courtes. S'y ajoutent jusqu'à 10 s d'attente de l'IA et l'envoi SMTP séquentiel, dans le fil qui ingère. En service multi-utilisateurs, avec des espaces nombreux ou peuplés, cela multiplie la durée d'ingestion et la charge du pool de connexions.

**Action** — Charger en une requête par signal les membres, leurs espaces et leurs abonnements actifs (jointure appartenance + subscription), ainsi que les alertes existantes (signal_id, compte_id) ; ne pas appeler envoi.envoyer dans l'observateur et laisser le job EnvoiAlertes (1 min) diffuser ; rendre l'observateur asynchrone (@ObservesAsync, ou file).

### AUD-PER-06 — Le fil de veille (/me/watch) relit chaque signal un par un et parcourt signal_package par des expressions non indexées

- **Sévérité** : Moyenne · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Preuve**

```text
argos-server/src/main/java/io/argos/signal/SignalQueries.java:209-217 « SELECT DISTINCT s.id, s.modified_at FROM signal s LEFT JOIN signal_package p ... WHERE lower(p.ecosystem) = ANY (?) OR split_part(substr(p.package_key, 5), '/', 1) = ANY (?) OR lower(regexp_replace(...)) = ANY (?) OR ... ORDER BY s.modified_at DESC LIMIT ? » ; :231 « ids.stream().map(this::find) » (1 requête par signal, jusqu'à 100) ; argos-server/src/main/java/io/argos/api/WatchResource.java:79 (signals.find par signal d'un filtre PROJECT) et :91 (search par abonnement). Index : signal_package_key (package_key) seul (V3:122), aucun index sur lower(ecosystem) ni sur signal.modified_at (signal_date porte sur coalesce(published_at, modified_at), V11:42).
```

**Impact** — Chaque GET /api/v1/me/watch coûte, par abonnement, un parcours complet de signal ⋈ signal_package (les OR d'expressions empêchent l'usage d'un index), un tri, puis jusqu'à 100 lectures unitaires. Le coût croît avec la taille du corpus OSV et le nombre d'abonnements, sans pagination côté API.

**Action** — Faire porter ce fil par RechercheVeille (déjà paginée et indexée) ou ajouter des colonnes normalisées indexées (ecosystem_lower, package_name) ; remplacer find() en boucle par une lecture « WHERE id = ANY (?) » ; paginer la réponse.

### AUD-PER-18 — Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge

- **Sévérité** : Moyenne · **Axes** : Performance, Qualité · **Effort** : S · **Sprint** : 8
- **Dépend de** : AUD-SEC-03

**Preuve**

```text
signal/opportunite/CollecteOpportunites.java:171-172 (élément de flux enregistré avec dateLimite null) ; signal/opportunite/Opportunites.java:42-43 « AND (o.date_limite IS NULL OR o.date_limite >= ?) ORDER BY … » sans LIMIT ; grep 'DELETE FROM opportunite' src/main -> aucun résultat ; api/artiste/ProfilArtisteResource.java:218 (liste complète renvoyée) ; alerting/RappelsOpportunites.java:93,110 (relecture complète pour chaque suiveur).
```

**Impact** — Tout élément de flux jamais collecté reste une opportunité « ouverte » pour toujours. GET /opportunites et la tâche quotidienne de rappels relisent et renvoient toute la table pour chaque utilisateur : le fil se remplit d'éléments périmés et la charge croît linéairement avec l'historique et le nombre de suiveurs (N+1 par suiveur).

**Action** — Donner aux éléments de flux une échéance (date de publication + durée configurée) ou les exclure au-delà d'un âge maximal ; paginer GET /opportunites (LIMIT/curseur) ; purge planifiée des opportunités échues, à intégrer à la rétention de SEC-03 ; calculer les rappels en une requête qui joint suivi_profil et opportunite.

### AUD-DEP-03 — Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée

- **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : S · **Sprint** : 2
- **Dépend de** : AUD-QUA-02

**Preuve**

```text
docs/deploiement/synology-et-pc.md:72-76 `gunzip -c ... | docker compose exec -T postgres sh -c 'psql -U ... -d ...'` puis « À tester au moins une fois après la mise en service » ; scripts/sauvegarder-base.sh:22 `pg_dump ... --no-owner` (format texte, sans --clean) ; application.properties:23 `quarkus.flyway.migrate-at-start=true`.
```

**Impact** — Sur une base où Argos a déjà démarré (Flyway a créé le schéma), la restauration produit des erreurs « already exists » / clés dupliquées que psql ignore : restauration partielle silencieuse. Aucun exercice de restauration n'a été réalisé (doc:76, doc:138-140).

**Action** — Documenter la séquence complète : arrêter le service argos, recréer la base (dropdb/createdb ou pg_dump --clean --if-exists), restaurer avec `psql -v ON_ERROR_STOP=1` ou `pg_restore` (format -Fc), puis redémarrer ; ajouter un test de restauration (script ou test Testcontainers) exécuté périodiquement.

### AUD-DEP-07 — Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier

- **Sévérité** : Moyenne · **Axes** : Dépendances · **Effort** : M · **Sprint** : 3

**Preuve**

```text
argos-server/pom.xml:43-151 : seule quarkus-smallrye-health (l.53-56) ; pas de quarkus-logging-json, quarkus-micrometer ni quarkus-opentelemetry ; application.properties : aucune clé quarkus.log.* ; grep -rlE '@Readiness|@Liveness|HealthCheck' argos-server/src/main/java -> aucun résultat ; journaux texte via LOG.*f (27 errorf, 16 infof, 11 warnf, 1 debugf), aucun MDC.
```

**Impact** — En exploitation publique, impossible de suivre les taux d'erreur, la latence, l'état DEGRADED des sources, la file d'envoi des e-mails ou la disponibilité d'Ollama autrement qu'en lisant les journaux Docker ; /q/health ne reflète que la base (contrôle par défaut de la datasource).

**Action** — Ajouter quarkus-logging-json (profil prod), quarkus-micrometer-registry-prometheus (endpoint protégé), des checks @Readiness pour ingestion/envoi e-mail/IA (dégradé non bloquant), et un identifiant de corrélation en MDC ; OpenTelemetry quand un collecteur existe.

### AUD-DEP-20 — Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée

- **Sévérité** : Moyenne · **Axes** : Dépendances, Qualité · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01

**Preuve**

```text
git log -- db/migration/V13__passerelle_ia.sql -> 4549bd7 (2026-11-16, création) puis 230f395 (2026-11-17, ajout de 'QUOTA_COMPTE' au CHECK de ia_appel.issue, l.30) ; git ls-tree -r HEAD^1 (c4a35bd, main avant la PR #7) : V13 absente, donc jamais en production. application.properties:14-17 : %dev utilise la base persistante de compose (devservices désactivé) ; :23 migrate-at-start=true. Aucune CI, aucun test ni hook ne refuse la modification d'un V*__*.sql existant.
```

**Impact** — Toute base de développement migrée entre les deux commits (base compose du poste) refuse de démarrer : somme de contrôle Flyway différente à la validation. Même règle en production : une fois le NAS en service, la réécriture d'une migration déjà fusionnée empêche Argos de démarrer, ou, après un « flyway repair », laisse un schéma divergent (ici, le CHECK sans QUOTA_COMPTE ferait échouer l'écriture du journal IA).

**Action** — Règle écrite (ADR ou CONTRIBUTING) : une migration fusionnée est immuable et toute correction passe par V(n+1). Contrôle en CI : échec si `git diff --name-status origin/main...HEAD -- argos-server/src/main/resources/db/migration` contient autre chose que des ajouts (A). Option : quarkus.flyway.validate-on-migrate explicite et test qui migre une base depuis le schéma de main.

## Faible (35)

### AUD-ARC-06 — Couplage par le schéma SQL : des modules lisent directement les tables d'autres modules, hors du contrôle d'ArchUnit

- **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 4
- **Fusionne** : AUD-QUA-07
- **Bloque** : AUD-ARC-03

**Preuve**

```text
python3 -I /home/claude/audit-tmp/arch/tables.py -> signal : impact(3 fichiers), signal_package/signal_version : impact ; actualite : impact(2) ; project : impact(5) ; component : impact(2) ; appartenance/compte : alerting ; signal_alias : vulnintel. Ex. impact/ImpactQueries.java:56-58 « JOIN signal s … JOIN project p … JOIN component c », alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c », vulnintel/Enricher.java:91 « FROM signal_alias a », impact/RechercheVeille.java:218,238 « FROM signal », « FROM actualite ».
[AUD-QUA-07] impact/ImpactQueries.java:56 « JOIN signal s » ; impact/AnalyseComplementaireIa.java:114 (signal, signal_package) ; impact/RechercheVeille.java:218 (signal, actualite) ; impact/RapprochementActualites.java (actualite, component, project) ; alerting/Destinataires.java:40 « FROM appartenance a JOIN compte c » et :60 (tables du module workspace) ; vulnintel/Enricher.java:89-93 (signal_alias). Commande : grep -rlE "(FROM|JOIN|INTO|UPDATE)\s+<table>" par table -> signal lue par 3 modules, project par 2, appartenance par workspace et alerting.
```

**Impact** — La propriété des tables n'est documentée nulle part ; renommer une colonne de signal ou d'inventory casse impact, alerting ou vulnintel sans qu'aucun test d'architecture ne le signale. Toutes ces arêtes SQL doublent une arête Java déjà autorisée (pas de dépendance cachée nouvelle), mais elles fixent le schéma comme contrat public.

**Action** — Documenter en arc42 §5.3 le module propriétaire de chaque table ; pour les lectures étrangères, passer par les services Query du propriétaire (SignalQueries, InventoryQueries, EspacesService) ou par des vues SQL nommées comme contrat ; ajouter un test qui vérifie que chaque module ne cite que ses tables ou des vues publiées.

### AUD-ARC-07 — La table ALLOWED est plus large que le code et ne contraint pas un paquet io.argos non déclaré

- **Sévérité** : Faible · **Axes** : Architecture, Tests · **Effort** : S · **Sprint** : 1
- **Fusionne** : AUD-TST-11

**Preuve**

```text
compare.py -> « autorisées inutilisées: api->regintel, catalog->audit, impact->vulnintel, mcp->projectintel, regintel->{aigateway,catalog,signal}, releaseradar->signal, vulnintel->catalog, workspace->audit » (10 sur 54). ModuleBoundariesTest.java:71-73 : la liste des paquets interdits est calculée à partir de ALLOWED.keySet() ; un nouveau paquet io.argos.x absent de la table n'est ni contraint ni protégé.
[AUD-TST-11] argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java:31-57 (table ALLOWED, 16 entrées) et :69-80 (règle générée uniquement pour ALLOWED.keySet(), interdits calculés à partir des seules clés, allowEmptyShould(true))
```

**Impact** — Des dépendances peuvent apparaître sans revue (déjà autorisées) ; un module ajouté sans entrée dans la table échappe entièrement au contrôle.

**Action** — Retirer les 10 autorisations inutilisées (les rajouter le jour où le code en a besoin) ; ajouter une règle « toute classe de io.argos réside dans un paquet de premier niveau listé dans ALLOWED ».

### AUD-ARC-09 — Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP

- **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 7

**Preuve**

```text
Observateurs transactionnels : impact/CorrelationEngine.java:76,84, vulnintel/Enricher.java:57, impact/RapprochementActualites.java:64 « @Observes(during = TransactionPhase.AFTER_SUCCESS) ». Observateurs synchrones : alerting/MoteurAlertes.java:124, impact/RadarProjets.java:187, alerting/RappelsRadar.java:130, api/preference/ActivationProfilParDefaut.java:34. catalog/standard/ReferentielEspace.java:104 « r.changement().ifPresent(modifications::fire); » après commit, dans le thread du PUT /standards.
```

**Impact** — Un PUT sur le référentiel déclenche synchroniquement RadarProjets puis RappelsRadar (lecture des inventaires, émission d'alertes) avant de répondre ; les exceptions sont avalées et journalisées (RadarProjets.java:196-198) : latence et échecs silencieux côté utilisateur. La règle de choix entre les deux styles n'est écrite nulle part.

**Action** — Documenter la règle en arc42 §8 (événement inter-modules = AFTER_SUCCESS ou @ObservesAsync) et l'appliquer à StandardModifie, AnnonceStandard et ImpactsRecorded.

### AUD-ARC-10 — La collecte HTTP est dispersée dans six clients répartis sur cinq modules

- **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 7
- **Fusionne** : AUD-QUA-12
- **Dépend de** : AUD-SEC-01

**Preuve**

```text
grep -rln "HttpClient.newBuilder" -> ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, scm/ForgeHttp.java:36, vulnintel/EnrichmentHttp.java:36, releaseradar/ClientEndoflife.java:72, aigateway/ollama/FournisseurOllama.java:59 ; tous « followRedirects(NEVER) » et connectTimeout. arc42 §5.3 (05-vue-blocs.md:119) attribue la collecte à source-ingestion ; la garde d'adresses (ingestion/web/GardeAdresses.java) n'est utilisée que par la veille web.
[AUD-QUA-12] HttpClient.newBuilder() dans vulnintel/EnrichmentHttp.java:36, scm/ForgeHttp.java:36, releaseradar/ClientEndoflife.java:72, ingestion/HttpSourceClient.java:33, ingestion/web/RecuperateurWeb.java:180, aigateway/ollama/FournisseurOllama.java:59 (chaque fois connectTimeout + Redirect.NEVER, limites de taille et gestion d'InterruptedException réécrites). scm/RepositoryActivity.java:150-162 boucle de pagination manuelle alors que pages() (:326-337) fait la même chose.
[À 9d96406] scm/ForgeHttp.java:44 (au lieu de :36) ; 05-vue-blocs.md:120 ; toujours 6 HttpClient.newBuilder ; la collecte artiste réutilise RecuperateurWeb.
```

**Impact** — Configuration homogène aujourd'hui, mais chaque évolution (proxy, User-Agent, limites de taille, garde SSRF) doit être faite six fois ; vulnintel et releaseradar sont à la fois métier et adaptateurs.

**Action** — Fournir une fabrique de clients HTTP commune (timeouts, taille maximale, User-Agent, garde d'adresses optionnelle) dans le module transverse proposé en AUD-ARC-05 ; mettre à jour §5.3.

### AUD-ARC-11 — Aucun objet-dieu ; impact porte aussi la recherche plein texte que le package-info attribue à signal

- **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 9

**Preuve**

```text
find io/argos -name '*.java' | xargs wc -l | sort -n | tail -> plus gros fichier scm/RepositoryActivity.java 394 lignes, puis workspace/EspacesService.java 383, mcp/OutilsSecurite.java 383 ; moyenne 87 lignes/fichier. impact = 1 919 lignes/13 fichiers (CorrelationEngine, RadarProjets, RechercheVeille, RapprochementActualites, AnalyseComplementaireIa). signal/package-info.java : « Signaux normalisés, taxonomie, déduplication, recherche » alors que la recherche est impact/RechercheVeille.java (lit les tables signal et actualite, l.218, 238).
[À 9d96406] signal 20 fichiers / 2 550 l. > impact 16 / 2 159 l. : la partie « impact le plus dense » ne tient plus.
```

**Impact** — Taille des classes saine ; les responsabilités d'impact débordent toutefois (corrélation, recherche, radar, IA), ce qui en fait le point de convergence le plus exposé aux changements de signal et d'inventory.

**Action** — Corriger le package-info de signal (ou déplacer la recherche dans un sous-paquet dédié) et consigner en §5.3 que impact porte la recherche plein texte.

### AUD-ARC-12 — Le contrat client.ts est écrit à la main et partiel (8 appels pour 20 ressources REST) ; ses trois fonctions HTTP divergent sur la gestion d'erreur

- **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 5
- **Fusionne** : AUD-QUA-13
- **Bloque** : AUD-ARC-02, AUD-ARC-15, AUD-TST-09

**Preuve**

```text
argos-ui/src/api/client.ts : 8 fonctions exportées pour 18 classes @Path("/api/v1…") côté serveur ; types recopiés « schéma OpenAPI PreferencesEffectives » (l.58) sans génération ; getJson (l.32), putJson (l.83), postJson (l.169) dupliquées, seul putJson lit le corps Erreur (l.96). Chemins utilisés vérifiés présents côté serveur (api/AlertesResource.java:32,45-46,52,60).
[AUD-QUA-13] argos-ui/src/api/client.ts:32-50 getJson, :83-109 putJson, :169-188 postJson (même minuterie, même try/catch) ; seul putJson (:93-100) lit le message d'erreur du serveur. Types : client.ts:150 « interface AlerteVue » et contenu/alertes-poste.ts:22 « interface AlerteRecue » décrivent le même schéma AlerteVue ; « nature: string », « etat: string » au lieu d'unions. Fonction message(e) recopiée dans views/AffichageView.vue:27 et views/AlertesView.vue:36.
[À 9d96406] client.ts : 8 fonctions, inchangé ; 20 classes @Path (ajout de ProfilArtisteResource.java:66 et RubriquesResource.java:27).
```

**Impact** — Une évolution d'un record Java (AlerteVue, PreferencesEffectives) ne casse aucune compilation côté IHM ; dérive silencieuse du contrat dès que l'IHM s'étoffera.

**Action** — Générer les types depuis /q/openapi (openapi-typescript) en build, ou ajouter un test de contrat ; factoriser une seule fonction request() (méthode, corps, lecture d'Erreur, futur jeton OIDC).

### AUD-ARC-14 — Le socle d'architecture repose sur des ADR restés « Proposé », y compris ceux que le code a déjà remplacés

- **Sévérité** : Faible · **Axes** : Architecture · **Effort** : S · **Sprint** : 4
- **Bloque** : AUD-ARC-05

**Preuve**

```text
grep -m1 Statut docs/architecture/adr/0*.md -> 22 ADR sur 29 « Proposé », dont 0001-monolithe-modulaire.md:3 (cité en tête de ModuleBoundariesTest.java:21) et 0016-workspace-rbac-rls.md:3. 0028-collecteur-veille-web-integre.md:6 et 0029-passerelle-ia-ollama-local.md:6 remplacent 0006 et 0004 « à l'acceptation », alors que io.argos.ingestion.web et io.argos.aigateway.ollama sont livrés.
```

**Impact** — Les décisions effectivement en production ne sont pas actées ; ADR-0004 (Claude) et ADR-0006 (FreshRSS séparé) restent formellement en vigueur et contredisent le code, ce qui alimente l'écart documentaire (AUD-ARC-03).

**Action** — Passer en « Accepté » les ADR implémentés (0001, 0013, 0014, 0016, 0027, 0028, 0029…), marquer 0004 et 0006 « Remplacé », et mettre à jour adr/README.md. Rédiger l'ADR du noyau commun (prérequis d'AUD-ARC-05).

### AUD-ARC-15 — Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client

- **Sévérité** : Faible · **Axes** : Architecture, Qualité · **Effort** : M · **Sprint** : 6
- **Dépend de** : AUD-ARC-02, AUD-ARC-12

**Preuve**

```text
git diff --stat f58a68c HEAD : aucun fichier sous argos-ui/ ni argos-desktop/ ; api/artiste/ProfilArtisteResource.java:157-291 (GET listes, GET/PUT suivis, GET opportunites, GET resume, sources, collectes), api/preference/RubriquesResource.java:27,49 ; argos-ui/src/api/client.ts : 8 fonctions, aucune pour /profils/artiste.
```

**Impact** — La fonction n'est utilisable que par l'API avec un jeton : comme ARC-02, aucune preuve de bout en bout. L'écart entre contrat serveur et client (ARC-12) se creuse. Le lien des rappels sans lien officiel vise une page /profils/artiste/opportunites/<id> qui n'existe pas (RappelsOpportunites.java, contenu()).

**Action** — Ajouter les vues artiste (suivis, opportunités) dans argos-ui une fois ARC-02 traité, ou consigner l'écart dans docs/specifications/profil-artiste.md (« Écarts constatés »).

### AUD-QUA-08 — Le typage entre modules repose sur des chaînes et des tableaux : niveau d'impact en String alors que l'énumération existe, String[] comme couple de retour, clé composite découpée par split(":")

- **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 4

**Preuve**

```text
impact/MatchRules.java:23 « public enum Level { EXACT, PROBABLE, A_VERIFIER } » mais impact/ImpactQueries.java:34 « String level » ; alerting/MoteurAlertes.java:191 « !"A_VERIFIER".equals(i.level()) », :233-236 « case "EXACT" -> 0; case "PROBABLE" -> 1; » ; :146 et :262-271 « new String[] {texte, "DISPONIBLE"} » ; :163 « String[] fc = cle.split(":") » ; impact/CorrelationEngine.java:106-121 statuts « "NONE" », « "DONE" », « "UNAVAILABLE" » ; inventory/InventoryService.java:30 « String status » (IMPORTED, REJECTED...).
```

**Impact** — Une faute de frappe ou un nouveau niveau n'est détecté qu'à l'exécution ; le classement rangNiveau() renvoie 2 par défaut pour toute valeur inconnue, ce qui masquerait silencieusement un renommage.

**Action** — Exposer MatchRules.Level (ou un type partagé) dans ImpactView, remplacer String[] par un record ResumeIa(texte, etat) et la clé « fréquence:canal » par un record ; convertir les statuts en enums sérialisés par Jackson.

### AUD-QUA-09 — Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant

- **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 9

**Preuve**

```text
Tables (migrations) : « espace », « appartenance » mais « workspace_vue », « workspace_preference_utilisateur », « subscription », « project_impact » ; colonnes d.workspace_id vs a.espace_id (RadarProjets.java:215, Destinataires.java:41). Classes : impact/ComplementaryAnalysis.java et impact/AnalyseComplementaireIa.java ; ImpactQueries, CorrelationEngine, SyncStore, InventoryService à côté de RechercheVeille, MoteurAlertes, EspacesService ; MoteurAlertes.java:175 « impacts.forSignals(...) » / « abonnements.activeFor(...) ». Aucune convention de langue dans docs/architecture (grep -rli "nommage|langue du code" -> aucun ADR).
```

**Impact** — Recherche dans le code et dans le schéma plus difficile (espace ou workspace ? projet ou project ?), risque de doublons conceptuels, coût d'entrée pour un contributeur.

**Action** — Écrire une courte règle (ADR ou arc42 §8) : domaine en français, termes techniques et API externes en anglais par exemple ; appliquer la règle au nouveau code et renommer progressivement les identifiants ambigus (espace/workspace) lors des migrations déjà prévues.

### AUD-QUA-10 — Horloge injectable seulement dans CollecteWeb et LimiteurDebit : 37 appels à now() côté Java (23 fichiers) sans Clock commun, mêlés à 45 now() SQL, et deux LocalDate.now() sans fuseau

- **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 3

**Preuve**

```text
grep -rnE "(Instant|LocalDate|OffsetDateTime)\.now\(\)" argos-server/src/main/java | wc -l -> 31 (19 fichiers), grep "Clock" -> 0. Décisions métier prises avec l'heure système : workspace/EspacesService.java:254 (expiration d'invitation), inventory/InventoryQueries.java:214 (SBOM périmée), vulnintel/KevSource.java:65 (cache), api/recherche/TraductionCriteres.java:117. catalog/standard/ReferentielEspace.java:138 et mcp/OutilsReferentiel.java:100 « LocalDate.now() » (fuseau JVM) alors que RappelsRadar.java:93 utilise « LocalDate.now(ZoneId.of(fuseau)) » (argos.alerting.fuseau). 42 « now() » SQL (ex. StockActualites.java:88, :250) côtoient Instant.now() Java dans la même mise à jour (StockActualites.java:248-252).
[À 9d96406] 37 now() Java (23 fichiers), 45 now() SQL ; LocalDate.now() sans fuseau inchangés (ReferentielEspace.java:138, OutilsReferentiel.java:100) ; le titre est à corriger (CollecteWeb.java:81).
```

**Impact** — Tests dépendants du temps réel pour ces chemins ; deux horloges (JVM et PostgreSQL) dans une même règle ; la date « du jour » dépend de la variable TZ du conteneur (compose.yaml:54) et non du fuseau configuré. Le schéma déjà présent « point d'entrée planifié -> méthode recevant maintenant » (MoteurAlertes.java:126, RappelsRadar.java:93) limite le problème.

**Action** — Produire un bean java.time.Clock (fuseau argos.alerting.fuseau) et l'injecter à la place de Instant.now()/LocalDate.now() ; passer l'instant en paramètre SQL plutôt que now() là où la règle compare avec une valeur calculée en Java.

### AUD-QUA-11 — Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés

- **Sévérité** : Faible · **Axes** : Qualité · **Effort** : S · **Sprint** : 3

**Preuve**

```text
alerting/MoteurAlertes.java:75 « private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(2, 2, ...) » ; projectintel/SyntheseActivite.java:46 (même motif recopié) ; ingestion/web/RecuperateurWeb.java:51 « Executors.newSingleThreadScheduledExecutor » ; grep -rn "shutdown\|@PreDestroy\|ManagedExecutor" -> aucun résultat.
```

**Impact** — Les fils cœur ne s'arrêtent jamais : fuite de fils à chaque rechargement à chaud en développement ; tâches perdues sans trace à l'arrêt ; contexte CDI et métriques Quarkus absents dans ces fils ; dimensionnement codé en dur et dupliqué.

**Action** — Utiliser un ManagedExecutor (SmallRye Context Propagation) ou un bean @ApplicationScoped qui possède l'exécuteur avec @PreDestroy shutdownNow(), taille configurable ; factoriser le motif « soumettre + get(délai) + annuler » partagé par MoteurAlertes et SyntheseActivite.

### AUD-SEC-04 — Alertes e-mail envoyées à l'adresse du jeton même non vérifiée

- **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 5

**Preuve**

```text
argos-server/src/main/java/io/argos/workspace/ComptesService.java:74 `ps.setString(4, identite.email());` (adresse brute, alors que seule emailVerifieNormalise() sert aux invitations) ; alerting/Destinataires.java:40 `SELECT DISTINCT c.id, c.email FROM appartenance a JOIN compte c ...` utilisé par CanalEmail.
```

**Impact** — Un utilisateur dont l'e-mail n'est pas vérifié par Keycloak (modifiable par l'utilisateur selon la configuration du royaume) peut faire envoyer les alertes de ses espaces (titres, noms de projets) à un tiers : relais de courriels non sollicités au nom du NAS.

**Action** — N'enregistrer comme adresse d'envoi que l'e-mail vérifié (emailVerifieNormalise) et le mettre à jour à la connexion ; ignorer le canal EMAIL tant qu'aucune adresse vérifiée n'est connue.

### AUD-SEC-05 — Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus

- **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 3

**Preuve**

```text
argos-server/src/main/resources/application.properties : `grep -n 'quarkus.http.header' application.properties` ne renvoie rien ; aucune CSP, X-Content-Type-Options, Referrer-Policy, frame-ancestors ni HSTS côté serveur (la CSP n'existe que dans le desktop, argos-desktop/src/ui-protocol.js).
```

**Impact** — L'IHM web n'a pas de défense en profondeur contre l'injection de script ni le clickjacking ; en accès direct (ARGOS_BIND=0.0.0.0) rien n'impose HTTPS. Risque borné : aucun v-html dans l'IHM (voir constat Info).

**Action** — Ajouter via quarkus.http.header.* (ou filtre) : Content-Security-Policy alignée sur celle du desktop, X-Content-Type-Options: nosniff, Referrer-Policy: no-referrer, frame-ancestors 'none' ; HSTS au reverse proxy DSM.

### AUD-SEC-06 — Mot de passe de base de données par défaut « argos » si .env absent

- **Sévérité** : Faible · **Axes** : Sécurité, Dépendances · **Effort** : S · **Sprint** : 2
- **Fusionne** : AUD-DEP-10

**Preuve**

```text
compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` (et :44 pour l'application) ; .env.example:6 valeur d'exemple à changer.
[AUD-DEP-10] compose.yaml:20 `POSTGRES_PASSWORD: ${ARGOS_DB_PASSWORD:-argos}` et :44 idem ; docs/deploiement/synology-et-pc.md:92 « défaut argos ».
```

**Impact** — Si le fichier .env est oublié, la base démarre avec un mot de passe trivial. Risque borné : port publié sur 127.0.0.1 seulement (compose.yaml:23) et réseau interne au projet.

**Action** — Remplacer le repli par `${ARGOS_DB_PASSWORD:?ARGOS_DB_PASSWORD obligatoire}` pour refuser le démarrage sans mot de passe explicite.

### AUD-SEC-07 — Sauvegardes complètes de la base écrites en clair avec les permissions par défaut

- **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 2
- **Dépend de** : AUD-QUA-02

**Preuve**

```text
scripts/sauvegarder-base.sh:19 `mkdir -p "$DEST"` et :22 `pg_dump ... | gzip > "$FILE"` exécutés en root sur le NAS (commentaire l.4), sans umask ni chiffrement ; copie hors NAS recommandée (docs/deploiement/synology-et-pc.md:68).
```

**Impact** — Les dumps (comptes, e-mails, projets, journaux) sont lisibles par d'autres comptes du NAS selon l'umask et voyagent en clair vers le stockage de sauvegarde.

**Action** — `umask 077` en tête du script, dossier en 700 ; chiffrer le dump (age/gpg) ou activer le chiffrement côté Hyper Backup.

### AUD-SEC-09 — Recherche plein texte sans limite de débit

- **Sévérité** : Faible · **Axes** : Sécurité · **Effort** : S · **Sprint** : 5

**Preuve**

```text
argos-server/src/main/java/io/argos/api/recherche/RechercheResource.java:48 `rechercher(...)` et VuesResource `/{id}/resultats` : aucun LimiteurDebit (présent seulement sur veille web, synthèse d'activité et MCP).
```

**Impact** — Un compte peut enchaîner des requêtes plein texte coûteuses sur le processeur du NAS (dégradation pour les autres utilisateurs).

**Action** — Appliquer LimiteurDebit (ex. 60/min/compte) aux routes de recherche, ou une limite globale au reverse proxy.

### AUD-TST-02 — Pas de seuil de couverture : JaCoCo absent du pom, la couverture (83,8 % des lignes) n'est mesurable qu'à la main

- **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01
- **Bloque** : AUD-TST-04

**Preuve**

```text
argos-server/pom.xml:177-185 (seul plugin de test : maven-surefire-plugin, configuration limitée à java.util.logging.manager) ; grep -n jacoco pom.xml -> aucun résultat
```

**Impact** — Aucune alerte si la couverture recule sur un module critique ; la mesure de cet audit a dû être lancée par une commande ajoutée à la main.

**Action** — Ajouter jacoco-maven-plugin (prepare-agent + report, compatible @QuarkusTest via quarkus-jacoco) et un check avec seuils par paquet (au moins api.security, mcp, workspace, alerting) une fois la CI en place.

### AUD-TST-06 — Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s)

- **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 3

**Preuve**

```text
alerting/AlertesTest.java:314-323 (Instant.now() avant/après publier(), assertTrue(attente < 4 s)) avec testsupport/SimulatedResumeIa.java:73 Thread.sleep(5_000) et application.properties:68 %test…resume-ia.delai-max=PT1S ; api/comptes/DefenseEnProfondeurTest.java:158 Thread.sleep(1000); assertFalse(retrait.isDone()) ; signal/web/CollecteWebTest.java:120-125 boucle System.currentTimeMillis()+5000 / Thread.sleep(10)
```

**Impact** — Marge de 3 s sur machine lente ou chargée (runner CI partagé, NAS) : échecs intermittents possibles ; le test de verrou (sleep 1 s) peut passer à vide si l'ordonnancement retarde la soumission.

**Action** — Injecter une horloge/un délai simulé dans la passerelle de résumé (comme CollecteWeb.horloge) au lieu de dormir ; remplacer le sleep de DefenseEnProfondeurTest par une observation du verrou (pg_locks / pg_stat_activity wait_event) ; utiliser Awaitility pour les attentes actives.

### AUD-TST-08 — Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées

- **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 6

**Preuve**

```text
testsupport/TestDb.java:49-56 (TRUNCATE dette_version, … sync_cursor CASCADE) ; comparaison avec les CREATE TABLE des 14 migrations -> non vidées : appartenance, compte, espace, invitation, workspace_preference_utilisateur, ia_prompt (source partiellement : DELETE … WHERE id <> 'OSV')
[À 9d96406] TestDb.java:49-56 inchangé ; les 4 tables de V15 sont vidées hors TestDb par ArtisteFixtures.java:24 (seconde liste codée en dur).
```

**Impact** — Isolation par convention (chaque test crée ses espaces, cf. Javadoc TestDb) ; toute nouvelle table oubliée dans la liste fait fuir de l'état entre classes et rend les résultats dépendants de l'ordre d'exécution.

**Action** — Générer la liste à partir de information_schema (toutes les tables du schéma sauf flyway_schema_history et référentiels explicitement conservés) ou ajouter un test qui échoue si une table n'est ni vidée ni déclarée conservée.

### AUD-TST-10 — Desktop : main.js (260 l.) et preload.js (42 l.), qui portent la configuration de sécurité Electron et l'IPC, ne sont pas testés

- **Sévérité** : Faible · **Axes** : Tests · **Effort** : S · **Sprint** : 5
- **Bloque** : AUD-ARC-02

**Preuve**

```text
argos-desktop/test/*.test.js ne requièrent que ../src/{preferences,notifications,poste,ui-protocol} ; argos-desktop/src/main.js:92-94 (contextIsolation: true, nodeIntegration: false, sandbox: true) non vérifié par un test
```

**Impact** — Une régression sur les webPreferences, la CSP ou la surface exposée par preload (contextBridge) ouvrirait l'exécution de code distant dans le client sans qu'aucun test ne le signale.

**Action** — Extraire la construction des options de BrowserWindow et la liste des API exposées par preload dans des fonctions pures testées (contextIsolation/sandbox à true, nodeIntegration à false, liste blanche des canaux IPC).

### AUD-TST-12 — Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest)

- **Sévérité** : Faible · **Axes** : Tests · **Effort** : M · **Sprint** : 9

**Preuve**

```text
argos-server/pom.xml:28 <skipITs>true</skipITs> ; find . -name "*IT.java" -> aucun résultat ; Dockerfile:22 mvn -B package -DskipTests
```

**Impact** — Le jar/l'image réellement déployés (configuration %prod, Flyway au démarrage, chargement des ressources) ne sont jamais démarrés par un test ; les écarts entre mode test et mode prod ne sont pas détectés avant déploiement.

**Action** — Ajouter un smoke test @QuarkusIntegrationTest (santé, /api/v1/system/info, 401 sans jeton) ou un test compose (docker compose up + curl) dans la future CI.

### AUD-TST-19 — Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image)

- **Sévérité** : Faible · **Axes** : Tests, Dépendances · **Effort** : S · **Sprint** : 1
- **Dépend de** : AUD-TST-01

**Preuve**

```text
Mesure communiquée par le mainteneur (non rejouée ici) : premier `mvn verify` sans clean -> 0 test lancé, « TestEngine archunit failed to discover tests », LimiteDebitMcpTest.class absente de target/test-classes (le source existe : src/test/java/io/argos/mcp/LimiteDebitMcpTest.java:27) ; `mvn clean verify` -> 416 tests, BUILD SUCCESS. pom.xml:16 maven.compiler.release=17 ; Dockerfile:25 eclipse-temurin:17-jre ; aucun maven-enforcer ni toolchains dans pom.xml.
```

**Impact** — Un build local peut échouer ou, pire, passer sur des classes de test périmées. Les tests passent sur JDK 24 alors que l'image tourne sur JRE 17 : une différence de comportement de la JVM (TLS, HttpClient, GC) n'est pas couverte. L'échec observé était bruyant (engine en erreur), donc pas silencieux.

**Action** — En CI : toujours `mvn -B clean verify` sur un JDK 17 (setup-java 17, ou maven-toolchains). Faire écrire l'IDE dans un répertoire distinct de target/ (ex. VS Code Java : bin/). Ajouter maven-enforcer (requireJavaVersion [17,18) en CI).

### AUD-PER-05 — Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant

- **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Preuve**

```text
argos-server/src/main/java/io/argos/impact/RadarProjets.java:252-253 « return inventaires.componentsMatching(parCle.keySet(), Optional.empty()).stream().filter(m -> espace.equals(m.workspaceId())).toList(); » ; :131 boucle par espace ; :148 calendrier.finDeSupport(...) par composant -> argos-server/src/main/java/io/argos/releaseradar/CalendrierEndoflife.java:119 (referentiel.technologies) + :127 (requête cycle_vie), donc 2 requêtes par composant ; :150 enregistrerDette = 1 connexion + 1 INSERT par dette ; argos-server/src/main/java/io/argos/alerting/RappelsRadar.java:114-117 : espace × jours de rappel (jusqu'à 10) × echeancesTouchant, qui rappelle composants().
```

**Impact** — Le coût est en O(espaces × composants de toute la plateforme correspondant aux clés), auquel s'ajoutent 2 requêtes par composant et jusqu'à 10 rescans par espace pour les rappels. Sur le NAS mono-utilisateur, c'est négligeable. En ligne, avec des espaces nombreux suivant les mêmes technologies (spring, node…), la tâche quotidienne grossit de façon quadratique.

**Action** — Ajouter un paramètre workspaceId à componentsMatching (filtre SQL p.workspace_id = ?) ; charger le calendrier (cycles des produits suivis) une fois par espace, puis évaluer en mémoire ; insérer les dettes par lot ; calculer les échéances de tous les jours de rappel en une passe (intervalle [min, max]).

### AUD-PER-07 — Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page

- **Sévérité** : Faible · **Axes** : Performance · **Effort** : M · **Sprint** : 8

**Preuve**

```text
argos-server/src/main/java/io/argos/impact/RechercheVeille.java:136-138 « WITH r AS MATERIALIZED (SELECT * FROM argos_rechercher_veille(...)) SELECT r.type, r.id, r.rang, (SELECT count(*) FROM r) FROM r ORDER BY r.rang DESC, ... LIMIT ? OFFSET ? » ; argos-server/src/main/resources/db/migration/V11__recherche_veille.sql:101-105 (sans mots, la branche signal renvoie tous les signaux, ts_rank sur chaque correspondance) ; docs/architecture/quality/mesure-qs13-recherche.md : « aucun filtre (fil complet) 120 000 … p95 272 ms » et « mot présent dans tous les éléments 120 000 … p95 3 722 ms » (poste de dev, 20 000 signaux).
```

**Impact** — La latence croît avec le nombre de correspondances, pas avec la taille de la page : ts_rank détoaste le vecteur de chaque ligne (toast_tuple_target=256) et le count(*) est exact. La mesure QS-13 a été prise sur un PC de développement avec 20 000 signaux. Sur le Ryzen R1600 du NAS, avec le corpus OSV réel, un mot fréquent ou le fil sans filtre dépassera la cible Q-05 de 500 ms (risque R-16 déjà identifié). OFFSET rend aussi les pages profondes coûteuses.

**Action** — Borner la sélection (par exemple LIMIT 1 000 candidats triés par date avant ts_rank, ou ts_rank seulement si le nombre de correspondances est sous un seuil) ; remplacer le total exact par un total plafonné (« plus de 1 000 ») ; fil sans mots : tri par date avec l'index signal_date/actualite_date et pagination par curseur (keyset) ; refaire la mesure QS-13 sur le NAS avec un corpus OSV réel.

### AUD-PER-09 — Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation

- **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 6

**Preuve**

```text
argos-server/src/main/resources/db/migration/V5__impacts_projet.sql:11 « component_id uuid NOT NULL REFERENCES component (id) ON DELETE CASCADE » (seul index contenant component_id : project_impact_triplet_uk (signal_id, project_id, component_id), en 3e position) ; V12__radar_et_standards.sql:107 (dette_version.component_id, en 2e position dans dette_version_uk) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java:72 « DELETE FROM component WHERE project_id = ? AND import_id <> ? » ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:215 et :234 (WHERE source_id = ? ORDER BY ...) sans index sur ingestion_failure(source_id) ni ops_event(source_id) (V3:55-72) ; sync_attempt(run_id) non indexé (V3:43-52) ; InventoryQueries.java:112 sans index sur inventory_import_failure(project_id).
```

**Impact** — Chaque composant retiré d'un inventaire déclenche une recherche en cascade dans project_impact et dette_version : faute d'index adapté, c'est un parcours séquentiel par ligne supprimée. Une réimportation qui retire beaucoup de composants devient alors quadratique quand project_impact grossit. Les listes d'exploitation font des parcours complets, sans gravité tant que les volumes restent faibles.

**Action** — Migration V15 : CREATE INDEX ON project_impact (component_id) ; ON dette_version (component_id) ; ON ingestion_failure (source_id, received_at) ; ON ops_event (source_id, occurred_at) ; ON sync_attempt (run_id) ; ON inventory_import_failure (project_id, received_at).

### AUD-PER-10 — Listes d'exploitation renvoyées en entier, sans pagination (sources rejetées, événements, échecs d'import)

- **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 6

**Preuve**

```text
argos-server/src/main/java/io/argos/api/SourceOperationsResource.java:40-55 (rejected, events -> List complète) ; argos-server/src/main/java/io/argos/ingestion/SyncStore.java:213-216 et :234 (aucun LIMIT) ; argos-server/src/main/java/io/argos/api/InventoryResource.java:50-52 -> InventoryQueries.java:112 (aucun LIMIT). À l'inverse, recherche (RechercheVeille, TAILLE_MAX=100), impacts (ImpactQueries.java:82 LIMIT/OFFSET), alertes (ConsultationAlertes.java:58 LIMIT) et actualités (ActualitesWeb.java:83 LIMIT) sont bornées.
```

**Impact** — Ces tables ne sont jamais purgées (voir la purge ci-dessus) : la réponse et la mémoire allouée grossissent à chaque synchronisation ou import en échec. Les endpoints sont réservés à l'exploitation, ce qui limite l'exposition.

**Action** — Ajouter limit/offset (ou un curseur sur l'horodatage), avec un maximum de 100 à 500 éléments, et trier du plus récent au plus ancien.

### AUD-PER-11 — Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille

- **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 7

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java:59 « client.send(request, HttpResponse.BodyHandlers.ofString()) » (index modified_id.csv complet de chaque écosystème, OsvAdvisoryFeed.java:60, puis csv.split("\\R") et une liste de FeedEntry) ; argos-server/src/main/java/io/argos/vulnintel/EnrichmentHttp.java:43 (idem, catalogue KEV complet). À l'inverse, ForgeHttp (scm), FournisseurOllama (1 Mio), ClientEndoflife et RecuperateurWeb plafonnent la taille lue.
```

**Impact** — Sous un tas d'environ 768 Mo (mem_limit 1g × MaxRAMPercentage 75), l'index npm complet est conservé à la fois en String, en tableau de lignes et en liste d'entrées. Une réponse anormale (proxy, erreur) pourrait faire monter la mémoire jusqu'à l'OOM, alors que les autres clients HTTP sont bornés.

**Action** — Lire en flux (BodyHandlers.ofInputStream + BufferedReader ligne à ligne) avec un plafond configurable, sur le modèle de ForgeHttp.get(..., tailleMax).

### AUD-PER-12 — Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut

- **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 2

**Preuve**

```text
Dockerfile : « ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" » ; compose.yaml : « mem_limit: ${ARGOS_MEM_LIMIT:-1g} » (argos) et « mem_limit: ${ARGOS_DB_MEM_LIMIT:-512m} » (postgres, sans réglage shared_buffers ni work_mem) ; argos-server/src/main/resources/application.properties : aucune clé quarkus.datasource.jdbc.max-size/min-size/acquisition-timeout ni quarkus.http.enable-compression (grep sans résultat).
```

**Impact** — Il reste environ 256 Mio hors tas pour le metaspace, le code cache, les piles de fils, les tampons directs de Netty et du client HTTP JDK. Un dépassement provoque l'arrêt du conteneur (OOM-kill), sans heap dump ni message JVM. Le pool par défaut (20 connexions), combiné aux connexions tenues pendant les appels distants (verrou consultatif de collecte web : StockActualites.java:127-141, connexion dédiée pendant la récupération HTTP), n'est ni dimensionné ni documenté. Sans compression HTTP, le bundle de 111 kB est servi non compressé hors reverse proxy.

**Action** — JAVA_OPTS : MaxRAMPercentage=60 à 65, -XX:+ExitOnOutOfMemoryError, éventuellement -XX:MaxMetaspaceSize ; expliciter quarkus.datasource.jdbc.max-size (ex. 10 sur le NAS) et acquisition-timeout ; activer quarkus.http.enable-compression=true ; régler shared_buffers (~128 Mo) et work_mem pour la limite de 512 Mo de Postgres.

### AUD-PER-13 — Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels

- **Sévérité** : Faible · **Axes** : Performance · **Effort** : S · **Sprint** : 7

**Preuve**

```text
argos-server/src/main/java/io/argos/projectintel/CollecteActivite.java:81-83 « for (ProjectDirectory.Project projet : projets.withRepository()) { ... store.prochaineCollecte(projet.id()); » (1 requête par projet, alors que l'index activite_collecte_prochaine existe : V14:47) ; argos.scm.activite.pages-max=10 et argos.sources.http-timeout=PT30S (application.properties).
```

**Impact** — Chaque passage horaire charge tous les projets à dépôt et interroge la base pour chacun, puis enchaîne jusqu'à 10 pages × 30 s par dépôt. Le chevauchement est évité (SKIP), mais en ligne la durée d'un passage devient proportionnelle au nombre total de projets.

**Action** — Sélectionner en SQL les seuls projets échus (JOIN activite_collecte WHERE prochaine_collecte <= now() ORDER BY prochaine_collecte LIMIT n), borner la durée d'un passage comme CollecteWeb (duree-max-passage), et paralléliser modérément par forge.

### AUD-DEP-05 — Aucune veille automatisée des dépendances (Renovate/Dependabot, CVE Maven, SBOM du produit) ; Electron déjà en retard d'un correctif (45.3.2, 45.3.3 publié)

- **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 1
- **Fusionne** : AUD-DEP-14

**Preuve**

```text
Absence de .github/dependabot.yml et de renovate.json (find à la racine : aucun) ; argos-server/pom.xml:153-187 sans plugin OWASP dependency-check ni cyclonedx-maven-plugin.
[AUD-DEP-14] argos-desktop/package.json `"electron": "^45.3.2"`, package-lock.json node_modules/electron 45.3.2 ; `npm view electron version` -> 45.3.3. Context7 /websites/electronjs (electron-timelines) : « The latest three stable major versions are supported... We only support the latest minor release for each stable release series ».
```

**Impact** — Les montées de version (Quarkus LTS, Electron, images) et les CVE transitives Maven ne sont détectées que manuellement, alors que le produit est lui-même un outil de veille de vulnérabilités (ADR-0011 SBOM CycloneDX).

**Action** — Ajouter Renovate ou Dependabot (maven, npm x2, docker), générer la SBOM CycloneDX de l'image en CI et l'importer dans Argos lui-même (dogfooding).

### AUD-DEP-06 — Images Docker en tags flottants sans digest : node:22-slim, maven:3.9-eclipse-temurin-17, eclipse-temurin:17-jre, postgres:18

- **Sévérité** : Faible · **Axes** : Dépendances, Tests · **Effort** : S · **Sprint** : 1
- **Fusionne** : AUD-TST-07

**Preuve**

```text
Dockerfile:6 `FROM node:22-slim AS ui`, :16 `FROM maven:3.9-eclipse-temurin-17 AS server`, :25 `FROM eclipse-temurin:17-jre` ; compose.yaml:15 `image: postgres:18`. (Ollama est épinglé : compose.yaml:66 `ollama/ollama:${ARGOS_OLLAMA_VERSION:-0.12.6}`.)
[AUD-TST-07] grep image-name application.properties src/test/resources -> aucun résultat ; seules lignes devservices : application.properties:14 (%dev…enabled=false), 207-211 (realm Keycloak) ; compose.yaml:15 image: postgres:18
```

**Impact** — Build non reproductible : la procédure de mise à jour (doc:80-84, `docker compose up -d --build`) reconstruit avec la base du jour ; une régression d'image amont ou un changement de comportement de PostgreSQL 18.x arrive sans trace ni retour arrière possible vers l'image précédente (image locale `argos:local` écrasée).

**Action** — Épingler chaque FROM et l'image postgres par version précise + digest (`eclipse-temurin:17.0.x_y-jre@sha256:...`), laisser Renovate proposer les mises à jour, et étiqueter l'image argos par version (ARGOS_VERSION) pour permettre le retour arrière.

### AUD-DEP-08 — Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker

- **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 2

**Preuve**

```text
compose.yaml:35-59 (service argos : restart, depends_on, mem_limit mais pas de healthcheck), :65-76 (ollama idem) ; Dockerfile:25-32 sans HEALTHCHECK ; aucune clé `logging:` dans compose.yaml (grep).
```

**Impact** — Un Argos bloqué (JVM vivante mais /q/health/ready en échec) reste « running » pour Container Manager ; les journaux json-file croissent sans limite sur le volume du NAS.

**Action** — Ajouter un healthcheck sur /q/health/ready (outil présent dans l'image ou petite commande Java), `logging: {driver: json-file, options: {max-size: 10m, max-file: '3'}}` sur chaque service.

### AUD-DEP-15 — Client desktop distribué sans signature de code ni mise à jour automatique

- **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : M · **Sprint** : 9

**Preuve**

```text
argos-desktop/package.json, bloc build : win nsis, mac dmg, linux AppImage sans certificateFile/identity/notarize ni publish ; aucune dépendance electron-updater ; grep -iE 'autoUpdater|electron-updater' argos-desktop/src/*.js -> aucun résultat ; docs/deploiement/synology-et-pc.md:101 construction manuelle `npm run dist`.
```

**Impact** — Pour une diffusion « tous publics » : avertissements SmartScreen/Gatekeeper (macOS refuse un dmg non notarisé), authenticité de l'installeur invérifiable, et les postes restent indéfiniment sur le Chromium embarqué de leur installation (failles navigateur non corrigées).

**Action** — Signer (Authenticode, Apple Developer ID + notarisation) dans la CI, publier via electron-builder `publish` et intégrer electron-updater (vérification de signature) ; à défaut, restreindre officiellement le client desktop à l'usage personnel.

### AUD-DEP-19 — Inventaire des licences non automatisé et incomplet pour npm ; pas d'avis tiers (NOTICE) joint à l'image

- **Sévérité** : Faible · **Axes** : Dépendances · **Effort** : S · **Sprint** : 9

**Preuve**

```text
ADR-0021:59 « La licence de chaque dépendance est enregistrée au moment de son introduction » : respecté par commentaires dans pom.xml (l.57-150) mais rien pour argos-ui/argos-desktop ; aucun fichier THIRD-PARTY/NOTICE dans le dépôt ; Dockerfile:30 copie seulement target/quarkus-app.
```

**Impact** — Les obligations d'attribution (MIT/Apache/BSD) ne sont pas matérialisées dans l'image distribuée ou l'installeur ; une dépendance transitive à licence incompatible pourrait entrer sans être vue.

**Action** — Générer en CI un rapport de licences (license-maven-plugin / cyclonedx, `npx license-checker --production`) avec liste d'autorisation conforme à ADR-0021, et l'embarquer dans l'image (THIRD-PARTY-NOTICES).

## Info (34)

### AUD-ARC-01 — Graphe inter-modules acyclique et intégralement conforme à la table ModuleBoundariesTest

- **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
python3 -I /home/claude/audit-tmp/arch/graphe.py argos-server/src/main/java/io/argos -> 44 arêtes, « SCC [] » (aucun cycle) ; compare.py -> « modules ALLOWED: 16 », « hors table: [] ». La seule référence impact->workspace est une javadoc (impact/RlsSession.java:14). Les dépendances déclarées dans les 16 package-info.java sont identiques à ALLOWED (ModuleBoundariesTest.java:30-56).
```

**Impact** — Les frontières déclarées sont respectées par le code ; les sources de vérité code (ALLOWED, package-info, arc42 §5.6) concordent.

**Action** — Aucune ; conserver la règle « table d'abord, puis arc42 » (javadoc du test).

### AUD-ARC-08 — Le module api reste une couche d'exposition mince, sans accès SQL

- **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
grep -rn "DataSource|prepareStatement|createStatement|EntityManager|SELECT " api -> aucun résultat (règle arc42 §5.5-4 respectée). Plus gros contrôleur : api/RadarResource.java (245 lignes) dont la seule orchestration est le calcul d'horizon et la jointure des libellés (RadarResource.java:171-196). Contrôle d'accès en tête de chaque méthode (membre/administrateur, RadarResource.java:230-244).
```

**Impact** — Les règles métier restent dans les modules ; l'API REST et le serveur MCP peuvent partager les mêmes services.

**Action** — Aucune ; optionnellement une règle ArchUnit « api ne dépend pas de javax.sql » pour figer §5.5-4, et déplacer le calcul d'horizon de RadarResource dans releaseradar.

### AUD-ARC-13 — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées

- **Sévérité** : Info · **Axes** : Architecture · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
grep import argos-ui/src/mises-en-page -> uniquement ../../contenu/MentionsObligatoires.vue et le type contenu/modeles ; grep -rln api/client argos-ui/src -> views/AffichageView.vue, views/AlertesView.vue, views/StatusView.vue, contenu/suivi-poste.ts ; test argos-ui/tests/architecture.test.mjs. argos-desktop/src/main.js:72 : seul net.fetch, vers un fichier local (pas d'appel API), conforme à 05-vue-blocs.md:44.
```

**Impact** — La règle « une mise en page n'appelle pas l'API » (arc42 §5.4.1) est tenue et testée.

**Action** — Aucune.

### AUD-QUA-14 — La gestion d'erreurs côté serveur est saine : aucun catch vide, aucun printStackTrace, interruptions rétablies, messages exposés assainis

- **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
grep -rnE "printStackTrace|System\.(out|err)" argos-server/src/main/java -> 0 ; grep -rnE "TODO|FIXME|XXX|HACK" sur argos-server, argos-ui, argos-desktop, scripts -> 0 ; 36 catch (Runtime)Exception, tous placés aux frontières (observateurs, tâches planifiées, boucles par espace/compte) avec LOG.errorf(e, ...) et identifiant, ex. CorrelationEngine.java:79-80, RappelsRadar.java:121-122 ; InterruptedException traitée avec Thread.currentThread().interrupt() en 8 endroits (PasserelleIa.java:129-131, MoteurAlertes.java:264-267, SourceSynchronizer.java:78-79...) ; messages persistés ou renvoyés réduits au nom de classe (SourceSynchronizer.java:86-87, CanalEmail.java:35, EnvoiAlertes.java:146) ; GestionErreurs.java ne mappe que des exceptions de domaine. Journaux : 24 loggers, seuls des identifiants sont journalisés (aucun e-mail, jeton ni prompt trouvé par grep).
```

**Impact** — Robustesse correcte des traitements de fond : une erreur sur un élément n'interrompt pas les autres et laisse une trace exploitable ; pas de fuite de détails techniques vers le client.

**Action** — Conserver ces règles ; les inscrire dans arc42 §8 (gestion des erreurs et journalisation) pour qu'elles survivent aux contributions futures.

### AUD-QUA-15 — Les ressources JDBC sont systématiquement fermées (try-with-resources), y compris le cas particulier du verrou consultatif

- **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
grep -rn "getConnection()" argos-server/src/main/java -> 93 appels, 92 dans un try-with-resources ; le seul autre, signal/web/StockActualites.java:127-156, ferme la connexion dans un finally sauf transfert explicite de propriété à Verrou (AutoCloseable, :57-77) qui libère le verrou et rend la connexion.
```

**Impact** — Pas de fuite de connexions du pool identifiée par lecture.

**Action** — Aucune action ; conserver le motif et le reprendre dans l'utilitaire JDBC proposé par AUD-QUA-06.

### AUD-QUA-16 — Taille et complexité du code maîtrisées ; TypeScript en mode strict sans any ni contournement

- **Sévérité** : Info · **Axes** : Qualité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Plus gros fichier Java : scm/RepositoryActivity.java (394 lignes) ; méthode la plus longue : alerting/ResumesPeriodiques.java:118 composerPour (100 lignes), puis CorrelationEngine.java:142 (80) (script /home/claude/audit-tmp/qual-scripts/longmeth.py). UI : plus gros fichier contenu/alertes-poste.ts (277 lignes), desktop src/main.js (260). argos-ui/tsconfig : « strict: true », « noUnusedLocals », « noUnusedParameters » ; grep -rnE ": any|as any|as unknown|@ts-ignore|eslint-disable" argos-ui/src argos-desktop/src -> 0. Le module pur alertes-poste.ts injecte ses dépendances (DependancesSuivi) et est testable sans DOM.
```

**Impact** — Base lisible et testable ; pas de fichier « dieu ». Seules ResumesPeriodiques.composerPour et CorrelationEngine.deterministic gagneraient à être découpées.

**Action** — Aucune action obligatoire ; découper composerPour (lecture, filtrage RLS, écriture) lors de la prochaine évolution des résumés.

### AUD-SEC-10 — Aucun secret réel versionné ni dans l'historique git

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
`git grep -nIiE '(password|secret|token|api[_-]?key)\s*[:=]'` : seulement des références d'environnement (application.properties:12,51,52,79), les valeurs de développement/test (application.properties:17,209,212 ; src/test/resources/keycloak/argos-realm.json:13,95, comptes de test) et .env.example:6 (exemple) ; `git log --all -p | grep -E 'ghp_|github_pat_|glpat-|AKIA|BEGIN .*PRIVATE KEY|sk-'` : uniquement des jeux d'essai factices des tests du filtre IA. .env et .env locaux ignorés (.gitignore, .dockerignore).
```

**Impact** — Pas de rotation nécessaire. Les secrets de test (secret-de-test, mots de passe des comptes de test) ne doivent jamais servir hors profils %dev/%test.

**Action** — Aucune ; conserver la règle « tout secret par variable d'environnement ».

### AUD-SEC-11 — Requêtes SQL paramétrées ; les seules concaténations portent sur des constantes

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
workspace/EspacesService.java:325 `... WHERE " + filtre` : filtre toujours littéral (appels l.199, 206, 230, 248), valeur liée par setObject ; impact/RechercheVeille.java:136 selection() constante ; V11__recherche_veille.sql:102-129 `RETURN QUERY EXECUTE $q$ ... $q$ USING ...` (texte constant, saisies en paramètres) ; requête utilisateur via websearch_to_tsquery (V11:29).
```

**Impact** — Pas d'injection SQL identifiée dans les *Queries, *Store, RechercheVeille, SubscriptionService.

**Action** — Ne pas transformer lireInvitations(filtre) en API recevant un texte externe ; garder EXECUTE ... USING.

### AUD-SEC-12 — Authentification : jeton OIDC obligatoire partout, audience vérifiée, aucun mode sans authentification

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
application.properties:200-218 (application-type=service, token.audience=argos-server, auth-server-url sans valeur par défaut en prod, /* authenticated) ; seul CallerResolver de production : api/security/OidcCallerResolver.java ; UnauthenticatedCallerResolver supprimé de l'historique ; HeaderCallerResolver uniquement dans src/test ; tests AuthentificationTest (401 sur /api/v1/espaces, /q/openapi).
```

**Impact** — Socle d'authentification sain ; l'administrateur plateforme n'est désigné que par l'e-mail vérifié ARGOS_ADMIN_EMAIL à la création du compte (ComptesService).

**Action** — Aucune ; ne pas ajouter de règle permit élargie (voir AUD-SEC-02).

### AUD-SEC-13 — Autorisation par espace vérifiée dans chaque ressource REST, doublée par la RLS PostgreSQL (pas d'IDOR trouvé)

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Contrôles memberOf/adminOf : RadarResource (membre/administrateur), InventoryResource.authorize, ActiviteResource.autoriser, VeilleWebResource.sourceGeree/exigerAdministrateur, SubscriptionResource (TEAM réservé à l'administrateur), EspacesService.exiger ; alertes filtrées par compte_id (ConsultationAlertes:95,119) ; RLS argos_app NOBYPASSRLS (V2:176) avec politiques V2/V6/V10/V11/V12/V14 ; hors périmètre = 404 journalisé (GestionErreurs). Signaux = données publiques (lecture par tout compte authentifié, voulu).
```

**Impact** — Pas d'accès inter-espaces identifié par remontée des flux.

**Action** — Aucune ; tout nouvel endpoint doit appeler access.* et passer par ContexteBase.

### AUD-SEC-14 — Garde SSRF de la veille web complète hors rebinding (plages, IPv6 embarquées, ports, redirections, tailles)

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
ingestion/web/GardeAdresses.java (plages IPv4/IPv6, NAT64/6to4/mappées, ports 80/443, identifiants refusés, messages sans nom d'hôte) ; RecuperateurWeb.java:100 chaque redirection revérifiée, redirections suivies à la main (Redirect.NEVER), délai et taille bornés ; liens d'articles jamais récupérés ; sources d'avis et forges sans redirection (HttpSourceClient.java:35, ForgeHttp.java:36) et à URL de configuration uniquement ; aucun endpoint de création de projet (ProjectDirectory.create sans appelant REST), dépôt GitHub validé par regex.
```

**Impact** — Protection à conserver ; seul le défaut de AUD-SEC-01 reste ouvert.

**Action** — Ne pas assouplir ; renseigner argos.veille-web.exceptions uniquement en dev/test.

### AUD-SEC-15 — XXE neutralisé dans la lecture RSS/Atom

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
ingestion/web/LecteurFlux.java (ROME 2.1.0, pom.xml:24, DOCTYPE refusé par défaut) ; test LecteursTest.java:69-74 avec `<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>` refusé ; contenu réduit en texte par Jsoup ; aucune autre analyse XML ni désérialisation Java (grep ObjectInputStream/activateDefaultTyping/DocumentBuilderFactory : rien).
```

**Impact** — Pas d'XXE ni de désérialisation dangereuse.

**Action** — Garder le test ; ne pas activer setAllowDoctypes(true).

### AUD-SEC-16 — Serveur MCP : authentification, limitation et balisage du contenu externe en place

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Les 7 outils (OutilsSecurite/Projets/Referentiel/Reglementaires) passent par garde.appeler ; mcp/GardeAppels.java:74 limiteur 60/min/compte ; refus journalisés ; /mcp couvert par la règle /* authenticated ; contenu externe marqué CONTENU_EXTERNE_NON_FIABLE et tronqué (BalisageExterne) ; outils en lecture seule.
```

**Impact** — Surface MCP maîtrisée ; injection indirecte vers l'assistant signalée, pas supprimable.

**Action** — Aucune.

### AUD-SEC-17 — Passerelle IA : données filtrées, sortie validée, fournisseur non pilotable par l'utilisateur

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
aigateway/PasserelleIa.java:99 refus des classes non transmissibles, :105 refus si secret détecté, :160 chevrons neutralisés dans les données ; ValidationSortie par schéma ; URL Ollama de configuration seulement (application.properties argos.ia.ollama.url) ; analyse de corrélation (noms de projets) désactivée par défaut ; Ollama sans port publié (compose.yaml). Sujets d'e-mail nettoyés des CR/LF (CanalEmail.sujet) et e-mail en texte brut.
```

**Impact** — L'injection de prompt par un flux externe ne peut produire qu'un texte affiché comme « produit par l'IA », sans action ni outil.

**Action** — Aucune ; garder l'IA sans outils ni actions.

### AUD-SEC-18 — Client Electron durci

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
argos-desktop/src/main.js:92-94 contextIsolation true, nodeIntegration false, sandbox true ; :107,114 openExternal seulement pour http(s), navigation hors app://argos bloquée ; :205 IPC acceptée seulement depuis la fenêtre principale ; permissions limitées aux notifications ; ui-protocol.js:51 traversée de chemin refusée et CSP stricte (script-src 'self', object-src 'none') ; preload n'expose que des valeurs simples.
```

**Impact** — Pas de vecteur d'exécution de code depuis le contenu affiché.

**Action** — Aucune ; pour le dev, comparer l'origine (new URL(url).origin === DEV_URL) plutôt que startsWith.

### AUD-SEC-19 — IHM Vue sans rendu HTML brut des contenus de veille

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
`grep -rnE 'v-html|innerHTML|eval\(' argos-ui/src` : aucun résultat ; liens externes filtrés par urlSure (argos-ui/src/contenu/liens.ts, http/https seulement) avec rel=noopener noreferrer (MentionsObligatoires.vue:32, AlertesView.vue:76) ; serveur réduit les flux en texte (LecteurFlux.texte).
```

**Impact** — XSS par contenus externes non identifiée.

**Action** — Interdire v-html par règle de lint.

### AUD-SEC-20 — Conteneurs et exposition réseau raisonnables ; CSRF non applicable

- **Sévérité** : Info · **Axes** : Sécurité · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Dockerfile:27-29 utilisateur système 1001 (non root) ; compose.yaml:23 PostgreSQL sur 127.0.0.1, :58 Argos sur 127.0.0.1 par défaut, Ollama sans port ; application.properties:192 CORS limité à app://argos en prod ; authentification par jeton porteur sans cookie (application-type=service) : pas de surface CSRF.
```

**Impact** — Surface réseau minimale par défaut.

**Action** — Aucune.

### AUD-SEC-21 — Delta du lot 4 conforme : RLS sur les suivis, sources réservées à l'administrateur et collectées après vérification des conditions, hôtes data.culture filtrés, liens externes assainis

- **Sévérité** : Info · **Axes** : Sécurité, Architecture · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
V15__profil_artiste.sql : ENABLE ROW LEVEL SECURITY + politiques compte_id = argos_compte_courant() sur suivi_profil et suivi_rappels ; workspace/suivi/SuivisProfil.java:71,80 (ContexteBase) ; ProfilArtisteResource.java:243,259,274,287 access.operator() et :288 limiteur ; CollecteOpportunites.java:112 (conditions vérifiées), :121 (hôte admis), :124 (RecuperateurWeb + GardeAdresses) ; CorrespondanceListes.java:71-80 (http/https sans userinfo) ; ResumesIaActualites.java:87 (contrôle d'accès avant le cache partagé) ; aucune nouvelle arête inter-modules (comparaison des imports f58a68c/HEAD) ; SuiviIsolationTest ; ProfilArtisteTest.java:306-308 (403).
```

**Impact** — Aucun IDOR trouvé sur les nouvelles routes. Les opportunités sont des données publiques non cloisonnées, à dessein (GET /opportunites/{id}/resume accessible à tout compte dont le profil artiste est actif).

**Action** — Aucune.

### AUD-TST-13 — argos-ui : npm test passe (25/25) et la vérification de types vue-tsc passe

- **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
cd build-tests/argos-ui && npm ci && npm test -> « # tests 25 # pass 25 # fail 0 # skipped 0 # duration_ms 524.0763 » (real 3,1 s avec npm ci) ; npm run typecheck (vue-tsc --noEmit) -> code de sortie 0
```

**Impact** — Les tests existants sont sains et rapides ; le test d'architecture IHM (tests/architecture.test.mjs, couche mise en page sans réseau) est exécuté.

**Action** — Aucune ; les brancher dans la CI (AUD-TST-01).

### AUD-TST-14 — argos-desktop : npm test passe (22/22) et check:syntax passe

- **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
cd build-tests/argos-desktop && ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci && npm test -> « # tests 22 # pass 22 # fail 0 # duration_ms 205.05 » ; npm run check:syntax -> OK
```

**Impact** — Les modules purs du client (préférences, notifications, poste, protocole UI/CSP) sont testés et passent.

**Action** — Aucune ; utiliser npm run check dans la CI.

### AUD-TST-15 — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté

- **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
grep @Disabled|@DisabledIf|assume* src/test/java -> seul api/recherche/MesurePerformanceRechercheTest.java:48 @EnabledIfSystemProperty(named = "argos.performance", matches = "true") (campagne de 100 000 actualités, lancement documenté :45) ; grep .only(|.skip(|todo( dans argos-ui/tests et argos-desktop/test -> aucun résultat
```

**Impact** — Pas de dette de tests ignorés ; la mesure de performance Q-05 est à la demande.

**Action** — Aucune ; éventuellement planifier la campagne de performance (job nocturne).

### AUD-TST-16 — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé

- **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
application.properties:20 %test.quarkus.http.test-port=0 ; testsupport/StubServer.java:53 HttpServer.create(new InetSocketAddress("localhost", 0), 0) et Javadoc :22-26 « aucun test n'appelle de service externe ni de modèle réel » ; signal/web/CollecteWebTest.java:146-153 horloge injectée (instance.horloge) ; testsupport/RecordingSleeper.java ; application.properties:83 %test.quarkus.mailer.mock=true, :85 scheduler désactivé ; aucun @TestMethodOrder/@Order
```

**Impact** — Tests reproductibles et parallélisables, sans dépendance réseau ; densité d'assertions correcte (pas d'assertTrue(true), pas de catch vide).

**Action** — Aucune.

### AUD-TST-17 — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux

- **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
mcp : 8 classes @QuarkusTest, 31 tests (AuthentificationMcpTest 5, CloisonnementMcpTest 5, ContenuExterneMcpTest 2, ContratOutilsMcpTest 5, LimiteDebitMcpTest 2, OutilsConsultationMcpTest 7, RechercheMcpTest 3, ReglementaireMcpTest 2) ; api/comptes : 9 classes, 28 tests (Cloisonnement, DefenseEnProfondeur, JournalRefus…) ; alerting : 5 classes, 28 tests ; ingestion/web : 19 tests unitaires (GardeAdressesTest 9 = SSRF) + chain/CollecteTest, ResilienceTest, EnrichmentTest
```

**Impact** — Les zones à risque (cloisonnement par espace et RLS, authentification MCP et flux d'appareil, garde d'adresses SSRF, échec SMTP, IA indisponible) sont couvertes par des scénarios reliés aux exigences.

**Action** — Aucune ; compléter par la matrice d'autorisations (AUD-TST-05).

### AUD-TST-18 — ModuleBoundariesTest couvre les 16 modules et 5 règles fines ; il est éligible à surefire (nom *Test, JUnit 5)

- **Sévérité** : Info · **Axes** : Tests · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
architecture/ModuleBoundariesTest.java:31-57 (16 modules, identiques aux paquets io.argos.*) ; :67-80 @TestFactory un test dynamique par module ; :88-174 mcpNeLitQueLaSecuriteDeLApi, seuleLaPasserelleConnaitLeFournisseur, signal/projectintel/catalogNEmprunteQueRlsEtErreurs ; pom.xml:145-150 archunit-junit5 1.5.1 en scope test ; surefire 3.5.3 sans <includes>/<excludes>
```

**Impact** — Les frontières documentées (arc42 §5) sont vérifiées dès qu'un mvn test est lancé ; exécution effective non constatée ici (build Maven impossible, cf. angles morts) et jamais automatisée (AUD-TST-01).

**Action** — Aucune ; voir AUD-TST-11 pour les modules futurs.

### AUD-PER-14 — Les tâches planifiées sont protégées contre le chevauchement et idempotentes en base

- **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
concurrentExecution = Scheduled.ConcurrentExecution.SKIP sur les 7 @Scheduled (CollecteWeb.java:83-84, CollecteActivite.java:69-70, PurgeActivite.java:32-33, EnvoiAlertes.java:72-73, RappelsRadar.java:90-91, ResumesPeriodiques.java:70-77) ; garde-fous en base : sync_run_one_running (V3:40), alerte_immediate_uk / alerte_resume_uk (V8:36-38), alerte_radar_uk (V12:134), verrou consultatif par source web (StockActualites.java:82), réservation avec bail dans EnvoiAlertes (BAIL = 5 min, LIMIT 500).
```

**Impact** — Pas de double exécution sur une instance. Sur plusieurs instances (service en ligne), les doublons de messages sont déjà évités par les index uniques et les verrous ; seul le travail est fait en double.

**Action** — Aucune action immédiate ; avant de passer à plusieurs instances, ajouter un verrou de cluster (quarkus-quartz en mode JDBC, ou pg_try_advisory_lock en tête de chaque tâche).

### AUD-PER-15 — Les appels IA sont bornés : exécuteurs dédiés à file bornée, délais maximaux, taille de réponse plafonnée, Ollama configuré à un modèle et une requête à la fois

- **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java:75-80 (ThreadPoolExecutor(2, 2, ..., ArrayBlockingQueue<>(16), AbortPolicy)) ; argos-server/src/main/java/io/argos/projectintel/SyntheseActivite.java:46 (1-2 fils, file 8) ; argos-server/src/main/java/io/argos/aigateway/ollama/FournisseurOllama.java:37 (TAILLE_MAX_REPONSE 1 Mio), :60 (connectTimeout ≤ 5 s), :82 (timeout argos.ia.delai=PT60S) ; compose.yaml : OLLAMA_MAX_LOADED_MODELS=1, OLLAMA_NUM_PARALLEL=1, mem_limit 3g ; quotas argos.ia.plafond-instance-jour / quota-espace-jour.
```

**Impact** — L'indisponibilité ou la lenteur du modèle sur le CPU du DS1522+ n'épuise ni les fils ni la mémoire de l'application. Les demandes au-delà de la file passent en « indisponible ».

**Action** — Aucune ; à surveiller : la somme des fils IA (MoteurAlertes 2 + SyntheseActivite 2 + autres) dépasse OLLAMA_NUM_PARALLEL=1, donc les demandes s'empilent côté Ollama jusqu'à leur délai.

### AUD-PER-16 — Clients HTTP sortants : délais de connexion et de requête configurés partout, sans redirection ; caches KEV et CERT-FR avec durée de vie

- **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
argos-server/src/main/java/io/argos/ingestion/HttpSourceClient.java (connectTimeout + timeout = argos.sources.http-timeout PT30S, Redirect.NEVER) ; vulnintel/EnrichmentHttp.java:36 ; scm/ForgeHttp.java:36,46 (+ taille plafonnée, gestion de quota 429/403) ; releaseradar/ClientEndoflife.java:72,82 (PT10S) ; ingestion/web/RecuperateurWeb.java:51,140 (échéance globale imposée par une minuterie, en plus du timeout) ; vulnintel/KevSource.java:64-73 et CertFrSource.java:63-90 (cache synchronisé, argos.sources.certfr.cache-ttl PT6H) ; LimiteurDebit en mémoire avec nettoyage (api/security/LimiteurDebit.java:28-76).
```

**Impact** — Aucun appel distant ne peut bloquer indéfiniment un fil. Les catalogues volumineux (KEV) ne sont pas retéléchargés à chaque signal.

**Action** — Aucune ; noter que le limiteur de débit en mémoire ne vaut que pour une seule instance (à partager, en base ou dans Redis, en cas de mise à l'échelle horizontale).

### AUD-PER-17 — Bundle de l'IHM léger (42 kB gzippé) ; l'import d'inventaire par lot est prêt mais pas encore joignable en production (AUD-QUA-01)

- **Sévérité** : Info · **Axes** : Performance · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Commande : cp -r argos-ui /home/claude/audit-tmp/build-perf/ && npm ci && npx vite build -> « dist/assets/index-DXBFwvE8.js 111.25 kB │ gzip: 41.87 kB », « dist/assets/index-Ds4gtZKj.css 5.18 kB » (2 dépendances d'exécution : vue, vue-router) ; argos-server/src/main/java/io/argos/inventory/InventoryStore.java (INSERT component par addBatch/executeBatch) ; argos.inventory.max-document-bytes=20971520.
```

**Impact** — Le chargement initial reste négligeable, y compris sur le réseau domestique ou en ligne ; l'archive Electron est dominée par le runtime Electron, pas par l'IHM.

**Action** — Aucune ; découper par route (import() dynamique) seulement si le bundle dépasse ~300 kB.

### AUD-DEP-11 — npm audit argos-desktop : 6 vulnérabilités modérées (minimatch GHSA-g3hv-75wg-m249, déni de service) via electron-builder, outil de build uniquement

- **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Copie dans /home/claude/audit-tmp/build-deps/argos-desktop, `npm audit --json` -> {moderate: 6, high: 0, critical: 0} ; chaîne : electron-builder 26.18.0 -> app-builder-lib -> @electron/universal -> minimatch 9.0.9 (plage vulnérable >=7.0.0 <11.0.1) ; fixAvailable proposé = electron-builder 24.13.3 (rétrogradation majeure, non pertinente).
```

**Impact** — Aucun impact en exécution : electron-builder ne sert qu'à l'empaquetage local sur un poste de confiance ; la DoS suppose des motifs glob malveillants.

**Action** — Ne pas appliquer `npm audit fix --force` ; suivre la prochaine version d'electron-builder et accepter le risque explicitement (documenté) d'ici là.

### AUD-DEP-12 — argos-ui : aucune vulnérabilité connue et dépendances à jour (Vue 3.5.45, Vite 8.3.8, vue-router 5.5.1), lockfiles v3 présents et utilisés par npm ci

- **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Copie /home/claude/audit-tmp/build-deps/argos-ui, `npm audit --json` -> total 0 ; `npm view <pkg> version` : vite 8.3.8, vue 3.5.45, @vitejs/plugin-vue 6.0.11, vue-tsc 3.3.14 = versions résolues dans package-lock.json (lockfileVersion 3) ; Dockerfile:7-8 `COPY ... package-lock.json` + `npm ci`. Seul écart : typescript ~6.0.3 alors que 7.0.2 est publié (choix délibéré par le tilde).
```

**Impact** — Chaîne front reproductible et saine.

**Action** — Aucune ; planifier la montée TypeScript 7 quand vue-tsc la prend en charge.

### AUD-DEP-13 — Quarkus 3.40.1.2 sur le flux LTS courant (3.40) ; Java 17 compatible jusqu'à la fin des 3.x

- **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
argos-server/pom.xml:14-21 `<quarkus.platform.version>3.40.1.2</quarkus.platform.version>` (commentaire : LTS jusqu'au 30/09/2027). Context7 /quarkusio/quarkusio.github.io, _data/versions.yaml : version courante 3.40.1.1, flux maintenus « 3.40, 3.33, 3.27, ... », jdk « 17+ » ; politique LTS : support 12 mois, nouvelle LTS tous les 6 mois.
```

**Impact** — Plateforme serveur à jour ; la sortie de Quarkus 4 imposera Java 21 (Dockerfile:16 et :25 à faire évoluer).

**Action** — Aucune ; anticiper le passage à Java 21 (images temurin-21) avant la fin de support de la LTS 3.40.

### AUD-DEP-16 — Conteneurisation et assemblage Compose solides : multi-étapes, JRE seul, utilisateur non root, mem_limit, restart, healthcheck PostgreSQL, ports locaux par défaut

- **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
Dockerfile:6/16/25 trois étapes, :27-29 `useradd --system --uid 1001` + `USER 1001` ; .dockerignore exclut **/.env ; compose.yaml:16/37/68 `restart: unless-stopped`, :27/59/76 mem_limit, :28-33 healthcheck pg_isready + :38-40 depends_on service_healthy, :22-23 et :55-58 publication sur 127.0.0.1 ; ollama sans port publié et profil opt-in. `docker compose -f compose.yaml config -q` -> rc=0.
```

**Impact** — Base d'exploitation adaptée au NAS (mémoire bornée, surface réseau minimale).

**Action** — Aucune.

### AUD-DEP-17 — Configuration par profils correcte : prod sans valeurs par défaut pour la base et l'OIDC, mailer simulé seulement en dev/test, STARTTLS exigé, aucun secret réel versionné

- **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
application.properties:10-12 `%prod.quarkus.datasource.*=${ARGOS_DB_*}` sans défaut, :203 `%prod.quarkus.oidc.auth-server-url=${ARGOS_OIDC_URL}`, :80-83 mock=false en prod, :75 `start-tls=${ARGOS_SMTP_STARTTLS:REQUIRED}`, :209/:212 secret client limité à %test/%dev (valeur de test, non reproduite) ; recherche de motifs de secrets (ghp_, glpat-, AKIA, clés privées) hors tests : seules les expressions de filtrage de FiltreDonnees.java:32-33 ; .gitignore exclut .env et sauvegardes/.
```

**Impact** — Démarrage en échec franc si la base ou l'OIDC manque ; pas de fuite de secret dans le dépôt. Aucun pipeline, donc aucun secret de pipeline à gérer.

**Action** — Aucune.

### AUD-DEP-18 — Licences des dépendances livrées compatibles avec ADR-0021 (MIT/Apache-2.0/BSD) ; licences restrictives uniquement dans l'outillage de build

- **Sévérité** : Info · **Axes** : Dépendances · **Effort** : S · **Hors sprint** (Info)

**Preuve**

```text
LICENSE : PolyForm Internal Use 1.0.0 ; ADR docs/architecture/adr/0021-double-licence.md:57 (permissives acceptées). Analyse du package-lock.json : bundle argos-ui = vue, vue-router (MIT) ; MPL-2.0 (lightningcss 1.33.0), BSD-3, ISC, 0BSD côté Vite ; argos-desktop : MIT 213, ISC 35, BlueOak-1.0.0 9, Python-2.0 1 (argparse), WTFPL 1, tous dans la chaîne electron-builder ; pom.xml:44-150 commente la licence de chaque dépendance (Apache-2.0, MIT, JDOM).
```

**Impact** — Aucune dépendance copyleft forte ni non commerciale n'entre dans le produit distribué.

**Action** — Aucune.
