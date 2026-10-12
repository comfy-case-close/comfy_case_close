-- Ported from feat/payroll (af2926c): db/changelog/identity/1.0.0.25-staff-nickname.xml
-- Display name used on shift grids; unique among active staff of a business.

ALTER TABLE identity.staff
  ADD COLUMN nickname TEXT CHECK (nickname IS NULL OR char_length(btrim(nickname)) BETWEEN 1 AND 30);

CREATE UNIQUE INDEX uq_staff_nickname_active
  ON identity.staff (business_id, lower(btrim(nickname)))
  WHERE is_active AND nickname IS NOT NULL;
