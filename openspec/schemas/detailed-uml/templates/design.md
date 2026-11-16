# Conception : <!-- titre du changement -->

<!--
Niveau (repris de la proposition) : complète | allégée | non applicable.
Une section sans objet porte « Non applicable — <raison> » ; ne pas la remplir de généralités.
Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] (légende : /docs/specifications/README.md#statuts-des-informations).
Noms de composants : ceux de /docs/architecture/arc42/05-vue-blocs.md ; termes : glossaire arc42 §12.
Chaque diagramme Mermaid : identifiant DC-<n>, question posée, type exact (UML, C4, entité-association, organigramme),
existant ou cible, exigences concernées, explication sous le diagramme.
-->

**Niveau de conception** : <!-- complète | allégée | non applicable — justification -->

## Contexte technique

<!-- État actuel et contraintes utiles ([EXISTANT] avec preuve, ou « aucune implémentation » le cas échéant). Renvoyer à proposal.md pour la motivation. -->

## Exigences couvertes

| Exigence | Section(s) de conception | Diagramme(s) |
|---|---|---|
| <!-- PRÉFIXE-NN --> | <!-- § --> | <!-- DC-n --> |

## Objectifs et non-objectifs

**Objectifs :**
- <!-- limites propres à la conception -->

**Non-objectifs :**
- <!-- … -->

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| <!-- nom arc42 §5 --> | <!-- … --> | <!-- … --> | <!-- existant / nouveau / modifié --> |

<!-- Vue de composants si les frontières changent : flowchart à stéréotypes C4 («Component», «adapter», «database»). -->

## Modèle de domaine et invariants

<!-- classDiagram si le modèle évolue : classes, attributs significatifs, relations et cardinalités. Lister les invariants testables (INV-1 …). Signaler toute cardinalité non établie. -->

## Traitements et interactions

<!-- sequenceDiagram par interaction significative, avec branches alt/opt pour les erreurs importantes. -->

## États et transitions

<!-- stateDiagram-v2 pour tout cycle de vie introduit ou modifié : états, événements, gardes, état terminal. -->

## Contrats d'interface

<!--
Référencer le contrat exécutable (OpenAPI, schéma MCP, schéma d'événement) plutôt que le recopier.
Pour une API, si applicable :
| Élément | Valeur |
|---|---|
| Opération et chemin | |
| Authentification / autorisations | |
| Champs (requis/facultatifs, types, contraintes) | |
| Exemple de requête / réponse | |
| Erreurs et codes | |
| Pagination, tri, filtrage | |
| Compatibilité et idempotence | |
-->

## Persistance et intégrité

<!-- erDiagram si le schéma change : entités, clés, contraintes d'unicité, intégrité référentielle, migrations. -->

## Transactions, concurrence et idempotence

<!-- Frontières transactionnelles, clés d'idempotence, ordre des événements, verrouillage, rejeu. -->

## Sécurité et isolation

<!-- Authentification, autorisations (RBAC/RLS), classification des données, données envoyées hors du système, secrets, entrées non fiables. -->

## Gestion des erreurs et reprise

<!-- Erreurs transitoires vs permanentes, retry borné, état dégradé, reprise après redémarrage, visibilité des échecs. -->

## Observabilité

<!-- Journaux, métriques, traces, identifiants de corrélation, événements d'exploitation. -->

## Performance et stratégie de mesure

<!-- Cible chiffrée uniquement si elle provient d'une source (citer) ; sinon [QUESTION]. Méthode de mesure. -->

## Compatibilité, migration et retour arrière

<!-- Étapes de déploiement, migration de données, compatibilité des contrats, procédure de retour arrière. -->

## Décisions, alternatives et compromis

### D1 — <!-- décision -->

- **Statut** : <!-- [DÉCISION] | [PROPOSITION] -->
- **Choix** : <!-- … -->
- **Justification** : <!-- … -->
- **Alternatives écartées** : <!-- option — raison -->
- **Compromis** : <!-- … -->
- **ADR** : <!-- ADR existant, candidat à un nouvel ADR (Proposé), ou « non : décision locale » -->

## Risques

- <!-- [Risque] → atténuation ; lien vers /docs/architecture/risks/register.md si le risque y figure -->

## Écarts code / tests / specs / documentation

<!-- Écart, sources comparées, conséquence, traitement proposé. « Aucun écart constaté » sinon. -->

## Stratégie de vérification

| Exigence / scénario | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| <!-- PRÉFIXE-NN-S1 --> | <!-- unitaire, intégration, contrat, E2E, architecture, revue --> | <!-- test prévu, commande, mesure --> | <!-- à écrire / écrit / exécuté --> |

## Documentation durable impactée

- <!-- /docs/specifications/<capability-path>.md, sections arc42, diagrammes (inventaire), glossaire, ADR -->

## Questions ouvertes

<!-- Seulement les inconnues qui ne changent ni les specs, ni l'approche, ni les tâches. Les autres sont posées avant de continuer. -->
