# Delta de spécification : alertes-digests

## ADDED Requirements

### Requirement: ALR-09 Évaluation des alertes en un nombre fixe de requêtes
Le système DOIT (MUST) évaluer les règles d'alerte des impacts publiés d'un signal en un nombre de requêtes indépendant du nombre de membres des espaces touchés et de leurs canaux, et DOIT (MUST) laisser la diffusion des alertes créées à la tâche planifiée d'envoi, sans changer les alertes ni les résumés produits (ALR-01 à ALR-08).
Source : AUD-PER-04 ; critère de sortie du sprint 10 ; décision du porteur du projet (2026-11-26, lecture technique filtrée par appartenance). Statut : [PROPOSITION].

#### Scenario: ALR-09-S1 Nombre de requêtes constant
- **GIVEN** deux espaces touchés par un signal, l'un de 2 membres, l'autre de 20 membres, chacun avec des abonnements immédiats et périodiques sur deux canaux
- **WHEN** les règles sont évaluées pour chaque espace
- **THEN** les deux évaluations exécutent le même nombre de requêtes

#### Scenario: ALR-09-S2 Diffusion par la tâche d'envoi
- **GIVEN** une alerte immédiate créée par l'évaluation d'un signal
- **WHEN** l'évaluation se termine
- **THEN** l'alerte est enregistrée en attente d'envoi
- **AND** elle est diffusée au passage suivant de la tâche planifiée d'envoi

#### Scenario: ALR-09-S3 Contenu limité aux espaces du destinataire
- **GIVEN** un signal qui touche un projet d'un espace A et un projet d'un espace B, et un destinataire membre de A seulement
- **WHEN** son alerte est créée
- **THEN** elle ne cite que le projet de l'espace A
