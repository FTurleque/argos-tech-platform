# 9. Décisions architecturales

Cette section indexe les ADR. Elle ne recopie pas leur contenu.

## 9.1 Index

| ID | Titre | Statut | Date | Remplace |
|---|---|---|---|---|
| ADR-0001 | Adopter un monolithe modulaire pour la première version | Proposé | 2026-09-24 | — |
| ADR-0002 | Utiliser PostgreSQL comme stockage principal | Proposé | 2026-09-24 | — |
| ADR-0003 | Introduire un modèle canonique d’événements | Proposé | 2026-09-24 | — |
| ADR-0004 | Encapsuler Claude derrière un AI Gateway | Proposé | 2026-09-24 | — |
| ADR-0005 | Utiliser Mermaid comme source des diagrammes d’architecture | Accepté | 2026-09-24 | — |
| ADR-0006 | Maintenir FreshRSS et changedetection.io comme systèmes séparés | Proposé | 2026-09-24 | — |
| ADR-0007 | Limiter n8n aux automatisations périphériques | Proposé | 2026-09-24 | — |
| ADR-0008 | Utiliser l’IdP d’entreprise via OIDC en première intention | Proposé | 2026-09-24 | — |
| ADR-0009 | Utiliser PolyForm Internal Use License 1.0.0 | Accepté | 2026-09-24 | — |

## 9.2 Règles de gouvernance

- un ADR **Accepté** n’est jamais supprimé ;
- un changement crée un nouvel ADR et marque l’ancien **Remplacé** ;
- un ADR doit citer critères, options et méthode de validation ;
- tout choix coûteux à inverser doit passer par ADR ;
- les décisions encore hypothétiques restent `Proposé`.

## 9.3 Décisions encore à formaliser

1. choix frontend : React, Vue ou autre ;
2. mécanisme de scheduling/jobs ;
3. ajout ou non d’un broker ;
4. stratégie RAG/embeddings ;
5. moteur de recherche au-delà de PostgreSQL FTS ;
6. hébergement production : VM/containers/Kubernetes/OpenShift ;
7. observabilité imposée ou stack dédiée ;
8. source(s) CVE ;
9. gestion des secrets.

## 9.4 Preuves

- les ADR listés sont versionnés dans `docs/architecture/adr/` ;
- seuls les choix explicitement validés par le cadrage peuvent être marqués Acceptés ;
- la licence acceptée est matérialisée par le fichier `LICENSE` à la racine et documentée dans le README.

## 9.5 Risque

Le principal risque est de laisser des choix structurants s’installer dans le code sans ADR. Un contrôle de revue/CI est recommandé pour maintenir l’index et les statuts cohérents.
