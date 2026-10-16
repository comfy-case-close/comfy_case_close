#!/usr/bin/env bash
# Disposable current-schema PostgreSQL + full backend regression suite. Never uses a real database.
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/.." && pwd)
cd "$repo_root"
# Read SQL files from the current XML master in migration order; apply them only to the disposable DB.
sql_dir=$(mktemp -d)
python3 - "$sql_dir" <<'EXTRACT'
import sys,xml.etree.ElementTree as E
from pathlib import Path
base=Path('db/changelog');out=Path(sys.argv[1]);ns={'x':'http://www.liquibase.org/xml/ns/dbchangelog'}
manifest=[]
for i,cs in enumerate(E.parse(base/'db.changelog-master.xml').getroot().findall('x:changeSet',ns)):
    file=cs.find('x:sqlFile',ns)
    assert file is not None, cs.attrib['id']
    name=f'{i:03d}.sql';(out/name).write_text((base/file.attrib['path']).read_text())
    manifest.append(name+'\t'+('notx' if cs.get('runInTransaction')=='false' else 'tx'))
(out/'manifest.tsv').write_text('\n'.join(manifest)+'\n')
print('Prepared',len(manifest),'current changelog SQL files')
EXTRACT
test_container=$(docker run --detach --rm --publish 127.0.0.1::5432 \
  --env POSTGRES_PASSWORD=local-test-only \
  --volume "$sql_dir:/tmp/changelog:ro" postgres:16-alpine)
trap 'docker rm -f "$test_container" >/dev/null 2>&1 || true; rm -rf "$sql_dir"' EXIT
export LOGGING_TEST_CONTAINER="$test_container" SQL_DIR="$sql_dir"
python3 - <<'PY'
import os, subprocess, time
container = os.environ['LOGGING_TEST_CONTAINER']
for attempt in range(60):
    if subprocess.run(['docker','exec',container,'pg_isready','-h','127.0.0.1','-U','postgres'], capture_output=True).returncode == 0:
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
export FNB_PLATFORM_TEST_DB_URL="$FNB_CASHCLOSE_TEST_DB_URL"
# Caller may supply JVM options (for example a Mockito Java agent) after the script name.
mvn "$@" verify
