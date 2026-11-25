# Delta de spécification : veille-reglementaire

## ADDED Requirements

### Requirement: REG-09 Demande d'évolution contrôlée avant tout appel à la forge
Le système DOIT (MUST) vérifier que la fiche est visible de l'appelant, qu'il en est l'expert et qu'elle est à l'état « impact produit » avant d'interroger une forge sur une demande d'évolution ; pour un appelant non autorisé, la réponse NE DOIT PAS (MUST NOT) dépendre de l'existence de la demande sur la forge.
Source : AUD-SEC-22 (/docs/audit/2026-11-21/findings.md) ; REG-08 ; ADR-0030 (Accepté) ; ACC-03 ; ACC-04. Statut : [PROPOSITION].

#### Scenario: REG-09-S1 Appelant non membre de l'espace de la fiche
- **GIVEN** une fiche « impact produit » d'un espace dont l'appelant n'est pas membre
- **WHEN** l'appelant associe à cette fiche l'adresse d'un ticket existant puis celle d'un ticket inexistant d'une forge configurée
- **THEN** les deux demandes reçoivent la même réponse que pour une fiche inexistante
- **AND** aucune requête n'est envoyée à la forge et la fiche n'est pas modifiée

#### Scenario: REG-09-S2 Membre non expert
- **GIVEN** une fiche « impact produit » visible d'un membre qui n'en est pas l'expert
- **WHEN** ce membre associe l'adresse d'une demande d'évolution d'une forge configurée
- **THEN** la demande est refusée pour rôle insuffisant, quelle que soit l'existence de la demande sur la forge
- **AND** aucune requête n'est envoyée à la forge et le refus est journalisé

#### Scenario: REG-09-S3 Fiche pas encore « impact produit »
- **GIVEN** une fiche visible de son expert, à un autre état que « impact produit »
- **WHEN** l'expert associe l'adresse d'une demande d'évolution
- **THEN** la demande est refusée en indiquant que l'état « impact produit » est requis
- **AND** aucune requête n'est envoyée à la forge

#### Scenario: REG-09-S4 Expert autorisé
- **GIVEN** une fiche « impact produit » visible de son expert
- **WHEN** l'expert associe l'adresse d'une demande inexistante, puis celle d'une demande existante
- **THEN** la première est refusée comme demande introuvable, la seconde est associée avec l'état lu sur la forge
- **AND** l'association est historisée sur la fiche
