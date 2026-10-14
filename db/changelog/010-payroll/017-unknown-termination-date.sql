-- A confirmed departure may have no known date. Do not invent terminated_on.
ALTER TABLE payroll.employee_profile
  ADD COLUMN IF NOT EXISTS termination_date_unknown BOOLEAN NOT NULL DEFAULT false;

DO $lifecycle$
BEGIN
  -- Also accept databases already prepared by the standalone employee import.
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='employee_profile' AND column_name='is_active'
      AND is_generated='ALWAYS' AND generation_expression LIKE '%termination_date_unknown%'
  ) THEN
    IF current_setting('server_version_num')::integer >= 170000 THEN
      EXECUTE 'ALTER TABLE payroll.employee_profile ALTER COLUMN is_active
        SET EXPRESSION AS (terminated_on IS NULL AND NOT termination_date_unknown)';
    ELSE
      -- PostgreSQL 16 cannot replace a generated expression in place.
      -- No CASCADE: fail if a dependent view prevents recreating this derived column.
      ALTER TABLE payroll.employee_profile DROP COLUMN is_active RESTRICT;
      ALTER TABLE payroll.employee_profile ADD COLUMN is_active BOOLEAN
        GENERATED ALWAYS AS (terminated_on IS NULL AND NOT termination_date_unknown) STORED;
    END IF;
  END IF;
END $lifecycle$;

COMMENT ON COLUMN payroll.employee_profile.termination_date_unknown IS
  'Termination confirmed but date unavailable; retain NULL terminated_on rather than invent a date. Clear on verified rehire.';
