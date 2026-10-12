-- Version role assignments without inventing history lost before this migration.
-- Existing assigned_at values are retained as legacy data, not verified role-start dates.
ALTER TABLE identity.staff_branch_role
  ADD COLUMN assignment_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE identity.staff_branch_role DROP CONSTRAINT staff_branch_role_pkey;
ALTER TABLE identity.staff_branch_role ADD PRIMARY KEY (assignment_id);
CREATE UNIQUE INDEX uq_sbr_current ON identity.staff_branch_role (staff_id, branch_id)
  WHERE revoked_at IS NULL;
ALTER TABLE identity.staff_branch_role ADD CONSTRAINT ex_sbr_validity
  EXCLUDE USING gist (staff_id WITH =, branch_id WITH =,
    tstzrange(assigned_at, revoked_at, '[)') WITH &&);
ALTER TABLE identity.staff_branch_role ALTER COLUMN assigned_at SET DEFAULT clock_timestamp();

-- Versions may only be closed once. Their identity, role and start cannot be rewritten.
CREATE FUNCTION identity.fn_guard_role_history() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'Role history cannot be deleted';
  END IF;
  IF OLD.revoked_at IS NOT NULL
     OR NEW.revoked_at IS NULL
     OR (to_jsonb(NEW) - 'revoked_at') IS DISTINCT FROM (to_jsonb(OLD) - 'revoked_at') THEN
    RAISE EXCEPTION 'Role versions are immutable except for closing a live version';
  END IF;
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_role_history_guard BEFORE UPDATE OR DELETE ON identity.staff_branch_role
  FOR EACH ROW EXECUTE FUNCTION identity.fn_guard_role_history();

-- Keep the existing audit stream, adding the version, boundary and authenticated actor.
CREATE OR REPLACE FUNCTION platform.fn_audit_role_change() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  INSERT INTO platform.audit_log
    (business_id, actor_user_id, action, entity_type, entity_id, old_value, new_value)
  VALUES (
    COALESCE(NEW.business_id, OLD.business_id), shared.current_user_id(),
    CASE WHEN TG_OP = 'INSERT' THEN 'ROLE_GRANTED' ELSE 'ROLE_REVOKED' END,
    'staff_branch_role', COALESCE(NEW.staff_id, OLD.staff_id)::text,
    CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE to_jsonb(OLD) END,
    CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE to_jsonb(NEW) END
  );
  RETURN NULL;
END $fn$;
