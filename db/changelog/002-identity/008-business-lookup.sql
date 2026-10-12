-- ============================================================================
-- fn_find_business_by_code - the one deliberate hole in tenant isolation.
--
-- business_id is a UUID, and LoginRequest, StartSignUpRequest and SignUpRequest
-- all require it. No human can be expected to know it, so a staff member needs
-- some way to turn "COMFY" into the tenant they are logging into.
--
-- Every other query in the system runs with a tenant context already chosen, so
-- RLS has something to filter on. This one cannot: resolving the code IS how the
-- tenant gets chosen. shared.current_business_id() returns NULL, the policy
-- evaluates to NULL rather than TRUE, and a plain SELECT returns zero rows.
--
-- SECURITY DEFINER runs the body as the function's owner - the migration role,
-- which holds BYPASSRLS or superuser (005-staff-names already requires this) -
-- so the lookup can see across tenants. What keeps that narrow:
--   * it returns TWO columns, never the row: no type, currency, timezone, no
--     is_active flag beyond the fact that inactive businesses return nothing;
--   * it matches an EXACT uppercase code, so it confirms a guess and cannot be
--     used to enumerate or browse tenants;
--   * the body is static SQL - the parameter is only ever compared, never
--     concatenated into executed text;
--   * search_path is pinned, so the owner's privileges cannot be redirected at
--     a shadowed function or table;
--   * EXECUTE is revoked from PUBLIC (which holds it by default on every new
--     function) and granted to svc_identity alone.
--
-- Why not a dedicated BYPASSRLS role, the way 800-analytics owns fn_refresh_all
-- with analytics_refresher? Because rls-guard 9.2 asserts that analytics_refresher
-- is the ONLY non-superuser role holding BYPASSRLS, and a guard that gets edited
-- every time new code finds it inconvenient stops being a guard. The migration
-- role is already privileged, already required to be by 005, and is excluded from
-- 9.2 as a superuser - so this adds no role and moves no fence.
--
-- Business codes are guessable by design - that is what makes them useful on a
-- login screen. Treat the mapping code -> business_id as public information; it
-- authorises nothing on its own, exactly as the existing LoginRequest contract
-- already assumes ("selecting the namespace grants no authorization").
-- ============================================================================

-- The function's owner must be able to see all tenants, or it silently returns
-- nothing and every login screen breaks in a way no test would notice. Fail loudly
-- at migration time instead, as 005-staff-names does.
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles
                 WHERE rolname = current_user AND (rolsuper OR rolbypassrls)) THEN
    RAISE EXCEPTION
      'fn_find_business_by_code must be owned by a migration role with BYPASSRLS or superuser';
  END IF;
END $do$;

CREATE OR REPLACE FUNCTION shared.fn_find_business_by_code(p_code TEXT)
RETURNS TABLE (business_id UUID, business_name TEXT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $fn$
  SELECT b.business_id, b.business_name
  FROM identity.business b
  WHERE b.business_code = upper(btrim(p_code))
    AND b.is_active
$fn$;

REVOKE ALL ON FUNCTION shared.fn_find_business_by_code(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION shared.fn_find_business_by_code(TEXT) TO svc_identity;
