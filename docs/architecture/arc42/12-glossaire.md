# 12. Glossaire

| Terme | Définition dans ARGOS |
|---|---|
| **ARGOS** | Technology & Project Intelligence Platform |
| **Technology Intelligence** | collecte, qualification et analyse des changements technologiques externes |
| **Project Intelligence** | consolidation factuelle de l’activité, des jalons, releases, pipelines et signaux projet |
| **IntelligenceItem** | élément de veille normalisé provenant d’une source externe |
| **ProjectActivity** | événement projet normalisé : issue, PR/MR, pipeline, release, déploiement, etc. |
| **ProjectImpact** | relation argumentée entre un `IntelligenceItem` et un projet potentiellement affecté |
| **Référentiel des standards** | technologies d’un espace avec leur statut (`ADOPT`, `TRIAL`, `ASSESS`, `HOLD`), version recommandée, responsable, justification et date de revue ; tenu par les administrateurs de l’espace (§8.15) |
| **Radar des sorties** | échéances (sortie, correctif, fin de support) des technologies d’un espace sur 30 jours, 6 mois ou 1 an, issues d’endoflife.date |
| **Rappel d’échéance** | message envoyé aux membres d’un espace, aux jours de rappel de l’espace (J-30, J-7, J0 par défaut), pour une échéance qui touche un de ses projets |
| **Dette de version** | impact d’un projet dont une version de l’inventaire a atteint sa fin de support, avec la version recommandée de l’espace |
| **Source** | configuration permettant de collecter une origine de veille |
| **Repository** | dépôt GitHub/GitLab associé à un projet |
| **Workspace** | frontière organisationnelle et d’autorisation regroupant projets et sources |
| **Espace** | nom de l’IHM et de l’API (`/api/v1/espaces`) pour un workspace ; ses membres y ont un rôle (capacité `comptes-acces`) |
| **Compte** | identité Argos d’une personne, créée à sa première connexion OIDC et reliée au sujet du jeton |
| **Appartenance** | lien entre un compte et un espace, portant le rôle `LECTEUR`, `CONTRIBUTEUR` ou `ADMINISTRATEUR` |
| **Invitation** | proposition, émise par un administrateur d’espace, de rejoindre l’espace avec un rôle ; adressée à un e-mail, valable 14 jours par défaut |
| **Administrateur plateforme** | attribut d’un compte pour l’exploitation et les catalogues, sans accès implicite aux espaces |
| **Espace courant** | espace transmis à PostgreSQL dans chaque transaction (`argos.espace_courant`) pour la RLS |
| **Keycloak** | fournisseur d’identité OIDC open source (Apache-2.0) auto-hébergé, retenu par ADR-0027 (Proposé) |
| **Profil de veille** | type de veille qu’un utilisateur active (`developpeur`, `expert-metier`…) : sources, rubriques, stratégies de rapprochement et garde-fous décrits par un manifeste du catalogue ; cumulable, sans effet sur les droits ; à ne pas confondre avec un rôle (ADR-0024) |
| **Mise en page** | présentation de l’IHM choisie par l’utilisateur (`classique`, `journal`, `fantaisie`, `gazette`) ; change la forme, jamais le contenu ni les mentions (ADR-0023) |
| **Alerte** | message immédiat adressé à un utilisateur sur un canal quand un impact exact ou probable satisfait une de ses règles ; une seule par signal, utilisateur et canal (capacité `alertes-digests`) |
| **Résumé périodique** | message quotidien ou hebdomadaire listant les éléments non alertés immédiatement ; jamais envoyé vide (en anglais : *digest*) |
| **Vue enregistrée** | recherche nommée d’un utilisateur (mots et filtres), visible de lui seul et rejouée sous ses droits courants ; n’accorde aucun droit (capacité `recherche-veille`) |
| **Horizon** | filtre de recherche retenant les éléments des N derniers jours, d’après leur date de publication, sinon de modification ou de réception (capacité `recherche-veille`) |
| **Canal de diffusion** | moyen d’acheminement d’une alerte ou d’un résumé : `EMAIL` (relais SMTP du Synology) ou `DESKTOP` (client desktop) |
| **Alerte ouverte** | alerte immédiate reçue par le client desktop et pas encore consultée dans l’application ; leur nombre s’affiche dans la zone de notification (capacité `notifications-poste`) |
| **Réglages du poste** | réglages propres à un client desktop, gardés sur le poste : notifications du poste (actives par défaut) et démarrage avec la session (désactivé par défaut) ; sans effet sur les autres canaux |
| **C4** | modèle de visualisation d’architecture par niveaux Context, Container, Component et Code |
| **arc42** | structure de documentation d’architecture en douze sections |
| **ADR** | Architecture Decision Record ; enregistrement versionné d’une décision structurante |
| **OpenAPI** | spécification de description d’API HTTP ; à ne pas confondre avec OpenAI |
| **OpenAI** | fournisseur de services IA ; non retenu comme fournisseur IA initial dans le cadrage actuel |
| **Claude** | famille de modèles d’Anthropic retenue comme fournisseur IA initial proposé |
| **LLM** | Large Language Model |
| **AI Gateway** | composant ARGOS encapsulant appels LLM, prompts, politiques, quotas, validation et audit |
| **Structured output** | réponse LLM contrainte/validée selon une structure attendue, idéalement un schéma |
| **RAG** | Retrieval-Augmented Generation ; enrichissement d’un appel LLM par des données retrouvées à la demande |
| **Embedding** | représentation vectorielle utilisée pour similarité/recherche sémantique |
| **pgvector** | extension PostgreSQL pour stocker et rechercher des vecteurs |
| **FTS** | Full-Text Search, recherche plein texte |
| **JSONB** | format JSON binaire/indexable de PostgreSQL |
| **RLS** | Row-Level Security ; contrôle d’accès au niveau des lignes PostgreSQL |
| **Webhook** | appel HTTP poussé par un système externe lors d’un événement |
| **Polling** | interrogation périodique d’une source pour détecter de nouvelles données |
| **Idempotence** | propriété garantissant qu’un même événement rejoué ne produit pas plusieurs effets métier |
| **DLQ** | Dead Letter Queue ; zone de mise à l’écart de messages/traitements en échec |
| **Outbox** | pattern garantissant la cohérence entre transaction métier et publication d’événements |
| **OIDC** | OpenID Connect, protocole d’identité au-dessus d’OAuth 2.0 |
| **SAML** | protocole d’échange d’assertions d’identité, courant en entreprise |
| **RBAC** | Role-Based Access Control |
| **SSO** | Single Sign-On |
| **IdP** | Identity Provider |
| **SLO** | Service Level Objective |
| **RPO** | Recovery Point Objective ; perte maximale de données acceptable |
| **RTO** | Recovery Time Objective ; délai maximal visé pour restaurer le service |
| **SBOM** | Software Bill of Materials ; inventaire des composants/dépendances d’un logiciel |
| **SAST** | Static Application Security Testing |
| **DAST** | Dynamic Application Security Testing |
| **CVE** | Common Vulnerabilities and Exposures |
| **Signal de sécurité** | avis de vulnérabilité normalisé, unique par source et identifiant externe, avec sa provenance et l'historique de ses versions (exigences COL-01 à COL-03) |
| **Inventaire** | liste de référence des composants d'un projet, issue de la SBOM CycloneDX de la branche principale ou, à défaut, des manifestes, avec son origine et sa date (INV-01, INV-04) |
| **Composant** | élément d'un inventaire identifié par son PURL et sa version, direct ou transitif (INV-01, INV-02) |
| **Niveau d'impact** | confiance d'un impact : `exact`, `probable` ou `à vérifier` (COR-02) ; distinct du cycle de traitement d'un impact |
| **MCP** | Model Context Protocol ; protocole par lequel un assistant IA appelle les outils d'un serveur. Argos expose huit outils en lecture seule, en protocole sans état (MCP-01 à MCP-07) |
| **Contenu externe** | texte issu d'une source tierce (avis, calendrier, texte officiel), renvoyé par le serveur MCP dans un champ `TexteExterne` marqué `CONTENU_EXTERNE_NON_FIABLE` et tronqué, à part des champs produits par Argos (MCP-05) |
| **Flux d'appareil** | OAuth 2.0 Device Authorization Grant (RFC 8628) : un outil sans navigateur intégré, comme un IDE, obtient un jeton après que l'utilisateur s'est connecté sur une page de vérification (Q-3 du changement `exposer-serveur-mcp`) |
| **Source web** | flux RSS/Atom ou page surveillée, d'un espace (créée par ses administrateurs) ou personnelle, dont l'adresse a passé le contrôle SSRF ; état `ACTIVE` ou `EN_ERREUR` avec le motif du dernier échec (WEB-01 à WEB-03, ADR-0028) |
| **Actualité** | élément de veille issu d'une source web : article d'un flux ou changement d'une page surveillée, dédupliqué par empreinte et cité avec toutes ses sources ; son contenu est une citation externe non fiable (WEB-04, WEB-05) |
| **Rapprochement par nom** | association d'une actualité à un projet dont un composant porte le nom de technologie mentionné ; toujours au niveau `à vérifier`, sans impact projet ni alerte (WEB-06) |
| **OTLP** | OpenTelemetry Protocol |
| **POC** | Proof of Concept |
| **MVP** | Minimum Viable Product |

## Termes encore ambigus à préciser

- **projet** : projet métier, dépôt ou agrégat de plusieurs dépôts ?
- **progression** : aucun pourcentage global ne doit être utilisé sans définition objective ;
- **risque projet** : doit être défini par règle mesurable et provenance ;
- **impact** : distinction à préciser entre impact probable, confirmé et résolu ;
- **source critique** : seuil et règles à définir.

## Preuve et maintenance

Le glossaire doit être mis à jour dès qu’un terme métier est introduit dans le code, l’API, un ADR ou un diagramme.
