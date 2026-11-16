# ADR-0026 — Héberger Argos sur le serveur Synology du porteur avant toute offre en ligne

- **Statut** : Accepté (cible d'hébergement) ; modalités détaillées Proposées
- **Date** : 2026-11-16
- **Décideurs** : porteur du projet
- **Remplace** : ADR-0017, pour la cible d'hébergement (VM interne de Ciril)
- **Remplacé par** : —

## Contexte

ADR-0017 prévoyait un POC local sous Docker Compose, puis une VM Linux interne de Ciril. Argos est désormais un produit ouvert porté à titre personnel (ADR-0025) : la VM interne n'est plus la cible.

Le porteur du projet a décidé le 2026-11-16 de déployer Argos **sur son serveur Synology**. Une version en ligne hébergée viendra plus tard.

Matériel : **Synology DS1522+**, processeur AMD Ryzen R1600 (x86-64, 2 cœurs, 4 threads), 8 Go de mémoire extensibles à 32 Go. **Hypothèse à valider** : version de DSM (7.2 ou plus pour Container Manager et ses projets Compose). Argos doit aussi pouvoir tourner sous Docker sur le PC du porteur.

## Critères de décision

1. Déployer sans infrastructure d'entreprise ni coût d'hébergement.
2. Réutiliser l'assemblage Docker Compose du POC local.
3. Pouvoir passer plus tard à un hébergement en ligne sans refonte.
4. Ne pas exposer le NAS sur Internet sans protection.

## Options considérées

### Option A — Conteneurs sur le Synology (Container Manager)

- avantages : matériel déjà disponible ; Docker Compose pris en charge par Container Manager (DSM 7.2 et plus) ; reverse proxy, certificats Let's Encrypt et sauvegardes (Hyper Backup) fournis par DSM ;
- inconvénients : ressources limitées selon le modèle ; disponibilité liée à la connexion et à l'alimentation du domicile ;
- risques : exposition d'un NAS personnel sur Internet ; processeur ARM sur certains modèles.

### Option B — Hébergement en ligne dès maintenant (VPS, cloud)

- avantages : disponibilité, bande passante, isolation du réseau domestique ;
- inconvénients : coût récurrent ; exploitation à prévoir avant d'avoir validé le produit.

### Option C — VM interne de Ciril (ADR-0017)

- écartée : incompatible avec ADR-0025.

## Décision

Option A.

1. Argos est déployé sur le Synology du porteur, avec **Docker Compose** via Container Manager : PostgreSQL et Argos dans des conteneurs, données sur des volumes persistants du NAS.
2. Le poste de développement garde le POC local d'ADR-0017 (inchangé) ; le même assemblage est déployé sur le NAS.
3. Une seule image applicative sert l'API, le serveur MCP et l'IHM web, pour limiter la mémoire consommée et supprimer le besoin de CORS côté web. Elle est construite pour x86-64, ce qui couvre le DS1522+ et les PC. Le même `compose.yaml` sert sur le PC et sur le NAS.
4. [Proposé] Accès extérieur : par défaut, **aucun port ouvert** ; accès distant par VPN (VPN Server de DSM ou réseau privé de type WireGuard). Si une exposition publique est souhaitée : reverse proxy de DSM, HTTPS avec certificat Let's Encrypt, authentification obligatoire.
5. [Proposé] Authentification : OIDC est conservé (ADR-0008), avec un fournisseur d'identité auto-hébergé, par exemple le SSO Server de DSM ou un conteneur dédié, à choisir.
6. [Proposé] Sauvegardes : export PostgreSQL quotidien (`pg_dump`) déposé sur un dossier du NAS, puis copié hors site par Hyper Backup ; restauration testée.
7. L'hébergement en ligne fera l'objet d'un ADR distinct ; l'image et le Compose doivent rester déployables ailleurs sans modification du code.

## Conséquences positives

- déploiement immédiat, sans coût ;
- environnement proche de ce que serait un petit hébergement en ligne.

## Conséquences négatives

- disponibilité et performances limitées par le NAS et la connexion domestique ;
- le porteur assure seul l'exploitation : mises à jour, sauvegardes, sécurité ;
- les comptes de tiers hébergés sur le NAS en font un traitement de données personnelles (ADR-0025).

## Conséquences neutres / compromis

- ADR-0017 reste valable pour le POC local sur le poste de développement.

## Méthode de validation

- relevé de la version de DSM ;
- construction de l'image et démarrage complet sur le PC, puis sur le NAS ;
- démarrage du Compose sur le NAS et mesure de la mémoire au repos ;
- accès distant par VPN vérifié, aucun port exposé par défaut ;
- sauvegarde puis restauration PostgreSQL réussies.

## Traçabilité

- **Exigences** : —
- **Scénarios qualité** : sauvegarde et reprise (arc42 §7.6)
- **Diagrammes** : arc42 §7.3
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `Dockerfile`, `compose.yaml`, `.env.example`, `scripts/sauvegarder-base.sh` ; guide `/docs/deploiement/synology-et-pc.md`

## Notes

Les fonctions de DSM citées (Container Manager, reverse proxy, VPN Server, SSO Server, Hyper Backup) dépendent du modèle et de la version : à vérifier sur le NAS réel.
