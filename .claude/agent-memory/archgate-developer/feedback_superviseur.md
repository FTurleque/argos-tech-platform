---
name: feedback-superviseur
description: Corriger les remontées du superviseur de code (complexité, nommage, captures, null JS, montants) plutôt que les justifier ; choix arrêtés par le porteur
metadata:
  type: feedback
---

Le porteur veut que les remontées du superviseur de code soient corrigées, pas seulement expliquées. Ses choix (2026-11-20) :
- complexité et nommage : toujours corrigés (méthodes découpées, records pour les paramètres liés, noms explicites) ;
- captures d'exception : types précis, ceux que le code appelé lève vraiment ; une erreur imprévue n'est jamais absorbée. Aux frontières d'erreur (arc42 §8, ADR-0039), une capture de `RuntimeException` reste admise : dans une boucle d'unités indépendantes, journaliser, retenir puis relancer en fin de passe ; dans un traitement de fond sans appelant, journaliser avec la cause et compter l'échec (confirmé par le porteur le 2026-11-27) ;
- erreurs avalées : la cause est toujours journalisée ;
- JavaScript : pas de `return null` interne ; rendre une collection vide que l'appelant parcourt, un objet explicite (`{ trouvee: false }`) ou `undefined` ; le contrat IPC vers l'IHM garde `null`, converti au point d'envoi ;
- montants (coût IA) : `BigDecimal`, jamais `double` ;
- nombres en dur : constantes nommées ; lignes ≤ 140 caractères.

**Why:** « corrige moi les remontées du superviseur ! » ; il a refusé les justifications de convention pour les noms courts et choisi l'option stricte sur les captures, les null et les montants.

**How to apply:** corriger, revalider, commiter et pousser ; ne jamais se contenter de justifier une remontée sur du code écrit ou touché. S'applique dans ce code, et dans les consignes des agents. Restent légitimement laissées : une méthode d'API externe imposée (ex. `handle` d'Electron dans une doublure) et une factorisation qui violerait `ModuleBoundariesTest` — les dire explicitement. Voir [[feedback-nommage]].
