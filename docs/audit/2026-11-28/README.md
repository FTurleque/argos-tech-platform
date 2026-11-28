# Audit technique d'ARGOS — mise à jour du 2026-11-28

**44 constats restent à traiter sur 192** (3 Élevée, 8 Moyenne, 33 Faible).

## Contexte

Troisième mise à jour de l'audit (17/11 : 9d96406 ; 20/11 : 54add78 ; 21/11 : b142695). Entre le 21 et le 28/11, 232 commits ont été intégrés (PR #31 à #79, 800 fichiers, +55 600 / −7 400 lignes) : le mainteneur a mis en œuvre les douze sprints de l'audit précédent et tenu leur suivi dans 35 changements OpenSpec qui citent les identifiants AUD-*. Ce suivi a été confronté au code, constat par constat, sur les sept axes, puis le delta a été audité. Mesures exécutées : `mvn -B clean verify` sur un worktree de 01fea6d sur le poste du mainteneur (1 251 tests, 1 échec propre au poste Windows, AUD-TST-32 ; le profil `integration-continue`, qui exige le JDK 17, a été refusé par l'enforcer sous JDK 24 comme prévu), `npm test` de l'IHM (276/276) et du desktop (92/92), vérifications de types, campagne d'accessibilité (29/29), scripts de contrôle du dépôt (configuration, royaume, nommage, licences, documentation avec et sans rendu), `npm audit`, `vite build`. Relecture contradictoire par un agent indépendant. Pendant l'audit, les PR #80 et #82 (18 commits) ont été fusionnées : elles traitent AUD-ARC-11, AUD-QUA-09 et AUD-QUA-18, vérifiés dans le code à ae4a2da ; le reste de ces 18 commits (refactorisations PMD, scripts) n'a pas été audité. Aucun code applicatif modifié.

## Bilan des constats

| Statut | Nombre |
|---|---|
| Fermé | 21 |
| Fermé avec réserve | 66 |
| Partiel | 13 |
| Ouvert | 31 |
| Décision documentée | 1 |
| Info | 60 |

Le suivi du mainteneur est largement confirmé par le code : sur les 99 constats qui restaient à traiter le 21/11, 86 sont fermés (20 sans réserve, 66 avec réserve), 12 partiels, 1 seul encore ouvert — en comptant les trois constats réglés par la PR #80 pendant l'audit. Les fermetures s'appuient presque toutes sur des tests nommés qui existent et vérifient ce qui est annoncé ; les réserves tiennent surtout à des recettes non jouées sur le NAS, à des tests d'intégration Keycloak marqués « non exécuté » et à des ADR restés « Proposé ». Le delta ajoute 38 constats (31 à traiter), dont trois Élevés : lecture de dépôts privés avec les jetons du serveur, installeur desktop publié figé sur localhost:8080, archives OSV écrites dans un /tmp en mémoire.

Détail constat par constat : [etat.md](etat.md).

## Notes par axe

Les notes remontent fortement (Tests 70 → 99, Architecture 76 → 95) parce que le barème ne compte que les constats non fermés : les 66 fermetures avec réserve n'y pèsent plus. Deux lectures avant de les citer. **Les réserves comptent** : recettes NAS non jouées, Keycloak non testé derrière le reverse proxy, exécution de la CI et protection de la branche non observables d'ici — une note de 99 en Tests ne dit pas que la CI a tourné vert. **Sécurité (79), Performance (79) et Dépendances (81)** sont tirées chacune par un Élevé nouveau du delta, corrigeable rapidement ; sans eux, elles seraient à 89, 89 et 91.

| Axe | 17/11 | 20/11 | 21/11 | 28/11 | Élevée | Moyenne | Faible | Calcul |
|---|---|---|---|---|---|---|---|---|
| Architecture | 70 | 70 | 76 | **95** | 0 | 0 | 5 | 100 − 1×5 = 95 |
| Qualité | 68 | 62 | 51 | **84** | 0 | 3 | 4 | 100 − 4×3 − 1×4 = 84 |
| Sécurité | 87 | 80 | 69 | **79** | 1 | 2 | 3 | 100 − 10×1 − 4×2 − 1×3 = 79 |
| Tests | 74 | 72 | 70 | **99** | 0 | 0 | 1 | 100 − 1×1 = 99 |
| Performance | 57 | 61 | 53 | **79** | 1 | 1 | 7 | 100 − 10×1 − 4×1 − 1×7 = 79 |
| Dépendances | 73 | 65 | 50 | **81** | 1 | 1 | 5 | 100 − 10×1 − 4×1 − 1×5 = 81 |
| Documentation | — | 78 | 72 | **87** | 0 | 1 | 9 | 100 − 4×1 − 1×9 = 87 |

Barème : 100 − 25 par Critique, 10 par Élevée, 4 par Moyenne, 1 par Faible, plancher 0, sur les constats non fermés (une décision documentée reste comptée) ; un constat fusionné compte une fois, dans son axe principal.

## Cinq constats majeurs

1. **AUD-SEC-34 — Inventaire et activité de projet : tout compte peut créer un espace, y rattacher n'importe quel dépôt GitHub/GitLab et en lire manifestes, SBOM, jalons et versions avec les jetons du serveur** (Ouvert). Tout compte invité crée un espace, en devient administrateur et y rattache n'importe quel dépôt GitHub ou GitLab ; le serveur le lit avec ses propres jetons et en restitue manifestes, SBOM, jalons et versions. Fuite de dépôts privés dès que le jeton du serveur y a accès.
1. **AUD-DEP-29 — L'installeur desktop publié par la CI est construit sans adresse de serveur (IHM figée sur http://localhost:8080) et remplace par mise à jour automatique tout client emballé pour un serveur distant ; ARGOS_API_URL à l'exécution, présenté comme parade, ne change que la CSP** (Ouvert). Le workflow de publication construit l'installeur sans adresse de serveur et le publie comme dernière version : la première étiquette desktop-v* remplacerait, par la mise à jour automatique, tous les clients configurés pour le NAS. Mesure conservatoire : aucune étiquette avant correction.
1. **AUD-PER-30 — L'amorçage OSV écrit jusqu'à trois archives all.zip (1 Gio chacune au plus) dans /tmp, un tmpfs compté dans le plafond mémoire de 1 Gio du conteneur argos, dont le tas peut prendre jusqu'à 60 %** (Ouvert). L'amorçage OSV peut écrire jusqu'à trois archives d'au plus 1 Gio dans un /tmp en mémoire, compté dans le plafond de 1 Gio du conteneur : arrêt probable de la JVM, puis relance horaire du même amorçage. À confirmer par un relevé docker stats sur le NAS.
1. **AUD-SEC-33 — Mise à jour automatique du desktop sans authenticité : installeurs non signés, vérification Authenticode sautée faute de publisherName, empreinte SHA-512 servie par la même release** (Ouvert). La mise à jour automatique installe à la fermeture un installeur non signé dont l'empreinte vient de la même release : aucune garantie d'authenticité, contrairement à ce qu'affirme l'ADR-0042.
1. **AUD-QUA-24 — La file d'enrichissement ne reporte pas un signal en erreur : il reste en tête de file (ORDER BY prochain_essai_le, lot de 5 toutes les 30 s), sans tentative comptée, et cinq signaux en échec persistant bloquent tout l'enrichissement KEV/NVD** (Ouvert). Cinq signaux en erreur persistante suffisent à bloquer toute la file d'enrichissement, sans même faire échouer la tâche planifiée.

## Thèmes transverses

- **Le plan a été exécuté et le code le prouve** — Les douze sprints ont produit ce qu'ils annonçaient : CI avec gates atteignables, JaCoCo bloquant, matrice des rôles tirée de l'OpenAPI, noyau commun, RLS unique, horloge et exécuteurs injectés, ingestion asynchrone, requêtes à nombre d'instructions constant vérifié en CI, rétention, pagination, IHM servie avec CSP. Chaque garde-fou a un test, souvent avec un exemple négatif. (AUD-TST-01, AUD-ARC-04, AUD-ARC-05, AUD-QUA-04, AUD-PER-03, AUD-SEC-05, AUD-ARC-26)
- **Les nouvelles surfaces n'ont pas reçu le même soin** — Ce que le delta a ajouté pour fermer les constats — routes d'inventaire, publication et mise à jour du desktop, file d'enrichissement, archive OSV — ouvre les nouveaux défauts : délégation confuse, installeur figé et non authentifié, file bloquable, tmpfs saturable. (AUD-SEC-34, AUD-DEP-29, AUD-DEP-30, AUD-SEC-33, AUD-QUA-24, AUD-PER-30, AUD-SEC-35)
- **Les frontières d'erreur reposent sur la mauvaise exception** — La règle des frontières capture IllegalStateException au lieu de la hiérarchie du domaine, et six boucles planifiées y échappent : une erreur imprévue interrompt encore certains passages, et des erreurs de programmation sont absorbées comme des pannes. (AUD-QUA-26, AUD-QUA-25, AUD-QUA-28, AUD-QUA-29, AUD-QUA-06)
- **Fermé sur le poste, à vérifier sur le NAS** — La plupart des réserves portent sur ce qui n'a jamais tourné en conditions réelles : recettes R-1 à R-8 « à faire », Keycloak derrière le reverse proxy, essai de sauvegarde et restauration hors CI, mesures de performance en quarkus:dev. (AUD-DOC-03, AUD-SEC-29, AUD-QUA-02, AUD-DEP-03, AUD-PER-30)
- **Des données encore sans borne** — Quelques listes et lectures échappent à la pagination, des purges filtrent sur des colonnes non indexées, et la conservation exclut les versions d'avis, les alertes et les impacts résolus. (AUD-PER-10, AUD-PER-33, AUD-PER-37, AUD-PER-34, AUD-PER-35)

## Sprints

| Sprint | Titre | À traiter | Charge (j) |
|---|---|---|---|
| 1 | Sécurité et exploitation | 6 | 2,25 à 8 |
| 2 | Chaîne de publication du client desktop | 7 | 4,75 à 15 |
| 3 | Frontières d'erreur et file d'enrichissement | 9 | 6 à 19 |
| 4 | Données, isolation et contrats | 9 | 6 à 19 |
| 5 | Décisions et cohérence des modules | 3 | 0,75 à 3 |
| 6 | Documentation alignée | 10 | 2,5 à 10 |

Détail : [sprints.md](sprints.md).

## Chemin critique

- **AUD-SEC-34** — Seul défaut exploitable par un compte ordinaire ; une liste blanche de propriétaires de dépôts suffit à le fermer.
- **AUD-DEP-29** — Débloque toute publication du desktop et la documentation de sa configuration (AUD-DOC-04) ; d'ici là, aucune étiquette desktop-v*.
- **AUD-QUA-06** — La migration du JDBC hors noyau vers l'utilitaire commun permet de traduire les SQLException en erreurs du domaine (AUD-QUA-26), puis d'aligner les six boucles (AUD-QUA-25) et l'arc42 §8 (AUD-DOC-25).
- **AUD-PER-30** — Débloque la correction du rafraîchissement des traitements longs (AUD-PER-32) et l'amorçage réel d'OSV sur le NAS.

## Écarts restants de l'audit d'accessibilité

L'audit d'accessibilité des mises en page (docs/architecture/quality/audit-accessibilite-mises-en-page.md, 17/11) listait six écarts restants et limites. À 01fea6d, cinq sont toujours ouverts et un est partiel (la veille réelle à 320 px est désormais testée par e2e/tests/accessibilite-ecrans.spec.ts, pas à 375 px ni en mouvement réduit). Le commit 9a0d3e6 corrige le focus et la redistribution des écrans de l'application, hors du périmètre de cet audit. Le rapport, daté du 17/11, ne mentionne pas ce nouvel audit des écrans.

- Lecteur d'écran : aucune restitution vérifiée avec NVDA, JAWS ou VoiceOver — **toujours ouvert**
- Espacement du texte et zoom (RGAA 10.4, 10.12) non contrôlés — **toujours ouvert**
- Polices de titrage gothiques (UnifrakturMaguntia, UnifrakturCook) : lisibilité non mesurée — **toujours ouvert**
- Thèmes RGAA sans objet dans les mises en page (cadres, multimédia, tableaux, formulaires, scripts, consultation) — **toujours ouvert**
- Veille réelle : 320 px, 375 px et mouvement réduit contrôlés sur le seul jeu d'exemple ; titres très longs non essayés sur des données de production — **partiel**
- AFF-05-S1 : la publication au catalogue serveur (catalog/mises-en-page.json) n'est pas bloquée par le contrôle de contraste du registre de l'IHM — **toujours ouvert**

## Angles morts

- Exécutions de la CI GitHub Actions et protection de la branche main non observables (API GitHub inaccessible depuis l'environnement d'audit) : les gates sont jugés atteignables à la lecture, pas vus verts.
- Couverture JaCoCo non relevée au 28/11 : le build du poste s'est arrêté sur l'échec propre à Windows (AUD-TST-32) avant le rapport ; le seuil bloquant 85 % / 70 % de la CI n'a pas été observé.
- Image Docker, profil idp-local et Keycloak derrière le reverse proxy non démarrés (registres et Maven Central refusés par le proxy) ; essai de sauvegarde et restauration non rejoué ; taille réelle des archives OSV inconnue.
- Comportement à l'exécution non observé hors des mesures versionnées par le projet, toutes en quarkus:dev sur le poste de développement ; aucune mesure sur le DS1522+.
- Pas de graphe d'appels complet : graphe inter-modules issu d'une analyse statique des imports ; code atteint par réflexion ou injection CDI par nom mal couvert.
- Aucune étiquette desktop-v* dans le dépôt : la chaîne de publication du desktop n'a peut-être jamais tourné.
- La branche locale main du mainteneur avait 38 commits de retard sur origin/main ; l'audit porte sur origin/main.
- Les rapports d'audit (docs/audit/) ne sont toujours pas versionnés, alors que 20 lignes « Source » des spécifications les citent (AUD-DOC-29).
- Les 18 commits des PR #80 et #82, fusionnés pendant l'audit, n'ont été vérifiés que pour AUD-ARC-11, AUD-QUA-09 et AUD-QUA-18.

## Fichiers

- [etat.md](etat.md) — statut de chaque constat des audits précédents
- [findings.md](findings.md) et [findings.json](findings.json) — tous les constats, avec statut, vérification, sprint et dépendances
- [sprints.md](sprints.md) et [sprints-meta.json](sprints-meta.json) — ordre de correction
- [architecture.md](architecture.md) — découpage observé et diagramme
- [plan-action.md](plan-action.md) — classement par urgence
- [../2026-11-21/](../2026-11-21/README.md) et [../2026-11-20/](../2026-11-20/README.md) — mises à jour précédentes ; [../2026-11-17/](../2026-11-17/README.md) — audit initial
