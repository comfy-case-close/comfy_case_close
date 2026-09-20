-- ============================================================================
-- 007 CASHCLOSE - INTEGRITY ENGINE
--
-- Project rule: "wrong logic in the backend must not be able to corrupt the
-- database."
--
--   13.1  child rows freeze once the close leaves editing
--   13.2  a close is born clean, as DRAFT
--   13.3  close state machine and column guards
--   13.4  per-movement state machine and decision ledger
--   13.5  VOID is the one status change with no cash_close_decision row
--   13.6  derived formulas, VERSIONED
--   13.7  convenience views
--
-- ── What is gone, and why ───────────────────────────────────────────────────
--
-- fn_refresh_close_totals + trg_*_totals: REMOVED.
--   Six total columns used to be recomputed and written back to cash_close after
--   every child change. Those columns are gone; totals are computed on READ in
--   the versioned views at 13.6. That removed a write-back loop and the
--   set_config('cashclose.maintenance') escape hatch that came with it.
--
-- Drawer identity check: REMOVED.
--   cash_remaining used to be a column the app wrote, which the DB then compared
--   against counted - withdrawal - eod - tips_in_drawer at both submit and
--   approve. It is now that expression, computed in the view. An identity that
--   cannot be violated needs no check. What we give up is the double entry - the
--   shift lead's own claimed figure is no longer there to compare against. That
--   trade is deliberate: in the real data every mismatch was a duplicate-ID or
--   typo, never new information.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 13.1 Child guard: freeze after decision, check tenant scope and kind rules
-- ----------------------------------------------------------------------------
CREATE FUNCTION cashclose.fn_close_child_guard() RETURNS trigger
LANGUAGE plpgsql AS $fn$
DECLARE
  v_close cashclose.cash_close%ROWTYPE;
  v_kind  platform.movement_kind%ROWTYPE;
BEGIN
  IF TG_OP = 'UPDATE' AND NEW.cash_close_id <> OLD.cash_close_id THEN
    RAISE EXCEPTION 'a line cannot be moved to a different cash close';
  END IF;

  SELECT * INTO v_close FROM cashclose.cash_close
   WHERE cash_close_id = COALESCE(NEW.cash_close_id, OLD.cash_close_id);
  IF NOT FOUND THEN
    RAISE EXCEPTION 'cash close % not found', COALESCE(NEW.cash_close_id, OLD.cash_close_id);
  END IF;

  IF v_close.status NOT IN ('DRAFT','SUBMITTED','PENDING_REVIEW') THEN
    RAISE EXCEPTION 'close % is %, its lines are frozen',
      v_close.cash_close_code, v_close.status;
  END IF;

  -- Lookup rows must be global or belong to the same business. Per-kind "note
  -- required" / "receipt required" is enforced here too, because a CHECK cannot
  -- join to another table.
  IF TG_TABLE_NAME = 'cash_movement' AND TG_OP IN ('INSERT','UPDATE') THEN
    SELECT * INTO v_kind FROM platform.movement_kind WHERE kind_sk = NEW.kind_sk;
    IF v_kind.business_id IS NOT NULL AND v_kind.business_id <> v_close.business_id THEN
      RAISE EXCEPTION 'movement kind "%" belongs to another business', v_kind.kind_code;
    END IF;
    -- The kind version must be in force on the close's BUSINESS DATE, not now():
    -- a 15 Jun close submitted late on 17 Jun still follows the 15 Jun rules.
    IF v_close.business_date < v_kind.valid_from
       OR (v_kind.valid_to IS NOT NULL AND v_close.business_date >= v_kind.valid_to) THEN
      RAISE EXCEPTION
        'movement kind "%" (version % .. %) is not in force on business date %',
        v_kind.kind_code, v_kind.valid_from, v_kind.valid_to, v_close.business_date;
    END IF;
    IF v_kind.requires_note AND COALESCE(btrim(NEW.description), '') = '' THEN
      RAISE EXCEPTION 'movement kind "%" requires a description', v_kind.kind_code;
    END IF;
    IF v_kind.requires_receipt AND NEW.receipt_attachment_id IS NULL THEN
      RAISE EXCEPTION 'movement kind "%" requires an attached receipt', v_kind.kind_code;
    END IF;
  END IF;

  RETURN COALESCE(NEW, OLD);
END $fn$;

DO $do$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['cash_denomination_line','cash_movement','close_attachment'] LOOP
    EXECUTE format(
      'CREATE TRIGGER trg_%s_guard BEFORE INSERT OR UPDATE OR DELETE ON cashclose.%I
       FOR EACH ROW EXECUTE FUNCTION cashclose.fn_close_child_guard()', t, t);
  END LOOP;
END $do$;

-- ----------------------------------------------------------------------------
-- 13.2 INSERT: a close is born as a clean DRAFT
-- ----------------------------------------------------------------------------
CREATE FUNCTION cashclose.fn_close_before_insert() RETURNS trigger
LANGUAGE plpgsql AS $fn$
BEGIN
  IF NEW.status <> 'DRAFT' THEN
    RAISE EXCEPTION 'a cash close must be created as DRAFT (got %)', NEW.status;
  END IF;
  -- thresholds are snapshotted at SUBMIT, not at creation
  NEW.applied_diff_allowed_abs := NULL;
  NEW.applied_diff_alert_abs   := NULL;
  NEW.is_late := false;
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_close_before_insert BEFORE INSERT ON cashclose.cash_close
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_close_before_insert();

-- ----------------------------------------------------------------------------
-- 13.3 UPDATE: state machine and column guards
--
-- ── Why close-level approval exists even though it changes no number ───────
-- Per-line approval decides what is EXPLAINED. Close-level approval decides what
-- to do with the RESIDUAL - the part nobody declared at all. A shift 200,000
-- short with zero declared lines has no line to approve; the close itself is the
-- only decision point. The two are not redundant.
--
-- It also provides:
--   * the LOCK - child rows freeze once the close leaves editing (13.1). Without
--     an approved state nothing ever freezes, and someone can edit a movement
--     from three months ago while v_close_calc silently restates history.
--   * the REPORTING FILTER - analytics counts approved shifts only
--   * the ATTESTATION - a named manager accepted this shift, recorded in
--     cashclose.cash_close_decision with who, when, and the note they wrote
--
-- "Closes are records, not things to approve" is the journal-versus-closed-period
-- distinction. Even a pure journal has a close: post freely, then the period locks
-- and turns read-only. Dropping approval does not make this a journal, it makes it
-- a ledger that never closes.
-- ----------------------------------------------------------------------------
CREATE FUNCTION cashclose.fn_close_before_update() RETURNS trigger
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
      (OLD.status = 'REJECTED'       AND NEW.status IN ('DRAFT','VOIDED')) OR
      (OLD.status = 'APPROVED'       AND NEW.status = 'VOIDED')
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
CREATE TRIGGER trg_close_before_update BEFORE UPDATE ON cashclose.cash_close
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_close_before_update();
CREATE TRIGGER trg_close_touch BEFORE UPDATE ON cashclose.cash_close
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- ============================================================================
-- 13.4 Per-movement state machine and decision ledger
--
--   PENDING  -> APPROVED | REJECTED     decide
--   APPROVED -> PENDING                 reopen (manager changed their mind)
--   REJECTED -> PENDING                 reopen (staff will fix and resubmit)
--
-- EDITING RULE: amount and kind may only change while PENDING. Fixing a typo is
-- a correction to the same event, so it updates the row in place - inserting a
-- second row would double-count in every sum. Once a decision exists the figures
-- are frozen; changing them requires a reopen, and the reopen is recorded. So
-- "employee mistyped 5,230,000 instead of 523,000" is a free edit if nobody has
-- looked yet, and an audited event if someone has.
-- ============================================================================
CREATE FUNCTION cashclose.fn_movement_status_guard() RETURNS trigger
LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.approval_status <> 'PENDING' THEN
      RAISE EXCEPTION 'a movement line must be created as PENDING (got %)', NEW.approval_status;
    END IF;
    RETURN NEW;
  END IF;

  -- figures are frozen unless the line is (still) pending
  IF OLD.approval_status <> 'PENDING' AND NEW.approval_status = OLD.approval_status THEN
    IF NEW.signed_amount IS DISTINCT FROM OLD.signed_amount
       OR NEW.kind_sk     IS DISTINCT FROM OLD.kind_sk
       OR NEW.effect_type IS DISTINCT FROM OLD.effect_type THEN
      RAISE EXCEPTION
        'movement line is % - reopen it to PENDING before changing amount or kind',
        OLD.approval_status;
    END IF;
  END IF;

  IF NEW.approval_status IS DISTINCT FROM OLD.approval_status THEN
    IF NOT (
      (OLD.approval_status = 'PENDING'  AND NEW.approval_status IN ('APPROVED','REJECTED')) OR
      (OLD.approval_status = 'APPROVED' AND NEW.approval_status = 'PENDING') OR
      (OLD.approval_status = 'REJECTED' AND NEW.approval_status = 'PENDING')
    ) THEN
      RAISE EXCEPTION 'illegal movement transition % -> %',
        OLD.approval_status, NEW.approval_status;
    END IF;

    -- Reopening must not change the figures in the same statement, otherwise the
    -- decision row would snapshot the NEW amount and the ledger would lose what
    -- was actually approved. Reopen first, then edit.
    IF NEW.approval_status = 'PENDING' THEN
      IF NEW.signed_amount IS DISTINCT FROM OLD.signed_amount
         OR NEW.kind_sk IS DISTINCT FROM OLD.kind_sk THEN
        RAISE EXCEPTION 'reopen and edit must be separate steps, so the ledger keeps the approved figures';
      END IF;
      -- clears the decision so ck_movement_decided_fields still holds
      NEW.decided_by := NULL;
      NEW.decided_at := NULL;
    END IF;

    -- rejecting without saying why is how disputes become unresolvable
    IF NEW.approval_status = 'REJECTED'
       AND COALESCE(btrim(NEW.decision_note), '') = '' THEN
      RAISE EXCEPTION 'rejecting a movement line requires a reason';
    END IF;
  END IF;

  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_movement_status_guard
  BEFORE INSERT OR UPDATE ON cashclose.cash_movement
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_movement_status_guard();

-- The ledger row is written by the DATABASE, not by the application, so
-- "we forgot to log it" is not a possible bug.
--
-- kind_sk and signed_amount are snapshotted because status history alone does not
-- catch the interesting fraud: approve 523,000, reopen, change to 5,230,000,
-- approve again. Without the figures the log would only read
-- PENDING->APPROVED->PENDING->APPROVED.
CREATE FUNCTION cashclose.fn_movement_decision_log() RETURNS trigger
LANGUAGE plpgsql AS $fn$
BEGIN
  IF NEW.approval_status IS DISTINCT FROM OLD.approval_status THEN
    INSERT INTO cashclose.cash_movement_decision
      (movement_id, business_id, old_status, new_status,
       kind_sk, signed_amount, decided_by, note)
    VALUES
      (NEW.movement_id, NEW.business_id,
       OLD.approval_status, NEW.approval_status,
       NEW.kind_sk, NEW.signed_amount,
       COALESCE(NEW.decided_by, shared.current_user_id()),
       NEW.decision_note);
  END IF;
  RETURN NULL;
END $fn$;
CREATE TRIGGER trg_movement_decision_log
  AFTER UPDATE ON cashclose.cash_movement
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_movement_decision_log();

-- ----------------------------------------------------------------------------
-- 13.5 VOID: the only status change with no matching cash_close_decision row,
--      and therefore the only one still mirrored into audit_log.
-- ----------------------------------------------------------------------------
CREATE FUNCTION cashclose.fn_close_void_audit() RETURNS trigger
LANGUAGE plpgsql AS $fn$
DECLARE v_calc RECORD;
BEGIN
  IF NEW.status = 'VOIDED' AND OLD.status <> 'VOIDED' THEN
    SELECT cash_difference, unexplained_difference INTO v_calc
      FROM cashclose.v_close_calc WHERE cash_close_id = OLD.cash_close_id;
    INSERT INTO platform.audit_log (business_id, action, entity_type, entity_id, old_value, new_value)
    VALUES (NEW.business_id, 'CASH_CLOSE_VOIDED', 'cash_close', NEW.cash_close_code,
            jsonb_build_object('status', OLD.status,
                               'cash_difference', v_calc.cash_difference,
                               'unexplained_difference', v_calc.unexplained_difference),
            jsonb_build_object('status', NEW.status, 'voided_at', NEW.voided_at));
  END IF;
  RETURN NULL;
END $fn$;
CREATE TRIGGER trg_close_void_audit AFTER UPDATE ON cashclose.cash_close
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_close_void_audit();

-- ============================================================================
-- 13.6 DERIVED FORMULAS - VERSIONED
--
-- The formula lives in a SQL view, not in Java, because the owner is meant to
-- run ad-hoc queries. If the rule lived in Java, their hand-written query and
-- the app's number would be two independent implementations and would drift.
--
-- ── WHEN TO VERSION ─────────────────────────────────────────────────────────
-- Create a new version ONLY when the change would alter figures on an APPROVED
-- close. Typos, new columns, index changes, formatting -> edit v1 in place.
-- Changing what a plus or minus means -> create v_close_calc_v2.
--
-- Shipping v2:
--   1. CREATE VIEW cashclose.v_close_calc_v2 AS ...   (do NOT touch v1)
--   2. add a UNION ALL branch to v_close_calc
--   3. ALTER TABLE cash_close ALTER COLUMN calc_version SET DEFAULT 'v2';
--   4. UPDATE cashclose.cash_close SET calc_version='v2' WHERE status <> 'APPROVED';
--      -- approved closes are untouched; trigger 13.3(d) blocks it anyway
--
-- Expect to reach v2 once or twice a year. More often than that means the
-- formula is unsettled, not that this mechanism is annoying.
-- ============================================================================
CREATE VIEW cashclose.v_close_calc_v1 AS
WITH counted AS (
  SELECT l.cash_close_id,
         SUM(d.face_value * l.quantity) AS counted_cash
  FROM cashclose.cash_denomination_line l
  JOIN platform.denomination d USING (denomination_id)
  GROUP BY l.cash_close_id
),
mv AS (
  -- A REJECTED line counts as never declared: not explained, not pending, it
  -- falls straight through to unexplained. That is the right signal - "you told
  -- me, but I do not accept it, so this part is still unexplained."
  SELECT m.cash_close_id,
         COALESCE(SUM(m.signed_amount) FILTER (
                    WHERE k.affects_difference AND m.approval_status = 'APPROVED'), 0) AS explained_difference,
         COALESCE(SUM(m.signed_amount) FILTER (
                    WHERE k.affects_difference AND m.approval_status = 'PENDING'),  0) AS pending_difference,
         -- affects_remaining means "this left the drawer before hand-over", so it
         -- is always subtracted as an absolute value, whether the original sign
         -- was an expense (negative) or a tip (positive).
         COALESCE(SUM(abs(m.signed_amount)) FILTER (WHERE k.affects_remaining),           0) AS deducted_total,
         -- EXPENSE is filtered by expense_category, NOT by effect_type.
         -- "Borrowed from the drawer to give change", "swapped small notes" and
         -- "suspected loss" are all CASH_OUT but none of them is a cost;
         -- filtering by direction would inflate the expense report.
         COALESCE(SUM(abs(m.signed_amount)) FILTER (WHERE k.expense_category IS NOT NULL), 0) AS expense_total,
         COALESCE(SUM(abs(m.signed_amount)) FILTER (WHERE m.effect_type = 'CASH_OUT'),     0) AS cash_out_total,
         COALESCE(SUM(abs(m.signed_amount)) FILTER (WHERE m.effect_type = 'CASH_IN'),      0) AS cash_in_total,
         COALESCE(SUM(abs(m.signed_amount)) FILTER (WHERE k.diff_reason_group = 'TIP'),    0) AS tips_total,
         COALESCE(SUM(abs(m.signed_amount)) FILTER (
                    WHERE k.diff_reason_group = 'TIP' AND k.affects_remaining),            0) AS tips_in_drawer_total
  FROM cashclose.cash_movement m
  JOIN platform.movement_kind k ON k.kind_sk = m.kind_sk
  WHERE m.approval_status <> 'REJECTED'
  GROUP BY m.cash_close_id
)
SELECT
  cc.cash_close_id,
  cc.business_id,
  'v1'::text                                          AS calc_version,
  COALESCE(ct.counted_cash, 0)                        AS counted_cash,
  COALESCE(ct.counted_cash, 0) - cc.pos_expected_cash AS cash_difference,
  COALESCE(mv.explained_difference, 0)                AS explained_difference,
  COALESCE(mv.pending_difference,   0)                AS pending_difference,
  COALESCE(ct.counted_cash, 0) - cc.pos_expected_cash
    - COALESCE(mv.explained_difference, 0)
    - COALESCE(mv.pending_difference,   0)            AS unexplained_difference,
  COALESCE(ct.counted_cash, 0) - cc.withdrawal_amount
    - COALESCE(mv.deducted_total, 0)                  AS cash_remaining,
  COALESCE(mv.expense_total,        0)                AS expense_total,
  COALESCE(mv.cash_out_total,       0)                AS cash_out_total,
  COALESCE(mv.cash_in_total,        0)                AS cash_in_total,
  COALESCE(mv.tips_total,           0)                AS tips_total,
  COALESCE(mv.tips_in_drawer_total, 0)                AS tips_in_drawer_total
FROM cashclose.cash_close cc
LEFT JOIN counted ct ON ct.cash_close_id = cc.cash_close_id
LEFT JOIN mv        ON mv.cash_close_id  = cc.cash_close_id;

-- Dispatcher. The app, the dashboard and the owner all read THIS view only.
CREATE VIEW cashclose.v_close_calc AS
SELECT v.*
FROM cashclose.v_close_calc_v1 v
JOIN cashclose.cash_close c
  ON c.cash_close_id = v.cash_close_id AND c.calc_version = 'v1';
-- with v2:  UNION ALL SELECT v.* FROM cashclose.v_close_calc_v2 v
--           JOIN cashclose.cash_close c ON ... AND c.calc_version = 'v2';

-- ============================================================================
-- 13.7 Convenience views
--
-- risk_level is computed here rather than stored: it is a function of the
-- unexplained difference and the two thresholds snapshotted at submit. Because
-- those thresholds are frozen per close, the value is stable over time even if
-- the owner later changes the config.
--
-- The approver comes from cash_close_decision, not from a column on cash_close.
-- ============================================================================
CREATE VIEW cashclose.v_cash_close_overview AS
SELECT cc.business_id,
       cc.cash_close_id,
       cc.cash_close_code,
       bu.business_code,
       br.branch_code, br.branch_name,
       st.shift_code,  st.shift_name,
       cc.business_date,
       u_creator.full_name AS created_by_name,
       u_sub.full_name AS submitted_by_name,
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
  SELECT u.full_name, d.acted_at
  FROM cashclose.cash_close_decision d
  JOIN identity.staff u ON u.staff_id = d.acted_by
  WHERE d.cash_close_id = cc.cash_close_id AND d.new_status = 'APPROVED'
  ORDER BY d.acted_at DESC
  LIMIT 1
) ap ON true;

-- Decision history for movement lines, with the figures each decision endorsed.
-- Reading this answers "was the amount changed after someone approved it".
CREATE VIEW cashclose.v_movement_decision_detail AS
SELECT d.business_id,
       m.cash_close_id,
       cc.cash_close_code,
       d.movement_id,
       d.decision_id,
       d.old_status, d.new_status,
       k.kind_code,
       d.signed_amount,
       abs(d.signed_amount) AS abs_amount,
       u.full_name AS decided_by_name,
       d.decided_at,
       d.note
FROM cashclose.cash_movement_decision d
JOIN cashclose.cash_movement m ON m.movement_id = d.movement_id
JOIN cashclose.cash_close    cc ON cc.cash_close_id = m.cash_close_id
JOIN platform.movement_kind  k ON k.kind_sk = d.kind_sk
LEFT JOIN identity.staff  u ON u.staff_id = d.decided_by;

-- Readable ledger: one row per movement with the arithmetic meaning attached.
CREATE VIEW cashclose.v_cash_movement_detail AS
SELECT m.business_id,
       m.cash_close_id,
       cc.cash_close_code,
       cc.business_date,
       cc.branch_id,
       m.movement_id,
       k.kind_code, k.display_name,
       m.effect_type,
       k.expense_category, k.diff_reason_group,
       k.affects_difference, k.affects_remaining,
       m.signed_amount,
       abs(m.signed_amount) AS abs_amount,
       m.staff_user_id, m.description,
       m.approval_status, m.decided_by, m.decided_at,
       m.receipt_attachment_id, m.created_by, m.created_at
FROM cashclose.cash_movement m
JOIN platform.movement_kind k ON k.kind_sk = m.kind_sk
JOIN cashclose.cash_close  cc ON cc.cash_close_id = m.cash_close_id;
