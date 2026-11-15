# Delta de spécification : passerelle-ia

## Purpose

Faire de chaque appel à un modèle d'IA un appel tracé, borné en coût, limité aux données autorisées et dont la sortie est vérifiée et signalée. Fiche : [/docs/specifications/passerelle-ia.md](/docs/specifications/passerelle-ia.md).

## ADDED Requirements

### Requirement: IAG-01 Appel tracé par la passerelle
Le système DOIT (MUST) faire passer tout appel à un modèle d'IA par la passerelle, qui enregistre le modèle, la version du prompt, les jetons, le coût, l'empreinte des entrées et le résultat de validation.
Source : ADR-0004 (Proposé) ; arc42 §8.8. Statut : [PROPOSITION].

#### Scenario: IAG-01-S1 Résumé d'une alerte
- **WHEN** le module d'alerte demande le résumé d'un avis public
- **THEN** l'appel est enregistré avec le modèle, la version du prompt, les jetons, le coût et l'empreinte des entrées

### Requirement: IAG-02 Classes de données autorisées
Le système NE DOIT PAS (MUST NOT) transmettre à un modèle externe une donnée classée CONFIDENTIAL ou RESTRICTED ; seules les classes PUBLIC et INTERNAL sont admises.
Source : arc42 §10.2 Q-02 ; QS-09 ; risque R-01. Statut : [PROPOSITION].

#### Scenario: IAG-02-S1 Donnée confidentielle
- **WHEN** une demande de résumé contient une donnée classée CONFIDENTIAL
- **THEN** la passerelle refuse l'appel
- **AND** aucune donnée n'est envoyée au fournisseur et le refus est journalisé

#### Scenario: IAG-02-S2 Donnée publique
- **WHEN** une demande de résumé ne contient que des données PUBLIC
- **THEN** l'appel est transmis au fournisseur

### Requirement: IAG-03 Plafond et quotas
Le système DOIT (MUST) refuser tout appel au-delà du plafond mensuel global ou du quota de l'espace, le traitement demandeur se poursuivant sans résultat IA.
Source : arc42 §10.2 Q-06 ; QS-08 ; baseline v2.3 §6. Statut : [PROPOSITION].

#### Scenario: IAG-03-S1 Quota d'espace épuisé
- **GIVEN** un espace dont le quota mensuel est épuisé
- **WHEN** un résumé est demandé pour cet espace
- **THEN** l'appel est refusé pour dépassement de quota
- **AND** l'élément est diffusé sans résumé

### Requirement: IAG-04 Sortie validée par schéma
Le système DOIT (MUST) valider chaque sortie de modèle contre le schéma de la tâche et NE DOIT PAS (MUST NOT) diffuser une sortie non conforme.
Source : ADR-0004 (Proposé) ; QS-15. Statut : [PROPOSITION].

#### Scenario: IAG-04-S1 Sortie non conforme
- **WHEN** le modèle renvoie une fiche sans le champ obligatoire « date d'effet »
- **THEN** la sortie est rejetée et l'erreur journalisée
- **AND** aucun résumé n'est diffusé pour cet élément

### Requirement: IAG-05 Mention du contenu produit par l'IA
Le système DOIT (MUST) signaler comme produit par l'IA tout contenu issu d'un modèle et l'accompagner des sources sur lesquelles il s'appuie.
Source : ADR-0004 (Proposé) ; AFF-02. Statut : [PROPOSITION].

#### Scenario: IAG-05-S1 Résumé affiché
- **WHEN** un résumé produit par l'IA est affiché dans une mise en page ou renvoyé par MCP
- **THEN** il porte la mention « produit par l'IA » et la liste de ses sources
