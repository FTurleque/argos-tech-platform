# comptes-acces Specification

## Purpose
Garantir que seuls des utilisateurs authentifiés accèdent à Argos, chacun dans les espaces dont il est membre et avec le rôle qui lui est attribué. Fiche : [/docs/specifications/comptes-acces.md](/docs/specifications/comptes-acces.md).

## Requirements

### Requirement: ACC-01 Authentification obligatoire
Le système DOIT (MUST) exiger une session authentifiée par OIDC pour toute lecture ou modification de veille, d'impacts, d'abonnements ou de préférences ; seuls l'état de santé et l'identité du serveur restent publics.
Source : ADR-0008 (Proposé) ; ADR-0025 (Accepté). Statut : [EXIGENCE].

#### Scenario: ACC-01-S1 Accès sans session
- **WHEN** une requête sans session demande la veille d'un utilisateur
- **THEN** la requête est refusée comme non authentifiée
- **AND** aucune donnée de veille n'est renvoyée

#### Scenario: ACC-01-S2 Point technique public
- **WHEN** une requête sans session demande l'identité du serveur
- **THEN** la réponse indique le nom et la version du serveur, sans donnée d'utilisateur

### Requirement: ACC-02 Création du compte à la première connexion
Le système DOIT (MUST) créer le compte d'un utilisateur à sa première connexion OIDC réussie, sans lui donner accès à aucun espace existant.
Source : ADR-0025 (Accepté) ; ADR-0016 (Proposé). Statut : [EXIGENCE].

#### Scenario: ACC-02-S1 Première connexion
- **GIVEN** une personne sans compte Argos
- **WHEN** elle se connecte avec succès auprès du fournisseur d'identité
- **THEN** un compte Argos est créé à son nom
- **AND** elle n'est membre d'aucun espace existant

#### Scenario: ACC-02-S2 Connexion refusée par le fournisseur
- **GIVEN** une personne sans compte Argos
- **WHEN** le fournisseur d'identité refuse son authentification
- **THEN** la connexion échoue avec un message explicite
- **AND** aucun compte n'est créé

### Requirement: ACC-03 Espaces et rôles
Le système DOIT (MUST) permettre à un utilisateur de créer un espace dont il devient administrateur, et n'autoriser la gestion des rôles d'un espace qu'à ses administrateurs.
Source : ADR-0016 (Proposé) ; ADR-0025 (Accepté). Statut : [EXIGENCE].

#### Scenario: ACC-03-S1 Création d'un espace
- **GIVEN** un utilisateur connecté
- **WHEN** il crée l'espace « Atelier »
- **THEN** l'espace est créé
- **AND** l'utilisateur en est administrateur

#### Scenario: ACC-03-S2 Gestion des rôles par un contributeur
- **GIVEN** un contributeur de l'espace « Atelier »
- **WHEN** il tente de nommer un autre membre administrateur
- **THEN** la demande est refusée comme interdite
- **AND** les rôles de l'espace sont inchangés

### Requirement: ACC-04 Cloisonnement par espace
Le système NE DOIT PAS (MUST NOT) restituer une donnée d'un espace à un utilisateur qui n'en est pas membre, que la demande passe par l'API, le serveur MCP ou une requête en base sans contexte d'espace.
Source : ADR-0016 (Proposé) ; exigence COR-08 ; risque R-17. Statut : [EXIGENCE].

#### Scenario: ACC-04-S1 Lecture d'un autre espace
- **GIVEN** un utilisateur membre du seul espace A
- **WHEN** il demande un impact de l'espace B
- **THEN** la demande est refusée
- **AND** aucune donnée de l'espace B ne lui est renvoyée

#### Scenario: ACC-04-S2 Requête en base sans contexte
- **GIVEN** une requête sur les impacts exécutée sans espace courant
- **WHEN** la base l'exécute
- **THEN** aucune ligne n'est renvoyée

### Requirement: ACC-05 Invitations et révocation
Le système DOIT (MUST) permettre à un administrateur d'espace d'inviter un utilisateur avec un rôle et de révoquer son accès, la révocation s'appliquant dès la requête suivante.
Source : ADR-0016 (Proposé). Statut : [EXIGENCE].

#### Scenario: ACC-05-S1 Invitation acceptée
- **GIVEN** un administrateur de l'espace « Atelier »
- **WHEN** il invite un utilisateur comme lecteur et celui-ci accepte
- **THEN** l'utilisateur voit les données de l'espace en lecture seule

#### Scenario: ACC-05-S2 Révocation
- **GIVEN** un lecteur de l'espace « Atelier »
- **WHEN** l'administrateur révoque son accès
- **THEN** sa requête suivante sur l'espace est refusée
- **AND** il ne figure plus parmi les membres

### Requirement: ACC-06 Journal des refus d'accès
Le système DOIT (MUST) journaliser chaque refus d'accès avec l'utilisateur, la ressource demandée et l'horodatage, sans recopier le contenu de la ressource.
Source : ADR-0016 (Proposé) ; arc42 §8.1. Statut : [EXIGENCE].

#### Scenario: ACC-06-S1 Refus journalisé
- **WHEN** un utilisateur se voit refuser l'accès à une ressource d'un autre espace
- **THEN** une entrée de journal enregistre l'utilisateur, la ressource et l'heure
- **AND** l'entrée ne contient aucune donnée de la ressource

### Requirement: ACC-07 Connexion à l'IHM
L'IHM, portail web comme client desktop, DOIT (MUST) exiger une session pour toute vue autre que l'état du serveur et obtenir cette session auprès du fournisseur d'identité par le flux code d'autorisation avec PKCE S256, en vérifiant le state du retour ; aucun mot de passe n'est saisi dans Argos.
Source : ADR-0027 (Proposé) ; ADR-0022 point 6 (Proposé) ; ADR-0033 (Proposé) ; demande du lot 7 (connecter-ihm-oidc). Statut : [EXIGENCE].

#### Scenario: ACC-07-S1 Vue protégée sans session
- **GIVEN** une personne sans session dans l'IHM
- **WHEN** elle ouvre une vue autre que l'état du serveur
- **THEN** l'écran de connexion s'affiche à la place de la vue
- **AND** aucune donnée de cette vue n'est demandée à l'API

#### Scenario: ACC-07-S2 Retour réussi du fournisseur
- **GIVEN** une connexion lancée depuis une vue protégée
- **WHEN** le fournisseur renvoie un code avec le state de la demande
- **THEN** le code est échangé avec le vérificateur PKCE et la vue demandée s'affiche
- **AND** ni le code ni le state ne restent dans l'adresse de la page ni dans son historique

#### Scenario: ACC-07-S3 State invalide
- **GIVEN** une connexion en cours
- **WHEN** un retour arrive avec un state absent ou différent de celui de la demande
- **THEN** la connexion échoue avec un message d'erreur
- **AND** aucun code n'est échangé et aucune session n'est ouverte

#### Scenario: ACC-07-S4 Refus du fournisseur
- **GIVEN** une connexion en cours
- **WHEN** le fournisseur renvoie un refus avec le state de la demande
- **THEN** l'IHM affiche un message explicite choisi dans une liste fixe, sans reprendre le texte reçu
- **AND** aucune session n'est ouverte

#### Scenario: ACC-07-S5 État du serveur public
- **GIVEN** une personne sans session
- **WHEN** elle ouvre la vue d'état du serveur
- **THEN** la vue s'affiche et interroge l'identité du serveur sans jeton

### Requirement: ACC-08 Conservation et cycle de vie des jetons
L'IHM NE DOIT PAS (MUST NOT) conserver les jetons ailleurs qu'en mémoire ni les placer dans une adresse ou un journal ; elle DOIT (MUST) renouveler le jeton d'accès avant son expiration, passer à l'état « session expirée » si le renouvellement échoue et oublier les jetons à la déconnexion.
Source : ADR-0027 (Proposé) ; ADR-0033 (Proposé) ; demande du lot 7. Statut : [EXIGENCE].

#### Scenario: ACC-08-S1 Jetons hors du stockage du navigateur
- **GIVEN** une session ouverte dans l'IHM
- **WHEN** on inspecte le stockage local, le stockage de session et les cookies lisibles de l'IHM
- **THEN** aucun jeton n'y figure
- **AND** la seule donnée de connexion écrite dans le stockage de session est la demande en cours du portail web, effacée dès le retour

#### Scenario: ACC-08-S2 Renouvellement avant expiration
- **GIVEN** une session ouverte avec un jeton d'accès valable 600 secondes
- **WHEN** 540 secondes se sont écoulées
- **THEN** l'IHM obtient un nouveau jeton d'accès sans interrompre la personne

#### Scenario: ACC-08-S3 Renouvellement refusé
- **GIVEN** une session ouverte
- **WHEN** le fournisseur refuse le renouvellement
- **THEN** l'IHM affiche l'état « session expirée » et propose de se reconnecter
- **AND** les jetons sont oubliés

#### Scenario: ACC-08-S4 Déconnexion
- **GIVEN** une session ouverte
- **WHEN** la personne se déconnecte
- **THEN** les jetons sont oubliés et la fin de session du fournisseur est ouverte
- **AND** une vue protégée ouverte ensuite affiche l'écran de connexion

### Requirement: ACC-09 Jeton porteur et refus présentés par l'IHM
L'IHM DOIT (MUST) joindre le jeton d'accès en en-tête `Authorization: Bearer` à chaque appel de /api/v1 hormis l'identité du serveur, relancer la connexion sur une réponse 401 et afficher un refus sur une réponse 403, avec un écran dédié quand le compte n'est pas créé faute d'invitation.
Source : ACC-01, ACC-02 ; ADR-0027 (Proposé) ; demande du lot 7. Statut : [EXIGENCE].

#### Scenario: ACC-09-S1 Appel authentifié
- **GIVEN** une session ouverte
- **WHEN** l'IHM appelle une route de /api/v1 autre que l'identité du serveur
- **THEN** la requête porte l'en-tête `Authorization: Bearer` avec le jeton d'accès
- **AND** le jeton ne figure pas dans l'adresse appelée

#### Scenario: ACC-09-S2 Identité du serveur sans jeton
- **GIVEN** une session ouverte
- **WHEN** l'IHM demande l'identité du serveur
- **THEN** la requête ne porte aucun en-tête `Authorization`

#### Scenario: ACC-09-S3 Réponse 401
- **GIVEN** une session ouverte
- **WHEN** l'API répond 401 à un appel
- **THEN** les jetons sont oubliés et l'écran de connexion s'affiche avec l'état « session expirée »

#### Scenario: ACC-09-S4 Compte non créé
- **GIVEN** une personne authentifiée sans compte Argos ni invitation en attente
- **WHEN** l'API répond 403 avec le code `INSCRIPTION_SUR_INVITATION`
- **THEN** l'IHM affiche un écran indiquant que l'accès est refusé faute d'invitation
- **AND** l'écran ne propose aucune invitation et les vues protégées restent inaccessibles

#### Scenario: ACC-09-S5 Autre réponse 403
- **GIVEN** une session ouverte
- **WHEN** l'API répond 403 pour un rôle insuffisant
- **THEN** l'IHM affiche un message de refus sans fermer la session

### Requirement: ACC-10 Connexion du client desktop
Le client desktop DOIT (MUST) mener l'autorisation dans le navigateur du système avec une redirection vers l'interface de bouclage 127.0.0.1 sur un port éphémère, sans afficher la page du fournisseur dans l'application ; sa politique de sécurité du contenu NE DOIT PAS (MUST NOT) autoriser d'autres connexions que le serveur Argos et le fournisseur d'identité configurés.
Source : ADR-0022 points 4 et 6 (Proposé) ; RFC 8252 §7.3 et §8.12 ; ADR-0033 (Proposé). Statut : [EXIGENCE].

#### Scenario: ACC-10-S1 Autorisation dans le navigateur du système
- **GIVEN** le client desktop sans session
- **WHEN** la personne lance la connexion
- **THEN** le navigateur du système s'ouvre sur la page d'autorisation, avec une redirection vers 127.0.0.1 et un port choisi par le système
- **AND** aucune page du fournisseur ne s'affiche dans la fenêtre de l'application

#### Scenario: ACC-10-S2 Retour unique sur l'interface de bouclage
- **GIVEN** une autorisation en cours
- **WHEN** le fournisseur redirige vers l'adresse de bouclage
- **THEN** le retour est transmis à la seule fenêtre principale, qui revient au premier plan
- **AND** l'écoute locale est fermée et la page affichée dans le navigateur ne reprend aucun paramètre reçu

#### Scenario: ACC-10-S3 Adresse d'autorisation non conforme
- **GIVEN** une écoute locale préparée
- **WHEN** l'IHM demande d'ouvrir une adresse hors de l'émetteur configuré, sans PKCE S256 ou avec une autre redirection
- **THEN** la demande est refusée
- **AND** aucun navigateur n'est ouvert

#### Scenario: ACC-10-S4 Politique de sécurité du contenu
- **GIVEN** le client desktop configuré avec un serveur Argos et un fournisseur d'identité
- **WHEN** l'IHM est servie par le protocole interne
- **THEN** la politique n'autorise les connexions que vers ces deux origines
- **AND** les scripts restent limités à l'IHM livrée

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

### Requirement: ACC-14 Écoute de bouclage fermée sur le seul state attendu
L'écoute de bouclage du client desktop DOIT (MUST) comparer le state de chaque retour reçu à celui de la demande d'autorisation en cours et ne transmettre le retour puis se fermer que s'ils sont égaux ; un retour au state absent ou différent NE DOIT PAS (MUST NOT) fermer l'écoute ni être transmis à l'IHM.
Source : AUD-SEC-23 (/docs/audit/2026-11-21/findings.md) ; ACC-10 ; ADR-0033 (Accepté) point 4 ; RFC 8252 §7.3. Statut : [PROPOSITION].

#### Scenario: ACC-14-S1 Retour au state étranger
- **GIVEN** une autorisation en cours avec une écoute de bouclage active
- **WHEN** un processus local envoie à l'adresse de retour une requête dont le state est absent ou différent de celui de la demande
- **THEN** la requête reçoit une réponse d'erreur de requête invalide
- **AND** l'écoute reste ouverte, rien n'est transmis à l'IHM et la connexion en cours peut aboutir

#### Scenario: ACC-14-S2 Retour au state attendu après un retour étranger
- **GIVEN** une écoute de bouclage qui a déjà refusé un retour au state étranger
- **WHEN** le fournisseur redirige vers l'adresse de retour avec le state de la demande
- **THEN** le retour est transmis à la fenêtre principale
- **AND** l'écoute est fermée
