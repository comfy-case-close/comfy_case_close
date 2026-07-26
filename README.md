# Comfy Cash Close — backend

Spring Boot + Liquibase backend for the cash-close / end-of-shift reconciliation system.
Schema is defined in `../database.md` / `../mermaid_erd.md` and applied by Liquibase on startup.

## Layout

```
src/main/resources/
  application.properties                     # datasource + JPA + liquibase wiring
  db/changelog/
    db.changelog-master.yaml                 # include order
    changes/001-create-enums.sql             # 14 enum types
    changes/002-create-tables.sql            # 16 tables (FK-ordered)
    changes/003-create-indexes.sql           # secondary indexes
    changes/004-seed-system-config.sql       # singleton config row
```

Liquibase owns the schema; Hibernate is `ddl-auto=none` and must never generate DDL.

## Configure the database

Set these before running (env vars, or edit `application.properties`):

| Var | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/comfy_cash_close` |
| `DB_USERNAME` | `comfy` |
| `DB_PASSWORD` | `comfy` |

Create the empty database first (Liquibase creates the objects inside it, not the database itself):

```bash
createdb comfy_cash_close
```

## Run

```bash
./mvnw spring-boot:run
```

On boot, Liquibase applies changesets `001`–`004` and records them in `databasechangelog`.

## Test

`ComfyApplicationTests` uses **Testcontainers**, so it needs **Docker running** but no hand-configured
database — it spins up a throwaway Postgres, applies all migrations, and asserts the context loads:

```bash
./mvnw test
```

## Note on the Spring Boot version

`pom.xml` inherits `spring-boot-starter-parent:4.1.1-SNAPSHOT` (from the Initializr scaffold), resolved
from the Spring snapshots repo. Snapshots can shift under you — pin to a stable GA release before this
goes anywhere near production.
