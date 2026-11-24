# Tâches : durcir les conteneurs de service

<!-- Chaque tâche cite les scénarios couverts et sa vérification. Les essais se font sur une pile isolée
(`docker compose -p <projet-d-essai>`), jamais avec l'étiquette `argos:local` ni les conteneurs `argos-*` du porteur. -->

## 1. Préparation

- [x] 1.1 Faire trancher Q-1 (image Keycloak construite localement) et noter la réponse dans proposal.md — couvre EXP-16 — vérification : réponse datée dans le tableau des questions (acceptée le 2026-11-24, design.md D3 en [DÉCISION])
- [x] 1.2 Partir de la branche de la PR #44 (`completer-configuration-conteneurs`), empilement choisi par le porteur le 2026-11-24 tant qu'elle n'est pas fusionnée ; rebaser sur `main` après sa fusion — couvre EXP-16 — vérification : `compose.yaml` contient le durcissement d'`argos`, `x-journaux` et le point d'entrée de garde de `keycloak`

## 2. Base de données et modèle local

- [x] 2.1 Ajouter le fragment `x-durcissement` à `compose.yaml`, le fusionner dans `argos` à la place de ses trois clés (design.md D1) — couvre EXP-16-S1 — vérification : `docker compose config` identique pour `argos` avant et après
- [x] 2.2 Fusionner `x-durcissement` dans `postgres` avec `user: postgres` et tmpfs `/tmp`, `/var/run/postgresql` ; dans `ollama` avec tmpfs `/tmp` (design.md D2, D4) — couvre EXP-16-S1, EXP-16-S2, EXP-16-S4 — vérification : `docker inspect` (`ReadonlyRootfs`, `CapDrop`, `CapAdd` vide, `SecurityOpt`) ; écriture refusée dans `/etc`, acceptée dans le volume et `/tmp` ; volume créé sans durcissement relu après redémarrage durci
- [x] 2.3 Étendre `scripts/verifier-configuration.mjs` et son test : aucun `cap_add`, `read_only` et `no-new-privileges` présents sur les quatre services (INV-1) — couvre EXP-16-S1 — vérification : `node --test scripts/verifier-configuration.test.mjs` passe et échoue sur un `cap_add` ajouté
- [x] 2.4 Rejouer `scripts/essai-sauvegarde-restauration.sh` contre la base durcie — couvre EXP-16-S5 — vérification : script en succès, résultat consigné sous « Preuves »
- [x] 2.5 Documenter dans `/docs/deploiement/synology-et-pc.md` la reprise d'un volume PostgreSQL dont les fichiers n'appartiennent pas à 999 — couvre EXP-16-S4 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Fournisseur d'identité

- [x] 3.1 Créer `deploiement/keycloak/Dockerfile` (construction `kc.sh build`, `KC_DB=postgres`, `KC_HEALTH_ENABLED=true`) ; passer `keycloak` en `build`, image `argos-keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}`, `start --optimized --import-realm` dans le point d'entrée de garde, fragment `x-durcissement`, tmpfs `/tmp` et `/opt/keycloak/data` (design.md D3) — couvre EXP-16-S1, EXP-16-S2, EXP-16-S3, EXP-16-S6 — vérification : santé `UP`, royaume `argos` importé, aucune trace `ARJUNA048006` ni `Read-only file system` dans les journaux ; confirmer dans la documentation de Keycloak l'absence de XA par défaut
- [x] 3.2 Faire suivre par Renovate la version de Keycloak dans l'argument de construction ou le `Dockerfile` (`renovate.json`) — couvre EXP-16 — vérification : `npx --yes renovate-config-validator` réussit et une exécution à blanc détecte `quay.io/keycloak/keycloak`
- [x] 3.3 Documenter dans `/docs/deploiement/synology-et-pc.md` la construction de l'image Keycloak (`up -d --build`, reconstruction à chaque changement de `ARGOS_KEYCLOAK_VERSION`) et ajouter à ADR-0027 la conséquence « image construite localement » — couvre EXP-16-S3 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Documentation durable et décisions

- [x] 4.1 Compléter la fiche `/docs/specifications/exploitation-plateforme.md` (EXP-16 citée par identifiant, statut [PROPOSITION] avec l'identifiant du changement, critère d'acceptation, Q-7 renvoyée vers ce changement, Q-8 répondue par la décision Q-1 du 2026-11-24) et la colonne Changement de `/docs/specifications/README.md` — couvre EXP-16 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.2 Mettre à jour arc42 §7 (vue de déploiement) et §8.3 (sécurité), les notes de D-11 et D-12 si elles citent le durcissement, puis l'inventaire `/docs/architecture/diagrams/README.md` — couvre EXP-16 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate durcir-conteneurs-de-service --strict` réussit
- [ ] 5.2 Démarrer la pile complète (`ia` et `idp-local`) sur le PC puis sur le NAS, recréer les conteneurs, relever la durée de démarrage de Keycloak sur le NAS — couvre EXP-16-S1, EXP-16-S3, EXP-16-S6 — vérification : relevé `docker compose ps` et `docker inspect` consigné sous « Preuves »

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| EXP-16 | EXP-16-S1 | design.md D1 à D4, DC-1 | 2.1, 2.2, 2.3, 3.1, 5.2 | `docker inspect` ; `scripts/verifier-configuration.test.mjs` | exécuté le 2026-11-24 sur pile isolée ; NAS non relevé |
| EXP-16 | EXP-16-S2 | design.md D2 à D4, DC-1 | 2.2, 3.1 | écritures dans les conteneurs | exécuté le 2026-11-24 sur pile isolée |
| EXP-16 | EXP-16-S3 | design.md D3, DC-2 | 3.1, 5.2 | santé, royaume, requête au modèle | exécuté le 2026-11-24 sur pile isolée (installation neuve) ; NAS non relevé |
| EXP-16 | EXP-16-S4 | design.md D2 ; Compatibilité | 2.2, 2.5 | table témoin relue après passage au durcissement | exécuté le 2026-11-24 sur l'assemblage final (pile isolée) |
| EXP-16 | EXP-16-S5 | design.md Compatibilité | 2.4 | `scripts/essai-sauvegarde-restauration.sh` | exécuté le 2026-11-24 : réussi (1 contrôle de permissions non vérifié sous Windows) |
| EXP-16 | EXP-16-S6 | design.md D3, DC-1 | 3.1, 5.2 | `up -d --force-recreate`, données présentes | exécuté le 2026-11-24 sur pile isolée ; NAS non relevé |

## Preuves

- 2026-11-24, pile isolée `essai-durcissement` : relevés de design.md § Contexte technique (chemins écrits et capacités par image).
- 2026-11-24, assemblage final sur pile isolée `essai-exp16` (PC, Docker 29.9.3, ports 15432 et 18180, services `postgres`, `keycloak`, `ollama`) :
  - S1 : `docker inspect` des trois conteneurs : `ReadonlyRootfs=true`, `CapDrop=[ALL]`, `CapAdd=[]`, `SecurityOpt=[no-new-privileges:true]` ; utilisateurs `postgres`, `1000`, `root`.
  - S2 : écritures refusées (`Read-only file system`) dans `/etc`, `/usr/lib/postgresql`, `/opt/keycloak/conf`, `/opt/keycloak/lib`, `/usr/bin` ; acceptées dans les volumes, `/opt/keycloak/data` et `/tmp`.
  - S3 : installation neuve (`down -v` puis `up --wait`) : trois services `healthy`, rôles créés, royaume `argos` importé, `ollama pull all-minilm` puis `POST /api/embed` HTTP 200 ; démarrage de Keycloak en 4 à 5 s, sans `ARJUNA048006` ni `Read-only file system`.
  - S4 : volume `argos-pgdata` créé par le `compose.yaml` de la PR #44 (non durci), table témoin, puis démarrage durci : valeur relue.
  - S5 : `sh scripts/essai-sauvegarde-restauration.sh` sur le `compose.yaml` durci : « Essai réussi (1 contrôle(s) non vérifié(s)) », le contrôle des permissions POSIX étant non vérifiable sur le système de fichiers Windows.
  - S6 : `up -d --force-recreate` : royaume `argos` et son compte présents, modèle présent, `/tmp` vide dans les trois conteneurs.
  - Renovate : `renovate-config-validator` réussi ; `renovate --platform=local --dry-run=extract` relève `quay.io/keycloak/keycloak` dans l'argument de construction, l'étiquette et `.env.example`.
- Restant : tâche 5.2 sur le NAS (démarrage complet avec Argos et durée de démarrage de Keycloak).

## Workflow follow-up

- Basculer dans la fiche `exploitation-plateforme` le statut [PROPOSITION] d'EXP-16 en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive durcir-conteneurs-de-service`), après l'archivage de `fiabiliser-sauvegarde-et-roles-base`.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/durcir-conteneurs-de-service/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
