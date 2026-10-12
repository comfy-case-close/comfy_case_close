-- Approved hardening: invalidate renewal exactly, even within the same JWT iat second.
-- Access-token validation remains stateless and never reads this value.
ALTER TABLE identity.staff
  ADD COLUMN refresh_version BIGINT NOT NULL DEFAULT 0 CHECK (refresh_version >= 0);
