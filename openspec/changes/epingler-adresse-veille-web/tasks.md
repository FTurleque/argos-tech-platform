# Tâches : épingler l'adresse validée de la veille web

<!--
Constat traité : AUD-SEC-01 (/docs/audit/2026-11-21/findings.md, sprint 3).
Règles : aucune requête vers Internet dans les tests (serveurs HTTP et HTTPS locaux, résolveur simulé) ; aucune dépendance nouvelle sans décision du porteur du projet (ADR-0021, design.md D1) ; WEB-03 inchangée.
-->

## 1. Préparation

- [x] 1.1 Vérifier que le client HTTP retenu par design.md D1 permet de se connecter à une adresse IP en portant le nom d'hôte d'origine pour SNI, `Host` et la vérification du certificat, avec le délai global existant ; sinon soumettre l'option de repli (dépendance nouvelle) au porteur du projet — couvre WEB-07 — vérification : choix consigné dans design.md D1 (statut [DÉCISION] avec date), essai sur serveur HTTPS local

## 2. Connexion épinglée

- [x] 2.1 Faire renvoyer par le contrôle d'adresse une destination contrôlée (URI et adresses IP autorisées), couples exemptés compris (I-3), sans modifier la règle de WEB-03 — couvre WEB-07, WEB-07-S3 — vérification : `GardeAdressesTest` passe sans modification de ses attentes de refus
- [x] 2.2 Remplacer le transport de production par un transport qui se connecte à une adresse de la destination contrôlée, essaie les suivantes dans le délai global (design.md D3) et ne fait aucune résolution (I-1, I-2) ; appliquer la même règle à chaque redirection — couvre WEB-07-S1, WEB-07-S2 — vérification : tests de 2.3 passent
- [x] 2.3 Tests du groupe : résolveur simulé qui change de réponse entre deux appels, sans et avec redirection ; redirection interdite dès le contrôle ; résolveur qui échoue au second appel (I-2) ; première adresse injoignable (D3) ; tests existants de délai, taille et serveur muet sur le nouveau transport — couvre WEB-07-S1, WEB-07-S2, WEB-07-S3 — vérification : `mvn -B -f argos-server/pom.xml verify` passe, `grep -rn rebind argos-server/src/test` non vide
- [x] 2.4 Test HTTPS sur serveur local avec certificat de test : certificat valide pour le nom accepté, certificat d'un autre nom refusé et source en erreur — couvre WEB-07-S4 — vérification : test passe dans `mvn -B -f argos-server/pom.xml verify`
- [x] 2.5 Réécrire la Javadoc de `RecuperateurWeb` et de `GardeAdresses` (connexion à l'adresse contrôlée, plus de seconde résolution) — couvre WEB-07 — vérification : relecture du diff

## 3. Documentation durable et décisions

- [x] 3.1 Fiche `/docs/specifications/veille-rss-web.md` : règle WEB-07 ([PROPOSITION], `epingler-adresse-veille-web`), critères d'acceptation, écart « risque résiduel » résolu, traçabilité ; registre `/docs/specifications/README.md` (compte des exigences de `veille-rss-web`, changement actif) — couvre WEB-07 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 3.2 arc42 §8.3 (`/docs/architecture/arc42/08-concepts-transverses.md`) : contrôle SSRF complété par la connexion à l'adresse contrôlée, paragraphe « Risque résiduel » réécrit (reste : filtrage de sortie) ; proposer au porteur du projet la mise à jour d'ADR-0028 (note datée ou remplacement, Q-3) et de l'index arc42 §9 si un ADR est créé — couvre WEB-07 — vérification : `node scripts/verifier-docs.mjs` sans erreur ; décision du porteur consignée
- [x] 3.3 Registre des risques `/docs/architecture/risks/register.md` (R-12 : parade et statut) et D-12 `/docs/architecture/diagrams/d-12-frontieres-confiance.md` si le franchissement vers Internet y décrit la résolution ; inventaire `/docs/architecture/diagrams/README.md` si la portée de D-12 change — couvre WEB-07 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement et la documentation — vérification : `openspec validate epingler-adresse-veille-web --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.2 Non-régression de WEB-01 à WEB-03 : la suite serveur complète, dont les tests de la veille web (`argos-server/src/test/java/io/argos/ingestion/web/` et les tests de collecte des sources web), passe avec le nouveau transport ; aucun parcours E2E ne couvre la veille web à ce jour (`e2e/tests/`) — couvre WEB-07 — vérification : `mvn -B -f argos-server/pom.xml verify` passe

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| WEB-07 | WEB-07-S1 | design.md D1, D2, « Traitements et interactions » ; DC-1 | 1.1, 2.1, 2.2, 2.3 | test de rebinding sans redirection (`argos-server/src/test/java/io/argos/ingestion/web/`) | exécuté, vert (`RecuperateurWebTest`, `TransportEpingleTest`) |
| WEB-07 | WEB-07-S2 | design.md D2 ; DC-1 (branche redirection) | 2.2, 2.3 | test de rebinding après redirection | exécuté, vert (`RecuperateurWebTest`, `TransportEpingleTest`) |
| WEB-07 | WEB-07-S3 | design.md D2 ; DC-1 (branche refus) | 2.1, 2.3 | test de redirection interdite dès le contrôle (complète WEB-03-S2) | exécuté, vert (`RecuperateurWebTest`, `TransportEpingleTest`) |
| WEB-07 | WEB-07-S4 | design.md D1, « Sécurité et isolation » | 1.1, 2.4 | test HTTPS local, certificat conforme et non conforme | exécuté, vert (`TransportEpingleTest.certificatVerifiePourLeNomDHote`) |

Constat → exigence → tâche → preuve attendue :

| Constat | Exigence / scénarios | Tâches | Preuve attendue | État |
|---|---|---|---|---|
| AUD-SEC-01 (épinglage de l'adresse validée, à chaque redirection) | WEB-07-S1, WEB-07-S2, WEB-07-S3 | 2.1, 2.2, 2.3 | tests de rebinding verts ; `grep -rn rebind argos-server/src/test` non vide | exécuté, vert |
| AUD-SEC-01 (SNI et `Host` d'origine) | WEB-07-S4 | 1.1, 2.4 | test HTTPS local vert | exécuté, vert |
| AUD-SEC-01 (risque résiduel documenté) | WEB-07 | 3.2, 3.3 | arc42 §8.3, R-12 et ADR-0028 à jour ; `node scripts/verifier-docs.mjs` sans erreur | non exécuté |
| AUD-SEC-01 (filtrage de sortie en hébergement en ligne) | — (hors périmètre, Q-2) | — | décision du porteur du projet | non réalisée |

## Workflow follow-up

- Basculer dans la fiche `veille-rss-web` le statut [PROPOSITION] de WEB-07 selon les preuves obtenues, et le statut du constat AUD-SEC-01 dans le suivi d'audit.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive epingler-adresse-veille-web`).
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/epingler-adresse-veille-web/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
