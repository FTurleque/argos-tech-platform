# ADR-0019 — Retenir Java 17+, Quarkus, Vue.js 3 et PostgreSQL

- **Statut** : Accepté
- **Date** : 2026-09-27
- **Décideurs** : cadrage ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

La baseline v2.3 laissait encore ouverts le framework backend et le frontend. Le cadrage v2.4 fixe la stack afin d’éviter de lancer le POC avec des choix encore flottants.

## Critères de décision

1. Java 17 minimum.
2. Stack maîtrisable en entreprise.
3. API REST/OpenAPI claire.
4. Frontend moderne séparé du backend.
5. Compatibilité avec PostgreSQL et le serveur MCP.
6. Déploiement simple en conteneurs.

## Options considérées

### Option A — Java 17+ / Quarkus / Vue.js 3 / PostgreSQL

- backend/API Quarkus ;
- IHM Vue.js 3 ;
- PostgreSQL comme stockage principal ;
- MCP via l’écosystème Quarkus/Quarkiverse ;
- compatible avec un monolithe modulaire et Docker Compose.

### Option B — Spring Boot / frontend non déterminé

- écartée pour ARGOS ;
- maintiendrait des choix non finalisés avant le POC.

## Décision

ARGOS utilise :

- **Java 17 minimum** ;
- **Quarkus** pour le backend et les API ;
- **Vue.js 3** pour l’IHM ;
- **PostgreSQL** pour le stockage principal.

Spring Boot et Spring AI ne font pas partie de la stack ARGOS.

## Conséquences positives

- stack définie avant le premier commit applicatif ;
- cohérence entre API, MCP et déploiement ;
- réduction des décisions techniques à prendre pendant le POC ;
- frontend explicitement séparé du backend.

## Conséquences négatives

- les exemples et documents existants mentionnant Spring doivent être corrigés lorsqu’ils décrivent ARGOS lui-même ;
- les extensions Quarkus choisies devront être validées par le POC.

## Méthode de validation

- application Quarkus Java 17 minimale ;
- API REST/OpenAPI ;
- SPA Vue.js 3 ;
- connexion PostgreSQL + migrations ;
- Docker Compose local ;
- prototype MCP en lecture seule.

## Traçabilité

- **Baseline** : `../baseline-v2.4.md`
- **Contraintes** : `../arc42/02-contraintes.md`
- **Stratégie** : `../arc42/04-strategie-solution.md`
- **Vue des blocs** : `../arc42/05-vue-blocs.md`
- **Déploiement** : `../arc42/07-vue-deploiement.md`
