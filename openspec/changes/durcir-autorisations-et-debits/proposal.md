# Proposition : durcir les autorisations et limiter les débits

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le contrôle de rôle d'Argos repose sur des appels impératifs (80 appels `access.*()` pour 96 opérations) sans aucun test matriciel point d'entrée × rôle ; une opération d'exploitation n'est appelée par aucun test (AUD-TST-05). Quatre défauts d'autorisation ou de débit restent ouverts : la demande d'évolution fait interroger GitHub ou GitLab avec les jetons du serveur avant tout contrôle de visibilité et d'expertise, avec une réponse qui révèle l'existence d'un ticket privé (AUD-SEC-22) ; les alertes, résumés et rappels partent vers l'adresse e-mail brute du jeton, même non vérifiée (AUD-SEC-04) ; la recherche plein texte et « Ma veille » n'ont aucune limite de débit (AUD-SEC-09) ; l'écoute de bouclage du client desktop se ferme sur le premier retour reçu sans comparer le state (AUD-SEC-23) [EXISTANT — `/docs/audit/2026-11-21/findings.md`, constats vérifiés à la révision `b142695`]. L'audit les place dans la seconde moitié du sprint 6 (« Portail déployable et autorisations ») [PROPOSITION — `/docs/audit/2026-11-21/sprints.md`].

## What Changes — Changements

- Demande d'évolution d'une fiche réglementaire : visibilité de la fiche, expertise de l'appelant et état « impact produit » contrôlés avant tout appel à une forge ; pour un appelant non autorisé, réponse identique quel que soit le résultat qu'aurait donné la forge.
- Alertes, résumés et rappels par e-mail : envoyés seulement vers une adresse vérifiée par le fournisseur d'identité, tenue à jour à la connexion ; sans adresse vérifiée, aucun e-mail, l'élément reste visible dans Argos et sur le poste. Les adresses déjà enregistrées sont effacées par migration et réécrites à la connexion suivante si le jeton les dit vérifiées [DÉCISION — décision du porteur, 2026-11-24].
- Limite de débit par compte, réglable par configuration, refus journalisé : 60 requêtes par minute pour la recherche plein texte (résultats d'une vue enregistrée compris, un seul compteur) et 60 requêtes par minute pour « Ma veille » (compteur distinct) ; limites relevées par configuration pendant la campagne de performance [DÉCISION — décision du porteur, 2026-11-24].
- Client desktop : l'écoute de bouclage ignore (400) un retour dont le state n'est pas celui de la demande en cours et ne se ferme que sur le state attendu.
- Test matriciel : chaque opération du document OpenAPI, pour chaque rôle, avec le code attendu ; échec si une opération n'est pas déclarée dans la matrice.
- **BREAKING** : un client qui dépasse la limite de débit reçoit 429 sur la recherche et « Ma veille » ; un compte sans adresse vérifiée ne reçoit plus d'e-mail.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `veille-reglementaire` : exigence ajoutée REG-09 (demande d'évolution contrôlée avant tout appel à la forge, réponse non différenciée). REG-08 inchangée.
- `alertes-digests` : exigence ajoutée ALR-08 (e-mail seulement vers une adresse vérifiée). ALR-06 inchangée.
- `recherche-veille` : exigence ajoutée RCH-06 (limite de débit de la recherche).
- `profils-veille` : exigence ajoutée PRF-08 (limite de débit de « Ma veille »).
- `comptes-acces` : exigence ajoutée ACC-14 (écoute de bouclage du desktop fermée sur le seul state attendu). ACC-10 inchangée.

## Périmètre

### Inclus

- `PUT /api/v1/reglementaire/fiches/{id}/demande-evolution` et la lecture de l'état d'une demande auprès des forges.
- L'adresse e-mail enregistrée pour un compte et les canaux e-mail des alertes, résumés et rappels réglementaires.
- `GET /api/v1/recherche`, `GET /api/v1/vues/{id}/resultats`, `GET /api/v1/me/veille`.
- L'écoute de bouclage du client desktop (`argos-desktop/src/connexion.js`).
- Le test matriciel des opérations REST, dont `POST /api/v1/ops/sources/{id}/synchronizations` et les opérations d'exploitation sans test de refus.

### Exclu

- Les limites des autres opérations (déjà limitées : veille web, synthèse d'activité, profil artiste, réglementaire, MCP) : inchangées.
- Une limite globale au reverse proxy (alternative d'AUD-SEC-09) : relève de l'exploitation du NAS.
- Le passage des contrôles impératifs à des annotations déclaratives : non demandé par l'audit ; le test matriciel suffit à détecter un oubli.
- Les chemins publics et le 401 global (AUD-TST-25) : changement `rendre-portail-deployable`.
- Le serveur MCP : hors de la matrice REST (contrôles propres, MCP-06).

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Expert métier réglementaire | seul à associer une demande d'évolution | [EXIGENCE] — REG-08 ; ADR-0030 (Accepté) |
| Membre non expert, non-membre | ne doivent plus pouvoir faire interroger une forge | [PROPOSITION] — REG-09 |
| Destinataire des alertes | reçoit les e-mails à son adresse vérifiée seulement | [PROPOSITION] — ALR-08 |
| Utilisateur de la recherche et de « Ma veille » | soumis à une limite de débit par compte | [PROPOSITION] — RCH-06, PRF-08 |
| Utilisateur du client desktop | sa connexion n'est plus interrompue par un retour étranger | [PROPOSITION] — ACC-14 |
| Porteur du projet | a fixé les limites de débit et le sort des adresses existantes ; tranche Q-4 et Q-5 | [DÉCISION] — décision du porteur, 2026-11-24 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Demande d'évolution : contrôle d'authentification seul, limite de débit, puis appel au service | [EXISTANT] | `argos-server/src/main/java/io/argos/api/reglementaire/ReglementaireResource.java:315-320` |
| Contrôle préalable sans levée pour une fiche invisible ou un non-expert ; appel à la forge avant le contrôle de visibilité et d'expertise ; réponse `DEMANDE_INTROUVABLE` sur un 404 de la forge | [EXISTANT] | `regintel/ExpertiseFiches.java:178`, `:181-182`, `:188`, `:218-223` ; `scm/DemandesEvolution.java:122` (jeton envoyé), `:124-125` (404 → vide) |
| Seul test de la demande d'évolution : parcours de l'expert | [EXISTANT] | `argos-server/src/test/java/io/argos/chain/VeilleReglementaireTest.java:326-341` |
| Non-membre ou ressource inexistante : 404 ; rôle insuffisant : 403 ; limite atteinte : 429 avec `Retry-After`, refus journalisé `LIMITE_DEBIT` | [EXISTANT] | `argos-server/src/main/java/io/argos/api/GestionErreurs.java:25-27`, `:78` |
| Adresse brute du jeton enregistrée à la création du compte ; seule l'adresse vérifiée sert aux invitations | [EXISTANT] | `workspace/ComptesService.java:54`, `:74` ; compte existant renvoyé sans mise à jour (`:56-58`) |
| Destinataires e-mail lus dans `compte.email` | [EXISTANT] | `alerting/Destinataires.java:39`, `:51` ; `alerting/RappelsReglementaires.java:113-116` ; `alerting/CanalEmail.java` |
| Rappels réglementaires : canal poste à défaut d'adresse | [EXISTANT] | `alerting/RappelsReglementaires.java:131-132` |
| Recherche et « Ma veille » sans limiteur | [EXISTANT] | `api/recherche/RechercheResource.java:57`, `api/recherche/VuesResource.java:115`, `api/veille/VeilleProfilsResource.java:139` ; `grep -rln LimiteurDebit argos-server/src/main/java` : 8 fichiers, aucun de recherche ni de veille |
| Limiteur existant : fenêtre glissante d'une minute par compte, valeur configurable | [EXISTANT] | `api/security/LimiteurDebit.java:21-45` |
| Valeurs en vigueur : 10 requêtes/min (synthèse d'activité, profil artiste, réglementaire, veille web), 60 requêtes/min (MCP) | [EXISTANT] | `ActiviteResource.java:52`, `ProfilArtisteResource.java:151`, `ReglementaireResource.java:130`, `VeilleWebResource.java:84`, `mcp/GardeAppels.java:58` |
| Limitation de débit du serveur MCP | [PROPOSITION] | MCP-06 (`/openspec/specs/serveur-mcp/spec.md`), ADR-0014 (Proposé) |
| Valeur proposée par l'auditeur pour la recherche : « ex. 60/min/compte » | [PROPOSITION] | AUD-SEC-09, « Action » |
| Limites retenues : 60 requêtes/min/compte pour la recherche (résultats de vue compris, un compteur) et 60 requêtes/min/compte pour « Ma veille » (compteur distinct), réglables, relevées pendant la campagne de performance | [DÉCISION] | décision du porteur, 2026-11-24 |
| Adresses e-mail enregistrées effacées par migration, réécrites à la connexion suivante si le jeton les dit vérifiées | [DÉCISION] | décision du porteur, 2026-11-24 |
| Écoute de bouclage : méthode, chemin et écoute active seulement ; fermeture sur la première requête conforme ; state vérifié par l'IHM | [EXISTANT] | `argos-desktop/src/connexion.js:101-104`, `:119-130` ; `argos-ui/src/session/oidc.ts:150` |
| Desktop : redirection de bouclage, retour unique transmis à la fenêtre principale | [EXIGENCE] | ACC-10 (`/openspec/specs/comptes-acces/spec.md`) ; ADR-0033 (Accepté) point 4 |
| Contrôle de rôle impératif, 96 opérations dans 23 ressources, synchronisation manuelle jamais testée | [EXISTANT] | AUD-TST-05 ; `api/SourceOperationsResource.java:57-63` ; `grep -rn synchronizations argos-server/src/test/java` : 0 |
| Autorisation dans Argos (espaces, rôles), cloisonnement | [DÉCISION] | ADR-0016 ; ADR-0027 (Accepté) ; ACC-03, ACC-04 |

## Écarts constatés

- **Test de refus d'exploitation** : l'audit cite `chain/ResilienceTest.java:151` « `Api.en("jules")` » ; le code lit `Api.enTantQue("jules")` (même ligne, même assertion 403). Preuve inchangée sur le fond.
- **Débit cumulable de la forge** : l'audit chiffre l'oracle à 10 appels/min/compte, ce qui correspond à la limite réglementaire existante (`ReglementaireResource.java:130`) ; elle reste en place après REG-09, qui supprime l'appel à la forge pour un appelant non autorisé.
- **Campagne de performance et limite de « Ma veille »** : PRF-07-S1 fait appeler `GET /api/v1/me/veille` 200 fois par 4 clients avec le même compte (`/openspec/specs/profils-veille/spec.md`) ; une limite par compte inférieure ferait échouer la campagne en 429. La limite est réglable et la campagne la relève par configuration (design.md D3).
- **Comptes existants** : les adresses déjà enregistrées ne disent pas si elles étaient vérifiées (`V2__comptes_et_acces.sql:28`, colonne `email` facultative) ; elles sont effacées par migration (design.md D2).
- **Vérification du state par l'IHM** : l'audit cite `argos-ui/src/session/oidc.ts:148` ; la comparaison est à `:150` (comparaison `egales`), même comportement.
- Les autres preuves relues à `b142695` sont à jour.

## Impact

- **Composants** (noms de arc42 §5) : `API & Security` (ressources de recherche, de veille, réglementaire ; limiteur), `reg-intel` (association d'une demande), `scm-connector` (lecture de l'état d'une demande, inchangée), `workspace` (adresse du compte), `alerting` (destinataires e-mail), Client desktop Electron (écoute de bouclage).
- **Contrats d'interface** : réponse 429 documentée sur `GET /api/v1/recherche`, `GET /api/v1/vues/{id}/resultats`, `GET /api/v1/me/veille` ; codes de refus de `PUT …/demande-evolution` pour un non-membre (404) et un non-expert (403) avant tout appel à la forge.
- **Données et persistance** : `compte.email` ne contient plus qu'une adresse vérifiée ; migration Flyway qui efface les adresses enregistrées (ADR-0032, Accepté).
- **Sécurité, confidentialité, droits** : suppression d'un oracle d'existence sur les dépôts privés ; aucun relais d'e-mail vers une adresse non vérifiée ; protection du processeur du NAS ; connexion desktop non interruptible par un processus local.
- **Exploitation et observabilité** : nouveaux réglages de débit ; refus journalisés (`LIMITE_DEBIT`, ACC-06) ; refus de la demande d'évolution journalisés.
- **Documentation durable à mettre à jour** : fiches `/docs/specifications/veille-reglementaire.md`, `alertes-digests.md`, `recherche-veille.md`, `profils-veille.md`, `comptes-acces.md` ; registre `/docs/specifications/README.md` ; arc42 §8.1 et §8.3 ; §8.11 (test matriciel) ; §8.7 si migration.
- **ADR** : aucun ADR modifié ; aucune décision candidate à un ADR (décisions locales).
- **Dépendances entre changements** : aucune dépendance obligatoire. Coordination : `rendre-portail-deployable` ajoute ACC-11 à ACC-13 à la même capacité (identifiants disjoints) ; `verifier-types-et-frontieres-modules` touche les types de `argos-desktop/src/connexion.js` (AUD-TST-26), même fichier qu'ACC-14 ; `isoler-erreurs-aux-frontieres` peut toucher `GestionErreurs` (429) ; `mettre-en-place-integration-continue` exécutera le test matriciel.

## Conception requise

complète — sécurité transverse (quatre composants serveur et le desktop), migration de données possible et un test d'architecture nouveau.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-4 | Rôles de la matrice : anonyme, membre lecteur, administrateur d'espace, exploitant, membre d'un autre espace (liste de l'audit), plus administrateur plateforme et expert réglementaire ? | non : proposition en design.md D5 | porteur du projet |
| Q-5 | Le blocage temporaire de la forge d'un expert autorisé (quota) reste-t-il signalé par `DEMANDE_INTROUVABLE` ou un état « non vérifié » comme aujourd'hui ? | non : comportement actuel conservé | porteur du projet |
