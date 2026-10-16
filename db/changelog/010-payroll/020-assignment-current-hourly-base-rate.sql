-- Independent current roster rate; preserve the contractual rate and payroll calculation rules.
ALTER TABLE payroll.employment_assignment
  ADD COLUMN current_hourly_base_rate shared.d_money_nonneg;

COMMENT ON COLUMN payroll.employment_assignment.current_hourly_base_rate IS
  'Current hourly rate from the employee roster (VND/hour), independent of the contractual hourly_base_rate. NULL when unknown; not used by the payroll calculation engine.';
