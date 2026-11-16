# Fiche capacité : `inventaire-sbom`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `INV` |
| Spec de référence | `/openspec/specs/inventaire-sbom/spec.md` — absente : capacité non livrée |
| Changement en cours | [`formaliser-chaine-securite-poc`](/openspec/changes/formaliser-chaine-securite-poc/specs/inventaire-sbom/spec.md) — proposé, non archivé |
| Statut de la capacité | **proposée**, implémentée dans `argos-server` (changement non archivé) : paquets `io.argos.scm`, `io.argos.inventory` |
| Composants (arc42 §5) | `inventory`, `scm-connector`, adaptateur PostgreSQL |

Tout le contenu de cette fiche porte le statut [PROPOSITION] (changement `formaliser-chaine-securite-poc`) sauf mention contraire.

## Objectif

Répondre de façon fiable à la question « quels composants, et dans quelles versions, chaque projet embarque-t-il ? », dépendances transitives comprises, pour permettre la corrélation déterministe. Source : ADR-0011 (contexte).

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| CI de la branche principale des projets suivis | produit la SBOM CycloneDX | [PROPOSITION] — ADR-0011 |
| GitLab / GitHub (via `scm-connector`) | donne accès aux SBOM et manifestes | [PROPOSITION] — arc42 §5.3 ; baseline v2.3 §2.4 |
| Utilisateur technique, Tech Lead | consultent l'inventaire et sa fraîcheur | [PROPOSITION] — arc42 §1.3 |

## Périmètre et exclusions

- **Inclus** : import de la SBOM de la branche principale, PURL et versions, dépendances transitives, âge de l'inventaire, repli sur les manifestes, rejet d'une SBOM non conforme.
- **Exclu** : génération de la SBOM (responsabilité de la CI des projets), Dependency-Track (ADR-0012, optionnel), analyse de licences, autres branches que la branche principale.

## Préconditions et déclencheurs

- Le projet est suivi et rattaché à au moins un dépôt (glossaire « Repository »). La relation projet ↔ dépôts n'est pas tranchée [QUESTION — glossaire, termes ambigus].
- Déclencheur de l'import : non documenté (webhook de pipeline, interrogation périodique ou import manuel) [QUESTION Q-INV-1].

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Entrée : SBOM CycloneDX | composants, PURL, versions, graphe de dépendances, date | document CycloneDX JSON exploitable, schéma 1.5 ou 1.6 (décision du 2026-11-16, Q-7) | [PROPOSITION] |
| Entrée : manifeste de dépendances | dépendances déclarées | utilisé seulement sans SBOM | [PROPOSITION] |
| Sortie : inventaire du projet | composants, PURL, version, direct/transitif, origine (SBOM ou manifeste), date de la source | un inventaire de référence par projet | [PROPOSITION] |
| Sortie : échec d'import | motif | consultable | [PROPOSITION] |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| La SBOM de la branche principale fait référence | INV-01 | ADR-0011 | [PROPOSITION] |
| Les dépendances transitives sont incluses et distinguées | INV-02 | ADR-0011 ; baseline v2.3 §4, §6 | [PROPOSITION] |
| L'âge de la SBOM et l'absence de SBOM sont visibles | INV-03 | ADR-0011 | [PROPOSITION] |
| Sans SBOM, repli sur les manifestes, origine affichée | INV-04 | ADR-0011 | [PROPOSITION] |
| SBOM non conforme refusée, motif visible | INV-05 | arc42 §8.3, §11.2 | [PROPOSITION] |

## Droits d'accès et restrictions

L'inventaire appartient au projet et suit le cloisonnement par espace (ADR-0016, Proposé) : seuls les utilisateurs autorisés sur le projet le consultent. Les exigences de cloisonnement des impacts sont portées par COR-08 ; le cloisonnement de l'inventaire lui-même relève de la capacité de contrôle d'accès (lot ultérieur) [QUESTION].

## Effets de bord observables

- remplacement de l'inventaire de référence à chaque nouvelle SBOM ;
- nouvelle corrélation des signaux existants pour le projet après changement d'inventaire — attendu par la chaîne baseline v2.3 §3, déclenchement non spécifié [QUESTION Q-INV-2] ;
- enregistrement des échecs d'import.

## Qualités mesurables

| Qualité | Cible | Source | Statut |
|---|---|---|---|
| Couverture SBOM des projets pilotes | 2 projets pilotes avec SBOM fraîche, transitives incluses | baseline v2.3 §6 critère 2 | [PROPOSITION] |
| Réponse « projets touchés » | < 5 min | ADR-0011, traçabilité | [QUESTION] — cible non validée |
| Seuil d'obsolescence d'une SBOM | 30 jours après la date de génération | ADR-0011 ; décision du 2026-11-16 (Q-3) | [DÉCISION] |

## Diagrammes

### DF-INV-1 — Import d'une SBOM

- **Question** : comment l'inventaire de référence d'un projet est-il remplacé par une nouvelle SBOM, et que se passe-t-il si elle est invalide ?
- **Type** : UML séquence
- **Portée** : cible proposée ; déclencheur non documenté (Q-INV-1)
- **Exigences** : INV-01, INV-02, INV-05

```mermaid
sequenceDiagram
    participant CI as «Software System» CI du projet (branche principale)
    participant SCM as scm-connector
    participant Inv as inventory
    participant DB as PostgreSQL
    participant Imp as impact-engine

    CI-->>SCM: SBOM CycloneDX disponible (déclencheur à définir)
    SCM->>Inv: transmettre la SBOM et le projet
    Inv->>Inv: valider le document CycloneDX
    alt SBOM exploitable
        Inv->>DB: remplacer l'inventaire de référence (PURL, version, direct ou transitif)
        Inv->>DB: enregistrer la date et l'origine SBOM
        Inv-)Imp: inventaire du projet modifié
    else SBOM non conforme
        Inv->>DB: enregistrer l'échec d'import et son motif
        Note over Inv,DB: aucun composant du fichier n'est ajouté (INV-05)
    end
```

L'envoi vers `impact-engine` est asynchrone et matérialise l'effet de bord « nouvelle corrélation » dont le déclenchement reste à spécifier (Q-INV-2). Le devenir de l'inventaire précédent après un import refusé n'est pas documenté (Q-INV-3).

### DF-INV-2 — Origine de l'inventaire d'un projet

- **Question** : d'où provient l'inventaire utilisé pour un projet, et avec quelle conséquence sur la confiance ?
- **Type** : organigramme (flowchart)
- **Portée** : cible proposée
- **Exigences** : INV-01, INV-03, INV-04, COR-06

```mermaid
flowchart TD
    P["Projet suivi"] --> S{"SBOM CycloneDX de la<br/>branche principale importée ?"}
    S -- oui --> O{"SBOM générée depuis<br/>plus de 30 jours ? (Q-3)"}
    O -- non --> R1["Inventaire SBOM<br/>niveau exact possible (COR-02)"]
    O -- oui --> R2["Inventaire SBOM obsolète<br/>niveau autre que exact (COR-06)"]
    S -- non --> M{"Manifeste lisible ?"}
    M -- oui --> R3["Inventaire de repli « manifeste »<br/>niveau autre que exact (INV-04, COR-06)"]
    M -- non --> R4["Aucun inventaire<br/>absence signalée (INV-03-S2)"]
```

Le diagramme relie l'origine de l'inventaire au niveau d'impact défini par `correlation-impact`. Le comportement d'un projet sans aucun inventaire vis-à-vis de la corrélation (aucun impact calculable) est une conséquence logique, non une règle écrite dans les sources.

## Critères d'acceptation

- Scénarios `INV-01-S1` à `INV-05-S1` de la spec.
- Baseline v2.3 §4 : « SBOM CycloneDX de deux projets, PURL et versions transitives consultables ».

## Interprétations introduites par un changement

Interprétations validées par le porteur du projet le 2026-11-16 [DÉCISION].

- INV-01-S2 : un composant absent de la nouvelle SBOM disparaît de l'inventaire de référence (remplacement complet) ;
- INV-02 : la distinction direct/transitif est affichée — la source exige la couverture des transitives, pas l'affichage de la distinction.

Interprétation introduite par l'implémentation, à valider [PROPOSITION] :

- INV-03 : une SBOM sans date de génération est traitée comme obsolète, faute de pouvoir établir sa fraîcheur ; la corrélation lui attribue au mieux le niveau `probable`. La décision Q-3 ne fixe que le seuil de 30 jours après la date de génération.

## Écarts constatés

- Le code de `argos-server` implémente les règles de cette fiche (`io.argos.inventory`, `io.argos.scm` ; tests `argos-server/src/test/java/io/argos/chain/InventoryTest.java` et `DocumentParsingTest.java`). La consultation de l'inventaire s'exécute sous le rôle applicatif `argos_app` : la RLS des tables `project`, `inventory`, `component` et `inventory_import_failure` limite la lecture aux membres de l'espace du projet.
- L'indicateur `obsolete` de la consultation ne vaut vrai que pour une SBOM ; un inventaire de repli (manifestes) n'est pas une SBOM, mais la corrélation ne lui accorde jamais le niveau `exact`.
- Les niveaux des impacts sont calculés à la corrélation et enregistrés : une SBOM qui dépasse ensuite les 30 jours ne fait pas recalculer les impacts existants tant qu'aucun nouveau signal ni inventaire ne déclenche de corrélation (aucune planification, tâche 6.3).
- Les adaptateurs GitLab et GitHub ne sont testés que sur des réponses simulées (tâche 3.2).

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-INV-1 | Qu'est-ce qui déclenche l'import d'une SBOM ? | conception `scm-connector` | Tech Lead |
| Q-INV-2 | Un changement d'inventaire relance-t-il la corrélation des signaux existants ? | COR-01 | architecte |
| Q-INV-3 | Après un import refusé, l'inventaire précédent reste-t-il la référence ? | INV-05 | Tech Lead |
| Q-INV-4 | Quels formats de manifeste le repli doit-il lire ? | INV-04 | Tech Lead |
| Q-3 (changement) | Âge à partir duquel une SBOM est obsolète ? | répondue le 2026-11-16 : obsolète 30 jours après sa date de génération [DÉCISION] | sécurité / Tech Lead |
| Q-7 (changement) | Versions du schéma CycloneDX acceptées ? | répondue le 2026-11-16 : CycloneDX 1.5 et 1.6, format JSON [DÉCISION] | Tech Lead |

## Traçabilité

Voir la matrice du changement : [`tasks.md`](/openspec/changes/formaliser-chaine-securite-poc/tasks.md#matrice-de-traçabilité). Les scénarios INV-01-S1 à INV-05-S1 sont vérifiés par `argos-server/src/test/java/io/argos/chain/InventoryTest.java` et `DocumentParsingTest.java`. La récupération GitLab et GitHub n'est testée que sur des réponses simulées (tâche 3.2 ouverte).
