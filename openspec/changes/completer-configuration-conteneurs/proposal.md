# Proposition : compléter la configuration des conteneurs

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

L'assemblage Docker Compose sert à la fois le PC et le Synology [DÉCISION — ADR-0026, Accepté], mais l'audit du 2026-11-21 (révision `b142695`, sprint 3 de `/docs/audit/2026-11-21/sprints.md`) montre qu'il n'est pas exploitable tel quel [EXISTANT — `/docs/audit/2026-11-21/findings.md`] :

- AUD-DEP-01 : `compose.yaml` ne transmet pas au conteneur 28 des 37 variables lues en production, et 27 manquent dans `.env.example` ; les e-mails partent vers `localhost:587` et leurs liens vers `http://localhost:5173` ;
- AUD-DEP-27 : le profil `idp-local` démarre avec des mots de passe d'administration et initial vides, et l'import du royaume n'est jamais rejoué ;
- AUD-DEP-08 : `argos` et `ollama` n'ont pas de contrôle de santé, l'image n'a pas de `HEALTHCHECK`, les journaux Docker ne tournent pas ;
- AUD-PER-12 : la JVM prend 75 % d'une limite de 1 Gio sans s'arrêter sur `OutOfMemoryError`, le pool de connexions et la compression HTTP sont laissés par défaut ;
- AUD-PER-28 : Keycloak ajoute 1 Gio et partage un PostgreSQL de 512 Mio non réglé ; les plafonds cumulés atteignent 5,5 Gio sur un NAS de 8 Go ;
- AUD-SEC-25 : le code de l'application appartient à l'utilisateur d'exécution et les conteneurs n'ont ni système de fichiers en lecture seule, ni `cap_drop`, ni `no-new-privileges`.

AUD-SEC-01 (rebinding DNS de la veille web), aussi au sprint 3, relève du changement `epingler-adresse-veille-web`.

## What Changes — Changements

- Toute variable lue en production atteint le conteneur et figure dans `.env.example` ; un test échoue sur tout écart (EXP-07).
- Le profil `idp-local` refuse de démarrer sans ses variables obligatoires (EXP-08).
- Chaque service a un contrôle de santé et des journaux bornés (EXP-09).
- Le serveur s'arrête sur saturation mémoire et compresse les ressources textuelles (EXP-10).
- Un dimensionnement NAS borne la mémoire et les connexions (EXP-11).
- Le conteneur applicatif est durci (EXP-12).
- **BREAKING** (exploitation) : `docker compose --profile idp-local up` échoue sans `ARGOS_ADMIN_EMAIL`, `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL` ni `ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE`.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune : la capacité `exploitation-plateforme` (préfixe `EXP`) est créée par `fiabiliser-sauvegarde-et-roles-base`.

### Modified Capabilities — Capacités modifiées

- `exploitation-plateforme` : exigences ajoutées EXP-07 à EXP-12. Delta ADDED, sans Purpose ; la spec principale n'existe qu'après l'archivage de `fiabiliser-sauvegarde-et-roles-base`.

## Périmètre

### Inclus

- `compose.yaml`, `.env.example`, `Dockerfile`, `argos-server/src/main/resources/application.properties` (pool, compression).
- Un test de parité des variables (application, Compose, `.env.example`).
- Le guide `/docs/deploiement/synology-et-pc.md` (variables, profil `idp-local`, dimensionnement NAS).

### Exclu

- AUD-SEC-01 : changement `epingler-adresse-veille-web`.
- Les contrôles de santé métier (ingestion, e-mail, IA) : changement `instrumenter-observabilite` ; ici, le contrôle de santé du conteneur s'appuie sur la disponibilité existante.
- Les mots de passe de base et les comptes PostgreSQL : changement `fiabiliser-sauvegarde-et-roles-base` (EXP-05, EXP-06).
- La réécriture complète du guide de déploiement (AUD-DOC-03) : seules les sections touchées sont mises à jour.
- Le durcissement des conteneurs `postgres`, `keycloak` et `ollama` au-delà du contrôle de santé et des journaux : [QUESTION] Q-3.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Exploitant (porteur sur le NAS) | renseigne `.env`, choisit les profils, lit l'état des conteneurs dans Container Manager | [EXISTANT] — `/docs/deploiement/synology-et-pc.md` |
| Utilisateur d'Argos | reçoit des e-mails dont les liens pointent vers l'adresse publique | [PROPOSITION] — EXP-07 |
| Porteur du projet | tranche Q-1 à Q-4 | [QUESTION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Bloc `environment` du service `argos` limité à 10 clés | [EXISTANT] | `compose.yaml:89-102` (relu à `b142695`) |
| Variables lues avec défauts de développement (SMTP, adresse publique, jetons, audience) | [EXISTANT] | `application.properties:58-59`, `:63`, `:78-86`, `:276` |
| Variables du profil `idp-local` vides par défaut, commentées dans `.env.example` | [EXISTANT] | `compose.yaml:57-60` ; `.env.example:25-27` |
| Contrôles de santé : `postgres` et `keycloak` seulement ; aucune clé `logging:` | [EXISTANT] | `compose.yaml:30-34`, `:66-77` ; `argos` `:79-107`, `ollama` `:113-124` |
| `JAVA_OPTS` à `MaxRAMPercentage=75` sans `ExitOnOutOfMemoryError`, pas de `HEALTHCHECK` | [EXISTANT] | `Dockerfile:53`, `:45-54` |
| Aucune clé de pool ni de compression | [EXISTANT] | `application.properties` : aucune clé `quarkus.datasource.jdbc.max-size` ni `quarkus.http.enable-compression` (recherche à `b142695`) |
| Plafonds mémoire : postgres 512m, keycloak 1g, argos 1g, ollama 3g | [EXISTANT] | `compose.yaml:29`, `:65`, `:107`, `:124` |
| Code applicatif copié avec `--chown=1001:0`, puis `USER 1001` | [EXISTANT] | `Dockerfile:48`, `:51` |
| DS1522+ livré avec 8 Go | [DÉCISION] | ADR-0026 (Accepté), « Contexte » |
| Profil NAS laissant au moins 3 Go au DSM, pools sous `max_connections` | [PROPOSITION] | critère de sortie du sprint 3, `/docs/audit/2026-11-21/sprints.md` |
| Rotation `max-size: 10m`, `max-file: 3` | [PROPOSITION] | action d'AUD-DEP-08 |
| Import du royaume ignoré si le royaume existe | [EXISTANT] (documentation Keycloak citée par l'audit) | AUD-DEP-27 |

## Écarts constatés

- Comparaison des preuves de l'audit avec `b142695` : identiques. L'audit cite `compose.yaml:58-60` pour le profil `idp-local` ; `KC_BOOTSTRAP_ADMIN_USERNAME` (défaut `admin`) est en `:57`.
- Le guide annonce « Deux conteneurs démarrent » et un tableau de réglages sans SMTP ni adresse publique (AUD-DEP-01, AUD-DOC-03) ; ce changement ne corrige que les sections qu'il touche.
- `ARGOS_IA_PLAFOND_JOUR`, `ARGOS_IA_QUOTA_ESPACE_JOUR` et `ARGOS_IA_QUOTA_COMPTE_JOUR` sont documentées (`.env.example:44-46`) mais non transmises.

## Impact

- **Composants** (noms de arc42 §5) : Quarkus Backend (configuration, compression, pool) ; PostgreSQL (réglages mémoire et connexions) ; Keycloak auto-hébergé (garde des variables, pool) ; Ollama (contrôle de santé, journaux).
- **Contrats d'interface** : aucun contrat d'API ; réponses HTTP compressées si le client l'annonce (en-tête `Content-Encoding`), sans changement de contenu.
- **Données et persistance** : aucune.
- **Sécurité, confidentialité, droits** : durcissement du conteneur applicatif ; secrets SMTP et jetons de forge transmis depuis `.env` (jamais versionnés).
- **Exploitation et observabilité** : état `healthy` lisible dans Container Manager ; `depends_on: service_healthy` possible pour un futur service ; journaux bornés.
- **Documentation durable à mettre à jour** : fiche `/docs/specifications/exploitation-plateforme.md` ; `/docs/deploiement/synology-et-pc.md` ; arc42 §7.3, §8.12 ; D-11 et son inventaire.
- **ADR** : ADR-0026 (Accepté), ADR-0027 (Accepté), ADR-0029 (Ollama). Pas de nouvel ADR attendu ; le dimensionnement NAS par défaut (IA active ou non) peut le devenir si le porteur le juge structurant (Q-1).
- **Dépendances entre changements** : dépend de `fiabiliser-sauvegarde-et-roles-base` (création de la capacité `exploitation-plateforme` et de sa fiche ; mêmes fichiers `compose.yaml` et `.env.example`, à reprendre après lui). `instrumenter-observabilite` réutilise les contrôles de santé posés ici.

## Conception requise

allégée — configuration d'exploitation sans modèle de données ni contrat d'API ; quelques choix de valeurs à justifier (mémoire, pool, durcissement).

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Profil NAS par défaut : IA désactivée, ou plafond d'Ollama réduit, pour laisser au moins 3 Go au DSM avec `ia` et `idp-local` ? | non : EXP-11 fixe la marge, pas le moyen ; répondue le 2026-11-24 : plafond de 2,5 Gio, IA désactivée par défaut [DÉCISION] | porteur du projet |
| Q-2 | Valeurs retenues pour `quarkus.datasource.jdbc.max-size`, `KC_DB_POOL_MAX_SIZE`, `max_connections`, `shared_buffers`, `work_mem` et la fraction de tas (l'audit suggère 10, 10, ~128 Mo et 60 à 65 %) : à mesurer sur le NAS ? | non : EXP-11 impose l'inégalité, la valeur se règle | exploitant |
| Q-3 | Le durcissement (lecture seule, `cap_drop`, `no-new-privileges`) s'étend-il à `keycloak`, `postgres` et `ollama` ? | non ; répondue le 2026-11-24 : changement dédié [DÉCISION] | porteur du projet |
| Q-4 | Keycloak est-il construit en image optimisée (`kc.sh build`, `--optimized`) pour accélérer son démarrage sur le Ryzen R1600 (non mesuré) ? | non | porteur du projet |
