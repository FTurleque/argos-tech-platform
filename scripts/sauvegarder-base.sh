#!/bin/sh
# Sauvegarde des bases de la plateforme (ADR-0026 ; EXP-01 à EXP-03) : la base d'Argos et, si elle existe dans
# l'instance, la base keycloak, chacune dans une archive horodatée (même horodatage), restaurable seule.
#
# Chaque export est écrit dans un fichier temporaire, vérifié (intégrité gzip et fin d'export), puis renommé. La
# rotation (ARGOS_BACKUP_KEEP archives par base) n'a lieu qu'après le succès de toutes les bases. En cas d'échec,
# le script sort avec un code non nul en nommant la base, sans laisser d'archive invalide ni en supprimer aucune.
# Archives en 0600 et dossier en 0700, quel que soit le masque du système : le dossier des sauvegardes est réservé
# à ce script. Un fichier « .partiel » laissé par une exécution interrompue brutalement est supprimé au démarrage.
#
# PC : sh scripts/sauvegarder-base.sh
# NAS : tâche planifiée DSM (utilisateur root) : sh /volume1/docker/argos/scripts/sauvegarder-base.sh
# Restauration : scripts/restaurer-base.sh.
set -eu
umask 077

cd "$(dirname "$0")/.."
. ./scripts/sauvegarde-commun.sh
charger_configuration

ARCHIVES_CONSERVEES_PAR_DEFAUT=14
KEEP=$(reglage ARGOS_BACKUP_KEEP "$ARCHIVES_CONSERVEES_PAR_DEFAUT")
NIVEAU_COMPRESSION=6
PARTIEL=""
ECRITES=""

echouer() {
  echo "Sauvegarde en échec : $1. Aucune sauvegarde existante n'a été supprimée." >&2
  exit 1
}

nettoyer() {
  if [ -n "$PARTIEL" ]; then
    rm -f "$PARTIEL"
  fi
}
trap nettoyer EXIT
trap 'exit "$CODE_INTERRUPTION"' INT TERM

case "$KEEP" in
  '' | *[!0-9]* | 0) echouer "ARGOS_BACKUP_KEEP doit être un entier positif (valeur lue : $KEEP)" ;;
  *) ;;
esac

mkdir -p "$DEST"
chmod 700 "$DEST"
rm -f "$DEST"/*.sql.gz.partiel
HORODATAGE=$(date +%Y%m%d-%H%M%S)

# $1 : base. Export compressé par pg_dump dans le conteneur ; son code de retour est celui de « docker compose exec ».
exporter() {
  BASE_EXPORTEE="$1"
  ARCHIVE="$DEST/$BASE_EXPORTEE-$HORODATAGE.sql.gz"
  PARTIEL="$ARCHIVE.partiel"
  if ! $COMPOSE exec -T -e ARGOS_BASE="$BASE_EXPORTEE" -e ARGOS_COMPRESSION="$NIVEAU_COMPRESSION" postgres \
      sh -c 'pg_dump -U "$POSTGRES_USER" -d "$ARGOS_BASE" --no-owner --compress="$ARGOS_COMPRESSION"' > "$PARTIEL"; then
    echouer "export de la base $BASE_EXPORTEE"
  fi
  if ! archive_valide "$PARTIEL"; then
    echouer "archive de la base $BASE_EXPORTEE illisible ou incomplète"
  fi
  mv "$PARTIEL" "$ARCHIVE"
  PARTIEL=""
  ECRITES="$ECRITES $BASE_EXPORTEE"
  echo "Sauvegarde écrite : $ARCHIVE"
}

# $1 : base. Garde ses KEEP archives les plus récentes (l'horodatage du nom les ordonne).
tourner() {
  BASE_TOURNEE="$1"
  ls -1 "$DEST/$BASE_TOURNEE"-*.sql.gz 2>/dev/null | sort -r | tail -n +"$((KEEP + 1))" | while IFS= read -r ANCIENNE; do
    rm -f "$ANCIENNE"
  done
}

exporter "$BASE_ARGOS"

CODE_PRESENCE=0
base_existe "$BASE_KEYCLOAK" || CODE_PRESENCE=$?
case "$CODE_PRESENCE" in
  0) exporter "$BASE_KEYCLOAK" ;;
  1) echo "Base $BASE_KEYCLOAK absente de l'instance : rien à sauvegarder pour Keycloak." ;;
  *) echouer "recherche de la base $BASE_KEYCLOAK" ;;
esac

for BASE in $ECRITES; do
  tourner "$BASE"
done
echo "Sauvegarde terminée ($HORODATAGE)."
