# 1. Introduction et objectifs

## 1.1 Résumé du système

ARGOS est une plateforme de **Technology Intelligence** et **Project Intelligence**. Elle vise à réunir dans un même système :

- la veille technologique issue de flux RSS/Atom et d’autres sources externes ;
- la surveillance de pages, documentations, releases et vulnérabilités ;
- le suivi factuel de l’activité des projets GitHub/GitLab ;
- la corrélation entre événements externes et technologies/projets internes ;
- la synthèse et l’analyse assistées par Claude/Anthropic ;
- la restitution via tableaux de bord, recherche, alertes et digests.

**État : Hypothèse à valider.** Cette mission est issue du cadrage du projet et du README ; aucune implémentation n’est encore présente.

## 1.2 Objectifs métier

1. **Réduire le coût de veille manuelle** en centralisant les sources pertinentes.
2. **Accélérer la détection d’impacts** : relier une CVE, une release ou un changement de documentation aux projets concernés.
3. **Donner une vue factuelle de l’activité projet** sans fabriquer de pourcentage d’avancement arbitraire.
4. **Capitaliser l’information** : conserver les signaux, analyses, décisions et historique de projet.
5. **Aider à la décision** grâce à des synthèses traçables fondées sur les données sources.

## 1.3 Parties prenantes

| Partie prenante | Besoin principal | Statut |
|---|---|---|
| Développeur | Voir les changements qui impactent ses technologies et projets | Hypothèse à valider |
| Lead / Tech Lead | Suivre risques, activité, releases et dépendances | Hypothèse à valider |
| Responsable projet | Obtenir une synthèse factuelle des jalons et blocages | Hypothèse à valider |
| Architecte | Examiner technologies, dépendances et impacts transverses | Hypothèse à valider |
| Sécurité | Identifier vulnérabilités et risques de chaîne logicielle | Hypothèse à valider |
| Administrateur ARGOS | Configurer sources, workspaces, droits et intégrations | Hypothèse à valider |
| Exploitant / DevOps | Déployer, observer, sauvegarder et restaurer la plateforme | Hypothèse à valider |

## 1.4 Objectifs qualité prioritaires

| Priorité | Objectif | Cible initiale |
|---|---|---|
| 1 | **Traçabilité** | toute synthèse ou alerte doit pouvoir remonter aux données sources |
| 2 | **Sécurité / confidentialité** | aucune donnée sensible transmise à un LLM sans politique explicite |
| 3 | **Maintenabilité** | ajout d’une source externe sans modifier le cœur métier |
| 4 | **Fiabilité** | ingestion idempotente, rejouable et tolérante aux erreurs externes |
| 5 | **Maîtrise des coûts** | self-hosted/gratuit d’abord ; budget IA mesurable et plafonnable |

Les seuils mesurables sont détaillés dans `10-exigences-qualite.md` et `../quality/scenarios.md`.

## 1.5 Scope initial proposé

### Dans le périmètre

- RSS/Atom ;
- surveillance Web ;
- GitHub et/ou GitLab ;
- releases et vulnérabilités ;
- tableaux de bord projet et portefeuille ;
- moteur de recherche ;
- synthèses Claude ;
- alertes/digests ;
- workspaces et RBAC.

### Hors périmètre initial

- remplacement de GitHub/GitLab ;
- gestion complète de projet type Jira ;
- orchestration CI/CD ;
- microservices distribués par défaut ;
- IA autonome capable d’exécuter des actions destructrices sur les dépôts.

## 1.6 Preuves, hypothèses et questions ouvertes

### Preuves observées

- `README.md` : mission, statut initial et principes visés.

### Hypothèses à valider

- utilisateurs et rôles exacts ;
- nécessité d’un mode multi-workspace ;
- choix définitif entre GitHub, GitLab ou les deux ;
- niveau de disponibilité attendu.

### Questions ouvertes

- Qui est propriétaire fonctionnel du produit ?
- Quel périmètre de projets pilote le POC ?
- Quelles données sont considérées confidentielles/restrictives ?

### Risques associés

- dérive de périmètre vers un outil de gestion de projet généraliste ;
- dépendance excessive au LLM ;
- métriques de suivi projet mal interprétées.

### Fichiers source concernés

- `README.md`
- `docs/architecture/README.md`
