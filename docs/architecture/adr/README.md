# Architecture Decision Records

Les ADR enregistrent les décisions architecturales importantes d’ARGOS.

## Statuts

- **Proposé** : décision préparée mais pas encore validée ;
- **Accepté** : décision validée et applicable ;
- **Déprécié** : encore présent pour historique mais déconseillé ;
- **Remplacé** : supplanté par un nouvel ADR ;
- **Rejeté** : option analysée puis abandonnée.

## Règles

1. Ne jamais supprimer ni renuméroter un ADR accepté ou déjà versionné.
2. En cas de changement, créer un nouvel ADR et référencer celui qu’il remplace.
3. Un ADR doit contenir contexte, critères, options, décision, conséquences et validation.
4. Les preuves dans le code, les tests et les mesures du POC doivent être ajoutées lorsqu’elles existent.
5. Le fichier `../arc42/09-decisions.md` doit rester synchronisé avec cet index.
6. Une décision du dossier de proposition qui n’a pas encore d’ADR reste une **décision proposée**, jamais un fait implémenté.

## Index

### Baseline initiale

- [ADR-0001 — Monolithe modulaire](0001-monolithe-modulaire.md)
- [ADR-0002 — PostgreSQL comme stockage principal](0002-postgresql-stockage-principal.md) — **Accepté v2.4**
- [ADR-0003 — Modèle canonique d’événements](0003-modele-canonique-evenements.md)
- [ADR-0004 — AI Gateway Claude](0004-ai-gateway-claude.md) — **Remplacé par ADR-0029**
- [ADR-0005 — Mermaid pour les diagrammes](0005-mermaid-diagrammes.md) — **Accepté**
- [ADR-0006 — Collecteurs séparés](0006-collecteurs-separes.md) — **Remplacé par ADR-0028 pour la veille RSS/Atom et web** ; reste de la portée inchangé
- [ADR-0007 — n8n périphérique](0007-n8n-peripherique.md)
- [ADR-0008 — IAM OIDC](0008-iam-oidc.md)
- [ADR-0009 — PolyForm Internal Use License 1.0.0](0009-licence-polyform-internal-use.md) — **Accepté**

### Compléments issus du dossier d’architecture v2.3

- [ADR-0010 — Sources de vulnérabilités](0010-sources-vulnerabilites.md)
- [ADR-0011 — SBOM CycloneDX](0011-sbom-cyclonedx.md)
- [ADR-0012 — Dependency-Track optionnel](0012-dependency-track-optionnel.md)
- [ADR-0013 — Corrélation déterministe](0013-correlation-deterministe.md)
- [ADR-0014 — MCP lecture seule / Streamable HTTP](0014-mcp-readonly-streamable-http.md)
- [ADR-0015 — PostgreSQL FTS / pgvector différé](0015-postgresql-fts-pgvector.md)
- [ADR-0016 — Workspaces RBAC + RLS](0016-workspace-rbac-rls.md)
- [ADR-0017 — POC local Docker puis VM interne](0017-deploiement-vm-conteneurs.md) — cible VM interne remplacée par ADR-0026
- [ADR-0018 — Veille réglementaire et validation MKP](0018-veille-reglementaire-mkp.md) — **Remplacé par ADR-0030**

### Décisions v2.4

- [ADR-0019 — Java 17+ / Quarkus / Vue.js 3 / PostgreSQL](0019-stack-java17-quarkus-vue3-postgresql.md) — **Accepté**

### Gouvernance documentaire

- [ADR-0020 — Spécifier les exigences avec OpenSpec et le schéma `detailed-uml`](0020-specifications-openspec-detailed-uml.md) — Proposé

### Licence

- [ADR-0021 — Préserver la possibilité d'une double licence](0021-double-licence.md) — Proposé ; complète ADR-0009

### Clients

- [ADR-0022 — Client desktop Electron réutilisant l'IHM Vue.js 3](0022-client-desktop-electron.md) — Proposé

### Personnalisation

- [ADR-0023 — Mises en page sélectionnables par utilisateur](0023-mises-en-page-selectionnables.md) — **Accepté**
- [ADR-0024 — Profils de veille extensibles et cumulables](0024-profils-de-veille-extensibles.md) — **Accepté**

### Positionnement

- [ADR-0025 — Produit ouvert à tous les publics, porté à titre personnel](0025-produit-ouvert-projet-personnel.md) — **Accepté** ; ADR-0008, ADR-0009 et ADR-0017 à revoir
- [ADR-0026 — Hébergement sur le serveur Synology du porteur](0026-hebergement-synology.md) — **Accepté** ; remplace ADR-0017 pour la VM interne

### Identité

- [ADR-0027 — Keycloak auto-hébergé comme fournisseur d'identité](0027-keycloak-idp-auto-heberge.md) — **Accepté** ; complète ADR-0008
- [ADR-0033 — Session OIDC de l'IHM en mémoire, desktop connecté par l'interface de bouclage](0033-session-oidc-ihm-memoire-bouclage.md) — **Accepté** ; complète ADR-0027, précise ADR-0022 point 6
- [ADR-0038 — Configuration de l'IHM lue à l'exécution dans la réponse publique du serveur](0038-configuration-ihm-a-l-execution.md) — Proposé ; complète ADR-0033

### Collecte

- [ADR-0028 — Collecteur RSS/Atom et pages surveillées intégré au serveur](0028-collecteur-veille-web-integre.md) — **Accepté** ; remplace ADR-0006 pour la veille RSS/Atom et web

### Intelligence artificielle

- [ADR-0029 — Passerelle IA servie par un modèle local Ollama](0029-passerelle-ia-ollama-local.md) — **Accepté** ; remplace ADR-0004

### Veille réglementaire

- [ADR-0030 — Veille réglementaire validée par l'expert métier, domaines par espace et données ouvertes JORF](0030-veille-reglementaire-expert-metier.md) — **Accepté** ; remplace ADR-0018

### Exploitation du serveur

- [ADR-0031 — Planifier les traitements périodiques avec quarkus-scheduler dans le serveur](0031-planification-quarkus-scheduler.md) — **Accepté**
- [ADR-0032 — Versionner le schéma PostgreSQL par des migrations SQL Flyway appliquées au démarrage](0032-migrations-flyway.md) — **Accepté**
- [ADR-0035 — Comptes et isolation des bases de la plateforme](0035-comptes-et-isolation-des-bases.md) — **Proposé** ; précise ADR-0016, ADR-0026 et ADR-0027
- [ADR-0036 — Pile d'observabilité du serveur](0036-pile-observabilite-serveur.md) — **Accepté**

### Outillage de développement

- [ADR-0034 — Vérifier chaque changement par GitHub Actions et suivre les dépendances avec Renovate](0034-integration-continue-github-actions-renovate.md) — Proposé

Les ADR-0010 à ADR-0016 restent **Proposés** jusqu’à validation par revue d’architecture et/ou preuves produites pendant le POC.

Utiliser [`template.md`](template.md) pour toute nouvelle décision.
