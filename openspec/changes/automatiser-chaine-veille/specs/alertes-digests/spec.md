# Delta de spécification : alertes-digests

## ADDED Requirements

### Requirement: ALR-07 Aucune alerte pour un impact écarté ou résolu
Le système NE DOIT PAS (MUST NOT) émettre d'alerte immédiate ni citer dans un résumé périodique un impact à l'état `écarté` ou `résolu` (COR-09) ; un impact qui repasse `ouvert` redevient éligible selon ALR-01 à ALR-03.
Source : décision J5 du porteur du projet (2026-11-19) ; COR-09, COR-11. Statut : [PROPOSITION].

#### Scenario: ALR-07-S1 Impact écarté avant l'envoi
- **GIVEN** un impact `exact` qui satisfait une règle d'alerte immédiate de `jules`, en attente d'envoi
- **WHEN** un contributeur écarte l'impact avant l'envoi
- **THEN** aucune alerte n'est envoyée à `jules` pour cet impact

#### Scenario: ALR-07-S2 Résumé sans impact résolu
- **GIVEN** un impact `probable` destiné au résumé quotidien de `jules`, résolu par le système avant l'heure du résumé
- **WHEN** le résumé quotidien est produit
- **THEN** il ne cite pas cet impact
- **AND** aucun résumé n'est envoyé s'il ne reste aucun élément (ALR-04)
