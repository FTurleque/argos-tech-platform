# Delta de spécification : inventaire-sbom

## ADDED Requirements

### Requirement: INV-11 Erreur d'un projet isolée
Le système DOIT (MUST), lors de l'import planifié, limiter à son projet l'effet d'une SBOM fautive ou d'une erreur imprévue : les projets suivants du passage sont importés, le projet en échec est compté au bilan du passage et l'erreur est journalisée avec sa cause.
Source : AUD-QUA-20 ; INV-05 ; INV-07-S2 ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: INV-11-S1 SBOM portant un PURL mal encodé
- **GIVEN** deux projets dont le premier publie une SBOM CycloneDX dont un composant porte le PURL `pkg:npm/%zz@1.0.0`
- **WHEN** le passage quotidien de l'import s'exécute
- **THEN** l'inventaire du second projet est importé
- **AND** le composant au PURL mal encodé du premier projet est écarté et signalé comme tout composant au PURL illisible (INV-01), les autres composants de sa SBOM étant importés

#### Scenario: INV-11-S2 Erreur imprévue pendant l'import d'un projet
- **GIVEN** deux projets, l'import du premier échouant sur une erreur imprévue autre qu'une panne d'accès aux données
- **WHEN** le passage quotidien de l'import s'exécute
- **THEN** l'inventaire du second projet est importé
- **AND** l'inventaire en place du premier projet est conservé, le bilan du passage compte un projet en échec, l'erreur est journalisée avec sa cause et le passage la relance
