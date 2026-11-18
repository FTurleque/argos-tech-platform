# ADR-0033 — Tenir la session OIDC de l'IHM en mémoire et connecter le desktop par l'interface de bouclage

- **Statut** : Accepté
- **Date** : 2026-11-18 (acceptation ; proposition du 2026-11-17)
- **Décideurs** : porteur du projet (acceptation du 2026-11-18)
- **Complète** : [ADR-0027](0027-keycloak-idp-auto-heberge.md) (Accepté) pour les clients ; précise [ADR-0022](0022-client-desktop-electron.md) point 6 (Proposé)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- L'API exige un jeton OIDC émis par Keycloak pour le client public `argos-ui`, Authorization Code + PKCE S256 (ADR-0027, Proposé ; `argos-server/src/main/resources/application.properties`).
- La même IHM Vue 3 tourne dans le navigateur (portail web) et dans le client desktop Electron, servie par le protocole interne `app://argos` sous une CSP stricte (ADR-0022, Proposé ; `argos-desktop/src/ui-protocol.js`).
- ADR-0022 point 6 prévoit pour le desktop « le navigateur du système et une redirection locale (RFC 8252) » et la conservation des jetons « via le magasin sécurisé du système (`safeStorage`) ».
- La demande du lot 7 impose des jetons en mémoire seulement (jamais `localStorage`, `sessionStorage` ni cookie lisible), aucune dépendance payante et des licences permissives (ADR-0021, Proposé).
- RFC 8252 : les applications natives utilisent un agent utilisateur externe (§8.12) ; la redirection vers l'interface de bouclage accepte un port quelconque (§7.3).

## Critères de décision

1. Aucun jeton lisible par un script hors de la mémoire de la page, ni dans une adresse ou un journal.
2. Même code de session pour le portail web et le desktop.
3. Conformité RFC 8252 pour le desktop : aucune page du fournisseur dans l'application.
4. Aucune dépendance supplémentaire, ou une dépendance sous licence permissive (ADR-0021).
5. Logique testable sans navigateur ni fournisseur réel.

## Options considérées

### Option A — Bibliothèque `oidc-client-ts` (Apache-2.0)

- avantages : bibliothèque répandue, découverte, renouvellement et fin de session fournis ;
- inconvénients : stockage par défaut dans `sessionStorage` à remplacer ; modèle de navigation mal adapté au retour mené par le processus principal du desktop ;
- risques : dépendance à suivre et à recenser ;
- coûts/complexité : adaptation du transport desktop.

### Option B — Implémentation minimale sans dépendance

- avantages : aucune dépendance ; transport du retour injecté (web ou desktop) ; fonctions pures testées par `node:test` ;
- inconvénients : code de sécurité à maintenir ; pas de validation de signature de l'`id_token` côté client (utilisé seulement comme `id_token_hint`) ;
- risques : erreur d'implémentation, réduite par les tests et le parcours réel contre Keycloak (`ClientIhmOidcTest`) ;
- coûts/complexité : environ 800 lignes commentaires compris.

### Option C — Desktop : redirection `app://argos/` interceptée dans la fenêtre

- avantages : aucun port local ;
- inconvénients : la page du fournisseur s'afficherait dans l'application (agent utilisateur intégré, RFC 8252 §8.12) ;
- risques : saisie du mot de passe dans une fenêtre contrôlée par l'application.

### Option D — Desktop : persistance du jeton de renouvellement par `safeStorage`

- avantages : session conservée entre deux lancements ;
- inconvénients : jeton sur le disque, même chiffré ; non demandé par le lot 7.

## Décision

**Proposition : option B, sans l'option C ni l'option D.**

1. La session OIDC de l'IHM (`argos-ui/src/session/`) est une implémentation minimale sans dépendance : PKCE S256, state aléatoire vérifié, adresse nettoyée au retour, renouvellement avant expiration, fin de session chez le fournisseur.
2. Les jetons vivent dans la seule mémoire de la page (champ privé), jamais dans un stockage du navigateur, un cookie, une adresse ou un journal ; ils sont perdus au rechargement de la page ou à la fermeture du desktop.
3. Portail web : redirection pleine page ; seule la demande en cours (state, vérificateur PKCE, adresse de retour, vue demandée) est écrite dans `sessionStorage` pendant la redirection, puis effacée au retour avant vérification.
4. Desktop : autorisation dans le navigateur du système ; le processus principal écoute `http://127.0.0.1:<port éphémère>/argos/retour` le temps d'un retour, n'ouvre que des adresses de l'émetteur configuré avec PKCE S256, et rend à l'IHM le code et le state, qu'elle échange avec son vérificateur. La CSP de l'IHM n'ajoute que l'origine du fournisseur configuré.

## Conséquences positives

- aucun jeton exposé à un script en dehors de la mémoire de la page ni écrit sur le disque ;
- un seul code de session pour les deux clients ; aucune dépendance nouvelle ;
- conformité RFC 8252 pour le desktop ; la saisie du mot de passe reste dans le navigateur du système.

## Conséquences négatives

- reconnexion à chaque rechargement de page et à chaque lancement du desktop (atténuée par la session du fournisseur) ;
- code de sécurité maintenu dans le dépôt.

## Conséquences neutres / compromis

- ADR-0022 point 6 (`safeStorage`) n'est pas mis en œuvre ; une persistance pourra être décidée plus tard (question Q-1 du changement) sans changer les exigences ACC-07 à ACC-10.
- Un port local reste ouvert au plus cinq minutes pendant une connexion du desktop.

## Méthode de validation

- tests `node:test` : `argos-ui/tests/oidc.test.mjs`, `session.test.mjs`, `autorisation.test.mjs`, `securite-session.test.mjs` ; `argos-desktop/test/connexion.test.js`, `ui-protocol.test.js` ;
- parcours réel contre le Keycloak des Dev Services : `argos-server/src/test/java/io/argos/api/comptes/ClientIhmOidcTest.java` (portail, bouclage à port libre, origine `app://argos`, PKCE obligatoire) ;
- tests de bout en bout navigateur et Electron (étape ultérieure du lot 7) ;
- revue de sécurité de la CSP et des canaux du desktop.

## Traçabilité

- **Exigences** : ACC-07 à ACC-10 (`/openspec/changes/archive/2026-11-18-connecter-ihm-oidc/specs/comptes-acces/spec.md`)
- **Scénarios qualité** : aucun ; risque R-18
- **Diagrammes** : DC-1 à DC-4 (`/openspec/changes/archive/2026-11-18-connecter-ihm-oidc/design.md`), DF-ACC-2 (`docs/specifications/comptes-acces.md`)
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-ui/src/session/`, `argos-ui/src/api/autorisation.ts`, `argos-desktop/src/connexion.js`, `argos-desktop/src/ui-protocol.js`, `argos-server/src/test/resources/keycloak/argos-realm.json`

## Notes

Aucune dépendance ajoutée : rien à recenser au titre d'ADR-0021.
