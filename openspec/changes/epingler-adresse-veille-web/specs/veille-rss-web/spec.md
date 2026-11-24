# Delta de spécification : veille-rss-web

## ADDED Requirements

### Requirement: WEB-07 Connexion à l'adresse validée
Le système DOIT (MUST) se connecter, pour chaque récupération et après chaque redirection, à une adresse IP issue de la résolution contrôlée selon WEB-03, sans nouvelle résolution du nom ; en HTTPS, le certificat reste vérifié pour le nom d'hôte.
Source : AUD-SEC-01 ; ADR-0028 (Accepté) ; R-12 ; arc42 §8.3. Statut : [PROPOSITION].

#### Scenario: WEB-07-S1 Résolution qui change entre le contrôle et la connexion
- **GIVEN** une source dont l'hôte est résolu une première fois vers une adresse publique autorisée, puis vers 127.0.0.1 à toute résolution suivante
- **WHEN** la source est collectée
- **THEN** la connexion vise l'adresse publique contrôlée
- **AND** aucune requête n'est reçue à l'adresse 127.0.0.1

#### Scenario: WEB-07-S2 Redirection vers un hôte qui change de résolution
- **GIVEN** un flux public qui redirige vers un second hôte, résolu une première fois vers une adresse publique autorisée, puis vers une adresse privée
- **WHEN** la source est collectée
- **THEN** la connexion qui suit la redirection vise l'adresse publique contrôlée pour ce second hôte
- **AND** aucune requête n'est reçue à l'adresse privée

#### Scenario: WEB-07-S3 Redirection dont la résolution contrôlée est interdite
- **GIVEN** un flux public qui redirige vers un hôte résolu vers une adresse privée dès le contrôle
- **WHEN** la source est collectée
- **THEN** la récupération est interrompue avec le motif `REDIRECTION_INTERDITE`, sans hôte ni adresse IP dans le message
- **AND** aucune connexion n'est ouverte vers cette adresse

#### Scenario: WEB-07-S4 Certificat vérifié pour le nom d'hôte
- **GIVEN** une source HTTPS dont l'hôte est résolu vers une adresse autorisée
- **WHEN** le serveur présente un certificat valide pour ce nom d'hôte
- **THEN** le contenu est récupéré, la requête portant le nom d'hôte d'origine (indication SNI et en-tête `Host`)
- **AND** lorsque le serveur présente un certificat qui ne correspond pas à ce nom d'hôte, la récupération est refusée et la source passe en erreur
