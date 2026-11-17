# Tâches : personnaliser les profils de veille et la mise en page

<!-- Le code de ce changement est intégré avec celui de formaliser-chaine-securite-poc (signal, impact-engine, inventaire, abonnements) et de gerer-comptes-et-acces (authentification OIDC, comptes, espaces, RLS). Les tâches non cochées attendent les fiches réglementaires (regintel), le serveur MCP, une IHM authentifiée, des E2E ou une revue humaine. -->

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
  - Implémenté le 2026-11-17 : modèle commun `argos-ui/src/mises-en-page/modele.ts`, quatre mises en page, sélecteur `MiseEnPage.vue` raccordé à l'aperçu d'`AffichageView` (jeu d'exemple), polices des maquettes embarquées (`@fontsource/*`, OFL-1.1) et enregistrées dans `argos-ui/NOTICE`. `npm --prefix argos-ui test` : 135 tests, 0 échec, dont `tests/mises-en-page.test.mjs` (mêmes faits, liens et mentions dans les quatre mises en page sur un même jeu, rendu côté serveur) ; banc Playwright `npm --prefix e2e run test:accessibilite` : 29 tests, 0 échec, dont la comparaison des faits dans le navigateur.
  - Case non cochée : l'E2E exigé, sur la veille d'un utilisateur réel avec un impact non autorisé (AFF-02-S1, AFF-02-S2), n'est pas exécuté ; il suppose l'écran de veille raccordé à `GET /api/v1/me/veille`.
- [ ] 2.5 Lire la préférence dans le client desktop — couvre AFF-03-S1 — vérification : E2E web puis desktop passe
  - Partie web exécutée avec succès le 2026-11-17 : `npm --prefix e2e test`, parcours `e2e/tests/affichage.spec.ts` (connexion d'`alice`, « gazette » choisie dans `#/affichage`, retrouvée cochée après rechargement dans une nouvelle session, préférence `gazette` relue par l'API) ; rapport [`e2e/resultats/rapport-2026-11-17.json`](/e2e/resultats/rapport-2026-11-17.json), capture [`e2e/resultats/mise-en-page-gazette-web.png`](/e2e/resultats/mise-en-page-gazette-web.png).
  - Partie desktop non exécutée : binaire Electron absent de `argos-desktop/node_modules/electron/dist` : le script d'installation du paquet `electron` est bloqué par la politique npm `allow-scripts` du poste, et Playwright `_electron` ne peut pas lancer l'application sans lui.
- [x] 2.6 Auditer l'accessibilité des quatre mises en page — couvre AFF-05-S1 — vérification : rapport d'audit versionné
  - Rapport du 2026-11-17 : [`docs/architecture/quality/audit-accessibilite-mises-en-page.md`](/docs/architecture/quality/audit-accessibilite-mises-en-page.md) ; treize écarts trouvés et corrigés ; restitution par lecteur d'écran, zoom et espacement du texte à vérifier par le référent accessibilité.
- [x] 2.7 Mettre à jour la fiche `affichage-personnalise` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Profils de veille

- [x] 3.1 Définir le format du manifeste de profil et le catalogue dans `catalog` ; publier `developpeur` et `expert-metier` — couvre PRF-03-S1 — vérification : test d'intégration du catalogue passe
- [x] 3.2 Enregistrer les profils actifs dans `workspace`, avec au moins un profil actif — couvre PRF-01-S1 à PRF-01-S3 — vérification : tests d'intégration passent
- [x] 3.3 Brancher les stratégies de rapprochement des profils dans `impact-engine` — couvre PRF-03-S2 — vérification : tests unitaires des stratégies passent
- [ ] 3.4 Restituer l'union des profils actifs, sans doublon, avec rubriques — couvre PRF-02-S1, PRF-02-S2 — vérification : tests d'intégration passent
  - Aucun parcours E2E ne prouve l'union le 2026-11-17 : `argos-ui` n'affiche pas `GET /api/v1/me/veille` (l'aperçu des mises en page utilise des données d'exemple) et n'offre aucun écran de choix des profils ; les motifs de la note sous la matrice restent valables.
- [x] 3.5 Publier un profil de démonstration et vérifier l'absence d'effet sur les autres utilisateurs — couvre PRF-04-S1 — vérification : test d'intégration passe
- [x] 3.6 Vérifier qu'aucun profil n'élargit les données visibles — couvre PRF-05-S1 — vérification : tests inter-espaces passent ; règle ArchUnit : `workspace` ne lit pas les préférences pour décider d'une autorisation
- [x] 3.7 Appliquer les garde-fous côté serveur, dans l'API et le serveur MCP — couvre PRF-06-S1, PRF-06-S2 — vérification : tests d'intégration et de contrat MCP passent
- [x] 3.8 Mettre à jour la fiche `profils-veille` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 3.9 Ajouter à la veille par profils (`GET /api/v1/me/veille`) les sources des abonnements `PERSONAL` de l'appelant, dans la limite de ses droits (décision Q-3) — couvre PRF-02, ABO-04 — vérification : test d'intégration passe

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour arc42 §5 (responsabilités de `catalog`, `workspace`, IHM), arc42 §8 (taxonomie extensible des signaux) et le glossaire (« profil de veille », « mise en page »), puis l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Faire revoir ADR-0023 et ADR-0024 et mettre à jour leur statut dans l'index et arc42 §9 — vérification : index ADR et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate personnaliser-profils-et-affichage --strict` réussit
- [ ] 5.2 Parcours complet : un utilisateur `developpeur` + `expert-metier` choisit la mise en page `gazette` sur le web, la retrouve sur le desktop et voit l'union de ses veilles — vérification : scénario E2E consigné avec ses résultats
  - Non exécutable le 2026-11-17 : seul le choix de « gazette » sur le web est exécuté (tâche 2.5) ; il manque l'affichage de la veille par profils dans `argos-ui` (tâche 2.4), un écran ou un jeu de données activant les deux profils et le binaire Electron pour la partie desktop.

## Matrice de traçabilité

État au 2026-11-16, code intégré avec `formaliser-chaine-securite-poc` et `gerer-comptes-et-acces`. Commandes de preuve : `mvn -B -f argos-server/pom.xml verify` (164 tests, 0 échec, PostgreSQL et Keycloak jetables par Quarkus Dev Services, jetons réels) et `npm --prefix argos-ui test` (10 tests, 0 échec). Chemins serveur relatifs à `argos-server/src/test/java/io/argos/`. La clé des préférences est l'identifiant du compte Argos relié au sujet OIDC (migration `V7__preferences_utilisateur.sql`). Tâches 3.4, 3.5 et 3.7 (2026-11-17) : veille par profils `GET /api/v1/me/veille` (`io.argos.api.veille`) ; `mvn -B -f argos-server/pom.xml verify` : 469 tests, 0 échec, 1 ignoré.

Tâche 3.4 non cochée, bien que ses tests d'intégration réussissent : (1) PRF-02-S1 décrit un même élément qui relève de `developpeur` et d'`expert-metier` ; aucun élément réel ne relève des deux, si bien que ce cas n'est prouvé avec ces deux manifestes qu'au niveau unitaire (`UnionProfilsTest`), et de bout en bout seulement avec le profil de test `demonstration` (`ProfilDemonstrationTest`) ; (2) la décision Q-3 (une source d'abonnement `PERSONAL` s'ajoute à l'union des profils actifs), prise sur le périmètre de PRF-02, n'est pas appliquée par `GET /api/v1/me/veille` (tâche 3.9). La case attend la tâche 3.9 et l'acceptation de la preuve de PRF-02-S1 par le porteur.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AFF-01 | AFF-01-S1 | DC-1 | 2.1, 2.2 | `api/preference/PreferencesResourceTest#aff01S1ChoixDUneMiseEnPagePubliee` ; contrat `api/OpenApiContractTest` | passe (serveur) ; affichage dans l'IHM non testé de bout en bout |
| AFF-01 | AFF-01-S2 | DC-1 | 2.2 | `api/preference/PreferencesResourceTest#aff01S2MiseEnPageAbsenteDuCatalogue` | passe |
| AFF-02 | AFF-02-S1 | DC-2, D1, D4 | 2.3, 2.4 | `argos-ui/tests/architecture.test.mjs` (couche mise en page sans client API, mentions obligatoires dans chaque mise en page) ; `argos-ui/tests/liens.test.mjs` (lien de source limité à `http`/`https`) ; `argos-ui/tests/mises-en-page.test.mjs` (mêmes faits, liens et mentions IA dans les quatre mises en page, jeu d'exemple) ; banc `e2e/accessibilite/mises-en-page.spec.ts` (comparaison dans Chromium) | passe (2026-11-17) ; E2E sur la veille réelle : à écrire |
| AFF-02 | AFF-02-S2 | DC-2, D1 | 2.4 | E2E quatre mises en page | à écrire ; suppose la restitution de la veille sous droits |
| AFF-03 | AFF-03-S1 | DC-1 | 2.5 | préférence lue sur le serveur, rattachée au compte : `PreferencesResourceTest#aff01S1ChoixDUneMiseEnPagePubliee` ; E2E web `e2e/tests/affichage.spec.ts` | E2E web passe (2026-11-17) ; E2E desktop non exécuté (binaire Electron absent) |
| AFF-04 | AFF-04-S1 | DC-1, INV-2 | 2.2 | `api/preference/PreferencesResourceTest#aff04S1SansChoixLaMiseEnPageClassiqueSApplique` ; `argos-ui/tests/resolution.test.mjs` | passe |
| AFF-04 | AFF-04-S2 | DC-1, INV-2 | 2.2 | `api/preference/MiseEnPageRetireeTest#aff04S2MiseEnPageRetireeClassiqueEtMessage` | passe |
| AFF-05 | AFF-05-S1 | stratégie de vérification, D5 | 1.4, 2.6 | [rapport d'audit RGAA 4.1.2](/docs/architecture/quality/audit-accessibilite-mises-en-page.md) ; `argos-ui/tests/contrastes.test.mjs` (palette sous le seuil refusée) ; `e2e/accessibilite/mises-en-page.spec.ts` ; rapport [`e2e/resultats/accessibilite/rapport-2026-11-17.json`](/e2e/resultats/accessibilite/rapport-2026-11-17.json) | passe (2026-11-17) ; lecteur d'écran non vérifié ; la publication au catalogue du serveur n'est pas bloquée par ce contrôle |
| PRF-01 | PRF-01-S1 | DC-1, INV-1 | 3.2 | `api/preference/PreferencesResourceTest#prf01S1CumulDeDeuxProfils` ; profil `developpeur` activé à la création d'un compte : `#laCreationDUnCompteActiveLeProfilDeveloppeur` | passe |
| PRF-01 | PRF-01-S2 | DC-1, INV-1, D3 | 1.2, 3.2 | `api/preference/PreferencesResourceTest#prf01S2DesactivationDuDernierProfil` | passe ; la contrainte `workspace_preference_au_moins_un_profil` n'est exercée par aucun test (le service refuse l'ensemble vide avant la base) |
| PRF-01 | PRF-01-S3 | DC-1, INV-2 | 3.2 | `api/preference/PreferencesResourceTest#prf01S3ProfilAbsentDuCatalogue` | passe |
| PRF-02 | PRF-02-S1 | DC-2 | 1.3, 3.4 | `api/veille/VeilleProfilsTest#prf02UnionDesDeuxProfilsSansDoublon` (developpeur + expert-metier : avis et fiche validée restitués une fois chacun, rubriques `securite` et `reglementation`) ; `api/veille/UnionProfilsTest#prf02S1ElementCommunADeuxProfils`, `#prf02S1MemeElementAtteintParDeuxCategories` (manifestes publiés, élément de deux catégories) ; `api/catalogue/ProfilDemonstrationTest#prf02S1ElementCommunADeuxProfilsActifs` (même avis rangé par `developpeur` et `demonstration` : une occurrence, deux rubriques) | passe ; aucun élément réel ne relève à la fois de `developpeur` et d'`expert-metier` dans le catalogue publié |
| PRF-02 | PRF-02-S2 | DC-2 | 3.4 | `api/veille/VeilleProfilsTest#prf02S2ElementDUnProfilInactif`, `#prf06S2FicheValidee` (fin) ; `api/veille/UnionProfilsTest#prf02S2ElementDUnProfilInactif` | passe |
| PRF-03 | PRF-03-S1 | DC-1, D2 | 3.1 | `api/catalogue/CatalogueResourceTest#prf03S1LeCatalogueInitialListeDeveloppeurEtExpertMetier` ; `catalog/profil/CatalogueProfilsTest` | passe |
| PRF-03 | PRF-03-S2 | DC-2, D2 | 3.3 | `chain/MatchRulesTest` (stratégie `purl-plage-versions` = règle PURL + plage de versions d'`impact-engine`) ; `chain/ProfilsDeVeilleTest#lesStrategiesDuProfilDeveloppeurSontImplementeesParLeMoteurDImpact`, `#prf03S2RapprochementPropreAuProfilSansIa` (IA indisponible, niveau `EXACT`, stratégie dans la provenance) | passe pour `developpeur` ; la stratégie `domaine-metier-nor` d'`expert-metier` n'a pas d'implémentation (`regintel` absent) |
| PRF-04 | PRF-04-S1 | D2 | 3.5 | `api/catalogue/ProfilDemonstrationTest` (profil publié ; profils actifs inchangés ; `#prf04S1LaVeilleDesAutresUtilisateursEstInchangee` : veille des utilisateurs `developpeur`, `expert-metier` et des deux sans élément, rubrique ni mention du profil de démonstration, qui range pourtant les avis de sécurité) ; `api/veille/UnionProfilsTest#prf04SeulsLesProfilsActifsComptent` ; `catalog/profil/CatalogueProfilsTest#ajouterUnProfilNeModifiePasLesAutres` | passe ; la publication se fait par configuration (redémarrage) : la veille, catalogue enrichi, est comparée à celle que définissent les seuls profils actifs |
| PRF-05 | PRF-05-S1 | DC-1, INV-3 | 3.6 | `chain/ProfilsDeVeilleTest#prf05S1ActivationDUnProfilSansDroitSurLesProjets` (jetons réels, RLS sous `argos_app` : aucun impact, inventaire ni espace B restitué après activation de `developpeur` puis `expert-metier` ; appartenances inchangées) ; règle ArchUnit `architecture/PreferencesIsolationTest` | passe |
| PRF-06 | PRF-06-S1 | DC-2 | 3.7 | `api/veille/VeilleProfilsTest#prf06S1FicheNonValideeNiVeilleNiMcp` (fiche en attente absente de la veille et de `get_regulatory_changes`, pour un lecteur et pour l'expert qui la voit dans la file de relecture ; jetons réels, RLS sous `argos_app`) ; `mcp/ReglementaireMcpTest` ; `api/veille/UnionProfilsTest#prf06S1ElementNonValideDUnProfilAGardeFou` | passe |
| PRF-06 | PRF-06-S2 | DC-2 | 3.7 | `api/veille/VeilleProfilsTest#prf06S2FicheValidee` (validation par l'expert via l'API, puis fiche restituée avec `mentionValidation` dans la veille d'un utilisateur `expert-metier` et par MCP) ; contrat MCP `#contratMcpMentionDeValidation` (schéma de sortie) ; contrat OpenAPI `api/OpenApiContractTest#laVeilleParProfilsExposeSesChamps` | passe ; `argos-ui` n'affiche pas encore `GET /api/v1/me/veille` (mises en page : tâche 2.4) |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive personnaliser-profils-et-affichage`).
- Après archivage, vérifier que la section Purpose des nouvelles specs principales contient le lien vers la fiche durable.
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/personnaliser-profils-et-affichage/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
