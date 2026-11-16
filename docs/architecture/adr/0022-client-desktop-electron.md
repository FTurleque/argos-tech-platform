# ADR-0022 — Ajouter un client desktop Electron qui réutilise l'IHM Vue.js 3

- **Statut** : Proposé
- **Date** : 2026-11-15
- **Décideurs** : porteur du projet (choix d'Electron, 2026-11-15) ; revue d'architecture à venir
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Le dossier de proposition v2.4 et l'architecture consolidée (arc42 §5.1, §7) ne prévoient qu'une **IHM web** (SPA Vue.js 3) servie par le serveur ARGOS. Le porteur du projet demande en plus une **application desktop** pour les postes de travail.

Le serveur reste la seule source de vérité : collecte, corrélation, autorisations (RBAC + RLS, ADR-0016) et audit restent côté Quarkus. Le desktop est un client de l'API REST/OpenAPI au même titre que la SPA.

Besoins exprimés ou observés :

- recevoir les alertes critiques sans dépendre d'un onglet de navigateur ouvert ni du canal Teams, dont le mécanisme d'envoi n'est pas encore décidé (arc42 §9.3, point 7) ;
- disposer d'un point d'entrée permanent (icône dans la zone de notification) ;
- ne pas maintenir deux interfaces.

Contraintes : ADR-0019 (Vue.js 3), ADR-0021 point 4 (licences des dépendances distribuées compatibles avec une distribution commerciale), ADR-0008 (OIDC vers l'IdP d'entreprise).

**Hypothèses à valider** : parc majoritairement Windows 10/11 ; un certificat de signature de code peut être obtenu en interne.

## Critères de décision

1. Une seule base de code d'IHM pour le web et le desktop.
2. Notifications système natives et icône dans la zone de notification.
3. Rendu identique sur tous les postes, quel que soit le moteur web installé.
4. Compétences de l'équipe : pas de nouveau langage à maintenir.
5. Licence compatible ADR-0021 (permissive).
6. Surface d'attaque maîtrisée : aucune capacité native non nécessaire exposée à l'IHM.
7. Authentification OIDC conforme aux recommandations pour applications natives (RFC 8252 : code d'autorisation + PKCE).

## Options considérées

### Option A — Electron qui emballe `argos-ui`

- avantages : tout en JavaScript ; rendu identique partout (Chromium embarqué) ; écosystème mature (mise à jour automatique, installeurs, signature) ; licence MIT ;
- inconvénients : installation lourde (Chromium et Node.js embarqués) et consommation mémoire plus élevée ;
- risques : chaque version livrée embarque un Chromium à maintenir à jour ; surface d'attaque plus large si les règles de sécurité d'Electron ne sont pas appliquées ;
- coût : faible à développer ; suivi régulier des versions d'Electron à prévoir.

### Option B — Tauri 2 qui emballe `argos-ui`

- avantages : très léger (binaire Linux optimisé mesuré à **5,1 Mo**, IHM comprise, le 2026-11-15) ; modèle de permissions par capacités ; licence MIT / Apache-2.0 ;
- inconvénients : une couche Rust à maintenir ; rendu dépendant de la WebView du système (WebView2, WebKitGTK, WKWebView) ;
- risques : compétence Rust absente de l'équipe ; écarts de rendu entre postes ;
- coût : faible ; prototype réalisé puis abandonné au profit de l'option A.

### Option C — Client JavaFX

- avantages : reste dans l'écosystème Java du serveur ;
- inconvénients : réécriture complète de l'IHM ; deux interfaces à maintenir ;
- coût : élevé.

### Option D — Pas de desktop : SPA installable (PWA)

- avantages : aucun livrable supplémentaire ;
- inconvénients : notifications soumises au navigateur et à ses réglages ; pas d'icône permanente ;
- risques : ne répond pas à la demande.

## Décision

Option A, **proposée** : choix du porteur du projet, qui privilégie l'homogénéité du rendu et l'absence de Rust à la légèreté de Tauri.

1. Le module `argos-desktop/` emballe le build de `argos-ui/` avec Electron. Aucune logique métier n'est ajoutée côté desktop.
2. Le desktop ne contient **ni base de données ni serveur embarqué** : il exige un serveur ARGOS joignable (VM ou poste de développement).
3. L'URL du serveur est fixée au build de l'IHM (`VITE_ARGOS_API_URL`) ; la même IHM tourne dans le navigateur et dans le desktop.
4. Règles de sécurité Electron appliquées : `contextIsolation`, `sandbox`, pas de `nodeIntegration` ; l'IHM ne voit qu'un pont `window.argosDesktop` limité aux notifications ; l'IHM est servie par le protocole interne `app://argos` avec une politique de sécurité du contenu qui n'autorise que le serveur ARGOS ; navigation et ouverture de fenêtres bloquées, liens externes ouverts dans le navigateur du système ; une seule instance.
5. Le serveur autorise l'origine `app://argos` dans sa configuration CORS.
6. Authentification : OIDC code d'autorisation + PKCE via le navigateur du système et une redirection locale (RFC 8252), quand ADR-0008 sera mis en œuvre. Jetons conservés via le magasin sécurisé du système (`safeStorage`), jamais en clair.
7. Hors périmètre de cet ADR : **l'analyse locale des dépendances présentes sur les postes** (cas Shai-Hulud, dossier v2.4 §A.2). Elle demanderait un accès au système de fichiers et une revue RSSI / DPO (pas d'évaluation individuelle, consentement) ; elle fera l'objet d'un ADR distinct si elle est retenue.

## Conséquences positives

- une seule IHM pour le web et le desktop ;
- alertes critiques visibles sur le poste, indépendamment de Teams ;
- rendu identique sur tous les postes ; aucun nouveau langage dans l'équipe.

## Conséquences négatives

- installeur et mémoire nettement plus lourds qu'avec Tauri ;
- suivi des versions d'Electron (correctifs Chromium) à intégrer à la veille d'ARGOS elle-même ;
- un livrable de plus à construire, signer, distribuer et mettre à jour.

## Conséquences neutres / compromis

- les notifications du desktop complètent les canaux Teams et e-mail ; elles ne les remplacent pas ;
- le desktop n'a aucun intérêt sans serveur : il ne remet pas en cause ADR-0017 ;
- l'IHM ne dépend pas d'Electron : revenir à Tauri ne toucherait que `argos-desktop/`.

## Méthode de validation

- POC : `npm run start` dans `argos-desktop` affiche l'état du serveur local ;
- test d'une notification système déclenchée depuis l'IHM ;
- mesure de la taille de l'installeur et de la mémoire au repos sur un poste Windows représentatif ;
- revue sécurité selon la liste de contrôle officielle d'Electron (isolation, bac à sable, CSP, navigation) ;
- `npm run check` : tests du protocole interne (sortie du dossier de l'IHM, CSP) ;
- vérification des licences des dépendances livrées (ADR-0021 point 6).

## Traçabilité

- **Exigences** : aucune exigence OpenSpec ne couvre encore le desktop ; un changement OpenSpec dédié est à créer avant l'implémentation des notifications
- **Scénarios qualité** : —
- **Diagrammes** : arc42 §5.1 (C4 Container) et §7.2
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-desktop/`, `argos-ui/`

## Notes

Licences relevées le 2026-11-15 : `electron` 45.x et `electron-builder` 26.x : MIT (outils de développement et de packaging ; Electron est redistribué dans l'installeur).

