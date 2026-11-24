# Delta de spécification : radar-standards

## ADDED Requirements

### Requirement: RAD-07 Enregistrement d'un standard sans erreur différée
Le système DOIT (MUST) refuser par une erreur de saisie (400) une clé de technologie mal encodée, sans rien modifier, et NE DOIT PAS (MUST NOT) répondre en erreur à une modification du référentiel déjà enregistrée lorsque son annonce (RAD-06) échoue sur une panne d'accès aux données ; cet échec est journalisé avec sa cause.
Source : AUD-QUA-20 ; RAD-01-S2 ; RAD-06 ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: RAD-07-S1 Clé mal encodée
- **GIVEN** un administrateur de l'espace
- **WHEN** il enregistre un standard de clé `pkg:maven/%zz/x`
- **THEN** l'enregistrement est refusé par une réponse 400 qui indique le champ `cle`
- **AND** le référentiel et son historique sont inchangés

#### Scenario: RAD-07-S2 Annonce en échec après l'enregistrement
- **GIVEN** une bibliothèque utilisée par un projet de l'espace, et l'annonce du changement de standard qui échoue sur une panne d'accès aux données
- **WHEN** l'administrateur la passe en HOLD avec une justification
- **THEN** la modification est confirmée à l'administrateur avec l'entrée enregistrée
- **AND** l'entrée et sa ligne d'historique sont persistées, et l'échec de l'annonce est journalisé avec sa cause

#### Scenario: RAD-07-S3 Erreur imprévue de l'annonce
- **GIVEN** une bibliothèque utilisée par un projet de l'espace, et l'annonce du changement de standard qui échoue sur une erreur imprévue
- **WHEN** l'administrateur la passe en HOLD avec une justification
- **THEN** l'erreur est journalisée avec sa cause et la requête répond en erreur serveur, sans détail interne
- **AND** l'entrée et sa ligne d'historique restent persistées
