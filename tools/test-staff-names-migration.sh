#!/usr/bin/env bash
# Upgrade a populated legacy schema in a disposable PostgreSQL 16 container.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
container="$(docker run --detach --rm --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=fnbx_names_test --env POSTGRES_USER=fnbx_owner \
  --env POSTGRES_PASSWORD=fnbx_names_test_password postgres:16-alpine)"
trap 'docker rm --force "$container" >/dev/null 2>&1 || true' EXIT
for attempt in {1..60}; do
  if docker exec "$container" pg_isready -U fnbx_owner -d fnbx_names_test >/dev/null 2>&1; then break; fi
  sleep 1
done
mapping="$(docker port "$container" 5432/tcp)"
export PGHOST=127.0.0.1 PGPORT="${mapping##*:}" PGDATABASE=fnbx_names_test
export PGUSER=fnbx_owner PGPASSWORD=fnbx_names_test_password
while IFS= read -r migration; do
  psql -v ON_ERROR_STOP=1 -q -f "db/changelog/$migration"
done < <(python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse('db/changelog/db.changelog-master.xml').getroot()
for node in root.iter('{http://www.liquibase.org/xml/ns/dbchangelog}sqlFile'):
    path = node.attrib['path']
    if path == '002-identity/005-staff-names.sql':
        break
    print(path)
PY
)
psql -v ON_ERROR_STOP=1 --single-transaction -f tools/test-staff-names-migration.sql
bash db/rls-guard.sh
echo 'Populated staff-name migration and tenant-isolation checks passed.'
