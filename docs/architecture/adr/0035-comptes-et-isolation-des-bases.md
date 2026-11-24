# ADR-0035 — Comptes et isolation des bases de la plateforme

- **Statut** : Proposé
- **Date** : 2026-11-24
- **Décideurs** : porteur du projet (décision Q-1 du changement `fiabiliser-sauvegarde-et-roles-base`, 2026-11-24) ; acceptation de l'ADR à venir
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- Argos et Keycloak (profil `idp-local`, [ADR-0027](0027-keycloak-idp-auto-heberge.md)) partagent une instance PostgreSQL 18 sur le Synology du porteur ([ADR-0026](0026-hebergement-synology.md)), avec une base chacun : `argos` et `keycloak`.
- Constat AUD-SEC-30 de l'audit du 2026-11-21 (révision `b142695`) : avec un compte commun, `POSTGRES_USER` de l'image, superutilisateur de l'instance, la compromission de l'une des applications donne la base de l'autre (clés de signature du royaume comprises) et l'accès aux fichiers du serveur ; la RLS d'Argos ([ADR-0016](0016-workspace-rbac-rls.md)) ne tient alors qu'au `SET LOCAL ROLE argos_app` que pose chaque transaction d'un utilisateur ([`ContexteBase`](/argos-server/src/main/java/io/argos/workspace/ContexteBase.java)), qu'un superutilisateur annule en revenant à son rôle initial.
- Argos applique ses migrations au démarrage ([ADR-0032](0032-migrations-flyway.md)). Ses traitements de plateforme (collectes, imports, corrélation, diffusion des alertes, journaux) écrivent sans contexte utilisateur dans des tables sous RLS : le propriétaire d'une table échappe à ses politiques, un autre rôle n'y accède que par une politique qui le nomme.
- Le NAS (DS1522+, 8 Go) compte déjà la mémoire de chaque conteneur (AUD-PER-28).

## Critères de décision

1. Aucun compte utilisé par une application n'est superutilisateur ni ne contourne la RLS (`rolsuper`, `rolbypassrls`).
2. Une application ne peut ouvrir de session que sur sa propre base.
3. Le compte de connexion d'Argos ne peut ni modifier le schéma ni lire un espace étranger en quittant `argos_app`.
4. Pas de conteneur ni de volume supplémentaire sur le NAS ; une seule procédure de sauvegarde et de restauration.
5. Les tests d'intégration s'exécutent avec les mêmes comptes que la production.

## Options considérées

### Option A — Instance PostgreSQL dédiée à Keycloak

- avantages : isolation la plus forte entre les deux applications ;
- inconvénients : un conteneur, un volume et une sauvegarde de plus ; mémoire du NAS ;
- risques : deux instances à mettre à jour et à restaurer ;
- coûts/complexité : élevés pour un usage personnel.

### Option B — Rôles dédiés dans l'instance partagée

- avantages : aucune ressource supplémentaire ; isolation par les droits de connexion et de propriété ;
- inconvénients : l'instance reste un point de défaillance commun ; Argos emploie deux jeux d'identifiants (migrations, connexion) ;
- risques : un oubli de droit sur une nouvelle table bloque un traitement ; couvert par les tests ;
- coûts/complexité : faibles.

## Décision

Option B [DÉCISION — porteur du projet, 2026-11-24] :

1. **Superutilisateur d'administration** (`ARGOS_DB_ADMIN_USER`, défaut `argos_admin`) : `POSTGRES_USER` de l'image, réservé à l'initialisation, à la sauvegarde et à la restauration, depuis l'intérieur du conteneur.
2. **Keycloak** : rôle `keycloak` (`LOGIN NOSUPERUSER NOBYPASSRLS`), propriétaire de la seule base `keycloak`.
3. **Propriétaire de la base Argos** (`ARGOS_DB_OWNER`, défaut `argos`, `NOSUPERUSER`) : possède le schéma et applique les migrations ; Flyway s'y connecte avec ses propres identifiants (`quarkus.flyway.username` et `password`).
4. **Rôle de connexion d'Argos** (`ARGOS_DB_USER`, défaut `argos_connexion`, `NOSUPERUSER NOBYPASSRLS`, propriétaire d'aucun objet), membre :
   - d'`argos_app` sans en hériter les droits (`INHERIT FALSE, SET TRUE`) : il y bascule dans chaque transaction d'un utilisateur ;
   - d'`argos_technique`, dont il hérite les droits : lecture et écriture de toutes les tables sauf l'historique des migrations, aucun droit sur le schéma.
5. **RLS des traitements de plateforme** : chaque table sous RLS porte une politique `<table>_technique` pour `argos_technique`, ouverte seulement hors de tout contexte utilisateur (`argos_hors_contexte_utilisateur()` : compte, sujet, e-mail et espace courants vides). Une session d'utilisateur qui quitte `argos_app` par `RESET ROLE` garde son contexte et ne lit plus aucune ligne cloisonnée. Toute nouvelle table sous RLS reçoit cette politique dans sa migration.
6. **Bases fermées** : `CONNECT` retiré à `PUBLIC` sur les deux bases ; seul le rôle de connexion d'Argos est autorisé en plus du propriétaire sur la base `argos`.
7. Les rôles et les bases sont créés au premier démarrage du volume par [`deploiement/postgres/initialiser-bases.sh`](/deploiement/postgres/initialiser-bases.sh) ; les droits d'`argos_technique` et ses politiques, par la migration `V22__roles_connexion_et_technique.sql`. Une installation existante passe aux nouveaux comptes par sauvegarde et restauration sur un volume neuf ([guide de déploiement](/docs/deploiement/synology-et-pc.md#passer-aux-comptes-de-base-distincts)) : le compte initial y est le superutilisateur créé avec le volume, que PostgreSQL ne permet pas de rétrograder.

## Conséquences positives

- Une injection SQL dans Argos ne donne ni la base de Keycloak, ni l'accès aux fichiers du serveur, ni la modification du schéma ; dans Keycloak, elle ne donne pas la base d'Argos.
- La RLS d'une session d'utilisateur ne dépend plus du seul `SET LOCAL ROLE`.
- Les tests Maven appliquent les migrations sous le propriétaire et exécutent l'application sous le rôle de connexion, avec le script d'initialisation de production (Dev Services).

## Conséquences négatives

- Quatre mots de passe de base à gérer dans `.env`.
- Une installation antérieure demande une migration manuelle de son volume (sauvegarde, volume neuf, restauration).
- La politique hors contexte utilisateur repose sur des paramètres de transaction qu'une instruction injectée peut effacer avant de quitter `argos_app` : elle double la bascule sans remplacer le contrôle d'accès du service (défense en profondeur, ADR-0016). Contre une injection SQL, la protection du rôle de connexion tient à ce qu'il ne peut pas faire (schéma, base de Keycloak, fichiers du serveur), non à la RLS ; deux pools de connexions distincts, l'un réduit à `argos_app`, la renforceraient.
- À l'initialisation du volume, les mots de passe des rôles passent en variables sur la ligne de commande de `psql`, visibles par `ps` dans le conteneur pendant quelques instants ; le conteneur n'exécute rien d'autre à ce moment.

## Conséquences neutres / compromis

- L'instance reste un point de défaillance commun aux deux applications ; la sauvegarde exporte les deux bases à chaque exécution.
- `argos_technique` lit toutes les lignes hors contexte utilisateur, comme le faisait le propriétaire : les traitements de plateforme gardent leur portée.

## Méthode de validation

- Tests `RolesBaseTest` (EXP-05-S1 à S5 et couverture des politiques techniques) et `CouvertureRlsTest`, sur une base initialisée par le script de production.
- Essai `scripts/essai-sauvegarde-restauration.sh` : restauration sous le propriétaire de chaque base.
- Exercice de restauration de bout en bout et migration d'une installation existante, consignés dans les tâches du changement `fiabiliser-sauvegarde-et-roles-base`.

## Traçabilité

- **Exigences** : EXP-05 (fiche [`exploitation-plateforme`](/docs/specifications/exploitation-plateforme.md)) ; ACC-04 (cloisonnement).
- **Scénarios qualité** : QS-14 ([`scenarios.md`](../quality/scenarios.md)).
- **Diagrammes** : D-11 ([`d-11-deploiement-cible.md`](../diagrams/d-11-deploiement-cible.md)), D-12 ([`d-12-frontieres-confiance.md`](../diagrams/d-12-frontieres-confiance.md)).
- **Tickets/PR** : changement OpenSpec [`fiabiliser-sauvegarde-et-roles-base`](/openspec/changes/fiabiliser-sauvegarde-et-roles-base/proposal.md) ; constat AUD-SEC-30.
- **Fichiers source** : [`compose.yaml`](/compose.yaml), [`initialiser-bases.sh`](/deploiement/postgres/initialiser-bases.sh), [`V22__roles_connexion_et_technique.sql`](/argos-server/src/main/resources/db/migration/V22__roles_connexion_et_technique.sql), [`application.properties`](/argos-server/src/main/resources/application.properties).

## Notes

Toute décision remplacée doit rester versionnée et pointer vers son successeur.
