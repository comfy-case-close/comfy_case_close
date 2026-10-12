-- Period reconciliation records cannot identify who physically withdrew cash.
-- Preserve any existing data for reconciliation instead of inventing that identity.
ALTER TABLE cashclose.fund_withdrawal RENAME TO fund_withdrawal_legacy;
CREATE TRIGGER trg_fund_legacy_read_only BEFORE INSERT OR UPDATE OR DELETE
ON cashclose.fund_withdrawal_legacy FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();
REVOKE INSERT, UPDATE, DELETE ON cashclose.fund_withdrawal_legacy FROM svc_cashclose;

ALTER TABLE cashclose.cash_close ADD CONSTRAINT uq_close_transfer_scope
  UNIQUE (cash_close_id, business_id, branch_id);

CREATE TABLE cashclose.fund_withdrawal (
  fund_withdrawal_id UUID CONSTRAINT fund_transfer_revision_pk PRIMARY KEY DEFAULT gen_random_uuid(),
  fund_withdrawal_code TEXT NOT NULL,
  transfer_id UUID NOT NULL,
  revision INTEGER NOT NULL CHECK (revision > 0),
  supersedes_id UUID,
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id UUID NOT NULL,
  cash_close_id UUID,
  from_pot TEXT NOT NULL CHECK (from_pot IN ('DRAWER','BRANCH_SAFE','CENTRAL_SAFE')),
  to_pot TEXT NOT NULL CHECK (to_pot IN ('DRAWER','BRANCH_SAFE','CENTRAL_SAFE')),
  amount shared.d_money_nonneg NOT NULL,
  withdrawn_by UUID NOT NULL,
  withdrawn_at TIMESTAMPTZ NOT NULL,
  recorded_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','REJECTED','SUPERSEDED')),
  confirmed_by UUID,
  confirmed_at TIMESTAMPTZ,
  rejected_by UUID,
  rejected_at TIMESTAMPTZ,
  rejection_reason TEXT,
  superseded_at TIMESTAMPTZ,
  edit_reason TEXT,
  note TEXT,
  CONSTRAINT uq_fund_revision_tenant UNIQUE (fund_withdrawal_id,business_id),
  CONSTRAINT uq_fund_revision UNIQUE (transfer_id,revision),
  CONSTRAINT uq_fund_revision_code UNIQUE (business_id,fund_withdrawal_code),
  CONSTRAINT fk_fund_transfer_root FOREIGN KEY (transfer_id,business_id)
    REFERENCES cashclose.fund_withdrawal(fund_withdrawal_id,business_id),
  CONSTRAINT fk_fund_supersedes FOREIGN KEY (supersedes_id,business_id)
    REFERENCES cashclose.fund_withdrawal(fund_withdrawal_id,business_id),
  FOREIGN KEY (branch_id,business_id) REFERENCES identity.branch(branch_id,business_id),
  FOREIGN KEY (cash_close_id,business_id,branch_id)
    REFERENCES cashclose.cash_close(cash_close_id,business_id,branch_id),
  FOREIGN KEY (withdrawn_by,business_id) REFERENCES identity.staff(staff_id,business_id),
  FOREIGN KEY (recorded_by,business_id) REFERENCES identity.staff(staff_id,business_id),
  FOREIGN KEY (confirmed_by,business_id) REFERENCES identity.staff(staff_id,business_id),
  FOREIGN KEY (rejected_by,business_id) REFERENCES identity.staff(staff_id,business_id),
  CHECK (from_pot <> to_pot),
  CHECK (cash_close_id IS NULL OR (from_pot='DRAWER' AND to_pot='BRANCH_SAFE')),
  CHECK ((revision=1 AND supersedes_id IS NULL AND transfer_id=fund_withdrawal_id AND amount>0)
      OR (revision>1 AND supersedes_id IS NOT NULL AND edit_reason IS NOT NULL AND length(trim(edit_reason))>0)),
  CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL)),
  CHECK ((rejected_by IS NULL) = (rejected_at IS NULL)),
  CHECK ((confirmed_by IS NULL AND confirmed_at IS NULL)
      OR (confirmed_by=withdrawn_by AND confirmed_at IS NOT NULL)),
  CHECK ((rejected_by IS NULL AND rejected_at IS NULL AND rejection_reason IS NULL)
      OR (rejected_by=withdrawn_by AND rejected_at IS NOT NULL AND rejection_reason IS NOT NULL AND length(trim(rejection_reason))>0)),
  CHECK (NOT (confirmed_at IS NOT NULL AND rejected_at IS NOT NULL)),
  CHECK ((status='PENDING' AND confirmed_at IS NULL AND rejected_at IS NULL AND superseded_at IS NULL)
      OR (status='CONFIRMED' AND confirmed_at IS NOT NULL AND superseded_at IS NULL)
      OR (status='REJECTED' AND rejected_at IS NOT NULL AND superseded_at IS NULL)
      OR (status='SUPERSEDED' AND superseded_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_fund_current_transfer ON cashclose.fund_withdrawal(transfer_id) WHERE status<>'SUPERSEDED';
CREATE UNIQUE INDEX uq_fund_current_close ON cashclose.fund_withdrawal(cash_close_id) WHERE status<>'SUPERSEDED';
CREATE INDEX idx_fund_transfer_branch_time ON cashclose.fund_withdrawal(business_id,branch_id,withdrawn_at DESC);

CREATE FUNCTION cashclose.fn_fund_revision_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
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
      IF ROW(NEW.confirmed_by,NEW.confirmed_at,NEW.rejected_by,NEW.rejected_at,NEW.rejection_reason)
          IS DISTINCT FROM ROW(OLD.confirmed_by,OLD.confirmed_at,OLD.rejected_by,OLD.rejected_at,OLD.rejection_reason) THEN
        RAISE EXCEPTION 'Historical withdrawal decisions must be preserved';
      END IF;
    ELSIF OLD.status='PENDING' AND NEW.status IN ('CONFIRMED','REJECTED') THEN
      IF shared.current_user_id() IS DISTINCT FROM OLD.withdrawn_by THEN
        RAISE EXCEPTION 'Only the named withdrawing person may decide';
      END IF;
    ELSE RAISE EXCEPTION 'Illegal withdrawal transition';
    END IF;
  END IF;
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_fund_revision_guard BEFORE INSERT OR UPDATE OR DELETE ON cashclose.fund_withdrawal
FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_revision_guard();

-- Deferred because correcting a close and inserting its new revision are one transaction.
CREATE FUNCTION cashclose.fn_fund_close_consistency() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE close_id UUID; c cashclose.cash_close%ROWTYPE; w cashclose.fund_withdrawal%ROWTYPE;
BEGIN
  close_id := NEW.cash_close_id;
  IF close_id IS NULL THEN RETURN NULL; END IF;
  SELECT * INTO c FROM cashclose.cash_close WHERE cash_close_id=close_id;
  SELECT * INTO w FROM cashclose.fund_withdrawal WHERE cash_close_id=close_id AND status<>'SUPERSEDED';
  IF c.withdrawal_amount>0 AND w.fund_withdrawal_id IS NULL
      OR w.fund_withdrawal_id IS NOT NULL AND w.amount<>c.withdrawal_amount THEN
    RAISE EXCEPTION 'Close withdrawal amount must match its current transfer revision';
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

-- One confirmed declaration per physical transfer, even during a pending correction.
-- A confirmed zero correction cancels an erroneous declaration. It is not another cash movement.
CREATE VIEW cashclose.v_confirmed_fund_transfer WITH (security_invoker=true) AS
SELECT DISTINCT ON (transfer_id) * FROM cashclose.fund_withdrawal
WHERE confirmed_at IS NOT NULL ORDER BY transfer_id,revision DESC;

SELECT shared.fn_apply_tenant_rls('cashclose','fund_withdrawal');
GRANT SELECT,INSERT,UPDATE ON cashclose.fund_withdrawal TO svc_cashclose;
REVOKE DELETE ON cashclose.fund_withdrawal FROM svc_cashclose;
GRANT SELECT ON cashclose.fund_withdrawal TO svc_reporting,svc_workforce;
GRANT SELECT ON cashclose.v_confirmed_fund_transfer TO svc_cashclose,svc_reporting,svc_workforce;

-- Superseding is only valid if a new current revision exists at commit.
CREATE FUNCTION cashclose.fn_fund_current_revision() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF (SELECT count(*) FROM cashclose.fund_withdrawal
      WHERE transfer_id=NEW.transfer_id AND status<>'SUPERSEDED') <> 1 THEN
    RAISE EXCEPTION 'A transfer must have exactly one current revision';
  END IF;
  RETURN NULL;
END $fn$;
CREATE CONSTRAINT TRIGGER trg_fund_current_revision AFTER INSERT OR UPDATE ON cashclose.fund_withdrawal
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cashclose.fn_fund_current_revision();
