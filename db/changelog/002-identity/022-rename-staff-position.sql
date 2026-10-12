-- Keep existing rows, foreign keys, indexes, triggers, and grants while using
-- the shorter table name for all new application queries.
ALTER TABLE identity.staff_position RENAME TO position;
