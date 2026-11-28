# Delta de spécification : recherche-veille

## ADDED Requirements

### Requirement: RCH-07 Seule la page demandée est classée
Le système DOIT (MUST) classer et restituer une page de résultats sans classer ni matérialiser toute la sélection : le classement par pertinence porte au plus sur les 1 000 éléments les plus récents qui correspondent, et le total restitué est plafonné à 1 000 avec un indicateur de plafond.
Source : AUD-PER-07 ; décision du porteur du projet (2026-11-26, total plafonné) ; RCH-05. Statut : [PROPOSITION].

#### Scenario: RCH-07-S1 Sélection plus grande que le plafond
- **GIVEN** plus de 1 000 avis qui contiennent le mot recherché
- **WHEN** l'utilisateur demande la première page de 20 résultats
- **THEN** 20 résultats sont restitués, classés par pertinence parmi les 1 000 avis les plus récents qui correspondent
- **AND** le total vaut 1 000 et l'indicateur de plafond est vrai

#### Scenario: RCH-07-S2 Sélection sous le plafond
- **GIVEN** 37 éléments qui correspondent à la recherche
- **WHEN** l'utilisateur demande une page
- **THEN** le total vaut 37 et l'indicateur de plafond est faux
- **AND** l'ordre des résultats est celui de RCH-01 (pertinence, puis date, puis identifiant)
