# Tâches : fiabiliser la sauvegarde, la restauration et les rôles de base

<!--
Sprint 2 de l'audit du 2026-11-21 (/docs/audit/2026-11-21/sprints.md), ordre interne : AUD-QUA-02 → AUD-SEC-07 → AUD-DEP-24 → AUD-SEC-30 → AUD-DEP-03 → AUD-SEC-06.
Critère de sortie du sprint : pg_dump en échec → script en erreur sans rotation ; bases argos et keycloak sauvegardées en 0600 ; restauration des deux bases rejouée sur une instance vierge, un utilisateur s'y reconnecte avec ses espaces ; rôles distincts, non superutilisateurs, propriétaires de leur seule base ; aucun mot de passe par défaut dans compose.yaml.
Q-1 tranchée (décision du porteur, 2026-11-24) : rôle dédié keycloak dans le PostgreSQL partagé ; dans Argos, propriétaire des migrations distinct du rôle de connexion.
-->

## 1. Sauvegarde fiable et protégée

- [x] 1.1 Réécrire `scripts/sauvegarder-base.sh` : export vers un fichier temporaire, code de retour de l'export contrôlé sans pipeline, vérification d'intégrité de l'archive, renommage seulement en cas de succès, rotation après le succès de toutes les bases, code non nul et base nommée en cas d'échec (design.md D1, DC-1) — couvre EXP-01-S1, EXP-01-S2, EXP-01-S3 (AUD-QUA-02) — vérification : tests de la tâche 1.4 ; `sh -n scripts/sauvegarder-base.sh`
- [x] 1.2 Poser `umask 077` en tête du script et le dossier de sauvegarde en 0700 — couvre EXP-03-S1, EXP-03-S2 (AUD-SEC-07) — vérification : tests de la tâche 1.4
- [x] 1.3 Exporter aussi la base `keycloak` lorsqu'elle existe dans l'instance, dans une archive distincte de même horodatage ; signaler son absence sans échouer ; rotation par base (design.md D2) — couvre EXP-02-S1, EXP-02-S2 (AUD-DEP-24) — vérification : tests de la tâche 1.4
- [x] 1.4 Écrire le script de test de la sauvegarde contre une pile PostgreSQL jetable : postgres arrêté, archive tronquée, échecs répétés autant que `ARGOS_BACKUP_KEEP`, volume avec et sans base `keycloak`, permissions sous `umask 000` — couvre EXP-01, EXP-02, EXP-03 — vérification : le script de test passe et échoue si l'on réintroduit l'ancien pipeline

## 2. Restauration complète et rejouable

- [x] 2.1 Écrire un script (ou une procédure pas à pas) de restauration des bases `argos` et `keycloak` : services `argos` et `keycloak` arrêtés, bases recréées, rechargement arrêté à la première erreur, code non nul et base nommée en cas d'échec, Keycloak démarré seulement après la restauration (design.md D3, DC-2) — couvre EXP-04-S1, EXP-04-S2, EXP-04-S3 (AUD-DEP-03, AUD-DEP-24) — vérification : tests de la tâche 2.2
- [x] 2.2 Tester la restauration : archive tronquée (arrêt et code non nul), restauration sur une base déjà migrée (contenu égal à la sauvegarde, aucune ligne en double) — couvre EXP-04-S2, EXP-04-S3 — vérification : le script de test passe
- [x] 2.3 Réécrire la section Restauration de `/docs/deploiement/synology-et-pc.md` (deux bases, ordre des opérations, arrêt sur erreur, sauvegarde préalable) — couvre EXP-04 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 2.4 Compléter la section « Avec Keycloak sur le même poste » du guide (base `keycloak` sauvegardée, restaurée avant le premier démarrage de Keycloak) et `/docs/deploiement/recette-poste.md` (étape de restauration) — couvre EXP-02, EXP-04 (AUD-DEP-24) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Comptes de base distincts et secrets obligatoires

- [x] 3.1 Rédiger l'ADR « Comptes et isolation des bases de la plateforme » au statut Proposé, consignant la décision du porteur du 2026-11-24 (rôle dédié `keycloak`, instance séparée écartée, séparation propriétaire des migrations / rôle de connexion dans Argos), avec l'index arc42 §9 — couvre EXP-05 (AUD-SEC-30) — vérification : ADR présent dans `/docs/architecture/adr/`, `node scripts/verifier-docs.mjs` sans erreur
- [x] 3.2 Créer à l'initialisation du volume les comptes distincts (`keycloak` propriétaire de sa seule base, `CONNECT` retiré à `PUBLIC` ; propriétaire de la base `argos` non superutilisateur ; rôle de connexion d'Argos NOSUPERUSER NOBYPASSRLS ; superutilisateur d'administration distinct) et brancher `compose.yaml` sur ces comptes — couvre EXP-05-S1, EXP-05-S2, EXP-05-S3 (AUD-SEC-30) — vérification : tests de la tâche 3.6
- [x] 3.3 Faire appliquer les migrations par le propriétaire de la base, connecter l'application avec le rôle de connexion membre de `argos_app`, accorder par migration ses droits sur les objets existants et futurs ; mettre à jour arc42 §8.7 — couvre EXP-05-S3, EXP-05-S4, EXP-05-S5 — vérification : `mvn -B -f argos-server/pom.xml verify` passe ; tests de la tâche 3.6
- [x] 3.4 Écrire et tester sur une copie de volume la migration d'une installation existante (sauvegarde, superutilisateur d'administration, rôle Keycloak et propriété de sa base, retrait du superutilisateur au compte Argos devenu propriétaire des migrations, création du rôle de connexion, nouveaux mots de passe, retour arrière) dans `/docs/deploiement/synology-et-pc.md` — couvre EXP-05 — vérification : migration rejouée sur une copie, Argos et Keycloak redémarrent, utilisateur reconnecté
- [x] 3.5 Rendre obligatoires dans `compose.yaml` tous les mots de passe de base (`${VAR:?message}`) et les documenter dans `.env.example` — couvre EXP-06-S1, EXP-06-S2 (AUD-SEC-06) — vérification : `docker compose config` échoue sans `.env` en nommant la variable, réussit avec ; `grep -n ':-argos}' compose.yaml` ne trouve aucun mot de passe
- [x] 3.6 Tester les comptes : connexions croisées refusées, attributs des comptes (`pg_roles`), retour au rôle initial puis lecture d'un espace étranger sans résultat, DDL refusé au rôle de connexion, migration appliquée par le propriétaire — couvre EXP-05-S1 à EXP-05-S5 — vérification : le script de test passe sur une pile neuve

## 4. Documentation durable et décisions

- [x] 4.1 Créer la fiche `/docs/specifications/exploitation-plateforme.md` à partir de `/docs/specifications/_modele-fiche-capacite.md` (objectif, acteurs, périmètre, règles EXP-01 à EXP-06 citées par identifiant, statuts [PROPOSITION] avec l'identifiant du changement, décision du porteur du 2026-11-24 (Q-1), questions Q-2 à Q-4, diagrammes métier de la sauvegarde et de la restauration) et renseigner sa ligne du registre `/docs/specifications/README.md` (colonne Fiche, inventaire des diagrammes métier) ; une fois la fiche créée, remplacer dans le Purpose du delta `specs/exploitation-plateforme/spec.md` le chemin entre accents graves par le lien `Fiche : [/docs/specifications/exploitation-plateforme.md](/docs/specifications/exploitation-plateforme.md)` — couvre EXP-01 à EXP-06 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.2 Mettre à jour arc42 §7.3, §7.7 (restauration testée), §7.8 et §8.3 (comptes de base), les diagrammes D-11 et D-12, puis l'inventaire `/docs/architecture/diagrams/README.md` — couvre EXP-02, EXP-04, EXP-05 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.3 Mettre à jour R-10 dans `/docs/architecture/risks/register.md` (mesure mise en place, périodicité selon Q-4) — couvre EXP-04 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [x] 5.1 Exercice de restauration de bout en bout : installation `idp-local` avec un utilisateur membre d'un espace, sauvegarde, instance vierge, restauration, connexion de l'utilisateur, espace retrouvé ; consigner la date et la révision dans ce fichier — couvre EXP-04-S1 (AUD-DEP-03, AUD-DEP-24 ; QS-14) — vérification : compte rendu de l'exercice sous « Preuves »
- [x] 5.2 Valider le changement — vérification : `openspec validate fiabiliser-sauvegarde-et-roles-base --strict` et `node scripts/verifier-docs.mjs` réussissent

## Matrice de traçabilité

| Exigence | Scénario | Constat | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|---|
| EXP-01 | EXP-01-S1 | AUD-QUA-02 | design.md D1, DC-1 | 1.1, 1.4 | `scripts/essai-sauvegarde-restauration.sh` | réussi le 2026-11-24 |
| EXP-01 | EXP-01-S2 | AUD-QUA-02 | design.md D1, DC-1 | 1.1, 1.4 | idem (postgres arrêté) | réussi le 2026-11-24 |
| EXP-01 | EXP-01-S3 | AUD-QUA-02 | design.md D1, INV-2 | 1.1, 1.4 | idem (échecs répétés) | réussi le 2026-11-24 |
| EXP-02 | EXP-02-S1 | AUD-DEP-24 | design.md D2, DC-1 | 1.3, 1.4 | idem (volume avec base `keycloak`) | réussi le 2026-11-24 |
| EXP-02 | EXP-02-S2 | AUD-DEP-24 | design.md D2 | 1.3, 1.4 | idem (volume sans base `keycloak`) | réussi le 2026-11-24 |
| EXP-03 | EXP-03-S1 | AUD-SEC-07 | design.md « Sécurité et isolation » | 1.2, 1.4 | idem (`stat`, `umask 000`) | réussi le 2026-11-24 (WSL, système de fichiers Linux) |
| EXP-03 | EXP-03-S2 | AUD-SEC-07 | idem | 1.2, 1.4 | idem (lecture par un autre compte) | réussi le 2026-11-24 (WSL, en root, compte `nobody`) |
| EXP-04 | EXP-04-S1 | AUD-DEP-03, AUD-DEP-24 | design.md D3, DC-2 | 2.1, 5.1 | exercice de restauration (« Preuves ») | réussi le 2026-11-24 |
| EXP-04 | EXP-04-S2 | AUD-DEP-03 | design.md D3, DC-2 | 2.1, 2.2 | `scripts/essai-sauvegarde-restauration.sh` | réussi le 2026-11-24 |
| EXP-04 | EXP-04-S3 | AUD-DEP-03 | design.md D3 | 2.1, 2.2 | idem (base modifiée, restauration rejouée) | réussi le 2026-11-24 |
| EXP-05 | EXP-05-S1 | AUD-SEC-30 | design.md D4, « Persistance et intégrité » | 3.1, 3.2, 3.6 | `RolesBaseTest#connexionsCroiseesRefusees` | réussi le 2026-11-24 |
| EXP-05 | EXP-05-S2 | AUD-SEC-30 | design.md D4, INV-3 | 3.2, 3.6 | `RolesBaseTest#aucunSuperutilisateurApplicatif` | réussi le 2026-11-24 |
| EXP-05 | EXP-05-S3 | AUD-SEC-30 | design.md D4 | 3.2, 3.3, 3.6 | `RolesBaseTest#abandonDuRoleRestreintSansEffet` | réussi le 2026-11-24 |
| EXP-05 | EXP-05-S4 | AUD-SEC-30 | design.md D4, « Persistance et intégrité » | 3.2, 3.3, 3.6 | `RolesBaseTest#schemaHorsDePorteeDuRoleDeConnexion` | réussi le 2026-11-24 |
| EXP-05 | EXP-05-S5 | AUD-SEC-30 | design.md D4, « Persistance et intégrité » | 3.3, 3.6 | `RolesBaseTest#migrationsSousLeProprietaireRequetesSousLaConnexion` ; `mvn -B -f argos-server/pom.xml verify` | réussi le 2026-11-24 |
| EXP-06 | EXP-06-S1 | AUD-SEC-06 | design.md D5 | 3.5 | `docker compose config` sans `.env` | réussi le 2026-11-24 (refus nommant chaque variable) |
| EXP-06 | EXP-06-S2 | AUD-SEC-06 | design.md D5 | 3.5 | `docker compose config` avec `.env` | réussi le 2026-11-24 |

## Preuves

Exercice de bout en bout du 2026-11-24 (tâches 3.4 et 5.1), révision de base `b1757c3` complétée par ce changement, sur un PC Windows (Docker Engine 29.9.3), projet Compose isolé `argos-exercice` :

1. Installation à l'ancien format (`compose.yaml` et image d'Argos construits depuis `b1757c3`, compte unique `argos` superutilisateur, profil `idp-local`) ; l'administrateur se connecte, Argos crée son compte (`1664ab58-…`) et l'espace « Espace exercice restauration » (`8fad68ba-…`) ; son mot de passe Keycloak est remplacé par un mot de passe définitif.
2. Migration selon le guide (« Passer aux comptes de base distincts ») : sauvegarde des deux bases par le nouveau script sur l'ancien conteneur, copie de l'ancien volume, volume neuf, restauration, Keycloak puis Argos démarrés. Résultat : l'utilisateur se connecte avec son mot de passe définitif, sous le même compte Argos, membre du même espace ; V22 appliquée sous `argos` (devenu propriétaire non superutilisateur) ; sessions ouvertes par `argos_connexion` sur `argos` et par `keycloak` sur `keycloak` ; aucun de ces comptes n'a `rolsuper` ni `rolbypassrls`.
3. Restauration sur une instance vierge : sauvegarde, `docker compose down -v` (volume supprimé), `docker compose up -d postgres`, `scripts/restaurer-base.sh`, Keycloak puis Argos. Keycloak journalise « Realm 'argos' already exists. Import skipped » ; l'utilisateur se reconnecte avec son mot de passe définitif, sous le même compte Argos, et retrouve son espace (EXP-04-S1).

## Workflow follow-up

- Basculer dans la fiche `exploitation-plateforme` les statuts [PROPOSITION] de EXP-01 à EXP-06 en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive fiabiliser-sauvegarde-et-roles-base`) ; il doit l'être avant `completer-configuration-conteneurs` et `instrumenter-observabilite`, qui ajoutent des exigences à la capacité qu'il crée.
- Après archivage, vérifier que la section Purpose de `/openspec/specs/exploitation-plateforme/spec.md` contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/fiabiliser-sauvegarde-et-roles-base/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
