# Tâches : extraire le noyau commun du serveur

<!--
Refactoring du sprint 5 (AUD-ARC-05, AUD-ARC-04, AUD-QUA-06, AUD-ARC-06, AUD-QUA-04), skip_specs: true.
Ordre interne du sprint : AUD-ARC-05 → AUD-ARC-04 → AUD-QUA-06 → AUD-ARC-06 → AUD-QUA-04 (/docs/audit/2026-11-21/sprints.md).
Dépend de ecrire-decisions-architecture-socle (décision du porteur sur l'ADR du noyau commun, encore à rédiger).
Décisions du porteur du 2026-11-24 : modèle transactionnel tout en JTA (D2) ; utilitaire JDBC interne sans dépendance nouvelle (D3).
Règles : comportement observable inchangé (REST, MCP, RLS) ; chaque groupe laisse mvn -B -f argos-server/pom.xml verify vert ;
aucune dépendance nouvelle (D3).
-->

## 1. Décisions préalables

- [x] 1.1 Vérifier que l'ADR du noyau commun a reçu la décision du porteur (`ecrire-decisions-architecture-socle`, tâche 3.2) ; s'il n'est pas accepté, s'arrêter et le signaler — couvre AUD-ARC-05 — vérification : statut de l'ADR relu
- [x] 1.2 Soumettre au porteur les alternatives D2 (modèle transactionnel) et D3 (outil JDBC) de design.md et consigner sa décision sous « Décisions du porteur » — couvre AUD-QUA-04, AUD-QUA-06 — vérification : décision du porteur du 2026-11-24 (D2 tout en JTA, D3 utilitaire interne) consignée
- [x] 1.3 Consigner D2 (tout en JTA) et D3 (utilitaire JDBC interne) dans l'ADR du noyau commun s'il est encore Proposé, sinon dans un nouvel ADR Proposé ; mettre à jour `/docs/architecture/adr/README.md` et `/docs/architecture/arc42/09-decisions.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Paquet du noyau commun (AUD-ARC-05)

- [ ] 2.1 Créer le paquet du noyau commun et y déplacer les erreurs du domaine partagées (`DonneeInvalide`, `RegleViolee`, `Introuvable`, `AccesRefuse`, `MotifRefus`) et le contexte RLS ; sous-paquet sécurité pour `Caller` et `LimiteurDebit` ; mettre à jour les imports — couvre AUD-ARC-05 — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [ ] 2.2 Test des frontières : entrée `commun` autorisée à tous, `commun` sans dépendance ; retirer les cinq règles d'exception et les entrées `ALLOWED` devenues inutiles (design.md, D5) ; règle NC-3 — couvre AUD-ARC-05 — vérification : `ModuleBoundariesTest` passe sans règle d'exception ; un exemple de violation (paquet d'exemples de test) est détecté
- [ ] 2.3 Documentation du groupe : arc42 §5.3 (composant « commun » : responsabilité, « ne doit pas »), §5.5 (règle de dépendance vers le noyau), §5.2 (diagramme de composants), inventaire `/docs/architecture/diagrams/README.md` si le diagramme change — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Contexte RLS unique (AUD-ARC-04)

- [ ] 3.1 Faire de l'application du contexte du noyau le seul point qui pose `SET LOCAL ROLE argos_app` et les quatre paramètres ; supprimer `impact/RlsSession` et les variantes de `SubscriptionService` et `PreferencesRepository` ; un compte nul donne la chaîne vide — couvre AUD-ARC-04 — vérification : `grep -rn "SET LOCAL ROLE" argos-server/src/main/java` ne renvoie que le noyau
- [ ] 3.2 Tests du groupe : test de source NC-1 (aucune occurrence de `SET LOCAL ROLE` ni `set_config('argos.` hors du noyau) ; test NC-4 (quatre paramètres posés, valeurs nulles) ; tests de cloisonnement existants (`SubscriptionIsolationTest`, `SuiviIsolationTest`, tests ACC-04 et COR-08) verts — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [ ] 3.3 Documentation du groupe : arc42 §8.1 ou §8.7, point d'application unique du contexte RLS — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Utilitaire JDBC (AUD-QUA-06)

- [ ] 4.1 Implémenter l'utilitaire JDBC interne du noyau commun (D3), sans dépendance nouvelle, avec les convertisseurs communs (dates, troncature) et une seule traduction de `SQLException` — couvre AUD-QUA-06 — vérification : tests unitaires de l'utilitaire et des conversions passent ; `argos-server/pom.xml` sans dépendance ajoutée
- [ ] 4.2 Migrer les modules cœur `signal`, `impact`, `alerting` ; supprimer leurs copies de `instant(ResultSet …)`, `couper`/`cut`, `utc` — couvre AUD-QUA-06 — vérification : `grep -rn "private static Instant instant(ResultSet" argos-server/src/main/java/io/argos/{signal,impact,alerting}` vide ; tests existants de ces modules verts
- [ ] 4.3 Documentation du groupe : arc42 §8.7, règle d'accès aux données (SQL explicite, utilitaire commun, modules restant à migrer) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Propriété des tables (AUD-ARC-06)

- [ ] 5.1 Établir la table « module propriétaire → tables » à partir des migrations V1 à la dernière, et la reporter dans arc42 §5.3 — couvre AUD-ARC-06 — vérification : chaque table créée par une migration a un propriétaire
- [ ] 5.2 Faire passer les trois lectures non couvertes par le propriétaire selon D4 (vue nommée pour `impact → compte`, service de lecture pour `catalog → appartenance` et `alerting → préférences`), avec une migration Flyway au numéro libre si une vue est créée (ligne ajoutée à l'inventaire d'arc42 §8.7) — couvre AUD-ARC-06 — vérification : tests existants de l'historique des transitions, du dictionnaire métier et des destinataires verts ; test sous RLS de la vue
- [ ] 5.3 Test de propriété NC-5 (chaque module ne cite que ses tables ou des vues publiées) ; corriger arc42 §5.4.1 et `PreferencesIsolationTest` pour le lecteur `alerting` — couvre AUD-ARC-06 — vérification : test vert ; une citation étrangère d'exemple est détectée ; `node scripts/verifier-docs.mjs` sans erreur

## 6. Modèle transactionnel unique (AUD-QUA-04)

- [ ] 6.1 Migrer les six sites manuels vers des transactions JTA ouvertes par le noyau commun (D2), un site par commit (`ContexteBase`, `QuotasIa`, `RadarProjets`, `RapprochementActualites`, `PreparationFiches`, `CyclesDeVie`) — couvre AUD-QUA-04 — vérification : `grep -rn "setAutoCommit" argos-server/src/main/java` ne renvoie rien ; tests de chaque site verts
- [ ] 6.2 Règle ArchUnit NC-2 (aucun appel à `Connection.setAutoCommit`, `commit`, `rollback` dans le serveur) — couvre AUD-QUA-04 — vérification : règle verte ; un exemple de violation est détecté
- [ ] 6.3 Documentation du groupe : arc42 §8.7, modèle transactionnel unique et règle outillée ; ADR du noyau commun à jour (tâche 1.3) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 7. Vérifications d'intégration

- [ ] 7.1 Valider le changement et la documentation — vérification : `openspec validate extraire-noyau-commun --strict` réussit ; `node scripts/verifier-docs.mjs` sans erreur
- [ ] 7.2 Contrat inchangé — vérification : document `/q/openapi` identique avant et après (diff vide) ; suite `mvn -B -f argos-server/pom.xml verify` complète verte
- [ ] 7.3 Performance non dégradée — vérification : `npm --prefix e2e run test:performance` sous le verrou de la pile E2E, toutes les opérations dans la cible d'arc42 §10.2 Q-05

## Matrice de traçabilité

| Constat | Exigence / scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AUD-ARC-05 | aucune (`skip_specs`) | D1, D5, DC-1, DC-2, NC-3 | 1.1, 2.1, 2.2, 2.3 | `ModuleBoundariesTest` sans exception | non exécuté |
| AUD-ARC-04 | aucune ; non-régression ACC-04, COR-08 | DC-2, DC-3, NC-1, NC-4 | 3.1, 3.2, 3.3 | test de source NC-1, test NC-4, tests de cloisonnement | non exécuté |
| AUD-QUA-06 | aucune | D3, DC-2 | 1.2, 4.1, 4.2, 4.3 | décision du porteur du 2026-11-24 ; tests de l'utilitaire ; tests de `signal`, `impact`, `alerting` | décision obtenue (1.2) ; tests non exécutés |
| AUD-ARC-06 | aucune | D4, NC-5 | 5.1, 5.2, 5.3 | test de propriété NC-5 ; arc42 §5.3 | non exécuté |
| AUD-QUA-04 | aucune | D2, DC-3, NC-2 | 1.2, 6.1, 6.2, 6.3 | décision du porteur du 2026-11-24 ; règle ArchUnit NC-2 ; tests des six sites | décision obtenue (1.2) ; tests non exécutés |
| Contrat et performance inchangés | aucune | « Contrats d'interface », « Performance » | 7.2, 7.3 | diff `/q/openapi` ; campagne de performance | non exécuté |

## Décisions du porteur

- 2026-11-24 — [DÉCISION] D2 : modèle transactionnel tout en JTA ; l'utilitaire manuel unique est écarté.
- 2026-11-24 — [DÉCISION] D3 : utilitaire JDBC interne, sans dépendance nouvelle ; Jdbi est écarté.
- 2026-11-25 — [DÉCISION] ADR-0037 (noyau commun du serveur, D2 et D3 consignés) : « Accepté ».

## Workflow follow-up

- Mettre à jour le statut d'AUD-ARC-05, AUD-ARC-04, AUD-QUA-06, AUD-ARC-06 et AUD-QUA-04 dans `/docs/audit/2026-11-21/etat.md` ; signaler à AUD-DOC-02 (sprint 12) que §5.3 et §8.7 sont alignées.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive extraire-noyau-commun`).
