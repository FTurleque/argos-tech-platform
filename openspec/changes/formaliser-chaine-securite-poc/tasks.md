# Tâches : formaliser la chaîne sécurité du POC et la personnalisation de la veille

<!-- Aucune tâche n'est réalisée. Les tâches d'implémentation supposent l'initialisation Quarkus / PostgreSQL du POC (baseline v2.4 §6, semaine 1), hors de ce changement. -->

## 1. Arbitrages bloquants

- [x] 1.1 Faire définir les règles des niveaux `probable` et `à vérifier` (Q-2) et les reporter dans COR-02 par un MODIFIED avant archivage — vérification : décision consignée dans la fiche `correlation-impact` et `openspec validate formaliser-chaine-securite-poc --strict` réussit (décidé le 2026-11-16)
- [x] 1.2 Faire fixer le seuil d'obsolescence d'une SBOM (Q-3) — couvre INV-03, COR-06 — vérification : valeur et source inscrites dans la fiche `inventaire-sbom` (décidé le 2026-11-16)
- [x] 1.3 Faire fixer le nombre maximal de tentatives et le seuil `DEGRADED` (Q-4) — couvre COL-06, COL-07 — vérification : valeurs inscrites dans la fiche `collecte-avis-securite` (décidé le 2026-11-16)
- [x] 1.4 Faire décider qui crée un abonnement `TEAM` et si un `TEAM` obligatoire peut être quitté (Q-5) — couvre ABO-03 — vérification : décision inscrite dans la fiche `abonnements-veille` et, si elle crée une règle, exigence ajoutée au delta (décidé le 2026-11-16)
- [x] 1.5 Faire fixer les versions de schéma CycloneDX acceptées (Q-7) — couvre INV-01, INV-05 — vérification : liste inscrite dans la fiche `inventaire-sbom` (décidé le 2026-11-16)
- [x] 1.6 Faire valider ou rejeter les interprétations listées dans chaque fiche (« Interprétations introduites par un changement ») — vérification : chaque interprétation marquée validée ou retirée du delta (décidé le 2026-11-16)

## 2. Collecte des avis de sécurité

- [ ] 2.1 Créer les migrations `SOURCE`, `SIGNAL`, `SIGNAL_VERSION`, `ENRICHMENT`, `SYNC_CURSOR`, `INGESTION_FAILURE` avec la contrainte d'unicité (source, external_id) — couvre COL-02, COL-03 — vérification : test d'intégration PostgreSQL d'unicité passe
- [ ] 2.2 Implémenter l'adaptateur OSV et la normalisation en signal dans `source-ingestion` / `signal` — couvre COL-01-S1, COL-01-S2, COL-02-S1 — vérification : test de contrat sur réponses OSV enregistrées passe
- [ ] 2.3 Implémenter idempotence et historisation — couvre COL-03-S1, COL-03-S2, COL-03-S3 — vérification : tests d'intégration rejeu, modification et concurrence passent
- [ ] 2.4 Implémenter l'enrichissement NVD / KEV / EPSS / CERT-FR dans `vuln-intel` — couvre COL-04-S1 à COL-04-S3 — vérification : tests de contrat, dont source secondaire indisponible, passent
- [ ] 2.5 Implémenter curseur durable, retry borné, `Retry-After`, erreurs permanentes et état `DEGRADED` — couvre COL-05, COL-06, COL-07 — vérification : tests de résilience (429, 5xx, timeout, arrêt forcé) passent ; QS-03 et QS-07 exécutés
- [ ] 2.6 Implémenter la zone d'échec — couvre COL-02-S2, COL-08-S1 — vérification : test d'un lot contenant un avis malformé passe
- [ ] 2.7 Mettre à jour la fiche `collecte-avis-securite` (preuves, statuts) et le glossaire (« Signal de sécurité ») — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Inventaire SBOM

- [ ] 3.1 Créer les migrations `INVENTORY`, `COMPONENT` — couvre INV-01, INV-02 — vérification : test d'intégration PostgreSQL passe
- [ ] 3.2 Implémenter la récupération des SBOM et manifestes dans `scm-connector` — couvre INV-01, INV-04 — vérification : test de contrat GitLab/GitHub enregistré passe
- [ ] 3.3 Implémenter validation et import CycloneDX, transitivité et remplacement — couvre INV-01-S1, INV-01-S2, INV-02-S1, INV-05-S1 — vérification : tests avec SBOM valides, transitives et invalides passent
- [ ] 3.4 Implémenter âge, absence de SBOM et repli manifestes — couvre INV-03-S1, INV-03-S2, INV-04-S1, INV-04-S2 — vérification : tests d'intégration passent
- [ ] 3.5 Mettre à jour la fiche `inventaire-sbom` et le glossaire (« Inventaire », « Composant ») — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Corrélation d'impact

- [ ] 4.1 Créer la migration `PROJECT_IMPACT` avec unicité (signal, projet, composant) et politique RLS par espace — couvre COR-03, COR-07, COR-08 — vérification : test d'intégration unicité et RLS passe
- [ ] 4.2 Implémenter la règle PURL + plage de versions dans `impact-engine` — couvre COR-01-S1 à COR-01-S4 — vérification : tests unitaires des règles passent
- [ ] 4.3 Implémenter niveaux et confiance réduite selon les règles fixées en 1.1 et 1.2 — couvre COR-02-S1, COR-06-S1 — vérification : tests unitaires passent
- [ ] 4.4 Implémenter provenance et contrôle d'intégrité — couvre COR-03-S1, COR-03-S2 — vérification : test d'intégrité « aucun impact orphelin » passe ; QS-01 exécuté
- [ ] 4.5 Garantir l'indépendance vis-à-vis de l'IA (décision D3) — couvre COR-04-S1, COR-05-S1, COR-05-S2 — vérification : tests avec `ai-gateway` simulé indisponible ou plafonné passent ; QS-10 exécuté
- [ ] 4.6 Implémenter la consultation filtrée et la journalisation des refus — couvre COR-08-S1, COR-08-S2 — vérification : tests inter-espaces REST passent
- [ ] 4.7 Constituer le jeu de référence annoté et mesurer précision et rappel — couvre COR-01, COR-02 — vérification : rapport de mesure versionné (baseline v2.3 §6 critère 3)
- [ ] 4.8 Mettre à jour la fiche `correlation-impact` (preuves, niveaux définis) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Abonnements de veille

- [ ] 5.1 Créer la migration `SUBSCRIPTION` et la validation des attributs obligatoires — couvre ABO-01-S1, ABO-02-S1, ABO-02-S2 — vérification : tests d'API passent
- [ ] 5.2 Implémenter l'héritage du socle `TEAM` — couvre ABO-03-S1 — vérification : test d'intégration passe
- [ ] 5.3 Implémenter les abonnements `PERSONAL` et la restitution de la veille sous RLS — couvre ABO-04-S1, ABO-05-S1, ABO-05-S2 — vérification : tests inter-espaces passent
- [ ] 5.4 Ajouter la règle ArchUnit interdisant à `workspace` l'accès aux rôles (INV-D7) — couvre ABO-05 — vérification : test d'architecture passe
- [ ] 5.5 Mettre à jour la fiche `abonnements-veille` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Documentation d'architecture et décisions

- [ ] 6.1 Renommer ou annoter les participants des diagrammes D-06 à D-09 selon la table de correspondance de arc42 §6, après décision — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 6.2 Produire D-11 (déploiement cible), D-12 (frontières de confiance) et D-13 (modèle physique) à partir du schéma réellement migré, ou les retirer de l'inventaire — vérification : inventaire `/docs/architecture/diagrams/README.md` conforme aux fichiers
- [ ] 6.3 Rédiger les ADR candidats signalés en D4 (planification/jobs, outil de migration) au statut Proposé et les indexer en arc42 §9 — vérification : index ADR et arc42 §9 synchronisés

## 7. Vérifications d'intégration

- [ ] 7.1 Exécuter la chaîne avis critique de test → signal → impact sur les deux projets pilotes — couvre baseline v2.3 §6 critères 1 à 3 — vérification : scénario E2E consigné avec ses résultats
- [ ] 7.2 Valider le changement — vérification : `openspec validate formaliser-chaine-securite-poc --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur

## Matrice de traçabilité

État au 2026-11-15 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| COL-01 | COL-01-S1, COL-01-S2 | DF-COL-1, DC-1 | 2.2 | test de contrat OSV | à écrire |
| COL-02 | COL-02-S1, COL-02-S2 | DF-COL-2, DC-2 | 2.1, 2.2, 2.6 | test d'intégration | à écrire |
| COL-03 | COL-03-S1, COL-03-S2, COL-03-S3 | DF-COL-2, design « Transactions » | 2.1, 2.3 | tests rejeu / concurrence | à écrire |
| COL-04 | COL-04-S1, COL-04-S2, COL-04-S3 | DF-COL-1 | 2.4 | tests de contrat enrichissement | à écrire |
| COL-05 | COL-05-S1, COL-05-S2 | DF-COL-1, DC-3 | 2.5 | QS-03, QS-07 | à écrire |
| COL-06 | COL-06-S1, COL-06-S2, COL-06-S3 | DC-3 | 1.3, 2.5 | tests de résilience | à écrire ; seuils non fixés |
| COL-07 | COL-07-S1, COL-07-S2 | DF-COL-3 | 1.3, 2.5 | test d'état de source | à écrire ; seuil non fixé |
| COL-08 | COL-08-S1 | DF-COL-2 | 2.6 | test lot malformé | à écrire |
| INV-01 | INV-01-S1, INV-01-S2 | DF-INV-1, DC-2 | 1.5, 3.1, 3.2, 3.3 | tests d'import | à écrire |
| INV-02 | INV-02-S1 | DF-INV-1 | 3.3 | test transitive | à écrire |
| INV-03 | INV-03-S1, INV-03-S2 | DF-INV-2 | 1.2, 3.4 | test d'âge | à écrire |
| INV-04 | INV-04-S1, INV-04-S2 | DF-INV-2 | 3.2, 3.4 | test de repli | à écrire |
| INV-05 | INV-05-S1 | DF-INV-1 | 3.3 | test SBOM invalide | à écrire |
| COR-01 | COR-01-S1 à COR-01-S4 | DF-COR-1, DF-COR-2 | 4.2, 4.7 | tests unitaires, jeu de référence | à écrire |
| COR-02 | COR-02-S1 | DF-COR-3 | 1.1, 4.3 | tests unitaires | à écrire ; règles incomplètes |
| COR-03 | COR-03-S1, COR-03-S2 | DF-COR-1, DC-2 | 4.1, 4.4 | QS-01, contrôle d'intégrité | à écrire |
| COR-04 | COR-04-S1 | DF-COR-2, décision D3 | 4.5 | test IA simulée | à écrire |
| COR-05 | COR-05-S1, COR-05-S2 | DF-COR-2, décision D3 | 4.5 | QS-10 | à écrire |
| COR-06 | COR-06-S1 | DF-COR-3, DF-INV-2 | 1.2, 4.3 | tests unitaires | à écrire ; seuil non fixé |
| COR-07 | COR-07-S1 | DC-2, design « Transactions » | 4.1 | test d'unicité | à écrire |
| COR-08 | COR-08-S1, COR-08-S2 | DF-ABO-2, design « Sécurité » | 4.1, 4.6 | tests inter-espaces | à écrire |
| ABO-01 | ABO-01-S1 | DF-ABO-1 | 5.1 | test d'API | à écrire |
| ABO-02 | ABO-02-S1, ABO-02-S2 | DF-ABO-1, DC-2 | 5.1 | test d'API | à écrire |
| ABO-03 | ABO-03-S1 | DF-ABO-1 | 1.4, 5.2 | test d'intégration | à écrire ; règle TEAM incomplète |
| ABO-04 | ABO-04-S1 | DF-ABO-2 | 5.3 | test d'intégration | à écrire |
| ABO-05 | ABO-05-S1, ABO-05-S2 | DF-ABO-1, DF-ABO-2 | 5.3, 5.4 | tests inter-espaces, ArchUnit | à écrire |

## Workflow follow-up

- Basculer dans les quatre fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] (après validation des ADR et arbitrages) ou en [EXISTANT] (après preuve par test).
- Archiver le changement uniquement sur demande explicite, après revue et levée des arbitrages du groupe 1 (`openspec archive formaliser-chaine-securite-poc`).
- Après archivage, vérifier que la section Purpose des quatre specs principales contient le lien vers leur fiche durable.
- Après archivage, remplacer dans les fiches et dans `/docs/specifications/README.md` les liens vers `/openspec/changes/formaliser-chaine-securite-poc/` par les specs principales ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
