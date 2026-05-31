#!/usr/bin/env bash
# Rota — stop local development infrastructure stack.
# Pass --volumes to also remove data volumes (full wipe).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../infra/docker-compose.yml"

if [[ "${1:-}" == "--volumes" ]]; then
  echo "→ Stopping Rota dev stack AND removing volumes (data wipe)..."
  docker compose -f "${COMPOSE_FILE}" down --volumes
else
  echo "→ Stopping Rota dev stack (data preserved)..."
  docker compose -f "${COMPOSE_FILE}" down
fi

echo "✓ Done."
