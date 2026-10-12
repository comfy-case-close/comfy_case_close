-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.8-shift-scheduling.xml
-- Weekly schedule, the one-branch-per-day guard, assignments, their change log and auto-fill runs.

CREATE TYPE payroll.schedule_status        AS ENUM ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'LOCKED');
CREATE TYPE payroll.assignment_source      AS ENUM ('AUTO', 'MANUAL');
CREATE TYPE payroll.assignment_event_type  AS ENUM ('ADDED', 'REPLACED', 'REMOVED', 'TIME_CHANGED');

CREATE TABLE payroll.shift_schedule (
  shift_schedule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id       UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id         UUID NOT NULL,
  week_start        DATE NOT NULL CHECK (EXTRACT(ISODOW FROM week_start) = 1),
  status            payroll.schedule_status NOT NULL DEFAULT 'DRAFT',
  submitted_by      UUID,
  submitted_at      TIMESTAMPTZ,
  submit_note       TEXT,
  approved_by       UUID,
  approved_at       TIMESTAMPTZ,
  return_reason     TEXT,
  version           BIGINT NOT NULL DEFAULT 0,
  UNIQUE (branch_id, week_start),
  UNIQUE (shift_schedule_id, business_id),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (submitted_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (approved_by, business_id) REFERENCES identity.staff(staff_id, business_id)
);

CREATE TABLE payroll.schedule_generation_run (
  schedule_generation_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id                UUID NOT NULL REFERENCES identity.business(business_id),
  shift_schedule_id          UUID NOT NULL,
  params                     JSONB NOT NULL,
  status                     payroll.run_status NOT NULL DEFAULT 'RUNNING',
  summary                    JSONB,
  created_by                 UUID NOT NULL,
  started_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at                TIMESTAMPTZ,
  undone_at                  TIMESTAMPTZ,
  UNIQUE (schedule_generation_run_id, business_id),
  FOREIGN KEY (shift_schedule_id, business_id)
    REFERENCES payroll.shift_schedule(shift_schedule_id, business_id),
  FOREIGN KEY (created_by, business_id) REFERENCES identity.staff(staff_id, business_id)
);

CREATE TABLE payroll.staff_day_branch (
  staff_id    UUID NOT NULL,
  work_date   DATE NOT NULL,
  branch_id   UUID NOT NULL,
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  PRIMARY KEY (staff_id, work_date),
  UNIQUE (staff_id, work_date, branch_id),
  FOREIGN KEY (staff_id, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id)
);

CREATE TABLE payroll.shift_assignment (
  shift_assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id         UUID NOT NULL REFERENCES identity.business(business_id),
  shift_schedule_id   UUID NOT NULL,
  branch_id           UUID NOT NULL,
  staff_id            UUID NOT NULL,
  work_date           DATE NOT NULL,
  shift_slot_id       UUID NOT NULL,
  position_id         UUID NOT NULL,
  start_time          TIME NOT NULL,
  end_time            TIME NOT NULL,
  source              payroll.assignment_source NOT NULL,
  generation_run_id   UUID,
  note                TEXT,
  version             BIGINT NOT NULL DEFAULT 0,
  UNIQUE (shift_assignment_id, business_id),
  CONSTRAINT uq_shift_assignment_slot UNIQUE (shift_schedule_id, staff_id, work_date, shift_slot_id),
  FOREIGN KEY (shift_schedule_id, business_id)
    REFERENCES payroll.shift_schedule(shift_schedule_id, business_id),
  FOREIGN KEY (staff_id, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (shift_slot_id, business_id) REFERENCES payroll.shift_slot(shift_slot_id, business_id),
  FOREIGN KEY (position_id, business_id) REFERENCES payroll.position_profile(position_id, business_id),
  FOREIGN KEY (generation_run_id, business_id)
    REFERENCES payroll.schedule_generation_run(schedule_generation_run_id, business_id),
  CONSTRAINT fk_shift_assignment_staff_day_branch FOREIGN KEY (staff_id, work_date, branch_id)
    REFERENCES payroll.staff_day_branch(staff_id, work_date, branch_id),
  CHECK (end_time > start_time),
  CONSTRAINT ex_shift_assignment_no_overlap EXCLUDE USING gist (
    staff_id WITH =,
    tsrange(work_date + start_time, work_date + end_time) WITH &&
  )
);
CREATE INDEX idx_shift_assignment_schedule ON payroll.shift_assignment (shift_schedule_id);
CREATE INDEX idx_shift_assignment_staff_day ON payroll.shift_assignment (staff_id, work_date);
CREATE INDEX idx_shift_assignment_branch_day ON payroll.shift_assignment (business_id, branch_id, work_date);

CREATE FUNCTION payroll.fn_claim_staff_day_branch() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  INSERT INTO payroll.staff_day_branch (staff_id, work_date, branch_id, business_id)
  VALUES (NEW.staff_id, NEW.work_date, NEW.branch_id, NEW.business_id)
  ON CONFLICT (staff_id, work_date) DO NOTHING;
  RETURN NEW;
END $fn$;

CREATE FUNCTION payroll.fn_release_staff_day_branch() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  DELETE FROM payroll.staff_day_branch d
   WHERE d.staff_id = OLD.staff_id AND d.work_date = OLD.work_date
     AND NOT EXISTS (SELECT 1 FROM payroll.shift_assignment a
                      WHERE a.staff_id = OLD.staff_id AND a.work_date = OLD.work_date);
  RETURN NULL;
END $fn$;

CREATE TRIGGER trg_shift_assignment_claim
  BEFORE INSERT OR UPDATE OF staff_id, work_date, branch_id ON payroll.shift_assignment
  FOR EACH ROW EXECUTE FUNCTION payroll.fn_claim_staff_day_branch();
CREATE TRIGGER trg_shift_assignment_release
  AFTER DELETE OR UPDATE OF staff_id, work_date, branch_id ON payroll.shift_assignment
  FOR EACH ROW EXECUTE FUNCTION payroll.fn_release_staff_day_branch();

CREATE TABLE payroll.shift_assignment_event (
  shift_assignment_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id               UUID NOT NULL REFERENCES identity.business(business_id),
  shift_schedule_id         UUID NOT NULL,
  shift_assignment_id       UUID NOT NULL,
  staff_id                  UUID NOT NULL,
  work_date                 DATE NOT NULL,
  event_type                payroll.assignment_event_type NOT NULL,
  actor_id                  UUID NOT NULL,
  before_value              JSONB,
  after_value               JSONB,
  reason                    TEXT,
  occurred_at               TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  FOREIGN KEY (shift_schedule_id, business_id)
    REFERENCES payroll.shift_schedule(shift_schedule_id, business_id),
  FOREIGN KEY (actor_id, business_id) REFERENCES identity.staff(staff_id, business_id)
);
CREATE INDEX idx_shift_assignment_event_schedule
  ON payroll.shift_assignment_event (shift_schedule_id, occurred_at);
CREATE TRIGGER trg_shift_assignment_event_append_only
  BEFORE UPDATE OR DELETE ON payroll.shift_assignment_event
  FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();
REVOKE UPDATE, DELETE ON payroll.shift_assignment_event FROM svc_hrm;

SELECT shared.fn_apply_tenant_rls('payroll', 'shift_schedule');
SELECT shared.fn_apply_tenant_rls('payroll', 'schedule_generation_run');
SELECT shared.fn_apply_tenant_rls('payroll', 'staff_day_branch');
SELECT shared.fn_apply_tenant_rls('payroll', 'shift_assignment');
SELECT shared.fn_apply_tenant_rls('payroll', 'shift_assignment_event');
