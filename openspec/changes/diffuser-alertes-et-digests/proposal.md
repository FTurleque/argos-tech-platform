# Proposition : diffuser les alertes ciblées et les résumés périodiques

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

La promesse « Réagir » d'Argos est une alerte ciblée vers les seules personnes concernées [PROPOSITION — ADR-0010 (Proposé), cible < 1 h ; baseline v2.3 §6, critère 1]. Le lot 2 de `/docs/specifications/README.md` liste « alerte ciblée et digests » comme capacité restant à spécifier. Le principal risque produit est le bruit [PROPOSITION — arc42 §11 ; dossier v2.4 §A.12] : les règles doivent limiter les alertes immédiates aux impacts établis.

## What Changes — Changements

- Règles d'alerte portées par les abonnements (type, gravité, projets, canal, fréquence).
- Alerte immédiate uniquement pour un impact exact ou probable ; les impacts « à vérifier » restent dans le fil.
- Une seule alerte par signal, utilisateur et canal.
- Résumés quotidiens et hebdomadaires des éléments non alertés immédiatement.
- Diffusion maintenue sans résumé IA ; canaux e-mail et notification sur le poste.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `alertes-digests` (préfixe `ALR`) : règles d'alerte, alertes immédiates, regroupement, résumés périodiques, canaux de diffusion.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Évaluation des règles, regroupement, envoi par e-mail, remise au client desktop (change `notifier-sur-le-poste`).

### Exclu

- Les canaux de messagerie d'équipe (Teams, Slack, Matrix) : question Q-2.
- Le statut d'action d'un impact (corrigé, risque accepté) : relève de `correlation-impact`.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur | reçoit alertes et résumés selon ses abonnements | [PROPOSITION] — ABO-01 à ABO-04 |
| Administrateur d'espace | définit les règles du socle d'équipe | [PROPOSITION] — ABO-03 |
| Serveur de messagerie | achemine les e-mails | [QUESTION] — Q-1 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Alerte ciblée < 1 h | [PROPOSITION] | `/docs/architecture/adr/0010-sources-vulnerabilites.md` (Proposé) |
| Niveaux exact / probable / à vérifier, à vérifier sans alerte | [PROPOSITION] | `/docs/architecture/adr/0013-correlation-deterministe.md` (Proposé) |
| Aucune alerte bloquée sans IA | [PROPOSITION] | baseline v2.3 §6 ; QS-10 ; exigence COR-05 |
| Abonnements TEAM / PERSONAL | [PROPOSITION] | exigences ABO-01 à ABO-04 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

Aucun écart constaté (comparaison avec les ADR et sections arc42 citées).

## Impact

- **Composants** (arc42 §5) : `alerting`, `workspace`, `impact-engine`
- **Contrats d'interface** : flux d'alertes pour le client desktop ; gabarits d'e-mail
- **Données et persistance** : alertes émises, résumés, état d'envoi par canal
- **Sécurité, confidentialité, droits** : une alerte ne contient que des données autorisées pour son destinataire
- **Exploitation et observabilité** : file d'envoi, échecs d'envoi visibles
- **Documentation durable à mettre à jour** : `/docs/specifications/alertes-digests.md`, registre `/docs/specifications/README.md`, arc42 §6.1
- **ADR** : aucun nouvel ADR requis

## Conception requise

Allégée : traitement asynchrone dans un module existant, sans nouvelle dépendance structurante hors du serveur de messagerie.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Quel serveur d'envoi d'e-mails : relais du Synology ou service externe ? | **Répondue le 2026-11-16** : relais SMTP du Synology (DSM), configuré par variables d'environnement ; aucun service externe payant [DÉCISION] | porteur du projet |
| Q-2 | Faut-il un canal de messagerie d'équipe (Teams, Slack, Matrix) ? | **Répondue le 2026-11-16** : non : aucun canal de messagerie d'équipe pour l'instant [DÉCISION] | porteur du projet |
| Q-3 | À quelle heure et quel jour partent les résumés ? | **Répondue le 2026-11-16** : résumé quotidien à 08:00 et hebdomadaire le lundi à 08:00 (Europe/Paris), paramétrables [DÉCISION] | Product Owner |
| Q-4 | La cible « alerte en moins d'une heure » est-elle validée ? | non : cible qualité | Product Owner |
