#!/usr/bin/env bash
# ============================================================================
# RLS GUARD QUERIES - ADR-0003 section 9
#
# All of them MUST return EMPTY. If any returns a row, the build fails.
#
# These turn "hopefully nobody forgets" into "nobody can forget". The number one
# cause of cross-tenant leaks in practice is adding a table and forgetting to
# enable RLS on it - query 9.1 catches that at the pull request, not in production.
# ============================================================================
set -euo pipefail
export PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5433}"
export PGDATABASE="${PGDATABASE:-fnbx_oltp}"
export PGUSER="${PGUSER:-fnbx_owner}" PGPASSWORD="${PGPASSWORD:-fnbx_dev_password}"

FAIL=0
run() {  # run <name> <sql>
  local name="$1" sql="$2" out
  out="$(psql -v ON_ERROR_STOP=1 -At -c "$sql")"
  if [[ -n "$out" ]]; then
    echo "FAIL  $name"; echo "$out" | sed 's/^/        /'; FAIL=1
  else
    echo "ok    $name"
  fi
}

# --- 9.1 Every table with business_id must have RLS + FORCE + >=1 policy -----
run "9.1 RLS + FORCE + policy on every tenant table" "
SELECT c.relnamespace::regnamespace || '.' || c.relname
       || '  rls=' || c.relrowsecurity
       || '  force=' || c.relforcerowsecurity
       || '  policies=' || (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid)
FROM pg_class c
JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'business_id' AND a.attnum > 0
WHERE c.relkind = 'r'
  AND c.relnamespace::regnamespace::text IN
      ('identity','platform','files','notify','integration',
       'cashclose','workforce','inventory')
  AND (c.relrowsecurity IS FALSE
    OR c.relforcerowsecurity IS FALSE
    OR (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) = 0);"

# --- 9.2 Only analytics_refresher may hold BYPASSRLS -------------------------
# The matview refresh runs under FORCE RLS with no tenant context, so it needs
# exactly one role that bypasses. Any OTHER role holding the attribute - service
# roles above all - is a hole straight through tenant isolation.
run "9.2 only analytics_refresher has BYPASSRLS" "
SELECT rolname FROM pg_roles
WHERE rolbypassrls
  AND NOT rolsuper
  AND rolname <> 'analytics_refresher';"

# --- 9.3 ai_agent must not be granted directly on a materialized view --------
# PostgreSQL has no RLS on materialized views, so ai_agent may only read the
# security_barrier wrapper views that re-apply the tenant filter.
run "9.3 ai_agent cannot read a matview directly" "
SELECT c.relnamespace::regnamespace || '.' || c.relname
FROM pg_class c
WHERE c.relkind = 'm'
  AND has_table_privilege('ai_agent', c.oid, 'SELECT');"

# --- 9.4 ai_agent must not reach the business schemas at all ----------------
run "9.4 ai_agent cannot reach business schemas" "
SELECT n.nspname
FROM pg_namespace n
WHERE n.nspname IN ('identity','platform','files','notify','integration',
                    'cashclose','workforce','inventory')
  AND has_schema_privilege('ai_agent', n.oid, 'USAGE');"

# --- 9.5 The two decision ledgers must be append-only -----------------------
# Both the trigger and the revoked grant are required; a trigger can be disabled
# by anyone with the privilege, a grant cannot.
run "9.5 decision ledgers are append-only" "
SELECT t.tbl || ' missing ' || t.what
FROM (
  SELECT tbl, 'append-only trigger' AS what FROM unnest(ARRAY[
           'cashclose.cash_close_decision',
           'cashclose.cash_movement_decision',
           'platform.audit_log',
           'integration.shift_sales']) AS tbl
   WHERE NOT EXISTS (
     SELECT 1 FROM pg_trigger g
      WHERE g.tgrelid = tbl::regclass AND NOT g.tgisinternal)
  UNION ALL
  SELECT tbl, 'revoked UPDATE/DELETE' FROM unnest(ARRAY[
           'cashclose.cash_close_decision',
           'cashclose.cash_movement_decision']) AS tbl
   WHERE has_table_privilege('svc_cashclose', tbl::regclass, 'UPDATE')
      OR has_table_privilege('svc_cashclose', tbl::regclass, 'DELETE')
) t;"

echo
if [[ $FAIL -eq 1 ]]; then
  echo "RLS GUARD FAILED - see ADR-0003 section 9." >&2
  exit 1
fi
echo "RLS GUARD: ALL GREEN"
