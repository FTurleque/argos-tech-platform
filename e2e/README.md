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

## Parcours

| Fichier | Parcours | Exigences |
|---|---|---|
| `tests/connexion.spec.ts` | vue protégée sans session ; connexion réelle et retour sur la vue demandée, adresse nettoyée, jetons hors du stockage, jeton porteur ; state forgé ; déconnexion ; compte non créé ; renouvellement refusé | ACC-07-S1 à S3, ACC-08-S1, ACC-08-S3, ACC-08-S4, ACC-09-S1, ACC-09-S2, ACC-09-S4 |
| `tests/recherche.spec.ts` | recherche « Oracle », projet « Paie », 30 jours, vue enregistrée, rappelée dans la même session puis dans une nouvelle session | RCH-01-S1, RCH-02-S1, RCH-04-S1 |
| `tests/affichage.spec.ts` | mise en page « gazette » choisie sur le web et retrouvée dans une nouvelle session | AFF-01-S1, AFF-03-S1 (partie web) |

## Résultats

- `test-results/` (ignoré par Git) : rapport JSON, traces et captures des parcours en échec.
- `resultats/` : rapport JSON et captures d'une exécution de référence, consignés dans les `tasks.md` des changements OpenSpec concernés.

## Limites

Le client desktop n'est pas piloté : Playwright `_electron` exige le binaire Electron, que `npm ci` de `argos-desktop` ne télécharge pas tant que la politique npm `allow-scripts` bloque le script d'installation du paquet `electron`. Les parcours desktop (connexion par l'interface de bouclage, notifications, mise en page relue par le desktop) restent à écrire une fois ce binaire présent.

## Dépendances

Licences enregistrées dans [`NOTICE`](NOTICE) (ADR-0021). Outillage de test seulement : rien n'est livré avec ARGOS.
