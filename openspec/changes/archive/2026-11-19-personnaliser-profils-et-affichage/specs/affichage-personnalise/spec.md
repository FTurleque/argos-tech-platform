# Delta de spécification : affichage-personnalise

## Purpose

Permettre à chaque utilisateur de choisir la mise en page d'Argos parmi un catalogue, sans que ce choix change le contenu, les droits ou les mentions affichées. Fiche : [/docs/specifications/affichage-personnalise.md](/docs/specifications/affichage-personnalise.md).

## ADDED Requirements

### Requirement: AFF-01 Choix de la mise en page dans le catalogue
Le système DOIT (MUST) permettre à un utilisateur authentifié de choisir sa mise en page parmi celles publiées au catalogue ; le catalogue initial comprend `classique`, `journal`, `fantaisie` et `gazette`.
Source : demande du porteur (2026-11-15) ; ADR-0023 (Accepté). Statut : [PROPOSITION].

#### Scenario: AFF-01-S1 Choix d'une mise en page publiée
- **GIVEN** un utilisateur authentifié dont la mise en page est `classique`
- **WHEN** il choisit la mise en page `gazette`
- **THEN** Argos s'affiche avec la mise en page `gazette`
- **AND** sa préférence de mise en page vaut `gazette`

#### Scenario: AFF-01-S2 Mise en page absente du catalogue
- **GIVEN** un utilisateur authentifié
- **WHEN** il demande une mise en page dont l'identifiant n'est pas publié au catalogue
- **THEN** la demande est refusée avec un message indiquant que la mise en page n'existe pas
- **AND** sa préférence de mise en page est inchangée

### Requirement: AFF-02 Contenu et mentions identiques dans toute mise en page
Le système DOIT (MUST) restituer, pour un même utilisateur et un même instant, les mêmes éléments de veille, les mêmes impacts et les mêmes mentions obligatoires (source, date, niveau de confiance, résumé produit par l'IA) quelle que soit la mise en page choisie.
Source : ADR-0023 (Accepté) ; dossier v2.4 §A.3 ; ADR-0013 (Proposé). Statut : [PROPOSITION].

#### Scenario: AFF-02-S1 Changement de mise en page
- **GIVEN** un utilisateur dont la veille comporte trois éléments, dont une alerte avec un résumé produit par l'IA
- **WHEN** il passe de la mise en page `classique` à la mise en page `journal`
- **THEN** les trois mêmes éléments sont restitués
- **AND** l'alerte porte toujours la mention indiquant que son résumé est produit par l'IA

#### Scenario: AFF-02-S2 Élément non autorisé
- **GIVEN** un impact sur un projet que l'utilisateur n'a pas le droit de voir
- **WHEN** l'utilisateur consulte sa veille dans n'importe quelle mise en page du catalogue
- **THEN** cet impact n'est restitué dans aucune d'elles

### Requirement: AFF-03 Préférence partagée entre le portail web et le desktop
Le système DOIT (MUST) enregistrer la mise en page choisie comme préférence de l'utilisateur côté serveur, et l'appliquer dans le portail web comme dans le client desktop.
Source : ADR-0023 (Accepté) ; ADR-0022 (Proposé). Statut : [PROPOSITION].

#### Scenario: AFF-03-S1 Choix fait sur le web, retrouvé sur le desktop
- **GIVEN** un utilisateur qui choisit la mise en page `fantaisie` dans le portail web
- **WHEN** il ouvre ensuite le client desktop avec le même compte
- **THEN** le client desktop s'affiche avec la mise en page `fantaisie`

### Requirement: AFF-04 Mise en page par défaut
Le système DOIT (MUST) appliquer la mise en page `classique` à un utilisateur qui n'a fait aucun choix, ou dont la mise en page choisie a été retirée du catalogue.
Source : ADR-0023 (Accepté). Statut : [PROPOSITION].

#### Scenario: AFF-04-S1 Premier accès
- **GIVEN** un utilisateur qui n'a jamais choisi de mise en page
- **WHEN** il ouvre Argos
- **THEN** Argos s'affiche avec la mise en page `classique`

#### Scenario: AFF-04-S2 Mise en page retirée
- **GIVEN** un utilisateur dont la préférence vaut `fantaisie`
- **AND** la mise en page `fantaisie` est retirée du catalogue
- **WHEN** il ouvre Argos
- **THEN** Argos s'affiche avec la mise en page `classique`
- **AND** un message l'informe que sa mise en page n'est plus disponible

### Requirement: AFF-05 Accessibilité de chaque mise en page
Le système DOIT (MUST) n'inscrire au catalogue qu'une mise en page conforme au référentiel d'accessibilité retenu pour Argos, vérifiée sur les mêmes écrans de référence que les autres mises en page.
Source : ADR-0023 (Accepté) point 6 ; référentiel à fixer (question Q-4). Statut : [PROPOSITION].

#### Scenario: AFF-05-S1 Mise en page non conforme
- **GIVEN** une nouvelle mise en page dont un texte n'atteint pas le contraste minimal du référentiel retenu
- **WHEN** l'administrateur plateforme demande sa publication au catalogue
- **THEN** la publication est refusée tant que l'écart n'est pas corrigé
- **AND** le catalogue est inchangé
