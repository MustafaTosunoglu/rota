#!/usr/bin/env bash
# Stop the app containers (backend + web) but LEAVE the infra (postgres/redis/kafka/
# maildev) running — handy when you want to switch back to ./gradlew bootRun on the host.
# To stop the whole stack, use scripts/dev-down.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE="$ROOT/infra/docker-compose.yml"

docker compose -f "$COMPOSE" stop backend web
