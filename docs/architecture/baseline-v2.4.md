# Baseline d’architecture ARGOS v2.4

- **Date** : 27 septembre 2026
- **Statut** : proposition mise à jour à valider
- **Source de cadrage** : dossier ARGOS v2.4
- **Source d’architecture versionnée** : `docs/architecture/`

## 1. Arbitrages v2.4

La v2.4 conserve la baseline v2.3 et ajoute quatre décisions de cadrage :

1. **stack cible** : Java 17 minimum, backend/API Quarkus, IHM Vue.js 3, PostgreSQL ;
2. **POC réaliste** : environ 20 à 24 jours-homme de travail effectif, soit environ un mois concentré ;
3. **progression sans attendre la VM** : démonstration locale possible sur le poste de développement avec Docker Compose, puis migration sur VM interne dès disponibilité ;
4. **personnalisation à deux niveaux** : socle d’équipe + veille personnelle facultative, sans élargissement des droits d’accès.

## 2. Planning POC et phase 1

Le chiffre précédent de 12 j.h est abandonné. Le POC représente désormais **≈ 20–24 j.h** de travail effectif.

En environnement entreprise, la durée calendaire peut être supérieure à la charge : provisionnement VM, proxy/SSO, revues RSSI/DPO, disponibilité des équipes pilotes et urgences opérationnelles. Si la VM est requise dès le début, une fenêtre de **6 à 10 semaines calendaires** est plausible. La démonstration locale Docker permet de ne pas bloquer la progression technique.

Pour le MVP, l’ordre de grandeur devient **≈ 50–60 j.h cumulés**, avec **3 à 5 mois calendaires** possibles selon les dépendances organisationnelles. Ces valeurs seront recalibrées à la fin du POC.

## 3. Stack applicative

| Couche | Décision |
|---|---|
| JDK | Java 17 minimum |
| Backend/API | Quarkus |
| Architecture | monolithe modulaire, frontières vérifiées par tests d’architecture / ArchUnit |
| IHM | Vue.js 3 en SPA |
| API | REST + OpenAPI |
| Base | PostgreSQL |
| MCP | module Quarkus, lecture seule au MVP, Streamable HTTP |
| Déploiement POC | Docker Compose local, puis VM Linux interne |

Aucun composant Spring Boot ou Spring AI n’est retenu pour ARGOS.

## 4. Personnalisation équipe + utilisateur

Chaque utilisateur reçoit un **socle d’équipe** correspondant à ses projets et responsabilités. Il peut en plus créer une **veille personnelle** sur des technologies, éditeurs, domaines ou sources non directement liés à son équipe afin d’apprendre et d’élargir ses compétences.

La personnalisation n’accorde aucun droit supplémentaire : une préférence de veille ne permet jamais de lire un projet ou une donnée interne hors du périmètre autorisé par RBAC/RLS.

Le modèle `Subscription / Rule` porte donc un `scope` `TEAM` ou `PERSONAL`, avec caractère obligatoire ou facultatif selon le cas.

## 5. IA : développement et runtime

Deux budgets doivent être distingués :

- **productivité de développement** : ne pas dimensionner le POC sur un plan Claude Pro seul ; hypothèse projet : prévoir au minimum un niveau de capacité supérieur, par exemple Max 5x, ou des crédits d’usage équivalents, puis mesurer ;
- **fonctionnement d’ARGOS** : Claude API/Console est facturée séparément de l’abonnement Claude et passe obligatoirement par l’AI Gateway avec quotas, traçabilité et plafond budgétaire.

## 6. Plan POC effectif

| Semaine | Charge | Livrables |
|---|---:|---|
| S1 — Fondation | ≈ 4–5 j.h | Java 17 + Quarkus, Vue.js 3 minimal, PostgreSQL, Docker Compose local, migrations, CI, tests d’architecture |
| S2 — Sécurité | ≈ 5 j.h | OSV, modèle canonique, idempotence, provenance |
| S3 — Inventaire / impact | ≈ 5 j.h | CycloneDX, PURL, corrélation déterministe |
| S4 — Restitution / MCP / réglementaire / démo | ≈ 6–9 j.h | portail Vue.js 3, API Quarkus, alertes, MCP, Légifrance, fiche MKP, KPI, tests sécurité, coûts IA, démo |

## 7. ADR

- ADR-0001 reste **Proposé** pour le monolithe modulaire, désormais appliqué à Quarkus ;
- ADR-0002 PostgreSQL passe à **Accepté** ;
- ADR-0017 est précisé : POC local Docker possible, puis VM interne ;
- ADR-0019 formalise et accepte la stack Java 17+/Quarkus/Vue.js 3/PostgreSQL.

## 8. Critères go / no-go

Les critères v2.3 restent applicables : sécurité, couverture SBOM, précision de corrélation, MCP, réglementation, fonctionnement dégradé sans IA, confidentialité, exploitabilité, coût et cohérence documentaire.

Le go/no-go se tient **à l’issue du POC** et non à une date fixe indépendante des délais d’infrastructure ou de disponibilité des équipes.
