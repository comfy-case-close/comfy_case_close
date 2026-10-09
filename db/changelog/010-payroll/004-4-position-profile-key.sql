-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.4-employee-profile-and-contract-fields.xml
-- Composite key so tenant-scoped foreign keys can point at a position profile.

ALTER TABLE payroll.position_profile ADD CONSTRAINT uq_position_profile_business UNIQUE (position_id, business_id);
