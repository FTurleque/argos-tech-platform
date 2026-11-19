# Mesure de la corrélation sur le jeu de référence annoté

Rapport de la tâche 4.7 du changement [`formaliser-chaine-securite-poc`](/openspec/changes/formaliser-chaine-securite-poc/tasks.md) (exigences COR-01 et COR-02 de la fiche [`correlation-impact`](/docs/specifications/correlation-impact.md)). Critère comparé : baseline v2.3 §6 critère 3, « un jeu de référence permet de mesurer vrais/faux positifs et faux négatifs de la corrélation » ([baseline v2.3](../baseline-v2.3.md#6-critères-go--no-go)). ADR-0013 (Proposé) cite un seuil de pertinence de 80 %, non validé (question de la fiche `correlation-impact`).

## Méthode

| Élément | Valeur |
|---|---|
| Test | `argos-server/src/test/java/io/argos/chain/JeuReferenceCorrelationTest.java`, exécuté à chaque `mvn -B -f argos-server/pom.xml verify` ; seul : `mvn -f argos-server/pom.xml test -Dtest=JeuReferenceCorrelationTest` |
| Jeu | `argos-server/src/test/resources/fixtures/jeu-reference-correlation/` : `jeu.json` (projets et composants à signaler à l'import, vérité annotée, négatifs annotés, écarts consignés, métriques consignées), `avis/` (16 avis OSV), `sbom/` (11 SBOM CycloneDX 1.5 et 1.6), `manifestes/` (un manifeste npm, `package.manifeste.json`, importé sous le nom `package.json`) |
| Chaîne exécutée | chaîne réelle d'ARGOS, sans accès à Internet : création des projets, import des inventaires (`InventoryService`), collecte OSV (`SourceSynchronizer`) sur le serveur HTTP local des tests, corrélation déclenchée par la collecte (`CorrelationEngine`), lecture des impacts enregistrés dans `project_impact` ; puis corrélation par projet (second déclencheur) qui ne crée ni ne retire aucun impact |
| Base | PostgreSQL des Dev Services Quarkus (conteneur jetable), migrations V1 à V17 |
| Dates des SBOM | `metadata.timestamp` réécrit à l'exécution à « maintenant moins `ageJours` » : le caractère récent ou obsolète (seuil de 30 jours) ne dépend pas de la date d'exécution |
| Unité de mesure | triplet (projet, avis, composant), clé d'unicité d'un impact (COR-07) ; le composant est identifié par sa clé PURL et sa version, sans qualificatif |
| Vérité | annotée à la main depuis le texte de chaque avis et le contenu de chaque inventaire : impact attendu ou non, niveau attendu selon COR-02, caractère transitif attendu. Monde fermé : un impact observé hors de la vérité est un faux positif |
| Garde-fou | le test échoue si l'ensemble des écarts diffère des écarts consignés dans `jeu.json`, si les composants signalés à l'import d'un projet diffèrent de ceux annotés (`composantsSignales`), ou si une métrique diffère des métriques consignées, qui sont celles de ce rapport. Le rapport calculé est écrit dans `argos-server/target/jeu-reference-correlation/mesure.md`, détail par triplet compris |

### Composition du jeu

| Projet | Inventaire | Ce que le projet éprouve |
|---|---|---|
| `api-paiement` | SBOM Maven 1.5, 3 jours | versions affectées directes et transitives (log4j-core, Spring, commons-text, snakeyaml, guava, jackson-databind) ; artefact voisin non concerné (log4j-api) |
| `batch-corrige` | SBOM Maven 1.6, 5 jours | versions corrigées sur les bornes `fixed` exclues ; paquets renommés ou forks (reload4j, log4j-1.2-api) ; guava `32.0.0-jre` face à la borne `32.0.0-android` (ordre Maven des qualificatifs inconnus) |
| `legacy-log4j1` | SBOM Maven 1.5, 10 jours | borne `last_affected` incluse (log4j 1.2.17) ; versions `RELEASE` de Spring 4 ; version `22.0-android` énumérée ; version antérieure à la borne `introduced` |
| `distribution-plugins` | SBOM Maven 1.6, 1 jour | composants imbriqués (bibliothèques embarquées par 14 greffons) ; bornes de pré-version (`2.0-beta9`, `2.4.0-rc1`) ; paquet republié sous un autre groupe et version énumérée sans intervalle (`2.6.3-CUSTOM`) ; intervalles multiples |
| `api-obsolete` | SBOM Maven 1.5, 45 jours | SBOM obsolète : niveau `probable` |
| `outil-sans-purl` | SBOM Maven 1.6, 2 jours | composant concerné décrit sans PURL (groupe, nom, version, CPE), écarté de l'inventaire et signalé à l'import |
| `front-portail` | SBOM npm 1.6, 4 jours | paquets à portée (`@babel/traverse`) ; deux versions d'un même paquet ; borne `last_affected` (lodash.template, request) |
| `front-corrige` | SBOM npm 1.5, 6 jours | versions corrigées ; fork renommé corrigé (`@cypress/request` 3.0.1) ; même nom dans un autre écosystème (`pkg:pypi/semver`) |
| `front-bornes` | SBOM npm 1.6, 2 jours | versions malveillantes énumérées (ua-parser-js) ; pré-versions SemVer (`8.0.0-alpha.0` incluse, `8.0.0-alpha.4` exclue) ; ancien nom à intervalle ouvert (`babel-traverse`) ; fork renommé affecté |
| `front-manifeste` | `package.json` (repli) | inventaire de repli : niveau `probable` ; contraintes `^` et `~` : version inconnue, niveau `à vérifier` |
| `widget-sans-version` | SBOM npm 1.6, 3 jours | composant sans version |
| `outil-python` | SBOM PyPI 1.6, 2 jours | nom `PyYAML` normalisé ; même vulnérabilité publiée par deux bases (GHSA et PYSEC, alias CVE-2020-14343) |

Avis : GHSA-jfh8-c2jp-5v3q (log4j-core, CVE-2021-44228), GHSA-fp5r-v3w9-4333 (log4j 1.x), GHSA-36p3-wjmg-h94x (Spring, CVE-2022-22965), GHSA-599f-7c49-w659 (commons-text), GHSA-mjmj-j48q-9wg2 (snakeyaml), GHSA-7g45-4rm6-3mm3 (guava), GHSA-jjjh-jjxp-wpff (jackson-databind), GHSA-35jh-r3h4-6jhm (lodash), GHSA-c2qf-rxjj-qqgw (semver), GHSA-pjwm-rvh2-c87w (ua-parser-js), GHSA-67hx-6x53-jw92 (@babel/traverse), GHSA-9c47-m6qq-7p4h (json5), GHSA-p8p7-x288-28g6 (request, @cypress/request), GHSA-8q59-q68h-6hv4 et PYSEC-2021-142 (PyYAML), et l'avis dérivé ARGOS-REF-2026-0001 (GHSA-599f-7c49-w659 sans PURL déclaré, pour le niveau `à vérifier` d'un avis identifié par son seul nom).

### Métriques

- **VP / FP / FN, tous niveaux** : un impact attendu et observé, quel que soit le niveau, est un vrai positif ; un impact observé non attendu, un faux positif ; un impact attendu non observé, un faux négatif. Précision = VP / (VP + FP), rappel = VP / (VP + FN).
- **Niveaux confirmés** : même calcul en ne comptant comme positifs que les niveaux `exact` et `probable` ; un impact `à vérifier` y est traité comme une absence d'impact.
- **Par niveau** : VP = niveau attendu et observé identiques ; FP = niveau observé mais autre niveau attendu (ou aucun) ; FN = niveau attendu mais autre niveau observé (ou aucun).
- **Vrais négatifs** : paires (projet, avis, composant) annotées négatives, choisies là où une confusion est plausible (borne exclue, version corrigée, fork, autre écosystème), restées sans impact.

## Résultats du 2026-11-18

12 projets, 16 avis, 52 impacts attendus, 51 impacts observés, 36 négatifs annotés.

Mesure reprise le 2026-11-19 après la décision E3 (ordre `ComparableVersion` de `maven-artifact` pour toute comparaison Maven) : `mvn -B -f argos-server/pom.xml test -Dtest=JeuReferenceCorrelationTest` réussit et les métriques sont identiques. Le jeu ne contient aucune version Maven dont l'ordre diffère entre l'ordre générique et `ComparableVersion` (qualificatifs `dev`, `pre`, `preview`, `post`, `rev`, `r`, `c`, ou `a`, `b`, `m` sans chiffre) : ses qualificatifs Maven (`RELEASE`, `alpha`, `beta`, `rc`, `jre`, `jre17`, `android`, `CUSTOM`) sont ordonnés de la même façon par les deux ordres, hors du cas E-1 déjà tranché par l'ordre Maven.

| Mesure | VP | FP | FN | Précision | Rappel |
|---|---:|---:|---:|---:|---:|
| **Tous niveaux** | 51 | 0 | 1 | **100,0 %** | **98,1 %** |
| Niveaux confirmés (`exact`, `probable`) | 46 | 0 | 0 | 100,0 % | 100,0 % |
| `exact` | 42 | 0 | 0 | 100,0 % | 100,0 % |
| `probable` | 4 | 0 | 0 | 100,0 % | 100,0 % |
| `à vérifier` | 5 | 0 | 1 | 100,0 % | 83,3 % |

| Contrôle complémentaire | Résultat |
|---|---|
| Vrais négatifs | 36 sur 36, dont guava `32.0.0-jre` de `batch-corrige` (E-1) |
| Caractère transitif des vrais positifs | 47 corrects sur 47 annotés (inventaire de repli : transitif inconnu, non annoté) |
| Niveau des vrais positifs | aucun écart de niveau |
| Alias CVE des avis indexés (`signal_alias`) | 16 sur 16 |
| Doublons d'alias (deux signaux de même CVE sur un même composant) | 3 : PyYAML (GHSA-8q59-q68h-6hv4 et PYSEC-2021-142) ; commons-text dans `api-paiement` et `api-obsolete` (GHSA-599f-7c49-w659 et l'avis dérivé, qui en reprend les alias) |
| Corrélation par projet après la collecte | aucun impact créé ni retiré |
| Composants signalés à l'import | 1 sur les 12 imports : `org.apache.logging.log4j:log4j-core` 2.14.1 du projet `outil-sans-purl`, motif `SANS_PURL` (E-2) ; aucun autre import ne signale de composant |

Le jeu permet de mesurer vrais positifs, faux positifs et faux négatifs de la corrélation, ce que demande littéralement le critère 3 de la baseline v2.3 §6. Le jeu ne contient pas les inventaires des projets pilotes. [DÉCISION — porteur du projet, 2026-11-18] Le jeu composé ne suffit pas au critère 3 : la mesure se fait sur les inventaires des projets pilotes, avec la tâche 7.1 ; la tâche 4.7 reste ouverte et la présente mesure est une preuve partielle. Précision et rappel dépassent le seuil de 80 % cité par ADR-0013, seuil non validé.

## Écarts analysés

| Réf. | Triplet | Nature | Analyse | Suite |
|---|---|---|---|---|
| E-1 | `batch-corrige`, GHSA-7g45-4rm6-3mm3, `pkg:maven/com.google.guava/guava@32.0.0-jre` | vrai négatif ; faux positif `à vérifier` sous l'ordre générique | La borne `fixed` de l'avis vaut `32.0.0-android`. Les qualificatifs `jre` et `android` sont inconnus de l'ordre générique de `VersionComparator`, qui ne classe pas `32.0.0-jre` face à `32.0.0-android`. Pour l'écosystème Maven, toute comparaison suit `ComparableVersion` de la bibliothèque `maven-artifact` : les qualificatifs inconnus suivent la version publiée, par ordre lexical ; `32.0.0-jre` suit `32.0.0-android` et n'est pas affectée, alors que `31.1-jre` l'est. Vérifié par `MatchRulesTest#cor01S5GuavaJreFaceALaBorneAndroid`, `#mavenQualificatifsInconnus` et ce jeu ; hors Maven, un qualificatif inconnu laisse la plage non interprétable (`MatchRulesTest#qualificatifInconnuHorsMavenResteAVerifier`, `#ordreGeneriqueInchangeHorsMaven`) | résolu par la décision D5 du porteur du projet, 2026-11-18 (question Q-COR-3 tranchée, COR-01-S5), révisée par la décision E3 du 2026-11-19 [DÉCISION] |
| E-2 | `outil-sans-purl`, GHSA-jfh8-c2jp-5v3q, `org.apache.logging.log4j:log4j-core:2.14.1` sans PURL | faux négatif `à vérifier`, signalé à l'import | Le composant n'est décrit que par groupe, nom, version et CPE. INV-01 identifie chaque composant par son PURL : `CycloneDxParser` l'écarte de l'inventaire et aucune correspondance, même `à vérifier`, n'est possible. L'import le signale (nom, groupe, version, motif `SANS_PURL`) dans son résultat et dans l'inventaire restitué par l'API (INV-01-S3) ; le test vérifie ce signalement. Limite de l'inventaire et non défaut de la corrélation | signalé à l'import selon la décision D6 du porteur du projet, 2026-11-18, et affiché dans la vue d'inventaire de l'IHM selon la décision E4 du 2026-11-19 ; l'inventaire reste fondé sur le PURL [DÉCISION] |

Observation hors écart : un même composant reçoit un impact par avis, y compris quand deux avis décrivent la même vulnérabilité sous des identifiants différents (alias). Chaque impact est juste à l'unité de mesure (triplet) ; le regroupement par alias relève de la restitution et des alertes.

Hors de l'ordre des qualificatifs Maven inconnus (E-1, décisions D5 et E3), le jeu ne révèle aucun défaut de corrélation.

## Limites

- **Jeu composé, non issu des projets pilotes** : les avis sont réels, mais les inventaires sont des SBOM de test écrites pour couvrir des cas représentatifs. Les proportions de cas (bornes, forks, versions corrigées) sont choisies et non tirées d'un portefeuille : les taux mesurés ne prédisent pas ceux d'un parc réel. La mesure sur les SBOM des deux projets pilotes relève de la tâche 7.1 ([PROPOSITION] : ajouter leurs inventaires au jeu dès qu'ils existent).
- **Annotation par un seul analyste**, sans double lecture ; les motifs de chaque annotation figurent dans `jeu.json`.
- **Avis figés au 2026-11-18** : les avis OSV évoluent (versions énumérées, intervalles) ; le jeu mesure la corrélation sur ces versions et non sur l'état courant de la base OSV.
- **Écosystèmes** : Maven, npm et un cas PyPI ; ni Go, ni NuGet, ni crates.io, ni intervalles `GIT`. Seul `package.json` est lu comme manifeste de repli (pas de `pom.xml`).
- **CERT-FR** : ses avis ne déclarent ni PURL ni plage de versions et n'alimentent pas la corrélation (enrichissement par CVE, `io.argos.vulnintel.CertFrSource`) ; ils sont hors du jeu.
- **Vieillissement** : les niveaux sont calculés à la corrélation ; le jeu ne mesure pas le passage d'une SBOM au-delà de 30 jours sans nouvelle corrélation (écart déjà consigné dans la fiche `correlation-impact`).

## Provenance et licences

| Données | Source | Licence |
|---|---|---|
| 14 avis GHSA | API OSV (`https://api.osv.dev/v1/vulns/<id>`), lus le 2026-11-18, reproduits sans modification ; données de la GitHub Advisory Database | Creative Commons Attribution 4.0 International (CC-BY 4.0), © GitHub, Inc. et contributeurs |
| PYSEC-2021-142 | API OSV, lu le 2026-11-18, reproduit sans modification ; données de la PyPI Advisory Database (PyPA) | CC-BY 4.0, © Python Packaging Authority et contributeurs |
| ARGOS-REF-2026-0001 | adaptation de GHSA-599f-7c49-w659 : identifiant remplacé, alias complétés de l'identifiant GHSA, champs `purl` retirés | CC-BY 4.0 (adaptation signalée) |
| SBOM et manifeste | écrits pour ce jeu au format CycloneDX 1.5 / 1.6 et `package.json` ; projets, groupes `com.exemple.*` et numéros de série fictifs ; noms et versions de paquets publics, données factuelles | données de test du dépôt |

Le détail figure dans `argos-server/src/test/resources/fixtures/NOTICE`. Aucune donnée personnelle n'est ajoutée ; les avis reproduits contiennent les crédits publics de leur source.
