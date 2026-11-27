# Déployer Argos sur le Synology ou sur un PC

Argos s'installe de la même façon sur le NAS Synology DS1522+ et sur un PC : avec Docker Compose, à partir du fichier [`compose.yaml`](../../compose.yaml) à la racine du dépôt. Décision : [ADR-0026](../architecture/adr/0026-hebergement-synology.md).

Argos exige un fournisseur d'identité OIDC, Keycloak ([ADR-0027](../architecture/adr/0027-keycloak-idp-auto-heberge.md)) : sans `ARGOS_OIDC_URL`, le conteneur `argos` s'arrête au démarrage. Sur une machine sans Keycloak, le profil Compose `idp-local` en fournit un, avec sa base dans le même PostgreSQL ([Choisir le fournisseur d'identité](#choisir-le-fournisseur-didentité)).

| Conteneur | Démarre | Rôle | Mémoire maximale par défaut |
|---|---|---|---|
| `postgres` | toujours | base de données PostgreSQL 18, données dans le volume `argos-pgdata` (bases `argos` et `keycloak`) | 512 Mio |
| `argos` | toujours | IHM web + API Quarkus, sur le port 8080 | 1 Gio |
| `keycloak` | avec le profil `idp-local` | fournisseur d'identité, sur le port 8180 | 768 Mio |
| `ollama` | avec le profil `ia` | modèle d'IA local, facultatif ([ADR-0029](../architecture/adr/0029-passerelle-ia-ollama-local.md)) | 2,5 Gio |

Chaque conteneur déclare un contrôle de santé et borne ses journaux ([Santé et journaux des conteneurs](#santé-et-journaux-des-conteneurs)) ; les plafonds tiennent dans la mémoire du NAS ([Dimensionnement du NAS](#dimensionnement-du-nas)).

Chaque application a son compte PostgreSQL, ni superutilisateur ni propriétaire d'une autre base ([ADR-0035](../architecture/adr/0035-comptes-et-isolation-des-bases.md)) :

| Compte | Variable (défaut) | Usage |
|---|---|---|
| administration | `ARGOS_DB_ADMIN_USER` (`argos_admin`) | superutilisateur de l'instance : initialisation, sauvegarde, restauration |
| propriétaire d'Argos | `ARGOS_DB_OWNER` (`argos`) | possède le schéma de la base `argos`, applique les migrations au démarrage |
| connexion d'Argos | `ARGOS_DB_USER` (`argos_connexion`) | requêtes de l'application, soumises à la RLS |
| Keycloak | `ARGOS_KEYCLOAK_DB_USER` (`keycloak`) | propriétaire de la seule base `keycloak` |

Les rôles et les deux bases sont créés au premier démarrage du volume par [`deploiement/postgres/initialiser-bases.sh`](../../deploiement/postgres/initialiser-bases.sh). Les quatre mots de passe (`ARGOS_DB_ADMIN_PASSWORD`, `ARGOS_DB_OWNER_PASSWORD`, `ARGOS_DB_PASSWORD`, `ARGOS_KEYCLOAK_DB_PASSWORD`) sont obligatoires : sans eux, `docker compose` refuse de démarrer et nomme la variable manquante. Un volume créé avant ces comptes se migre selon [Passer aux comptes de base distincts](#passer-aux-comptes-de-base-distincts).

L'image `argos` est construite à partir du [`Dockerfile`](../../Dockerfile) : elle compile l'IHM, l'embarque dans le serveur, contrôle les licences des dépendances du serveur et de l'IHM et ne garde qu'un JRE 17 et les avis de licence ([Avis de licence de l'image](#avis-de-licence-de-limage)). Elle cible les processeurs x86-64, ce qui couvre le DS1522+ (AMD Ryzen R1600) et les PC.

## Choisir le fournisseur d'identité

| Situation | Choix | Réglages dans `.env` |
|---|---|---|
| Aucun Keycloak disponible (machine vierge) | Keycloak fourni par `compose.yaml`, profil `idp-local` | `COMPOSE_PROFILES=idp-local` et le bloc [Keycloak fourni](#keycloak-fourni-profil-idp-local) |
| Un Keycloak existe déjà | royaume existant | le bloc [Keycloak existant](#keycloak-existant) |

`COMPOSE_PROFILES=idp-local`, écrit dans `.env`, active le profil pour toutes les commandes `docker compose` lancées dans le dossier, y compris la mise à jour et la restauration : il n'est plus nécessaire d'ajouter `--profile idp-local`.

### Keycloak fourni (profil `idp-local`)

```bash
COMPOSE_PROFILES=idp-local
ARGOS_OIDC_URL=http://keycloak:8080/realms/argos
ARGOS_OIDC_ISSUER_PUBLIC=http://localhost:8180/realms/argos
ARGOS_ADMIN_EMAIL=vous@exemple.fr
ARGOS_ADMIN_MOT_DE_PASSE_INITIAL=<provisoire, 12 caractères au moins>
ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE=<console d'administration de Keycloak>
```

- `ARGOS_OIDC_URL` est l'adresse du royaume pour le serveur, sur le réseau du projet ; `ARGOS_OIDC_ISSUER_PUBLIC` est l'émetteur public, l'adresse que joint le navigateur ([Adresses de l'instance](#adresses-de-linstance)). Les valeurs ci-dessus servent un navigateur ouvert sur la machine elle-même ; depuis un autre poste, voir [Se connecter depuis un autre poste](#se-connecter-depuis-un-autre-poste).
- `ARGOS_ADMIN_EMAIL` désigne l'administrateur plateforme : c'est le seul compte du royaume importé, et Argos crée son compte sans invitation à sa première connexion.
- `ARGOS_ADMIN_EMAIL`, `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL` et `ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE` sont obligatoires avec ce profil : s'il en manque une, le conteneur `keycloak` s'arrête avant d'importer le royaume, avec un message qui la nomme (`docker compose logs keycloak`), et redémarre en boucle jusqu'à la correction du `.env`.

La première commande `docker compose up -d --build` construit aussi l'image Keycloak optimisée `argos-keycloak` ([`deploiement/keycloak/Dockerfile`](../../deploiement/keycloak/Dockerfile)) : Keycloak s'exécute en lecture seule et ne peut pas reconstruire son installation au démarrage (EXP-16). Après tout changement de `ARGOS_KEYCLOAK_VERSION`, relancez la commande avec `--build` ; sans `--build`, l'image de la version précédente reste servie.

Keycloak répond sur <http://localhost:8180> (`ARGOS_KEYCLOAK_PORT`), Argos sur le port `ARGOS_PORT`. Le royaume importé ([`deploiement/keycloak/argos-realm.json`](../../deploiement/keycloak/argos-realm.json)) déclare les clients `argos-server`, `argos-ui` et `argos-mcp` et un seul compte, l'administrateur. Les adresses de retour, les origines et les adresses de fin de session de `argos-ui` sont exactes, sans joker : elles reprennent `ARGOS_PUBLIC_URL` (par défaut `http://localhost:` suivi de `ARGOS_PORT`) au moment de l'import, plus `http://127.0.0.1/argos/retour` et `app://argos` pour le client desktop. Le royaume n'est importé qu'une fois, au premier démarrage sur une base `keycloak` vide : changer ensuite `ARGOS_PUBLIC_URL`, `ARGOS_ADMIN_EMAIL` ou `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL` ne modifie ni le compte ni le royaume, qui se gèrent alors dans la console ([Royaume déjà importé](#royaume-déjà-importé)).

Le royaume livré est durci avant toute ouverture (ACC-13) :

| Réglage | Valeur |
|---|---|
| Protection contre la force brute | blocage après 5 échecs, attente de 60 s qui croît à chaque série d'échecs, jusqu'à 15 min |
| Mot de passe | au moins 12 caractères, ni l'identifiant ni l'adresse e-mail, différent des 3 derniers |
| TLS (`sslRequired`) | `external` : le clair n'est accepté que depuis le réseau local et la machine elle-même |
| Mot de passe provisoire de l'administrateur | à changer à la première connexion (la politique s'applique au nouveau mot de passe) |
| Inscription libre | désactivée : l'administrateur crée les comptes ([Ajouter des utilisateurs](#ajouter-des-utilisateurs)) |

`node scripts/verifier-royaume.mjs` contrôle ces valeurs dans le fichier, et la CI l'exécute.

La base `keycloak` porte les comptes et leurs sujets OIDC, auxquels Argos rattache ses propres comptes : elle est sauvegardée avec celle d'Argos ([Sauvegarder](#4-sauvegarder)). Après une reprise, restaurez-la **avant le premier démarrage de Keycloak** ([Restaurer](#5-restaurer)) : Keycloak n'importe le royaume que s'il n'existe pas, et un royaume réimporté donnerait de nouveaux sujets à tous les comptes, que les comptes Argos ne retrouveraient plus.

### Keycloak existant

Sans profil `idp-local` (pas de `COMPOSE_PROFILES`) :

```bash
ARGOS_OIDC_URL=https://auth.exemple.fr/realms/argos
ARGOS_ADMIN_EMAIL=vous@exemple.fr
```

`ARGOS_OIDC_URL` est l'adresse du royaume ; `ARGOS_OIDC_ISSUER_PUBLIC` reste vide quand cette adresse est aussi celle que joint le navigateur. Le royaume déclare les clients de [`deploiement/keycloak/argos-realm.json`](../../deploiement/keycloak/argos-realm.json), réglés comme dans [Royaume déjà importé](#royaume-déjà-importé) : `argos-server` (audience des jetons, `ARGOS_OIDC_CLIENT_ID` et `ARGOS_OIDC_AUDIENCE`), `argos-ui` (client public, code d'autorisation avec PKCE S256, adresse de retour `<ARGOS_PUBLIC_URL>/`) et, pour un assistant IA, `argos-mcp`. Le compte de `ARGOS_ADMIN_EMAIL` a une adresse e-mail vérifiée.

## Sur un PC (Windows, macOS, Linux)

Prérequis : Docker Desktop (ou Docker Engine avec le plugin Compose) et Git.

1. Récupérez le dépôt et créez `.env` :

   ```bash
   git clone <adresse-du-dépôt> argos-tech-platform
   cd argos-tech-platform
   cp .env.example .env
   ```

2. Dans `.env`, renseignez les quatre mots de passe de base, longs et distincts, puis le bloc du fournisseur d'identité ([Keycloak fourni](#keycloak-fourni-profil-idp-local) sur une machine vierge).
3. Construisez et démarrez :

   ```bash
   docker compose up -d --build
   docker compose ps          # attendre l'état « healthy » de chaque conteneur
   ```

   La première construction télécharge les images de base et les dépendances : comptez plusieurs minutes.
4. Connectez-vous ([Première connexion](#première-connexion)) sur <http://localhost:8080>.

Pour arrêter : `docker compose down` (les données restent dans le volume). Pour développer, ne lancez que la base (`docker compose up -d postgres`) et suivez la section « Démarrage local » du [README](../../README.md).

Depuis un autre poste du réseau local, voir [Se connecter depuis un autre poste](#se-connecter-depuis-un-autre-poste) ; le pare-feu du PC doit accepter les connexions entrantes sur les ports `ARGOS_PORT` et `ARGOS_KEYCLOAK_PORT`.

Si le conteneur `argos` redémarre en boucle et que `docker compose logs argos` affiche `Failed to load config value of type class java.lang.String for: argos.ui.oidc.emetteur`, `ARGOS_OIDC_URL` manque dans `.env`.

## Première connexion

1. Ouvrez le portail (`ARGOS_PUBLIC_URL`, par défaut <http://localhost:8080>) et choisissez « Se connecter », puis encore « Se connecter » : le navigateur passe sur la page de connexion de Keycloak.
2. Identifiant : `ARGOS_ADMIN_EMAIL` ; mot de passe : `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL`. Keycloak exige aussitôt un nouveau mot de passe (12 caractères au moins), puis le prénom et le nom du compte.
3. Le navigateur revient sur le portail, connecté : le nom du compte s'affiche dans la barre de navigation. Ce compte est l'administrateur plateforme.

## Ajouter des utilisateurs

L'inscription libre est désactivée et Argos n'envoie aucune invitation par e-mail ni ne crée de compte Keycloak ([`comptes-acces`](../specifications/comptes-acces.md)). Le portail n'a pas d'écran de création d'espace ni d'invitation : ces opérations passent par l'API, avec un jeton d'accès obtenu par le flux d'appareil du client `argos-mcp`, déclaré par le royaume livré.

1. **Jeton d'accès** de la personne qui agit (RFC 8628) :

   ```bash
   KC=http://localhost:8180/realms/argos      # émetteur public
   API=http://localhost:8080/api/v1           # ARGOS_PUBLIC_URL + /api/v1
   curl -s -d client_id=argos-mcp -d scope=openid "$KC/protocol/openid-connect/auth/device"
   # Ouvrez verification_uri_complete dans un navigateur, connectez-vous et accordez l'accès, puis :
   curl -s -d client_id=argos-mcp -d grant_type=urn:ietf:params:oauth:grant-type:device_code \
        -d device_code=<device_code> "$KC/protocol/openid-connect/token"
   JETON=<access_token>
   ```

2. **Premier espace**, créé par l'administrateur plateforme, qui en devient administrateur :

   ```bash
   curl -s -X POST -H "Authorization: Bearer $JETON" -H 'Content-Type: application/json' \
        -d '{"nom":"Mon espace"}' "$API/espaces"          # la réponse donne l'identifiant de l'espace
   ```

3. **Compte Keycloak** de la personne : console de Keycloak (<http://localhost:8180/admin>, compte `ARGOS_KEYCLOAK_ADMIN`, par défaut `admin`, mot de passe `ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE`) › royaume `argos` › **Utilisateurs** › **Créer** : adresse e-mail, prénom, nom, « E-mail vérifié » activé ; onglet **Identifiants** : mot de passe provisoire.
4. **Invitation**, par un administrateur de l'espace, avec le rôle `LECTEUR`, `CONTRIBUTEUR` ou `ADMINISTRATEUR` ; elle vaut 14 jours :

   ```bash
   curl -s -X POST -H "Authorization: Bearer $JETON" -H 'Content-Type: application/json' \
        -d '{"email":"bea@exemple.fr","role":"LECTEUR"}' "$API/espaces/<espace>/invitations"
   ```

5. **Acceptation**, par la personne invitée : elle obtient son propre jeton (étape 1, en changeant son mot de passe provisoire à la connexion), lit ses invitations, puis accepte :

   ```bash
   curl -s -H "Authorization: Bearer $JETON" "$API/invitations"
   curl -s -X POST -H "Authorization: Bearer $JETON" "$API/invitations/<invitation>/acceptation"
   ```

   L'espace figure ensuite dans `GET $API/espaces` et dans le portail. Une personne connectée sans invitation en attente voit l'écran « aucun compte ARGOS ».

## Se connecter depuis un autre poste

### Adresses de l'instance

Trois adresses distinctes coexistent, et chacune a une valeur par défaut pour l'installation locale :

| Adresse | Variable | Défaut | Qui la consomme |
|---|---|---|---|
| Adresse publique d'Argos, telle que l'ouvre le navigateur, sans « / » final | `ARGOS_PUBLIC_URL` | `http://localhost:` + `ARGOS_PORT` (8080) | adresse de retour, origine et fin de session du client `argos-ui` du royaume livré (à l'import) ; liens des e-mails |
| Émetteur public : le royaume tel que le joint le navigateur et que le portent les jetons | `ARGOS_OIDC_ISSUER_PUBLIC` (avec Keycloak fourni, `ARGOS_KEYCLOAK_PUBLIC_URL` + `/realms/argos`) | `ARGOS_OIDC_URL` | IHM (lecture au démarrage dans `GET /api/v1/system/info`), politique de sécurité du contenu, `KC_HOSTNAME` de Keycloak, client desktop |
| Adresse interne du royaume, pour le serveur | `ARGOS_OIDC_URL` | aucune (obligatoire) | validation des jetons par le serveur |

Pour se connecter **depuis un autre poste** du réseau local avec Keycloak fourni, réglez aussi ces variables dans `.env` (remplacez `192.168.1.20` par l'adresse de la machine qui héberge Argos), puis `docker compose up -d` :

```bash
ARGOS_BIND=0.0.0.0
ARGOS_PUBLIC_URL=http://192.168.1.20:8080
ARGOS_KEYCLOAK_BIND=0.0.0.0
ARGOS_KEYCLOAK_PUBLIC_URL=http://192.168.1.20:8180
ARGOS_OIDC_ISSUER_PUBLIC=http://192.168.1.20:8180/realms/argos
```

`ARGOS_OIDC_URL` ne change pas : le serveur joint toujours Keycloak par le réseau du projet. `ARGOS_PUBLIC_URL` sert à l'import du royaume : sur une base `keycloak` vide, réglez-le avant le premier démarrage ; sur un royaume déjà importé, déclarez la nouvelle adresse dans la console ([Royaume déjà importé](#royaume-déjà-importé)). Le navigateur de la machine hôte utilise alors lui aussi `http://192.168.1.20:8080` : l'émetteur public est le même pour tous les postes.

Le royaume livré est déjà durci ; ne l'ouvrez pas avant d'avoir vérifié qu'il l'est : `node scripts/verifier-royaume.mjs` sur le dépôt, ou, sur un royaume déjà importé, les réglages de [Royaume déjà importé](#royaume-déjà-importé).

Le client desktop de cet autre poste reçoit les mêmes adresses ([Client desktop](#client-desktop)).

### Royaume déjà importé

Keycloak n'importe le royaume qu'une fois : une installation existante garde ses anciens réglages. Dans la console d'administration (royaume `argos`) :

1. Clients › `argos-ui` : adresses de redirection valides = `<ARGOS_PUBLIC_URL>/` (et `http://127.0.0.1/argos/retour` pour le client desktop), origines web autorisées = `<ARGOS_PUBLIC_URL>` et `app://argos`, adresses de redirection après déconnexion = `<ARGOS_PUBLIC_URL>/`, sans joker.
2. Paramètres du royaume › Défenses de sécurité › Détection des attaques par force brute : activée, 5 échecs, attente de 60 s, attente maximale de 15 min, blocage temporaire.
3. Authentification › Politiques › Politique de mot de passe : longueur minimale 12, pas le nom d'utilisateur, pas l'e-mail, historique de 3.
4. Paramètres du royaume › Général › SSL requis : « Requêtes externes ».

### HTTPS derrière le reverse proxy

Pour un nom de domaine en HTTPS, le reverse proxy de DSM termine TLS : il ajoute l'en-tête `Strict-Transport-Security` (Panneau de configuration › Portail de connexion › Avancé › Proxy inversé › en-têtes personnalisés), qu'Argos ne pose pas. Dans `.env`, `ARGOS_PUBLIC_URL`, `ARGOS_KEYCLOAK_PUBLIC_URL` et `ARGOS_OIDC_ISSUER_PUBLIC` prennent des adresses `https://`. Une fois le passage en HTTPS vérifié, passez « SSL requis » du royaume à « Toutes les requêtes » (`sslRequired: all` dans la console ou dans `argos-realm.json` avant un premier import) : Keycloak refuse alors tout accès en clair, y compris depuis le réseau local.

Ordre de mise en service : durcir avant d'exposer. Le royaume durci (force brute, politique de mot de passe, TLS) est en place avant d'ouvrir Keycloak hors de `127.0.0.1` (`ARGOS_KEYCLOAK_BIND`) ou derrière un nom de domaine.

## Sur le Synology DS1522+

Prérequis : DSM 7.2 ou plus récent, paquet **Container Manager** installé depuis le Centre de paquets.

### 1. Copier le projet et préparer `.env`

Créez le dossier partagé `docker` s'il n'existe pas, puis copiez le dépôt dans `/volume1/docker/argos` (File Station, partage réseau ou `git clone` en SSH).

Dans ce dossier, copiez `.env.example` en `.env` et renseignez :

- les quatre mots de passe de base (`ARGOS_DB_ADMIN_PASSWORD`, `ARGOS_DB_OWNER_PASSWORD`, `ARGOS_DB_PASSWORD`, `ARGOS_KEYCLOAK_DB_PASSWORD`), longs et distincts ;
- le fournisseur d'identité : le bloc [Keycloak fourni](#keycloak-fourni-profil-idp-local), avec `COMPOSE_PROFILES=idp-local`, ou le bloc [Keycloak existant](#keycloak-existant) ;
- les adresses du NAS, pour se connecter depuis les autres postes : le bloc de [Se connecter depuis un autre poste](#se-connecter-depuis-un-autre-poste), avec l'adresse du NAS.

### 2. Créer le projet dans Container Manager

1. Container Manager › **Projet** › **Créer**.
2. Nom : `argos` ; chemin : `/volume1/docker/argos` ; source : utiliser le `compose.yaml` existant.
3. Lancez la création : les images `argos` et `argos-keycloak` sont construites sur le NAS, puis `postgres`, `keycloak` et `argos` démarrent.

La première construction télécharge les images de base et les dépendances : comptez plusieurs minutes. Elle peut aussi se faire en SSH, dans `/volume1/docker/argos` : `sudo docker compose up -d --build`.

**À vérifier sur votre NAS** : si Container Manager refuse de construire l'image, ou ne démarre pas le conteneur `keycloak` malgré `COMPOSE_PROFILES`, construisez et démarrez en SSH avec la commande ci-dessus, puis gérez le projet depuis Container Manager.

### 3. Accéder à Argos

| Usage | Réglage |
|---|---|
| Depuis le réseau local | `ARGOS_BIND=0.0.0.0` et les adresses du NAS dans `.env` ([Se connecter depuis un autre poste](#se-connecter-depuis-un-autre-poste)), puis <http://adresse-du-nas:8080> |
| À distance (recommandé) | VPN Server de DSM (ou WireGuard), puis la même adresse que sur le réseau local |
| Nom de domaine en HTTPS | Panneau de configuration › Portail de connexion › Avancé › **Proxy inversé** : source `https://argos.votre-domaine`, destination `http://localhost:8080` ; certificat Let's Encrypt dans Panneau de configuration › Sécurité › Certificat ; puis [HTTPS derrière le reverse proxy](#https-derrière-le-reverse-proxy) |

Par défaut, Argos et Keycloak n'écoutent que sur le NAS lui-même (`ARGOS_BIND=127.0.0.1`, `ARGOS_KEYCLOAK_BIND=127.0.0.1`) : rien n'est exposé. N'ouvrez pas les ports 8080 et 8180 sur votre box : l'accès distant passe par le VPN, ou par le reverse proxy en HTTPS une fois le royaume durci et `sslRequired` passé à « Toutes les requêtes ».

Connectez-vous ensuite selon [Première connexion](#première-connexion), puis ajoutez les utilisateurs ([Ajouter des utilisateurs](#ajouter-des-utilisateurs)), en remplaçant `localhost` par l'adresse du NAS.

### 4. Sauvegarder

Le script [`scripts/sauvegarder-base.sh`](../../scripts/sauvegarder-base.sh) exporte, dans `sauvegardes/` (`ARGOS_BACKUP_DIR`), la base `argos` et, si elle existe, la base `keycloak` : une archive compressée par base, de même horodatage (`argos-AAAAMMJJ-HHMMSS.sql.gz`, `keycloak-AAAAMMJJ-HHMMSS.sql.gz`).

- Chaque archive est vérifiée (intégrité, export allé à son terme) avant d'être conservée ; en cas d'échec, le script sort avec un code non nul en nommant la base, sans laisser d'archive invalide.
- Les 14 dernières archives de chaque base sont gardées (`ARGOS_BACKUP_KEEP`) ; les plus anciennes ne sont supprimées qu'après une sauvegarde complète réussie : une suite d'échecs ne fait disparaître aucune bonne sauvegarde.
- Archives en 0600 et dossier en 0700 : lisibles par le seul compte qui exécute le script. Elles contiennent toutes les données, comptes Keycloak compris ; leur chiffrement reste à décider (question Q-2 du changement `fiabiliser-sauvegarde-et-roles-base`).
- Les deux bases sont exportées l'une après l'autre : un compte créé pendant la sauvegarde peut manquer dans l'une des deux archives.

Planification sur le NAS :

1. Panneau de configuration › **Planificateur de tâches** › Créer › Tâche planifiée › Script défini par l'utilisateur.
2. Utilisateur : `root` ; planification : tous les jours, la nuit ; Paramètres de tâche › **Envoyer les détails d'exécution par e-mail**, en ne gardant que les exécutions terminées anormalement : un code de sortie non nul signale l'échec.
3. Script : `sh /volume1/docker/argos/scripts/sauvegarder-base.sh`
4. Copiez ensuite `/volume1/docker/argos/sauvegardes` hors du NAS avec **Hyper Backup** (disque USB ou stockage distant).

### 5. Restaurer

Le script [`scripts/restaurer-base.sh`](../../scripts/restaurer-base.sh) restaure les deux bases d'une même sauvegarde. Il **remplace** leur contenu : sauvegardez d'abord l'état courant (`sh scripts/sauvegarder-base.sh`), même dégradé.

```bash
ls sauvegardes/                                   # choisir l'horodatage, par exemple 20261124-031500
sh scripts/restaurer-base.sh 20261124-031500
docker compose --profile idp-local up -d --wait keycloak   # avec Keycloak fourni
docker compose up -d argos
```

Le script vérifie les archives, arrête Argos et Keycloak, démarre PostgreSQL, puis, pour chaque base : la recrée vide avec son propriétaire et ses droits, la recharge dans une seule transaction sous ce propriétaire et s'arrête à la première erreur (code non nul, base nommée, transaction annulée). Sans archive `keycloak` pour cet horodatage, la base `keycloak` reste en l'état. Argos et Keycloak restent arrêtés : démarrez Keycloak, puis Argos, une fois la restauration terminée. Argos applique au démarrage les migrations postérieures à la sauvegarde.

La procédure se rejoue sur une instance vierge (volume supprimé puis recréé par `docker compose up -d postgres`) comme sur une base en service ; elle est exercée par [`scripts/essai-sauvegarde-restauration.sh`](../../scripts/essai-sauvegarde-restauration.sh) et par l'exercice R-6 de [`recette-poste.md`](recette-poste.md).

### 6. Mettre à jour

```bash
cd /volume1/docker/argos
git pull
sudo docker compose up -d --build
```

Les migrations de base s'appliquent au démarrage d'Argos. Faites une sauvegarde avant chaque mise à jour.

## Lire les journaux et les métriques

Choix d'observabilité : [ADR-0036](../architecture/adr/0036-pile-observabilite-serveur.md) ; exigences EXP-13 à EXP-15 de la fiche [`exploitation-plateforme`](../specifications/exploitation-plateforme.md).

**Journaux.** Argos écrit une ligne JSON par événement : `timestamp`, `level`, `loggerName`, `message`, `mdc` (identifiant de corrélation `correlation`, nom de la tâche planifiée `tache`) et, pour une erreur, `exception` (type, message, pile, causes). Tous les événements d'une requête portent le même identifiant, renvoyé au client dans l'en-tête `X-Request-Id` :

```bash
sudo docker compose logs argos | grep '"level":"ERROR"'
sudo docker compose logs argos | grep '"correlation":"<identifiant>"'
sudo docker compose logs argos | grep '"tache":"signal-synchronisation-sources"'
```

Aucun jeton, mot de passe ni adresse e-mail n'est journalisé ; un jeton cité par un message est remplacé par `[jeton masqué]`.

**État de santé.** `http://localhost:8080/q/health/ready` (public) répond 200 tant que la base est joignable, 503 sinon. Ses contrôles `tache-<identité>` (une par tâche active : `a-l-heure`, `jamais-executee`, `en-echec` ou `en-retard`, après deux intervalles sans exécution), `sources-avis`, `envoi-alertes` et `passerelle-ia` restent `UP` et décrivent un état dégradé dans leurs données.

**Métriques.** `http://localhost:8080/q/metrics`, au format Prometheus, est réservé à l'administrateur plateforme (compte `ARGOS_ADMIN_EMAIL`) : il se lit avec son jeton OIDC, par exemple `curl -H "Authorization: Bearer <jeton>" http://localhost:8080/q/metrics`. Un appel anonyme reçoit 401, un autre compte 403. Les métriques d'Argos commencent par `argos_` (exécutions et durée des tâches, sources par état, file d'envoi des alertes, appels IA) ; HTTP, JVM et pool de connexions s'y ajoutent. Aucun collecteur n'est fourni.

## Passer aux comptes de base distincts

Un volume PostgreSQL créé avant [ADR-0035](../architecture/adr/0035-comptes-et-isolation-des-bases.md) n'a qu'un compte, `argos`, superutilisateur de l'instance, que PostgreSQL ne permet pas de rétrograder ; les scripts d'initialisation ne s'exécutent que sur un volume neuf. La migration passe donc par une sauvegarde, un volume neuf et une restauration ; l'ancien volume est copié pour le retour arrière. Commandes en SSH sur le NAS (préfixe `sudo`) ou sur le PC, dans le dossier du déploiement ; `argos` est le nom du projet Compose.

1. Récupérez la nouvelle version (`git pull`) **sans redémarrer** les conteneurs, et gardez une copie de l'ancien `.env`.
2. Dans `.env` : supprimez la ligne `ARGOS_DB_USER=argos` (le compte `argos` devient le propriétaire, `ARGOS_DB_OWNER`), puis ajoutez les quatre mots de passe de base ([`.env.example`](../../.env.example)).
3. Sauvegardez avec le nouveau script, qui s'exécute dans le conteneur en service : `sh scripts/sauvegarder-base.sh`. Notez l'horodatage affiché ; avec le profil `idp-local`, vérifiez que l'archive `keycloak` existe.
4. Arrêtez la pile et copiez l'ancien volume :

   ```bash
   docker compose --profile idp-local down
   docker volume create argos_argos-pgdata-avant-comptes
   docker run --rm -v argos_argos-pgdata:/source:ro -v argos_argos-pgdata-avant-comptes:/copie postgres:18 \
     sh -c 'cp -a /source/. /copie/'
   docker volume rm argos_argos-pgdata
   ```

5. Créez l'instance neuve, puis restaurez : `docker compose up -d --wait postgres`, puis `sh scripts/restaurer-base.sh <horodatage>`.
6. Démarrez Keycloak, puis Argos : `docker compose --profile idp-local up -d --wait keycloak`, puis `docker compose --profile idp-local up -d --build`. Au démarrage, Argos applique sous le propriétaire la migration des droits du rôle de connexion (V22).
7. Connectez-vous : votre mot de passe Keycloak et vos espaces sont ceux d'avant la migration. Supprimez la copie de l'ancien volume une fois l'installation vérifiée (`docker volume rm argos_argos-pgdata-avant-comptes`).

Retour arrière : `docker compose --profile idp-local down`, `docker volume rm argos_argos-pgdata`, recréez `argos_argos-pgdata` et recopiez-y la copie (même commande `docker run`, source et destination inversées), remettez l'ancien `.env` et la version précédente du dépôt (`git checkout <révision>`), puis `docker compose --profile idp-local up -d --build`.

## Client desktop

Le client desktop (ADR-0022) emballe l'IHM et se connecte à un serveur Argos. Il a besoin de deux adresses, fixées à l'emballage et remplaçables à l'exécution :

| Réglage | Build de l'IHM | Emballage (`package.json` emballé) | Exécution (variable du poste, prioritaire) | Valeur attendue |
|---|---|---|---|---|
| Adresse du serveur | `VITE_ARGOS_API_URL` | `argos.apiUrl` | `ARGOS_API_URL` | `ARGOS_PUBLIC_URL` du serveur |
| Émetteur OIDC | — | `argos.oidcUrl` | `ARGOS_OIDC_URL` | `ARGOS_OIDC_ISSUER_PUBLIC` du serveur (son émetteur public) |

Sans réglage, le client vise `http://localhost:8080` et `http://localhost:8180/realms/argos`, les valeurs par défaut de Keycloak fourni sur le même poste. Sur le poste, `ARGOS_OIDC_URL` désigne l'émetteur public, que le client joint depuis le poste : ce n'est pas l'adresse interne que le serveur reçoit sous le même nom.

Les adresses doivent coïncider :

- `argos.apiUrl` (ou `ARGOS_API_URL`) est la même adresse que `VITE_ARGOS_API_URL` donnée au build de l'IHM : l'IHM appelle cette dernière, et la politique de sécurité du contenu du client n'autorise que la première ;
- `argos.oidcUrl` (ou `ARGOS_OIDC_URL`) est exactement l'émetteur public du serveur : l'IHM lit l'émetteur dans la réponse publique du serveur, et le client n'ouvre dans le navigateur du système que l'adresse d'autorisation de cet émetteur. Un écart bloque la connexion.

Pour un serveur sur une autre adresse que `http://localhost:8080`, par exemple le NAS en `192.168.1.20` (même valeurs que dans [Se connecter depuis un autre poste](#se-connecter-depuis-un-autre-poste)), sur le poste de construction :

```powershell
npm --prefix argos-ui ci
npm --prefix argos-desktop ci
$env:VITE_ARGOS_API_URL = 'http://192.168.1.20:8080'
npm --prefix argos-desktop run dist -- '-c.extraMetadata.argos.apiUrl=http://192.168.1.20:8080' '-c.extraMetadata.argos.oidcUrl=http://192.168.1.20:8180/realms/argos'
```

Le client desktop se connecte par l'interface de bouclage ([ADR-0033](../architecture/adr/0033-session-oidc-ihm-memoire-bouclage.md)) : le client `argos-ui` du royaume accepte `http://127.0.0.1/argos/retour` et l'origine `app://argos`, déjà déclarées par le royaume livré ([Royaume déjà importé](#royaume-déjà-importé) pour un royaume existant). Changer d'émetteur côté serveur demande de modifier `.env` et de redémarrer `argos`, sans reconstruire l'image ; le client desktop, lui, reçoit la nouvelle adresse par `ARGOS_OIDC_URL` ou par un nouvel emballage.

L'installateur est écrit dans `argos-desktop/release/` ; `"ARGOS-Setup-0.1.0.exe" /S` l'installe sans interface dans le profil de l'utilisateur. Un installeur emballé sur le poste pour une autre adresse de serveur reçoit les mises à jour publiées, emballées pour les adresses par défaut : `ARGOS_API_URL` et `ARGOS_OIDC_URL` à l'exécution gardent alors l'adresse du serveur, ou `ARGOS_MISE_A_JOUR=off` coupe la mise à jour.

### Distribution et mise à jour

Décisions du porteur du projet du 2026-11-27 ([ADR-0042](../architecture/adr/0042-distribution-et-mise-a-jour-du-client-desktop.md)) :

- une étiquette `desktop-v<version>`, égale à la version de `argos-desktop/package.json`, déclenche le workflow « Publication du client desktop », qui publie dans la release GitHub du dépôt l'installeur Windows (`ARGOS-Setup-<version>.exe`) et l'AppImage Linux (`ARGOS-<version>.AppImage`), leurs fichiers `latest.yml` et `latest-linux.yml`, leurs empreintes SHA-256 (`SHA256SUMS-*.txt`) et une attestation de provenance ;
- le client installé cherche une version plus récente au démarrage, la télécharge en arrière-plan, vérifie son empreinte SHA-512 et l'installe à la fermeture d'ARGOS, après une notification ; `ARGOS_MISE_A_JOUR=off` désactive la recherche ; sous macOS, la mise à jour reste manuelle ;
- les installeurs ne sont pas signés tant qu'aucun certificat n'est fourni : Windows SmartScreen demande une confirmation au premier lancement. L'origine d'un installeur se vérifie par `gh attestation verify <fichier> --repo FTurleque/argos-tech-platform`, son contenu par l'empreinte SHA-256 publiée. Les secrets `ARGOS_CSC_LINK` (certificat PKCS#12 en base64) et `ARGOS_CSC_KEY_PASSWORD` du dépôt suffisent à signer les publications suivantes ;
- l'installeur livre, dans ses ressources (`resources/licences/THIRD-PARTY-npm.txt`), l'avis des dépendances npm de l'IHM et du client avec leurs textes de licence ; Electron y ajoute ses propres avis (`LICENSE.electron.txt`, `LICENSES.chromium.html`).

Les recettes à dérouler avec cette installation sont dans [`recette-poste.md`](recette-poste.md).

## Réglages (`.env`)

[`.env.example`](../../.env.example) liste toutes les variables lues par Argos ou par `compose.yaml`, réglages facultatifs commentés avec leur défaut. Le contrôle [`scripts/verifier-configuration.mjs`](../../scripts/verifier-configuration.mjs), exécuté par l'intégration continue, échoue si une variable lue par le serveur en production n'est pas transmise au conteneur `argos`, manque au modèle, ou si une variable `ARGOS_*` lue par le serveur ou par `compose.yaml` manque aux tableaux ci-dessous.

### Base de données

| Variable | Défaut | Rôle |
|---|---|---|
| `ARGOS_DB_ADMIN_PASSWORD` | aucun (obligatoire) | mot de passe du superutilisateur d'administration (`ARGOS_DB_ADMIN_USER`, `argos_admin`) |
| `ARGOS_DB_OWNER_PASSWORD` | aucun (obligatoire) | mot de passe du propriétaire de la base `argos` (`ARGOS_DB_OWNER`, `argos`), employé par les migrations |
| `ARGOS_DB_PASSWORD` | aucun (obligatoire) | mot de passe du rôle de connexion d'Argos (`ARGOS_DB_USER`, `argos_connexion`) |
| `ARGOS_KEYCLOAK_DB_PASSWORD` | aucun (obligatoire) | mot de passe du compte de Keycloak (`ARGOS_KEYCLOAK_DB_USER`, `keycloak`) |
| `ARGOS_DB_ADMIN_USER` / `ARGOS_DB_OWNER` / `ARGOS_DB_USER` / `ARGOS_KEYCLOAK_DB_USER` | `argos_admin` / `argos` / `argos_connexion` / `keycloak` | noms des comptes, fixés au premier démarrage du volume |
| `ARGOS_DB_NAME` | `argos` | nom de la base d'Argos |
| `ARGOS_DB_URL` | `jdbc:postgresql://postgres:5432/` + `ARGOS_DB_NAME` | adresse JDBC de la base, pour une base hors du projet |
| `ARGOS_DB_PORT` | `5432` | port de la base sur la machine (accessible localement seulement) |

### Accès et identité

| Variable | Défaut | Rôle |
|---|---|---|
| `ARGOS_BIND` | `127.0.0.1` | adresse d'écoute d'Argos (`0.0.0.0` pour le réseau local) |
| `ARGOS_PORT` | `8080` | port d'Argos sur la machine |
| `ARGOS_PUBLIC_URL` | `http://localhost:` + `ARGOS_PORT` | adresse publique d'Argos, telle que l'ouvre le navigateur : adresse de retour du royaume livré, début des liens des e-mails (par exemple `https://argos.exemple.fr` derrière le reverse proxy de DSM) |
| `ARGOS_OIDC_URL` | aucun (obligatoire) | adresse du royaume pour le serveur (`http://keycloak:8080/realms/argos` avec Keycloak fourni) |
| `ARGOS_OIDC_ISSUER_PUBLIC` | `ARGOS_OIDC_URL` | émetteur public, l'adresse du royaume que joint le navigateur ([Adresses de l'instance](#adresses-de-linstance)) |
| `ARGOS_OIDC_CLIENT_ID` / `ARGOS_OIDC_AUDIENCE` | `argos-server` / `argos-server` | client OIDC du serveur et audience exigée dans les jetons d'accès |
| `ARGOS_ADMIN_EMAIL` | vide | e-mail vérifié de l'administrateur plateforme, créé sans invitation à sa première connexion ; obligatoire avec `idp-local` |
| `ARGOS_VERSION` | `local` | étiquette de l'image `argos` construite |

### Keycloak du profil `idp-local`

| Variable | Défaut | Rôle |
|---|---|---|
| `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL` | aucun (obligatoire avec `idp-local`) | mot de passe provisoire de l'administrateur plateforme, à l'import du royaume |
| `ARGOS_KEYCLOAK_ADMIN` / `ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE` | `admin` / aucun (obligatoire avec `idp-local`) | compte de la console d'administration de Keycloak |
| `ARGOS_KEYCLOAK_BIND` / `ARGOS_KEYCLOAK_PORT` | `127.0.0.1` / `8180` | adresse d'écoute et port de Keycloak sur la machine |
| `ARGOS_KEYCLOAK_PUBLIC_URL` | `http://localhost:` + `ARGOS_KEYCLOAK_PORT` | adresse de Keycloak vue par le navigateur (`KC_HOSTNAME`) |
| `ARGOS_KEYCLOAK_VERSION` | `26.7.4` | version de Keycloak de l'image `argos-keycloak` (reconstruire avec `--build`) |

### E-mails et forges

| Variable | Défaut | Rôle |
|---|---|---|
| `ARGOS_SMTP_HOST` / `ARGOS_SMTP_PORT` | `localhost` / `587` | relais SMTP des e-mails (celui de DSM sur le NAS) |
| `ARGOS_SMTP_USER` / `ARGOS_SMTP_PASSWORD` | vides | compte du relais SMTP |
| `ARGOS_SMTP_STARTTLS` / `ARGOS_SMTP_TIMEOUT` | `REQUIRED` / `PT10S` | STARTTLS exigé et délai d'envoi |
| `ARGOS_MAIL_FROM` | `argos@localhost` | expéditeur des e-mails |
| `ARGOS_GITLAB_TOKEN` / `ARGOS_GITHUB_TOKEN` | vides | jetons de forge en lecture seule, facultatifs (GitLab `read_api`, GitHub à granularité fine) |

### Planifications

Expressions cron Quartz au fuseau `ARGOS_ALERTES_FUSEAU` ; la valeur `off` désactive la tâche.

| Variable | Défaut | Tâche |
|---|---|---|
| `ARGOS_ALERTES_FUSEAU` | `Europe/Paris` | fuseau horaire des planifications et des résumés |
| `ARGOS_INGESTION_PLANIFICATION_CADENCE` | `1h` | synchronisation planifiée de chaque source d'avis (durée entre deux passages) |
| `ARGOS_INVENTORY_IMPORT_CRON` | `0 0 3 * * ?` | import des inventaires des dépôts |
| `ARGOS_RESUME_QUOTIDIEN_CRON` / `ARGOS_RESUME_HEBDOMADAIRE_CRON` | `0 0 8 * * ?` / `0 0 8 ? * MON` | résumés quotidien et hebdomadaire |
| `ARGOS_RADAR_CRON` | `0 0 6 * * ?` | radar des standards |
| `ARGOS_ACTIVITE_PURGE_CRON` | `0 30 4 * * ?` | purge de l'historique d'activité des projets, conservé 13 mois |
| `ARGOS_CONSERVATION_CRON` | `0 45 4 * * ?` | purge des données journalisées ([ADR-0041](../architecture/adr/0041-conservation-des-donnees-journalisees.md)) |
| `ARGOS_ARTISTE_COLLECTE_CRON` / `ARGOS_ARTISTE_RAPPELS_CRON` | `0 0 5 * * ?` / `0 0 7 * * ?` | collecte et rappels du profil artiste |
| `ARGOS_REGLEMENTAIRE_COLLECTE_CRON` / `ARGOS_REGLEMENTAIRE_RAPPELS_CRON` | `0 30 6 * * ?` / `0 15 7 * * ?` | collecte et rappels de la veille réglementaire |
| `ARGOS_JORF_URL` | `https://echanges.dila.gouv.fr/OPENDATA/JORF` | archives JORF de la DILA (veille réglementaire) |

### Passerelle IA (profil `ia`)

Facultative et désactivée par défaut : sans elle, Argos fonctionne en mode déterministe. `docker compose --profile ia up -d` (ou `COMPOSE_PROFILES=idp-local,ia`) démarre `ollama`, puis `docker compose exec ollama ollama pull qwen2.5:1.5b` télécharge le modèle une fois.

| Variable | Défaut | Rôle |
|---|---|---|
| `ARGOS_IA_ACTIVE` | `false` | active la passerelle IA |
| `ARGOS_OLLAMA_URL` | `http://ollama:11434` | adresse du serveur Ollama |
| `ARGOS_IA_MODELE` / `ARGOS_IA_DELAI` | `qwen2.5:1.5b` / `PT60S` | modèle par défaut et délai d'un appel |
| `ARGOS_IA_PLAFOND_JOUR` | `1000` | appels par jour pour l'instance |
| `ARGOS_IA_QUOTA_ESPACE_JOUR` / `ARGOS_IA_QUOTA_COMPTE_JOUR` | `100` / `20` | appels par jour et par espace, par jour et par compte |
| `ARGOS_IA_ANALYSE_CORRELATION` | `false` | analyse complémentaire de la corrélation (noms de projets transmis au modèle) |
| `ARGOS_OLLAMA_VERSION` / `ARGOS_OLLAMA_KEEP_ALIVE` | `0.40.17` / `10m` | version de l'image `ollama` et durée de maintien du modèle en mémoire |

### Dimensionnement et sauvegardes

| Variable | Défaut | Rôle |
|---|---|---|
| `ARGOS_MEM_LIMIT` / `ARGOS_DB_MEM_LIMIT` | `1g` / `512m` | mémoire maximale des conteneurs `argos` et `postgres` |
| `ARGOS_KEYCLOAK_MEM_LIMIT` / `ARGOS_OLLAMA_MEM_LIMIT` | `768m` / `2560m` | mémoire maximale des conteneurs `keycloak` et `ollama` |
| `ARGOS_DB_MAX_CONNECTIONS` / `ARGOS_DB_POOL_MAX_SIZE` / `ARGOS_KEYCLOAK_DB_POOL_MAX_SIZE` | `40` / `10` / `10` | connexions de PostgreSQL et pools d'Argos et de Keycloak ([Dimensionnement du NAS](#dimensionnement-du-nas)) |
| `ARGOS_DB_SHARED_BUFFERS` / `ARGOS_DB_WORK_MEM` | `128MB` / `4MB` | mémoire partagée et mémoire de travail de PostgreSQL |
| `ARGOS_BACKUP_DIR` / `ARGOS_BACKUP_KEEP` | `./sauvegardes` / `14` | dossier des sauvegardes et nombre d'archives conservées par base |
| `COMPOSE_PROFILES` | vide | profils activés pour toutes les commandes `docker compose` (`idp-local`, `ia`) |
| `TZ` | `Europe/Paris` | fuseau horaire du conteneur `argos` |

## Santé et journaux des conteneurs

Chaque conteneur a un contrôle de santé, dont Container Manager affiche l'état (`healthy`, `unhealthy`) :

| Conteneur | Contrôle |
|---|---|
| `argos` | `/q/health/ready` (`HEALTHCHECK` de l'image) : non sain si la base est injoignable |
| `postgres` | `pg_isready` |
| `keycloak` | `/health/ready` sur son port de gestion |
| `ollama` | réponse de son API (`ollama list`) |

Les journaux de chaque conteneur tournent : trois fichiers de 10 Mio au plus (`docker compose logs <conteneur>` lit le plus récent). Une saturation du tas arrête le serveur (`Terminating due to java.lang.OutOfMemoryError` dans son journal) ; la politique `restart: unless-stopped` le relance.

Tous les conteneurs sont durcis (EXP-12, EXP-16) : système de fichiers racine en lecture seule, aucune capacité système, élévation de privilèges interdite. Seuls restent inscriptibles :

| Conteneur | Utilisateur | Emplacements inscriptibles |
|---|---|---|
| `argos` | `argos` (1001) ; code de l'application à root | `/tmp` (en mémoire) |
| `postgres` | `postgres` (999) | volume `argos-pgdata`, `/tmp` et `/var/run/postgresql` (en mémoire) |
| `keycloak` | `keycloak` (1000), image optimisée | `/tmp` et `/opt/keycloak/data` (en mémoire) ; données dans la base `keycloak` |
| `ollama` | `root`, sans capacité | volume `argos-ollama`, `/tmp` (en mémoire) |

Les emplacements en mémoire sont vidés à chaque redémarrage. Un volume `argos-pgdata` créé par l'image `postgres` lui appartient déjà. Si ses fichiers appartiennent à un autre utilisateur (copie manuelle), `postgres` s'arrête sur `Permission denied` ; rendez-les à l'utilisateur 999 avant de redémarrer :

```bash
sudo docker compose stop postgres
sudo docker run --rm -v argos_argos-pgdata:/var/lib/postgresql postgres:18 chown -R 999:999 /var/lib/postgresql
sudo docker compose up -d postgres
```

## Dimensionnement du NAS

Le DS1522+ est livré avec 8 Go ([ADR-0026](../architecture/adr/0026-hebergement-synology.md)). Les plafonds par défaut laissent au moins 3 Go à DSM avec les deux profils actifs :

| Conteneur | Plafond (`mem_limit`) | Réglages |
|---|---|---|
| `postgres` | 512 Mio | `max_connections=40`, `shared_buffers=128MB`, `work_mem=4MB` |
| `keycloak` (`idp-local`) | 768 Mio | pool de 10 connexions |
| `argos` | 1 Gio | tas à 60 % du plafond, pool de 10 connexions, attente d'une connexion libre bornée à 5 s |
| `ollama` (`ia`) | 2,5 Gio | un modèle chargé, une requête à la fois ; `qwen2.5:1.5b` tient dans environ 2 Gio |
| **Total** | **4,75 Gio** | **3,25 Gio laissés à DSM** |

La somme des pools (20) reste sous `max_connections` (40) : la marge sert aux migrations au démarrage, à la sauvegarde et à l'administration. Un modèle plus gros demande de relever `ARGOS_OLLAMA_MEM_LIMIT` et de réduire un autre plafond, ou d'ajouter de la mémoire au NAS. Relevé `docker stats` sur un PC, pile au repos avec les deux profils : `postgres` 80 Mio, `keycloak` 614 Mio, `argos` 316 Mio, `ollama` 10 Mio sans modèle chargé. Ces valeurs restent à mesurer sur le NAS sous charge (`docker stats`, `SELECT count(*) FROM pg_stat_activity`).

## Avis de licence de l'image

La construction de l'image contrôle les licences des dépendances livrées du serveur (profil Maven `licences`, [README du serveur](../../argos-server/README.md#licences-des-dépendances)) et celles des dépendances npm de l'IHM (`scripts/verifier-licences-npm.mjs`) : elle échoue si une dépendance n'a aucune licence déclarée ou porte une licence non admise, et affiche la liste des écarts. Ce contrôle demande l'accès au dépôt Maven Central, comme le reste de la construction.

L'image contient les avis de licence dans `/opt/argos/licenses/` :

| Fichier | Contenu |
|---|---|
| `NOTICE` | avis NOTICE propres au serveur (copie de `argos-server/NOTICE`) |
| `THIRD-PARTY.txt` | dépendances tierces livrées, leurs licences, les avis NOTICE de leurs archives, les titulaires des droits (licences MIT, BSD, JDOM) et l'adresse du code source des dépendances sous EPL-2.0, générés à la construction de l'image |
| `Apache-2.0.txt` | texte de l'Apache License 2.0 |
| `MIT.txt`, `MIT-0.txt` | textes des licences MIT et MIT No Attribution |
| `BSD-2-Clause.txt`, `BSD-3-Clause.txt` | textes des licences BSD à deux et trois clauses |
| `EPL-2.0.txt` | texte de l'Eclipse Public License 2.0 (neuf API Jakarta embarquées par Quarkus) |
| `JDOM.txt` | texte de la licence de JDOM (lecture des flux RSS et Atom) |
| `THIRD-PARTY-npm.txt` | dépendances npm de l'IHM servie (vue, vue-router, polices…), leurs versions, leurs licences et leurs textes de licence, générés à la construction de l'image par `scripts/verifier-licences-npm.mjs` |

[DÉCISION — porteur du projet, 2026-11-27] L'image livre les textes des licences MIT, BSD (variantes présentes) et EPL-2.0 des dépendances du serveur, à côté des avis existants, et l'avis des dépendances npm de l'IHM avec leurs textes ([ADR-0042](../architecture/adr/0042-distribution-et-mise-a-jour-du-client-desktop.md)). Seuls les textes des licences effectivement retenues par les dépendances entrent dans l'image. Le dossier (0755) et les fichiers (0444) appartiennent à root : l'utilisateur d'exécution (UID 1001) les lit sans pouvoir les modifier.

[DÉCISION — porteur du projet, 2026-11-19] `JDOM.txt` est livré en plus des textes des licences MIT, BSD et EPL-2.0 : la clause 2 de la licence de JDOM impose de reproduire l'avis dans la documentation d'une distribution binaire ([README du serveur](../../argos-server/README.md#licences-des-dépendances)).

Pour les lire sans démarrer Argos :

```bash
docker run --rm --entrypoint ls argos:local /opt/argos/licenses
docker run --rm --entrypoint cat argos:local /opt/argos/licenses/THIRD-PARTY.txt
```

Remplacez `argos:local` par `argos:<ARGOS_VERSION>` si la variable est définie dans `.env`. Sur un conteneur démarré : `docker compose exec argos cat /opt/argos/licenses/NOTICE`.

Commande dédiée, hors de l'image (Maven et Node.js installés) : `mvn -B -f argos-server/pom.xml -P licences validate`, qui écrit `argos-server/target/THIRD-PARTY.txt` et copie les textes des licences retenues dans `argos-server/target/licenses/`.

## Images de base

| Image | Usage | Référence |
|---|---|---|
| `node:22-slim` | construction de l'IHM ; binaire Node.js du contrôle des licences | tag |
| `maven:3.9-eclipse-temurin-17` | construction du serveur | tag |
| `eclipse-temurin:17-jre` | exécution d'Argos | tag |
| `postgres:18` | base de données (`compose.yaml`) | tag |
| `quay.io/keycloak/keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}` | base de l'image Keycloak optimisée du profil `idp-local` (`deploiement/keycloak/Dockerfile`) | tag |
| `ollama/ollama:${ARGOS_OLLAMA_VERSION:-0.40.17}` | modèle d'IA local, désactivé par défaut (`compose.yaml`) | tag |

[DÉCISION — porteur du projet, 2026-11-19 (H2)] Les images de base Docker restent épinglées par tag, sans empreinte (digest). Un tag désigne une ligne de versions (`22`, `3.9`, `17`, `18`) : la reconstruction de l'image (`docker compose up -d --build`, ou `docker compose build --pull` pour forcer le téléchargement) reçoit les correctifs publiés sur cette ligne. En contrepartie, deux constructions à des dates différentes peuvent partir d'images de base différentes ; le contrôle des licences et les tests portent sur le serveur, non sur le contenu des images de base.

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

- `compose.yaml` validé par `docker compose config` : refusé sans `.env` en nommant chaque mot de passe manquant, accepté avec.
- Sauvegarde et restauration vérifiées par [`scripts/essai-sauvegarde-restauration.sh`](../../scripts/essai-sauvegarde-restauration.sh) : PostgreSQL arrêté, export tronqué, échecs répétés, rotation, base `keycloak` présente ou absente, restauration rejouée, archive tronquée, erreur de rechargement ; permissions 0600 et 0700 sous `umask 000` et lecture refusée à un autre compte sur un système de fichiers Linux. Comptes de base vérifiés par les tests `RolesBaseTest` sur une base initialisée par `initialiser-bases.sh`.
- Serveur MCP : protocole, authentification et outils vérifiés par les tests `io.argos.mcp` ; **non vérifié** : la connexion réelle d'un assistant IA configuré par `.mcp.json`.
- Construction de l'image vérifiée le 2026-11-19 sur un PC Windows (Docker Engine 29.9.3) : `docker build` réussit, l'étape de contrôle affiche « 194 dépendances inventoriées, 26 avis NOTICE reproduits », « 7 textes de licence copiés » puis « Aucune licence absente ni non admise », et `/opt/argos/licenses/` (dossier 0755) contient `NOTICE`, `THIRD-PARTY.txt`, `Apache-2.0.txt`, `BSD-2-Clause.txt`, `BSD-3-Clause.txt`, `EPL-2.0.txt`, `JDOM.txt`, `MIT-0.txt` et `MIT.txt`, propriété de root en lecture seule (0444), lisibles et non modifiables par l'utilisateur d'UID 1001. Un inventaire factice (licence GPL-3.0, EPL-2.0 hors des neuf API Jakarta, licence absente) donné au script dans l'étage `server` fait échouer `docker build` avec le code 1.
- Pile complète (`ia` et `idp-local`) démarrée sur un PC Windows le 2026-11-24 (Docker Compose 5.7.0, projet de test isolé) : quatre conteneurs `healthy`, IHM servie, API protégée (401 sans jeton) ; `argos` déclaré `unhealthy` 73 s après l'arrêt de la base, puis `healthy` après son redémarrage ; écriture refusée dans `/opt/argos` et hors de `/tmp`, acceptée dans `/tmp` ; `docker inspect` : `ReadonlyRootfs`, `CapDrop` `ALL`, `no-new-privileges`, journaux `json-file` bornés sur les quatre services ; `SHOW max_connections` : 40 ; arrêt sur `OutOfMemoryError` et redémarrages observés avec un tas réduit ; Keycloak arrêté en nommant `ARGOS_ADMIN_MOT_DE_PASSE_INITIAL` manquant, base `keycloak` restée vide.
- Parcours du PC rejoué le 2026-11-27 sur un PC Windows (Docker Engine 29.9.3, Docker Compose 5.7.0, révision `0140252` de `main`, projet Compose isolé `-p argos-sprint12`, ports 8095 et 8195) en suivant ce guide : `.env` tiré de `.env.example` avec les quatre mots de passe et le bloc [Keycloak fourni](#keycloak-fourni-profil-idp-local), `COMPOSE_PROFILES=idp-local` compris ; `docker compose up -d --build` : trois conteneurs `healthy` ; [Première connexion](#première-connexion) dans le navigateur de l'hôte : mot de passe provisoire, nouveau mot de passe, prénom et nom demandés, retour connecté sur le portail ; [Ajouter des utilisateurs](#ajouter-des-utilisateurs) : jeton par le flux d'appareil, espace créé, compte Keycloak créé, invitation, acceptation par la personne invitée, espace listé pour elle. Sans `ARGOS_OIDC_URL`, `argos` redémarre en boucle avec le message cité dans [Sur un PC](#sur-un-pc-windows-macos-linux). Bloc [Se connecter depuis un autre poste](#se-connecter-depuis-un-autre-poste) avec l'adresse réseau de l'hôte, sur un volume neuf : `GET /api/v1/system/info` et la politique de sécurité du contenu nomment l'émetteur à l'adresse réseau, le royaume importé porte cet émetteur, accepte les adresses de retour `http://<adresse>:<port>/` et `http://127.0.0.1:<port>/argos/retour` et refuse `http://localhost:<port>/`.
- **Non vérifié** : l'installation sur une machine vierge ; le démarrage réel sur le NAS, dont la prise en compte de `COMPOSE_PROFILES` par Container Manager ; la connexion du portail et du client desktop depuis un second poste ; l'emballage du client desktop avec les adresses d'un serveur distant ; la réception d'un e-mail par un relais SMTP réel (variables SMTP et adresse publique relevées dans le conteneur seulement). Ces vérifications forment la recette R-8 de [`recette-poste.md`](recette-poste.md).
