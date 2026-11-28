---
name: feedback-nommage
description: Aucun nom de variable d'une ou deux lettres (c, ps, rs, e, s, v, x, r…) dans le code écrit ou modifié ; des noms qui disent ce qu'ils contiennent
metadata:
  type: feedback
---

Le porteur du projet refuse les noms d'une ou deux lettres : `c`, `ps`, `rs`, `e`, `s`, `v`, `x`, `r`, `k`, `ws`… Il faut un nom qui dit ce que la variable contient : `connexion`, `requete`, `resultat`, `erreur`, `signal`, `abonnement`, `cle`, `espaceProjet`. Les paramètres de lambda et les variables de `catch` sont concernés. `id` reste admis, car il a un sens.

**Why:** « ça veut rien dire » (porteur, 2026-11-20) ; il a refusé de laisser passer les remarques de nommage du superviseur de code sur ce motif, même quand c'était la convention existante du dépôt.

**How to apply:** dans tout code écrit ou modifié (Java, TypeScript, scripts), y compris les tests, et dans les consignes données aux agents des workflows. Traiter aussi les remarques de complexité du superviseur sur le code touché (découper les méthodes). Le reste du dépôt contient encore beaucoup de noms courts : ne pas les invoquer comme convention. Voir [[feedback-cycle-lots]].
