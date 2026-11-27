# Delta de spécification : correlation-impact

## MODIFIED Requirements

### Requirement: COR-07 Idempotence de la corrélation
Le système DOIT (MUST) garantir qu'une nouvelle exécution de la corrélation pour un même signal, un même projet et un même composant ne crée pas de second impact, et NE DOIT PAS (MUST NOT) modifier un impact existant, ni sa date de mise à jour, lorsque aucune de ses valeurs ne change.
Source : arc42 §6.1 (invariants) ; QS-04 ; AUD-PER-25. Statut : [PROPOSITION].

#### Scenario: COR-07-S1 Corrélation rejouée
- **GIVEN** un impact existant pour un signal, un projet et un composant
- **WHEN** la corrélation de ce signal est exécutée de nouveau sans changement d'inventaire
- **THEN** un seul impact existe pour ce triplet

#### Scenario: COR-07-S2 Recorrélation sans changement
- **GIVEN** un impact ouvert dont le niveau, la version de l'avis, la provenance et le composant n'ont pas changé depuis sa dernière corrélation
- **WHEN** l'import quotidien recorrèle son projet
- **THEN** l'impact garde sa date de mise à jour
- **AND** aucune valeur de l'impact n'est modifiée

#### Scenario: COR-07-S3 Recorrélation avec changement
- **GIVEN** un impact ouvert dont l'avis a reçu une nouvelle version
- **WHEN** la corrélation du signal s'exécute
- **THEN** l'impact porte la nouvelle version de l'avis
- **AND** sa date de mise à jour est celle de cette corrélation

## ADDED Requirements

### Requirement: COR-13 Corrélation en un nombre fixe d'instructions par lot
Le système DOIT (MUST) corréler un projet par lots de signaux, chaque lot s'exécutant en un nombre d'instructions SQL indépendant du nombre de signaux du lot et du nombre de composants correspondants, sans changer les impacts obtenus (COR-01 à COR-11).
Source : AUD-PER-03 ; critère de sortie du sprint 10 (`/docs/audit/2026-11-21/sprints.md`). Statut : [PROPOSITION].

#### Scenario: COR-13-S1 Nombre d'instructions constant
- **GIVEN** deux projets dont l'un a dix fois plus de signaux et de composants correspondants que l'autre, chacun tenant en un lot
- **WHEN** chaque projet est recorrélé
- **THEN** les deux recorrélations exécutent le même nombre d'instructions SQL

#### Scenario: COR-13-S2 Résultat inchangé
- **GIVEN** un projet dont des impacts sont à créer, à mettre à jour, à rouvrir et à résoudre
- **WHEN** le projet est recorrélé par lots
- **THEN** les impacts créés, mis à jour, rouverts et résolus, leurs états, motifs et historiques sont ceux qu'exigent COR-07 et COR-11
