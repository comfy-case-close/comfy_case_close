-- Payroll is appended after the original 900-rls changeset has already run.
-- Apply the same tenant policy to its initial tables without changing that
-- applied changeset's checksum. Later payroll tables apply RLS themselves.
DO $do$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname AS tbl
    FROM pg_class c
    JOIN pg_attribute a ON a.attrelid = c.oid
      AND a.attname = 'business_id' AND a.attnum > 0 AND NOT a.attisdropped
    WHERE c.relkind = 'r' AND c.relnamespace = 'payroll'::regnamespace
  LOOP
    PERFORM shared.fn_apply_tenant_rls('payroll', r.tbl);
  END LOOP;
END $do$;
