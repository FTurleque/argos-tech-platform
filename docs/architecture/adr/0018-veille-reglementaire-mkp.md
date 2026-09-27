# ADR-0018 — Intégrer la veille réglementaire avec validation MKP obligatoire

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Les produits RH destinés au secteur public dépendent d’évolutions réglementaires fréquentes. ARGOS doit appliquer la même logique de collecte, qualification, échéance et diffusion que pour les signaux techniques, sans se substituer à l’expertise métier.

## Critères de décision

1. Traçabilité vers les textes officiels.
2. Distinction publication / entrée en vigueur.
3. Validation humaine avant diffusion aux développeurs.
4. Aucun avis juridique autonome produit par l’IA.

## Options considérées

### Option A — Module `reg-intel` intégré à ARGOS

- modèle commun de signal et d’échéance ;
- rattachement aux domaines métier et modules produits ;
- fiche d’analyse IA validée par un MKP.

### Option B — Outil séparé de veille juridique

- spécialisation forte ;
- duplication des mécanismes de collecte, alerting, recherche et gouvernance.

## Décision

La veille réglementaire est intégrée dans ARGOS via un module dédié `reg-intel`. Le **texte officiel reste la référence** et toute fiche d’analyse destinée aux équipes de développement nécessite une **validation MKP obligatoire**.

## Conséquences positives

- vision unifiée technique + réglementaire ;
- échéances et impacts produit traçables ;
- garde-fou métier explicite.

## Conséquences négatives

- dictionnaire métier à maintenir ;
- workflow de validation à concevoir ;
- disponibilité des MKP nécessaire.

## Méthode de validation

POC sur le domaine Paie : collecte d’un texte officiel, qualification, extraction de date, génération d’une fiche structurée, correction/validation MKP et diffusion après validation uniquement.

## Traçabilité

- **Exigences** : F14, F15
- **Scénarios qualité** : fiche disponible sous 1 jour ouvré ; aucun texte diffusé aux développeurs avant validation MKP
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/03-contexte-perimetre.md`, `arc42/06-vue-execution.md`, `arc42/08-concepts-transverses.md`
