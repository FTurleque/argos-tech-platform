# Delta de spécification : recherche-veille

## ADDED Requirements

### Requirement: RCH-06 Limite de débit de la recherche
Le système DOIT (MUST) limiter par compte et par minute le nombre de recherches plein texte, lecture des résultats d'une vue enregistrée comprise ; au-delà de la limite configurée, il refuse la recherche avec une erreur de limite qui indique quand réessayer, journalise le refus et n'affecte pas les autres comptes.
Source : AUD-SEC-09 (/docs/audit/2026-11-21/findings.md) ; ACC-06 ; MCP-06 (limite existante du serveur MCP) ; valeur : décision du porteur, 2026-11-24. Statut : [PROPOSITION].

#### Scenario: RCH-06-S1 Limite atteinte
- **GIVEN** un compte qui a lancé 60 recherches plein texte ou lectures de résultats de vue dans la dernière minute, avec la limite par défaut
- **WHEN** il lance une recherche plein texte ou lit les résultats d'une de ses vues enregistrées
- **THEN** la demande est refusée avec une erreur de limite de débit et un délai avant nouvel essai
- **AND** le refus est journalisé et aucune recherche n'est exécutée

#### Scenario: RCH-06-S2 Autres comptes non affectés
- **GIVEN** un compte qui a atteint sa limite de recherches
- **WHEN** un autre compte lance une recherche dans la même minute
- **THEN** la recherche de l'autre compte est exécutée normalement

#### Scenario: RCH-06-S3 Fenêtre écoulée
- **GIVEN** un compte refusé pour limite de débit
- **WHEN** il relance une recherche après l'écoulement de la fenêtre d'une minute
- **THEN** la recherche est exécutée normalement
