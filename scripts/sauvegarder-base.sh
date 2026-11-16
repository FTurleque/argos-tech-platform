#!/bin/sh
# Sauvegarde de la base Argos (ADR-0026) : export compressé et rotation.
# PC : sh scripts/sauvegarder-base.sh
# NAS : tâche planifiée DSM (utilisateur root) : sh /volume1/docker/argos/scripts/sauvegarder-base.sh
set -eu

cd "$(dirname "$0")/.."
[ -f .env ] && . ./.env

DEST="${ARGOS_BACKUP_DIR:-./sauvegardes}"
KEEP="${ARGOS_BACKUP_KEEP:-14}"

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

mkdir -p "$DEST"
FILE="$DEST/argos-$(date +%Y%m%d-%H%M%S).sql.gz"

$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$FILE"

# Garde les KEEP sauvegardes les plus récentes.
COUNT=0
for OLD in $(ls -1t "$DEST"/argos-*.sql.gz); do
  COUNT=$((COUNT + 1))
  if [ "$COUNT" -gt "$KEEP" ]; then
    rm -f "$OLD"
  fi
done

echo "Sauvegarde écrite : $FILE"
