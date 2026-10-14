-- HR work classification is independent of the pay model on each assignment.
-- NULL means the source has not supplied a classification; do not infer it from contracts.
ALTER TABLE payroll.employee_profile
  ADD COLUMN employment_type TEXT
    CONSTRAINT ck_employee_profile_employment_type
      CHECK (employment_type IN ('FULL_TIME','PART_TIME'));

COMMENT ON COLUMN payroll.employee_profile.employment_type IS
  'HR classification from the employee roster: FULL_TIME or PART_TIME; independent of employment_assignment.employment_type.';
