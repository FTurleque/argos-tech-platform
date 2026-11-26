# Delta de spécification : radar-standards

## MODIFIED Requirements

### Requirement: RAD-07 Enregistrement d'un standard sans erreur différée
Le système DOIT (MUST) refuser par une erreur de saisie (400) une clé de technologie mal encodée, sans rien modifier, et DOIT (MUST) confirmer une modification enregistrée du référentiel sans attendre son annonce (RAD-06) ; un échec de l'annonce NE DOIT PAS (MUST NOT) changer la réponse et est journalisé avec sa cause.
Source : AUD-QUA-20 ; AUD-ARC-09 ; RAD-01-S2 ; RAD-06 ; arc42 §8.5 ; ADR-0039 (Proposé). Statut : [PROPOSITION].

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
- **THEN** la modification est confirmée à l'administrateur avec l'entrée enregistrée
- **AND** l'erreur est journalisée avec sa cause et comptée parmi les échecs de traitement asynchrone exposés à l'exploitant

#### Scenario: RAD-07-S4 Annonce sans attente
- **GIVEN** une bibliothèque utilisée par un projet de l'espace, et l'annonce du changement de standard qui tarde
- **WHEN** l'administrateur la passe en HOLD avec une justification
- **THEN** la modification est confirmée sans attendre la fin de l'annonce
- **AND** les utilisateurs du projet reçoivent ensuite l'annonce (RAD-06-S1)
