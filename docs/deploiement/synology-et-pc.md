# Déployer Argos sur le Synology ou sur un PC

Argos s'installe de la même façon sur le NAS Synology DS1522+ et sur un PC : avec Docker Compose, à partir du fichier [`compose.yaml`](../../compose.yaml) à la racine du dépôt. Décision : [ADR-0026](../architecture/adr/0026-hebergement-synology.md).

Deux conteneurs démarrent :

| Conteneur | Rôle | Mémoire maximale par défaut |
|---|---|---|
| `postgres` | base de données PostgreSQL 18, données dans le volume `argos-pgdata` | 512 Mo |
| `argos` | IHM web + API Quarkus, sur le port 8080 | 1 Go |

L'image `argos` est construite à partir du [`Dockerfile`](../../Dockerfile) : elle compile l'IHM, l'embarque dans le serveur, contrôle les licences des dépendances du serveur et ne garde qu'un JRE 17 et les avis de licence ([Avis de licence de l'image](#avis-de-licence-de-limage)). Elle cible les processeurs x86-64, ce qui couvre le DS1522+ (AMD Ryzen R1600) et les PC.

## Sur un PC (Windows, macOS, Linux)

Prérequis : Docker Desktop (ou Docker Engine avec le plugin Compose) et Git.

```bash
git clone <adresse-du-dépôt> argos-tech-platform
cd argos-tech-platform
cp .env.example .env          # puis adapter si besoin
docker compose up -d --build
```

Argos répond sur <http://localhost:8080>. Pour l'arrêter : `docker compose down` (les données restent dans le volume).

Pour développer, ne lancez que la base (`docker compose up -d postgres`) et suivez la section « Démarrage local » du [README](../../README.md).

## Sur le Synology DS1522+

Prérequis : DSM 7.2 ou plus récent, paquet **Container Manager** installé depuis le Centre de paquets.

### 1. Copier le projet sur le NAS

Créez le dossier partagé `docker` s'il n'existe pas, puis copiez le dépôt dans `/volume1/docker/argos` (File Station, partage réseau ou `git clone` en SSH).

Dans ce dossier, copiez `.env.example` en `.env` et changez au minimum `ARGOS_DB_PASSWORD`.

### 2. Créer le projet dans Container Manager

1. Container Manager › **Projet** › **Créer**.
2. Nom : `argos` ; chemin : `/volume1/docker/argos` ; source : utiliser le `compose.yaml` existant.
3. Lancez la création : l'image `argos` est construite sur le NAS, puis les deux conteneurs démarrent.

La première construction télécharge les images de base et les dépendances : comptez plusieurs minutes. Elle peut aussi se faire en SSH, dans `/volume1/docker/argos` : `sudo docker compose up -d --build`.

**À vérifier sur votre NAS** : si Container Manager refuse de construire l'image, construisez-la en SSH avec la commande ci-dessus, puis gérez le projet depuis Container Manager.

### 3. Accéder à Argos

Par défaut, Argos n'écoute que sur le NAS lui-même (`ARGOS_BIND=127.0.0.1`) : rien n'est exposé.

| Usage | Réglage |
|---|---|
| Depuis le réseau local | `ARGOS_BIND=0.0.0.0` dans `.env`, puis <http://adresse-du-nas:8080> |
| À distance (recommandé) | VPN Server de DSM (ou WireGuard), puis la même adresse que sur le réseau local |
| Nom de domaine en HTTPS | Panneau de configuration › Portail de connexion › Avancé › **Proxy inversé** : source `https://argos.votre-domaine`, destination `http://localhost:8080` ; certificat Let's Encrypt dans Panneau de configuration › Sécurité › Certificat |

N'ouvrez pas le port 8080 sur votre box. Tant que l'authentification (OIDC, ADR-0008) n'est pas en place, Argos ne doit pas être exposé sur Internet.

### 4. Sauvegarder

Le script [`scripts/sauvegarder-base.sh`](../../scripts/sauvegarder-base.sh) exporte la base dans `sauvegardes/` (compressée) et garde les 14 dernières.

1. Panneau de configuration › **Planificateur de tâches** › Créer › Tâche planifiée › Script défini par l'utilisateur.
2. Utilisateur : `root` ; planification : tous les jours, la nuit.
3. Script : `sh /volume1/docker/argos/scripts/sauvegarder-base.sh`
4. Copiez ensuite `/volume1/docker/argos/sauvegardes` hors du NAS avec **Hyper Backup** (disque USB ou stockage distant).

Restauration :

```bash
gunzip -c sauvegardes/argos-AAAAMMJJ-HHMMSS.sql.gz | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

À tester au moins une fois après la mise en service.

### 5. Mettre à jour

```bash
cd /volume1/docker/argos
git pull
sudo docker compose up -d --build
```

Les migrations de base s'appliquent au démarrage d'Argos. Faites une sauvegarde avant chaque mise à jour.

## Réglages (`.env`)

| Variable | Défaut | Rôle |
|---|---|---|
| `ARGOS_DB_PASSWORD` | `argos` | mot de passe de la base — à changer sur le NAS |
| `ARGOS_BIND` | `127.0.0.1` | adresse d'écoute d'Argos (`0.0.0.0` pour le réseau local) |
| `ARGOS_PORT` | `8080` | port d'Argos sur la machine |
| `ARGOS_DB_PORT` | `5432` | port de la base sur la machine (accessible localement seulement) |
| `ARGOS_MEM_LIMIT` / `ARGOS_DB_MEM_LIMIT` | `1g` / `512m` | mémoire maximale des conteneurs |
| `ARGOS_BACKUP_DIR` / `ARGOS_BACKUP_KEEP` | `./sauvegardes` / `14` | dossier et nombre de sauvegardes conservées |

## Avis de licence de l'image

La construction de l'image contrôle les licences des dépendances livrées du serveur (profil Maven `licences`, [README du serveur](../../argos-server/README.md#licences-des-dépendances)) : elle échoue si une dépendance n'a aucune licence déclarée ou porte une licence non admise, et affiche la liste des écarts. Ce contrôle demande l'accès au dépôt Maven Central, comme le reste de la construction.

L'image contient les avis de licence dans `/opt/argos/licenses/` :

| Fichier | Contenu |
|---|---|
| `NOTICE` | avis NOTICE propres au serveur (copie de `argos-server/NOTICE`) |
| `THIRD-PARTY.txt` | dépendances tierces livrées, leurs licences et les avis NOTICE de leurs archives, générés à la construction de l'image |
| `Apache-2.0.txt` | texte de l'Apache License 2.0 |

Pour les lire sans démarrer Argos :

```bash
docker run --rm --entrypoint ls argos:local /opt/argos/licenses
docker run --rm --entrypoint cat argos:local /opt/argos/licenses/THIRD-PARTY.txt
```

Remplacez `argos:local` par `argos:<ARGOS_VERSION>` si la variable est définie dans `.env`. Sur un conteneur démarré : `docker compose exec argos cat /opt/argos/licenses/NOTICE`.

Commande dédiée, hors de l'image (Maven et Node.js installés) : `mvn -B -f argos-server/pom.xml -P licences validate`, qui écrit `argos-server/target/THIRD-PARTY.txt`.

## Client desktop

Le client desktop (ADR-0022) se connecte à un serveur Argos. Pour viser le NAS, construisez l'IHM avec l'adresse du serveur : `VITE_ARGOS_API_URL=http://adresse-du-nas:8080`, puis `npm run dist` dans `argos-desktop`.

## Connecter un assistant IA (MCP)

Argos expose un serveur MCP en lecture seule sur `POST /mcp` (fiche [`serveur-mcp`](../specifications/serveur-mcp.md)), en protocole sans état 2026-07-28 ; un client d'une version antérieure négocie une session, que le jeton accompagne toujours. Chaque appel porte un jeton d'accès Keycloak de l'utilisateur ; Argos ne stocke aucun jeton.

1. **Client Keycloak** : dans le royaume d'Argos, un client public `argos-mcp` avec le flux d'appareil activé (« OAuth 2.0 Device Authorization Grant ») et le mappeur d'audience `argos-server`. Le royaume de test (`argos-server/src/test/resources/keycloak/argos-realm.json`) en donne la définition.
2. **Obtenir un jeton par le flux d'appareil** (RFC 8628) :

   ```bash
   KC=https://keycloak.votre-domaine/realms/argos
   curl -s -d client_id=argos-mcp -d scope=openid "$KC/protocol/openid-connect/auth/device"
   # Ouvrez verification_uri_complete dans un navigateur et connectez-vous, puis :
   curl -s -d client_id=argos-mcp -d grant_type=urn:ietf:params:oauth:grant-type:device_code \
        -d device_code=<device_code> "$KC/protocol/openid-connect/token"
   export ARGOS_MCP_TOKEN=<access_token>
   ```

   Le jeton expire selon la durée de vie fixée dans Keycloak ; le `refresh_token` reçu en délivre un nouveau (`grant_type=refresh_token`). Ces jetons restent sur le poste du développeur.
3. **Configurer l'assistant** : fichier `.mcp.json` du projet, par exemple pour Claude Code :

   ```json
   {
     "mcpServers": {
       "argos": {
         "type": "http",
         "url": "https://argos.votre-domaine/mcp",
         "headers": { "Authorization": "Bearer ${ARGOS_MCP_TOKEN}" }
       }
     }
   }
   ```

   Claude Code développe `${ARGOS_MCP_TOKEN}` depuis l'environnement : le fichier ne contient aucun jeton et peut être versionné. Exemple pour la pile de test locale : [`e2e/donnees/mcp.json`](../../e2e/donnees/mcp.json) ; procédure de constat avec Claude Code : fiche [`serveur-mcp`](../specifications/serveur-mcp.md#procédure-manuelle-avec-claude-code).

Limites : 60 appels par minute et par utilisateur (`argos.mcp.debit.requetes-par-minute`) ; texte externe tronqué à 2 000 caractères (`argos.mcp.contenu-externe.longueur-max`).

## État de vérification

- `compose.yaml` validé par `docker compose config` ; script de sauvegarde contrôlé par `sh -n`.
- Serveur MCP : protocole, authentification et outils vérifiés par les tests `io.argos.mcp` ; **non vérifié** : la connexion réelle d'un assistant IA configuré par `.mcp.json`.
- Construction de l'image vérifiée le 2026-11-19 sur un PC Windows (Docker Engine 29.9.3) : `docker build` réussit, l'étape de contrôle affiche « 194 dépendances inventoriées, 26 avis NOTICE reproduits » puis « Aucune licence absente ni non admise », et `/opt/argos/licenses/` contient `NOTICE`, `THIRD-PARTY.txt` et `Apache-2.0.txt`, propriété de root en lecture seule (0444) et lisibles par l'utilisateur d'UID 1001. Un inventaire factice (licence GPL-3.0, EPL-2.0 hors des neuf API Jakarta, licence absente) donné au script dans l'étage `server` fait échouer `docker build` avec le code 1.
- **Non vérifié** : le démarrage réel sur le NAS. Premier essai à faire sur le PC avec `docker compose up -d --build`.
