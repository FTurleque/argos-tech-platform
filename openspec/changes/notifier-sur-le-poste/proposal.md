# Proposition : notifier les alertes sur le poste de travail

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le client desktop a été ajouté pour recevoir les alertes critiques sans dépendre d'un navigateur ni d'une messagerie d'équipe [PROPOSITION — ADR-0022 (Proposé)]. Le socle Electron sait déjà afficher une notification à la demande de l'IHM [EXISTANT — `argos-desktop/src/main.js`, canal `argos:notify`], mais aucune règle ne dit quand et comment les alertes arrivent sur le poste.

## What Changes — Changements

- Notification système à chaque alerte immédiate destinée à l'utilisateur, fenêtre ouverte ou masquée.
- Ouverture de l'élément au clic sur la notification.
- Compteur d'alertes ouvertes dans la zone de notification.
- Préférence pour couper les notifications du poste sans couper les autres canaux.
- Contenu minimal à l'écran ; reprise des alertes manquées après une coupure.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `notifications-poste` (préfixe `NOT`) : notifications système, ouverture de l'élément, compteur de zone de notification, préférence, reprise.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Client desktop Electron et sa connexion au serveur pour les alertes.

### Exclu

- Les notifications quand l'application est entièrement fermée (question Q-1).
- Les notifications mobiles.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur du client desktop | reçoit et ouvre les notifications | [PROPOSITION] — ADR-0022 (Proposé) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Client desktop Electron, notifications, zone de notification | [PROPOSITION] | `/docs/architecture/adr/0022-client-desktop-electron.md` (Proposé) |
| Notification et menu de zone de notification déjà codés | [EXISTANT] | `argos-desktop/src/main.js` (non exécuté, vérification de types seulement) |
| Alertes immédiates et canal desktop | [PROPOSITION] | exigences ALR-01, ALR-06 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- Le compteur de la zone de notification n'existe pas dans `argos-desktop/src/main.js` : seule une infobulle fixe « ARGOS » est définie.

## Impact

- **Composants** (arc42 §5) : `argos-desktop`, IHM `argos-ui` (couche contenu), `alerting`
- **Contrats d'interface** : flux d'alertes de l'utilisateur (défini par `diffuser-alertes-et-digests`)
- **Données et persistance** : préférence « notifications du poste » de l'utilisateur
- **Sécurité, confidentialité, droits** : contenu affiché sur l'écran verrouillé limité
- **Exploitation et observabilité** : aucune
- **Documentation durable à mettre à jour** : `/docs/specifications/notifications-poste.md`, registre `/docs/specifications/README.md`, ADR-0022
- **ADR** : aucun nouvel ADR requis

## Conception requise

Allégée : comportement local au client desktop, sur un contrat d'alertes défini par un autre change.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Faut-il recevoir les notifications quand l'application est entièrement fermée (service en arrière-plan) ? | non | porteur du projet |
| Q-2 | L'application doit-elle démarrer avec la session de l'utilisateur ? | non | porteur du projet |
