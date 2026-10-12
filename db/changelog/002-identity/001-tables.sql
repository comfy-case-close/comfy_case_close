-- ============================================================================
-- 002 IDENTITY (CORE) - who is who, at which branch, in what role
--
-- The highest fan-in in the system: staff is read by 7 of 9 services, and it
-- changes very slowly. That is exactly why it is NOT merged into workforce
-- (ADR-0003 section 14.3) - a fast-moving domain must not drag a slow, widely
-- read one along with it.
--
-- Multi-tenancy: EVERY table here carries business_id so RLS has something to
-- filter on. `business` is the one exception - there business_id IS the key.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- business - one brand / customer
-- ----------------------------------------------------------------------------
CREATE TABLE identity.business (
  business_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_code  TEXT NOT NULL UNIQUE CHECK (business_code = upper(business_code)),
  business_name  TEXT NOT NULL,
  business_type  shared.business_type NOT NULL DEFAULT 'CAFE',
  currency_code  CHAR(3) NOT NULL DEFAULT 'VND',
  timezone       TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
  is_active      BOOLEAN NOT NULL DEFAULT true,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_business_touch BEFORE UPDATE ON identity.business
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- ----------------------------------------------------------------------------
-- branch
-- ----------------------------------------------------------------------------
CREATE TABLE identity.branch (
  branch_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id              UUID NOT NULL REFERENCES identity.business(business_id),
  branch_code              TEXT NOT NULL,
  branch_name              TEXT NOT NULL,
  address                  TEXT,
  target_cash_remaining    shared.d_money_nonneg NOT NULL DEFAULT 0,
  cash_remaining_tolerance shared.d_money_nonneg NOT NULL DEFAULT 0,
  is_active                BOOLEAN NOT NULL DEFAULT true,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (business_id, branch_code),
  -- Target for composite FKs: forces every child row to sit in the same business.
  -- This is the mechanism that makes the denormalised business_id on child tables
  -- safe rather than a hope.
  UNIQUE (branch_id, business_id)
);
CREATE INDEX idx_branch_business ON identity.branch (business_id, branch_id);
CREATE TRIGGER trg_branch_touch BEFORE UPDATE ON identity.branch
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- ----------------------------------------------------------------------------
-- staff_position - job title, kept separate from the application role.
-- The title is printed on the badge; the role decides which buttons work.
-- ----------------------------------------------------------------------------
CREATE TABLE identity.staff_position (
  position_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id   UUID NOT NULL REFERENCES identity.business(business_id),
  position_code TEXT NOT NULL,
  position_name TEXT NOT NULL,
  is_active     BOOLEAN NOT NULL DEFAULT true,
  UNIQUE (business_id, position_code),
  UNIQUE (position_id, business_id)
);

-- ----------------------------------------------------------------------------
-- staff - a person who works here.
--
-- Named `staff`, not `user`: `user` is a RESERVED keyword in PostgreSQL and would
-- have to be double-quoted in every statement, every view and every native query,
-- with one forgotten quote failing only at runtime. `staff` is also the more
-- honest name - the row carries employee_code, full_name and position_id.
--
-- ADR-0003 decision 24: only data everyone may read lives here. Contracts,
-- salary, ID documents and insurance belong in a separate `payroll` schema with
-- its own DB role, created when payroll is actually stored. Split by sensitivity,
-- not by subject - do NOT add a salary column to this table.
-- ----------------------------------------------------------------------------
CREATE TABLE identity.staff (
  staff_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id   UUID NOT NULL REFERENCES identity.business(business_id),
  employee_code TEXT NOT NULL,
  full_name     TEXT NOT NULL,
  email         TEXT,
  phone         TEXT,
  position_id   UUID,

  -- SECURITY WARNING: a 4-digit passcode hashed with unsalted SHA-256 is
  -- inherited from the old spreadsheet. A 10,000-value search space breaks in
  -- seconds. Production MUST use argon2id or bcrypt; passcode_algo exists so
  -- users migrate one at a time on next login instead of a forced global reset.
  passcode_hash TEXT NOT NULL,
  passcode_algo TEXT NOT NULL DEFAULT 'sha256-legacy'
                CHECK (passcode_algo IN ('sha256-legacy','bcrypt','argon2id')),

  -- Avatar: files owns the bytes, identity keeps the reference plus an immutable
  -- snapshot. Safe because a file never changes once uploaded. The day
  -- file-service is extracted, dropping the constraint is the whole migration.
  avatar_file_id UUID,
  avatar_url     TEXT,
  avatar_sha256  TEXT,

  note          TEXT,
  is_active     BOOLEAN NOT NULL DEFAULT true,
  last_login_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  UNIQUE (business_id, employee_code),
  UNIQUE (business_id, email),
  UNIQUE (staff_id, business_id),
  FOREIGN KEY (position_id, business_id)
    REFERENCES identity.staff_position(position_id, business_id)
);
CREATE INDEX idx_staff_business ON identity.staff (business_id, staff_id);
CREATE INDEX idx_staff_position ON identity.staff (position_id);
CREATE TRIGGER trg_staff_touch BEFORE UPDATE ON identity.staff
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- ----------------------------------------------------------------------------
-- staff_branch_role - replaces the CSV `branchIds` column of the old spreadsheet,
-- which violated 1NF and turned "who may approve closes at branch X" into string
-- matching instead of a join. Revoking sets revoked_at rather than deleting: an
-- investigation three months later needs to know who USED TO have access.
-- ----------------------------------------------------------------------------
CREATE TABLE identity.staff_branch_role (
  staff_id    UUID NOT NULL,
  branch_id   UUID NOT NULL,
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  role        shared.user_role NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at  TIMESTAMPTZ,
  PRIMARY KEY (staff_id, branch_id),
  CHECK (revoked_at IS NULL OR revoked_at >= assigned_at),
  FOREIGN KEY (staff_id,  business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id)
);
CREATE INDEX idx_sbr_branch ON identity.staff_branch_role (business_id, branch_id);
CREATE INDEX idx_sbr_live   ON identity.staff_branch_role (staff_id) WHERE revoked_at IS NULL;

-- ----------------------------------------------------------------------------
-- staff_session - login sessions.
--
-- Not for keeping users logged in; the JWT does that. This exists to answer two
-- questions the JWT cannot: revoke access the moment someone quits (their token
-- is valid until tomorrow morning), and "which device and IP submitted the
-- 15 Aug close" when one person is suspected of submitting for another.
-- ----------------------------------------------------------------------------
CREATE TABLE identity.staff_session (
  session_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  staff_id    UUID NOT NULL,
  business_id UUID NOT NULL REFERENCES identity.business(business_id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  ip_address  INET,
  user_agent  TEXT,
  CHECK (expires_at > created_at),
  FOREIGN KEY (staff_id, business_id) REFERENCES identity.staff(staff_id, business_id)
);
CREATE INDEX idx_session_staff ON identity.staff_session (business_id, staff_id);
CREATE INDEX idx_session_live  ON identity.staff_session (staff_id) WHERE revoked_at IS NULL;

-- ----------------------------------------------------------------------------
-- shift_type - a pub shift (18:00-02:00) looks nothing like a cafe shift
-- (06:00-14:00), so shifts belong to a tenant rather than being global constants.
-- submit_deadline is what decides whether a close is flagged late.
-- ----------------------------------------------------------------------------
CREATE TABLE identity.shift_type (
  shift_type_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id          UUID NOT NULL REFERENCES identity.business(business_id),
  shift_code           TEXT NOT NULL,
  shift_name           TEXT NOT NULL,
  sort_order           INT  NOT NULL DEFAULT 0,
  suggested_start_time TIME,
  suggested_end_time   TIME,
  submit_deadline      TIME NOT NULL,
  is_active            BOOLEAN NOT NULL DEFAULT true,
  UNIQUE (business_id, shift_code),
  UNIQUE (shift_type_id, business_id)
);
