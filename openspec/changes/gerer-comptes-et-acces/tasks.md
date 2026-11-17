# Tâches : gérer les comptes, les espaces et le contrôle d'accès

<!-- Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). Une tâche n'est cochée que lorsque sa vérification a été exécutée avec succès. -->

## 1. Arbitrages

- [x] 1.1 Faire choisir le fournisseur d'identité (Q-1) — couvre ACC-01, ACC-02 — vérification : choix inscrit dans la fiche `comptes-acces` (décidé le 2026-11-16)
- [x] 1.2 Faire décider l'inscription ouverte ou sur invitation et le profil par défaut (Q-2, Q-3) — couvre ACC-02 — vérification : décisions inscrites dans la fiche (décidé le 2026-11-16)

## 2. Authentification et comptes

- [x] 2.1 Brancher la validation OIDC dans `API & Security` et protéger toutes les routes sauf santé et identité du serveur — couvre ACC-01-S1, ACC-01-S2 — vérification : tests d'intégration passent
- [x] 2.2 Créer les migrations `compte` et la création à la première connexion — couvre ACC-02-S1, ACC-02-S2 — vérification : tests d'intégration avec IdP de test passent

## 3. Espaces, rôles et cloisonnement

- [x] 3.1 Créer les migrations `espace`, `appartenance`, `invitation` et l'API associée — couvre ACC-03, ACC-05 — vérification : test de contrat OpenAPI passe
- [x] 3.2 Implémenter rôles, invitations et révocation — couvre ACC-03-S1, ACC-03-S2, ACC-05-S1, ACC-05-S2 — vérification : tests d'intégration passent
- [x] 3.3 Activer la RLS et la transmission de l'espace courant ; test sans contexte — couvre ACC-04-S1, ACC-04-S2 — vérification : tests inter-espaces et RLS passent ; QS-02 exécuté
- [x] 3.4 Journaliser les refus dans `audit` — couvre ACC-06-S1 — vérification : test d'intégration passe

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour la fiche `comptes-acces`, les sections arc42 §8.1, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.2 Rédiger un ADR complétant ADR-0008 pour un fournisseur d'identité auto-hébergé (statut Proposé) — vérification : index `/docs/architecture/adr/README.md` et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate gerer-comptes-et-acces --strict` réussit
- [x] 5.2 Parcours : première connexion, création d'un espace, invitation d'un lecteur, refus d'accès à un autre espace journalisé — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16, code intégré avec `formaliser-chaine-securite-poc` et `personnaliser-profils-et-affichage` : commande `mvn -B -f argos-server/pom.xml verify` réussie (164 tests, 0 échec), avec le Keycloak (royaume `argos-server/src/test/resources/keycloak/argos-realm.json`) et le PostgreSQL des Dev Services Quarkus. Chemins des tests relatifs à `/argos-server/src/test/java/io/argos/api/comptes/` sauf mention contraire.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| ACC-01 | ACC-01-S1 | DC-1 | 1.1, 2.1 | `AuthentificationTest#accesSansSessionRefuse` (6 routes, 401 sans corps), `#jetonInvalideRefuse`, `#jetonDUneAutreAudienceRefuse` | réussi |
| ACC-01 | ACC-01-S2 | DC-1 | 1.1, 2.1 | `AuthentificationTest#identiteDuServeurPublique`, `#santePublique` ; `io/argos/api/SystemInfoResourceTest` | réussi |
| ACC-02 | ACC-02-S1 | DC-1 | 1.1, 1.2, 2.2 | `PremiereConnexionTest#premiereConnexionCreeLeCompteSansEspace` (compte créé, aucun espace, événement `CompteCree` avec profil `developpeur`) ; `io/argos/api/preference/PreferencesResourceTest#laCreationDUnCompteActiveLeProfilDeveloppeur` (profil enregistré dans les préférences du compte) | réussi |
| ACC-02 | ACC-02-S2 | DC-1 | 1.1, 1.2, 2.2 | `PremiereConnexionTest#connexionRefuseeParLeFournisseur` (Keycloak répond `invalid_grant`, aucun compte), `#inscriptionSansInvitationRefusee` (Q-2) | réussi ; le « message explicite » d'un écran de connexion n'existe pas (aucun écran de connexion dans argos-ui) |
| ACC-03 | ACC-03-S1 | DC-1 | 3.1, 3.2 | `EspacesEtRolesTest#creationDUnEspace` ; contrat : `ContratOpenApiTest#contratPublie` | réussi |
| ACC-03 | ACC-03-S2 | DC-1 | 3.1, 3.2 | `EspacesEtRolesTest#gestionDesRolesParUnContributeurInterdite` (403, rôles inchangés), `#dernierAdministrateurConserve` (INV-1), `DefenseEnProfondeurTest#inv1SurEnConcurrence` (verrou de l'espace) | réussi |
| ACC-04 | ACC-04-S1 | DC-1, DC-2 | 3.3 | `CloisonnementTest#lectureDUnAutreEspaceRefusee` (404 sans donnée de l'espace B, Q-4) ; serveur MCP : `io/argos/mcp/CloisonnementMcpTest#mcp04S1ProjetDUnAutreEspace` | réussi |
| ACC-04 | ACC-04-S2 | DC-1, DC-2 | 3.3 | `CloisonnementTest#requeteEnBaseSansContexteNeRenvoieRien`, `#rlsLimiteeAuxEspacesDuCompte` (tables `espace`, `appartenance`, `invitation`) ; `DefenseEnProfondeurTest` (invitations, comptes, journal) ; impacts : `io/argos/chain/CorrelationTest#rlsGardeFouEnBase`, `#rlsAppliqueeSurLeCheminApplicatif` ; préférences : `io/argos/api/preference/PreferencesResourceTest#laBaseNExposeQueLesPreferencesDuCompteCourant` — tous sous le rôle non super-utilisateur `argos_app` ; schéma entier : `CouvertureRlsTest#touteTableAccessibleAuRoleApplicatifEstSousRls` (toute table accessible à `argos_app` sous RLS avec politique, hors 5 données de référence globales sans espace ni compte), `#aucuneLigneSansContexteSurToutesLesTablesSousRls`, `#roleApplicatifSansPrivilegeDeContournement` (ni super-utilisateur, ni `BYPASSRLS`, ni propriétaire), `#contexteLocalALaTransaction` (rôle et espace courant locaux à la transaction) ; MCP : `io/argos/mcp/CloisonnementMcpTest#rlsSousLeRoleApplicatif` | réussi |
| ACC-05 | ACC-05-S1 | DC-1 | 3.1, 3.2 | `InvitationsTest#invitationAccepteeDonneUnAccesEnLectureSeule` (invitation déjà utilisée : 409 `INVITATION_INACTIVE`), `#invitationDUnTiersIntrouvable` (404 non journalisé), `#invitationRevoquee` (409) ; `DefenseEnProfondeurTest#leDestinataireNeReecritNiLeRoleNiLEspaceDeSonInvitation` | réussi |
| ACC-05 | ACC-05-S2 | DC-1 | 3.1, 3.2 | `InvitationsTest#revocationImmediate` | réussi |
| ACC-06 | ACC-06-S1 | DC-2 | 3.4 | `JournalRefusTest#refusJournalise` (compte, ressource, horodatage, aucune donnée de la ressource), `#refusPourRoleInsuffisantJournalise` ; journal unique pour toute l'API : `io/argos/chain/CorrelationTest#cor08S1EtS2…`, `io/argos/chain/SubscriptionTest#abo06SeulLAdministrateurDeLEspaceCreeUnAbonnementTeam` ; `argos_app` sans droit sur le journal : `DefenseEnProfondeurTest#moindrePrivilegeSurLesComptesEtLeJournal` | réussi |

Tâche 3.3 (vérifiée le 2026-11-17) : l'espace courant est transmis par le chemin de la requête et posé par `ContexteBase` (`SET LOCAL ROLE argos_app`, `argos.espace_courant`) ; les tests inter-espaces et RLS ci-dessus réussissent sous `argos_app`. QS-02 (`/docs/architecture/quality/scenarios.md`, arc42 §10.2 Q-02) porte sur la confidentialité des appels LLM ; il est exécuté au niveau des tests de la passerelle IA : `io/argos/aigateway/PasserelleIaTest#iag02S1DonneeConfidentielleRefuseeSansEnvoiEtJournalisee` et `io/argos/chain/ParcoursIaTest#iag02S1DonneeConfidentielleRefuseeEtJournalisee` (entrées CONFIDENTIAL et RESTRICTED refusées, aucun envoi au fournisseur simulé, refus journalisé dans `ia_appel` avec son espace : 0 violation). Commande : `mvn -B -f argos-server/pom.xml test -Dtest=CouvertureRlsTest,CloisonnementTest,PasserelleIaTest#iag02*,ParcoursIaTest#iag02*` réussie (10 tests, 0 échec). Aucun scénario qualité du registre ne couvre l'accès inter-espaces.

Parcours de bout en bout (tâche 5.2), au niveau de l'API sans IHM : `ParcoursComptesEtAccesTest#parcoursComplet` — l'administrateur plateforme invite Hugo ; première connexion de Hugo (compte sans espace) ; Hugo crée « Atelier de Hugo » (administrateur) ; Hugo invite Gina comme lectrice, Gina accepte et lit l'espace ; Gina demande l'espace d'Alice : 404 et une entrée `NON_MEMBRE` dans `refus_acces`. Résultat : réussi.

Autres vérifications : `openspec validate gerer-comptes-et-acces --strict` valide ; `node scripts/verifier-docs.mjs` sans erreur ; `io/argos/architecture/ModuleBoundariesTest` réussi.

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive gerer-comptes-et-acces`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/gerer-comptes-et-acces/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
