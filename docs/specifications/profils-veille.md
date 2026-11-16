# Fiche capacité : `profils-veille`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `PRF` |
| Spec de référence | `/openspec/specs/profils-veille/spec.md` — absente tant qu'aucun changement n'est archivé |
| Changements en cours | [`personnaliser-profils-et-affichage`](/openspec/changes/personnaliser-profils-et-affichage/proposal.md) (en cours d'implémentation) |
| Statut de la capacité | proposée ; catalogue des profils et profils actifs implémentés |
| Composants (arc42 §5) | `catalog`, `workspace`, `impact-engine`, `signal`, `API & Security`, `mcp-server` |

## Objectif

[PROPOSITION — `personnaliser-profils-et-affichage`] Permettre à chaque utilisateur de choisir un ou plusieurs **profils de veille**. Un profil décide de ce qu'Argos surveille pour lui et de la façon dont une information est jugée le concerner. Les deux premiers profils sont ceux du dossier v2.4 ; d'autres publics pourront être servis plus tard (demande du porteur, 2026-11-15).

Un profil n'est pas un rôle : il ne donne aucun droit (ADR-0016, PRF-05).

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Utilisateur | active un ou plusieurs profils | [PROPOSITION] |
| Administrateur d'espace | propose des profils par défaut à son équipe | [PROPOSITION] |
| Administrateur plateforme | publie un profil au catalogue | [PROPOSITION] |
| Expert métier | valide les éléments soumis à garde-fou | [PROPOSITION] — ADR-0018 |
| DPO | revoit tout profil concernant des personnes | [PROPOSITION] — ADR-0024 |

## Périmètre et exclusions

Inclus : catalogue, profils actifs, union des veilles, deux profils initiaux, ajout d'un profil, garde-fous. Exclus : la réalisation des profils futurs, chacun relevant de son propre changement.

### Profils initiaux

| Profil | Sources | Objets suivis | Rapprochement | Garde-fous | Statut |
|---|---|---|---|---|---|
| `developpeur` | OSV, NVD, KEV, EPSS, CERT-FR, éditeurs, endoflife.date | projets, composants (SBOM, PURL) | PURL + plage de versions, déterministe | IA sans décision d'impact | [PROPOSITION] — ADR-0010 à ADR-0013 |
| `expert-metier` | Légifrance, net-entreprises, BOSS, DGCL, DGAFP, caisses de retraite | domaines métier, modules produits | nature, NOR, codes modifiés, dictionnaire métier | validation par l'expert avant diffusion ; aucun avis juridique | [PROPOSITION] — ADR-0018 |

### Manifeste de profil

[EXISTANT — `argos-server/src/main/java/io/argos/catalog/profil/`] Un profil est décrit par un manifeste JSON `catalog/profils/<identifiant>.json`, au format 1 :

| Champ | Contenu | Contrôle au démarrage |
|---|---|---|
| `formatManifeste` | version du format | vaut 1 |
| `identifiant` | minuscules, chiffres et tirets | égal au nom du fichier, unique |
| `version`, `libelle`, `description` | version du pack, textes affichés | renseignés |
| `categoriesSources`, `connecteurs` | sources surveillées et connecteurs de collecte | non vides |
| `rubriques` | `identifiant` et `libelle` de chaque rubrique | au moins une, sans doublon |
| `typesObjetsSuivis` | objets auxquels les signaux sont rapprochés | non vide |
| `strategies` | `identifiant` et `deterministe` | au moins une, toutes déterministes (ADR-0013) |
| `gardeFous` | `type` et `description` | type parmi `VALIDATION_HUMAINE`, `IA_SANS_DECISION`, `AUCUN_AVIS_JURIDIQUE`, `AUCUN_AVIS_ELIGIBILITE`, `SOURCES_VERIFIEES` |
| `listes` (facultatif) | listes fermées des valeurs suivies : `typeObjet` et `valeurs` (`identifiant`, `libelle`) | type déclaré dans `typesObjetsSuivis`, une liste par type, non vide, valeurs sans doublon |

Les manifestes publiés sont listés par `argos.catalogue.profils.manifestes` ; un profil listé dans `argos.catalogue.profils.retires` n'est plus publié. Un manifeste invalide empêche le serveur de démarrer. La confrontation des connecteurs et des stratégies au code attend leur implémentation (tâche 3.3).

### Exemples de profils futurs (non engagés)

| Profil | Sources possibles | Objets suivis | Rapprochement pressenti | Garde-fous pressentis |
|---|---|---|---|---|
| `artiste` (publié, fiche [`profil-artiste`](profil-artiste.md)) | data.culture.gouv.fr (Licence Ouverte Etalab) et flux RSS/Atom publics d'organismes | discipline, territoire, échéances | discipline + territoire + date limite de candidature | sources aux conditions d'usage vérifiées ; aucun avis d'éligibilité |
| `comedien` | annonces de casting, convention collective et règles de l'intermittence, festivals, offres de formation | spécialités, région, agenda | spécialité + région + date | sources publiques ; aucune donnée personnelle de tiers |
| `politique` | agenda et textes des assemblées, Journal officiel, rapports publics, consultations | thèmes, commissions, territoires | thème + institution + étape de procédure | sources institutionnelles, pluralité, aucune recommandation ni classement partisan par l'IA |

Ces exemples servent à vérifier que le modèle tient ; ils ne sont pas des exigences. Argos étant un produit ouvert à tous les publics ([DÉCISION] — ADR-0025, 2026-11-16), ils sont dans la cible ; leur réalisation suppose encore une revue des sources (licences, conditions d'usage) et des données personnelles.

## Préconditions et déclencheurs

Utilisateur authentifié ; profil publié au catalogue.

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Activation / désactivation | `PUT /api/v1/preferences/profils` `{"profilsActifs": [...]}` : remplace l'ensemble | publié au catalogue, sinon refus 400 `PROFIL_INCONNU` ; ensemble vide refusé, 400 `AU_MOINS_UN_PROFIL` (PRF-01) | [EXISTANT] — `PreferencesResource`, `PreferencesResourceTest` |
| Catalogue | `GET /api/v1/catalogue/profils` : identifiant, nom, description, version, rubriques, garde-fous | seuls les profils publiés | [EXISTANT] — `CatalogueResourceTest` |
| Veille restituée | éléments des profils actifs | union sans doublon, rubriques de chaque profil (PRF-02) | [PROPOSITION] |
| Manifeste de profil | sources, rubriques, objets suivis, stratégies, garde-fous | format 1 validé au démarrage (voir « Manifeste de profil ») ; les stratégies de `developpeur` sont confrontées au moteur d'impact par un test, celles d'`expert-metier` n'ont pas d'implémentation | [EXISTANT] partiel — `ValidateurManifeste`, `CatalogueProfilsTest` |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Au moins un profil actif, cumul possible | PRF-01 | ADR-0024 | [EXISTANT] — service, contrainte en base et tests PRF-01-S1 à S3 |
| Union des profils actifs, sans doublon | PRF-02 | ADR-0024 | [PROPOSITION] |
| Profils initiaux `developpeur` et `expert-metier` | PRF-03 | ADR-0024 ; ADR-0013 ; ADR-0018 | [EXISTANT] au catalogue (PRF-03-S1) ; rapprochement de `developpeur` par la règle PURL + plage de versions du moteur d'impact, sans IA (PRF-03-S2) ; rapprochement d'`expert-metier` absent (pas de capacité `regintel`) |
| Ajout d'un profil sans effet sur les autres | PRF-04 | ADR-0024 | [PROPOSITION] |
| Aucun droit accordé | PRF-05 | ADR-0024 ; ADR-0016 ; ABO-05 | [EXISTANT] — tests inter-espaces avec RLS (`ProfilsDeVeilleTest`) et règle ArchUnit `PreferencesIsolationTest` |
| Garde-fous appliqués avant diffusion, partout | PRF-06 | ADR-0024 ; ADR-0018 | [PROPOSITION] |

## Droits d'accès et restrictions

Les droits (RBAC + RLS) s'appliquent avant les profils. Un profil peut seulement restreindre ce qui est restitué, jamais l'élargir.

## Effets de bord observables

Modification des profils actifs de l'utilisateur ; la veille restituée change en conséquence.

## Qualités mesurables

[QUESTION] Aucune cible propre ; voir la cible de restitution du dossier v2.4 §10.

## Diagrammes

### DF-PRF-1 — Ce que décrit un profil

- **Question** : de quoi un profil de veille est-il composé, et qu'est-ce qu'il ne contient pas ?
- **Type** : UML classes
- **Portée** : cible proposée
- **Exigences** : PRF-01, PRF-03, PRF-04, PRF-05

```mermaid
classDiagram
    class ProfilVeille {
        identifiant
        libelle
        version
    }
    class CategorieSource
    class Rubrique
    class TypeObjetSuivi
    class StrategieRapprochement {
        deterministe
    }
    class GardeFou
    class Role
    ProfilVeille "1" --> "1..*" CategorieSource : surveille
    ProfilVeille "1" *-- "1..*" Rubrique : classe en
    ProfilVeille "1" --> "1..*" TypeObjetSuivi : rapproche de
    ProfilVeille "1" --> "1..*" StrategieRapprochement : utilise
    ProfilVeille "1" *-- "0..*" GardeFou : impose
    note for Role "Aucun lien avec ProfilVeille :\nles droits restent décidés par les rôles"
```

Un profil assemble des briques existantes (connecteurs, stratégies). `Role` est représenté pour montrer l'absence de lien : activer un profil ne donne aucun droit.

### DF-PRF-2 — Veille d'un utilisateur à plusieurs profils

- **Question** : comment se compose la veille d'un utilisateur qui cumule deux profils ?
- **Type** : organigramme
- **Portée** : cible proposée
- **Exigences** : PRF-02, PRF-05, PRF-06

```mermaid
flowchart LR
    S["Signaux collectés<br/>(communs à tous)"] --> D["Filtre des droits<br/>RBAC + RLS"]
    D --> P1["Profil developpeur<br/>rapprochement SBOM"]
    D --> P2["Profil expert-metier<br/>rapprochement domaines"]
    P1 --> G["Garde-fous des profils<br/>(validation, neutralité)"]
    P2 --> G
    G --> U["Union sans doublon<br/>avec rubriques"]
    U --> V["Veille de l'utilisateur<br/>dans sa mise en page"]
```

Les signaux sont collectés une seule fois pour tous. Les droits passent en premier ; chaque profil actif ajoute les éléments qui le concernent ; les garde-fous retirent ce qui n'est pas encore diffusable ; l'union élimine les doublons.

## Critères d'acceptation

Scénarios PRF-01-S1 à PRF-06-S2 du changement.

## Interprétations introduites par un changement

- « Au moins un profil actif » (PRF-01, décision D3) : non exprimé par la demande, à faire valider.
- Profils proposés par l'équipe : repris de la logique du socle d'équipe (ABO-03) ; désactivables tant qu'un profil reste actif (Q-2, décidé le 2026-11-16). Non implémentés : ils supposent les équipes du module `workspace`.
- Profils appliqués à un utilisateur qui n'a jamais choisi, ou dont tous les profils ont été retirés : profils par défaut de la plateforme (`argos.preferences.profils-par-defaut`, `developpeur` aujourd'hui). Non exprimé par la demande, à faire valider. Les profils proposés par l'équipe n'existent pas ; ces profils par défaut en tiennent lieu.
- Profil activé à la création d'un compte (Q-3 de `comptes-acces`) : l'événement `CompteCree` enregistre `developpeur` dans les préférences du compte s'il n'a encore aucun profil (`io.argos.api.preference.ActivationProfilParDefaut`).
- Clé des préférences : identifiant du compte Argos (table `compte`), relié au sujet OIDC du jeton ; jamais le nom du principal, qui peut changer ou être réattribué. Sous le rôle applicatif `argos_app`, la RLS n'expose à un compte que ses propres préférences.

## Écarts constatés

- La taxonomie des signaux du dossier v2.4 §8.2 est fermée ; ce changement la veut extensible par profil (voir la proposition).
- La restitution de la veille (`GET /api/v1/me/watch`) ne dépend pas encore des profils actifs : elle suit les abonnements et les droits. Aucun élément de veille ne relève d'`expert-metier` tant que la capacité `regintel` n'existe pas : l'union des profils (PRF-02) et la comparaison de la veille avant et après publication d'un profil (PRF-04-S1) ne sont donc pas vérifiables.
- `PreferencesIsolationTest` prouve l'isolement des préférences vis-à-vis du reste de `workspace` ; `api` peut les lire. PRF-05 est prouvé par les tests inter-espaces, pas par cette seule règle.

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-1 | Argos reste-t-il interne ou s'ouvre-t-il à d'autres publics ? | répondue le 2026-11-16 : produit ouvert (ADR-0025) | porteur du projet |
| Q-2 | Un profil proposé par l'équipe peut-il être désactivé ? | répondue le 2026-11-16 : oui, tant qu'un profil reste actif (PRF-01) [DÉCISION] | Product Owner |
| Q-3 | Un abonnement `PERSONAL` peut-il viser une source hors de ses profils ? | répondue le 2026-11-16 : oui, dans la limite de ses droits ; la source s'ajoute à l'union des profils actifs [DÉCISION] | Product Owner |
| Q-5 | Qui publie un profil au catalogue, après quelle revue ? | PRF-04 | Product Owner / architecte |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| PRF-01 | PRF-01-S1 à PRF-01-S3 | DC-1, DF-PRF-1 | 3.2 | `argos-server/src/test/java/io/argos/api/preference/PreferencesResourceTest.java` — passe |
| PRF-02 | PRF-02-S1, PRF-02-S2 | DC-2, DF-PRF-2 | 3.4 | à écrire |
| PRF-03 | PRF-03-S1, PRF-03-S2 | DC-1, DF-PRF-1 | 3.1, 3.3 | S1 : `CatalogueResourceTest.java`, `CatalogueProfilsTest.java` — passent ; S2 : `argos-server/src/test/java/io/argos/chain/ProfilsDeVeilleTest.java`, `MatchRulesTest.java` — passent |
| PRF-04 | PRF-04-S1 | DF-PRF-1 | 3.5 | partie catalogue : `ProfilDemonstrationTest.java` — passe ; veille inchangée : à écrire |
| PRF-05 | PRF-05-S1 | DC-1, DF-PRF-1, DF-PRF-2 | 3.6 | `argos-server/src/test/java/io/argos/chain/ProfilsDeVeilleTest.java#prf05S1ActivationDUnProfilSansDroitSurLesProjets`, règle ArchUnit `PreferencesIsolationTest.java` — passent |
| PRF-06 | PRF-06-S1, PRF-06-S2 | DC-2, DF-PRF-2 | 3.7 | à écrire |
