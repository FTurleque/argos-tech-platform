# Delta de spécification : profil-artiste

## ADDED Requirements

### Requirement: ART-08 Âge maximal d'une opportunité sans date limite
Le système DOIT (MUST) restituer une opportunité sans date limite seulement tant que sa première collecte date de moins que l'âge maximal réglé. Au-delà, elle NE DOIT PAS (MUST NOT) être présentée comme une opportunité ouverte.
Source : /docs/audit/2026-11-21/findings.md, AUD-PER-18 (action) ; ART-03. Statut : [PROPOSITION].

#### Scenario: ART-08-S1 Élément de flux récent
- **GIVEN** un âge maximal de 90 jours et un élément de flux RSS correspondant aux suivis, sans date limite, collecté pour la première fois il y a 10 jours
- **WHEN** l'utilisateur lit ses opportunités
- **THEN** l'élément lui est restitué, signalé « date à confirmer »

#### Scenario: ART-08-S2 Élément de flux trop ancien
- **GIVEN** le même élément collecté pour la première fois il y a 120 jours, toujours présent dans le flux
- **WHEN** l'utilisateur lit ses opportunités
- **THEN** l'élément n'est pas restitué
