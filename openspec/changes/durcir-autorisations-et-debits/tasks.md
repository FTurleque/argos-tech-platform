# Tâches : durcir les autorisations et limiter les débits

<!--
Constats traités : AUD-TST-05, AUD-SEC-22, AUD-SEC-04, AUD-SEC-09, AUD-SEC-23 (/docs/audit/2026-11-21/findings.md), dans l'ordre interne du sprint 6.
Décisions du porteur, 2026-11-24 : 60 requêtes/min/compte pour la recherche (un compteur avec les résultats de vue) et pour « Ma veille » (compteur distinct), réglables ; adresses e-mail enregistrées effacées par migration.
Règles : aucune dépendance nouvelle (ADR-0021) ; aucune requête vers Internet dans les tests (forges simulées) ; migrations Flyway non réversibles automatiquement (ADR-0032).
-->

## 1. Demande d'évolution contrôlée avant la forge

- [x] 1.1 Contrôler visibilité, expertise et état « impact produit » avant tout appel à la forge, appel hors transaction, nouveau contrôle dans la transaction d'écriture — couvre REG-09, AUD-SEC-22 — vérification : tests 1.2 au vert
- [x] 1.2 Tests avec serveur de forge simulé qui compte les requêtes : non-membre (ticket existant et inexistant, même réponse 404, zéro requête), membre non expert (403, zéro requête, refus journalisé), fiche hors « impact produit » (409, zéro requête), expert autorisé (400 `DEMANDE_INTROUVABLE` puis association historisée) — couvre REG-09-S1, REG-09-S2, REG-09-S3, REG-09-S4 — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [x] 1.3 Fiche `/docs/specifications/veille-reglementaire.md` (REG-09 en [PROPOSITION], diagramme de l'association) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. E-mail seulement vers une adresse vérifiée

- [x] 2.1 Enregistrer l'adresse vérifiée normalisée à la création du compte et la mettre à jour à chaque connexion si elle change ; migration Flyway qui efface `compte.email` pour les comptes existants — couvre ALR-08, ALR-08-S3, ALR-08-S4, AUD-SEC-04 — vérification : tests 2.2 ; migration appliquée dans le test d'intégration
- [x] 2.2 Tests : jeton à adresse non vérifiée (aucun e-mail d'alerte, de résumé ni de rappel ; élément visible dans Argos et sur le poste), adresse vérifiée (envoi), adresse vérifiée modifiée puis reconnexion (envoi à la nouvelle seulement), compte existant après migration (aucun e-mail avant reconnexion) — couvre ALR-08-S1, ALR-08-S2, ALR-08-S3, ALR-08-S4 — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [x] 2.3 Fiches `/docs/specifications/alertes-digests.md` (ALR-08, effet de la migration) et `/docs/specifications/comptes-acces.md` (adresse enregistrée) ; arc42 §8.7 (migration, prochaine version) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Limites de débit de la recherche et de « Ma veille »

- [x] 3.1 Limiteur par compte sur `GET /api/v1/recherche` et `GET /api/v1/vues/{id}/resultats` (compteur commun), 60 requêtes/min/compte par défaut, réglable, 429 documenté dans l'OpenAPI — couvre RCH-06, AUD-SEC-09 — vérification : tests 3.3
- [x] 3.2 Limiteur par compte sur `GET /api/v1/me/veille` (compteur distinct), 60 requêtes/min/compte par défaut, réglable, 429 documenté — couvre PRF-08, AUD-SEC-09 — vérification : tests 3.3
- [x] 3.3 Tests : limite atteinte (429, `Retry-After`, refus journalisé `LIMITE_DEBIT`, aucune recherche ni donnée de veille), autre compte non affecté, fenêtre écoulée (horloge injectée), compteurs de la recherche et de « Ma veille » indépendants — couvre RCH-06-S1, RCH-06-S2, RCH-06-S3, PRF-08-S1, PRF-08-S2, PRF-08-S3 — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [ ] 3.4 Relever la limite de « Ma veille » dans la configuration de la campagne de performance et relancer la campagne — couvre PRF-08 sans régression de PRF-07-S1 — vérification : `npm --prefix e2e run test:performance` sans 429, rapport `/docs/architecture/quality/mesure-performance-api-mcp.md`
- [x] 3.5 Fiches `/docs/specifications/recherche-veille.md` (RCH-06) et `/docs/specifications/profils-veille.md` (PRF-08) ; arc42 §8.3 (débits par opération) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Écoute de bouclage du desktop

- [x] 4.1 Mémoriser le state de l'URL d'autorisation validée dans l'écoute ; n'accepter que le retour au state égal (comparaison en temps constant) ; 400 sans fermer pour les autres — couvre ACC-14, AUD-SEC-23 — vérification : tests 4.2
- [x] 4.2 Tests `argos-desktop/test/connexion.test.js` : retour au state absent ou différent (400, écoute ouverte, rien transmis), puis retour au bon state (transmis, écoute fermée) ; cas existants d'ACC-10 toujours verts — couvre ACC-14-S1, ACC-14-S2 — vérification : `npm --prefix argos-desktop run check` passe
- [x] 4.3 Fiche `/docs/specifications/comptes-acces.md` (ACC-14, DF-ACC-2 ou diagramme d'états de l'écoute) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Test matriciel point d'entrée × rôle

- [x] 5.1 Matrice versionnée (opération × rôle → code attendu) pour les rôles retenus en Q-4, et test paramétré qui énumère les opérations de `/q/openapi`, échoue si une opération manque à la matrice et compare chaque code reçu au code attendu — couvre AUD-TST-05 (dont `POST /api/v1/ops/sources/{id}/synchronizations`, `ops/impacts/integrity`, `ops/sources/{id}/rejected`, `ops/sources/{id}/events`, `PUT /api/v1/profils/artiste/sources/{id}/conditions-usage`) et REG-09-S1, REG-09-S2 (sans requête vers la forge simulée) — vérification : le test échoue si l'on retire un appel `access.operator()` d'une opération d'exploitation (essai local), puis passe ; `mvn -B -f argos-server/pom.xml verify` passe
- [x] 5.2 arc42 §8.11 (stratégie de test : matrice des rôles) et §8.1 (D-14, renvoi à la matrice) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Documentation durable et vérifications d'intégration

- [x] 6.1 Registre `/docs/specifications/README.md` (lignes et couverture des cinq capacités) ; diagrammes DC-1 et DC-2 reportés dans les fiches et inventoriés dans `/docs/architecture/diagrams/README.md` s'ils le sont — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 6.2 Valider le changement et la documentation — vérification : `openspec validate durcir-autorisations-et-debits --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur
- [ ] 6.3 Critère de sortie du sprint 6 pour ce périmètre (`/docs/audit/2026-11-21/sprints.md`) : matrice des rôles, demande d'évolution refusée sans requête vers la forge, écoute fermée sur le seul state attendu, limites de la recherche et de « Ma veille », alertes vers une adresse vérifiée — vérification : preuves des tâches 1.2, 2.2, 3.3, 4.2, 5.1

## Matrice de traçabilité

| Exigence | Scénario | Constat | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|---|
| REG-09 | REG-09-S1 | AUD-SEC-22, AUD-TST-05 | D1, D5 ; DC-1 | 1.1, 1.2, 5.1 | test de la demande d'évolution (forge simulée, zéro requête) ; matrice des rôles | non exécuté |
| REG-09 | REG-09-S2 | AUD-SEC-22, AUD-TST-05 | D1, D5 ; DC-1 | 1.1, 1.2, 5.1 | idem | non exécuté |
| REG-09 | REG-09-S3 | AUD-SEC-22 | D1 ; DC-1 | 1.1, 1.2 | test de la demande d'évolution | non exécuté |
| REG-09 | REG-09-S4 | AUD-SEC-22 | D1 ; DC-1 | 1.1, 1.2 | test de la demande d'évolution ; `chain/VeilleReglementaireTest` toujours vert | non exécuté |
| ALR-08 | ALR-08-S1 | AUD-SEC-04 | D2 | 2.1, 2.2 | test des destinataires e-mail (adresse non vérifiée) | non exécuté |
| ALR-08 | ALR-08-S2 | AUD-SEC-04 | D2 | 2.1, 2.2 | test des destinataires e-mail (adresse vérifiée) | non exécuté |
| ALR-08 | ALR-08-S3 | AUD-SEC-04 | D2 | 2.1, 2.2 | test de reconnexion avec adresse modifiée | non exécuté |
| ALR-08 | ALR-08-S4 | AUD-SEC-04 | D2 ; « Persistance et intégrité » | 2.1, 2.2 | test de la migration puis reconnexion | non exécuté |
| RCH-06 | RCH-06-S1 | AUD-SEC-09 | D3 | 3.1, 3.3 | test de limite de la recherche (429, journal) | non exécuté |
| RCH-06 | RCH-06-S2 | AUD-SEC-09 | D3 | 3.1, 3.3 | test multi-comptes | non exécuté |
| RCH-06 | RCH-06-S3 | AUD-SEC-09 | D3 | 3.1, 3.3 | test à horloge injectée | non exécuté |
| PRF-08 | PRF-08-S1 | AUD-SEC-09 | D3 | 3.2, 3.3, 3.4 | test de limite de « Ma veille » ; campagne de performance sans 429 | non exécuté |
| PRF-08 | PRF-08-S2 | AUD-SEC-09 | D3 | 3.2, 3.3 | test multi-comptes | non exécuté |
| PRF-08 | PRF-08-S3 | AUD-SEC-09 | D3 | 3.1, 3.2, 3.3 | test des compteurs indépendants | non exécuté |
| ACC-14 | ACC-14-S1 | AUD-SEC-23 | D4 ; DC-2 | 4.1, 4.2 | `argos-desktop/test/connexion.test.js` | non exécuté |
| ACC-14 | ACC-14-S2 | AUD-SEC-23 | D4 ; DC-2 | 4.1, 4.2 | `argos-desktop/test/connexion.test.js` | non exécuté |

Constat sans exigence propre : AUD-TST-05 (test matriciel point d'entrée × rôle) — design.md D5, tâche 5.1, preuve : test matriciel, non exécuté.

## Workflow follow-up

- Basculer dans les fiches `veille-reglementaire`, `alertes-digests`, `recherche-veille`, `profils-veille` et `comptes-acces` les statuts [PROPOSITION] de REG-09, ALR-08, RCH-06, PRF-08 et ACC-14 selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive durcir-autorisations-et-debits`).
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/durcir-autorisations-et-debits/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
