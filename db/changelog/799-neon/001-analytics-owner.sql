-- The analytics changeset transfers a SECURITY DEFINER function and two
-- materialized views to analytics_refresher. PostgreSQL requires a non-superuser
-- migration role to be able to SET ROLE to the new owner, and the new owner to
-- have CREATE on the target schema. Local Docker uses a superuser and needs no
-- extra grant; Neon's database owner needs both.
DO $do$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'neon_superuser') THEN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'analytics_refresher') THEN
      CREATE ROLE analytics_refresher NOLOGIN BYPASSRLS;
    END IF;

    GRANT CREATE ON SCHEMA analytics TO analytics_refresher;
    EXECUTE format('GRANT analytics_refresher TO %I WITH SET TRUE', current_user);
  END IF;
END $do$;
