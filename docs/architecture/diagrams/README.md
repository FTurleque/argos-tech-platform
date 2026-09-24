# Inventaire des diagrammes ARGOS

Tous les diagrammes d’architecture sont écrits en **Mermaid** et versionnés directement dans les fichiers Markdown qui les expliquent. Ce dossier sert d’index ; aucune image binaire n’est la source de vérité.

## Convention visuelle

Les vues utilisent des stéréotypes explicites dans les libellés :

- `«Person»`
- `«Software System»`
- `«Container»`
- `«Component»`
- `«interface»`
- `«adapter»`
- `«node»`
- `«database»`

Chaque relation doit porter une action et le protocole lorsqu’il est architecturalement significatif.

## Inventaire

| ID | Type | Portée | Source |
|---|---|---|---|
| D-01 | C4 Context | ARGOS et systèmes externes | `arc42/03-contexte-perimetre.md` |
| D-02 | UML-style composants | style ports/adapters | `arc42/04-strategie-solution.md` |
| D-03 | C4 Container | ARGOS | `arc42/05-vue-blocs.md` |
| D-04 | C4 Component | ARGOS Backend | `arc42/05-vue-blocs.md` |
| D-05 | UML classDiagram | modèle métier conceptuel | `arc42/05-vue-blocs.md` |
| D-06 | UML sequenceDiagram | signal techno → impact projet | `arc42/06-vue-execution.md` |
| D-07 | UML sequenceDiagram | webhook projet | `arc42/06-vue-execution.md` |
| D-08 | UML sequenceDiagram | erreur API/retry | `arc42/06-vue-execution.md` |
| D-09 | UML sequenceDiagram | redémarrage/reprise | `arc42/06-vue-execution.md` |
| D-10 | UML-style deployment | POC/MVP | `arc42/07-vue-deploiement.md` |
| D-11 | UML-style deployment | cible entreprise | `arc42/07-vue-deploiement.md` |
| D-12 | UML-style trust boundaries | sécurité | `arc42/08-concepts-transverses.md` |
| D-13 | Mermaid erDiagram | modèle physique conceptuel | `arc42/08-concepts-transverses.md` |

## Diagrammes à ajouter lorsque l’implémentation existe

- C4 Component focalisé `Ingestion` si le module devient complexe ;
- C4 Component focalisé `AI Gateway` ;
- `stateDiagram-v2` du cycle de vie `IntelligenceItem` ;
- `stateDiagram-v2` du cycle de vie `ProjectImpact` ;
- séquence authentification/autorisation OIDC ;
- pipeline CI/CD réel ;
- déploiement réel issu des manifests IaC ;
- niveau C4 Code uniquement pour des zones critiques justifiées.

## Validation CI recommandée

1. extraire tous les blocs `mermaid` ;
2. les rendre avec Mermaid CLI ;
3. échouer le pipeline sur erreur de syntaxe ;
4. vérifier les liens Markdown ;
5. interdire `.puml` si Mermaid reste la règle du projet ;
6. ne pas exiger de couleur pour comprendre un diagramme.

## Preuve

La conformité de cet inventaire doit être contrôlée lors de chaque PR modifiant `docs/architecture/`.
