-- One stable movement row. Every correction and status transition is an explicit
-- append-only decision; its insert trigger applies the new current state.
DROP TRIGGER trg_movement_status_guard ON cashclose.cash_movement;
DROP TRIGGER trg_movement_decision_log ON cashclose.cash_movement;
DROP FUNCTION cashclose.fn_movement_status_guard();
DROP FUNCTION cashclose.fn_movement_decision_log();

ALTER TABLE cashclose.cash_movement_decision
  DROP CONSTRAINT ck_movement_decision_changed,
  ADD COLUMN action TEXT,
  ADD COLUMN changes JSONB;
-- Legacy decisions recorded only the endorsed kind and amount. Their earlier
-- field values cannot be reconstructed, so leave before=null honestly.
UPDATE cashclose.cash_movement_decision
SET action=CASE new_status WHEN 'APPROVED' THEN 'APPROVE'
                           WHEN 'REJECTED' THEN 'REJECT' ELSE 'REOPEN' END,
    changes=jsonb_build_object('before',NULL,'after',
              jsonb_build_object('kindSk',kind_sk,'signedAmount',signed_amount));
ALTER TABLE cashclose.cash_movement_decision
  ALTER COLUMN action SET NOT NULL,
  ALTER COLUMN changes SET NOT NULL,
  ADD CONSTRAINT ck_movement_decision_action CHECK (action IN ('EDIT','APPROVE','REJECT','REOPEN')),
  ADD CONSTRAINT ck_movement_decision_status CHECK (
    (action='EDIT' AND old_status IN ('PENDING','APPROVED','REJECTED') AND new_status='PENDING') OR
    (action='APPROVE' AND old_status='PENDING' AND new_status='APPROVED') OR
    (action='REJECT' AND old_status='PENDING' AND new_status='REJECTED') OR
    (action='REOPEN' AND old_status IN ('APPROVED','REJECTED') AND new_status='PENDING')),
  ADD CONSTRAINT ck_movement_decision_note CHECK (
    action NOT IN ('EDIT','REJECT') OR nullif(btrim(note),'') IS NOT NULL),
  ADD CONSTRAINT ck_movement_decision_changes CHECK (jsonb_typeof(changes)='object');

-- Keep the existing view contract while deriving its last actor/time from the
-- ledger instead of storing a second copy on the movement.
CREATE OR REPLACE VIEW cashclose.v_cash_movement_detail AS
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
       m.approval_status, last_decision.decided_by, last_decision.decided_at,
       m.receipt_attachment_id, m.created_by, m.created_at
FROM cashclose.cash_movement m
JOIN platform.movement_kind k ON k.kind_sk = m.kind_sk
JOIN cashclose.cash_close cc ON cc.cash_close_id = m.cash_close_id
LEFT JOIN LATERAL (
  SELECT d.decided_by,d.decided_at FROM cashclose.cash_movement_decision d
  WHERE d.movement_id=m.movement_id AND d.business_id=m.business_id
  ORDER BY d.decided_at DESC,d.decision_id DESC LIMIT 1
) last_decision ON TRUE;

ALTER TABLE cashclose.cash_movement
  DROP CONSTRAINT ck_movement_decided_fields,
  DROP COLUMN decided_by,
  DROP COLUMN decided_at,
  DROP COLUMN decision_note;

CREATE FUNCTION cashclose.fn_movement_snapshot(m cashclose.cash_movement) RETURNS JSONB
LANGUAGE sql STABLE AS $fn$
  SELECT jsonb_build_object('kindSk',m.kind_sk,'effectType',m.effect_type,
         'signedAmount',m.signed_amount,'staffUserId',m.staff_user_id,
         'description',m.description,'receiptAttachmentId',m.receipt_attachment_id)
$fn$;

CREATE FUNCTION cashclose.fn_movement_state_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Movement history cannot be deleted'; END IF;
  IF TG_OP='INSERT' THEN
    IF NEW.approval_status<>'PENDING' THEN RAISE EXCEPTION 'A movement starts pending'; END IF;
  ELSE
    IF pg_trigger_depth()<>2 THEN RAISE EXCEPTION 'Movement changes require a decision record'; END IF;
    IF ROW(NEW.movement_id,NEW.cash_close_id,NEW.business_id,NEW.created_by,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.movement_id,OLD.cash_close_id,OLD.business_id,OLD.created_by,OLD.created_at) THEN
      RAISE EXCEPTION 'Movement identity, close and recorder cannot change';
    END IF;
  END IF;
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_movement_state_guard BEFORE INSERT OR UPDATE OR DELETE ON cashclose.cash_movement
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_movement_state_guard();

CREATE FUNCTION cashclose.fn_movement_decision_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE m cashclose.cash_movement%ROWTYPE; c cashclose.cash_close%ROWTYPE;
        before_data JSONB; after_data JSONB; prior_time TIMESTAMPTZ;
BEGIN
  SELECT * INTO m FROM cashclose.cash_movement
  WHERE movement_id=NEW.movement_id AND business_id=NEW.business_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Movement not found'; END IF;
  -- Match service lock order: close first, then line.
  SELECT * INTO c FROM cashclose.cash_close WHERE cash_close_id=m.cash_close_id FOR UPDATE;
  IF NOT FOUND OR c.status NOT IN ('SUBMITTED','PENDING_REVIEW') THEN
    RAISE EXCEPTION 'Cash close is frozen';
  END IF;
  SELECT * INTO m FROM cashclose.cash_movement WHERE movement_id=NEW.movement_id FOR UPDATE;
  IF NEW.decided_by IS DISTINCT FROM shared.current_user_id() THEN
    RAISE EXCEPTION 'Movement decision must identify the authenticated actor';
  END IF;
  IF NEW.old_status IS DISTINCT FROM m.approval_status THEN
    RAISE EXCEPTION 'Movement changed since this decision was prepared';
  END IF;
  -- Historical REOPEN rows remain readable, but new ones cannot be inserted.
  IF NEW.action='REOPEN' THEN
    RAISE EXCEPTION 'Reopening movements is no longer supported; correct the movement instead';
  END IF;
  before_data:=cashclose.fn_movement_snapshot(m);
  IF NEW.action='EDIT' THEN
    IF NEW.changes IS NULL OR NOT (NEW.changes ?& ARRAY['before','after'])
        OR NEW.changes->'before' IS DISTINCT FROM before_data
        OR NOT ((NEW.changes->'after') ?& ARRAY[
          'kindSk','effectType','signedAmount','staffUserId','description','receiptAttachmentId']) THEN
      RAISE EXCEPTION 'Edit requires complete, current before/after movement values';
    END IF;
    after_data:=NEW.changes->'after';
    IF after_data=before_data THEN RAISE EXCEPTION 'Movement correction must change a value'; END IF;
  ELSE
    after_data:=before_data;
  END IF;
  -- A decision always snapshots what it actually applies, independent of any
  -- caller-supplied snapshot on approvals and rejections.
  NEW.kind_sk:=(after_data->>'kindSk')::BIGINT;
  NEW.signed_amount:=(after_data->>'signedAmount')::NUMERIC;
  NEW.changes:=jsonb_build_object('before',before_data,'after',after_data);
  SELECT max(decided_at) INTO prior_time FROM cashclose.cash_movement_decision
  WHERE movement_id=m.movement_id;
  NEW.decided_at:=greatest(clock_timestamp(),prior_time+interval '1 microsecond');
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_movement_decision_guard BEFORE INSERT ON cashclose.cash_movement_decision
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_movement_decision_guard();

CREATE FUNCTION cashclose.fn_apply_movement_decision() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  UPDATE cashclose.cash_movement
  SET approval_status=NEW.new_status,
      kind_sk=(NEW.changes->'after'->>'kindSk')::BIGINT,
      effect_type=NEW.changes->'after'->>'effectType',
      signed_amount=(NEW.changes->'after'->>'signedAmount')::NUMERIC,
      staff_user_id=(NEW.changes->'after'->>'staffUserId')::UUID,
      description=NEW.changes->'after'->>'description',
      receipt_attachment_id=(NEW.changes->'after'->>'receiptAttachmentId')::UUID
  WHERE movement_id=NEW.movement_id AND business_id=NEW.business_id;
  RETURN NULL;
END $fn$;
CREATE TRIGGER trg_apply_movement_decision AFTER INSERT ON cashclose.cash_movement_decision
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_apply_movement_decision();
