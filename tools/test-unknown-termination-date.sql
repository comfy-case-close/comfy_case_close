-- Run against a disposable database with the current changelog applied.
BEGIN;
DO $test$
DECLARE
  business UUID := gen_random_uuid();
  active_staff UUID := gen_random_uuid();
  dated_staff UUID := gen_random_uuid();
  unknown_staff UUID := gen_random_uuid();
BEGIN
  INSERT INTO identity.business(business_id,business_code,business_name)
    VALUES(business,upper(business::text),'Termination test');
  INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash)
    VALUES(active_staff,business,'ACTIVE','Active','Test','unused'),
          (dated_staff,business,'DATED','Dated','Test','unused'),
          (unknown_staff,business,'UNKNOWN','Unknown','Test','unused');
  INSERT INTO payroll.employee_profile(staff_id,business_id,terminated_on)
    VALUES(active_staff,business,NULL),(dated_staff,business,DATE '2026-09-01'),
          (unknown_staff,business,NULL);
  IF NOT (SELECT is_active AND NOT termination_date_unknown FROM payroll.employee_profile WHERE staff_id=active_staff)
    THEN RAISE EXCEPTION 'Default flag must preserve active employees'; END IF;
  IF (SELECT is_active FROM payroll.employee_profile WHERE staff_id=dated_staff)
    THEN RAISE EXCEPTION 'Known departure date must still make employees inactive'; END IF;

  UPDATE payroll.employee_profile SET termination_date_unknown=true WHERE staff_id=unknown_staff;
  IF NOT (SELECT NOT is_active AND terminated_on IS NULL FROM payroll.employee_profile WHERE staff_id=unknown_staff)
    THEN RAISE EXCEPTION 'Unknown departure date must allow inactive status without inventing a date'; END IF;

  UPDATE payroll.employee_profile SET terminated_on=DATE '2026-09-02',termination_date_unknown=false
    WHERE staff_id=unknown_staff;
  IF (SELECT is_active FROM payroll.employee_profile WHERE staff_id=unknown_staff)
    THEN RAISE EXCEPTION 'Confirming a departure date must keep employees inactive'; END IF;

  UPDATE payroll.employee_profile SET terminated_on=NULL,termination_date_unknown=false WHERE staff_id=unknown_staff;
  IF NOT (SELECT is_active FROM payroll.employee_profile WHERE staff_id=unknown_staff)
    THEN RAISE EXCEPTION 'Verified rehire must restore active status'; END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='payroll'
      AND table_name='employee_profile' AND column_name='is_active' AND is_generated='ALWAYS')
    THEN RAISE EXCEPTION 'is_active must remain generated for the existing JPA mapping'; END IF;
END $test$;
ROLLBACK;
