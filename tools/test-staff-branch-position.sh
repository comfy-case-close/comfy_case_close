#!/usr/bin/env bash
# Isolated migration test: never uses the configured application database.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# Every changeSet as a plain .sql file named after its id, in changelog order.
sql_dir="$(mktemp -d)"
python3 tools/changelog_sql.py extract "$sql_dir"
container="$(docker run --detach --rm \
  --env POSTGRES_PASSWORD=local-test-only \
  --volume "$sql_dir:/tmp/db/changelog:ro" \
  --volume "$ROOT/tools/test-staff-branch-position.sql:/tmp/tools/test-staff-branch-position.sql:ro" \
  postgres:16-alpine)"
trap 'docker rm --force "$container" >/dev/null 2>&1 || true; rm -rf "$sql_dir"' EXIT
for attempt in {1..60}; do
  if docker exec "$container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
# Apply everything that runs BEFORE identity 1.0.0.12. The test script applies 1.0.0.12 and 1.0.0.13
# itself, on top of populated legacy data.
while IFS=$'\t' read -r migration mode; do
  if [[ "$migration" == "identity-1.0.0.12-staff-branch-position.sql" ]]; then break; fi
  docker exec "$container" psql -U postgres -v ON_ERROR_STOP=1 -q -f "/tmp/db/changelog/$migration"
done < "$sql_dir/manifest.tsv"
docker exec "$container" psql -U postgres -v ON_ERROR_STOP=1 --single-transaction \
  -f /tmp/tools/test-staff-branch-position.sql
echo 'Position upgrade, role migration, composite FKs and tenant isolation passed.'
