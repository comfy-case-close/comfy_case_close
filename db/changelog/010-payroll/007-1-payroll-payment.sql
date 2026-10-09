-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.7-payment-and-confirmation.xml
-- Manual salary transfers: one payment per payslip with a frozen bank snapshot, plus an append-only ledger.

CREATE TYPE payroll.payment_status AS ENUM ('PENDING', 'PAID', 'FAILED');
CREATE TYPE payroll.payment_action AS ENUM ('MARK_PAID', 'FAIL');

CREATE TABLE payroll.payroll_payment (
  payroll_payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id        UUID NOT NULL REFERENCES identity.business(business_id),
  payslip_id         UUID NOT NULL,
  amount             shared.d_money NOT NULL,
  bank_code          TEXT REFERENCES payroll.bank(bank_code),
  bank_account_no    TEXT,
  bank_account_name  TEXT,
  transfer_note      TEXT NOT NULL,
  status             payroll.payment_status NOT NULL DEFAULT 'PENDING',
  paid_at            TIMESTAMPTZ,
  paid_by            UUID,
  bank_reference     TEXT,
  failure_reason     TEXT,
  UNIQUE (payslip_id),
  UNIQUE (payroll_payment_id, business_id),
  FOREIGN KEY (payslip_id, business_id)
    REFERENCES payroll.payslip(payslip_id, business_id),
  FOREIGN KEY (paid_by, business_id)
    REFERENCES identity.staff(staff_id, business_id),
  CHECK ((status = 'PAID') = (paid_at IS NOT NULL AND paid_by IS NOT NULL)),
  CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL)
);
CREATE INDEX idx_payroll_payment_status ON payroll.payroll_payment (business_id, status);

CREATE TABLE payroll.payroll_payment_event (
  payroll_payment_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id              UUID NOT NULL REFERENCES identity.business(business_id),
  payroll_payment_id       UUID NOT NULL,
  action                   payroll.payment_action NOT NULL,
  acted_by                 UUID NOT NULL,
  acted_at                 TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  paid_at                  TIMESTAMPTZ,
  bank_reference           TEXT,
  reason                   TEXT,
  FOREIGN KEY (payroll_payment_id, business_id)
    REFERENCES payroll.payroll_payment(payroll_payment_id, business_id),
  FOREIGN KEY (acted_by, business_id)
    REFERENCES identity.staff(staff_id, business_id),
  CHECK (action <> 'FAIL' OR reason IS NOT NULL)
);
CREATE INDEX idx_payroll_payment_event_payment ON payroll.payroll_payment_event (payroll_payment_id, acted_at);
CREATE TRIGGER trg_payroll_payment_event_append_only
  BEFORE UPDATE OR DELETE ON payroll.payroll_payment_event
  FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();

REVOKE UPDATE, DELETE ON payroll.payroll_payment_event FROM svc_hrm;

SELECT shared.fn_apply_tenant_rls('payroll', 'payroll_payment');
SELECT shared.fn_apply_tenant_rls('payroll', 'payroll_payment_event');
