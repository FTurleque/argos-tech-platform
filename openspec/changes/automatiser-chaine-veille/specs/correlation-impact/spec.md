# Delta de spécification : correlation-impact

## Purpose

Relier de façon déterministe, explicable et traçable un signal de sécurité aux seuls projets dont l'inventaire contient un composant affecté, sans dépendre d'un fournisseur d'IA. Fiche : [/docs/specifications/correlation-impact.md](/docs/specifications/correlation-impact.md).

## ADDED Requirements

### Requirement: COR-09 Niveau de confiance et état de traitement distincts
Le système DOIT (MUST) porter pour chaque impact, d'une part son niveau de confiance (`exact`, `probable`, `à vérifier`, COR-02), d'autre part son état de traitement (`ouvert`, `confirmé`, `écarté`, `résolu`) ; un impact créé DOIT (MUST) être `ouvert`, et un changement de l'un des deux axes NE DOIT PAS (MUST NOT) modifier l'autre.
Source : décision J5 du porteur du projet (2026-11-19, Q-COR-2) ; ADR-0013 (Proposé). Statut : [PROPOSITION].

#### Scenario: COR-09-S1 Impact créé
- **GIVEN** un signal qui satisfait COR-01 pour un composant d'un projet
- **WHEN** la corrélation crée l'impact
- **THEN** l'impact porte son niveau selon COR-02 et l'état `ouvert`
- **AND** les deux valeurs sont restituées séparément à la consultation

#### Scenario: COR-09-S2 Niveau recalculé sans effet sur l'état
- **GIVEN** un impact `confirmé` au niveau `exact`
- **WHEN** une nouvelle corrélation lui attribue le niveau `probable` (SBOM devenue obsolète)
- **THEN** l'impact porte le niveau `probable` et reste `confirmé`

#### Scenario: COR-09-S3 État changé sans effet sur le niveau
- **GIVEN** un impact `ouvert` au niveau `à vérifier`
- **WHEN** un utilisateur autorisé le confirme
- **THEN** l'impact est `confirmé` et garde le niveau `à vérifier`

### Requirement: COR-10 Transitions d'état par un utilisateur autorisé
Le système DOIT (MUST) n'accepter que les transitions `ouvert`→`confirmé`, `ouvert`→`écarté`, `ouvert`→`résolu`, `confirmé`→`résolu`, `confirmé`→`écarté`, `écarté`→`ouvert` et `résolu`→`ouvert`, demandées par un membre `CONTRIBUTEUR` ou `ADMINISTRATEUR` de l'espace du projet ; passer à `écarté` DOIT (MUST) être motivé. Toute autre demande est refusée sans effet.
Source : décision J5 du porteur du projet (2026-11-19) ; ADR-0016 (Proposé) ; rôles de `comptes-acces`. Statut : [PROPOSITION].

#### Scenario: COR-10-S1 Confirmation par un contributeur
- **GIVEN** un impact `ouvert` d'un projet de l'espace A et un membre `CONTRIBUTEUR` de l'espace A
- **WHEN** ce membre demande la transition vers `confirmé` avec un commentaire
- **THEN** l'impact est `confirmé`
- **AND** la transition est historisée avec l'auteur, la date, l'état de départ, l'état d'arrivée et le commentaire

#### Scenario: COR-10-S2 Lecteur refusé
- **GIVEN** un impact `ouvert` d'un projet de l'espace A et un membre `LECTEUR` de l'espace A
- **WHEN** ce membre demande la transition vers `écarté`
- **THEN** la demande est refusée par une réponse 403
- **AND** l'impact reste `ouvert` et le refus est journalisé avec le compte, la ressource et le motif `ROLE_INSUFFISANT`

#### Scenario: COR-10-S3 Transition non prévue
- **GIVEN** un impact `écarté`
- **WHEN** un membre autorisé demande la transition vers `confirmé`
- **THEN** la demande est refusée par une réponse 409 qui nomme les transitions possibles
- **AND** l'impact reste `écarté` et aucune transition n'est historisée

#### Scenario: COR-10-S4 Écarter sans motif
- **GIVEN** un impact `ouvert` et un membre autorisé
- **WHEN** ce membre demande la transition vers `écarté` sans motif
- **THEN** la demande est refusée par une réponse 400 `DONNEE_INVALIDE`
- **AND** l'impact reste `ouvert`

#### Scenario: COR-10-S5 Impact d'un autre espace
- **GIVEN** un membre `ADMINISTRATEUR` de l'espace A seulement
- **WHEN** il demande une transition sur un impact d'un projet de l'espace B
- **THEN** la demande est refusée par une réponse 404 sans révéler l'impact
- **AND** le refus est journalisé avec le motif `NON_MEMBRE`

### Requirement: COR-11 Résolution automatique d'un impact devenu sans objet
Le système DOIT (MUST) passer à `résolu`, avec l'auteur « système » et un motif, un impact `ouvert` ou `confirmé` qu'une nouvelle corrélation ne vérifie plus, et le conserver consultable ; il NE DOIT PAS (MUST NOT) modifier l'état d'un impact `écarté`. Un impact `résolu` de nouveau vérifié DOIT (MUST) repasser `ouvert`.
Source : décisions J4 et J5 du porteur du projet (2026-11-19) ; arc42 §4.2 (P3). Statut : [PROPOSITION].

#### Scenario: COR-11-S1 Composant retiré de l'inventaire
- **GIVEN** un impact `confirmé` sur `pkg:npm/exemple@1.5.0` d'un projet
- **WHEN** un import retire ce composant de l'inventaire du projet et que la recorrélation s'exécute
- **THEN** l'impact est `résolu` avec le motif `COMPOSANT_RETIRE` et l'auteur « système »
- **AND** l'impact, sa provenance et son historique restent consultables

#### Scenario: COR-11-S2 Composant mis à jour hors plage
- **GIVEN** un impact `ouvert` sur `pkg:npm/exemple@1.5.0` pour un signal qui affecte les versions inférieures à 2.0.0
- **WHEN** un import remplace ce composant par `pkg:npm/exemple@2.1.0` et que la recorrélation s'exécute
- **THEN** l'impact est `résolu` avec le motif `VERSION_HORS_PLAGE`
- **AND** aucun impact n'est créé pour `pkg:npm/exemple@2.1.0`

#### Scenario: COR-11-S3 Avis modifié ou retiré par sa source
- **GIVEN** un impact `ouvert` pour un signal
- **WHEN** la source modifie l'avis de sorte que le composant n'est plus affecté, ou retire l'avis, et que la corrélation du signal s'exécute
- **THEN** l'impact est `résolu` avec le motif `AVIS_MODIFIE` ou `AVIS_RETIRE`
- **AND** il n'est pas supprimé

#### Scenario: COR-11-S4 Impact écarté
- **GIVEN** un impact `écarté` dont le composant est ensuite retiré de l'inventaire
- **WHEN** la recorrélation du projet s'exécute
- **THEN** l'impact reste `écarté`

#### Scenario: COR-11-S5 Impact résolu de nouveau vérifié
- **GIVEN** un impact `résolu` avec le motif `AVIS_MODIFIE`
- **WHEN** la source republie l'avis avec une plage qui couvre de nouveau le composant et que la corrélation s'exécute
- **THEN** l'impact repasse `ouvert`, avec l'auteur « système » et le motif `DE_NOUVEAU_VERIFIE` dans son historique
- **AND** un seul impact existe pour ce signal, ce projet et ce composant (COR-07)

### Requirement: COR-12 Historique et filtre par état
Le système DOIT (MUST) restituer avec un impact son état courant et l'historique complet de ses transitions, et permettre de filtrer les impacts consultables par état, dans le périmètre autorisé de l'utilisateur (COR-08).
Source : décision J5 du porteur du projet (2026-11-19) ; arc42 §10.2 Q-01. Statut : [PROPOSITION].

#### Scenario: COR-12-S1 Historique consulté
- **GIVEN** un impact créé `ouvert`, confirmé par un contributeur puis résolu par le système
- **WHEN** un membre `LECTEUR` de l'espace consulte l'impact
- **THEN** l'état `résolu` et les deux transitions sont affichés dans l'ordre, avec auteur, date, états et motif ou commentaire

#### Scenario: COR-12-S2 Liste filtrée par état
- **GIVEN** trois impacts d'un projet, `ouvert`, `écarté` et `résolu`
- **WHEN** un utilisateur autorisé demande les impacts du projet à l'état `ouvert`
- **THEN** seul l'impact `ouvert` est restitué
- **AND** sans filtre, la liste par défaut restitue les impacts `ouvert` et `confirmé`
