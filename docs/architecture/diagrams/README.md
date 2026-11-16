# Inventaire des diagrammes ARGOS

Tous les diagrammes d’architecture sont écrits en **Mermaid** et versionnés directement dans les fichiers Markdown qui les expliquent. Ce dossier sert d’index ; aucune image binaire n’est la source de vérité.

## Convention visuelle

Les vues utilisent des stéréotypes explicites dans les libellés :

- `«Person»`
- `«Software System»`
- `«Container»`
- `«Component»`
- `«interface»`
- `«adapter»`
- `«node»`
- `«database»`

Chaque relation doit porter une action et le protocole lorsqu’il est architecturalement significatif.

## Inventaire

| ID | Type | Portée | Source |
|---|---|---|---|
| D-01 | C4 Context | ARGOS et systèmes externes | `arc42/03-contexte-perimetre.md` |
| D-02 | UML-style composants | style ports/adapters | `arc42/04-strategie-solution.md` |
| D-03 | C4 Container | ARGOS | `arc42/05-vue-blocs.md` |
| D-04 | C4 Component | ARGOS Backend | `arc42/05-vue-blocs.md` |
| D-05 | UML classDiagram | modèle métier conceptuel | `arc42/05-vue-blocs.md` §5.7 |
| D-06 | UML sequenceDiagram | signal techno → impact projet | `arc42/06-vue-execution.md` |
| D-07 | UML sequenceDiagram | webhook projet | `arc42/06-vue-execution.md` |
| D-08 | UML sequenceDiagram | erreur API/retry | `arc42/06-vue-execution.md` |
| D-09 | UML sequenceDiagram | redémarrage/reprise | `arc42/06-vue-execution.md` |
| D-10 | UML-style deployment | POC/MVP : deux vues, POC local et POC partagé sur VM | `arc42/07-vue-deploiement.md` §7.2 et §7.3 |
| D-11 | UML-style deployment | cible entreprise | **manquant** : `arc42/07-vue-deploiement.md` §7.4 ne contient pas de diagramme |
| D-12 | UML-style trust boundaries | sécurité | **manquant** : annoncé pour `arc42/08-concepts-transverses.md`, absent du fichier |
| D-13 | Mermaid erDiagram | modèle physique conceptuel | **manquant** : annoncé pour `arc42/08-concepts-transverses.md`, absent du fichier |
| D-14 | UML sequenceDiagram | authentification OIDC, autorisation et cloisonnement d’une requête | `arc42/08-concepts-transverses.md` §8.1 |
| D-15 | C4 Component | préférences d’affichage et de veille, couches contenu et mise en page de l’IHM | `arc42/05-vue-blocs.md` §5.4.1 |
| D-16 | UML sequenceDiagram | impact → alerte immédiate ou résumé périodique, e-mail et client desktop | `arc42/06-vue-execution.md` §6.1.1 |
| D-17 | UML sequenceDiagram | alerte `DESKTOP` → notification système, clic, reprise après coupure | `arc42/06-vue-execution.md` §6.1.2 |
| D-18 | UML sequenceDiagram | tâche quotidienne du radar : calendrier endoflife.date, dette de version, rappels | `arc42/08-concepts-transverses.md` §8.15 |

D-06 à D-09 emploient des noms de participants différents des composants de la section 5 ; la correspondance figure en tête de `arc42/06-vue-execution.md`.

Le collecteur RSS/Atom et web intégré (ADR-0028) n'ajoute aucun diagramme d'architecture : DF-WEB-1 ([`veille-rss-web`](../../specifications/veille-rss-web.md#df-web-1--acceptation-dune-adresse)) et DC-1 du changement `collecter-veille-rss-web` décrivent l'acceptation d'une adresse et la collecte.

La recherche plein texte et les vues enregistrées (changement `rechercher-et-filtrer`) n'ajoutent aucun diagramme d'architecture : DF-RCH-1 ([`recherche-veille`](../../specifications/recherche-veille.md#df-rch-1--une-recherche-filtrée)) décrit l'ordre des droits, des mots et des filtres.

Les diagrammes métier par capacité (`DF-*`) sont inventoriés dans [`../../specifications/README.md`](../../specifications/README.md#inventaire-des-diagrammes-métier) ; les diagrammes de conception propres à un changement OpenSpec (`DC-*`) restent dans son `design.md`.

## Diagrammes à ajouter lorsque l’implémentation existe

- C4 Component focalisé `Ingestion` si le module devient complexe ;
- C4 Component focalisé `AI Gateway` ;
- `stateDiagram-v2` du cycle de vie `IntelligenceItem` ;
- `stateDiagram-v2` du cycle de vie `ProjectImpact` ;
- pipeline CI/CD réel ;
- déploiement réel issu des manifests IaC ;
- niveau C4 Code uniquement pour des zones critiques justifiées.

## Validation CI recommandée

1. extraire tous les blocs `mermaid` ;
2. les rendre avec Mermaid CLI ;
3. échouer le pipeline sur erreur de syntaxe ;
4. vérifier les liens Markdown ;
5. interdire `.puml` si Mermaid reste la règle du projet ;
6. ne pas exiger de couleur pour comprendre un diagramme.

## Preuve

La conformité de cet inventaire doit être contrôlée lors de chaque PR modifiant `docs/architecture/`.
