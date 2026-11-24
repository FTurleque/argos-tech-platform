# Proposition : durcir les conteneurs de service

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION]
     légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le changement `completer-configuration-conteneurs` durcit le seul conteneur `argos` (EXP-12) : système de fichiers racine
en lecture seule, `/tmp` en tmpfs, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]` [PROPOSITION — PR #44].
Les trois autres services de `compose.yaml` gardent la configuration par défaut de leur image : racine inscriptible, jeu de
capacités par défaut de Docker, élévation de privilèges possible [EXISTANT — `/compose.yaml`, services `postgres`,
`keycloak`, `ollama`]. Le constat AUD-SEC-25 (audit du 2026-11-21) vise tous les conteneurs. La question Q-7 de la fiche
`exploitation-plateforme` (« le durcissement s'étend-il à `keycloak`, `postgres` et `ollama` ? ») est tranchée par le
porteur le 2026-11-24 : « changement dédié » [DÉCISION — fiche `exploitation-plateforme`, Q-7, PR #44].

## What Changes — Changements

- Les services `postgres`, `keycloak` et `ollama` s'exécutent avec une racine en lecture seule, sans capacité système et sans
  élévation de privilèges ; seuls leurs volumes de données et des tmpfs déclarés restent inscriptibles (EXP-16).
- `postgres` s'exécute directement sous l'utilisateur `postgres` de l'image, sans capacité réajoutée.
- `keycloak` s'exécute depuis une image optimisée construite localement (`kc.sh build`) et démarre en `start --optimized` :
  l'image officielle reconstruit Quarkus au démarrage dans `/opt/keycloak/lib/quarkus`, ce qu'une racine en lecture seule
  interdit [EXISTANT — essai du 2026-11-24, design.md].
- `ollama` garde l'utilisateur `root` de l'image, sans capacité réajoutée.
- **BREAKING** (exploitation) : le profil `idp-local` construit une image Keycloak au premier `docker compose up --build` ;
  une mise à jour de `ARGOS_KEYCLOAK_VERSION` demande une reconstruction.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune : la capacité `exploitation-plateforme` (préfixe `EXP`) est créée par `fiabiliser-sauvegarde-et-roles-base`.

### Modified Capabilities — Capacités modifiées

- `exploitation-plateforme` : exigence ajoutée EXP-16. Delta ADDED, sans Purpose ; la spec principale n'existe qu'après
  l'archivage de `fiabiliser-sauvegarde-et-roles-base`. EXP-07 à EXP-12 sont prises par `completer-configuration-conteneurs`,
  EXP-13 à EXP-15 par `instrumenter-observabilite`.

## Périmètre

### Inclus

- `compose.yaml` : services `postgres`, `keycloak`, `ollama` ; fragment commun de durcissement partagé avec `argos`.
- Un `Dockerfile` de l'image Keycloak optimisée sous `deploiement/keycloak/`.
- La configuration Renovate qui suit la version de Keycloak.
- Le guide `/docs/deploiement/synology-et-pc.md`, la fiche `exploitation-plateforme`, arc42 §7 et §8, le registre des risques.

### Exclu

- Le conteneur `argos` : EXP-12, `completer-configuration-conteneurs`.
- L'exécution d'Ollama sous un utilisateur non `root` : le volume `argos-ollama` existant appartient à `root` et la reprise
  demanderait une migration de propriétaire ; `root` sans capacité, en lecture seule et sans élévation est retenu.
- Les conteneurs des tests (Dev Services Quarkus, pile e2e) : hors de `compose.yaml`.
- Les espaces de noms utilisateur du démon Docker (`userns-remap`) : réglage de l'hôte, hors du dépôt.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Exploitant (porteur sur le NAS) | reconstruit l'image Keycloak à chaque changement de version, lit l'état des conteneurs | [PROPOSITION] — EXP-16 |
| Porteur du projet | accepte l'image Keycloak construite localement (Q-1) | [QUESTION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Conteneur `argos` durci (lecture seule, tmpfs `/tmp`, `cap_drop`, `no-new-privileges`) | [PROPOSITION] | EXP-12, PR #44 |
| Extension aux trois autres services par un changement dédié | [DÉCISION] | Q-7, fiche `exploitation-plateforme` (PR #44), 2026-11-24 |
| Assemblage unique PC et Synology | [DÉCISION] | ADR-0026 (Accepté) |
| Keycloak auto-hébergé, profil `idp-local` | [DÉCISION] | ADR-0027 (Accepté) |
| Ollama local, profil `ia` | [DÉCISION] | ADR-0029 (Accepté) |
| Comptes de base et sauvegarde par `docker compose exec postgres` | [PROPOSITION] | ADR-0035 (Proposé), EXP-01 à EXP-06 |
| Chemins écrits et capacités nécessaires par image | [EXISTANT] | essai sur pile isolée du 2026-11-24, design.md § Contexte technique |

## Écarts constatés

- Le constat AUD-SEC-25 est cité par `completer-configuration-conteneurs` avec le chemin `/docs/audit/2026-11-21/findings.md`,
  absent du dépôt ; il est cité ici sans lien.
- La fiche `exploitation-plateforme` de `main` ne contient ni EXP-07 à EXP-15 ni Q-7 : ces ajouts arrivent avec les PR #44 et
  #47, non fusionnées. La mise à jour de la fiche pour EXP-16 est une tâche de ce changement, à faire après leur fusion.
- Q-8 de la fiche (image Keycloak optimisée pour accélérer le démarrage) est ouverte ; ce changement rend l'image optimisée
  nécessaire pour une autre raison (lecture seule). Q-1 ci-dessous la soumet au porteur.

## Impact

- **Composants** (noms de arc42 §5) : PostgreSQL ; Keycloak auto-hébergé (profil `idp-local`) ; modèle local Ollama
  (profil `ia`). Quarkus Backend inchangé.
- **Contrats d'interface** : aucun ; ports, réseau et variables d'environnement inchangés.
- **Données et persistance** : volumes `argos-pgdata` et `argos-ollama` repris tels quels ; Keycloak sans volume, ses
  données restent dans la base `keycloak`.
- **Sécurité, confidentialité, droits** : surface réduite pour les trois services (écriture du code et des binaires, capacités,
  élévation par binaire setuid).
- **Exploitation et observabilité** : étape de construction de l'image Keycloak ; démarrage de Keycloak plus court.
- **Documentation durable à mettre à jour** : `/docs/specifications/exploitation-plateforme.md`,
  `/docs/deploiement/synology-et-pc.md`, arc42 §7 (déploiement) et §8 (concepts transverses), D-11 et D-12 si leurs notes
  citent le durcissement, `/docs/architecture/risks/register.md`.
- **ADR** : ADR-0027 (Accepté) gagne une conséquence (image construite localement). Aucun nouvel ADR : le durcissement prolonge
  une décision déjà prise pour `argos`.

## Conception requise

Complète — changement de sécurité sur trois images, avec une nouvelle image construite et une reprise de volume existant à
garantir.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | L'image Keycloak construite localement (`deploiement/keycloak/Dockerfile`, `start --optimized`) est-elle acceptée ? Elle répond aussi à Q-8 de la fiche. Sinon, `keycloak` garde une racine inscriptible avec `cap_drop` et `no-new-privileges` (design.md D3). | oui : bloque la tâche 3.1 | porteur du projet |
| Q-2 | Les tmpfs reçoivent-ils une taille maximale (`size=`) ? Leur contenu compte dans `mem_limit` ; aucune valeur n'est fixée par une source. | non : peut attendre la mesure sur le NAS | exploitant |
