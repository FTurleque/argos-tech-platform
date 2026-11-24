# Proposition : mettre en place l'intégration continue

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Aucun test n'est exécuté automatiquement : le dépôt n'a ni workflow d'intégration continue ni hook, l'image Docker est construite avec `-DskipTests`, et les six commandes de vérification du README ne sont lancées qu'à la main [EXISTANT — `.github/` absent à `b142695` ; `Dockerfile:37` ; `README.md`, section « Vérifications locales »]. L'audit du 2026-11-21 classe ce constat en sévérité élevée, car il bloque la vérification de toutes les corrections suivantes (AUD-TST-01, `/docs/audit/2026-11-21/findings.md`). Cinq constats en dépendent directement : JDK de test différent de celui de l'image et build incrémental non fiable (AUD-TST-19), couverture mesurée à la main sans seuil (AUD-TST-02), aucune garde contre la réécriture d'une migration fusionnée (AUD-DEP-20), script de licences sans test et exécuté seulement au build de l'image (AUD-DEP-22), aucune veille automatisée des dépendances (AUD-DEP-05). Ce changement porte la partie « gates » du sprint 1 de l'audit (`/docs/audit/2026-11-21/sprints.md`, « Sprint 1 — Remettre les gates en marche ») ; la vérification de types de `e2e/` et d'`argos-desktop`, la table des frontières de modules et le lancement de Maven par la campagne de performance relèvent du changement `verifier-types-et-frontieres-modules`.

## What Changes — Changements

- Un workflow GitHub Actions déclenché à chaque push et à chaque pull request, sur JDK 17 [DÉCISION — décision du porteur, 2026-11-24] :
  - `mvn -B clean verify` du serveur, sur un exécuteur disposant de Docker (base PostgreSQL jetable des tests) ;
  - pour `argos-ui` : `npm ci`, `npm test`, `npm run typecheck` (vue-tsc) et `npm run build` ;
  - pour `argos-desktop` : `npm ci` puis `npm run check` (syntaxe et tests) ;
  - tests des scripts (`node --test scripts/*.test.mjs "e2e/performance/*.test.mjs"`), `node scripts/verifier-docs.mjs`, `node scripts/verifier-nommage.mjs` ;
  - `npm audit` des trois projets npm (`argos-ui`, `argos-desktop`, `e2e`).
- Garde de version de JDK : le build du serveur refuse un JDK hors de la plage de production (maven-enforcer, `requireJavaVersion`), au moins en intégration continue ; toujours `clean verify` en intégration continue (AUD-TST-19).
- Couverture : JaCoCo (agent et rapport, compatibles `@QuarkusTest`) publié à chaque exécution, avec un seuil global bloquant de 85 % des lignes et 70 % des branches [DÉCISION — décision du porteur, 2026-11-24] (AUD-TST-02).
- Garde des migrations : l'intégration continue échoue si une pull request modifie, renomme ou supprime un fichier existant de `argos-server/src/main/resources/db/migration` (seuls les ajouts sont admis), règle déjà décidée par ADR-0032 (AUD-DEP-20).
- Script de licences : tests `node:test` de `scripts/verifier-licences-serveur.mjs` sur des inventaires factices (dépendance GPL, licence absente, BOM absent, inventaire conforme) ; `mvn -P licences validate` exécuté en intégration continue quand `argos-server/pom.xml` ou le script change (AUD-DEP-22).
- Veille des dépendances : Renovate ouvre des pull requests pour Maven, les trois projets npm, les images Docker (`Dockerfile` et `compose.yaml`, variables d'image comprises) et les actions du workflow [DÉCISION — décision du porteur, 2026-11-24] (AUD-DEP-05).
- Protection de la branche `main` par le statut du workflow : réglage du dépôt GitHub, effectué par le porteur du projet.
- Aucun comportement applicatif modifié ; aucune rupture de contrat (**BREAKING** : aucun).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

Aucune — changement d'outillage sans comportement spécifié : `skip_specs: true` dans `.openspec.yaml`.

## Périmètre

### Inclus

- Fichier(s) de workflow GitHub Actions sous `.github/workflows/`, configuration de Renovate (`renovate.json`).
- `argos-server/pom.xml` : maven-enforcer, JaCoCo et son seuil.
- Script de garde des migrations (étape du workflow) et tests du script de licences (`scripts/verifier-licences-serveur.test.mjs`).
- Documentation : README (« Vérifications locales »), arc42 §8.11 et §8.7, `argos-server/README.md` (licences), registre des risques et dette (§11).

### Exclu

- Vérification de types de `e2e/` et d'`argos-desktop`, règle « tout paquet de premier niveau figure dans la table des frontières », lancement de Maven par chemin absolu dans la campagne de performance : changement `verifier-types-et-frontieres-modules` (qui ajoutera ses étapes au workflow créé ici).
- Exécution automatique de la campagne E2E Playwright (AUD-TST-20, sprint 11) : elle demande Keycloak, Electron et une pile complète.
- Montées de version elles-mêmes (Electron 45.4.x, Playwright 1.65.x, Keycloak 26.7.5 ou plus) : elles arrivent par les pull requests du robot et sont relues une à une.
- Génération de la SBOM CycloneDX de l'image en intégration continue et son import dans Argos (action secondaire d'AUD-DEP-05) : [QUESTION] Q-5.
- Retrait de `-DskipTests` du `Dockerfile` : les tests demandent Docker et restent exécutés hors de l'image (`Dockerfile:17`) ; l'intégration continue devient la barrière.
- Construction et publication d'une image par l'intégration continue : non demandée par l'audit.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Porteur du projet (mainteneur unique) | a choisi la plateforme, le robot de mises à jour et le seuil de couverture ; active lui-même la protection de branche | [DÉCISION] — décision du porteur, 2026-11-24 (Q-1 à Q-3) ; Q-4 ouverte |
| Développeur (humain ou agent) | reçoit un statut d'échec sur sa pull request au lieu de lancer six commandes à la main | [PROPOSITION] — AUD-TST-01 |
| Exploitant (NAS, PC) | ne construit plus une image à partir d'un commit non vérifié | [PROPOSITION] — AUD-TST-01, impact |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Aucun workflow, aucun hook, `core.hooksPath` vide | [EXISTANT] | `.github/` absent (vérifié à HEAD = `b142695`) ; AUD-TST-01 |
| Image construite sans tests | [EXISTANT] | `Dockerfile:37` `RUN mvn -B package -DskipTests` ; commentaire `Dockerfile:17` |
| Vérifications locales manuelles (six commandes) | [EXISTANT] | `README.md`, section « Vérifications locales » |
| Compilation Java 17, image JRE 17, aucun enforcer ni toolchains | [EXISTANT] | `argos-server/pom.xml:16` ; `Dockerfile:18`, `:45` ; AUD-TST-19 |
| Tests locaux sur JDK 24, build incrémental ayant lancé 0 test | [EXISTANT] (mesure du mainteneur, non rejouée par l'audit) | AUD-TST-19, preuve d'origine |
| Aucun JaCoCo ; couverture de référence 85,8 % des lignes, 72,0 % des branches (scm 51,8 % / 41,3 %) | [EXISTANT] (mesure de l'audit) | `argos-server/pom.xml:200` (surefire seul) ; AUD-TST-02 |
| Migration fusionnée immuable, correction par nouvelle migration | [DÉCISION] | ADR-0032 (Accepté), section « Décision » |
| Aucune garde mécanique de cette règle | [EXISTANT] | AUD-DEP-20 (statut Partiel) |
| Script de licences de 440 lignes sans test, appelé seulement par le `Dockerfile` ; profil `licences` hors de `mvn verify` | [EXISTANT] | `scripts/verifier-licences-serveur.mjs` ; `Dockerfile:33` ; `argos-server/pom.xml:221` ; AUD-DEP-22 |
| Pratique de test des scripts déjà en place | [EXISTANT] | `scripts/verifier-nommage.test.mjs` |
| Aucun Renovate ni Dependabot ; Electron 45.3.2, Playwright 1.61.1, Keycloak 26.7.4 en retard | [EXISTANT] | `argos-desktop/package.json` ; `e2e/package.json` ; `compose.yaml` ; AUD-DEP-05 |
| Dépôt hébergé sur GitHub | [EXISTANT] | `git remote -v` (`github.com`) |
| Plateforme d'intégration continue : GitHub Actions | [DÉCISION] | décision du porteur, 2026-11-24 (Q-1) |
| Seuil JaCoCo global bloquant : 85 % des lignes, 70 % des branches | [DÉCISION] | décision du porteur, 2026-11-24 (Q-2) |
| Robot de mises à jour : Renovate, qui suit aussi les images de `compose.yaml` | [DÉCISION] | décision du porteur, 2026-11-24 (Q-3) |
| Protection de `main` activée par le porteur lui-même | [DÉCISION] | décision du porteur, 2026-11-24 (Q-4, options restant à préciser) |
| Licences permissives pour les dépendances et l'outillage | [PROPOSITION] (ADR Proposé) ; contrôle [EXISTANT] | ADR-0021 (Proposé) ; `scripts/verifier-licences-serveur.mjs` |
| Critère de sortie du sprint 1 | [PROPOSITION] | `/docs/audit/2026-11-21/sprints.md`, « Sprint 1 » |

## Écarts constatés

- Les preuves de l'audit sont inchangées à `b142695` : `.github/` absent, `Dockerfile:37` (`-DskipTests`), `argos-server/pom.xml:16` et surefire seul (le bloc surefire commence désormais ligne 200, l'audit cite 199-207 : simple décalage), profil `licences` ligne 221 (l'audit cite 211-219 dans la preuve d'origine).
- Le commentaire de `application.properties` qualifie encore ADR-0032 de « proposé » alors qu'il est Accepté (AUD-DEP-20) : écart documentaire signalé, traité par une tâche.
- ADR-0032 porte le statut Accepté mais sa section « Décision » commence par « Proposition : option A » : écart de rédaction signalé, hors périmètre (registre des ADR).

## Impact

- **Composants** (noms de arc42 §5) : aucun composant applicatif ; outillage de build de `Quarkus Backend`, `Vue.js 3 SPA` et `Client desktop Electron`.
- **Contrats d'interface** : aucun.
- **Données et persistance** : aucune donnée ; garde sur le répertoire des migrations Flyway.
- **Sécurité, confidentialité, droits** : le workflow n'utilise aucun secret applicatif ; jeton GitHub en lecture seule par défaut (`permissions: contents: read`) ; `npm audit` signale les CVE des dépendances npm.
- **Exploitation et observabilité** : statut du workflow sur chaque pull request ; rapport JaCoCo publié comme artefact d'exécution.
- **Dépendances entre changes** : `verifier-types-et-frontieres-modules` ajoute ses étapes (types de `e2e/` et d'`argos-desktop`) au workflow créé ici et dépend de ce changement pour leur exécution automatique. `injecter-horloge-et-executeurs` (sprint 4) profite de la barrière pour vérifier son refactoring sans changement de comportement.
- **Documentation durable à mettre à jour** : `README.md` (« Vérifications locales »), arc42 §8.11 (Tests) et §8.7 (garde des migrations), §11 (dette : retrait de l'absence de CI), `argos-server/README.md` (licences), inventaire des diagrammes si le schéma du pipeline est versionné dans arc42. Aucune fiche de capacité ni le registre `/docs/specifications/README.md` : aucun comportement spécifié.
- **ADR** : ADR-0032 (Accepté) appliqué, inchangé ; ADR-0021 (Proposé) pour les licences des outils. Candidat à un nouvel ADR : GitHub Actions et Renovate (décisions du porteur du 2026-11-24, transverses, avec alternatives sérieuses), à consigner si le porteur le souhaite.

## Conception requise

allégée — outillage sans comportement applicatif ; la conception consigne l'enchaînement des étapes, les choix (plateforme, enforcer, seuil, garde des migrations, robot) et leurs alternatives.

## Questions ouvertes

Q-1 (GitHub Actions), Q-2 (seuil 85 % / 70 %) et Q-3 (Renovate) sont tranchées par la décision du porteur du 2026-11-24 (voir « Sources et preuves »).

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-4 | Protection de `main` (activée par le porteur lui-même) : statut obligatoire seul, ou aussi pull request obligatoire et historique linéaire ? | non : après le groupe 1 | porteur du projet |
| Q-5 | Générer la SBOM CycloneDX de l'image en intégration continue et l'importer dans Argos (dogfooding, AUD-DEP-05) dans ce changement ou plus tard ? | non | porteur du projet |
| Q-6 | maven-enforcer : plage `[17,18)` imposée partout (oblige un JDK 17 local) ou seulement en intégration continue (profil activé par une propriété) ? | non : D2 propose le profil | porteur du projet |
