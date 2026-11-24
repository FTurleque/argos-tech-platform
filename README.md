# ARGOS — Technology & Project Intelligence Platform

> **Observer. Corréler. Comprendre. Anticiper.**

ARGOS est une plateforme de **Technology Intelligence** et **Project Intelligence** destinée à centraliser la veille technologique, les changements de documentation, les releases, les vulnérabilités, les signaux GitHub/GitLab, la veille réglementaire et les indicateurs factuels d’avancement des projets.

Le nom **ARGOS** assume une double référence : **Argos Panoptès**, gardien aux multiples yeux de la mythologie grecque, et **Argo City** dans l’univers de Supergirl. L’ambition est la même : surveiller de nombreuses sources, relier les signaux utiles et donner une vision claire de l’écosystème technologique et des projets.

## Statut

**Phase : conception / cadrage POC — socle applicatif initialisé.**

Le dépôt contient la documentation d’architecture, les spécifications OpenSpec et un **socle applicatif sans fonctionnalité métier** : serveur Quarkus, IHM Vue.js 3, client desktop Electron et base PostgreSQL locale. La baseline active est **v2.4**.

## Stack retenue

- **Java 17 minimum** ;
- **Quarkus** pour le backend et les API ;
- **Vue.js 3** pour l’IHM ;
- **PostgreSQL** comme stockage principal ;
- **Docker Compose** sur le PC et sur le serveur Synology du porteur (ADR-0026) ; hébergement en ligne plus tard ;
- **Electron** pour le client desktop, qui réutilise l’IHM Vue.js 3 (proposé).

Voir [ADR-0019](docs/architecture/adr/0019-stack-java17-quarkus-vue3-postgresql.md) et [ADR-0022](docs/architecture/adr/0022-client-desktop-electron.md).

> Quarkus 3.40 est la dernière LTS compatible Java 17 (support jusqu’au 30/09/2027). Quarkus 4 exigera Java 21.

## Arborescence applicative

| Dossier | Contenu |
|---|---|
| [`argos-server/`](argos-server/) | Monolithe modulaire Quarkus (Java 17) : un paquet par module d’arc42 §5, frontières vérifiées par ArchUnit, Flyway, OpenAPI, santé |
| [`argos-ui/`](argos-ui/) | IHM Vue.js 3 + Vite + TypeScript, partagée par le portail web et le desktop |
| [`argos-desktop/`](argos-desktop/) | Client desktop Electron : emballe `argos-ui`, notifications système, icône de zone de notification |
| [`e2e/`](e2e/README.md) | Tests de bout en bout du portail web (Playwright) |
| [`compose.yaml`](compose.yaml) | PostgreSQL 18 et Argos complet, sur le PC comme sur le Synology |
| [`Dockerfile`](Dockerfile) | image unique IHM + API (x86-64) |
| [`docs/deploiement/`](docs/deploiement/synology-et-pc.md) | guide de déploiement Synology DS1522+ et PC, sauvegardes |

## Démarrage local

Prérequis : JDK 17 ou plus, Maven 3.9, Node.js 22.12 ou plus, Docker.

```bash
# 1. Base de données
docker compose up -d postgres

# 2. Serveur (http://localhost:8080, OpenAPI sur /q/openapi, santé sur /q/health)
cd argos-server
mvn quarkus:dev          # tests : mvn verify (Docker requis : base jetable)

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
npm run dist             # installeurs (Windows NSIS, Linux AppImage) dans argos-desktop/release/
```

L’URL du serveur se règle avec `VITE_ARGOS_API_URL` (voir `argos-ui/.env.example`).

Tests de bout en bout du portail web (Playwright, Keycloak et PostgreSQL de test) : `npm --prefix e2e ci` puis `npm --prefix e2e test` ; voir [`e2e/README.md`](e2e/README.md).

Liste des dépendances tierces du serveur, de leurs licences et de leurs avis NOTICE (commande dédiée, hors `mvn verify`) : voir [`argos-server/README.md`](argos-server/README.md#licences-des-dépendances).

### Vérifications locales

L'intégration continue ([workflow](.github/workflows/integration-continue.yml), [arc42 §8.11](docs/architecture/arc42/08-concepts-transverses.md#intégration-continue)) exécute ces commandes à chaque pull request et à chaque push sur `main`, sur JDK 17 ; elle ajoute la garde des migrations fusionnées et, quand `argos-server/pom.xml` ou le script de licences change, le contrôle des licences. Les mêmes commandes se rejouent sur le poste :

```bash
mvn -B -f argos-server/pom.xml clean verify                 # tests du serveur, seuil de couverture (Docker requis)
npm --prefix argos-ui test && npm --prefix argos-ui run typecheck && npm --prefix argos-ui run build
npm --prefix argos-desktop run check                        # syntaxe, types (tsc --checkJs) et tests
npm --prefix e2e ci && npm --prefix e2e run typecheck       # types des parcours E2E (tsc --strict)
node --test scripts/*.test.mjs "e2e/performance/*.test.mjs"
node scripts/verifier-docs.mjs                              # liens, Mermaid, identifiants d'exigences
node scripts/verifier-nommage.mjs                           # aucun identifiant d'une ou deux lettres
npm --prefix argos-ui audit --audit-level=high              # de même pour argos-desktop et e2e
```

Le build du serveur se lance toujours avec `clean` : un build incrémental peut réutiliser des classes compilées par un autre JDK ou ne lancer aucun test. L'IDE doit compiler hors de `target/` (dossier de sortie propre à l'IDE), pour que ses classes ne se mêlent pas à celles de Maven. L'intégration continue impose un JDK 17 (profil `integration-continue`, `mvn -P integration-continue clean verify` pour reproduire la contrainte) ; en local, Java 17 ou plus est accepté. Le rapport de couverture JaCoCo est écrit dans `argos-server/target/site/jacoco/index.html`.

`node scripts/verifier-nommage.mjs` applique la règle de nommage d'[arc42 §8.16](docs/architecture/arc42/08-concepts-transverses.md#816-conventions-de-code) : aucun type, méthode, fonction, import, variable, champ ou paramètre d'une ou deux lettres, hors `id`, paramètres de type générique et contrats externes inscrits dans sa liste blanche ; `node --test scripts/*.test.mjs` l'exécute aussi sur tout le dépôt. Le contrôle est en Node pur : il ne fait pas partie de `mvn verify`, l'image Maven du build Docker n'ayant pas Node.

### Argos complet sous Docker (PC ou Synology)

```bash
cp .env.example .env
docker compose up -d --build   # http://localhost:8080
```

Détails, accès distant et sauvegardes : [guide de déploiement](docs/deploiement/synology-et-pc.md).

## Mission

ARGOS doit permettre de :

- agréger des flux RSS/Atom et des sources Web ;
- surveiller releases, dépendances, vulnérabilités et fins de support ;
- maintenir un inventaire projet par SBOM CycloneDX ;
- corréler déterministiquement les signaux avec les projets ;
- fournir un radar des versions et standards internes ;
- surveiller les évolutions réglementaires utiles aux MKP ;
- utiliser Claude derrière un **AI Gateway** pour synthétiser et expliquer, sans faire de l’IA la source de vérité ;
- exposer un serveur **MCP en lecture seule** aux IDE ;
- produire tableaux de bord, alertes et synthèses ;
- permettre à chaque utilisateur de compléter le socle de veille de son équipe par une **veille personnelle** sans élargir ses droits d’accès.

## POC v2.4

Le POC est estimé à **20–24 jours-homme de travail effectif**, soit environ un mois concentré. Une démonstration peut être réalisée localement avec Docker Compose sans attendre la VM.

En contexte entreprise, VM, proxy/SSO, RSSI/DPO, pilotes et urgences opérationnelles peuvent porter la durée à **6–10 semaines calendaires**. Le go/no-go intervient à l’issue du POC, pas à une date artificiellement fixe.

## IA : développement et runtime

Deux sujets sont séparés :

- pour le **développement intensif**, le projet ne doit pas être dimensionné sur un plan Claude Pro seul ; une capacité supérieure ou des crédits d’usage doivent être prévus et mesurés ;
- pour le **runtime ARGOS**, Claude API/Console est facturée séparément et tous les appels passent par l’AI Gateway avec quotas et plafond.

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

Il combine arc42, C4, Mermaid, ADR, scénarios qualité, registre des risques et baseline POC.

Les exigences fonctionnelles sont spécifiées avec OpenSpec (`openspec/`, schéma `detailed-uml`) ; les fiches par capacité et le suivi de couverture se trouvent dans [`docs/specifications/`](docs/specifications/README.md).

## Prochaines étapes

1. ~~initialiser Maven/Quarkus en Java 17~~ (socle `argos-server/`) ;
2. ~~initialiser Vue.js 3~~ (`argos-ui/`) ;
3. ~~créer PostgreSQL + migrations~~ (socle Flyway ; outil de migration à confirmer par ADR, arc42 §9.3) ;
4. ~~créer Docker Compose local~~ (`compose.yaml`) ;
5. ~~mettre en place les frontières de modules et tests d’architecture~~ (`ModuleBoundariesTest`) ;
6. connecter OSV et importer une première SBOM ;
7. produire la première corrélation et alerte ;
8. prototyper le MCP lecture seule ;
9. engager en parallèle VM/proxy/SSO/RSSI/DPO ;
10. mesurer les KPI et coûts avant le MVP.

## Licence

ARGOS est **source-available** sous la **PolyForm Internal Use License 1.0.0**. L’utilisation personnelle et interne à une entreprise est autorisée ; la redistribution ou la revente à des tiers ne l’est pas sous cette licence.

Le texte applicable est celui du fichier [`LICENSE`](LICENSE).
