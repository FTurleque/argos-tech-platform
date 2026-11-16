# Tâches : tenir le référentiel des standards et le radar des sorties

<!-- Les tâches supposent le socle argos-server et les changements dont dépend celui-ci (voir la proposition). Tests : argos-server/src/test/java/io/argos/chain/. -->

## 1. Référentiel

- [x] 1.1 Créer le référentiel par espace, ses validations et son historique — couvre RAD-01-S1, RAD-01-S2 — vérification : tests d'intégration passent
- [x] 1.2 Appliquer la gouvernance et les suggestions en attente — couvre RAD-02-S1, RAD-02-S2 — vérification : tests d'intégration passent
- [x] 1.3 Annoncer les changements de standard — couvre RAD-06-S1 — vérification : test d'intégration passe

## 2. Radar

- [x] 2.1 Créer le connecteur endoflife.date et les échéances par horizon — couvre RAD-03-S1 — vérification : tests de contrat et d'intégration passent
- [x] 2.2 Envoyer les rappels J-30, J-7, J0 — couvre RAD-04-S1 — vérification : test avec horloge simulée passe
- [x] 2.3 Créer les impacts « dette de version » — couvre RAD-05-S1 — vérification : test d'intégration passe

## 3. Documentation durable et décisions

- [x] 3.1 Mettre à jour la fiche `radar-standards`, les sections arc42 §8, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement — vérification : `openspec validate construire-radar-et-standards --strict` réussit
- [x] 4.2 Parcours : référentiel d'un espace, version en fin de support dans l'inventaire, impact « dette de version » et rappel, affichés dans le radar — vérification : scénario E2E consigné avec ses résultats

### Scénario E2E consigné (tâche 4.2)

Test : `ParcoursRadarTest.referentielInventaireDetteRappelEtRadar` (API réelle, jetons Keycloak des Dev Services, PostgreSQL jetable ; seul endoflife.date est simulé par le serveur HTTP local). Résultat : réussi (`mvn -B -f argos-server/pom.xml test -Dtest=ParcoursRadarTest`, 1 test, 0 échec, 2026-11-16).

1. L'administratrice `lina` enregistre PostgreSQL (`pkg:generic/postgresql`) en `ADOPT` 17, relié au produit `postgresql` : `200`.
2. Le projet « Portail agent » importe une SBOM avec `pkg:generic/postgresql@14.10` ; la réponse endoflife.date enregistrée fixe la fin de support de la branche 14 au jour du test.
3. La tâche quotidienne (`RappelsRadar.executer`) synchronise le produit (`A_JOUR`), crée 1 dette de version et 2 rappels J0 (un par membre).
4. Le lecteur `karim` voit l'échéance `FIN_DE_SUPPORT` 14 dans le radar à 30 jours, la dette « Portail agent » avec la version standard 17, et le rappel « Fin de support de PostgreSQL 14 aujourd'hui (…) » à l'état `ENVOYEE` dans ses alertes.

Le parcours s'arrête à l'API : `argos-ui` n'a pas d'écran du radar (écart consigné dans la fiche).

## Décisions d'implémentation

- [PROPOSITION] Statuts exposés `ADOPT`, `TRIAL`, `ASSESS`, `HOLD` partout, y compris dans le point d'extension MCP (`ReferentielStandards.Statut`), au lieu des libellés provisoires `ADOPTE`, `A_ESSAYER`, `A_EVALUER`, `A_EVITER`.
- [PROPOSITION] Correspondance technologie → produit endoflife.date saisie dans l'entrée du référentiel (`produitCycleVie`) ; le calendrier ne lit que les correspondances des espaces de l'appelant (`CalendrierSorties` reçoit les espaces).
- [PROPOSITION] Suggestion de l'IA : point d'entrée Java `ReferentielEspace.suggerer`, sans route REST, en attendant la passerelle IA.
- [PROPOSITION] Utilisateurs des projets concernés = membres de l'espace du projet ; rappels et annonces hors abonnements, canal `EMAIL` si le compte a une adresse, sinon `DESKTOP`.
- [PROPOSITION] Tâche quotidienne portée par `alerting.RappelsRadar` (cron `argos.radar.tache.cron`, 06:00) ; dépendances de modules ajoutées : `catalog` → `workspace` (contexte RLS et erreurs, `catalog.standard` seulement) et `impact` → `catalog` (ModuleBoundariesTest, arc42 §5.6).
- [PROPOSITION] Une création d'entrée n'est pas annoncée ; une échéance `CORRECTIF` apparaît dans le radar mais ne produit pas de rappel.

## Matrice de traçabilité

État au 2026-11-16 : toutes les tâches réalisées. Commande : `mvn -B -f argos-server/pom.xml test -Dtest=RadarStandardsTest,ParcoursRadarTest,OutilsConsultationMcpTest,ModuleBoundariesTest` — 38 tests, 0 échec après correction ; `RadarStandardsTest` seul : 11 tests, 0 échec.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| RAD-01 | RAD-01-S1 | Composants | 1.1 | `RadarStandardsTest.rad01S1StandardComplet` | réussi |
| RAD-01 | RAD-01-S2 | Composants | 1.1 | `RadarStandardsTest.rad01S2JustificationManquante` | réussi |
| RAD-02 | RAD-02-S1 | Composants | 1.2 | `RadarStandardsTest.rad02S1Lecteur`, `rlsDuReferentiel` (sous `argos_app`) | réussi |
| RAD-02 | RAD-02-S2 | Composants | 1.2 | `RadarStandardsTest.rad02S2SuggestionDeLIa` | réussi |
| RAD-03 | RAD-03-S1 | DC-1 | 2.1 | `RadarStandardsTest.rad03S1Horizon30Jours`, `contratConnecteurEndoflife` | réussi |
| RAD-04 | RAD-04-S1 | DC-1 | 2.2 | `RadarStandardsTest.rad04S1RappelAJ30`, `rappelsParametrablesParEspace` (Q-1) | réussi |
| RAD-05 | RAD-05-S1 | DC-1 | 2.3 | `RadarStandardsTest.rad05S1VersionPlusMaintenue` | réussi |
| RAD-06 | RAD-06-S1 | Composants | 1.3 | `RadarStandardsTest.rad06S1PassageEnHold` | réussi |
| — | parcours 4.2 | DC-1, arc42 D-17 | 4.2 | `ParcoursRadarTest.referentielInventaireDetteRappelEtRadar` | réussi |
| — | documentation | — | 3.1 | `node scripts/verifier-docs.mjs` : aucune erreur | réussi |
| — | validation | — | 4.1 | `openspec validate construire-radar-et-standards --strict` : valide | réussi |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive construire-radar-et-standards`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/construire-radar-et-standards/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
