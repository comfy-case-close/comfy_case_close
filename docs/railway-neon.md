# Deploy the backend to Railway with Neon

This repository has one Liquibase changelog and ten independently running Spring
Boot services. A Railway service runs one process; create one Railway service per
Java module. Run the migration service before deploying or restarting the Java
services. The existing `db/docker-compose.yml` and `db/apply.sh` remain for local
PostgreSQL and must not be used to migrate Neon.

## 1. Neon production connection

Use the `production` branch and `neondb` database in the Neon project. Rotate
any password shown in a screenshot. In Neon's **Connect** dialog, select
`neondb_owner` and copy the host twice: with pooling **off** for Liquibase and
with pooling **on** for Java services. Keep passwords out of Git and URLs.

The migration URL is a JDBC URL with the direct hostname (no `-pooler`):

```text
jdbc:postgresql://<direct-host>.aws.neon.tech/neondb?sslmode=require
```

The Java services use the pooled hostname:

```text
jdbc:postgresql://<host>-pooler.aws.neon.tech/neondb?sslmode=require
```

Do not connect a Java service as `neondb_owner`: on this Neon project that role
has `BYPASSRLS`. The application uses its own identity tables and JWTs; Neon's
separate `neon_auth` schema is not used by these services.

## 2. Run the pinned migration image on Railway

Create a private Railway service named `db-migrate` from this GitHub repository.
Set **Root Directory** to `/db`; Railway will build [db/Dockerfile](../db/Dockerfile),
which pins `liquibase/liquibase:4.29.2`. Do not assign a public domain. Set the
restart policy to **Never** because a successful migration process exits.
Clear any custom Start Command so the image entrypoint runs. Add
these service variables in Railway:

| Variable | Value |
| --- | --- |
| `LIQUIBASE_COMMAND_URL` | Direct JDBC URL above, with `sslmode=require` |
| `LIQUIBASE_COMMAND_USERNAME` | `neondb_owner` |
| `LIQUIBASE_COMMAND_PASSWORD` | Rotated Neon owner password (secret) |

The image validates the changelog, applies pending changesets, and reports
status. It refuses pooled URLs and URLs without TLS. Liquibase's changelog lock
prevents concurrent migrations. The repo's `runOnChange` changesets are still
managed by the same master changelog.

For the first production rollout, turn off GitHub autodeploy on the migration
and Java services. Deploy `db-migrate` from the intended commit and confirm that its
Railway deployment exits successfully before deploying Java services. Keep
this ordering for future schema changes; independently auto-deploying every
Railway service from the same push does not guarantee migration-first ordering.

From a trusted workstation, set `PGHOST` to the direct host, `PGPORT=5432`,
`PGDATABASE=neondb`, `PGUSER=neondb_owner`, `PGSSLMODE=require`, and `PGPASSWORD`
from a secret manager, then run `db/rls-guard.sh`. All guard queries must pass.
Do not run `changelog-sync` on a fresh application schema, and do not run
`drop-all` against this Neon database.

## 3. Enable restricted service logins

Liquibase creates the `svc_*` permission roles as `NOLOGIN`. Connect to Neon
directly as `neondb_owner` with `psql` and enable login for the services you
will deploy. For example:

```sql
ALTER ROLE svc_identity LOGIN;
ALTER ROLE svc_platform LOGIN;
ALTER ROLE svc_files LOGIN;
ALTER ROLE svc_notify LOGIN;
ALTER ROLE svc_integration LOGIN;
ALTER ROLE svc_cashclose LOGIN;
ALTER ROLE svc_workforce LOGIN;
ALTER ROLE svc_inventory LOGIN;
ALTER ROLE svc_reporting LOGIN;
```

Neon rejects `psql`'s `\password` command because it sends a pre-hashed
password; Neon currently requires plaintext in `ALTER ROLE`. Exit `psql` and
reconnect with `psql -n` (`--no-readline`) to keep the SQL commands out of the
local psql history. Set a distinct, strong password for each deployed role:

```sql
ALTER ROLE svc_identity PASSWORD '<new-unique-identity-password>';
-- Repeat for each other svc_* role, using a different password each time.
```

Neon receives the password in plaintext for this command. Do not save or
share the SQL containing it, or include it in screenshots. Store each final
password as that Java service's Railway `SPRING_DATASOURCE_PASSWORD` variable.
Check:

```sql
SELECT rolname, rolcanlogin, rolbypassrls
FROM pg_roles WHERE rolname LIKE 'svc_%' ORDER BY rolname;
```

Every deployed service role must show `rolcanlogin=true` and
`rolbypassrls=false`.

## 4. Deploy the Java services

For each module, create a Railway service from the same GitHub repository with
**Root Directory** `/`. Clear any existing custom Start Command so the image
entrypoint runs. The root [Dockerfile](../Dockerfile) builds one module;
set its build variable `SERVICE_MODULE` to one of:

| Module | Suggested Railway service name | `PORT` |
| --- | --- | ---: |
| `identity` | `identity` | 8081 |
| `platform` | `platform` | 8082 |
| `files` | `files` | 8083 |
| `notify` | `notify` | 8084 |
| `integration` | `integration` | 8085 |
| `cashclose` | `cashclose` | 8086 |
| `workforce` | `workforce` | 8087 |
| `inventory` | `inventory` | 8088 |
| `reporting` | `reporting` | 8089 |
| `api-gateway` | `api-gateway` | 8080 |

The existing single `comfy_case_close` Railway service can be assigned one
`SERVICE_MODULE`; use it as `api-gateway` if it should be the public entrypoint.
It cannot run all ten processes. Give only `api-gateway` a public domain; the
other services communicate over Railway private networking.

For each database-using service, set these Railway runtime variables (use the
corresponding service role and its own password):

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<pooled-host>/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=svc_cashclose
SPRING_DATASOURCE_PASSWORD=<cashclose-role-password>
PORT=8086
JWT_SECRET=<the-same-base64-key-for-all-JWT-validating-services>
```

Spring's `SPRING_DATASOURCE_*` variables override the localhost defaults in
`application.yml`. Generate `JWT_SECRET` once with `openssl rand -base64 33`;
the code requires at least 32 random bytes after Base64 decoding. Share that
exact value with all Java services that verify JWTs. `api-gateway` has no
datasource; give it the same `JWT_SECRET` and the internal URLs for every
upstream it routes to, for example:

```text
SVC_IDENTITY_URI=http://${{identity.RAILWAY_PRIVATE_DOMAIN}}:8081
SVC_PLATFORM_URI=http://${{platform.RAILWAY_PRIVATE_DOMAIN}}:8082
SVC_CASHCLOSE_URI=http://${{cashclose.RAILWAY_PRIVATE_DOMAIN}}:8086
```

Also set `SVC_FILES_URI`, `SVC_NOTIFY_URI`, `SVC_INTEGRATION_URI`,
`SVC_WORKFORCE_URI`, `SVC_INVENTORY_URI`, and `SVC_REPORTING_URI` the same way,
using their service names and ports in the table. Each service's `PORT` must
match the port used in its gateway URL.
`identity` also requires `PLATFORM_ADMIN_KEY` (at least 32 random characters).
Configure `CORS_ALLOWED_ORIGINS` for the frontend. Set `GCS_ENABLED=false` on
`files` until its bucket and mounted service-account key are configured; file
uploads remain unavailable while disabled. Configure mail credentials when
email delivery is needed. Start the data services first, then
the gateway. The app schemas must already exist because JPA uses
`ddl-auto: validate` and does not create them.
