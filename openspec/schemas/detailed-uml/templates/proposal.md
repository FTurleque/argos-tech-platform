# Proposition : <!-- titre du changement -->

<!--
Statuts à appliquer à chaque information factuelle (légende durable : /docs/specifications/README.md#statuts-des-informations) :
  [EXIGENCE]     comportement attendu issu d'une exigence validée ou d'un ADR accepté
  [EXISTANT]     comportement existant vérifié — preuve obligatoire (fichier, test, commande)
  [DÉCISION]     décision de conception prise (ADR accepté ou décision tracée de ce changement)
  [PROPOSITION]  proposition à valider (y compris ADR au statut Proposé)
  [QUESTION]     question ouverte
Correspondance avec le vocabulaire arc42 du dépôt : Observé = [EXISTANT] ; Accepté = [DÉCISION] ;
Décision proposée / Hypothèse à valider = [PROPOSITION] ; Non déterminé = [QUESTION].
Une section sans objet porte « Non applicable — <raison en une ligne> ».
-->

## Why — Pourquoi

<!-- Problème ou opportunité en 2 à 5 phrases. Pourquoi maintenant. Origine de la demande (personne, document, ADR, constat) avec son statut. -->

## What Changes — Changements

<!-- Liste à puces des comportements ajoutés, modifiés ou retirés. Marquer **BREAKING** toute rupture de contrat (API, données, comportement visible). -->

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

<!-- Un chemin kebab-case par comportement durable ; chacun crée specs/<capability-path>/spec.md. Indiquer le préfixe d'identifiant d'exigence retenu. -->
- `<capability-path>` (préfixe `<PRÉFIXE>`) : <!-- ce que couvre la capacité -->

### Modified Capabilities — Capacités modifiées

<!-- Chemin exact sous openspec/specs/, seulement si le comportement contractuel change. Sans aucune capacité : skip_specs: true dans .openspec.yaml. -->
- `<existing-capability-path>` : <!-- exigence(s) modifiée(s), par identifiant -->

## Périmètre

### Inclus

- <!-- éléments couverts -->

### Exclu

- <!-- éléments explicitement hors périmètre, avec la raison -->

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| <!-- ex. Utilisateur technique --> | <!-- action ou bénéfice --> | <!-- [EXIGENCE] / [PROPOSITION] … --> |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| <!-- règle, contrainte ou comportement --> | <!-- [EXIGENCE] … --> | <!-- /docs/…, ADR-XXXX (statut), fichier ou test --> |

## Écarts constatés

<!-- Divergences entre code, tests, specs et documentation. Les décrire sans les résoudre ici. « Aucun écart constaté » si c'est le cas, avec ce qui a été comparé. -->

## Impact

- **Composants** (noms de arc42 §5) : <!-- … -->
- **Contrats d'interface** (OpenAPI, MCP, webhooks, événements) : <!-- … -->
- **Données et persistance** : <!-- … -->
- **Sécurité, confidentialité, droits** : <!-- … -->
- **Exploitation et observabilité** : <!-- … -->
- **Documentation durable à mettre à jour** : <!-- fiches /docs/specifications/…, sections arc42, diagrammes, glossaire -->
- **ADR** : <!-- ADR concernés (avec statut) ; décision candidate à un nouvel ADR -->

## Conception requise

<!-- complète | allégée | non applicable — justification en une ou deux phrases (voir l'instruction design). -->

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | <!-- … --> | <!-- oui : bloque specs/conception/tâches ; non : peut attendre --> | <!-- rôle --> |
