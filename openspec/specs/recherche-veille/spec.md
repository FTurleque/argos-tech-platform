# recherche-veille Specification

## Purpose
Retrouver rapidement les éléments de veille utiles par mots-clés et filtres, et rappeler en un clic ses recherches habituelles. Fiche : [/docs/specifications/recherche-veille.md](/docs/specifications/recherche-veille.md).

## Requirements

### Requirement: RCH-01 Recherche plein texte
Le système DOIT (MUST) renvoyer les éléments autorisés dont le titre, le résumé ou le contenu correspondent aux mots recherchés, du plus pertinent au moins pertinent.
Source : ADR-0015 (Proposé). Statut : [EXIGENCE].

#### Scenario: RCH-01-S1 Mot trouvé
- **WHEN** l'utilisateur recherche « Oracle correctif »
- **THEN** les éléments correspondants sont renvoyés, les plus pertinents en premier

#### Scenario: RCH-01-S2 Aucun résultat
- **WHEN** l'utilisateur recherche un mot absent de tout élément
- **THEN** une liste vide est renvoyée avec un message explicite

### Requirement: RCH-02 Filtres combinables
Le système DOIT (MUST) permettre de combiner à une recherche des filtres par projet, technologie, type, gravité, horizon et profil de veille.
Source : maquette « Fil de veille » ; PRF-02. Statut : [EXIGENCE].

#### Scenario: RCH-02-S1 Filtres combinés
- **WHEN** l'utilisateur filtre sur le projet Paie, la technologie Oracle et l'horizon 30 jours
- **THEN** seuls les éléments satisfaisant les trois filtres sont renvoyés

### Requirement: RCH-03 Résultats limités aux droits
Le système NE DOIT PAS (MUST NOT) renvoyer dans une recherche un élément, un impact ou un extrait que l'utilisateur n'est pas autorisé à voir.
Source : ACC-04 ; risque R-17. Statut : [EXIGENCE].

#### Scenario: RCH-03-S1 Mot présent dans un autre espace
- **GIVEN** un impact de l'espace B contenant le mot « Portail »
- **WHEN** un utilisateur du seul espace A recherche « Portail »
- **THEN** l'impact de l'espace B n'est pas renvoyé

### Requirement: RCH-04 Vues enregistrées
Le système DOIT (MUST) permettre à un utilisateur d'enregistrer une recherche et ses filtres sous un nom, de la rappeler et de la supprimer ; une vue n'est visible que de son auteur.
Source : maquette « Mes vues » ; arc42 §8.2. Statut : [EXIGENCE].

#### Scenario: RCH-04-S1 Vue rappelée
- **GIVEN** une vue « Sécurité de mes projets » enregistrée
- **WHEN** l'utilisateur la rappelle
- **THEN** la recherche et les filtres enregistrés sont appliqués

#### Scenario: RCH-04-S2 Vue d'un autre utilisateur
- **WHEN** un utilisateur demande la vue d'un autre utilisateur
- **THEN** la demande est refusée

### Requirement: RCH-05 Temps de réponse
Le système DOIT (MUST) respecter la cible de temps de réponse de consultation définie par arc42 §10.2 (Q-05) pour une recherche filtrée, hors appel à l'IA.
Source : arc42 §10.2 Q-05 ; QS-13. Statut : [EXIGENCE].

#### Scenario: RCH-05-S1 Mesure de charge
- **GIVEN** un jeu de données au volume cible (Q-2)
- **WHEN** une campagne de recherches filtrées est exécutée
- **THEN** le temps de réponse mesuré respecte la cible de Q-05

### Requirement: RCH-06 Limite de débit de la recherche
Le système DOIT (MUST) limiter par compte et par minute le nombre de recherches plein texte, lecture des résultats d'une vue enregistrée comprise ; au-delà de la limite configurée, il refuse la recherche avec une erreur de limite qui indique quand réessayer, journalise le refus et n'affecte pas les autres comptes.
Source : AUD-SEC-09 (/docs/audit/2026-11-21/findings.md) ; ACC-06 ; MCP-06 (limite existante du serveur MCP) ; valeur : décision du porteur, 2026-11-24. Statut : [PROPOSITION].

#### Scenario: RCH-06-S1 Limite atteinte
- **GIVEN** un compte qui a lancé 60 recherches plein texte ou lectures de résultats de vue dans la dernière minute, avec la limite par défaut
- **WHEN** il lance une recherche plein texte ou lit les résultats d'une de ses vues enregistrées
- **THEN** la demande est refusée avec une erreur de limite de débit et un délai avant nouvel essai
- **AND** le refus est journalisé et aucune recherche n'est exécutée

#### Scenario: RCH-06-S2 Autres comptes non affectés
- **GIVEN** un compte qui a atteint sa limite de recherches
- **WHEN** un autre compte lance une recherche dans la même minute
- **THEN** la recherche de l'autre compte est exécutée normalement

#### Scenario: RCH-06-S3 Fenêtre écoulée
- **GIVEN** un compte refusé pour limite de débit
- **WHEN** il relance une recherche après l'écoulement de la fenêtre d'une minute
- **THEN** la recherche est exécutée normalement
