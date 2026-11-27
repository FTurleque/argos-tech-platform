# 3. Contexte et périmètre

## 3.1 Frontière du système

ARGOS est un **Software System** autonome qui consomme des sources externes, conserve un modèle interne normalisé et restitue de l’information aux utilisateurs, par le portail web, le client desktop, les e-mails et un serveur MCP.

### Dans la frontière ARGOS

- collecte et orchestration d’ingestion, collecteur RSS/Atom et pages surveillées compris ([ADR-0028](../adr/0028-collecteur-veille-web-integre.md)) ;
- normalisation et déduplication ;
- modèle métier ;
- corrélation technologie ↔ projet ;
- recherche ;
- calcul de métriques factuelles ;
- passerelle IA ;
- portail web et client desktop ;
- alertes et résumés ;
- serveur MCP en lecture seule ;
- contrôle d’accès et audit applicatif.

### Hors frontière ARGOS

- Keycloak, fournisseur d’identité auto-hébergé ([ADR-0027](../adr/0027-keycloak-idp-auto-heberge.md)), déployé à côté d’Argos par le profil `idp-local` ou existant ;
- Ollama, modèle d’IA local facultatif ([ADR-0029](../adr/0029-passerelle-ia-ollama-local.md)) ;
- GitHub et GitLab ;
- sources d’avis de sécurité, calendrier des fins de support, archives JORF ;
- sites qui publient les flux et les pages surveillés ;
- relais SMTP ;
- assistants IA des développeurs, clients du serveur MCP.

## 3.2 Acteurs

| Acteur | Interaction principale | État |
|---|---|---|
| Utilisateur | consulte veille, impacts, projets et recherche ; gère ses abonnements et sa veille personnelle | [EXISTANT] |
| Administrateur d’espace | gère membres, rôles, projets et sources de son espace | [EXISTANT] |
| Expert métier | qualifie et valide les fiches réglementaires de ses domaines | [EXISTANT] |
| Administrateur plateforme | déclare les sources partagées, lit les métriques, lance les synchronisations | [EXISTANT] |
| Exploitant (le porteur) | déploie, sauvegarde, restaure et diagnostique la plateforme | [EXISTANT] |

## 3.3 Systèmes externes

| Système | Rôle | Interface | État |
|---|---|---|---|
| Keycloak | authentification des utilisateurs, émission des jetons | OIDC, code d’autorisation + PKCE S256 ; flux d’appareil pour les clients MCP | [EXISTANT] (ADR-0027) |
| Ollama | résumés et explications | API HTTP sur le réseau du projet | [EXISTANT], facultatif (ADR-0029) |
| GitHub, GitLab | SBOM, manifestes et activité des dépôts | API REST en lecture seule, jetons facultatifs ; aucun webhook | [EXISTANT] |
| OSV, NVD, CISA KEV, FIRST EPSS, CERT-FR | avis de sécurité et enrichissement | API HTTP et archives | [EXISTANT] (ADR-0010) |
| endoflife.date | calendrier des fins de support | API HTTP | [EXISTANT] |
| DILA (archives JORF) | textes officiels | archives HTTPS, Licence Ouverte | [EXISTANT] (ADR-0030) |
| Sites publiés (RSS/Atom, pages) | veille web | HTTP(S), adresses contrôlées à chaque collecte | [EXISTANT] (ADR-0028) |
| Relais SMTP | e-mails d’alertes et de résumés | SMTP avec STARTTLS | [EXISTANT] |
| Assistant IA d’un développeur | interrogation en lecture seule | MCP Streamable HTTP sur `POST /mcp` | [EXISTANT] (ADR-0014) |

## 3.4 C4 — System Context

**Type : C4 Context. Portée : ARGOS et son environnement direct.**

```mermaid
flowchart LR
    USER["«Person»\nUtilisateur ARGOS\nConsulte veille, projets et impacts"]
    ADMIN["«Person»\nAdministrateur\nConfigure espaces, sources et accès"]
    OPS["«Person»\nExploitant\nDéploie et sauvegarde la plateforme"]
    DEV["«Person»\nDéveloppeur\nInterroge Argos depuis son IDE"]

    ARGOS["«Software System»\nARGOS\nTechnology & Project Intelligence Platform"]

    IDP["«Software System»\nKeycloak\nAuthentifie les utilisateurs"]
    LLM["«Software System»\nOllama\nModèle d'IA local, facultatif"]
    FORGE["«Software System»\nGitHub / GitLab\nDépôts, SBOM et activité"]
    AVIS["«Software System»\nSources d'avis de sécurité\nOSV, NVD, KEV, EPSS, CERT-FR"]
    EOL["«Software System»\nendoflife.date\nFins de support"]
    JORF["«Software System»\nDILA\nArchives JORF"]
    WEB["«Software System»\nSites publiés\nFlux RSS/Atom et pages"]
    SMTP["«Software System»\nRelais SMTP\nDiffuse les e-mails"]
    IA["«Software System»\nAssistant IA\nClient MCP"]

    USER -->|consulte via HTTPS, portail ou desktop| ARGOS
    ADMIN -->|configure via HTTPS| ARGOS
    OPS -->|déploie et observe| ARGOS
    DEV -->|utilise| IA
    IA -->|appelle les outils en lecture seule via MCP| ARGOS

    USER -->|se connecte via OIDC| IDP
    ARGOS -->|valide les jetons via OIDC| IDP
    ARGOS -->|soumet un contexte filtré via HTTP| LLM
    ARGOS -->|lit SBOM et activité via API REST| FORGE
    ARGOS -->|collecte les avis via HTTPS| AVIS
    ARGOS -->|lit le calendrier via HTTPS| EOL
    ARGOS -->|télécharge les archives via HTTPS| JORF
    ARGOS -->|récupère flux et pages via HTTP(S)| WEB
    ARGOS -->|envoie alertes et résumés via SMTP| SMTP
```

## 3.5 Interfaces

### Interfaces entrantes

- portail web et client desktop (IHM servie par le serveur, API REST `/api/v1`) ;
- serveur MCP `POST /mcp` ;
- santé `/q/health` et métriques `/q/metrics`.

### Interfaces sortantes

- Keycloak (validation des jetons) ;
- Ollama (passerelle IA) ;
- API GitHub et GitLab ;
- sources d’avis, endoflife.date, archives JORF, sites publiés ;
- relais SMTP.

## 3.6 Données échangées

| Flux | Données principales | Sensibilité |
|---|---|---|
| Sources publiques → ARGOS | avis, calendriers, textes officiels, titres, liens, contenu | Public |
| GitHub/GitLab → ARGOS | SBOM, manifestes, compteurs d’activité sans identité | Internal |
| ARGOS → Ollama | entrées classées PUBLIC ou INTERNAL, secrets refusés, données personnelles masquées | Public à Internal |
| ARGOS → utilisateurs | veille, impacts, métriques, synthèses, filtrés par espace (RLS) | Internal |
| ARGOS → relais SMTP | alertes et résumés, liens vers le portail | Internal |

## 3.7 Questions ouvertes

- Quels canaux de notification au-delà de l’e-mail et du client desktop ?
- Quelles sources d’avis supplémentaires pour un hébergement en ligne ?

## 3.8 Risques associés

- dépendance aux limites d’API des forges et des sources ;
- contenu externe hostile (injection d’instructions, falsification de requête côté serveur) ;
- indisponibilité de Keycloak, qui bloque toute connexion ;
- couplage excessif à un fournisseur si le modèle canonique est insuffisant.

Registre : [`../risks/register.md`](../risks/register.md).

## 3.9 Fichiers source concernés

- `argos-server/src/main/resources/application.properties` (adresses des sources) ;
- `compose.yaml` (Keycloak, Ollama) ;
- `argos-ui/src/api/openapi.json` (contrat REST).
