# Delta de spécification : inventaire-sbom

## ADDED Requirements

### Requirement: INV-12 Administration des projets et de leurs dépôts
Le système DOIT (MUST) permettre à l'administrateur d'un espace, par l'API et l'IHM, de créer un projet dans cet espace, de lui rattacher un dépôt GitHub ou GitLab et de détacher un dépôt ; il NE DOIT PAS (MUST NOT) l'autoriser à un autre rôle ni hors de l'espace de l'appelant.
Source : AUD-QUA-01 ; INV-10 ; ADR-0016 (Accepté) ; décision du porteur du projet, 2026-11-26. Statut : [PROPOSITION].

#### Scenario: INV-12-S1 Création d'un projet avec son premier dépôt
- **GIVEN** un administrateur de l'espace E
- **WHEN** il crée le projet « portail » dans E en indiquant le dépôt GitHub `org/portail`, branche `main`
- **THEN** la réponse est 201 avec l'identifiant du projet
- **AND** le projet apparaît dans la liste des projets de l'appelant et la synthèse de son inventaire liste `org/portail` comme dépôt principal, sans import

#### Scenario: INV-12-S2 Rattachement d'un second dépôt
- **GIVEN** un projet de l'espace E qui a déjà un dépôt principal
- **WHEN** l'administrateur de E lui rattache le dépôt GitLab `groupe/api`
- **THEN** la réponse est 201 et la synthèse liste deux dépôts, `groupe/api` non principal

#### Scenario: INV-12-S3 Détachement d'un dépôt
- **GIVEN** un projet à deux dépôts importés
- **WHEN** l'administrateur détache le second dépôt
- **THEN** la réponse est 204, la synthèse ne liste plus ce dépôt et l'inventaire du projet ne contient plus les composants de ce seul dépôt

#### Scenario: INV-12-S4 Rôle insuffisant
- **GIVEN** un contributeur de l'espace E
- **WHEN** il tente de créer un projet dans E ou d'y rattacher un dépôt
- **THEN** la réponse est 403 et le refus est journalisé (motif `ROLE_INSUFFISANT`)
- **AND** aucun projet ni dépôt n'est enregistré

#### Scenario: INV-12-S5 Espace étranger
- **GIVEN** un utilisateur qui n'est pas membre de l'espace F
- **WHEN** il tente de créer un projet dans F ou de rattacher un dépôt à un projet de F
- **THEN** la réponse est 404 et le refus est journalisé (motif `NON_MEMBRE`)

#### Scenario: INV-12-S6 Saisie invalide
- **GIVEN** un administrateur de l'espace E
- **WHEN** il crée un projet sans nom, ou rattache un dépôt d'une forge autre que GitHub ou GitLab, ou sans identifiant de dépôt
- **THEN** la réponse est 400 `DONNEE_INVALIDE` avec l'attribut en cause et rien n'est enregistré

### Requirement: INV-13 Import manuel par l'API et l'IHM
Le système DOIT (MUST) permettre à un contributeur ou à un administrateur de l'espace d'un projet d'importer une SBOM CycloneDX JSON, d'importer des manifestes de dépendances et de relancer l'import depuis les dépôts du projet, par l'API et l'IHM, avec les mêmes règles que l'import planifié (INV-01, INV-05, INV-08, INV-09).
Source : AUD-QUA-01 ; INV-08 ; décision du porteur du projet, 2026-11-26. Statut : [PROPOSITION].

#### Scenario: INV-13-S1 Import d'une SBOM conforme
- **GIVEN** un projet de l'espace E et un contributeur de E
- **WHEN** il envoie une SBOM CycloneDX 1.6 JSON de trois composants dotés d'un PURL
- **THEN** la réponse est 200 avec le statut `IMPORTED`, l'origine `SBOM` et trois composants
- **AND** la page des composants du projet liste les trois composants et les avis connus sont recorrélés pour le projet

#### Scenario: INV-13-S2 SBOM non conforme
- **GIVEN** un projet doté d'un inventaire
- **WHEN** un contributeur envoie un document qui n'est pas une SBOM CycloneDX exploitable
- **THEN** la réponse est 200 avec le statut `REJECTED` et le motif
- **AND** l'inventaire en place est conservé et l'échec apparaît dans les imports refusés du projet

#### Scenario: INV-13-S3 Document trop volumineux
- **GIVEN** la taille maximale configurée des documents importés
- **WHEN** un contributeur envoie une SBOM plus grande que cette taille
- **THEN** l'import est refusé avec le statut `REJECTED` et l'inventaire en place est conservé

#### Scenario: INV-13-S4 Import de manifestes
- **GIVEN** un projet sans SBOM
- **WHEN** un contributeur envoie un `package.json` lisible
- **THEN** la réponse est 200 avec le statut `IMPORTED` et l'origine `MANIFEST`

#### Scenario: INV-13-S5 Relance depuis les dépôts
- **GIVEN** un projet rattaché à un dépôt dont la forge publie une SBOM
- **WHEN** un contributeur relance l'import du projet
- **THEN** la réponse donne l'issue de chaque dépôt et l'inventaire du dépôt importé est remplacé
- **AND** un projet sans dépôt reçoit 409 sans modification

#### Scenario: INV-13-S6 Lecteur
- **GIVEN** un lecteur de l'espace du projet
- **WHEN** il tente un import ou une relance
- **THEN** la réponse est 403 et le refus est journalisé
