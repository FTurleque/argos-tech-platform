# ADR-0007 — Limiter n8n aux automatisations périphériques

- **Statut** : Proposé
- **Date** : 2026-09-24
- **Décideurs** : Non déterminé
- **Remplace** : —

## Contexte

ARGOS peut bénéficier d’automatisations pour notifications, digests ou intégrations ponctuelles. Placer les règles métier centrales dans n8n rendrait cependant la logique difficile à tester, versionner et faire évoluer avec le modèle de domaine.

## Critères

- maintenabilité ;
- testabilité ;
- portabilité ;
- gouvernance des règles métier ;
- coût/licence ;
- facilité d’intégration.

## Options

### A — n8n périphérique

+ pratique pour orchestration non critique et notifications ;
+ le cœur ARGOS reste autonome ;
- deux surfaces d’exploitation si n8n est déployé.

### B — n8n comme moteur de workflow central

+ rapidité pour certains flux ;
- logique métier fragmentée ;
- dépendance forte à l’outil et à sa licence.

### C — aucun n8n

+ stack plus simple ;
- moins de flexibilité pour automatisations ad hoc.

## Décision

**Proposition : option A.** Le backend ARGOS reste propriétaire des règles métier et de l’état. n8n peut consommer des événements/API pour envoyer notifications, intégrer des outils secondaires ou prototyper des workflows.

## Conséquences positives

- ARGOS reste testable sans n8n ;
- suppression/remplacement de n8n possible ;
- règles critiques restent dans le code versionné.

## Conséquences négatives

- certaines automatisations nécessiteront une API/événement explicite ;
- exploitation de n8n à prévoir s’il est utilisé.

## Conséquences neutres

n8n n’est pas obligatoire pour le MVP.

## Validation

- POC d’un digest/notification ;
- revue de la licence/version utilisée ;
- test ARGOS sans n8n ;
- aucune dépendance domaine → n8n.

## Traçabilité

- `arc42/04-strategie-solution.md`
- `arc42/05-vue-blocs.md`
