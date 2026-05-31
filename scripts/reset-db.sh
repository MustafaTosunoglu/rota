#!/usr/bin/env bash
# Rota — reset the local Postgres database (drops + recreates the volume).
# WARNING: destroys all local data in the rota database.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../infra/docker-compose.yml"

echo "⚠  This will DESTROY all local Postgres data. Press Ctrl+C to abort."
read -r -p "Type 'yes' to continue: " confirm
if [[ "${confirm}" != "yes" ]]; then
  echo "Aborted."
  exit 1
fi

echo "→ Removing Postgres container and volume..."
docker compose -f "${COMPOSE_FILE}" rm -sf postgres
docker volume rm rota-dev_postgres-data 2>/dev/null || true

echo "→ Recreating Postgres..."
docker compose -f "${COMPOSE_FILE}" up -d postgres

echo "✓ Postgres reset complete."
