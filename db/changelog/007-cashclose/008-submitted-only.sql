-- Final schema accepts only completed submissions; the historical enum label is
-- blocked by CHECK constraints because PostgreSQL cannot drop an enum value in place.
-- Close the now-unused opening grants while retaining immutable permission history.
UPDATE identity.position_permission SET revoked_at=greatest(clock_timestamp(), granted_at)
WHERE permission_code='CLOSE_OPEN' AND revoked_at IS NULL;
UPDATE identity.staff_branch_permission SET revoked_at=greatest(clock_timestamp(), granted_at)
WHERE permission_code='CLOSE_OPEN' AND revoked_at IS NULL;

ALTER TABLE cashclose.cash_close ALTER COLUMN status SET DEFAULT 'SUBMITTED';
ALTER TABLE cashclose.cash_close DROP CONSTRAINT ck_close_submitted_fields;
ALTER TABLE cashclose.cash_close DROP CONSTRAINT ck_close_thresholds_snapshotted;
ALTER TABLE cashclose.cash_close ADD CONSTRAINT ck_close_submitted_fields
  CHECK (submitted_by IS NOT NULL AND submitted_at IS NOT NULL);
ALTER TABLE cashclose.cash_close ADD CONSTRAINT ck_close_thresholds_snapshotted
  CHECK (applied_diff_allowed_abs IS NOT NULL AND applied_diff_alert_abs IS NOT NULL);
ALTER TABLE cashclose.cash_close ADD CONSTRAINT ck_close_no_draft CHECK (status <> 'DRAFT');
ALTER TABLE cashclose.cash_close_decision ALTER COLUMN old_status DROP NOT NULL;
ALTER TABLE cashclose.cash_close_decision ADD CONSTRAINT ck_close_decision_initial
  CHECK ((action='SUBMIT' AND old_status IS NULL AND new_status='SUBMITTED')
      OR (action<>'SUBMIT' AND old_status IS NOT NULL));
ALTER TABLE cashclose.cash_close_decision ADD CONSTRAINT ck_close_decision_no_draft
  CHECK (old_status IS DISTINCT FROM 'DRAFT' AND new_status <> 'DRAFT');

CREATE OR REPLACE FUNCTION cashclose.fn_close_before_insert() RETURNS trigger
LANGUAGE plpgsql AS $fn$
DECLARE
  v_deadline TIME;
  v_tz TEXT;
BEGIN
  IF NEW.status <> 'SUBMITTED' THEN
    RAISE EXCEPTION 'a cash close must be submitted when created';
  END IF;
  IF NEW.submitted_by IS NULL OR NEW.submitted_at IS NULL THEN
    RAISE EXCEPTION 'submission requires submitter and time';
  END IF;
  NEW.applied_diff_allowed_abs := COALESCE(platform.fn_config_num(NEW.branch_id, 'DIFF_ALLOWED_ABS'), 0);
  NEW.applied_diff_alert_abs := COALESCE(platform.fn_config_num(NEW.branch_id, 'DIFF_ALERT_ABS'), 0);
  SELECT st.submit_deadline, b.timezone INTO v_deadline, v_tz
  FROM identity.shift_type st JOIN identity.business b ON b.business_id=st.business_id
  WHERE st.shift_type_id=NEW.shift_type_id;
  NEW.is_late := (NEW.submitted_at AT TIME ZONE v_tz)::time > v_deadline
              OR (NEW.submitted_at AT TIME ZONE v_tz)::date > NEW.business_date;
  RETURN NEW;
END $fn$;

CREATE OR REPLACE FUNCTION cashclose.fn_close_child_guard() RETURNS trigger
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

  IF v_close.status NOT IN ('SUBMITTED','PENDING_REVIEW') THEN
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

CREATE OR REPLACE FUNCTION cashclose.fn_close_before_update() RETURNS trigger
LANGUAGE plpgsql AS $fn$
BEGIN
  -- (a) identity columns are immutable
  IF NEW.cash_close_id <> OLD.cash_close_id OR NEW.business_id <> OLD.business_id THEN
    RAISE EXCEPTION 'cash close identity columns are immutable';
  END IF;
  IF NEW.created_by IS DISTINCT FROM OLD.created_by THEN
    RAISE EXCEPTION 'cash close creator is immutable';
  END IF;
  IF (NEW.branch_id       <> OLD.branch_id OR
                                NEW.shift_type_id   <> OLD.shift_type_id OR
                                NEW.business_date   <> OLD.business_date OR
                                NEW.cash_close_code <> OLD.cash_close_code) THEN
    RAISE EXCEPTION 'branch, shift, date and code are immutable after submission';
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
      (OLD.status = 'SUBMITTED'      AND NEW.status IN ('PENDING_REVIEW','APPROVED','REJECTED','VOIDED')) OR
      (OLD.status = 'PENDING_REVIEW' AND NEW.status IN ('APPROVED','REJECTED','VOIDED')) OR
      (OLD.status = 'REJECTED'       AND NEW.status IN ('PENDING_REVIEW','VOIDED')) OR
      (OLD.status = 'APPROVED'       AND NEW.status IN ('PENDING_REVIEW','VOIDED'))
    ) THEN
      RAISE EXCEPTION 'illegal status transition % -> %', OLD.status, NEW.status;
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

CREATE FUNCTION cashclose.fn_require_submitted_count() RETURNS trigger
LANGUAGE plpgsql AS $fn$
DECLARE v_id UUID; v_status shared.close_status;
BEGIN
  v_id := CASE WHEN TG_OP='DELETE' THEN OLD.cash_close_id ELSE NEW.cash_close_id END;
  SELECT status INTO v_status FROM cashclose.cash_close WHERE cash_close_id=v_id;
  IF v_status IS NOT NULL AND v_status <> 'VOIDED'
     AND NOT EXISTS (SELECT 1 FROM cashclose.cash_denomination_line WHERE cash_close_id=v_id) THEN
    RAISE EXCEPTION 'submitted cash close % requires a denomination count', v_id;
  END IF;
  RETURN NULL;
END $fn$;
CREATE CONSTRAINT TRIGGER trg_close_submitted_count
  AFTER INSERT ON cashclose.cash_close DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_require_submitted_count();
CREATE CONSTRAINT TRIGGER trg_denom_submitted_count
  AFTER DELETE ON cashclose.cash_denomination_line DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_require_submitted_count();
