# Proposition : suivre les évolutions réglementaires avec validation par un expert

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende dans le modèle du schéma detailed-uml. -->

## Why — Pourquoi

Le profil `expert-metier` doit repérer tôt les textes officiels qui touchent ses domaines et préparer une fiche fiable [PROPOSITION — ADR-0018 (Proposé) ; exigence PRF-03]. Argos étant un produit ouvert (ADR-0025, Accepté), les domaines et le dictionnaire métier ne sont plus ceux d'un éditeur précis : ils sont configurés par chaque espace. Le lot 3 du registre liste cette capacité.

## What Changes — Changements

- Collecte quotidienne des textes du Journal officiel (API Légifrance, repli sur les données ouvertes LEGI et JORF).
- Qualification déterministe par domaine à partir d'un dictionnaire métier propre à l'espace ; proposition IA marquée en cas de doute.
- Distinction systématique des dates de publication et d'entrée en vigueur.
- Fiche d'analyse préparée par l'IA, validée ou corrigée par l'expert avant diffusion.
- Suivi jusqu'à la livraison, calendrier des entrées en vigueur, rappels, lien vers un outil de suivi externe.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `veille-reglementaire` (préfixe `REG`) : collecte des textes officiels, qualification, dates d'effet, fiche d'analyse validée, suivi, calendrier.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Textes du Journal officiel et codes consolidés suivis.

### Exclu

- Les autres sources réglementaires (normes déclaratives, sites ministériels) : pages surveillées par `veille-rss-web`.
- Tout avis juridique.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Expert métier | valide ou corrige les fiches, qualifie l'impact | [PROPOSITION] — ADR-0018 (Proposé) |
| Utilisateur d'un profil couvrant le domaine | consulte les fiches validées | [PROPOSITION] — PRF-06 |
| API Légifrance (portail PISTE) | fournit les textes | [QUESTION] — Q-1 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Veille réglementaire avec validation | [PROPOSITION] | `/docs/architecture/adr/0018-veille-reglementaire-mkp.md` (Proposé) |
| Garde-fou de validation | [PROPOSITION] | exigence PRF-06 |
| Produit ouvert : domaines configurables | [DÉCISION] | ADR-0025 (Accepté) |
| Fiche produite via la passerelle IA | [PROPOSITION] | exigences IAG-04, IAG-05 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- ADR-0018 nomme les MKP et les modules RH d'un éditeur ; depuis ADR-0025, le rôle s'appelle « expert métier » et les domaines sont propres à chaque espace.

## Impact

- **Composants** (arc42 §5) : `reg-intel`, `source-ingestion`, `signal`, `catalog`, `ai-gateway`
- **Contrats d'interface** : OpenAPI des fiches et de leur validation ; outil MCP `get_regulatory_changes`
- **Données et persistance** : textes, fiches, historique de validation, dictionnaire métier par espace
- **Sécurité, confidentialité, droits** : fiche invisible avant validation ; aucun avis juridique
- **Exploitation et observabilité** : état de la collecte Légifrance
- **Documentation durable à mettre à jour** : `/docs/specifications/veille-reglementaire.md`, registre `/docs/specifications/README.md`, arc42 §6
- **ADR** : ADR-0018 (Proposé) à mettre à jour (expert métier, produit ouvert)

## Conception requise

Complète : nouveau cycle de vie (fiche), source externe à conditions d'usage, dépendance à la passerelle IA.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Les conditions d'usage de l'API Légifrance autorisent-elles un produit diffusé, éventuellement commercial ? | oui pour REG-01 | porteur du projet |
| Q-2 | À quelles échéances envoyer les rappels avant une entrée en vigueur ? | non : paramétrable | Product Owner |
| Q-3 | Quel outil de suivi externe relier (Jira, GitHub, GitLab) ? | non | porteur du projet |
| Q-4 | Faut-il fournir des dictionnaires métier types à la création d'un espace ? | non | Product Owner |
