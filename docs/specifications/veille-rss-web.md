# Fiche capacité : `veille-rss-web`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `WEB` |
| Spec de référence | `/openspec/specs/veille-rss-web/spec.md` — absente tant qu'aucun changement n'est archivé |
| Changements en cours | [`collecter-veille-rss-web`](/openspec/changes/collecter-veille-rss-web/proposal.md) (implémenté, non archivé) |
| Statut de la capacité | proposée ; code et tests présents dans `argos-server` |
| Composants (arc42 §5) | `source-ingestion`, `signal`, `impact-engine` |
| Décision | [ADR-0028](/docs/architecture/adr/0028-collecteur-veille-web-integre.md) (Proposé) : collecteur intégré au serveur |

## Objectif

[PROPOSITION — `collecter-veille-rss-web`] Suivre blogs, notes de version et pages choisis par chacun, sans exposer Argos aux adresses et contenus malveillants.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Utilisateur | ajoute, collecte et supprime ses sources personnelles ; consulte les actualités de ses sources et de celles de ses espaces | [EXISTANT] — `VeilleWebResource` |
| Administrateur d'espace | ajoute, collecte et supprime les sources de l'espace | [EXISTANT] — `VeilleWebResource` |
| Membre d'un espace | consulte les sources et les actualités de l'espace | [EXISTANT] — RLS de la migration V10 |
| Sites et flux externes | publient le contenu | [EXISTANT] — simulés par un serveur HTTP local dans les tests |

## Périmètre et exclusions

Inclus :

- ajout de flux RSS/Atom et de pages surveillées, au niveau d'un espace ou à titre personnel ;
- collecte planifiée, déduplication et restitution des actualités avec leurs sources ;
- rapprochement par nom de technologie, au niveau « à vérifier ».

Exclus :

- moteur de recherche web, contournement de murs payants ;
- rendu JavaScript des pages surveillées ;
- résumé produit par une IA : la passerelle IA relève du changement `encadrer-appels-ia`.

## Préconditions et déclencheurs

Utilisateur authentifié (ADR-0008, ADR-0027). Collecte déclenchée à l'ajout de la source, par le planificateur (sources échues, toutes les `argos.veille-web.cadence`) ou à la demande du propriétaire ou d'un administrateur de l'espace.

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Demande de source | portée (`ESPACE` ou `PERSONNELLE`), espace, type (`FLUX` ou `PAGE`), adresse, libellé facultatif | adresse `http(s)` publique, contenu qui est un flux RSS/Atom ou une page HTML (WEB-01, WEB-03) | [EXISTANT] |
| Source | état `ACTIVE` ou `EN_ERREUR`, échecs consécutifs, motif du dernier échec, prochaine collecte | — | [EXISTANT] |
| Actualité | type (`ARTICLE` ou `CHANGEMENT_PAGE`), titre, lien, date, contenu, sources citées | contenu en texte brut, marqué `CITATION_EXTERNE` et `fiable = false` (WEB-04) | [EXISTANT] |
| Détail d'une actualité | actualité, résumé extractif (`EXTRAIT_V1`), rapprochements | — | [EXISTANT] |

API : `POST|GET /api/v1/veille-web/sources`, `DELETE /api/v1/veille-web/sources/{id}`, `POST /api/v1/veille-web/sources/{id}/collectes`, `GET /api/v1/veille-web/actualites`, `GET /api/v1/veille-web/actualites/{id}`.

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Une adresse est enregistrée seulement si elle renvoie un flux valide (type `FLUX`) ou une page HTML (type `PAGE`) ; sinon refus `FLUX_INVALIDE` ou `PAGE_INVALIDE`, aucune source créée | WEB-01 | ADR-0028 (Proposé) | [EXISTANT] |
| La première collecte suit l'ajout ; chaque source est ensuite collectée toutes les heures (`argos.veille-web.frequence`) | WEB-01 | Q-2 | [EXISTANT] |
| Une page surveillée garde un instantané (texte par blocs, empreinte SHA-256) ; un contenu différent crée une actualité datée dont le texte est l'aperçu des blocs ajoutés, à défaut retirés | WEB-02 | ADR-0028 (Proposé) | [EXISTANT] |
| Adresse refusée (`ADRESSE_INTERDITE`) si une adresse IP de l'hôte, après résolution DNS, est privée, locale, de lien local ou réservée ; contrôle refait à chaque redirection (5 au plus) ; 10 s et 2 Mio par récupération, lecture du contenu comprise (un serveur qui se tait après ses en-têtes est coupé à l'échéance) | WEB-03 | risque R-12 ; arc42 §8.3 | [EXISTANT] |
| Une collecte en échec fait passer la source `EN_ERREUR` avec son motif ; la tentative suivante attend fréquence × 2^(échecs − 1), au plus × 8 | WEB-03 | conception, gestion des erreurs | [EXISTANT] |
| Le contenu collecté est réduit à du texte brut et restitué comme citation non fiable ; le résumé est un extrait déterministe ; aucun lien collecté n'est récupéré | WEB-04 | risque R-11 | [EXISTANT] |
| Un article reçu par plusieurs sources a une seule empreinte (lien normalisé, à défaut identifiant, à défaut titre et texte) et est restitué une fois avec toutes ses sources visibles | WEB-05 | arc42 §6.1 | [EXISTANT] |
| Une actualité citée par une source d'espace est rapprochée des projets de cet espace dont un composant porte le nom de technologie mentionné ; niveau `A_VERIFIER` seulement, aucun impact projet, aucune alerte | WEB-06 | ADR-0013 (Proposé) ; ALR-02 | [EXISTANT] |
| Une source personnelle ne produit aucun rapprochement | WEB-06 | [PROPOSITION] : rien de la veille personnelle n'est révélé aux autres membres | [EXISTANT] |
| 50 sources personnelles par utilisateur au plus (`argos.veille-web.sources-personnelles-max`) ; une adresse n'est suivie qu'une fois par propriétaire ou par espace | — | Q-3 | [EXISTANT] |

## Droits d'accès et restrictions

[EXISTANT] Une source personnelle n'est visible, collectée et supprimée que par son propriétaire. Une source d'espace est visible des membres de l'espace, créée, collectée et supprimée par ses administrateurs : un membre d'un autre rôle reçoit 403, un non-membre 404 ; chaque refus est journalisé (ACC-06). Une actualité n'est visible que par une source visible, et seules les sources visibles sont citées. Ces règles sont appliquées par `VeilleWebResource` puis, sous le rôle `argos_app`, par les politiques RLS de la migration V10.

## Effets de bord observables

Sources, actualités, citations et rapprochements enregistrés ; état et motif d'erreur de chaque source ; aucune alerte, aucun impact projet.

## Qualités mesurables

[QUESTION] Aucune cible chiffrée de performance dans les sources. Bornes de sécurité : 10 s et 2 Mio par récupération, 5 redirections.

## Diagrammes

### DF-WEB-1 — Acceptation d'une adresse

- **Question** : quand une adresse proposée est-elle acceptée ?
- **Type** : organigramme
- **Portée** : existant
- **Exigences** : WEB-01, WEB-03

```mermaid
flowchart TD
    A["Adresse proposée"] --> B{"http(s), sans identifiants,<br/>toutes les IP publiques ?"}
    B -- "non" --> R["Refusée<br/>aucune requête émise"]
    B -- "oui" --> C{"Récupération bornée<br/>redirections revérifiées"}
    C -- "échec" --> R
    C -- "contenu reçu" --> D{"Flux RSS/Atom<br/>ou page HTML ?"}
    D -- "non" --> R
    D -- "oui" --> E["Source créée<br/>première collecte intégrée"]
```

Une adresse n'est acceptée que si elle mène à un serveur public et renvoie un contenu exploitable.

## Critères d'acceptation

Scénarios WEB-01-S1 à WEB-06-S1 du changement, couverts par `argos-server/src/test/java/io/argos/chain/VeilleWebTest.java` et `ParcoursVeilleWebTest.java`.

## Interprétations introduites par un changement

- Aperçu de la modification d'une page (WEB-02) : blocs de texte ajoutés (« Ajouté : … »), à défaut retirés, 500 caractères au plus [PROPOSITION].
- Résumé d'un article (WEB-04) : extrait déterministe des premières phrases, 280 caractères au plus, sans IA [PROPOSITION].
- Nom de technologie (WEB-06) : portée d'un paquet npm à portée (`@angular/core` → `angular`), sinon nom du paquet, 3 caractères au moins, recherché comme mot entier [PROPOSITION].
- Sources d'espace sans limite de nombre ; leur création est réservée aux administrateurs [PROPOSITION].
- Les refus d'une demande de source réutilisent les erreurs communes du domaine : `DonneeInvalide` (HTTP 400, code du motif de refus ou `DONNEE_INVALIDE`, attribut en cause) et `RegleViolee` (HTTP 409, `LIMITE_SOURCES`, `SOURCE_EXISTANTE`) ; la RLS est appliquée par `ContexteBase`, seul mécanisme de contexte de cloisonnement du module `workspace` emprunté par `signal` [PROPOSITION].

## Écarts constatés

- La passerelle IA n'existe pas encore : le scénario WEB-04-S1 est vérifié sur le résumé extractif ; la vérification avec la passerelle IA réelle reste à faire avec `encadrer-appels-ia`.
- Rebinding DNS : le client HTTP résout l'hôte une seconde fois après le contrôle (risque résiduel, ADR-0028).
- L'IHM ne présente pas encore les sources ni les actualités web.

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-1 | Utiliser FreshRSS et changedetection.io en services séparés (AGPL pour FreshRSS) ou un collecteur intégré ? | répondue le 2026-11-16 : collecteur intégré au serveur, à bibliothèques sous licence permissive ; ni FreshRSS (AGPL) ni changedetection.io [DÉCISION] ; ADR-0028 | porteur du projet |
| Q-2 | À quelle fréquence collecter ? | répondue le 2026-11-16 : toutes les heures, paramétrable [DÉCISION] | Product Owner |
| Q-3 | Combien de sources personnelles par utilisateur au maximum ? | répondue le 2026-11-16 : 50 sources personnelles par utilisateur, paramétrable [DÉCISION] | porteur du projet |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| WEB-01 | WEB-01-S1, WEB-01-S2 | DC-1, DF-WEB-1 | 1.1, 2.1, 2.2, 3.1 | `VeilleWebTest` : `fluxAtomValideCreeLaSourceEtSesArticlesApparaissent`, `adresseSansFluxRefuseeSansCreerDeSource` |
| WEB-02 | WEB-02-S1 | DC-1 | 1.1, 3.1 | `VeilleWebTest.pageSurveilleeModifieeCreeUnElementDateAvecApercu` |
| WEB-03 | WEB-03-S1, WEB-03-S2 | DC-1, DF-WEB-1 | 2.1 | `GardeAdressesTest`, `RecuperateurWebTest`, `VeilleWebTest` : `adressePriveeRefuseeSansRequete`, `redirectionVersAdresseLocaleMetLaSourceEnErreur` |
| WEB-04 | WEB-04-S1 | DC-1 | 3.3 | `VeilleWebTest.articlePiegeCiteCommeDonneeSansEffetSurLesImpacts` |
| WEB-05 | WEB-05-S1 | DC-1 | 3.2 | `VeilleWebTest.memeArticleDansDeuxFluxRestitueUneFoisAvecSesDeuxSources` |
| WEB-06 | WEB-06-S1 | DC-1 | 3.4 | `VeilleWebTest.articleSurUneTechnologieUtiliseeRapprocheAVerifierSansAlerte` |
