#!/usr/bin/env bash
# Check host bind persistence across two removed/recreated containers; no app/database starts.
set -euo pipefail
log_test_dir=$(mktemp -d)
trap 'rm -rf "$log_test_dir"' EXIT
mkdir -p "$log_test_dir/cashclose-service/archive"
python3 - "$log_test_dir" <<'PY'
import gzip,sys
from pathlib import Path
p=Path(sys.argv[1])/'cashclose-service/archive/application.previous.log.gz'
with gzip.open(p,'wt') as f:f.write('archive-persistent-marker\n')
PY
docker run --rm --volume "$log_test_dir/cashclose-service:/logs/cashclose-service" \
  --log-driver local --log-opt max-size=20m --log-opt max-file=5 \
  --entrypoint sh postgres:16-alpine -c 'printf "container-one-marker\n" >> /logs/cashclose-service/application.log'
docker run --rm --volume "$log_test_dir/cashclose-service:/logs/cashclose-service" \
  --log-driver local --log-opt max-size=20m --log-opt max-file=5 \
  --entrypoint sh postgres:16-alpine -c \
  'grep -q container-one-marker /logs/cashclose-service/application.log && zcat /logs/cashclose-service/archive/application.previous.log.gz | grep -q archive-persistent-marker'
printf 'PASS: active log and compressed archive survive container deletion/recreation through host bind mount\n'
