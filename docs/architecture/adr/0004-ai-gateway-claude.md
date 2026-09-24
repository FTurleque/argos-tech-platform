# ADR-0004 — Encapsuler Claude derrière un AI Gateway

- **Statut** : Proposé
- **Date** : 2026-09-24
- **Décideurs** : Non déterminé
- **Remplace** : —

## Contexte

Claude/Anthropic est le fournisseur IA envisagé pour ARGOS. Les appels IA concernent plusieurs cas : résumé, classification, extraction, analyse d’impact et synthèse projet. Appeler directement le SDK Anthropic depuis chaque module créerait verrouillage fournisseur, dispersion des prompts, absence de budget commun et risque de fuite de données.

## Critères

- confidentialité ;
- traçabilité ;
- maîtrise des coûts ;
- sorties structurées ;
- testabilité ;
- capacité multi-provider future.

## Options

### A — AI Gateway interne

+ point unique pour policy, prompts, quotas, cache, audit et validation ;
+ abstraction du fournisseur ;
+ contrôle des données envoyées ;
- composant supplémentaire à maintenir.

### B — appels Claude directs depuis les modules

+ implémentation initiale simple ;
- couplage fort et règles dupliquées ;
- gouvernance difficile.

### C — aucune IA externe

+ confidentialité maximale ;
- perte des fonctions de synthèse/raisonnement prévues ;
- pourrait rester un mode de fonctionnement pour données sensibles.

## Décision

**Proposition : option A.** Seul `AI Gateway` est autorisé à communiquer avec Claude. Le domaine lui fournit des demandes typées ; le gateway applique classification, budget, choix du modèle, prompt versionné et validation de réponse.

## Conséquences positives

- contrôle centralisé ;
- possibilité de remplacer/compléter Anthropic ;
- coûts mesurables ;
- sécurité renforcée.

## Conséquences négatives

- nécessite une API interne stable ;
- risque de devenir un composant générique surdimensionné si son scope n’est pas maîtrisé.

## Conséquences neutres

Le fournisseur initial reste Claude ; l’abstraction n’implique pas de supporter plusieurs fournisseurs dès le MVP.

## Validation

- POC structured output ;
- tests de politique PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED ;
- enregistrement modèle/prompt/tokens ;
- test de budget dépassé ;
- test de réponse invalide ;
- évaluation sur jeu de référence.

## Traçabilité

- `arc42/05-vue-blocs.md`
- `arc42/06-vue-execution.md`
- `arc42/08-concepts-transverses.md`
- Q-01, Q-02, Q-06
