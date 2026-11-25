# Delta de spécification : comptes-acces

## ADDED Requirements

### Requirement: ACC-11 Connexion au portail servi selon la configuration de l'instance
Le portail web servi par le serveur Argos DOIT (MUST) mener la connexion auprès de l'émetteur OIDC et du client configurés pour l'instance, avec une adresse de retour égale à l'adresse publique configurée d'Argos ; il NE DOIT PAS (MUST NOT) dépendre d'un émetteur ou d'un port figés lors de la construction de l'image.
Source : /docs/audit/2026-11-21/findings.md (AUD-DEP-21, AUD-DEP-25) ; ADR-0026 (Accepté) ; ADR-0027 (Accepté) ; ADR-0033 (Accepté) ; ACC-07. Statut : [PROPOSITION].

#### Scenario: ACC-11-S1 Connexion depuis l'hôte avec les valeurs par défaut
- **GIVEN** une installation selon le guide avec le profil Keycloak local et les valeurs par défaut du fichier d'exemple de configuration
- **WHEN** une personne ouvre le portail à l'adresse publiée par défaut sur l'hôte et se connecte avec un compte du royaume
- **THEN** le fournisseur accepte l'adresse de retour et l'origine du portail
- **AND** le portail affiche la vue demandée avec une session ouverte

#### Scenario: ACC-11-S2 Connexion depuis un autre poste
- **GIVEN** une instance dont l'adresse d'écoute, l'adresse publique d'Argos et l'émetteur public sont réglés selon le guide pour le réseau local
- **WHEN** une personne ouvre le portail depuis un autre poste du réseau et se connecte
- **THEN** son navigateur est dirigé vers l'émetteur public configuré, jamais vers une adresse de bouclage
- **AND** la connexion aboutit et le portail affiche la vue demandée

#### Scenario: ACC-11-S3 Émetteur modifié sans reconstruction de l'image
- **GIVEN** une image déjà construite et une instance démarrée avec un premier émetteur
- **WHEN** l'exploitant change l'émetteur public dans la configuration et redémarre le conteneur sans reconstruire l'image
- **THEN** le portail mène la connexion auprès du nouvel émetteur
- **AND** aucune ressource servie de l'IHM ne contient l'ancien émetteur

#### Scenario: ACC-11-S4 Adresse de retour non déclarée
- **GIVEN** un royaume livré importé avec l'adresse publique configurée
- **WHEN** une demande d'autorisation du client du portail porte une adresse de retour d'une autre origine ou d'un autre chemin
- **THEN** le fournisseur refuse la demande
- **AND** aucun code d'autorisation n'est émis

### Requirement: ACC-12 Ressources publiques et en-têtes de sécurité de l'IHM servie
Le serveur DOIT (MUST) servir sans session les seules ressources statiques de l'IHM, refuser sans session toute autre route hors santé et identité du serveur, et poser sur l'IHM servie les en-têtes de sécurité : contenu limité à son origine et à l'émetteur configuré, pas d'affichage en cadre, pas de type de contenu deviné, politique de référent.
Source : AUD-SEC-05, AUD-TST-25 (/docs/audit/2026-11-21/findings.md) ; ADR-0033 (Accepté) ; ACC-01 ; ACC-10-S4. Statut : [PROPOSITION].

#### Scenario: ACC-12-S1 Ressources de l'IHM sans session
- **GIVEN** un serveur qui sert l'IHM
- **WHEN** une requête sans session demande la page d'accueil, une ressource de build de l'IHM ou l'avis de licence des polices
- **THEN** la ressource est renvoyée sans refus d'authentification
- **AND** elle ne contient aucune donnée de veille, d'espace ni d'utilisateur

#### Scenario: ACC-12-S2 API toujours protégée
- **GIVEN** un serveur qui sert l'IHM
- **WHEN** une requête sans session demande une route de l'API autre que l'identité du serveur, ou une route technique autre que la santé
- **THEN** la requête est refusée comme non authentifiée
- **AND** aucune donnée n'est renvoyée

#### Scenario: ACC-12-S3 En-têtes de sécurité de l'IHM servie
- **GIVEN** une instance configurée avec un émetteur
- **WHEN** un navigateur charge la page de l'IHM servie
- **THEN** la réponse porte une politique de sécurité du contenu dont les connexions sont limitées à l'origine du portail et à celle de cet émetteur, qui interdit l'affichage en cadre et limite les scripts à l'IHM livrée
- **AND** la réponse interdit de deviner le type de contenu et fixe une politique de référent

#### Scenario: ACC-12-S4 Affichage dans un cadre tiers
- **GIVEN** une page d'un autre site qui insère le portail dans un cadre
- **WHEN** le navigateur charge ce cadre
- **THEN** le navigateur refuse d'afficher le portail dans le cadre

### Requirement: ACC-13 Royaume livré durci
Le royaume Keycloak livré pour l'installation DOIT (MUST) activer la protection contre la force brute, imposer une politique de mot de passe, exiger TLS au moins pour les adresses non locales et ne déclarer pour le client du portail que des adresses de retour, origines et adresses de fin de session exactes, sans joker.
Source : AUD-SEC-29, AUD-DEP-25 ; ADR-0027 (Accepté) ; valeurs : décision du porteur, 2026-11-24. Statut : [PROPOSITION].

#### Scenario: ACC-13-S1 Essais répétés de mot de passe
- **GIVEN** le royaume livré importé et le compte de l'administrateur plateforme
- **WHEN** cinq essais de mot de passe erronés se succèdent
- **THEN** le compte est temporairement bloqué, l'attente commençant à 60 secondes et croissant à chaque nouvelle série d'échecs sans dépasser 15 minutes
- **AND** une connexion avec le bon mot de passe pendant le blocage est refusée

#### Scenario: ACC-13-S2 Mot de passe hors politique
- **GIVEN** le compte de l'administrateur plateforme avec son mot de passe provisoire
- **WHEN** il choisit à la première connexion un mot de passe de moins de 12 caractères, égal à son identifiant ou à son adresse e-mail, ou identique à l'un de ses 3 derniers mots de passe
- **THEN** le mot de passe est refusé avec la règle non respectée
- **AND** le mot de passe provisoire reste à changer

#### Scenario: ACC-13-S3 Connexion en clair depuis une adresse non locale
- **GIVEN** le royaume livré importé, qui exige TLS pour les adresses non locales
- **WHEN** une connexion en http sans TLS parvient au royaume depuis une adresse publique
- **THEN** le royaume refuse la connexion en exigeant HTTPS
- **AND** une connexion en http depuis l'hôte local reste acceptée pour l'installation locale

#### Scenario: ACC-13-S4 Contrôle du fichier du royaume livré
- **GIVEN** le fichier du royaume livré dans le dépôt
- **WHEN** le contrôle automatique du royaume s'exécute
- **THEN** il échoue si la protection contre la force brute est absente ou ses seuils différents de 5 échecs, 60 secondes et 15 minutes, si la politique de mot de passe diffère de celle retenue, si l'exigence de TLS ne vaut pas « adresses externes » ou si une adresse du client du portail contient un joker
