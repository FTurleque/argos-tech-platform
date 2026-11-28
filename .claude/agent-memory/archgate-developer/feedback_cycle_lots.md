---
name: feedback-cycle-lots
description: Cycle des lots OpenSpec ARGOS — pousser et ouvrir la PR sans redemander une fois le lot revérifié, puis enchaîner le lot suivant dès la PR ouverte
metadata:
  type: feedback
---

Une fois un lot revérifié (suites complètes, e2e, licences), pousser la branche d'intégration et ouvrir la PR sans redemander ; dès la PR ouverte, lancer le lot suivant en parallèle. La fusion reste soumise à l'accord explicite de l'utilisateur.

**Why:** l'utilisateur veut un flux continu de lots sans attendre une validation intermédiaire pour le push et la PR.

**How to apply:** ne pas poser la question « pousser ? » après la revérification ; suivre le gate SonarCloud par Auto-fix pendant que le lot suivant tourne ; demander seulement pour la fusion. Contraintes inchangées : gratuit, licences permissives (voir [[cycle-lots-argos]]).
