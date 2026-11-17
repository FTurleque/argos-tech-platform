# Tâches : notifier les alertes sur le poste de travail

<!-- Les tâches s'appuient sur le contrat d'alertes de `diffuser-alertes-et-digests` (archivé) et sur le client desktop Electron existant. Une tâche n'est cochée que si sa vérification a réussi. -->

## 1. Flux et notifications

- [ ] 1.1 Lire périodiquement les alertes `DESKTOP` en attente dans la couche contenu de `argos-ui` (l'état `EN_ATTENTE` tient lieu de curseur, D1) — couvre NOT-01, NOT-06-S1 — vérification : test E2E de reprise passe
  - Implémenté : `argos-ui/src/contenu/alertes-poste.ts` (lecture des alertes `DESKTOP` `EN_ATTENTE`, confirmation de remise, reprise, attente croissante), `argos-ui/src/contenu/suivi-poste.ts`, `argos-ui/src/api/client.ts` ; décision D1 (lecture périodique du contrat existant, l'état `EN_ATTENTE` tient lieu de curseur).
  - Preuve obtenue : `npm --prefix argos-ui test` — `tests/alertes-poste.test.mjs` (12 tests : coupure puis retour sur un serveur simulé, NOT-06-S1 ; absence de doublon après un rechargement ; première connexion qui n'est pas une reprise) et `tests/reprise-poste.test.mjs` (3 tests : boucle réelle `demarrer()` / `arreter()` sur l'horloge simulée de `node:test`, lecture toutes les 30 s, perte de connexion signalée, nouvelles tentatives à 5 s puis 10 s, retour avec les deux alertes émises pendant la coupure, sans perte ni doublon ; rechargement de la fenêtre pendant une coupure), au vert.
  - E2E restant à faire (tâche non cochée, vérification exigée : test E2E de reprise) : client desktop connecté à un serveur ARGOS réel (connexion OIDC de l'IHM requise, absente aujourd'hui : le serveur répond 401) ; arrêt du serveur, émission de deux alertes `DESKTOP` immédiates pendant l'arrêt, redémarrage ; constat du bandeau « Hors ligne », puis de « Connexion rétablie : 2 alertes émises pendant la coupure », de deux notifications exactement et de l'état `ENVOYEE` des deux alertes côté serveur. Le contrat HTTP de `api/client.ts` (`GET /api/v1/alertes?canal=DESKTOP&etat=EN_ATTENTE`, `POST /api/v1/alertes/{id}/remise`) n'est couvert que par ce parcours.
- [ ] 1.2 Étendre le pont `window.argosDesktop` (identifiant d'élément, compteur) et le processus principal — couvre NOT-01-S1, NOT-01-S2, NOT-02-S1, NOT-03-S1 — vérification : tests E2E Electron passent ; `npm run check` passe
  - Implémenté : `argos-desktop/src/preload.js`, `argos-desktop/src/canaux.js` (canaux du processus principal, refus de tout émetteur autre que la fenêtre principale, mise au premier plan sur l'alerte), `argos-desktop/src/poste.js`, `argos-desktop/src/main.js` (compteur dans l'infobulle et le menu de la zone de notification, ouverture de l'alerte au clic) ; `argos-ui/src/App.vue`, `argos-ui/src/views/AlertesView.vue`.
  - Preuve obtenue : `npm --prefix argos-desktop run check` — syntaxe et 43 tests au vert : `test/poste.test.js` (doublures des objets Electron, dont le réglage « démarrage avec la session » ignoré là où il est indisponible) ; `test/preload.test.js` (preload livré exécuté dans un contexte `vm` avec un Electron factice : surface exacte du pont, seul module `electron` chargé, canaux identiques à ceux de `canaux.js`, valeurs réduites à des types simples, désabonnement) ; `test/canaux.test.js` (refus des émetteurs autres que la fenêtre principale, bornes de la notification libre, fenêtre masquée ou réduite remise au premier plan) ; `test/chaine-poste.test.js` (chaîne pont → bus IPC en mémoire → canaux → poste → notification factice → clic → fenêtre au premier plan → identifiant reçu par l'IHM : NOT-01-S1, NOT-01-S2, NOT-02-S1, NOT-03-S1).
  - E2E restant à faire (tâche non cochée, vérification exigée : tests E2E Electron) : lancer l'application Electron réelle (binaire absent : script d'installation d'`electron` bloqué par la politique `allow-scripts` de npm) et y vérifier, fenêtre masquée, l'affichage d'une vraie notification système (NOT-01-S1), son absence quand les notifications d'ARGOS sont refusées dans les réglages du système (NOT-01-S2), l'ouverture de la fenêtre au premier plan sur `#/alertes/{id}` au clic (NOT-02-S1) et l'infobulle « ARGOS — 3 alertes ouvertes » de la zone de notification (NOT-03-S1). Les objets Electron réels (`Notification`, `Tray`, `BrowserWindow`, `ipcMain`, `contextBridge`) ne sont exercés que par ce parcours.
- [x] 1.3 Limiter le texte des notifications — couvre NOT-05-S1 — vérification : test unitaire passe
  - `argos-desktop/src/notifications.js` (texte construit par le processus principal sous un titre fixe « Nouvelle alerte ARGOS »), `alertePoste` dans `argos-ui/src/contenu/alertes-poste.ts` (seuls l'identifiant, la gravité et le nombre de projets sont transmis : ni titre du signal, ni nom de projet ou de composant) ; `test/notifications.test.js` (« NOT-05-S1… », titre de signal nommant `log4j-core` jamais affiché) et `tests/alertes-poste.test.mjs` (« NOT-05-S1… ») au vert.
- [x] 1.4 Ajouter la préférence « notifications du poste » dans les réglages — couvre NOT-04-S1 — vérification : test d'intégration passe
  - `argos-desktop/src/preferences.js`, menu de la zone de notification, `argos-ui/src/views/ReglagesPosteView.vue` (avec « Démarrer avec la session », désactivé par défaut, Q-2) ; décision D3 (réglages gardés sur le poste).
  - `test/poste.test.js` (`NOT-04-S1…`, `Q-2…`) et `test/preferences.test.js` au vert. Le canal e-mail n'est pas touché : le réglage ne quitte pas le poste ; l'envoi des e-mails reste couvert par `AlertesTest` (`alertes-digests`).

## 2. Documentation durable et décisions

- [x] 2.1 Mettre à jour la fiche `notifications-poste`, les sections arc42 §5.1 (client desktop), le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
  - Fiche, registre des spécifications, arc42 §5.1 et §6.1.2 (diagramme D-17), glossaire (« alerte ouverte », « réglages du poste »), traçabilité d'ADR-0022.

## 3. Vérifications d'intégration

- [x] 3.1 Valider le changement — vérification : `openspec validate notifier-sur-le-poste --strict` réussit
- [ ] 3.2 Parcours : client desktop masqué, alerte critique de test, notification, clic, alerte affichée ; puis coupure et reprise sans perte — vérification : scénario E2E consigné avec ses résultats
  - Non exécuté : binaire Electron absent du poste de vérification ; l'IHM n'a pas encore de connexion OIDC (le serveur répond 401 à ses appels).

## Matrice de traçabilité

État au 2026-11-17 : implémentation terminée ; preuves par tests unitaires et d'intégration (preload livré et canaux exercés avec doublures Electron, serveur simulé, boucle de lecture sur horloge simulée) ; aucune preuve E2E.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| NOT-01 | NOT-01-S1 | DC-1 | 1.1, 1.2 | `argos-desktop/test/poste.test.js` « NOT-01-S1… » ; `argos-desktop/test/chaine-poste.test.js` « NOT-01-S1… » ; `argos-desktop/test/preload.test.js` « NOT-01-S1 / NOT-05… » ; `argos-ui/tests/alertes-poste.test.mjs` « NOT-01… » | passe (intégration) ; E2E manquant |
| NOT-01 | NOT-01-S2 | DC-1 | 1.1, 1.2 | `argos-desktop/test/poste.test.js` « NOT-01-S2… » ; `argos-desktop/test/chaine-poste.test.js` « NOT-01-S2… » ; `argos-ui/tests/alertes-poste.test.mjs` « NOT-01-S2… » | passe (intégration) ; E2E manquant |
| NOT-02 | NOT-02-S1 | DC-1 | 1.2 | `argos-desktop/test/poste.test.js` « NOT-02-S1… » ; `argos-desktop/test/chaine-poste.test.js` « NOT-02-S1… » ; `argos-desktop/test/canaux.test.js` « NOT-02-S1… » ; `argos-desktop/test/preload.test.js` « NOT-02-S1… » | passe (intégration) ; E2E manquant |
| NOT-03 | NOT-03-S1 | DC-1 | 1.2 | `argos-desktop/test/poste.test.js` « NOT-03-S1… » ; `argos-desktop/test/chaine-poste.test.js` « NOT-03-S1… » ; `argos-desktop/test/preload.test.js` « NOT-03-S1… » ; `argos-ui/tests/alertes-poste.test.mjs` « NOT-03-S1… », « NOT-03… » | passe (intégration) ; E2E manquant |
| NOT-04 | NOT-04-S1 | DC-1 | 1.4 | `argos-desktop/test/poste.test.js` « NOT-04-S1… » ; `argos-desktop/test/preferences.test.js` « NOT-04… » | passe |
| NOT-05 | NOT-05-S1 | DC-1 | 1.3 | `argos-desktop/test/notifications.test.js` « NOT-05-S1… » ; `argos-ui/tests/alertes-poste.test.mjs` « NOT-05-S1… » | passe |
| NOT-06 | NOT-06-S1 | DC-1 | 1.1 | `argos-ui/tests/alertes-poste.test.mjs` « NOT-06-S1… », « NOT-06 : une confirmation… », « NOT-06 : un rechargement… », « NOT-06 : la première connexion… » ; `argos-ui/tests/reprise-poste.test.mjs` « NOT-06-S1 : boucle réelle… », « NOT-06 : rechargement de la fenêtre pendant une coupure… » | passe (serveur simulé) ; E2E manquant |

Commandes exécutées le 2026-11-17 : `npm --prefix argos-desktop ci && npm --prefix argos-desktop run check` (43 tests, 0 échec) ; `npm --prefix argos-ui ci && npm --prefix argos-ui test && npm --prefix argos-ui run build` (28 tests, 0 échec ; build réussi).

## Workflow follow-up

- Exécuter le parcours 3.2 sur un poste où Electron est installé, une fois la connexion OIDC de l'IHM disponible, puis cocher 1.1, 1.2 et 3.2.
- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive notifier-sur-le-poste`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/notifier-sur-le-poste/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
