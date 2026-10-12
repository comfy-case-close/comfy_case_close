-- ============================================================================
-- business_registration - a stranger asking for a tenant to be created.
--
-- The public front door. Anyone may file one of these; nobody may create a
-- business by filing one. A platform administrator reads the queue and either
-- approves it - which is the act that writes identity.business, its first branch
-- and its owner - or rejects it with a reason. Both decisions are emailed to the
-- address on the request. See docs/security/onboarding.md.
--
-- WHY THIS TABLE HAS NO business_id, AND THEREFORE NO RLS
-- Every other table in this schema carries business_id so row-level security has
-- something to filter on. This one cannot: it exists precisely because the
-- business does not exist yet, and a row here belongs to no tenant. The 900-rls
-- discovery loop looks for a business_id column and so skips it, which is the
-- correct outcome rather than an oversight - and rls-guard 9.1 stays green for
-- the same reason.
--
-- What replaces tenant isolation here is the grant below: svc_identity alone can
-- read this table, and the only routes that expose it are behind the platform
-- key. Treat any future column here as visible to every platform administrator.
-- ============================================================================

CREATE TYPE shared.registration_status AS ENUM ('PENDING','APPROVED','REJECTED');

CREATE TABLE identity.business_registration (
  registration_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- The business as applied for. Deliberately mirrors identity.business rather
  -- than inventing a shape: the day this needs a citizen ID or a business
  -- licence, those columns are added here and nowhere else.
  business_code       TEXT NOT NULL CHECK (business_code = upper(business_code)),
  business_name       TEXT NOT NULL,
  business_type       shared.business_type NOT NULL DEFAULT 'CAFE',
  currency_code       CHAR(3) NOT NULL DEFAULT 'VND',
  timezone            TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',

  -- The first branch, applied for at the same time. A business with no branch is
  -- a business nobody can log into, so the two are never separate applications.
  branch_code         TEXT NOT NULL DEFAULT 'MAIN' CHECK (branch_code = upper(branch_code)),
  branch_name         TEXT NOT NULL,
  branch_address      TEXT,

  -- The person who will hold ADMIN. No password: the address is unverified at
  -- this point, and storing a credential for an address nobody has proved
  -- ownership of buys nothing - approval provisions the account unverified and
  -- the owner sets their own password by activating it.
  owner_email         TEXT NOT NULL,
  owner_first_name    TEXT NOT NULL,
  owner_last_name     TEXT NOT NULL,
  owner_phone         TEXT,

  status              shared.registration_status NOT NULL DEFAULT 'PENDING',
  submitted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  decided_at          TIMESTAMPTZ,
  decision_note       TEXT,

  -- What the approval produced, so the trail is answerable in both directions.
  created_business_id UUID REFERENCES identity.business(business_id),
  created_staff_id    UUID,

  -- When the decision letter actually went out. NULL on a decided row means the
  -- applicant was never told - a visible failure rather than a silent one.
  notified_at         TIMESTAMPTZ,

  -- There is no decided_by. The platform administrator authenticates with a
  -- shared key, not a staff account, so there is no identity to record. When
  -- platform accounts replace the key (onboarding.md section 8.2), this is the
  -- column that gets added.
  CHECK ((status = 'PENDING') = (decided_at IS NULL)),
  CHECK (decided_at IS NULL OR decided_at >= submitted_at),
  -- A rejection without a reason cannot be explained to the applicant, and the
  -- letter has nothing to say. Same rule the cash-close ledger applies.
  CHECK (status <> 'REJECTED' OR nullif(btrim(decision_note), '') IS NOT NULL),
  CHECK (status <> 'APPROVED' OR (created_business_id IS NOT NULL AND created_staff_id IS NOT NULL)),
  CHECK (status = 'APPROVED' OR (created_business_id IS NULL AND created_staff_id IS NULL)),
  FOREIGN KEY (created_staff_id, created_business_id)
    REFERENCES identity.staff(staff_id, business_id)
);

-- One live application per business code and per owner address. Partial, so a
-- rejected applicant may reapply and a code freed by a rejection is available
-- again. Case-insensitive on the email to match how staff emails are compared.
CREATE UNIQUE INDEX uq_registration_pending_code
  ON identity.business_registration (business_code) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_registration_pending_email
  ON identity.business_registration (lower(owner_email)) WHERE status = 'PENDING';

-- The reviewer's queue: pending first, oldest first.
CREATE INDEX idx_registration_queue
  ON identity.business_registration (status, submitted_at);

CREATE TRIGGER trg_business_registration_touch BEFORE UPDATE ON identity.business_registration
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- ----------------------------------------------------------------------------
-- Grants. identity.* is readable by six other service roles through the
-- read-only grants in 910-roles, and ALTER DEFAULT PRIVILEGES would extend that
-- to this table automatically. It must not: these rows are unreviewed input from
-- the public internet, carrying a named individual's email and phone number, and
-- no other service has any business reading them.
-- ----------------------------------------------------------------------------
REVOKE ALL ON identity.business_registration FROM PUBLIC,
  svc_platform, svc_files, svc_notify, svc_integration, svc_cashclose,
  svc_workforce, svc_inventory, svc_reporting;
GRANT SELECT, INSERT, UPDATE, DELETE ON identity.business_registration TO svc_identity;
