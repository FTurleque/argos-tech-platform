# Fiche capacité : `collecte-avis-securite`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `COL` |
| Spec de référence | `/openspec/specs/collecte-avis-securite/spec.md` — absente : capacité non livrée |
| Changement en cours | [`formaliser-chaine-securite-poc`](/openspec/changes/formaliser-chaine-securite-poc/specs/collecte-avis-securite/spec.md) — proposé, non archivé |
| Statut de la capacité | **proposée**, implémentée dans `argos-server` (changement non archivé) : paquets `io.argos.ingestion`, `io.argos.signal`, `io.argos.vulnintel` |
| Composants (arc42 §5) | `source-ingestion`, `signal`, `vuln-intel`, `audit`, adaptateur PostgreSQL |

Tout le contenu de cette fiche porte le statut [PROPOSITION] (changement `formaliser-chaine-securite-poc`) sauf mention contraire.

## Objectif

Disposer, sans veille manuelle, des avis de vulnérabilité qui concernent les composants open source, sous une forme normalisée et traçable, afin que la corrélation (`correlation-impact`) puisse désigner les projets touchés. Source : arc42 §1.2 objectifs 1 et 2 ; ADR-0010.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| OSV | publie les avis, source principale | [PROPOSITION] — ADR-0010 |
| NVD, CISA KEV, FIRST EPSS, CERT-FR | enrichissent les signaux | [PROPOSITION] — ADR-0010 |
| Responsable sécurité, utilisateur technique | consultent les signaux et leur provenance | [PROPOSITION] — arc42 §1.3 |
| Exploitant | surveille l'état des sources | [PROPOSITION] — arc42 §3.2, §6.3 |
| Planificateur interne | déclenche les synchronisations | [QUESTION] — composant absent de arc42 §5 ; hypothèse arc42 §6.5 |

## Périmètre et exclusions

- **Inclus** : collecte OSV, enrichissement, normalisation en signal, historisation des modifications, idempotence, reprise sur erreur, état dégradé, mise à l'écart des avis non conformes.
- **Exclu** : corrélation avec les projets (`correlation-impact`), alertes (lot 2), analyse IA, autres familles de signaux (releases, réglementation, RSS).

## Préconditions et déclencheurs

- La source OSV est configurée et joignable depuis l'environnement (proxy sortant selon politique SI : arc42 §7.3) [QUESTION].
- Déclencheur : synchronisation planifiée ou reprise après redémarrage (arc42 §6.3, §6.4). La fréquence n'est pas définie [QUESTION Q-COL-1].

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Entrée : avis OSV | identifiant, paquets (PURL), plages de versions, alias CVE, dates | identifiant externe obligatoire ; contenu traité comme donnée non fiable (arc42 §8.3) | [PROPOSITION] |
| Entrée : données d'enrichissement | score/fiche NVD, présence KEV, probabilité EPSS, avis CERT-FR | rattachées par identifiant (CVE) | [PROPOSITION] |
| Sortie : signal de sécurité | contenu normalisé, provenance, historique des versions | un signal logique par source + identifiant externe | [PROPOSITION] |
| Sortie : zone d'échec | avis rejeté, motif, provenance | aucune suppression silencieuse (arc42 §11.2) | [PROPOSITION] |
| Sortie : événement d'exploitation | passage en `DEGRADED` | daté, visible de l'exploitant | [PROPOSITION] |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| OSV est la source principale ; les autres enrichissent | COL-01, COL-04 | ADR-0010 | [PROPOSITION] |
| Un avis = un signal logique ; les modifications sont historisées | COL-03 | arc42 §6.1 ; baseline v2.3 §4 | [PROPOSITION] |
| Un signal conserve sa provenance complète | COL-02 | arc42 §4.2 P3 | [PROPOSITION] |
| Le curseur n'avance qu'après persistance durable | COL-05 | arc42 §6.3 | [PROPOSITION] |
| Retry borné, `Retry-After` respecté, erreurs permanentes distinguées | COL-06 | arc42 §6.3, §8.5 | [PROPOSITION] |
| Seuil d'échec → `DEGRADED` + événement d'exploitation | COL-07 | arc42 §6.3 | [PROPOSITION] |
| Avis non normalisable → zone d'échec, lot poursuivi | COL-08 | arc42 §8.3, §8.5, §11.2 | [PROPOSITION] |

## Droits d'accès et restrictions

Les signaux de sécurité sont globaux, non rattachés à un espace (ADR-0016, contexte). Leur consultation exige une identité authentifiée (ADR-0008, Proposé). L'état des sources est destiné au rôle `OPS` (arc42 §8.1) [PROPOSITION].

## Effets de bord observables

- création ou mise à jour de signaux et de leur historique ;
- avancement du curseur de la source ;
- enregistrements dans la zone d'échec ;
- changement d'état de la source et événement d'exploitation ;
- déclenchement de la corrélation pour les signaux créés ou modifiés (capacité `correlation-impact`).

## Qualités mesurables

| Qualité | Cible | Source | Statut |
|---|---|---|---|
| Perte d'avis lors d'une panne | 0 | arc42 §10.2 Q-03 ; QS-03 | [PROPOSITION] |
| Duplication logique après rejeu | 0 | arc42 §10.2 Q-03 ; QS-04 | [PROPOSITION] |
| Délai publication source → signal | « alerte sécurité < 1 h » | ADR-0010, traçabilité | [DÉCISION] Q-6 du changement — porteur du projet, 2026-11-18 : cible de qualité mesurée pendant le POC, sans être un critère d'acceptation |
| Fraîcheur des sources | métrique à exposer, seuil non défini | arc42 §8.6 | [QUESTION] |

## Diagrammes

### DF-COL-1 — Synchronisation d'une source de vulnérabilités

- **Question** : comment un avis OSV devient-il un signal enrichi, et que se passe-t-il quand une source répond en erreur ?
- **Type** : UML séquence
- **Portée** : implémenté dans `argos-server` (`io.argos.ingestion.SourceSynchronizer`, `SyncStore`, `RetryPolicy`, `io.argos.ingestion.osv`, `io.argos.vulnintel`) ; la planification automatique n'existe pas : une synchronisation se lance par `POST /api/v1/ops/sources/{id}/synchronizations`
- **Exigences** : COL-01, COL-03, COL-04, COL-05, COL-06

```mermaid
sequenceDiagram
    participant Plan as Planificateur (non défini en §5)
    participant Ing as source-ingestion
    participant OSV as «Software System» OSV
    participant Sig as signal
    participant Vuln as vuln-intel
    participant Sec as «Software System» NVD / KEV / EPSS / CERT-FR
    participant DB as PostgreSQL

    Plan->>Ing: lancer la synchronisation OSV
    Ing->>DB: lire le dernier curseur persisté
    Ing->>OSV: demander les avis depuis le curseur
    alt réponse reçue
        OSV-->>Ing: avis publiés ou modifiés
        loop pour chaque avis
            Ing->>Ing: normaliser (voir DF-COL-2)
            Ing->>Sig: créer ou historiser le signal (source + identifiant externe)
            Sig->>DB: enregistrer signal, version et provenance
            Sig->>Vuln: demander l'enrichissement
            opt identifiant CVE présent
                Vuln->>Sec: lire les données d'enrichissement
                alt source secondaire disponible
                    Sec-->>Vuln: données
                    Vuln->>DB: rattacher l'enrichissement au signal
                else source secondaire indisponible
                    Vuln-->>Sig: enrichissement absent, signal conservé
                end
            end
        end
        Ing->>DB: avancer le curseur après persistance durable
    else 429 avec Retry-After, timeout ou 5xx
        OSV--xIng: erreur transitoire
        Ing->>DB: enregistrer tentative et erreur, curseur inchangé
        Ing->>Plan: replanifier après Retry-After ou délai croissant
    else erreur permanente
        OSV--xIng: authentification refusée ou requête invalide
        Ing->>DB: enregistrer erreur permanente, sans nouvelle tentative
    end
```

Le curseur n'avance qu'en fin de lot, après l'enregistrement durable des signaux (COL-05) : une panne entre deux avis fait rejouer le lot, et l'idempotence (COL-03) absorbe les avis déjà enregistrés. L'enrichissement est facultatif : son absence ne bloque pas le signal (COL-04-S3). Le planificateur figure en §6 (`Scheduler & Jobs`) mais pas parmi les composants de §5 ; son existence reste une hypothèse (arc42 §6.5).

### DF-COL-2 — Traitement d'un avis reçu

- **Question** : quelle décision le système prend-il pour chaque avis reçu ?
- **Type** : organigramme (flowchart), pas un diagramme UML
- **Portée** : cible proposée
- **Exigences** : COL-02, COL-03, COL-08

```mermaid
flowchart TD
    A["Avis reçu de la source"] --> B{"Identifiant externe présent<br/>et contenu normalisable ?"}
    B -- non --> F["Zone d'échec : avis, motif, provenance<br/>(COL-08, COL-02-S2)"]
    B -- oui --> C{"Signal existant pour<br/>source + identifiant externe ?"}
    C -- non --> N["Créer le signal et sa provenance<br/>(COL-01, COL-02)"]
    C -- oui --> D{"Contenu identique à la<br/>dernière version ?"}
    D -- oui --> I["Aucun effet<br/>(COL-03-S1)"]
    D -- non --> H["Historiser la nouvelle version<br/>sur le même signal (COL-03-S2)"]
    F --> S["Avis suivant du lot"]
    N --> S
    I --> S
    H --> S
```

Un avis rejeté n'interrompt pas le lot (COL-08-S1). Le critère « contenu identique » n'est pas défini dans les sources (comparaison complète ou date de modification de l'avis) [QUESTION Q-COL-2].

### DF-COL-3 — État de synchronisation d'une source

- **Question** : comment une source passe-t-elle en état dégradé et en sort-elle ?
- **Type** : UML états (stateDiagram-v2)
- **Portée** : cible proposée ; seul l'état `DEGRADED` est nommé par les sources (arc42 §6.3) — les noms `Nominale` et `En échec` sont proposés par ce changement
- **Exigences** : COL-06, COL-07

```mermaid
stateDiagram-v2
    state "Nominale" as Nominale
    state "En échec (compteur < seuil)" as EnEchec
    state "DEGRADED" as Degraded

    [*] --> Nominale
    Nominale --> EnEchec: synchronisation en erreur
    EnEchec --> EnEchec: nouvel échec, compteur incrémenté
    EnEchec --> Nominale: synchronisation réussie
    EnEchec --> Degraded: seuil d'échec atteint / événement d'exploitation
    Degraded --> Nominale: synchronisation réussie (COL-07-S2)
```

Seuils décidés le 2026-11-16 (Q-4) [DÉCISION] : 5 tentatives par synchronisation avec attente exponentielle ; `DEGRADED` après 3 synchronisations consécutives en échec. Ce sont les valeurs par défaut de `argos.ingestion.max-attempts` et `argos.ingestion.degraded-threshold`. Le retour de `DEGRADED` à `Nominale` sur succès est une interprétation de ce changement. Dans le code, `Nominale` vaut `NOMINAL` et `En échec` vaut `FAILING`.

## Critères d'acceptation

- Scénarios `COL-01-S1` à `COL-08-S1` de la spec.
- QS-03 (429/5xx sans perte) et QS-04 (rejeu sans duplication logique) du registre `/docs/architecture/quality/scenarios.md`.
- Baseline v2.3 §4 : « avis OSV collecté, normalisé, dédupliqué et historisé avec provenance ».

## Interprétations introduites par un changement

Interprétations validées par le porteur du projet le 2026-11-16 [DÉCISION].

Comportements absents des sources, ajoutés pour rendre les règles testables (changement `formaliser-chaine-securite-poc`) :

- COL-03-S3 : collectes concurrentes du même avis → un seul signal ;
- COL-06-S3 : une erreur permanente n'est pas retentée automatiquement ;
- COL-07-S2 : une synchronisation réussie fait quitter l'état `DEGRADED`.

## Écarts constatés

- Le code de `argos-server` implémente les règles de cette fiche (tests `argos-server/src/test/java/io/argos/chain/CollecteTest.java`, `EnrichmentTest.java`, `ResilienceTest.java`). Toute erreur inattendue pendant une synchronisation (et pas seulement une erreur de source) termine le traitement en échec permanent et fait changer l'état de la source ; un traitement `RUNNING` sans activité depuis `argos.ingestion.running-expiry` (30 minutes) est repris au lancement suivant.
- Le délai imposé par `Retry-After` est borné par le délai maximal des tentatives (`argos.ingestion.backoff-max`, 5 minutes).
- Un avis OSV retiré (champ `withdrawn`) reste historisé comme signal, mais ne déclare plus aucun paquet affecté : il ne produit aucun impact.
- Aucune planification automatique des synchronisations n'est installée (tâche 6.3 du changement) ; la première synchronisation OSV sans curseur énumère tout l'index `modified_id.csv` des écosystèmes configurés, dans la requête d'exploitation, sans borne de volume.

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-COL-1 | Fréquence de synchronisation de chaque source ? | délai source → signal | sécurité / exploitation |
| Q-COL-2 | Qu'est-ce qu'un avis « modifié » : contenu différent ou date de modification de l'avis ? | COL-03 | backend |
| Q-COL-3 | Quels écosystèmes (Maven, npm, PyPI…) le POC doit-il couvrir ? | périmètre COL-01 | Tech Lead |
| Q-4 (changement) | Nombre maximal de tentatives et seuil d'échec ? | répondue le 2026-11-16 : 5 tentatives par synchronisation, avec attente exponentielle ; `DEGRADED` après 3 synchronisations consécutives en échec [DÉCISION] | backend / exploitation |

## Traçabilité

Voir la matrice du changement : [`tasks.md`](/openspec/changes/formaliser-chaine-securite-poc/tasks.md#matrice-de-traçabilité). Les scénarios COL-01-S1 à COL-08-S1 sont vérifiés par `argos-server/src/test/java/io/argos/chain/CollecteTest.java`, `ResilienceTest.java`, `EnrichmentTest.java` et `DocumentParsingTest.java` (contrat sur une réponse OSV réelle enregistrée) ; les sources externes y sont simulées.
