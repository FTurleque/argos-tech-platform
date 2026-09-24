# Architecture Decision Records

Les ADR enregistrent les décisions architecturales importantes d’ARGOS.

## Statuts

- **Proposé** : décision préparée mais pas encore validée ;
- **Accepté** : décision validée et applicable ;
- **Déprécié** : encore présent pour historique mais déconseillé ;
- **Remplacé** : supplanté par un nouvel ADR ;
- **Rejeté** : option analysée puis abandonnée.

## Règles

1. Ne jamais supprimer un ADR accepté.
2. En cas de changement, créer un nouvel ADR et référencer celui qu’il remplace.
3. Un ADR doit contenir contexte, critères, options, décision, conséquences et validation.
4. Les preuves dans le code et les tests doivent être ajoutées lorsqu’elles existent.
5. Le fichier `../arc42/09-decisions.md` doit rester synchronisé avec cet index.

## Index initial

- [ADR-0001 — Monolithe modulaire](0001-monolithe-modulaire.md)
- [ADR-0002 — PostgreSQL comme stockage principal](0002-postgresql-stockage-principal.md)
- [ADR-0003 — Modèle canonique d’événements](0003-modele-canonique-evenements.md)
- [ADR-0004 — AI Gateway Claude](0004-ai-gateway-claude.md)
- [ADR-0005 — Mermaid pour les diagrammes](0005-mermaid-diagrammes.md)
- [ADR-0006 — Collecteurs séparés](0006-collecteurs-separes.md)
- [ADR-0007 — n8n périphérique](0007-n8n-peripherique.md)
- [ADR-0008 — IAM OIDC](0008-iam-oidc.md)

Utiliser [`template.md`](template.md) pour toute nouvelle décision.
