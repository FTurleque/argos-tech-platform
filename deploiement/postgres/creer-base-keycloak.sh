#!/bin/sh
# Crée la base de Keycloak à côté de celle d'Argos, au premier démarrage du volume PostgreSQL.
set -e
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE DATABASE keycloak"
