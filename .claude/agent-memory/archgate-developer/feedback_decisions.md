---
name: feedback-decisions
description: Les décisions en attente (ADR, licences, tâches bloquées) doivent être soumises au porteur sous forme de questions à choix, pas seulement listées
metadata:
  type: feedback
---

Quand des décisions attendent le porteur (statut d'ADR, licence, preuve acceptable pour une tâche, écart de comportement), les lui présenter comme des questions à choix (AskUserQuestion, 4 par série, recommandation en premier avec le contexte utile), puis appliquer ses réponses dans un lot et les consigner en [DÉCISION] datée.

**Why:** le porteur a demandé « pour les décisions il faut que tu me les donnes et je décide » : une simple liste de points bloqués ne lui permet pas de trancher.

**How to apply:** dès qu'un lot laisse des tâches bloquées par une décision, poser les questions plutôt que de les énumérer en fin de compte rendu. Voir aussi [[feedback-cycle-lots]].
