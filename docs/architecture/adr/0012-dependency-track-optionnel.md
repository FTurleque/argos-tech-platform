# ADR-0012 — Garder OWASP Dependency-Track optionnel au POC

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Dependency-Track peut apporter ingestion SBOM, politiques et tableaux de bord. ARGOS doit toutefois éviter d’ajouter une brique d’exploitation avant d’avoir prouvé qu’elle simplifie réellement le MVP.

## Critères de décision

1. Gain de couverture et de gouvernance SBOM.
2. Coût d’exploitation et d’intégration.
3. Évitement de doublons avec `inventory`, `vuln-intel` et `impact-engine`.
4. Compatibilité CycloneDX.

## Options considérées

### Option A — Dependency-Track optionnel

- l’évaluer sur les deux projets pilotes ;
- ne l’adopter que si le gain est mesurable.

### Option B — Analyse SBOM entièrement interne

- moins de composants ;
- davantage de logique à développer et maintenir.

## Décision

Dependency-Track reste **optionnel**. Le POC compare son apport à une ingestion CycloneDX minimale directement dans ARGOS. L’adoption définitive nécessite un résultat mesuré.

## Conséquences positives

- évite une dépendance prématurée ;
- permet une comparaison factuelle.

## Conséquences négatives

- deux chemins peuvent être prototypés brièvement ;
- décision reportée après mesure.

## Méthode de validation

Comparer couverture, complexité, exploitation, temps d’intégration et capacité de requête utiles à ARGOS.

## Traçabilité

- **Exigences** : F03, F04
- **Scénarios qualité** : maintenabilité, simplicité d’exploitation
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/04-strategie-solution.md`
