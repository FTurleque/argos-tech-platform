#!/bin/sh
# Essai de la sauvegarde et de la restauration (EXP-01 à EXP-04) contre une pile PostgreSQL jetable.
#
#   sh scripts/essai-sauvegarde-restauration.sh
#
# Copie compose.yaml, le script d'initialisation et les scripts de sauvegarde dans un dossier temporaire, avec son
# propre .env (projet Compose « argos-essai-sauvegarde », port 55433) : la pile réelle n'est pas touchée. Le dossier
# et la pile sont supprimés à la fin. ESSAI_SAUVEGARDES (dossier existant, chemin absolu) accueille le sous-dossier
# jetable des sauvegardes ailleurs, par exemple sur un système de fichiers qui gère les permissions POSIX ; sans
# elles, les contrôles de permissions sont signalés comme non vérifiés. Code de sortie non nul si un contrôle échoue.
set -eu

RACINE=$(cd "$(dirname "$0")/.." && pwd)
# Projet Compose de l'essai, exporté : il prime sur le « name: argos » de compose.yaml dans le script appelé aussi.
# Compose v2 est exigé : docker-compose v1 ne garantit pas cette priorité et viserait la pile réelle.
export COMPOSE_PROJECT_NAME=argos-essai-sauvegarde
if ! docker compose version >/dev/null 2>&1; then
  echo "Essai impossible : le plugin Docker Compose v2 (« docker compose ») est requis." >&2
  exit 2
fi
COMPOSE="docker compose"
CODE_INTERRUPTION=130
# Tailles des archives tronquées : un export coupé en cours de route, une archive dont même l'en-tête gzip est partiel.
OCTETS_EXPORT_TRONQUE=512
OCTETS_ARCHIVE_TRONQUEE=300
DOSSIER=$(mktemp -d)
# Sous-dossier jetable, même avec ESSAI_SAUVEGARDES : l'essai ne touche jamais un dossier existant.
if [ -n "${ESSAI_SAUVEGARDES:-}" ]; then
  SAUVEGARDES=$(mktemp -d "$ESSAI_SAUVEGARDES/essai.XXXXXX")
else
  SAUVEGARDES="$DOSSIER/sauvegardes"
fi
CONSERVEES=3
ECHECS=0
NON_VERIFIES=0
CHEMIN_INITIAL="$PATH"

nettoyer() {
  PATH="$CHEMIN_INITIAL"
  (cd "$DOSSIER" && $COMPOSE --profile idp-local down -v --remove-orphans >/dev/null 2>&1) || true
  rm -rf "$DOSSIER" "$SAUVEGARDES"
}
trap nettoyer EXIT
trap 'exit "$CODE_INTERRUPTION"' INT TERM

reussi() { echo "  ok    $1"; }
rate() {
  echo "  ÉCHEC $1" >&2
  if [ -f "$DOSSIER/sortie.txt" ]; then
    sed 's/^/          | /' "$DOSSIER/sortie.txt" | tail -n 5 >&2
    : > "$DOSSIER/sortie.txt"
  fi
  ECHECS=$((ECHECS + 1))
}
verifier() {
  LIBELLE="$1"
  shift
  if "$@"; then reussi "$LIBELLE"; else rate "$LIBELLE"; fi
}

mkdir -p "$DOSSIER/scripts" "$DOSSIER/deploiement/postgres"
cp "$RACINE/compose.yaml" "$DOSSIER/"
cp "$RACINE/deploiement/postgres/initialiser-bases.sh" "$DOSSIER/deploiement/postgres/"
cp "$RACINE/scripts/sauvegarde-commun.sh" "$RACINE/scripts/sauvegarder-base.sh" "$RACINE/scripts/restaurer-base.sh" \
  "$DOSSIER/scripts/"
# Mots de passe tirés au hasard pour cette pile jetable ; celui de connexion contient « ; » pour vérifier que les
# scripts lisent le .env comme du texte.
aleatoire() { od -An -N16 -tx1 /dev/urandom | tr -d ' \n'; }
cat > "$DOSSIER/.env" <<ENV
COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT_NAME
ARGOS_DB_PORT=55433
ARGOS_DB_ADMIN_PASSWORD=$(aleatoire)
ARGOS_DB_OWNER_PASSWORD=$(aleatoire)
ARGOS_DB_PASSWORD=$(aleatoire);echo injecte > injecte.txt
ARGOS_KEYCLOAK_DB_PASSWORD=$(aleatoire)
ARGOS_BACKUP_DIR=$SAUVEGARDES
ARGOS_BACKUP_KEEP=$CONSERVEES
ENV
cd "$DOSSIER"

# $1 : base ; $2 : rôle ; entrée standard : SQL. Exécuté dans le conteneur par le superutilisateur d'administration.
sql() {
  BASE_REQUETEE="$1"
  ROLE_REQUETE="$2"
  $COMPOSE exec -T -e ARGOS_BASE="$BASE_REQUETEE" -e ARGOS_ROLE="$ROLE_REQUETE" postgres \
    sh -c 'psql -X -q -tA -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$ARGOS_BASE" -c "SET ROLE \"$ARGOS_ROLE\"" -f -'
}
contenu_argos() { echo "SELECT string_agg(id || ':' || libelle, ',' ORDER BY id) FROM essai;" | sql argos argos; }
contenu_keycloak() { echo "SELECT string_agg(sujet, ',' ORDER BY sujet) FROM utilisateur;" | sql keycloak keycloak; }
archives() {
  BASE_LISTEE="$1"
  ls -1 "$SAUVEGARDES"/"$BASE_LISTEE"-*.sql.gz 2>/dev/null | sort
}
nombre_archives() {
  BASE_COMPTEE="$1"
  archives "$BASE_COMPTEE" | wc -l | tr -d ' '
}
mode_de() {
  CHEMIN_EXAMINE="$1"
  stat -c %a "$CHEMIN_EXAMINE" 2>/dev/null || stat -f %Lp "$CHEMIN_EXAMINE"
}
modes_posix() {
  TEMOIN="$SAUVEGARDES/.temoin-modes"
  : > "$TEMOIN"
  chmod 600 "$TEMOIN"
  MODE=$(mode_de "$TEMOIN")
  rm -f "$TEMOIN"
  [ "$MODE" = 600 ]
}
sauvegarder() { : > "$DOSSIER/sortie.txt"; sh scripts/sauvegarder-base.sh > "$DOSSIER/sortie.txt" 2>&1; }
restaurer() {
  HORODATAGE_RESTAURE="$1"
  sh scripts/restaurer-base.sh "$HORODATAGE_RESTAURE" > "$DOSSIER/sortie.txt" 2>&1
}
sortie_contient() {
  TEXTE_ATTENDU="$1"
  grep -qF -- "$TEXTE_ATTENDU" "$DOSSIER/sortie.txt"
}
aucun_partiel() { ! ls "$SAUVEGARDES"/*.partiel >/dev/null 2>&1; }
code_non_nul() { ! "$@"; }

echo "Pile jetable : démarrage de PostgreSQL."
$COMPOSE up -d --wait postgres >/dev/null 2>&1
sql argos argos <<'SQL'
CREATE TABLE essai (id integer PRIMARY KEY, libelle text NOT NULL);
INSERT INTO essai VALUES (1, 'un'), (2, 'deux'), (3, 'trois');
SQL
sql keycloak keycloak <<'SQL'
CREATE TABLE utilisateur (sujet text PRIMARY KEY);
INSERT INTO utilisateur VALUES ('sujet-alice'), ('sujet-bob');
SQL
CONTENU_ARGOS=$(contenu_argos)
CONTENU_KEYCLOAK=$(contenu_keycloak)

echo "EXP-01-S1, EXP-02-S1, EXP-03-S1 : sauvegarde des deux bases sous umask 000."
mkdir -p "$SAUVEGARDES"
chmod 777 "$SAUVEGARDES"
verifier "code de sortie nul" sh -c "umask 000; sh scripts/sauvegarder-base.sh > '$DOSSIER/sortie.txt' 2>&1"
ARCHIVE_ARGOS=$(archives argos | tail -n 1)
HORODATAGE=$(basename "$ARCHIVE_ARGOS" .sql.gz | sed 's/^argos-//')
verifier "archive argos écrite" test -f "$ARCHIVE_ARGOS"
verifier "archive keycloak de même horodatage" test -f "$SAUVEGARDES/keycloak-$HORODATAGE.sql.gz"
if modes_posix; then
  verifier "archive argos en 0600" test "$(mode_de "$ARCHIVE_ARGOS")" = 600
  verifier "archive keycloak en 0600" test "$(mode_de "$SAUVEGARDES/keycloak-$HORODATAGE.sql.gz")" = 600
  verifier "dossier en 0700" test "$(mode_de "$SAUVEGARDES")" = 700
  if [ "$(id -u)" = 0 ] && command -v su >/dev/null 2>&1; then
    verifier "EXP-03-S2 : lecture refusée au compte nobody" \
      code_non_nul su -s /bin/sh nobody -c "cat '$ARCHIVE_ARGOS' > /dev/null 2>&1"
  else
    echo "  non vérifié : EXP-03-S2, lecture par un autre compte (essai à lancer en root)"
    NON_VERIFIES=$((NON_VERIFIES + 1))
  fi
else
  echo "  non vérifié : permissions (système de fichiers sans modes POSIX, voir ESSAI_SAUVEGARDES)"
  NON_VERIFIES=$((NON_VERIFIES + 1))
fi

verifier ".env lu comme du texte : mot de passe avec « ; » non exécuté" test ! -e "$DOSSIER/injecte.txt"

echo "EXP-01-S1 : rotation après une sauvegarde réussie."
REFERENCE="$DOSSIER/reference.sql.gz"
cp "$ARCHIVE_ARGOS" "$REFERENCE"
rm -f "$SAUVEGARDES"/*.sql.gz
for ANCIENNE in 20000101-000001 20000101-000002 20000101-000003; do
  cp "$REFERENCE" "$SAUVEGARDES/argos-$ANCIENNE.sql.gz"
done
verifier "code de sortie nul" sauvegarder
verifier "$CONSERVEES archives argos conservées" test "$(nombre_archives argos)" = "$CONSERVEES"
verifier "seule la plus ancienne est supprimée" test ! -f "$SAUVEGARDES/argos-20000101-000001.sql.gz"
verifier "la suivante est conservée" test -f "$SAUVEGARDES/argos-20000101-000002.sql.gz"
VALIDES=$(archives argos)

echo "EXP-01-S2, EXP-01-S3 : PostgreSQL arrêté, $CONSERVEES échecs consécutifs."
$COMPOSE stop postgres >/dev/null 2>&1
TENTATIVE=0
while [ "$TENTATIVE" -lt "$CONSERVEES" ]; do
  TENTATIVE=$((TENTATIVE + 1))
  verifier "échec $TENTATIVE : code de sortie non nul" code_non_nul sauvegarder
done
verifier "message nommant la base argos" sortie_contient "base argos"
verifier "aucun fichier temporaire laissé" aucun_partiel
verifier "sauvegardes valides intactes" test "$(archives argos)" = "$VALIDES"
$COMPOSE up -d --wait postgres >/dev/null 2>&1

echo "EXP-01 : export tronqué alors que l'export se dit réussi."
BIN="$DOSSIER/bin"
mkdir -p "$BIN"
DOCKER_REEL=$(command -v docker)
cat > "$BIN/docker" <<SHIM
#!/bin/sh
case "\$*" in
  *pg_dump*) "$DOCKER_REEL" "\$@" | head -c $OCTETS_EXPORT_TRONQUE; exit 0 ;;
esac
exec "$DOCKER_REEL" "\$@"
SHIM
chmod +x "$BIN/docker"
PATH="$BIN:$PATH"
verifier "code de sortie non nul" code_non_nul sauvegarder
PATH="$CHEMIN_INITIAL"
verifier "message nommant l'archive incomplète" sortie_contient "archive de la base argos illisible ou incomplète"
verifier "aucun fichier temporaire laissé" aucun_partiel
verifier "sauvegardes valides intactes" test "$(archives argos)" = "$VALIDES"

echo "EXP-04-S3 : restauration sur des bases modifiées depuis la sauvegarde."
DERNIERE=$(archives argos | tail -n 1)
HORODATAGE=$(basename "$DERNIERE" .sql.gz | sed 's/^argos-//')
sql argos argos <<'SQL'
DELETE FROM essai WHERE id = 1;
INSERT INTO essai VALUES (4, 'quatre');
SQL
echo "INSERT INTO utilisateur VALUES ('sujet-intrus');" | sql keycloak keycloak
verifier "code de sortie nul" restaurer "$HORODATAGE"
verifier "contenu argos égal à la sauvegarde" test "$(contenu_argos)" = "$CONTENU_ARGOS"
verifier "contenu keycloak égal à la sauvegarde" test "$(contenu_keycloak)" = "$CONTENU_KEYCLOAK"
PROPRIETAIRES=$(echo "SELECT tableowner FROM pg_tables WHERE tablename = 'essai';" | sql argos argos)
verifier "objets restaurés sous le propriétaire de la base" test "$PROPRIETAIRES" = argos
verifier "rejouée une seconde fois sans erreur" restaurer "$HORODATAGE"
verifier "toujours aucune ligne en double" test "$(contenu_argos)" = "$CONTENU_ARGOS"

echo "EXP-04-S2 : archive tronquée, puis archive intègre dont une instruction échoue."
head -c "$OCTETS_ARCHIVE_TRONQUEE" "$DERNIERE" > "$SAUVEGARDES/argos-20990101-000000.sql.gz"
verifier "archive tronquée : code de sortie non nul" code_non_nul restaurer 20990101-000000
verifier "message nommant la base argos" sortie_contient "base argos"
verifier "aucun succès annoncé" code_non_nul sortie_contient "Restauration terminée"
verifier "base argos laissée intacte" test "$(contenu_argos)" = "$CONTENU_ARGOS"
printf 'CREATE TABLE essai_partiel (id integer);\nSELECT 1 / 0;\n%s\n' "-- PostgreSQL database dump complete" \
  | gzip > "$SAUVEGARDES/argos-20990101-000001.sql.gz"
verifier "erreur de rechargement : code de sortie non nul" code_non_nul restaurer 20990101-000001
verifier "message nommant la base argos" sortie_contient "rechargement de la base argos"
verifier "aucun succès annoncé" code_non_nul sortie_contient "Restauration terminée"
PARTIELLES=$(echo "SELECT count(*) FROM pg_tables WHERE tablename = 'essai_partiel';" | sql argos argos)
verifier "transaction annulée : aucun objet partiel" test "$PARTIELLES" = 0

echo "EXP-02-S2 : instance sans base keycloak."
echo "DROP DATABASE keycloak WITH (FORCE);" | $COMPOSE exec -T postgres sh -c 'psql -X -q -U "$POSTGRES_USER" -d postgres'
AVANT=$(nombre_archives keycloak)
verifier "code de sortie nul" sauvegarder
verifier "absence de la base keycloak signalée" sortie_contient "Base keycloak absente"
verifier "aucune archive keycloak ajoutée" test "$(nombre_archives keycloak)" = "$AVANT"

echo
if [ "$ECHECS" -gt 0 ]; then
  echo "Essai en échec : $ECHECS contrôle(s) en échec, $NON_VERIFIES non vérifié(s)." >&2
  exit 1
fi
echo "Essai réussi ($NON_VERIFIES contrôle(s) non vérifié(s))."
