# ADR-0002 — Utiliser PostgreSQL comme stockage principal

- **Statut** : Proposé
- **Date** : 2026-09-24
- **Décideurs** : Non déterminé
- **Remplace** : —

## Contexte

ARGOS doit stocker données relationnelles, payloads externes variables, historique, recherche et éventuellement vecteurs. Multiplier les moteurs de données au MVP augmenterait la charge d’exploitation.

## Critères

- robustesse transactionnelle ;
- JSON flexible ;
- recherche initiale ;
- coût d’exploitation ;
- sauvegarde/restauration ;
- possibilité de RLS et pgvector si nécessaires.

## Options

### A — PostgreSQL principal

+ relationnel mature ;
+ JSONB, FTS, extensions ;
+ un seul moteur à opérer ;
- peut devenir insuffisant pour certains usages de recherche à très grande échelle.

### B — PostgreSQL + moteur de recherche dédié dès le MVP

+ fonctions de recherche spécialisées ;
- synchronisation, exploitation et coûts supplémentaires sans volumétrie prouvée.

### C — stockage documentaire principal

+ schéma flexible ;
- moins naturel pour les relations workspace/projet/technologie et métriques cohérentes.

## Décision

**Proposition : option A.** PostgreSQL porte le stockage métier, JSONB et FTS. `pgvector` n’est activé qu’après POC démontrant un besoin.

## Conséquences positives

- architecture initiale simple ;
- transactions cohérentes ;
- sauvegarde centralisée.

## Conséquences négatives

- nécessité de surveiller taille/indexation ;
- possible migration partielle si besoins de recherche massifs.

## Conséquences neutres

L’ajout d’un moteur spécialisé reste possible via un adapter/index secondaire.

## Validation

- benchmark FTS sur corpus représentatif ;
- test JSONB/index ;
- test backup/restore ;
- mesure taille et requêtes critiques.

## Traçabilité

- `arc42/05-vue-blocs.md`
- `arc42/08-concepts-transverses.md`
- Q-03, Q-05
