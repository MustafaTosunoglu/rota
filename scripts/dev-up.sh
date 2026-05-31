#!/usr/bin/env bash
# Rota — start local development infrastructure stack.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../infra/docker-compose.yml"

echo "→ Starting Rota dev stack (postgres, redis, kafka, maildev)..."
docker compose -f "${COMPOSE_FILE}" up -d

echo ""
echo "→ Waiting for containers to become healthy..."
docker compose -f "${COMPOSE_FILE}" ps

echo ""
echo "✓ Dev stack starting. Service endpoints:"
echo "    Postgres   localhost:5432   (user: rota / db: rota)"
echo "    Redis      localhost:6379"
echo "    Kafka      localhost:9092"
echo "    MailDev    http://localhost:1080  (SMTP on :1025)"
echo ""
echo "  Check health with: docker compose -f infra/docker-compose.yml ps"
