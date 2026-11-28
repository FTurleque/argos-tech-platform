# Architecture observée — mise à jour du 2026-11-21

Base : `origin/main` à b142695. Découpage tiré du code (imports et noms qualifiés de `src/main`), comparé aux bases du 17/11 (9d96406) et du 20/11 (54add78).

## Découpage réel

| Famille | Module | Fichiers | Lignes (17/11 → 21/11) | Rôle observé |
|---|---|---|---|---|
| Clients | argos-ui | 63 | ~1 600 → ~7 900 | IHM Vue 3 ; session OIDC Authorization Code + PKCE (ADR-0033), point d'appel unique `appelerApi` ; contrat écrit à la main (11 racines REST sur 23) |
| Clients | argos-desktop | 23 | ~860 → ~1 150 | Electron durci ; connexion OIDC par le navigateur du système et une écoute 127.0.0.1 |
| Exposition | api | 42 | 2 997 → 4 499 | 23 ressources REST, sécurité ; toujours aucun accès SQL |
| Exposition | mcp | 12 | 1 000 → 1 201 | 8 outils en lecture seule |
| Analyse | impact | 20 | 2 159 → 2 960 | Corrélation (désormais nocturne sur tous les projets), état de traitement des impacts, recherche plein texte |
| Analyse | alerting | 19 | 2 051 → 2 517 | Alertes, résumés, rappels radar, opportunités et réglementaires |
| Veille | signal | 22 | 2 550 → 2 790 | Signaux, veille web, opportunités, abonnements |
| Veille | regintel | 14 | 73 → 2 201 | Veille réglementaire (LEGI, JORF), fiches, demandes d'évolution vers les forges |
| Veille | vulnintel | 10 | 528 → 563 | Enrichissement KEV, EPSS, NVD, CERT-FR |
| Veille | releaseradar | 5 | 530 | endoflife.date |
| Référentiels | catalog | 20 | 1 292 → 1 733 | Standards, profils, mises en page, dictionnaire métier |
| Référentiels | inventory | 11 | 821 → 1 865 | Projets, composants, dépôts, import planifié — sans point d'entrée de création (AUD-QUA-01) |
| Référentiels | projectintel | 10 | 863 → 875 | Activité des dépôts |
| Collecte | ingestion | 28 | 2 020 → 2 896 | OSV (synchronisation horaire planifiée), RSS/pages web, data.culture, LEGI |
| Collecte | scm | 13 | 754 → 1 374 | GitHub/GitLab, factorisé, contrats enregistrés |
| Transverse | workspace | 26 | 1 693 → 1 761 | Comptes, espaces, rôles — et noyau implicite RLS/erreurs (AUD-ARC-05) |
| Transverse | aigateway | 13 | 1 057 → 1 163 | Passerelle Ollama |
| Transverse | audit | 4 | 186 | Journaux |

Serveur : 269 fichiers, ~29 100 lignes ; 21 migrations Flyway ; 13 tâches planifiées. Déploiement : `compose.yaml` avec PostgreSQL, Argos (IHM servie par Quarkus), Ollama (profil `ia`) et Keycloak (profil `idp-local`, base `keycloak` dans le même PostgreSQL).

## Graphe de dépendances

`A --> B` signifie « A importe B ». Le nombre sur une flèche entre familles compte les arêtes module → module qu'elle résume.

```mermaid
flowchart TB
  subgraph Clients
    ui[argos-ui]
    desk[argos-desktop]
  end
  subgraph Exposition
    api[api · 23 ressources]
    mcp[mcp · 8 outils]
  end
  subgraph Analyse
    impact
    alerting
  end
  subgraph Veille
    signal
    regintel
    vulnintel
    releaseradar
  end
  subgraph Referentiels[Référentiels]
    catalog
    inventory
    projectintel
  end
  subgraph Collecte
    ingestion
    scm
  end
  subgraph Transverse
    workspace[workspace · noyau RLS implicite]
    aigateway
    audit
  end
  ui -. "IHM servie sans jeton ; émetteur figé, redirections 8088 (DEP-21, DEP-25)" .-> api
  desk -- "jeton OIDC" --> api
  mcp --> api
  Exposition -- 17 --> Analyse & Veille & Referentiels
  Exposition -- 4 --> Transverse
  alerting --> impact
  Analyse -- 3 --> Veille
  Analyse -- 2 --> Referentiels
  Analyse -- 4 --> Transverse
  Analyse -. "lectures SQL directes (ARC-06)" .-> Veille
  vulnintel --> signal
  Veille -- 3 --> Referentiels
  Veille -- 3 --> Collecte
  Veille -- 4 --> Transverse
  inventory --> catalog
  projectintel --> inventory
  Referentiels -- 2 --> Collecte
  Referentiels -- 3 --> Transverse
  aigateway --> audit
  classDef ecart stroke:#c42b2b,stroke-width:2px;
  class workspace ecart;
```

## Ce qui a changé depuis le 20/11

- **Graphe inchangé** : 50 arêtes réelles (plus une référence de javadoc impact → workspace), aucun cycle, toutes autorisées par `ModuleBoundariesTest` (AUD-ARC-01) ; seuls trois comptes de références bougent (alerting → regintel 2 → 4, alerting → workspace 9 → 7, signal → ingestion 28 → 29).
- **IHM servie** (PR #28) : `/`, `/index.html`, `/assets/*` et `/licences-polices.txt` sont publics, l'API reste derrière `/*` authentifié ; le routeur en mode hash ne demande jamais de vue au serveur (AUD-ARC-02 fermé avec réserve). Les liens des alertes visent pourtant des chemins serveur que l'IHM ne route pas (AUD-ARC-22).
- **Keycloak** entre dans l'assemblage, mais partage le compte superutilisateur PostgreSQL d'Argos (AUD-SEC-30), n'est pas sauvegardé (AUD-DEP-24) et n'accepte que des redirections vers `localhost:8088` (AUD-DEP-25).
- Le couplage par le schéma reste à 13 tables lues par plusieurs modules, dont 6 lectures entre modules sans dépendance autorisée (AUD-ARC-06).

## Écarts avec la documentation

- arc42 §5.1 a été corrigé (Ollama, Keycloak) ; §5.2 mélange toujours flux et dépendances, et §3 et §7 citent encore Claude API et l'IdP d'entreprise (AUD-DOC-02, qui absorbe AUD-ARC-03).
- 14 ADR sur 33 restent « Proposé » alors que le code les applique, dont 0001, 0013, 0014, 0016 et 0021 (AUD-ARC-14).

## Ce que le graphe ne montre pas

Un graphe de modules ne voit pas les défauts internes, qui restent le vrai sujet : l'enrichissement et la corrélation exécutés dans le fil d'ingestion (AUD-PER-02), les mécanismes recopiés (4 contextes RLS, 6 transactions manuelles, 273 `prepareStatement`, 7 clients HTTP) et le fait que la connexion au portail de l'image reste limitée au poste hôte (AUD-DEP-21, AUD-DEP-25).
