# Audit technique d'ARGOS — mise à jour du 2026-11-20

**82 constats restent à traiter sur 132** (7 Élevée, 22 Moyenne, 53 Faible).

> **Identifiants de l'axe Performance** : `AUD-PER-NN` (jusqu'au 20/11, `AUD-PRF-NN`, mêmes numéros). Le préfixe a été changé parce que `PRF-NN` désigne aussi les exigences « profils de veille » du projet et faisait échouer `scripts/verifier-docs.mjs`.

## Contexte

Mise à jour de l'audit du 17/11 (base 9d96406). Entre les deux, 158 commits et 14 lots ont été intégrés (+49 500 lignes) : connexion OIDC de l'IHM et du desktop (ADR-0033), API d'inventaire et dépôts de projet, licences et NOTICE de l'image, veille réglementaire, automatisation de la chaîne de veille, campagnes E2E, de performance et d'accessibilité. Le dépôt ne tenait aucun suivi des identifiants AUD-* : chaque constat du 17/11 a été relu dans le code à 54add78, puis le delta a été audité sur les six axes. Un septième axe, Documentation, est audité pour la première fois. Mesures exécutées : `mvn clean verify` avec JaCoCo sur le poste du mainteneur (649 tests, 0 échec, 1 ignoré, 15 min 50 s ; 85,5 % des lignes et 71,7 % des branches), `npm test` de l'IHM (206/206) et du desktop (54/54), `vue-tsc`, campagne d'accessibilité (29/29), `vite build`, `npm audit`, `verifier-docs` avec et sans rendu. Relecture contradictoire par un agent indépendant. Aucun code applicatif modifié.

## Bilan des constats

| Statut | Nombre |
|---|---|
| Fermé | 0 |
| Fermé avec réserve | 0 |
| Partiel | 10 |
| Ouvert | 72 |
| Décision documentée | 1 |
| Info | 49 |

Aucun constat du 17/11 n'est fermé : le travail des lots 5 à 18 a porté sur des fonctionnalités et en a fait avancer dix (connexion OIDC côté clients, collecte planifiée, import planifié, licences du serveur, tests de scm et du preload, règle d'ADR sur les migrations), sans en solder aucun. Cinq constats se sont aggravés (variables de compose 23 → 28, JDBC 198 → 273 prepareStatement, now() 37 → 63, N+1 de corrélation, couplage SQL 9 → 13 tables). Le delta et le nouvel axe Documentation ajoutent 39 constats (24 à traiter), dont un bloquant de déploiement (émetteur OIDC figé dans l'image).

Détail constat par constat : [etat.md](etat.md).

## Notes par axe

Les notes bougent peu alors que 158 commits ont été intégrés : ces commits ont livré des fonctionnalités, et le barème ne compte que les constats. Trois lectures à corriger avant de citer les notes. **Tests (72, contre 74)** baisse parce que l'absence de CI passe en Élevée (14 lots intégrés sans gate) ; sans ce reclassement, la note monterait à 78 ; la suite elle-même progresse nettement : 649 tests verts et 85,5 % de couverture des lignes. **Sécurité (80, contre 87)** baisse sous l'effet de la surface nouvelle (une route qui interroge la forge avant de vérifier l'appelant, durcissement du conteneur) ; les protections de fond — RLS, autorisation par espace, flux PKCE, garde SSRF, MCP — ont toutes été revérifiées et tiennent. **Documentation (78)** est une première mesure, sans point de comparaison.

| Axe | 17/11 | 20/11 | Élevée | Moyenne | Faible | Calcul |
|---|---|---|---|---|---|---|
| Architecture | 70 | **70** | 1 | 3 | 8 | 100 − 10×1 − 4×3 − 1×8 = 70 |
| Qualité | 68 | **62** | 2 | 3 | 6 | 100 − 10×2 − 4×3 − 1×6 = 62 |
| Sécurité | 87 | **80** | 0 | 3 | 8 | 100 − 4×3 − 1×8 = 80 |
| Tests | 74 | **72** | 1 | 2 | 10 | 100 − 10×1 − 4×2 − 1×10 = 72 |
| Performance | 57 | **61** | 1 | 5 | 9 | 100 − 10×1 − 4×5 − 1×9 = 61 |
| Dépendances | 73 | **65** | 2 | 2 | 7 | 100 − 10×2 − 4×2 − 1×7 = 65 |
| Documentation | — | **78** | 0 | 4 | 6 | 100 − 4×4 − 1×6 = 78 |

Barème : 100 − 25 par Critique, 10 par Élevée, 4 par Moyenne, 1 par Faible, plancher 0, sur les constats non fermés (une décision documentée reste comptée) ; un constat fusionné compte une fois, dans son axe principal.

## Cinq constats majeurs

1. **AUD-ARC-02 — L'IHM et le desktop joignent désormais le jeton OIDC, mais l'IHM embarquée dans l'image Docker reste derrière « /* authenticated » et ne peut pas se charger** (Partiel). Les clients joignent maintenant le jeton, mais dans l'image Docker la règle /* authenticated couvre index.html : le portail servi par l'image ne se charge pas. Avec AUD-DEP-21, c'est un seul bloqueur de déploiement.
1. **AUD-DEP-21 — L'image Docker fige l'émetteur OIDC de l'IHM sur http://localhost:8180/realms/argos (aucun ARG au build) et le desktop installé borne sa CSP et son émetteur à localhost faute de ARGOS_API_URL/ARGOS_OIDC_URL : connexion impossible hors du poste de développement** (Ouvert). Même une fois la règle corrigée, l'IHM de l'image redirigerait vers le Keycloak du poste de développement. Aucun ARG ne permet de passer l'émetteur au build ; les E2E ne le voient pas, ils tournent contre le Keycloak des Dev Services.
1. **AUD-QUA-02 — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes** (Ouvert). Inchangé depuis le 17/11 alors que la correction tient en une ligne : un pg_dump en échec produit un gzip vide annoncé comme réussi, et la rotation finit par supprimer toutes les vraies sauvegardes.
1. **AUD-DEP-01 — compose.yaml ne transmet pas au conteneur 28 des 37 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et 27 manquent dans .env.example : e-mails vers localhost:587, liens vers localhost:5173** (Ouvert). Aggravé : 28 variables lues en production n'atteignent pas le conteneur (23 le 17/11). Les cinq nouvelles sont les planifications et l'URL du JORF ; les quotas IA sont documentés dans .env.example mais toujours pas transmis.
1. **AUD-QUA-01 — La création de projet, l'ajout de dépôt et l'import manuel (SBOM, manifestes) n'ont toujours aucun point d'entrée en production ; seul l'import planifié des dépôts déjà configurés est branché** (Partiel). L'import nocturne existe, mais rien ne crée de projet ni ne rattache de dépôt : il n'a rien à importer. La documentation affirme le contraire (inventaire-sbom.md:33).

## Thèmes transverses

- **Livré, testé, mais pas déployable** — Chaque lot arrive avec ses tests et ses preuves E2E, mais ces preuves tournent sur le poste de développement (quarkus:dev, Vite, Keycloak des Dev Services). Ce que voit un utilisateur du NAS — l'image, son compose, son guide — n'a jamais été rejoué : portail bloqué, émetteur figé, variables non transmises, installation qui ne démarre pas en suivant le guide. (AUD-ARC-02, AUD-DEP-21, AUD-DEP-01, AUD-DOC-03, AUD-DOC-04, AUD-TST-12, AUD-TST-20)
- **Une discipline réelle que rien n'impose** — 649 tests, ADR, OpenSpec, contrôles documentaire et de licences existent et passent, mais uniquement quand le mainteneur les lance : aucune CI, image construite sans tests, contrôle des licences hors de mvn verify, règle des migrations écrite mais non vérifiée. (AUD-TST-01, AUD-TST-02, AUD-TST-19, AUD-DEP-20, AUD-DEP-22, AUD-DOC-10)
- **Tout s'exécute encore dans le fil appelant** — La collecte est désormais planifiée, mais l'enrichissement, la corrélation et les alertes restent des observateurs synchrones, transaction ouverte pendant les appels réseau ; un enrichissement refusé n'est jamais rattrapé. La corrélation nocturne sur tous les projets rend le N+1 plus coûteux. (AUD-PER-02, AUD-PER-01, AUD-PER-03, AUD-PER-04, AUD-PER-19, AUD-ARC-09)
- **Les mécanismes recopiés progressent avec le code** — Le delta a reproduit les motifs signalés le 17/11 au lieu de les factoriser : 75 prepareStatement et 26 now() de plus, une sixième transaction manuelle, un septième client HTTP, une cinquième règle ArchUnit d'exception, quatre nouvelles tables lues par plusieurs modules — dont trois lectures qu'aucune dépendance autorisée ne couvre. (AUD-QUA-06, AUD-QUA-10, AUD-QUA-04, AUD-ARC-10, AUD-ARC-05, AUD-ARC-06, AUD-ARC-04)
- **La documentation vivante décrit le cadrage de septembre** — Les liens sont sains et les documents récents exacts, mais les points d'entrée (README, arc42 §1-3 et §7, registres de risques, openspec/config.yaml) parlent encore d'un socle sans métier, de Claude API et d'un IdP d'entreprise ; 14 ADR appliqués restent « Proposé ». (AUD-DOC-01, AUD-DOC-02, AUD-ARC-14, AUD-DOC-07, AUD-DOC-08)

## Sprints

| Sprint | Titre | À traiter | Charge (j) |
|---|---|---|---|
| 1 | Remettre les gates en marche | 9 | 3 à 11 |
| 2 | Exploitation sûre : sauvegarde, déploiement, SSRF | 9 | 3 à 11 |
| 3 | Observabilité et socle d'exécution | 5 | 2 à 7 |
| 4 | Décisions écrites et noyau commun | 7 | 4,75 à 15 |
| 5 | Portail déployable et autorisations | 9 | 4,5 à 15 |
| 6 | Alimenter l'inventaire et couvrir les modules | 8 | 5 à 16 |
| 7 | Ingestion asynchrone | 7 | 4 à 13 |
| 8 | Requêtes et volumétrie | 9 | 6,75 à 21 |
| 9 | Structure et distribution | 9 | 8,75 à 26 |
| 10 | Documentation alignée sur le code livré | 10 | 4 à 14 |

Détail : [sprints.md](sprints.md).

## Chemin critique

- **AUD-TST-01** — Débloque le seuil de couverture, le build sur JDK 17, la garde des migrations, les tests du script de licences, la campagne E2E automatisée et le contrôle documentaire en CI — et conditionne tous les critères « vérifié en CI » des sprints 5 à 10.
- **AUD-ARC-14** — Effort S : écrire l'ADR du noyau commun et passer en « Accepté » les ADR appliqués débloque l'extraction du noyau (ARC-05), puis l'unification RLS et JDBC, la refonte d'arc42 (DOC-02) et la réécriture des ADR (DOC-01, DOC-08).
- **AUD-ARC-05** — Le module transverse unique est le prérequis de l'unification du contexte RLS (ARC-04), de l'utilitaire JDBC (QUA-06) et de la description d'architecture.
- **AUD-DEP-21** — Avec ARC-02, il ferme le bloqueur de déploiement du portail et conditionne la procédure d'installation du guide (DOC-03) et celle du desktop (DOC-04).

## Écarts restants de l'audit d'accessibilité

L'audit d'accessibilité des mises en page (docs/architecture/quality/audit-accessibilite-mises-en-page.md, 17/11) listait six écarts restants et limites. Revérifiés à 54add78, ils sont tous encore ouverts (hors barème de cet audit). Son extension aux écrans livrés depuis est portée par AUD-TST-23.

- Lecteur d'écran : aucune restitution vérifiée avec NVDA, JAWS ou VoiceOver — **toujours ouvert**
- Espacement du texte et zoom 200 % (RGAA 10.4, 10.12) non contrôlés — **toujours ouvert**
- Polices de titrage gothiques (UnifrakturMaguntia, UnifrakturCook) de lisibilité non mesurée — **toujours ouvert**
- Thèmes RGAA sans objet dans les mises en page (cadres, multimédia, tableaux, formulaires, scripts, consultation) — **toujours ouvert**
- Veille réelle : 320/375 px et mouvement réduit contrôlés sur le seul jeu d'exemple ; titres externes longs non essayés sur données de production — **toujours ouvert**
- AFF-05-S1 : la publication au catalogue serveur (catalog/mises-en-page.json) n'est pas bloquée par le contrôle des palettes de l'IHM — **toujours ouvert**

## Angles morts

- Pas de graphe d'appels complet : le graphe inter-modules vient d'une analyse statique des imports et noms qualifiés ; le code atteint par réflexion, injection CDI par nom ou fonctions SQL est mal couvert, et les lectures SQL croisées sont repérées par expression régulière.
- Comportement à l'exécution non observé hors des mesures versionnées par le projet : aucun EXPLAIN sur base peuplée, aucune mesure sur le DS1522+, empreinte mémoire réelle de la JVM sous 1 Gio inconnue.
- Image Docker non construite ici : le blocage du portail (AUD-ARC-02) et l'émetteur figé (AUD-DEP-21) sont déduits de la configuration et d'un bundle construit avec les mêmes variables, pas observés sur l'image.
- CVE Maven non analysées : Maven Central est inaccessible depuis l'environnement d'audit ; seules les versions ont été jugées. Fraîcheur des dépendances Maven secondaires non vérifiée.
- Configuration de production absente du dépôt : royaume Keycloak de production, reverse proxy DSM, planification réelle de la sauvegarde sur le NAS.
- Campagne E2E principale non rejouée (binaire Electron absent) ; le rapport versionné du 19/11 (34/34) porte sur le même code que 54add78. `openspec validate` non rejoué (CLI absente).
- Restauration de sauvegarde et installeurs Electron non exécutés ; couverture des tests JavaScript non mesurée.
- Le rapport du 17/11 n'a jamais été commité dans le dépôt : ce suivi ne repose sur aucun historique tenu par le mainteneur.

## Fichiers

- [etat.md](etat.md) — statut de chaque constat du 17/11
- [findings.md](findings.md) et [findings.json](findings.json) — tous les constats, avec statut, vérification, sprint et dépendances
- [sprints.md](sprints.md) et [sprints-meta.json](sprints-meta.json) — ordre de correction
- [architecture.md](architecture.md) — découpage observé et diagramme
- [plan-action.md](plan-action.md) — classement par urgence
- [../2026-11-17/](../2026-11-17/README.md) — audit initial
