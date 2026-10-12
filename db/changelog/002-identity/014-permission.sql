-- Global dictionary; migrations alone may change the vocabulary.
CREATE TABLE identity.permission (
 permission_code TEXT PRIMARY KEY,
 scope TEXT NOT NULL CHECK(scope IN ('BRANCH','BUSINESS')),
 description TEXT NOT NULL,
 UNIQUE(permission_code, scope)
);
INSERT INTO identity.permission VALUES
('CLOSE_READ','BRANCH','close read'),
('CLOSE_OPEN','BRANCH','close open'),
('CLOSE_EDIT','BRANCH','close edit'),
('CLOSE_SUBMIT','BRANCH','close submit'),
('CLOSE_REVIEW','BRANCH','close review'),
('CLOSE_VOID','BRANCH','close void'),
('DENOMINATION_WRITE','BRANCH','denomination write'),
('MOVEMENT_ADD','BRANCH','movement add'),
('MOVEMENT_REVIEW','BRANCH','movement review'),
('WITHDRAWAL_RECORD','BRANCH','withdrawal record'),
('FINANCE_READ','BRANCH','finance read'),
('REPORT_READ','BRANCH','report read'),
('CONFIG_WRITE','BRANCH','config write'),
('BRANCH_CREATE','BUSINESS','branch create'),
('BRANCH_DEACTIVATE','BUSINESS','branch deactivate'),
('STAFF_ASSIGN','BUSINESS','staff assign'),
('JOIN_REQUEST_DECIDE','BUSINESS','join request decide'),
('BUSINESS_UPDATE','BUSINESS','business update'),
('PERMISSION_GRANT','BUSINESS','permission grant');
REVOKE ALL ON identity.permission FROM PUBLIC;
REVOKE INSERT,UPDATE,DELETE ON identity.permission FROM svc_identity;
GRANT SELECT ON identity.permission TO svc_identity,svc_cashclose,svc_reporting,svc_platform,svc_files,svc_notify,svc_integration,svc_workforce,svc_inventory;

-- A transactional generation invalidates caches in ALL service instances, including
-- when grants are changed by SQL. Cache hits still check this small indexed row.
CREATE SEQUENCE identity.permission_revision_seq;
GRANT USAGE ON SEQUENCE identity.permission_revision_seq TO svc_identity;
CREATE TABLE identity.permission_revision (
 business_id UUID PRIMARY KEY REFERENCES identity.business(business_id),
 revision BIGINT NOT NULL DEFAULT 0
);

-- Seed before applying RLS, the order 012-staff-branch-position.sql uses. The
-- migration role holds BYPASSRLS (800-analytics requires it to create
-- analytics_refresher), so FORCE ROW LEVEL SECURITY does not bind it either way -
-- but keeping data steps ahead of the policy means this file still works if that
-- role is ever narrowed.
INSERT INTO identity.permission_revision(business_id) SELECT business_id FROM identity.business;

SELECT shared.fn_apply_tenant_rls('identity','permission_revision');
GRANT SELECT ON identity.permission_revision TO svc_identity,svc_cashclose,svc_reporting,svc_platform,svc_files,svc_notify,svc_integration,svc_workforce,svc_inventory;
GRANT INSERT,UPDATE ON identity.permission_revision TO svc_identity;

CREATE FUNCTION identity.fn_guard_permission_history() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Permission history cannot be deleted'; END IF;
 IF OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL
 OR (to_jsonb(NEW)-'revoked_at') IS DISTINCT FROM (to_jsonb(OLD)-'revoked_at') THEN
  RAISE EXCEPTION 'Versions are immutable except for closing a live version';
 END IF;
 RETURN NEW;
END $fn$;
CREATE FUNCTION identity.fn_audit_permission_change() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
 INSERT INTO identity.permission_revision(business_id,revision) VALUES(NEW.business_id,nextval('identity.permission_revision_seq'))
 ON CONFLICT(business_id) DO UPDATE SET revision=nextval('identity.permission_revision_seq');
 INSERT INTO platform.audit_log(business_id,actor_user_id,action,entity_type,entity_id,old_value,new_value)
 VALUES(NEW.business_id,shared.current_user_id(),
 CASE WHEN TG_OP='INSERT' THEN 'PERMISSION_GRANTED' ELSE 'PERMISSION_REVOKED' END,
 TG_TABLE_NAME,coalesce(to_jsonb(NEW)->>'grant_id',to_jsonb(NEW)->>'assignment_id'),
 CASE WHEN TG_OP='INSERT' THEN NULL ELSE to_jsonb(OLD) END,to_jsonb(NEW));
 RETURN NULL;
END $fn$;
-- Active flags affect effective permissions without modifying grant rows.
CREATE FUNCTION identity.fn_invalidate_permissions() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
 INSERT INTO identity.permission_revision(business_id,revision) VALUES(NEW.business_id,nextval('identity.permission_revision_seq'))
 ON CONFLICT(business_id) DO UPDATE SET revision=nextval('identity.permission_revision_seq');
 RETURN NULL;
END $fn$;
CREATE TRIGGER trg_staff_permission_revision AFTER UPDATE OF is_active ON identity.staff FOR EACH ROW EXECUTE FUNCTION identity.fn_invalidate_permissions();
CREATE TRIGGER trg_branch_permission_revision AFTER UPDATE OF is_active ON identity.branch FOR EACH ROW EXECUTE FUNCTION identity.fn_invalidate_permissions();
CREATE TRIGGER trg_position_permission_revision AFTER UPDATE OF is_active ON identity.staff_position FOR EACH ROW EXECUTE FUNCTION identity.fn_invalidate_permissions();
CREATE TRIGGER trg_business_permission_revision AFTER UPDATE OF is_active ON identity.business FOR EACH ROW EXECUTE FUNCTION identity.fn_invalidate_permissions();
