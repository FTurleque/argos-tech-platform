# 9. Décisions architecturales

Cette section indexe les ADR. Elle ne recopie pas leur contenu.

> **Baseline active : v2.4 — 27 septembre 2026.** Les ADR-0001 à ADR-0009 conservent leur historique. Les décisions issues de la v2.3 occupent ADR-0010 à ADR-0018. La v2.4 ajoute ADR-0019 pour la stack applicative.

## 9.1 Index

| ID | Titre | Statut | Date | Remplace |
|---|---|---|---|---|
| ADR-0001 | Adopter un monolithe modulaire pour la première version | Accepté | 2026-09-24 | — |
| ADR-0002 | Utiliser PostgreSQL comme stockage principal | Accepté | 2026-09-24 | — |
| ADR-0003 | Introduire un modèle canonique d’événements | Accepté | 2026-09-24 | — |
| ADR-0004 | Encapsuler Claude derrière un AI Gateway | Remplacé par ADR-0029 | 2026-09-24 | — |
| ADR-0005 | Utiliser Mermaid comme source des diagrammes d’architecture | Accepté | 2026-09-24 | — |
| ADR-0006 | Maintenir FreshRSS et changedetection.io comme systèmes séparés | Remplacé par ADR-0028 pour la veille RSS/Atom et web | 2026-09-24 | — |
| ADR-0007 | Limiter n8n aux automatisations périphériques | Proposé | 2026-09-24 | — |
| ADR-0008 | Utiliser l’IdP d’entreprise via OIDC en première intention | Accepté | 2026-09-24 | — |
| ADR-0009 | Utiliser PolyForm Internal Use License 1.0.0 | Accepté | 2026-09-24 | — |
| ADR-0010 | OSV principal, enrichi par NVD / KEV / EPSS / CERT-FR | Accepté | 2026-09-27 | — |
| ADR-0011 | CycloneDX comme inventaire SBOM de référence | Accepté | 2026-09-27 | — |
| ADR-0012 | Dependency-Track optionnel au POC | Proposé | 2026-09-27 | — |
| ADR-0013 | Corrélation signal ↔ projet déterministe | Accepté | 2026-09-27 | — |
| ADR-0014 | Serveur MCP intégré, lecture seule, Streamable HTTP | Accepté | 2026-09-27 | — |
| ADR-0015 | PostgreSQL FTS au MVP, pgvector différé | Accepté | 2026-09-27 | — |
| ADR-0016 | Cloisonnement workspace avec RBAC + PostgreSQL RLS | Accepté | 2026-09-27 | — |
| ADR-0017 | POC local Docker puis VM interne | Remplacé (cible VM) par ADR-0026 | 2026-09-27 | — |
| ADR-0018 | Veille réglementaire intégrée avec validation MKP | Remplacé par ADR-0030 | 2026-09-27 | — |
| ADR-0019 | Java 17+ / Quarkus / Vue.js 3 / PostgreSQL | Accepté | 2026-09-27 | — |
| ADR-0020 | Spécifier les exigences avec OpenSpec et le schéma `detailed-uml` | Accepté | 2026-11-15 | — |
| ADR-0021 | Préserver la possibilité d'une double licence | Accepté | 2026-11-15 | — |
| ADR-0022 | Client desktop Electron réutilisant l'IHM Vue.js 3 | Accepté | 2026-11-15 | — |
| ADR-0023 | Mises en page sélectionnables par utilisateur | Accepté | 2026-11-18 | — |
| ADR-0024 | Profils de veille extensibles et cumulables | Accepté | 2026-11-18 | — |
| ADR-0025 | Produit ouvert à tous les publics, porté à titre personnel | Accepté | 2026-11-16 | — |
| ADR-0026 | Hébergement sur le serveur Synology du porteur | Accepté | 2026-11-18 | ADR-0017 (cible VM) |
| ADR-0027 | Authentifier les utilisateurs avec un Keycloak auto-hébergé | Accepté | 2026-11-18 | — (complète ADR-0008) |
| ADR-0028 | Collecter les flux RSS/Atom et les pages surveillées dans le serveur Argos | Accepté | 2026-11-18 | ADR-0006 pour la veille RSS/Atom et web |
| ADR-0029 | Servir la passerelle IA par un modèle local Ollama | Accepté | 2026-11-18 | ADR-0004 |
| ADR-0030 | Veille réglementaire validée par l'expert métier, domaines par espace et données ouvertes JORF | Accepté | 2026-11-18 | ADR-0018 |
| ADR-0031 | Planifier les traitements périodiques avec quarkus-scheduler dans le serveur | Accepté | 2026-11-18 | — |
| ADR-0032 | Versionner le schéma PostgreSQL par des migrations SQL Flyway appliquées au démarrage | Accepté | 2026-11-18 | — |
| ADR-0033 | Tenir la session OIDC de l'IHM en mémoire et connecter le desktop par l'interface de bouclage | Accepté | 2026-11-18 | — (complète ADR-0027, précise ADR-0022 point 6) |
| ADR-0034 | Vérifier chaque changement par GitHub Actions et suivre les dépendances avec Renovate | Proposé | 2026-11-24 | — |
| ADR-0035 | Comptes et isolation des bases de la plateforme | Proposé | 2026-11-24 | — (précise ADR-0016, ADR-0026, ADR-0027) |
| ADR-0036 | Pile d'observabilité du serveur | Accepté | 2026-11-25 | — |
| ADR-0037 | Porter les mécanismes techniques partagés par un noyau commun du serveur | Accepté | 2026-11-25 | — (précise ADR-0001, ADR-0016) |

## 9.2 Règles de gouvernance

- un ADR **Accepté** n’est jamais supprimé ni réécrit rétroactivement ;
- un changement crée un nouvel ADR et marque l’ancien **Remplacé** ;
- un ADR doit citer critères, options et méthode de validation ;
- tout choix coûteux à inverser doit passer par ADR ;
- les décisions encore hypothétiques restent `Proposé` ;
- le dossier Word/PDF est une vue de proposition et de décision ; `docs/architecture/` reste la source d’architecture versionnée ;
- tout changement architectural implémenté doit mettre à jour dans la même PR les ADR, arc42 et diagrammes concernés.

## 9.3 Décisions encore à formaliser après la baseline v2.4

1. mécanisme précis de scheduling/jobs : tranché par ADR-0031 (Accepté) ;
2. ajout ou non d’un broker après mesure ;
3. Flyway ou Liquibase : tranché par ADR-0032 (Accepté, Flyway) ;
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
- [DÉCISION] ADR-0023 à ADR-0033 sont acceptés par le porteur du projet le 2026-11-18 ; ADR-0029 remplace ADR-0004 ;
- [DÉCISION] ADR-0006 est remplacé par ADR-0028 pour la veille RSS/Atom et web, le reste de sa portée étant inchangé, et ADR-0018 est remplacé par ADR-0030 — porteur du projet, 2026-11-19 ;
- ADR-0017 documente la stratégie POC local puis VM ;
- [DÉCISION] ADR-0001, 0003, 0008, 0010, 0011, 0013, 0014, 0015, 0016, 0020, 0021 et 0022 sont acceptés par le porteur du projet le 2026-11-24, après relecture de leur preuve dans le code ; ADR-0007 et ADR-0012 restent à confirmer par une preuve du POC.

## 9.5 Risque

Le principal risque est de laisser diverger le code, le dossier de proposition et la documentation versionnée. Un contrôle de revue/CI est recommandé pour maintenir l’index, les statuts, les liens arc42 et les sources Mermaid cohérents.
