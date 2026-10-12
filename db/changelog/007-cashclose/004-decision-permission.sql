ALTER TABLE cashclose.cash_close_decision ADD COLUMN acted_permission TEXT
 REFERENCES identity.permission(permission_code);
-- acted_role and its historical CHECK remain unchanged and nullable.
DROP TRIGGER trg_close_decision_role ON cashclose.cash_close_decision;
DROP FUNCTION cashclose.fn_require_decision_role();

-- During the expansion phase either application version can record its authority.
CREATE FUNCTION cashclose.fn_require_decision_authority() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.acted_permission IS NULL AND NEW.acted_role IS NULL THEN
  RAISE EXCEPTION 'New close decisions require the authorizing permission or legacy role';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER trg_close_decision_authority
 BEFORE INSERT ON cashclose.cash_close_decision
 FOR EACH ROW EXECUTE FUNCTION cashclose.fn_require_decision_authority();
