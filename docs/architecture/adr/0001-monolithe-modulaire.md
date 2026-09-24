# ADR-0001 — Adopter un monolithe modulaire pour la première version

- **Statut** : Proposé
- **Date** : 2026-09-24
- **Décideurs** : Non déterminé
- **Remplace** : —

## Contexte

ARGOS doit couvrir ingestion, veille, Project Intelligence, corrélation, recherche, IA et alertes. Le dépôt ne contient encore aucun code. Distribuer immédiatement ces responsabilités en microservices augmenterait le coût de développement, de test, de déploiement et d’observabilité sans preuve de besoin.

## Critères

- simplicité d’exploitation ;
- maintien de frontières métier explicites ;
- capacité à extraire un module plus tard ;
- coût initial faible ;
- testabilité.

## Options

### A — Monolithe modulaire

+ déploiement simple ;
+ transactions locales ;
+ faible coût d’exploitation ;
- nécessite des règles strictes de dépendance interne.

### B — Microservices dès le départ

+ scalabilité et déploiement indépendants ;
- forte complexité distribuée ;
- contrats réseau, observabilité, sécurité et cohérence à gérer immédiatement.

### C — Monolithe non modulaire

+ très simple au démarrage ;
- dette structurelle rapide et extraction difficile.

## Décision

**Proposition : option A.** Un backend déployable unique, structuré en modules métier explicites avec ports/adapters.

## Conséquences positives

- POC/MVP plus rapide ;
- déploiement et debug simplifiés ;
- meilleure cohérence transactionnelle.

## Conséquences négatives

- scalabilité indépendante non immédiate ;
- discipline nécessaire pour éviter les dépendances circulaires.

## Conséquences neutres

Une extraction ultérieure en service séparé reste possible si une métrique le justifie.

## Validation

- tests d’architecture des dépendances ;
- revue des packages/modules ;
- mesure charge/volumétrie avant toute extraction.

## Traçabilité

- `arc42/04-strategie-solution.md`
- `arc42/05-vue-blocs.md`
- scénarios Q-04 maintenabilité
