#!/bin/sh
# Rôles et bases de la plateforme dans l'instance PostgreSQL partagée (EXP-05, ADR-0034).
#
# Premier démarrage d'un volume : l'image postgres exécute ce script depuis /docker-entrypoint-initdb.d. Il crée :
#   - argos_app et argos_technique, rôles sans connexion qui portent les droits d'Argos (migrations V2 et V22) ;
#   - le propriétaire de la base Argos (ARGOS_DB_OWNER), qui applique les migrations ;
#   - le rôle de connexion d'Argos (ARGOS_DB_USER), membre d'argos_technique et autorisé à basculer sur argos_app ;
#   - le rôle de Keycloak (ARGOS_KEYCLOAK_DB_USER), propriétaire de sa seule base ;
#   - les bases Argos (POSTGRES_DB) et keycloak, fermées à PUBLIC.
# Aucun de ces rôles n'est superutilisateur ; seul POSTGRES_USER, réservé à l'administration, l'est.
#
# Restauration (scripts/restaurer-base.sh) : avec ARGOS_BASE_A_RECREER=<base>, le script recrée seulement cette base,
# vide, avec son propriétaire et ses droits de connexion ; les rôles existent déjà.

KEYCLOAK_BASE=keycloak

executer_sql() {
  psql -X -q -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres "$@"
}

creer_roles() {
  executer_sql \
    -v proprietaire="$ARGOS_DB_OWNER" -v proprietaire_mdp="$ARGOS_DB_OWNER_PASSWORD" \
    -v connexion="$ARGOS_DB_USER" -v connexion_mdp="$ARGOS_DB_PASSWORD" \
    -v keycloak="$KEYCLOAK_ROLE" -v keycloak_mdp="$ARGOS_KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE argos_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE argos_technique NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE :"proprietaire" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'proprietaire_mdp';
CREATE ROLE :"connexion" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'connexion_mdp';
CREATE ROLE :"keycloak" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'keycloak_mdp';
-- La migration V2 accorde argos_app au propriétaire : il doit pouvoir administrer ce rôle.
GRANT argos_app TO :"proprietaire" WITH ADMIN TRUE;
-- Le rôle de connexion bascule sur argos_app (SET LOCAL ROLE) sans en hériter les droits, et hérite de ceux
-- d'argos_technique pour les traitements sans contexte utilisateur.
GRANT argos_app TO :"connexion" WITH INHERIT FALSE, SET TRUE;
GRANT argos_technique TO :"connexion";
SQL
}

# $1 : base ; $2 : propriétaire ; $3 (facultatif) : rôle autorisé à s'y connecter en plus du propriétaire.
recreer_base() {
  executer_sql -v base="$1" -v proprietaire="$2" <<'SQL'
DROP DATABASE IF EXISTS :"base" WITH (FORCE);
CREATE DATABASE :"base" OWNER :"proprietaire";
REVOKE ALL ON DATABASE :"base" FROM PUBLIC;
SQL
  if [ -n "${3:-}" ]; then
    executer_sql -v base="$1" -v connexion="$3" <<'SQL'
GRANT CONNECT ON DATABASE :"base" TO :"connexion";
SQL
  fi
}

principal() {
  set -eu
  : "${POSTGRES_USER:?POSTGRES_USER manquant}" "${POSTGRES_DB:?POSTGRES_DB manquant}"
  : "${ARGOS_DB_OWNER:?ARGOS_DB_OWNER manquant}" "${ARGOS_DB_USER:?ARGOS_DB_USER manquant}"
  KEYCLOAK_ROLE="${ARGOS_KEYCLOAK_DB_USER:-keycloak}"

  if [ -n "${ARGOS_BASE_A_RECREER:-}" ]; then
    case "$ARGOS_BASE_A_RECREER" in
      "$POSTGRES_DB") recreer_base "$POSTGRES_DB" "$ARGOS_DB_OWNER" "$ARGOS_DB_USER" ;;
      "$KEYCLOAK_BASE") recreer_base "$KEYCLOAK_BASE" "$KEYCLOAK_ROLE" ;;
      *)
        echo "Base inconnue : $ARGOS_BASE_A_RECREER (attendu : $POSTGRES_DB ou $KEYCLOAK_BASE)" >&2
        return 2
        ;;
    esac
    return 0
  fi

  : "${ARGOS_DB_OWNER_PASSWORD:?ARGOS_DB_OWNER_PASSWORD manquant}" "${ARGOS_DB_PASSWORD:?ARGOS_DB_PASSWORD manquant}"
  : "${ARGOS_KEYCLOAK_DB_PASSWORD:?ARGOS_KEYCLOAK_DB_PASSWORD manquant}"
  creer_roles
  recreer_base "$POSTGRES_DB" "$ARGOS_DB_OWNER" "$ARGOS_DB_USER"
  recreer_base "$KEYCLOAK_BASE" "$KEYCLOAK_ROLE"
}

# Sous-shell : l'image peut aussi lire ce script par « . » ; ses options et ses variables ne la touchent pas.
( principal )
