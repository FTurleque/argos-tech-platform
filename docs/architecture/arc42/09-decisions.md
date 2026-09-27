# 9. Décisions architecturales

Cette section indexe les ADR. Elle ne recopie pas leur contenu.

> **Baseline active : v2.4 — 27 septembre 2026.** Les ADR-0001 à ADR-0009 conservent leur historique. Les décisions issues de la v2.3 occupent ADR-0010 à ADR-0018. La v2.4 ajoute ADR-0019 pour la stack applicative.

## 9.1 Index

| ID | Titre | Statut | Date | Remplace |
|---|---|---|---|---|
| ADR-0001 | Adopter un monolithe modulaire pour la première version | Proposé | 2026-09-24 | — |
| ADR-0002 | Utiliser PostgreSQL comme stockage principal | Accepté | 2026-09-24 | — |
| ADR-0003 | Introduire un modèle canonique d’événements | Proposé | 2026-09-24 | — |
| ADR-0004 | Encapsuler Claude derrière un AI Gateway | Proposé | 2026-09-24 | — |
| ADR-0005 | Utiliser Mermaid comme source des diagrammes d’architecture | Accepté | 2026-09-24 | — |
| ADR-0006 | Maintenir FreshRSS et changedetection.io comme systèmes séparés | Proposé | 2026-09-24 | — |
| ADR-0007 | Limiter n8n aux automatisations périphériques | Proposé | 2026-09-24 | — |
| ADR-0008 | Utiliser l’IdP d’entreprise via OIDC en première intention | Proposé | 2026-09-24 | — |
| ADR-0009 | Utiliser PolyForm Internal Use License 1.0.0 | Accepté | 2026-09-24 | — |
| ADR-0010 | OSV principal, enrichi par NVD / KEV / EPSS / CERT-FR | Proposé | 2026-09-27 | — |
| ADR-0011 | CycloneDX comme inventaire SBOM de référence | Proposé | 2026-09-27 | — |
| ADR-0012 | Dependency-Track optionnel au POC | Proposé | 2026-09-27 | — |
| ADR-0013 | Corrélation signal ↔ projet déterministe | Proposé | 2026-09-27 | — |
| ADR-0014 | Serveur MCP intégré, lecture seule, Streamable HTTP | Proposé | 2026-09-27 | — |
| ADR-0015 | PostgreSQL FTS au MVP, pgvector différé | Proposé | 2026-09-27 | — |
| ADR-0016 | Cloisonnement workspace avec RBAC + PostgreSQL RLS | Proposé | 2026-09-27 | — |
| ADR-0017 | POC local Docker puis VM interne | Proposé | 2026-09-27 | — |
| ADR-0018 | Veille réglementaire intégrée avec validation MKP | Proposé | 2026-09-27 | — |
| ADR-0019 | Java 17+ / Quarkus / Vue.js 3 / PostgreSQL | Accepté | 2026-09-27 | — |

## 9.2 Règles de gouvernance

- un ADR **Accepté** n’est jamais supprimé ni réécrit rétroactivement ;
- un changement crée un nouvel ADR et marque l’ancien **Remplacé** ;
- un ADR doit citer critères, options et méthode de validation ;
- tout choix coûteux à inverser doit passer par ADR ;
- les décisions encore hypothétiques restent `Proposé` ;
- le dossier Word/PDF est une vue de proposition et de décision ; `docs/architecture/` reste la source d’architecture versionnée ;
- tout changement architectural implémenté doit mettre à jour dans la même PR les ADR, arc42 et diagrammes concernés.

## 9.3 Décisions encore à formaliser après la baseline v2.4

1. mécanisme précis de scheduling/jobs ;
2. ajout ou non d’un broker après mesure ;
3. Flyway ou Liquibase ;
4. gestion des secrets et rotation ;
5. stack d’observabilité réellement imposée par l’entreprise ;
6. stratégie de sauvegarde/PRA entreprise ;
7. mécanisme de notifications Teams/e-mail ;
8. politique de rétention et suppression des données ;
9. critères d’activation de pgvector/RAG après le MVP.

Le frontend n’est plus une question ouverte : **Vue.js 3 est retenu**. Le backend/API est **Quarkus sur Java 17 minimum**.

## 9.4 Preuves

- les ADR listés sont versionnés dans `docs/architecture/adr/` ;
- ADR-0002 et ADR-0019 matérialisent les choix de stack désormais acceptés ;
- ADR-0017 documente la stratégie POC local puis VM ;
- les ADR-0010 à ADR-0018 restent à confirmer par revue d’architecture et/ou preuve issue du POC.

## 9.5 Risque

Le principal risque est de laisser diverger le code, le dossier de proposition et la documentation versionnée. Un contrôle de revue/CI est recommandé pour maintenir l’index, les statuts, les liens arc42 et les sources Mermaid cohérents.
