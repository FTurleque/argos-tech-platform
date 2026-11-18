## ADDED Requirements

### Requirement: ACC-07 Connexion à l'IHM
L'IHM, portail web comme client desktop, DOIT (MUST) exiger une session pour toute vue autre que l'état du serveur et obtenir cette session auprès du fournisseur d'identité par le flux code d'autorisation avec PKCE S256, en vérifiant le state du retour ; aucun mot de passe n'est saisi dans Argos.
Source : ADR-0027 (Proposé) ; ADR-0022 point 6 (Proposé) ; ADR-0033 (Proposé) ; demande du lot 7 (connecter-ihm-oidc). Statut : [PROPOSITION].

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
Source : ADR-0027 (Proposé) ; ADR-0033 (Proposé) ; demande du lot 7. Statut : [PROPOSITION].

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
Source : ACC-01, ACC-02 ; ADR-0027 (Proposé) ; demande du lot 7. Statut : [PROPOSITION].

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
Source : ADR-0022 points 4 et 6 (Proposé) ; RFC 8252 §7.3 et §8.12 ; ADR-0033 (Proposé). Statut : [PROPOSITION].

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
