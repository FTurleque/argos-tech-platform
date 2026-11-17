# ADR-0030 — Veille réglementaire validée par l'expert métier, domaines propres à chaque espace et données ouvertes JORF

- **Statut** : Proposé
- **Date** : 2026-11-17
- **Décideurs** : porteur du projet (questions Q-1 à Q-4 du changement `suivre-veille-reglementaire`, répondues le 2026-11-17) ; mise à jour d'ADR-0018 soumise à validation humaine
- **Remplace** : [ADR-0018](0018-veille-reglementaire-mkp.md) (Proposé), à l'acceptation du présent ADR
- **Remplacé par** : —

## Contexte

- [ADR-0018](0018-veille-reglementaire-mkp.md) (Proposé) intègre la veille réglementaire dans un module `reg-intel` avec une validation obligatoire par un « MKP » et des domaines métier d'un éditeur de logiciels RH.
- Depuis [ADR-0025](0025-produit-ouvert-projet-personnel.md) (Accepté), Argos est un produit ouvert à tous les publics : le rôle s'appelle « expert métier » et les domaines ne sont plus ceux d'un éditeur.
- Le porteur a tranché le 2026-11-17 (changement [`suivre-veille-reglementaire`](/openspec/changes/suivre-veille-reglementaire/proposal.md)) : source principale = jeux de données LEGI et JORF de la DILA sous Licence Ouverte, sans l'API PISTE de Légifrance (Q-1) ; rappels J-90, J-30 et J-7 paramétrables (Q-2) ; suivi externe GitHub et GitLab par les adapters `scm`, sans Jira (Q-3) ; aucun dictionnaire type (Q-4).
- [ADR-0021](0021-double-licence.md) impose des composants gratuits sous licence permissive ; [ADR-0029](0029-passerelle-ia-ollama-local.md) (Proposé) rend l'IA facultative.

## Critères de décision

1. Aucune analyse diffusée au-delà des experts avant validation humaine (ADR-0018, PRF-06).
2. Domaines et règles de rattachement configurables par chaque espace (ADR-0025).
3. Source gratuite, réutilisable dans un produit diffusé, avec mention de la source.
4. Fonctionnement complet sans IA (mode dégradé d'ADR-0029).
5. Cloisonnement par espace garanti par la base (ADR-0016).

## Options considérées

### Option A — Dictionnaire global et validation par un rôle de plateforme

- avantages : configuration unique ;
- inconvénients : inadapté à des publics différents ; un rôle de plateforme donnerait un accès implicite aux espaces (contraire à ADR-0016).

### Option B — Domaines, dictionnaire et experts par espace ; validation par un expert du domaine

- avantages : chaque espace qualifie selon ses propres domaines ; l'expert est un membre désigné de l'espace, sans nouveau rôle global ; la RLS restreint la fiche non validée aux experts de ses domaines ;
- inconvénients : chaque espace construit son dictionnaire ; un espace sans expert ne valide aucune fiche.

### Option C — Source : API PISTE de Légifrance

- avantages : recherche ciblée, données à jour ;
- inconvénients : conditions d'usage non vérifiées pour un produit diffusé ; compte et jeton à gérer.

## Décision

**Proposition : option B, avec les données ouvertes JORF de la DILA.**

- Le module `reg-intel` collecte chaque jour les archives JORF de la DILA (Licence Ouverte), sans l'API PISTE ; un texte est enregistré une seule fois par identifiant officiel et un jour manqué est repris au retour de la source.
- Chaque espace définit ses domaines métier, leurs règles déterministes (nature, ministère, préfixe NOR, code modifié, mot-clé) et leurs experts, désignés par un administrateur parmi les membres contributeurs ou administrateurs (module `catalog`, paquetage `dictionnaire`).
- La qualification est déterministe d'abord ; l'IA ne propose un domaine, marqué avec sa confiance, que si aucune règle ne s'applique, dans une limite par espace et par collecte.
- La fiche (résumé, articles modifiés, dates de publication et d'effet, domaines, liens officiels) reste visible des seuls experts de ses domaines jusqu'à sa validation ; corrections et décisions sont historisées ; toute fiche rappelle qu'elle ne constitue pas un avis juridique.
- Le suivi d'un texte suit le cycle nouveau → en analyse → sans impact / impact produit → spécifié → livré ; le passage à « spécifié » exige une demande d'évolution GitHub ou GitLab associée.

Le statut et le texte d'ADR-0018 restent inchangés jusqu'à l'acceptation du présent ADR.

## Conséquences positives

- aucune fiche non validée ne sort du cercle des experts, ni par l'API, ni par le serveur MCP, ni par un rappel ;
- coût nul, aucune clé ni aucun compte externe ;
- la veille fonctionne sans IA : fiche préparée sans résumé, rédigé par l'expert.

## Conséquences négatives

- les archives JORF quotidiennes sont volumineuses et publiées en soirée, après le passage de la collecte du matin : chaque collecte relit les trois derniers jours (`argos.reglementaire.relecture-jours`) et ne télécharge que les archives encore non lues ; une archive publiée au-delà de cette fenêtre n'est lue que par un rejeu de l'administrateur plateforme ;
- un jour dont les archives échouent cinq fois de suite (`argos.reglementaire.tentatives-max`) est abandonné pour ne pas bloquer les jours suivants ; il se rejoue à la demande ;
- un espace sans dictionnaire ne reçoit aucune fiche ;
- la qualité des propositions IA d'un petit modèle local reste à mesurer.

## Conséquences neutres / compromis

- les liens vers la version consolidée pointent vers Légifrance ; les archives LEGI (codes consolidés) ne sont pas lues dans cette version ;
- les jours de rappel se règlent par espace.

## Méthode de validation

- tests de contrat sur des archives JORF composées au format DILA (`LecteurJorfTest`, `CollecteReglementaireTest`) ;
- tests d'intégration de la qualification, de la fiche, de la validation, de la RLS et des rappels (`VeilleReglementaireTest`) et parcours de bout en bout (`ParcoursReglementaireTest`) ;
- revue des conditions de la Licence Ouverte (mention de la source et de la date de dernière mise à jour des données sur chaque fiche) ;
- mesure, sur le NAS, de la durée d'une collecte réelle (à réaliser).

## Traçabilité

- **Exigences** : REG-01 à REG-08 ([fiche `veille-reglementaire`](../../specifications/veille-reglementaire.md)) ; PRF-06 ; MCP-07
- **Scénarios qualité** : aucune diffusion avant validation (ADR-0018) ; fonctionnement sans IA (ADR-0029)
- **Diagrammes** : DC-1, DC-2 du changement `suivre-veille-reglementaire` ; DF-REG-1 ; arc42 §6.6
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-server/src/main/java/io/argos/regintel/`, `argos-server/src/main/java/io/argos/ingestion/legi/`, `argos-server/src/main/java/io/argos/catalog/dictionnaire/`, `argos-server/src/main/resources/db/migration/V16__veille_reglementaire.sql`

## Notes

ADR-0018 reste la référence tant que le présent ADR n'est pas accepté ; l'acceptation le marquera **Remplacé**.
