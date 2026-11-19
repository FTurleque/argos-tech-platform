# Delta de spécification : profils-veille

## Purpose

Permettre à chaque utilisateur d'activer un ou plusieurs profils de veille (développeur, expert métier, puis d'autres publics), chacun apportant ses sources, ses rubriques, ses règles de rapprochement et ses garde-fous. Fiche : [/docs/specifications/profils-veille.md](/docs/specifications/profils-veille.md).

## ADDED Requirements

### Requirement: PRF-01 Profils actifs d'un utilisateur
Le système DOIT (MUST) permettre à un utilisateur d'activer un ou plusieurs profils publiés au catalogue, et NE DOIT PAS (MUST NOT) le laisser sans aucun profil actif.
Source : demande du porteur (2026-11-15) ; ADR-0024 (Accepté) point 2. Statut : [PROPOSITION].

#### Scenario: PRF-01-S1 Cumul de deux profils
- **GIVEN** un utilisateur dont le seul profil actif est `developpeur`
- **WHEN** il active le profil `expert-metier`
- **THEN** ses profils actifs sont `developpeur` et `expert-metier`

#### Scenario: PRF-01-S2 Désactivation du dernier profil
- **GIVEN** un utilisateur dont le seul profil actif est `expert-metier`
- **WHEN** il demande à le désactiver
- **THEN** la demande est refusée avec un message indiquant qu'au moins un profil doit rester actif
- **AND** ses profils actifs sont inchangés

#### Scenario: PRF-01-S3 Profil absent du catalogue
- **GIVEN** un utilisateur authentifié
- **WHEN** il demande à activer un profil dont l'identifiant n'est pas publié au catalogue
- **THEN** la demande est refusée avec un message indiquant que le profil n'existe pas
- **AND** ses profils actifs sont inchangés

### Requirement: PRF-02 Veille restituée comme l'union des profils actifs
Le système DOIT (MUST) restituer à un utilisateur l'union des éléments de veille relevant de ses profils actifs et des éléments retenus par ses abonnements `PERSONAL`, dans la limite de ses droits, chaque élément n'apparaissant qu'une fois, avec les rubriques de chacun des profils dont il relève.
Source : ADR-0024 (Accepté) point 2 ; décision Q-3 du 2026-11-16 (abonnements `PERSONAL`, ABO-04). Statut : [PROPOSITION].

#### Scenario: PRF-02-S1 Élément commun à deux profils
- **GIVEN** un utilisateur dont les profils actifs sont `developpeur` et `expert-metier`
- **AND** un élément de veille qui relève des deux profils
- **WHEN** l'utilisateur consulte sa veille
- **THEN** l'élément est restitué une seule fois
- **AND** il porte les rubriques des deux profils

#### Scenario: PRF-02-S2 Élément d'un profil inactif
- **GIVEN** un utilisateur dont le seul profil actif est `developpeur`
- **AND** un élément de veille qui ne relève que du profil `expert-metier`
- **WHEN** l'utilisateur consulte sa veille
- **THEN** l'élément n'est pas restitué

#### Scenario: PRF-02-S3 Source d'un abonnement personnel hors des profils actifs
- **GIVEN** un utilisateur dont le seul profil actif est `expert-metier`
- **AND** un abonnement `PERSONAL` de cet utilisateur qui retient un avis de sécurité
- **WHEN** l'utilisateur consulte sa veille
- **THEN** l'avis est restitué une seule fois, avec l'abonnement qui l'a retenu
- **AND** les éléments de son profil actif restent restitués avec leurs rubriques

#### Scenario: PRF-02-S4 Abonnement personnel dans la limite des droits
- **GIVEN** un utilisateur membre du seul espace A
- **AND** un abonnement `PERSONAL` de cet utilisateur qui vise un projet de l'espace B
- **WHEN** l'utilisateur consulte sa veille
- **THEN** aucun élément ni projet de l'espace B n'est restitué

### Requirement: PRF-03 Profils initiaux
Le système DOIT (MUST) publier au catalogue les profils `developpeur`, qui rapproche les signaux des composants des projets, et `expert-metier`, qui rapproche les textes officiels des domaines et modules produits ; ces profils reprennent les règles de rapprochement proposées par ADR-0013 et ADR-0018.
Source : ADR-0024 (Accepté) point 3 ; ADR-0013 (Proposé) ; ADR-0018 (Proposé). Statut : [PROPOSITION].

#### Scenario: PRF-03-S1 Catalogue initial
- **GIVEN** une installation d'Argos sans profil ajouté
- **WHEN** un utilisateur consulte le catalogue des profils
- **THEN** il voit les profils `developpeur` et `expert-metier`, chacun avec son nom et sa description

#### Scenario: PRF-03-S2 Rapprochement propre au profil
- **GIVEN** un utilisateur dont le seul profil actif est `developpeur`
- **AND** un avis de sécurité visant un composant d'un projet qu'il est autorisé à voir
- **WHEN** l'avis est rapproché des projets
- **THEN** l'impact est restitué avec un niveau de confiance calculé sans IA

### Requirement: PRF-04 Ajout d'un profil sans effet sur les autres
Le système DOIT (MUST) permettre de publier un nouveau profil au catalogue sans modifier la veille restituée aux utilisateurs qui ne l'activent pas.
Source : ADR-0024 (Accepté) points 1 et 8. Statut : [PROPOSITION].

#### Scenario: PRF-04-S1 Publication d'un profil de démonstration
- **GIVEN** des utilisateurs dont les profils actifs sont `developpeur` ou `expert-metier`
- **WHEN** l'administrateur plateforme publie un profil de démonstration au catalogue
- **THEN** le profil de démonstration apparaît au catalogue
- **AND** la veille restituée à ces utilisateurs est inchangée

### Requirement: PRF-05 Aucun droit accordé par un profil
Le système NE DOIT PAS (MUST NOT) donner accès, au titre d'un profil de veille, à un projet ou à une donnée interne situés hors du périmètre autorisé de l'utilisateur ; l'autorisation reste déterminée uniquement par ses rôles.
Source : ADR-0024 (Accepté) point 6 ; ADR-0016 (Proposé) ; exigence ABO-05. Statut : [PROPOSITION].

#### Scenario: PRF-05-S1 Activation d'un profil sans droit sur les projets
- **GIVEN** un utilisateur sans droit sur les projets de l'espace B
- **WHEN** il active le profil `developpeur`
- **THEN** aucun impact sur un projet de l'espace B ne lui est restitué
- **AND** ses rôles et autorisations sont inchangés

### Requirement: PRF-06 Garde-fous d'un profil appliqués avant diffusion
Le système DOIT (MUST) appliquer les garde-fous déclarés par un profil, comme une validation humaine obligatoire, avant de restituer un élément relevant de ce profil, dans toutes les mises en page et via le serveur MCP.
Source : ADR-0024 (Accepté) point 7 ; ADR-0018 (Proposé). Statut : [PROPOSITION].

#### Scenario: PRF-06-S1 Fiche réglementaire non validée
- **GIVEN** une fiche d'analyse du profil `expert-metier` qui n'a pas encore été validée par un expert métier
- **WHEN** un développeur consulte sa veille ou interroge le serveur MCP
- **THEN** la fiche ne lui est pas restituée

#### Scenario: PRF-06-S2 Fiche validée
- **GIVEN** la même fiche, validée par l'expert métier référent
- **WHEN** un utilisateur dont un profil actif couvre ce domaine consulte sa veille
- **THEN** la fiche lui est restituée avec la mention de sa validation
