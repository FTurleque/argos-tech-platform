# Proposition : formaliser la chaîne sécurité du POC et la personnalisation de la veille

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Les comportements attendus d'ARGOS sont décrits dans `docs/architecture/` (arc42, baselines v2.3 et v2.4, ADR-0010 à ADR-0018), mais sous forme de cibles d'architecture, sans exigences atomiques ni scénarios testables. Les ADR citent des exigences `F01` à `F16` dont les définitions ne figurent pas dans le dépôt [QUESTION]. Le POC v2.4 doit démontrer en priorité la chaîne « avis de sécurité → inventaire SBOM → corrélation » (baseline v2.3 §3, critères go/no-go 1 à 3, 6 et 7) : sans exigences vérifiables, ces critères ne peuvent être ni testés ni tracés.

Ce changement formalise un premier lot de quatre capacités en exigences OpenSpec, sans rien présenter comme livré : le dépôt ne contient aucun code applicatif [EXISTANT — `README.md`, `docs/architecture/README.md`].

## What Changes — Changements

- Création de quatre capacités proposées, dont les exigences sont toutes au statut [PROPOSITION] car elles dérivent d'ADR « Proposé » ou de baselines « à valider ».
- Création des fiches fonctionnelles durables correspondantes sous `/docs/specifications/`, avec leurs diagrammes métier Mermaid.
- Aucune modification de comportement existant : aucune spec de référence n'existe (`openspec list --specs` vide).
- Aucune rupture de contrat (**BREAKING** : aucun).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `collecte-avis-securite` (préfixe `COL`) : collecte des avis de vulnérabilité (OSV principal, enrichissements), normalisation, provenance, idempotence, comportement en cas d'erreur de source.
- `inventaire-sbom` (préfixe `INV`) : inventaire des composants d'un projet à partir de la SBOM CycloneDX de la branche principale, fraîcheur et repli sur les manifestes.
- `correlation-impact` (préfixe `COR`) : rapprochement déterministe d'un signal de sécurité et des projets concernés, niveaux d'impact, provenance, fonctionnement sans IA, visibilité restreinte.
- `abonnements-veille` (préfixe `ABO`) : socle de veille d'équipe et veille personnelle, sans élargissement des droits.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Comportements observables des quatre capacités, tels que documentés dans les sources ci-dessous.
- Erreurs, rejeux et cas limites cités par arc42 §6.3, §8.5, §10 et le registre de scénarios qualité.

### Exclu

- Alerte ciblée et digests (`alerting`) : lot 2, voir `/docs/specifications/README.md`.
- Serveur MCP, veille réglementaire, AI Gateway, radar versions/EOL, Project Intelligence, recherche : lots ultérieurs.
- Chemins d'API, schéma de base et choix d'outillage : non documentés, relèvent de la conception et d'ADR à venir.
- Toute valeur de seuil non présente dans les sources (nombre de tentatives, âge d'obsolescence d'une SBOM, délais) : traitée comme [QUESTION].

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur technique (développeur) | consulte les impacts de ses projets ; gère sa veille personnelle | [PROPOSITION] — arc42 §1.3, §3.2 |
| Responsable sécurité | exploite les signaux de vulnérabilité | [PROPOSITION] — arc42 §1.3 |
| Administrateur ARGOS / d'espace | configure les sources et le socle d'équipe | [PROPOSITION] — arc42 §3.2, §8.1 |
| Exploitant | surveille l'état des sources | [PROPOSITION] — arc42 §3.2, §6.3 |
| Systèmes externes : OSV, NVD, CISA KEV, FIRST EPSS, CERT-FR, CI des projets (SBOM) | fournissent avis et inventaires | [PROPOSITION] — ADR-0010, ADR-0011 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| OSV source principale, enrichie par NVD, KEV, EPSS, CERT-FR | [PROPOSITION] | `/docs/architecture/adr/0010-sources-vulnerabilites.md` (Proposé) |
| SBOM CycloneDX de la branche principale comme inventaire de référence, repli manifestes | [PROPOSITION] | `/docs/architecture/adr/0011-sbom-cyclonedx.md` (Proposé) |
| Corrélation déterministe, niveaux `exact` / `probable` / `à vérifier`, IA jamais seule pour une alerte critique | [PROPOSITION] | `/docs/architecture/adr/0013-correlation-deterministe.md` (Proposé) |
| Cloisonnement RBAC + RLS, refus journalisés | [PROPOSITION] | `/docs/architecture/adr/0016-workspace-rbac-rls.md` (Proposé) |
| Abonnements `TEAM` / `PERSONAL` sans droit supplémentaire | [PROPOSITION] | `/docs/architecture/baseline-v2.4.md` §4 ; arc42 §5.4, §8.2 |
| Provenance, idempotence sur source + identifiant externe | [PROPOSITION] | arc42 §4.2 (P3), §6.1 |
| Curseur durable, retry borné, `Retry-After`, état `DEGRADED` | [PROPOSITION] | arc42 §6.3 (« règles proposées »), §8.5 |
| Impacts 100 % traçables ; 0 perte, 0 duplication logique | [PROPOSITION] | arc42 §10.2 Q-01, Q-03 ; `/docs/architecture/quality/scenarios.md` |
| Aucune alerte bloquée sans IA ; aucune donnée CONFIDENTIAL/RESTRICTED envoyée à l'IA | [PROPOSITION] | baseline v2.3 §6, critères 6 et 7 |
| Stack Java 17+ / Quarkus / Vue.js 3 / PostgreSQL | [DÉCISION] | ADR-0019 (Accepté), ADR-0002 (Accepté) |
| Absence de code applicatif | [EXISTANT] | `README.md` §Statut ; arborescence du dépôt |

## Écarts constatés

- Les ADR-0010 à ADR-0018 citent des exigences `F01`–`F16` absentes du dépôt ; la correspondance avec les identifiants `COL`, `INV`, `COR`, `ABO` ne peut pas être établie [QUESTION Q-1].
- arc42 §6 annonce reprendre « strictement » les composants de la section 5, mais ses participants (`Technology Intelligence`, `Impact Correlation`, `Workspace & Project Core`, `Scheduler & Jobs`…) diffèrent des composants de la section 5 (`signal`, `impact-engine`, `workspace`…). Une table de correspondance est ajoutée à arc42 §6 ; le renommage des diagrammes reste à décider.
- L'inventaire des diagrammes annonce D-05 (classes, §5), D-11 (déploiement cible, §7), D-12 et D-13 (§8) ; aucun de ces diagrammes n'est présent dans les fichiers cités. D-05 est ajouté à partir du glossaire ; D-11, D-12 et D-13 restent signalés manquants.

## Impact

- **Composants** (arc42 §5) : `source-ingestion`, `vuln-intel`, `signal`, `inventory`, `scm-connector`, `impact-engine`, `workspace`, `audit`, `ai-gateway` (absence de dépendance bloquante).
- **Contrats d'interface** : aucun contrat existant ; l'OpenAPI des consultations reste à créer.
- **Données et persistance** : signaux, versions d'avis, inventaires, impacts, abonnements, curseurs et zone d'échec (PostgreSQL, ADR-0002).
- **Sécurité, confidentialité, droits** : impacts et abonnements cloisonnés par espace ; abonnements personnels sans effet sur les droits.
- **Exploitation et observabilité** : état `DEGRADED` d'une source, événement d'exploitation, refus d'accès journalisés.
- **Documentation durable à mettre à jour** : `/docs/specifications/` (index et quatre fiches), arc42 §5 (D-05) et §6 (correspondance), `/docs/architecture/diagrams/README.md`.
- **ADR** : ADR-0010, 0011, 0013, 0016 (Proposés) restent la source des décisions ; aucun nouvel ADR métier n'est requis par ce changement.

## Conception requise

Complète : le changement couvre plusieurs modules, introduit le modèle de données des signaux, inventaires et impacts, et touche la sécurité (cloisonnement) et la reprise sur erreur.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Où sont définies les exigences `F01`–`F16` citées par les ADR, et à quelles exigences `COL`/`INV`/`COR`/`ABO` correspondent-elles ? | non pour les specs ; oui pour la traçabilité avec le dossier v2.x | Product Owner / architecte |
| Q-2 | Quelles règles distinguent les niveaux `probable` et `à vérifier` ? | **Répondue le 2026-11-16** : `exact` : PURL et version dans la plage affectée, SBOM non obsolète ; `probable` : même correspondance, mais inventaire de repli ou SBOM obsolète ; `à vérifier` : correspondance sans PURL (nom ou CPE seul), version inconnue ou plage non interprétable [DÉCISION] | architecte / sécurité |
| Q-3 | À partir de quel âge une SBOM est-elle obsolète ? | **Répondue le 2026-11-16** : obsolète 30 jours après sa date de génération [DÉCISION] | sécurité / Tech Lead |
| Q-4 | Nombre maximal de tentatives et seuil d'échec qui place une source en `DEGRADED` ? | **Répondue le 2026-11-16** : 5 tentatives par synchronisation, avec attente exponentielle ; `DEGRADED` après 3 synchronisations consécutives en échec [DÉCISION] | backend / exploitation |
| Q-5 | Qui peut créer un abonnement `TEAM`, et un utilisateur peut-il se désabonner d'un abonnement `TEAM` obligatoire ? | **Répondue le 2026-11-16** : seul un administrateur de l'espace crée un abonnement `TEAM` ; un `TEAM` obligatoire ne peut pas être quitté, un `TEAM` facultatif peut l'être [DÉCISION] | Product Owner |
| Q-6 | Le délai cible source → signal (« alerte sécurité < 1 h », ADR-0010) est-il validé ? | non : cible qualité à valider | Product Owner / sécurité |
| Q-7 | Quelles versions du schéma CycloneDX doivent être acceptées ? | **Répondue le 2026-11-16** : CycloneDX 1.5 et 1.6, format JSON [DÉCISION] | Tech Lead |
