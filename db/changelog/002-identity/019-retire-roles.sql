-- LAST, after backfill verification and application cutover. Keep all old versions
-- as immutable audit evidence before dropping the obsolete authorization tables.
INSERT INTO platform.audit_log(business_id,actor_user_id,action,entity_type,entity_id,old_value,new_value)
SELECT business_id,NULL,'ROLE_HISTORY_ARCHIVED','staff_branch_role',assignment_id::text,to_jsonb(r),NULL
FROM identity.staff_branch_role r;
DROP TABLE identity.staff_branch_role;
DROP TABLE identity.app_role;
DROP TYPE shared.user_role;
DROP FUNCTION identity.fn_guard_role_history();
DROP FUNCTION platform.fn_audit_role_change();

-- Historical role evidence stays intact; all decisions after cutover use permissions.
CREATE OR REPLACE FUNCTION cashclose.fn_require_decision_authority() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.acted_permission IS NULL THEN
  RAISE EXCEPTION 'New close decisions require the authorizing permission';
 END IF;
 RETURN NEW;
END;
$$;
