# Tâches : collecter la veille RSS/Atom et les pages web surveillées

<!-- Code : argos-server (io.argos.ingestion.web, io.argos.signal.web, io.argos.impact.RapprochementActualites, io.argos.api.VeilleWebResource, io.argos.api.security.LimiteurDebit, migration V10). Tests : io.argos.ingestion.web.*Test, io.argos.impact.TermesTechnologieTest, io.argos.signal.web.CollecteWebTest, io.argos.api.security.LimiteurDebitTest, io.argos.chain.VeilleWebTest, io.argos.chain.LimiteDebitVeilleWebTest, io.argos.chain.ParcoursVeilleWebTest. -->

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

## 6. Constats de relecture

- [x] 6.1 Cloisonner les actualités par périmètre de source (propriétaire ou espace) : déduplication, contenu et rapprochements, RLS sous `argos_app` — vérification : `VeilleWebTest.contenuDUneSourcePersonnelleCloisonneParCompte`, `sourcePersonnelleNInjecteRienDansUnEspace`, `cloisonnementEnBaseSousLeRoleApplicatif`
- [x] 6.2 Limiter le débit de l'ajout et de la collecte à la demande par compte (429 `LIMITE_DEBIT` journalisé), limiter les ports à 80 et 443, renvoyer des motifs sans indice réseau — vérification : `LimiteDebitVeilleWebTest`, `LimiteurDebitTest`, `GardeAdressesTest.portsLimitesAuxPortsAutorises`, `hoteIntrouvableIndiscernableDUnHoteInterdit`, `VeilleWebTest.refusSansIndiceReseau`
- [x] 6.3 Borner un passage du planificateur (nombre de sources, durée) et sérialiser la collecte d'une source — vérification : `CollecteWebTest.collecteDUneMemeSourceSerialisee`, `passageBorneEnNombreEtEnDuree`, `planificateurNeCollecteQueLesSourcesEchues`
- [x] 6.4 Refuser les IPv4 interdites embarquées en 6to4 et NAT64, Teredo, 64:ff9b:1::/48 et 100::/64 — vérification : `GardeAdressesTest.adressesPriveesLocalesEtReserveesRefusees`, `plagesPubliquesNonRefusees`
- [x] 6.5 Enregistrer l'adresse d'une source sous sa forme canonique ; état `EN_ERREUR` cohérent si la première collecte échoue — vérification : `CollecteWebTest.adresseCanonique`, `uniciteSurLAdresseCanonique`, `premiereCollecteEnEchecLaisseLaSourceEnErreur`
- [x] 6.6 Fixer et documenter le texte brut contenant des caractères de balisage en clair (échappement à la charge de l'IHM) — vérification : `LecteursTest.entitesDecodeesConserveesEnTexteBrutAEchapperALAffichage`, fiche `veille-rss-web`
- [x] 6.7 Supprimer les sources et actualités personnelles avec le compte (`ON DELETE CASCADE`, comme les migrations V7 et V8) — vérification : `VeilleWebTest.donneesPersonnellesSupprimeesAvecLeCompte`
- [x] 6.8 Aligner arc42 §8.3, ADR-0028 et la fiche sur le code — vérification : `node scripts/verifier-docs.mjs` sans erreur

## Matrice de traçabilité

État au 2026-11-16 : toutes les tâches réalisées ; preuves obtenues par `mvn -B -f argos-server/pom.xml verify` (PostgreSQL et Keycloak des Dev Services, jetons réels, RLS sous le rôle `argos_app`, sources simulées par le serveur HTTP local `StubServer`, aucune requête réseau externe).

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| WEB-01 | WEB-01-S1 | DC-1 | 1.1, 2.2, 3.1 | `VeilleWebTest.fluxAtomValideCreeLaSourceEtSesArticlesApparaissent` ; `ParcoursVeilleWebTest.parcoursVeilleWeb` | passe |
| WEB-01 | WEB-01-S2 | DC-1, DF-WEB-1 | 1.1, 2.1 | `VeilleWebTest.adresseSansFluxRefuseeSansCreerDeSource` ; `LecteursTest.pageHtmlOuDoctypeRefusesCommeFlux` | passe |
| WEB-02 | WEB-02-S1 | DC-1 | 1.1, 3.1 | `VeilleWebTest.pageSurveilleeModifieeCreeUnElementDateAvecApercu` ; `LecteursTest.pageLueParBlocsEtApercuDuChangement` | passe |
| WEB-03 | WEB-03-S1 | DC-1, DF-WEB-1 | 2.1, 6.2, 6.4 | `VeilleWebTest.adressePriveeRefuseeSansRequete`, `refusSansIndiceReseau` ; `RecuperateurWebTest.adressePriveeRefuseeSansAucuneRequete` ; `GardeAdressesTest` (41 cas : plages IPv4 et IPv6, IPv4 embarquées en 6to4, NAT64, Teredo, 100::/64, ports, hôte introuvable indiscernable d'un hôte interdit) | passe |
| WEB-03 | WEB-03-S2 | DC-1 | 2.1 | `VeilleWebTest.redirectionVersAdresseLocaleMetLaSourceEnErreur` ; `RecuperateurWebTest.redirectionVersLaBoucleLocaleInterrompue` ; durée bornée : `VeilleWebTest.recuperationTropLongueInterrompue`, `RecuperateurWebTest.delaiEtTailleBornes`, `RecuperateurWebTest.corpsMuetInterrompuALEcheance` (en-têtes reçus puis silence : corps fermé à l'échéance) ; état après échec de la première collecte : `CollecteWebTest.premiereCollecteEnEchecLaisseLaSourceEnErreur` | passe |
| WEB-04 | WEB-04-S1 | DC-1 | 3.3, 6.6 | `VeilleWebTest.articlePiegeCiteCommeDonneeSansEffetSurLesImpacts` (résumé extractif ; passerelle IA réelle absente, voir écart) ; `LecteursTest.entitesDecodeesConserveesEnTexteBrutAEchapperALAffichage` | passe |
| WEB-05 | WEB-05-S1 | DC-1 | 3.2, 6.1, 6.5 | `VeilleWebTest.memeArticleDansDeuxFluxRestitueUneFoisAvecSesDeuxSources`, `contenuDUneSourcePersonnelleCloisonneParCompte` ; `ParcoursVeilleWebTest.parcoursVeilleWeb` ; `CollecteWebTest.uniciteSurLAdresseCanonique` | passe |
| WEB-06 | WEB-06-S1 | DC-1 | 3.4, 6.1 | `VeilleWebTest.articleSurUneTechnologieUtiliseeRapprocheAVerifierSansAlerte`, `sourcePersonnelleNeRapprocheAucunProjet`, `sourcePersonnelleNInjecteRienDansUnEspace` ; `TermesTechnologieTest` | passe |

Preuves complémentaires : cloisonnement en base sous `argos_app` (`VeilleWebTest.cloisonnementEnBaseSousLeRoleApplicatif`, `contenuDUneSourcePersonnelleCloisonneParCompte`, `sourcePersonnelleNInjecteRienDansUnEspace`), droits d'espace et journal des refus (`sourceEspaceReserveeAuxAdministrateursEtRefusJournalise`), limite de 50 sources personnelles (`limiteDeSourcesPersonnellesAppliquee`), adresse déjà suivie (`adresseDejaSuivieRefusee`, `CollecteWebTest.uniciteSurLAdresseCanonique`), limite de débit (`LimiteDebitVeilleWebTest.ajoutsEtCollectesLimitesParCompte`, `LimiteurDebitTest`), planification (`planificateurCollecteLesSourcesEchues`, `CollecteWebTest.passageBorneEnNombreEtEnDuree`), course entre collecte à la demande et planificateur (`CollecteWebTest.collecteDUneMemeSourceSerialisee`), suppression avec le compte (`donneesPersonnellesSupprimeesAvecLeCompte`), contrat OpenAPI (`ParcoursVeilleWebTest.contratOpenApiPublie`), frontières de modules (`ModuleBoundariesTest`, inchangé : le limiteur commun est dans `api.security`, déjà autorisé pour `mcp`).

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
- [PROPOSITION] `argos.veille-web.exceptions` (couples hôte:port exemptés du refus des adresses locales et des ports) ne sert qu'au développement et aux tests ; vide en exploitation.
- [PROPOSITION] Limite de débit de 10 requêtes par minute et par compte, commune à l'ajout d'une source et à la collecte à la demande ; limiteur `api.security.LimiteurDebit` partagé avec le serveur MCP, instances distinctes.
- [PROPOSITION] Un article présent dans plusieurs périmètres visibles d'un compte est restitué une fois : ligne d'un espace de préférence, puis la plus ancienne ; citations et rapprochements de toutes les lignes de même empreinte.
- [PROPOSITION] Hôte introuvable refusé comme `ADRESSE_INTERDITE` ; Teredo, 100::/64 et 64:ff9b::/32 hors préfixe bien connu refusés sans examen de l'IPv4 embarquée.
- [PROPOSITION] Durée maximale d'un passage du planificateur : 4 minutes, sous la cadence de 5 minutes.
- Risque résiduel de rebinding DNS documenté dans ADR-0028 et arc42 §8.3.
- L'IHM (`argos-ui`) n'affiche pas encore les sources ni les actualités web.
## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive collecter-veille-rss-web`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/collecter-veille-rss-web/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
