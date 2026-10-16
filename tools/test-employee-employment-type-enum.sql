-- Run after 018 and before 019, with psql -v enum_migration=/path/to/019.sql.
BEGIN;
CREATE TEMP TABLE enum_upgrade_business ON COMMIT DROP AS
SELECT gen_random_uuid() AS business_id;
INSERT INTO identity.business(business_id,business_code,business_name)
SELECT business_id,upper(business_id::text),'Enum upgrade test' FROM enum_upgrade_business;
INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash)
SELECT gen_random_uuid(),business_id,employee_code,'Enum','Upgrade','unused'
FROM enum_upgrade_business
CROSS JOIN (VALUES ('ENUM_FULL'),('ENUM_PART'),('ENUM_UNKNOWN')) codes(employee_code);
INSERT INTO payroll.employee_profile(staff_id,business_id,employment_type,note)
SELECT staff_id,business_id,
  CASE employee_code WHEN 'ENUM_FULL' THEN 'FULL_TIME' WHEN 'ENUM_PART' THEN 'PART_TIME' END,
  'Keep existing profile data'
FROM identity.staff JOIN enum_upgrade_business USING(business_id);

\i :enum_migration

DO $test$
BEGIN
  IF EXISTS (
    SELECT 1 FROM payroll.employee_profile p JOIN identity.staff s USING(staff_id,business_id)
    JOIN enum_upgrade_business USING(business_id)
    WHERE p.employment_type IS DISTINCT FROM
      (CASE s.employee_code WHEN 'ENUM_FULL' THEN 'FULLTIME' WHEN 'ENUM_PART' THEN 'PARTTIME' END)::payroll.employment_type
      OR p.note <> 'Keep existing profile data'
  ) THEN
    RAISE EXCEPTION 'Upgrade must convert existing classifications and preserve NULL and other data';
  END IF;
  IF (SELECT count(*) FROM payroll.employee_profile JOIN enum_upgrade_business USING(business_id))<>3 THEN
    RAISE EXCEPTION 'Upgrade lost profiles';
  END IF;
  IF (SELECT atttypid FROM pg_attribute
      WHERE attrelid='payroll.employee_profile'::regclass AND attname='employment_type')
      <> 'payroll.employment_type'::regtype THEN
    RAISE EXCEPTION 'Upgrade did not use the existing enum';
  END IF;
END $test$;
ROLLBACK;
