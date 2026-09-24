-- Match dev.UserRole exactly without granting elevated access to retired roles.
-- Retain original roles on history rows; audit/decision snapshots are untouched.
ALTER TABLE identity.staff_branch_role ADD COLUMN legacy_role TEXT;
ALTER TABLE identity.staff_branch_role DISABLE TRIGGER trg_role_history_guard;
ALTER TABLE identity.staff_branch_role DISABLE TRIGGER trg_role_audit;
UPDATE identity.staff_branch_role SET legacy_role = role::text
WHERE role::text IN ('HR', 'SHIFT_LEAD');
ALTER TABLE identity.staff_branch_role ENABLE TRIGGER trg_role_history_guard;
ALTER TABLE identity.staff_branch_role ENABLE TRIGGER trg_role_audit;

ALTER TYPE shared.user_role RENAME TO user_role_legacy;
CREATE TYPE shared.user_role AS ENUM ('STAFF', 'MANAGER', 'ADMIN', 'ACCOUNTANT');
ALTER TABLE identity.staff_branch_role ALTER COLUMN role TYPE shared.user_role
  USING (CASE WHEN role::text IN ('HR', 'SHIFT_LEAD') THEN 'STAFF'
              ELSE role::text END)::shared.user_role;
DROP TYPE shared.user_role_legacy;

-- Global application-role dictionary, not tenant-editable permission definitions.
CREATE TABLE identity.app_role (
  role_code shared.user_role PRIMARY KEY,
  role_name TEXT NOT NULL UNIQUE
);
INSERT INTO identity.app_role (role_code, role_name) VALUES
  ('STAFF', 'Staff'),
  ('MANAGER', 'Manager'),
  ('ADMIN', 'Admin'),
  ('ACCOUNTANT', 'Accountant');

ALTER TABLE identity.staff_branch_role ADD CONSTRAINT fk_sbr_app_role
  FOREIGN KEY (role) REFERENCES identity.app_role(role_code);

-- Default privileges can grant identity-service DML on newly created tables.
-- Roles are maintained by migrations; services only read this global dictionary.
REVOKE ALL ON identity.app_role FROM PUBLIC;
REVOKE INSERT, UPDATE, DELETE ON identity.app_role FROM svc_identity;
GRANT SELECT ON identity.app_role TO
  svc_identity, svc_platform, svc_files, svc_notify, svc_integration,
  svc_cashclose, svc_workforce, svc_inventory, svc_reporting;
