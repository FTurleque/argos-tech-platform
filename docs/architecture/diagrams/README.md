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
| D-07 | UML sequenceDiagram | collecte planifiée de l’activité d’un projet GitHub/GitLab, report sur quota, consultation sous RLS | `arc42/06-vue-execution.md` §6.2 |
| D-08 | UML sequenceDiagram | erreur API/retry | `arc42/06-vue-execution.md` |
| D-09 | UML sequenceDiagram | redémarrage/reprise | `arc42/06-vue-execution.md` |
| D-10 | UML-style deployment | POC/MVP : deux vues, POC local et POC partagé sur VM | `arc42/07-vue-deploiement.md` §7.2 et §7.3 |
| D-11 | UML-style deployment | cible Synology (ADR-0026) : conteneurs de `compose.yaml`, accès, identité, messagerie, sauvegarde | [`d-11-deploiement-cible.md`](d-11-deploiement-cible.md), cité en `arc42/07-vue-deploiement.md` §7.3 |
| D-12 | UML-style trust boundaries | zones de confiance, franchissements et contrôles | [`d-12-frontieres-confiance.md`](d-12-frontieres-confiance.md), cité en `arc42/08-concepts-transverses.md` §8.3 |
| D-13 | Mermaid erDiagram | modèle physique produit par les migrations V1 à V17 et V20, en cinq blocs (D-13.1 à D-13.5) | [`d-13-modele-physique.md`](d-13-modele-physique.md), cité en `arc42/08-concepts-transverses.md` §8.7 |
| D-14 | UML sequenceDiagram | authentification OIDC, autorisation et cloisonnement d’une requête | `arc42/08-concepts-transverses.md` §8.1 |
| D-15 | C4 Component | préférences d’affichage et de veille, couches contenu et mise en page de l’IHM | `arc42/05-vue-blocs.md` §5.4.1 |
| D-16 | UML sequenceDiagram | impact → alerte immédiate ou résumé périodique, e-mail et client desktop | `arc42/06-vue-execution.md` §6.1.1 |
| D-17 | UML sequenceDiagram | alerte `DESKTOP` → notification système, clic, reprise après coupure | `arc42/06-vue-execution.md` §6.1.2 |
| D-18 | UML sequenceDiagram | tâche quotidienne du radar : calendrier endoflife.date, dette de version, rappels | `arc42/08-concepts-transverses.md` §8.15 |
| D-19 | UML sequenceDiagram | texte officiel JORF → qualification, fiche, validation par l'expert, rappel ; source indisponible | `arc42/06-vue-execution.md` §6.6 |

Les diagrammes de séquence de `arc42/06-vue-execution.md` (D-06 à D-09, D-16, D-17, D-19) nomment leurs participants comme les composants de la section 5.

Le collecteur RSS/Atom et web intégré (ADR-0028) n'ajoute aucun diagramme d'architecture : DF-WEB-1 ([`veille-rss-web`](../../specifications/veille-rss-web.md#df-web-1--acceptation-dune-adresse)) et DC-1 du changement `collecter-veille-rss-web` décrivent l'acceptation d'une adresse et la collecte.

La recherche plein texte et les vues enregistrées (changement `rechercher-et-filtrer`) n'ajoutent aucun diagramme d'architecture : DF-RCH-1 ([`recherche-veille`](../../specifications/recherche-veille.md#df-rch-1--une-recherche-filtrée)) décrit l'ordre des droits, des mots et des filtres.

La passerelle IA (changement `encadrer-appels-ia`, ADR-0029 accepté) n'ajoute aucun diagramme d'architecture : DC-1 du changement décrit les vérifications qui précèdent et suivent un appel, DF-IAG-1 ([`passerelle-ia`](../../specifications/passerelle-ia.md#df-iag-1--un-résumé-est-il-produit-)) les cas où un élément reçoit un résumé ; les flèches `alerting`, `signal` et `impact-engine` vers `ai-gateway` figurent en arc42 §5.2.

Le profil de veille `artiste` (changement `ajouter-profil-artiste`) n'ajoute aucun diagramme d'architecture : ses composants sont décrits par le tableau de `arc42/05-vue-blocs.md` §5.4.2, DF-ART-1 ([`profil-artiste`](../../specifications/profil-artiste.md#df-art-1--un-appel-est-il-proposé-à-lartiste-)) et DC-1 du changement décrivent le rapprochement d'un appel.

La veille réglementaire (changement `suivre-veille-reglementaire`, ADR-0030 accepté) ajoute D-19 ; ses composants sont décrits par le tableau de `arc42/05-vue-blocs.md` §5.4.3, DF-REG-1 ([`veille-reglementaire`](../../specifications/veille-reglementaire.md#df-reg-1--parcours-dun-texte-officiel)), DC-1 et DC-2 du changement décrivent le parcours d'un texte et le cycle de vie de son suivi.

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
