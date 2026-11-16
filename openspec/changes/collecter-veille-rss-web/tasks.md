# Tâches : collecter la veille RSS/Atom et les pages web surveillées

<!-- Code : argos-server (io.argos.ingestion.web, io.argos.signal.web, io.argos.impact.RapprochementActualites, io.argos.api.VeilleWebResource, migration V10). Tests : io.argos.ingestion.web.*Test, io.argos.impact.TermesTechnologieTest, io.argos.chain.VeilleWebTest, io.argos.chain.ParcoursVeilleWebTest. -->

## 1. Arbitrages

- [x] 1.1 Faire trancher services séparés ou collecteur intégré (Q-1) — couvre WEB-01, WEB-02 — vérification : choix inscrit dans la fiche `veille-rss-web` et ADR rédigé (décidé le 2026-11-16 ; ADR-0028, Proposé)

## 2. Sources et sécurité

- [x] 2.1 Valider les adresses (flux valide, plages interdites, redirections, délais) — couvre WEB-01-S2, WEB-03-S1, WEB-03-S2 — vérification : tests de sécurité passent
- [x] 2.2 Créer les sources d'espace et personnelles — couvre WEB-01-S1 — vérification : tests d'intégration passent

## 3. Collecte et restitution

- [x] 3.1 Collecter flux et pages surveillées — couvre WEB-01-S1, WEB-02-S1 — vérification : tests d'intégration passent
- [x] 3.2 Dédupliquer et citer toutes les sources — couvre WEB-05-S1 — vérification : test d'intégration passe
- [x] 3.3 Marquer le contenu externe et tester l'injection — couvre WEB-04-S1 — vérification : test de l'article piégé passe
- [x] 3.4 Rapprocher par nom au niveau « à vérifier » — couvre WEB-06-S1 — vérification : test d'intégration passe

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour la fiche `veille-rss-web`, les sections arc42 §8.3, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.2 Rédiger l'ADR du choix de collecteur (statut Proposé) — vérification : index `/docs/architecture/adr/README.md` et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate collecter-veille-rss-web --strict` réussit
- [x] 5.2 Parcours : ajout d'un flux, refus d'une adresse privée, article reçu par deux flux restitué une fois, article piégé sans effet — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : toutes les tâches réalisées ; preuves obtenues par `mvn -B -f argos-server/pom.xml verify` (PostgreSQL et Keycloak des Dev Services, jetons réels, RLS sous le rôle `argos_app`, sources simulées par le serveur HTTP local `StubServer`, aucune requête réseau externe).

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| WEB-01 | WEB-01-S1 | DC-1 | 1.1, 2.2, 3.1 | `VeilleWebTest.fluxAtomValideCreeLaSourceEtSesArticlesApparaissent` ; `ParcoursVeilleWebTest.parcoursVeilleWeb` | passe |
| WEB-01 | WEB-01-S2 | DC-1, DF-WEB-1 | 1.1, 2.1 | `VeilleWebTest.adresseSansFluxRefuseeSansCreerDeSource` ; `LecteursTest.pageHtmlOuDoctypeRefusesCommeFlux` | passe |
| WEB-02 | WEB-02-S1 | DC-1 | 1.1, 3.1 | `VeilleWebTest.pageSurveilleeModifieeCreeUnElementDateAvecApercu` ; `LecteursTest.pageLueParBlocsEtApercuDuChangement` | passe |
| WEB-03 | WEB-03-S1 | DC-1, DF-WEB-1 | 2.1 | `VeilleWebTest.adressePriveeRefuseeSansRequete` ; `RecuperateurWebTest.adressePriveeRefuseeSansAucuneRequete` ; `GardeAdressesTest` (29 cas) | passe |
| WEB-03 | WEB-03-S2 | DC-1 | 2.1 | `VeilleWebTest.redirectionVersAdresseLocaleMetLaSourceEnErreur` ; `RecuperateurWebTest.redirectionVersLaBoucleLocaleInterrompue` ; durée bornée : `VeilleWebTest.recuperationTropLongueInterrompue`, `RecuperateurWebTest.delaiEtTailleBornes` | passe |
| WEB-04 | WEB-04-S1 | DC-1 | 3.3 | `VeilleWebTest.articlePiegeCiteCommeDonneeSansEffetSurLesImpacts` (résumé extractif ; passerelle IA réelle absente, voir écart) | passe |
| WEB-05 | WEB-05-S1 | DC-1 | 3.2 | `VeilleWebTest.memeArticleDansDeuxFluxRestitueUneFoisAvecSesDeuxSources` ; `ParcoursVeilleWebTest.parcoursVeilleWeb` | passe |
| WEB-06 | WEB-06-S1 | DC-1 | 3.4 | `VeilleWebTest.articleSurUneTechnologieUtiliseeRapprocheAVerifierSansAlerte`, `sourcePersonnelleNeRapprocheAucunProjet` ; `TermesTechnologieTest` | passe |

Preuves complémentaires : cloisonnement en base sous `argos_app` (`VeilleWebTest.cloisonnementEnBaseSousLeRoleApplicatif`), droits d'espace et journal des refus (`sourceEspaceReserveeAuxAdministrateursEtRefusJournalise`), limite de 50 sources personnelles (`limiteDeSourcesPersonnellesAppliquee`), adresse déjà suivie (`adresseDejaSuivieRefusee`), planification (`planificateurCollecteLesSourcesEchues`), contrat OpenAPI (`ParcoursVeilleWebTest.contratOpenApiPublie`), frontières de modules (`ModuleBoundariesTest`, inchangé).

### Parcours E2E (tâche 5.2)

`ParcoursVeilleWebTest.parcoursVeilleWeb`, exécuté par l'API avec les jetons de karim (administrateur) et nina (lectrice) :

| Étape | Attendu | Résultat |
|---|---|---|
| Ajout d'un flux d'espace (2 articles) | 201, articles visibles | 201 ; les 2 articles figurent dans la liste de l'étape 3 |
| Ajout de `http://192.168.1.1/flux` | refus, aucune source | 400 `ADRESSE_INTERDITE` ; 2 sources au total (les deux flux valides) |
| Même article par un flux personnel | une actualité, deux sources | 2 actualités au total ; « Angular 19 » cite 2 sources pour karim, 1 (« Flux équipe ») pour nina |
| Article piégé | citation non fiable, aucun impact | résumé `fiable = false` contenant la consigne citée ; 0 rapprochement ; 0 impact projet |
| Article sur Angular | rapprochement « à vérifier » | niveau `A_VERIFIER` ; aucun impact projet |

### Écarts et propositions

- WEB-04-S1 : la passerelle IA (`encadrer-appels-ia`) n'existe pas ; le résumé est un extrait déterministe. La vérification avec la passerelle IA réelle reste à faire avec ce changement.
- [PROPOSITION] Seules les sources d'espace produisent des rapprochements (WEB-06), limités aux projets de cet espace ; une source personnelle ne révèle rien aux autres membres.
- [PROPOSITION] Sources d'espace sans limite de nombre, création réservée aux administrateurs de l'espace.
- [PROPOSITION] `argos.veille-web.exceptions` (couples hôte:port exemptés du refus des adresses locales) ne sert qu'au développement et aux tests ; vide en exploitation.
- Risque résiduel de rebinding DNS documenté dans ADR-0028 et arc42 §8.3.
- L'IHM (`argos-ui`) n'affiche pas encore les sources ni les actualités web.
## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive collecter-veille-rss-web`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/collecter-veille-rss-web/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
