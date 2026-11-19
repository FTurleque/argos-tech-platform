# Audit d'accessibilité des mises en page

Rapport de la tâche 2.6 du changement [`personnaliser-profils-et-affichage`](/openspec/changes/archive/2026-11-19-personnaliser-profils-et-affichage/tasks.md) (exigence AFF-05, scénario AFF-05-S1). Il couvre les quatre mises en page du catalogue initial : `classique`, `journal`, `fantaisie` et `gazette` (ADR-0023, Accepté).

## Référentiel et périmètre

| Élément | Valeur |
|---|---|
| Référentiel | RGAA 4.1.2, aligné sur WCAG 2.1 niveau AA (décision Q-4 du 2026-11-16, fiche [`affichage-personnalise`](/docs/specifications/affichage-personnalise.md)). Les seuils de contraste sont ceux du WCAG 2.1 et 2.2 AA : 4,5:1 pour le texte, 3:1 pour le grand texte (24 px, ou 18,66 px en gras) et les indicateurs non textuels |
| Écran de référence | une mise en page affichant le jeu d'exemple `argos-ui/src/contenu/apercu.ts` : six éléments fictifs, chaque nature et chaque gravité de l'échelle, un élément sans gravité, sans projet, sans lien ou sans date, deux résumés produits par l'IA, une validation humaine avec garde-fou et mention de licence, un élément rangé dans deux rubriques, un élément sans rubrique retenu par un abonnement personnel. Le même jeu sert à l'aperçu de l'écran « Mise en page » et au banc d'essai `argos-ui/banc/index.html` |
| Composants audités | `argos-ui/src/mises-en-page/*/MiseEnPage*.vue`, `MiseEnPage.vue`, `Titre.vue`, `argos-ui/src/contenu/MentionsObligatoires.vue` |
| Écran complémentaire | « Ma veille » (`#/veille`) du portail web : veille réelle restituée par `GET /api/v1/me/veille` à un utilisateur de test (deux avis de sécurité, deux fiches réglementaires validées dont une au résumé produit par l'IA), affichée dans chacune des quatre mises en page |
| Hors périmètre | écrans de l'application autres que les mises en page (connexion, recherche, alertes), y compris l'en-tête et la navigation qui entourent « Ma veille » |
| Date | 2026-11-17 |
| Moyens | contrôles scriptés sans axe-core (licence MPL-2.0 écartée, ADR-0021) : `node --test` et Playwright 1.61.1 (Chromium révision 1228) ; revue manuelle des rendus et des sources |

## Méthode

### Contrôles automatisés sans navigateur (`npm --prefix argos-ui test`)

| Fichier | Contrôle | Critères |
|---|---|---|
| `argos-ui/tests/contrastes.test.mjs` | formule de luminance et de contraste WCAG ; chaque couple texte/fond déclaré dans la palette de chaque mise en page atteint son seuil ; toute couleur des feuilles de style est un jeton de la palette (aucune couleur en dur) ; tout jeton employé en couleur ou en fond figure dans un couple contrôlé ; transitions et animations seulement sous `prefers-reduced-motion: no-preference` ; contour de focus de 3 px sur les liens | RGAA 3.2, 3.3, 10.7, 13.8 |
| `argos-ui/tests/mises-en-page.test.mjs` | rendu côté serveur (Vite et `vue/server-renderer`) des quatre mises en page sur le même jeu : mêmes faits, mêmes liens et mêmes mentions ; mention « résumé produit par l'IA » sur chaque résumé IA ; jeu d'exemple annoncé ; liens `http`/`https` seulement, `rel="noopener noreferrer"`, nouvelle fenêtre annoncée ; liens nommés ; hiérarchie de titres sans saut au niveau demandé ; région et articles nommés ; listes ; dessins décoratifs `aria-hidden` ; aucun `tabindex` positif | RGAA 1.1, 1.2, 6.1, 6.2, 8.9, 9.1, 9.2, 9.3, 12.8 |
| `argos-ui/tests/polices.test.mjs` | polices embarquées déclarées, enregistrées et livrées avec leur avis ; aucune police chargée depuis un service externe | ADR-0023 point 5 |
| `argos-ui/tests/architecture.test.mjs` | couche mise en page sans client API ni appel réseau, mentions obligatoires dans chaque mise en page | AFF-02 |

### Contrôles dans le navigateur (`npm --prefix e2e run test:accessibilite`)

`e2e/accessibilite/mises-en-page.spec.ts`, sur le banc d'essai servi par Vite (boucle locale, sans serveur ARGOS). Pour chaque mise en page :

- polices embarquées chargées (`document.fonts`), aucune requête vers un autre hôte que `127.0.0.1` ;
- contraste de **chaque nœud de texte visible**, décor compris, mesuré sur la couleur calculée et le fond effectif (composition des fonds des ancêtres), seuil selon la taille et la graisse rendues ;
- ordre de tabulation identique à l'ordre des liens dans le document, contour de focus visible d'au moins 2 px, de contraste au moins 3:1 sur le fond effectif du lien, mesuré dans le navigateur ;
- largeur de 320 px : aucun défilement horizontal, aucun élément hors du cadre (RGAA 10.11, WCAG 1.4.10) ;
- largeur de 375 px : éléments empilés sur une seule colonne ;
- `prefers-reduced-motion: reduce` : aucune transition ni animation, survol compris ;
- arbre d'accessibilité : région nommée, six articles, titres, liens nommés « … (nouvelle fenêtre) », décor absent de l'arbre, bandeau « Données d'exemple » présent.

Un dernier test compare les faits restitués (nature, gravité, titre, résumé, projets et niveaux, action, rubriques, mentions) par les quatre mises en page.

### Contrôles sur la veille réelle, dans le navigateur (`npm --prefix e2e test`)

`e2e/tests/veille.spec.ts`, parcours « 2.6 », sur la pile complète (serveur ARGOS en `quarkus:dev`, Keycloak du royaume de test, PostgreSQL jetable, portail web servi par Vite), sans requête hors de la boucle locale. `jules` se connecte, sa préférence désigne la mise en page, et « Ma veille » affiche sa veille réelle (jeu `e2e/donnees/veille.sql`). Les mesures sont celles du banc (`e2e/support/mesures.ts`) :

- contraste de chaque nœud de texte visible de la mise en page sur son fond effectif ;
- ordre de tabulation : depuis le début de la page, les liens de la mise en page reçoivent le focus dans l'ordre du document ; contour visible d'au moins 2 px, de contraste au moins 3:1 sur le fond effectif ;
- arbre d'accessibilité : région nommée, un article par élément de la réponse de l'API, nommé par son titre, un titre par élément, liens nommés « … (nouvelle fenêtre) », aucune annonce « Données d'exemple ».

### Revue manuelle

Revue des captures plein écran et téléphone de chaque mise en page, et des sources : ordre de lecture, langue (`lang="fr"` porté par `index.html`), pertinence des titres, distinction entre décor et information, mentions obligatoires visibles, humour limité à la forme, lisibilité des polices de titrage, lisibilité à 375 px. La revue est faite par l'équipe de développement, sans lecteur d'écran : elle reste à confirmer par le référent accessibilité (voir « Limites »).

## Résultats du 2026-11-17

| Mise en page | Couples de la palette | Contraste minimal (palette) | Contraste minimal mesuré dans le navigateur | 320 px | Tabulation et focus | Mouvement réduit | Arbre d'accessibilité |
|---|---:|---|---|---|---|---|---|
| `classique` | 19 | 5,19:1 (lien sur carte) | 5,19:1 | conforme | conforme | conforme | conforme |
| `journal` | 9 | 5,08:1 (mentions sur papier) | 5,08:1 | conforme | conforme | conforme | conforme |
| `fantaisie` | 29 | 4,97:1 (rouge sur rose) | 4,97:1 | conforme | conforme | conforme | conforme |
| `gazette` | 9 | 6,67:1 (rouge sur papier) | 6,67:1 | conforme | conforme | conforme | conforme |

- `npm --prefix argos-ui test` : 135 tests, 0 échec.
- `npm --prefix e2e run test:accessibilite` : 29 tests, 0 échec ; rapport [`e2e/resultats/accessibilite/rapport-2026-11-17.json`](/e2e/resultats/accessibilite/rapport-2026-11-17.json), captures à 375 px [`classique`](/e2e/resultats/accessibilite/classique-telephone.png), [`journal`](/e2e/resultats/accessibilite/journal-telephone.png), [`fantaisie`](/e2e/resultats/accessibilite/fantaisie-telephone.png), [`gazette`](/e2e/resultats/accessibilite/gazette-telephone.png).
- Contrastes des indicateurs non textuels (contour de focus) : 4,97:1 au minimum (`fantaisie`, carte rose).

### Veille réelle (`npm --prefix e2e test`, 2026-11-17)

| Mise en page | Contraste minimal des textes | Contraste minimal du contour de focus | Tabulation | Noms accessibles (région, quatre articles, titres, liens) |
|---|---|---|---|---|
| `classique` | 5,19:1 | 12,24:1 | conforme | conforme |
| `journal` | 5,08:1 | 6,59:1 | conforme | conforme |
| `fantaisie` | 4,97:1 | 4,98:1 | conforme | conforme |
| `gazette` | 6,67:1 | 6,67:1 | conforme | conforme |

- Deux exécutions complètes consécutives de `npm --prefix e2e test` : 18 tests, 0 échec chacune ; rapport [`e2e/resultats/rapport-2026-11-17.json`](/e2e/resultats/rapport-2026-11-17.json) (annotations « contraste minimal » et « contraste minimal du focus »).
- Aucun écart nouveau : les contrastes minimaux sont ceux du jeu d'exemple, les deux liens de source (fiches réglementaires) sont atteints au clavier dans l'ordre du document.

## Écarts trouvés et corrigés

| # | Constat | Source | Critère | Correction |
|---|---|---|---|---|
| E1 | Polices chargées depuis Google Fonts : bloquées par la CSP du desktop et indisponibles sans Internet | maquettes | ADR-0022, ADR-0023 point 5 | polices des maquettes embarquées (`@fontsource/*`, SIL OFL 1.1, sous-ensemble latin) |
| E2 | Sarcelle `#1e6f6a` sur rose `#f7d9d2` : 4,47:1 ; rouge `#b23a26` sur rose : 4,48:1, dès qu'ils sont posés sur les cartes colorées | maquette « fantaisie » | RGAA 3.2 | sarcelle `#17615c` (5,45:1 sur rose) et rouge `#a8341f` (4,97:1 sur rose) |
| E3 | Contour des étiquettes de rubrique `#9aa7b8` sur blanc : 2,44:1 | mise en page « classique » précédente | RGAA 3.3 (non bloquant : étiquette non interactive) | étiquette sur fond `#e6f2ef`, texte `#16365c` (contrôlé) |
| E4 | Titres des éléments en `h3` fixe, manchettes « journal » et « gazette » hors des titres : hiérarchie fausse selon l'écran d'accueil | mises en page précédentes | RGAA 9.1 | composant `Titre.vue` : titre principal au niveau demandé, éléments au niveau suivant ; contrôlé de h1 à h3 |
| E5 | Titres de 96 à 150 px et grilles de 3 ou 4 colonnes fixes : défilement horizontal à 320 px | maquettes | RGAA 10.11 | tailles bornées par `clamp()` en unités de conteneur, grilles `auto-fit`, colonnes empilées |
| E6 | Grain `feTurbulence` superposé au texte (contraste réel inférieur au contraste nominal) ; gravure décorative exposée comme image | maquette « gazette » | RGAA 1.2, 3.2 | grain supprimé ; dessins `aria-hidden="true"` et `focusable="false"` |
| E7 | Rubriques présentées comme des liens qui mènent tous à la même page | maquettes « journal », « fantaisie » | RGAA 6.1 | sommaire non cliquable (liste des rubriques et du nombre d'éléments) ; seuls les liens vers la source restent |
| E8 | Liens vers la source ouverts dans une nouvelle fenêtre sans l'annoncer | `MentionsObligatoires.vue` | bonne pratique RGAA 6.1 / WCAG G201 | « (nouvelle fenêtre) » ajouté au nom accessible |
| E9 | Texte justifié en colonnes étroites | maquette « gazette » | lisibilité (WCAG 1.4.8, niveau AAA) | texte aligné à gauche |
| E10 | Libellés « Source », « Résumé », « Validation » coupés avant leur deux-points dans les colonnes étroites | revue manuelle | lisibilité | libellés des mentions insécables |
| E11 | Espace manquante dans « Rubriques :Sécurité » | revue manuelle (`journal`, `gazette`) | lisibilité | espace explicite |
| E12 | Tampon décoratif « Alerte critique » tronqué par sa forme | revue manuelle (`fantaisie`) | décor | tampon agrandi, masqué sous 28 rem de large |
| E13 | Colonnes de la gazette décalées une fois empilées (retrait et filet vertical conservés) | banc d'essai à 375 px | RGAA 10.11 | filet horizontal et retrait nul en colonne unique |

## Écarts restants et limites

- **Lecteur d'écran** : aucune restitution n'a été vérifiée avec NVDA, JAWS ou VoiceOver. L'arbre d'accessibilité de Chromium est contrôlé ; la restitution vocale reste à vérifier par le référent accessibilité.
- **Espacement du texte et zoom** (RGAA 10.4, 10.12) : non contrôlés automatiquement ; la mise en page n'impose ni hauteur fixe ni texte tronqué, ce qui reste à confirmer par un essai à 200 % et avec l'espacement augmenté.
- **Polices de titrage gothiques** (`UnifrakturMaguntia`, `UnifrakturCook`) : employées pour les manchettes et, dans la gazette, pour les titres des éléments. Le contraste est conforme, mais la lisibilité de ce style pour des personnes dyslexiques ou malvoyantes n'est pas mesurée par le référentiel ; la mise en page `classique` reste le défaut et le repli.
- **Thèmes RGAA sans objet** dans les mises en page : cadres, multimédia, tableaux, formulaires, scripts de composants interactifs, consultation (aucune limite de temps, aucun contenu en mouvement).
- **Veille réelle** : les contrôles du navigateur sont repris sur une veille réelle de test (quatre éléments) ; largeurs de 320 et 375 px et mouvement réduit restent contrôlés sur le seul jeu d'exemple. Un titre externe très long ou sans espace est replié (`overflow-wrap: anywhere`) mais n'a pas été essayé sur des données de production.
- **AFF-05-S1** : une mise en page entre au registre de l'IHM (`argos-ui/src/mises-en-page/registre.ts`) avec sa palette ; un couple sous le seuil fait échouer `npm --prefix argos-ui test`, donc la construction de l'IHM. La publication au catalogue du serveur (`catalog/mises-en-page.json`) reste une configuration distincte, que ce contrôle ne bloque pas.

## Conclusion

Aucun écart bloquant ne subsiste sur les critères contrôlés pour les quatre mises en page. Les limites ci-dessus, en premier lieu l'essai avec un lecteur d'écran, sont à lever avant de déclarer la conformité RGAA de l'application.
