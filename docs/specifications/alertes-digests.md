# Fiche capacité : `alertes-digests`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `ALR` |
| Spec de référence | [`/openspec/specs/alertes-digests/spec.md`](/openspec/specs/alertes-digests/spec.md) |
| Changements archivés | [`2026-11-16-diffuser-alertes-et-digests`](/openspec/changes/archive/2026-11-16-diffuser-alertes-et-digests/proposal.md) |
| Changements archivés, suite | [`durcir-autorisations-et-debits`](/openspec/changes/archive/2026-11-25-durcir-autorisations-et-debits/proposal.md) (ALR-08) |
| Changement en cours | [`automatiser-chaine-veille`](/openspec/changes/archive/2026-11-20-automatiser-chaine-veille/specs/alertes-digests/spec.md) — proposé ; ALR-07 implémenté et intégré le 2026-11-19 (volets A, B et C réunis) ; [`alimenter-inventaire-et-completer-ihm`](/openspec/changes/alimenter-inventaire-et-completer-ihm/proposal.md) (ALR-09) — implémenté, non archivé |
| Statut de la capacité | livrée ; implémentation et tests dans `argos-server` |
| Composants (arc42 §5) | `alerting`, `workspace`, `impact-engine` |

## Objectif

[PROPOSITION — `diffuser-alertes-et-digests`] Prévenir chaque utilisateur des éléments qui le concernent, immédiatement quand c'est grave et établi, sinon dans un résumé, sans bruit inutile.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Utilisateur | reçoit alertes et résumés selon ses abonnements | [EXISTANT] — `MoteurAlertes`, `ResumesPeriodiques` |
| Administrateur d'espace | définit les règles du socle d'équipe (abonnements `TEAM`) | [EXISTANT] — ABO-03, ABO-06 |
| Serveur de messagerie | relais SMTP du Synology (DSM) qui achemine les e-mails | [DÉCISION] du 2026-11-16 (Q-1) ; [EXISTANT] — `CanalEmail` |
| Client desktop | lit les alertes `DESKTOP` et confirme leur remise | [EXISTANT] — contrat `/api/v1/alertes` ; client : changement `notifier-sur-le-poste` |

## Périmètre et exclusions

Inclus :

- évaluation des règles, regroupement, résumés quotidiens et hebdomadaires, envoi par e-mail avec nouvelles tentatives, mise à disposition des alertes au client desktop (contrat utilisé par `notifier-sur-le-poste`).

Exclus :

- les canaux de messagerie d'équipe (Teams, Slack, Matrix) : aucun pour l'instant [DÉCISION] du 2026-11-16 (Q-2) ;
- le statut d'action d'un impact (corrigé, risque accepté) : relève de `correlation-impact` ;
- la notification système du poste : relève de `notifications-poste`.

## Préconditions et déclencheurs

Impacts calculés (`correlation-impact`) : `impact-engine` publie l'événement `ImpactsRecorded` après chaque corrélation ; abonnements définis (`abonnements-veille`) ; comptes et appartenances (`comptes-acces`). Les résumés partent à heure fixe (tâches planifiées Quarkus Scheduler).

### Configuration d'exploitation

Configuration par variables d'environnement (aucun secret versionné) :

| Variable | Rôle | Défaut |
|---|---|---|
| `ARGOS_SMTP_HOST`, `ARGOS_SMTP_PORT` | relais SMTP du Synology | `localhost`, `587` |
| `ARGOS_SMTP_USER`, `ARGOS_SMTP_PASSWORD` | authentification au relais | aucune |
| `ARGOS_SMTP_STARTTLS` | `DISABLED`, `OPTIONAL` ou `REQUIRED` | `OPTIONAL` |
| `ARGOS_MAIL_FROM` | expéditeur | `argos@localhost` |
| `ARGOS_PUBLIC_URL` | adresse publique de l'IHM, base des liens des alertes, résumés et rappels (`argos.alerting.lien-base`, ALR-09) | `http://localhost:5173` (serveur seul) ; `http://localhost:${ARGOS_PORT}` dans `compose.yaml` |
| `ARGOS_RESUME_QUOTIDIEN_CRON`, `ARGOS_RESUME_HEBDOMADAIRE_CRON`, `ARGOS_ALERTES_FUSEAU` | horaires des résumés | `0 0 8 * * ?`, `0 0 8 ? * MON`, `Europe/Paris` |

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Impact | signal (titre, gravité, source), projet, espace, niveau, paquet | niveau `EXACT`, `PROBABLE` ou `A_VERIFIER` | [EXISTANT] — `ImpactsRecorded` |
| Règle | abonnement actif : canal, fréquence, critères `PROJECT`, `PACKAGE`, `SOURCE`, `TECHNOLOGY`, `SEVERITY` | canal `EMAIL` ou `DESKTOP` ; fréquence `IMMEDIATE`, `DAILY` ou `WEEKLY` ; gravité `LOW`, `MODERATE`, `MEDIUM`, `HIGH` ou `CRITICAL` | [EXISTANT] — `RegleAlerte` |
| Alerte | titre, gravité, projets autorisés du destinataire, sources, lien vers l'élément, résumé IA éventuel et son état | données autorisées du destinataire seulement (ALR-06) | [EXISTANT] — table `alerte` (V8) |
| Résumé | éléments non alertés de la période, avec projets, sources et liens | aucun résumé vide (ALR-04-S2) | [EXISTANT] — table `alerte`, éléments `resume_element` |
| Contrat desktop | `GET /api/v1/alertes?canal=&etat=`, `GET /api/v1/alertes/{id}`, `POST /api/v1/alertes/{id}/remise` | alertes de l'appelant seulement | [EXISTANT] — `AlertesResource` |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Alerte immédiate quand une règle `IMMEDIATE` concerne l'impact et que la gravité minimale est atteinte | ALR-01 | ABO-02 | [EXISTANT] |
| Pas d'alerte immédiate sur un impact à vérifier ; l'élément reste dans le fil et entre dans le prochain résumé | ALR-02 | ADR-0013 (Proposé) | [EXISTANT] |
| Une alerte par signal, utilisateur et canal, qui cite tous les projets concernés ; un rejeu n'en crée pas d'autre | ALR-03 | dossier v2.4 §8.4 (regroupement) ; décision D2 | [EXISTANT] |
| Résumés quotidien (08:00) et hebdomadaire (lundi 08:00), Europe/Paris, paramétrables ; aucun résumé sans élément nouveau | ALR-04 | ABO-02 (fréquence) ; [DÉCISION] du 2026-11-16 (Q-3) | [EXISTANT] |
| Résumé IA demandé avec un délai borné ; alerte envoyée sans lui s'il est indisponible, refusé ou au-delà du plafond, avec mention dans le message | ALR-05 | baseline v2.3 §6 ; QS-10 | [EXISTANT] |
| Diffusion par e-mail (relais SMTP du Synology) et vers le client desktop ; nouvelles tentatives bornées puis état « en échec » | ALR-06 | ADR-0022 (Proposé) ; [DÉCISION] du 2026-11-16 (Q-1) | [EXISTANT] |
| Aucune alerte immédiate ni mention dans un résumé pour un impact `écarté` ou `résolu` ; un impact rouvert redevient éligible ; un abonnement suspendu ne produit aucune notification (ABO-07) | ALR-07 | décisions J5 et J7 du porteur du projet, 2026-11-19 | [PROPOSITION] — changement [`automatiser-chaine-veille`](/openspec/changes/archive/2026-11-20-automatiser-chaine-veille/specs/alertes-digests/spec.md) |
| Chaque lien d'une alerte, d'un résumé ou d'un rappel est l'adresse publique de la plateforme suivie d'un gabarit de l'historique « hash » de l'IHM vers une vue existante : `/#/signaux/{id}` (signal), `/#/espaces/{id}/radar` (radar de l'espace), `/#/profils/artiste/opportunites/{id}` (opportunité) ; aucun lien ne vise un chemin que l'IHM ne sert pas | ALR-09 | AUD-ARC-22 ; ALR-06 ; ADR-0038 | [PROPOSITION] — [`alimenter-inventaire-et-completer-ihm`](/openspec/changes/alimenter-inventaire-et-completer-ihm/proposal.md) ; vérifiée par `LiensIhmTest`, `AlertesTest`, `argos-ui/tests/liens-alertes.test.mjs` |
| E-mail (alerte, résumé, rappel) seulement vers l'adresse que le fournisseur d'identité déclare vérifiée, enregistrée normalisée à la création du compte et tenue à jour à chaque connexion ; sans adresse vérifiée connue, aucun e-mail et l'élément reste visible dans Argos et sur le poste | ALR-08 | AUD-SEC-04 ; ALR-06 ; ADR-0027 (Accepté) ; décision du porteur, 2026-11-24 (comptes existants) | [PROPOSITION] — `durcir-autorisations-et-debits` |

## Droits d'accès et restrictions

Une alerte ne cite que les projets que le destinataire est autorisé à voir (ACC-04) : les impacts sont relus sous son compte, rôle `argos_app` et RLS de `project_impact`. Les destinataires possibles sont les membres des espaces touchés ; une règle n'élargit aucun droit (ABO-05). Sous `argos_app`, un compte ne lit que ses alertes et ses éléments de résumé, et ne modifie que l'état et la date de remise de ses alertes `DESKTOP`. Une alerte d'un autre compte est introuvable via l'API et la tentative est journalisée (ACC-06).

## Effets de bord observables

- enregistrement des alertes et résumés, avec leur état d'envoi par canal (`EN_ATTENTE`, `ENVOYEE`, `EN_ECHEC`), le nombre de tentatives et l'échéance de la suivante ;
- envoi d'e-mails ;
- événement CDI `AlerteEmise` à chaque alerte ou résumé enregistré ;
- migration `V24__adresse_verifiee_des_comptes.sql` : les adresses déjà enregistrées sont effacées ; aucun e-mail ne part vers un compte tant qu'il ne s'est pas reconnecté avec un jeton qui déclare son adresse vérifiée, les alertes restant visibles dans Argos et sur le poste. Les alertes au canal e-mail d'un compte sans adresse passent en échec sans envoi (ALR-06).

## Qualités mesurables

[QUESTION] La cible « alerte en moins d'une heure » (Q-4) n'est pas validée. Le parcours de test mesure le délai entre la collecte et l'envoi (moins d'une seconde avec les services de test) ; en exploitation, la diffusion des messages en attente a lieu toutes les minutes.

## Diagrammes

### DF-ALR-1 — Alerte immédiate ou résumé

- **Question** : un élément part-il en alerte immédiate ou dans le résumé ?
- **Type** : organigramme
- **Portée** : comportement implémenté
- **Exigences** : ALR-01, ALR-02, ALR-03, ALR-04

```mermaid
flowchart TD
    A["Nouvel impact visible<br/>par l'utilisateur"] --> R{"Une de ses règles<br/>concerne l'impact ?"}
    R -- "non" --> Z["Rien"]
    R -- "oui" --> B{"Règle immédiate, gravité<br/>minimale atteinte et impact<br/>exact ou probable ?"}
    B -- "non" --> F["Fil de veille<br/>et prochain résumé"]
    B -- "oui" --> D{"Alerte déjà enregistrée pour ce<br/>signal, cet utilisateur et ce canal ?"}
    D -- "oui" --> E["Rien de plus"]
    D -- "non" --> G["Alerte immédiate<br/>avec ou sans résumé IA"]
```

Seuls les impacts établis qui satisfont une règle immédiate déclenchent une alerte, une seule fois par canal. Un élément alerté immédiatement n'est pas repris dans un résumé.

## Critères d'acceptation

Scénarios ALR-01-S1 à ALR-06-S2 du changement ; QS-10 (résilience sans IA) ; ALR-09-S1 à ALR-09-S3 du changement [`alimenter-inventaire-et-completer-ihm`](/openspec/changes/alimenter-inventaire-et-completer-ihm/specs/alertes-digests/spec.md) [PROPOSITION].

## Interprétations introduites par un changement

Interprétations du changement `diffuser-alertes-et-digests`, à faire valider [PROPOSITION] :

- gravité minimale portée par un critère d'abonnement `SEVERITY` ; une gravité absente ou non graduée (vecteur CVSS brut) n'atteint aucun seuil ;
- critères `PROJECT`, `PACKAGE`, `SOURCE`, `TECHNOLOGY` cumulatifs entre types et alternatifs dans un type ; les critères `VENDOR`, `THEME` et `DOMAIN` ne concernent aucun impact de sécurité ; les seuls éléments diffusés sont les impacts de sécurité ;
- un élément non alerté par une règle immédiate entre dans le résumé quotidien du canal de la règle ;
- un élément alerté sur un canal n'est repris dans aucun résumé du même utilisateur ;
- aucun résumé envoyé quand il est vide (ALR-04-S2) ;
- canaux `EMAIL` et `DESKTOP` seulement ; tout autre canal d'abonnement (`IN_APP`, par exemple) ne déclenche aucune diffusion ;
- le résumé IA porte sur le seul signal ; il est produit par la passerelle IA (`ResumeIaPasserelle`, capacité [`passerelle-ia`](passerelle-ia.md)) et vaut `INDISPONIBLE` si Ollama est injoignable ;
- le lien vers l'élément vise `<ARGOS_PUBLIC_URL>/#/signaux/<id>`, la vue du signal de l'IHM (`SignalView`), affichée après connexion (ALR-09) ;
- cinq tentatives au plus, délai initial d'une minute doublé à chaque échec, plafonné à 30 minutes ; une adresse e-mail absente est un échec définitif ;
- chaque envoi dure au plus 10 s (`quarkus.mailer.timeout`, variable `ARGOS_SMTP_TIMEOUT`) ; toute erreur, y compris un contenu illisible ou un relais qui ne répond pas, compte comme une tentative ; aucun verrou n'est tenu pendant l'envoi ;
- STARTTLS exigé par défaut (`ARGOS_SMTP_STARTTLS=REQUIRED`) : les identifiants SMTP ne circulent jamais en clair ; un avertissement est journalisé au démarrage en exploitation si `ARGOS_PUBLIC_URL` n'est pas défini ;
- l'évaluation des règles est synchrone dans le fil de la corrélation : un signal alerté attend au plus 10 s le résumé IA, puis 10 s par envoi d'e-mail ;
- « l'actualité » du scénario ALR-04-S1 désigne les éléments de veille ayant un impact de sécurité ; les actualités de la veille web (`veille-rss-web`) n'entrent dans aucun résumé (Q-5).

## Écarts constatés

- Le code de `argos-server` implémente les règles de cette fiche : module `io.argos.alerting` (`MoteurAlertes`, `RegleAlerte`, `ResumesPeriodiques`, `EnvoiAlertes`, `CanalEmail`, `ConsultationAlertes`), `io.argos.api.AlertesResource`, événement `io.argos.impact.ImpactsRecorded`, migration `V8__alertes_et_resumes.sql`.
- L'IHM présente les alertes (`AlertesView`, `/#/alertes` et `/#/alertes/{id}`) et les vues visées par les liens des messages : signal (`SignalView`, `/#/signaux/{id}` : provenance, gravité, résumé de la source, paquets, historique, impacts dans les espaces de l'utilisateur), radar d'un espace (`RadarView`) et opportunité du profil artiste (`ProfilArtisteView`). Les gabarits sont centralisés dans `io.argos.alerting.LiensIhm`, utilisé par `MoteurAlertes`, `RappelsRadar` et `RappelsOpportunites` ; `argos-ui/tests/liens-alertes.test.mjs` lit ces gabarits dans le source Java et échoue si l'un d'eux n'a pas de route dans `argos-ui/src/router.ts` (ALR-09-S3).
- Un lien sans le préfixe `/#/` (forme `<ARGOS_PUBLIC_URL>/signaux/<id>`) ne mène à aucune vue de l'IHM : les messages déjà remis sous cette forme gardent un lien mort (rupture visible d'ALR-09).
- ALR-07 (`automatiser-chaine-veille`, volet C, 2026-11-19) : `MoteurAlertes` et `ResumesPeriodiques` ne relisent que les impacts `ouvert` et `confirmé` (`ImpactQueries#forSignals`). Une alerte immédiate encore en attente (e-mail en nouvelle tentative, alerte du poste pas encore remise) dont tous les impacts visibles du destinataire ont été écartés ou résolus passe `ANNULEE` (`AlertesSansObjet`, sur l'événement `ImpactsTraites`, et contrôle à la réservation dans `EnvoiAlertes`) ; elle n'est plus envoyée ni remise et reste consultable. Si l'impact repasse ouvert, la même ligne d'alerte est remise en attente (clé d'idempotence ALR-03 inchangée). Nouvel état d'alerte `ANNULEE` (migration V20) [PROPOSITION P-17].
- Une corrélation ne publie au moteur d'alertes que les impacts nouveaux, rouverts, dont le niveau change ou portés par une nouvelle version de l'avis : la recorrélation quotidienne d'un projet sans changement (J4) ne produit aucune alerte, et la réactivation d'un abonnement ne rejoue pas les signaux corrélés pendant sa suspension (ABO-07-S2, J7) [PROPOSITION PI-4 de `automatiser-chaine-veille`, intégration du 2026-11-19 ; `chain/SubscriptionTest#abo07S1EtS2SuspensionPuisReactivationSansRejeu`, parcours `e2e/tests/chaine-automatisee.spec.ts`].

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-1 | Quel serveur d'envoi d'e-mails : relais du Synology ou service externe ? | répondue le 2026-11-16 : relais SMTP du Synology (DSM), configuré par variables d'environnement ; aucun service externe payant [DÉCISION] | porteur du projet |
| Q-2 | Faut-il un canal de messagerie d'équipe (Teams, Slack, Matrix) ? | répondue le 2026-11-16 : non : aucun canal de messagerie d'équipe pour l'instant [DÉCISION] | porteur du projet |
| Q-3 | À quelle heure et quel jour partent les résumés ? | répondue le 2026-11-16 : résumé quotidien à 08:00 et hebdomadaire le lundi à 08:00 (Europe/Paris), paramétrables [DÉCISION] | Product Owner |
| Q-4 | La cible « alerte en moins d'une heure » est-elle validée ? | non : cible qualité | Product Owner |
| Q-5 | Les résumés doivent-ils reprendre les actualités de la veille web (ALR-04-S1 « résumé hebdomadaire de l'actualité ») ? | ouverte : aujourd'hui seuls les éléments ayant un impact de sécurité sont diffusés [PROPOSITION] | Product Owner |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| ALR-01 | ALR-01-S1, ALR-01-S2 | DC-1 | 2.2 | `AlertesTest` (`alr01S1EtAlr06S1…`, `alr01S2…`), `RegleAlerteTest` |
| ALR-02 | ALR-02-S1 | DC-1 | 2.2 | `AlertesTest.alr02S1…` |
| ALR-03 | ALR-03-S1 | DC-1 | 2.1, 2.3 | `AlertesTest.alr03S1…`, `AlertesTest.cleDIdempotence…` |
| ALR-04 | ALR-04-S1, ALR-04-S2 | DC-1 | 3.1 | `AlertesTest.alr04S1…`, `alr04S2…`, `horairesDesResumes…` (signaux de sécurité ; Q-5) |
| ALR-05 | ALR-05-S1 | DC-1 | 2.4 | `AlertesTest.alr05S1…`, `qs10…` |
| ALR-06 | ALR-06-S1, ALR-06-S2 | DC-1 | 1.1, 3.2, 3.3 | `AlertesTest.alr01S1EtAlr06S1…`, `EchecSmtpTest`, `RelaisMuetTest`, `AlertesTest.contrat…`, `rls…`, `cloisonnement…` |
| ALR-07 | ALR-07-S1, ALR-07-S2 | `automatiser-chaine-veille` : D5, DC-5 | 3.4 (de ce changement) | `alerting/AlertesImpactsTraitesTest` — vérifié le 2026-11-19 (intégration) |
| ALR-08 | ALR-08-S1 à ALR-08-S4 | `durcir-autorisations-et-debits` D2 | 2.1, 2.2 | `AdresseVerifieeTest` (adresse non vérifiée, vérifiée, modifiée, migration puis reconnexion) |
| ALR-09 | ALR-09-S1 à ALR-09-S3 | `alimenter-inventaire-et-completer-ihm` D5 | 4.1 à 4.3 | `LiensIhmTest` (`alr09S1LienDUnSignal`, `alr09S2LiensDuRadarEtDesOpportunites`), `AlertesTest`, `argos-ui/tests/liens-alertes.test.mjs` (ALR-09-S3), `argos-ui/tests/vues/signal.spec.ts` |

Tests : `argos-server/src/test/java/io/argos/alerting/`. Matrice détaillée : [`tasks.md`](/openspec/changes/archive/2026-11-16-diffuser-alertes-et-digests/tasks.md#matrice-de-traçabilité).
