# 1. Introduction et objectifs

## 1.1 Résumé du système

ARGOS est une plateforme de **Technology Intelligence** et **Project Intelligence**, ouverte à tous les publics et portée à titre personnel par son porteur ([ADR-0025](../adr/0025-produit-ouvert-projet-personnel.md)). Elle réunit dans un même système :

- la veille issue de flux RSS/Atom et de pages surveillées, collectés par le serveur lui-même ([ADR-0028](../adr/0028-collecteur-veille-web-integre.md)) ;
- la collecte des avis de sécurité, des fins de support et des releases ;
- l’inventaire des projets par SBOM CycloneDX et le suivi factuel de l’activité des dépôts GitHub/GitLab, en lecture seule ;
- la corrélation déterministe entre événements externes et technologies/projets ;
- la veille réglementaire (JORF), publiée après validation par un expert métier ([ADR-0030](../adr/0030-veille-reglementaire-expert-metier.md)) ;
- des résumés et explications produits par un modèle d’IA local, facultatif, derrière une passerelle ([ADR-0029](../adr/0029-passerelle-ia-ollama-local.md)) ;
- la restitution par le portail web, le client desktop, la recherche, les alertes et résumés, et un serveur MCP en lecture seule.

**État : [EXISTANT].** Ces capacités sont implémentées et spécifiées par 19 spécifications principales OpenSpec ; l’état de chacune est tenu dans le [registre des spécifications](../../specifications/README.md).

## 1.2 Objectifs métier

1. **Réduire le coût de veille manuelle** en centralisant les sources pertinentes.
2. **Accélérer la détection d’impacts** : relier une CVE, une release ou un changement de documentation aux projets concernés.
3. **Donner une vue factuelle de l’activité projet** sans fabriquer de pourcentage d’avancement arbitraire.
4. **Capitaliser l’information** : conserver les signaux, analyses, décisions et historique de projet.
5. **Aider à la décision** grâce à des synthèses traçables fondées sur les données sources.

## 1.3 Parties prenantes

| Partie prenante | Besoin principal | Statut |
|---|---|---|
| Porteur du projet | décide de l’architecture et du périmètre, héberge et exploite l’instance sur son Synology | [EXISTANT] (ADR-0025, ADR-0026) |
| Utilisateur | voir les changements qui touchent ses technologies et projets, compléter la veille de son espace par une veille personnelle | [EXISTANT] (capacités `abonnements-veille`, `profils-veille`) |
| Administrateur d’espace | gérer les membres, les rôles, les projets et les sources de son espace | [EXISTANT] (capacité `comptes-acces`) |
| Expert métier | qualifier et valider les fiches réglementaires de ses domaines | [EXISTANT] (ADR-0030) |
| Administrateur plateforme | administrer l’instance, lire les métriques, déclencher les synchronisations | [EXISTANT] (`ARGOS_ADMIN_EMAIL`) |
| Développeur utilisant un assistant IA | interroger Argos depuis son IDE par MCP | [EXISTANT] (capacité `serveur-mcp`) |

## 1.4 Objectifs qualité prioritaires

| Priorité | Objectif | Cible initiale |
|---|---|---|
| 1 | **Traçabilité** | toute synthèse ou alerte doit pouvoir remonter aux données sources |
| 2 | **Sécurité / confidentialité** | cloisonnement des espaces (RBAC + RLS) ; seules les données publiques et internes sortent vers le modèle d’IA |
| 3 | **Maintenabilité** | ajout d’une source externe sans modifier le cœur métier |
| 4 | **Fiabilité** | ingestion idempotente, rejouable et tolérante aux erreurs externes |
| 5 | **Maîtrise des coûts** | composants gratuits et auto-hébergés ; appels IA plafonnés par jour, par espace et par compte |

Les seuils mesurables sont détaillés dans `10-exigences-qualite.md` et `../quality/scenarios.md`.

## 1.5 Périmètre

### Dans le périmètre

- RSS/Atom et pages surveillées ;
- avis de sécurité (OSV, NVD, CISA KEV, FIRST EPSS, CERT-FR) et fins de support (endoflife.date) ;
- inventaire SBOM et activité des dépôts GitHub et GitLab, en lecture seule ;
- veille réglementaire JORF ;
- corrélation, alertes, résumés, recherche et radar des standards ;
- espaces, rôles et veille personnelle ;
- résumés par un modèle d’IA local, facultatifs ;
- serveur MCP en lecture seule.

### Hors périmètre

- remplacement de GitHub/GitLab ;
- gestion complète de projet type Jira ;
- orchestration CI/CD ;
- microservices distribués par défaut ;
- IA autonome capable d’exécuter des actions sur les dépôts.

## 1.6 Preuves, hypothèses et questions ouvertes

### Preuves observées

- code de `argos-server/`, `argos-ui/`, `argos-desktop/` et `compose.yaml` ;
- spécifications principales `openspec/specs/` et fiches `docs/specifications/` ;
- ADR acceptés de `docs/architecture/adr/`.

### Hypothèses à valider

- volumétrie réelle (sources, projets, utilisateurs) d’une instance partagée ;
- niveau de disponibilité attendu ;
- modalités d’un hébergement en ligne, après le Synology.

### Risques associés

- dérive de périmètre vers un outil de gestion de projet généraliste ;
- dépendance excessive à l’IA ;
- métriques de suivi projet mal interprétées.

Registre : [`../risks/register.md`](../risks/register.md).

### Fichiers source concernés

- `README.md`
- `docs/architecture/README.md`
- `docs/specifications/README.md`
