-- One mutable withdrawal, with all corrections/decisions in its immutable ledger.
-- Keep the original transfer root ID when consolidating existing revision chains.
DROP VIEW cashclose.v_confirmed_fund_transfer;
DROP TRIGGER trg_fund_revision_guard ON cashclose.fund_withdrawal;
DROP TRIGGER trg_fund_current_revision ON cashclose.fund_withdrawal;
DROP TRIGGER trg_fund_decision_consistency ON cashclose.fund_withdrawal;
DROP TRIGGER trg_fund_close_consistency ON cashclose.fund_withdrawal;
DROP TRIGGER trg_close_fund_consistency ON cashclose.cash_close;
DROP TRIGGER trg_fund_decision_guard ON cashclose.fund_withdrawal_decision;
DROP TRIGGER trg_fund_decision_append_only ON cashclose.fund_withdrawal_decision;
DROP FUNCTION cashclose.fn_fund_revision_guard();
DROP FUNCTION cashclose.fn_fund_current_revision();
DROP FUNCTION cashclose.fn_fund_decision_consistency();

ALTER TABLE cashclose.fund_withdrawal_decision
  DROP CONSTRAINT fund_withdrawal_decision_fund_withdrawal_id_key,
  DROP CONSTRAINT fund_withdrawal_decision_action_check,
  DROP CONSTRAINT fund_withdrawal_decision_old_status_check,
  DROP CONSTRAINT fund_withdrawal_decision_check,
  ADD COLUMN changes JSONB;

CREATE FUNCTION cashclose.fn_fund_snapshot(w cashclose.fund_withdrawal) RETURNS JSONB
LANGUAGE sql STABLE AS $fn$
  SELECT jsonb_build_object('amount',w.amount,'withdrawnBy',w.withdrawn_by,
                           'withdrawnAt',w.withdrawn_at,'note',w.note)
$fn$;

-- Capture old revision state before consolidating the live rows.
CREATE TEMP TABLE fund_revision_migration ON COMMIT DROP AS
SELECT w.*, d.new_status AS decided_status, cashclose.fn_fund_snapshot(w) AS snapshot
FROM cashclose.fund_withdrawal w LEFT JOIN cashclose.fund_withdrawal_decision d
  ON d.fund_withdrawal_id=w.fund_withdrawal_id AND d.business_id=w.business_id;

UPDATE cashclose.fund_withdrawal_decision d
SET fund_withdrawal_id=w.transfer_id,
    changes=jsonb_build_object('before',w.snapshot,'after',w.snapshot)
FROM fund_revision_migration w WHERE w.fund_withdrawal_id=d.fund_withdrawal_id;

INSERT INTO cashclose.fund_withdrawal_decision
  (fund_withdrawal_id,business_id,action,acted_by,acted_at,old_status,new_status,note,changes)
SELECT w.transfer_id,w.business_id,'EDIT',w.recorded_by,w.created_at,
       coalesce(p.decided_status,'PENDING'),'PENDING',w.edit_reason,
       jsonb_build_object('before',p.snapshot,'after',w.snapshot)
FROM fund_revision_migration w JOIN fund_revision_migration p ON p.fund_withdrawal_id=w.supersedes_id
WHERE w.revision>1;

DROP INDEX cashclose.uq_fund_current_close;
ALTER TABLE cashclose.fund_withdrawal
  DROP COLUMN fund_withdrawal_code,
  DROP COLUMN transfer_id,
  DROP COLUMN revision,
  DROP COLUMN supersedes_id,
  DROP COLUMN superseded_at,
  DROP COLUMN edit_reason,
  DROP COLUMN created_at,
  DROP CONSTRAINT fund_withdrawal_status_check;
UPDATE cashclose.fund_withdrawal root
SET amount=latest.amount,withdrawn_by=latest.withdrawn_by,withdrawn_at=latest.withdrawn_at,
    status=latest.status,note=latest.note
FROM fund_revision_migration latest
WHERE latest.status<>'SUPERSEDED' AND root.fund_withdrawal_id=latest.transfer_id;
DELETE FROM cashclose.fund_withdrawal WHERE fund_withdrawal_id IN (
  SELECT fund_withdrawal_id FROM fund_revision_migration WHERE fund_withdrawal_id<>transfer_id);

ALTER TABLE cashclose.fund_withdrawal ADD CONSTRAINT ck_fund_status
  CHECK (status IN ('PENDING','CONFIRMED','REJECTED'));
CREATE UNIQUE INDEX uq_fund_close ON cashclose.fund_withdrawal(cash_close_id) WHERE cash_close_id IS NOT NULL;

ALTER TABLE cashclose.fund_withdrawal_decision
  ALTER COLUMN changes SET NOT NULL,
  ADD CONSTRAINT ck_fund_decision_action CHECK (action IN ('CONFIRM','REJECT','EDIT')),
  ADD CONSTRAINT ck_fund_decision_status CHECK (
    (action='CONFIRM' AND old_status='PENDING' AND new_status='CONFIRMED') OR
    (action='REJECT' AND old_status='PENDING' AND new_status='REJECTED') OR
    (action='EDIT' AND old_status IN ('PENDING','CONFIRMED','REJECTED') AND new_status='PENDING')),
  ADD CONSTRAINT ck_fund_decision_note CHECK (action='CONFIRM' OR (note IS NOT NULL AND length(trim(note))>0)),
  ADD CONSTRAINT ck_fund_decision_changes CHECK (
      jsonb_typeof(changes)='object'
          AND coalesce(jsonb_typeof(changes->'before')='object' AND jsonb_typeof(changes->'after')='object', false));

-- Service writes a decision, whose AFTER INSERT trigger updates the withdrawal.
-- Direct edits cannot bypass the ledger, even if they leave status unchanged.
CREATE FUNCTION cashclose.fn_fund_state_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Withdrawal history cannot be deleted'; END IF;
  IF NEW.withdrawn_at > clock_timestamp() THEN RAISE EXCEPTION 'Withdrawal time cannot be in the future'; END IF;
  IF TG_OP='INSERT' THEN
    IF NEW.status<>'PENDING' OR NEW.amount<=0 OR NEW.recorded_by IS DISTINCT FROM shared.current_user_id() THEN
      RAISE EXCEPTION 'A withdrawal starts pending with a positive amount and its recorder';
    END IF;
  ELSE
    IF pg_trigger_depth()<>2 THEN
      RAISE EXCEPTION 'Withdrawal changes require a decision record';
    END IF;
    IF ROW(NEW.fund_withdrawal_id,NEW.business_id,NEW.branch_id,NEW.cash_close_id,
           NEW.from_pot,NEW.to_pot,NEW.recorded_by)
       IS DISTINCT FROM ROW(OLD.fund_withdrawal_id,OLD.business_id,OLD.branch_id,OLD.cash_close_id,
           OLD.from_pot,OLD.to_pot,OLD.recorded_by) THEN
      RAISE EXCEPTION 'Withdrawal identity, scope, pots and recorder cannot change';
    END IF;
  END IF;
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_fund_state_guard BEFORE INSERT OR UPDATE OR DELETE ON cashclose.fund_withdrawal
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_state_guard();

CREATE OR REPLACE FUNCTION cashclose.fn_fund_decision_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE w cashclose.fund_withdrawal%ROWTYPE; prior_time TIMESTAMPTZ;
        before_data JSONB; after_data JSONB; amount_value NUMERIC; person_value UUID; time_value TIMESTAMPTZ;
BEGIN
  SELECT * INTO w FROM cashclose.fund_withdrawal
    WHERE fund_withdrawal_id=NEW.fund_withdrawal_id AND business_id=NEW.business_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Withdrawal not found'; END IF;
  -- Same lock order as Java and cash-close correction/approval.
  IF w.cash_close_id IS NOT NULL THEN
    PERFORM 1 FROM cashclose.cash_close WHERE cash_close_id=w.cash_close_id FOR UPDATE;
  END IF;
  SELECT * INTO w FROM cashclose.fund_withdrawal WHERE fund_withdrawal_id=NEW.fund_withdrawal_id FOR UPDATE;
  IF NEW.acted_by IS DISTINCT FROM shared.current_user_id() THEN
    RAISE EXCEPTION 'Withdrawal decision must identify the authenticated actor';
  END IF;
  IF NEW.old_status IS DISTINCT FROM w.status THEN
    RAISE EXCEPTION 'Withdrawal has changed since this decision was prepared';
  END IF;
  before_data := cashclose.fn_fund_snapshot(w);
  IF NEW.action IN ('CONFIRM','REJECT') THEN
    IF NEW.acted_by IS DISTINCT FROM w.withdrawn_by THEN
      RAISE EXCEPTION 'Only the named withdrawing person may decide';
    END IF;
    after_data := before_data; -- exact values this person acknowledged
  ELSIF NEW.action='EDIT' THEN
    IF NEW.changes IS NULL OR NOT (NEW.changes ?& ARRAY['before','after'])
        OR NOT ((NEW.changes->'before') ?& ARRAY['amount','withdrawnBy','withdrawnAt','note'])
        OR NOT ((NEW.changes->'after') ?& ARRAY['amount','withdrawnBy','withdrawnAt','note']) THEN
      RAISE EXCEPTION 'An edit requires complete before/after withdrawal values';
    END IF;
    IF ROW((NEW.changes->'before'->>'amount')::NUMERIC,(NEW.changes->'before'->>'withdrawnBy')::UUID,
           (NEW.changes->'before'->>'withdrawnAt')::TIMESTAMPTZ,NEW.changes->'before'->>'note')
       IS DISTINCT FROM ROW(w.amount,w.withdrawn_by,w.withdrawn_at,w.note) THEN
      RAISE EXCEPTION 'Withdrawal edit before values do not match the current record';
    END IF;
    amount_value := (NEW.changes->'after'->>'amount')::NUMERIC;
    person_value := (NEW.changes->'after'->>'withdrawnBy')::UUID;
    time_value := (NEW.changes->'after'->>'withdrawnAt')::TIMESTAMPTZ;
    IF amount_value IS NULL OR amount_value<0 OR amount_value>999999999999.99 OR amount_value<>round(amount_value,2)
        OR person_value IS NULL OR time_value IS NULL OR time_value>clock_timestamp() THEN
      RAISE EXCEPTION 'Invalid corrected withdrawal values';
    END IF;
    after_data := jsonb_build_object('amount',amount_value,'withdrawnBy',person_value,
                    'withdrawnAt',time_value,'note',NEW.changes->'after'->>'note');
  ELSE RAISE EXCEPTION 'Unknown withdrawal decision action';
  END IF;
  NEW.changes := jsonb_build_object('before',before_data,'after',after_data);
  -- Row locking plus monotonic decision times gives a deterministic history order.
  SELECT max(acted_at) INTO prior_time FROM cashclose.fund_withdrawal_decision
    WHERE fund_withdrawal_id=w.fund_withdrawal_id;
  NEW.acted_at := greatest(clock_timestamp(),prior_time+interval '1 microsecond');
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_fund_decision_guard BEFORE INSERT ON cashclose.fund_withdrawal_decision
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_decision_guard();

CREATE FUNCTION cashclose.fn_apply_fund_decision() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  UPDATE cashclose.fund_withdrawal
  SET status=NEW.new_status,amount=(NEW.changes->'after'->>'amount')::NUMERIC,
      withdrawn_by=(NEW.changes->'after'->>'withdrawnBy')::UUID,
      withdrawn_at=(NEW.changes->'after'->>'withdrawnAt')::TIMESTAMPTZ,
      note=NEW.changes->'after'->>'note'
  WHERE fund_withdrawal_id=NEW.fund_withdrawal_id AND business_id=NEW.business_id;
  RETURN NULL;
END $fn$;
CREATE TRIGGER trg_apply_fund_decision AFTER INSERT ON cashclose.fund_withdrawal_decision
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_apply_fund_decision();
CREATE TRIGGER trg_fund_decision_append_only BEFORE UPDATE OR DELETE ON cashclose.fund_withdrawal_decision
FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();

CREATE OR REPLACE FUNCTION cashclose.fn_fund_close_consistency() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE c cashclose.cash_close%ROWTYPE; w cashclose.fund_withdrawal%ROWTYPE;
BEGIN
  IF NEW.cash_close_id IS NULL THEN RETURN NULL; END IF;
  SELECT * INTO c FROM cashclose.cash_close WHERE cash_close_id=NEW.cash_close_id;
  SELECT * INTO w FROM cashclose.fund_withdrawal WHERE cash_close_id=NEW.cash_close_id;
  IF (c.withdrawal_amount>0 AND w.fund_withdrawal_id IS NULL)
      OR (w.fund_withdrawal_id IS NOT NULL AND w.amount<>c.withdrawal_amount) THEN
    RAISE EXCEPTION 'Close withdrawal amount must match its withdrawal';
  END IF;
  IF c.status='APPROVED' AND w.fund_withdrawal_id IS NOT NULL AND w.status<>'CONFIRMED' THEN
    RAISE EXCEPTION 'Close withdrawal requires confirmation before approval';
  END IF;
  RETURN NULL;
END $fn$;
CREATE CONSTRAINT TRIGGER trg_fund_close_consistency AFTER INSERT OR UPDATE ON cashclose.fund_withdrawal
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_close_consistency();
CREATE CONSTRAINT TRIGGER trg_close_fund_consistency AFTER INSERT OR UPDATE ON cashclose.cash_close
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_close_consistency();

-- Report the last CONFIRMED values even while a later edit awaits confirmation.
CREATE VIEW cashclose.v_confirmed_fund_transfer WITH (security_invoker=true) AS
SELECT w.fund_withdrawal_id,w.business_id,w.branch_id,w.cash_close_id,w.from_pot,w.to_pot,
       (d.changes->'after'->>'amount')::shared.d_money_nonneg AS amount,
       (d.changes->'after'->>'withdrawnBy')::UUID AS withdrawn_by,
       (d.changes->'after'->>'withdrawnAt')::TIMESTAMPTZ AS withdrawn_at,
       w.recorded_by,'CONFIRMED'::TEXT AS status,d.changes->'after'->>'note' AS note
FROM cashclose.fund_withdrawal w
JOIN LATERAL (
  SELECT changes FROM cashclose.fund_withdrawal_decision
  WHERE fund_withdrawal_id=w.fund_withdrawal_id AND business_id=w.business_id AND action='CONFIRM'
  ORDER BY acted_at DESC LIMIT 1
) d ON true;
GRANT SELECT ON cashclose.v_confirmed_fund_transfer TO svc_cashclose,svc_reporting,svc_workforce;
