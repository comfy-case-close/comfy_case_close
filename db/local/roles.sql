-- LOCAL DEVELOPMENT ONLY - applied by the `local-roles` service in db/docker-compose.yml.
--
-- The changelog creates every svc_* role NOLOGIN. Here they get a password so the services can
-- connect on a developer machine. `changeme` is the default of DB_PASSWORD in every service's
-- application.yml. Never run this against a shared or production database.
DO $do$
DECLARE r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['svc_identity', 'svc_platform', 'svc_files', 'svc_notify', 'svc_integration',
                           'svc_cashclose', 'svc_workforce', 'svc_inventory', 'svc_hrm', 'svc_reporting'] LOOP
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', r, 'changeme');
    END IF;
  END LOOP;
END $do$;
