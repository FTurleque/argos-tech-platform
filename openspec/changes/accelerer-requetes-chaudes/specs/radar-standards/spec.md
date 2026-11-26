# Delta de spécification : radar-standards

## ADDED Requirements

### Requirement: RAD-08 Radar en un nombre fixe de requêtes par espace
Le système DOIT (MUST) calculer les dettes de version et les échéances de rappel d'un espace en un nombre de requêtes indépendant du nombre de composants de ses projets, des composants des autres espaces et du nombre de jours de rappel, sans changer les dettes ni les rappels produits (RAD-04, RAD-05).
Source : AUD-PER-05 ; critère de sortie du sprint 10. Statut : [PROPOSITION].

#### Scenario: RAD-08-S1 Composants d'autres espaces ignorés en base
- **GIVEN** deux espaces qui suivent la même technologie, l'un avec 3 composants concernés, l'autre avec 300
- **WHEN** les dettes de version du premier espace sont calculées
- **THEN** seuls ses 3 composants sont lus
- **AND** le calcul exécute le même nombre de requêtes que pour un espace de 30 composants concernés

#### Scenario: RAD-08-S2 Rappels sur plusieurs jours
- **GIVEN** un espace avec des rappels à 30, 7 et 1 jour
- **WHEN** les rappels du jour sont calculés
- **THEN** les échéances des trois jours sont trouvées en une seule lecture du calendrier et des composants de l'espace
