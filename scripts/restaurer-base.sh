#!/bin/sh
# Restauration des bases de la plateforme à partir d'une même sauvegarde (EXP-04 ; ADR-0026).
#
#   sh scripts/restaurer-base.sh <horodatage>     par exemple : sh scripts/restaurer-base.sh 20261124-031500
#
# Restaure <base Argos>-<horodatage>.sql.gz et, si elle existe, keycloak-<horodatage>.sql.gz, depuis ARGOS_BACKUP_DIR.
# Ordre : vérification des archives, arrêt d'Argos et de Keycloak, démarrage de PostgreSQL, puis pour chaque base :
# recréation vide (deploiement/postgres/initialiser-bases.sh, propriétaire et droits de connexion), rechargement dans
# une seule transaction sous le propriétaire de la base, arrêt à la première erreur. Le contenu restauré remplace
# celui de la base. Argos et Keycloak restent arrêtés : l'exploitant démarre Keycloak, puis Argos, une fois la
# restauration terminée, ce qui garantit que Keycloak retrouve son royaume au lieu de le réimporter.
# Destructif : sauvegarder l'état courant avant de restaurer (scripts/sauvegarder-base.sh).
set -eu
umask 077

cd "$(dirname "$0")/.."
. ./scripts/sauvegarde-commun.sh
charger_configuration

ROLE_PROPRIETAIRE=$(reglage ARGOS_DB_OWNER argos)
ROLE_KEYCLOAK=$(reglage ARGOS_KEYCLOAK_DB_USER keycloak)

echouer() {
  echo "Restauration en échec : $1. Argos et Keycloak restent arrêtés." >&2
  exit 1
}

HORODATAGE="${1:-}"
case "$HORODATAGE" in
  '' | *[!0-9-]*)
    echo "Usage : sh scripts/restaurer-base.sh <horodatage> (par exemple 20261124-031500, voir $DEST)" >&2
    exit 2
    ;;
  *) ;;
esac
for ROLE in "$ROLE_PROPRIETAIRE" "$ROLE_KEYCLOAK"; do
  case "$ROLE" in
    '' | *[!a-z0-9_]*) echouer "nom de rôle inattendu : $ROLE" ;;
    *) ;;
  esac
done

ARCHIVE_ARGOS="$DEST/$BASE_ARGOS-$HORODATAGE.sql.gz"
ARCHIVE_KEYCLOAK="$DEST/$BASE_KEYCLOAK-$HORODATAGE.sql.gz"
[ -f "$ARCHIVE_ARGOS" ] || echouer "archive introuvable : $ARCHIVE_ARGOS"
archive_valide "$ARCHIVE_ARGOS" || echouer "archive de la base $BASE_ARGOS illisible ou incomplète : $ARCHIVE_ARGOS"
if [ -f "$ARCHIVE_KEYCLOAK" ]; then
  archive_valide "$ARCHIVE_KEYCLOAK" \
    || echouer "archive de la base $BASE_KEYCLOAK illisible ou incomplète : $ARCHIVE_KEYCLOAK"
else
  ARCHIVE_KEYCLOAK=""
  echo "Aucune archive $BASE_KEYCLOAK pour $HORODATAGE : la base $BASE_KEYCLOAK reste en l'état."
fi

$COMPOSE --profile idp-local stop argos keycloak || echouer "arrêt d'Argos et de Keycloak"
$COMPOSE up -d --wait postgres || echouer "démarrage de PostgreSQL"

# $1 : base ; $2 : propriétaire sous lequel les objets sont recréés ; $3 : archive.
restaurer() {
  BASE_RESTAUREE="$1"
  ROLE_RESTAURATION="$2"
  ARCHIVE_RESTAUREE="$3"
  # Chemin donné dans la commande : sous Git Bash (PC Windows), un argument commençant par « / » serait converti.
  $COMPOSE exec -T -e ARGOS_BASE_A_RECREER="$BASE_RESTAUREE" postgres \
    sh -c 'exec sh /docker-entrypoint-initdb.d/initialiser-bases.sh' || echouer "recréation de la base $BASE_RESTAUREE"
  # L'archive est intègre (vérifiée ci-dessus) : le code du pipeline est celui de psql, arrêté à la première erreur.
  if ! gzip -dc "$ARCHIVE_RESTAUREE" \
      | $COMPOSE exec -T -e ARGOS_BASE="$BASE_RESTAUREE" -e ARGOS_ROLE="$ROLE_RESTAURATION" postgres sh -c \
        'psql -X -q -o /dev/null -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$ARGOS_BASE" \
           -c "SET ROLE \"$ARGOS_ROLE\"" -f -'; then
    echouer "rechargement de la base $BASE_RESTAUREE (transaction annulée, base vide)"
  fi
  echo "Base $BASE_RESTAUREE restaurée depuis $ARCHIVE_RESTAUREE"
}

restaurer "$BASE_ARGOS" "$ROLE_PROPRIETAIRE" "$ARCHIVE_ARGOS"
if [ -n "$ARCHIVE_KEYCLOAK" ]; then
  restaurer "$BASE_KEYCLOAK" "$ROLE_KEYCLOAK" "$ARCHIVE_KEYCLOAK"
fi
echo "Restauration terminée. Démarrez Keycloak puis Argos : docker compose --profile idp-local up -d keycloak," \
  "puis docker compose up -d argos (sans Keycloak local : docker compose up -d)."
