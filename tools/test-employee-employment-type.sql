BEGIN;
DO $test$
DECLARE
  business UUID := gen_random_uuid();
  staff UUID := gen_random_uuid();
BEGIN
  INSERT INTO identity.business(business_id,business_code,business_name)
    VALUES(business,upper(business::text),'Employment type test');
  INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash)
    VALUES(staff,business,'HR_TYPE','HR','Type','unused');
  INSERT INTO payroll.employee_profile(staff_id,business_id) VALUES(staff,business);
  IF (SELECT employment_type IS NOT NULL FROM payroll.employee_profile WHERE staff_id=staff)
    THEN RAISE EXCEPTION 'Unknown classification must remain NULL'; END IF;
  UPDATE payroll.employee_profile SET employment_type='FULL_TIME' WHERE staff_id=staff;
  UPDATE payroll.employee_profile SET employment_type='PART_TIME' WHERE staff_id=staff;
  IF (SELECT employment_type FROM payroll.employee_profile WHERE staff_id=staff)<>'PART_TIME'
    THEN RAISE EXCEPTION 'Source classification must be updateable'; END IF;
  BEGIN
    UPDATE payroll.employee_profile SET employment_type='FULLTIME' WHERE staff_id=staff;
    RAISE EXCEPTION 'Unsupported classification was accepted';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END $test$;
ROLLBACK;
