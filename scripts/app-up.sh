#!/usr/bin/env bash
# Build + start the FULL Rota stack (infra + backend + web) under the rota-dev project.
# After this runs once, all containers show up in Docker Desktop and can be started /
# stopped individually from there. Re-run it any time to rebuild after code changes.
#
# For active coding you can still use ./gradlew bootRun + pnpm dev on the host instead —
# but don't run both at once, they share ports 8080 / 5173.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE="$ROOT/infra/docker-compose.yml"

docker compose -f "$COMPOSE" --profile app up -d --build

cat <<'EOF'

Rota is starting (give the backend ~20-30s on first run):
  Web      → http://localhost:5173
  Backend  → http://localhost:8080/actuator/health
  Maildev  → http://localhost:1080

Stop just the app:   scripts/app-down.sh
Stop everything:     scripts/dev-down.sh
EOF
