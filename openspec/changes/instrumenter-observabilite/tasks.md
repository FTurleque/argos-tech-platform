# Tâches : instrumenter l'observabilité du serveur

<!--
Sprint 4 de l'audit du 2026-11-21 (/docs/audit/2026-11-21/sprints.md), premier constat de l'ordre interne : AUD-DEP-07. Prépare les sprints 8 à 10 (ingestion asynchrone).
Prérequis : fiabiliser-sauvegarde-et-roles-base (capacité exploitation-plateforme et fiche). Q-1 (protection de /q/metrics) à trancher avant la tâche 2.2.
-->

## 1. Journaux structurés et corrélés

- [ ] 1.1 Ajouter `quarkus-logging-json` activée en production, texte en `dev` et `test` (design.md D1) ; vérifier les licences — couvre EXP-13 (AUD-DEP-07) — vérification : `mvn -B -P licences validate` réussit ; journal JSON au démarrage du profil `prod`
- [ ] 1.2 Poser l'identifiant de corrélation dans le MDC par un filtre de requête (API, MCP) et un intercepteur des tâches planifiées (nom de tâche compris), propagé aux fils d'exécution utilisés (design.md D2, DC-1) — couvre EXP-13-S1, EXP-13-S2 — vérification : tests de la tâche 1.4
- [ ] 1.3 Revoir les appels de journalisation existants : cause passée comme exception (pile), aucun jeton, mot de passe ni adresse e-mail en clair — couvre EXP-13-S3, EXP-13-S4 — vérification : tests de la tâche 1.4 ; recherche des appels qui passent l'exception en argument de format
- [ ] 1.4 Écrire les tests : lignes JSON valides, même identifiant dans une requête et identifiants distincts entre requêtes concurrentes, identifiant et nom de tâche d'une exécution planifiée, pile d'une erreur, jeton absent — couvre EXP-13-S1 à EXP-13-S4 — vérification : `mvn -B -f argos-server/pom.xml verify` passe

## 2. Métriques et santé

- [ ] 2.1 Ajouter `quarkus-micrometer-registry-prometheus` et les métriques métier : exécutions et durée par tâche (intercepteur de 1.2), sources par état, file d'envoi et échecs d'alertes, appels et refus de l'IA, étiquettes bornées (design.md D3, « Contrats d'interface ») — couvre EXP-14-S1, EXP-14-S3 (AUD-DEP-07) — vérification : tests de la tâche 2.4
- [ ] 2.2 Protéger `/q/metrics` selon Q-1 (port de gestion non publié ou rôle d'administration plateforme), en accord avec le contrôle de santé du conteneur de `completer-configuration-conteneurs` (design.md D4) — couvre EXP-14-S2 — vérification : tests de la tâche 2.4 ; `compose.yaml` ne publie pas le port de gestion si l'option A est retenue
- [ ] 2.3 Ajouter les contrôles `@Readiness` non bloquants : un par tâche planifiée active (retard toléré configurable, Q-2), sources, envoi d'e-mails, passerelle IA ; la base seule rend le serveur indisponible (design.md D5, DC-2) — couvre EXP-15-S1 à EXP-15-S4 — vérification : tests de la tâche 2.4
- [ ] 2.4 Écrire les tests : métriques d'une tâche en échec, appel anonyme refusé, étiquettes sans donnée personnelle, source `DEGRADED`, tâche en retard, tâche et IA désactivées, base arrêtée — couvre EXP-14-S1 à EXP-14-S3, EXP-15-S1 à EXP-15-S4 — vérification : `mvn -B -f argos-server/pom.xml verify` passe

## 3. Documentation durable et décisions

- [ ] 3.1 Compléter la fiche `/docs/specifications/exploitation-plateforme.md` (EXP-13 à EXP-15 citées par identifiant, statuts [PROPOSITION] avec l'identifiant du changement, diagramme de la composition de la santé, questions Q-1 à Q-4) et la colonne Changement du registre `/docs/specifications/README.md` si besoin — couvre EXP-13 à EXP-15 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 3.2 Mettre à jour arc42 §8.5 (health/readiness), §8.6 (pile, corrélation, nommage des métriques) et §11, le guide `/docs/deploiement/synology-et-pc.md` (journaux JSON, accès aux métriques) — couvre EXP-13, EXP-14, EXP-15 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 3.3 Rédiger l'ADR « Pile d'observabilité du serveur » au statut Proposé (Micrometer et Prometheus, OpenTelemetry différé, protection de `/q/metrics`) et l'ajouter à l'index arc42 §9 — couvre EXP-14 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [ ] 4.1 Relancer la campagne `npm --prefix e2e run test:performance` : opérations toujours dans la cible PERF-01 après instrumentation — couvre EXP-13, EXP-14 (absence de régression) — vérification : rapport de campagne, code de sortie 0
- [ ] 4.2 Valider le changement — vérification : `openspec validate instrumenter-observabilite --strict` et `node scripts/verifier-docs.mjs` réussissent

## Matrice de traçabilité

| Exigence | Scénario | Constat | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|---|
| EXP-13 | EXP-13-S1 | AUD-DEP-07 | design.md D1, D2, DC-1 | 1.1, 1.2, 1.4 | test d'intégration des journaux | non exécuté (à écrire) |
| EXP-13 | EXP-13-S2 | AUD-DEP-07 | design.md D2, DC-1 | 1.2, 1.4 | idem (exécution planifiée) | non exécuté (à écrire) |
| EXP-13 | EXP-13-S3 | AUD-DEP-07 | design.md DC-1 | 1.3, 1.4 | idem (erreur avec pile) | non exécuté (à écrire) |
| EXP-13 | EXP-13-S4 | AUD-DEP-07 | design.md « Sécurité et isolation » | 1.3, 1.4 | idem (jeton absent) | non exécuté (à écrire) |
| EXP-14 | EXP-14-S1 | AUD-DEP-07 | design.md D3, DC-1 | 2.1, 2.4 | test d'intégration des métriques | non exécuté (à écrire) |
| EXP-14 | EXP-14-S2 | AUD-DEP-07 | design.md D4 | 2.2, 2.4 | idem (appel anonyme) | non exécuté (à écrire) |
| EXP-14 | EXP-14-S3 | AUD-DEP-07 | design.md « Contrats d'interface », INV-2 | 2.1, 2.4 | idem (étiquettes) | non exécuté (à écrire) |
| EXP-15 | EXP-15-S1 | AUD-DEP-07 | design.md D5, DC-2 | 2.3, 2.4 | test d'intégration de la santé | non exécuté (à écrire) |
| EXP-15 | EXP-15-S2 | AUD-DEP-07 | design.md D5, DC-2 | 2.3, 2.4 | idem (tâche en retard) | non exécuté (à écrire) |
| EXP-15 | EXP-15-S3 | AUD-DEP-07 | design.md D5 | 2.3, 2.4 | idem (tâche et IA désactivées) | non exécuté (à écrire) |
| EXP-15 | EXP-15-S4 | AUD-DEP-07 | design.md D5, INV-3 | 2.3, 2.4 | idem (base arrêtée) | non exécuté (à écrire) |

## Workflow follow-up

- Basculer dans la fiche `exploitation-plateforme` les statuts [PROPOSITION] de EXP-13 à EXP-15 en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue, et après l'archivage de `fiabiliser-sauvegarde-et-roles-base` (`openspec archive instrumenter-observabilite`) : archivé seul, ce delta ADDED créerait une spec principale sans Purpose.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/instrumenter-observabilite/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
