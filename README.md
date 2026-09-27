# ARGOS — Technology & Project Intelligence Platform

> **Observer. Corréler. Comprendre. Anticiper.**

ARGOS est une plateforme de **Technology Intelligence** et **Project Intelligence** destinée à centraliser la veille technologique, les changements de documentation, les releases, les vulnérabilités, les signaux GitHub/GitLab, la veille réglementaire et les indicateurs factuels d’avancement des projets.

Le nom **ARGOS** assume une double référence : **Argos Panoptès**, gardien aux multiples yeux de la mythologie grecque, et **Argo City** dans l’univers de Supergirl. L’ambition est la même : surveiller de nombreuses sources, relier les signaux utiles et donner une vision claire de l’écosystème technologique et des projets.

## Statut

**Phase : conception / cadrage POC.**

Le dépôt contient la documentation d’architecture et la licence, mais pas encore l’implémentation applicative. La baseline active est **v2.4**.

## Stack retenue

- **Java 17 minimum** ;
- **Quarkus** pour le backend et les API ;
- **Vue.js 3** pour l’IHM ;
- **PostgreSQL** comme stockage principal ;
- **Docker Compose** pour le POC local, puis VM Linux interne pour le POC partagé/MVP.

Voir [ADR-0019](docs/architecture/adr/0019-stack-java17-quarkus-vue3-postgresql.md).

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

## Prochaines étapes

1. initialiser Maven/Quarkus en Java 17 ;
2. initialiser Vue.js 3 ;
3. créer PostgreSQL + migrations ;
4. créer Docker Compose local ;
5. mettre en place les frontières de modules et tests d’architecture ;
6. connecter OSV et importer une première SBOM ;
7. produire la première corrélation et alerte ;
8. prototyper le MCP lecture seule ;
9. engager en parallèle VM/proxy/SSO/RSSI/DPO ;
10. mesurer les KPI et coûts avant le MVP.

## Licence

ARGOS est **source-available** sous la **PolyForm Internal Use License 1.0.0**. L’utilisation personnelle et interne à une entreprise est autorisée ; la redistribution ou la revente à des tiers ne l’est pas sous cette licence.

Le texte applicable est celui du fichier [`LICENSE`](LICENSE).
