# Tâches : mettre en place l'intégration continue

<!--
Changement d'outillage (skip_specs: true) issu de l'audit du 2026-11-21, sprint 1 (/docs/audit/2026-11-21/sprints.md).
Décisions du porteur, 2026-11-24 : GitHub Actions (Q-1) ; seuil JaCoCo global bloquant 85 % des lignes, 70 % des branches (Q-2) ; Renovate (Q-3) ; protection de main activée par le porteur (Q-4).
Règles : aucun comportement applicatif modifié ; outils sous licence permissive (ADR-0021, Proposé) ; ADR-0032 inchangé.
-->

## 1. Workflow de base

- [ ] 1.1 Créer sous `.github/workflows/` un workflow GitHub Actions déclenché sur push de `main` et sur pull request, jeton `contents: read`, actions épinglées par empreinte — couvre AUD-TST-01 — vérification : le workflow apparaît sur une pull request de test
- [ ] 1.2 Job serveur : Temurin 17 (`actions/setup-java`), cache Maven, `mvn -B -f argos-server/pom.xml clean verify` sur un exécuteur avec Docker — couvre AUD-TST-01, AUD-TST-19 — vérification : job vert ; journal montrant Java 17 et le nombre de tests (654 à `b142695`)
- [ ] 1.3 Job IHM : `npm ci`, `npm test`, `npm run typecheck`, `npm run build` dans `argos-ui` — couvre AUD-TST-01 — vérification : job vert ; une erreur de type introduite sur une branche de test rend le job rouge
- [ ] 1.4 Job desktop : `npm ci` puis `npm run check` dans `argos-desktop` — couvre AUD-TST-01 — vérification : job vert
- [ ] 1.5 Job outils : `node --test scripts/*.test.mjs "e2e/performance/*.test.mjs"`, `node scripts/verifier-docs.mjs`, `node scripts/verifier-nommage.mjs` — couvre AUD-TST-01 — vérification : job vert
- [x] 1.6 Documentation du groupe : `README.md` (« Vérifications locales » : renvoi au workflow, commandes identiques), arc42 §8.11 (barrière d'intégration continue), arc42 §11.2 (dette : absence de CI retirée) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Garde du JDK et sécurité des dépendances npm

- [x] 2.1 Ajouter maven-enforcer à `argos-server/pom.xml` : Java 17 ou plus par défaut, plage `[17,18)` dans un profil activé par le workflow (design.md D2, Q-6) — couvre AUD-TST-19 — vérification : `mvn -B -P <profil> validate` sous JDK 21 ou 24 échoue avec le message de l'enforcer ; job serveur vert
- [x] 2.2 Consigner dans `README.md` la consigne IDE d'écrire hors de `target/` et l'usage de `clean verify` (AUD-TST-19, build incrémental) — couvre AUD-TST-19 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 2.3 Ajouter `npm audit` (niveau fixé par Q-7) pour `argos-ui`, `argos-desktop` et `e2e` — couvre AUD-TST-01 (critère de sortie du sprint 1) — vérification : étape présente dans le journal ; niveau documenté dans arc42 §8.11
- [x] 2.4 Corriger le commentaire `argos-server/src/main/resources/application.properties:22` (« ADR-0032, proposé » → Accepté) — couvre AUD-DEP-20 (écart documentaire) — vérification : `grep -n "ADR-0032" argos-server/src/main/resources/application.properties`

## 3. Couverture

- [x] 3.1 Ajouter jacoco-maven-plugin (`prepare-agent`, `report`) et l'extension de test `quarkus-jacoco` ; publier le rapport comme artefact du workflow — couvre AUD-TST-02 — vérification : rapport HTML et XML disponibles sur l'exécution ; taux global proche de la référence (85,8 % des lignes, 72,0 % des branches)
- [x] 3.2 Ajouter le `check` JaCoCo bloquant au niveau `BUNDLE` : `LINE` `COVEREDRATIO` minimum 0,85, `BRANCH` `COVEREDRATIO` minimum 0,70 (design.md D3) — couvre AUD-TST-02 — vérification : build vert avec ces seuils ; seuil relevé au-dessus de la mesure sur une branche de test → build en échec
- [x] 3.3 Documentation du groupe : arc42 §8.11 (rapport, seuil global 85 % des lignes et 70 % des branches, décision du porteur du 2026-11-24) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Migrations et licences

- [x] 4.1 Ajouter l'étape de garde des migrations (`fetch-depth: 0`, `git diff --name-status <base>...HEAD -- argos-server/src/main/resources/db/migration`, échec sur tout statut autre que `A`) — couvre AUD-DEP-20 — vérification : pull request de test modifiant une migration existante → rouge ; ajout d'une migration → vert
- [x] 4.2 Écrire `scripts/verifier-licences-serveur.test.mjs` (`node:test`) sur inventaires factices : dépendance GPL refusée, licence absente refusée, BOM absent en erreur, inventaire conforme accepté — couvre AUD-DEP-22 — vérification : `node --test scripts/*.test.mjs` passe et exécute les quatre cas
- [ ] 4.3 Ajouter le job `mvn -B -f argos-server/pom.xml -P licences validate`, filtré sur `argos-server/pom.xml` et `scripts/verifier-licences-serveur.mjs` — couvre AUD-DEP-22 — vérification : job déclenché par une modification de `pom.xml` sur une branche de test, ignoré sinon
- [x] 4.4 Documentation du groupe : arc42 §8.7 (garde mécanique de la règle d'ADR-0032), `argos-server/README.md` (licences : contrôle en intégration continue) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Veille des dépendances et protection de branche

- [ ] 5.1 Écrire `renovate.json` (gestionnaires `maven`, `npm` pour `argos-ui`, `argos-desktop` et `e2e`, `dockerfile`, `docker-compose`, `github-actions`, et gestionnaire `regex` pour les valeurs par défaut des variables d'image de `compose.yaml`, design.md D5) et demander au porteur d'installer l'application Renovate sur le dépôt — couvre AUD-DEP-05 — vérification : premières pull requests ouvertes (attendues : Electron 45.4.x, `@playwright/test`, image Keycloak 26.7.5 ou plus de `compose.yaml`) et vérifiées par le workflow
- [ ] 5.2 Demander au porteur du projet d'activer la protection de `main` (statut du workflow obligatoire, options de Q-4) — couvre AUD-TST-01 — vérification : pull request en échec non fusionnable (constat du porteur)
- [x] 5.3 Documentation du groupe : arc42 §8.11 ou §8.12 (veille des dépendances), et, si le porteur le retient, ADR « GitHub Actions et Renovate » consignant les décisions du porteur du 2026-11-24 (design.md D1, D5) avec l'index arc42 §9 — couvre AUD-TST-01, AUD-DEP-05 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Vérifications d'intégration

- [x] 6.1 Valider le changement — vérification : `openspec validate mettre-en-place-integration-continue --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur
- [ ] 6.2 Contrôler le critère de sortie du sprint 1 pour la part de ce changement (workflow, JDK 17, JaCoCo et seuil, garde des migrations, licences, Renovate) — vérification : liste cochée dans les preuves de ce fichier, avec liens vers les exécutions du workflow

## Preuves

Poste du mainteneur, 2026-11-24, JDK 17.0.7, Docker 29.9.3 :

- `mvn -B -f argos-server/pom.xml -P integration-continue clean verify` : BUILD SUCCESS, 656 tests, 0 échec, 1 ignoré préexistant ; « All coverage checks have been met » ; couverture globale mesurée par JaCoCo (agent et `quarkus-jacoco`) : 90,1 % des lignes (9 104 / 10 108), 75,9 % des branches (3 721 / 4 900), au-dessus de la référence de l'audit (85,8 % / 72,0 %, mesure d'origine différente) et du seuil 85 % / 70 %. Une première exécution a rencontré un échec intermittent de `ResilienceTest.col05S2ArretBrutalDuBackendPuisReprise` (ligne 203, `recoverExpired(Duration.ZERO)` comparant l'horloge de la JVM à celle de la base), vert à la relance : constat AUD-TST-06, traité par `injecter-horloge-et-executeurs` (sprint 4).
- 3.2 : `mvn -f argos-server/pom.xml jacoco:check@seuil-couverture -Dargos.couverture.lignes=0.95` échoue (« Coverage checks have not been met »).
- 2.1 : `mvn -B -f argos-server/pom.xml -P integration-continue validate` sous JDK 21 échoue (« JDK hors de la plage [17,18) ») ; sans le profil, `validate` réussit sous JDK 21.
- 2.3 : `npm audit --audit-level=high` réussit dans `argos-ui` (0 vulnérabilité), `argos-desktop` (6 modérées d'electron-builder, AUD-DEP-11, non bloquantes) et `e2e` (0) ; niveau `high` fixé par le porteur du projet le 2026-11-24 (Q-7).
- 4.1 : la commande de garde rejouée sur l'historique signale `M V16__veille_reglementaire.sql` pour le commit `7e6363f` et rien pour l'ajout de migration du commit `f3b1cfc`.
- 4.2 : `node --test scripts/verifier-licences-serveur.test.mjs` : 4 tests, 0 échec.
- 5.1 : `renovate.json` validé par `renovate-config-validator` ; le gestionnaire `regex` capte `quay.io/keycloak/keycloak` 26.7.4 et `ollama/ollama` 0.12.6 dans `compose.yaml`.
- 5.3 : décisions consignées dans [ADR-0034](/docs/architecture/adr/0034-integration-continue-github-actions-renovate.md) (Proposé), indexé dans `docs/architecture/adr/README.md` et arc42 §9.1 (le porteur a retenu l'ADR le 2026-11-24).
- Q-6 : maven-enforcer en profil d'intégration continue seulement (porteur du projet, 2026-11-24).
- `node scripts/verifier-docs.mjs` : aucune erreur ; `openspec validate mettre-en-place-integration-continue --strict` : valide.

## Matrice de traçabilité

| Constat | Exigence / scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AUD-TST-01 | aucune (`skip_specs`) | design.md DC-1, D1 | 1.1 à 1.6, 2.3, 5.2 | exécutions du workflow ; pull request en échec non fusionnable | non exécuté |
| AUD-TST-19 | aucune | design.md D2 | 1.2, 2.1, 2.2 | journal du job serveur (Java 17, `clean verify`) ; refus de l'enforcer sous JDK 21/24 | non exécuté |
| AUD-TST-02 | aucune | design.md D3 | 3.1 à 3.3 | rapport JaCoCo publié ; build en échec sous 85 % des lignes ou 70 % des branches | non exécuté |
| AUD-DEP-20 | aucune | design.md D4, DC-1 | 2.4, 4.1, 4.4 | pull request de test modifiant une migration → rouge | non exécuté |
| AUD-DEP-22 | aucune | design.md DC-1 | 4.2 à 4.4 | `scripts/verifier-licences-serveur.test.mjs` ; job licences filtré | à écrire |
| AUD-DEP-05 | aucune | design.md D5 | 5.1, 5.3 | pull requests de Renovate | non exécuté |

## Workflow follow-up

- Après la fusion, signaler au coordinateur de l'audit le passage d'AUD-TST-01, AUD-TST-19, AUD-TST-02, AUD-DEP-20, AUD-DEP-22 et AUD-DEP-05 à « Corrigé » selon les preuves.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive mettre-en-place-integration-continue`).
- Après archivage, remplacer dans la documentation les liens vers `/openspec/changes/mettre-en-place-integration-continue/` par le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
