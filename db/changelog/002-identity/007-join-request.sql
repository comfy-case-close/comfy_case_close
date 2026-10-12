-- ============================================================================
-- staff_join_request - an application to join an existing business.
--
-- Replaces the old behaviour where POST /auth/signup with a null business_id
-- silently created a whole tenant. A person who is not yet staff now files a
-- request here; an ADMIN or HR of that business turns it into an
-- identity.staff row by approving it, choosing the branch and role in the same
-- act. See docs/security/onboarding.md.
-- ============================================================================

CREATE TYPE shared.join_request_status AS ENUM ('PENDING','APPROVED','REJECTED');

CREATE TABLE identity.staff_join_request (
  join_request_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id      UUID NOT NULL REFERENCES identity.business(business_id),

  email            TEXT NOT NULL,
  first_name       TEXT NOT NULL,
  last_name        TEXT NOT NULL,
  phone            TEXT,

  -- The applicant already chose a password during signup; carrying its hash here
  -- is what lets approval create a usable account without a second setup email.
  -- It is a BCrypt hash, it is nulled the moment the request is decided (see the
  -- CHECK below), and no role other than svc_identity can read this table at all.
  -- NULL from the start for a Google-originated request, which has no password.
  passcode_hash    TEXT,
  passcode_algo    TEXT NOT NULL DEFAULT 'bcrypt'
                   CHECK (passcode_algo IN ('bcrypt','argon2id')),
  auth_provider    TEXT NOT NULL DEFAULT 'LOCAL'
                   CHECK (auth_provider IN ('LOCAL','GOOGLE')),
  avatar_url       TEXT,

  status           shared.join_request_status NOT NULL DEFAULT 'PENDING',
  requested_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

  decided_by       UUID,
  decided_at       TIMESTAMPTZ,
  decision_note    TEXT,
  -- Set on approval: which staff row this request produced. Keeps the audit trail
  -- answerable in both directions three months later.
  created_staff_id UUID,

  -- A decided request must say who decided and when; a pending one must not.
  CHECK ((status = 'PENDING') = (decided_at IS NULL)),
  CHECK ((status = 'PENDING') = (decided_by IS NULL)),
  CHECK (decided_at IS NULL OR decided_at >= requested_at),
  -- Rejection needs a reason. A silent rejection makes a dispute unresolvable,
  -- the same rule the cash-close ledger applies to a rejected movement.
  CHECK (status <> 'REJECTED' OR nullif(btrim(decision_note), '') IS NOT NULL),
  CHECK (status <> 'APPROVED' OR created_staff_id IS NOT NULL),
  -- The credential exists only while the request is live.
  CHECK (status = 'PENDING' OR passcode_hash IS NULL),
  CHECK (status <> 'PENDING' OR auth_provider = 'GOOGLE' OR passcode_hash IS NOT NULL),

  FOREIGN KEY (decided_by,       business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (created_staff_id, business_id) REFERENCES identity.staff(staff_id, business_id)
);

-- Exactly one live application per address per business. Case-insensitive, to match
-- uq_staff_business_email_ci and the case-insensitive login lookup. Decided rows are
-- excluded so the same person can reapply after a rejection.
CREATE UNIQUE INDEX uq_join_request_pending
  ON identity.staff_join_request (business_id, lower(email))
  WHERE status = 'PENDING';

-- The reviewer's queue: pending first, oldest first.
CREATE INDEX idx_join_request_queue
  ON identity.staff_join_request (business_id, status, requested_at);

CREATE TRIGGER trg_join_request_touch BEFORE UPDATE ON identity.staff_join_request
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- ----------------------------------------------------------------------------
-- RLS - applied EXPLICITLY, not by the 900-rls discovery loop.
--
-- That loop finds every table carrying a business_id, and its changeset is
-- runOnChange="true" - but appending a changeset to the master file does not
-- change THAT file, so the loop does not re-run. A table added afterwards would
-- ship with no tenant isolation whatsoever. Every future identity table must do
-- what the next line does.
-- ----------------------------------------------------------------------------
SELECT shared.fn_apply_tenant_rls('identity', 'staff_join_request');

-- ----------------------------------------------------------------------------
-- Grants. identity.* is readable by six other service roles through the
-- read-only grants in 910-roles, and ALTER DEFAULT PRIVILEGES would extend that
-- to this table automatically. It must not: this table holds password hashes
-- until the request is decided. Same treatment as identity.revoked_token.
-- ----------------------------------------------------------------------------
REVOKE ALL ON identity.staff_join_request FROM PUBLIC,
  svc_platform, svc_files, svc_notify, svc_integration, svc_cashclose,
  svc_workforce, svc_inventory, svc_reporting;
GRANT SELECT, INSERT, UPDATE, DELETE ON identity.staff_join_request TO svc_identity;
