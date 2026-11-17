# Tâches : encadrer tous les appels aux modèles d'IA par une passerelle unique

<!-- Preuves : tests exécutés par `mvn -B -f argos-server/pom.xml verify` (356 tests, 0 échec, 1 ignoré hors changement) et `npm --prefix argos-ui test` (25 tests) le 2026-11-17. L'API Ollama est simulée par un serveur HTTP local (`testsupport/StubServer`) ; aucun modèle réel ni aucune requête réseau externe. -->

## 1. Arbitrages

- [x] 1.1 Faire fixer le plafond mensuel, le quota par espace et les classes transmissibles (Q-1, Q-2, Q-4) — couvre IAG-02, IAG-03 — vérification : valeurs inscrites dans la fiche `passerelle-ia` (décidé le 2026-11-16)

## 2. Passerelle

- [x] 2.1 Créer le contrat interne, le registre de prompts versionnés et les tables de traçabilité — couvre IAG-01-S1 — vérification : test d'intégration avec fournisseur simulé passe (`PasserelleIaTest.iag01S1ResumeDAlerteTraceAvecModelePromptJetonsCoutEtEmpreinte`, `registreDesPromptsVersionnesInscritAuDemarrage`, `modeleRemplacableParTache` ; migration `V13__passerelle_ia.sql`)
- [x] 2.2 Appliquer la classification des entrées — couvre IAG-02-S1, IAG-02-S2 — vérification : tests d'intégration passent ; QS-09 exécuté (`PasserelleIaTest.iag02S1DonneeConfidentielleRefuseeSansEnvoiEtJournalisee`, `iag02S2DonneesPubliquesTransmises`, `secretsRefusesEtDonneesPersonnellesMasquees`, `contenuExterneBaliseCommeDonneeSansPouvoirFermerSaBalise`, `FiltreDonneesTest`)
- [x] 2.3 Appliquer plafond et quotas avec poursuite sans IA — couvre IAG-03-S1 — vérification : test d'intégration passe ; QS-08 exécuté (`PasserelleIaTest.iag03S1QuotaDEspaceEpuiseRefuseSansAppel`, `plafondDeLInstanceAtteintRefuseToutAppel`, `fournisseurIndisponibleOuTropLentDonneUnRefusSansException`)
- [x] 2.4 Valider les sorties par schéma — couvre IAG-04-S1 — vérification : test d'intégration passe ; QS-15 exécuté (`PasserelleIaTest.iag04S1FicheSansDateDEffetRejeteeEtJournalisee`, `qs15SortiesNonConformesRejetees`)
- [x] 2.5 Fournir la mention IA et les sources aux couches de restitution — couvre IAG-05-S1 — vérification : test E2E passe, au niveau de l'API REST authentifiée par le royaume Keycloak de test (`ParcoursIaTest.iag01S1EtIag05S1AlerteAvecResumeIaTraceMentionEtSources`, `iag05S1ResumeIaDUneActualiteWebAvecMentionEtSources`) et des tests unitaires de l'IHM (`argos-ui/tests/resume-ia.test.mjs`) ; aucun test de navigateur ; aucun outil MCP ne renvoie de contenu IA (mention MCP sans objet)

## 3. Documentation durable et décisions

- [x] 3.1 Mettre à jour la fiche `passerelle-ia`, les sections arc42 §8.8, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement — vérification : `openspec validate encadrer-appels-ia --strict` réussit
- [x] 4.2 Parcours : résumé d'un avis public (tracé), refus d'une donnée confidentielle, plafond atteint puis alerte diffusée sans résumé — vérification : scénario E2E consigné avec ses résultats (`ParcoursIaTest`, 6 tests, 0 échec : alerte avec résumé tracé dans `ia_appel`, modèle `qwen2.5:1.5b`, prompt v1, 30 + 12 jetons, coût 0 ; refus `REFUS_CLASSE` journalisé sans envoi ; quota de l'espace épuisé, alerte `ENVOYEE` avec `etat_resume_ia = PLAFOND_ATTEINT` et aucun appel au modèle ; Ollama injoignable, alerte `ENVOYEE` avec `INDISPONIBLE` ; analyse de corrélation limitée aux projets de l'espace, sans impact créé)

## Matrice de traçabilité

État au 2026-11-17 : toutes les tâches réalisées ; preuves exécutées par `mvn -B -f argos-server/pom.xml verify` et `npm --prefix argos-ui test`.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| IAG-01 | IAG-01-S1 | DC-1 | 2.1, 4.2 | `PasserelleIaTest.iag01S1ResumeDAlerteTraceAvecModelePromptJetonsCoutEtEmpreinte`, `empreinteCalculeeApresFiltrageSansDonneePersonnelleBrute` ; `ParcoursIaTest.iag01S1EtIag05S1AlerteAvecResumeIaTraceMentionEtSources` | passe |
| IAG-02 | IAG-02-S1 | DC-1 | 1.1, 2.2, 4.2 | `PasserelleIaTest.iag02S1DonneeConfidentielleRefuseeSansEnvoiEtJournalisee` ; `ParcoursIaTest.iag02S1DonneeConfidentielleRefuseeEtJournalisee` ; `FiltreDonneesTest` | passe |
| IAG-02 | IAG-02-S2 | DC-1 | 1.1, 2.2 | `PasserelleIaTest.iag02S2DonneesPubliquesTransmises` | passe |
| IAG-03 | IAG-03-S1 | DC-1 | 1.1, 2.3, 4.2 | `PasserelleIaTest.iag03S1QuotaDEspaceEpuiseRefuseSansAppel`, `plafondDeLInstanceAtteintRefuseToutAppel`, `quotaDuCompteDemandeurEpuiseRefuseSansAppel`, `reservationRendueQuandLeFournisseurNEstPasJoint` ; `ParcoursIaTest.iag03S1QuotaDEspaceEpuiseAlerteDiffuseeSansResume`, `resumeIaDUneActualiteSansIaOuAuDelaDuQuotaDuCompte` | passe |
| IAG-04 | IAG-04-S1 | DC-1 | 2.4 | `PasserelleIaTest.iag04S1FicheSansDateDEffetRejeteeEtJournalisee`, `qs15SortiesNonConformesRejetees` | passe |
| IAG-05 | IAG-05-S1 | DC-1 | 2.5 | `ParcoursIaTest.iag01S1EtIag05S1AlerteAvecResumeIaTraceMentionEtSources`, `iag05S1ResumeIaDUneActualiteWebAvecMentionEtSources` ; `argos-ui/tests/resume-ia.test.mjs` | passe (API et IHM unitaire ; MCP sans objet) |

Intégration du lot 4 (branche `lot4/integration`) : IA désactivée par défaut (`argos.ia.active=false`, `ARGOS_IA_ACTIVE`), réservation rendue quand Ollama n'est pas joint, quota journalier par compte pour les résumés d'actualité (issue `QUOTA_COMPTE`, migration V13) et résumé d'actualité gardé en mémoire (`ParcoursIaTest.iag05S1ResumeIaDUneActualiteWebAvecMentionEtSources`), empreinte calculée après filtrage, mention de l'e-mail tirée de `mentionResumeIa` (`AlertesTest`, `ParcoursIaTest`), plafond atteint entre la vérification et la demande restitué `PLAFOND_ATTEINT`.

Contrôles transverses : `PasserelleIaTest.roleApplicatifSansAucunDroitSurLesTablesDeLaPasserelle` (tables de la passerelle inaccessibles sous `argos_app`) ; `ModuleBoundariesTest` (règle `seuleLaPasserelleConnaitLeFournisseur`) passe.

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Faire valider ADR-0029 (Proposé) ; à son acceptation seulement, marquer ADR-0004 **Remplacé**.
- Mesurer sur le DS1522+ la mémoire et la latence de `qwen2.5:1.5b` (méthode de validation d'ADR-0029).
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive encadrer-appels-ia`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/encadrer-appels-ia/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
