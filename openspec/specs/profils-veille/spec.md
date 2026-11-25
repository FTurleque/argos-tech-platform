# profils-veille Specification

## Purpose
Permettre à chaque utilisateur d'activer un ou plusieurs profils de veille (développeur, expert métier, puis d'autres publics), chacun apportant ses sources, ses rubriques, ses règles de rapprochement et ses garde-fous. Fiche : [/docs/specifications/profils-veille.md](/docs/specifications/profils-veille.md).

## Requirements

### Requirement: PRF-01 Profils actifs d'un utilisateur
Le système DOIT (MUST) permettre à un utilisateur d'activer un ou plusieurs profils publiés au catalogue, et NE DOIT PAS (MUST NOT) le laisser sans aucun profil actif.
Source : demande du porteur (2026-11-15) ; ADR-0024 (Accepté) point 2. Statut : [EXIGENCE].

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
Source : ADR-0024 (Accepté) point 2 ; décision Q-3 du 2026-11-16 (abonnements `PERSONAL`, ABO-04). Statut : [EXIGENCE].

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
Source : ADR-0024 (Accepté) point 3 ; ADR-0013 (Proposé) ; ADR-0018 (Remplacé par ADR-0030). Statut : [EXIGENCE].

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
Source : ADR-0024 (Accepté) points 1 et 8. Statut : [EXIGENCE].

#### Scenario: PRF-04-S1 Publication d'un profil de démonstration
- **GIVEN** des utilisateurs dont les profils actifs sont `developpeur` ou `expert-metier`
- **WHEN** l'administrateur plateforme publie un profil de démonstration au catalogue
- **THEN** le profil de démonstration apparaît au catalogue
- **AND** la veille restituée à ces utilisateurs est inchangée

### Requirement: PRF-05 Aucun droit accordé par un profil
Le système NE DOIT PAS (MUST NOT) donner accès, au titre d'un profil de veille, à un projet ou à une donnée interne situés hors du périmètre autorisé de l'utilisateur ; l'autorisation reste déterminée uniquement par ses rôles.
Source : ADR-0024 (Accepté) point 6 ; ADR-0016 (Proposé) ; exigence ABO-05. Statut : [EXIGENCE].

#### Scenario: PRF-05-S1 Activation d'un profil sans droit sur les projets
- **GIVEN** un utilisateur sans droit sur les projets de l'espace B
- **WHEN** il active le profil `developpeur`
- **THEN** aucun impact sur un projet de l'espace B ne lui est restitué
- **AND** ses rôles et autorisations sont inchangés

### Requirement: PRF-06 Garde-fous d'un profil appliqués avant diffusion
Le système DOIT (MUST) appliquer les garde-fous déclarés par un profil, comme une validation humaine obligatoire, avant de restituer un élément relevant de ce profil, dans toutes les mises en page et via le serveur MCP.
Source : ADR-0024 (Accepté) point 7 ; ADR-0018 (Remplacé par ADR-0030). Statut : [EXIGENCE].

#### Scenario: PRF-06-S1 Fiche réglementaire non validée
- **GIVEN** une fiche d'analyse du profil `expert-metier` qui n'a pas encore été validée par un expert métier
- **WHEN** un développeur consulte sa veille ou interroge le serveur MCP
- **THEN** la fiche ne lui est pas restituée

#### Scenario: PRF-06-S2 Fiche validée
- **GIVEN** la même fiche, validée par l'expert métier référent
- **WHEN** un utilisateur dont un profil actif couvre ce domaine consulte sa veille
- **THEN** la fiche lui est restituée avec la mention de sa validation

### Requirement: PRF-07 Restitution de « Ma veille » dans la cible de latence
Le système DOIT (MUST) restituer la veille d'un utilisateur (`GET /api/v1/me/veille`) avec un p95 inférieur à 500 ms, mesuré par la campagne de performance avec le jeu de données de référence, sans modifier le contenu restitué (éléments, champs, ordre, droits : PRF-02, PRF-05).
Source : décisions K2 et K3 du porteur du projet (2026-11-19) ; arc42 §8, §10.2 Q-05 ; PERF-01. Statut : [PROPOSITION].

#### Scenario: PRF-07-S1 Ma veille dans la cible avec le jeu de référence
- **GIVEN** la pile de test locale chargée du jeu de référence : un espace de 20 projets de 100 composants, 1 000 avis et 20 000 impacts
- **AND** un utilisateur membre de cet espace, au profil actif `developpeur`, avec un abonnement `PERSONAL`
- **WHEN** la campagne de performance appelle `GET /api/v1/me/veille` 200 fois avec 4 clients simultanés, après échauffement
- **THEN** le rapport indique pour cette opération un p95 inférieur à 500 ms

#### Scenario: PRF-07-S2 Contenu restitué inchangé
- **GIVEN** des avis aux dates distinctes, dont un sans date de publication, qui impactent plusieurs projets de l'espace d'un utilisateur
- **AND** un impact écarté et un projet d'un espace dont l'utilisateur n'est pas membre
- **WHEN** l'utilisateur consulte sa veille
- **THEN** les éléments restitués, leurs champs et leur ordre sont ceux de l'union de ses profils actifs (PRF-02), du plus récent au plus ancien
- **AND** aucun impact écarté ni aucun projet d'un espace dont il n'est pas membre n'est restitué

### Requirement: PRF-08 Limite de débit de « Ma veille »
Le système DOIT (MUST) limiter par compte et par minute le nombre de restitutions de « Ma veille » ; au-delà de la limite configurée, il refuse la demande avec une erreur de limite qui indique quand réessayer, journalise le refus et n'affecte pas les autres comptes.
Source : AUD-SEC-09 (/docs/audit/2026-11-21/findings.md) ; ACC-06 ; PRF-07 ; valeur : décision du porteur, 2026-11-24. Statut : [PROPOSITION].

#### Scenario: PRF-08-S1 Limite atteinte
- **GIVEN** un compte qui a consulté « Ma veille » 60 fois dans la dernière minute, avec la limite par défaut
- **WHEN** il consulte sa veille
- **THEN** la demande est refusée avec une erreur de limite de débit et un délai avant nouvel essai
- **AND** le refus est journalisé et aucune donnée de veille n'est renvoyée

#### Scenario: PRF-08-S2 Autres comptes non affectés
- **GIVEN** un compte qui a atteint sa limite de restitutions de « Ma veille »
- **WHEN** un autre compte consulte sa veille dans la même minute
- **THEN** la veille de l'autre compte est restituée normalement

#### Scenario: PRF-08-S3 Compteur distinct de celui de la recherche
- **GIVEN** un compte qui a atteint sa limite de recherches plein texte pour la minute en cours
- **WHEN** il consulte « Ma veille » dans la même minute
- **THEN** sa veille est restituée normalement
