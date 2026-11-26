# ADR-0041 — Conserver les données journalisées pour une durée bornée par des règles déclarées par module

- **Statut** : Proposé
- **Date** : 2026-11-26
- **Décideurs** : porteur du projet (à valider)
- **Remplace** : —
- **Remplacé par** : —
- **Précise** : [ADR-0031](0031-planification-quarkus-scheduler.md) (traitements planifiés), [ADR-0037](0037-noyau-commun-du-serveur.md) (contenu du noyau commun)

## Contexte

- Les journaux (refus d'accès, appels MCP, appels IA), les échecs (avis mis à l'écart, imports refusés), les traces d'exploitation (synchronisations, tentatives, événements) et une partie du contenu collecté (versions d'avis, actualités, opportunités) grossissent sans limite. Seul l'historique d'activité des projets est purgé (`PurgeActivite`). Constats AUD-SEC-03 et AUD-PER-18 de l'audit du 2026-11-21.
- Les journaux conservent des sujets OIDC sans limite de durée, contraire à la minimisation des données.
- Chaque table a un module propriétaire (arc42 §5.2.1). Le noyau commun ne nomme aucune table (ADR-0037).
- Le NAS a un stockage et une mémoire limités (ADR-0026).

## Critères de décision

1. Chaque table de journal, d'échec ou d'historique a une durée de conservation réglable.
2. Le propriétaire d'une table garde la seule connaissance de son SQL.
3. Une table ajoutée plus tard ne peut pas échapper à la décision sans qu'un test échoue.
4. Aucune dépendance ni aucun service de plus (ADR-0021).
5. Aucune erreur imprévue absorbée (ADR-0039, critère 3).

## Options considérées

### Option A — Module technique `conservation` : un contrat implémenté par chaque module, une tâche planifiée unique

- avantages : un horaire, une métrique, un journal ; propriété des tables respectée ; complétude vérifiable ;
- inconvénients : un module de plus dans la liste fermée ;
- coûts : faibles.

### Option B — Une tâche planifiée par module

- avantages : aucun module nouveau ;
- inconvénients : horaires et réglages multipliés, complétude dispersée.

### Option C — Partitionnement par date

- avantages : suppression instantanée d'une partition ;
- inconvénients : extension ou maintenance manuelle des partitions, migrations lourdes, sans besoin mesuré.

## Décision

Proposition : **option A**.

- Le module technique `io.argos.conservation` ne dépend d'aucun module métier. Il porte le contrat `RegleConservation` (nom stable, durée réglable, suppression des lignes antérieures à une limite), la tâche planifiée `argos-purge-conservation` (ADR-0031 : `cron` réglable, `concurrentExecution = SKIP`, méthode testable qui reçoit l'instant) et la suppression par lots.
- Chaque module déclare, par un bean CDI, une règle par table ou groupe de tables liées dont il est propriétaire. Le SQL de chaque règle épargne les lignes encore utiles : traitement en cours, version courante, ligne citée.
- La durée de chaque règle se règle par `argos.conservation.<règle>` (durée ISO-8601). La suppression procède par lots autonomes, hors transaction JTA.
- Une erreur d'accès à la base pendant une règle est journalisée avec sa cause et comptée (`argos_purges_echecs_total`), puis la règle suivante s'exécute. Toute autre erreur remonte au planificateur.
- Un test d'architecture tient la liste fermée des tables de journal, d'échec ou d'historique, avec la règle qui purge chacune ou la raison de son exclusion. Une table nouvelle du schéma absente de cette liste le fait échouer.

## Conséquences positives

- Le volume des journaux et des échecs est borné par la durée réglée.
- Les sujets OIDC des journaux disparaissent à l'échéance.

## Conséquences négatives

- Les données purgées sont perdues : une enquête plus ancienne que la durée réglée n'est plus possible.
- Une garde de règle (« ligne citée ») peut demander un index sur la table qui cite.

## Conséquences neutres / compromis

- Les historiques métier attachés à une entité vivante (transitions d'un impact, historiques des standards et des fiches) disparaissent avec leur entité. Ils ne sont pas purgés par durée.
- `PurgeActivite` reste une tâche distincte : durée en mois, deux tables liées.

## Méthode de validation

- `ConservationCompleteTest` (liste fermée des tables) ; `PurgesConservationTest` (isolement d'une règle en échec, métrique) ;
- tests d'intégration de chaque règle : lignes échues supprimées, lignes récentes et lignes encore utiles conservées ;
- `ModuleBoundariesTest` : `conservation` ne dépend d'aucun module métier.

## Traçabilité

- **Exigences** : EXP-17, ACC-15
- **Scénarios qualité** : arc42 §10
- **Diagrammes** : `openspec/changes/retenir-importer-et-paginer/design.md` (DC-1, DC-2)
- **Fichiers source** : `argos-server/src/main/java/io/argos/conservation/`
