-- ============================================================================
-- 910 ROLES & GRANTS - split services by WRITE ownership
--
-- ADR-0002 section 5.0 + ADR-0003 section 3.
--
-- THE RULE IN ONE LINE:
--   Split services by who may WRITE, never by who may READ.
--
-- Service-Based architecture shares ONE database precisely so a service can
-- "leverage SQL queries and joins in the same way a traditional monolithic
-- layered architecture would". Forcing every identity lookup through an HTTP call
-- to identity-service is a Microservices rule, and adopting it here would throw
-- away the single biggest advantage of this style.
--
--   WRITE to schema X  ->  ONLY the service that owns X   (enforced by GRANT)
--   READ other schemas ->  every service, direct joins, no network calls
-- ============================================================================

-- ----------------------------------------------------------------------------
-- The schema-owning role. Applications never connect as this.
-- Even if one did, FORCE ROW LEVEL SECURITY would still filter it - but keeping
-- ownership separate is one more layer.
-- ----------------------------------------------------------------------------
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fnbx_owner') THEN
    CREATE ROLE fnbx_owner LOGIN PASSWORD 'fnbx_dev_password';
  END IF;
END $do$;

-- ----------------------------------------------------------------------------
-- One DB ROLE per service. All NOLOGIN here; real environments use IAM auth or a
-- password from a secret manager.
--
-- THREE mandatory attributes, ADR-0003 section 8:
--   NOBYPASSRLS               -> may never skip tenant isolation
--   statement_timeout         -> one runaway query cannot take down the cluster
--   idle_in_transaction_...   -> a forgotten transaction cannot hold locks forever
-- ----------------------------------------------------------------------------
DO $do$
DECLARE r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['svc_identity','svc_platform','svc_files','svc_notify',
                           'svc_integration','svc_cashclose','svc_workforce',
                           'svc_inventory','svc_reporting'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('CREATE ROLE %I NOLOGIN NOBYPASSRLS', r);
    END IF;
    EXECUTE format('ALTER ROLE %I SET statement_timeout = %L', r, '30s');
    EXECUTE format('ALTER ROLE %I SET idle_in_transaction_session_timeout = %L', r, '60s');
    EXECUTE format('ALTER ROLE %I SET search_path = %L', r, 'shared, public');
  END LOOP;
END $do$;

-- ----------------------------------------------------------------------------
-- Everyone needs to read the shared types
-- ----------------------------------------------------------------------------
DO $do$
DECLARE r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['svc_identity','svc_platform','svc_files','svc_notify',
                           'svc_integration','svc_cashclose','svc_workforce',
                           'svc_inventory','svc_reporting'] LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA shared TO %I', r);
    EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA shared TO %I', r);
  END LOOP;
END $do$;

-- ----------------------------------------------------------------------------
-- PERMISSION MATRIX
--   RW = the service that owns this schema
--   RO = readable for joins, never writable
--
--  service       | identity | platform | files | notify | integration | cashclose | workforce | inventory
--  --------------+----------+----------+-------+--------+-------------+-----------+-----------+----------
--  identity      |    RW    |    RO    |  RO   |   -    |      -      |     -     |     -     |    -
--  platform      |    RO    |    RW    |   -   |   -    |      -      |     -     |     -     |    -
--  files         |    RO    |    RO    |  RW   |   -    |      -      |     -     |     -     |    -
--  notify        |    RO    |    RO    |   -   |   RW   |      -      |     -     |     -     |    -
--  integration   |    RO    |    RO    |  RO   |   -    |     RW      |     -     |     -     |    -
--  cashclose     |    RO    |    RO    |  RO   |   -    |     RO      |    RW     |     -     |    -
--  workforce     |    RO    |    RO    |  RO   |   -    |      -      |    RO     |    RW     |    -
--  inventory     |    RO    |    RO    |  RO   |   -    |      -      |     -     |     -     |   RW
--  reporting     |    RO    |    RO    |  RO   |   RO   |     RO      |    RO     |    RO     |   RO
--
-- notify may NOT read cashclose: the link is soft (source_module +
-- source_entity_id). That is exactly what keeps notify in the CORE group.
-- ----------------------------------------------------------------------------
DO $do$
DECLARE
  rec    RECORD;
  own    TEXT;
  ro     TEXT;
BEGIN
  FOR rec IN
    SELECT * FROM (VALUES
      ('svc_identity',    'identity',    ARRAY['platform','files']),
      ('svc_platform',    'platform',    ARRAY['identity']),
      ('svc_files',       'files',       ARRAY['identity','platform']),
      ('svc_notify',      'notify',      ARRAY['identity','platform']),
      ('svc_integration', 'integration', ARRAY['identity','platform','files']),
      ('svc_cashclose',   'cashclose',   ARRAY['identity','platform','files','integration']),
      ('svc_workforce',   'workforce',   ARRAY['identity','platform','files','cashclose']),
      ('svc_inventory',   'inventory',   ARRAY['identity','platform','files'])
    ) AS t(role_name, own_schema, read_schemas)
  LOOP
    -- WRITE: only on the schema this service owns
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', rec.own_schema, rec.role_name);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO %I',
                   rec.own_schema, rec.role_name);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO %I',
                   rec.own_schema, rec.role_name);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
                   rec.own_schema, rec.role_name);

    -- READ: direct joins into other schemas, no network calls
    FOREACH ro IN ARRAY rec.read_schemas LOOP
      EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', ro, rec.role_name);
      EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO %I', ro, rec.role_name);
      EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT ON TABLES TO %I',
                     ro, rec.role_name);
    END LOOP;
  END LOOP;

  -- reporting: READ ONLY, everywhere
  FOREACH ro IN ARRAY ARRAY['identity','platform','files','notify','integration',
                            'cashclose','workforce','inventory'] LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO svc_reporting', ro);
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO svc_reporting', ro);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT ON TABLES TO svc_reporting', ro);
  END LOOP;
END $do$;

-- ----------------------------------------------------------------------------
-- APPEND-ONLY LEDGERS: revoke at the GRANT layer too, not only via triggers.
--
-- A trigger can be disabled by anyone holding the privilege. A revoked grant
-- cannot. The two layers complement each other rather than duplicating.
-- ----------------------------------------------------------------------------
REVOKE UPDATE, DELETE ON cashclose.cash_close_decision    FROM svc_cashclose;
REVOKE UPDATE, DELETE ON cashclose.cash_movement_decision FROM svc_cashclose;
REVOKE UPDATE, DELETE ON platform.audit_log               FROM svc_platform, svc_identity;
REVOKE UPDATE, DELETE ON integration.shift_sales          FROM svc_integration;
-- INSERT stays granted on cash_movement_decision: a plain plpgsql trigger runs
-- with the CALLER's privileges, so revoking it would break the very trigger that
-- writes the ledger. Append-only is enforced by revoking UPDATE and DELETE plus
-- trg_movement_decision_append_only - the same two layers used for the close
-- decision ledger.

-- audit_log: every service needs to WRITE (login, export, ...), nobody may EDIT
DO $do$
DECLARE r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['svc_identity','svc_files','svc_notify','svc_integration',
                           'svc_cashclose','svc_workforce','svc_inventory'] LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA platform TO %I', r);
    EXECUTE format('GRANT INSERT ON platform.audit_log TO %I', r);
    EXECUTE format('REVOKE UPDATE, DELETE ON platform.audit_log FROM %I', r);
  END LOOP;
END $do$;

-- ----------------------------------------------------------------------------
-- ai_agent - the role for the AI data analyst
--
-- ADR-0003 section 5. SQL produced by an LLM is UNTRUSTED INPUT. Therefore:
--   * SELECT only, in the analytics schema only, on plain VIEWS only
--   * PostgreSQL has no RLS on materialized views, so matviews are wrapped in
--     security_barrier views and mv_* is NEVER granted directly
--   * read-only transactions, a short timeout, and a connection cap
-- ----------------------------------------------------------------------------
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_agent') THEN
    CREATE ROLE ai_agent NOLOGIN NOBYPASSRLS CONNECTION LIMIT 5;
  END IF;
END $do$;
ALTER ROLE ai_agent SET default_transaction_read_only = on;
ALTER ROLE ai_agent SET statement_timeout = '10s';
ALTER ROLE ai_agent SET idle_in_transaction_session_timeout = '30s';
ALTER ROLE ai_agent SET search_path = 'analytics';

GRANT USAGE ON SCHEMA analytics TO ai_agent;
-- Nothing is granted on identity.*, cashclose.*, integration.* or analytics.mv_*.
-- Guard queries 9.3 and 9.4 in db/rls-guard.sh verify that on every build.

-- Authentication revocations are private even when this runOnChange grant matrix reruns.
-- On a fresh database the additive authentication migration creates the table later.
DO $auth_grants$
BEGIN
  IF to_regclass('identity.revoked_token') IS NOT NULL THEN
    REVOKE ALL ON identity.revoked_token FROM PUBLIC,
      svc_platform, svc_files, svc_notify, svc_integration, svc_cashclose,
      svc_workforce, svc_inventory, svc_reporting;
    REVOKE UPDATE ON identity.revoked_token FROM svc_identity;
  END IF;
END $auth_grants$;
