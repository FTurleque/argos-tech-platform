# Tâches : diffuser les alertes ciblées et les résumés périodiques

<!-- Tâches réalisées dans argos-server ; preuves dans la matrice de traçabilité. -->

## 1. Arbitrages

- [x] 1.1 Faire choisir le serveur d'envoi d'e-mails (Q-1) — couvre ALR-06 — vérification : choix inscrit dans la fiche `alertes-digests` (décidé le 2026-11-16)

## 2. Alertes immédiates

- [x] 2.1 Créer les migrations des alertes et de leur état par canal, avec la clé d'idempotence — couvre ALR-03 — vérification : test d'unicité passe
- [x] 2.2 Évaluer les règles sur les impacts publiés par `impact-engine` — couvre ALR-01-S1, ALR-01-S2, ALR-02-S1 — vérification : tests d'intégration passent
- [x] 2.3 Regrouper par signal, utilisateur et canal — couvre ALR-03-S1 — vérification : test d'intégration passe
- [x] 2.4 Demander le résumé IA sans bloquer l'envoi — couvre ALR-05-S1 — vérification : test avec ai-gateway au plafond passe ; QS-10 exécuté

## 3. Résumés et canaux

- [x] 3.1 Planifier et composer les résumés quotidiens et hebdomadaires — couvre ALR-04-S1, ALR-04-S2 — vérification : tests avec horloge simulée passent
- [x] 3.2 Envoyer par e-mail avec nouvelles tentatives et état d'échec — couvre ALR-06-S1, ALR-06-S2 — vérification : tests avec SMTP de test passent
- [x] 3.3 Exposer les alertes au client desktop (contrat utilisé par `notifier-sur-le-poste`) — couvre ALR-06 — vérification : test de contrat passe

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour la fiche `alertes-digests`, les sections arc42 §6.1 et §8.2, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate diffuser-alertes-et-digests --strict` réussit
- [x] 5.2 Parcours : faille critique de test → impact exact → alerte e-mail et desktop en moins du délai cible (Q-4), puis résumé du lendemain sans doublon — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : toutes les tâches sont réalisées et leurs vérifications réussissent. Commande : `mvn -B -f argos-server/pom.xml verify` (Docker requis pour le PostgreSQL et le Keycloak des Dev Services). Tests : `argos-server/src/test/java/io/argos/alerting/` (`AlertesTest`, `EchecSmtpTest`, `RegleAlerteTest`), règles d'architecture `ModuleBoundariesTest` et `SubscriptionIsolationTest`.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| ALR-01 | ALR-01-S1 | DC-1 | 2.2 | `AlertesTest.alr01S1EtAlr06S1FailleCritiqueSurUnProjetSuiviEnvoyeeParEmail` ; `RegleAlerteTest` | passe |
| ALR-01 | ALR-01-S2 | DC-1 | 2.2 | `AlertesTest.alr01S2GraviteInsuffisanteDansLeProchainResume`, `AlertesTest.graviteMinimaleInconnueRefuseeALaCreationDeLaRegle` | passe |
| ALR-02 | ALR-02-S1 | DC-1 | 2.2 | `AlertesTest.alr02S1CorrespondanceAVerifierSansAlerteEtVisibleDansLeFil` | passe |
| ALR-03 | ALR-03-S1 | DC-1 | 2.1, 2.3 | `AlertesTest.alr03S1SignalTouchantTroisProjetsUneSeuleAlerte` (deux règles et un rejeu), `AlertesTest.cleDIdempotenceUniqueParSignalCompteEtCanal` (SQLSTATE 23505) | passe |
| ALR-04 | ALR-04-S1 | DC-1 | 3.1 | `AlertesTest.alr04S1ResumeHebdomadaireAvecSources`, `AlertesTest.horairesDesResumesConformesALaDecisionQ3` (horloge simulée) | passe |
| ALR-04 | ALR-04-S2 | DC-1 | 3.1 | `AlertesTest.alr04S2RienDeNouveauAucunResume` | passe |
| ALR-05 | ALR-05-S1 | DC-1 | 2.4 | `AlertesTest.alr05S1PlafondIaAtteintAlerteEnvoyeeSansResume` ; QS-10 : `AlertesTest.qs10IaIndisponibleRefuseeOuTropLenteNeRetientPasLAlerte` | passe |
| ALR-06 | ALR-06-S1 | DC-1 | 1.1, 3.2, 3.3 | `AlertesTest.alr01S1EtAlr06S1FailleCritiqueSurUnProjetSuiviEnvoyeeParEmail`, `cloisonnementLesProjetsCitesSontCeuxDesEspacesDuDestinataire`, `contratDuClientDesktopListeRemiseEtCloisonnement`, `contratOpenApiDesAlertes`, `remiseRefuseePourUneAlerteEmail`, `rlsUnCompteNeVoitQueSesAlertes` (rôle `argos_app`) | passe |
| ALR-06 | ALR-06-S2 | DC-1 | 1.1, 3.2, 3.3 | `EchecSmtpTest.alr06S2EnvoiRetenteMarqueEnEchecEtAlerteVisible` (client SMTP réel vers un port local fermé) ; `RegleAlerteTest.delaiDesNouvellesTentativesCroissantEtPlafonne` | passe |

Parcours 5.2 : `AlertesTest.parcoursFailleCritiqueAlerteEmailEtDesktopPuisResumeDuLendemainSansDoublon`. Faille critique de test, impact `EXACT`, alerte e-mail envoyée et alerte desktop en attente de remise 75 ms après le début de la collecte (exécution complète de `verify` du 2026-11-16, 187 tests au vert ; 321 ms en exécution isolée ; très en deçà de la cible d'une heure, Q-4 non validée) ; le résumé du lendemain (e-mail et desktop) cite l'élément non alerté et omet l'élément déjà alerté ; un second résumé et une nouvelle diffusion n'envoient rien de plus.

Vérifications documentaires : `node scripts/verifier-docs.mjs` sans erreur ; `openspec validate diffuser-alertes-et-digests --strict` valide.

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive diffuser-alertes-et-digests`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/diffuser-alertes-et-digests/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
