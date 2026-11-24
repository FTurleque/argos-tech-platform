# Tâches : durcir les conteneurs de service

<!-- Chaque tâche cite les scénarios couverts et sa vérification. Les essais se font sur une pile isolée
(`docker compose -p <projet-d-essai>`), jamais avec l'étiquette `argos:local` ni les conteneurs `argos-*` du porteur. -->

## 1. Préparation

- [x] 1.1 Faire trancher Q-1 (image Keycloak construite localement) et noter la réponse dans proposal.md — couvre EXP-16 — vérification : réponse datée dans le tableau des questions (acceptée le 2026-11-24, design.md D3 en [DÉCISION])
- [ ] 1.2 Rebaser la branche d'implémentation sur `main` après la fusion de la PR #44 (`completer-configuration-conteneurs`) — couvre EXP-16 — vérification : `compose.yaml` contient le durcissement d'`argos`, `x-journaux` et le point d'entrée de garde de `keycloak`

## 2. Base de données et modèle local

- [ ] 2.1 Ajouter le fragment `x-durcissement` à `compose.yaml`, le fusionner dans `argos` à la place de ses trois clés (design.md D1) — couvre EXP-16-S1 — vérification : `docker compose config` identique pour `argos` avant et après
- [ ] 2.2 Fusionner `x-durcissement` dans `postgres` avec `user: postgres` et tmpfs `/tmp`, `/var/run/postgresql` ; dans `ollama` avec tmpfs `/tmp` (design.md D2, D4) — couvre EXP-16-S1, EXP-16-S2, EXP-16-S4 — vérification : `docker inspect` (`ReadonlyRootfs`, `CapDrop`, `CapAdd` vide, `SecurityOpt`) ; écriture refusée dans `/etc`, acceptée dans le volume et `/tmp` ; volume créé sans durcissement relu après redémarrage durci
- [ ] 2.3 Étendre `scripts/verifier-configuration.mjs` et son test : aucun `cap_add`, `read_only` et `no-new-privileges` présents sur les quatre services (INV-1) — couvre EXP-16-S1 — vérification : `node --test scripts/verifier-configuration.test.mjs` passe et échoue sur un `cap_add` ajouté
- [ ] 2.4 Rejouer `scripts/essai-sauvegarde-restauration.sh` contre la base durcie — couvre EXP-16-S5 — vérification : script en succès, résultat consigné sous « Preuves »
- [ ] 2.5 Documenter dans `/docs/deploiement/synology-et-pc.md` la reprise d'un volume PostgreSQL dont les fichiers n'appartiennent pas à 999 — couvre EXP-16-S4 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Fournisseur d'identité

- [ ] 3.1 Créer `deploiement/keycloak/Dockerfile` (construction `kc.sh build`, `KC_DB=postgres`, `KC_HEALTH_ENABLED=true`) ; passer `keycloak` en `build`, image `argos-keycloak:${ARGOS_KEYCLOAK_VERSION:-26.7.4}`, `start --optimized --import-realm` dans le point d'entrée de garde, fragment `x-durcissement`, tmpfs `/tmp` et `/opt/keycloak/data` (design.md D3) — couvre EXP-16-S1, EXP-16-S2, EXP-16-S3, EXP-16-S6 — vérification : santé `UP`, royaume `argos` importé, aucune trace `ARJUNA048006` ni `Read-only file system` dans les journaux ; confirmer dans la documentation de Keycloak l'absence de XA par défaut
- [ ] 3.2 Faire suivre par Renovate la version de Keycloak dans l'argument de construction ou le `Dockerfile` (`renovate.json`) — couvre EXP-16 — vérification : `npx --yes renovate-config-validator` réussit et une exécution à blanc détecte `quay.io/keycloak/keycloak`
- [ ] 3.3 Documenter dans `/docs/deploiement/synology-et-pc.md` la construction de l'image Keycloak (`up -d --build`, reconstruction à chaque changement de `ARGOS_KEYCLOAK_VERSION`) et ajouter à ADR-0027 la conséquence « image construite localement » — couvre EXP-16-S3 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Documentation durable et décisions

- [ ] 4.1 Compléter la fiche `/docs/specifications/exploitation-plateforme.md` (EXP-16 citée par identifiant, statut [PROPOSITION] avec l'identifiant du changement, critère d'acceptation, Q-7 renvoyée vers ce changement, Q-8 répondue par la décision Q-1 du 2026-11-24) et la colonne Changement de `/docs/specifications/README.md` — couvre EXP-16 — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Mettre à jour arc42 §7 (vue de déploiement) et §8.3 (sécurité), les notes de D-11 et D-12 si elles citent le durcissement, puis l'inventaire `/docs/architecture/diagrams/README.md` — couvre EXP-16 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate durcir-conteneurs-de-service --strict` réussit
- [ ] 5.2 Démarrer la pile complète (`ia` et `idp-local`) sur le PC puis sur le NAS, recréer les conteneurs, relever la durée de démarrage de Keycloak sur le NAS — couvre EXP-16-S1, EXP-16-S3, EXP-16-S6 — vérification : relevé `docker compose ps` et `docker inspect` consigné sous « Preuves »

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| EXP-16 | EXP-16-S1 | design.md D1 à D4, DC-1 | 2.1, 2.2, 2.3, 3.1, 5.2 | `docker inspect` ; `scripts/verifier-configuration.test.mjs` | à écrire |
| EXP-16 | EXP-16-S2 | design.md D2 à D4, DC-1 | 2.2, 3.1 | écritures dans les conteneurs | non exécuté sur l'assemblage final |
| EXP-16 | EXP-16-S3 | design.md D3, DC-2 | 3.1, 5.2 | santé, royaume, requête au modèle | non exécuté sur l'assemblage final |
| EXP-16 | EXP-16-S4 | design.md D2 ; Compatibilité | 2.2, 2.5 | table témoin relue après passage au durcissement | exécuté sur pile isolée le 2026-11-24 ; à rejouer |
| EXP-16 | EXP-16-S5 | design.md Compatibilité | 2.4 | `scripts/essai-sauvegarde-restauration.sh` | non exécuté |
| EXP-16 | EXP-16-S6 | design.md D3, DC-1 | 3.1, 5.2 | `up -d --force-recreate`, données présentes | non exécuté |

## Preuves

- 2026-11-24, pile isolée `essai-durcissement` : relevés de design.md § Contexte technique (chemins écrits et capacités par image).

## Workflow follow-up

- Basculer dans la fiche `exploitation-plateforme` le statut [PROPOSITION] d'EXP-16 en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive durcir-conteneurs-de-service`), après l'archivage de `fiabiliser-sauvegarde-et-roles-base`.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/durcir-conteneurs-de-service/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
