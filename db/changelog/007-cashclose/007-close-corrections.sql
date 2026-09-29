-- Expand the close decision ledger without relabeling historical role codes as positions.
-- acted_role remains only for already-recorded decisions; new writes use acted_permission.
INSERT INTO identity.permission(permission_code, scope, description)
VALUES ('CLOSE_CORRECT', 'BRANCH', 'correct a submitted cash close');

-- Store managers gain correction access through their active position. Existing
-- administrators with business grant authority receive the equivalent branch grant.
INSERT INTO identity.position_permission(position_id, business_id, permission_code)
SELECT p.position_id, p.business_id, 'CLOSE_CORRECT'
FROM identity.staff_position p
WHERE p.position_code IN ('STORE_MANAGER', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM identity.position_permission g
                  WHERE g.position_id=p.position_id AND g.permission_code='CLOSE_CORRECT'
                    AND g.revoked_at IS NULL);

INSERT INTO identity.staff_branch_permission(staff_id, branch_id, business_id, permission_code)
SELECT r.staff_id, r.branch_id, r.business_id, 'CLOSE_CORRECT'
FROM identity.staff_branch_permission r
WHERE r.permission_code='CLOSE_REVIEW' AND r.revoked_at IS NULL
  AND EXISTS (SELECT 1 FROM identity.staff_business_permission a
              WHERE a.staff_id=r.staff_id AND a.business_id=r.business_id
                AND a.permission_code='PERMISSION_GRANT' AND a.revoked_at IS NULL)
  AND NOT EXISTS (SELECT 1 FROM identity.staff_branch_permission x
                  WHERE x.staff_id=r.staff_id AND x.branch_id=r.branch_id
                    AND x.permission_code='CLOSE_CORRECT' AND x.revoked_at IS NULL);

ALTER TABLE cashclose.cash_close_decision ADD COLUMN acted_position TEXT;
ALTER TABLE cashclose.cash_close_decision ADD COLUMN changes JSONB;
ALTER TABLE cashclose.cash_close_decision ADD CONSTRAINT ck_close_decision_edit_changes
  CHECK (action <> 'EDIT' OR (changes IS NOT NULL AND jsonb_typeof(changes) = 'object'));

-- Allow a reviewed correction to reopen an approved or rejected close into review.
CREATE OR REPLACE FUNCTION cashclose.fn_close_before_update() RETURNS trigger
LANGUAGE plpgsql AS $fn$
DECLARE
  v_deadline TIME;
  v_tz       TEXT;
  v_lines    INT;
BEGIN
  -- (a) identity columns are immutable
  IF NEW.cash_close_id <> OLD.cash_close_id OR NEW.business_id <> OLD.business_id THEN
    RAISE EXCEPTION 'cash close identity columns are immutable';
  END IF;
  IF NEW.created_by IS DISTINCT FROM OLD.created_by THEN
    RAISE EXCEPTION 'cash close creator is immutable';
  END IF;
  IF OLD.status <> 'DRAFT' AND (NEW.branch_id       <> OLD.branch_id OR
                                NEW.shift_type_id   <> OLD.shift_type_id OR
                                NEW.business_date   <> OLD.business_date OR
                                NEW.cash_close_code <> OLD.cash_close_code) THEN
    RAISE EXCEPTION 'branch, shift, date and code can only change while DRAFT';
  END IF;

  -- (b) POS-sourced expected cash cannot be hand-edited
  IF OLD.expected_cash_source = 'POS_SYNC'
     AND NEW.pos_expected_cash IS DISTINCT FROM OLD.pos_expected_cash THEN
    RAISE EXCEPTION 'expected cash came from the POS and cannot be edited by hand';
  END IF;

  -- (c) the threshold snapshot is written once
  IF OLD.applied_diff_allowed_abs IS NOT NULL
     AND (NEW.applied_diff_allowed_abs IS DISTINCT FROM OLD.applied_diff_allowed_abs
       OR NEW.applied_diff_alert_abs   IS DISTINCT FROM OLD.applied_diff_alert_abs) THEN
    RAISE EXCEPTION 'applied config thresholds are a snapshot and cannot be overwritten';
  END IF;

  -- (d) the formula version of an APPROVED close is immutable, so that fixing a
  --     formula in code can never change a figure a manager already signed
  IF OLD.status = 'APPROVED' AND NEW.calc_version IS DISTINCT FROM OLD.calc_version THEN
    RAISE EXCEPTION 'close % is approved; calc_version cannot change (% -> %)',
      OLD.cash_close_code, OLD.calc_version, NEW.calc_version;
  END IF;

  -- (e) terminal states are frozen
  IF OLD.status = 'VOIDED' THEN
    RAISE EXCEPTION 'close % is VOIDED and immutable', OLD.cash_close_code;
  END IF;
  IF OLD.status = 'APPROVED' AND NEW.status = OLD.status THEN
    IF NEW.pos_expected_cash   IS DISTINCT FROM OLD.pos_expected_cash OR
       NEW.withdrawal_amount   IS DISTINCT FROM OLD.withdrawal_amount OR
       NEW.submitted_by        IS DISTINCT FROM OLD.submitted_by OR
       NEW.submitted_at        IS DISTINCT FROM OLD.submitted_at OR
       NEW.note                IS DISTINCT FROM OLD.note THEN
      RAISE EXCEPTION 'close % is APPROVED; only VOID remains', OLD.cash_close_code;
    END IF;
  END IF;

  -- (f) state machine
  IF NEW.status IS DISTINCT FROM OLD.status THEN
    IF NOT (
      (OLD.status = 'DRAFT'          AND NEW.status IN ('SUBMITTED','VOIDED')) OR
      (OLD.status = 'SUBMITTED'      AND NEW.status IN ('PENDING_REVIEW','APPROVED','REJECTED','VOIDED')) OR
      (OLD.status = 'PENDING_REVIEW' AND NEW.status IN ('APPROVED','REJECTED','DRAFT','VOIDED')) OR
      (OLD.status = 'REJECTED'       AND NEW.status IN ('DRAFT','PENDING_REVIEW','VOIDED')) OR
      (OLD.status = 'APPROVED'       AND NEW.status IN ('PENDING_REVIEW','VOIDED'))
    ) THEN
      RAISE EXCEPTION 'illegal status transition % -> %', OLD.status, NEW.status;
    END IF;

    -- entering SUBMITTED: require a cash count, snapshot thresholds, flag lateness
    IF NEW.status = 'SUBMITTED' THEN
      SELECT count(*) INTO v_lines
        FROM cashclose.cash_denomination_line WHERE cash_close_id = NEW.cash_close_id;
      IF v_lines = 0 THEN
        RAISE EXCEPTION 'cannot submit %: no denomination count entered', NEW.cash_close_code;
      END IF;

      IF NEW.applied_diff_allowed_abs IS NULL THEN
        NEW.applied_diff_allowed_abs :=
          COALESCE(platform.fn_config_num(NEW.branch_id, 'DIFF_ALLOWED_ABS'), 0);
        NEW.applied_diff_alert_abs :=
          COALESCE(platform.fn_config_num(NEW.branch_id, 'DIFF_ALERT_ABS'), 0);
      END IF;

      SELECT st.submit_deadline, b.timezone INTO v_deadline, v_tz
        FROM identity.shift_type st
        JOIN identity.business b ON b.business_id = st.business_id
       WHERE st.shift_type_id = NEW.shift_type_id;
      NEW.is_late := (NEW.submitted_at AT TIME ZONE v_tz)::time > v_deadline
                     OR (NEW.submitted_at AT TIME ZONE v_tz)::date > NEW.business_date;
    END IF;

    -- entering APPROVED: no line may still be waiting. Approving the close while
    -- an expense is pending means the "explained" figure the manager saw had not
    -- finished telling the story.
    IF NEW.status = 'APPROVED' THEN
      IF EXISTS (SELECT 1 FROM cashclose.cash_movement
                  WHERE cash_close_id = NEW.cash_close_id
                    AND approval_status = 'PENDING') THEN
        RAISE EXCEPTION
          'cannot approve %: movement lines are still pending - approve or reject each line first',
          NEW.cash_close_code;
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END $fn$;

-- A former approval is historical after a correction, not the current approver.
CREATE OR REPLACE VIEW cashclose.v_cash_close_overview AS
SELECT cc.business_id,
       cc.cash_close_id,
       cc.cash_close_code,
       bu.business_code,
       br.branch_code, br.branch_name,
       st.shift_code,  st.shift_name,
       cc.business_date,
       btrim(u_creator.first_name || ' ' || u_creator.last_name) AS created_by_name,
       btrim(u_sub.first_name || ' ' || u_sub.last_name) AS submitted_by_name,
       cc.submitted_at,
       cc.pos_expected_cash, cc.expected_cash_source,
       k.counted_cash,
       k.cash_difference,
       k.explained_difference,
       k.pending_difference,
       k.unexplained_difference,
       k.expense_total,
       k.cash_out_total, k.cash_in_total,
       cc.withdrawal_amount,
       k.cash_remaining,
       k.tips_total, k.tips_in_drawer_total,
       cc.status,
       CASE
         WHEN cc.applied_diff_alert_abs IS NULL                             THEN NULL
         WHEN abs(k.unexplained_difference) > cc.applied_diff_alert_abs     THEN 'HIGH'
         WHEN abs(k.unexplained_difference) > cc.applied_diff_allowed_abs   THEN 'MEDIUM'
         ELSE 'LOW'
       END::shared.risk_level AS risk_level,
       cc.is_late,
       cc.calc_version,
       cc.applied_diff_allowed_abs, cc.applied_diff_alert_abs,
       ap.full_name AS approved_by_name,
       ap.acted_at  AS approved_at
FROM cashclose.cash_close cc
JOIN cashclose.v_close_calc k ON k.cash_close_id = cc.cash_close_id
JOIN identity.business   bu ON bu.business_id   = cc.business_id
JOIN identity.branch     br ON br.branch_id     = cc.branch_id
JOIN identity.shift_type st ON st.shift_type_id = cc.shift_type_id
LEFT JOIN identity.staff u_creator ON u_creator.staff_id = cc.created_by
LEFT JOIN identity.staff u_sub ON u_sub.staff_id = cc.submitted_by
LEFT JOIN LATERAL (
  SELECT btrim(u.first_name || ' ' || u.last_name) AS full_name, d.acted_at
  FROM cashclose.cash_close_decision d
  JOIN identity.staff u ON u.staff_id = d.acted_by
  WHERE d.cash_close_id = cc.cash_close_id AND d.new_status = 'APPROVED'
    AND cc.status = 'APPROVED'
  ORDER BY d.acted_at DESC
  LIMIT 1
) ap ON true;
