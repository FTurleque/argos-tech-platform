# Audit technique d'ARGOS — mise à jour du 2026-11-21

**99 constats restent à traiter sur 154** (8 Élevée, 29 Moyenne, 62 Faible).

> **Identifiants de l'axe Performance** : `AUD-PER-NN` (`AUD-PRF-NN` jusqu'au 20/11, mêmes numéros) — `PRF-NN` désigne aussi les exigences « profils de veille » du projet et faisait échouer `scripts/verifier-docs.mjs`.

## Contexte

Deuxième mise à jour de l'audit (17/11, base 9d96406 ; 20/11, base 54add78). Entre le 20 et le 21/11, 110 commits ont été intégrés (PR #22 à #30, 438 fichiers, +13 700 / −9 300 lignes) : renommages (lot 20), découpages et captures d'exception resserrées (lots 21 et 23), optimisation de « Ma veille » (index V21, lecture groupée), installation locale avec Keycloak et IHM servie par Quarkus (PR #28). Le dépôt ne tient toujours aucun suivi des identifiants AUD-* : chaque constat a été relu dans le code à b142695, puis le delta a été audité sur les sept axes. Mesures exécutées : `mvn clean verify` avec JaCoCo sur le poste du mainteneur (654 tests, 0 échec, 1 ignoré, 10 min 47 s ; 85,8 % des lignes, 72,0 % des branches), `npm test` de l'IHM (206/206) et du desktop (54/54), `vue-tsc`, campagne d'accessibilité (29/29), `vite build`, `npm audit`, `verifier-docs` avec et sans rendu. Relecture contradictoire par un agent indépendant. Aucun code applicatif modifié.

## Bilan des constats

| Statut | Nombre |
|---|---|
| Fermé | 0 |
| Fermé avec réserve | 1 |
| Partiel | 16 |
| Ouvert | 83 |
| Décision documentée | 1 |
| Info | 53 |

Premier constat fermé depuis le 17/11 : l'IHM servie par l'image se charge désormais sans jeton, l'API restant protégée (AUD-ARC-02, fermé avec réserve faute de test). Six constats passent partiels, dont « Ma veille », revenue dans la cible (p95 85 ms contre 533,8 ms). Mais le delta ajoute 22 constats, dont deux Élevés : le resserrement des captures d'exception laisse un PURL mal encodé arrêter la synchronisation et l'import nocturne, et la base Keycloak de la nouvelle installation n'est pas sauvegardée. Le reste à traiter passe de 82 à 99.

Détail constat par constat : [etat.md](etat.md).

## Notes par axe

Quatre notes baissent nettement ; trois lectures à faire avant de les citer. **Qualité (51) et Dépendances (50)** perdent chacune un Élevé nouveau, tous deux liés à des changements de la veille (captures d'exception du lot 23, base Keycloak de la PR #28) et corrigeables en quelques heures. **Performance (53)** baisse alors que le gain le plus visible du delta y figure : « Ma veille » passe de 533,8 ms à 85 ms au p95 mesuré ; le constat reste compté Partiel tant que le jeu de mesure ne contient pas d'abonnements. **Sécurité (69)** baisse sous l'effet de l'installation Keycloak (royaume sans politique de mot de passe, compte PostgreSQL partagé, IHM servie sans CSP) ; les protections de fond — RLS, autorisation par espace, PKCE, SSRF, MCP — ont été revérifiées et tiennent. Architecture (76) progresse avec la fermeture d'AUD-ARC-02.

| Axe | 17/11 | 20/11 | 21/11 | Élevée | Moyenne | Faible | Calcul |
|---|---|---|---|---|---|---|---|
| Architecture | 70 | 70 | **76** | 0 | 4 | 8 | 100 − 4×4 − 1×8 = 76 |
| Qualité | 68 | 62 | **51** | 3 | 3 | 7 | 100 − 10×3 − 4×3 − 1×7 = 51 |
| Sécurité | 87 | 80 | **69** | 0 | 6 | 7 | 100 − 4×6 − 1×7 = 69 |
| Tests | 74 | 72 | **70** | 1 | 2 | 12 | 100 − 10×1 − 4×2 − 1×12 = 70 |
| Performance | 57 | 61 | **53** | 1 | 6 | 13 | 100 − 10×1 − 4×6 − 1×13 = 53 |
| Dépendances | 73 | 65 | **50** | 3 | 3 | 8 | 100 − 10×3 − 4×3 − 1×8 = 50 |
| Documentation | — | 78 | **72** | 0 | 5 | 8 | 100 − 4×5 − 1×8 = 72 |

Barème : 100 − 25 par Critique, 10 par Élevée, 4 par Moyenne, 1 par Faible, plancher 0, sur les constats non fermés (une décision documentée reste comptée) ; un constat fusionné compte une fois, dans son axe principal.

## Cinq constats majeurs

1. **AUD-QUA-20 — Le resserrement des captures d'exception du lot 23 laisse remonter les erreurs imprévues : un PURL mal encodé bloque la synchronisation des sources suivantes et l'import nocturne, et la chaîne radar fait échouer PUT /standards après commit** (Ouvert). Régression de la veille : une seule SBOM ou un seul avis portant un PURL mal encodé suffit à bloquer la synchronisation horaire à chaque échéance et l'import nocturne des projets suivants ; PUT /standards répond 500. Le test de résilience a été renommé pour ne simuler qu'une IllegalStateException.
1. **AUD-DEP-24 — La base keycloak ajoutée dans le même PostgreSQL n'est ni sauvegardée ni restaurée : après une reprise, le royaume réimporté émet de nouveaux sujets OIDC et les comptes Argos (sujet_oidc UNIQUE) deviennent orphelins** (Ouvert). Une reprise après incident rend tous les comptes orphelins : le royaume réimporté émet de nouveaux sujets OIDC, l'administrateur se retrouve avec un compte vide, et seul un UPDATE manuel rétablit l'accès aux espaces.
1. **AUD-QUA-02 — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes** (Ouvert). Toujours inchangé depuis le 17/11 : un pg_dump en échec produit un gzip vide annoncé comme réussi. La correction tient en une ligne et précède logiquement AUD-DEP-24.
1. **AUD-DEP-21 — L'image Docker fige l'émetteur OIDC de l'IHM sur http://localhost:8180/realms/argos (aucun ARG au build) : connexion web impossible hors du poste ; le desktop est désormais configurable à l'emballage (apiUrl, oidcUrl) mais sans documentation de l'émetteur ni test** (Partiel). L'installation locale fonctionne parce que tout tourne sur le poste : l'émetteur reste figé à localhost:8180 dans le bundle et KC_HOSTNAME vaut http://localhost. Sur le NAS consulté depuis un PC, la connexion est impossible. Avec AUD-DEP-25 (redirections limitées au port 8088), c'est le dernier verrou du portail.
1. **AUD-TST-01 — Aucune intégration continue : aucun test (mvn verify, npm test, ModuleBoundariesTest) n'est exécuté automatiquement** (Ouvert). Toujours aucune CI : les 110 commits du jour, dont 51 captures d'exception modifiées dans 29 classes, n'ont été vérifiés que par des lancements manuels — et la régression d'AUD-QUA-20 est passée.

## Thèmes transverses

- **L'installation fonctionne sur le poste, pas encore sur le NAS** — La PR #28 apporte Keycloak, l'IHM servie et un client desktop paramétrable ; l'image se charge enfin. Mais tout est réglé pour localhost : émetteur figé, KC_HOSTNAME, redirections au port 8088, variables non transmises, guide non réécrit. Aucun test n'exerce l'image ni le profil idp-local. (AUD-DEP-21, AUD-DEP-25, AUD-DEP-01, AUD-DEP-27, AUD-DOC-03, AUD-TST-12, AUD-TST-25)
- **Sans CI, chaque refactorisation est un pari** — Le lot 23 a resserré 51 captures d'exception dans 29 classes avec un seul test modifié ; la régression qui en résulte (AUD-QUA-20) n'a été vue par aucun gate. La discipline existe (654 tests, contrôles documentaire et de licences) mais rien ne l'impose. (AUD-TST-01, AUD-QUA-20, AUD-TST-02, AUD-TST-19, AUD-DEP-22, AUD-DOC-10)
- **Les données critiques n'ont pas de filet** — La sauvegarde échoue en silence, ignore la base Keycloak dont dépendent les comptes, n'a jamais été restaurée, et Keycloak partage le compte superutilisateur de la base métier. (AUD-QUA-02, AUD-DEP-24, AUD-DEP-03, AUD-SEC-30, AUD-SEC-07, AUD-SEC-06)
- **Tout s'exécute encore dans le fil appelant** — L'enrichissement, la corrélation et la chaîne radar restent des observateurs synchrones ; un enrichissement refusé n'est jamais rattrapé, deux fils peuvent synchroniser la même source, et la corrélation réécrit chaque impact chaque nuit. (AUD-PER-02, AUD-PER-01, AUD-PER-27, AUD-PER-25, AUD-PER-03, AUD-ARC-09)
- **Les mécanismes recopiés ne reculent pas** — Les refactorisations du jour ont découpé des méthodes et renommé, sans factoriser : 4 contextes RLS, 6 transactions manuelles (dont une qui ne restaure plus l'autocommit), 272 prepareStatement, 63 now() sans Clock, 7 clients HTTP. (AUD-ARC-04, AUD-ARC-05, AUD-QUA-04, AUD-QUA-06, AUD-QUA-10, AUD-ARC-10)

## Sprints

| Sprint | Titre | À traiter | Charge (j) |
|---|---|---|---|
| 1 | Remettre les gates en marche | 10 | 3,25 à 12 |
| 2 | Sauvegarde, restauration et base | 6 | 2,25 à 8 |
| 3 | Déploiement et conteneurs | 7 | 2,5 à 9 |
| 4 | Observabilité et frontières d'erreur | 6 | 3 à 10 |
| 5 | Décisions écrites et noyau commun | 7 | 4,75 à 15 |
| 6 | Portail déployable et autorisations | 10 | 3,25 à 12 |
| 7 | Alimenter l'inventaire et compléter l'IHM | 9 | 6 à 19 |
| 8 | Ingestion asynchrone | 8 | 4,25 à 14 |
| 9 | Rétention, import et pagination | 7 | 2,5 à 9 |
| 10 | Requêtes chaudes | 7 | 6,25 à 19 |
| 11 | Structure et distribution | 9 | 8,75 à 26 |
| 12 | Documentation alignée sur le code livré | 13 | 4,75 à 17 |

Détail : [sprints.md](sprints.md).

## Chemin critique

- **AUD-TST-01** — Débloque le seuil de couverture, le build sur JDK 17, la garde des migrations, les tests du script de licences, les campagnes E2E et le contrôle documentaire en CI — et aurait arrêté la régression d'AUD-QUA-20.
- **AUD-QUA-02** — Effort S : une ligne de pipefail, prérequis logique de la sauvegarde de la base Keycloak (AUD-DEP-24) et de la restauration rejouée (AUD-DEP-03).
- **AUD-ARC-14** — Effort S : l'ADR du noyau commun et les statuts d'ADR débloquent l'extraction du noyau (ARC-05), l'unification RLS et JDBC, arc42 (DOC-02) et la réécriture des ADR (DOC-01, DOC-08).
- **AUD-DEP-21** — Débloque la CSP de l'IHM servie (SEC-05) et les procédures d'installation du portail et du desktop (DOC-03, DOC-04).

## Écarts restants de l'audit d'accessibilité

L'audit d'accessibilité des mises en page (docs/architecture/quality/audit-accessibilite-mises-en-page.md, 17/11) listait six écarts restants et limites. Revérifiés à b142695, ils sont tous encore ouverts (hors barème de cet audit) ; les tests concernés n'ont reçu que des renommages. Son extension aux écrans livrés depuis est portée par AUD-TST-23.

- Lecteur d'écran : aucune restitution vérifiée avec NVDA, JAWS ou VoiceOver — **toujours ouvert**
- Espacement du texte et zoom (RGAA 10.4, 10.12) non contrôlés — **toujours ouvert**
- Polices de titrage gothiques (UnifrakturMaguntia, UnifrakturCook) : lisibilité non mesurée — **toujours ouvert**
- Thèmes RGAA sans objet dans les mises en page (cadres, multimédia, tableaux, formulaires, scripts, consultation) — **toujours ouvert**
- Veille réelle : 320 px, 375 px et mouvement réduit contrôlés sur le seul jeu d'exemple ; titres très longs non essayés sur des données de production — **toujours ouvert**
- AFF-05-S1 : la publication au catalogue serveur (catalog/mises-en-page.json) n'est pas bloquée par le contrôle de contraste du registre de l'IHM — **toujours ouvert**

## Angles morts

- Pas de graphe d'appels complet : graphe inter-modules issu d'une analyse statique des imports et noms qualifiés ; code atteint par réflexion, injection CDI par nom ou fonctions SQL mal couvert ; lectures SQL croisées repérées par expression régulière.
- Image Docker et profil idp-local non démarrés (registres quay.io et Maven Central refusés par le proxy) : la fermeture d'AUD-ARC-02 et les défauts de connexion (AUD-DEP-21, AUD-DEP-25) sont déduits de la configuration, de la documentation Quarkus et Keycloak (Context7) et d'un bundle construit avec les mêmes variables. À confirmer sur le poste : curl -i http://localhost:8080/ sans jeton, et une connexion OIDC depuis l'IHM servie.
- Comportement à l'exécution non observé hors des mesures versionnées par le projet : aucun EXPLAIN sur base peuplée, aucune mesure sur le DS1522+ ni avec plusieurs utilisateurs, débit d'ingestion OSV et durée de l'import nocturne jamais mesurés.
- CVE Maven non analysées (Maven Central inaccessible depuis l'environnement d'audit) ; seules les versions ont été jugées.
- Seules la collecte des avis, l'import des inventaires et la garde MCP ont été tracés jusqu'au bout après le resserrement des captures du lot 23 ; les autres frontières n'ont pas été vérifiées une à une.
- Campagne E2E principale non rejouée (binaire Electron absent) ; aucune trace de passage versionnée depuis le 19/11.
- Configuration de production absente du dépôt : royaume Keycloak de production, reverse proxy DSM, planification réelle de la sauvegarde ; version de Compose de Synology Container Manager (depends_on.required) non confirmée.
- Les rapports d'audit du 17/11 et du 20/11 n'ont jamais été commités : ce suivi ne repose sur aucun historique tenu par le mainteneur.

## Fichiers

- [etat.md](etat.md) — statut de chaque constat des audits précédents
- [findings.md](findings.md) et [findings.json](findings.json) — tous les constats, avec statut, vérification, sprint et dépendances
- [sprints.md](sprints.md) et [sprints-meta.json](sprints-meta.json) — ordre de correction
- [architecture.md](architecture.md) — découpage observé et diagramme
- [plan-action.md](plan-action.md) — classement par urgence
- [../2026-11-20/](../2026-11-20/README.md) — mise à jour précédente ; [../2026-11-17/](../2026-11-17/README.md) — audit initial
