-- ============================================================================
-- 001 SHARED - extensions, schemas, domain types, enums, utility functions
--
-- `shared` is a TECHNICAL schema, not a domain. It holds the types, enums and
-- helpers every domain schema uses. It depends on nothing, so it does not break
-- the CORE -> FEATURE dependency rule of ADR-0001.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- EXCLUDE constraints on ranges

CREATE SCHEMA IF NOT EXISTS shared;
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS files;
CREATE SCHEMA IF NOT EXISTS notify;
CREATE SCHEMA IF NOT EXISTS integration;
CREATE SCHEMA IF NOT EXISTS cashclose;
CREATE SCHEMA IF NOT EXISTS workforce;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS analytics;

-- ----------------------------------------------------------------------------
-- Domain types
-- ----------------------------------------------------------------------------
CREATE DOMAIN shared.d_money        AS NUMERIC(14,2);
CREATE DOMAIN shared.d_money_nonneg AS NUMERIC(14,2) CHECK (VALUE >= 0);

-- ----------------------------------------------------------------------------
-- Enums
-- ----------------------------------------------------------------------------
CREATE TYPE shared.business_type   AS ENUM ('CAFE','RESTAURANT','PUB','BAR','BAKERY','OTHER');
CREATE TYPE shared.user_role       AS ENUM ('ADMIN','MANAGER','ACCOUNTANT','SHIFT_LEAD','STAFF');
CREATE TYPE shared.close_status    AS ENUM ('DRAFT','SUBMITTED','PENDING_REVIEW','APPROVED','REJECTED','VOIDED');
CREATE TYPE shared.risk_level      AS ENUM ('LOW','MEDIUM','HIGH');
CREATE TYPE shared.approval_action AS ENUM ('SUBMIT','APPROVE','REJECT','REQUEST_CHANGES','VOID');
CREATE TYPE shared.file_kind       AS ENUM ('POS_REPORT','CASH_DRAWER_PHOTO','RECEIPT','TRANSFER_PROOF','AVATAR','OTHER');
CREATE TYPE shared.alert_severity  AS ENUM ('LOW','MEDIUM','HIGH','CRITICAL');
CREATE TYPE shared.alert_channel   AS ENUM ('EMAIL','ZALO','TELEGRAM','SMS','PUSH','IN_APP');
CREATE TYPE shared.alert_status    AS ENUM ('PENDING','SENT','FAILED','CANCELLED');
CREATE TYPE shared.fund_period     AS ENUM ('DAILY','WEEKLY','MONTHLY','ADHOC');
CREATE TYPE shared.fund_status     AS ENUM ('OPEN','CLOSED','VOIDED');
CREATE TYPE shared.config_scope    AS ENUM ('GLOBAL','BUSINESS','BRANCH');
CREATE TYPE shared.pos_vendor      AS ENUM ('IPOS','KIOTVIET','SAPO','HARAVAN','MANUAL');
CREATE TYPE shared.sync_status     AS ENUM ('SUCCESS','FAILED','PARTIAL');
CREATE TYPE shared.expected_cash_source AS ENUM ('POS_SYNC','MANUAL');

-- ----------------------------------------------------------------------------
-- Utility: touch updated_at
-- ----------------------------------------------------------------------------
CREATE FUNCTION shared.fn_touch_updated_at() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END $fn$;

-- Utility: append-only guard (the two decision ledgers, audit_log, shift_sales)
CREATE FUNCTION shared.fn_forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  RAISE EXCEPTION '% is append-only: % not allowed', TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'raise_exception';
END $fn$;

-- ----------------------------------------------------------------------------
-- Tenant context - reads the business_id the backend sets via set_config(..., true)
--
-- CRITICAL: the third argument of set_config MUST be true (is_local), so the
-- setting is scoped to the transaction. A session-level `SET` would LEAK THE
-- TENANT CONTEXT into another tenant's request once the connection is reused by
-- PgBouncer transaction pooling. ADR-0003 section 3.
--
-- Returns NULL when unset, so every RLS policy evaluates to NULL rather than
-- TRUE: forgetting the context shows you NOTHING, never everything. FAIL-CLOSED.
-- ----------------------------------------------------------------------------
CREATE FUNCTION shared.current_business_id() RETURNS uuid
LANGUAGE sql STABLE AS $fn$
  SELECT NULLIF(current_setting('app.business_id', true), '')::uuid
$fn$;

CREATE FUNCTION shared.current_user_id() RETURNS uuid
LANGUAGE sql STABLE AS $fn$
  SELECT NULLIF(current_setting('app.user_id', true), '')::uuid
$fn$;
