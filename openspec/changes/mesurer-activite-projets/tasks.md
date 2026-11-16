# Tâches : mesurer l'activité factuelle des projets (Project Intelligence)

<!-- Tâches cochées après vérification réelle du 2026-11-17 : `mvn -B -f argos-server/pom.xml verify` (351 tests, 0 échec, 1 ignoré), `openspec validate mesurer-activite-projets --strict`, `node scripts/verifier-docs.mjs`. -->

## 1. Collecte

- [x] 1.1 Lire les API GitHub et GitLab en lecture seule avec gestion des quotas — couvre PIN-05-S1 — vérification : tests de contrat passent
- [x] 1.2 Agréger par projet et période sans identité de personne — couvre PIN-01-S1, PIN-03-S1 — vérification : tests d'intégration passent ; revue du schéma

## 2. Restitution

- [x] 2.1 Calculer les avancements avec dénominateur explicite — couvre PIN-02-S1, PIN-02-S2 — vérification : tests unitaires passent
- [x] 2.2 Produire la synthèse IA marquée — couvre PIN-04-S1 — vérification : test d'intégration passe

## 3. Documentation durable et décisions

- [x] 3.1 Mettre à jour la fiche `activite-projets`, les sections arc42 §6.2, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement — vérification : `openspec validate mesurer-activite-projets --strict` réussit
- [x] 4.2 Parcours : projet relié à un dépôt de test, indicateurs sur 30 jours, jalon « 27 sur 30 », synthèse marquée, aucun indicateur par personne — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-17 : toutes les tâches réalisées ; preuves issues de `mvn -B -f argos-server/pom.xml verify` (BUILD SUCCESS, 351 tests, 0 échec). Tests dans `argos-server/src/test/java/io/argos/projectintel/` ; forges GitHub et GitLab simulées par `testsupport/StubServer` (serveur HTTP local, aucune requête réseau réelle) ; synthèse IA simulée par `testsupport/SimulatedSyntheseIa`.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| PIN-01 | PIN-01-S1 | Composants, Traitements, D1 ; arc42 §6.2 | 1.2, 4.2 | `ActiviteProjetsTest.pin01S1ProjetReliéGithubIndicateursSur30Jours`, `ActiviteProjetsTest.contratGitlab`, `ParcoursActiviteTest.parcoursActiviteDUnProjet` | passe |
| PIN-02 | PIN-02-S1 | D3, DF-PIN-1 | 2.1 | `AvancementTest.pin02S1JalonAvecTotalConnu`, `ActiviteProjetsTest.pin02S1EtS2AvancementAvecDenominateurSeulement` | passe |
| PIN-02 | PIN-02-S2 | D3, DF-PIN-1 | 2.1 | `AvancementTest.pin02S2PasDeDenominateurPasDePourcentage`, `ActiviteProjetsTest.pin02S1EtS2AvancementAvecDenominateurSeulement`, `AgregationEtIdentiteTest.syntheseRefuseUnChiffreAbsentDesIndicateurs` | passe |
| PIN-03 | PIN-03-S1 | Persistance, Sécurité, D1 | 1.2 | `ActiviteProjetsTest.pin03S1AucunIndicateurParPersonne` (schéma V14 inspecté sous `argos_app`), `AgregationEtIdentiteTest.pin03S1AucuneIdentiteDansLesStructuresDeLActivite` | passe |
| PIN-04 | PIN-04-S1 | Contrats d'interface, D4 | 2.2 | `ActiviteProjetsTest.pin04S1SyntheseMarqueeEtFondeeSurLesIndicateurs` (synthèse simulée) | passe |
| PIN-05 | PIN-05-S1 | Gestion des erreurs, D2 | 1.1 | `ActiviteProjetsTest.pin05S1QuotaEpuiseCollecteReporteeEtAgeAffiche`, `ActiviteProjetsTest.contratGithubLectureSeuleSansRedirectionNiJeton`, `ActiviteProjetsTest.echecDeForgeSansJetonDansLeMotifEtCollecteIdempotente` | passe |
| Q-2 (13 mois) | — | Persistance | 1.2 | `ActiviteProjetsTest.purgeDeLHistoriqueAuDelaDeTreizeMois` | passe |
| RLS (ADR-0016) | — | Sécurité, D5 | 1.2 | `ActiviteProjetsTest.cloisonnementSousArgosAppEtRefusJournalise`, `ModuleBoundariesTest.projectintelNEmprunteQueRlsEtErreurs` | passe |

Parcours E2E (tâche 4.2) consigné le 2026-11-17 par `ParcoursActiviteTest` : projet relié au dépôt GitHub de test `demo/argos`, collecte planifiée (bilan 1 collecté, 0 reporté, 0 échec), `GET /activite?jours=30` avec un jeton Keycloak réel → période du 2026-10-19 au 2026-11-17, 3 tickets ouverts, 2 fermés, 1 fusion fusionnée, 1 pipeline réussi, 1 version, jalon « v1.0 » `27 sur 30 (90 %)`, fraîcheur `A_JOUR` ; `GET /activite/synthese` → `DISPONIBLE`, mention « produite par l'IA », renvoi aux indicateurs ; aucune identité de personne dans les réponses.

Limite : PIN-04 est prouvé avec une synthèse simulée ; aucune implémentation réelle de `SyntheseIa` n'existe tant que la passerelle IA (`encadrer-appels-ia`, modèle local Ollama) n'est pas implémentée.

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive mesurer-activite-projets`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/mesurer-activite-projets/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
