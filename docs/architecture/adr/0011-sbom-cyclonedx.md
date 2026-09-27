# ADR-0011 — Utiliser CycloneDX comme inventaire SBOM de référence

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

ARGOS doit répondre rapidement à « quels projets sont touchés ? ». Une déclaration manuelle des dépendances ne garantit ni fraîcheur ni exhaustivité, notamment pour les dépendances transitives.

## Critères de décision

1. Inventaire automatisable en CI.
2. Dépendances directes et transitives.
3. Identifiants PURL exploitables pour la corrélation.
4. Format standard et portable.

## Options considérées

### Option A — SBOM CycloneDX générée en CI

- format standard OWASP ;
- PURL ;
- compatible avec plusieurs outils d’analyse ;
- reproductible à chaque build.

### Option B — Lecture des manifestes uniquement

- simple au démarrage ;
- transitivité et résolution réelle moins fiables ;
- qualité variable selon l’écosystème.

## Décision

La **SBOM CycloneDX produite par la CI de la branche principale** constitue l’inventaire de référence. La lecture de manifestes reste un mécanisme de repli lorsque la SBOM n’est pas encore disponible.

## Conséquences positives

- rapprochement déterministe package/version ;
- couverture des dépendances transitives ;
- mesure objective de fraîcheur de l’inventaire.

## Conséquences négatives

- ajout d’un job CI sur les projets suivis ;
- nécessité de gérer plusieurs versions de schéma CycloneDX.

## Conséquences neutres / compromis

ARGOS doit afficher l’âge de la dernière SBOM et dégrader le niveau de confiance si elle est absente ou obsolète.

## Méthode de validation

- génération sur deux projets pilotes ;
- import des PURL et versions ;
- test d’une dépendance transitive vulnérable ;
- mesure de couverture et de fraîcheur.

## Traçabilité

- **Exigences** : F03, F04
- **Scénarios qualité** : réponse « projets touchés » < 5 min
- **Diagrammes** : synchronisation GitLab, impact-engine
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/05-vue-blocs.md`, `arc42/06-vue-execution.md`
