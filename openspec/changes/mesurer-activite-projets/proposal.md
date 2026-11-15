# Proposition : mesurer l'activité factuelle des projets (Project Intelligence)

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende dans le modèle du schéma detailed-uml. -->

## Why — Pourquoi

Le suivi factuel de l'avancement des projets fait partie de la vision d'Argos, planifié après le MVP [PROPOSITION — baseline v2.3 §2.4 ; arc42 §6.2]. Il expose au risque d'une métrique trompeuse (R-02) et d'une surveillance individuelle perçue (R-09) : ses règles éthiques doivent être spécifiées avant tout code.

## What Changes — Changements

- Indicateurs factuels issus de GitHub et GitLab : tickets, demandes de fusion, jalons, pipelines, versions.
- Pourcentage affiché seulement avec un dénominateur explicite, jamais calculé par l'IA.
- Aucune mesure ni classement individuel.
- Synthèse IA des faits, marquée, jamais source d'un indicateur.
- Connexion aux dépôts en lecture seule, quotas respectés.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `activite-projets` (préfixe `PIN`) : indicateurs factuels d'activité des dépôts, sans mesure individuelle ni pourcentage inventé.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Indicateurs par projet et par jalon ; synthèse.

### Exclu

- Toute mesure de productivité individuelle.
- Les outils autres que GitHub et GitLab.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur | consulte l'activité des projets de son espace | [PROPOSITION] — arc42 §6.2 |
| GitHub / GitLab | fournissent les données d'activité | [PROPOSITION] — `scm-connector` |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Project Intelligence, phase 3 | [PROPOSITION] | baseline v2.3 §2.4 ; arc42 §6.2 |
| Métrique trompeuse | [PROPOSITION] | risque R-02 |
| Surveillance individuelle perçue | [PROPOSITION] | risque R-09 |
| Quotas GitHub/GitLab | [PROPOSITION] | risque R-14 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

Aucun écart constaté (comparaison avec les ADR et sections arc42 citées).

## Impact

- **Composants** (arc42 §5) : `project-intel`, `scm-connector`, `ai-gateway`
- **Contrats d'interface** : OpenAPI des indicateurs
- **Données et persistance** : agrégats d'activité par projet et par période, sans identité de personne
- **Sécurité, confidentialité, droits** : jeton de dépôt en lecture seule, droits minimaux
- **Exploitation et observabilité** : quotas des API, fraîcheur des données
- **Documentation durable à mettre à jour** : `/docs/specifications/activite-projets.md`, registre `/docs/specifications/README.md`, arc42 §6.2
- **ADR** : aucun nouvel ADR requis

## Conception requise

Allégée : agrégation de données d'API existantes, sans cycle de vie nouveau.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Faut-il prendre en charge GitHub et GitLab dès la première version ? | non | porteur du projet |
| Q-2 | Combien de temps conserver l'historique des indicateurs ? | non | porteur du projet / DPO |
