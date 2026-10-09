-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.3-payroll-period-dates.xml
-- One period per month stays; the date range becomes editable but may never overlap another period.

ALTER TABLE payroll.payroll_period
  ADD CONSTRAINT ck_payroll_period_max_days CHECK (end_date - start_date + 1 <= 62),
  ADD CONSTRAINT ex_payroll_period_no_overlap EXCLUDE USING gist (
    business_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
  );
