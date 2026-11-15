# ADR-0025 — Faire d'Argos un produit ouvert à tous les publics, porté à titre personnel

- **Statut** : Accepté
- **Date** : 2026-11-16
- **Décideurs** : porteur du projet
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Le dossier de proposition v2.4 présente Argos comme une **plateforme interne de Ciril** : hébergement sur une VM interne, SSO d'entreprise (ADR-0008), code dans le GitLab interne, budget et équipes pilotes demandés à la direction, cas d'usage centrés sur les logiciels RH du secteur public.

Le 2026-11-16, le porteur du projet a précisé qu'Argos **deviendra un produit ouvert** et qu'il le développe **pour lui-même**, pas spécialement pour Ciril. Cette précision répond à la question Q-1 du changement `personnaliser-profils-et-affichage` et rend possibles les profils destinés à d'autres publics (artistes, comédiens, vie politique — ADR-0024).

## Critères de décision

1. Pouvoir servir des publics extérieurs à toute entreprise.
2. Garder la maîtrise des droits sur le code (ADR-0021).
3. Ne pas dépendre d'une infrastructure ou d'un annuaire propres à un employeur.

## Options considérées

### Option A — Produit ouvert, porté à titre personnel

- avantages : couvre tous les publics visés ; maîtrise des droits par le porteur ;
- inconvénients : hébergement, comptes, support et cadre juridique à construire ;
- risques : titularité des droits à sécuriser (voir Conséquences négatives).

### Option B — Outil interne à Ciril (cadrage du dossier v2.4)

- avantages : infrastructure, SSO et équipes pilotes fournis ;
- inconvénients : exclut les publics extérieurs ; droits sur le code liés à l'employeur.

## Décision

Option A.

1. Argos est un produit destiné à des utilisateurs de tous horizons ; l'usage par une entreprise, Ciril compris, devient **un cas d'usage parmi d'autres**.
2. Les éléments propres à Ciril (VM interne, GitLab interne, SSO d'entreprise, modules RH, MKP) deviennent des exemples ou des options de déploiement, et non des hypothèses du produit.
3. La licence et la titularité sont régies par ADR-0009 et ADR-0021, à revoir à la lumière de cette décision (voir ci-dessous).

## Conséquences positives

- les profils grand public d'ADR-0024 deviennent atteignables ;
- l'architecture ne dépend plus d'une infrastructure d'entreprise.

## Conséquences négatives

- **Titularité** : le dossier v2.4 est signé « Fabrice Turleque — Ciril Group » et sollicite des moyens de Ciril. Si une partie du travail a été ou est réalisée dans le cadre de l'emploi ou avec ses moyens, les droits peuvent revenir à l'employeur. **Hypothèse à valider** avec un conseil juridique, et clarification écrite à obtenir avant toute diffusion.
- **Licence** : la PolyForm Internal Use License (ADR-0009) n'autorise que l'usage interne et interdit la redistribution ; elle ne convient pas telle quelle à un produit ouvert à des tiers. Le modèle de diffusion reste à choisir : service hébergé, logiciel distribué, licence commerciale (ADR-0021), voire open source.
- **Hébergement et comptes** : ADR-0008 (IdP d'entreprise) et ADR-0017 (VM interne) sont à revoir : inscription, authentification grand public, séparation des clients (multi-locataire).
- **Données personnelles** : le porteur devient responsable de traitement (RGPD) : registre, conditions d'utilisation, politique de confidentialité, hébergement des données.
- **Sources** : les conditions d'usage de chaque source (API Légifrance, NVD, OSV…) doivent autoriser l'usage dans un produit diffusé, éventuellement commercial.

## Conséquences neutres / compromis

- la stack (ADR-0019), les mises en page (ADR-0023) et les profils (ADR-0024) restent valables.

## Méthode de validation

- clarification écrite de la titularité avant la première diffusion ;
- ADR de remplacement ou de complément pour ADR-0008, ADR-0009 et ADR-0017 ;
- revue des conditions d'usage des sources du catalogue.

## Traçabilité

- **Exigences** : question Q-1 du changement `personnaliser-profils-et-affichage` (répondue)
- **Scénarios qualité** : —
- **Diagrammes** : —
- **Tickets/PR** : Non déterminé
- **Fichiers source** : —

## Notes

« Produit ouvert » signifie ici ouvert à des publics extérieurs à une entreprise. Le choix d'une licence open source n'est pas décidé par cet ADR.
