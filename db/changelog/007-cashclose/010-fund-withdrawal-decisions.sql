-- Move existing decisions before removing their inline fields. Each declaration
-- revision can receive one terminal decision; corrections create a new revision.
CREATE TABLE cashclose.fund_withdrawal_decision (
  decision_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  fund_withdrawal_id UUID NOT NULL UNIQUE,
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  action TEXT NOT NULL CHECK (action IN ('CONFIRM','REJECT')),
  acted_by UUID NOT NULL,
  acted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  old_status TEXT NOT NULL CHECK (old_status='PENDING'),
  new_status TEXT NOT NULL,
  note TEXT,
  FOREIGN KEY (fund_withdrawal_id,business_id)
    REFERENCES cashclose.fund_withdrawal(fund_withdrawal_id,business_id),
  FOREIGN KEY (acted_by,business_id) REFERENCES identity.staff(staff_id,business_id),
  CHECK ((action='CONFIRM' AND new_status='CONFIRMED')
      OR (action='REJECT' AND new_status='REJECTED' AND note IS NOT NULL AND length(trim(note))>0))
);
CREATE INDEX idx_fund_decision_tenant ON cashclose.fund_withdrawal_decision(business_id,fund_withdrawal_id,acted_at);

INSERT INTO cashclose.fund_withdrawal_decision
  (fund_withdrawal_id,business_id,action,acted_by,acted_at,old_status,new_status,note)
SELECT fund_withdrawal_id,business_id,'CONFIRM',confirmed_by,confirmed_at,'PENDING','CONFIRMED',NULL
FROM cashclose.fund_withdrawal WHERE confirmed_at IS NOT NULL
UNION ALL
SELECT fund_withdrawal_id,business_id,'REJECT',rejected_by,rejected_at,'PENDING','REJECTED',rejection_reason
FROM cashclose.fund_withdrawal WHERE rejected_at IS NOT NULL;

DROP VIEW cashclose.v_confirmed_fund_transfer;
ALTER TABLE cashclose.fund_withdrawal
  DROP COLUMN confirmed_by,
  DROP COLUMN confirmed_at,
  DROP COLUMN rejected_by,
  DROP COLUMN rejected_at,
  DROP COLUMN rejection_reason;
-- The old compound status CHECK was removed with the decision columns.
ALTER TABLE cashclose.fund_withdrawal ADD CONSTRAINT ck_fund_superseded_time
  CHECK ((status='SUPERSEDED') = (superseded_at IS NOT NULL));

CREATE OR REPLACE FUNCTION cashclose.fn_fund_revision_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE prior cashclose.fund_withdrawal%ROWTYPE;
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Withdrawal history cannot be deleted'; END IF;
  -- Serialize with close approval/correction and other decisions for this transfer.
  IF NEW.cash_close_id IS NOT NULL THEN
    PERFORM 1 FROM cashclose.cash_close WHERE cash_close_id=NEW.cash_close_id FOR UPDATE;
  END IF;
  IF TG_OP='INSERT' THEN
    IF NEW.status<>'PENDING' OR NEW.recorded_by IS DISTINCT FROM shared.current_user_id() THEN
      RAISE EXCEPTION 'A withdrawal starts pending and is attributed to its recorder';
    END IF;
    IF NEW.withdrawn_at > clock_timestamp() THEN RAISE EXCEPTION 'Withdrawal time cannot be in the future'; END IF;
    IF NEW.supersedes_id IS NOT NULL THEN
      SELECT * INTO prior FROM cashclose.fund_withdrawal WHERE fund_withdrawal_id=NEW.supersedes_id FOR UPDATE;
      IF NOT FOUND OR prior.status<>'SUPERSEDED' OR prior.transfer_id<>NEW.transfer_id
          OR NEW.revision<>prior.revision+1 OR prior.branch_id<>NEW.branch_id
          OR prior.cash_close_id IS DISTINCT FROM NEW.cash_close_id
          OR prior.from_pot<>NEW.from_pot OR prior.to_pot<>NEW.to_pot THEN
        RAISE EXCEPTION 'Invalid withdrawal revision chain';
      END IF;
    END IF;
  ELSE
    IF ROW(NEW.fund_withdrawal_id,NEW.fund_withdrawal_code,NEW.transfer_id,NEW.revision,NEW.supersedes_id,
           NEW.business_id,NEW.branch_id,NEW.cash_close_id,NEW.from_pot,NEW.to_pot,NEW.amount,
           NEW.withdrawn_by,NEW.withdrawn_at,NEW.recorded_by,NEW.created_at,NEW.edit_reason,NEW.note)
       IS DISTINCT FROM
       ROW(OLD.fund_withdrawal_id,OLD.fund_withdrawal_code,OLD.transfer_id,OLD.revision,OLD.supersedes_id,
           OLD.business_id,OLD.branch_id,OLD.cash_close_id,OLD.from_pot,OLD.to_pot,OLD.amount,
           OLD.withdrawn_by,OLD.withdrawn_at,OLD.recorded_by,OLD.created_at,OLD.edit_reason,OLD.note) THEN
      RAISE EXCEPTION 'Withdrawal declarations are immutable; create a revision';
    END IF;
    IF NEW.status='SUPERSEDED' AND OLD.status<>'SUPERSEDED' THEN
      NULL; -- Decisions stay immutable in fund_withdrawal_decision.
    ELSIF OLD.status='PENDING' AND NEW.status IN ('CONFIRMED','REJECTED') THEN
      IF shared.current_user_id() IS DISTINCT FROM OLD.withdrawn_by THEN
        RAISE EXCEPTION 'Only the named withdrawing person may decide';
      END IF;
    ELSE RAISE EXCEPTION 'Illegal withdrawal transition';
    END IF;
  END IF;
  RETURN NEW;
END $fn$;

-- Enforce attribution in SQL as well as Java. Lock in the same order as close
-- correction/approval: close, transfer root, then the selected revision.
CREATE FUNCTION cashclose.fn_fund_decision_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE w cashclose.fund_withdrawal%ROWTYPE;
BEGIN
  SELECT * INTO w FROM cashclose.fund_withdrawal
    WHERE fund_withdrawal_id=NEW.fund_withdrawal_id AND business_id=NEW.business_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Withdrawal revision not found'; END IF;
  IF w.cash_close_id IS NOT NULL THEN
    PERFORM 1 FROM cashclose.cash_close WHERE cash_close_id=w.cash_close_id FOR UPDATE;
  END IF;
  PERFORM 1 FROM cashclose.fund_withdrawal WHERE fund_withdrawal_id=w.transfer_id FOR UPDATE;
  SELECT * INTO w FROM cashclose.fund_withdrawal WHERE fund_withdrawal_id=NEW.fund_withdrawal_id FOR UPDATE;
  IF NEW.acted_by IS DISTINCT FROM w.withdrawn_by
      OR NEW.acted_by IS DISTINCT FROM shared.current_user_id() THEN
    RAISE EXCEPTION 'Only the named withdrawing person may decide';
  END IF;
  IF w.status IS DISTINCT FROM NEW.new_status THEN
    RAISE EXCEPTION 'Withdrawal status must match its decision';
  END IF;
  NEW.acted_at := clock_timestamp();
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_fund_decision_guard BEFORE INSERT ON cashclose.fund_withdrawal_decision
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_decision_guard();
CREATE TRIGGER trg_fund_decision_append_only BEFORE UPDATE OR DELETE ON cashclose.fund_withdrawal_decision
FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();

-- Status and decision are written in one transaction. Prevent direct status
-- changes that omit the audit record, including deciding then superseding.
CREATE FUNCTION cashclose.fn_fund_decision_consistency() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE decision_status TEXT;
BEGIN
  SELECT new_status INTO decision_status FROM cashclose.fund_withdrawal_decision
    WHERE fund_withdrawal_id=NEW.fund_withdrawal_id AND business_id=NEW.business_id;
  IF NEW.status IN ('CONFIRMED','REJECTED') AND decision_status IS DISTINCT FROM NEW.status THEN
    RAISE EXCEPTION 'A decided withdrawal requires its matching decision record';
  END IF;
  IF NEW.status='PENDING' AND TG_OP='UPDATE' AND decision_status IS NOT NULL THEN
    RAISE EXCEPTION 'A pending withdrawal cannot already have a decision';
  END IF;
  RETURN NULL;
END $fn$;
CREATE CONSTRAINT TRIGGER trg_fund_decision_consistency AFTER INSERT OR UPDATE ON cashclose.fund_withdrawal
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_decision_consistency();

-- Previous confirmed revisions remain effective until a correction is confirmed.
CREATE VIEW cashclose.v_confirmed_fund_transfer WITH (security_invoker=true) AS
SELECT DISTINCT ON (w.transfer_id) w.* FROM cashclose.fund_withdrawal w
WHERE EXISTS (SELECT 1 FROM cashclose.fund_withdrawal_decision d
  WHERE d.fund_withdrawal_id=w.fund_withdrawal_id AND d.business_id=w.business_id AND d.action='CONFIRM')
ORDER BY w.transfer_id,w.revision DESC;
SELECT shared.fn_apply_tenant_rls('cashclose','fund_withdrawal_decision');
GRANT SELECT,INSERT ON cashclose.fund_withdrawal_decision TO svc_cashclose;
REVOKE UPDATE,DELETE ON cashclose.fund_withdrawal_decision FROM svc_cashclose;
GRANT SELECT ON cashclose.fund_withdrawal_decision TO svc_reporting,svc_workforce;
GRANT SELECT ON cashclose.v_confirmed_fund_transfer TO svc_cashclose,svc_reporting,svc_workforce;

-- New close decisions identify the actor and authorizing permission.
CREATE OR REPLACE FUNCTION cashclose.fn_require_decision_authority() RETURNS trigger
LANGUAGE plpgsql AS $fn$
BEGIN
  IF NEW.acted_permission IS NULL THEN
    RAISE EXCEPTION 'New close decisions require the authorizing permission';
  END IF;
  RETURN NEW;
END $fn$;
ALTER TABLE cashclose.cash_close_decision DROP COLUMN acted_role, DROP COLUMN acted_position;
