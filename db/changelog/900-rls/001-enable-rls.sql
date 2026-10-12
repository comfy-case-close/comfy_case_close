-- ============================================================================
-- 900 RLS - TENANT ISOLATION AT THE DATABASE LAYER
--
-- ADR-0003 decision 3. This is the defence layer that must never be missing.
--
-- WHAT ROW LEVEL SECURITY IS: a policy attached to a table that PostgreSQL adds
-- to every query as an invisible WHERE clause. `SELECT * FROM cash_close` becomes
-- `... WHERE business_id = shared.current_business_id()` no matter who wrote the
-- query. Isolation stops being something the application must remember and
-- becomes something the database enforces.
--
-- THREE CONDITIONS - miss one and RLS is decorative:
--   1. ENABLE ROW LEVEL SECURITY   -> turns the mechanism on
--   2. FORCE ROW LEVEL SECURITY    -> applies it to the table OWNER too
--   3. The application role owns no tables and has NOBYPASSRLS
--
-- FAIL-CLOSED: shared.current_business_id() returns NULL when the backend forgets
-- to call set_config. `business_id = NULL` yields NULL, not TRUE, so no policy
-- matches and the query returns zero rows. Forgetting the context shows you
-- NOTHING rather than EVERYTHING.
--
-- That same property is why the analytics refresh needed a BYPASSRLS role: an
-- empty matview and a correctly-empty result look identical. See 800-analytics.
--
-- This file is RE-RUNNABLE (changeSet runOnChange=true): a table added later gets
-- RLS automatically on the next run.
--
-- The guard queries in db/rls-guard.sh must return EMPTY on every build.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Helper: enable RLS + FORCE + the standard policy on a table with business_id.
-- Idempotent - drops the old policy before recreating it.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION shared.fn_apply_tenant_rls(p_schema TEXT, p_table TEXT)
RETURNS void LANGUAGE plpgsql AS $fn$
BEGIN
  EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', p_schema, p_table);
  EXECUTE format('ALTER TABLE %I.%I FORCE  ROW LEVEL SECURITY', p_schema, p_table);
  EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I.%I', p_schema, p_table);
  EXECUTE format($p$
    CREATE POLICY tenant_isolation ON %I.%I
      USING      (business_id = shared.current_business_id())
      WITH CHECK (business_id = shared.current_business_id())
  $p$, p_schema, p_table);
END $fn$;

-- ----------------------------------------------------------------------------
-- Variant for lookup tables that are partly global and partly per tenant:
--   business_id IS NULL = a shared default every tenant can READ,
--   but a tenant may only WRITE its own rows (WITH CHECK rejects NULL).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION shared.fn_apply_tenant_or_global_rls(p_schema TEXT, p_table TEXT)
RETURNS void LANGUAGE plpgsql AS $fn$
BEGIN
  EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', p_schema, p_table);
  EXECUTE format('ALTER TABLE %I.%I FORCE  ROW LEVEL SECURITY', p_schema, p_table);
  EXECUTE format('DROP POLICY IF EXISTS tenant_isolation  ON %I.%I', p_schema, p_table);
  EXECUTE format('DROP POLICY IF EXISTS tenant_or_global  ON %I.%I', p_schema, p_table);
  EXECUTE format($p$
    CREATE POLICY tenant_or_global ON %I.%I
      USING      (business_id IS NULL OR business_id = shared.current_business_id())
      WITH CHECK (business_id = shared.current_business_id())
  $p$, p_schema, p_table);
END $fn$;

-- ----------------------------------------------------------------------------
-- APPLIED TO EVERY TABLE WITH A business_id COLUMN - discovered, not listed.
--
-- Hand-maintained lists are the number one cause of cross-tenant leaks in
-- practice: someone adds a table and forgets to add it to the list. This loop
-- cannot forget.
--
-- The tables below are handled SEPARATELY after the loop, because they need a
-- different policy:
--   platform.movement_kind, platform.app_config
--     -> must expose global defaults (business_id IS NULL) to every tenant
--   platform.audit_log
--     -> system events may belong to no tenant at all (e.g. LOGIN_FAILED)
-- ----------------------------------------------------------------------------
DO $do$
DECLARE
  r RECORD;
  v_special CONSTANT TEXT[] := ARRAY[
    'platform.movement_kind',
    'platform.app_config', 'platform.audit_log'];
BEGIN
  FOR r IN
    SELECT c.relnamespace::regnamespace::text AS sch, c.relname AS tbl
    FROM pg_class c
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'business_id' AND a.attnum > 0
    WHERE c.relkind = 'r'
      AND c.relnamespace::regnamespace::text IN
          ('identity','platform','files','notify','integration',
           'cashclose','workforce','inventory')
      AND NOT (c.relnamespace::regnamespace::text || '.' || c.relname) = ANY(v_special)
  LOOP
    PERFORM shared.fn_apply_tenant_rls(r.sch, r.tbl);
  END LOOP;
END $do$;

-- ----------------------------------------------------------------------------
-- Lookup tables: global defaults are readable by everyone
-- ----------------------------------------------------------------------------
SELECT shared.fn_apply_tenant_or_global_rls('platform', 'movement_kind');
SELECT shared.fn_apply_tenant_or_global_rls('platform', 'app_config');

-- ----------------------------------------------------------------------------
-- audit_log: reads are tenant-filtered (plus system events), writes are not blocked.
--
-- Why WITH CHECK (true): platform.fn_audit_config_change fires in the context of
-- whichever service changed the config. Blocking the write here would break the
-- audit path - and losing an audit trail is worse than leaking one. The
-- compensation is REVOKE UPDATE, DELETE in 910-roles, which keeps it append-only.
-- ----------------------------------------------------------------------------
ALTER TABLE platform.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.audit_log FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation  ON platform.audit_log;
DROP POLICY IF EXISTS tenant_or_system  ON platform.audit_log;
CREATE POLICY tenant_or_system ON platform.audit_log
  USING      (business_id IS NULL OR business_id = shared.current_business_id())
  WITH CHECK (true);
