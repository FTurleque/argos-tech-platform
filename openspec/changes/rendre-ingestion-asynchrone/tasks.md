# Tâches : rendre l'ingestion asynchrone

## 1. Fondations techniques (commun, execution, observabilite)

- [ ] 1.1 `commun.http` : `ClientsHttp` (client, client sans proxy, requête avec `User-Agent`), `LectureBornee` (octets, lignes, copie vers fichier), `ReponseTropVolumineuse` ; propriété `argos.http.taille-max-octets` — design D7 — vérification : `LectureBorneeTest` passe
- [ ] 1.2 `ExecuteurBorne` : `lancer` (sans attente, erreur journalisée et comptée), `executerTous` (délai global), `inactif` ; compteur `argos_traitements_fond_echecs_total` — design D1, D9 — vérification : `ExecuteurBorneTest` passe
- [ ] 1.3 `observabilite.PublicationAsynchrone` et `ExecuteurEvenements` (corrélation propagée, exécution sur le fil émetteur si la file est pleine, compteur `argos_evenements_asynchrones_echecs_total`) — design D5 — vérification : test unitaire de la publication en échec
- [ ] 1.4 ADR-0039 (Proposé) et index (`docs/architecture/adr/README.md`, arc42 §9) — design D1, D3 à D5, D7 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Enrichissement hors du fil d'ingestion (AUD-PER-02)

- [ ] 2.1 Migration `V25__file_enrichissement.sql` (table `enrichissement_attente`, cascade sur `signal`, droits comme `enrichment`) ; propriété `vulnintel` dans `ProprieteTablesTest` et arc42 §5.3.1 — DC-4 — vérification : `ProprieteTablesTest` passe
- [ ] 2.2 `FileEnrichissement`, `StockEnrichissements`, `Enricher` (observateur qui met en file, `enrich` hors transaction, `retryMissing` qui replace en file), `TraitementEnrichissements` (`vulnintel-enrichissement`, `vulnintel-rattrapage`, `@TacheObservee`) ; propriétés `argos.enrichissement.*` — COL-04-S1 à S4 — vérification : `EnrichmentTest` passe
- [ ] 2.3 Règle ArchUnit `AppelsReseauHorsTransactionTest` et classe d'exemple — INV-4 — vérification : la règle passe sur le code et échoue sur l'exemple
- [ ] 2.4 Fiche `collecte-avis-securite.md` (COL-04, DF-COL-1) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Lancement manuel en tâche de fond et exclusivité (AUD-PER-01, AUD-PER-27)

- [ ] 3.1 `SyncStore` : `attendreReprise` (reste `RUNNING`), `reprendre` conditionnel, `abandonner` ; `startOrResume` conditionnel — COL-12-S3, INV-1, INV-2 — vérification : test de concurrence passe
- [ ] 3.2 `SourceSynchronizer.demarrer` / `poursuivre` ; `LancementSynchronisation` (exécuteur borné) ; `SourceOperations.lancer` ; `POST …/synchronizations` → 202 `SyncLaunch`, 409 `SYNCHRONISATION_EN_COURS` / `LANCEMENT_SATURE` — COL-12-S1, S2 — vérification : `LancementSynchronisationTest` passe
- [ ] 3.3 Test de concurrence : une synchronisation lancée (manuelle et planifiée) pendant l'attente de reprise reçoit « déjà en cours » ; un seul `RUNNING` — COL-12-S3 — vérification : test passe
- [ ] 3.4 `e2e/performance/mesure.mjs` adapté au 202 — vérification : `node --test e2e/performance/mesure.test.mjs`
- [ ] 3.5 Fiche `collecte-avis-securite.md` (COL-12) ; arc42 §6.3/§6.4 si la séquence change — vérification : `node scripts/verifier-docs.mjs`

## 4. Amorçage OSV par archive et lecture bornée (AUD-PER-01, AUD-PER-11)

- [ ] 4.1 `AdvisoryFeed.lire` / `Lecture` ; `SourceSynchronizer` lit par la lecture fermable — design D8 — vérification : `ResilienceTest`, `CollecteTest` passent
- [ ] 4.2 `HttpSourceClient` et `EnrichmentHttp` sur `ClientsHttp` + `LectureBornee` (index OSV lu ligne à ligne) ; plafonds `argos.sources.taille-max-octets` — vérification : test de réponse trop volumineuse
- [ ] 4.3 `OsvAdvisoryFeed` : archive `all.zip` au-delà de `argos.sources.osv.seuil-archive`, fichier temporaire borné et supprimé, repli API — vérification : `OsvArchiveTest` passe

## 5. Événements (AUD-ARC-09, AUD-ARC-19)

- [ ] 5.1 `StandardModifie`, `AnnonceStandard`, `ImpactsRecorded` publiés par `PublicationAsynchrone` et observés par `@ObservesAsync` — RAD-07-S2 à S4 — vérification : `RadarStandardsTest`, `ParcoursRadarTest`, tests d'alertes passent avec attente bornée
- [ ] 5.2 Suppression d'`AlerteEmise` et de ses cinq émissions — vérification : `grep -rn AlerteEmise argos-server/src` vide
- [ ] 5.3 Règle `ObservateursEvenementsTest` (observateurs synchrones inter-modules en liste fermée) et classe d'exemple — INV-5 — vérification : test passe
- [ ] 5.4 arc42 §8 (événements entre modules, règle 3 des frontières d'erreur), fiche `radar-standards.md` (RAD-07) — vérification : `node scripts/verifier-docs.mjs`

## 6. Clients HTTP communs (AUD-ARC-10)

- [ ] 6.1 `ForgeHttp`, `ClientEndoflife`, `FournisseurOllama`, `ArchivesJorf` sur `ClientsHttp` et `LectureBornee` — vérification : tests SCM, radar, IA, réglementaires passent
- [ ] 6.2 Règle `ClientsHttpCommunsTest` : `HttpClient.newBuilder` seulement dans `commun.http` — vérification : test passe
- [ ] 6.3 arc42 §5.3 (commun) et §8.3 — vérification : `node scripts/verifier-docs.mjs`

## 7. Collecte d'activité (AUD-PER-13)

- [ ] 7.1 `ActiviteStore.prochainesCollectes()`, `ExecuteurCollecteActivite`, `CollecteActivite` par forge — design D9 — vérification : tests d'activité passent ; test : deux forges collectées, une requête d'échéances
- [ ] 7.2 arc42 §6.2 / §8.5 (exécuteurs) — vérification : `node scripts/verifier-docs.mjs`

## 8. Vérifications d'intégration

- [ ] 8.1 `openspec validate rendre-ingestion-asynchrone --strict` réussit
- [ ] 8.2 CI locale (`.github/workflows/integration-continue.yml`) : build et tests serveur, IHM, documentation ; `archgate check`
- [ ] 8.3 Revue par le superviseur de code ; remontées corrigées

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| COL-04 | COL-04-S1 | D3, DC-2 | 2.2 | `chain/EnrichmentTest#col04S1ExploitationAvereeSignaleeParKev` | à adapter |
| COL-04 | COL-04-S2 | D3, DC-2 | 2.2 | `chain/EnrichmentTest#col04S2NvdRattacheAuSignalExistant` | à adapter |
| COL-04 | COL-04-S3 | D3, DC-2 | 2.2 | `chain/EnrichmentTest#col04S3SourceSecondaireIndisponible` | à adapter |
| COL-04 | COL-04-S4 | D3, DC-2 | 2.2 | `chain/EnrichmentTest` (réponse NVD retenue) | à écrire |
| COL-12 | COL-12-S1 | D1, DC-1 | 3.2 | `chain/LancementSynchronisationTest` | à écrire |
| COL-12 | COL-12-S2 | D1, DC-1 | 3.2 | `chain/LancementSynchronisationTest` | à écrire |
| COL-12 | COL-12-S3 | D2, DC-3 | 3.1, 3.3 | `chain/LancementSynchronisationTest` (attente de reprise) | à écrire |
| RAD-07 | RAD-07-S1 | — | — | `chain/RadarStandardsTest` (clé mal encodée) | existant |
| RAD-07 | RAD-07-S2 | D5, DC-5 | 5.1 | `chain/RadarStandardsTest#rad07S2AnnonceEnPanneApresLEnregistrement` | à adapter |
| RAD-07 | RAD-07-S3 | D5, DC-5 | 5.1 | `chain/RadarStandardsTest` (erreur imprévue comptée) | à adapter |
| RAD-07 | RAD-07-S4 | D5, DC-5 | 5.1 | `chain/RadarStandardsTest` (annonce retenue) | à écrire |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (openspec archive rendre-ingestion-asynchrone).
- Après archivage, remplacer dans les fiches les liens vers /openspec/changes/rendre-ingestion-asynchrone/ par la spec principale ou le dossier d'archive, puis exécuter node scripts/verifier-docs.mjs.
