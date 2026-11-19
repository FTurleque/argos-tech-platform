# veille-reglementaire Specification

## Purpose
Repérer les textes officiels qui touchent les domaines d'un espace, en préparer une fiche fiable et ne la diffuser qu'après validation par un expert. Fiche : [/docs/specifications/veille-reglementaire.md](/docs/specifications/veille-reglementaire.md).

## Requirements

### Requirement: REG-01 Collecte quotidienne sans doublon
Le système DOIT (MUST) collecter chaque jour les textes publiés au Journal officiel et les enregistrer une seule fois par identifiant officiel, même en cas de rejeu.
Source : ADR-0018 (Remplacé par ADR-0030) ; arc42 §6.3. Statut : [EXIGENCE].

#### Scenario: REG-01-S1 Rejeu de la collecte
- **GIVEN** un décret déjà enregistré
- **WHEN** la collecte du jour est rejouée
- **THEN** le décret n'est pas enregistré une seconde fois

#### Scenario: REG-01-S2 API indisponible
- **GIVEN** l'API Légifrance indisponible
- **WHEN** la collecte du jour s'exécute
- **THEN** la source passe en état dégradé et la collecte reprend au jour manqué à son retour

### Requirement: REG-02 Qualification par domaine
Le système DOIT (MUST) rattacher un texte aux domaines de l'espace par règles déterministes (nature, ministère, NOR, codes modifiés, dictionnaire métier) et, à défaut, marquer toute proposition de l'IA comme telle avec son niveau de confiance.
Source : ADR-0018 (Remplacé par ADR-0030) ; ADR-0013 (Proposé). Statut : [EXIGENCE].

#### Scenario: REG-02-S1 Code suivi modifié
- **GIVEN** un espace dont le dictionnaire rattache le code général de la fonction publique au domaine « Carrière »
- **WHEN** un décret modifie ce code
- **THEN** le texte est rattaché au domaine « Carrière » sans appel à l'IA

#### Scenario: REG-02-S2 Qualification incertaine
- **GIVEN** un texte qu'aucune règle ne rattache
- **WHEN** le système le qualifie
- **THEN** un domaine proposé par l'IA est affiché comme proposition, avec son niveau de confiance, en attente de l'expert

### Requirement: REG-03 Dates de publication et d'entrée en vigueur
Le système DOIT (MUST) distinguer la date de publication et la date d'entrée en vigueur d'un texte, et indiquer « date à confirmer » quand la seconde n'est pas extraite.
Source : ADR-0018 (Remplacé par ADR-0030). Statut : [EXIGENCE].

#### Scenario: REG-03-S1 Date extraite
- **WHEN** un décret publié le 20 novembre fixe son entrée en vigueur au 1er janvier
- **THEN** les deux dates sont enregistrées et affichées séparément

#### Scenario: REG-03-S2 Date absente
- **WHEN** aucune date d'entrée en vigueur n'est extraite d'un texte
- **THEN** le texte est affiché avec la mention « date à confirmer »

### Requirement: REG-04 Fiche d'analyse préparée
Le système DOIT (MUST) préparer pour chaque texte qualifié une fiche d'analyse comprenant résumé, articles modifiés, date d'effet, domaines concernés et liens vers le texte publié et sa version consolidée.
Source : ADR-0018 (Remplacé par ADR-0030) ; IAG-04. Statut : [EXIGENCE].

#### Scenario: REG-04-S1 Fiche préparée
- **WHEN** un texte est rattaché au domaine « Paie »
- **THEN** une fiche est préparée avec ses rubriques et les liens officiels
- **AND** elle est marquée « produite par l'IA » et « en attente de validation »

### Requirement: REG-05 Validation par l'expert avant diffusion
Le système NE DOIT PAS (MUST NOT) diffuser une fiche à d'autres utilisateurs que les experts du domaine tant qu'un expert ne l'a pas validée ; toute correction est historisée.
Source : ADR-0018 (Remplacé par ADR-0030) ; PRF-06. Statut : [EXIGENCE].

#### Scenario: REG-05-S1 Fiche corrigée puis validée
- **GIVEN** une fiche en attente
- **WHEN** l'expert corrige la date d'effet puis valide
- **THEN** la fiche devient visible des utilisateurs du domaine
- **AND** la correction est conservée dans l'historique

#### Scenario: REG-05-S2 Fiche non validée
- **GIVEN** une fiche en attente
- **WHEN** un utilisateur non expert consulte sa veille
- **THEN** la fiche ne lui est pas restituée

### Requirement: REG-06 Suivi jusqu'à la livraison
Le système DOIT (MUST) faire suivre à chaque texte les états nouveau, en analyse, sans impact ou impact produit, spécifié, livré, et refuser toute transition hors de cet ordre.
Source : ADR-0018 (Remplacé par ADR-0030). Statut : [EXIGENCE].

#### Scenario: REG-06-S1 Transition valide
- **GIVEN** un texte « en analyse »
- **WHEN** l'expert le qualifie « impact produit »
- **THEN** l'état devient « impact produit »

#### Scenario: REG-06-S2 Transition invalide
- **GIVEN** un texte « nouveau »
- **WHEN** un utilisateur tente de le passer à « livré »
- **THEN** la transition est refusée
- **AND** l'état reste « nouveau »

### Requirement: REG-07 Calendrier et rappels
Le système DOIT (MUST) présenter le calendrier des entrées en vigueur des textes validés et rappeler les échéances aux utilisateurs du domaine.
Source : ADR-0018 (Remplacé par ADR-0030). Statut : [EXIGENCE].

#### Scenario: REG-07-S1 Rappel avant échéance
- **GIVEN** un texte validé entrant en vigueur le 1er janvier
- **WHEN** l'échéance de rappel configurée est atteinte
- **THEN** les utilisateurs du domaine reçoivent un rappel

### Requirement: REG-08 Lien vers un outil de suivi
Le système DOIT (MUST) permettre d'associer à un texte un lien vers une demande d'évolution dans un outil de suivi externe.
Source : ADR-0018 (Remplacé par ADR-0030). Statut : [EXIGENCE].

#### Scenario: REG-08-S1 Demande associée
- **WHEN** l'expert associe l'adresse d'une demande d'évolution à un texte « impact produit »
- **THEN** le lien est affiché sur la fiche
