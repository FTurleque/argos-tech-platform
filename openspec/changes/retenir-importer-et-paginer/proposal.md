# Proposition : Conserver les journaux pour une durée bornée, n'importer que l'inventaire modifié et paginer les listes

## Why — Pourquoi

Les tables de journal, d'échec et d'historique grossissent sans limite. Seul l'historique d'activité des projets est purgé (`projectintel/PurgeActivite.java`). Un sujet OIDC sans compte Argos écrit une ligne de refus à chaque requête (AUD-SEC-03). Les opportunités sans date limite, c'est-à-dire tous les éléments de flux RSS, restent restituées pour toujours (AUD-PER-18). L'import nocturne réécrit et recorrèle chaque inventaire même quand la SBOM ou les manifestes n'ont pas changé (AUD-PER-19), et il insère les composants un par un (AUD-PER-20). Plusieurs listes de l'API sont renvoyées en entier (AUD-PER-10) [EXISTANT — /docs/audit/2026-11-21/findings.md, blocs AUD-SEC-03, AUD-PER-18, AUD-PER-19, AUD-PER-20, AUD-PER-10, vérification du 21/11 à `b142695`, preuves relues le 2026-11-26]. Le sprint 9 fixe le critère de sortie : chaque table de journal, d'échec ou d'historique, opportunités comprises, a une purge planifiée avec une rétention configurée ; l'import nocturne ne réécrit ni ne recorrèle un inventaire inchangé et insère par lot ; toutes les listes renvoyées par l'API sont paginées [EXIGENCE — /docs/audit/2026-11-21/sprints.md, « Sprint 9 », périmètre réparti par le porteur le 2026-11-26 : AUD-PER-09 et AUD-PER-25 relèvent du sprint 10].

## What Changes — Changements

- Une purge quotidienne unique applique des règles de conservation que chaque module déclare pour ses propres tables : durée réglable par table, suppression par lots, échec d'une règle journalisé avec sa cause et compté, sans arrêter les autres règles (EXP-17).
- Des règles de conservation pour les journaux des refus, des appels MCP et des appels IA, les compteurs de quotas IA, les synchronisations terminées et leurs tentatives, les avis mis à l'écart, les événements d'exploitation, les échecs d'import d'inventaire, les versions périmées des avis, les actualités et les opportunités échues (EXP-17).
- Le journal des refus est borné par auteur, compte ou sujet OIDC, sur une fenêtre glissante d'une minute. Au-delà, le refus est toujours appliqué mais n'est plus écrit ; il est compté dans une métrique (ACC-15).
- Une opportunité sans date limite n'est restituée que pendant un âge maximal réglable, compté depuis sa première collecte (ART-08).
- **BREAKING** `GET /api/v1/profils/artiste/opportunites` est paginée : la réponse ajoute `page`, `size` et `total`, et `items` ne contient plus que la page demandée (PERF-03).
- L'import depuis un dépôt calcule une empreinte du document lu, SBOM ou manifestes. Un document identique au dernier import du dépôt donne `KEPT`, sans écriture ni recorrélation (INV-14, INV-09 modifiée).
- Les composants d'un import sont écrits par lot : un nombre d'instructions SQL indépendant du nombre de composants. Un composant inchangé n'est pas réécrit (INV-14).
- Un format de pagination unique pour toute l'API : paramètres `page` et `size`, réponse `{items, page, size, total}`, 50 éléments par défaut et 200 au plus (PERF-03, ADR-0040 proposé).
- **BREAKING** Ces opérations, qui rendaient un tableau, rendent une page : `GET /api/v1/ops/sources/{id}/rejected` et `GET /api/v1/ops/sources/{id}/events`, triées de la plus récente à la plus ancienne ; `GET /api/v1/projects/{projectId}/inventory/failures`, même tri ; `GET /api/v1/projects/{projectId}/impacts` (PERF-03).
- **BREAKING** Dans `GET /api/v1/projects/{projectId}/inventory`, le champ `components` devient une page de composants, paramètres `page` et `size` (PERF-03).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Non applicable : chaque comportement complète une capacité existante.

### Modified Capabilities — Capacités modifiées

- `exploitation-plateforme` : ajout d'EXP-17, conservation bornée des données journalisées.
- `performance-api-mcp` : ajout de PERF-03, listes de l'API paginées au format commun.
- `comptes-acces` : ajout d'ACC-15, journal des refus borné par auteur.
- `inventaire-sbom` : ajout d'INV-14, import inchangé et écriture par lot ; INV-09 modifiée, avec le scénario S3 étendu au document inchangé.
- `profil-artiste` : ajout d'ART-08, âge maximal d'une opportunité sans date limite.

## Périmètre

### Inclus

- Le mécanisme de conservation et ses règles pour les tables listées ci-dessus, une migration V26 (empreinte du dernier import d'un dépôt), le bornage du journal des refus.
- Le format de pagination commun : type partagé, lecture des paramètres REST, design et ADR-0040 (Proposé). Il est appliqué aux listes qui grandissent avec les données collectées, importées ou journalisées.
- L'empreinte et l'écriture par lot de l'import d'inventaire.

### Exclu

- AUD-PER-09 (index manquants) et AUD-PER-25 (`updated_at` de la corrélation) : sprint 10 [DÉCISION — répartition du porteur, 2026-11-26]. Ce changement ne crée aucun index sur `project_impact` et ne touche ni à la corrélation, ni au moteur d'alertes, ni au radar.
- Recorrélation différentielle (seulement les paquets modifiés) : elle relève du moteur de corrélation (sprint 10). Un inventaire modifié reste recorrélé en entier.
- Pagination de `/me/veille` et de la recherche : le sprint 10 y applique le format commun. Listes du radar : sprint 10.
- Listes bornées par la configuration : catalogue, mises en page, profils, rubriques, espaces, membres et invitations, abonnements, vues enregistrées, sources, domaines. Leur taille ne dépend pas de l'historique.
- Listes déjà bornées par `limite` (alertes, fiches réglementaires, actualités web) : elles gardent leur paramètre `limite` [PROPOSITION — recommandation soumise au porteur le 2026-11-26 ; voir design.md, D6].
- Les alertes et les éléments de résumé : la clé d'idempotence d'une alerte immédiate est la ligne d'alerte elle-même (`alerte_immediate_uk`, V8). La purger rouvrirait une alerte déjà envoyée. Purge reportée (design.md, écarts).
- Les historiques métier attachés à une entité vivante (`project_impact_transition`, `standard_historique`, `fiche_historique`) : ils disparaissent avec leur entité et ne croissent qu'avec des décisions humaines.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Exploitant de la plateforme | Règle les durées de conservation ; lit les listes d'exploitation paginées | [PROPOSITION] |
| Utilisateur du profil artiste | Reçoit des opportunités sans élément périmé, page par page | [PROPOSITION] |
| Personne disposant d'un compte du fournisseur d'identité, sans invitation | Ne peut plus faire croître le journal des refus sans borne | [PROPOSITION] |
| Sprint 10 (session parallèle) | Applique le format de pagination à `/me/veille` et à la recherche | [DÉCISION — consigne du porteur, 2026-11-26] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Aucune purge des journaux de refus, MCP, IA | [EXISTANT] | `audit/JournalRefus.java`, `audit/JournalAppelsMcp.java`, `audit/JournalAppelsIa.java` ; seule purge : `projectintel/PurgeActivite.java` |
| Une ligne de refus par requête d'un sujet sans compte | [EXISTANT] | `api/GestionErreurs.java` (`inscriptionRefusee`), `mcp/GardeAppels.java` |
| Opportunités sans date limite restituées sans fin | [EXISTANT] | `signal/opportunite/Opportunites.java` (`date_limite IS NULL OR …`), `CollecteOpportunites.lire` (élément de flux sans date limite) |
| Import réécrit sans comparaison | [EXISTANT] | `inventory/InventoryService.java` (`lire`, `lireSbom`), `inventory/InventoryStore.java` (`remplacer`, `ecrireOccurrences`) |
| Composants insérés un par un | [EXISTANT] | `inventory/InventoryStore.java`, boucle de `ecrireOccurrences` |
| Listes non paginées | [EXISTANT] | `api/SourceOperationsResource.java`, `ingestion/SyncStore.java` (`failures`, `events`), `api/InventoryResource.java` (`failures`, `inventory`), `api/ProjectResource.java` (`impacts`) |
| Formats de pagination existants | [EXISTANT] | `page`/`size` → `{items, page, size, total}` (`ProjectResource.projects`, `InventoryResource.components`, `SignalResource`) ; `page`/`taille` → `{elements, page, taille, total}` (recherche) ; `limite` → tableau (alertes, fiches, actualités) |
| Planification et exclusion en base | [EXIGENCE] | ADR-0031 (Accepté) |
| Noyau commun sans nom de table ni règle métier | [EXIGENCE] | ADR-0037 (Accepté) |
| Aucune erreur imprévue absorbée par un traitement sans appelant | [PROPOSITION] | ADR-0039 (Proposé), critère 3 |
| Durées de conservation par défaut | [QUESTION] | Aucune source ne les fixe ; l'audit suggère « 90 jours à 1 an, paramétrable ». Les valeurs par défaut sont proposées dans design.md (D3) et réglables. |
| Âge maximal d'une opportunité sans date limite | [QUESTION] | Aucune source ; 90 jours proposés (design.md, D5), réglables |

## Écarts constatés

- La spec `inventaire-sbom` (INV-09) impose une recorrélation après chaque import réussi. Le code recorrèle aussi un inventaire inchangé. Ce changement précise INV-09-S3.
- `docs/specifications/collecte-avis-securite.md` et la zone d'échec COL-08 ne fixent aucune durée de conservation des avis mis à l'écart.

## Niveau de conception

**Complète** : changement transverse (nouveau mécanisme de conservation, nouveau module technique), modèle de données (migration V26), contrat d'API (pagination commune, ruptures signalées) et performance.

## Impact

- Code : `io.argos.conservation` (nouveau module technique), `io.argos.commun` (`Page`, `Pagination`), `audit`, `aigateway`, `ingestion`, `signal`, `inventory`, `api`.
- Données : migration V26 (colonne d'empreinte de `repository_inventory`). Les suppressions planifiées retirent les lignes plus anciennes que la durée de conservation.
- API : opérations rendues paginées (ruptures listées ci-dessus) ; IHM : appel des opportunités adapté.
- Documentation : ADR-0040 et ADR-0041 (Proposés), arc42 §5, §8 et §9, fiches des capacités modifiées.
