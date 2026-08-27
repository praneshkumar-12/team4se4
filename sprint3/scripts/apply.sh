#!/bin/bash

set -e

# Get database name from TARGET_DATABASE.
# If not set, read POSTGRES_DB from .env.
if [ -n "${TARGET_DATABASE:-}" ]; then
    DATABASE="$TARGET_DATABASE"
else
    DATABASE=$(grep '^POSTGRES_DB=' .env | cut -d '=' -f2-)
fi

if [ -z "$DATABASE" ]; then
    echo "Error: TARGET_DATABASE is not set and POSTGRES_DB was not found in .env"
    exit 1
fi

echo "Target database: $DATABASE"

# PostgreSQL connection settings
PSQL="psql"
HOST="localhost"
PORT="5432"
USER="postgres"

# --------------------------------------------------
# Drop existing database
# --------------------------------------------------

echo "Dropping database if it exists: $DATABASE"

"$PSQL" \
    -h "$HOST" \
    -p "$PORT" \
    -U "$USER" \
    -d "postgres" \
    -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS \"$DATABASE\" WITH (FORCE);"

# --------------------------------------------------
# Create fresh database
# --------------------------------------------------

echo "Creating database: $DATABASE"

"$PSQL" \
    -h "$HOST" \
    -p "$PORT" \
    -U "$USER" \
    -v ON_ERROR_STOP=1 \
    -d "postgres" \
    -c "CREATE DATABASE \"$DATABASE\";"

echo "Database created successfully."

# --------------------------------------------------
# Apply migrations
# --------------------------------------------------

for migration in migrations/*.sql; do
    [ -e "$migration" ] || continue

    echo "Applying migration: $migration"

    "$PSQL" \
        -h "$HOST" \
        -p "$PORT" \
        -U "$USER" \
        -d "$DATABASE" \
        -v ON_ERROR_STOP=1 \
        -f "$migration"
done

# --------------------------------------------------
# Load seed files
# --------------------------------------------------

for seed_file in seed/*.sql; do
    [ -e "$seed_file" ] || continue

    echo "Loading seed file: $seed_file"

    "$PSQL" \
        -h "$HOST" \
        -p "$PORT" \
        -U "$USER" \
        -d "$DATABASE" \
        -v ON_ERROR_STOP=1 \
        -f "$seed_file"
done

echo ""
echo "Database migration and seed loading completed successfully."
echo "Database: $DATABASE"