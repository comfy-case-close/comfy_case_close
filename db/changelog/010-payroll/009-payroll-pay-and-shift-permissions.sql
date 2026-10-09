-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.9-payroll-pay-and-shift-permissions.xml
-- Dictionary entries for the accountant payment page and for shift scheduling; grants are assigned to positions afterwards.

INSERT INTO identity.permission (permission_code, scope, description) VALUES
 ('PAYROLL_PAY',            'BUSINESS', 'mark salary transfers as paid or failed, read full bank account numbers'),
 ('SHIFT_CONFIG_WRITE',     'BUSINESS', 'shift slots, staffing minimums and branch scheduling settings'),
 ('SHIFT_SCHEDULE_READ',    'BRANCH',   'view registrations, schedules and attendance of a branch'),
 ('SHIFT_SCHEDULE_EDIT',    'BRANCH',   'open registration, arrange and submit the schedule, create and mark attendance'),
 ('SHIFT_SCHEDULE_APPROVE', 'BRANCH',   'approve or return schedules, confirm or reopen attendance, edit a published schedule')
ON CONFLICT (permission_code) DO NOTHING;
