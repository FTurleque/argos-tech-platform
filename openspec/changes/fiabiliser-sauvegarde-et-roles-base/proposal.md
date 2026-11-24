# Proposition : fiabiliser la sauvegarde, la restauration et les rôles de base

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le Synology du porteur est la seule cible réelle d'Argos [DÉCISION — ADR-0026, Accepté] et l'audit du 2026-11-21 (révision `b142695`) relève que ses données ne sont pas protégées [EXISTANT — `/docs/audit/2026-11-21/findings.md`, sprint 2 de `/docs/audit/2026-11-21/sprints.md`] :

- AUD-QUA-02 : le script de sauvegarde ne détecte pas l'échec de `pg_dump` (pipeline sans `pipefail`) ; il sort en succès avec une archive vide, puis sa rotation supprime les bonnes sauvegardes après 14 échecs ;
- AUD-SEC-07 : les sauvegardes complètes sont écrites en clair, avec les permissions par défaut ;
- AUD-DEP-24 : la base `keycloak`, ajoutée dans le même PostgreSQL par l'installation locale (#28), n'est ni sauvegardée ni restaurée ; après une reprise, le royaume réimporté émet de nouveaux sujets OIDC et les comptes Argos deviennent orphelins ;
- AUD-SEC-30 : Keycloak et Argos partagent le même compte PostgreSQL, superutilisateur de l'instance ;
- AUD-DEP-03 : la restauration documentée n'est ni testée ni idempotente (dump texte sans `--clean`, `psql` sans `ON_ERROR_STOP`) ;
- AUD-SEC-06 : le mot de passe de base vaut « argos » si `.env` est absent, pour la base, Argos et Keycloak.

Ces constats ouvrent la nouvelle capacité `exploitation-plateforme`, que complètent les changements `completer-configuration-conteneurs` et `instrumenter-observabilite`.

## What Changes — Changements

- La sauvegarde échoue bruyamment : code de sortie non nul, aucune archive invalide conservée, aucune rotation après un échec (EXP-01).
- La sauvegarde emporte la base d'Argos et, lorsqu'elle existe, la base de Keycloak (EXP-02).
- Les sauvegardes ne sont lisibles que par le compte qui les produit (EXP-03) ; le chiffrement reste à décider (Q-2).
- Une procédure de restauration des deux bases, rejouable sur une instance vierge et arrêtée à la première erreur, est écrite et exercée (EXP-04).
- Keycloak reçoit un rôle dédié, non superutilisateur, propriétaire de sa seule base dans le PostgreSQL partagé ; Argos applique ses migrations avec un compte propriétaire et se connecte avec un rôle applicatif non superutilisateur qui ne contourne pas la RLS (EXP-05) [DÉCISION — décision du porteur, 2026-11-24].
- Le déploiement refuse de démarrer sans mot de passe de base explicite (EXP-06). **BREAKING** (exploitation) : un `.env` sans `ARGOS_DB_PASSWORD` ne démarre plus ; un volume PostgreSQL existant demande une migration de ses rôles (design.md, « Compatibilité, migration et retour arrière »).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `exploitation-plateforme` (préfixe `EXP`) : sauvegarde et restauration des données, configuration du déploiement, conteneurs et observabilité de la plateforme. Ce changement crée la capacité (Purpose) et ses exigences EXP-01 à EXP-06.

### Modified Capabilities — Capacités modifiées

Aucune. Les comptes Argos restent rattachés à leur sujet OIDC (`comptes-acces`, inchangée) ; ce changement garantit seulement que ce sujet survit à une restauration.

## Périmètre

### Inclus

- `scripts/sauvegarder-base.sh` (échec détecté, deux bases, permissions, rotation après succès seulement).
- Une procédure, et si possible un script, de restauration des bases `argos` et `keycloak`.
- `deploiement/postgres/creer-base-keycloak.sh` et `compose.yaml` (comptes de base distincts, mots de passe obligatoires), `.env.example`.
- Le guide `/docs/deploiement/synology-et-pc.md` (sauvegarde, restauration, profil `idp-local`, migration d'un volume existant).
- La fiche durable `/docs/specifications/exploitation-plateforme.md` et sa ligne du registre.

### Exclu

- La parité des variables, les contrôles de santé, la mémoire et le durcissement des conteneurs : changement `completer-configuration-conteneurs` (sprint 3).
- Journaux structurés, métriques et contrôles de santé métier : changement `instrumenter-observabilite` (sprint 4).
- La copie hors site (Hyper Backup) : réglage de DSM, hors dépôt [DÉCISION — ADR-0026, point 6 Proposé].
- Une instance PostgreSQL dédiée à Keycloak : écartée [DÉCISION — décision du porteur, 2026-11-24].
- Les valeurs RPO/RTO (§7.6 d'arc42, « à valider ») : non tranchées ici.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Exploitant (porteur sur le NAS) | planifie la sauvegarde, restaure, migre son volume | [EXISTANT] — `/docs/deploiement/synology-et-pc.md` (tâche planifiée DSM) |
| Utilisateur d'Argos | retrouve ses espaces après une restauration | [PROPOSITION] — EXP-04 |
| Porteur du projet | a tranché Q-1 ; tranche Q-2 à Q-4 | [DÉCISION] (Q-1, décision du porteur, 2026-11-24) ; [QUESTION] (Q-2 à Q-4) |
| Keycloak (profil `idp-local`) | fournisseur d'identité dont la base doit être sauvegardée | [DÉCISION] — ADR-0027, Accepté |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Pipeline `pg_dump | gzip` sous `set -eu`, rotation puis message de succès inconditionnels | [EXISTANT] | `scripts/sauvegarder-base.sh:5`, `:22`, `:26-31`, `:33` (relu à `b142695`) |
| Seule la base `$POSTGRES_DB` est exportée ; aucun `umask` | [EXISTANT] | `scripts/sauvegarder-base.sh:19`, `:22` |
| Base `keycloak` créée dans le même volume, par le même rôle | [EXISTANT] | `deploiement/postgres/creer-base-keycloak.sh:4` ; `compose.yaml:26-28`, `:49` |
| Même compte et repli « argos » pour PostgreSQL, Keycloak et Argos | [EXISTANT] | `compose.yaml:19-20`, `:50-51`, `:91-92` |
| Comptes Argos retrouvés par un sujet OIDC unique | [EXISTANT] | `argos-server/src/main/resources/db/migration/V2__comptes_et_acces.sql:26` |
| RLS appliquée par bascule `SET LOCAL ROLE argos_app`, contournable par un superutilisateur | [EXISTANT] | `V2__comptes_et_acces.sql:170-178` ; AUD-SEC-30 |
| Restauration documentée : `psql` sans `ON_ERROR_STOP`, base Argos seule, « à tester » | [EXISTANT] | `/docs/deploiement/synology-et-pc.md:91-97` |
| Migrations appliquées au démarrage | [EXISTANT] | `argos-server/src/main/resources/application.properties:23` ; ADR-0032 (Accepté) |
| Export quotidien et restauration testée | [PROPOSITION] | ADR-0026 (Accepté), point 6 marqué [Proposé] |
| Risque « sauvegarde non restaurable », scénario PRA | [PROPOSITION] | `/docs/architecture/risks/register.md` R-10 ; `/docs/architecture/quality/scenarios.md` QS-14 |
| Rôle dédié `keycloak` NOSUPERUSER propriétaire de sa seule base dans le PostgreSQL partagé ; dans Argos, propriétaire des migrations (Flyway) distinct d'un rôle de connexion NOSUPERUSER NOBYPASSRLS ; instance séparée écartée | [DÉCISION] | décision du porteur, 2026-11-24 (Q-1) |
| Critère de sortie du sprint 2 | [PROPOSITION] | `/docs/audit/2026-11-21/sprints.md`, « Sprint 2 » |

## Écarts constatés

- Comparaison des preuves de l'audit avec `b142695` : toutes les lignes citées par AUD-QUA-02, AUD-SEC-07, AUD-DEP-24, AUD-SEC-30, AUD-DEP-03 et AUD-SEC-06 sont à l'identique. Seule la section Restauration du guide est aux lignes 91-97 (l'audit cite 91-96 et :94) ; les « preuves d'origine » du 2026-11-20 citaient d'autres lignes (`compose.yaml:44`, guide :72-76), déjà corrigées par la vérification du 21/11.
- ADR-0026 (point 6) promet une « restauration testée » ; le guide la dit « À tester au moins une fois » (`synology-et-pc.md:97`) et la recette ne la mentionne pas (`/docs/deploiement/recette-poste.md`).
- Le guide (`synology-et-pc.md:29-37`) décrit le profil `idp-local` sans dire que sa base n'est pas sauvegardée.
- Le script d'initialisation (`creer-base-keycloak.sh`) s'exécute sur tout volume neuf, profil `idp-local` actif ou non : la base `keycloak` peut donc exister sans Keycloak, et manquer sur un volume antérieur à #28.

## Impact

- **Composants** (noms de arc42 §5) : PostgreSQL ; Keycloak auto-hébergé ; Quarkus Backend (compte de connexion seulement, aucun code métier).
- **Contrats d'interface** : aucun contrat d'API. Contrat d'exploitation : nouvelles variables de mot de passe (Keycloak, administration de l'instance), `ARGOS_DB_PASSWORD` obligatoire.
- **Données et persistance** : rôles PostgreSQL et propriété des bases ; format et contenu des sauvegardes. Séparation du propriétaire des migrations et du rôle de connexion d'Argos (droits du rôle de connexion sur les objets créés par les migrations).
- **Sécurité, confidentialité, droits** : fin du compte superutilisateur partagé ; sauvegardes en 0600 ; plus de mot de passe par défaut.
- **Exploitation et observabilité** : la tâche planifiée DSM signale l'échec par le code de sortie ; procédure de migration d'un volume existant.
- **Documentation durable à mettre à jour** : fiche `/docs/specifications/exploitation-plateforme.md` (à créer) et registre `/docs/specifications/README.md` ; `/docs/deploiement/synology-et-pc.md` ; arc42 §7.3, §7.7, §7.8 et §8.3 ; diagrammes D-11 et D-12 ; registre des risques (R-10).
- **ADR** : ADR-0026 (Accepté, point 6 Proposé), ADR-0027 (Accepté), ADR-0016 (RLS). ADR à rédiger (statut Proposé) pour consigner la décision du porteur du 2026-11-24 sur les comptes de base.
- **Dépendances entre changements** : aucun prérequis. `completer-configuration-conteneurs` et `instrumenter-observabilite` dépendent de ce changement, qui crée la capacité `exploitation-plateforme` et sa fiche ; `completer-configuration-conteneurs` modifie aussi `compose.yaml` et `.env.example` et doit partir de l'état laissé ici.

## Conception requise

complète — le changement touche la sécurité (rôles, secrets), la persistance et une procédure de migration d'installations existantes, et une séparation des comptes d'Argos qui touche ses migrations.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Keycloak reçoit-il un rôle dédié dans le PostgreSQL partagé ou sa propre instance ? Argos sépare-t-il le propriétaire des migrations d'un rôle de connexion `NOBYPASSRLS` ? | tranchée [DÉCISION — décision du porteur, 2026-11-24] : rôle dédié `keycloak` dans l'instance partagée ; séparation dans Argos dès ce changement | porteur du projet |
| Q-2 | Les sauvegardes sont-elles chiffrées par le script (age, gpg : outil et gestion de la clé à choisir) ou par Hyper Backup ? | non : EXP-03 couvre les permissions ; le chiffrement s'ajoute | porteur du projet |
| Q-3 | Quelle taille minimale d'archive tient lieu de contrôle de vraisemblance, en plus du test d'intégrité de l'archive ? | non : le contrôle d'intégrité suffit pour EXP-01 | exploitant |
| Q-4 | À quelle fréquence l'exercice de restauration est-il rejoué (R-10 demande des tests « périodiques ») ? | non | porteur du projet |
