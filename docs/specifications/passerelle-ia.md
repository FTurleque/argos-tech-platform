# Fiche capacité : `passerelle-ia`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `IAG` |
| Spec de référence | [`/openspec/specs/passerelle-ia/spec.md`](/openspec/specs/passerelle-ia/spec.md) |
| Changements archivés | [`2026-11-17-encadrer-appels-ia`](/openspec/changes/archive/2026-11-17-encadrer-appels-ia/proposal.md) |
| Statut de la capacité | livrée |
| Composants (arc42 §5) | `ai-gateway` (`io.argos.aigateway`), `audit` (`JournalAppelsIa`), modules appelants `alerting`, `signal.web`, `impact` |

## Objectif

[PROPOSITION — `encadrer-appels-ia`] Utiliser l'IA là où elle apporte de la valeur, sans fuite de données, sans dérive de charge et sans jamais lui laisser une décision. [EXISTANT] Le fournisseur est un modèle local servi par Ollama (licence MIT), coût nul ; ce fournisseur remplace celui d'ADR-0004 (Claude) : [ADR-0029](../architecture/adr/0029-passerelle-ia-ollama-local.md) (Accepté) remplace ADR-0004 ([DÉCISION] — porteur du projet, 2026-11-18).

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Module `alerting` | demande le résumé d'un avis public pour une alerte (`ResumeIaPasserelle`, tâche `resume-alerte`) | [EXISTANT] — `ParcoursIaTest` |
| Module `signal.web` | demande le résumé d'une actualité web à la demande de l'utilisateur (tâche `resume-actualite`) | [EXISTANT] — `ParcoursIaTest` |
| Module `impact` | demande des suggestions de projets après la corrélation (tâche `analyse-correlation`), désactivé par défaut | [EXISTANT] — `ParcoursIaTest` |
| Module `regintel` | fiche réglementaire (tâche `fiche-reglementaire`) : prompt publié, appelant à brancher | [PROPOSITION] — `suivre-veille-reglementaire` |
| Exploitant (porteur du projet) | fixe le plafond, les quotas et le modèle par tâche ; active Ollama (profil `ia`) | [DÉCISION] — Q-1 à Q-3, répondues le 2026-11-16 |
| Fournisseur de modèles (Ollama local) | exécute les requêtes sur le NAS | [DÉCISION] — ADR-0029 (Accepté) |

## Périmètre et exclusions

Inclus :

- [EXISTANT] Passerelle, registre de prompts versionnés, classes de données, filtrage des secrets et des données personnelles, plafond et quotas journaliers, validation des sorties, journal des appels, mention IA.

Exclus :

- Les embeddings et la recherche sémantique (ADR-0015 : différés).
- Un fournisseur distant ou payant.

## Préconditions et déclencheurs

[EXISTANT] Aucune clé d'API. L'IA est désactivée par défaut (`argos.ia.active=false`, variable `ARGOS_IA_ACTIVE`), comme le service `ollama` de `compose.yaml` (profil `ia`) ; activée, elle appelle `argos.ia.ollama.url` (variable `ARGOS_OLLAMA_URL`, défaut `http://localhost:11434` ; `http://ollama:11434` dans `compose.yaml`). Désactivée, aucune demande n'est tracée ni comptée. Activée sans Ollama joignable, chaque demande est refusée comme indisponible et tracée, sa réservation de quota est rendue, et Argos fonctionne en mode déterministe. Déclencheurs : création d'une alerte immédiate, `POST /api/v1/veille-web/actualites/{id}/resume-ia`, corrélation d'un signal (si `argos.ia.analyse-correlation.active=true`).

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Demande (`DemandeIa`) | tâche (prompt du registre), espace imputé, entrées nommées et classées, sources | classes PUBLIC ou INTERNAL seulement (IAG-02) ; secrets refusés, données personnelles masquées ; chaque entrée bornée à 8 000 caractères | [EXISTANT] |
| Message au modèle | consignes système du prompt versionné ; entrées entre balises `<donnees>` dans le message utilisateur, chevrons neutralisés | le contenu externe ne peut ni fermer sa balise ni rejoindre les consignes (R-11) | [EXISTANT] |
| Réponse (`ReponseIa`) | issue, champs validés, mention « produit par l'IA », sources, modèle, version du prompt | objet JSON conforme au schéma de la tâche, champs hors schéma ignorés (IAG-04) | [EXISTANT] |
| Résumé d'actualité (API) | `etat`, `methode` (`IA_V1` ou `EXTRAIT_V1`), `texte`, `mention`, `sources` | sans résultat IA : extrait déterministe, sans mention | [EXISTANT] |
| Contenu d'alerte (API, e-mail, IHM) | `resumeIa`, `etatResumeIa`, `mentionResumeIa` et les sources des éléments ; l'e-mail écrit « Résumé (produit par l'IA, à vérifier sur les sources) » à partir de `mentionResumeIa` | mention présente dès qu'un résumé l'est (IAG-05) | [EXISTANT] |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Appel tracé par la passerelle : modèle, version du prompt, jetons, coût (0 €), empreinte SHA-256 des entrées après filtrage (données personnelles masquées), issue ; aucune copie des données | IAG-01 | ADR-0029 (Accepté, remplace ADR-0004) ; décision D2 | [EXISTANT] — `PasserelleIaTest` |
| Seules PUBLIC et INTERNAL sortent ; jamais de secrets, de jetons ni de données personnelles | IAG-02 | Q-4, répondue le 2026-11-16 | [EXISTANT] — `PasserelleIaTest`, `FiltreDonneesTest` |
| Plafond de 1 000 appels par jour pour l'instance et de 100 par jour et par espace, paramétrables ; au-delà, aucun appel et traitement poursuivi sans IA | IAG-03 | Q-1, Q-2, répondues le 2026-11-16 | [EXISTANT] — `PasserelleIaTest`, `ParcoursIaTest` |
| Modèle `qwen2.5:1.5b` par défaut, remplaçable par tâche (`argos.ia.modeles.<tâche>`) | IAG-01 | Q-3, répondue le 2026-11-16 | [EXISTANT] — `PasserelleIaTest` |
| Sortie validée par schéma, rejetée et journalisée sinon | IAG-04 | ADR-0029 (Accepté, remplace ADR-0004) ; QS-15 | [EXISTANT] — `PasserelleIaTest` |
| Mention « produit par l'IA » et sources avec tout contenu produit | IAG-05 | ADR-0029 (Accepté, remplace ADR-0004) ; AFF-02 | [EXISTANT] — `ParcoursIaTest`, `argos-ui/tests/resume-ia.test.mjs` |
| Prompt modifié sans nouvelle version : démarrage refusé | IAG-01 | décision locale | [EXISTANT] — `RegistrePrompts` |

## Droits d'accès et restrictions

[EXISTANT] Seuls les modules d'Argos appellent la passerelle, par son contrat (`PasserelleIa`, `DemandeIa`, `ReponseIa`) ; l'adaptateur Ollama et les quotas restent internes (règle ArchUnit `seuleLaPasserelleConnaitLeFournisseur`). Le résumé d'une actualité n'est produit que pour une actualité visible de l'appelant sous le rôle `argos_app` (404 et refus journalisé sinon), partage la limite de débit de la veille web et est imputé au quota journalier du compte demandeur (`argos.ia.quota-compte-jour`, 20 par défaut) ; un résumé produit est gardé en mémoire (500 au plus, 24 h) pour la même actualité, le même contenu, la même version de prompt et le même modèle, et sert sans nouvel appel tout compte qui voit l'actualité. Les tables `ia_appel`, `ia_quota_jour` et `ia_prompt` ne sont accessibles qu'à l'utilisateur de connexion : `argos_app` n'y a aucun droit (vérifié par `PasserelleIaTest`).

## Effets de bord observables

[EXISTANT] Appels HTTP au serveur Ollama configuré (aucune redirection suivie, aucun mandataire, réponse bornée à 1 Mio et au délai `argos.ia.delai`) ; lignes du journal `ia_appel` ; compteurs journaliers `ia_quota_jour` ; empreintes des prompts dans `ia_prompt`.

## Qualités mesurables

- [EXISTANT] QS-08 : plafond et quotas vérifiés par compteurs atomiques (`PasserelleIaTest`, `ParcoursIaTest`).
- [EXISTANT] QS-09 : aucune entrée CONFIDENTIAL, RESTRICTED ou contenant un secret ne parvient au fournisseur simulé.
- [EXISTANT] QS-15 : sorties non JSON, hors schéma, vides, trop longues ou de date non ISO rejetées.
- [QUESTION] Latence et mémoire de `qwen2.5:1.5b` sur le DS1522+ : non mesurées (ADR-0029, méthode de validation).

## Diagrammes

### DF-IAG-1 — Un résumé est-il produit ?

- **Question** : dans quels cas un élément reçoit-il un résumé produit par l'IA ?
- **Type** : organigramme
- **Portée** : comportement implémenté
- **Exigences** : IAG-02, IAG-03, IAG-04

```mermaid
flowchart TD
    A["Résumé demandé"] --> B{"Données PUBLIC<br/>ou INTERNAL, sans secret ?"}
    B -- "non" --> X["Pas de résumé<br/>refus journalisé"]
    B -- "oui" --> C{"Plafond de l'instance et<br/>quota de l'espace disponibles ?"}
    C -- "non" --> X
    C -- "oui" --> D["Appel au modèle local"]
    D --> E{"Sortie conforme<br/>au schéma ?"}
    E -- "non ou modèle injoignable" --> X
    E -- "oui" --> F["Résumé diffusé<br/>marqué IA, avec sources"]
```

À chaque refus, l'élément est diffusé sans résumé : l'IA n'est jamais bloquante.

## Critères d'acceptation

Scénarios IAG-01-S1 à IAG-05-S1 du changement ; QS-08, QS-09, QS-15. Preuves : matrice de [`tasks.md`](/openspec/changes/archive/2026-11-17-encadrer-appels-ia/tasks.md#matrice-de-traçabilité).

## Interprétations introduites par un changement

- [PROPOSITION] Refus au-delà du plafond plutôt que bascule vers un autre modèle (décision D1).
- [PROPOSITION] Le résumé d'une alerte est imputé au premier espace concerné dont le quota n'est pas épuisé ; une actualité personnelle n'est imputée qu'au plafond de l'instance.
- [PROPOSITION] Les secrets font refuser toute la demande ; les données personnelles (adresses électroniques, numéros de téléphone, IBAN) sont masquées, leur nombre seul étant tracé.
- [PROPOSITION] L'analyse complémentaire de la corrélation, qui transmet des noms de projets (INTERNAL), est désactivée par défaut ; elle ne mêle jamais deux espaces dans un appel.
- [PROPOSITION] Aucun nouvel essai automatique après une panne du modèle : l'élément reste sans résumé.
- [PROPOSITION] L'IA est désactivée par défaut, comme le service `ollama` ; une réservation est rendue quand le fournisseur n'a pas pu être joint (connexion refusée ou impossible, adresse introuvable), pas quand il a répondu en erreur ou trop tard.
- [PROPOSITION] Une demande explicite d'un utilisateur (résumé d'une actualité) est aussi imputée à un quota journalier par compte (20 par défaut, issue `QUOTA_COMPTE`), et un résumé d'actualité produit est gardé en mémoire : un compte ne peut pas épuiser seul le plafond de l'instance.
- [PROPOSITION] L'empreinte des entrées est calculée après filtrage : deux entrées qui ne diffèrent que par une donnée personnelle masquée ont la même empreinte.

## Écarts constatés

- Le mode dégradé, la mention et les sources sont vérifiés par l'API REST et des tests unitaires de l'IHM ; aucun test de navigateur ni de modèle réel n'est exécuté.
- Aucun outil MCP ne restitue de contenu produit par l'IA : la mention via MCP (IAG-05-S1) est sans objet tant qu'aucun outil n'en renvoie.
- Les suggestions de l'analyse complémentaire de la corrélation ne sont restituées ni par l'API ni par l'IHM, et ne portent pas la mention IAG-05 : activée, l'analyse consomme les quotas des espaces sans résultat visible. Ses appels se font à la suite, un par espace, dans le fil de la corrélation, chacun borné par `argos.ia.delai`.
- L'alerte attend son résumé au plus `argos.alerting.resume-ia.delai-max` (10 s), alors qu'un appel à Ollama peut durer `argos.ia.delai` (60 s) et que le service traite une requête à la fois : sur un NAS, un résumé tardif est abandonné (état `INDISPONIBLE`) alors que l'appel, qui se poursuit, est compté et peut être tracé `PRODUIT`. La mesure sur le DS1522+ prévue par ADR-0029 fixera ces délais.
- Les motifs de secrets (`token|secret|jeton` suivis de `:` ou `=`), de téléphone et d'IBAN peuvent refuser ou masquer un contenu public légitime ; le traitement continue alors sans IA.
