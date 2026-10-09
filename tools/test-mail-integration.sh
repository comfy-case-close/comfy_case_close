#!/usr/bin/env bash
# Disposable PostgreSQL + both email-producing services. Mail transports are mocked; no emails are sent.
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/.." && pwd)
cd "$repo_root"
# The changelog is XML with inline SQL. Write every changeSet out as a plain .sql file, in the same
# order as `docker compose up`, and let the disposable database read them.
sql_dir=$(mktemp -d)
python3 tools/changelog_sql.py extract "$sql_dir"
test_container=$(docker run --detach --rm --publish 127.0.0.1::5432 \
  --env POSTGRES_PASSWORD=local-test-only \
  --volume "$sql_dir:/tmp/changelog:ro" postgres:16-alpine)
trap 'docker rm -f "$test_container" >/dev/null 2>&1 || true; rm -rf "$sql_dir"' EXIT
export MAIL_TEST_CONTAINER="$test_container" SQL_DIR="$sql_dir"
python3 - <<'PY'
import os, subprocess, time
container = os.environ['MAIL_TEST_CONTAINER']
for attempt in range(60):
    if subprocess.run(['docker','exec',container,'pg_isready','-U','postgres'], capture_output=True).returncode == 0:
        break
    time.sleep(0.5)
else:
    raise SystemExit('Test PostgreSQL did not become ready')
manifest = [line.split('\t') for line in open(os.path.join(os.environ['SQL_DIR'], 'manifest.tsv')).read().splitlines() if line]
for name, mode in manifest:
    command = ['docker','exec',container,'psql','-U','postgres','-v','ON_ERROR_STOP=1','-q']
    if mode == 'tx':
        command.append('--single-transaction')
    subprocess.run(command+['-f','/tmp/changelog/'+name], check=True)
subprocess.run(['docker','exec',container,'psql','-U','postgres','-q','-c',
               "ALTER ROLE svc_cashclose LOGIN PASSWORD 'cashclose-test-only'; ALTER ROLE svc_identity LOGIN PASSWORD 'fnbx_auth_test_password'"], check=True)
PY
test_port=$(docker port "$test_container" 5432/tcp)
export FNB_CASHCLOSE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:${test_port##*:}/postgres"
export FNB_AUTH_TEST_DB_URL="$FNB_CASHCLOSE_TEST_DB_URL"
export FNB_PERMISSION_TEST_DB_URL="$FNB_CASHCLOSE_TEST_DB_URL"
# Caller may supply JVM options (for example a Mockito Java agent) after the script name.
mvn test -pl services/identity,services/cashclose -am "$@"
