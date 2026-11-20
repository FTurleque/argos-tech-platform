# Proposition : exposer Argos aux assistants IA par un serveur MCP en lecture seule

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Les assistants IA (Claude Code, IDE) doivent pouvoir vérifier une dépendance, un standard ou une évolution avant de proposer du code [PROPOSITION — ADR-0014 (Proposé) ; baseline v2.3 §4]. La spécification MCP du 2026-07-28 rend le protocole sans état, et l'extension Quarkus MCP Server 2.x la prend en charge [PROPOSITION — `/docs/architecture/adr/0014-mcp-readonly-streamable-http.md`]. Le lot 3 du registre liste cette capacité.

## What Changes — Changements

- Serveur MCP intégré au monolithe, transport Streamable HTTP sans état.
- Huit outils en lecture seule : `search_signals`, `check_dependency`, `get_technology_standard`, `get_project_context`, `upcoming_releases`, `get_security_alerts`, `explain_change`, `get_regulatory_changes`.
- Authentification et cloisonnement identiques au portail.
- Contenu externe balisé et tronqué ; limitation de débit et journalisation.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `serveur-mcp` (préfixe `MCP`) : outils MCP en lecture seule, authentification, cloisonnement, contenu externe balisé, limitation de débit.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Outils, ressources en lecture (référentiel des standards) et contrôle d'accès.

### Exclu

- Tout outil d'écriture.
- Les prompts MCP partagés : ajout ultérieur.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Développeur | interroge Argos depuis son assistant IA | [PROPOSITION] — ADR-0014 (Proposé) |
| Assistant IA (client MCP) | appelle les outils pour le compte de l'utilisateur | [PROPOSITION] — ADR-0014 (Proposé) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| MCP intégré, lecture seule, Streamable HTTP | [PROPOSITION] | `/docs/architecture/adr/0014-mcp-readonly-streamable-http.md` (Proposé) |
| Outils `check_dependency`, `get_security_alerts` | [PROPOSITION] | baseline v2.3 §4 |
| Contenu externe = donnée non fiable | [PROPOSITION] | risque R-11 ; arc42 §8.3 |
| Cloisonnement et garde-fous | [PROPOSITION] | exigences ACC-04, PRF-06 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- La liste complète des huit outils figure dans le dossier de proposition v2.4 (§8.7, hors dépôt) ; baseline v2.3 n'en cite que deux.

## Impact

- **Composants** (arc42 §5) : `mcp-server`, `API & Security`, modules métier en lecture
- **Contrats d'interface** : schémas des outils MCP (entrées, sorties)
- **Données et persistance** : journal des appels MCP
- **Sécurité, confidentialité, droits** : authentification, cloisonnement, injection indirecte d'instructions
- **Exploitation et observabilité** : limitation de débit, journal des appels
- **Documentation durable à mettre à jour** : `/docs/specifications/serveur-mcp.md`, registre `/docs/specifications/README.md`, arc42 §5.3
- **ADR** : ADR-0014 (Proposé) reste la référence

## Conception requise

Complète : nouvelle interface externe exposée aux assistants IA, avec des enjeux d'authentification et d'injection d'instructions.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Quelle limite de débit par utilisateur ? | **Répondue le 2026-11-16** : 60 requêtes par minute et par utilisateur, paramétrable [DÉCISION] | porteur du projet |
| Q-2 | Quelle longueur maximale pour un texte externe renvoyé ? | **Répondue le 2026-11-16** : 2 000 caractères, paramétrable [DÉCISION] | architecte |
| Q-3 | Comment un IDE obtient-il son jeton (flux d'appareil OAuth, jeton personnel) ? | **Répondue le 2026-11-16** : flux d'appareil OAuth 2.0 (RFC 8628) de Keycloak ; Argos ne stocke aucun jeton personnel [DÉCISION] | architecte / sécurité |
