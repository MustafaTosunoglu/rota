#!/usr/bin/env bash
# Refreshes the committed OpenAPI snapshot from a RUNNING backend and regenerates the
# TypeScript client (plan §11.4). Start the backend first:
#   cd apps/backend && ./gradlew bootRun --args='--spring.profiles.active=local'
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC_URL="${ROTA_API_DOCS_URL:-http://localhost:8080/v3/api-docs}"
SPEC_FILE="$ROOT/packages/api-client/openapi.json"

echo "Fetching OpenAPI spec from $SPEC_URL ..."
curl -sf "$SPEC_URL" -o "$SPEC_FILE.tmp" || {
  echo "ERROR: cannot reach $SPEC_URL — is the backend running?" >&2
  exit 1
}
# Pretty-print for reviewable diffs.
python3 -m json.tool --sort-keys "$SPEC_FILE.tmp" > "$SPEC_FILE"
rm "$SPEC_FILE.tmp"

echo "Regenerating client (orval) ..."
pnpm --dir "$ROOT/packages/api-client" generate

echo "Done. Review the diff under packages/api-client/ and commit it."
