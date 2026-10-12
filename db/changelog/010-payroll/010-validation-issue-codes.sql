-- Ported from feat/payroll: db/changelog/payroll/1.0.0.10-validation-issue-codes.xml
-- New payroll validation issue codes: leave over the quota, negative net pay, hours without pay, paid hours outside the contract.

ALTER TYPE payroll.issue_code ADD VALUE IF NOT EXISTS 'LEAVE_OVERDRAWN';
ALTER TYPE payroll.issue_code ADD VALUE IF NOT EXISTS 'NEGATIVE_NET_PAY';
ALTER TYPE payroll.issue_code ADD VALUE IF NOT EXISTS 'ZERO_PAY_WITH_HOURS';
ALTER TYPE payroll.issue_code ADD VALUE IF NOT EXISTS 'CONTRACT_NOT_IN_FORCE';
