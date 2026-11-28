---
name: project-ordre-archivage
description: automatiser-chaine-veille ne s'archive qu'après formaliser-chaine-securite-poc (décision du porteur, 2026-11-19)
metadata:
  type: project
---

Le changement OpenSpec `automatiser-chaine-veille` (lot 18, toutes tâches cochées) reste actif jusqu'à l'archivage de `formaliser-chaine-securite-poc`, puis s'archive juste après.

**Why:** ses deltas ajoutent des exigences à quatre capacités (collecte-avis-securite, correlation-impact, inventaire-sbom, abonnements-veille) dont les exigences de base ne sont que dans `formaliser-chaine-securite-poc`, ouvert en attendant les projets pilotes. Le porteur veut que les specs principales se construisent dans l'ordre : base, puis ajouts.

**How to apply:** ne pas proposer d'archiver `automatiser-chaine-veille` seul. À l'archivage du POC, enchaîner les deux. Consigner cette décision en [DÉCISION] datée dans `tasks.md` du changement au prochain lot qui touche la documentation. Voir [[feedback-cycle-lots]].
