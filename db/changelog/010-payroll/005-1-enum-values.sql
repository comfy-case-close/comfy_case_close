-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.5-salary-supplement-and-birthday.xml
-- New validation issue codes and the contract bulk import job type.

ALTER TYPE payroll.issue_code ADD VALUE IF NOT EXISTS 'MISSING_BIRTH_DATE';
ALTER TYPE payroll.issue_code ADD VALUE IF NOT EXISTS 'TIMESHEET_OUTSIDE_PERIOD';
ALTER TYPE payroll.issue_code ADD VALUE IF NOT EXISTS 'ATTENDANCE_NOT_CONFIRMED';
ALTER TYPE payroll.import_job_type ADD VALUE IF NOT EXISTS 'CONTRACT_BULK';
