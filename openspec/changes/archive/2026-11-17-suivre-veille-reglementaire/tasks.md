# Tâches : suivre les évolutions réglementaires avec validation par un expert

<!-- Implémentation sur la branche lot5/integration (2026-11-17). Preuves : matrice ci-dessous. -->

## 1. Arbitrages

- [x] 1.1 Faire vérifier les conditions d'usage de l'API Légifrance pour un produit diffusé (Q-1) — couvre REG-01 — vérification : conclusion inscrite dans la fiche `veille-reglementaire` (décidé le 2026-11-17 : LEGI/JORF sous Licence Ouverte)

## 2. Collecte et qualification

- [x] 2.1 Créer le connecteur des archives JORF de la DILA (sans API PISTE ni repli, selon Q-1) et l'unicité par identifiant officiel — couvre REG-01-S1, REG-01-S2 — vérification : tests de contrat et de rejeu passent (`LecteurJorfTest` 5 tests, `CollecteReglementaireTest` 7 tests)
- [x] 2.2 Créer le dictionnaire métier par espace et la qualification déterministe — couvre REG-02-S1 — vérification : tests unitaires passent (`QualificationEtDatesTest`)
- [x] 2.3 Proposer un domaine par l'IA en cas de doute, marqué — couvre REG-02-S2 — vérification : test d'intégration passe (`VeilleReglementaireTest.reg02S1Reg02S2Reg04S1QualificationPuisFichesPrepareesParLIa`)
- [x] 2.4 Extraire les dates de publication et d'effet — couvre REG-03-S1, REG-03-S2 — vérification : tests unitaires passent (`QualificationEtDatesTest`)

## 3. Fiches, validation et suivi

- [x] 3.1 Préparer la fiche via la passerelle IA — couvre REG-04-S1 — vérification : test d'intégration passe (`VeilleReglementaireTest`, API Ollama simulée)
- [x] 3.2 Implémenter la validation, l'historique et la visibilité — couvre REG-05-S1, REG-05-S2 — vérification : tests d'intégration passent (`VeilleReglementaireTest`, dont `rlsSousArgosApp`)
- [x] 3.3 Implémenter le cycle de vie et ses transitions — couvre REG-06-S1, REG-06-S2 — vérification : tests unitaires passent (`QualificationEtDatesTest.reg06S1TransitionValide`, `reg06S2TransitionInvalide`)
- [x] 3.4 Calendrier, rappels et lien vers un outil de suivi — couvre REG-07-S1, REG-08-S1 — vérification : tests d'intégration passent (`VeilleReglementaireTest`, API GitHub simulée)

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour la fiche `veille-reglementaire`, les sections arc42 §6, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur (sur les fichiers versionnés ; voir « Vérifications »)
- [x] 4.2 Mettre à jour ADR-0018 (expert métier, domaines par espace) par un nouvel ADR au statut Proposé — vérification : index `/docs/architecture/adr/README.md` et arc42 §9 synchronisés (ADR-0030, Proposé)

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate suivre-veille-reglementaire --strict` réussit
- [x] 5.2 Parcours : décret de test collecté, rattaché au domaine, fiche préparée, corrigée et validée par l'expert, visible d'un utilisateur du domaine et via MCP — vérification : scénario E2E consigné avec ses résultats (`ParcoursReglementaireTest`, voir ci-dessous)

## Matrice de traçabilité

État au 2026-11-17 : tous les scénarios sont couverts par un test qui passe (commande et résultat : « Vérifications » ci-dessous). Archives JORF, API Ollama et API GitHub simulées par le serveur HTTP local (`StubServer`) ; aucune requête externe.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| REG-01 | REG-01-S1 | DC-1 | 1.1, 2.1 | `regintel/CollecteReglementaireTest.reg01S1RejeuSansDoublon`, `archivePublieeApresLePassageDeLaCollecteLueAuPassageSuivant` ; `ingestion/legi/LecteurJorfTest` ; `chain/VeilleReglementaireTest.rejeuDUnJourPrepareLesFichesManquantes` | passe |
| REG-01 | REG-01-S2 | DC-1 | 1.1, 2.1 | `regintel/CollecteReglementaireTest.reg01S2SourceIndisponiblePuisRepriseDuJourManque`, `archiveEnErreurPermanenteEcarteeSansBloquerLesJoursSuivants`, `jourEnEchecRepeteAbandonnePuisRejoue`, `jourEnEchecHorsDeLaFenetreDeRepriseAbandonne` | passe |
| REG-02 | REG-02-S1 | DC-1 | 2.2 | `regintel/QualificationEtDatesTest.reg02S1CodeSuiviModifieRattacheAuDomaineCarriere` ; `chain/VeilleReglementaireTest.reg02S1Reg02S2Reg04S1QualificationPuisFichesPrepareesParLIa` (aucun appel au modèle pour le domaine) | passe |
| REG-02 | REG-02-S2 | DC-1 | 2.3 | `chain/VeilleReglementaireTest.reg02S1Reg02S2Reg04S1QualificationPuisFichesPrepareesParLIa` (origine `IA`, confiance 72, fiche en attente) | passe |
| REG-03 | REG-03-S1 | DC-1 | 2.4 | `regintel/QualificationEtDatesTest.reg03S1DateExtraiteDistincteDeLaPublication` ; `CollecteReglementaireTest.reg01S1RejeuSansDoublon` | passe |
| REG-03 | REG-03-S2 | DC-1 | 2.4 | `regintel/QualificationEtDatesTest.reg03S2DateAbsente` ; `VeilleReglementaireTest` (« date à confirmer ») | passe |
| REG-04 | REG-04-S1 | DC-1 | 3.1 | `chain/VeilleReglementaireTest.reg02S1Reg02S2Reg04S1QualificationPuisFichesPrepareesParLIa` | passe |
| REG-05 | REG-05-S1 | DC-1, DC-2 | 3.2 | `chain/VeilleReglementaireTest.reg05S1Reg05S2CorrectionValidationHistoriqueEtVisibilite` | passe |
| REG-05 | REG-05-S2 | DC-1, DC-2 | 3.2 | `chain/VeilleReglementaireTest.reg05S1Reg05S2CorrectionValidationHistoriqueEtVisibilite`, `rlsSousArgosApp` (rattachement réservé aux experts de la fiche, fiche décidée figée, décision au compte courant), `correctionDesDomainesRetireAussiUnDomaineDontLExpertNEstPasExpert` ; `mcp/ReglementaireMcpTest` (dont `fichesEnAttenteNEvincentPasLesFichesValidees`) | passe |
| REG-06 | REG-06-S1 | DC-2 | 3.3 | `regintel/QualificationEtDatesTest.reg06S1TransitionValide` ; `chain/VeilleReglementaireTest.reg06Reg08S1CycleDeVieEtDemandeDEvolution` | passe |
| REG-06 | REG-06-S2 | DC-2 | 3.3 | `regintel/QualificationEtDatesTest.reg06S2TransitionInvalide` ; `chain/VeilleReglementaireTest.reg06Reg08S1CycleDeVieEtDemandeDEvolution` | passe |
| REG-07 | REG-07-S1 | DC-1 | 3.4 | `chain/VeilleReglementaireTest.reg07S1RappelAvantEcheanceEtCalendrier` (dont rappel manqué rattrapé une fois) | passe |
| REG-08 | REG-08-S1 | DC-2 | 3.4 | `chain/VeilleReglementaireTest.reg06Reg08S1CycleDeVieEtDemandeDEvolution` ; `scm/DemandesEvolutionTest` | passe |

### Parcours de bout en bout (tâche 5.2)

`chain/ParcoursReglementaireTest.decretCollecteQualifiePrepareCorrigeValideEtDiffuse`, passe :

1. collecte du 20 novembre 2026 dans l'archive JORF simulée : 2 textes nouveaux, 1 fiche préparée (le décret modifie le code général de la fonction publique, règle `CODE` du domaine « Carrière ») ;
2. fiche marquée « produit par l'IA », en attente : visible de l'experte, absente de la liste REST et de `get_regulatory_changes` pour un utilisateur du domaine ;
3. l'experte corrige la date d'effet (2027-02-01) puis valide ;
4. l'utilisateur du domaine (profil `expert-metier` actif) voit la fiche par l'API REST, avec la mention de validation et l'historique `PREPARATION`, `CORRECTION`, `VALIDATION` ;
5. `get_regulatory_changes` renvoie la fiche (référence `JORFTEXT000099000001`, publication 2026-11-20, effet 2027-02-01, résumé validé) et les garde-fous ;
6. rappel J-30 le 2027-01-02 pour l'experte et l'utilisateur du domaine.

### Vérifications

| Commande | Résultat |
|---|---|
| `mvn -B -f argos-server/pom.xml verify` | BUILD SUCCESS : 451 tests, 0 échec, 0 erreur, 1 ignoré (ignoré avant ce changement) ; dont 36 tests propres au changement et `ModuleBoundariesTest` (22) |
| `openspec validate --all --strict` | 13 éléments valides, 0 échec |
| `npm --prefix argos-ui ci && npm --prefix argos-ui test && npm --prefix argos-ui run build` | 25 tests, 0 échec ; build réussi (IHM non modifiée) |
| `npm --prefix argos-desktop ci && npm --prefix argos-desktop test` | 22 tests, 0 échec (desktop non modifié) |
| `node scripts/verifier-docs.mjs` | « Résultat : aucune erreur » sur une copie des fichiers versionnés de la branche (56 blocs Mermaid) ; le dossier non versionné `docs/audit/2026-11-17/`, apparu dans le dépôt de travail pendant la session et étranger à ce changement, porte à lui seul 36 erreurs d'identifiants non définis |

### Décisions prises sans arbitrage ([PROPOSITION])

- Expert métier : membre CONTRIBUTEUR ou ADMINISTRATEUR désigné par un administrateur pour un domaine (table `expert_domaine`) ; son statut cesse dès qu'il quitte l'espace ou devient lecteur.
- Fiches validées lisibles de tous les membres de l'espace ; rappels adressés aux experts des domaines et aux membres dont le profil `expert-metier` est actif (« utilisateurs du domaine »).
- Proposition IA d'un domaine limitée aux natures `LOI`, `ORDONNANCE`, `DECRET`, `ARRETE` et à 20 propositions par espace et par collecte (charge du modèle local).
- Une fiche validée ou rejetée n'est plus corrigée, ce que la base garantit aussi sous `argos_app` ; la validation exige un résumé et au moins un domaine.
- Demande d'évolution associée seulement à un texte « impact produit » ou au-delà ; forge injoignable : lien enregistré, état non vérifié.
- Chaque collecte relit les trois derniers jours (première collecte comprise) et ne télécharge que les archives non lues ; reprise des jours manqués sur 31 jours au plus ; jour abandonné après cinq échecs consécutifs de ses archives (une source injoignable ne compte pas) ou hors de la fenêtre de reprise ; archive en erreur permanente écartée ; le rejeu d'un jour par l'administrateur plateforme prépare les fiches manquantes.
- Rappel manqué le jour exact rattrapé dans les trois jours suivants, une seule fois ; lien du rappel vers le texte officiel tant que l'IHM n'a pas d'écran réglementaire.
- Date de dernière mise à jour des données (Licence Ouverte) : date de l'archive DILA du texte, à défaut date de collecte.
- Archives LEGI non lues : les liens « version consolidée » pointent vers Légifrance (`/loda/id/…`).

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive suivre-veille-reglementaire`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/archive/2026-11-17-suivre-veille-reglementaire/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
