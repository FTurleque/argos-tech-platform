# Fiche capacité : `veille-reglementaire`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `REG` |
| Spec de référence | `/openspec/specs/veille-reglementaire/spec.md` — absente tant qu'aucun changement n'est archivé |
| Changements en cours | [`suivre-veille-reglementaire`](/openspec/changes/suivre-veille-reglementaire/proposal.md) (proposé) |
| Statut de la capacité | proposée |
| Composants (arc42 §5) | `reg-intel`, `source-ingestion`, `signal`, `catalog`, `ai-gateway` |

## Objectif

[PROPOSITION — `suivre-veille-reglementaire`] Donner aux experts métier un fil des textes officiels qui touchent leurs domaines, avec une fiche fiable et un calendrier, sans jamais diffuser une analyse non validée.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Expert métier | valide ou corrige les fiches, qualifie l'impact | [PROPOSITION] — ADR-0018 (Proposé) |
| Utilisateur d'un profil couvrant le domaine | consulte les fiches validées | [PROPOSITION] — PRF-06 |
| API Légifrance (portail PISTE) | fournit les textes | [QUESTION] — Q-1 |

## Périmètre et exclusions

Inclus :

- Textes du Journal officiel et codes consolidés suivis.

Exclus :

- Les autres sources réglementaires (normes déclaratives, sites ministériels) : pages surveillées par `veille-rss-web`.
- Tout avis juridique.

## Préconditions et déclencheurs

Profil `expert-metier` actif ; domaines et dictionnaire métier configurés dans l'espace.

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Texte officiel | identifiant, nature, NOR, dates, articles | enregistré une seule fois (REG-01) | [PROPOSITION] |
| Fiche | résumé, articles, date d'effet, domaines, liens | validée avant diffusion (REG-05) | [PROPOSITION] |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Collecte quotidienne sans doublon | REG-01 | ADR-0018 (Proposé)  | [PROPOSITION] |
| Qualification par domaine | REG-02 | ADR-0018 (Proposé)  | [PROPOSITION] |
| Dates de publication et d'entrée en vigueur | REG-03 | ADR-0018 (Proposé) | [PROPOSITION] |
| Fiche d'analyse préparée | REG-04 | ADR-0018 (Proposé)  | [PROPOSITION] |
| Validation par l'expert avant diffusion | REG-05 | ADR-0018 (Proposé)  | [PROPOSITION] |
| Suivi jusqu'à la livraison | REG-06 | ADR-0018 (Proposé) | [PROPOSITION] |
| Calendrier et rappels | REG-07 | ADR-0018 (Proposé) | [PROPOSITION] |
| Lien vers un outil de suivi | REG-08 | ADR-0018 (Proposé) | [PROPOSITION] |

## Droits d'accès et restrictions

Validation réservée aux experts du domaine ; fiches visibles des autres utilisateurs du domaine après validation.

## Effets de bord observables

Textes et fiches enregistrés ; rappels envoyés ; historique des corrections.

## Qualités mesurables

[QUESTION] Aucune cible chiffrée dans les sources ; voir les questions ouvertes.

## Diagrammes

### DF-REG-1 — Parcours d'un texte officiel

- **Question** : quelles étapes un texte suit-il avant d'être diffusé ?
- **Type** : organigramme
- **Portée** : cible proposée
- **Exigences** : REG-02, REG-04, REG-05

```mermaid
flowchart LR
    A["Texte publié<br/>au Journal officiel"] --> B["Rattachement aux domaines<br/>règles, puis IA si doute"]
    B --> C["Fiche préparée<br/>marquée IA"]
    C --> D{"Validée par<br/>l'expert ?"}
    D -- "non" --> C
    D -- "oui" --> E["Fiche diffusée<br/>calendrier et rappels"]
```

Rien n'est diffusé au-delà des experts tant que la fiche n'est pas validée.

## Critères d'acceptation

Scénarios REG-01-S1 à REG-08-S1 du changement ; aucun scénario qualité transverse supplémentaire.

## Interprétations introduites par un changement

- Domaines et dictionnaire métier propres à chaque espace : déduit d'ADR-0025, à faire valider.

## Écarts constatés

- ADR-0018 nomme les MKP et les modules RH d'un éditeur ; depuis ADR-0025, le rôle s'appelle « expert métier » et les domaines sont propres à chaque espace.

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-1 | Les conditions d'usage de l'API Légifrance autorisent-elles un produit diffusé, éventuellement commercial ? | répondue le 2026-11-17 : source principale : jeux de données LEGI et JORF de la DILA, publiés sur data.gouv.fr sous Licence Ouverte / Open Licence (réutilisation gratuite, commerciale comprise, avec mention de la source : https://www.data.gouv.fr/datasets/legi-codes-lois-et-reglements-consolides) ; l'API PISTE de Légifrance n'est pas utilisée tant que ses conditions d'usage ne sont pas vérifiées [DÉCISION] | porteur du projet |
| Q-2 | À quelles échéances envoyer les rappels avant une entrée en vigueur ? | répondue le 2026-11-17 : J-90, J-30 et J-7 avant l'entrée en vigueur, paramétrables [DÉCISION] | Product Owner |
| Q-3 | Quel outil de suivi externe relier (Jira, GitHub, GitLab) ? | répondue le 2026-11-17 : GitHub et GitLab, par les adapters `scm` existants ; pas de Jira [DÉCISION] | porteur du projet |
| Q-4 | Faut-il fournir des dictionnaires métier types à la création d'un espace ? | répondue le 2026-11-17 : non pour l'instant : chaque espace construit son dictionnaire [DÉCISION] | Product Owner |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| REG-01 | REG-01-S1, REG-01-S2 | DC-1 | 1.1, 2.1 | à écrire |
| REG-02 | REG-02-S1, REG-02-S2 | DC-1 | 2.2, 2.3 | à écrire |
| REG-03 | REG-03-S1, REG-03-S2 | DC-1 | 2.4 | à écrire |
| REG-04 | REG-04-S1 | DC-1 | 3.1 | à écrire |
| REG-05 | REG-05-S1, REG-05-S2 | DC-1, DC-2 | 3.2 | à écrire |
| REG-06 | REG-06-S1, REG-06-S2 | DC-2 | 3.3 | à écrire |
| REG-07 | REG-07-S1 | DC-1 | 3.4 | à écrire |
| REG-08 | REG-08-S1 | DC-2 | 3.4 | à écrire |
