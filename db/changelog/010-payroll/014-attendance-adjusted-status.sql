-- Ported from feat/payroll: db/changelog/payroll/1.0.0.14-attendance-adjusted-status.xml
-- A shift worked for other hours than scheduled (left early with permission, arrived at an agreed time, worked extra): the sheet records the hours really worked.

ALTER TYPE payroll.attendance_exception_status ADD VALUE IF NOT EXISTS 'ADJUSTED';
