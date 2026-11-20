# Recette sur le poste installé

Vérifications qui exigent un environnement réel et qu'aucun test automatique ne remplace. Chaque écart constaté ouvre un changement OpenSpec ; chaque réussite est consignée ici, avec la date.

| Réf. | Change d'origine | Vérification | Procédure | Résultat |
|---|---|---|---|---|
| R-1 | `notifier-sur-le-poste` 1.2 | Bulle Windows réelle (NOT-01-S1) et clic sur la bulle (NOT-02-S1) | Connecté au client ARGOS, émettre une alerte `DESKTOP` ; voir la bulle, cliquer : la fenêtre passe au premier plan sur l'alerte | à faire |
| R-2 | `notifier-sur-le-poste` 1.2 | Notifications refusées dans les réglages Windows (NOT-01-S2) | Réglages Windows › Notifications › désactiver ARGOS ; émettre une alerte : compteur et alerte visibles, aucune bulle, aucune erreur | à faire |
| R-3 | `exposer-serveur-mcp` 5.2 | Claude Code configuré par `.mcp.json`, appel de `check_dependency` sur une version vulnérable de test | Procédure de la fiche [`serveur-mcp`](../specifications/serveur-mcp.md#procédure-manuelle-avec-claude-code) ; la réponse cite et source l'avis | à faire |
| R-4 | `formaliser-chaine-securite-poc` 3.2 | Récupération des SBOM et manifestes sur un dépôt pilote réel (INV-01, INV-04) | Connecter un dépôt GitLab ou GitHub pilote ; capturer la réponse réelle du connecteur | à faire |
| R-5 | `formaliser-chaine-securite-poc` 4.7 et 7.1 | Avis critique de test → signal → impact sur les deux projets pilotes ; précision et rappel mesurés sur leurs inventaires | Importer les SBOM des deux pilotes, injecter l'avis de test, relever signaux et impacts, annoter les faux positifs et négatifs ; rapport dans `docs/architecture/quality/` | à faire |
