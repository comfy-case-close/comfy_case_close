-- Run only through test-staff-names-migration.sh against its disposable database.
DO $test$
DECLARE b uuid; br uuid; s uuid; sh uuid; cc uuid; i integer;
BEGIN
  FOR i IN 1..2 LOOP
    INSERT INTO identity.business(business_code, business_name) VALUES ('NAMES' || i, 'Names test') RETURNING business_id INTO b;
    INSERT INTO identity.branch(business_id, branch_code, branch_name) VALUES (b, 'MAIN', 'Main') RETURNING branch_id INTO br;
    INSERT INTO identity.staff(business_id, employee_code, full_name, passcode_hash)
      VALUES (b, 'LEGACY', CASE WHEN i = 1 THEN 'Ho Viet Bach' ELSE 'Cher' END, 'unused') RETURNING staff_id INTO s;
    INSERT INTO identity.shift_type(business_id, shift_code, shift_name, submit_deadline)
      VALUES (b, 'AM', 'Morning', '12:00') RETURNING shift_type_id INTO sh;
    INSERT INTO cashclose.cash_close(cash_close_code, business_id, branch_id, shift_type_id, business_date, created_by)
      VALUES ('TEST', b, br, sh, current_date, s) RETURNING cash_close_id INTO cc;
    INSERT INTO cashclose.cash_denomination_line(cash_close_id, business_id, denomination_id, quantity)
      SELECT cc, b, denomination_id, 1 FROM platform.denomination ORDER BY denomination_id LIMIT 1;
    UPDATE cashclose.cash_close SET status = 'SUBMITTED', submitted_by = s, submitted_at = now(),
      applied_diff_allowed_abs = 0, applied_diff_alert_abs = 0 WHERE cash_close_id = cc;
    UPDATE cashclose.cash_close SET status = 'APPROVED' WHERE cash_close_id = cc;
  END LOOP;
END $test$;
SELECT analytics.fn_refresh_all();
CREATE TEMP TABLE expected_staff_risk AS TABLE analytics.mv_staff_risk_30d;
CREATE TEMP TABLE expected_view_metadata AS
  SELECT oid, relname, relowner, relacl, reloptions FROM pg_class
  WHERE oid IN ('analytics.staff_risk_30d'::regclass, 'analytics.alert_unacknowledged'::regclass,
    'cashclose.v_cash_close_overview'::regclass, 'cashclose.v_movement_decision_detail'::regclass);

\ir ../db/changelog/002-identity/005-staff-names.sql

DO $test$
BEGIN
  IF EXISTS (SELECT FROM information_schema.columns WHERE table_schema = 'identity' AND table_name = 'staff' AND column_name = 'full_name') THEN
    RAISE EXCEPTION 'Legacy column still exists';
  END IF;
  IF (SELECT count(*) FROM identity.staff WHERE employee_code = 'LEGACY' AND
      ((first_name = 'Ho' AND last_name = 'Viet Bach') OR (first_name = 'Cher' AND last_name = ''))) <> 2 THEN
    RAISE EXCEPTION 'Name backfill or stable employee codes failed';
  END IF;
  IF (SELECT count(*) FROM expected_staff_risk) <> 2 OR EXISTS (
      (TABLE expected_staff_risk EXCEPT TABLE analytics.mv_staff_risk_30d)
      UNION ALL (TABLE analytics.mv_staff_risk_30d EXCEPT TABLE expected_staff_risk)) THEN
    RAISE EXCEPTION 'Staff-risk cache changed during migration';
  END IF;
  IF EXISTS (SELECT FROM expected_view_metadata e LEFT JOIN pg_class c ON c.oid = e.oid
      WHERE c.oid IS NULL OR (c.relowner, c.relacl, c.reloptions) IS DISTINCT FROM (e.relowner, e.relacl, e.reloptions)) THEN
    RAISE EXCEPTION 'View identity, owner, grants or barrier changed';
  END IF;
  IF (SELECT count(*) FROM cashclose.v_cash_close_overview WHERE created_by_name IN ('Ho Viet Bach', 'Cher') AND submitted_by_name = created_by_name) <> 2 THEN
    RAISE EXCEPTION 'Cash-close display names changed';
  END IF;
  IF (SELECT relowner FROM pg_class WHERE oid = 'analytics.mv_staff_risk_30d'::regclass) <> 'analytics_refresher'::regrole THEN
    RAISE EXCEPTION 'Materialized view owner changed';
  END IF;
END $test$;
-- Exercise the real scheduled-refresh function after the swap, then the tenant wrapper.
SELECT set_config('app.business_id', (SELECT business_id::text FROM expected_staff_risk LIMIT 1), true);
SELECT analytics.fn_refresh_all();
SET LOCAL ROLE ai_agent;
DO $test$
BEGIN
  IF (SELECT count(*) FROM analytics.staff_risk_30d) <> 1 THEN
    RAISE EXCEPTION 'Analytics wrapper did not isolate one business';
  END IF;
END $test$;
RESET ROLE;
