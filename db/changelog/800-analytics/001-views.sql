-- ============================================================================
-- 800 ANALYTICS - the read layer for dashboards and the AI agent
--
-- ADR-0003 section 5: no separate warehouse yet. Materialized views over the OLTP
-- database are enough at 200 tenants (~63GB after 3 years).
--
-- PostgreSQL DOES NOT SUPPORT RLS ON MATERIALIZED VIEWS. That is why every matview
-- is wrapped in a plain view with security_barrier that re-applies the tenant
-- filter. ai_agent is GRANTed on the wrapper view ONLY, never on mv_*. Guard query
-- 9.3 in db/rls-guard.sh checks this on every build.
--
-- ── Refreshing under FORCE ROW LEVEL SECURITY ──────────────────────────────
-- The base tables have FORCE RLS, which applies to the table OWNER as well. A
-- scheduled REFRESH runs with no app.business_id set, so shared.current_business_id()
-- returns NULL, every policy evaluates to NULL, and the matview would refresh to
-- ZERO ROWS - silently, because fail-closed looks exactly like "no data".
--
-- Fix: fn_refresh_all is SECURITY DEFINER and owned by analytics_refresher, the
-- one role in the system with BYPASSRLS. Only that role bypasses; every reader
-- still goes through the wrapper views. Guard query 9.2 asserts no other role has
-- the attribute.
-- ============================================================================

-- ⚠️ Con so o day KHONG con doc tu cot tren cash_close (cac cot tong da bi xoa).
--    Chung den tu cashclose.v_close_calc — view co danh phien ban, tu chon dung
--    cong thuc theo cash_close.calc_version cua tung phieu. Nho vay phieu duyet
--    nam 2026 van giu con so cua nam 2026 sau khi cong thuc doi.
-- ----------------------------------------------------------------------------
-- Roles are created defensively here because this file GRANTs to them and it
-- runs BEFORE 910-roles in the master changelog. 910 creates them idempotently
-- too and is where their attributes and timeouts are set; this block only makes
-- a fresh database work.
-- ----------------------------------------------------------------------------
DO $do$
DECLARE r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['svc_reporting','ai_agent'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('CREATE ROLE %I NOLOGIN NOBYPASSRLS', r);
    END IF;
  END LOOP;
END $do$;

CREATE MATERIALIZED VIEW analytics.mv_cash_close_daily AS
SELECT cc.business_id,
       cc.branch_id,
       br.branch_name,
       cc.business_date,
       count(*)                                              AS close_count,
       count(*) FILTER (WHERE cc.is_late)                     AS late_count,
       count(*) FILTER (WHERE cc.expected_cash_source = 'MANUAL') AS manual_expected_count,
       sum(cc.pos_expected_cash)                              AS expected_cash,
       sum(k.counted_cash)                                    AS counted_cash,
       sum(k.cash_difference)                                 AS cash_difference,
       sum(k.explained_difference)                            AS explained_difference,
       sum(k.unexplained_difference)                          AS unexplained_difference,
       sum(k.expense_total)                                   AS total_expense,
       sum(k.cash_in_total)                                   AS total_cash_in,
       sum(k.tips_total)                                      AS tips_total,
       sum(cc.withdrawal_amount)                              AS withdrawal_amount,
       sum(k.cash_remaining)                                  AS cash_remaining
FROM cashclose.cash_close cc
JOIN cashclose.v_close_calc k ON k.cash_close_id = cc.cash_close_id
JOIN identity.branch br ON br.branch_id = cc.branch_id
WHERE cc.status = 'APPROVED'
GROUP BY cc.business_id, cc.branch_id, br.branch_name, cc.business_date;

CREATE UNIQUE INDEX uq_mv_cash_close_daily
  ON analytics.mv_cash_close_daily (business_id, branch_id, business_date);

-- ----------------------------------------------------------------------------
-- The wrapper view - THIS is what ai_agent may read.
-- security_barrier stops the planner pushing a filter condition below a
-- user-defined function, which is a real data-exfiltration technique: a leaky
-- function evaluated first would see rows the tenant filter should have removed.
-- ----------------------------------------------------------------------------
CREATE VIEW analytics.cash_close_daily
WITH (security_barrier = true) AS
SELECT * FROM analytics.mv_cash_close_daily
WHERE business_id = shared.current_business_id();

GRANT SELECT ON analytics.cash_close_daily TO ai_agent;
GRANT SELECT ON analytics.cash_close_daily TO svc_reporting;
-- KHONG: GRANT ... ON analytics.mv_cash_close_daily TO ai_agent;

-- ----------------------------------------------------------------------------
-- Staff with repeated unexplained gaps - the input for fraud detection.
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW analytics.mv_staff_risk_30d AS
SELECT cc.business_id,
       cc.submitted_by                                        AS staff_id,
       u.full_name,
       count(*)                                               AS close_count,
       count(*) FILTER (WHERE abs(k.unexplained_difference)
                              > cc.applied_diff_allowed_abs)   AS over_threshold_count,
       sum(abs(k.unexplained_difference))                      AS total_unexplained,
       -- A gap that leans consistently one way is more suspicious than one that
       -- balances out: a careless counter errs in both directions, someone
       -- skimming errs in only one.
       sum(k.unexplained_difference)                           AS net_unexplained,
       max(cc.business_date)                                   AS last_close_date
FROM cashclose.cash_close cc
JOIN cashclose.v_close_calc k ON k.cash_close_id = cc.cash_close_id
JOIN identity.staff u ON u.staff_id = cc.submitted_by
WHERE cc.status = 'APPROVED'
  AND cc.business_date >= current_date - INTERVAL '30 days'
GROUP BY cc.business_id, cc.submitted_by, u.full_name;

CREATE UNIQUE INDEX uq_mv_staff_risk_30d
  ON analytics.mv_staff_risk_30d (business_id, staff_id);

CREATE VIEW analytics.staff_risk_30d
WITH (security_barrier = true) AS
SELECT * FROM analytics.mv_staff_risk_30d
WHERE business_id = shared.current_business_id();

GRANT SELECT ON analytics.staff_risk_30d TO ai_agent;
GRANT SELECT ON analytics.staff_risk_30d TO svc_reporting;

-- ----------------------------------------------------------------------------
-- Unacknowledged HIGH/CRITICAL alerts - the single most important operational
-- metric of the whole control system. An ignored alert is worse than no alert,
-- because it manufactures a false sense of safety.
-- ----------------------------------------------------------------------------
CREATE VIEW analytics.alert_unacknowledged
WITH (security_barrier = true) AS
SELECT a.alert_id, a.business_id, br.branch_code,
       a.source_module, a.alert_type, a.severity, a.message,
       a.channel, a.status,
       u_rcpt.full_name AS recipient_name,
       u_subj.full_name AS subject_name,
       a.created_at,
       now() - a.created_at AS age
FROM notify.alert a
LEFT JOIN identity.branch   br     ON br.branch_id      = a.branch_id
LEFT JOIN identity.staff u_rcpt ON u_rcpt.staff_id   = a.recipient_user_id
LEFT JOIN identity.staff u_subj ON u_subj.staff_id   = a.subject_user_id
WHERE a.acknowledged_at IS NULL
  AND a.severity IN ('HIGH','CRITICAL')
  AND a.business_id = shared.current_business_id();

GRANT SELECT ON analytics.alert_unacknowledged TO svc_reporting;

-- ============================================================================
-- Refresh - called by the scheduler. CONCURRENTLY needs a unique index, which
-- both matviews have above.
--
-- ⚠️ SECURITY DEFINER is load-bearing here, not decoration.
--
-- The base tables use FORCE ROW LEVEL SECURITY, which applies to the table owner
-- too. Only a role with BYPASSRLS escapes it. A scheduled refresh has no
-- app.business_id set, so without this the matviews would rebuild to zero rows
-- and stay empty - and because RLS fails closed, that looks identical to
-- "there was no data", with no error anywhere.
--
-- analytics_refresher is the ONLY role in the system with BYPASSRLS. It owns
-- nothing else, can log in nowhere, and is reachable only through this function.
-- Every actual reader still goes through the security_barrier wrapper views,
-- which re-apply the tenant filter.
-- ============================================================================
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'analytics_refresher') THEN
    CREATE ROLE analytics_refresher NOLOGIN BYPASSRLS;
  END IF;
END $do$;

GRANT USAGE ON SCHEMA analytics, cashclose, identity TO analytics_refresher;
GRANT SELECT ON ALL TABLES IN SCHEMA cashclose, identity TO analytics_refresher;

CREATE FUNCTION analytics.fn_refresh_all() RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = analytics, cashclose, identity, shared, pg_temp
AS $fn$
BEGIN
  REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_cash_close_daily;
  REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_staff_risk_30d;
END $fn$;

-- The function must be OWNED by the bypassing role for SECURITY DEFINER to help.
ALTER FUNCTION analytics.fn_refresh_all() OWNER TO analytics_refresher;
ALTER MATERIALIZED VIEW analytics.mv_cash_close_daily OWNER TO analytics_refresher;
ALTER MATERIALIZED VIEW analytics.mv_staff_risk_30d   OWNER TO analytics_refresher;

-- Any service may trigger a refresh; none of them gains RLS bypass by doing so.
GRANT EXECUTE ON FUNCTION analytics.fn_refresh_all() TO svc_reporting;
