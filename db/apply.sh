#!/usr/bin/env bash
# ============================================================================
# Liquibase front end for day-to-day work.
#
# `docker compose up -d` (in this directory) ALREADY runs `update`, so a normal
# start needs none of this. Use this script when you want anything else:
#
#   ./apply.sh                   update, then the RLS guard (what CI runs)
#   ./apply.sh status            which changesets have not run yet
#   ./apply.sh history           what ran, when, and by which author
#   ./apply.sh validate          check the changelog BEFORE committing
#   ./apply.sh update-sql        PRINT the SQL that would run, touch nothing
#   ./apply.sh rollback-count 1  undo 1 changeset (only works where the changeset
#                                declares a rollback; the SQL changesets do not -
#                                migrations are forward only, see changelog/master.xml)
#   ./apply.sh drop-all          wipe the schemas (ONLY while there is no real data)
#
#   ./apply.sh changelog-sync    ONCE, on a database that was migrated by the old psql
#                                loop: marks EVERY changeset as run WITHOUT executing
#                                any SQL. Skip it and the first `update` tries to
#                                CREATE TABLE over tables that already exist.
#
# ADR-0003 decision 7:
#     db/  --migration-->  fnbx_oltp  --then-->  deploy the services
#     NEVER the other way round. Services do not migrate on start-up: the svc_*
#     roles have no DDL rights and spring.jpa.hibernate.ddl-auto is `validate`.
#
# The ORDER lives in exactly one place: changelog/master.xml. This script keeps no
# copy of it (a second list would drift, and nothing would stop CI from running a
# different set of files than a developer machine).
#
# Liquibase runs in the pinned container of the `liquibase` service in
# docker-compose.yml, so nobody installs it.
#
# ENVIRONMENT
#   FNBX_SKIP_RLS_GUARD=1        skip the RLS guard (CI runs it as its own step)
# ============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE=(docker compose -f "${HERE}/docker-compose.yml")

# No `shift` followed by an empty "$@": bash 3.2 (macOS default) fails with
# "unbound variable" under set -u.
CMD="${1:-update}"
ARGS=("${CMD}")
if (( $# > 1 )); then ARGS+=("${@:2}"); fi

echo "==> liquibase ${ARGS[*]}"
# --rm: the migration container is throw-away.
# depends_on in the compose file already waits for postgres to be healthy, so no
# sleep or retry is needed here.
"${COMPOSE[@]}" run --rm liquibase "${ARGS[@]}"

# The RLS guard only means something after the schema changed. `status`, `history`,
# `validate` and `update-sql` do not touch the database, so they skip it.
case "${CMD}" in
  update|update-count|update-to-tag|changelog-sync|rollback*|drop-all)
    if [[ "${FNBX_SKIP_RLS_GUARD:-0}" == "1" ]]; then
      echo
      echo "==> RLS guard skipped (FNBX_SKIP_RLS_GUARD=1)"
    else
      echo
      echo "==> Migration done. Running the RLS guard..."
      "${HERE}/rls-guard.sh"
    fi
    ;;
esac
