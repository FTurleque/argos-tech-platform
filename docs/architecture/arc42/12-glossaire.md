# 12. Glossaire

| Terme | Définition dans ARGOS |
|---|---|
| **ARGOS** | Technology & Project Intelligence Platform |
| **Technology Intelligence** | collecte, qualification et analyse des changements technologiques externes |
| **Project Intelligence** | consolidation factuelle de l’activité, des jalons, releases, pipelines et signaux projet |
| **IntelligenceItem** | élément de veille normalisé provenant d’une source externe |
| **ProjectActivity** | événement projet normalisé : issue, PR/MR, pipeline, release, déploiement, etc. |
| **ProjectImpact** | relation argumentée entre un `IntelligenceItem` et un projet potentiellement affecté |
| **Source** | configuration permettant de collecter une origine de veille |
| **Repository** | dépôt GitHub/GitLab associé à un projet |
| **Workspace** | frontière organisationnelle et d’autorisation regroupant projets et sources |
| **C4** | modèle de visualisation d’architecture par niveaux Context, Container, Component et Code |
| **arc42** | structure de documentation d’architecture en douze sections |
| **ADR** | Architecture Decision Record ; enregistrement versionné d’une décision structurante |
| **OpenAPI** | spécification de description d’API HTTP ; à ne pas confondre avec OpenAI |
| **OpenAI** | fournisseur de services IA ; non retenu comme fournisseur IA initial dans le cadrage actuel |
| **Claude** | famille de modèles d’Anthropic retenue comme fournisseur IA initial proposé |
| **LLM** | Large Language Model |
| **AI Gateway** | composant ARGOS encapsulant appels LLM, prompts, politiques, quotas, validation et audit |
| **Structured output** | réponse LLM contrainte/validée selon une structure attendue, idéalement un schéma |
| **RAG** | Retrieval-Augmented Generation ; enrichissement d’un appel LLM par des données retrouvées à la demande |
| **Embedding** | représentation vectorielle utilisée pour similarité/recherche sémantique |
| **pgvector** | extension PostgreSQL pour stocker et rechercher des vecteurs |
| **FTS** | Full-Text Search, recherche plein texte |
| **JSONB** | format JSON binaire/indexable de PostgreSQL |
| **RLS** | Row-Level Security ; contrôle d’accès au niveau des lignes PostgreSQL |
| **Webhook** | appel HTTP poussé par un système externe lors d’un événement |
| **Polling** | interrogation périodique d’une source pour détecter de nouvelles données |
| **Idempotence** | propriété garantissant qu’un même événement rejoué ne produit pas plusieurs effets métier |
| **DLQ** | Dead Letter Queue ; zone de mise à l’écart de messages/traitements en échec |
| **Outbox** | pattern garantissant la cohérence entre transaction métier et publication d’événements |
| **OIDC** | OpenID Connect, protocole d’identité au-dessus d’OAuth 2.0 |
| **SAML** | protocole d’échange d’assertions d’identité, courant en entreprise |
| **RBAC** | Role-Based Access Control |
| **SSO** | Single Sign-On |
| **IdP** | Identity Provider |
| **SLO** | Service Level Objective |
| **RPO** | Recovery Point Objective ; perte maximale de données acceptable |
| **RTO** | Recovery Time Objective ; délai maximal visé pour restaurer le service |
| **SBOM** | Software Bill of Materials ; inventaire des composants/dépendances d’un logiciel |
| **SAST** | Static Application Security Testing |
| **DAST** | Dynamic Application Security Testing |
| **CVE** | Common Vulnerabilities and Exposures |
| **OTLP** | OpenTelemetry Protocol |
| **POC** | Proof of Concept |
| **MVP** | Minimum Viable Product |

## Termes encore ambigus à préciser

- **projet** : projet métier, dépôt ou agrégat de plusieurs dépôts ?
- **progression** : aucun pourcentage global ne doit être utilisé sans définition objective ;
- **risque projet** : doit être défini par règle mesurable et provenance ;
- **impact** : distinction à préciser entre impact probable, confirmé et résolu ;
- **source critique** : seuil et règles à définir.

## Preuve et maintenance

Le glossaire doit être mis à jour dès qu’un terme métier est introduit dans le code, l’API, un ADR ou un diagramme.
