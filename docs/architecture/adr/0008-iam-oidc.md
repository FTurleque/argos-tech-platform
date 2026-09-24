# ADR-0008 — Utiliser l’IdP d’entreprise via OIDC en première intention

- **Statut** : Proposé
- **Date** : 2026-09-24
- **Décideurs** : Non déterminé
- **Remplace** : —

## Contexte

ARGOS doit gérer des accès potentiellement sensibles par workspace/projet. Construire une authentification locale complète dupliquerait des fonctions d’entreprise et compliquerait SSO, MFA, révocation et cycle de vie des comptes.

## Critères

- SSO ;
- MFA/politiques d’entreprise ;
- révocation centralisée ;
- sécurité ;
- simplicité applicative ;
- intégration des rôles/groupes.

## Options

### A — OIDC vers l’IdP entreprise

+ standard courant ;
+ SSO et politiques centralisées ;
+ tokens utilisables par Web/API ;
- dépend de la capacité OIDC de l’IdP.

### B — SAML

+ largement supporté en entreprise ;
- moins naturel pour certaines APIs modernes ;
- reste une option si imposée.

### C — comptes locaux ARGOS

+ autonomie ;
- gestion mots de passe/MFA/provisioning à construire et auditer.

## Décision

**Proposition : option A**, avec SAML uniquement si l’IdP l’impose. L’autorisation fine reste dans ARGOS : workspace, projet, rôle et permissions.

## Conséquences positives

- réduit la surface d’authentification ;
- cohérence avec politiques d’entreprise ;
- révocation et MFA délégués.

## Conséquences négatives

- dépendance à la disponibilité/configuration de l’IdP ;
- mapping groupes/rôles à concevoir.

## Conséquences neutres

Keycloak peut servir d’IdP/broker seulement si aucun IdP adapté n’existe ; ce n’est pas un composant obligatoire.

## Validation

- identifier l’IdP réel ;
- POC OIDC Authorization Code + PKCE ;
- tests token expiré/révoqué ;
- test multi-workspace et moindre privilège ;
- revue sécurité.

## Traçabilité

- `arc42/03-contexte-perimetre.md`
- `arc42/07-vue-deploiement.md`
- `arc42/08-concepts-transverses.md`
- Q-02
