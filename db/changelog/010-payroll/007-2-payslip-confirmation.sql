-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.7-payment-and-confirmation.xml
-- Employee confirmation of a received salary through a single-use token; only the token hash is stored.

CREATE TYPE payroll.confirmation_status AS ENUM ('PENDING', 'CONFIRMED', 'DISPUTED');

CREATE TABLE payroll.payslip_confirmation (
  payslip_confirmation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id             UUID NOT NULL REFERENCES identity.business(business_id),
  payslip_id              UUID NOT NULL,
  token_hash              TEXT NOT NULL UNIQUE CHECK (char_length(token_hash) = 64),
  token_expires_at        TIMESTAMPTZ NOT NULL,
  status                  payroll.confirmation_status NOT NULL DEFAULT 'PENDING',
  confirmed_at            TIMESTAMPTZ,
  responded_ip            TEXT,
  responded_user_agent    TEXT,
  dispute_note            TEXT,
  reminder_count          SMALLINT NOT NULL DEFAULT 0 CHECK (reminder_count >= 0),
  last_reminded_at        TIMESTAMPTZ,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payslip_id),
  UNIQUE (payslip_confirmation_id, business_id),
  FOREIGN KEY (payslip_id, business_id)
    REFERENCES payroll.payslip(payslip_id, business_id),
  CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
  CHECK (status <> 'DISPUTED' OR dispute_note IS NOT NULL)
);

SELECT shared.fn_apply_tenant_rls('payroll', 'payslip_confirmation');

DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles
                 WHERE rolname = current_user AND (rolsuper OR rolbypassrls)) THEN
    RAISE EXCEPTION
      'fn_resolve_payslip_confirmation must be owned by a migration role with BYPASSRLS or superuser';
  END IF;
END $do$;

CREATE OR REPLACE FUNCTION payroll.fn_resolve_payslip_confirmation(p_token_hash TEXT)
RETURNS TABLE (business_id UUID, payslip_confirmation_id UUID)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $fn$
  SELECT c.business_id, c.payslip_confirmation_id
  FROM payroll.payslip_confirmation c
  WHERE c.token_hash = p_token_hash
$fn$;

REVOKE ALL ON FUNCTION payroll.fn_resolve_payslip_confirmation(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION payroll.fn_resolve_payslip_confirmation(TEXT) TO svc_hrm;
