# Proposition : Alimenter l'inventaire et compléter l'IHM

## Why — Pourquoi

L'audit du 2026-11-21 (sprint 7 du plan d'action) constate qu'en exploitation aucun projet ne peut naître : la création de projet, le rattachement d'un dépôt et l'import manuel d'une SBOM ou de manifestes n'ont aucun point d'entrée (AUD-QUA-01, Élevée) ; la corrélation, le radar de dette et les alertes d'impact restent donc sans données réelles. Plusieurs fonctions ne sont atteintes que par les tests (suggestions IA du radar, collecte immédiate d'activité, trois méthodes sans appelant, AUD-QUA-17), le profil artiste n'a aucune IHM (AUD-ARC-15) et les liens des alertes visent des routes absentes de l'IHM (AUD-ARC-22). Côté qualité, le client de l'IHM n'est vérifié par aucun contrat (AUD-ARC-12), ni `appelerApi`, ni les vues, ni `main.js` du desktop ne sont testés (AUD-TST-09, AUD-TST-10), cinq modules serveur n'ont pas de classe de test dédiée (AUD-TST-04) et l'isolation entre tests repose sur une liste de tables codée en dur (AUD-TST-08). Origine : constats de `docs/audit/2026-11-21/findings.md` (copie de travail du porteur), sprint 7 du plan [EXIGENCE — plan d'audit validé par le porteur].

## What Changes — Changements

- **Inventaire (AUD-QUA-01)** : routes REST de création de projet dans un espace, de rattachement et de détachement de dépôt, d'import manuel d'une SBOM CycloneDX, d'import de manifestes et de relance de l'import depuis les dépôts ; vue « Inventaire » de l'IHM dotée des formulaires correspondants. Les écritures passent par les traitements d'inventaire existants, après le contrôle de rôle de l'API.
- **Radar (AUD-QUA-17)** : tâche planifiée qui, après la synchronisation des cycles de vie, demande à la passerelle IA une suggestion `HOLD` pour chaque version recommandée d'un standard d'espace arrivée en fin de support ; vue « Radar » de l'espace (échéances, dettes, référentiel, suggestions et décision).
- **Activité (AUD-QUA-17)** : route de collecte immédiate de l'activité d'un projet et bouton « Collecter maintenant » dans la vue d'inventaire ; suppression des méthodes publiques sans appelant (`EtatImpact.peutAller`, `TextesOfficiels.parIdentifiant`, `InventoryService.ImportProjet.importe`) ; test d'architecture qui refuse toute méthode publique de `src/main` atteinte seulement par les tests.
- **Profil artiste (AUD-ARC-15)** : vues de l'IHM pour les suivis (disciplines, territoires), les opportunités et leur résumé, les sources vérifiées et les rubriques de veille.
- **Liens des alertes (AUD-ARC-22)** : liens au format de l'historique « hash » de l'IHM (`<adresse publique>/#/…`) vers des vues existantes : signal (`/#/signaux/<id>`, vue créée), radar d'espace (`/#/espaces/<id>/radar`), opportunité (`/#/profils/artiste/opportunites/<id>`) ; un test vérifie que chaque gabarit de lien correspond à une route de l'IHM. **BREAKING** (format visible des liens des alertes et des courriels).
- **Contrat client (AUD-ARC-12)** : instantané de `/q/openapi` versionné dans `argos-ui`, comparé au schéma réel par un test serveur ; test de l'IHM qui vérifie que chaque appel du client (méthode et chemin) existe dans l'instantané.
- **Tests (AUD-TST-10, AUD-TST-09, AUD-TST-04, AUD-TST-08)** : tests de `main.js` (options de sécurité de la fenêtre, CSP, permissions, navigation) ; tests Vitest de `appelerApi` et des vues ; classes de test dédiées à `workspace`, `inventory`, `vulnintel`, `releaseradar`, `audit` et tests de `scm` jusqu'au seuil JaCoCo par module ; nettoyage entre tests dérivé du catalogue PostgreSQL.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Non applicable — les comportements ajoutés prolongent des capacités existantes.

### Modified Capabilities — Capacités modifiées

- `inventaire-sbom` : ajout d'INV-12 (administration des projets et des dépôts) et d'INV-13 (import manuel par l'API et l'IHM).
- `radar-standards` : ajout de RAD-08 (production des suggestions de l'IA) et de RAD-09 (vue radar de l'espace).
- `activite-projets` : ajout de PIN-06 (collecte immédiate à la demande).
- `profil-artiste` : ajout d'ART-07 (vues de l'IHM du profil artiste).
- `alertes-digests` : ajout d'ALR-09 (liens des alertes vers des vues de l'IHM).

## Périmètre

### Inclus

- Points d'entrée REST et écrans de l'IHM listés ci-dessus, avec contrôle d'accès par `Access` et RLS.
- Producteur planifié des suggestions de l'IA ; vue signal minimale (titre, gravité, versions, impacts).
- Tests et contrôles listés ci-dessus ; mise à jour des fiches durables, de l'arc42 et des diagrammes concernés.

### Exclu

- Webhooks de forge et notifications de pipeline (INV-07-S3 reste en vigueur : aucun point d'entrée non authentifié).
- Modification ou suppression d'un projet (seuls création, rattachement et détachement de dépôt sont demandés).
- Génération des types du client par openapi-typescript (option écartée par le porteur au profit du test de contrat).
- Administration des sources du profil artiste par l'opérateur dans l'IHM (déclaration, conditions d'usage, collecte) : routes conservées pour l'opérateur, hors du constat.
- Réécriture des vues existantes au-delà de ce qu'exigent les nouveaux formulaires.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Administrateur d'espace | crée les projets, rattache et détache leurs dépôts | [DÉCISION] porteur, 2026-11-26 |
| Contributeur d'espace | importe une SBOM ou des manifestes, relance l'import, lance la collecte d'activité | [DÉCISION] porteur, 2026-11-26 (imports) ; [PROPOSITION] (collecte) |
| Membre d'espace (tout rôle) | consulte radar, signal, inventaire, activité | [EXIGENCE] ADR-0016 (Accepté) |
| Utilisateur du profil artiste | choisit ses suivis, consulte opportunités et résumés | [EXIGENCE] ART-02, ART-03 |
| Passerelle IA | produit les suggestions `HOLD` (sortie validée, jamais appliquée) | [DÉCISION] porteur, 2026-11-26 ; RAD-02 |
| Porteur du projet | arbitre les décisions de ce changement | — |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Aucun `@POST`/`@PUT`/`@DELETE` sur `InventoryResource` ni `ProjectResource` ; `importSbom`, `importManifests`, `refresh`, `ajouterDepot`, `detacherDepot` sans appelant en production | [EXISTANT] | `argos-server/src/main/java/io/argos/api/InventoryResource.java`, `inventory/InventoryService.java:91-178`, `inventory/ProjectDirectory.java:91` |
| « L'import manuel existant reste possible » | [EXIGENCE] | INV-08 ; `/docs/specifications/inventaire-sbom.md` |
| Tables d'inventaire en lecture seule pour `argos_app` | [EXISTANT] | `V4__projets_et_inventaires.sql:64-73`, `V19__depots_projet_et_occurrences.sql:92-95` |
| Droits : administrateur pour projets et dépôts, contributeur pour les imports | [DÉCISION] | porteur du projet, 2026-11-26 (question du sprint 7) |
| `ReferentielEspace.suggerer` et `CollecteActivite.collecterProjet` sans appelant en production | [EXISTANT] | `catalog/standard/ReferentielEspace.java:141`, `projectintel/CollecteActivite.java:137` |
| `EtatImpact.peutAller`, `TextesOfficiels.parIdentifiant`, `ImportProjet.importe` sans appelant | [EXISTANT] | `impact/EtatImpact.java:49`, `regintel/TextesOfficiels.java:119`, `inventory/InventoryService.java:58` |
| Producteur planifié des suggestions de l'IA | [DÉCISION] | porteur du projet, 2026-11-26 ; RAD-02-S2 |
| Liens `/signaux/…`, `/espaces/…/radar`, `/profils/artiste/opportunites/…` sans route d'IHM ; IHM en historique « hash » | [EXISTANT] | `alerting/MoteurAlertes.java:275`, `alerting/RappelsRadar.java:274`, `alerting/RappelsOpportunites.java:159`, `argos-ui/src/router.ts` |
| `/` et `/index.html` publics, `ARGOS_PUBLIC_URL` relayée par `compose.yaml` | [EXISTANT] | `application.properties:364`, `compose.yaml:148` |
| Aucune route d'IHM pour `profils/artiste` ni `preferences/rubriques` | [EXISTANT] | `grep -rn "profils/artiste" argos-ui/src` → aucun résultat |
| Contrat par test de contrat ; tests IHM par Vitest, @vue/test-utils, happy-dom | [DÉCISION] | porteur du projet, 2026-11-26 |
| `main.js` sans test ; `preload.js` testé par chargement réel | [EXISTANT] | `argos-desktop/test/`, `argos-desktop/test/electron-factice.js` |
| Liste `TRUNCATE` codée en dur | [EXISTANT] | `argos-server/src/test/java/io/argos/testsupport/TestDb.java` |
| Seuil JaCoCo global (85 % lignes, 70 % branches) | [DÉCISION] | `argos-server/pom.xml`, porteur 2026-11-24 |

## Écarts constatés

- `/docs/specifications/inventaire-sbom.md` affirme que « l'import manuel existant reste possible » et qu'« aucune API ni écran n'administre les dépôts d'un projet » : le premier énoncé n'a aucun point d'entrée en production, le second devient faux avec ce changement.
- `/docs/specifications/alertes-digests.md` consigne qu'une page de l'IHM « reste à créer » pour les liens.
- AUD-ARC-22 demandait aussi de relayer `ARGOS_PUBLIC_URL` : c'est déjà fait (`compose.yaml:148`, `.env.example:34`) ; seul le format des liens et les vues restent à traiter.
- Le critère « chaque module atteint le seuil JaCoCo » n'est pas outillé : la règle JaCoCo ne porte que sur l'ensemble (`BUNDLE`).

## Impact

- **Composants** : `api` (REST), `inventory`, `projectintel`, `catalog` (référentiel), `releaseradar`, `alerting`, `aigateway` (nouveau prompt), IHM `argos-ui`, client `argos-desktop` (tests seulement).
- **Contrats d'interface** : nouvelles routes REST sous `/api/v1/espaces/{espaceId}/projets`, `/api/v1/projects/{projectId}/repositories`, `/api/v1/projects/{projectId}/inventory/{sbom,manifests,refresh}`, `/api/v1/projects/{projectId}/activite/collecte` ; format des liens des alertes (**BREAKING** visible).
- **Données et persistance** : aucune migration ni nouvelle table ; écritures dans les tables d'inventaire existantes.
- **Sécurité, confidentialité, droits** : écritures réservées par rôle via `Access` (refus journalisés), exécutées comme les traitements d'inventaire existants ; la RLS de lecture reste inchangée ; taille de document bornée (`argos.inventory.max-document-bytes`) ; refus journalisés.
- **Exploitation et observabilité** : nouvelle tâche planifiée des suggestions (santé par tâche, ADR-0031, ADR-0036).
- **Documentation durable à mettre à jour** : fiches `inventaire-sbom`, `radar-standards`, `activite-projets`, `profil-artiste`, `alertes-digests` ; arc42 §5, §6 et §8 (tests) ; diagrammes de séquence de l'import manuel.
- **ADR** : ADR-0016 (Accepté), ADR-0035 (Proposé), ADR-0031, ADR-0029, ADR-0019 ; aucune nouvelle décision structurante, l'outillage de test (Vitest) est candidat à une mention dans l'arc42 plutôt qu'à un ADR.

## Conception requise

Complète — le changement touche les droits en base (RLS), un traitement planifié avec IA, le format visible des liens et l'outillage de test de trois sous-projets.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Rôle minimal pour lancer la collecte immédiate d'activité : contributeur proposé, comme les imports | non | porteur du projet |
| Q-2 | Seuil de couverture par module : même valeur que le seuil global (85 % lignes, 70 % branches) proposée | non | porteur du projet |
