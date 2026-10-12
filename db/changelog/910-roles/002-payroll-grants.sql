-- Add the HRM role and payroll ownership grants from feat/payroll without
-- rewriting the existing grant matrix or creating a development LOGIN role.
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_hrm') THEN
    CREATE ROLE svc_hrm NOLOGIN NOBYPASSRLS;
  END IF;
END $do$;
ALTER ROLE svc_hrm SET statement_timeout = '30s';
ALTER ROLE svc_hrm SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE svc_hrm SET search_path = 'shared, public';

GRANT USAGE ON SCHEMA shared TO svc_hrm;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA shared TO svc_hrm;

GRANT USAGE ON SCHEMA payroll TO svc_hrm;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payroll TO svc_hrm;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payroll TO svc_hrm;
ALTER DEFAULT PRIVILEGES IN SCHEMA payroll
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO svc_hrm;

GRANT USAGE ON SCHEMA identity, platform TO svc_hrm;
GRANT SELECT ON ALL TABLES IN SCHEMA identity, platform TO svc_hrm;
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT SELECT ON TABLES TO svc_hrm;
ALTER DEFAULT PRIVILEGES IN SCHEMA platform GRANT SELECT ON TABLES TO svc_hrm;

-- Salary history is append-only. svc_hrm writes its audit trail in payroll;
-- it does not receive INSERT on platform.audit_log or access to auth tokens.
REVOKE UPDATE, DELETE ON payroll.payroll_period_decision, payroll.payroll_audit_log FROM svc_hrm;
REVOKE ALL ON identity.revoked_token FROM svc_hrm;

-- svc_reporting and ai_agent receive no payroll schema/table grants. The
-- existing reporting grant matrix deliberately stops at the other schemas.
