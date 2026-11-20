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
