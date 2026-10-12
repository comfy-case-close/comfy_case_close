#!/usr/bin/env bash
# Upgrade a populated legacy schema in a disposable PostgreSQL 16 container.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# Every changeSet as a plain .sql file named after its id, in changelog order.
sql_dir="$(mktemp -d)"
python3 tools/changelog_sql.py extract "$sql_dir"
container="$(docker run --detach --rm --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=fnbx_names_test --env POSTGRES_USER=fnbx_owner \
  --env POSTGRES_PASSWORD=fnbx_names_test_password postgres:16-alpine)"
trap 'docker rm --force "$container" >/dev/null 2>&1 || true; rm -rf "$sql_dir"' EXIT
for attempt in {1..60}; do
  if docker exec "$container" pg_isready -U fnbx_owner -d fnbx_names_test >/dev/null 2>&1; then break; fi
  sleep 1
done
mapping="$(docker port "$container" 5432/tcp)"
export PGHOST=127.0.0.1 PGPORT="${mapping##*:}" PGDATABASE=fnbx_names_test
export PGUSER=fnbx_owner PGPASSWORD=fnbx_names_test_password
# Apply everything that runs BEFORE identity 1.0.0.5. The test script applies that changeset itself,
# on top of populated legacy data.
while IFS=$'\t' read -r migration mode; do
  if [[ "$migration" == "identity-1.0.0.5-staff-names.sql" ]]; then break; fi
  psql -v ON_ERROR_STOP=1 -q -f "$sql_dir/$migration"
done < "$sql_dir/manifest.tsv"
psql -v ON_ERROR_STOP=1 -v sqldir="$sql_dir" --single-transaction -f tools/test-staff-names-migration.sql
bash db/rls-guard.sh
echo 'Populated staff-name migration and tenant-isolation checks passed.'
