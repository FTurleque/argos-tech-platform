---
id: ADR-0032
title: "Versionner le schéma PostgreSQL par des migrations SQL Flyway appliquées au démarrage"
domain: architecture
rules: false
---

# ADR-0032 — Versionner le schéma PostgreSQL par des migrations SQL Flyway appliquées au démarrage

- **Statut** : Accepté
- **Date** : 2026-11-18 (acceptation ; proposition du 2026-11-17)
- **Décideurs** : porteur du projet (acceptation du 2026-11-18)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- arc42 [§9.3](../arc42/09-decisions.md#93-décisions-encore-à-formaliser-après-la-baseline-v24) range « Flyway ou Liquibase » parmi les décisions à formaliser ; la décision D4 du changement [`formaliser-chaine-securite-poc`](/openspec/changes/archive/2026-11-20-formaliser-chaine-securite-poc/design.md) le signale comme candidat à ADR.
- Le serveur emploie Flyway depuis sa première version, qualifié de « choix provisoire » dans [`argos-server/pom.xml`](/argos-server/pom.xml) et [`application.properties`](/argos-server/src/main/resources/application.properties) :
  - extensions `quarkus-flyway` et `flyway-database-postgresql` (Flyway Community, licence Apache-2.0) ;
  - `quarkus.flyway.migrate-at-start=true`, `quarkus.flyway.locations=db/migration` : le serveur applique les migrations en attente à son démarrage, avant de servir des requêtes ;
  - seize migrations SQL versionnées, `V1__socle.sql` à `V16__veille_reglementaire.sql`, une par changement OpenSpec ou presque ; aucune migration répétable (`R__`) ni d'annulation (`U__`) ; aucune migration Java.
- Les migrations portent plus que des tables : création du rôle `argos_app` (`NOLOGIN NOSUPERUSER NOBYPASSRLS`, droit `CREATEROLE` requis à la migration, V2), privilèges par colonne, fonctions `SECURITY DEFINER` et politiques RLS ([ADR-0016](0016-workspace-rbac-rls.md)), recherche plein texte (V11, [ADR-0015](0015-postgresql-fts-pgvector.md)). Le modèle physique produit est D-13 ([`d-13-modele-physique.md`](../diagrams/d-13-modele-physique.md)).
- Les tests d'intégration appliquent toutes les migrations sur un PostgreSQL 18 jetable (Dev Services) à chaque `mvn verify` ; le guide de déploiement demande une sauvegarde avant chaque mise à jour ([`synology-et-pc.md`](/docs/deploiement/synology-et-pc.md)).
- Plusieurs changements développés en parallèle reçoivent chacun un numéro de migration distinct, attribué avant le développement.

## Critères de décision

1. Composant gratuit sous licence permissive ([ADR-0021](0021-double-licence.md)).
2. Fonctions PostgreSQL employées sans couche d'abstraction : RLS, `SECURITY DEFINER`, `tsvector`, index partiels ([ADR-0002](0002-postgresql-stockage-principal.md), Accepté).
3. Schéma identique sur le PC, le NAS et en test, sans étape manuelle ([ADR-0026](0026-hebergement-synology.md)).
4. Historique des migrations relisible en revue de code.
5. Intégration Quarkus maintenue.

## Options considérées

### Option A — Flyway, migrations SQL versionnées, appliquées au démarrage

- avantages : en place ; SQL PostgreSQL natif relu tel quel en revue ; extension Quarkus officielle ; ordre total par numéro de version ; somme de contrôle qui détecte une migration modifiée après application ;
- inconvénients : pas d'annulation dans l'édition communautaire ; numéros à coordonner entre branches parallèles ;
- risques : une migration en échec empêche le démarrage du serveur ; une migration longue retarde la disponibilité ;
- coûts/complexité : faibles.

### Option B — Liquibase

- avantages : journaux de modifications en XML, YAML ou SQL ; annulation déclarable ; extension Quarkus disponible ;
- inconvénients : réécriture ou reprise des seize migrations et de l'historique ; abstraction peu utile pour un schéma propre à PostgreSQL ; licence de l'édition communautaire actuelle à vérifier au regard du critère 1 ;
- risques : migration d'outil sur une base déjà déployée ;
- coûts/complexité : moyens.

### Option C — Migrations appliquées par une étape d'exploitation distincte (Flyway en ligne de commande ou conteneur dédié)

- avantages : le serveur démarre sans droit `CREATEROLE` ; migration lancée et surveillée explicitement ;
- inconvénients : étape manuelle ou conteneur de plus dans `compose.yaml` ; risque d'écart entre le code déployé et le schéma ;
- risques : oubli de l'étape lors d'une mise à jour sur le NAS ;
- coûts/complexité : moyens.

## Décision

**Option A retenue.** Le schéma est versionné par des migrations SQL Flyway, dans `argos-server/src/main/resources/db/migration/`, appliquées au démarrage du serveur :

- un fichier `V<n>__<sujet>.sql` par évolution, jamais modifié après sa fusion ; une correction passe par une nouvelle migration ;
- un numéro attribué à chaque changement avant son développement ;
- types PostgreSQL natifs, `varchar` plutôt que `char(n)` ;
- toute table cloisonnée crée dans la même migration sa politique RLS et ses privilèges pour `argos_app`.

Le passage à un hébergement où l'utilisateur de connexion n'a pas le droit `CREATEROLE`, ou à plusieurs instances démarrant en même temps, rouvre la décision (option C).

## Conséquences positives

- aucun outil ni étape à ajouter au déploiement sur le NAS ;
- chaque `mvn verify` prouve que la chaîne complète V1 à V16 s'applique sur une base vide ;
- la sécurité du schéma (RLS, privilèges) est relue avec la table qu'elle protège.

## Conséquences négatives

- aucun retour arrière automatique : la sauvegarde préalable à une mise à jour est la seule annulation ;
- l'utilisateur de connexion du serveur détient des droits de définition du schéma.

## Conséquences neutres / compromis

- les migrations historiques contiennent des types antérieurs à la règle `varchar` (`char(64)` dans V3) ; elles restent inchangées.

## Méthode de validation

- tests : `mvn -B -f argos-server/pom.xml verify` applique V1 à V16 sur PostgreSQL 18 jetable ; tests RLS sous `argos_app` ;
- revue : une seule migration par numéro, aucune migration fusionnée modifiée (somme de contrôle Flyway) ;
- exploitation : restauration d'une sauvegarde suivie d'un démarrage, à tester (arc42 §7.7).

## Traçabilité

- **Exigences** : COL-03 et COR-07 (contraintes d'unicité en base), ACC-04 (RLS sous `argos_app`) ;
- **Scénarios qualité** : QS-07 ;
- **Diagrammes** : D-13 ([`d-13-modele-physique.md`](../diagrams/d-13-modele-physique.md)), D-11 ;
- **Tickets/PR** : Non déterminé ;
- **Fichiers source** : `argos-server/pom.xml`, `argos-server/src/main/resources/application.properties`, `argos-server/src/main/resources/db/migration/`.

## Notes

Toute décision remplacée doit rester versionnée et pointer vers son successeur.
