# ADR-0037 — Porter les mécanismes techniques partagés par un noyau commun du serveur

- **Statut** : Accepté
- **Date** : 2026-11-25 (acceptation et proposition)
- **Décideurs** : porteur du projet (choix du modèle transactionnel et de l'outil JDBC, 2026-11-24) ; acceptation du 2026-11-25
- **Remplace** : —
- **Remplacé par** : —
- **Précise** : [ADR-0001](0001-monolithe-modulaire.md) (frontières de modules), [ADR-0016](0016-workspace-rbac-rls.md) (contexte RLS)

## Contexte

- Tous les modules du serveur emploient le contexte RLS, les erreurs du domaine partagées (`DonneeInvalide`, `RegleViolee`, `Introuvable`, `AccesRefuse`, `MotifRefus`), l'appelant (`Caller`) et le limiteur de débit. Placés dans `workspace` ou `api.security`, ces mécanismes obligent le test des frontières (`ModuleBoundariesTest`) à porter une règle d'exception par module.
- Le protocole RLS (`SET LOCAL ROLE argos_app` puis les paramètres `argos.*`) a un seul point d'écriture s'il est unique, et une copie par module sinon ; une copie partielle ne réinitialise ni le sujet ni le courriel courants.
- Un modèle transactionnel unique évite qu'une transaction manuelle (`setAutoCommit`, `commit`) échoue quand un appelant ouvre une transaction JTA autour d'elle ; JTA (`@Transactional`, `QuarkusTransaction`) est le modèle déjà employé par la grande majorité du code.
- Le squelette JDBC (`prepareStatement`, lecture, traduction de `SQLException`, conversion des dates et des textes) se recopie fichier par fichier s'il n'a pas d'utilitaire commun.
- [ADR-0001](0001-monolithe-modulaire.md) impose des frontières explicites entre modules ; un noyau implicite les contourne par des exceptions.

## Critères de décision

1. Un seul point d'application du contexte RLS, de la frontière transactionnelle et du squelette JDBC.
2. Aucune règle d'exception au cas par cas dans le test des frontières.
3. Aucune dépendance livrée en plus ([ADR-0021](0021-double-licence.md)).
4. Adoption progressive de l'utilitaire JDBC, sans changer les réponses de l'API.

## Options considérées

### Option A — Paquet transverse dans `argos-server`

- avantages : un composant `commun` autorisé à tous les modules et ne dépendant d'aucun ; supprime les exceptions et les copies ; frontières vérifiées par ArchUnit ;
- inconvénients : risque de « fourre-tout » ;
- coûts : déplacement de classes et mise à jour des imports.

### Option B — Statu quo : noyau dans `workspace` et `api.security`, avec exceptions

- avantages : aucun déplacement ;
- inconvénients : maintient les cinq exceptions et les copies du protocole RLS ;
- risques : la dérive constatée se poursuit.

### Option C — Module Maven séparé

- avantages : isolement plus fort ;
- inconvénients : coût de build et de version sans besoin démontré.

## Décision

**Option A.** Les paquets `io.argos.commun` et `io.argos.commun.securite` portent :

- le contexte RLS : un seul point qui pose `SET LOCAL ROLE argos_app` et les quatre paramètres (compte, sujet, courriel, espace) ; un paramètre absent vaut la chaîne vide ;
- le modèle transactionnel : tout en JTA (`QuarkusTransaction` ou `@Transactional`) ; le noyau offre la frontière qui ouvre la transaction et applique le contexte RLS ; aucune classe du serveur n'appelle `Connection.setAutoCommit`, `commit` ni `rollback` ;
- l'utilitaire d'accès JDBC interne, sans dépendance nouvelle : `lister`, `unique`, `modifier` prennent le SQL, une liaison de paramètres et une lecture de ligne ; convertisseurs communs (dates, troncature) ; une seule traduction de `SQLException` ; le SQL reste explicite, sans ORM ;
- les erreurs du domaine partagées ;
- dans `io.argos.commun.securite` : l'appelant (`Caller`) et le limiteur de débit ; la résolution OIDC reste dans `api.security`.

Le noyau est autorisé à tous les modules et ne dépend d'aucun module métier. N'y entrent pas : une règle métier, l'accès à une table d'un module, un nom de table. Un module métier propriétaire de ses tables en publie les lectures étrangères par un service de lecture ou une vue SQL nommée.

Jdbi est écarté : nouvelle dépendance livrée, deuxième style d'accès pendant la migration, intégration du contexte RLS et de JTA à valider. Le modèle manuel unique (transaction JDBC gérée par le noyau) est écarté : il laisserait deux modèles en place.

## Conséquences positives

- Un seul code pour le contexte RLS ; les politiques RLS sont celles des migrations.
- Un seul modèle transactionnel, JTA : un appelant peut ouvrir une transaction autour d'un service sans le casser.
- Le test des frontières n'a aucune règle d'exception au cas par cas.

## Conséquences négatives

- Code d'utilitaire JDBC à maintenir et à tester.
- Les modules qui n'emploient pas encore `Jdbc` écrivent leur JDBC directement, sous la même règle de transaction JTA.

## Conséquences neutres / compromis

- Le paquet doit rester petit : le test des frontières interdit au noyau de dépendre d'un module.
- La liaison de paramètres par nom n'est pas fournie.

## Méthode de validation

- `ModuleBoundariesTest` sans règle d'exception, avec une règle « `commun` ne dépend d'aucun module » ;
- test de source : aucune occurrence de `SET LOCAL ROLE` ni de `set_config('argos.` hors du noyau ;
- règle ArchUnit : aucun appel à `Connection.setAutoCommit`, `commit`, `rollback` ;
- tests de cloisonnement existants (ACC-04, COR-08) verts ;
- document `/q/openapi` identique avant et après.

## Traçabilité

- **Exigences** : ACC-04, COR-08 (non-régression)
- **Scénarios qualité** : arc42 §10
- **Diagrammes** : arc42 §5.2, §5.3
- **Fichiers source** : `argos-server/src/main/java/io/argos/commun/`, `argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java`
