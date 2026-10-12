#!/usr/bin/env bash
# Disposable PostgreSQL integration suite. Requires Docker, psql, Maven and Java 21.
# Never connects to the development or production database.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
container="$(docker run --detach --rm --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=fnbx_auth_test --env POSTGRES_USER=fnbx_owner \
  --env POSTGRES_PASSWORD=fnbx_auth_test_password postgres:16-alpine)"
# The changelog is XML with inline SQL: every changeSet as a plain .sql file, in `docker compose up` order.
sql_dir="$(mktemp -d)"
python3 tools/changelog_sql.py extract "$sql_dir"
trap 'docker rm --force "$container" >/dev/null 2>&1 || true; rm -rf "$sql_dir"' EXIT
for attempt in {1..60}; do
  if docker exec "$container" pg_isready -U fnbx_owner -d fnbx_auth_test >/dev/null 2>&1; then break; fi
  sleep 1
done
mapping="$(docker port "$container" 5432/tcp)"
export PGHOST=127.0.0.1 PGPORT="${mapping##*:}" PGDATABASE=fnbx_auth_test
export PGUSER=fnbx_owner PGPASSWORD=fnbx_auth_test_password
# Apply the migrations to THIS disposable database (db/apply.sh would migrate the compose database instead).
while IFS=$'\t' read -r migration mode; do
  if [[ "$mode" == "tx" ]]; then
    psql -v ON_ERROR_STOP=1 -q --single-transaction -f "$sql_dir/$migration"
  else
    psql -v ON_ERROR_STOP=1 -q -f "$sql_dir/$migration"
  fi
done < "$sql_dir/manifest.tsv"
psql -v ON_ERROR_STOP=1 -c "ALTER ROLE svc_identity LOGIN PASSWORD 'fnbx_auth_test_password';"
export FNB_AUTH_TEST_DB_URL="jdbc:postgresql://127.0.0.1:${PGPORT}/fnbx_auth_test"
mvn verify
