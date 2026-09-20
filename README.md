# F&B Nexus — `fnb` monorepo

Multi-tenant SaaS for Vietnamese F&B chains. This repo holds the **entire Java backend**: shared libraries, domain services, and database migrations.

> Everything here implements the **24 decisions locked in** `Comfy/docs/adr/0003-mo-hinh-trien-khai-va-du-lieu.md`. When you wonder "why is it done this way", the answer is there.

---

## 1. Architecture on one page

```
                    ┌──────────────────────────────┐
                    │  fnbx-frontend (separate repo)│
                    │  mobile (RN) · console (Next) │
                    └──────────────┬───────────────┘
                                   │ HTTPS + JWT
                    ┌──────────────▼───────────────┐
                    │      api-gateway :8080       │
                    │  verifies the JWT       │
                    └──┬───┬───┬───┬───┬───┬───┬───┘
        ┌──────────────┘   │   │   │   │   │   └────────────┐
        ▼                  ▼   ▼   ▼   ▼   ▼                ▼
   identity  platform  files  notify  integration  cashclose  workforce
    :8081     :8082    :8083   :8084     :8085       :8086      :8087
                                                  inventory  reporting
                                                    :8088      :8089
        │  one DB ROLE per service: svc_identity, svc_cashclose, …
        └──────────────────────┬──────────────────────────────┘
                               │  PgBouncer :6432 (transaction pooling)
                               ▼
   ┌───────────────────────────────────────────────────────────────┐
   │  fnbx_oltp — ONE PostgreSQL, ALL tenants                       │
   │  schemas: shared · identity · platform · files · notify ·      │
   │           integration · cashclose · workforce · inventory ·    │
   │           analytics                                            │
   │  RLS + FORCE on business_id, on every tenant-scoped table      │
   └───────────────────────────────────────────────────────────────┘
```

**Style:** Service-Based (Richards & Ford, *Fundamentals of Software Architecture*, Ch. 13) — many deployables, **one shared database**, keeping foreign keys, joins and ACID.

---

## 2. Directory layout

```
fnb/
├── pom.xml                     parent POM (aggregator, 21 modules, Lombok)
├── fnbx-bom/                   pins the version of every com.fnbx artifact
│
├── db/                         the ONE place migrations live (decision 7)
│   ├── changelog/                Liquibase changelog, one global order
│   │   ├── 001-shared/           types, enums, helper functions
│   │   ├── 002-identity/         CORE, highest fan-in
│   │   ├── 003-platform/         CORE — config, audit, movement_kind catalogue
│   │   ├── 004-files/            CORE
│   │   ├── 005-notify/           CORE
│   │   ├── 006-integration/      CORE — POS integration
│   │   ├── 007-cashclose/        FEATURE — tables + integrity engine
│   │   ├── 800-analytics/        matviews + RLS wrapper views for the AI agent
│   │   ├── 900-rls/              enables RLS AUTOMATICALLY on any business_id table
│   │   ├── 910-roles/            DB roles + the GRANT matrix
│   │   └── 920-seed/             lookup data
│   ├── docker-compose.yml        Postgres 16 + PgBouncer + Liquibase
│   ├── apply.sh                  Liquibase runner (update / status / history)
│   └── rls-guard.sh              5 RLS guard queries
│
├── libs/                       federated entity libraries (decision 5)
│   ├── fnbx-shared/              TenantContext, Money, exceptions, shared enums
│   ├── fnbx-archtest/            shared ArchUnit rules
│   ├── fnbx-entities-identity/   CODEOWNERS-locked
│   ├── fnbx-entities-platform/   CODEOWNERS-locked
│   ├── fnbx-entities-files/
│   ├── fnbx-entities-notify/
│   ├── fnbx-entities-integration/
│   ├── fnbx-entities-cashclose/
│   ├── fnbx-entities-workforce/  (not implemented yet)
│   └── fnbx-entities-inventory/  (not implemented yet)
│
├── services/                   one directory = one independent deployable
│   ├── api-gateway/
│   ├── identity-service/       CORE
│   ├── platform-service/       CORE
│   ├── files-service/          CORE
│   ├── notify-service/         CORE
│   ├── integration-service/    CORE
│   ├── cashclose-service/      FEATURE ← reference service, the most complete
│   ├── workforce-service/      FEATURE (skeleton)
│   ├── inventory-service/      FEATURE (skeleton)
│   └── reporting-service/      READ SIDE — no service layer (decision 11)
│
├── tools/
│   ├── extract-service.sh      simulates "this service already lives alone"
│   ├── deploy.sh
│   └── verify.py               static verification across the codebase
│
└── .github/
    ├── CODEOWNERS
    └── workflows/
        ├── ci.yml               build + test + ArchUnit + RLS guard
        ├── deploy.yml           incremental build via paths-filter
        └── split-readiness.yml  nightly — proves splitting the repo is still cheap
```

### Package conventions

| Package | Contains |
|---|---|
| `com.fnbx.<module>.entity` | JPA entities |
| `com.fnbx.<module>.enums` | enums used by **one** module |
| `com.fnbx.shared.enums` | enums used by **two or more** modules |
| `com.fnbx.<module>.repository` | Spring Data repositories (services only) |
| `com.fnbx.<module>.service` | business logic, `@Transactional` lives here |
| `com.fnbx.<module>.api` | controllers + DTOs, no business logic |

Entities use Lombok (`@Getter @Setter @NoArgsConstructor`), with `@Setter(AccessLevel.NONE)` on any column the database owns — generated columns, trigger-written snapshots, timestamps.

**Why enum placement differs between SQL and Java.** In PostgreSQL every enum type lives in the `shared` schema, because a type must exist before any column uses it, `001-shared` runs first, and every service role has `search_path = 'shared, public'`. That is a physical constraint, not a design choice.

Java has no such constraint, so placement follows blast radius instead: `fnbx-shared` has fan-in 9/9, and adding a value to an enum there forces a rebuild of every service — including ones that have no idea what the concept means. So `CloseStatus`, `FundStatus` and `RiskLevel` sit in `com.fnbx.cashclose.enums`, while `BusinessType`, `FileKind`, `EffectType` and `UserRole` — each genuinely crossing a module boundary — sit in `com.fnbx.shared.enums`.

---

## 3. Running locally

```bash
# 1. Bring up the database
cd db && docker compose up -d && ./apply.sh
```

`apply.sh` runs **Liquibase** in a pinned container — nothing to install. The
order of migrations lives in `changelog/db.changelog-master.xml` and nowhere
else. Useful commands:

```bash
./apply.sh status        # what has not run yet
./apply.sh history       # what ran, when, by whom
./apply.sh validate      # check the changelog before committing
./apply.sh update-sql    # print the SQL instead of executing it
```

> **Once, on a database that predates Liquibase** (one migrated by the old psql
> loop): run `./apply.sh changelog-sync` **before** the first `./apply.sh`. It
> creates `public.databasechangelog` and marks all changesets as applied without
> executing any SQL. Skip it and the first `update` tries to `CREATE TABLE` over
> tables that already exist.

```bash

# 2. Build everything
mvn clean install -DskipTests

# 3. Run one service
mvn -pl services/cashclose-service spring-boot:run

# 4. Static verification (needs neither Postgres nor Maven)
python3 tools/verify.py
```

| Connection | Port | Used for |
|---|---|---|
| PostgreSQL directly | 5433 | migrations, psql, debugging |
| **PgBouncer** | **6432** | **every service** — see §4.2 |

---

## 4. Non-negotiable rules

### 4.1 Write ownership

| | Rule | Enforced by |
|---|---|---|
| **WRITE** into schema X | **Only** the service that owns X | PostgreSQL `GRANT` |
| **READ** other schemas | Every service — direct joins, **no HTTP calls** | `GRANT SELECT` |

> **Split services by who may WRITE, never by who may READ.**

Inter-service chatter is *"something to definitely avoid with service-based architecture"*. Calling `identity-service` over HTTP just to fetch a name throws away the single biggest advantage of this style.

### 4.2 Tenant isolation — all three layers required

```java
// 1. business_id ALWAYS from the signed JWT. NEVER from a path, query or header.
// 2. The third argument of set_config MUST be true (is_local).
jdbc.execute("SELECT set_config('app.business_id', ?, true)", businessId);
// 3. RLS + FORCE on every table with business_id.
```

**What Row Level Security is:** a policy attached to a table that PostgreSQL adds to every query as an invisible `WHERE` clause. `SELECT * FROM cash_close` silently becomes `... WHERE business_id = shared.current_business_id()` regardless of who wrote the query. Isolation stops being something the application must remember and becomes something the database enforces.

`FORCE ROW LEVEL SECURITY` extends that to the table **owner** as well. Only a role with `BYPASSRLS` escapes — and exactly one such role exists (§4.5).

Using a session-level `SET` instead of `set_config(..., true)` **leaks the tenant context into another tenant's request** once PgBouncer reuses the connection. It is the most severe security bug possible in this architecture.

**Fail-closed:** forget the context and `shared.current_business_id()` returns NULL, no policy matches, and you get **zero rows** rather than everything.

### 4.3 Schema changes — expand, migrate, contract

**Why this exists.** Two artifacts drift independently:

| Artifact | How many copies | How it updates |
|---|---|---|
| The database schema | **one**, shared | migration runs **once**, atomically |
| The entity jar | **nine**, one bundled per service | each service redeploys **separately** |

The migration is atomic. The rollout is not. Everything between those two facts is where incidents happen.

```
1. EXPAND    add the new column (nullable), KEEP the old one
2. MIGRATE   services write BOTH, read the new one; backfill
3. CONTRACT  once EVERY service is on the new build → drop the old column
```

**Forbidden in a single release:** `RENAME COLUMN` · `DROP COLUMN` · incompatible `ALTER COLUMN TYPE` · `ADD COLUMN NOT NULL` without `DEFAULT`.

#### The failure it prevents

Renaming `identity.app_user` to `identity.staff` in one release, with services still on the old jar:

```
ERROR: relation "identity.app_user" does not exist
```

There is a nastier variant. `ddl-auto: validate` runs at **startup**, not per query. So a service on the old jar:

* **already running** → fails at query time
* **restarting** (crash, autoscale, reschedule) → fails to boot at all

```
Schema-validation: missing table [identity.app_user]
```

A service that looks healthy dies permanently at its next restart, and nobody notices until then.

#### Renaming safely

A table cannot carry two names, but it can leave a view behind:

```sql
-- EXPAND
ALTER TABLE identity.app_user RENAME TO staff;
ALTER TABLE identity.staff RENAME COLUMN user_id TO staff_id;

CREATE VIEW identity.app_user AS
  SELECT staff_id AS user_id, business_id, employee_code, full_name, ...
    FROM identity.staff;
```

A single-table view in PostgreSQL is auto-updatable, so INSERT/UPDATE/DELETE still work through the old name. Old and new builds run side by side. RLS still applies: the view runs as its owner, `FORCE RLS` covers the owner, and the policy reads the current session's `app.business_id`.

Before CONTRACT, **mandatory**:

```bash
grep -rn "app_user" libs/ services/ --include="*.java" --include="*.sql" --include="*.yml"
# must return nothing
```

```sql
-- CONTRACT, a separate release
DROP VIEW identity.app_user;
```

That grep is the reason this is still a monorepo. Across 9 separate repos, *"is any service still reading this?"* can only be guessed at — and native SQL in `reporting-service` is invisible to both the compiler and Spring.

#### When you can skip all of this

The monorepo does **not** remove the need. It guarantees all nine jars are *built* from the same entity version; it does not make them *start* at the same instant. One service failing to boot, one rollback, or one autoscaled pod on an old image reopens the window.

What actually decides it is whether you need zero downtime:

| Situation | Skip expand → migrate → contract? |
|---|---|
| No production data yet | **Yes** — `./apply.sh drop-all && ./apply.sh` |
| Planned downtime is acceptable | **Yes** — stop all 9, migrate, start all 9. No mixed window exists |
| Zero downtime required | No |
| Repos split, independent release cadence | No — the window becomes uncontrollable |

For three branches, a two-minute planned outage at 03:00 is cheaper than the view dance. Row two is the right answer for a while. Row three is what you grow into when 200 businesses across time zones mean there is no shared 03:00 any more.

### 4.4 Migrations run BEFORE deploys

```
db/  ──migrate──▶  fnbx_oltp  ──then──▶  deploy the service
NEVER the other way round
```

This ordering is also *why* §4.3 exists: between the migration finishing and the last service coming up, old code is running against the new schema. That window is unavoidable — the rules above are what make it survivable.

### 4.5 The one role with BYPASSRLS

`analytics_refresher` exists because `REFRESH MATERIALIZED VIEW` runs with no tenant context under `FORCE RLS`, and would therefore rebuild every matview to **zero rows** — silently, since fail-closed looks exactly like "there was no data".

It is `NOLOGIN`, owns nothing but the matviews, and is reachable only through `analytics.fn_refresh_all()` (`SECURITY DEFINER`). Every actual reader still goes through the `security_barrier` wrapper views that re-apply the tenant filter. Guard query 9.2 fails the build if any other role acquires the attribute.

---

## 5. Deploying

### 5.1 Libraries are shipped, not run

`libs/fnbx-*` produce ordinary jars with no `main()`. They are bundled **inside** each service's executable jar, exactly the way Hibernate and the Postgres driver are:

```
cashclose-service-1.0.0-SNAPSHOT.jar
├── META-INF/MANIFEST.MF
│     Start-Class: com.fnbx.cashclose.CashCloseApplication
├── BOOT-INF/classes/          ← the service's own code
└── BOOT-INF/lib/              ← every dependency, as nested jars
      fnbx-entities-cashclose-1.0.0-SNAPSHOT.jar
      fnbx-entities-identity-1.0.0-SNAPSHOT.jar
      fnbx-shared-1.0.0-SNAPSHOT.jar
      hibernate-core-....jar
      postgresql-....jar
```

Verify it yourself:

```bash
jar tf services/cashclose-service/target/cashclose-service-1.0.0-SNAPSHOT.jar | grep fnbx-entities
```

The same entity jar is bundled into several service jars, and each service process holds its own copy in its own JVM. They share a **definition**, never memory and never a runtime. There is no "entity server" for anyone to call.

That is also why `fnbx-bom` exists: if cashclose-service ships entity 1.4 while workforce-service ships 1.3, the two disagree about the shape of a table.

### 5.2 What a redeploy actually looks like

The nine services are nine independent processes, and `api-gateway` routes per path. Restarting `identity-service` does not stop `cashclose-service` — only the features routed through the restarting service fail.

| Strategy | Downtime | Mixed versions |
|---|---|---|
| **Stop → start** (single instance) | ~30–60 s per service | No |
| **Rolling** (2+ replicas) | none | **Yes, guaranteed** — v1.0 and v1.1 of the *same* service run together |
| **Blue/green** | none | Yes, during the switch |

Note the trade: rolling deployment is what *creates* the mixed-version window. You buy zero downtime with complexity, not with safety.

At the current scale — three branches, deploy at 03:00 — stop/start is the sensible choice. Add graceful shutdown so in-flight requests are not cut mid-transaction:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
```

## 6. When to split the repo — 4 triggers

The monorepo is a **deliberate** choice, not laziness. Split when **any** of these happens:

| # | Signal | Measured by |
|---|---|---|
| 1 | **2+ teams** own different services | Org chart |
| 2 | Whole-repo CI **> 15 min** even with incremental builds | GitHub Actions timing |
| 3 | A **third party** needs access to exactly one service | Actual request |
| 4 | A service is written in **another language** | Already applied: `fnbx-ai`, `fnbx-etl` (Python) live elsewhere |

**Cost when the time comes:** ~2 days for the first service (standing up a Maven registry), ~0.5 days for each one after.

Prove it any time:

```bash
./tools/extract-service.sh cashclose /tmp/solo && cd /tmp/solo && mvn -o clean verify
```

The script generates the exact `pom.xml` the service would have after extraction (parent switched to `spring-boot-starter-parent`, library versions pinned via `fnbx-bom`). If the offline build succeeds there are **no hidden dependencies**, and the "2 days" figure is real.

`split-readiness.yml` runs this for all 9 services **nightly**. If that job stays red and nobody fixes it, the cost of splitting is quietly rising from **2 days to 2 months**.

### 6.1 What a schema change costs *after* the split

The two days is the cost of splitting. This is the cost of every schema change afterwards. Same rename, `identity.app_user` → `identity.staff`, used by 7 of 9 services:

**1. `fnbx-db` — release EXPAND**

```sql
ALTER TABLE identity.app_user RENAME TO staff;
ALTER TABLE identity.staff RENAME COLUMN user_id TO staff_id;
CREATE VIEW identity.app_user AS SELECT staff_id AS user_id, ... FROM identity.staff;
```

Applied to production. Both names now resolve. No service has changed yet.

**2. `fnbx-libs` — publish 1.1.0**

```bash
mvn deploy          # → GitHub Packages
```

`1.0.0` stays published forever. That is the crucial difference from a monorepo: the old version is an immutable artifact the other services are still running, not code that has been edited out from under them.

**3. Each service repo, independently, at its own pace**

This is where the BOM earns its keep — one property per service, not one per library:

```xml
<properties>
  <fnbx.version>1.1.0</fnbx.version>          <!-- the only edit -->
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.fnbx</groupId><artifactId>fnbx-bom</artifactId>
      <version>${fnbx.version}</version><type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- no versions here; the BOM decides -->
  <dependency><groupId>com.fnbx</groupId><artifactId>fnbx-entities-identity</artifactId></dependency>
  <dependency><groupId>com.fnbx</groupId><artifactId>fnbx-shared</artifactId></dependency>
</dependencies>
```

Without the BOM that is 5 dependencies × 9 services = 45 edits, one of which will be missed — leaving 1.0.0 and 1.1.0 inside the same jar and a `NoSuchMethodError` at runtime.

Then `mvn clean verify` — the compiler points at every `getUserId()` — fix, test, deploy.

**4. Track adoption**

In the monorepo, `grep` answers this. Across nine repos it cannot, so each service has to declare its own version:

```yaml
info.fnbx.libs-version: "@fnbx.version@"
management.endpoints.web.exposure.include: info,health
```

```bash
for s in identity platform files notify integration cashclose workforce inventory reporting; do
  printf '%-14s %s\n' "$s" \
    "$(curl -s https://$s.internal/actuator/info | jq -r '.fnbx["libs-version"]')"
done
```

**5. `fnbx-db` — release CONTRACT**, only once all nine print `1.1.0`

```sql
DROP VIEW identity.app_user;
```

Steps 1 and 5 can be weeks apart. A one-line `grep` becomes a five-step tracked process — which is why every trigger in the table above is an **organisational** signal, not "the code got big". Split when the shape of the team forces it, not because it looks tidier.

---

## 7. Fitness functions — must be green on every build

| Check | Where | Catches |
|---|---|---|
| Every `business_id` table has RLS + FORCE + ≥1 policy | `db/rls-guard.sh` 9.1 | **Forgetting RLS on a new table** ← cause #1 of leaks |
| Only `analytics_refresher` holds `BYPASSRLS` | `rls-guard.sh` 9.2 | A role misconfigured into bypassing isolation |
| `ai_agent` is not granted directly on a matview | `rls-guard.sh` 9.3 | Data leak (matviews have no RLS) |
| `ai_agent` cannot reach the business schemas | `rls-guard.sh` 9.4 | LLM-generated SQL touching raw data |
| Both decision ledgers are append-only | `rls-guard.sh` 9.5 | An editable audit trail |
| Controllers do not call repositories | ArchUnit | Layer skipping |
| Repositories do not call services | ArchUnit | Dependency cycles |
| Controllers do not expose entities | ArchUnit | HTTP contract pinned to the table layout |
| Services do not import other services | ArchUnit | Cross-coupling → expensive repo split |
| Each service builds standalone | `split-readiness.yml` | Hidden coupling |
| `@Table`/`@Column` match the DDL | `tools/verify.py` | Entity drift |

---

## 8. The business invariants that matter most

### 8.1 Sign convention — read this before touching any figure

```
signed_amount   < 0  =>  cash OUT of the drawer (an expense)
signed_amount   > 0  =>  cash IN to the drawer (tip left inside, change fund)

cash_difference = counted_cash - pos_expected_cash
                < 0  =>  SHORT
                > 0  =>  OVER
```

Both live in the same sign space, so

```
unexplained = cash_difference - explained - pending
```

contains no inversion anywhere. The previous convention (`pos - counted`, shortage positive) forced every reader to remember "short is positive", which was a recurring source of arithmetic bugs.

### 8.2 Nothing is a cached total

`cash_close` stores **no** computed column. Counted cash, explained/pending/unexplained difference, cash remaining, expense totals, tip totals and risk level all come from `cashclose.v_close_calc`.

Cached totals on a parent row are a systematic source of drift. In Comfy's real spreadsheet, 11 of 181 closes had an explained total that disagreed with their own explanation rows, and 16 of 181 had a counted-cash figure that disagreed with the denomination count. What is not stored cannot drift.

The drawer identity is therefore a definition rather than a check:

```
cash_remaining = counted_cash - withdrawal_amount
                 - SUM(abs(signed_amount)) over lines that leave the drawer
```

### 8.3 Formulas are versioned

`cash_close.calc_version` records which formula produced the figures a manager signed off on. Changing a formula means adding `v_close_calc_vN`, never editing the old view; an APPROVED close keeps its version forever and a trigger refuses to change it. A bug fix in the arithmetic can never silently restate closed books.

Only create a new version when the change would alter figures on an **approved** close. Typos, new columns and index changes are edited in place.

### 8.4 One ledger, one catalogue

`cash_movement` is the single cash ledger for a shift: expenses, end-of-day spending, tips, change swaps, borrowing, unpaid bills, POS errors, miscounts. It replaced three tables — in the real data 260 of 311 "explanation" rows were verbatim copies of a movement row.

`platform.movement_kind` is the single catalogue behind it, and carries the three flags that decide the arithmetic: `effect_type` (did cash actually move, which way), `affects_difference` (was it already inside the counted cash), `affects_remaining` (does it leave before hand-over). It is **SCD-2**: changing a rule inserts a new version, and a movement points at the version in force on its business date, so a close approved in 2026 is never recomputed under a 2027 rule.

### 8.5 Two decision ledgers, one pattern

| | Decides | Current state on | History in |
|---|---|---|---|
| Per line | what is **explained** | `cash_movement.approval_status` | `cash_movement_decision` |
| Per close | the **residual** — what nobody declared | `cash_close.status` | `cash_close_decision` |

Both are insert-only and keep `old_status`/`new_status`. `cash_movement_decision` rows are written by a trigger, never by the application, and snapshot the amount each decision endorsed — otherwise approving 523,000, reopening, changing to 5,230,000 and approving again would leave no trace of the amount change.

These are **event logs**, not SCD-2 dimensions: an event happens at an instant, so it has `decided_at` and no validity range. SCD-2 belongs on `movement_kind`, where what changes over time is a rule rather than a state.

### 8.6 Expected revenue is not typed by the person counting the cash

In the old spreadsheet, staff typed the shift's cash revenue by hand — and that is the baseline the gap is measured against. The person counting the money also typed the yardstick, so hiding a shortfall took one keystroke: **the entire control collapsed at a single input field.**

`integration.shift_sales` takes that figure straight from the POS. When `expected_cash_source = 'POS_SYNC'` a trigger forbids hand edits. When the POS is down, `MANUAL` is still allowed — but flagged and alerted on. The share of MANUAL closes per branch is an internal-control metric worth watching.

---

## 9. Before writing the first line of production code

- [x] Port Vakot authentication to identity-service, with BCrypt, rotating refresh tokens and verified tenant/branch claims. See [authentication and deployment](docs/security/authentication.md).
- [x] Standardize application exceptions and error responses across services. See [shared error handling and codes](docs/error-handling.md).
- [ ] Configure the signing key, SMTP and Google client ID; activate/provision staff accounts.
- [ ] Review [remaining security hardening](docs/security/optimization-proposals.md) before production rollout.
- [ ] Put POS secrets in a secret manager; `credential_ref` holds only the reference
- [ ] Keep services (8081–8089) off the internet — only the gateway (8080) is exposed
- [ ] Enable `split-readiness.yml` and watch it

---

## 10. Documents

| Document | Contents |
|---|---|
| ADR-0001 | Service and database topology, module dependency rules |
| ADR-0002 | Architecture selection per *Fundamentals of Software Architecture* |
| **ADR-0003** | **The 24 locked decisions — this repo implements them** |
