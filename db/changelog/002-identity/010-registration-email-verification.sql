-- Legacy registrations have no proof of email ownership; do not backfill it.
ALTER TABLE identity.business_registration ADD COLUMN owner_email_verified_at TIMESTAMPTZ;
