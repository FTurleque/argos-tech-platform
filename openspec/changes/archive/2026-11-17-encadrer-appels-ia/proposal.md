# Proposition : encadrer tous les appels aux modèles d'IA par une passerelle unique

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Argos utilise Claude pour résumer et expliquer, jamais pour décider [PROPOSITION — ADR-0004, ADR-0013 (Proposés)]. Les risques de fuite de données (R-01), de coûts non maîtrisés (R-05) et de verrouillage fournisseur (R-08) imposent un point d'appel unique [PROPOSITION — `/docs/architecture/adr/0004-ai-gateway-claude.md` ; arc42 §8.8 ; QS-08, QS-09, QS-15]. Pour un produit ouvert hébergé par son porteur (ADR-0025, ADR-0026), le budget API est personnel : le plafond devient critique.

## What Changes — Changements

- Tout appel à un modèle passe par la passerelle et y est tracé.
- Seules les données classées PUBLIC ou INTERNAL peuvent être envoyées.
- Plafond mensuel global et quota par espace, avec arrêt propre sans IA au-delà.
- Sorties structurées validées par schéma, rejetées sinon.
- Tout contenu produit par l'IA est signalé comme tel, avec ses sources.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `passerelle-ia` (préfixe `IAG`) : point d'appel unique aux modèles, classes de données, plafond et quotas, sorties validées, traçabilité et mention IA.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Passerelle, registre de prompts versionnés, comptage des coûts, validation des sorties.

### Exclu

- Les embeddings et la recherche sémantique (ADR-0015 : différés).
- Le choix définitif des modèles par tâche (question Q-3).

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Modules métier (`reg-intel`, `impact-engine`, `alerting`) | demandent résumés et fiches | [PROPOSITION] — arc42 §5.5 règle 3 |
| Porteur du projet | fixe le plafond et suit les coûts | [PROPOSITION] — ADR-0025 (Accepté) |
| Fournisseur de modèles (Anthropic) | exécute les requêtes | [PROPOSITION] — ADR-0004 (Proposé) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| AI Gateway unique | [PROPOSITION] | `/docs/architecture/adr/0004-ai-gateway-claude.md` (Proposé) ; arc42 §5.5 règle 3 |
| Classes de données et confidentialité | [PROPOSITION] | arc42 §10.2 Q-02 ; QS-09 ; risque R-01 |
| Maîtrise des coûts | [PROPOSITION] | arc42 §10.2 Q-06 ; QS-08 ; risque R-05 |
| IA jamais décisionnaire | [PROPOSITION] | ADR-0013 (Proposé) ; exigence COR-04 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- Les classes de données transmissibles à Claude restent une question ouverte de `/docs/architecture/README.md` (question 2).

## Impact

- **Composants** (arc42 §5) : `ai-gateway`, `audit`, modules appelants
- **Contrats d'interface** : contrat interne de la passerelle (tâche, entrées classées, schéma de sortie)
- **Données et persistance** : appels tracés, prompts versionnés, compteurs de coût
- **Sécurité, confidentialité, droits** : classification des données, secret de l'API, aucune donnée confidentielle sortante
- **Exploitation et observabilité** : tableau des coûts, alerte de plafond
- **Documentation durable à mettre à jour** : `/docs/specifications/passerelle-ia.md`, registre `/docs/specifications/README.md`, arc42 §8.8
- **ADR** : ADR-0004 (Proposé) reste la référence

## Conception requise

Complète : composant transverse, dépendance externe payante, enjeux de confidentialité et de coût.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Quel plafond mensuel de dépense API ? | **Répondue le 2026-11-16** : 0 € : fournisseur local Ollama (MIT), sans API payante ; plafond de charge de 1 000 appels par jour pour toute l'instance, paramétrable [DÉCISION] | porteur du projet |
| Q-2 | Quel quota par espace pour un produit ouvert ? | **Répondue le 2026-11-16** : 100 appels par jour et par espace, paramétrable [DÉCISION] | porteur du projet |
| Q-3 | Quels modèles par tâche (classement, résumé, analyse) ? | **Répondue le 2026-11-16** : modèle par défaut `qwen2.5:1.5b` (Apache-2.0) pour toutes les tâches, remplaçable par tâche dans la configuration [DÉCISION] | porteur du projet |
| Q-4 | Quelles classes de données peuvent sortir (question 2 de `/docs/architecture/README.md`) ? | **Répondue le 2026-11-16** : données publiques et internes (avis, métadonnées de composants, textes de veille) ; jamais de secrets, de jetons ni de données personnelles [DÉCISION] | porteur du projet / DPO |
