CREATE TABLE identity.staff_branch_permission (
 grant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 staff_id UUID NOT NULL,
 branch_id UUID NOT NULL,
 permission_code TEXT NOT NULL,
 business_id UUID NOT NULL REFERENCES identity.business(business_id),
 scope TEXT NOT NULL DEFAULT 'BRANCH' CHECK(scope='BRANCH'),
 granted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 revoked_at TIMESTAMPTZ,
 CHECK(revoked_at IS NULL OR revoked_at>=granted_at),
 FOREIGN KEY(staff_id,business_id) REFERENCES identity.staff(staff_id,business_id),
 FOREIGN KEY(branch_id,business_id) REFERENCES identity.branch(branch_id,business_id),
 FOREIGN KEY(permission_code,scope) REFERENCES identity.permission(permission_code,scope),
 EXCLUDE USING gist(staff_id WITH =,branch_id WITH =,permission_code WITH =,tstzrange(granted_at,revoked_at,'[)') WITH &&)
);
CREATE UNIQUE INDEX uq_staff_branch_permission_live ON identity.staff_branch_permission(staff_id,branch_id,permission_code) WHERE revoked_at IS NULL;
SELECT shared.fn_apply_tenant_rls('identity','staff_branch_permission');
CREATE TRIGGER trg_staff_branch_permission_guard BEFORE UPDATE OR DELETE ON identity.staff_branch_permission FOR EACH ROW EXECUTE FUNCTION identity.fn_guard_permission_history();
CREATE TRIGGER trg_staff_branch_permission_audit AFTER INSERT OR UPDATE ON identity.staff_branch_permission FOR EACH ROW EXECUTE FUNCTION identity.fn_audit_permission_change();
GRANT SELECT ON identity.staff_branch_permission TO svc_identity,svc_cashclose,svc_reporting,svc_platform,svc_files,svc_notify,svc_integration,svc_workforce,svc_inventory;
GRANT INSERT,UPDATE ON identity.staff_branch_permission TO svc_identity;
REVOKE DELETE ON identity.staff_branch_permission FROM svc_identity;

CREATE TABLE identity.staff_business_permission (
 grant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 staff_id UUID NOT NULL,
 permission_code TEXT NOT NULL,
 business_id UUID NOT NULL REFERENCES identity.business(business_id),
 scope TEXT NOT NULL DEFAULT 'BUSINESS' CHECK(scope='BUSINESS'),
 granted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 revoked_at TIMESTAMPTZ,
 CHECK(revoked_at IS NULL OR revoked_at>=granted_at),
 FOREIGN KEY(staff_id,business_id) REFERENCES identity.staff(staff_id,business_id),
 FOREIGN KEY(permission_code,scope) REFERENCES identity.permission(permission_code,scope),
 EXCLUDE USING gist(staff_id WITH =,permission_code WITH =,tstzrange(granted_at,revoked_at,'[)') WITH &&)
);
CREATE UNIQUE INDEX uq_staff_business_permission_live ON identity.staff_business_permission(staff_id,permission_code) WHERE revoked_at IS NULL;
SELECT shared.fn_apply_tenant_rls('identity','staff_business_permission');
CREATE TRIGGER trg_staff_business_permission_guard BEFORE UPDATE OR DELETE ON identity.staff_business_permission FOR EACH ROW EXECUTE FUNCTION identity.fn_guard_permission_history();
CREATE TRIGGER trg_staff_business_permission_audit AFTER INSERT OR UPDATE ON identity.staff_business_permission FOR EACH ROW EXECUTE FUNCTION identity.fn_audit_permission_change();
GRANT SELECT ON identity.staff_business_permission TO svc_identity,svc_cashclose,svc_reporting,svc_platform,svc_files,svc_notify,svc_integration,svc_workforce,svc_inventory;
GRANT INSERT,UPDATE ON identity.staff_business_permission TO svc_identity;
REVOKE DELETE ON identity.staff_business_permission FROM svc_identity;
