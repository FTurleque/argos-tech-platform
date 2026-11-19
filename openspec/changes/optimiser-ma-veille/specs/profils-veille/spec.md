# Delta de spécification : profils-veille

## ADDED Requirements

### Requirement: PRF-07 Restitution de « Ma veille » dans la cible de latence
Le système DOIT (MUST) restituer la veille d'un utilisateur (`GET /api/v1/me/veille`) avec un p95 inférieur à 500 ms, mesuré par la campagne de performance avec le jeu de données de référence, sans modifier le contenu restitué (éléments, champs, ordre, droits : PRF-02, PRF-05).
Source : décisions K2 et K3 du porteur du projet (2026-11-19) ; arc42 §8, §10.2 Q-05 ; PERF-01. Statut : [PROPOSITION].

#### Scenario: PRF-07-S1 Ma veille dans la cible avec le jeu de référence
- **GIVEN** la pile de test locale chargée du jeu de référence : un espace de 20 projets de 100 composants, 1 000 avis et 20 000 impacts
- **AND** un utilisateur membre de cet espace, au profil actif `developpeur`, avec un abonnement `PERSONAL`
- **WHEN** la campagne de performance appelle `GET /api/v1/me/veille` 200 fois avec 4 clients simultanés, après échauffement
- **THEN** le rapport indique pour cette opération un p95 inférieur à 500 ms

#### Scenario: PRF-07-S2 Contenu restitué inchangé
- **GIVEN** des avis aux dates distinctes, dont un sans date de publication, qui impactent plusieurs projets de l'espace d'un utilisateur
- **AND** un impact écarté et un projet d'un espace dont l'utilisateur n'est pas membre
- **WHEN** l'utilisateur consulte sa veille
- **THEN** les éléments restitués, leurs champs et leur ordre sont ceux de l'union de ses profils actifs (PRF-02), du plus récent au plus ancien
- **AND** aucun impact écarté ni aucun projet d'un espace dont il n'est pas membre n'est restitué
