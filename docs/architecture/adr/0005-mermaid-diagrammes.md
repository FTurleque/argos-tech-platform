# ADR-0005 — Utiliser Mermaid comme source des diagrammes d’architecture

- **Statut** : Accepté
- **Date** : 2026-09-24
- **Décideurs** : cadrage du projet
- **Remplace** : —

## Contexte

La documentation doit être versionnée avec le code, diffable et modifiable sans source binaire. Le cadrage impose Mermaid et interdit les diagrammes ASCII ou les images binaires comme source.

## Critères

- source textuelle ;
- rendu natif GitHub ;
- versionnement Git ;
- compatibilité avec C4/UML-style ;
- automatisation CI possible.

## Options

### A — Mermaid

+ imposé par le cadrage ;
+ rendu GitHub ;
+ syntaxe textuelle ;
- certaines notations UML/C4 doivent être représentées via `flowchart` et stéréotypes.

### B — PlantUML

Rejeté : ne respecte pas la contrainte explicite du projet.

### C — diagrams.net / images binaires

Rejeté comme source principale : moins diffable et contraire à la règle documentaire.

## Décision

Utiliser **Mermaid** comme source unique des diagrammes. Les vues C4 sont représentées avec des éléments clairement stéréotypés (`«Person»`, `«Software System»`, `«Container»`, `«Component»`, `«interface»`, `«adapter»`, `«node»`, `«database»`).

## Conséquences positives

- documentation-as-code ;
- revue des diagrammes dans les PR ;
- pas de dépendance à un éditeur graphique.

## Conséquences négatives

- rendu dépendant du moteur Mermaid ;
- validation syntaxique CI nécessaire.

## Conséquences neutres

Des SVG/PNG peuvent être générés pour publication, mais ne deviennent pas la source de vérité.

## Validation

- rendre chaque bloc Mermaid dans GitHub ;
- ajouter un lint/render Mermaid en CI ;
- vérifier absence de sources PlantUML/diagrammes binaires non justifiés.

## Traçabilité

- `README.md`
- `docs/architecture/diagrams/README.md`
- toutes les sections arc42 contenant des diagrammes
