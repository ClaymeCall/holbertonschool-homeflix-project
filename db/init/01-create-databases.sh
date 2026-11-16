#!/bin/sh
# Runs once, the first time the PostgreSQL data volume is initialized.
# Creates one database and one dedicated user per service (ADR-0006).
# Each user owns only its own database, and PUBLIC access is revoked, so a
# service cannot connect to another service's database.
set -eu

create_service_db() {
  name="$1"
  password="$2"
  psql -v ON_ERROR_STOP=1 -v pass="$password" \
    --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE ${name} LOGIN PASSWORD :'pass';
CREATE DATABASE ${name} OWNER ${name};
REVOKE ALL ON DATABASE ${name} FROM PUBLIC;
SQL
}

create_service_db homeflix_identity "$IDENTITY_DB_PASSWORD"
create_service_db homeflix_library "$LIBRARY_DB_PASSWORD"
create_service_db homeflix_acquisition "$ACQUISITION_DB_PASSWORD"
