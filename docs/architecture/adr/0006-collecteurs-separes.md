# ADR-0006 — Maintenir FreshRSS et changedetection.io comme systèmes séparés

- **Statut** : Proposé
- **Date** : 2026-09-24
- **Décideurs** : Non déterminé
- **Remplace** : —

## Contexte

ARGOS a besoin de collecte RSS/Atom et de détection de changements Web. FreshRSS et changedetection.io fournissent déjà ces capacités. Réimplémenter immédiatement ces moteurs détournerait le projet de sa valeur principale : normalisation, corrélation et Project Intelligence.

## Critères

- délai de mise en œuvre ;
- maintenance ;
- découplage licence/technologie ;
- facilité de remplacement ;
- qualité de collecte.

## Options

### A — Services séparés consommés via API

+ réutilise des outils spécialisés ;
+ limite le code ARGOS ;
+ séparation claire ;
- dépendance opérationnelle à plusieurs services.

### B — Incorporer leur code/fonctions dans ARGOS

- couplage fort ;
- complexité licence/maintenance ;
- scope plus large.

### C — Réécrire les collecteurs

+ contrôle total ;
- coût important sans avantage métier démontré.

## Décision

**Proposition : option A.** ARGOS consomme FreshRSS et changedetection.io derrière des adapters. Leur base interne n’est jamais utilisée directement.

## Conséquences positives

- POC accéléré ;
- composants remplaçables ;
- responsabilités nettes.

## Conséquences négatives

- monitoring et sauvegarde de services additionnels ;
- compatibilité API à surveiller.

## Conséquences neutres

Un adapter natif RSS pourra être créé plus tard si FreshRSS devient inutile ou contraignant.

## Validation

- POC API FreshRSS ;
- POC API changedetection.io ;
- vérification licences/version retenue ;
- test de reprise et rate limits.

## Traçabilité

- `arc42/03-contexte-perimetre.md`
- `arc42/04-strategie-solution.md`
- Q-04
