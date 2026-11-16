# Tâches : personnaliser les profils de veille et la mise en page

<!-- Les tâches non cochées qui touchent aux équipes, rôles, signaux, impacts, fiches réglementaires ou au serveur MCP supposent le code du changement formaliser-chaine-securite-poc (module workspace, signal, impact-engine) et l'authentification de gerer-comptes-et-acces. -->

## 1. Arbitrages

- [x] 1.1 Faire trancher la portée d'Argos : outil interne ou produit ouvert à d'autres publics (Q-1) — couvre ADR-0024 point 8 — vérification : décision consignée dans la fiche `profils-veille` (produit ouvert, ADR-0025, 2026-11-16)
- [x] 1.2 Faire décider si un profil proposé par l'équipe peut être désactivé (Q-2) — couvre PRF-01 — vérification : décision inscrite dans la fiche `profils-veille` et, si elle crée une règle, exigence ajoutée au delta (décidé le 2026-11-16)
- [x] 1.3 Faire décider l'articulation entre abonnements `PERSONAL` et profils actifs (Q-3) — couvre PRF-02, ABO-04 — vérification : décision inscrite dans les fiches `profils-veille` et `abonnements-veille` (décidé le 2026-11-16)
- [x] 1.4 Faire fixer le référentiel d'accessibilité (Q-4) — couvre AFF-05 — vérification : référentiel inscrit dans la fiche `affichage-personnalise` (décidé le 2026-11-16)

## 2. Préférences et mise en page

- [x] 2.1 Écrire l'OpenAPI des préférences et du catalogue des mises en page, puis la migration des préférences dans `workspace` — couvre AFF-01, AFF-03, AFF-04 — vérification : test de contrat OpenAPI passe
- [x] 2.2 Implémenter le choix, le refus d'une mise en page inconnue et le défaut `classique` — couvre AFF-01-S1, AFF-01-S2, AFF-04-S1, AFF-04-S2 — vérification : tests d'intégration passent
- [x] 2.3 Découper `argos-ui` en couche contenu et couche mise en page, avec un test d'architecture interdisant à une mise en page d'importer le client API — couvre AFF-02 — vérification : test d'architecture front-end passe
- [ ] 2.4 Implémenter les quatre mises en page à partir des maquettes, polices embarquées et licences enregistrées — couvre AFF-01, AFF-02 — vérification : E2E « même jeu de données dans les quatre mises en page » passe (AFF-02-S1, AFF-02-S2)
- [ ] 2.5 Lire la préférence dans le client desktop — couvre AFF-03-S1 — vérification : E2E web puis desktop passe
- [ ] 2.6 Auditer l'accessibilité des quatre mises en page — couvre AFF-05-S1 — vérification : rapport d'audit versionné
- [x] 2.7 Mettre à jour la fiche `affichage-personnalise` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Profils de veille

- [x] 3.1 Définir le format du manifeste de profil et le catalogue dans `catalog` ; publier `developpeur` et `expert-metier` — couvre PRF-03-S1 — vérification : test d'intégration du catalogue passe
- [x] 3.2 Enregistrer les profils actifs dans `workspace`, avec au moins un profil actif — couvre PRF-01-S1 à PRF-01-S3 — vérification : tests d'intégration passent
- [ ] 3.3 Brancher les stratégies de rapprochement des profils dans `impact-engine` — couvre PRF-03-S2 — vérification : tests unitaires des stratégies passent
- [ ] 3.4 Restituer l'union des profils actifs, sans doublon, avec rubriques — couvre PRF-02-S1, PRF-02-S2 — vérification : tests d'intégration passent
- [ ] 3.5 Publier un profil de démonstration et vérifier l'absence d'effet sur les autres utilisateurs — couvre PRF-04-S1 — vérification : test d'intégration passe
- [ ] 3.6 Vérifier qu'aucun profil n'élargit les données visibles — couvre PRF-05-S1 — vérification : tests inter-espaces passent ; règle ArchUnit : `workspace` ne lit pas les préférences pour décider d'une autorisation
- [ ] 3.7 Appliquer les garde-fous côté serveur, dans l'API et le serveur MCP — couvre PRF-06-S1, PRF-06-S2 — vérification : tests d'intégration et de contrat MCP passent
- [x] 3.8 Mettre à jour la fiche `profils-veille` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour arc42 §5 (responsabilités de `catalog`, `workspace`, IHM), arc42 §8 (taxonomie extensible des signaux) et le glossaire (« profil de veille », « mise en page »), puis l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Faire revoir ADR-0023 et ADR-0024 et mettre à jour leur statut dans l'index et arc42 §9 — vérification : index ADR et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate personnaliser-profils-et-affichage --strict` réussit
- [ ] 5.2 Parcours complet : un utilisateur `developpeur` + `expert-metier` choisit la mise en page `gazette` sur le web, la retrouve sur le desktop et voit l'union de ses veilles — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16. Commandes de preuve : `mvn -f argos-server/pom.xml verify` (51 tests, 0 échec, PostgreSQL jetable par Quarkus Dev Services) et `npm --prefix argos-ui test` (7 tests, 0 échec). Chemins serveur relatifs à `argos-server/src/test/java/io/argos/`.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AFF-01 | AFF-01-S1 | DC-1 | 2.1, 2.2 | `api/preference/PreferencesResourceTest#aff01S1ChoixDUneMiseEnPagePubliee` ; contrat `api/OpenApiContractTest` | passe (serveur) ; affichage dans l'IHM non testé de bout en bout |
| AFF-01 | AFF-01-S2 | DC-1 | 2.2 | `api/preference/PreferencesResourceTest#aff01S2MiseEnPageAbsenteDuCatalogue` | passe |
| AFF-02 | AFF-02-S1 | DC-2, D1 | 2.3, 2.4 | `argos-ui/tests/architecture.test.mjs` (couche mise en page sans client API, mentions obligatoires dans chaque mise en page) | règle de couche : passe ; E2E quatre mises en page : à écrire |
| AFF-02 | AFF-02-S2 | DC-2, D1 | 2.4 | E2E quatre mises en page | à écrire ; suppose la restitution de la veille sous droits |
| AFF-03 | AFF-03-S1 | DC-1 | 2.5 | préférence lue sur le serveur : `PreferencesResourceTest#aff01S1ChoixDUneMiseEnPagePubliee` ; E2E web puis desktop | E2E à écrire ; suppose l'authentification |
| AFF-04 | AFF-04-S1 | DC-1, INV-2 | 2.2 | `api/preference/PreferencesResourceTest#aff04S1SansChoixLaMiseEnPageClassiqueSApplique` ; `argos-ui/tests/resolution.test.mjs` | passe |
| AFF-04 | AFF-04-S2 | DC-1, INV-2 | 2.2 | `api/preference/MiseEnPageRetireeTest#aff04S2MiseEnPageRetireeClassiqueEtMessage` | passe |
| AFF-05 | AFF-05-S1 | stratégie de vérification | 1.4, 2.6 | rapport d'audit RGAA 4.1.2 | à écrire ; référentiel fixé (RGAA 4.1.2) |
| PRF-01 | PRF-01-S1 | DC-1, INV-1 | 3.2 | `api/preference/PreferencesResourceTest#prf01S1CumulDeDeuxProfils` | passe |
| PRF-01 | PRF-01-S2 | DC-1, INV-1, D3 | 1.2, 3.2 | `api/preference/PreferencesResourceTest#prf01S2DesactivationDuDernierProfil` ; contrainte `workspace_preference_au_moins_un_profil` | passe |
| PRF-01 | PRF-01-S3 | DC-1, INV-2 | 3.2 | `api/preference/PreferencesResourceTest#prf01S3ProfilAbsentDuCatalogue` | passe |
| PRF-02 | PRF-02-S1 | DC-2 | 1.3, 3.4 | test d'intégration | à écrire ; suppose signaux et impacts |
| PRF-02 | PRF-02-S2 | DC-2 | 3.4 | test d'intégration | à écrire ; suppose signaux et impacts |
| PRF-03 | PRF-03-S1 | DC-1, D2 | 3.1 | `api/catalogue/CatalogueResourceTest#prf03S1LeCatalogueInitialListeDeveloppeurEtExpertMetier` ; `catalog/profil/CatalogueProfilsTest` | passe |
| PRF-03 | PRF-03-S2 | DC-2, D2 | 3.3 | tests unitaires des stratégies | à écrire ; suppose `impact-engine` et l'inventaire |
| PRF-04 | PRF-04-S1 | D2 | 3.5 | `api/catalogue/ProfilDemonstrationTest` (profil publié, profils actifs inchangés) ; `catalog/profil/CatalogueProfilsTest#ajouterUnProfilNeModifiePasLesAutres` | partie catalogue : passe ; veille inchangée : à écrire |
| PRF-05 | PRF-05-S1 | DC-1, INV-3 | 3.6 | règle ArchUnit `architecture/PreferencesIsolationTest` ; tests inter-espaces | règle : passe ; tests inter-espaces à écrire (RBAC + RLS absents) |
| PRF-06 | PRF-06-S1 | DC-2 | 3.7 | tests d'intégration et de contrat MCP | à écrire ; suppose fiches réglementaires et serveur MCP |
| PRF-06 | PRF-06-S2 | DC-2 | 3.7 | tests d'intégration | à écrire |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive personnaliser-profils-et-affichage`).
- Après archivage, vérifier que la section Purpose des nouvelles specs principales contient le lien vers la fiche durable.
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/personnaliser-profils-et-affichage/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
