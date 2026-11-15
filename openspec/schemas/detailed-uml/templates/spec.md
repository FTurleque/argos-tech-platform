# Delta de spécification : <!-- capability-path -->

<!--
Règles de forme conservées par OpenSpec (ne pas ajouter d'autre section ## : l'archivage les ignore) :
- ## Purpose : nouvelle capacité uniquement ; supprimer pour une capacité existante.
- Garder seulement les sections de delta utilisées (ADDED / MODIFIED / REMOVED / RENAMED).
- Description d'exigence ≤ 500 caractères, contenant MUST ou SHALL : « DOIT (MUST) », « NE DOIT PAS (MUST NOT) ».
- Chaque scénario : **GIVEN** ou **WHEN**, puis **THEN** ; **AND** pour les effets persistés ou leur absence.
- Liens depuis la racine du dépôt : /docs/…, jamais ../…

Sujets à couvrir par les exigences et scénarios lorsqu'ils s'appliquent :
préconditions et déclencheurs ; entrées, sorties et validations ; règles métier ; droits d'accès et refus ;
effets de bord observables ; alternatives, erreurs et cas limites ; rejeu et concurrence ;
qualités mesurables connues ; critères d'acceptation. Le contexte détaillé (acteurs, périmètre,
diagrammes métier) va dans la fiche durable /docs/specifications/<capability-path>.md.
-->

## Purpose

<!-- Une ou deux phrases (≥ 50 caractères) sur la finalité de la capacité, puis : Fiche : [/docs/specifications/<capability-path>.md](/docs/specifications/<capability-path>.md). -->

## ADDED Requirements

### Requirement: <!-- PRÉFIXE-NN Titre court -->
<!-- Une seule règle observable : « Le système DOIT (MUST) … ». -->
Source : <!-- ADR-XXXX (statut) | /docs/architecture/arc42/… §x.y | demande | fichier/test -->. Statut : <!-- [EXIGENCE] | [PROPOSITION] -->.

#### Scenario: <!-- PRÉFIXE-NN-S1 Cas nominal -->
- **GIVEN** <!-- situation initiale : données présentes, droits, état -->
- **WHEN** <!-- action ou événement déclencheur -->
- **THEN** <!-- résultat observable -->
- **AND** <!-- effet persisté, ou « aucune donnée n'est créée ni modifiée » -->

#### Scenario: <!-- PRÉFIXE-NN-S2 Erreur, refus ou cas limite -->
- **GIVEN** <!-- situation initiale -->
- **WHEN** <!-- événement -->
- **THEN** <!-- erreur attendue : nature, code ou message observable -->
- **AND** <!-- absence d'effet persisté / état inchangé -->

## MODIFIED Requirements

<!-- Copier le bloc ENTIER depuis openspec/specs/<capability-path>/spec.md (en-tête identique, description, tous les scénarios), puis le modifier. -->

## REMOVED Requirements

### Requirement: <!-- en-tête exact existant -->
**Reason**: <!-- pourquoi l'exigence est retirée -->
**Migration**: <!-- comment les utilisateurs ou systèmes en aval s'adaptent -->

## RENAMED Requirements

- FROM: `### Requirement: <!-- en-tête exact existant -->`
- TO: `### Requirement: <!-- nouvel en-tête, même identifiant -->`
