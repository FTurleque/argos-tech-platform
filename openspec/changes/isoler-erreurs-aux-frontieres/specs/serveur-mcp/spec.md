# Delta de spécification : serveur-mcp

## ADDED Requirements

### Requirement: MCP-08 Erreur interne d'un outil journalisée, sans donnée partielle
Le serveur MCP DOIT (MUST) journaliser avec sa cause toute erreur interne d'un outil, inscrire l'appel en erreur et ne renvoyer ni donnée partielle ni message interne ; une panne d'accès aux données répond `INDISPONIBLE`, une erreur imprévue NE DOIT PAS (MUST NOT) être traduite en `INDISPONIBLE`.
Source : AUD-QUA-20 ; MCP-06 ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: MCP-08-S1 Panne d'accès aux données
- **GIVEN** un utilisateur authentifié et la base de données indisponible
- **WHEN** son assistant appelle `check_dependency`
- **THEN** le résultat est une erreur d'outil dont le texte commence par `INDISPONIBLE`, sans donnée partielle
- **AND** l'appel est inscrit au journal des appels avec l'issue en erreur

#### Scenario: MCP-08-S2 Erreur imprévue d'un outil
- **GIVEN** un utilisateur authentifié et un outil dont le traitement échoue sur une erreur imprévue autre qu'une panne d'accès aux données
- **WHEN** son assistant appelle cet outil
- **THEN** la réponse est une erreur de protocole JSON-RPC, sans résultat partiel ni message interne
- **AND** l'appel est inscrit au journal des appels avec l'issue en erreur et l'erreur est journalisée avec sa cause
