# ADR-0042 — Distribuer le client desktop par GitHub Releases, mis à jour par electron-updater, sans certificat payant

- **Statut** : Accepté
- **Date** : 2026-11-27
- **Décideurs** : porteur du projet
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Le client desktop (ADR-0022) est empaqueté par electron-builder en installeur NSIS pour Windows et en AppImage pour
Linux. Sans canal de distribution, chaque nouvelle version doit être téléchargée et installée à la main, et un poste
peut rester sur une version vulnérable (constat AUD-DEP-15). Sans signature de code, Windows SmartScreen avertit au
premier lancement et l'utilisateur ne peut pas vérifier l'origine du fichier.

Contraintes : outils gratuits sous licence permissive ; le dépôt est public sous PolyForm Internal Use (ADR-0009), une
licence non OSI qui exclut les programmes gratuits de signature réservés à l'open source (SignPath Foundation) ;
macOS n'installe une mise à jour que d'une application signée.

L'image Docker et l'installeur embarquent l'IHM argos-ui, donc vue et vue-router (licence MIT), qui exigent la
reproduction de leur avis de licence (constat AUD-DEP-19).

## Critères de décision

1. Coût récurrent nul.
2. Intégrité de la mise à jour vérifiable par le client avant installation.
3. Origine des installeurs vérifiable par l'utilisateur.
4. Aucune infrastructure supplémentaire à exploiter.

## Options considérées

### Signature — Option A : aucun certificat payant, intégrité et provenance publiées

- avantages : gratuit ; empreinte SHA-512 de chaque installeur vérifiée par electron-updater ; attestation de
  provenance GitHub (Sigstore) et empreintes SHA-256 publiées ; signature branchée dès qu'un certificat est fourni ;
- inconvénients : avertissement SmartScreen au premier lancement ; pas de mise à jour automatique sous macOS.

### Signature — Option B : certificat auto-signé

- avantages : gratuit ;
- inconvénients : aucune confiance accordée par Windows ou macOS hors d'un parc où le certificat racine est déployé ;
  SmartScreen avertit toujours.

### Signature — Option C : certificat payant (Azure Artifact Signing, certificat OV)

- avantages : plus d'avertissement après acquisition de réputation ;
- inconvénients : coût récurrent (environ 10 $ par mois à 400 € par an) ; identité à faire valider.

### Canal — Option 1 : GitHub Releases du dépôt (fournisseur `github` d'electron-updater)

- avantages : gratuit, sans serveur à exploiter, publication automatisée par un workflow ;
- inconvénients : dépend de la disponibilité de GitHub.

### Canal — Option 2 : serveur HTTP auto-hébergé (fournisseur `generic`)

- avantages : hébergement maîtrisé (Synology, ADR-0026) ;
- inconvénients : publication manuelle des fichiers ; serveur exposé à maintenir.

### Canal — Option 3 : avertir seulement

- avantages : aucun mécanisme d'installation ;
- inconvénients : la mise à jour reste à l'initiative de l'utilisateur.

## Décision

Option A et option 1.

1. Le workflow `.github/workflows/publication-desktop.yml` construit, sur une étiquette `desktop-v<version>` égale à
   la version de `argos-desktop/package.json`, les installeurs Windows (NSIS) et Linux (AppImage) et les dépose dans
   une release brouillon de même étiquette (préfixe `desktop-v` de la configuration de publication d'electron-builder),
   avec leurs fichiers `latest*.yml` (empreinte SHA-512), leurs empreintes SHA-256 et une attestation de provenance
   (`actions/attest-build-provenance`) ; la release n'est publiée, comme dernière version, qu'une fois tous ses
   fichiers déposés. electron-updater lit la dernière release publiée du dépôt : aucune autre release du dépôt ne doit
   être marquée « dernière version ».
2. Les installeurs ne sont pas signés tant qu'aucun certificat n'est fourni ; les secrets `ARGOS_CSC_LINK` et
   `ARGOS_CSC_KEY_PASSWORD` suffisent à les signer, sans autre changement.
3. Le client desktop empaqueté (Windows et Linux) cherche une nouvelle version au démarrage par electron-updater
   (licence MIT), la télécharge en arrière-plan, l'installe à la fermeture et l'annonce par une notification
   (`argos-desktop/src/mise-a-jour.js`). `ARGOS_MISE_A_JOUR=off` désactive la recherche. Sous macOS, la mise à jour
   reste manuelle et aucun installeur n'est publié.
4. Les dépendances npm livrées (IHM et desktop) sont inventoriées par `scripts/verifier-licences-npm.mjs` : une licence
   hors de la liste admise fait échouer le build de l'image, la CI et la publication ; l'avis produit, avec le texte
   de licence de chaque dépendance, est livré dans l'image (`/opt/argos/licenses/THIRD-PARTY-npm.txt`) et dans
   l'installeur (ressources `licences/`).

## Conséquences positives

- un poste reçoit les correctifs sans intervention ;
- une mise à jour altérée est refusée par le client (empreinte SHA-512) ;
- l'origine d'un installeur se vérifie par `gh attestation verify` ;
- les obligations des licences MIT (vue, vue-router) et OFL (polices) sont remplies dans l'image et l'installeur.

## Conséquences négatives

- avertissement SmartScreen au premier lancement d'un installeur non signé ;
- pas de mise à jour automatique sous macOS ;
- la distribution dépend de GitHub.

## Conséquences neutres / compromis

- l'adoption d'un certificat (option C) ne demande que les deux secrets ;
- la licence du dépôt encadre toujours la diffusion (ADR-0025, conséquences négatives).

## Méthode de validation

- tests du module de mise à jour (`argos-desktop/test/mise-a-jour.test.js`, `main.test.js`) ;
- tests du contrôle des licences npm (`scripts/verifier-licences-npm.test.mjs`) et contrôle en CI ;
- première publication d'une étiquette `desktop-v*` : release créée, installeurs, `latest*.yml`, empreintes et
  attestation présents ; mise à jour d'un poste depuis la version précédente.

## Traçabilité

- **Exigences** : constats AUD-DEP-15 et AUD-DEP-19 de l'audit du 2026-11-21
- **Scénarios qualité** : —
- **Diagrammes** : arc42 §7 (déploiement)
- **Tickets/PR** : sprint 11 de l'audit
- **Fichiers source** : `argos-desktop/src/mise-a-jour.js`, `argos-desktop/package.json` (`build.publish`),
  `.github/workflows/publication-desktop.yml`, `scripts/verifier-licences-npm.mjs`, `Dockerfile`

## Notes

Licences relevées le 2026-11-27 : `electron-updater` 6.8 : MIT ; ses dépendances d'exécution : MIT (`builder-util-runtime`,
`debug`, `fs-extra`, `js-yaml`, `jsonfile`, `lazy-val`, `lodash.escaperegexp`, `lodash.isequal`, `ms`,
`tiny-typed-emitter`, `universalify`), ISC (`semver`), BlueOak-1.0.0 (`graceful-fs`, `sax`), Python-2.0 (`argparse`) ;
`actions/attest-build-provenance` : MIT.
