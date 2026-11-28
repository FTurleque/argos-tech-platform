# Audit technique d'ARGOS — 2026-11-17

> **Identifiants de l'axe Performance** : `AUD-PER-NN` (jusqu'au 20/11, `AUD-PRF-NN`, mêmes numéros). Le préfixe a été changé parce que `PRF-NN` désigne aussi les exigences « profils de veille » du projet et faisait échouer `scripts/verifier-docs.mjs`.

## Contexte

ARGOS est une plateforme de veille technologique et de suivi de projets : un monolithe modulaire Quarkus 3.40 LTS / Java 17 (16 modules, 228 fichiers, ~20 600 lignes), PostgreSQL 18 avec RLS, une IHM Vue 3 partagée par le portail web et un client desktop Electron, assemblés par Docker Compose pour un NAS Synology personnel, avant un hébergement ouvert au public. Profil d'audit : complet (6 axes). L'analyse a été faite sur f58a68c (branche lot4/integration, 17/11 à 0 h 30), puis chaque constat a été revérifié sur 9d96406 et le delta du lot 4 (~3 500 lignes : profil artiste, opportunités) a été audité. Mesures exécutées : `mvn clean verify` avec JaCoCo sur le poste du mainteneur (416 tests, 0 échec, 1 ignoré, 12 min ; 83,8 % des lignes, 68,6 % des branches), `npm test` de l'IHM (25/25) et du desktop (22/22), `vue-tsc`, `vite build`, `npm audit`. Aucun code applicatif n'a été modifié.

## Notes par axe

Deux notes communiquent mal à elles seules. **Performance (57)** est tirée par des constats structurels (traitements synchrones, requêtes en N+1) relevés à la lecture : aucune mesure d'exécution n'a été possible, et sur le NAS mono-utilisateur plusieurs restent latents ; ils deviennent réels avec le volume OSV complet ou plusieurs espaces. **Tests (74)** sanctionne l'absence de gate et la structure de la pyramide, pas la suite elle-même : 416 tests verts et 83,8 % de couverture des lignes, c'est un niveau rare à ce stade. À l'inverse, **Sécurité (87)** est haute à juste titre : RLS, autorisation par espace, garde SSRF, MCP et Electron ont été vérifiés flux par flux.

| Axe | Note /100 | Élevée | Moyenne | Faible | Calcul |
|---|---|---|---|---|---|
| Architecture | **70** | 1 | 3 | 8 | 100 − 10×1 − 4×3 − 1×8 = 70 |
| Qualité | **68** | 2 | 2 | 4 | 100 − 10×2 − 4×2 − 1×4 = 68 |
| Sécurité | **87** | 0 | 2 | 5 | 100 − 4×2 − 1×5 = 87 |
| Tests | **74** | 0 | 5 | 6 | 100 − 4×5 − 1×6 = 74 |
| Performance | **57** | 2 | 4 | 7 | 100 − 10×2 − 4×4 − 1×7 = 57 |
| Dépendances | **73** | 1 | 3 | 5 | 100 − 10×1 − 4×3 − 1×5 = 73 |

Barème : 100 − 25 par Critique, 10 par Élevée, 4 par Moyenne, 1 par Faible, plancher 0. Un constat fusionné compte une seule fois, dans son axe principal. Total : 94 constats — 0 Critique, 6 Élevée, 19 Moyenne, 35 Faible, 34 Info.

## Cinq constats majeurs

1. **AUD-ARC-02 — L'IHM et le client desktop n'envoient aucun jeton alors que l'API exige OIDC sur toutes les routes sauf /system/info.** En image Docker, même index.html reçoit 401 : l'IHM web est inutilisable, le desktop n'affiche que son écran de statut. Le parcours de bout en bout n'a jamais pu tourner.
1. **AUD-QUA-01 — La création de projet et l'import d'inventaire (SBOM, manifestes) n'ont aucun point d'entrée en production : code atteint seulement par les tests.** Corrélation, radar de dette et alertes d'impact sont testés mais n'ont aucune donnée réelle : rien ne permet de créer un projet ni d'importer un SBOM hors des tests.
1. **AUD-QUA-02 — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes.** Si pg_dump échoue, le script écrit un gzip vide, annonce le succès et fait tourner la rotation : après 14 échecs, il ne reste aucune vraie sauvegarde. Correction en une ligne (set -o pipefail ou contrôle du code de retour).
1. **AUD-DEP-01 — compose.yaml ne transmet pas au conteneur 23 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, quotas IA) et la doc de déploiement est en retard : e-mails vers localhost:587, liens vers localhost:5173.** Même renseignées dans .env, ces valeurs n'atteignent pas le conteneur : aucune alerte e-mail ne part et les liens des messages sont faux. La PR #7 a reproduit le défaut (ARGOS_IA_QUOTA_COMPTE_JOUR) : il faut un test de parité.
1. **AUD-PER-02 — L'enrichissement NVD/EPSS et la corrélation sont exécutés de façon synchrone pour chaque signal créé ou modifié, dans le fil de l'ingestion.** Chaque signal déclenche dans le fil d'ingestion des appels NVD/EPSS bloquants, transaction ouverte ; un enrichissement refusé n'est jamais rattrapé (retryMissing n'a aucun appelant).

## Thèmes transverses

- **Un serveur solide que rien ne permet encore d'utiliser de bout en bout** — Les modules sont testés et cloisonnés, mais les entrées réelles manquent : connexion depuis l'IHM et le desktop, saisie des projets et des inventaires, vues du profil artiste, configuration transmise par compose. Le produit n'a jamais tourné comme un utilisateur le ferait. (AUD-ARC-02, AUD-QUA-01, AUD-ARC-15, AUD-ARC-12, AUD-DEP-01)
- **Tout s'exécute dans le fil appelant** — Synchronisation des avis, enrichissement, corrélation, alertes et chaîne radar s'enchaînent par observateurs CDI synchrones, dans la requête HTTP ou le fil d'ingestion, parfois transaction ouverte pendant un appel réseau. C'est la cause commune de la plupart des constats de performance. (AUD-PER-01, AUD-PER-02, AUD-PER-04, AUD-ARC-09, AUD-PER-03)
- **Des mécanismes partagés recopiés plutôt que factorisés** — Contexte RLS (4 copies divergentes), transactions manuelles (5 copies), accès JDBC (198 prepareStatement sans utilitaire), clients HTTP (6), et un noyau commun logé dans workspace et api qui impose des exceptions ArchUnit au cas par cas. La règle ArchUnit tient les imports ; elle ne voit ni les copies ni les lectures SQL croisées. (AUD-ARC-04, AUD-ARC-05, AUD-QUA-04, AUD-QUA-06, AUD-ARC-10, AUD-ARC-06)
- **Une discipline réelle, mais que rien n'impose** — Tests nombreux, ADR, spécifications et contrôle documentaire existent, mais tout repose sur des lancements manuels : pas de CI, image construite sans tests, build incrémental qui a lancé 0 test, migration réécrite, sauvegarde et restauration jamais éprouvées. (AUD-TST-01, AUD-TST-19, AUD-DEP-20, AUD-QUA-02, AUD-DEP-03, AUD-DEP-07)
- **Des données qui grossissent sans borne** — Journaux d'audit, appels MCP et IA, versions brutes des signaux, opportunités sans date limite : aucune rétention hors activité des projets, et plusieurs listes renvoyées en entier. Sur un NAS, le disque est la première ressource à manquer ; c'est aussi une question RGPD dès l'ouverture au public. (AUD-SEC-03, AUD-PER-18, AUD-PER-10, AUD-PER-07)

## Sprints

| Sprint | Titre | Constats | Charge (j) |
|---|---|---|---|
| 1 | Remettre les gates en marche | 7 | 2,5 à 9 |
| 2 | Exploitation sûre : sauvegarde, déploiement, SSRF | 8 | 2,75 à 10 |
| 3 | Observabilité et socle d'exécution | 5 | 2 à 7 |
| 4 | Décisions écrites et noyau commun | 7 | 4,75 à 15 |
| 5 | Authentification de bout en bout | 6 | 3,75 à 12 |
| 6 | Alimenter l'inventaire et couvrir les modules | 7 | 4,75 à 15 |
| 7 | Ingestion asynchrone | 6 | 3,75 à 12 |
| 8 | Requêtes et volumétrie | 7 | 5,5 à 17 |
| 9 | Structure, documentation et distribution | 7 | 6 à 18 |

Détail, critères de sortie et ordre interne : [sprints.md](sprints.md).

## Chemin critique

- **AUD-TST-01** — Débloque directement le seuil de couverture, la garde des migrations et le build propre sur JDK 17, puis les tests ciblés du sprint 6 ; surtout, c'est ce qui rend vérifiable chacune des 59 autres corrections.
- **AUD-ARC-12** — Un client HTTP unique dans client.ts est le prérequis de l'authentification (ARC-02), des tests de l'IHM et des vues artiste.
- **AUD-ARC-02** — Le flux PKCE rend enfin l'IHM utilisable et débloque les vues du profil artiste ; c'est la seule voie vers une preuve de bout en bout.
- **AUD-ARC-14** — Effort S : écrire l'ADR du noyau commun et mettre les statuts à jour débloque l'extraction du noyau (ARC-05), puis l'unification RLS, l'utilitaire JDBC et la refonte d'arc42.

## Angles morts

- Pas de graphe d'appels complet : le graphe inter-modules vient d'une analyse statique des imports et des noms qualifiés ; le code atteint par réflexion, injection CDI par nom ou fonctions SQL (argos_rechercher_veille) est mal couvert, et les lectures SQL croisées ont été repérées par expression régulière.
- Comportement à l'exécution non observé : aucune latence sur le DS1522+, aucun EXPLAIN sur base peuplée, empreinte mémoire réelle de la JVM sous 1 Gio inconnue, volume réel d'une première synchronisation OSV non mesuré.
- CVE Maven non analysées : Maven Central et le registre Docker étaient inaccessibles depuis l'environnement d'audit ; les dépendances Java et les images n'ont été jugées que sur leurs versions. L'arbre de dépendances et un scanner CVE (OWASP dependency-check ou Trivy) restent à lancer en CI.
- Configuration de production absente du dépôt : royaume Keycloak de production, reverse proxy DSM (TLS, HSTS, délais de coupure), planification réelle de la sauvegarde sur le NAS.
- Le rebinding DNS (AUD-SEC-01) n'a pas été exploité en pratique ; la largeur de la fenêtre repose sur la lecture du code et le cache DNS par défaut de la JVM.
- La cause exacte du build incrémental sans test (AUD-TST-19) est supposée : nettoyage concurrent de target/ par l'IDE.
- Restauration de sauvegarde, construction réelle de l'image Docker et installeurs Electron non exécutés ; couverture des tests JavaScript non mesurée.
- Le commit local 8132d8b de la branche lot5/arbitrages (archivage openspec, sans code) n'a pas été audité.

## Fichiers

- [findings.md](findings.md) — tous les constats, avec preuve, impact, action, sprint et dépendances
- [findings.json](findings.json) — les mêmes, en JSON (source du tableau de bord)
- [sprints.md](sprints.md) et [sprints-meta.json](sprints-meta.json) — ordre de correction
- [architecture.md](architecture.md) — découpage observé et diagramme
- [plan-action.md](plan-action.md) — classement par urgence
