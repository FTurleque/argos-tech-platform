# Delta de spécification : serveur-mcp

## ADDED Requirements

### Requirement: MCP-08 Erreur interne répondue INDISPONIBLE
Le serveur MCP DOIT (MUST) répondre à toute erreur interne survenue pendant un outil par un résultat d'erreur d'outil `INDISPONIBLE`, sans donnée partielle ni détail interne, et journaliser l'appel en erreur avec sa cause ; il NE DOIT PAS (MUST NOT) la transformer en erreur de protocole.
Source : AUD-QUA-20 ; MCP-06 ; fiche serveur-mcp (codes d'erreur d'outil). Statut : [PROPOSITION].

#### Scenario: MCP-08-S1 Panne d'accès aux données
- **GIVEN** un utilisateur authentifié et la base de données indisponible
- **WHEN** son assistant appelle `check_dependency`
- **THEN** le résultat est une erreur d'outil dont le texte commence par `INDISPONIBLE`, sans donnée partielle
- **AND** l'appel est inscrit au journal des appels avec l'issue en erreur

#### Scenario: MCP-08-S2 Erreur imprévue d'un outil
- **GIVEN** un utilisateur authentifié et un outil dont le traitement échoue sur une erreur imprévue autre qu'une panne d'accès aux données
- **WHEN** son assistant appelle cet outil
- **THEN** le résultat est une erreur d'outil dont le texte commence par `INDISPONIBLE`, sans trace ni message interne, et non une erreur de protocole JSON-RPC
- **AND** l'appel est inscrit au journal des appels avec l'issue en erreur et l'erreur est journalisée avec sa cause
