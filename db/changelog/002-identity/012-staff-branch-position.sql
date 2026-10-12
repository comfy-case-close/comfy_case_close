-- Multiple job positions per staff member and branch, like dev.user_positions.
-- Composite FKs prevent associating records from different businesses.
CREATE TABLE identity.staff_branch_position (
  staff_id    UUID NOT NULL,
  branch_id   UUID NOT NULL,
  position_id UUID NOT NULL,
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  PRIMARY KEY (staff_id, branch_id, position_id, business_id),
  FOREIGN KEY (staff_id, business_id)
    REFERENCES identity.staff(staff_id, business_id) ON DELETE CASCADE,
  FOREIGN KEY (branch_id, business_id)
    REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (position_id, business_id)
    REFERENCES identity.staff_position(position_id, business_id)
);
CREATE INDEX idx_sbp_branch ON identity.staff_branch_position (business_id, branch_id);
CREATE INDEX idx_sbp_position ON identity.staff_branch_position (position_id, business_id);

-- Preserve known legacy positions at currently assigned branches. Keep staff.position_id
-- for existing consumers and people who do not yet have a branch assignment.
INSERT INTO identity.staff_branch_position (staff_id, branch_id, position_id, business_id)
SELECT s.staff_id, r.branch_id, s.position_id, s.business_id
FROM identity.staff s
JOIN identity.staff_branch_role r
  ON r.staff_id = s.staff_id AND r.business_id = s.business_id
WHERE s.position_id IS NOT NULL AND r.revoked_at IS NULL;

-- This migration runs after the original RLS/grant pass on upgrades AND fresh installs.
SELECT shared.fn_apply_tenant_rls('identity', 'staff_branch_position');
GRANT SELECT, INSERT, UPDATE, DELETE ON identity.staff_branch_position TO svc_identity;
GRANT SELECT ON identity.staff_branch_position TO
  svc_platform, svc_files, svc_notify, svc_integration, svc_cashclose,
  svc_workforce, svc_inventory, svc_reporting;
