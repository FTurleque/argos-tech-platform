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
COPY --from=construction /opt/keycloak/ /opt/keycloak/
