# Delta de spécification : alertes-digests

## ADDED Requirements

### Requirement: ALR-08 E-mail seulement vers une adresse vérifiée
Le système DOIT (MUST) n'envoyer d'alerte, de résumé ou de rappel par e-mail qu'à l'adresse que le fournisseur d'identité déclare vérifiée pour le compte, tenue à jour à chaque connexion ; sans adresse vérifiée connue, il NE DOIT PAS (MUST NOT) envoyer d'e-mail et laisse l'élément visible dans Argos et sur le poste.
Source : AUD-SEC-04 (/docs/audit/2026-11-21/findings.md) ; ALR-06 ; ADR-0027 (Accepté) ; comptes existants : décision du porteur, 2026-11-24. Statut : [PROPOSITION].

#### Scenario: ALR-08-S1 Adresse non vérifiée
- **GIVEN** un compte dont le jeton porte une adresse e-mail que le fournisseur ne déclare pas vérifiée
- **AND** une règle d'alerte de ce compte au canal e-mail
- **WHEN** une alerte, un résumé ou un rappel réglementaire doit lui être diffusé
- **THEN** aucun e-mail n'est envoyé, ni à cette adresse ni à une autre
- **AND** l'élément reste visible dans Argos et sur le client desktop

#### Scenario: ALR-08-S2 Adresse vérifiée
- **GIVEN** un compte dont l'adresse e-mail est déclarée vérifiée par le fournisseur
- **WHEN** une alerte au canal e-mail lui est diffusée
- **THEN** l'e-mail est envoyé à cette adresse vérifiée

#### Scenario: ALR-08-S3 Adresse vérifiée modifiée chez le fournisseur
- **GIVEN** un compte existant dont l'adresse vérifiée a changé chez le fournisseur
- **WHEN** l'utilisateur se reconnecte puis qu'une alerte au canal e-mail lui est diffusée
- **THEN** l'e-mail est envoyé à la nouvelle adresse vérifiée
- **AND** plus aucun e-mail ne part vers l'ancienne adresse

#### Scenario: ALR-08-S4 Compte existant avant la mise en place de la règle
- **GIVEN** un compte créé avant la mise en place de la règle, dont une adresse e-mail est enregistrée
- **WHEN** la mise à jour est déployée puis qu'une alerte au canal e-mail doit lui être diffusée avant sa prochaine connexion
- **THEN** aucun e-mail n'est envoyé
- **AND** après sa reconnexion avec un jeton qui déclare son adresse vérifiée, les e-mails suivants partent à cette adresse
