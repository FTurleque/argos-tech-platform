# ADR-0017 — Démarrer le POC localement puis migrer sur VM interne

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

La mise à disposition d’une VM, du réseau, du proxy, du SSO et des validations associées peut prendre plus de temps que la réalisation technique. Attendre l’infrastructure avant toute démonstration ferait peser un risque inutile sur le POC.

## Critères de décision

1. Ne pas bloquer le développement sur le provisioning.
2. Démontrer rapidement la chaîne verticale.
3. Garder un environnement reproductible.
4. Préparer sans refonte le passage vers l’infrastructure interne.

## Options considérées

### Option A — Docker Compose local puis VM Linux interne

- POC technique sur le poste de développement ;
- démonstration locale reproductible ;
- migration du même assemblage sur VM pour le POC partagé/MVP ;
- permet d’engager en parallèle les demandes d’infrastructure.

### Option B — Attendre la VM avant de commencer

- environnement directement proche de la cible ;
- risque de bloquer plusieurs semaines sur les délais organisationnels.

### Option C — Kubernetes/OpenShift dès le départ

- capacités d’orchestration avancées ;
- coût et complexité prématurés.

## Décision

Le POC technique peut s’exécuter **localement avec Docker Compose** sur le poste de développement. Dès que la VM Linux interne est disponible, le même ensemble est déployé pour le POC partagé puis le MVP, avec reverse proxy TLS, volumes persistants et mécanismes d’entreprise.

La durée de travail du POC est estimée à **20–24 j.h**, mais sa durée calendaire peut atteindre **6 à 10 semaines** si les dépendances inter-équipes sont sur le chemin critique.

## Conséquences positives

- progression technique immédiate ;
- démo possible sans attendre la VM ;
- déploiement reproductible ;
- séparation claire entre charge effective et délai calendaire.

## Conséquences négatives

- nécessité de vérifier les écarts poste local / VM ;
- certaines fonctions SSO/proxy/secrets ne seront validables que sur l’environnement entreprise.

## Méthode de validation

- démarrage complet local ;
- export/import ou redéploiement sur VM ;
- restauration PostgreSQL ;
- validation réseau/SSO/proxy ;
- mesure des besoins de ressources.

## Traçabilité

- **Baseline** : `../baseline-v2.4.md`
- **Fichiers source** : `../arc42/07-vue-deploiement.md`
