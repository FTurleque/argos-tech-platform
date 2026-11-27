# alertes-digests Specification

## Purpose
Prévenir chaque utilisateur, sur le bon canal et au bon rythme, des éléments de veille qui le concernent, sans l'inonder d'alertes inutiles. Fiche : [/docs/specifications/alertes-digests.md](/docs/specifications/alertes-digests.md).

## Requirements

### Requirement: ALR-01 Alerte selon les règles de l'utilisateur
Le système DOIT (MUST) émettre une alerte immédiate lorsqu'un élément satisfait une règle d'abonnement de fréquence immédiate de l'utilisateur (type, gravité minimale, projets).
Source : ABO-02 ; ADR-0010 (Proposé). Statut : [EXIGENCE].

#### Scenario: ALR-01-S1 Faille critique sur un projet suivi
- **GIVEN** un utilisateur abonné aux failles de gravité élevée de ses projets, en alerte immédiate
- **WHEN** une faille critique touche un de ses projets avec un impact exact
- **THEN** une alerte lui est envoyée sur le canal de la règle

#### Scenario: ALR-01-S2 Gravité insuffisante
- **GIVEN** la même règle
- **WHEN** une faille de gravité faible touche ce projet
- **THEN** aucune alerte immédiate n'est envoyée
- **AND** l'élément figure dans son prochain résumé

### Requirement: ALR-02 Pas d'alerte immédiate sur un impact à vérifier
Le système NE DOIT PAS (MUST NOT) émettre d'alerte immédiate pour un impact de niveau « à vérifier » ; l'élément reste visible dans le fil.
Source : ADR-0013 (Proposé) ; arc42 §11 (bruit). Statut : [EXIGENCE].

#### Scenario: ALR-02-S1 Correspondance par nom seulement
- **GIVEN** un article mentionnant une technologie utilisée par un projet de l'utilisateur
- **WHEN** le rapprochement le classe « à vérifier »
- **THEN** aucune alerte immédiate n'est envoyée
- **AND** l'élément apparaît dans le fil de veille

### Requirement: ALR-03 Une alerte par signal, utilisateur et canal
Le système DOIT (MUST) regrouper en une seule alerte, par utilisateur et par canal, un même signal qui satisfait plusieurs règles ou touche plusieurs projets.
Source : dossier v2.4 §8.4 (regroupement). Statut : [EXIGENCE].

#### Scenario: ALR-03-S1 Signal touchant trois projets
- **GIVEN** un utilisateur dont trois projets sont touchés par la même faille
- **WHEN** l'alerte est émise
- **THEN** il reçoit une seule alerte qui cite les trois projets

### Requirement: ALR-04 Résumés périodiques
Le système DOIT (MUST) envoyer à chaque utilisateur qui l'a demandé un résumé quotidien ou hebdomadaire des éléments qui ne lui ont pas été alertés immédiatement.
Source : ABO-02 (fréquence) ; arc42 §8.2. Statut : [EXIGENCE].

#### Scenario: ALR-04-S1 Résumé hebdomadaire
- **GIVEN** un utilisateur abonné au résumé hebdomadaire de l'actualité
- **WHEN** l'heure du résumé arrive
- **THEN** il reçoit un résumé listant les éléments de la semaine avec leurs sources

#### Scenario: ALR-04-S2 Rien de nouveau
- **GIVEN** un utilisateur sans aucun élément nouveau depuis son dernier résumé
- **WHEN** l'heure du résumé arrive
- **THEN** aucun résumé ne lui est envoyé

### Requirement: ALR-05 Diffusion sans IA bloquante
Le système DOIT (MUST) envoyer une alerte même lorsque son résumé IA est indisponible, refusé ou au-delà du plafond de coût, en le signalant dans l'alerte.
Source : baseline v2.3 §6 ; QS-10 ; COR-05. Statut : [EXIGENCE].

#### Scenario: ALR-05-S1 Plafond IA atteint
- **GIVEN** le plafond mensuel des appels IA est atteint
- **WHEN** une faille critique touche un projet suivi
- **THEN** l'alerte est envoyée sans résumé IA
- **AND** elle indique que le résumé n'est pas disponible

### Requirement: ALR-06 Canaux de diffusion
Le système DOIT (MUST) diffuser les alertes et résumés par e-mail et vers le client desktop, chaque message contenant un lien vers l'élément et ses sources, sans aucune donnée d'un espace non autorisé.
Source : ADR-0022 (Proposé) ; ACC-04. Statut : [EXIGENCE].

#### Scenario: ALR-06-S1 Alerte par e-mail
- **GIVEN** une règle dont le canal est l'e-mail
- **WHEN** une alerte est émise
- **THEN** l'e-mail contient le titre, la gravité, les projets autorisés du destinataire, les sources et un lien vers l'élément

#### Scenario: ALR-06-S2 Échec d'envoi
- **GIVEN** le serveur d'e-mails est injoignable
- **WHEN** une alerte doit partir par e-mail
- **THEN** l'envoi est retenté puis marqué en échec s'il n'aboutit pas
- **AND** l'alerte reste visible dans Argos

### Requirement: ALR-07 Aucune alerte pour un impact écarté ou résolu
Le système NE DOIT PAS (MUST NOT) émettre d'alerte immédiate ni citer dans un résumé périodique un impact à l'état `écarté` ou `résolu` (COR-09) ; un impact qui repasse `ouvert` redevient éligible selon ALR-01 à ALR-03.
Source : décision J5 du porteur du projet (2026-11-19) ; COR-09, COR-11. Statut : [PROPOSITION].

#### Scenario: ALR-07-S1 Impact écarté avant l'envoi
- **GIVEN** un impact `exact` qui satisfait une règle d'alerte immédiate de `jules`, en attente d'envoi
- **WHEN** un contributeur écarte l'impact avant l'envoi
- **THEN** aucune alerte n'est envoyée à `jules` pour cet impact

#### Scenario: ALR-07-S2 Résumé sans impact résolu
- **GIVEN** un impact `probable` destiné au résumé quotidien de `jules`, résolu par le système avant l'heure du résumé
- **WHEN** le résumé quotidien est produit
- **THEN** il ne cite pas cet impact
- **AND** aucun résumé n'est envoyé s'il ne reste aucun élément (ALR-04)

### Requirement: ALR-08 E-mail seulement vers une adresse vérifiée
Le système DOIT (MUST) n'envoyer d'alerte, de résumé ou de rappel par e-mail qu'à l'adresse que le fournisseur d'identité déclare vérifiée pour le compte, tenue à jour à chaque connexion ; sans adresse vérifiée connue, il NE DOIT PAS (MUST NOT) envoyer d'e-mail et laisse l'élément visible dans Argos et sur le poste.
Source : AUD-SEC-04 (/docs/audit/2026-11-21/findings.md) ; ALR-06 ; ADR-0027 (Accepté) ; comptes existants : décision du porteur, 2026-11-24. Statut : [PROPOSITION].

#### Scenario: ALR-08-S1 Adresse non vérifiée
- **GIVEN** un compte dont le jeton porte une adresse e-mail que le fournisseur ne déclare pas vérifiée
- **AND** une règle d'alerte de ce compte au canal e-mail
- **WHEN** une alerte, un résumé ou un rappel réglementaire doit lui être diffusé
- **THEN** aucun e-mail n'est envoyé, ni à cette adresse ni à une autre
- **AND** l'élément reste visible dans Argos et sur le client desktop

#### Scenario: ALR-08-S2 Adresse vérifiée
- **GIVEN** un compte dont l'adresse e-mail est déclarée vérifiée par le fournisseur
- **WHEN** une alerte au canal e-mail lui est diffusée
- **THEN** l'e-mail est envoyé à cette adresse vérifiée

#### Scenario: ALR-08-S3 Adresse vérifiée modifiée chez le fournisseur
- **GIVEN** un compte existant dont l'adresse vérifiée a changé chez le fournisseur
- **WHEN** l'utilisateur se reconnecte puis qu'une alerte au canal e-mail lui est diffusée
- **THEN** l'e-mail est envoyé à la nouvelle adresse vérifiée
- **AND** plus aucun e-mail ne part vers l'ancienne adresse

#### Scenario: ALR-08-S4 Compte existant avant la mise en place de la règle
- **GIVEN** un compte créé avant la mise en place de la règle, dont une adresse e-mail est enregistrée
- **WHEN** la mise à jour est déployée puis qu'une alerte au canal e-mail doit lui être diffusée avant sa prochaine connexion
- **THEN** aucun e-mail n'est envoyé
- **AND** après sa reconnexion avec un jeton qui déclare son adresse vérifiée, les e-mails suivants partent à cette adresse

### Requirement: ALR-09 Liens des alertes vers des vues de l'IHM
Le système DOIT (MUST) construire chaque lien d'une alerte, d'un résumé ou d'un rappel à partir de l'adresse publique de la plateforme, au format d'adresse de l'IHM, vers une vue existante : le signal, le radar de l'espace ou l'opportunité concernés ; un lien NE DOIT PAS (MUST NOT) viser un chemin que l'IHM ne sert pas.
Source : AUD-ARC-22 ; ALR-06 ; ADR-0038. Statut : [PROPOSITION].

#### Scenario: ALR-09-S1 Lien d'un signal
- **GIVEN** l'adresse publique `https://argos.example` et une alerte sur le signal S
- **WHEN** l'alerte est produite
- **THEN** le lien de son élément vaut `https://argos.example/#/signaux/S`
- **AND** ouvert dans l'IHM, il affiche la vue du signal S après connexion

#### Scenario: ALR-09-S2 Liens du radar et des opportunités
- **GIVEN** la même adresse publique
- **WHEN** un rappel du radar de l'espace E ou un rappel d'opportunité O est produit
- **THEN** le lien vaut `https://argos.example/#/espaces/E/radar` ou `https://argos.example/#/profils/artiste/opportunites/O`

#### Scenario: ALR-09-S3 Gabarits couverts par l'IHM
- **GIVEN** les gabarits de lien produits par le serveur
- **WHEN** la suite de tests de l'IHM s'exécute
- **THEN** chaque gabarit correspond à une route déclarée de l'IHM, sinon la suite échoue

### Requirement: ALR-10 Évaluation des alertes en un nombre fixe de requêtes
Le système DOIT (MUST) évaluer les règles d'alerte des impacts publiés d'un signal en un nombre de requêtes indépendant du nombre de membres des espaces touchés et de leurs canaux, et DOIT (MUST) laisser la diffusion des alertes créées à la tâche planifiée d'envoi, sans changer les alertes ni les résumés produits (ALR-01 à ALR-08).
Source : AUD-PER-04 ; critère de sortie du sprint 10 ; décision du porteur du projet (2026-11-26, lecture technique filtrée par appartenance). Statut : [PROPOSITION].

#### Scenario: ALR-10-S1 Nombre de requêtes constant
- **GIVEN** deux espaces touchés par un signal, l'un de 2 membres, l'autre de 11 membres, chacun avec des abonnements immédiats et périodiques sur deux canaux
- **WHEN** les règles sont évaluées pour chaque espace
- **THEN** les deux évaluations exécutent le même nombre de requêtes

#### Scenario: ALR-10-S2 Diffusion par la tâche d'envoi
- **GIVEN** une alerte immédiate créée par l'évaluation d'un signal
- **WHEN** l'évaluation se termine
- **THEN** l'alerte est enregistrée en attente d'envoi
- **AND** elle est diffusée au passage suivant de la tâche planifiée d'envoi

#### Scenario: ALR-10-S3 Contenu limité aux espaces du destinataire
- **GIVEN** un signal qui touche un projet d'un espace A et un projet d'un espace B, et un destinataire membre de A seulement
- **WHEN** son alerte est créée
- **THEN** elle ne cite que le projet de l'espace A
