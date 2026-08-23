# Comfy Cash Close — backend

Spring Boot + Liquibase backend for the cash-close / end-of-shift reconciliation system.
The schema is defined in `../database.md` / `../mermaid_erd.md` (the source of truth) and applied by
Liquibase. Liquibase owns the schema; Hibernate is `ddl-auto=none` and must never generate DDL.

## Layout

Multi-module Maven build (aggregator root + two modules):

```
comfy_case_close/                        # aggregator pom (packaging: pom)
  comfy-case-close-app/                  # Spring Boot application module
    src/main/java/...                    # application code
    src/main/resources/application.properties
  database-migration/                    # standalone Liquibase migration module
    src/main/resources/liquibase/comfy/
      master.xml                         # <include> chain — the changelog entrypoint
      liquibase.properties               # connection config for the liquibase-maven-plugin
      scripts/01_create_user.sql         # one-time role + database bootstrap
      scripts/02_create_schemas.sql      # one-time schema bootstrap
      changelogs/1.0.0-extensions/
        1.0.0.1-init.xml                 # tagDatabase
        1.0.0.2-create-tables.xml        # enums + 14 tables + indexes + views (one file)
```

The changelogs live **only** in `database-migration`. The app module depends on that module, which
puts `classpath:liquibase/comfy/master.xml` on the app classpath — so there is a single source of
truth for the migration whether it is run standalone or on app startup.

## Configure the database

Defaults (in [`comfy-case-close-app/src/main/resources/application.properties`](comfy-case-close-app/src/main/resources/application.properties)
and [`database-migration/.../liquibase.properties`](database-migration/src/main/resources/liquibase/comfy/liquibase.properties)):

| Setting  | Default                                        |
|----------|------------------------------------------------|
| URL      | `jdbc:postgresql://localhost:6666/comfy_db`    |
| Username | `comfy_db`                                      |
| Password | `comfy_db`                                       |

For the **app**, override at runtime without editing files via Spring's env vars:
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
For the **standalone migration**, edit `liquibase.properties` or pass `-Dliquibase.url=...` etc.

Create the role, empty database, and required schemas once (Liquibase creates the objects *inside*
`cash_close_final`, not the database itself). Run the user/database script as a Postgres superuser,
then create the schemas in the new database:

```bash
psql -U postgres -f database-migration/src/main/resources/liquibase/comfy/scripts/01_create_user.sql
psql -U postgres -d comfy_db -f database-migration/src/main/resources/liquibase/comfy/scripts/02_create_schemas.sql
```

## Run the database migration (Podman / Docker Compose)

The preferred local workflow. [`docker-compose.yaml`](docker-compose.yaml) defines two pods — a
PostgreSQL `db` and a `migration` (Liquibase) pod. `up` starts the database, waits until it is
healthy, then runs `liquibase update` once and exits the migration pod:

```bash
podman compose up
```

The `migration` pod mounts the changelogs straight from `database-migration/` (the single source of
truth) and applies `master.xml`. Teardown:

```bash
podman compose down        # stop containers, keep data
podman compose down -v     # also drop the database volume for a clean slate
```

The database is published on host port **6666**, so the app (below) connects to it unchanged.
(`docker compose up` works identically if you use Docker instead of Podman.)

## Run the database migration (standalone, via Maven)

Alternatively, the `database-migration` module runs Liquibase on its own against any reachable
database (connection details in
[`liquibase.properties`](database-migration/src/main/resources/liquibase/comfy/liquibase.properties)):

```bash
./mvnw -pl database-migration liquibase:update
```

Other useful goals (same module):

```bash
./mvnw -pl database-migration liquibase:status
./mvnw -pl database-migration liquibase:rollback -Dliquibase.rollbackCount=1
./mvnw -pl database-migration liquibase:updateSQL
```

## Run the app

On boot the app also applies any pending changesets (convenient in dev), recording them in the
`databasechangelog` table — the same changelog the standalone runner uses, so the two never diverge:

```bash
./mvnw -pl comfy-case-close-app spring-boot:run
```

To have the app connect to an already-migrated database and **not** run Liquibase itself
(recommended for production, where `database-migration` is run as a separate deploy step), set
`spring.liquibase.enabled=false` (or `SPRING_LIQUIBASE_ENABLED=false`).

## Build / test

```bash
./mvnw clean install      # builds database-migration, then comfy-case-close-app
```

`ComfyCaseCloseApplicationTests` is a plain `@SpringBootTest` that starts the full context, so it
needs a reachable database at the configured URL. There is no Testcontainers setup yet — either
point it at a running Postgres or add Testcontainers before wiring this into CI.

## Note on the Spring Boot version

The aggregator inherits `spring-boot-starter-parent:4.1.1-SNAPSHOT` (from the Initializr scaffold),
resolved from the Spring snapshots repo. Snapshots can shift under you — pin to a stable GA release
before this goes anywhere near production.
