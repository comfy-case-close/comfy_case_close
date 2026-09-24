-- Disposable upgrade fixture inserted after 013 and before the permission migrations.
INSERT INTO identity.business(business_id,business_code,business_name) VALUES
('00000000-0000-4000-8000-000000000001','PERMISSION_UPGRADE','Permission upgrade');
INSERT INTO identity.branch(branch_id,business_id,branch_code,branch_name) VALUES
('00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000001','MAIN','Main');
INSERT INTO identity.staff_position(position_id,business_id,position_code,position_name) VALUES
('00000000-0000-4000-8000-000000000003','00000000-0000-4000-8000-000000000001','BARISTA','Barista');
INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash)
SELECT ('00000000-0000-4000-8000-00000000010'||n)::uuid,'00000000-0000-4000-8000-000000000001',role,'Test',role,'unused'
FROM (VALUES(1,'STAFF'),(2,'MANAGER'),(3,'ADMIN'),(4,'ACCOUNTANT')) AS v(n,role);
INSERT INTO identity.staff_branch_role(staff_id,branch_id,business_id,role)
SELECT staff_id,'00000000-0000-4000-8000-000000000002',business_id,employee_code::shared.user_role
FROM identity.staff WHERE business_id='00000000-0000-4000-8000-000000000001';
INSERT INTO identity.staff_branch_position(staff_id,branch_id,position_id,business_id)
SELECT staff_id,'00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000003',business_id
FROM identity.staff WHERE business_id='00000000-0000-4000-8000-000000000001';
