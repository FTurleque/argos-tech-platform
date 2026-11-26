# ADR-0039 — Exécuter hors requête les traitements longs, propager les événements selon trois styles et encadrer les appels sortants

- **Statut** : Proposé
- **Date** : 2026-11-26
- **Décideurs** : porteur du projet (périmètre des observateurs asynchrones, alertes comprises ; suppression de `AlerteEmise` ; réponse 409 pour une synchronisation en cours ; 2026-11-26)
- **Remplace** : —
- **Remplacé par** : —
- **Précise** : [ADR-0031](0031-planification-quarkus-scheduler.md) (traitements planifiés), [ADR-0037](0037-noyau-commun-du-serveur.md) (contenu du noyau commun)

## Contexte

- Un lancement de synchronisation par l'exploitant, l'enrichissement d'un signal par NVD, EPSS, KEV et CERT-FR, l'annonce d'un changement de standard et l'évaluation des alertes s'exécutent dans le fil de leur déclencheur : requête HTTP ou fil d'ingestion (audit du 2026-11-21, AUD-PER-01, AUD-PER-02, AUD-ARC-09).
- Les appels aux sources d'enrichissement ont lieu dans une transaction ouverte ; un délai de source (PT30S) occupe alors une connexion et approche le délai de transaction par défaut.
- Trois manières d'observer un événement CDI coexistent sans règle écrite : `@Observes` synchrone, `@Observes(during = AFTER_SUCCESS)`, aucune `@ObservesAsync`. Hors transaction JTA, un observateur `AFTER_SUCCESS` est appelé immédiatement, sur le fil de l'émetteur.
- Six clients `java.net.http` sont construits séparément ; deux lisent leurs réponses sans plafond (AUD-ARC-10, AUD-PER-11).
- Les exécuteurs bornés possédés par un bean (`io.argos.execution.ExecuteurBorne`) existent ; aucun broker n'est retenu (arc42 §8.9).

## Critères de décision

1. Aucune requête HTTP n'attend un traitement long, un appel réseau, l'IA ou un envoi.
2. Aucun appel réseau dans une transaction ouverte, vérifiable automatiquement.
3. Aucune erreur imprévue absorbée : un traitement sans appelant journalise sa cause et compte son échec.
4. Aucune dépendance livrée en plus ([ADR-0021](0021-double-licence.md)).
5. Une seule configuration des clients HTTP sortants.

## Options considérées

### Option A — Exécuteurs possédés par des beans, file persistante pour l'enrichissement, observateurs asynchrones encadrés

- avantages : réutilise `ExecuteurBorne` (arrêt propre, corrélation des journaux, tailles configurées) ; l'enrichissement survit au redémarrage ; règles vérifiables par ArchUnit ;
- inconvénients : tests d'alertes et du radar à rendre asynchrones (attente bornée) ;
- coûts : une table, quatre exécuteurs configurés.

### Option B — Broker de messages

- avantages : découplage fort, rejeu ;
- inconvénients : service de plus à exploiter sur le NAS ; arc42 §8.9 l'exclut sans besoin mesuré.

### Option C — `ManagedExecutor` et pool par défaut de `fireAsync`

- avantages : moins de code ;
- inconvénients : extension supplémentaire, pool partagé non borné par usage, corrélation des journaux non propagée.

## Décision

**Option A.**

1. **Traitement long déclenché par une requête** : la requête démarre le traitement et en rend l'identifiant (`202 Accepted`), puis un exécuteur borné possédé par un bean le poursuit (`ExecuteurBorne.lancer`). Une erreur imprévue qui sort d'une tâche de fond est journalisée au niveau erreur avec sa cause et comptée (`argos_traitements_fond_echecs_total`). La synchronisation planifiée et le lancement manuel exécutent le même traitement.
2. **Appels réseau hors transaction** : aucune méthode qui appelle une source externe d'avis ou d'enrichissement, ni aucun de ses appelants, n'est transactionnelle ou n'ouvre de transaction ; chaque écriture qui suit est une transaction courte. Le travail réseau en attente est tenu dans une file persistante traitée par une tâche planifiée (ADR-0031) quand il doit survivre au redémarrage.
3. **Événements entre modules**, trois styles :
   - `@Observes` synchrone : travail court, local, sans appel réseau, dont l'émetteur ou sa réponse a besoin avant de continuer, ou invalidation en mémoire ; chaque observateur synchrone d'un événement d'un autre module figure dans une liste fermée vérifiée par un test ;
   - `@Observes(during = AFTER_SUCCESS)` : travail court en base qui suit une modification validée ;
   - `@ObservesAsync`, publié par `PublicationAsynchrone` sur un exécuteur dédié (file pleine : exécution sur le fil émetteur) : travail long, réseau, IA, envoi, ou tout travail qu'une requête ne doit pas attendre. Une erreur imprévue d'un observateur asynchrone est journalisée avec sa cause et comptée (`argos_evenements_asynchrones_echecs_total`).
4. **Appels sortants** : `io.argos.commun.http` (noyau commun, ADR-0037) construit tous les clients `java.net.http` (redirections refusées, `User-Agent` fixe, délai de connexion fourni par l'appelant) et lit chaque corps sous une taille maximale (`LectureBornee`) ; chaque client garde sa classification d'erreurs. Le transport épinglé de la veille web garde son propre transport et son plafond.

## Conséquences positives

- Les requêtes d'exploitation et du référentiel répondent sans attendre les sources, l'IA ni les envois.
- Un enrichissement refusé est repris ; aucune connexion n'est tenue pendant un appel réseau.
- Un nouvel observateur synchrone inter-modules ou un nouveau client HTTP hors fabrique fait échouer un test.

## Conséquences négatives

- Les tests qui vérifient une alerte ou une annonce attendent la fin des observateurs asynchrones (Awaitility).
- Une alerte ou une annonce arrive après la réponse de la requête qui l'a déclenchée.

## Conséquences neutres / compromis

- Les attentes de reprise d'une synchronisation dorment toujours, sur un fil de fond dédié, sous l'exclusion du traitement.
- Le débit vers NVD est borné par la taille des lots et la cadence de la file, sans limiteur par jeton.

## Méthode de validation

- `AppelsReseauHorsTransactionTest`, `ObservateursEvenementsTest`, `ClientsHttpCommunsTest`, chacun prouvé contre une classe d'exemple qui viole sa règle ;
- `LancementSynchronisationTest` (202, 409, exclusivité pendant l'attente de reprise) ;
- `EnrichmentTest` (file, reprise, rattrapage), `RadarStandardsTest` (annonce asynchrone, échec compté).

## Traçabilité

- **Exigences** : COL-04, COL-12, RAD-07
- **Scénarios qualité** : arc42 §10
- **Diagrammes** : arc42 §5.3, §6.1, §8 ; `openspec/changes/archive/2026-11-26-rendre-ingestion-asynchrone/design.md` (DC-1 à DC-5)
- **Fichiers source** : `argos-server/src/main/java/io/argos/execution/`, `argos-server/src/main/java/io/argos/commun/http/`, `argos-server/src/main/java/io/argos/vulnintel/TraitementEnrichissements.java`, `argos-server/src/main/java/io/argos/ingestion/LancementSynchronisation.java`
