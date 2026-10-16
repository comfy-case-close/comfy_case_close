-- Reuse the assignment enum while keeping the independent, nullable HR classification.
-- 018 is already deployed: translate its FULL_TIME/PART_TIME values during the upgrade.
ALTER TABLE payroll.employee_profile
  DROP CONSTRAINT ck_employee_profile_employment_type,
  ALTER COLUMN employment_type TYPE payroll.employment_type
    USING (CASE employment_type
      WHEN 'FULL_TIME' THEN 'FULLTIME'
      WHEN 'PART_TIME' THEN 'PARTTIME'
      ELSE employment_type
    END)::payroll.employment_type;

COMMENT ON COLUMN payroll.employee_profile.employment_type IS
  'HR classification from the employee roster: FULLTIME or PARTTIME; NULL when unknown. Independent of the pay model on each employment_assignment.';
