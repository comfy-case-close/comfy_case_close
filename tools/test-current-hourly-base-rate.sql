-- Run after Liquibase 010-payroll-021, on a disposable database as the migration owner.
BEGIN;
DO $test$
DECLARE
  test_business uuid := gen_random_uuid();
  test_staff uuid := gen_random_uuid();
  test_position uuid := gen_random_uuid();
  test_branch uuid := gen_random_uuid();
  test_assignment uuid := gen_random_uuid();
BEGIN
  IF (SELECT atttypid FROM pg_attribute WHERE attrelid='payroll.employee_profile'::regclass
      AND attname='current_hourly_base_rate' AND NOT attisdropped)
      IS DISTINCT FROM (SELECT atttypid FROM pg_attribute WHERE attrelid='payroll.employment_assignment'::regclass
      AND attname='hourly_base_rate' AND NOT attisdropped)
    THEN RAISE EXCEPTION 'Current rate must use exactly the contractual rate domain'; END IF;
  INSERT INTO identity.business(business_id,business_code,business_name)
    VALUES(test_business,upper(test_business::text),'Rate test');
  INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash)
    VALUES(test_staff,test_business,'RATE_TEST','Rate','Test','unused');
  INSERT INTO payroll.employee_profile(staff_id,business_id) VALUES(test_staff,test_business);
  IF EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid='payroll.employment_assignment'::regclass
      AND attname='current_hourly_base_rate' AND NOT attisdropped)
    THEN RAISE EXCEPTION 'Current roster rate must no longer be on assignments'; END IF;
  INSERT INTO identity.position(position_id,business_id,position_code,position_name)
    VALUES(test_position,test_business,'RATE_TEST','Rate test');
  INSERT INTO identity.branch(branch_id,business_id,branch_code,branch_name)
    VALUES(test_branch,test_business,'RATE_TEST','Rate test');
  INSERT INTO payroll.employment_assignment(employment_assignment_id,business_id,staff_id,position_id,
    default_branch_id,employment_type,monthly_base_salary,hourly_base_rate,effective_from)
    VALUES(test_assignment,test_business,test_staff,test_position,test_branch,'FULLTIME',0,12345,'2026-01-01');
  IF (SELECT current_hourly_base_rate IS NOT NULL FROM payroll.employee_profile WHERE staff_id=test_staff)
    THEN RAISE EXCEPTION 'Missing current rate must remain NULL'; END IF;
  UPDATE payroll.employee_profile SET current_hourly_base_rate=0 WHERE staff_id=test_staff;
  UPDATE payroll.employee_profile SET current_hourly_base_rate=33654 WHERE staff_id=test_staff;
  BEGIN
    UPDATE payroll.employee_profile SET current_hourly_base_rate=-1 WHERE staff_id=test_staff;
    RAISE EXCEPTION 'Negative current rate was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  IF (SELECT hourly_base_rate FROM payroll.employment_assignment WHERE employment_assignment_id=test_assignment)<>12345
    THEN RAISE EXCEPTION 'Contractual rate changed'; END IF;
  IF (SELECT current_hourly_base_rate FROM payroll.employee_profile WHERE staff_id=test_staff)<>33654
    THEN RAISE EXCEPTION 'Current rate was not retained'; END IF;
END $test$;
ROLLBACK;
