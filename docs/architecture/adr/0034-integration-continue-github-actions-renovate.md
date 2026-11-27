---
id: ADR-0034
title: "Vérifier chaque changement par GitHub Actions et suivre les dépendances avec Renovate"
domain: architecture
rules: false
---

# ADR-0034 — Vérifier chaque changement par GitHub Actions et suivre les dépendances avec Renovate

- **Statut** : Proposé
- **Date** : 2026-11-24
- **Décideurs** : porteur du projet (choix de la plateforme, du robot de mises à jour et du seuil de couverture, 2026-11-24)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- Le dépôt est hébergé sur GitHub ; il réunit un serveur Maven/Quarkus, une IHM Vue.js 3, un client Electron, une campagne Playwright et des scripts Node.
- Les tests du serveur demandent Docker (base PostgreSQL jetable des Dev Services, [ADR-0032](0032-migrations-flyway.md)) ; l'image Docker est construite sans eux (`Dockerfile`, étage `server`).
- L'audit du 2026-11-21 relève l'absence de toute exécution automatique des tests (AUD-TST-01) et de toute veille automatisée des dépendances, images Docker comprises (AUD-DEP-05) ; le changement OpenSpec [`mettre-en-place-integration-continue`](/openspec/changes/archive/2026-11-24-mettre-en-place-integration-continue/proposal.md) en porte la correction.
- Le projet est porté par un mainteneur unique, avec des outils gratuits et des licences permissives ([ADR-0021](0021-double-licence.md), [ADR-0025](0025-produit-ouvert-projet-personnel.md)).

## Critères de décision

1. Chaque pull request et chaque push sur `main` exécutent les contrôles du README sans intervention, avec un statut visible en revue.
2. Un exécuteur dispose de Docker pour les tests d'intégration du serveur.
3. Aucune infrastructure à exploiter par le mainteneur ; coût nul.
4. Les mises à jour couvrent Maven, les trois projets npm, les images du `Dockerfile` et de `compose.yaml` (valeurs par défaut des variables d'image comprises) et les actions du workflow.

## Options considérées

### Option A — GitHub Actions et Renovate

- avantages : intégré à l'hébergeur ; protection de branche native par statut ; exécuteurs `ubuntu-latest` avec Docker ; Renovate lit les valeurs par défaut des variables d'image de `compose.yaml` par un gestionnaire `regex` ;
- inconvénients : dépendance à un service externe pour la barrière ; application Renovate à installer sur le dépôt ;
- risques : quota d'exécution d'un dépôt privé ;
- coûts/complexité : un fichier de workflow et un `renovate.json`.

### Option B — GitHub Actions et Dependabot

- avantages : robot natif, sans application tierce ;
- inconvénients : ne suit pas les valeurs par défaut des variables d'image de `compose.yaml` (`${ARGOS_KEYCLOAK_VERSION:-…}`) ;
- risques : image Keycloak en retard sans signal ;
- coûts/complexité : faibles.

### Option C — Intégration continue auto-hébergée (GitLab CI en miroir, Jenkins ou Woodpecker sur le NAS)

- avantages : indépendance vis-à-vis de GitHub ;
- inconvénients : miroir à maintenir ou serveur à exploiter ; Docker dans Docker sur le NAS ;
- risques : charge d'exploitation pour un mainteneur unique ;
- coûts/complexité : élevés.

### Option D — Hooks Git locaux seuls

- avantages : aucune dépendance externe ;
- inconvénients : contournables, invisibles en revue, absents des pull requests du robot ;
- risques : barrière non vérifiable ;
- coûts/complexité : faibles.

## Décision

Option A :

1. le workflow [`.github/workflows/integration-continue.yml`](/.github/workflows/integration-continue.yml) s'exécute à chaque pull request et à chaque push sur `main`, jeton en lecture seule, sans secret, actions épinglées par empreinte de commit ;
2. le serveur se construit sur Temurin 17, JDK de l'image d'exécution, par `mvn clean verify` avec le profil `integration-continue` (plage de JDK `[17,18)` imposée par maven-enforcer) ;
3. JaCoCo mesure la couverture des tests du serveur et fait échouer le build sous 85 % des lignes ou 70 % des branches (seuil global du porteur du projet, 2026-11-24) ; le rapport est publié à chaque exécution ;
4. `npm audit` des trois projets npm fait échouer le build à partir du niveau `high` (porteur du projet, 2026-11-24) ;
5. Renovate ouvre les pull requests de mise à jour des dépendances, des images et des actions ([`renovate.json`](/renovate.json)), trois jours au moins après la publication d'une version ; les versions majeures des images Java et Node.js suivent le workflow et ne sont pas proposées, de même que celles de `@types/node` (qui suivent la version de Node.js exécutée) ; TypeScript reste sous la version 7, avec laquelle le compilateur de Vue ne compile pas les composants ; chaque pull request passe par le même workflow et une relecture avant fusion, sans fusion automatique.

La protection de `main` par le statut du workflow est un réglage du dépôt GitHub, effectué par le porteur du projet.

## Conséquences positives

- aucun commit n'atteint `main` sans les contrôles du README, une fois la protection de branche active ;
- les tests du serveur s'exécutent sur le JDK de production ;
- les montées de version (Electron, Playwright, Keycloak, Quarkus) arrivent vérifiées, une par une.

## Conséquences négatives

- la barrière dépend de la disponibilité de GitHub Actions ;
- le job serveur dure une dizaine de minutes par exécution ;
- l'application Renovate dispose d'un accès en écriture au dépôt pour ouvrir ses branches.

## Conséquences neutres / compromis

- les commandes restent celles du README et se rejouent sur le poste ; seule la plage de JDK diffère (Java 17 ou plus en local) ;
- la campagne E2E Playwright et la campagne de performance, qui demandent une pile complète, restent hors du workflow.

## Méthode de validation

- exécution verte du workflow sur une pull request ; statut rouge sur un test, une erreur de type, une migration fusionnée modifiée ou une couverture sous le seuil ;
- premières pull requests de Renovate vérifiées par le workflow ;
- pull request en échec non fusionnable une fois la protection de branche active.

## Traçabilité

- **Exigences** : aucune (outillage) ; constats AUD-TST-01, AUD-TST-19, AUD-TST-02, AUD-DEP-20, AUD-DEP-22, AUD-DEP-05 de l'audit du 2026-11-21
- **Scénarios qualité** : —
- **Diagrammes** : DC-1 du changement [`mettre-en-place-integration-continue`](/openspec/changes/archive/2026-11-24-mettre-en-place-integration-continue/design.md)
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `.github/workflows/integration-continue.yml`, `renovate.json`, `argos-server/pom.xml`

## Notes

arc42 [§8.11](../arc42/08-concepts-transverses.md#811-tests) décrit les contrôles exécutés.
