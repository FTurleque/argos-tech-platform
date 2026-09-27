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
- [ADR-0004 — AI Gateway Claude](0004-ai-gateway-claude.md)
- [ADR-0005 — Mermaid pour les diagrammes](0005-mermaid-diagrammes.md) — **Accepté**
- [ADR-0006 — Collecteurs séparés](0006-collecteurs-separes.md)
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
- [ADR-0017 — POC local Docker puis VM interne](0017-deploiement-vm-conteneurs.md)
- [ADR-0018 — Veille réglementaire et validation MKP](0018-veille-reglementaire-mkp.md)

### Décisions v2.4

- [ADR-0019 — Java 17+ / Quarkus / Vue.js 3 / PostgreSQL](0019-stack-java17-quarkus-vue3-postgresql.md) — **Accepté**

Les ADR-0010 à ADR-0018 restent **Proposés** jusqu’à validation par revue d’architecture et/ou preuves produites pendant le POC.

Utiliser [`template.md`](template.md) pour toute nouvelle décision.
