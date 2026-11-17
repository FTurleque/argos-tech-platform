# Tests de bout en bout d'ARGOS

Parcours du portail web `argos-ui` exécutés dans Chromium par Playwright, contre un serveur ARGOS réel, son Keycloak de test et une base PostgreSQL jetable. Aucune requête ne quitte le poste : toute requête du navigateur vers un hôte autre que `localhost` fait échouer le parcours, et les tâches planifiées qui joindraient une source externe sont désactivées.

## Lancer

Prérequis : Docker démarré, JDK 17 ou plus, Maven 3.9, Node.js 22.18 ou plus, dépendances de `argos-ui` installées (`npm --prefix argos-ui ci`).

```bash
npm --prefix e2e ci
npx --prefix e2e playwright install chromium   # une fois : navigateur de Playwright 1.61.1 (Chromium révision 1228)
npm --prefix e2e test                          # options de Playwright après « -- », par exemple : -- -g recherche
```

`npm --prefix e2e test` exécute `scripts/lancer.mjs` :

1. démarre la base PostgreSQL de `compose.yaml` (port 55432, données en mémoire) ;
2. lance `playwright test`, qui démarre ou réutilise :
   - le serveur par `mvn quarkus:dev` sur le port 8090, base de test, Keycloak des Dev Services sur le port 8180 avec le royaume de test `argos-server/src/test/resources/keycloak/argos-realm.json` ;
   - le portail web par Vite sur le port 5173, `VITE_ARGOS_API_URL=http://localhost:8090` ;
3. arrête et efface la base à la fin. `ARGOS_E2E_GARDER_BASE=1` la conserve pour examen.

Docker est appelé par un chemin absolu, pris parmi ses emplacements d’installation habituels (`e2e/support/docker.mjs`) : le `PATH` n’est pas consulté. `ARGOS_E2E_DOCKER` indique un autre emplacement.

Le serveur écoute sur 8090 et non sur 8080 : le port 8080 est souvent pris par un autre service du poste. Les ports 5173 et 8180 sont imposés par le royaume de test (redirections du client `argos-ui`) et par `%dev.quarkus.keycloak.devservices.port`.

## Données et comptes

- Comptes : ceux du royaume de test du dépôt, mot de passe égal à l'identifiant. `alice` est l'administratrice plateforme ; `paul` n'a reçu aucune invitation.
- `tests/preparation.setup.ts` crée par l'API l'espace « Espace E2E recherche » d'`alice`, puis `donnees/recherche.sql` y ajoute les projets « Paie » et « Comptabilité », quatre signaux et leurs impacts, comme les écrivent la collecte et la corrélation.
- Pour « Ma veille », la préparation crée aussi par l'API les comptes d'`ines` et de `karim` (invitation dans « Espace E2E accueil » d'`alice`), « Espace E2E veille A » d'`ines`, où `jules` est lecteur, « Espace E2E veille B » de `karim`, et l'abonnement `PERSONAL` de `jules` (deux critères `PACKAGE`). `donnees/veille.sql` y ajoute les projets « Paie E2E veille » (A) et « RH confidentiel » (B), trois avis de sécurité et leurs impacts, le domaine « paie » d'`ines` et trois fiches réglementaires (deux validées, dont une au résumé produit par l'IA, une en attente).

## Parcours

| Fichier | Parcours | Exigences |
|---|---|---|
| `tests/connexion.spec.ts` | vue protégée sans session ; connexion réelle et retour sur la vue demandée, adresse nettoyée, jetons hors du stockage, jeton porteur ; state forgé ; déconnexion ; compte non créé ; renouvellement refusé | ACC-07-S1 à S3, ACC-08-S1, ACC-08-S3, ACC-08-S4, ACC-09-S1, ACC-09-S2, ACC-09-S4 |
| `tests/recherche.spec.ts` | recherche « Oracle », projet « Paie », 30 jours, vue enregistrée, rappelée dans la même session puis dans une nouvelle session | RCH-01-S1, RCH-02-S1, RCH-04-S1 |
| `tests/affichage.spec.ts` | mise en page « gazette » choisie sur le web et retrouvée dans une nouvelle session | AFF-01-S1, AFF-03-S1 (partie web) |
| `tests/veille.spec.ts` | « Ma veille » : profils `developpeur` + `expert-metier` choisis dans l'IHM et abonnement personnel, union sans doublon avec rubriques ; utilisateur d'un autre espace limité à ses droits ; même veille réelle dans les quatre mises en page ; « gazette » choisie sur le web puis « Ma veille » en gazette ; contrastes, tabulation et noms accessibles des quatre mises en page sur la veille réelle | PRF-02 (union de deux profils), PRF-02-S3, PRF-02-S4, AFF-02-S1, AFF-02-S2, AFF-05-S1 ; tâche 5.2 (partie web) |

## Audit d'accessibilité des mises en page

`npm --prefix e2e run test:accessibilite` exécute `accessibilite/mises-en-page.spec.ts` avec `accessibilite.config.ts`, sans serveur ARGOS, base ni Keycloak : Playwright démarre Vite sur `127.0.0.1:5174` et ouvre le banc d’essai `argos-ui/banc/index.html?mise-en-page=<identifiant>`, qui affiche une mise en page sur le jeu d’exemple de l’aperçu. Le banc n’est pas une entrée du build : il est absent de `dist/`.

Pour chacune des quatre mises en page : polices embarquées chargées et aucune requête hors du poste ; contraste de chaque texte rendu sur son fond effectif (4,5:1, ou 3:1 pour un grand texte) ; ordre de tabulation et contour de focus ; lecture à 320 px sans défilement horizontal ; colonnes empilées à 375 px ; aucune transition ni animation sous `prefers-reduced-motion: reduce` ; région, articles, titres et liens nommés, décor absent de l’arbre d’accessibilité. Un dernier test compare les faits restitués par les quatre mises en page. Les mesures exécutées dans la page (contrastes, état du focus) sont partagées avec `tests/veille.spec.ts` par `support/mesures.ts`. Rapport : [`docs/architecture/quality/audit-accessibilite-mises-en-page.md`](../docs/architecture/quality/audit-accessibilite-mises-en-page.md).

## Résultats

- `test-results/` (ignoré par Git) : rapport JSON, traces et captures des parcours en échec.
- `resultats/` : rapport JSON et captures d'une exécution de référence, consignés dans les `tasks.md` des changements OpenSpec concernés ; `resultats/accessibilite/` pour l'audit des mises en page.

## Limites

Le client desktop n'est pas piloté : Playwright `_electron` exige le binaire Electron, que `npm ci` de `argos-desktop` ne télécharge pas tant que la politique npm `allow-scripts` bloque le script d'installation du paquet `electron`. Les parcours desktop (connexion par l'interface de bouclage, notifications, mise en page relue par le desktop) restent à écrire une fois ce binaire présent.

## Dépendances

Licences enregistrées dans [`NOTICE`](NOTICE) (ADR-0021). Outillage de test seulement : rien n'est livré avec ARGOS.
