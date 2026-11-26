# Mesure QS-13 — recherche de veille

Rapport de la tâche 2.2 du changement [`rechercher-et-filtrer`](/openspec/changes/archive/2026-11-18-rechercher-et-filtrer/tasks.md) (exigence RCH-05, scénario qualité QS-13 de [`scenarios.md`](scenarios.md)). Cible comparée : Q-05 d'arc42 §10.2, latence API p95 < 500 ms, cible initiale.

## Méthode

| Élément | Valeur |
|---|---|
| Test | `argos-server/src/test/java/io/argos/api/recherche/MesurePerformanceRechercheTest.java`, exécuté à la demande : `mvn -f argos-server/pom.xml test -Dtest=MesurePerformanceRechercheTest -Dargos.performance=true` |
| Base | PostgreSQL des Dev Services Quarkus (conteneur jetable, configuration par défaut), migrations V1 à V11 |
| Jeu (Q-2) | 100 000 actualités dans chacun de deux espaces, 20 000 signaux publics, 2 000 impacts par espace. L'utilisateur mesuré voit 120 000 éléments ; les 100 000 actualités de l'autre espace sont écartées par les règles de visibilité. Textes de 30 mots tirés d'un vocabulaire de 3 000 mots ; « Oracle » dans 1 % des titres, « Kubernetes » dans 5 %, « correctif » dans 10 % ; dates réparties sur un an |
| Appel mesuré | `GET /api/v1/recherche` avec un jeton réel du Keycloak de test, client RestAssured sur le même poste ; durée de l'appel HTTP complet |
| Campagne | 11 requêtes, 3 tours d'échauffement, puis 20 répétitions de chacune |
| Poste | poste de développement Windows 10, Docker Desktop, partagé avec d'autres compilations et suites de tests exécutées en parallèle pendant la mesure |

## Résultats du 2026-11-16

| Requête | Résultats | p50 (ms) | p95 (ms) | max (ms) |
|---|---:|---:|---:|---:|
| mot sélectif (1 %) : `q=Oracle` | 1 200 | 164 | 209 | 242 |
| deux mots : `q=Oracle correctif` | 1 200 | 146 | 167 | 170 |
| mot moyen (5 %) : `q=Kubernetes` | 6 000 | 167 | 202 | 251 |
| filtres seuls : 30 jours | 9 868 | 89 | 106 | 164 |
| filtres seuls : projet | 2 000 | 79 | 96 | 101 |
| `q=Oracle` + projet + 30 jours | 16 | 59 | 70 | 81 |
| technologie Oracle + projet + 30 jours | 16 | 43 | 68 | 77 |
| gravité HIGH + 30 jours | 825 | 25 | 43 | 49 |
| articles + `q=correctif` | 10 000 | 123 | 156 | 182 |
| aucun filtre (fil complet) | 120 000 | 250 | 272 | 274 |
| `q=Kubernetes`, page 10 | 6 000 | 110 | 148 | 157 |
| **Campagne (220 appels)** | | **111** | **250** | **274** |
| Hors campagne : mot présent dans tous les éléments | 120 000 | 3 629 | 3 722 | 3 810 |

La campagne respecte la cible Q-05 : p95 = 250 ms < 500 ms.

## Lecture

- Le coût dominant d'une recherche par mots est le classement (`ts_rank`) de tous les éléments correspondants : il croît avec leur nombre. Un mot présent dans tous les éléments (120 000 classés) dépasse la cible d'un facteur 7 ; c'est la limite connue du risque R-16, à surveiller avant tout moteur dédié.
- Les opérateurs plein texte de PostgreSQL ne sont pas « leakproof » : sous la RLS, l'index GIN d'une table protégée serait inutilisable. La sélection passe donc par une fonction qui applique explicitement les règles de visibilité, puis la page est relue sous la RLS : `argos_rechercher_veille` (migration V11) lors de cette mesure, `argos_selectionner_veille` (migration V28, candidats plafonnés) aujourd'hui.
- `toast_tuple_target = 256` sur `signal` et `actualite` range le vecteur et les textes hors de la ligne : les parcours sans mots lisent des lignes étroites.
- Mesure sur un poste partagé : les valeurs sont indicatives d'un ordre de grandeur, pas d'une capacité de production.
