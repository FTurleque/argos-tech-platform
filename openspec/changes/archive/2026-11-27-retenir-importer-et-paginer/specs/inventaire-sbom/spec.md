# Delta de spécification : inventaire-sbom

## ADDED Requirements

### Requirement: INV-14 Document inchangé conservé et écriture par lot
Le système DOIT (MUST) conserver sans écriture l'inventaire d'un dépôt lorsque le document lu sur sa forge (SBOM ou manifestes) est identique à celui de son dernier import réussi. Un import DOIT (MUST) écrire ses composants en un nombre d'instructions indépendant de leur nombre, et NE DOIT PAS (MUST NOT) réécrire un composant inchangé.
Source : /docs/audit/2026-11-21/findings.md, AUD-PER-19 et AUD-PER-20 (actions). Statut : [PROPOSITION].

#### Scenario: INV-14-S1 Passage nocturne sans changement
- **GIVEN** un dépôt importé dont la SBOM n'a pas changé sur la forge
- **WHEN** le passage quotidien lit de nouveau ce dépôt
- **THEN** le résultat du dépôt est `KEPT`, motif « document inchangé depuis le dernier import »
- **AND** l'inventaire, ses composants et leurs occurrences ne sont pas réécrits, et aucune recorrélation n'est lancée

#### Scenario: INV-14-S2 Document modifié
- **GIVEN** le même dépôt dont la SBOM ajoute un composant
- **WHEN** le passage quotidien lit ce dépôt
- **THEN** le résultat est `IMPORTED` et l'inventaire contient le nouveau composant
- **AND** les composants déjà présents gardent leur identifiant

#### Scenario: INV-14-S3 Import manuel toujours appliqué
- **GIVEN** un dépôt principal dont le dernier import porte un document donné
- **WHEN** un contributeur importe manuellement ce même document
- **THEN** le résultat est `IMPORTED` et l'inventaire est réécrit

#### Scenario: INV-14-S4 Gros document
- **GIVEN** une SBOM de plusieurs milliers de composants, dont des doublons de même paquet et même version
- **WHEN** elle est importée
- **THEN** chaque paquet et version donne un seul composant, direct si une occurrence au moins est directe
- **AND** le nombre d'instructions SQL de l'écriture ne dépend pas du nombre de composants

## MODIFIED Requirements

### Requirement: INV-09 Recorrélation à chaque import
Le système DOIT (MUST), après chaque import réussi de l'inventaire d'un projet, recorréler pour ce projet les signaux de sécurité déjà connus : les impacts nouvellement vérifiés sont créés et les impacts qui ne sont plus vérifiés sont traités selon COR-11.
Source : décision J4 du porteur du projet (2026-11-19, Q-INV-2) ; baseline v2.3 §3. Statut : [PROPOSITION].

#### Scenario: INV-09-S1 Nouveau composant affecté
- **GIVEN** un signal connu qui affecte `pkg:npm/exemple@<2.0.0` et un projet dont l'inventaire ne contient pas ce paquet
- **WHEN** un import ajoute `pkg:npm/exemple@1.5.0` à l'inventaire du projet
- **THEN** un impact `ouvert` relie ce signal, ce projet et ce composant, sans nouvelle collecte de l'avis

#### Scenario: INV-09-S2 Composant retiré
- **GIVEN** un impact `ouvert` sur un composant d'un projet
- **WHEN** un import produit un inventaire qui ne contient plus ce composant
- **THEN** l'impact n'est plus compté parmi les impacts ouverts du projet et est traité selon COR-11

#### Scenario: INV-09-S3 Import refusé ou inventaire conservé
- **GIVEN** un projet avec un inventaire de référence et des impacts
- **WHEN** le passage quotidien refuse la SBOM du dépôt (INV-05), conserve l'inventaire faute de nouvelle SBOM, ou lit un document identique à celui du dernier import (INV-14)
- **THEN** aucune recorrélation n'est lancée pour ce projet
- **AND** ses impacts et leurs états sont inchangés

#### Scenario: INV-09-S4 Échec de la recorrélation
- **GIVEN** un import réussi pour un projet
- **WHEN** la recorrélation qui le suit échoue
- **THEN** l'inventaire importé reste l'inventaire de référence
- **AND** l'échec est journalisé et la recorrélation du projet est relancée au passage planifié suivant
