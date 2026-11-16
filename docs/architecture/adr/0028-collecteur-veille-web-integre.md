# ADR-0028 — Collecter les flux RSS/Atom et les pages surveillées dans le serveur Argos

- **Statut** : Proposé
- **Date** : 2026-11-16
- **Décideurs** : porteur du projet (choix du collecteur, Q-1 de `collecter-veille-rss-web`, répondue le 2026-11-16)
- **Remplace** : [ADR-0006](0006-collecteurs-separes.md) (Proposé) pour la veille RSS/Atom et web, à l'acceptation du présent ADR
- **Remplacé par** : —

## Contexte

- [ADR-0006](0006-collecteurs-separes.md) (Proposé) prévoit FreshRSS et changedetection.io comme systèmes séparés. FreshRSS est sous AGPL-3.0 ; [ADR-0021](0021-double-licence.md) point 5 impose ses obligations à tout composant livré, ce qui gêne une future licence commerciale.
- Argos est un produit ouvert ([ADR-0025](0025-produit-ouvert-projet-personnel.md), Accepté) hébergé sur le Synology du porteur ([ADR-0026](0026-hebergement-synology.md), Accepté) : chaque conteneur supplémentaire consomme une mémoire partagée et demande une sauvegarde.
- N'importe quel utilisateur peut proposer une adresse : la protection contre la falsification de requête côté serveur (R-12) et l'injection d'instructions par contenu externe (R-11) doivent s'appliquer à toutes les sources, sans dépendre d'un service tiers.
- Le porteur a tranché le 2026-11-16 (questions du changement [`collecter-veille-rss-web`](/openspec/changes/collecter-veille-rss-web/proposal.md)) : collecteur intégré au serveur, bibliothèques sous licence permissive seulement (Q-1) ; collecte toutes les heures, paramétrable (Q-2) ; 50 sources personnelles par utilisateur au plus, paramétrable (Q-3).

## Critères de décision

1. Gratuit et sous licence permissive (ADR-0021 point 4) : ni AGPL ni GPL ni licence « source available ».
2. Aucun conteneur supplémentaire sur le Synology (ADR-0026).
3. Contrôle SSRF appliqué à l'adresse saisie et à chaque redirection, délai et taille bornés (R-12).
4. Contenu collecté traité comme donnée, jamais comme instruction (R-11).
5. Tests automatisés sans requête réseau réelle.

## Options considérées

### Option A — FreshRSS et changedetection.io en services séparés (ADR-0006)

- avantages : outils éprouvés, peu de code Argos ;
- inconvénients : AGPL-3.0 pour FreshRSS (ADR-0021) ; deux conteneurs et leurs bases à exploiter ; contrôle SSRF délégué à des services tiers ; contrats d'API à maintenir et à simuler.

### Option B — Collecteur intégré au serveur, bibliothèques permissives

- avantages : aucune licence copyleft ; aucun service à exploiter ; contrôle SSRF, limites et marquage du contenu dans le code d'Argos, testés avec un serveur HTTP local ;
- inconvénients : code de collecte à maintenir (planification, détection de changement, déduplication) ; fonctions avancées de changedetection.io (rendu JavaScript, sélecteurs visuels) absentes.

### Option C — Réécrire aussi la lecture RSS/Atom et l'analyse HTML

- avantages : aucune dépendance ;
- inconvénients : formats RSS 0.9x, 1.0, 2.0 et Atom à couvrir, analyse HTML robuste à écrire ; risque de défauts de sécurité dans un analyseur maison.

## Décision

**Proposition : option B.** Le serveur Argos collecte lui-même les flux et les pages surveillées :

- `source-ingestion` (`io.argos.ingestion.web`) vérifie les adresses (`GardeAdresses`), récupère le contenu (`RecuperateurWeb`) et le lit avec **ROME** 2.1.0 (Apache-2.0 ; dépendances jdom2 sous licence JDOM, de type Apache/BSD, et slf4j-api MIT) pour les flux et **jsoup** 1.22.1 (MIT) pour les pages et le nettoyage du texte ; la planification utilise l'extension **quarkus-scheduler** (Apache-2.0) ;
- une adresse est refusée si l'une des adresses IP de son hôte, après résolution DNS, appartient à un réseau privé, local, de lien local ou réservé ; le contrôle est refait à chaque redirection, suivie une à une (5 au plus) ; chaque récupération est bornée en durée (10 s) et en taille (2 Mio) ; les DOCTYPE des flux sont refusés ;
- `signal` (`io.argos.signal.web`) enregistre les sources, déduplique les actualités par empreinte et conserve le contenu en texte brut, restitué comme citation non fiable ; `impact-engine` rapproche les actualités des projets par nom de technologie, au niveau « à vérifier » seulement ;
- chaque source est collectée toutes les heures (`argos.veille-web.frequence`), avec des tentatives espacées après un échec ; un utilisateur possède au plus 50 sources personnelles (`argos.veille-web.sources-personnelles-max`).

## Conséquences positives

- aucune licence copyleft dans le produit livré ; aucune base ni conteneur de plus ;
- protection SSRF et marquage du contenu sous le contrôle d'Argos, couverts par des tests ;
- sources cloisonnées par la RLS comme les autres données de veille (ADR-0016).

## Conséquences négatives

- code de collecte à maintenir et à faire évoluer ;
- pas de rendu JavaScript : une page dont le contenu est produit côté navigateur n'est pas surveillable ;
- risque résiduel de rebinding DNS : le client HTTP résout l'hôte une seconde fois entre le contrôle et la connexion ; un mandataire de sortie filtrant serait la parade complète.

## Conséquences neutres / compromis

- ADR-0006 reste versionné ; il passe à « Remplacé » si le présent ADR est accepté.
- La passerelle IA (changement `encadrer-appels-ia`) devra transmettre le contenu collecté comme donnée encadrée ; aucune IA n'est appelée par la collecte.

## Méthode de validation

- tests unitaires du contrôle d'adresses, de la récupération (transport simulé) et des lecteurs : `argos-server/src/test/java/io/argos/ingestion/web/` ;
- tests d'intégration et parcours par l'API, sources simulées par un serveur HTTP local, RLS vérifiée sous `argos_app` : `argos-server/src/test/java/io/argos/chain/VeilleWebTest.java`, `ParcoursVeilleWebTest.java` ;
- revue de sécurité de la liste des plages refusées et mesure de la mémoire en exploitation sur le Synology.

## Traçabilité

- **Exigences** : WEB-01 à WEB-06 (`openspec/changes/collecter-veille-rss-web/specs/veille-rss-web/spec.md`)
- **Scénarios qualité** : aucun scénario du registre `docs/architecture/quality/scenarios.md` ne porte sur la SSRF ni sur le contenu externe ; risques R-11 et R-12 (`docs/architecture/risks/register.md`)
- **Diagrammes** : DC-1 (`openspec/changes/collecter-veille-rss-web/design.md`), DF-WEB-1 (`docs/specifications/veille-rss-web.md`)
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-server/src/main/java/io/argos/ingestion/web/`, `argos-server/src/main/java/io/argos/signal/web/`, `argos-server/src/main/resources/db/migration/V10__veille_rss_web.sql`
