-- Branch access is inherited only from active staff positions at that branch.
-- Refuse to discard direct grants, including historical rows, on an upgrade.
DO $guard$
BEGIN
  IF EXISTS (SELECT 1 FROM identity.staff_branch_permission) THEN
    RAISE EXCEPTION 'staff_branch_permission contains grants; migrate or review them before retiring direct branch grants';
  END IF;
END $guard$;

DROP TABLE identity.staff_branch_permission;
