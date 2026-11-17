# Tests de bout en bout d'ARGOS

Parcours du portail web `argos-ui` exécutés dans Chromium et du client desktop `argos-desktop` piloté dans Electron, par Playwright, contre un serveur ARGOS réel, son Keycloak de test et une base PostgreSQL jetable. Aucune requête ne quitte le poste : toute requête du navigateur ou de la session Electron vers un hôte hors de la boucle locale fait échouer le parcours, et les tâches planifiées qui joindraient une source externe sont désactivées.

## Lancer

Prérequis : Docker démarré, JDK 17 ou plus, Maven 3.9, Node.js 22.18 ou plus, dépendances de `argos-ui` installées (`npm --prefix argos-ui ci`), binaire Electron 45.3.2 de `argos-desktop` présent dans `argos-desktop/node_modules/electron/dist/` (script d’installation du paquet `electron`, voir « Client desktop »).

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
   puis exécute les projets `preparation` (données), `portail-web` (parcours du portail), `construction-desktop` et `desktop` (parcours Electron) ;
3. arrête et efface la base à la fin. `ARGOS_E2E_GARDER_BASE=1` la conserve pour examen.

Un seul projet : `npm --prefix e2e test -- --project=desktop` (ses dépendances `preparation` et `construction-desktop` s’exécutent aussi).

Docker est appelé par un chemin absolu, pris parmi ses emplacements d’installation habituels (`e2e/support/docker.mjs`) : le `PATH` n’est pas consulté. `ARGOS_E2E_DOCKER` indique un autre emplacement.

Le serveur écoute sur 8090 et non sur 8080 : le port 8080 est souvent pris par un autre service du poste. Les ports 5173 et 8180 sont imposés par le royaume de test (redirections du client `argos-ui`) et par `%dev.quarkus.keycloak.devservices.port`.

## Données et comptes

- Comptes : ceux du royaume de test du dépôt, mot de passe égal à l'identifiant. `alice` est l'administratrice plateforme ; `paul` n'a reçu aucune invitation.
- `tests/preparation.setup.ts` crée par l'API l'espace « Espace E2E recherche » d'`alice`, puis `donnees/recherche.sql` y ajoute les projets « Paie » et « Comptabilité », quatre signaux et leurs impacts, comme les écrivent la collecte et la corrélation.
- Pour « Ma veille », la préparation crée aussi par l'API les comptes d'`ines` et de `karim` (invitation dans « Espace E2E accueil » d'`alice`), « Espace E2E veille A » d'`ines`, où `jules` est lecteur, « Espace E2E veille B » de `karim`, et l'abonnement `PERSONAL` de `jules` (deux critères `PACKAGE`). `donnees/veille.sql` y ajoute les projets « Paie E2E veille » (A) et « RH confidentiel » (B), trois avis de sécurité et leurs impacts, le domaine « paie » d'`ines` et trois fiches réglementaires (deux validées, dont une au résumé produit par l'IA, une en attente).
- Pour les alertes du poste, chaque parcours de `desktop/notifications.spec.ts` crée au besoin le compte de `lina` (invitation dans « Espace E2E accueil »), efface ses alertes `DESKTOP` (`donnees/purge-alertes-poste.sql`), puis émet à la demande des alertes immédiates `DESKTOP` à l'état `EN_ATTENTE` (`donnees/alertes-poste.sql`, variables `compte` et `numero`, de `01` à `06`), comme les écrit le moteur d'alertes : signal, version et alerte au contenu propre au destinataire. Identifiants fixes : une nouvelle émission remet l'alerte en attente. Titres de signaux et noms de projets fictifs, qu'aucune notification ne doit reprendre.

## Parcours

| Fichier | Parcours | Exigences |
|---|---|---|
| `tests/connexion.spec.ts` | vue protégée sans session ; connexion réelle et retour sur la vue demandée, adresse nettoyée, jetons hors du stockage, jeton porteur ; state forgé ; déconnexion ; compte non créé ; renouvellement refusé | ACC-07-S1 à S3, ACC-08-S1, ACC-08-S3, ACC-08-S4, ACC-09-S1, ACC-09-S2, ACC-09-S4 |
| `tests/recherche.spec.ts` | recherche « Oracle », projet « Paie », 30 jours, vue enregistrée, rappelée dans la même session puis dans une nouvelle session | RCH-01-S1, RCH-02-S1, RCH-04-S1 |
| `tests/affichage.spec.ts` | mise en page « gazette » choisie sur le web et retrouvée dans une nouvelle session | AFF-01-S1, AFF-03-S1 (partie web) |
| `desktop/connexion.spec.ts` | client desktop : écran de connexion, adresse d’autorisation reçue par le « navigateur du système », connexion sur Keycloak, redirection suivie vers `127.0.0.1:<port éphémère>/argos/retour`, page de retour statique (`default-src 'none'`, `no-store`), écoute fermée, fenêtre réduite ramenée au premier plan, session ouverte et `/api/v1/moi` accepté avec le jeton porteur, CSP lue et appliquée, journal de la session sans hôte non local ni page du fournisseur | ACC-10-S1, ACC-10-S2, ACC-10-S4 |
| `desktop/notifications.spec.ts` | client desktop connecté (`lina`), fenêtre masquée dans la zone de notification : deux alertes puis une alerte critique sur trois projets, notifications réelles d’Electron au titre fixe « Nouvelle alerte ARGOS » et au texte « Gravité critique · 3 projets » sans titre de signal ni nom de projet, infobulle « ARGOS — 2 alertes ouvertes » puis « 3 alertes ouvertes », état `ENVOYEE` côté serveur ; clic simulé sur la notification : fenêtre au premier plan sur `#/alertes/{id}`, alerte affichée ; réglage « Notifications du poste » coupé dans l’IHM : aucune notification, alerte comptée et visible ; notifications indisponibles pour le système (`Notification.isSupported` simulé faux), puis refusées par les réglages de Windows (événement `failed` simulé) : aucune notification affichée, alertes comptées et visibles ; coupure simulée du serveur, deux alertes émises pendant la coupure : bandeau « Hors ligne », puis « Connexion rétablie : 2 alertes émises pendant la coupure », deux notifications exactement, `ENVOYEE` côté serveur, aucun doublon | NOT-01-S1, NOT-01-S2 (partie application), NOT-02-S1, NOT-03-S1, NOT-04-S1 (partie poste), NOT-05-S1, NOT-06-S1 |
| `desktop/recherche.spec.ts` | recherche « Oracle », projet « Paie », 30 jours, enregistrée en vue sur le portail web par `alice`, puis rappelée depuis la liste des vues du client desktop connecté avec le même compte : même vue, critères restitués, même résultat, vue et résultats lus avec le jeton porteur | RCH-02-S1, RCH-04-S1 ; tâche 4.2 de rechercher-et-filtrer |
| `desktop/affichage.spec.ts` | « fantaisie » choisie sur le web par `alice`, appliquée par le desktop (choix coché, aperçu et « Ma veille » en fantaisie, préférence lue sur le serveur) ; `jules` (`developpeur` + `expert-metier`, abonnement personnel) choisit « gazette » sur le web et retrouve « Ma veille » en gazette sur le desktop, avec l'union de ses veilles, rubriques, mention IA et rien hors de ses droits | AFF-03-S1 ; tâches 2.5 et 5.2 de personnaliser-profils-et-affichage |
| `tests/veille.spec.ts` | « Ma veille » : profils `developpeur` + `expert-metier` choisis dans l'IHM et abonnement personnel, union sans doublon avec rubriques ; utilisateur d'un autre espace limité à ses droits ; même veille réelle dans les quatre mises en page ; « gazette » choisie sur le web puis « Ma veille » en gazette ; contrastes, tabulation et noms accessibles des quatre mises en page sur la veille réelle | PRF-02 (union de deux profils), PRF-02-S3, PRF-02-S4, AFF-02-S1, AFF-02-S2, AFF-05-S1 ; tâche 5.2 (partie web) |

## Audit d'accessibilité des mises en page

`npm --prefix e2e run test:accessibilite` exécute `accessibilite/mises-en-page.spec.ts` avec `accessibilite.config.ts`, sans serveur ARGOS, base ni Keycloak : Playwright démarre Vite sur `127.0.0.1:5174` et ouvre le banc d’essai `argos-ui/banc/index.html?mise-en-page=<identifiant>`, qui affiche une mise en page sur le jeu d’exemple de l’aperçu. Le banc n’est pas une entrée du build : il est absent de `dist/`.

Pour chacune des quatre mises en page : polices embarquées chargées et aucune requête hors du poste ; contraste de chaque texte rendu sur son fond effectif (4,5:1, ou 3:1 pour un grand texte) ; ordre de tabulation et contour de focus ; lecture à 320 px sans défilement horizontal ; colonnes empilées à 375 px ; aucune transition ni animation sous `prefers-reduced-motion: reduce` ; région, articles, titres et liens nommés, décor absent de l’arbre d’accessibilité. Un dernier test compare les faits restitués par les quatre mises en page. Les mesures exécutées dans la page (contrastes, état du focus) sont partagées avec `tests/veille.spec.ts` par `support/mesures.ts`. Rapport : [`docs/architecture/quality/audit-accessibilite-mises-en-page.md`](../docs/architecture/quality/audit-accessibilite-mises-en-page.md).

## Résultats

- `test-results/` (ignoré par Git) : rapport JSON, traces et captures des parcours en échec.
- `resultats/` : rapport JSON et captures d'une exécution de référence, consignés dans les `tasks.md` des changements OpenSpec concernés ; `resultats/accessibilite/` pour l'audit des mises en page.

## Client desktop

Le projet `desktop` lance l’application `argos-desktop` telle qu’elle est livrée, hors paquet, par `_electron.launch` (API expérimentale de `@playwright/test`, sans dépendance ajoutée) :

1. `desktop/construction.setup.ts` (projet `construction-desktop`) vérifie la présence du binaire Electron, puis construit l’IHM comme `npm --prefix argos-desktop start` : `npm --prefix argos-ui run build` avec `VITE_ARGOS_API_URL=http://localhost:8090` et `VITE_ARGOS_OIDC_ISSUER=http://localhost:8180/realms/argos`. Le build remplace `argos-ui/dist/` (ignoré par Git), que `main.js` charge hors paquet par le protocole interne `app://argos` avec sa CSP.
2. `support/electron.ts` lance `argos-desktop/node_modules/electron/dist/electron.exe` (ou `electron` / `Electron.app` selon le système) sur le dossier `argos-desktop`, avec `ARGOS_API_URL=http://localhost:8090`, `ARGOS_OIDC_URL=http://localhost:8180/realms/argos` et un dossier de données propre au test (`--user-data-dir` dans `test-results/`) : le verrou d’instance unique et les réglages du poste ne touchent pas un ARGOS ouvert sur le poste. `ARGOS_E2E_ELECTRON` désigne un autre binaire de la même version (par exemple celui du dépôt principal depuis un worktree où `npm ci` n’a pas pu télécharger le binaire). Binaire absent : le projet échoue avec un message explicite, il n’est pas sauté.

Aucun code de production n’est modifié pour les tests ; les doublures sont installées dans le processus principal par `electronApp.evaluate`, depuis le test :

- **navigateur du système** : `shell.openExternal` enregistre l’adresse au lieu d’ouvrir le navigateur du poste ; le test l’ouvre dans une page Chromium de Playwright (soumise à la garde réseau des parcours web), s’y connecte sur Keycloak et suit la redirection jusqu’à l’écoute locale du desktop ;
- **garde réseau** : `session.defaultSession.webRequest` annule toute requête vers un hôte hors de la boucle locale et tient un journal (adresse sans chaîne de requête, type, présence d’un jeton porteur sans sa valeur, statut) ;
- **état de la fenêtre** : visibilité, réduction, focus, adresse et événements (`minimize`, `restore`, `focus`…) de la fenêtre principale.
- **notifications** : `Notification.prototype.show` est enveloppé pour garder chaque objet `Notification` réel affiché (titre, corps), puis appelle l’original : la notification est bien demandée au système. Le clic sur la bulle est simulé en émettant l’événement `click` de cet objet (`cliquerNotification`) ; `refuserNotificationsSysteme('non-supportees')` remplace `Notification.isSupported` par une fonction qui renvoie faux, `refuserNotificationsSysteme('reglages-windows')` remplace l’appel au système par l’événement `failed` que Windows fait émettre quand ses réglages refusent la notification (« Settings prevent the notification from being delivered. »), et `accepterNotificationsSysteme()` rétablit les originaux ;
- **zone de notification** : `Tray.prototype.setToolTip` est enveloppé pour garder chaque infobulle (Electron ne permet pas de la relire) et l’icône, dont `cliquerZoneNotification` émet le `click` ;
- **coupure du serveur** : `couper(hôte:port)` fait annuler par la garde réseau toute requête de la session vers cet hôte, comme un serveur injoignable, jusqu’à `retablir()`. Le serveur reste en marche : l’arrêter arrêterait aussi le Keycloak des Dev Services, dont les clés de signature changent au redémarrage, et la session du desktop ne serait plus valide.

Fonctions réutilisables de `support/electron.ts` : `lancerDesktop` (lancement, doublures, rechargement pour un journal complet), `ApplicationDesktop.fenetre` (fenêtre principale), `journal`, `ouvertures`, `etatFenetre`, `fermer`, `reduireFenetre`, `attendreOuverture`, `connecterCompte` (connexion d’un compte du royaume de test depuis l’écran de connexion du desktop), `masquerFenetre` (fermeture de la fenêtre, qui la masque), `notifications`, `cliquerNotification`, `infobulles`, `cliquerZoneNotification`, `refuserNotificationsSysteme`, `couper`, `retablir`, `ouvrirVueConnectee` (vue protégée, connexion, retour sur la vue), `appelApiAuthentifie` (appel de l’API abouti avec le jeton porteur dans le journal), `verifierJournalLocal` (aucun hôte hors de la boucle locale, aucun document hors de `app://argos`).

Parcours qui enchaînent le portail web et le desktop (`desktop/recherche.spec.ts`, `desktop/affichage.spec.ts`) : l’action est faite sur le portail dans la page `page` de Playwright, puis le desktop se connecte avec le même compte par la fixture `navigateurSysteme` (`support/fixtures.ts`), page d’un second contexte Chromium sans la session Keycloak du portail et soumise à la même garde réseau. Les lectures d’écran communes aux deux clients sont dans `support/ecrans.ts`.

Ce que le parcours ne couvre pas : le navigateur réellement installé sur le poste (remplacé par la doublure) et l’activation de la fenêtre par le gestionnaire de fenêtres du système au-delà de ce qu’Electron rapporte (`isFocused`, événements de la fenêtre).

Ce que les parcours des alertes du poste ne couvrent pas : l’affichage de la bulle par Windows, son refus réel dans les réglages de Windows (le test n’a pas le droit de modifier les réglages du système ; sous Windows, `Notification.isSupported` ne reflète pas ce refus, que le système signale par l’événement `failed` de la notification, simulé), le clic réel sur la bulle (simulé par l’événement `click` de l’objet réel) et l’arrêt réel du serveur (coupure simulée au niveau de la session).

## Dépendances

Licences enregistrées dans [`NOTICE`](NOTICE) (ADR-0021). Outillage de test seulement : rien n'est livré avec ARGOS.
