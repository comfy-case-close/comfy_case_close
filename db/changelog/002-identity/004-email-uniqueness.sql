-- Match case-insensitive authentication without changing tenant membership.
-- Existing UNIQUE (business_id, email) is case-sensitive and allows case variants.
-- Fail on existing duplicates so they can be resolved explicitly; never merge or
-- delete staff automatically. NULL remains allowed for staff without email login.
CREATE UNIQUE INDEX uq_staff_business_email_ci
  ON identity.staff (business_id, lower(email));
