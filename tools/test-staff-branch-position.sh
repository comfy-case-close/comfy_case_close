#!/usr/bin/env bash
# Isolated migration test: never uses the configured application database.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
container="$(docker run --detach --rm \
  --env POSTGRES_PASSWORD=local-test-only \
  --volume "$ROOT/db/changelog:/tmp/db/changelog:ro" \
  --volume "$ROOT/tools/test-staff-branch-position.sql:/tmp/tools/test-staff-branch-position.sql:ro" \
  postgres:16-alpine)"
trap 'docker rm --force "$container" >/dev/null 2>&1 || true' EXIT
for attempt in {1..60}; do
  if docker exec "$container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
while IFS= read -r migration; do
  docker exec "$container" psql -U postgres -v ON_ERROR_STOP=1 -q \
    -f "/tmp/db/changelog/$migration"
done < <(python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse('db/changelog/db.changelog-master.xml').getroot()
for node in root.iter('{http://www.liquibase.org/xml/ns/dbchangelog}sqlFile'):
    path = node.attrib['path']
    if path == '002-identity/012-staff-branch-position.sql':
        break
    print(path)
PY
)
docker exec "$container" psql -U postgres -v ON_ERROR_STOP=1 --single-transaction \
  -f /tmp/tools/test-staff-branch-position.sql
echo 'Position upgrade, role migration, composite FKs and tenant isolation passed.'
