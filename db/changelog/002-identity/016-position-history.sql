ALTER TABLE identity.staff_branch_position
 ADD COLUMN assignment_id UUID NOT NULL DEFAULT gen_random_uuid(),
 ADD COLUMN assigned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 ADD COLUMN revoked_at TIMESTAMPTZ;
ALTER TABLE identity.staff_branch_position DROP CONSTRAINT staff_branch_position_pkey;
ALTER TABLE identity.staff_branch_position ADD PRIMARY KEY(assignment_id),
 ADD CHECK(revoked_at IS NULL OR revoked_at>=assigned_at),
 ADD CONSTRAINT ex_sbp_validity EXCLUDE USING gist(staff_id WITH =,branch_id WITH =,position_id WITH =,tstzrange(assigned_at,revoked_at,'[)') WITH &&);
CREATE UNIQUE INDEX uq_sbp_live ON identity.staff_branch_position(staff_id,branch_id,position_id) WHERE revoked_at IS NULL;
CREATE TRIGGER trg_sbp_history BEFORE UPDATE OR DELETE ON identity.staff_branch_position FOR EACH ROW EXECUTE FUNCTION identity.fn_guard_permission_history();
CREATE TRIGGER trg_sbp_audit AFTER INSERT OR UPDATE ON identity.staff_branch_position FOR EACH ROW EXECUTE FUNCTION identity.fn_audit_permission_change();
REVOKE DELETE ON identity.staff_branch_position FROM svc_identity;
