# ARGOS — Technology & Project Intelligence Platform

> **Observer. Corréler. Comprendre. Anticiper.**

ARGOS est une plateforme de **Technology Intelligence** et **Project Intelligence** destinée à centraliser la veille technologique, les changements de documentation, les releases, les vulnérabilités, les signaux GitHub/GitLab, la veille réglementaire et les indicateurs factuels d’avancement des projets.

Le nom **ARGOS** assume une double référence : **Argos Panoptès**, gardien aux multiples yeux de la mythologie grecque, et **Argo City** dans l’univers de Supergirl. L’ambition est la même : surveiller de nombreuses sources, relier les signaux utiles et donner une vision claire de l’écosystème technologique et des projets.

## Statut

**Phase : POC fonctionnel, porté à titre personnel et ouvert à tous les publics ([ADR-0025](docs/architecture/adr/0025-produit-ouvert-projet-personnel.md)).**

Le dépôt contient l'application et sa documentation :

- un serveur Quarkus (monolithe modulaire, API REST et serveur MCP en lecture seule), une IHM Vue.js 3 servie par le serveur et un client desktop Electron qui l'emballe ;
- les capacités spécifiées dans OpenSpec : 19 spécifications principales dans `openspec/specs/`, dont la collecte des avis de sécurité, l'inventaire SBOM, la corrélation, les abonnements, alertes et résumés, la veille RSS et web, la veille réglementaire, le radar des standards, la recherche, les comptes et accès, le serveur MCP et l'exploitation ; l'état de chaque capacité est tenu dans le [registre des spécifications](docs/specifications/README.md) ;
- un déploiement Docker Compose pour le PC et le serveur Synology du porteur ([ADR-0026](docs/architecture/adr/0026-hebergement-synology.md)), avec Keycloak auto-hébergé ([ADR-0027](docs/architecture/adr/0027-keycloak-idp-auto-heberge.md)) et un modèle d'IA local facultatif ([ADR-0029](docs/architecture/adr/0029-passerelle-ia-ollama-local.md)).

## Stack retenue

- **Java 17 minimum** et **Quarkus** pour le serveur et les API ;
- **Vue.js 3** pour l’IHM ;
- **PostgreSQL** comme stockage principal, schéma versionné par Flyway ([ADR-0032](docs/architecture/adr/0032-migrations-flyway.md)) ;
- **Keycloak** comme fournisseur d'identité OIDC ;
- **Electron** pour le client desktop, qui réutilise l’IHM Vue.js 3 ;
- **Ollama** comme modèle d'IA local, derrière la passerelle IA, facultatif ;
- **Docker Compose** sur le PC et sur le serveur Synology du porteur.

Voir [ADR-0019](docs/architecture/adr/0019-stack-java17-quarkus-vue3-postgresql.md) et [ADR-0022](docs/architecture/adr/0022-client-desktop-electron.md).

> Quarkus 3.40 est la dernière LTS compatible Java 17 (support jusqu’au 30/09/2027). Quarkus 4 exigera Java 21.

## Arborescence applicative

| Dossier | Contenu |
|---|---|
| [`argos-server/`](argos-server/README.md) | Monolithe modulaire Quarkus (Java 17) : un paquet par module d’arc42 §5, frontières vérifiées par ArchUnit, Flyway, OpenAPI, santé |
| [`argos-ui/`](argos-ui/) | IHM Vue.js 3 + Vite + TypeScript, partagée par le portail web et le desktop |
| [`argos-desktop/`](argos-desktop/) | Client desktop Electron : emballe `argos-ui`, notifications système, icône de zone de notification |
| [`e2e/`](e2e/README.md) | Tests de bout en bout (Playwright) du portail web, du client desktop et du serveur MCP, et campagne de performance |
| [`compose.yaml`](compose.yaml) | PostgreSQL 18 et Argos ; Keycloak avec le profil `idp-local`, Ollama avec le profil `ia` ; sur le PC comme sur le Synology |
| [`Dockerfile`](Dockerfile) | image unique IHM + API (x86-64) |
| [`deploiement/`](deploiement/) | image Keycloak optimisée et royaume livré (`deploiement/keycloak/`), création des comptes et des bases PostgreSQL (`deploiement/postgres/`) |
| [`scripts/`](scripts/) | sauvegarde et restauration des bases, contrôles exécutés par l'intégration continue |
| [`docs/`](docs/deploiement/synology-et-pc.md) | guide de déploiement (`docs/deploiement/`), architecture (`docs/architecture/`), fiches fonctionnelles (`docs/specifications/`) |
| [`openspec/`](openspec/config.yaml) | spécifications principales et changements OpenSpec |

## Démarrage local

Prérequis : JDK 17 ou plus, Maven 3.9, Node.js 22.18 ou plus (`^22.18.0 || >=23.6.0`), Docker démarré. Docker sert à la base de données et au Keycloak de développement que `mvn quarkus:dev` démarre lui-même (Dev Services), ainsi qu'aux tests du serveur.

```bash
# 1. Base de données : .env copié de .env.example, avec les quatre mots de passe de base
docker compose up -d postgres

# 2. Serveur (http://localhost:8080, OpenAPI sur /q/openapi, santé sur /q/health)
#    Mots de passe du rôle de connexion et du propriétaire (migrations), ceux du .env, lus dans l'environnement :
#    export ARGOS_DB_PASSWORD=… ARGOS_DB_OWNER_PASSWORD=…   (PowerShell : $env:ARGOS_DB_PASSWORD = '…')
#    quarkus:dev démarre aussi Keycloak sur http://localhost:8180 avec le royaume de test.
cd argos-server
mvn quarkus:dev          # tests : mvn verify (Docker requis : base et Keycloak jetables)

# 3a. Portail web (http://localhost:5173)
cd argos-ui
npm install
npm run dev

# 3b. ou client desktop Electron
npm --prefix argos-ui install
cd argos-desktop
npm install
npm run start            # construit l'IHM puis ouvre le desktop
npm run dev              # variante : recharge à chaud, avec « npm run dev » lancé dans argos-ui
npm run dist             # installeurs (Windows NSIS, Linux AppImage) dans argos-desktop/release/ (publication : ADR-0042)
```

**Connexion en développement.** Le royaume de test ([`argos-server/src/test/resources/keycloak/argos-realm.json`](argos-server/src/test/resources/keycloak/argos-realm.json)) déclare des comptes dont le mot de passe est égal à l'identifiant : `alice` est l'administratrice plateforme (`alice@argos.test`) ; `bob`, `carla` et les autres n'entrent dans un espace qu'après une invitation. « Se connecter » dans le portail ou le desktop ouvre la page de Keycloak.

L’URL du serveur se règle avec `VITE_ARGOS_API_URL` (voir `argos-ui/.env.example`) ; l'émetteur OIDC est lu dans la réponse publique du serveur. Le client desktop vise par défaut `http://localhost:8080` et `http://localhost:8180/realms/argos` ([Client desktop](docs/deploiement/synology-et-pc.md#client-desktop)).

Tests de bout en bout (Playwright : portail web, client desktop, serveur MCP, avec Keycloak et PostgreSQL de test) : `npm --prefix e2e ci` puis `npm --prefix e2e test` ; voir [`e2e/README.md`](e2e/README.md).

Liste des dépendances tierces du serveur, de leurs licences et de leurs avis NOTICE (commande dédiée, hors `mvn verify`) : voir [`argos-server/README.md`](argos-server/README.md#licences-des-dépendances).

### Vérifications locales

L'intégration continue ([workflow](.github/workflows/integration-continue.yml), [arc42 §8.11](docs/architecture/arc42/08-concepts-transverses.md#intégration-continue)) exécute ces commandes à chaque pull request et à chaque push sur `main`, sur JDK 17 ; elle ajoute la garde des migrations fusionnées et, quand `argos-server/pom.xml` ou le script de licences change, le contrôle des licences. Les mêmes commandes se rejouent sur le poste :

```bash
mvn -B -f argos-server/pom.xml clean verify                 # tests du serveur, seuil de couverture (Docker requis)
node scripts/verifier-couverture-modules.mjs                # seuils de couverture par module (après mvn verify)
npm --prefix argos-ui test && npm --prefix argos-ui run typecheck && npm --prefix argos-ui run build
npm --prefix argos-desktop run check                        # syntaxe, types (tsc --checkJs) et tests
node scripts/verifier-licences-npm.mjs --paquet argos-ui --paquet argos-desktop   # licences npm livrées (après npm ci)
npm --prefix e2e ci && npm --prefix e2e run typecheck       # types des parcours E2E (tsc --strict)
node --test scripts/*.test.mjs "e2e/performance/*.test.mjs"
node scripts/verifier-docs.mjs --rendu                      # liens, chemins cités, rendu Mermaid (mmdc), identifiants
node scripts/verifier-nommage.mjs                           # aucun identifiant d'une ou deux lettres
node scripts/verifier-configuration.mjs                     # variables : application.properties, compose.yaml, .env.example, guide
npm --prefix argos-ui audit --audit-level=high              # de même pour argos-desktop et e2e
```

`node scripts/verifier-docs.mjs --rendu` contrôle aussi les README des sous-projets et rend chaque diagramme avec Mermaid CLI (`npm install -g @mermaid-js/mermaid-cli@12.2.0`, version de l'intégration continue) ; sans mmdc, il échoue.

Le build du serveur se lance toujours avec `clean` : un build incrémental peut réutiliser des classes compilées par un autre JDK ou ne lancer aucun test. L'IDE doit compiler hors de `target/` (dossier de sortie propre à l'IDE), pour que ses classes ne se mêlent pas à celles de Maven. L'intégration continue impose un JDK 17 (profil `integration-continue`, `mvn -P integration-continue clean verify` pour reproduire la contrainte) ; en local, Java 17 ou plus est accepté. Le rapport de couverture JaCoCo est écrit dans `argos-server/target/site/jacoco/index.html`.

`node scripts/verifier-nommage.mjs` applique la règle de nommage d'[arc42 §8.16](docs/architecture/arc42/08-concepts-transverses.md#816-conventions-de-code) : aucun type, méthode, fonction, import, variable, champ ou paramètre d'une ou deux lettres, hors `id`, paramètres de type générique et contrats externes inscrits dans sa liste blanche ; `node --test scripts/*.test.mjs` l'exécute aussi sur tout le dépôt. Le contrôle est en Node pur : il ne fait pas partie de `mvn verify`, l'image Maven du build Docker n'ayant pas Node.

### Argos complet sous Docker (PC ou Synology)

Argos exige un fournisseur d'identité : sur une machine sans Keycloak, le profil `idp-local` en fournit un.

```bash
cp .env.example .env
# .env : les quatre mots de passe de base, puis COMPOSE_PROFILES=idp-local, ARGOS_OIDC_URL,
# ARGOS_OIDC_ISSUER_PUBLIC, ARGOS_ADMIN_EMAIL, ARGOS_ADMIN_MOT_DE_PASSE_INITIAL et ARGOS_KEYCLOAK_ADMIN_MOT_DE_PASSE
docker compose up -d --build   # http://localhost:8080, Keycloak sur http://localhost:8180
```

Valeurs, première connexion, ajout d'utilisateurs, accès depuis un autre poste, client desktop et sauvegardes : [guide de déploiement](docs/deploiement/synology-et-pc.md).

## Mission

ARGOS doit permettre de :

- agréger des flux RSS/Atom et des sources Web ;
- surveiller releases, dépendances, vulnérabilités et fins de support ;
- maintenir un inventaire projet par SBOM CycloneDX ;
- corréler déterministiquement les signaux avec les projets ;
- fournir un radar des versions et standards internes ;
- surveiller les évolutions réglementaires, publiées après validation par un expert métier ([ADR-0030](docs/architecture/adr/0030-veille-reglementaire-expert-metier.md)) ;
- résumer et expliquer par un modèle d'IA local derrière une passerelle, facultatif, sans faire de l’IA la source de vérité ;
- exposer un serveur **MCP en lecture seule** aux IDE ;
- produire tableaux de bord, alertes et synthèses ;
- permettre à chaque utilisateur de compléter le socle de veille de son équipe par une **veille personnelle** sans élargir ses droits d’accès.

## Principes d’architecture

- monolithe modulaire avant microservices ;
- modèle canonique d’événements ;
- PostgreSQL/JSONB/FTS, pgvector seulement si besoin démontré ;
- REST + OpenAPI ;
- déterministe d’abord, IA ensuite ;
- Mermaid comme source de vérité des diagrammes ;
- arc42 pour la documentation ;
- ADR Markdown pour les décisions ;
- gratuit/self-hosted d’abord lorsque pertinent.

## Documentation d’architecture

Le dossier principal se trouve dans [`docs/architecture/`](docs/architecture/README.md).

Il combine arc42, C4, Mermaid, ADR, scénarios qualité, registre des risques et baselines de cadrage.

Les exigences fonctionnelles sont spécifiées avec OpenSpec (`openspec/`, schéma `detailed-uml`) ; les fiches par capacité et le suivi de couverture se trouvent dans [`docs/specifications/`](docs/specifications/README.md). Les changements en cours se listent par `openspec list` ; les vérifications qui exigent un environnement réel sont tenues dans la [recette sur le poste installé](docs/deploiement/recette-poste.md).

## Licence

ARGOS est **source-available** sous la **PolyForm Internal Use License 1.0.0**. L’utilisation personnelle et interne à une entreprise est autorisée ; la redistribution ou la revente à des tiers ne l’est pas sous cette licence.

Le texte applicable est celui du fichier [`LICENSE`](LICENSE).
