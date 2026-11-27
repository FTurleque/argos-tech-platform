# 2. Contraintes

Cette section distingue les **contraintes imposées** des **décisions d’architecture** prises par ADR et des **préférences** qui n’en ont pas encore.

## 2.1 Contraintes observées

| Domaine | Contrainte | Nature | Preuve |
|---|---|---|---|
| Documentation | arc42 obligatoire | Imposée par le cadrage | demande d’architecture du projet |
| Modélisation | C4 Context, Container, Component et Code si pertinent | Imposée par le cadrage | demande d’architecture du projet |
| Diagrammes | Mermaid uniquement ; aucun diagramme ASCII ni image binaire comme source | Imposée par le cadrage | ADR-0005 |
| Décisions | ADR Markdown pour les choix structurants | Imposée par le cadrage | `docs/architecture/adr/` |
| Langue | documentation d’architecture en français | Imposée par le cadrage | demande d’architecture du projet |
| Exactitude | toute information non prouvée doit être marquée `Hypothèse à valider` ou `Non déterminé` | Imposée par le cadrage | demande d’architecture du projet |
| Licence | PolyForm Internal Use 1.0.0, double licence possible ; dépendances sous licence permissive | Décision acceptée | ADR-0009, ADR-0021 |
| Coût | composants gratuits et auto-hébergés ; aucun service payant requis | Décision acceptée | ADR-0025, ADR-0029 |
| JDK | Java 17 minimum | Décision acceptée | ADR-0019 |
| Backend/API | Quarkus | Décision acceptée | ADR-0019 |
| IHM | Vue.js 3 ; client desktop Electron | Décision acceptée | ADR-0019, ADR-0022 |
| Stockage | PostgreSQL | Décision acceptée | ADR-0002 + ADR-0019 |

## 2.2 Décisions techniques

| Sujet | État | Preuve |
|---|---|---|
| Monolithe modulaire Quarkus, noyau `commun` | Accepté | ADR-0001, ADR-0037, `ModuleBoundariesTest` |
| Migrations SQL Flyway appliquées au démarrage | Accepté | ADR-0032, `argos-server/src/main/resources/db/migration/` |
| Fournisseur d’identité Keycloak auto-hébergé | Accepté | ADR-0027, profil `idp-local` de `compose.yaml` |
| Collecteur RSS/Atom et pages surveillées intégré au serveur | Accepté | ADR-0028, `io.argos.ingestion.web` |
| Modèle d’IA local Ollama, facultatif | Accepté | ADR-0029, profil `ia` de `compose.yaml` |
| Traitements périodiques par `quarkus-scheduler` | Accepté | ADR-0031 |
| Observabilité sans collecteur (journaux JSON, métriques Prometheus, santé) | Accepté | ADR-0036 |
| Docker Compose sur le PC et sur le Synology du porteur | Accepté | ADR-0026, `compose.yaml` |
| Comptes et isolation des bases | Proposé | ADR-0035 |
| Intégration continue GitHub Actions, Renovate | Proposé | ADR-0034, `.github/workflows/integration-continue.yml` |
| n8n périphérique | Proposé, non utilisé | ADR-0007 |
| Hébergement en ligne | Non déterminé | ADR à venir |

## 2.3 Contraintes réglementaires et sécurité

### Données personnelles

L’activité GitHub/GitLab peut contenir des données liées à des personnes : auteur d’un commit, assignation, commentaires, review, horodatages. `project-intel` agrège l’activité par jour sans conserver ni restituer d’identité de personne (capacité `activite-projets`).

**Hypothèse à valider :** le traitement des comptes et des journaux doit être qualifié au regard du RGPD pour une instance ouverte à d’autres utilisateurs que le porteur.

**Preuves nécessaires :** registre de traitement, finalités, catégories de données, durées de conservation (ADR-0041, Proposé), politique de confidentialité.

### IA

Le modèle d’IA est local (ADR-0029) : aucun appel ne sort de l’instance par défaut. Seules les entrées classées PUBLIC ou INTERNAL sont transmises au modèle : la passerelle refuse toute entrée CONFIDENTIAL ou RESTRICTED et toute entrée contenant un secret, et masque les données personnelles (IAG-02, capacité `passerelle-ia`).

## 2.4 Contraintes organisationnelles

| Sujet | État |
|---|---|
| porteur et exploitant | une seule personne, le porteur du projet (ADR-0025) |
| hébergement | Synology DS1522+ du porteur, 8 Go de mémoire (ADR-0026) |
| fournisseur d’identité | Keycloak auto-hébergé (ADR-0027) |
| secrets | fichier `.env` hors dépôt ; aucun coffre-fort |
| sauvegarde | export quotidien vérifié des bases `argos` et `keycloak`, copie hors site par Hyper Backup (EXP-01 à EXP-04) |
| relais de messagerie | relais SMTP de DSM |

## 2.5 Préférences d’architecture

Ces éléments guident la conception sans être des contraintes imposées :

1. PostgreSQL avant multiplication des moteurs de stockage ;
2. API REST/OpenAPI ;
3. connecteurs/adapters aux frontières ;
4. modèle canonique d’événements ;
5. traitement de fond hors requête lorsque l’ingestion l’exige (ADR-0039, Proposé) ;
6. gratuit/self-hosted d’abord ;
7. passerelle IA interne devant le modèle, contrat indépendant du fournisseur ;
8. socle de veille d’espace complété par des abonnements personnels sans élargissement des droits.

## 2.6 Risques de contrainte

- la mémoire du NAS borne la taille du modèle d’IA et le nombre de services ;
- GitHub/GitLab imposent des limites d’API ;
- la licence d’un composant peut empêcher l’usage envisagé ;
- les objectifs RPO/RTO peuvent faire évoluer l’architecture de déploiement ;
- un hébergement en ligne demandera une revue de l’exposition, des secrets et des sauvegardes.

## 2.7 Fichiers source concernés

- `README.md`
- `compose.yaml`, `Dockerfile`, `.env.example`
- `adr/0019-stack-java17-quarkus-vue3-postgresql.md`, `adr/0025-produit-ouvert-projet-personnel.md`, `adr/0026-hebergement-synology.md`
