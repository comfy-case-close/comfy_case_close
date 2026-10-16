-- Run as the migration owner (BYPASSRLS/superuser) to preserve data across all businesses.
-- Refuse to discard conflicting values or values without an existing employee profile.
-- Keep the validation/copy/drop snapshot stable until the changeset commits.
LOCK TABLE payroll.employment_assignment IN ACCESS EXCLUSIVE MODE;
DO $migration$
BEGIN
  IF EXISTS (
    SELECT 1 FROM payroll.employment_assignment
    WHERE current_hourly_base_rate IS NOT NULL
    GROUP BY business_id, staff_id
    HAVING count(DISTINCT current_hourly_base_rate) > 1
  ) THEN
    RAISE EXCEPTION 'Cannot move current_hourly_base_rate: a staff member has conflicting assignment rates; reconcile them before retrying';
  END IF;
  IF EXISTS (
    SELECT 1 FROM payroll.employment_assignment a
    WHERE a.current_hourly_base_rate IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM payroll.employee_profile p
        WHERE p.staff_id = a.staff_id AND p.business_id = a.business_id)
  ) THEN
    RAISE EXCEPTION 'Cannot move current_hourly_base_rate: an assignment rate has no matching employee_profile; create the profile before retrying';
  END IF;
END $migration$;

ALTER TABLE payroll.employee_profile
  ADD COLUMN current_hourly_base_rate shared.d_money_nonneg;

-- The guard above guarantees that MAX selects the only distinct non-NULL value.
UPDATE payroll.employee_profile p
SET current_hourly_base_rate = source.rate
FROM (
  SELECT business_id, staff_id, max(current_hourly_base_rate) AS rate
  FROM payroll.employment_assignment
  WHERE current_hourly_base_rate IS NOT NULL
  GROUP BY business_id, staff_id
) source
WHERE p.business_id = source.business_id AND p.staff_id = source.staff_id;

ALTER TABLE payroll.employment_assignment
  DROP COLUMN current_hourly_base_rate;

COMMENT ON COLUMN payroll.employee_profile.current_hourly_base_rate IS
  'Current hourly rate from the employee roster (VND/hour). NULL when unknown. Independent of contractual employment_assignment.hourly_base_rate; not used by the payroll calculation engine.';
