-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.8-shift-scheduling.xml
-- Shift slots, per-period staffing minimums and the scheduling defaults of a branch and of a person.

CREATE TYPE payroll.shift_period AS ENUM ('MORNING', 'EVENING');
CREATE TYPE payroll.shift_day_type AS ENUM ('WEEKDAY', 'WEEKEND');

CREATE TABLE payroll.shift_slot (
  shift_slot_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id     UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id       UUID NOT NULL,
  name            TEXT NOT NULL,
  shift_period    payroll.shift_period NOT NULL,
  employment_type payroll.employment_type NOT NULL,
  day_type        payroll.shift_day_type NOT NULL,
  start_time      TIME NOT NULL,
  end_time        TIME NOT NULL,
  effective_from  DATE NOT NULL,
  effective_to    DATE,
  sort_order      SMALLINT NOT NULL DEFAULT 0,
  is_active       BOOLEAN NOT NULL DEFAULT true,
  UNIQUE (shift_slot_id, business_id),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id),
  CHECK (end_time > start_time),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  EXCLUDE USING gist (
    branch_id WITH =, name WITH =, day_type WITH =,
    daterange(effective_from, coalesce(effective_to, 'infinity'::date), '[]') WITH &&
  )
);
CREATE INDEX idx_shift_slot_branch ON payroll.shift_slot (branch_id, effective_from);

CREATE TABLE payroll.shift_period_requirement (
  shift_period_requirement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id                 UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id                   UUID NOT NULL,
  shift_period                payroll.shift_period NOT NULL,
  position_id                 UUID NOT NULL,
  min_staff                   SMALLINT NOT NULL CHECK (min_staff >= 0),
  effective_from              DATE NOT NULL,
  UNIQUE (branch_id, shift_period, position_id, effective_from),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (position_id, business_id) REFERENCES payroll.position_profile(position_id, business_id)
);

CREATE TABLE payroll.branch_scheduling_setting (
  branch_id           UUID PRIMARY KEY,
  business_id         UUID NOT NULL REFERENCES identity.business(business_id),
  min_rest_hours      SMALLINT NOT NULL DEFAULT 10 CHECK (min_rest_hours >= 0),
  min_hours_part_time NUMERIC(5,2) NOT NULL DEFAULT 20 CHECK (min_hours_part_time >= 0),
  min_hours_full_time NUMERIC(5,2) NOT NULL DEFAULT 48 CHECK (min_hours_full_time >= 0),
  max_hours_week      NUMERIC(5,2) CHECK (max_hours_week > 0),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id)
);

CREATE TABLE payroll.staff_scheduling_profile (
  staff_id       UUID PRIMARY KEY,
  business_id    UUID NOT NULL REFERENCES identity.business(business_id),
  min_hours_week NUMERIC(5,2) CHECK (min_hours_week >= 0),
  max_hours_week NUMERIC(5,2) CHECK (max_hours_week > 0),
  FOREIGN KEY (staff_id, business_id) REFERENCES identity.staff(staff_id, business_id),
  CHECK (max_hours_week IS NULL OR min_hours_week IS NULL OR max_hours_week >= min_hours_week)
);

SELECT shared.fn_apply_tenant_rls('payroll', 'shift_slot');
SELECT shared.fn_apply_tenant_rls('payroll', 'shift_period_requirement');
SELECT shared.fn_apply_tenant_rls('payroll', 'branch_scheduling_setting');
SELECT shared.fn_apply_tenant_rls('payroll', 'staff_scheduling_profile');
