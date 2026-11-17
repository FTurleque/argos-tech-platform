# D-11 — Déploiement cible sur le serveur Synology

Vue de déploiement de la cible retenue par [ADR-0026](../adr/0026-hebergement-synology.md) (Accepté, cible), au niveau des conteneurs réellement décrits par [`compose.yaml`](/compose.yaml) et des réglages de [`application.properties`](/argos-server/src/main/resources/application.properties). Elle détaille D-10 (arc42 [§7.3](../arc42/07-vue-deploiement.md#73-serveur-synology-adr-0026)) ; l'hébergement en ligne d'arc42 §7.4 n'a pas de diagramme tant qu'aucune décision ne le décrit.

```mermaid
flowchart TB
    subgraph POSTES["«node» Postes des utilisateurs"]
        NAV["«Person» Navigateur\nIHM Vue.js 3"]
        DESK["«Application» Client desktop Electron\nargos-desktop (ADR-0022, proposé)"]
        ASSIST["«Software System» Assistant IA\nclient MCP (.mcp.json)"]
    end

    subgraph NAS["«node» Synology DS1522+ — DSM"]
        VPN["«Component» VPN Server DSM\naccès distant par défaut"]
        RP["«Component» Reverse proxy DSM\nHTTPS, certificat Let's Encrypt"]
        SMTP["«Component» Relais SMTP DSM"]
        subgraph CM["«node» Container Manager — projet compose « argos »"]
            ARGOS["«Container» argos\nimage unique : IHM Vue.js 3 + API Quarkus + MCP\nJRE 17, utilisateur 1001 · port 8080 lié à 127.0.0.1 par défaut\nmem_limit 1g"]
            PG["«database» postgres:18\nport 5432 lié à 127.0.0.1\nmem_limit 512m"]
            OLL["«Container» ollama/ollama\nprofil « ia » seulement, aucun port publié\nmem_limit 3g"]
        end
        VOLPG[("«volume» argos-pgdata")]
        VOLOL[("«volume» argos-ollama")]
        SAUV[("«dossier» sauvegardes/\nexports compressés, 14 conservés")]
    end

    KC["«Software System» Keycloak, royaume argos\nauto-hébergé (ADR-0027, proposé)\nhors compose.yaml : ARGOS_OIDC_URL"]
    HB["«Software System» Copie hors site\nHyper Backup"]
    EXT["«Software System» Sources publiques\nOSV, NVD, KEV, EPSS, CERT-FR, endoflife.date,\nDILA (JORF), flux et pages surveillés"]
    FORGES["«Software System» GitLab / GitHub\nAPI REST en lecture seule"]

    NAV -->|HTTPS| VPN
    DESK -->|HTTPS, REST| VPN
    ASSIST -->|HTTPS, POST /mcp, jeton Bearer| VPN
    VPN --> RP
    RP -->|HTTP local, localhost:8080| ARGOS
    ARGOS -->|JDBC, réseau du projet| PG
    ARGOS -->|HTTP, réseau du projet, ollama:11434| OLL
    ARGOS -->|SMTP, STARTTLS| SMTP
    ARGOS -->|HTTPS, découverte OIDC et clés JWKS| KC
    NAV -->|HTTPS, connexion OIDC| KC
    ARGOS -->|HTTPS GET| EXT
    ARGOS -->|HTTPS GET, jeton de lecture| FORGES
    PG --- VOLPG
    OLL --- VOLOL
    PG -->|scripts/sauvegarder-base.sh, pg_dump| SAUV
    SAUV -->|copie planifiée DSM| HB
```

## Lecture

| Élément | Fait observé | Source |
|---|---|---|
| Image | image unique x86-64 (DS1522+ et PC) : IHM construite puis servie par Quarkus, JRE 17, utilisateur non privilégié d'UID 1001 | [`Dockerfile`](/Dockerfile) |
| Conteneurs | `argos`, `postgres` (image `postgres:18`), `ollama` (profil `ia`, désactivé par défaut) dans le projet compose `argos` | `compose.yaml` |
| Exposition | ports d'Argos et de PostgreSQL liés à `127.0.0.1` ; `ARGOS_BIND=0.0.0.0` ouvre Argos au réseau local ou au VPN ; Ollama ne publie aucun port | `compose.yaml` |
| Accès distant | VPN Server de DSM par défaut ; nom de domaine en HTTPS par le reverse proxy de DSM | [`synology-et-pc.md`](/docs/deploiement/synology-et-pc.md) |
| Identité | Keycloak lu par `ARGOS_OIDC_URL` ; son déploiement sur le NAS n'est pas décrit dans `compose.yaml` | ADR-0027 (Proposé), `compose.yaml` |
| Messagerie | relais SMTP du Synology, `ARGOS_SMTP_HOST`, STARTTLS | fiche [`alertes-digests`](../../specifications/alertes-digests.md) |
| Migrations | Flyway appliqué au démarrage d'Argos (`quarkus.flyway.migrate-at-start=true`) | `application.properties`, [ADR-0032](../adr/0032-migrations-flyway.md) (Proposé) |
| Tâches planifiées | exécutées dans le conteneur `argos` par `quarkus-scheduler`, sans ordonnanceur externe | [ADR-0031](../adr/0031-planification-quarkus-scheduler.md) (Proposé) |
| Sauvegarde | export par `scripts/sauvegarder-base.sh`, copie hors du NAS par Hyper Backup | `synology-et-pc.md` |

Une seule instance du conteneur `argos` est prévue : les tâches planifiées et la réservation des traitements reposent sur cette hypothèse ou sur des verrous PostgreSQL (ADR-0031).
