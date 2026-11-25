# Proposition : écrire les décisions d'architecture du socle

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le code applique depuis le début des décisions d'architecture que leurs ADR présentent encore comme des propositions : 14 ADR sur 33 sont « Proposé », dont ADR-0001 (monolithe modulaire, cité en tête du test des frontières de modules), ADR-0013 (corrélation déterministe), ADR-0014 (serveur MCP en lecture seule) et ADR-0016 (RBAC et RLS) [EXISTANT — AUD-ARC-14, /docs/audit/2026-11-21/findings.md#aud-arc-14, vérification du 21/11 à `b142695`]. Le contexte d'ADR-0021 (double licence) affirme qu'aucune dépendance n'est déclarée alors que le build contrôle les licences de 194 dépendances [EXISTANT — AUD-DOC-09, fusionné dans AUD-ARC-14]. Aucun ADR ne décrit le noyau commun (contexte RLS, erreurs du domaine, appelant) que plusieurs modules empruntent déjà par des exceptions au cas par cas ; cet ADR est le prérequis de l'extraction du noyau (AUD-ARC-05) [EXISTANT — AUD-ARC-14, champ « Bloque » : AUD-ARC-05, AUD-DOC-01, AUD-DOC-08]. Le sprint 5 commence par ce constat : les décisions doivent être écrites avant de migrer les consommateurs vers un mécanisme unique [EXISTANT — /docs/audit/2026-11-21/sprints.md, « Sprint 5 », ordre interne].

## What Changes — Changements

- Appliquer aux 14 ADR « Proposé » le statut décidé par le porteur [DÉCISION — décision du porteur, 2026-11-24] :
  - « Accepté » : ADR-0001, 0010, 0011, 0013, 0014, 0015, 0016, 0020, 0022 ;
  - « Accepté » après amendement de clarification : ADR-0003 (noms des modèles canoniques réels) et ADR-0021 (contexte, ligne 16) ;
  - « Accepté » pour le principe (OIDC, autorisation fine dans ARGOS), avec renvoi à ADR-0027 pour le choix de l'IdP : ADR-0008 ;
  - « Proposé » maintenu : ADR-0007 et ADR-0012.
- Le statut écrit de chaque ADR porte le porteur du projet comme décideur et la date du 2026-11-24 [EXIGENCE — /docs/architecture/adr/README.md, « Statuts » : Accepté = décision validée].
- Clarifier, sans changer la décision, les mentions devenues fausses : contexte d'ADR-0021 (dépendances déclarées et contrôlées), champ « Complété par » d'ADR-0008 (ADR-0027 est accepté), champ « Remplace » d'ADR-0028 et d'ADR-0030 (« à l'acceptation du présent ADR », acceptation faite).
- Constater qu'ADR-0004 et ADR-0006 sont déjà marqués « Remplacé » (voir « Écarts constatés ») ; aucune nouvelle modification de leur statut.
- Rédiger l'ADR du noyau commun, au statut **Proposé** : un paquet transverse autorisé à tous les modules porte les mécanismes techniques partagés ; il est soumis au porteur une fois rédigé.
- Mettre à jour l'index `/docs/architecture/adr/README.md` (statut de chaque ADR, phrase finale sur ADR-0010 à ADR-0016) et l'index arc42 §9 (`/docs/architecture/arc42/09-decisions.md`), qui doit rester synchronisé [EXIGENCE — /docs/architecture/adr/README.md, règle 5].
- Aucun comportement du système ne change (**BREAKING** : aucun).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

Aucune — changement documentaire sans comportement contractuel (`skip_specs: true` dans `.openspec.yaml`).

## Périmètre

### Inclus

- Les 14 ADR « Proposé » : ADR-0001, 0003, 0007, 0008, 0010, 0011, 0012, 0013, 0014, 0015, 0016, 0020, 0021, 0022.
- La vérification que ADR-0004, 0006, 0027, 0028 et 0029, cités par l'action du constat, sont déjà dans l'état demandé.
- L'ADR du noyau commun (nouveau, Proposé) et les deux index d'ADR.

### Exclu

- La réécriture de fond des ADR anciens (AUD-DOC-01, AUD-DOC-08) : sprint 12, débloquée par ce changement.
- L'alignement d'arc42 sur le code livré (AUD-DOC-02) : sprint 12.
- La révision d'ADR-0009 et d'ADR-0021 demandée par ADR-0025 (produit ouvert) : décision de licence du porteur, hors constat ; consignée comme question.
- L'extraction du noyau commun dans le code : changement `extraire-noyau-commun`.
- La configuration d'Archgate (`.archgate/adrs/` ne contient que l'exemple `GEN-001`, aucune règle) : hors constat.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Porteur du projet | a décidé du statut des 14 ADR « Proposé » ; décide de l'ADR du noyau commun | [DÉCISION] — décision du porteur, 2026-11-24 ; Q-5 pour le noyau commun |
| Développeur, agent de codage | applique les statuts décidés et les clarifications, rédige l'ADR du noyau commun | [PROPOSITION] |
| Lecteur de l'architecture | lit des statuts conformes au code livré | [PROPOSITION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| 14 ADR « Proposé » sur 33, dont 0001, 0013, 0014, 0016 appliqués | [EXISTANT] | AUD-ARC-14 ; `grep -m1 Statut docs/architecture/adr/0*.md` à `b142695` (recompté le 2026-11-24 : 14 Proposé) |
| ADR-0001 cité par le test des frontières | [EXISTANT] | `argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java:21` |
| Contexte d'ADR-0021 obsolète, contrôle de licences au build | [EXISTANT] | AUD-DOC-09 ; `docs/architecture/adr/0021-double-licence.md:16` ; `argos-server/pom.xml:221` (profil `licences`) ; `Dockerfile:24` |
| ADR-0004 et ADR-0006 déjà « Remplacé » | [EXISTANT] | `docs/architecture/adr/0004-ai-gateway-claude.md:3`, `0006-collecteurs-separes.md:3` ; commits `faf9663`, `18bb454` |
| ADR-0027, 0028, 0029 déjà « Accepté » | [EXISTANT] | ligne Statut de chaque ADR ; `/docs/architecture/adr/README.md` |
| Règles de statut, de remplacement et de synchronisation de l'index | [EXIGENCE] | `/docs/architecture/adr/README.md`, « Statuts » et « Règles » 1, 2, 5 |
| Noyau commun implicite, cinq règles d'exception | [EXISTANT] | AUD-ARC-05 ; `ModuleBoundariesTest.java:93, :125, :144, :164, :191` (révision `b142695`) |
| Statut cible de chacun des 14 ADR « Proposé » (design.md, D1) | [DÉCISION] | décision du porteur, 2026-11-24 |
| ADR-0025 demande de revoir ADR-0008, ADR-0009 et ADR-0021 | [EXISTANT] | `/docs/architecture/adr/README.md` (ligne ADR-0025) ; AUD-DOC-09 |

## Écarts constatés

- **Preuve qui a bougé** : l'action d'AUD-ARC-14 demande de marquer ADR-0004 et ADR-0006 « Remplacé » et de passer ADR-0027, 0028 et 0029 en « Accepté ». C'est déjà fait (commits `18bb454` et `faf9663`) ; la vérification du 21/11 le reflète (« 15 Accepté, 4 Remplacé ») mais le texte « Action » et la phrase « Impact » (« ADR-0004 et ADR-0006 restent formellement en vigueur ») datent de la preuve d'origine. Ce changement ne fait que le constater.
- `/docs/architecture/adr/README.md` n'affiche pas de statut pour la plupart des ADR anciens et se termine par « Les ADR-0010 à ADR-0016 restent **Proposés** » ; `/docs/architecture/arc42/09-decisions.md` porte les statuts.
- ADR-0008 indique « Complété par ADR-0027 (Proposé) » alors qu'ADR-0027 est accepté ; ADR-0028 et ADR-0030 remplacent leur prédécesseur « à l'acceptation du présent ADR », acceptation déjà intervenue.
- ADR-0003 nomme des événements canoniques (`IntelligenceItem`, `ProjectActivity`) absents du code ; le modèle normalisé réel est l'avis normalisé de `source-ingestion` (`argos-server/src/main/java/io/argos/ingestion/NormalizedAdvisory.java`).
- Archgate est initialisé (`.archgate/config.json`, `domains` vide) mais ne porte aucun ADR du projet ni aucune règle : les ADR vivent dans `docs/architecture/adr/`.

## Impact

- **Composants** (arc42 §5) : aucun composant modifié. L'ADR du noyau commun introduit un nouveau composant transverse proposé (« commun »), réalisé par `extraire-noyau-commun`.
- **Contrats d'interface** : aucun.
- **Données et persistance** : aucune.
- **Sécurité, confidentialité, droits** : aucune modification du système ; ADR-0016 (RBAC + RLS) et ADR-0014 (lecture seule) passent en « Accepté » (décision du porteur, 2026-11-24).
- **Exploitation et observabilité** : aucune.
- **Documentation durable à mettre à jour** : ADR concernés (ligne Statut, clarifications), `/docs/architecture/adr/README.md`, `/docs/architecture/arc42/09-decisions.md` ; aucune fiche de capacité, aucun diagramme.
- **ADR** : nouvel ADR du noyau commun (Proposé, prochain numéro libre, ADR-0034 à la date du 2026-11-24) ; statuts des 14 ADR « Proposé » selon la décision du porteur du 2026-11-24 (12 acceptés, 0007 et 0012 maintenus « Proposé »).
- **Dépendances entre changes** : ce changement débloque `extraire-noyau-commun` (l'ADR du noyau commun doit avoir reçu la réponse du porteur avant l'extraction). Il précède `typer-echanges-entre-modules` dans l'ordre du sprint 5, sans dépendance technique.

## Conception requise

allégée — aucun comportement ni composant livré ; la conception consigne la grille de vérification, le statut décidé et la preuve par ADR, et les options de l'ADR du noyau commun.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-4 | La révision d'ADR-0009 et d'ADR-0021 demandée par ADR-0025 fait-elle l'objet d'un ADR de suite ? | non : hors périmètre | porteur du projet |
| Q-5 | Le porteur accepte-t-il l'ADR du noyau commun une fois rédigé (tâche 3.2) ? | oui : bloque `extraire-noyau-commun` | porteur du projet |
