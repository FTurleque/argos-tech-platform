# Delta de spécification : comptes-acces

## ADDED Requirements

### Requirement: ACC-14 Écoute de bouclage fermée sur le seul state attendu
L'écoute de bouclage du client desktop DOIT (MUST) comparer le state de chaque retour reçu à celui de la demande d'autorisation en cours et ne transmettre le retour puis se fermer que s'ils sont égaux ; un retour au state absent ou différent NE DOIT PAS (MUST NOT) fermer l'écoute ni être transmis à l'IHM.
Source : AUD-SEC-23 (/docs/audit/2026-11-21/findings.md) ; ACC-10 ; ADR-0033 (Accepté) point 4 ; RFC 8252 §7.3. Statut : [PROPOSITION].

#### Scenario: ACC-14-S1 Retour au state étranger
- **GIVEN** une autorisation en cours avec une écoute de bouclage active
- **WHEN** un processus local envoie à l'adresse de retour une requête dont le state est absent ou différent de celui de la demande
- **THEN** la requête reçoit une réponse d'erreur de requête invalide
- **AND** l'écoute reste ouverte, rien n'est transmis à l'IHM et la connexion en cours peut aboutir

#### Scenario: ACC-14-S2 Retour au state attendu après un retour étranger
- **GIVEN** une écoute de bouclage qui a déjà refusé un retour au state étranger
- **WHEN** le fournisseur redirige vers l'adresse de retour avec le state de la demande
- **THEN** le retour est transmis à la fenêtre principale
- **AND** l'écoute est fermée
