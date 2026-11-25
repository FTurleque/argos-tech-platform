# Proposition : épingler l'adresse validée de la veille web

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le contrôle SSRF de la veille web (WEB-03) vérifie les adresses IP obtenues par une première résolution DNS de l'hôte, puis le client HTTP résout le nom une seconde fois pour se connecter : un nom à durée de vie courte peut répondre une adresse publique au contrôle et une adresse privée à la connexion (rebinding DNS) [EXISTANT — `argos-server/src/main/java/io/argos/ingestion/web/GardeAdresses.java:103`, `RecuperateurWeb.java:89`, `:100`, `:189`, révision `b142695`]. Le constat AUD-SEC-01 (Moyenne, sprint 3, « à repasser Élevée avant l'ouverture au public ») le confirme au 2026-11-21 : aucune connexion à l'adresse validée, aucun test de rebinding [EXISTANT — `/docs/audit/2026-11-21/findings.md#aud-sec-01`]. ADR-0028 (Accepté) consigne ce risque résiduel et arc42 §8.3 le répète [DÉCISION — `/docs/architecture/adr/0028-collecteur-veille-web-integre.md`, Conséquences]. Le sprint 3 de l'audit le traite avant l'ouverture du service (NAS puis service public, ADR-0026) [PROPOSITION — `/docs/audit/2026-11-21/sprints.md`].

## What Changes — Changements

- La connexion de chaque récupération se fait vers une adresse IP issue de la résolution qui a été contrôlée, jamais vers le résultat d'une seconde résolution du nom.
- Il en va de même à chaque redirection : la destination est résolue une fois, contrôlée, puis la connexion vise cette adresse.
- En HTTPS, le certificat reste vérifié pour le nom d'hôte de l'adresse (indication SNI et en-tête `Host` d'origine), pas pour l'adresse IP.
- Un test simule un résolveur qui change de réponse entre deux appels (rebinding).
- Aucune rupture de contrat (**BREAKING** : aucun) : mêmes motifs de refus (`ADRESSE_INTERDITE`, `REDIRECTION_INTERDITE`), mêmes bornes, mêmes messages sans indice réseau.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `veille-rss-web` : exigence ajoutée WEB-07 (connexion à l'adresse validée, à chaque redirection comprise). WEB-03 « Adresses interdites » et les autres exigences sont inchangées.

## Périmètre

### Inclus

- La récupération du contenu des sources de veille web (`source-ingestion`, paquet `io.argos.ingestion.web`) : contrôle d'adresse, transport HTTP, suivi des redirections.
- Les tests unitaires du rebinding et de la vérification du certificat par nom d'hôte, sur serveur HTTP local, sans requête vers Internet.
- La documentation durable : fiche `veille-rss-web`, arc42 §8.3, ADR-0028 (risque résiduel), registre des risques (R-12), D-12.

### Exclu

- Le filtrage de sortie (pare-feu, mandataire de sortie) des plages privées et de 169.254.0.0/16 recommandé en hébergement en ligne par AUD-SEC-01 : relève du déploiement, pas du code d'Argos ([QUESTION] Q-2).
- La liste des plages refusées, les ports autorisés, les bornes de durée et de taille : inchangés (ADR-0028, arc42 §8.3).
- Les autres clients HTTP d'Argos (sources d'avis, forges, endoflife.date, DILA) : ils ne visent que des origines de configuration, pas des adresses saisies par un utilisateur [EXISTANT — `/docs/audit/2026-11-21/findings.md#aud-sec-14`, « Tient hors rebinding »].

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur authentifié | propose une adresse de flux ou de page ; ne doit pas pouvoir faire viser une adresse privée par Argos | [EXIGENCE] — WEB-03 |
| Administrateur de l'instance (NAS) | protégé contre l'accès à l'interface DSM et au réseau local par rebond | [EXIGENCE] — risque R-12 |
| Porteur du projet | valide la conception du transport épinglé et l'amendement d'ADR-0028 | [PROPOSITION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Refus d'une adresse ou d'une redirection menant à un réseau privé, local ou de lien local | [EXIGENCE] | WEB-03, `/openspec/specs/veille-rss-web/spec.md` ; arc42 §8.3 |
| Contrôle refait à chaque collecte et à chaque redirection, suivie une à une (5 au plus) | [DÉCISION] | ADR-0028 (Accepté) ; `/docs/architecture/arc42/08-concepts-transverses.md` §8.3 |
| Risque résiduel de rebinding : le client HTTP résout l'hôte une seconde fois | [DÉCISION] (risque accepté à date) | ADR-0028, Conséquences ; arc42 §8.3 « Risque résiduel » |
| Résolution contrôlée puis seconde résolution par `HttpClient` | [EXISTANT] | `GardeAdresses.java:103` (`resolveur.resoudre(hote)`), `RecuperateurWeb.java:89`, `:100` (`garde.verifier`), `:189` (`client.send`), révision `b142695` |
| Aucun test de rebinding | [EXISTANT] | `grep -rn rebind argos-server/src/test` : aucun résultat (2026-11-24) |
| Résolveur remplaçable dans les tests, transport remplaçable | [EXISTANT] | `GardeAdresses.java:56` (interface `Resolveur`), `RecuperateurWeb.java:45-48` (interface `Transport`) |
| Socle Java 17 (pas de fournisseur de résolution enfichable du JDK 18) | [EXISTANT] | `argos-server/pom.xml:16` (`maven.compiler.release` 17) ; ADR-0019 |
| Licences permissives pour toute dépendance | [DÉCISION] | ADR-0021 |
| Constat, action recommandée et sévérité | [EXISTANT] (constat d'audit) | `/docs/audit/2026-11-21/findings.md#aud-sec-01` ; `/docs/audit/2026-11-21/etat.md` |

## Écarts constatés

- Les preuves du constat n'ont pas bougé depuis l'audit : `GardeAdresses.java:103` et `RecuperateurWeb.java:89`, `:100`, `:189` sont aux mêmes lignes à `b142695` (relu le 2026-11-24).
- La fiche `veille-rss-web` (règle WEB-03, [EXISTANT]) ne mentionne pas le risque résiduel de rebinding, que ADR-0028 et arc42 §8.3 consignent : la fiche présente le contrôle comme complet.
- Le registre des risques porte R-12 « Ouvert » avec la parade « validation DNS/IP, egress contrôlé » ; l'egress contrôlé n'est pas en place (hors périmètre de ce changement).

## Impact

- **Composants** (arc42 §5) : `source-ingestion` (contrôle d'adresse et transport de la veille web). `signal` (enregistrement des sources et actualités) : inchangé.
- **Contrats d'interface** : aucun changement ; OpenAPI identique ; mêmes motifs et messages de refus.
- **Données et persistance** : aucune.
- **Sécurité, confidentialité, droits** : ferme la fenêtre de rebinding DNS entre le contrôle et la connexion (R-12). Le certificat TLS reste vérifié pour le nom d'hôte.
- **Exploitation et observabilité** : le journal applicatif nomme l'adresse IP retenue pour la connexion (déjà le cas pour une adresse refusée) ; pas de nouvelle métrique.
- **Documentation durable à mettre à jour** : fiche `/docs/specifications/veille-rss-web.md` (WEB-07, règle, traçabilité, écart du risque résiduel), registre `/docs/specifications/README.md`, arc42 §8.3 (risque résiduel), D-12 si le contrôle du franchissement y est décrit, registre des risques R-12.
- **ADR** : ADR-0028 (Accepté) — le paragraphe « risque résiduel de rebinding DNS » devient faux une fois le changement livré ; sa mise à jour (note datée ou remplacement) est soumise au porteur du projet. Le choix du moyen de transport épinglé (design.md, D1) est une décision locale tant qu'il n'ajoute aucune dépendance ; candidat ADR s'il en ajoute une.
- **Dépendances entre changes** : aucune bloquante. `mettre-en-place-integration-continue` (sprint 1) exécutera le test de rebinding à chaque intégration une fois livré ; `durcir-autorisations-et-debits` touche le même limiteur de la veille web sans recouvrement attendu.

## Conception requise

allégée — un seul composant (`source-ingestion`) et un seul flux (récupération), sans contrat ni donnée nouvelle ; la conception doit toutefois trancher le moyen de connexion épinglée en HTTPS (SNI, vérification du nom) et ses alternatives.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Lorsque l'hôte a plusieurs adresses IP toutes autorisées, la connexion essaie-t-elle les suivantes après un échec de connexion à la première, ou la récupération échoue-t-elle ? (aucune source ne fixe ce comportement) | non : la conception retient une valeur par défaut révisable (design.md, D3) | Porteur du projet |
| Q-2 | Le filtrage de sortie des plages privées (pare-feu, mandataire) est-il exigé pour l'installation NAS, ou seulement pour un hébergement en ligne ? | non : hors périmètre de ce changement | Porteur du projet, exploitant |
| Q-3 | ADR-0028 reçoit-il une note datée qui retire le risque résiduel, ou un ADR qui le remplace sur ce point ? | non : tâche de documentation ; répondue le 2026-11-24 : note datée dans ADR-0028 [DÉCISION] | Porteur du projet |
