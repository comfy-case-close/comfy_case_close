CREATE TABLE identity.position_permission (
 grant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 position_id UUID NOT NULL,
 permission_code TEXT NOT NULL,
 business_id UUID NOT NULL REFERENCES identity.business(business_id),
 scope TEXT NOT NULL DEFAULT 'BRANCH' CHECK(scope='BRANCH'),
 granted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 revoked_at TIMESTAMPTZ,
 CHECK(revoked_at IS NULL OR revoked_at>=granted_at),
 FOREIGN KEY(position_id,business_id) REFERENCES identity.staff_position(position_id,business_id),
 FOREIGN KEY(permission_code,scope) REFERENCES identity.permission(permission_code,scope),
 EXCLUDE USING gist(position_id WITH =,permission_code WITH =,tstzrange(granted_at,revoked_at,'[)') WITH &&)
);
CREATE UNIQUE INDEX uq_position_permission_live ON identity.position_permission(position_id,permission_code) WHERE revoked_at IS NULL;
-- Seeded before the policy and before the audit trigger, so the starter bundle is
-- data setup rather than N audited grant events. Same ordering as 012.
-- Conservative starter bundle. Elevated access is explicit, not guessed from titles.
INSERT INTO identity.position_permission(position_id,business_id,permission_code)
SELECT p.position_id,p.business_id,k.permission_code FROM identity.staff_position p
CROSS JOIN identity.permission k WHERE k.permission_code IN
('CLOSE_READ','CLOSE_OPEN','CLOSE_EDIT','CLOSE_SUBMIT','DENOMINATION_WRITE','MOVEMENT_ADD','WITHDRAWAL_RECORD');

SELECT shared.fn_apply_tenant_rls('identity','position_permission');
CREATE TRIGGER trg_position_permission_guard BEFORE UPDATE OR DELETE ON identity.position_permission FOR EACH ROW EXECUTE FUNCTION identity.fn_guard_permission_history();
CREATE TRIGGER trg_position_permission_audit AFTER INSERT OR UPDATE ON identity.position_permission FOR EACH ROW EXECUTE FUNCTION identity.fn_audit_permission_change();
GRANT SELECT ON identity.position_permission TO svc_identity,svc_cashclose,svc_reporting,svc_platform,svc_files,svc_notify,svc_integration,svc_workforce,svc_inventory;
GRANT INSERT,UPDATE ON identity.position_permission TO svc_identity;
REVOKE DELETE ON identity.position_permission FROM svc_identity;
