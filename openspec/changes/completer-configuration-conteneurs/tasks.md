# Tâches : compléter la configuration des conteneurs

<!--
Sprint 3 de l'audit du 2026-11-21 (/docs/audit/2026-11-21/sprints.md), hors AUD-SEC-01 (changement epingler-adresse-veille-web). Ordre interne : AUD-DEP-01 → AUD-DEP-27 → AUD-DEP-08 → AUD-PER-12 → AUD-PER-28 → AUD-SEC-25.
Prérequis : fiabiliser-sauvegarde-et-roles-base (capacité exploitation-plateforme, fiche, compose.yaml et .env.example à jour).
-->

## 1. Configuration transmise et gardée

- [x] 1.1 Écrire le test de parité : `${ARGOS_*}` lus par `application.properties` en production ou sans profil, comparés au bloc `environment` du service `argos` et à `.env.example` ; variables interpolées par `compose.yaml` comparées à `.env.example` ; échec nommant la variable et le fichier (design.md D1) — couvre EXP-07-S2, EXP-07-S3 (AUD-DEP-01, AUD-DEP-27) — vérification : le test échoue sur l'état actuel, puis réussit après 1.2
- [x] 1.2 Transmettre au service `argos` toutes les variables lues en production (SMTP, `ARGOS_PUBLIC_URL`, jetons de forge, audience OIDC, quotas IA, planifications) et les compléter dans `.env.example`, avec les variables Keycloak et Ollama manquantes — couvre EXP-07-S1, EXP-07-S3 (AUD-DEP-01) — vérification : test de 1.1 vert ; e-mail de test reçu par un serveur SMTP local, lien sur l'adresse publique configurée
- [x] 1.3 Rendre obligatoires `ARGOS_ADMIN_EMAIL`, `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL` et `ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE` dans le service `keycloak` (design.md D2) et documenter dans le guide que l'import du royaume n'a lieu qu'une fois — couvre EXP-08-S1, EXP-08-S2 (AUD-DEP-27) — vérification : démarrage de `keycloak` refusé sans les variables, en les nommant, royaume non importé (Compose interpole aussi les profils inactifs : garde dans la commande d'entrée, design.md D2) ; `docker compose config` sans profil réussit

## 2. Santé, journaux et mémoire

- [x] 2.1 Ajouter les contrôles de santé de `argos` (`/q/health/ready`) et `ollama`, et la rotation des journaux sur chaque service (design.md D3, DC-1) — couvre EXP-09-S1, EXP-09-S2, EXP-09-S3 (AUD-DEP-08) — vérification : `docker inspect` : `healthy`, puis `unhealthy` base arrêtée ; configuration de journalisation présente sur chaque service
- [x] 2.2 Ajouter `-XX:+ExitOnOutOfMemoryError` et réduire la fraction de tas dans `JAVA_OPTS` ; activer `quarkus.http.enable-compression` (design.md D4) — couvre EXP-10-S1, EXP-10-S2, EXP-10-S3 (AUD-PER-12) — vérification : test d'intégration avec et sans `Accept-Encoding: gzip` ; arrêt et redémarrage observés sous un plafond réduit
- [x] 2.3 Expliciter `quarkus.datasource.jdbc.max-size` et `acquisition-timeout`, `KC_DB_POOL_MAX_SIZE`, et la commande de `postgres` (`max_connections`, `shared_buffers`, `work_mem`) ; écrire le tableau de dimensionnement NAS du guide, défaut d'Ollama selon Q-1 (design.md D5) — couvre EXP-11-S1, EXP-11-S2 (AUD-PER-28, AUD-PER-12) — vérification : somme des plafonds ≤ 5 Go avec `ia` et `idp-local` ; `SHOW max_connections` supérieur à la somme des pools ; `docker stats` relevé sur le NAS

## 3. Durcissement du conteneur applicatif

- [x] 3.1 Copier `quarkus-app` sans `--chown` dans le `Dockerfile` ; ajouter au service `argos` `read_only: true`, `tmpfs: /tmp`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]` (design.md D6) — couvre EXP-12-S1, EXP-12-S2, EXP-12-S3 (AUD-SEC-25) — vérification : écriture refusée dans `/opt/argos` et hors `/tmp`, acceptée dans `/tmp` ; `docker inspect` montre `ReadonlyRootfs`, `CapDrop` et `SecurityOpt`
- [x] 3.2 Construire l'image et vérifier la conformité des licences, inchangée — vérification : `docker compose build` puis démarrage complet de la pile, `mvn -B -P licences validate` réussit

## 4. Documentation durable

- [x] 4.1 Compléter la fiche `/docs/specifications/exploitation-plateforme.md` (EXP-07 à EXP-12 citées par identifiant, statuts [PROPOSITION] avec l'identifiant du changement, questions Q-1 à Q-4) et la colonne Changement du registre `/docs/specifications/README.md` si besoin — couvre EXP-07 à EXP-12 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.2 Mettre à jour `/docs/deploiement/synology-et-pc.md` (variables, profil `idp-local`, dimensionnement NAS, contrôles de santé), arc42 §7.3 et §8.12, le diagramme D-11 et l'inventaire `/docs/architecture/diagrams/README.md` — couvre EXP-07, EXP-08, EXP-09, EXP-11 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [ ] 5.1 Démarrer la pile complète (`ia` et `idp-local`) sur le PC puis sur le NAS : tous les services `healthy`, Argos joignable, connexion OIDC réussie — couvre EXP-09, EXP-11, EXP-12 — vérification : relevé `docker compose ps` et `docker stats` consigné sous « Preuves »
- [x] 5.2 Valider le changement — vérification : `openspec validate completer-configuration-conteneurs --strict` et `node scripts/verifier-docs.mjs` réussissent

## Matrice de traçabilité

| Exigence | Scénario | Constat | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|---|
| EXP-07 | EXP-07-S1 | AUD-DEP-01 | design.md D1 | 1.2 | variables SMTP et adresse publique relevées dans le conteneur ; e-mail réel non envoyé | partielle (PC) |
| EXP-07 | EXP-07-S2 | AUD-DEP-01, AUD-DEP-27 | design.md D1, INV-1 | 1.1 | `scripts/verifier-configuration.test.mjs` (variable ajoutée, absente du modèle) ; échec du contrôle sur l'état initial | exécuté, vert |
| EXP-07 | EXP-07-S3 | AUD-DEP-01 | design.md D1 | 1.1, 1.2 | `scripts/verifier-configuration.test.mjs` (fichiers du dépôt) | exécuté, vert |
| EXP-08 | EXP-08-S1 | AUD-DEP-27 | design.md D2 | 1.3 | démarrage de `keycloak` sans `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL` : message nommant la variable, base `keycloak` vide | exécuté (PC) |
| EXP-08 | EXP-08-S2 | AUD-DEP-27 | design.md D2 | 1.3 | `docker compose config` sans profil ni variable `idp-local` | exécuté (PC) |
| EXP-09 | EXP-09-S1 | AUD-DEP-08 | design.md D3, DC-1 | 2.1, 5.1 | `docker compose ps` : quatre services `healthy` | exécuté (PC) ; NAS non exécuté |
| EXP-09 | EXP-09-S2 | AUD-DEP-08 | design.md D3 | 2.1 | `argos` `unhealthy` 73 s après l'arrêt de `postgres`, `healthy` après son redémarrage | exécuté (PC) |
| EXP-09 | EXP-09-S3 | AUD-DEP-08 | design.md D3 | 2.1 | `docker inspect` : `json-file`, `max-size` 10m, `max-file` 3 sur les quatre services | exécuté (PC) |
| EXP-10 | EXP-10-S1 | AUD-PER-12 | design.md D4 | 2.2 | image lancée avec `-Xmx10m` : « Terminating due to java.lang.OutOfMemoryError », six redémarrages | exécuté (PC) |
| EXP-10 | EXP-10-S2 | AUD-PER-12 | design.md D4 | 2.2 | `CompressionReponsesTest` ; bundle de l'image : 68 292 octets gzip | exécuté, vert |
| EXP-10 | EXP-10-S3 | AUD-PER-12 | design.md D4 | 2.2 | `CompressionReponsesTest` ; bundle de l'image : 201 740 octets sans `Accept-Encoding` | exécuté, vert |
| EXP-11 | EXP-11-S1 | AUD-PER-28 | design.md D5, DC-1 | 2.3, 5.1 | `docker compose config` : 4,75 Gio cumulés ; tableau du guide ; `docker stats` sur le PC | exécuté (PC) ; NAS non exécuté |
| EXP-11 | EXP-11-S2 | AUD-PER-28, AUD-PER-12 | design.md D5, DC-1 | 2.3 | `SHOW max_connections` : 40 ; pools 10 + 10 | exécuté (PC) |
| EXP-12 | EXP-12-S1 | AUD-SEC-25 | design.md D6 | 3.1 | `touch quarkus-run.jar`, `touch lib/nouveau` refusés ; fichiers propriété de root | exécuté (PC) |
| EXP-12 | EXP-12-S2 | AUD-SEC-25 | design.md D6 | 3.1 | `touch /tmp/essai` accepté, `touch /opt/essai` refusé | exécuté (PC) |
| EXP-12 | EXP-12-S3 | AUD-SEC-25 | design.md D6 | 3.1 | `docker inspect` : `ReadonlyRootfs=true`, `CapDrop=[ALL]`, `SecurityOpt=[no-new-privileges:true]` | exécuté (PC) |

## Preuves

Pile complète (`ia` et `idp-local`) démarrée le 2026-11-24 sur un PC Windows (Docker Compose 5.7.0), sous un nom de projet de test isolé, image construite depuis cette branche :

- `docker compose ps` : `postgres`, `keycloak`, `argos` et `ollama` `healthy` ; IHM servie (200), API protégée (401 sans jeton), `/q/health/ready` `UP` ;
- `docker stats`, pile au repos : `argos` 315,7 Mio / 1 Gio, `keycloak` 614,2 Mio / 768 Mio, `ollama` 10,4 Mio / 2,5 Gio (modèle non chargé), `postgres` 80,5 Mio / 512 Mio ;
- `SHOW max_connections` : 40 ; 8 connexions clientes au repos ;
- non-régression : campagne E2E locale (`npm --prefix e2e test`, JDK 17), 34 parcours sur 34 verts (préparation, MCP, portail web, desktop) ;
- non exécuté : la même pile sur le NAS, une connexion OIDC complète (les adresses de retour du client `argos-ui` visent le port 8088 du poste du porteur), la réception d'un e-mail par un relais SMTP.

## Workflow follow-up

- Basculer dans la fiche `exploitation-plateforme` les statuts [PROPOSITION] de EXP-07 à EXP-12 en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue, et après l'archivage de `fiabiliser-sauvegarde-et-roles-base` (`openspec archive completer-configuration-conteneurs`) : archivé seul, ce delta ADDED créerait une spec principale sans Purpose.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/completer-configuration-conteneurs/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
