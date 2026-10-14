-- Departments belong to identity: merge payroll.department into identity.department and add the DEPARTMENT_MANAGE permission.
--
--   payroll.department       -> merged into identity.department (identity already has the same catalogue, see
--                               002-identity/020-business-departments.sql), then dropped
--   payroll.position_profile -> STAYS in payroll (hrm owns it); its department_id now points at identity.department
--
-- Write ownership: identity.department is written only by identity-service (svc_identity has INSERT/UPDATE on it).
-- hrm reads it (svc_hrm has SELECT only) and no longer has a department API of its own. The statements below make sure
-- svc_hrm holds no write privilege on it.
--
-- Data is kept as it is:
--   * a payroll department whose (business, code) already exists in identity.department is not copied again;
--     the identity row stays as it is and position_profile.department_id is re-pointed to it
--   * any other payroll department is copied with the SAME department_id, so nothing that points at it changes
--
-- DEPARTMENT_MANAGE (BUSINESS scope) is the permission for creating, editing and deactivating departments in identity-service.
-- It is only added to the dictionary: granting it to positions is a separate step (identity.position_permission).
--
-- Needs an administrative migration role (BYPASSRLS or superuser): the tables are FORCE ROW LEVEL SECURITY and the
-- migration reads and writes every business. Same requirement as 002-identity/005-staff-names.sql.

-- ----------------------------------------------------------------------------
-- 0. Preconditions
-- ----------------------------------------------------------------------------
DO $do$
DECLARE
  v_bad text;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)) THEN
    RAISE EXCEPTION 'Merging payroll.department into identity.department needs an administrative migration role with BYPASSRLS or superuser';
  END IF;
  IF to_regclass('payroll.department') IS NULL OR to_regclass('payroll.position_profile') IS NULL THEN
    RAISE EXCEPTION 'payroll.department and payroll.position_profile must both exist (already merged?)';
  END IF;
  IF to_regclass('identity.department') IS NULL THEN
    RAISE EXCEPTION 'identity.department does not exist (002-identity/020-business-departments.sql has not run)';
  END IF;

  -- identity.department rejects codes that are not upper case and blank names. Do not rewrite data silently: stop and say which rows.
  SELECT string_agg(pd.business_id || ':' || pd.department_code, ', ') INTO v_bad
    FROM payroll.department pd
   WHERE NOT EXISTS (SELECT 1 FROM identity.department d
                      WHERE d.business_id = pd.business_id AND d.department_code = upper(btrim(pd.department_code)))
     AND (pd.department_code = '' OR pd.department_code <> upper(pd.department_code) OR btrim(pd.department_name) = '');
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'payroll.department rows that identity.department would reject (code must be upper case, name not blank): %', v_bad;
  END IF;
END $do$;

-- Row counts before the merge, for the check at the end.
CREATE TEMP TABLE mig_department_counts AS
SELECT (SELECT count(*) FROM payroll.department)       AS departments,
       (SELECT count(*) FROM payroll.position_profile) AS profiles;

-- ----------------------------------------------------------------------------
-- 1. Permission: DEPARTMENT_MANAGE (global dictionary, migrations alone change it)
-- ----------------------------------------------------------------------------
INSERT INTO identity.permission (permission_code, scope, description) VALUES
 ('DEPARTMENT_MANAGE', 'BUSINESS', 'create, edit and deactivate departments')
ON CONFLICT (permission_code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. Departments: copy the ones identity does not have yet (same department_id), then map old id -> identity id
-- ----------------------------------------------------------------------------
INSERT INTO identity.department (department_id, business_id, department_code, department_name, display_order, is_active)
SELECT pd.department_id, pd.business_id, pd.department_code, pd.department_name, pd.display_order, pd.is_active
  FROM payroll.department pd
 WHERE NOT EXISTS (SELECT 1 FROM identity.department d
                    WHERE d.business_id = pd.business_id AND d.department_code = upper(btrim(pd.department_code)));

CREATE TEMP TABLE mig_department_map AS
SELECT pd.department_id AS old_id, d.department_id AS new_id, pd.business_id
  FROM payroll.department pd
  JOIN identity.department d ON d.business_id = pd.business_id AND d.department_code = upper(btrim(pd.department_code));

-- ----------------------------------------------------------------------------
-- 3. position_profile (stays in payroll): detach from payroll.department, re-point the rows, attach to identity.department
-- ----------------------------------------------------------------------------
DO $do$
DECLARE
  c record;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
            WHERE conrelid = 'payroll.position_profile'::regclass AND contype = 'f'
              AND confrelid = 'payroll.department'::regclass
  LOOP
    EXECUTE format('ALTER TABLE payroll.position_profile DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $do$;

UPDATE payroll.position_profile pp
   SET department_id = m.new_id
  FROM mig_department_map m
 WHERE m.old_id = pp.department_id AND m.business_id = pp.business_id AND m.new_id <> pp.department_id;

ALTER TABLE payroll.position_profile
  ADD CONSTRAINT position_profile_department_id_business_id_fkey
  FOREIGN KEY (department_id, business_id) REFERENCES identity.department (department_id, business_id);

-- ----------------------------------------------------------------------------
-- 4. payroll.department is now redundant. No CASCADE on purpose: if something else still depends on it, this fails.
-- ----------------------------------------------------------------------------
DROP TABLE payroll.department;

-- hrm only reads identity.department. SELECT comes from 910-roles; make sure no write privilege exists.
REVOKE INSERT, UPDATE, DELETE ON identity.department FROM svc_hrm;

-- ----------------------------------------------------------------------------
-- 5. Checks: nothing lost, tenant isolation intact. Any failure rolls the whole changeset back.
-- ----------------------------------------------------------------------------
DO $do$
DECLARE
  v_before record;
  n bigint;
BEGIN
  SELECT * INTO v_before FROM mig_department_counts;
  SELECT count(*) INTO n FROM payroll.position_profile;
  IF n <> v_before.profiles THEN
    RAISE EXCEPTION 'position_profile rows: % after the merge, % before', n, v_before.profiles;
  END IF;
  IF EXISTS (SELECT 1 FROM mig_department_map m
              LEFT JOIN identity.department d ON d.department_id = m.new_id AND d.business_id = m.business_id
             WHERE d.department_id IS NULL) THEN
    RAISE EXCEPTION 'a payroll department has no counterpart in identity.department';
  END IF;
  IF (SELECT count(*) FROM mig_department_map) <> v_before.departments THEN
    RAISE EXCEPTION 'department map has % rows, payroll.department had %', (SELECT count(*) FROM mig_department_map), v_before.departments;
  END IF;
  IF to_regclass('payroll.department') IS NOT NULL THEN
    RAISE EXCEPTION 'payroll.department is still there';
  END IF;
  IF to_regclass('payroll.position_profile') IS NULL THEN
    RAISE EXCEPTION 'payroll.position_profile must stay in payroll';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                  WHERE conrelid = 'payroll.position_profile'::regclass AND contype = 'f'
                    AND confrelid = 'identity.department'::regclass) THEN
    RAISE EXCEPTION 'position_profile is not attached to identity.department';
  END IF;
  IF has_table_privilege('svc_hrm', 'identity.department', 'INSERT')
     OR has_table_privilege('svc_hrm', 'identity.department', 'UPDATE')
     OR has_table_privilege('svc_hrm', 'identity.department', 'DELETE') THEN
    RAISE EXCEPTION 'svc_hrm must not write identity.department';
  END IF;
  IF NOT has_table_privilege('svc_hrm', 'identity.department', 'SELECT') THEN
    RAISE EXCEPTION 'svc_hrm must be able to read identity.department';
  END IF;
  IF NOT has_table_privilege('svc_identity', 'identity.department', 'INSERT')
     OR NOT has_table_privilege('svc_identity', 'identity.department', 'UPDATE') THEN
    RAISE EXCEPTION 'svc_identity must be able to write identity.department';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM identity.permission WHERE permission_code = 'DEPARTMENT_MANAGE' AND scope = 'BUSINESS') THEN
    RAISE EXCEPTION 'DEPARTMENT_MANAGE is missing from identity.permission';
  END IF;
END $do$;

DROP TABLE mig_department_map, mig_department_counts;
