# ADR-0017 — Déployer le POC/MVP en conteneurs sur une VM

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Le POC et le MVP doivent être simples à déployer sur l’infrastructure interne sans introduire un cluster orchestré avant d’en avoir besoin.

## Critères de décision

1. Temps de mise en œuvre.
2. Exploitabilité par les équipes internes.
3. Sauvegarde et restauration maîtrisées.
4. Trajectoire vers la haute disponibilité sans refonte applicative.

## Options considérées

### Option A — Docker Compose ou Podman sur une VM Linux

- faible complexité ;
- adapté au POC/MVP ;
- composants isolés et reproductibles.

### Option B — Kubernetes/OpenShift dès le départ

- capacités d’orchestration avancées ;
- coût d’exploitation et d’apprentissage prématurés.

## Décision

Le POC/MVP cible **une VM Linux interne** avec services conteneurisés via **Docker Compose ou Podman**, reverse proxy TLS et volumes persistants. La cible entreprise pourra évoluer vers plusieurs nœuds si le besoin est validé.

## Conséquences positives

- démarrage rapide ;
- peu de pièces mobiles ;
- déploiement reproductible.

## Conséquences négatives

- haute disponibilité non couverte au MVP ;
- procédures de sauvegarde/restore indispensables.

## Méthode de validation

Déploiement de recette, redémarrage complet, restauration PostgreSQL, mise à jour contrôlée et mesure des besoins de ressources.

## Traçabilité

- **Exigences** : objectifs d’exploitation et de sécurité
- **Scénarios qualité** : RPO/RTO MVP, robustesse
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/07-vue-deploiement.md`
