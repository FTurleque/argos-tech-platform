# correlation-impact Specification

## Purpose
Relier de façon déterministe, explicable et traçable un signal de sécurité aux seuls projets dont l'inventaire contient un composant affecté, sans dépendre d'un fournisseur d'IA. Fiche : [/docs/specifications/correlation-impact.md](/docs/specifications/correlation-impact.md).

## Requirements

### Requirement: COR-01 Rapprochement par PURL et plage de versions
Le système DOIT (MUST) créer un impact projet lorsqu'un composant de l'inventaire d'un projet a le même PURL qu'un paquet affecté par un signal de sécurité et une version comprise dans la plage affectée, et NE DOIT PAS (MUST NOT) en créer pour les autres projets. Toute comparaison de versions Maven suit `ComparableVersion` (`maven-artifact`).
Source : ADR-0013 ; ADR-0011 (Proposés) ; baseline v2.3 §4 ; décision D5 du 2026-11-18 (Q-COR-3) ; décision E3 du 2026-11-19. Statut : [PROPOSITION].

#### Scenario: COR-01-S1 Version dans la plage affectée
- **GIVEN** un projet dont l'inventaire contient `pkg:maven/org.exemple/lib@1.2.0`
- **AND** un signal qui affecte `pkg:maven/org.exemple/lib` pour les versions inférieures à 1.3.0
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** un impact relie le signal à ce projet et au composant concerné

#### Scenario: COR-01-S2 Version hors plage
- **GIVEN** un projet dont l'inventaire contient `pkg:maven/org.exemple/lib@1.3.0`
- **AND** un signal qui affecte ce paquet pour les versions inférieures à 1.3.0
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** aucun impact n'est créé pour ce projet

#### Scenario: COR-01-S3 Dépendance transitive affectée
- **GIVEN** un projet dont le composant affecté n'est qu'une dépendance transitive
- **WHEN** la corrélation du signal s'exécute
- **THEN** un impact est créé pour ce projet
- **AND** l'impact indique que le composant est une dépendance transitive

#### Scenario: COR-01-S4 Projet sans le paquet
- **GIVEN** un projet dont l'inventaire ne contient aucun composant du paquet affecté
- **WHEN** la corrélation du signal s'exécute
- **THEN** aucun impact n'est créé pour ce projet

#### Scenario: COR-01-S5 Qualificatifs inconnus d'une version Maven
- **GIVEN** un projet dont l'inventaire contient `pkg:maven/com.google.guava/guava@32.0.0-jre`
- **AND** un signal qui affecte ce paquet pour les versions inférieures à `32.0.0-android`
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** aucun impact n'est créé pour ce composant, `32.0.0-jre` suivant `32.0.0-android` dans l'ordre des versions Maven
- **AND** un composant `31.1-jre` du même paquet reçoit un impact

### Requirement: COR-02 Niveau de chaque impact
Le système DOIT (MUST) attribuer à chaque impact exactement un niveau, affiché avec l'impact : `exact` (PURL déclaré et version dans la plage, SBOM non obsolète), `probable` (même correspondance, inventaire de repli ou SBOM obsolète) ou `à vérifier` (correspondance sans PURL, version inconnue ou plage non interprétable).
Source : ADR-0013 (Proposé) ; décision du porteur du projet du 2026-11-16 (question Q-2). Statut : [PROPOSITION].

#### Scenario: COR-02-S1 Correspondance PURL et version établie
- **GIVEN** un impact créé selon COR-01 à partir d'une SBOM CycloneDX non obsolète
- **WHEN** un utilisateur autorisé consulte l'impact
- **THEN** l'impact porte le niveau `exact`

#### Scenario: COR-02-S2 Correspondance sur un inventaire de repli ou une SBOM obsolète
- **GIVEN** un projet dont l'inventaire provient des manifestes ou d'une SBOM générée il y a plus de 30 jours
- **AND** un signal dont le PURL et la plage correspondent à un composant de cet inventaire
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact porte le niveau `probable`

#### Scenario: COR-02-S3 Correspondance sans PURL ou version inconnue
- **GIVEN** un signal dont le paquet affecté n'est identifié que par son nom, ou un composant dont la version est inconnue
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact porte le niveau `à vérifier`

### Requirement: COR-03 Provenance de chaque impact
Le système DOIT (MUST) associer à chaque impact le signal source, le projet, le composant rapproché, l'inventaire utilisé avec sa date, la méthode d'analyse et sa provenance ; aucun impact NE DOIT (MUST NOT) exister sans cette provenance.
Source : arc42 §10.2 Q-01 ; QS-01 ; arc42 §1.4. Statut : [PROPOSITION].

#### Scenario: COR-03-S1 Consultation d'un impact
- **GIVEN** un impact créé par la corrélation
- **WHEN** un utilisateur autorisé ouvre cet impact
- **THEN** le signal source et sa provenance, le projet, le composant, l'inventaire utilisé et la méthode d'analyse sont affichés

#### Scenario: COR-03-S2 Contrôle d'intégrité
- **GIVEN** l'ensemble des impacts enregistrés
- **WHEN** le contrôle d'intégrité des impacts s'exécute
- **THEN** il ne trouve aucun impact sans signal source, sans projet ou sans méthode d'analyse

### Requirement: COR-04 Aucune alerte critique sur la seule suggestion de l'IA
Le système NE DOIT PAS (MUST NOT) déclencher une alerte critique à partir d'une suggestion produite par un modèle d'IA en l'absence de rapprochement déterministe.
Source : ADR-0013 (Proposé) ; arc42 §4.2 (P4). Statut : [PROPOSITION].

#### Scenario: COR-04-S1 Suggestion IA sans correspondance déterministe
- **GIVEN** une analyse IA qui suggère qu'un projet est concerné par un signal
- **AND** aucun composant de l'inventaire de ce projet ne satisfait COR-01
- **WHEN** les règles d'alerte sont évaluées pour ce projet
- **THEN** aucune alerte critique n'est émise pour ce projet

### Requirement: COR-05 Corrélation sans fournisseur d'IA
Le système DOIT (MUST) calculer et enregistrer les impacts déterministes lorsque le fournisseur d'IA est indisponible ou que le plafond budgétaire d'IA est atteint.
Source : baseline v2.3 §6 critère 6 ; QS-10 ; ADR-0013 (Proposé). Statut : [PROPOSITION].

#### Scenario: COR-05-S1 Fournisseur d'IA indisponible
- **GIVEN** un signal qui satisfait COR-01 pour un projet et le fournisseur d'IA indisponible
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact est créé et consultable avec sa provenance

#### Scenario: COR-05-S2 Plafond budgétaire d'IA atteint
- **GIVEN** un signal qui satisfait COR-01 pour un projet et le plafond budgétaire d'IA atteint
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact est créé et consultable avec sa provenance
- **AND** aucun appel au fournisseur d'IA n'est effectué

### Requirement: COR-06 Confiance réduite sans SBOM à jour
Le système DOIT (MUST) attribuer un niveau autre que `exact` à un impact calculé à partir d'un inventaire de repli ou d'une SBOM obsolète. Une SBOM est obsolète 30 jours après sa date de génération.
Source : ADR-0011 (Proposé, conséquences neutres) ; décision du 2026-11-16 (question Q-3). Statut : [PROPOSITION].

#### Scenario: COR-06-S1 Inventaire issu des manifestes
- **GIVEN** un projet dont l'inventaire provient du repli sur les manifestes
- **AND** un signal dont le paquet et la plage correspondent à une dépendance déclarée
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact est créé avec un niveau autre que `exact`

### Requirement: COR-07 Idempotence de la corrélation
Le système DOIT (MUST) garantir qu'une nouvelle exécution de la corrélation pour un même signal, un même projet et un même composant ne crée pas de second impact, et NE DOIT PAS (MUST NOT) modifier un impact existant, ni sa date de mise à jour, lorsque aucune de ses valeurs ne change.
Source : arc42 §6.1 (invariants) ; QS-04 ; AUD-PER-25. Statut : [PROPOSITION].

#### Scenario: COR-07-S1 Corrélation rejouée
- **GIVEN** un impact existant pour un signal, un projet et un composant
- **WHEN** la corrélation de ce signal est exécutée de nouveau sans changement d'inventaire
- **THEN** un seul impact existe pour ce triplet

#### Scenario: COR-07-S2 Recorrélation sans changement
- **GIVEN** un impact ouvert dont le niveau, la version de l'avis, la provenance et le composant n'ont pas changé depuis sa dernière corrélation
- **WHEN** l'import quotidien recorrèle son projet
- **THEN** l'impact garde sa date de mise à jour
- **AND** aucune valeur de l'impact n'est modifiée

#### Scenario: COR-07-S3 Recorrélation avec changement
- **GIVEN** un impact ouvert dont l'avis a reçu une nouvelle version
- **WHEN** la corrélation du signal s'exécute
- **THEN** l'impact porte la nouvelle version de l'avis
- **AND** sa date de mise à jour est celle de cette corrélation

### Requirement: COR-08 Visibilité des impacts limitée au périmètre autorisé
Le système DOIT (MUST) ne restituer à un utilisateur que les impacts des projets de son périmètre autorisé, et journaliser toute tentative d'accès à un impact hors de ce périmètre.
Source : ADR-0016 (Proposé) ; arc42 §6.1 ; arc42 §8.1. Statut : [PROPOSITION].

#### Scenario: COR-08-S1 Liste des impacts filtrée
- **GIVEN** un signal qui impacte un projet de l'espace A et un projet de l'espace B
- **AND** un utilisateur autorisé uniquement sur l'espace A
- **WHEN** cet utilisateur consulte les impacts du signal
- **THEN** seul l'impact du projet de l'espace A est restitué

#### Scenario: COR-08-S2 Accès direct à un impact d'un autre espace
- **GIVEN** un utilisateur autorisé uniquement sur l'espace A
- **WHEN** il demande directement un impact d'un projet de l'espace B
- **THEN** la demande est refusée sans révéler le contenu de l'impact
- **AND** le refus est journalisé avec l'utilisateur, la ressource demandée et la date

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

### Requirement: COR-13 Corrélation en un nombre fixe d'instructions par lot
Le système DOIT (MUST) corréler un projet par lots de signaux, chaque lot s'exécutant en un nombre d'instructions SQL indépendant du nombre de signaux du lot et du nombre de composants correspondants, sans changer les impacts obtenus (COR-01 à COR-11).
Source : AUD-PER-03 ; critère de sortie du sprint 10 (`/docs/audit/2026-11-21/sprints.md`). Statut : [PROPOSITION].

#### Scenario: COR-13-S1 Nombre d'instructions constant
- **GIVEN** deux projets dont l'un a dix fois plus de signaux et de composants correspondants que l'autre, chacun tenant en un lot
- **WHEN** chaque projet est recorrélé
- **THEN** les deux recorrélations exécutent le même nombre d'instructions SQL

#### Scenario: COR-13-S2 Résultat inchangé
- **GIVEN** un projet dont des impacts sont à créer, à mettre à jour, à rouvrir et à résoudre
- **WHEN** le projet est recorrélé par lots
- **THEN** les impacts créés, mis à jour, rouverts et résolus, leurs états, motifs et historiques sont ceux qu'exigent COR-07 et COR-11
