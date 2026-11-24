# syntax=docker/dockerfile:1
# Image Keycloak optimisée du profil « idp-local » (EXP-16, ADR-0027).
# L'image officielle reconstruit Quarkus dans /opt/keycloak/lib/quarkus au démarrage dès qu'une option de
# construction diffère de ses défauts : impossible avec un système de fichiers racine en lecture seule. Cette
# image fige ces options par « kc.sh build » ; le conteneur démarre avec « kc.sh start --optimized ».
# Version : argument VERSION_KEYCLOAK, transmis par compose.yaml depuis ARGOS_KEYCLOAK_VERSION. Toute option de
# construction ajoutée ici (KC_DB, KC_HEALTH_ENABLED, KC_FEATURES…) demande une reconstruction de l'image.
ARG VERSION_KEYCLOAK

FROM quay.io/keycloak/keycloak:${VERSION_KEYCLOAK} AS construction
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${VERSION_KEYCLOAK}
# Contenu de l'installation propriété de root, lisible par le groupe 0 de l'utilisateur keycloak : comme le code d'Argos
# (EXP-12), elle reste non modifiable même sans racine en lecture seule. Les seuls emplacements écrits à l'exécution,
# /tmp et /opt/keycloak/data, sont des tmpfs déclarés dans compose.yaml.
COPY --from=construction --chown=0:0 /opt/keycloak/ /opt/keycloak/
