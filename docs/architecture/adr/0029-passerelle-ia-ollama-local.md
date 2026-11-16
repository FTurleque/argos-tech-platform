# ADR-0029 — Servir la passerelle IA par un modèle local Ollama

- **Statut** : Proposé
- **Date** : 2026-11-16
- **Décideurs** : porteur du projet (questions Q-1 à Q-4 du changement `encadrer-appels-ia`, répondues le 2026-11-16) ; remplacement d'ADR-0004 soumis à validation humaine
- **Remplace** : [ADR-0004](0004-ai-gateway-claude.md) (Proposé), à l'acceptation du présent ADR
- **Remplacé par** : —

## Contexte

- [ADR-0004](0004-ai-gateway-claude.md) (Proposé) encapsule Claude, fournisseur payant, derrière une passerelle IA. Argos est un produit ouvert porté à titre personnel ([ADR-0025](0025-produit-ouvert-projet-personnel.md), Accepté) et hébergé sur le Synology du porteur ([ADR-0026](0026-hebergement-synology.md), Accepté) : tout budget d'API est personnel.
- Le porteur a tranché le 2026-11-16 (changement [`encadrer-appels-ia`](/openspec/changes/encadrer-appels-ia/proposal.md)) : coût nul avec le fournisseur local Ollama (MIT) et un plafond de charge de 1 000 appels par jour pour l'instance (Q-1) ; 100 appels par jour et par espace (Q-2) ; modèle par défaut `qwen2.5:1.5b` (Apache-2.0), remplaçable par tâche (Q-3) ; seules les données publiques et internes sortent vers le modèle, jamais de secrets, de jetons ni de données personnelles (Q-4).
- [ADR-0021](0021-double-licence.md) impose des composants gratuits sous licence permissive.
- La passerelle elle-même (point d'appel unique, prompts versionnés, classes de données, quotas, validation des sorties, traçabilité) reste la décision d'ADR-0004 ; seul le fournisseur change.

## Critères de décision

1. Coût d'exploitation nul et aucune clé d'API à gérer.
2. Licences permissives seulement (ADR-0021).
3. Aucune donnée ne quitte l'infrastructure du porteur (R-01, QS-09).
4. Fonctionnement intégral d'Argos sans IA (ALR-05, COR-05, QS-10).
5. Tests automatisés sans modèle réel ni requête réseau externe.

## Options considérées

### Option A — Claude par l'API Anthropic (ADR-0004)

- avantages : qualité de résumé et d'extraction élevée ; sorties structurées fiables ; aucune charge sur le NAS ;
- inconvénients : coût à l'usage sur un budget personnel ; clé d'API à protéger et à faire tourner ; données envoyées à un tiers ; dépendance réseau.

### Option B — Modèle local servi par Ollama

- avantages : gratuit (Ollama sous MIT, modèles Qwen 2.5 sous Apache-2.0) ; données traitées sur le NAS ; API HTTP locale simple (`POST /api/chat`) avec sortie JSON contrainte par un schéma ; facile à simuler dans les tests ;
- inconvénients : qualité nettement inférieure des petits modèles (résumés plus plats, consignes parfois ignorées, sorties hors schéma plus fréquentes) ; charge mémoire et processeur sur le DS1522+ (processeur AMD Ryzen R1600 à deux cœurs, sans GPU ; mémoire partagée avec PostgreSQL, Keycloak et Argos) ; latence de plusieurs secondes à quelques dizaines de secondes par appel ; modèles de plusieurs gigaoctets à télécharger et à stocker.

### Option C — Aucune IA

- avantages : aucune charge ni aucun risque de fuite ;
- inconvénients : perte des résumés et des suggestions ; reste le mode dégradé obligatoire de toute façon.

## Décision

**Proposition : option B.** La passerelle `ai-gateway` appelle un serveur Ollama local (`argos.ia.ollama.url`, par défaut `http://localhost:11434`, variable `ARGOS_OLLAMA_URL`) avec le modèle `qwen2.5:1.5b` par défaut, remplaçable par tâche (`argos.ia.modeles.<tâche>`). Le service Ollama est facultatif : il figure dans `compose.yaml` sous le profil `ia`, désactivé par défaut. Le plafond journalier de l'instance et le quota journalier par espace remplacent le plafond de dépense ; le coût tracé de chaque appel est nul. Le contrat interne de la passerelle reste indépendant du fournisseur (R-08) : revenir à un fournisseur distant ne demanderait qu'un autre adaptateur.

Le statut et le texte d'ADR-0004 restent inchangés jusqu'à l'acceptation du présent ADR.

## Conséquences positives

- aucun coût ni aucune clé d'API ; aucune donnée envoyée hors du NAS ;
- tests reproductibles avec l'API Ollama simulée par un serveur HTTP local ;
- l'IA reste facultative : sans Ollama joignable, chaque demande est refusée comme indisponible et Argos poursuit en mode déterministe.

## Conséquences négatives

- résumés de qualité moindre : la mention « produit par l'IA, à vérifier » et la validation par schéma deviennent essentielles ;
- environ 2 Gio de mémoire occupés pendant qu'un modèle de 1,5 milliard de paramètres est chargé (borne `ARGOS_OLLAMA_MEM_LIMIT`, 3 Gio par défaut) ; un modèle plus gros dépasse vite la mémoire du NAS de base ;
- appels lents : jamais sur le chemin critique (alertes au plus 10 s d'attente, résumé de veille à la demande) ;
- une seule requête traitée à la fois (`OLLAMA_NUM_PARALLEL=1`) : les appels simultanés s'attendent.

## Conséquences neutres / compromis

- le choix du modèle par tâche reste paramétrable, sans changement de code ;
- les prompts restent versionnés : un changement de modèle se mesure sur le même jeu de référence.

## Méthode de validation

- tests d'intégration de la passerelle avec l'API Ollama simulée (`PasserelleIaTest`, `ParcoursIaTest`) ;
- mesure sur le DS1522+ de la mémoire et de la latence de `qwen2.5:1.5b` sur un jeu de référence d'avis et d'actualités (à réaliser) ;
- évaluation humaine de la qualité des résumés sur ce jeu (à réaliser).

## Traçabilité

- **Exigences** : IAG-01 à IAG-05 ([fiche `passerelle-ia`](../../specifications/passerelle-ia.md))
- **Scénarios qualité** : QS-08, QS-09, QS-10, QS-15
- **Diagrammes** : DC-1 du changement `encadrer-appels-ia` ; DF-IAG-1
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-server/src/main/java/io/argos/aigateway/`, `compose.yaml` (service `ollama`)

## Notes

ADR-0004 reste la référence tant que le présent ADR n'est pas accepté ; l'acceptation le marquera **Remplacé**.
