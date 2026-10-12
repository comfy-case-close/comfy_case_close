-- This is the signed branch role actually used by the application's authorization
-- check, not a join to the user's mutable current role. Legacy decisions stay NULL:
-- a current assignment is insufficient evidence of the token used in the past.
ALTER TABLE cashclose.cash_close_decision ADD COLUMN acted_role TEXT;
ALTER TABLE cashclose.cash_close_decision ADD CONSTRAINT ck_close_decision_role
  CHECK (acted_role IN ('ADMIN', 'HR', 'MANAGER', 'ACCOUNTANT', 'SHIFT_LEAD', 'STAFF'));
ALTER TABLE cashclose.cash_close_decision ALTER COLUMN acted_at SET DEFAULT clock_timestamp();
CREATE FUNCTION cashclose.fn_require_decision_role() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF NEW.acted_role IS NULL THEN
    RAISE EXCEPTION 'New close decisions require the authorizing branch role';
  END IF;
  RETURN NEW;
END $fn$;
CREATE TRIGGER trg_close_decision_role BEFORE INSERT ON cashclose.cash_close_decision
  FOR EACH ROW EXECUTE FUNCTION cashclose.fn_require_decision_role();
