# Delta de spécification : profils-veille

## ADDED Requirements

### Requirement: PRF-08 Limite de débit de « Ma veille »
Le système DOIT (MUST) limiter par compte et par minute le nombre de restitutions de « Ma veille » ; au-delà de la limite configurée, il refuse la demande avec une erreur de limite qui indique quand réessayer, journalise le refus et n'affecte pas les autres comptes.
Source : AUD-SEC-09 (/docs/audit/2026-11-21/findings.md) ; ACC-06 ; PRF-07 ; valeur : décision du porteur, 2026-11-24. Statut : [PROPOSITION].

#### Scenario: PRF-08-S1 Limite atteinte
- **GIVEN** un compte qui a consulté « Ma veille » 60 fois dans la dernière minute, avec la limite par défaut
- **WHEN** il consulte sa veille
- **THEN** la demande est refusée avec une erreur de limite de débit et un délai avant nouvel essai
- **AND** le refus est journalisé et aucune donnée de veille n'est renvoyée

#### Scenario: PRF-08-S2 Autres comptes non affectés
- **GIVEN** un compte qui a atteint sa limite de restitutions de « Ma veille »
- **WHEN** un autre compte consulte sa veille dans la même minute
- **THEN** la veille de l'autre compte est restituée normalement

#### Scenario: PRF-08-S3 Compteur distinct de celui de la recherche
- **GIVEN** un compte qui a atteint sa limite de recherches plein texte pour la minute en cours
- **WHEN** il consulte « Ma veille » dans la même minute
- **THEN** sa veille est restituée normalement
