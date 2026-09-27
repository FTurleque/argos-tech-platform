# ADR-0015 — Utiliser PostgreSQL FTS au MVP et différer pgvector

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Le MVP doit permettre recherche, filtrage et navigation sans introduire prématurément une chaîne RAG/embeddings.

## Critères de décision

1. Simplicité d’exploitation.
2. Pertinence suffisante pour les usages MVP.
3. Coût et réversibilité.
4. Besoin démontré de recherche sémantique.

## Options considérées

### Option A — PostgreSQL FTS puis pgvector si besoin mesuré

- réutilise la base principale ;
- faible coût d’exploitation ;
- évolution possible sans refonte du domaine.

### Option B — Recherche vectorielle/RAG dès le MVP

- recherche sémantique plus riche ;
- modèle d’embeddings et pipeline supplémentaires ;
- coût et complexité non justifiés à ce stade.

## Décision

Le MVP utilise **PostgreSQL Full Text Search**. `pgvector` et les embeddings ne sont introduits qu’après preuve qu’un cas d’usage ne peut pas être satisfait correctement par le FTS et les filtres structurés.

## Conséquences positives

- moins de composants ;
- coût réduit ;
- architecture plus facile à tester et exploiter.

## Conséquences négatives

- recherche sémantique différée ;
- benchmark à prévoir si la pertinence du FTS devient insuffisante.

## Méthode de validation

Jeu de requêtes métier représentatives, mesure de pertinence et latence avant toute activation de pgvector.

## Traçabilité

- **Exigences** : F07
- **Scénarios qualité** : p95 < 500 ms hors IA
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/04-strategie-solution.md`, `arc42/08-concepts-transverses.md`
