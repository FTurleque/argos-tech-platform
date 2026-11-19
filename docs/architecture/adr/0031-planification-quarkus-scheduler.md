# ADR-0031 — Planifier les traitements périodiques avec quarkus-scheduler dans le serveur

- **Statut** : Accepté
- **Date** : 2026-11-18 (acceptation ; proposition du 2026-11-17)
- **Décideurs** : porteur du projet (acceptation du 2026-11-18)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- arc42 [§9.3](../arc42/09-decisions.md#93-décisions-encore-à-formaliser-après-la-baseline-v24) range le « mécanisme précis de scheduling/jobs » parmi les décisions à formaliser ; la décision D4 du changement [`formaliser-chaine-securite-poc`](/openspec/changes/formaliser-chaine-securite-poc/design.md) le signale comme candidat à ADR.
- Le serveur emploie déjà l'extension `quarkus-scheduler` (licence Apache-2.0, [`argos-server/pom.xml`](/argos-server/pom.xml)), introduite par le changement `diffuser-alertes-et-digests` puis réutilisée par les lots suivants. Onze méthodes `@Scheduled` existent :

| Identité | Déclenchement (réglage, valeur par défaut) | Classe |
|---|---|---|
| `argos-envoi-alertes` | `every` `argos.alerting.envoi.intervalle`, 1 min | `io.argos.alerting.EnvoiAlertes` |
| `argos-resume-quotidien` | `cron` `argos.alerting.resume-quotidien.cron`, 08:00 | `io.argos.alerting.ResumesPeriodiques` |
| `argos-resume-hebdomadaire` | `cron` `argos.alerting.resume-hebdomadaire.cron`, lundi 08:00 | `io.argos.alerting.ResumesPeriodiques` |
| `argos-radar-quotidien` | `cron` `argos.radar.tache.cron`, 06:00 | `io.argos.alerting.RappelsRadar` |
| `rappels-profil-artiste` | `cron` `argos.profil-artiste.rappels.cron`, 07:00 | `io.argos.alerting.RappelsOpportunites` |
| `rappels-reglementaires` | `cron` `argos.reglementaire.rappels.cron`, 07:15 | `io.argos.alerting.RappelsReglementaires` |
| `argos-activite-collecte` | `every` `argos.activite.cadence`, 1 h | `io.argos.projectintel.CollecteActivite` |
| `argos-activite-purge` | `cron` `argos.activite.purge.cron`, 04:30 | `io.argos.projectintel.PurgeActivite` |
| `collecte-reglementaire` | `cron` `argos.reglementaire.collecte.cron`, 06:30 | `io.argos.regintel.CollecteReglementaire` |
| `collecte-profil-artiste` | `cron` `argos.profil-artiste.collecte.cron`, 05:00 | `io.argos.signal.opportunite.CollecteOpportunites` |
| `collecte-veille-web` | `every` `argos.veille-web.cadence`, 5 min | `io.argos.signal.web.CollecteWeb` |

- Toutes déclarent `concurrentExecution = SKIP` ; les tâches `cron` s'exécutent au fuseau `argos.alerting.fuseau` (Europe/Paris par défaut). Chaque méthode planifiée délègue à une méthode publique qui reçoit l'instant courant : les tests l'appellent avec une horloge simulée, le planificateur est désactivé dans le profil de test (`%test.quarkus.scheduler.enabled=false`).
- La reprise et l'exclusion mutuelle reposent sur PostgreSQL, pas sur le planificateur : réservation `FOR UPDATE SKIP LOCKED` des alertes à envoyer, verrous consultatifs (`pg_try_advisory_lock` pour la collecte d'une source web, `pg_advisory_xact_lock` pour la corrélation), index unique `sync_run_one_running` et battement de cœur `heartbeat_at` pour la collecte des avis, échéances persistées (`prochaine_collecte_le`, `prochaine_collecte`, `prochain_essai`).
- Deux traitements de la chaîne sécurité ne sont pas planifiés : la synchronisation des sources d'avis se déclenche par `POST /api/v1/ops/sources/{id}/synchronizations` ; le rafraîchissement de l'inventaire d'un projet depuis sa forge (`InventoryService.refresh`) n'a aucun déclencheur en production.
- La cible est une instance unique du conteneur `argos` sur le Synology ([ADR-0026](0026-hebergement-synology.md), Accepté ; D-11). [ADR-0007](0007-n8n-peripherique.md) (Proposé) limite n8n aux automatisations périphériques.

## Critères de décision

1. Aucun service ni composant supplémentaire à déployer sur le NAS (8 Go partagés, ADR-0026).
2. Composant gratuit sous licence permissive ([ADR-0021](0021-double-licence.md)).
3. Traitements testables sans attendre l'horloge réelle.
4. Aucun traitement perdu ni dupliqué après un arrêt brutal (QS-07, COL-05).
5. Cadences et horaires réglables par l'exploitant sans recompiler.

## Options considérées

### Option A — `quarkus-scheduler` dans le serveur, état et exclusion portés par PostgreSQL

- avantages : déjà en place ; aucune dépendance ni conteneur ajouté ; cadences en propriétés de configuration ; la base porte les échéances et les verrous, donc un redémarrage reprend là où le traitement s'était arrêté ;
- inconvénients : planification en mémoire, propre à chaque instance ; une exécution manquée pendant un arrêt n'est pas rejouée par le planificateur ;
- risques : deux instances du serveur exécuteraient chacune les tâches sans verrou en base ;
- coûts/complexité : faibles.

### Option B — `quarkus-quartz` avec stockage JDBC en grappe

- avantages : exécutions manquées rattrapées, une seule exécution par tâche dans une grappe ;
- inconvénients : tables Quartz à migrer et à sauvegarder ; configuration plus lourde ; aucune grappe prévue ;
- risques : complexité sans besoin mesuré ;
- coûts/complexité : moyens (Apache-2.0).

### Option C — Ordonnanceur externe (cron de DSM, n8n) appelant des points d'entrée REST

- avantages : planification visible hors de l'application ;
- inconvénients : points d'entrée à authentifier et à protéger ; dépendance à l'hôte ou à n8n, contraire à ADR-0007 pour un traitement cœur ;
- risques : tâches arrêtées sans que le serveur le sache ;
- coûts/complexité : moyens.

## Décision

Proposition : **option A**. Les traitements périodiques s'exécutent dans le serveur par `quarkus-scheduler` ; chaque tâche :

- porte une identité stable et une cadence ou une expression `cron` lue dans la configuration ;
- déclare `concurrentExecution = SKIP` ;
- délègue à une méthode testable qui reçoit l'instant courant ;
- persiste son échéance et son état dans PostgreSQL et s'exclut des exécutions concurrentes par un verrou ou une réservation en base dès qu'une action à la demande ou une seconde instance peut la concurrencer.

Le passage à plusieurs instances du serveur, ou le besoin de rattraper une exécution manquée, rouvre la décision (option B).

## Conséquences positives

- aucun composant de plus à déployer, sauvegarder ou superviser ;
- comportement identique sur le PC et sur le NAS ;
- tests déterministes, sans attente réelle.

## Conséquences négatives

- un arrêt du serveur pendant l'horaire d'une tâche `cron` la reporte à l'horaire suivant, sauf si la tâche relit elle-même ses échéances en retard ;
- chaque nouvelle tâche doit prévoir son propre verrou en base.

## Conséquences neutres / compromis

- la synchronisation des avis reste déclenchée par l'API ; le rafraîchissement des inventaires n'a pas de déclencheur : leur planification éventuelle suit les règles ci-dessus.

## Méthode de validation

- revue : chaque `@Scheduled` déclare une identité, une cadence configurable et `concurrentExecution = SKIP` (11 sur 11 au 2026-11-17) ;
- tests : traitements appelés avec une horloge simulée (`io.argos.alerting`, `io.argos.projectintel`, `io.argos.regintel`, `io.argos.signal`) ; reprise après arrêt brutal (`chain/ResilienceTest#col05S2ArretBrutalDuBackendPuisReprise`) ;
- critère d'acceptation : aucun envoi ni collecte en double après redémarrage, vérifié par les contraintes d'unicité en base.

## Traçabilité

- **Exigences** : COL-05 (reprise depuis le curseur), ALR-06 (diffusion et nouvelles tentatives) ; collecte de la veille web, tâche quotidienne du radar, collecte et rappels réglementaires, collecte de l'activité des projets (fiches [`veille-rss-web`](../../specifications/veille-rss-web.md), [`veille-reglementaire`](../../specifications/veille-reglementaire.md)) ;
- **Scénarios qualité** : QS-07 ;
- **Diagrammes** : D-11 ([`d-11-deploiement-cible.md`](../diagrams/d-11-deploiement-cible.md)), D-16, D-18, D-19 ;
- **Tickets/PR** : Non déterminé ;
- **Fichiers source** : `argos-server/pom.xml`, `argos-server/src/main/resources/application.properties`, classes listées ci-dessus.

## Notes

Toute décision remplacée doit rester versionnée et pointer vers son successeur.
