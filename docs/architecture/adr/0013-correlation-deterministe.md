# ADR-0013 — Rendre la corrélation signal ↔ projet déterministe

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

La valeur centrale d’ARGOS est de relier un signal externe aux projets réellement concernés. Cette décision ne doit pas dépendre d’un LLM lorsqu’un identifiant structuré et une version sont disponibles.

## Critères de décision

1. Explicabilité.
2. Reproductibilité.
3. Faux positifs/faux négatifs mesurables.
4. Fonctionnement sans fournisseur IA.

## Options considérées

### Option A — Règles déterministes + IA en appoint

- PURL + plage de versions pour la sécurité ;
- taxonomie/référentiel pour releases et EOL ;
- IA uniquement pour les contenus non structurés et sans déclenchement critique autonome.

### Option B — Corrélation principalement par LLM

- souple sur le texte libre ;
- moins reproductible, plus coûteuse et moins explicable.

## Décision

Le calcul d’impact est **déterministe d’abord**. Les niveaux sont `exact`, `probable` et `à vérifier`. Une suggestion IA ne déclenche jamais seule une alerte critique.

## Conséquences positives

- auditabilité ;
- maîtrise du coût ;
- dégradation sans IA possible.

## Conséquences négatives

- maintien de règles par écosystème ;
- nécessité d’un dictionnaire métier pour les signaux non techniques.

## Méthode de validation

Jeu de référence annoté, mesure précision/rappel et tests de régression des règles.

## Traçabilité

- **Exigences** : F02, F04, F11, F14
- **Scénarios qualité** : pertinence ≥ 80 %, aucune alerte bloquée sans IA
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/06-vue-execution.md`, `arc42/08-concepts-transverses.md`
