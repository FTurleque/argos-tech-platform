# Tâches : ajouter le profil de veille « artiste »

<!-- Les tâches supposent `personnaliser-profils-et-affichage` (format du manifeste et catalogue). -->

## 1. Arbitrages

- [x] 1.1 Faire choisir les sources prioritaires, le périmètre géographique et les disciplines (Q-1 à Q-3) — couvre ART-02, ART-05 — vérification : choix inscrits dans la fiche `profil-artiste` (décidé le 2026-11-16)

## 2. Pack et rapprochement

- [x] 2.1 Écrire le manifeste du pack `artiste` (rubriques, listes, garde-fous) — couvre ART-01-S1 — vérification : test du catalogue passe
- [x] 2.2 Enregistrer disciplines et territoires suivis — couvre ART-02-S1, ART-02-S2 — vérification : tests d'intégration passent
- [x] 2.3 Implémenter la stratégie discipline + territoire + date limite — couvre ART-03-S1, ART-03-S2 — vérification : tests unitaires passent
- [x] 2.4 Planifier les rappels de date limite — couvre ART-04-S1 — vérification : test avec horloge simulée passe

## 3. Sources et garde-fous

- [x] 3.1 Bloquer la collecte des sources non vérifiées — couvre ART-05-S1 — vérification : test d'intégration passe
- [x] 3.2 Appliquer le gabarit de résumé sans avis d'éligibilité — couvre ART-06-S1 — vérification : test d'intégration et revue passent

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour la fiche `profil-artiste`, les sections arc42 §5.4 (personnalisation), le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate ajouter-profil-artiste --strict` réussit
- [x] 5.2 Parcours : utilisateur développeur active le profil artiste, choisit arts visuels et Occitanie, reçoit un appel correspondant puis son rappel ; sa veille développeur est inchangée — vérification : scénario E2E consigné avec ses résultats

### Revue du gabarit de résumé (tâche 3.2)

`ResumeOpportunite` compose le résumé sans IA à partir des champs collectés : titre, organisme, critères cités tels qu'énoncés (texte brut, tronqués à 2 000 caractères avec l'indicateur `criteresTronques`), date limite ou « à confirmer », lien vers le texte officiel et mention fixe « Argos ne se prononce pas sur votre éligibilité : reportez-vous au texte officiel de l'organisme. ». Le gabarit n'a ni champ ni formule qui porte une appréciation de l'éligibilité ; aucun contenu externe n'est envoyé à un modèle. La même mention figure dans chaque rappel.

### Scénario E2E consigné (tâche 5.2)

Test : `ParcoursArtisteTest.developpeurActiveArtisteRecoitUnAppelPuisSonRappelSansChangerSaVeille` (API réelle, jetons Keycloak des Dev Services, PostgreSQL jetable ; seule l'API data.culture.gouv.fr est simulée par le serveur HTTP local). Résultat : réussi (`mvn -B -f argos-server/pom.xml test -Dtest=ProfilArtisteTest,ParcoursArtisteTest,ProfilsDeVeilleTest`, 15 tests, 0 échec, 2026-11-17).

1. L'utilisateur `hugo`, profil `developpeur` seul : ses rubriques, sa veille (`/api/v1/me/watch`), ses abonnements et le nombre de signaux sont relevés.
2. L'administratrice plateforme `alice` déclare la source data.culture.gouv.fr (simulée) et enregistre ses conditions d'usage (Licence Ouverte Etalab 2.0).
3. `hugo` active `developpeur` et `artiste`, choisit `arts-visuels` et `occitanie` : `200`.
4. La collecte enregistre l'appel « Résidence arts visuels en Occitanie » (date limite J+7) ; `hugo` le voit restitué avec sa date limite et son lien officiel.
5. La tâche quotidienne des rappels (`RappelsOpportunites.executer`, jour simulé) crée 1 rappel ; `hugo` le voit dans `/api/v1/alertes` : « Date limite dans 7 jours (…) : Résidence arts visuels en Occitanie ».
6. Les rubriques du profil développeur, la veille, les abonnements et le nombre de signaux sont identiques à l'étape 1 ; les cinq rubriques artiste s'y ajoutent.

Le parcours s'arrête à l'API : `argos-ui` n'a pas d'écran du profil artiste (écart consigné dans la fiche).

## Décisions d'implémentation

- [PROPOSITION] Objets suivis génériques par compte, profil et type d'objet (`workspace.suivi`, tables `suivi_profil` et `suivi_rappels` sous RLS) ; les valeurs sont confrontées aux listes fermées du manifeste (champ facultatif `listes`) par l'API avant l'écriture.
- [PROPOSITION] Territoires : « France entière » et les 18 régions (13 métropolitaines, 5 d'outre-mer) ; un appel `france` correspond à tout territoire suivi.
- [PROPOSITION] Un appel sans date limite lisible est restitué « date à confirmer », sans rappel ; le jour de la date limite reste ouvert.
- [PROPOSITION] Jours de rappel réglables par chaque utilisateur dans ses suivis, J-30, J-7 et J-1 par défaut (`argos.profil-artiste.rappels-jours`) ; canal `EMAIL` si le compte a une adresse, sinon `DESKTOP`, comme les rappels du radar (enregistrement partagé avec `RappelsRadar`).
- [PROPOSITION] Sources et opportunités dans le module `signal` (`signal.opportunite`), données publiques de la plateforme sans espace ; registre réservé à l'administrateur plateforme ; récupération par `ingestion.web.RecuperateurWeb` (garde SSRF de la veille web), lecture data.culture.gouv.fr par `ingestion.culture.LecteurDataCulture` ; une source `DATA_CULTURE` limitée aux hôtes de `argos.profil-artiste.data-culture.hotes`.
- [PROPOSITION] Noms des champs d'un jeu de données data.culture.gouv.fr fixés par configuration (`argos.profil-artiste.data-culture.champ.*`), à confirmer pour chaque jeu retenu.
- [PROPOSITION] Rubriques de la veille (`GET /api/v1/preferences/rubriques`) restituées dans l'ordre du catalogue, quel que soit l'ordre d'activation des profils.
- [PROPOSITION] Aucune dépendance de module ajoutée (ModuleBoundariesTest inchangé) ; `SuiviIsolationTest` vérifie que les suivis n'empruntent à `workspace` que `ContexteBase` et ne lisent aucune donnée d'autorisation.

## Matrice de traçabilité

État au 2026-11-17 : toutes les tâches réalisées. Commandes : `mvn -B -f argos-server/pom.xml test -Dtest=ManifesteArtisteTest,StrategieDisciplineTerritoireTest,LecteurDataCultureTest,CorrespondanceListesTest,RappelsOpportunitesTest,ProfilArtisteTest,SuiviIsolationTest,CatalogueResourceTest,ModuleBoundariesTest` — 56 tests, 1 échec (ordre des rubriques), corrigé ; `-Dtest=ProfilArtisteTest,ParcoursArtisteTest,ProfilsDeVeilleTest` — 15 tests, 0 échec. Suite complète `mvn -B -f argos-server/pom.xml verify` : 368 tests, 0 échec, BUILD SUCCESS.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| ART-01 | ART-01-S1 | Composants | 2.1 | `ManifesteArtisteTest` (4 tests), `CatalogueResourceTest.prf03S1LeCatalogueListeDeveloppeurExpertMetierEtArtiste`, `ProfilArtisteTest.art01S1ActivationAjouteLesCinqRubriquesArtiste` | réussi |
| ART-02 | ART-02-S1 | Composants | 1.1, 2.2 | `ProfilArtisteTest.art02S1ChoixEnregistres`, `lesSuivisSontCloisonnesParCompteSousArgosApp` (sous `argos_app`), `SuiviIsolationTest` | réussi |
| ART-02 | ART-02-S2 | Composants | 1.1, 2.2 | `ProfilArtisteTest.art02S2DisciplineHorsListeRefusee` | réussi |
| ART-03 | ART-03-S1 | DC-1 | 2.3 | `StrategieDisciplineTerritoireTest.art03S1AppelCorrespondantRestitueAvecSaDateLimite`, `ProfilArtisteTest.art03RapprochementParDisciplineTerritoireEtDateLimite` | réussi |
| ART-03 | ART-03-S2 | DC-1 | 2.3 | `StrategieDisciplineTerritoireTest.art03S2DateLimiteDepasseeNonRestituee`, `ProfilArtisteTest.art03RapprochementParDisciplineTerritoireEtDateLimite` | réussi |
| ART-04 | ART-04-S1 | DC-1 | 2.4 | `RappelsOpportunitesTest.art04S1RappelALEcheanceAvecDateLimiteEtLienOfficiel`, `lesJoursDeRappelSontParametrables` (Q-4) | réussi |
| ART-05 | ART-05-S1 | Composants | 1.1, 3.1 | `ProfilArtisteTest.art05S1SourceNonVerifieeNonCollectee`, `lesAdressesDesSourcesSontControlees`, `lesSourcesSontReserveesALAdministrateurPlateforme` | réussi |
| ART-06 | ART-06-S1 | Composants | 3.2 | `ProfilArtisteTest.art06S1ResumeSansAvisDEligibilite`, `StrategieDisciplineTerritoireTest.art06LeGabaritDeResumeNeSePrononcePasSurLEligibilite`, revue ci-dessus | réussi |
| PRF-04 | — | Composants | 5.2 | `ProfilArtisteTest.lActivationNAAucunEffetSurLesAutresUtilisateurs`, `RappelsOpportunitesTest.sansProfilActifAucunRappel` | réussi |
| — | parcours 5.2 | DC-1 | 5.2 | `ParcoursArtisteTest.developpeurActiveArtisteRecoitUnAppelPuisSonRappelSansChangerSaVeille` | réussi |
| — | documentation | — | 4.1 | `node scripts/verifier-docs.mjs` : aucune erreur | réussi |
| — | validation | — | 5.1 | `openspec validate ajouter-profil-artiste --strict` : valide | réussi |
| — | suite complète | — | — | `mvn -B -f argos-server/pom.xml verify` : 368 tests, 0 échec, 1 ignoré (`MesurePerformanceRechercheTest`, mesure à la demande), BUILD SUCCESS (2026-11-17) | réussi |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive ajouter-profil-artiste`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/ajouter-profil-artiste/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
