# Proposition : personnaliser les profils de veille et la mise en page

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le porteur du projet a demandé le 2026-11-15 deux personnalisations absentes du dossier v2.4 [DÉCISION — demande du porteur, ADR-0023 et ADR-0024 (Acceptés le 2026-11-18)] :

- choisir sa **mise en page**, parmi les quatre maquettées (classique, journal, fantaisie, gazette), pour une IHM « plus jolie et qui ne fasse pas penser au travail » ;
- choisir **un ou plusieurs profils d'utilisation** : développeur et expert métier d'abord, puis d'autres publics comme des artistes, des comédiens ou des acteurs de la vie politique.

Les deux doivent être prises en compte maintenant : elles conditionnent le découpage de l'IHM, la taxonomie des signaux et le modèle des préférences, alors qu'aucun code métier n'existe encore [EXISTANT — `README.md` §Statut].

## What Changes — Changements

- Nouvelle capacité de **mise en page personnalisable** : catalogue de mises en page, choix par utilisateur partagé entre web et desktop, contenu identique dans toutes les mises en page.
- Nouvelle capacité de **profils de veille** : catalogue de profils, activation d'un ou plusieurs profils par utilisateur, veille restituée comme l'union des profils actifs, garde-fous propres à chaque profil, ajout d'un profil sans modifier les autres.
- Les deux profils initiaux reprennent les comportements déjà proposés pour les développeurs et les experts métier ; aucun nouveau public n'est livré par ce changement.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `affichage-personnalise` (préfixe `AFF`) : catalogue des mises en page, choix et persistance par utilisateur, mise en page par défaut, contenu et mentions identiques.
- `profils-veille` (préfixe `PRF`) : catalogue des profils, profils actifs d'un utilisateur, union des veilles, profils initiaux, ajout d'un profil, absence de droits accordés, garde-fous.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe. Le lien avec `abonnements-veille` (changement `formaliser-chaine-securite-poc`, non archivé) est traité comme une question (Q-3).

## Périmètre

### Inclus

- Choix de la mise en page et des profils par chaque utilisateur.
- Les quatre mises en page initiales et les deux profils initiaux.
- Les règles permettant d'ajouter plus tard une mise en page ou un profil.

### Exclu

- Les profils `artiste`, `comedien` et `politique` : exemples de packs futurs, chacun fera l'objet de son propre changement (ADR-0024 point 8).
- La personnalisation fine d'une mise en page (couleurs, densité) : « on améliorera plus tard l'affichage » (demande du porteur).
- Le contenu détaillé des écrans de chaque mise en page : maquettes de référence dans l'artefact « ARGOS desktop — maquettes des écrans ».
- L'hébergement, les comptes et la licence d'un produit ouvert : décision prise par ADR-0025, mise en œuvre à traiter par d'autres changements.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur | choisit sa mise en page et ses profils | [PROPOSITION] — demande du porteur |
| Administrateur d'espace | propose des profils par défaut à son équipe | [PROPOSITION] — ADR-0024 point 2 |
| Administrateur plateforme | publie ou retire des mises en page et des profils du catalogue | [PROPOSITION] — ADR-0023, ADR-0024 |
| Expert métier (MKP) | valide les fiches avant diffusion, garde-fou du profil `expert-metier` | [PROPOSITION] — ADR-0018 (Proposé) |
| DPO | revoit tout futur profil qui concerne des personnes | [PROPOSITION] — ADR-0024 point 8 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Quatre mises en page initiales, choix par utilisateur, contenu identique | [PROPOSITION] | demande du porteur (2026-11-15) ; `/docs/architecture/adr/0023-mises-en-page-selectionnables.md` (Proposé) |
| Profils cumulables, packs déclaratifs, garde-fous par profil | [PROPOSITION] | demande du porteur (2026-11-15) ; `/docs/architecture/adr/0024-profils-de-veille-extensibles.md` (Proposé) |
| Rapprochement déterministe, IA sans décision | [PROPOSITION] | `/docs/architecture/adr/0013-correlation-deterministe.md` (Proposé) |
| Validation MKP avant diffusion | [PROPOSITION] | `/docs/architecture/adr/0018-veille-reglementaire-mkp.md` (Proposé) |
| Préférences sans effet sur les droits | [PROPOSITION] | `/docs/architecture/adr/0016-workspace-rbac-rls.md` (Proposé) ; exigence `ABO-05` |
| Client desktop partageant l'IHM | [PROPOSITION] | `/docs/architecture/adr/0022-client-desktop-electron.md` (Proposé) |
| Absence de code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` ne contient que des `package-info.java` et `SystemInfoResource` |

## Écarts constatés

- La taxonomie des signaux du dossier v2.4 (§8.2) est une liste fermée (SECURITY, RELEASE, UPCOMING…) ; ADR-0024 la veut extensible par profil. La taxonomie n'est pas encore versionnée dans `docs/architecture/` : à intégrer en arc42 §8 lors de la conception.
- Le dossier v2.4 désigne les MKP comme « experts métier » ; le profil s'appelle `expert-metier` pour ne pas lier l'identifiant à un intitulé interne à Ciril.

## Impact

- **Composants** (arc42 §5) : `catalog` (catalogue des profils), `workspace` (préférences : profils actifs, mise en page), `impact-engine` (stratégies de rapprochement par profil), `signal` (taxonomie extensible), `mcp-server` et `API & Security` (exposition des préférences et garde-fous), IHM `argos-ui` (couches contenu et mise en page).
- **Contrats d'interface** : OpenAPI des préférences et du catalogue à créer ; outils MCP filtrés par profils actifs.
- **Données et persistance** : préférences utilisateur (profils actifs, mise en page) ; manifestes de profils ; attributs de signal propres à un profil.
- **Sécurité, confidentialité, droits** : un profil n'accorde aucun droit ; garde-fous appliqués avant diffusion ; profils futurs concernant des personnes soumis au DPO.
- **Exploitation et observabilité** : nombre d'utilisateurs par profil et par mise en page (sans suivi individuel).
- **Documentation durable à mettre à jour** : `/docs/specifications/affichage-personnalise.md`, `/docs/specifications/profils-veille.md`, registre `/docs/specifications/README.md`, arc42 §5 et §8, glossaire.
- **ADR** : ADR-0023 et ADR-0024 (Acceptés) ; aucun autre ADR modifié.

## Conception requise

Allégée : le changement fixe des règles fonctionnelles et le découpage des responsabilités, sans schéma de données ni API détaillés à ce stade ; ceux-ci seront conçus avec les premières tâches d'implémentation.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Argos reste-t-il un outil interne, ou deviendra-t-il un produit ouvert à d'autres publics ? | **Répondue le 2026-11-16** : produit ouvert, porté à titre personnel (ADR-0025, accepté) | porteur du projet |
| Q-2 | Un utilisateur peut-il désactiver un profil proposé par défaut par son équipe ? | **Répondue le 2026-11-16** : oui, tant qu'un profil reste actif (PRF-01) [DÉCISION] | Product Owner |
| Q-3 | Un abonnement `PERSONAL` (ABO-04) peut-il porter sur une source qui n'appartient à aucun de ses profils actifs ? | **Répondue le 2026-11-16** : oui, dans la limite de ses droits ; la source s'ajoute à l'union des profils actifs [DÉCISION] | Product Owner |
| Q-4 | Quel référentiel d'accessibilité s'impose aux mises en page (RGAA, WCAG 2.1 AA) ? | **Répondue le 2026-11-16** : RGAA 4.1.2 (aligné sur WCAG 2.1 AA) [DÉCISION] | RSSI / référent accessibilité |
| Q-5 | Qui publie un nouveau profil au catalogue, et après quelle revue ? | non pour les profils initiaux | Product Owner / architecte |
