#!/usr/bin/env bash
# Disposable PostgreSQL + both email-producing services. Mail transports are mocked; no emails are sent.
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/.." && pwd)
cd "$repo_root"
test_container=$(docker run --detach --rm --publish 127.0.0.1::5432 \
  --env POSTGRES_PASSWORD=local-test-only \
  --volume "$repo_root/db/changelog:/tmp/changelog:ro" postgres:16-alpine)
trap 'docker rm -f "$test_container" >/dev/null 2>&1 || true' EXIT
export MAIL_TEST_CONTAINER="$test_container"
python3 - <<'PY'
import os, subprocess, time, xml.etree.ElementTree as ET
container = os.environ['MAIL_TEST_CONTAINER']
for attempt in range(60):
    if subprocess.run(['docker','exec',container,'pg_isready','-U','postgres'], capture_output=True).returncode == 0:
        break
    time.sleep(0.5)
else:
    raise SystemExit('Test PostgreSQL did not become ready')
root = ET.parse('db/changelog/db.changelog-master.xml').getroot()
ns = '{http://www.liquibase.org/xml/ns/dbchangelog}'
for changeset in root.findall(ns+'changeSet'):
    for sql in changeset.findall(ns+'sqlFile'):
        command = ['docker','exec',container,'psql','-U','postgres','-v','ON_ERROR_STOP=1','-q']
        if changeset.get('runInTransaction', 'true') != 'false':
            command.append('--single-transaction')
        subprocess.run(command+['-f','/tmp/changelog/'+sql.get('path')], check=True)
subprocess.run(['docker','exec',container,'psql','-U','postgres','-q','-c',
               "ALTER ROLE svc_cashclose LOGIN PASSWORD 'cashclose-test-only'; ALTER ROLE svc_identity LOGIN PASSWORD 'fnbx_auth_test_password'"], check=True)
PY
test_port=$(docker port "$test_container" 5432/tcp)
export FNB_CASHCLOSE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:${test_port##*:}/postgres"
export FNB_AUTH_TEST_DB_URL="$FNB_CASHCLOSE_TEST_DB_URL"
export FNB_PERMISSION_TEST_DB_URL="$FNB_CASHCLOSE_TEST_DB_URL"
# Caller may supply JVM options (for example a Mockito Java agent) after the script name.
mvn test -pl services/identity-service,services/cashclose-service -am "$@"
