# Conception : personnaliser les profils de veille et la mise en page

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] (légende : /docs/specifications/README.md#statuts-des-informations). -->

**Niveau de conception** : allégée — règles fonctionnelles et responsabilités fixées ; schéma de données et API détaillés à concevoir avec les premières tâches d'implémentation.

## Contexte technique

Aucune implémentation métier [EXISTANT — `argos-server/src/main/java/io/argos/` ne contient que des `package-info.java` et `api/SystemInfoResource.java` ; `argos-ui/src/` ne contient qu'une page d'état]. Les frontières de modules sont vérifiées par `argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java` [EXISTANT — test non encore exécuté]. Motivation : voir `proposal.md`.

## Exigences couvertes

| Exigence | Section(s) de conception | Diagramme(s) |
|---|---|---|
| AFF-01, AFF-03, AFF-04 | Composants ; Modèle de domaine | DC-1 |
| AFF-02 | Traitements et interactions ; D1 | DC-2 |
| AFF-05 | Stratégie de vérification | — |
| PRF-01, PRF-03, PRF-04 | Composants ; Modèle de domaine ; D2 | DC-1 |
| PRF-02, PRF-05, PRF-06 | Traitements et interactions ; Sécurité et isolation | DC-2 |

## Objectifs et non-objectifs

**Objectifs :**
- séparer ce qu'un utilisateur voit (droits, profils, garde-fous) de la façon dont il le voit (mise en page) ;
- permettre l'ajout d'un profil ou d'une mise en page sans toucher aux existants.

**Non-objectifs :**
- concevoir les profils `artiste`, `comedien`, `politique` ;
- fixer le format exact du manifeste de profil (première tâche d'implémentation).

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| `catalog` | publier les profils (manifestes) et la liste des mises en page | connaître les préférences d'un utilisateur | modifié |
| `workspace` | enregistrer les profils actifs et la mise en page de chaque utilisateur ; profils proposés par une équipe | accorder un droit | modifié |
| `impact-engine` | exécuter les stratégies de rapprochement déclarées par les profils actifs | appeler l'IA pour décider d'un impact | modifié |
| `signal` | porter le noyau commun d'un signal et les rubriques ajoutées par un profil | interpréter les rubriques d'un profil | modifié |
| `API & Security` | exposer préférences et catalogue ; filtrer par droits puis par profils | appliquer une règle d'affichage | modifié |
| `mcp-server` | filtrer les réponses par profils actifs et garde-fous | contourner un garde-fou | modifié |
| IHM `argos-ui` — couche contenu | construire les modèles de vue à partir de l'API | dépendre d'une mise en page | nouveau |
| IHM `argos-ui` — couche mise en page | présenter les modèles de vue selon le style choisi | appeler l'API, filtrer ou omettre une mention obligatoire | nouveau |

## Modèle de domaine et invariants

### DC-1 — Profils, mises en page et préférences

- **Question** : quels concepts relient un utilisateur à ses profils et à sa mise en page, sans toucher à ses droits ?
- **Type** : UML classes
- **Portée** : cible proposée
- **Exigences** : AFF-01, AFF-03, AFF-04, PRF-01, PRF-03, PRF-04, PRF-05

```mermaid
classDiagram
    class Utilisateur {
        identifiant
    }
    class PreferencesUtilisateur {
        miseEnPage
        profilsActifs
    }
    class ProfilVeille {
        identifiant
        libelle
        version
        etat
    }
    class StrategieRapprochement {
        identifiant
        deterministe
    }
    class GardeFou {
        type
    }
    class Rubrique {
        identifiant
        libelle
    }
    class MiseEnPage {
        identifiant
        libelle
        etat
    }
    class Role {
        nom
    }
    Utilisateur "1" --> "1" PreferencesUtilisateur : possède
    PreferencesUtilisateur "*" --> "1..*" ProfilVeille : active
    PreferencesUtilisateur "*" --> "0..1" MiseEnPage : choisit
    ProfilVeille "1" *-- "1..*" Rubrique : déclare
    ProfilVeille "1" --> "1..*" StrategieRapprochement : utilise
    ProfilVeille "1" *-- "0..*" GardeFou : impose
    Utilisateur "*" --> "*" Role : autorisations
```

Les préférences (profils, mise en page) et les rôles sont deux associations distinctes : aucune ne dérive de l'autre. Sans mise en page choisie, `classique` s'applique (cardinalité `0..1`).

Invariants :

- **INV-1** : un utilisateur a au moins un profil actif (PRF-01).
- **INV-2** : un profil actif ou une mise en page choisie est publié au catalogue, sinon la préférence est ignorée et le défaut s'applique (AFF-04).
- **INV-3** : aucune préférence ne modifie les rôles d'un utilisateur (PRF-05).

## Traitements et interactions

### DC-2 — Restitution de la veille d'un utilisateur

- **Question** : dans quel ordre droits, profils, garde-fous et mise en page s'appliquent-ils ?
- **Type** : UML séquence
- **Portée** : cible proposée
- **Exigences** : AFF-02, PRF-02, PRF-05, PRF-06

```mermaid
sequenceDiagram
    actor U as Utilisateur
    participant C as argos-ui couche contenu
    participant A as API and Security
    participant W as workspace
    participant I as impact-engine
    participant M as argos-ui couche mise en page
    U->>C: ouvre Argos
    C->>A: GET veille
    A->>A: filtre par rôles et espaces (RBAC + RLS)
    A->>W: profils actifs de l'utilisateur
    W-->>A: developpeur, expert-metier
    A->>I: éléments relevant de ces profils
    I-->>A: éléments et impacts, niveaux de confiance
    A->>A: applique les garde-fous des profils
    A->>A: fusionne sans doublon, ajoute les rubriques
    A-->>C: éléments autorisés, avec mentions obligatoires
    C->>C: construit les modèles de vue
    C->>M: modèles de vue et mise en page choisie
    M-->>U: affichage
```

Les droits s'appliquent avant les profils, et les garde-fous avant la fusion : la couche mise en page ne reçoit que des éléments déjà autorisés, déjà filtrés et accompagnés de leurs mentions. Elle ne peut donc ni montrer un élément de plus, ni en retirer une mention (AFF-02).

## États et transitions

Non applicable — les cycles de vie de publication d'un profil ou d'une mise en page (brouillon, publié, retiré) seront modélisés avec la tâche du catalogue ; seul l'état « retiré » est utilisé par AFF-04.

## Contrats d'interface

Non applicable à ce stade — l'OpenAPI des préférences et du catalogue est à écrire (tâche 2.1). Opérations pressenties : lecture du catalogue des mises en page et des profils ; lecture et modification des préférences de l'utilisateur courant.

## Persistance et intégrité

Non applicable à ce stade — les préférences seront une table du module `workspace` ; les manifestes de profils, des ressources versionnées du module `catalog`. À concevoir avec les tâches 2.1 et 3.1.

## Transactions, concurrence et idempotence

Une modification de préférences est une opération idempotente qui remplace l'ensemble des profils actifs ou la mise en page ; la dernière écriture l'emporte.

## Sécurité et isolation

- les préférences ne figurent jamais dans les décisions d'autorisation (INV-3, PRF-05) ;
- un manifeste de profil est une donnée de configuration publiée par l'administrateur plateforme : il ne peut déclarer que des connecteurs et stratégies existants dans le code ;
- les garde-fous sont appliqués par le serveur, jamais par l'IHM (PRF-06).

## Gestion des erreurs et reprise

Mise en page ou profil inconnu : refus explicite (AFF-01-S2, PRF-01-S3). Mise en page retirée : défaut `classique` avec message (AFF-04-S2).

## Observabilité

Compteurs agrégés d'utilisateurs par profil et par mise en page ; aucun suivi individuel (dossier v2.4 §8.10).

## Performance et stratégie de mesure

[QUESTION] Aucune cible propre à ce changement ; la cible p95 < 500 ms du dossier v2.4 §10 s'applique à la restitution de la veille, à mesurer avec deux profils actifs.

## Compatibilité, migration et retour arrière

Non applicable — aucune donnée existante.

## Décisions, alternatives et compromis

### D1 — Deux couches dans l'IHM

- **Statut** : [PROPOSITION]
- **Choix** : couche contenu commune, couche mise en page par style.
- **Justification** : contenu et mentions identiques garantis par construction (AFF-02).
- **Alternatives écartées** : thèmes purement graphiques — ne permettent pas les structures maquettées ; une IHM par style — logique dupliquée.
- **Compromis** : chaque nouvel écran est décliné dans toutes les mises en page.
- **ADR** : ADR-0023 (Proposé).

### D2 — Profil = pack déclaratif et stratégies enfichables

- **Statut** : [PROPOSITION]
- **Choix** : manifeste de profil dans `catalog`, stratégies déterministes dans `impact-engine`.
- **Justification** : ajout d'un public sans toucher au cœur (PRF-04), rapprochement explicable (ADR-0013).
- **Alternatives écartées** : profils codés en dur ; une instance par public ; rapprochement confié à l'IA.
- **Compromis** : la taxonomie des signaux devient extensible.
- **ADR** : ADR-0024 (Proposé).

### D3 — Au moins un profil actif

- **Statut** : [PROPOSITION]
- **Choix** : refuser la désactivation du dernier profil.
- **Justification** : une veille vide n'a pas de sens et masquerait des alertes.
- **Alternatives écartées** : profil vide autorisé ; profil implicite caché.
- **Compromis** : —
- **ADR** : non : décision locale.

## Risques

- Multiplication des mises en page à maintenir → catalogue limité aux quatre initiales jusqu'à retour d'usage.
- Profils futurs grand public (produit ouvert, ADR-0025) → hébergement, comptes, licence et RGPD à traiter avant le premier pack hors entreprise.
- Profil politique : risque de partialité → garde-fous de neutralité obligatoires dans son futur pack (ADR-0024 point 7).

## Écarts code / tests / specs / documentation

La taxonomie fermée du dossier v2.4 §8.2 n'est pas versionnée dans `docs/architecture/` : à intégrer en arc42 §8 (tâche 4.1).

## Stratégie de vérification

| Exigence / scénario | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| AFF-01-S1, AFF-01-S2, AFF-04-S1, AFF-04-S2 | intégration API | tests des préférences | à écrire |
| AFF-02-S1, AFF-02-S2 | E2E IHM | même jeu de données affiché dans les quatre mises en page, contenu comparé | à écrire |
| AFF-03-S1 | E2E | préférence modifiée sur le web, lue par le desktop | à écrire |
| AFF-05-S1 | revue | audit d'accessibilité par mise en page | à écrire |
| PRF-01-S1 à PRF-01-S3 | intégration API | tests des préférences | à écrire |
| PRF-02-S1, PRF-02-S2 | intégration | veille d'un utilisateur à deux profils | à écrire |
| PRF-03-S1, PRF-03-S2 | intégration | catalogue initial ; rapprochement `developpeur` | à écrire |
| PRF-04-S1 | intégration | publication d'un profil de démonstration | à écrire |
| PRF-05-S1 | intégration | tests inter-espaces | à écrire |
| PRF-06-S1, PRF-06-S2 | intégration + contrat MCP | fiche non validée invisible partout | à écrire |

## Documentation durable impactée

- `/docs/specifications/affichage-personnalise.md`, `/docs/specifications/profils-veille.md`, `/docs/specifications/README.md` ;
- arc42 §5 (responsabilités de `catalog`, `workspace`, IHM) et §8 (taxonomie extensible) ; glossaire (« profil de veille », « mise en page ») ;
- ADR-0023, ADR-0024.

## Questions ouvertes

Aucune autre que celles de la proposition.
