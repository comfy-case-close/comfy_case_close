-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.8-shift-scheduling.xml
-- Registration windows opened by a button and the busy shifts each employee reports; every other shift is free.

CREATE TYPE payroll.registration_status AS ENUM ('NOT_OPEN', 'OPEN', 'CLOSED');
CREATE TYPE payroll.submission_status   AS ENUM ('DRAFT', 'SUBMITTED');

CREATE TABLE payroll.registration_window (
  registration_window_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id            UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id              UUID NOT NULL,
  week_start             DATE NOT NULL CHECK (EXTRACT(ISODOW FROM week_start) = 1),
  status                 payroll.registration_status NOT NULL DEFAULT 'NOT_OPEN',
  opened_by              UUID,
  opened_at              TIMESTAMPTZ,
  closed_by              UUID,
  closed_at              TIMESTAMPTZ,
  UNIQUE (branch_id, week_start),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (opened_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (closed_by, business_id) REFERENCES identity.staff(staff_id, business_id)
);

CREATE TABLE payroll.availability_submission (
  availability_submission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id                UUID NOT NULL REFERENCES identity.business(business_id),
  staff_id                   UUID NOT NULL,
  week_start                 DATE NOT NULL CHECK (EXTRACT(ISODOW FROM week_start) = 1),
  status                     payroll.submission_status NOT NULL DEFAULT 'DRAFT',
  submitted_at               TIMESTAMPTZ,
  entered_by                 UUID,
  note                       TEXT,
  version                    BIGINT NOT NULL DEFAULT 0,
  UNIQUE (staff_id, week_start),
  UNIQUE (availability_submission_id, business_id),
  FOREIGN KEY (staff_id, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (entered_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  CHECK ((status = 'SUBMITTED') = (submitted_at IS NOT NULL))
);

CREATE TABLE payroll.busy_shift (
  availability_submission_id UUID NOT NULL,
  business_id                UUID NOT NULL REFERENCES identity.business(business_id),
  work_date                  DATE NOT NULL,
  shift_slot_id              UUID NOT NULL,
  PRIMARY KEY (availability_submission_id, work_date, shift_slot_id),
  FOREIGN KEY (availability_submission_id, business_id)
    REFERENCES payroll.availability_submission(availability_submission_id, business_id) ON DELETE CASCADE,
  FOREIGN KEY (shift_slot_id, business_id) REFERENCES payroll.shift_slot(shift_slot_id, business_id)
);

SELECT shared.fn_apply_tenant_rls('payroll', 'registration_window');
SELECT shared.fn_apply_tenant_rls('payroll', 'availability_submission');
SELECT shared.fn_apply_tenant_rls('payroll', 'busy_shift');
