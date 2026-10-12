-- Port dev's tip-jar payout ledger into the UUID, tenant-scoped cashclose schema.
-- A payout is a branch financial event, not a child of one shift close.
-- TIP_DIRECT already means money handed straight to staff; only TIP_JAR is
-- pooled and can fund a later payout. TIP_IN_DRAWER is reported separately.
INSERT INTO platform.movement_kind
  (kind_code, effect_type, affects_difference, affects_remaining,
   expense_category, diff_reason_group, requires_receipt, requires_note, display_name)
VALUES ('TIP_JAR', 'NO_CASH_FLOW', false, false, NULL, 'TIP', false, false,
        'Tip collected in the pooled jar')
ON CONFLICT DO NOTHING;

CREATE TABLE cashclose.tip_payout (
  tip_payout_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id   UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id     UUID NOT NULL,
  amount        shared.d_money_nonneg NOT NULL CHECK (amount > 0),
  payout_date   DATE NOT NULL,
  recipient_name VARCHAR(100),
  note          TEXT,
  created_by    UUID NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (branch_id, business_id)
    REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (created_by, business_id)
    REFERENCES identity.staff(staff_id, business_id)
);
CREATE INDEX idx_tip_payout_branch_date
  ON cashclose.tip_payout (business_id, branch_id, payout_date DESC);
CREATE TRIGGER trg_tip_payout_append_only
  BEFORE UPDATE OR DELETE ON cashclose.tip_payout
  FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();

-- This migration runs after the original 900/910 changesets. Apply protection
-- directly so both fresh and already-migrated databases get it immediately.
SELECT shared.fn_apply_tenant_rls('cashclose', 'tip_payout');
GRANT SELECT, INSERT ON cashclose.tip_payout TO svc_cashclose;
GRANT SELECT ON cashclose.tip_payout TO svc_reporting, svc_workforce;
REVOKE UPDATE, DELETE ON cashclose.tip_payout FROM svc_cashclose;

-- dev used a fixed app_config column; the split architecture resolves config
-- by BRANCH -> BUSINESS -> GLOBAL with this key instead.
INSERT INTO platform.app_config (scope, config_key, config_value, description)
VALUES ('GLOBAL', 'FUND_WITHDRAWAL_WARNING_ABS', '50000000',
        'Warn when a single fund withdrawal exceeds this amount (VND)')
ON CONFLICT DO NOTHING;
