-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.8-shift-scheduling.xml
-- Attendance sheet created from a published schedule; only exceptions are stored.

CREATE TYPE payroll.attendance_sheet_status     AS ENUM ('OPEN', 'IN_REVIEW', 'SUBMITTED', 'CONFIRMED');
CREATE TYPE payroll.attendance_exception_status AS ENUM ('LATE', 'LEAVE_PAID', 'LEAVE_UNPAID', 'ABSENT');
CREATE TYPE payroll.late_level                  AS ENUM ('LEVEL_1', 'LEVEL_2');

CREATE TABLE payroll.attendance_sheet (
  attendance_sheet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id         UUID NOT NULL REFERENCES identity.business(business_id),
  shift_schedule_id   UUID NOT NULL,
  status              payroll.attendance_sheet_status NOT NULL DEFAULT 'OPEN',
  submitted_by        UUID,
  submitted_at        TIMESTAMPTZ,
  return_reason       TEXT,
  confirmed_by        UUID,
  confirmed_at        TIMESTAMPTZ,
  reopened_by         UUID,
  reopen_reason       TEXT,
  version             BIGINT NOT NULL DEFAULT 0,
  UNIQUE (shift_schedule_id),
  UNIQUE (attendance_sheet_id, business_id),
  FOREIGN KEY (shift_schedule_id, business_id)
    REFERENCES payroll.shift_schedule(shift_schedule_id, business_id),
  FOREIGN KEY (submitted_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (confirmed_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (reopened_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);

CREATE TABLE payroll.attendance_exception (
  shift_assignment_id UUID PRIMARY KEY,
  business_id         UUID NOT NULL REFERENCES identity.business(business_id),
  status              payroll.attendance_exception_status NOT NULL,
  late_level          payroll.late_level,
  payable_hours       payroll.d_hours,
  note                TEXT,
  set_by              UUID NOT NULL,
  set_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (shift_assignment_id, business_id)
    REFERENCES payroll.shift_assignment(shift_assignment_id, business_id) ON DELETE CASCADE,
  FOREIGN KEY (set_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  CHECK ((status = 'LATE') = (late_level IS NOT NULL))
);

ALTER TABLE payroll.timesheet_entry
  ADD CONSTRAINT fk_timesheet_entry_attendance_sheet
  FOREIGN KEY (attendance_sheet_id, business_id)
  REFERENCES payroll.attendance_sheet(attendance_sheet_id, business_id);

SELECT shared.fn_apply_tenant_rls('payroll', 'attendance_sheet');
SELECT shared.fn_apply_tenant_rls('payroll', 'attendance_exception');
