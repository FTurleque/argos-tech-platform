# Delta de spécification : comptes-acces

## ADDED Requirements

### Requirement: ACC-15 Journal des refus borné par auteur
Le système DOIT (MUST) limiter le nombre de refus écrits au journal des refus par auteur (compte, ou sujet OIDC sans compte) sur une fenêtre glissante d'une minute. Au-delà de la limite, le refus DOIT (MUST) toujours être appliqué, sans être écrit, et être compté.
Source : /docs/audit/2026-11-21/findings.md, AUD-SEC-03 (action) ; ACC-06. Statut : [PROPOSITION].

#### Scenario: ACC-15-S1 Sujet sans invitation qui insiste
- **GIVEN** une limite de 30 refus journalisés par minute et par auteur
- **WHEN** un sujet OIDC sans compte Argos envoie 100 requêtes dans la minute
- **THEN** chaque requête est refusée (403 `INSCRIPTION_SUR_INVITATION`)
- **AND** 30 refus au plus sont écrits pour ce sujet ; les autres sont comptés comme refus non journalisés

#### Scenario: ACC-15-S2 Auteurs indépendants
- **GIVEN** un sujet qui a atteint la limite
- **WHEN** un autre compte reçoit un refus dans la même minute
- **THEN** le refus de cet autre compte est écrit au journal
