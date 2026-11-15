# Spécifications fonctionnelles ARGOS

Ce dossier contient la **documentation fonctionnelle durable** d'ARGOS : une fiche par capacité, ses diagrammes métier et le suivi de couverture des exigences. Il complète OpenSpec et arc42 sans les dupliquer.

## Répartition des responsabilités

| Support | Contenu | Emplacement |
|---|---|---|
| Specs de référence OpenSpec | exigences normatives **livrées**, scénarios | `/openspec/specs/<capability-path>/spec.md` |
| Changements OpenSpec | deltas d'exigences **proposés**, proposition, conception propre au changement, tâches, traçabilité | `/openspec/changes/<change-id>/` |
| Fiches capacité (ce dossier) | contexte fonctionnel durable : acteurs, règles, droits, diagrammes métier, questions | `/docs/specifications/<capability-path>.md` |
| Architecture consolidée | arc42, vues C4, diagrammes de conception maintenus | `/docs/architecture/` |
| ADR | décisions structurantes et leurs conséquences | `/docs/architecture/adr/` |

Règles :

- une exigence normative n'est écrite qu'une fois, dans OpenSpec ; les fiches la citent par identifiant ;
- la synchronisation ou l'archivage OpenSpec ne met à jour ni les fiches, ni arc42, ni les diagrammes : les tâches de chaque changement le prévoient ;
- un contenu introduit par un changement non archivé porte le statut [PROPOSITION] et l'identifiant du changement ;
- le guide d'utilisation du schéma est [`guide-openspec-detailed-uml.md`](guide-openspec-detailed-uml.md) ; le modèle de fiche est [`_modele-fiche-capacite.md`](_modele-fiche-capacite.md).

## Statuts des informations

| Statut | Signification | Équivalent arc42 |
|---|---|---|
| [EXIGENCE] | comportement attendu issu d'une exigence validée ou d'un ADR accepté | Accepté |
| [EXISTANT] | comportement existant vérifié par une preuve versionnée | Observé |
| [DÉCISION] | décision de conception prise | Accepté |
| [PROPOSITION] | proposition à valider, y compris ADR « Proposé » | Décision proposée / Hypothèse à valider |
| [QUESTION] | question ouverte | Non déterminé |

## Registre des capacités et des préfixes

Un préfixe n'est jamais réattribué ; un identifiant d'exigence retiré n'est jamais réutilisé.

| Capacité | Préfixe | Fiche | Spec de référence | Changement | Statut |
|---|---|---|---|---|---|
| `collecte-avis-securite` | `COL` | [fiche](collecte-avis-securite.md) | absente | `formaliser-chaine-securite-poc` | proposée |
| `inventaire-sbom` | `INV` | [fiche](inventaire-sbom.md) | absente | `formaliser-chaine-securite-poc` | proposée |
| `correlation-impact` | `COR` | [fiche](correlation-impact.md) | absente | `formaliser-chaine-securite-poc` | proposée |
| `abonnements-veille` | `ABO` | [fiche](abonnements-veille.md) | absente | `formaliser-chaine-securite-poc` | proposée |

## Inventaire des diagrammes métier

| ID | Type | Question | Fiche |
|---|---|---|---|
| DF-COL-1 | UML séquence | comment un avis devient-il un signal enrichi, et que se passe-t-il en erreur ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-1--synchronisation-dune-source-de-vulnérabilités) |
| DF-COL-2 | organigramme | quelle décision pour chaque avis reçu ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-2--traitement-dun-avis-reçu) |
| DF-COL-3 | UML états | comment une source passe-t-elle en `DEGRADED` et en sort-elle ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-3--état-de-synchronisation-dune-source) |
| DF-INV-1 | UML séquence | comment une SBOM remplace-t-elle l'inventaire ? | [inventaire-sbom](inventaire-sbom.md#df-inv-1--import-dune-sbom) |
| DF-INV-2 | organigramme | d'où provient l'inventaire et quelle confiance en découle ? | [inventaire-sbom](inventaire-sbom.md#df-inv-2--origine-de-linventaire-dun-projet) |
| DF-COR-1 | UML classes | quels concepts la corrélation relie-t-elle ? | [correlation-impact](correlation-impact.md#df-cor-1--modèle-conceptuel-de-la-corrélation) |
| DF-COR-2 | UML séquence | comment un signal produit-il des impacts, avec ou sans IA ? | [correlation-impact](correlation-impact.md#df-cor-2--corrélation-dun-signal-avec-et-sans-ia) |
| DF-COR-3 | organigramme | quel niveau porte un impact ? | [correlation-impact](correlation-impact.md#df-cor-3--détermination-du-niveau-dun-impact) |
| DF-ABO-1 | UML classes | comment les abonnements se rattachent-ils sans toucher aux droits ? | [abonnements-veille](abonnements-veille.md#df-abo-1--abonnements-équipes-et-autorisations) |
| DF-ABO-2 | UML séquence | dans quel ordre droits et abonnements s'appliquent-ils ? | [abonnements-veille](abonnements-veille.md#df-abo-2--restitution-de-la-veille-dun-utilisateur) |

Les diagrammes d'architecture (D-01 à D-13) sont inventoriés dans [`/docs/architecture/diagrams/README.md`](../architecture/diagrams/README.md) ; les diagrammes de conception d'un changement (DC-*) restent dans son `design.md`.

## Bilan de couverture

État au 2026-11-15.

### Inventaire OpenSpec

| Élément | Nombre | Détail |
|---|---:|---|
| Specs de référence | 0 | aucune capacité livrée ; aucun code applicatif |
| Changements actifs | 1 | `formaliser-chaine-securite-poc` (schéma `detailed-uml`) |
| Changements archivés | 0 | — |

Le dépôt ne contient aucune spec de référence ni aucun changement d'un autre schéma : aucun contenu n'est à préserver ni aucun changement à migrer.

### Couverture du lot 1 (`formaliser-chaine-securite-poc`)

| Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---:|---:|---:|---:|---:|
| `collecte-avis-securite` | 8 | 18 | 3 | 4 | 0 |
| `inventaire-sbom` | 5 | 8 | 2 | 5 | 0 |
| `correlation-impact` | 8 | 14 | 3 | 4 | 0 |
| `abonnements-veille` | 5 | 7 | 2 | 4 | 0 |
| **Total** | **26** | **47** | **10** | **17** | **0** |

Toutes les exigences sont au statut [PROPOSITION] : elles dérivent d'ADR « Proposé » ou de baselines « à valider ». Matrice détaillée : [`tasks.md` du changement](/openspec/changes/formaliser-chaine-securite-poc/tasks.md#matrice-de-traçabilité).

### Capacités restant à spécifier

Sources : baseline v2.3 §4 (matrice architecture → preuve) et arc42 §5.

| Lot | Capacité pressentie | Composant(s) | Sources disponibles | Manques principaux |
|---|---|---|---|---|
| 2 | alerte ciblée et digests | `alerting`, `workspace` | baseline v2.3 §4, §6 critère 1 ; arc42 §6.1 | canaux (question ouverte n° 7 de `/docs/architecture/README.md`), règles d'alerte, statut d'action |
| 2 | contrôle d'accès par espace | `API & Security`, `workspace` | ADR-0008, ADR-0016 ; arc42 §8.1 | IdP, matrice rôles × actions |
| 3 | serveur MCP lecture seule | `mcp-server` | ADR-0014 ; baseline v2.3 §4 (`check_dependency`, `get_security_alerts`) | schémas des outils, authentification IDE |
| 3 | AI Gateway et politique de données | `ai-gateway` | ADR-0004 ; arc42 §8.3, §8.8 ; QS-02, QS-06, QS-15 | classes autorisées (question n° 2), budget |
| 3 | veille réglementaire avec validation MKP | `reg-intel` | ADR-0018 ; baseline v2.3 §3 | workflow MKP, dictionnaire métier, source Légifrance |
| 4 | radar versions / EOL et standards internes | `release-radar`, `catalog` | baseline v2.3 §4 | référentiel EOL, statuts ADOPT/HOLD |
| 4 | veille RSS / Web | `source-ingestion` | ADR-0006 ; arc42 §3 | hébergement FreshRSS / changedetection.io (question n° 3) |
| 4 | Project Intelligence | `scm-connector` | arc42 §6.2 ; baseline v2.3 §2.4 (phase 3) | métriques factuelles, RGPD (R-09) |
| 4 | recherche | — | ADR-0015 ; QS-13 | p95 cible, périmètre |

### Écarts documentaires relevés

| Écart | Traitement |
|---|---|
| Les ADR citent des exigences `F01`–`F16` absentes du dépôt | question Q-1 du changement ; aucune correspondance inventée |
| arc42 §6 annonçait les noms de §5 mais utilise d'autres participants | table de correspondance ajoutée en arc42 §6 ; renommage à décider (tâche 6.1) |
| Inventaire des diagrammes : D-05, D-11, D-12, D-13 annoncés, absents | D-05 ajouté en arc42 §5.7 ; D-11, D-12 et D-13 marqués manquants (tâche 6.2) |
| Glossaire : « impact probable, confirmé, résolu » vs niveaux ADR-0013 | question Q-COR-2 |

## Vérification

```bash
node scripts/verifier-docs.mjs
```

Le script contrôle les liens internes, les blocs Mermaid et les identifiants d'exigences cités ; voir le guide pour le détail et les limites.
