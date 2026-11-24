# Delta de spécification : collecte-avis-securite

## ADDED Requirements

### Requirement: COL-11 Erreur d'une source isolée
Le système DOIT (MUST) terminer en échec visible la synchronisation d'une source qu'interrompt une erreur imprévue, y compris due à un avis mal formé, et poursuivre la synchronisation des autres sources du même passage ; il NE DOIT PAS (MUST NOT) laisser cette synchronisation en cours.
Source : AUD-QUA-20 ; COL-07 ; COL-08 ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: COL-11-S1 Avis au PURL mal encodé
- **GIVEN** deux sources d'avis synchronisées dans le même passage planifié, la première recevant un avis dont un paquet porte le PURL `pkg:npm/%zz@1.0.0`
- **WHEN** le passage planifié s'exécute
- **THEN** la seconde source est synchronisée et ses avis enregistrés selon COL-01 et COL-03
- **AND** la synchronisation de la première source n'est plus en cours à la fin du passage, et l'avis au PURL mal encodé est enregistré comme signal ou mis à l'écart avec son motif (COL-08), jamais ignoré sans trace

#### Scenario: COL-11-S2 Erreur imprévue pendant la synchronisation d'une source
- **GIVEN** deux sources d'avis, le traitement d'un avis de la première (lecture, normalisation ou enregistrement) échouant sur une erreur imprévue autre qu'une panne d'accès aux données
- **WHEN** le passage planifié s'exécute
- **THEN** la synchronisation de la première source se termine en échec permanent, avec son motif, et compte pour le seuil de COL-07
- **AND** le curseur de la première source est inchangé (COL-05), la seconde source est synchronisée, puis le passage relance l'erreur imprévue

#### Scenario: COL-11-S3 Seuil atteint par des erreurs imprévues
- **GIVEN** une source dont les synchronisations échouent sur la même erreur imprévue
- **WHEN** le seuil d'échec configuré est atteint
- **THEN** la source passe en état `DEGRADED` et un événement d'exploitation daté est enregistré (COL-07-S1)
