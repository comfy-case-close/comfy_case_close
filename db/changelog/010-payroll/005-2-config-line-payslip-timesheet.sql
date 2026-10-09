-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.5-salary-supplement-and-birthday.xml
-- Birthday allowance config, late-shift counters and the attendance origin of a timesheet cell.

ALTER TABLE payroll.payroll_config
  ADD COLUMN birthday_allowance_amount     shared.d_money_nonneg NOT NULL DEFAULT 0,
  ADD COLUMN birthday_allowance_applies_to payroll.applies_to NOT NULL DEFAULT 'BOTH';

ALTER TABLE payroll.payroll_line
  ADD COLUMN late_shift_count SMALLINT NOT NULL DEFAULT 0 CHECK (late_shift_count >= 0),
  ADD COLUMN snap_supplement_allowance shared.d_money NOT NULL DEFAULT 0;

ALTER TABLE payroll.payslip
  ADD COLUMN late_shift_total SMALLINT NOT NULL DEFAULT 0 CHECK (late_shift_total >= 0);

CREATE TYPE payroll.timesheet_source AS ENUM ('MANUAL', 'ATTENDANCE');

ALTER TABLE payroll.timesheet_entry
  ADD COLUMN late_shifts         SMALLINT NOT NULL DEFAULT 0 CHECK (late_shifts >= 0),
  ADD COLUMN source              payroll.timesheet_source NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN attendance_sheet_id UUID,
  ADD COLUMN adjust_reason       TEXT,
  ADD CONSTRAINT ck_timesheet_entry_attendance_source
    CHECK ((source = 'ATTENDANCE') = (attendance_sheet_id IS NOT NULL));
