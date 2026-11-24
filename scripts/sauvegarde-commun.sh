# Fonctions communes à sauvegarder-base.sh et restaurer-base.sh, lues par « . » depuis le dossier du déploiement.
# POSIX sh : le shell des tâches planifiées de DSM n'est pas garanti bash, donc aucun pipefail ; chaque code de retour
# utile est lu hors pipeline.

# Ligne écrite par pg_dump à la fin d'un export complet, recherchée dans les dernières lignes de l'archive.
FIN_EXPORT='-- PostgreSQL database dump complete'
LIGNES_FIN_EXPORT=8
BASE_KEYCLOAK=keycloak

# $1 : nom ; $2 : valeur par défaut. Valeur de l'environnement, sinon dernière ligne « NOM=valeur » du .env, sinon
# défaut. Le .env est lu comme du texte, jamais exécuté : un mot de passe qui contient « $ », « ; » ou une espace ne
# change pas la lecture des autres réglages. Les guillemets ou apostrophes qui entourent la valeur sont retirés.
reglage() {
  VALEUR=$(printenv "$1" || true)
  if [ -z "$VALEUR" ] && [ -f .env ]; then
    VALEUR=$(grep -E "^$1=" .env | tail -n 1 | cut -d= -f2- | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/")
  fi
  printf '%s\n' "${VALEUR:-$2}"
}

charger_configuration() {
  DEST=$(reglage ARGOS_BACKUP_DIR ./sauvegardes)
  BASE_ARGOS=$(reglage ARGOS_DB_NAME argos)
  if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
  else
    COMPOSE="docker-compose"
  fi
}

# $1 : archive. Vrai si l'archive est un gzip intègre qui contient un export allé à son terme.
archive_valide() {
  gzip -t "$1" 2>/dev/null || return 1
  gzip -dc "$1" | tail -n "$LIGNES_FIN_EXPORT" | grep -qF -- "$FIN_EXPORT"
}

# $1 : base. Code 0 si la base existe dans l'instance, 1 si elle est absente, 2 si l'instance ne répond pas.
base_existe() {
  if ! PRESENCE=$(printf "SELECT 1 FROM pg_database WHERE datname = :'base';\n" \
      | $COMPOSE exec -T postgres sh -c 'psql -X -tA -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -v base="$1"' \
        sh "$1"); then
    return 2
  fi
  [ "$PRESENCE" = 1 ]
}
