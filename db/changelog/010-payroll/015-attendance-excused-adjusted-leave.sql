-- Ported from feat/payroll: db/changelog/payroll/1.0.0.15-attendance-excused-adjusted-leave.xml
-- Late with permission (no deduction, level not needed), and hours really worked.

ALTER TABLE payroll.attendance_exception
  ADD COLUMN excused        BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN adjusted_hours payroll.d_hours;

ALTER TABLE payroll.attendance_exception DROP CONSTRAINT attendance_exception_check;

ALTER TABLE payroll.attendance_exception
  ADD CONSTRAINT ck_attendance_exception_late
    CHECK ((status = 'LATE') = (late_level IS NOT NULL OR excused)),
  ADD CONSTRAINT ck_attendance_exception_excused
    CHECK (NOT excused OR status = 'LATE'),
  ADD CONSTRAINT ck_attendance_exception_adjusted
    CHECK ((status = 'ADJUSTED') = (adjusted_hours IS NOT NULL));
