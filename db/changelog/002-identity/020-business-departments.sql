-- Departments belong to a business, not a branch. A staff member may have
-- multiple current department assignments within that same business.
CREATE TABLE identity.department (
  department_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  department_code TEXT NOT NULL CHECK (department_code <> '' AND department_code = upper(department_code)),
  department_name TEXT NOT NULL CHECK (btrim(department_name) <> ''),
  display_order INTEGER NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (business_id, department_code),
  UNIQUE (department_id, business_id)
);
CREATE INDEX idx_department_business_order ON identity.department(business_id, display_order);
CREATE TRIGGER trg_department_touch BEFORE UPDATE ON identity.department
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- Keep assignment history when a staff member changes departments. Both
-- composite foreign keys enforce that staff and department share a business.
CREATE TABLE identity.staff_business_department (
  assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  staff_id UUID NOT NULL,
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  department_id UUID NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  revoked_at TIMESTAMPTZ,
  CHECK (revoked_at IS NULL OR revoked_at >= assigned_at),
  FOREIGN KEY (staff_id, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (department_id, business_id) REFERENCES identity.department(department_id, business_id)
);
CREATE UNIQUE INDEX uq_staff_business_department_live
  ON identity.staff_business_department(staff_id, department_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_staff_business_department_department
  ON identity.staff_business_department(business_id, department_id) WHERE revoked_at IS NULL;
CREATE TRIGGER trg_staff_business_department_history BEFORE UPDATE OR DELETE
  ON identity.staff_business_department FOR EACH ROW
  EXECUTE FUNCTION identity.fn_guard_permission_history();

SELECT shared.fn_apply_tenant_rls('identity', 'department');
SELECT shared.fn_apply_tenant_rls('identity', 'staff_business_department');
GRANT SELECT ON identity.department, identity.staff_business_department TO
  svc_identity, svc_platform, svc_files, svc_notify, svc_integration,
  svc_cashclose, svc_workforce, svc_inventory, svc_reporting;
GRANT INSERT, UPDATE ON identity.department, identity.staff_business_department TO svc_identity;

-- Apply the catalogue to existing businesses when this changeset first runs.
INSERT INTO identity.department(business_id, department_code, department_name, display_order)
SELECT b.business_id, d.department_code, d.department_name, d.display_order
FROM identity.business b
CROSS JOIN (VALUES
  ('HR_OPERATIONS',    'HR / Operations', 1),
  ('STORE_OPERATIONS', 'Store Operations', 2),
  ('PRODUCT',          'Product', 3),
  ('MARKETING',        'Marketing', 4),
  ('FINANCE',          'Finance', 5)
) AS d(department_code, department_name, display_order)
ON CONFLICT (business_id, department_code) DO NOTHING;

-- New businesses receive the same catalogue in their provisioning transaction.
CREATE FUNCTION identity.fn_seed_default_departments() RETURNS trigger
LANGUAGE plpgsql AS $fn$
BEGIN
  INSERT INTO identity.department(business_id, department_code, department_name, display_order)
  VALUES
    (NEW.business_id, 'HR_OPERATIONS',    'HR / Operations', 1),
    (NEW.business_id, 'STORE_OPERATIONS', 'Store Operations', 2),
    (NEW.business_id, 'PRODUCT',          'Product', 3),
    (NEW.business_id, 'MARKETING',        'Marketing', 4),
    (NEW.business_id, 'FINANCE',          'Finance', 5);
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_business_seed_departments AFTER INSERT ON identity.business
  FOR EACH ROW EXECUTE FUNCTION identity.fn_seed_default_departments();
