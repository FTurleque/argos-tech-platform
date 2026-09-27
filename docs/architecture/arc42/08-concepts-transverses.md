# 8. Concepts transverses

## 8.1 Identité et accès

**Hypothèse à valider :** authentification fédérée via OIDC avec l’IdP d’entreprise.

Rôles proposés : `USER`, `PROJECT_MANAGER`, `WORKSPACE_ADMIN`, `PLATFORM_ADMIN`, `OPS`.

L’autorisation est évaluée au niveau workspace/projet et reste indépendante des préférences de veille.

## 8.2 Personnalisation équipe + utilisateur

ARGOS distingue deux niveaux d’abonnement :

- `TEAM` : socle de veille défini pour une équipe, lié à ses projets, technologies, responsabilités et obligations ;
- `PERSONAL` : veille facultative ajoutée par un utilisateur sur des technologies, thèmes, éditeurs ou sources de son choix.

Exemple : un développeur de l’équipe RH reçoit le socle RH mais peut suivre Rust, Kubernetes ou une technologie IA sans lien direct avec ses projets courants afin d’apprendre ou de préparer un futur besoin.

**Règle de sécurité :** suivre un sujet n’accorde aucun droit supplémentaire. Les projets et données internes restent filtrés par RBAC/RLS.

Une `Subscription` doit donc porter au minimum :

- `scope` : `TEAM` ou `PERSONAL` ;
- propriétaire équipe/utilisateur ;
- filtres ;
- canal/fréquence ;
- caractère obligatoire ou facultatif ;
- date de création et état.

## 8.3 Sécurité

Principes : deny-by-default, validation des entrées externes, secrets hors dépôt, chiffrement en transit, contrôle SSRF, rate limiting, séparation par workspace et contenu Internet traité comme donnée non fiable.

Classification minimale : `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`. Les deux dernières classes ne sortent pas vers un LLM externe par défaut.

## 8.4 Interfaces et versionnement

- API REST/OpenAPI Quarkus ;
- Vue.js 3 consomme les contrats HTTP ;
- adapters externes indépendants du domaine ;
- contract tests sur GitHub/GitLab/FreshRSS/changedetection/MCP ;
- stratégie de dépréciation documentée.

## 8.5 Résilience

- timeouts explicites ;
- retry borné avec backoff/jitter ;
- idempotence ;
- curseurs de synchronisation persistés ;
- table d’échec / DLQ logique au MVP ;
- replay contrôlé ;
- health/readiness distincts.

## 8.6 Observabilité

OpenTelemetry/Micrometer côté Quarkus, logs structurés, métriques et traces lorsque pertinent.

Métriques clés : fraîcheur des sources, impacts détectés, couverture SBOM, alertes, latence API/MCP, tokens/coût IA, erreurs de structured output, refus pour politique de confidentialité.

## 8.7 Persistance

PostgreSQL est **Accepté** comme stockage principal : relationnel, JSONB, FTS et RLS. `pgvector` n’est activé que si un besoin est démontré.

## 8.8 IA et coûts

Tous les appels runtime passent par l’AI Gateway : quotas, prompts versionnés, schémas de sortie, audit et plafond.

Le budget d’abonnement utilisé par le développeur pour Claude/Claude Code est distinct du budget Claude API/Console consommé par ARGOS.

## 8.9 Messaging

Aucun broker n’est imposé au MVP. Une file/broker ne sera introduite qu’après mesure d’un besoin réel de backlog, multi-consommateurs ou scalabilité indépendante.

## 8.10 Performance

Cibles initiales : API/MCP p95 < 500 ms hors appels externes/IA, pagination des collections, ingestion découplée de l’affichage et aucun appel LLM bloquant pour une page standard.

## 8.11 Tests

- unitaires domaine ;
- tests d’architecture Quarkus/ArchUnit ;
- intégration PostgreSQL ;
- contract tests adapters et MCP ;
- E2E critiques ;
- sécurité ;
- résilience ;
- tests IA avec jeu de référence.

## 8.12 Déploiement

POC local Docker Compose, puis VM Linux interne pour le POC partagé/MVP. Voir ADR-0017 et section 7.

## 8.13 Questions ouvertes

- IdP et coffre-fort imposés ?
- stack d’observabilité imposée ?
- durées de conservation ?
- mécanisme Teams/e-mail ?
- Flyway ou Liquibase ?
