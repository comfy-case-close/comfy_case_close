-- Additive migration: preserve staff, branches, roles and existing business data.
ALTER TABLE identity.staff
  ADD COLUMN tokens_valid_from TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01 00:00:00+00',
  ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN auth_provider TEXT NOT NULL DEFAULT 'LOCAL'
    CHECK (auth_provider IN ('LOCAL', 'GOOGLE'));

-- New passwords use Vakot's BCrypt encoder. Existing hashes are not rewritten.
ALTER TABLE identity.staff ALTER COLUMN passcode_algo SET DEFAULT 'bcrypt';

-- A global set of random, signed refresh-token identifiers, as in Vakot.
-- It contains no tenant, account identifier, token string, email or other PII.
-- Only identity can read/write it; cleanup needs no tenant/RLS bypass.
CREATE TABLE identity.revoked_token (
  jti UUID PRIMARY KEY,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_revoked_token_expires_at ON identity.revoked_token (expires_at);

REVOKE ALL ON identity.revoked_token FROM PUBLIC,
  svc_platform, svc_files, svc_notify, svc_integration, svc_cashclose,
  svc_workforce, svc_inventory, svc_reporting;
GRANT SELECT, INSERT, DELETE ON identity.revoked_token TO svc_identity;
REVOKE UPDATE ON identity.revoked_token FROM svc_identity;
