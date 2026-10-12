-- A movement's receipt points straight at files.stored_file instead of at a
-- close_attachment row. Staff attach the image to the movement itself; the
-- close's images are its close_attachment files plus its movements' receipts.
--
-- close_attachment.file_kind is dropped: the kind already lives on
-- files.stored_file, and two copies of it could disagree.

-- 1. Rename the column and drop the "attachment of THIS close" FK.
-- 001 left that FK unnamed, so find it by what it references.
DO $do$
DECLARE c TEXT;
BEGIN
  SELECT conname INTO STRICT c FROM pg_constraint
  WHERE conrelid = 'cashclose.cash_movement'::regclass AND contype = 'f'
    AND confrelid = 'cashclose.close_attachment'::regclass;
  EXECUTE format('ALTER TABLE cashclose.cash_movement DROP CONSTRAINT %I', c);
END $do$;
ALTER TABLE cashclose.cash_movement
  RENAME COLUMN receipt_attachment_id TO receipt_file_id;
-- The view follows the column by reference; only its output name is stale.
ALTER VIEW cashclose.v_cash_movement_detail
  RENAME COLUMN receipt_attachment_id TO receipt_file_id;

-- 2. Translate existing attachment ids into the file ids they wrapped. The row
--    guards reject direct updates (frozen closes, decision-only changes), so they
--    are suspended for this one statement only.
ALTER TABLE cashclose.cash_movement DISABLE TRIGGER trg_cash_movement_guard;
ALTER TABLE cashclose.cash_movement DISABLE TRIGGER trg_movement_state_guard;
UPDATE cashclose.cash_movement m
SET receipt_file_id = a.file_id
FROM cashclose.close_attachment a
WHERE a.attachment_id = m.receipt_file_id;
ALTER TABLE cashclose.cash_movement ENABLE TRIGGER trg_cash_movement_guard;
ALTER TABLE cashclose.cash_movement ENABLE TRIGGER trg_movement_state_guard;

-- 3. A receipt must be a file of the same business. The branch check lives in
--    the service, like close_attachment's.
ALTER TABLE cashclose.cash_movement
  ADD CONSTRAINT fk_movement_receipt_file
  FOREIGN KEY (receipt_file_id, business_id) REFERENCES files.stored_file(file_id, business_id);
CREATE INDEX idx_movement_receipt_file ON cashclose.cash_movement (business_id, receipt_file_id)
  WHERE receipt_file_id IS NOT NULL;

-- 4. The kind belongs to the file.
ALTER TABLE cashclose.close_attachment DROP COLUMN file_kind;

-- 5. Functions that named the old column or JSON key. Earlier decision rows keep
--    'receiptAttachmentId' in their changes: they are an append-only record of
--    what was true then.
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
    IF v_kind.requires_receipt AND NEW.receipt_file_id IS NULL THEN
      RAISE EXCEPTION 'movement kind "%" requires an attached receipt', v_kind.kind_code;
    END IF;
  END IF;

  RETURN COALESCE(NEW, OLD);
END $fn$;

CREATE OR REPLACE FUNCTION cashclose.fn_movement_snapshot(m cashclose.cash_movement) RETURNS JSONB
LANGUAGE sql STABLE AS $fn$
  SELECT jsonb_build_object('kindSk',m.kind_sk,'effectType',m.effect_type,
         'signedAmount',m.signed_amount,'staffUserId',m.staff_user_id,
         'description',m.description,'receiptFileId',m.receipt_file_id)
$fn$;

CREATE OR REPLACE FUNCTION cashclose.fn_movement_decision_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
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
          'kindSk','effectType','signedAmount','staffUserId','description','receiptFileId']) THEN
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

CREATE OR REPLACE FUNCTION cashclose.fn_apply_movement_decision() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  UPDATE cashclose.cash_movement
  SET approval_status=NEW.new_status,
      kind_sk=(NEW.changes->'after'->>'kindSk')::BIGINT,
      effect_type=NEW.changes->'after'->>'effectType',
      signed_amount=(NEW.changes->'after'->>'signedAmount')::NUMERIC,
      staff_user_id=(NEW.changes->'after'->>'staffUserId')::UUID,
      description=NEW.changes->'after'->>'description',
      receipt_file_id=(NEW.changes->'after'->>'receiptFileId')::UUID
  WHERE movement_id=NEW.movement_id AND business_id=NEW.business_id;
  RETURN NULL;
END $fn$;
