-- Run in a disposable DB after migrations 001..011, before 012/013.
INSERT INTO identity.business (business_id, business_code, business_name) VALUES
 ('10000000-0000-0000-0000-000000000001', 'POSITION_TEST_A', 'A'),
 ('10000000-0000-0000-0000-000000000002', 'POSITION_TEST_B', 'B');
INSERT INTO identity.branch (branch_id, business_id, branch_code, branch_name)
SELECT business_id, business_id, 'MAIN', 'Main' FROM identity.business
WHERE business_code LIKE 'POSITION_TEST_%';
INSERT INTO identity.staff_position (position_id, business_id, position_code, position_name)
SELECT business_id, business_id, 'BARISTA', 'Barista' FROM identity.business
WHERE business_code LIKE 'POSITION_TEST_%';
INSERT INTO identity.staff (staff_id, business_id, employee_code, first_name, last_name,
 position_id, passcode_hash, passcode_algo)
SELECT business_id, business_id, 'TEST', 'Test', 'Staff', business_id, 'unused', 'bcrypt'
FROM identity.business WHERE business_code LIKE 'POSITION_TEST_%';
INSERT INTO identity.staff_branch_role (staff_id, branch_id, business_id, role)
SELECT business_id, business_id, business_id,
 CASE WHEN business_code = 'POSITION_TEST_A' THEN 'SHIFT_LEAD' ELSE 'HR' END::shared.user_role
FROM identity.business WHERE business_code LIKE 'POSITION_TEST_%';

\ir ../db/changelog/002-identity/012-staff-branch-position.sql
\ir ../db/changelog/002-identity/013-app-role.sql

DO $$
DECLARE
 a UUID := '10000000-0000-0000-0000-000000000001';
 b UUID := '10000000-0000-0000-0000-000000000002';
BEGIN
 IF (SELECT count(*) FROM identity.staff_branch_position) <> 2 THEN
   RAISE EXCEPTION 'Legacy positions were not backfilled';
 END IF;
 IF (SELECT count(*) FROM identity.staff_branch_role WHERE role = 'STAFF'
     AND legacy_role IN ('HR', 'SHIFT_LEAD')) <> 2 THEN
   RAISE EXCEPTION 'Legacy roles were not preserved and migrated';
 END IF;
 IF enum_range(NULL::shared.user_role)::text <> '{STAFF,MANAGER,ADMIN,ACCOUNTANT}'
    OR (SELECT count(*) FROM identity.app_role) <> 4 THEN
   RAISE EXCEPTION 'Role dictionary differs from dev';
 END IF;
 BEGIN
   INSERT INTO identity.staff_branch_position VALUES (a, b, a, a);
   RAISE EXCEPTION 'Cross-business branch allowed';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
   INSERT INTO identity.staff_branch_position VALUES (b, a, a, a);
   RAISE EXCEPTION 'Cross-business staff allowed';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
   INSERT INTO identity.staff_branch_position VALUES (a, a, b, a);
   RAISE EXCEPTION 'Cross-business position allowed';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
   INSERT INTO identity.staff_branch_position VALUES (a, a, a, a);
   RAISE EXCEPTION 'Duplicate position allowed';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
 INSERT INTO identity.staff_position VALUES (gen_random_uuid(), a, 'CASHIER', 'Cashier', true);
 INSERT INTO identity.staff_branch_position
 SELECT a, a, position_id, a FROM identity.staff_position WHERE position_code = 'CASHIER';
 IF (SELECT count(*) FROM identity.staff_branch_position WHERE staff_id = a) <> 2 THEN
   RAISE EXCEPTION 'Multiple positions rejected';
 END IF;
 IF has_table_privilege('svc_identity', 'identity.app_role', 'INSERT') THEN
   RAISE EXCEPTION 'Service may modify global role catalog';
 END IF;
END $$;

SET ROLE svc_identity;
SELECT set_config('app.business_id', '10000000-0000-0000-0000-000000000001', false);
DO $$ BEGIN
 IF (SELECT count(*) FROM identity.staff_branch_position) <> 2 THEN
   RAISE EXCEPTION 'RLS did not isolate tenant A';
 END IF;
 BEGIN
   INSERT INTO identity.staff_branch_position VALUES (
     '10000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000002');
   RAISE EXCEPTION 'Cross-tenant write allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
END $$;
RESET ROLE;
