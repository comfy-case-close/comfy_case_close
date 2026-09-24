# Permission model — target design (replaces roles)

Status: **design decision, not yet implemented.** Supersedes the role model for
authorization. Recorded 2026-09-22.

## Context
Multi-tenant F&B platform (fnbx / Comfy cash-close). Spring Boot 4, Java 21,
PostgreSQL with RLS, Liquibase migrations in db/changelog, services split by
domain (identity, platform, cashclose, reporting, …). Tenant comes from a
verified JWT; branch comes from the `X-Branch-Id` header and is verified
server-side. Today authority is a per-branch role (STAFF/MANAGER/ADMIN/
ACCOUNTANT) stored in `identity.staff_branch_role`.

## Decision
Drop roles entirely. Authority is a set of PERMISSIONS, granted through
POSITIONS (job titles) and, exceptionally, directly to a person.

- An Admin configures position → permissions.
- HR assigns positions to staff per branch (many positions per branch allowed).
- Effective permissions at a branch = union of the permissions of every live
  position held there, plus any direct per-staff grants.
- APIs check permissions only. They never check position or role.

## Tables (all in schema `identity`)

1. `permission` — global dictionary, no business_id, no RLS.
   permission_code TEXT PK, scope TEXT NOT NULL CHECK (scope IN ('BRANCH','BUSINESS')),
   description TEXT NOT NULL.

2. `position_permission` — business-scoped, SCD-2.
   grant_id UUID PK, position_id UUID, permission_code TEXT,
   business_id UUID NOT NULL, granted_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ.

3. `staff_branch_position` — SCD-2. Many positions per (staff, branch).
   assignment_id UUID PK, staff_id, branch_id, position_id, business_id,
   assigned_at, revoked_at.

4. `staff_branch_permission` — per-person exceptions at one branch, SCD-2.
   Grants only; no deny rows (avoids precedence puzzles).

5. `staff_business_permission` — business-wide acts that belong to no branch,
   SCD-2. REQUIRED: creating/deactivating a branch, approving a join request,
   renaming the business, granting permissions. These cannot be expressed as
   branch permissions.

SCD-2 pattern for 2–5, copied from `002-identity/011-role-history.sql`:
surrogate UUID PK, `[granted_at, revoked_at)` validity, partial unique index on
live rows, `EXCLUDE USING gist (… WITH =, tstzrange(...) WITH &&)` (needs
btree_gist), an immutability trigger allowing only the close of a live version,
and an audit trigger into `platform.audit_log`.

## Starter permission set
BRANCH scope:  CLOSE_READ, CLOSE_OPEN, CLOSE_EDIT, CLOSE_SUBMIT, CLOSE_REVIEW,
CLOSE_VOID, DENOMINATION_WRITE, MOVEMENT_ADD, MOVEMENT_REVIEW, WITHDRAWAL_RECORD,
FINANCE_READ, REPORT_READ, CONFIG_WRITE
BUSINESS scope: BRANCH_CREATE, BRANCH_DEACTIVATE, STAFF_ASSIGN,
JOIN_REQUEST_DECIDE, BUSINESS_UPDATE, PERMISSION_GRANT

Endpoint mapping (cashclose-service):
POST /cash-closes → CLOSE_OPEN · PATCH /cash-closes/{id} → CLOSE_EDIT ·
PUT /cash-closes/{id}/denominations → DENOMINATION_WRITE ·
POST /{id}/submit → CLOSE_SUBMIT · approve|reject|reopen → CLOSE_REVIEW ·
POST /{id}/void → CLOSE_VOID · POST /{id}/movements → MOVEMENT_ADD ·
movements approve|reject|reopen → MOVEMENT_REVIEW · GET routes → CLOSE_READ

## Enforcement
- `Permission` enum in `libs/fnbx-shared` (`com.fnbx.shared.security`).
- `BranchAccessGuard.require(UUID branchId, Permission p)` — reads the live
  effective set from the DB and throws AccessDeniedException (403) otherwise.
- `requireBusiness(Permission p)` — same against `staff_business_permission`.
- Controllers read `X-Branch-Id` (missing/unparseable → 400) and pass it down.
  Do NOT use `@PreAuthorize("hasRole(...)")`: `ServletSecurityConfiguration`
  sets `setJwtGrantedAuthoritiesConverter(jwt -> List.of())` on purpose, so
  Spring authorities are empty and global role checks would let a person
  authorized at branch A act on branch B.

## JWT
Claims stay small and carry NO authorization: `uid`, `business_id`, exp.
Remove the `branch_roles` claim. The frontend gets its render hint from
`GET /me/permissions?branchId=…`, called on branch switch. The backend never
reads a permission claim. Rationale: a token is a snapshot — a permission
revoked at noon must not survive until the token expires — and per-branch
permission claims blow past 8 KB proxy header limits for a multi-branch manager.

## Caching
Removing `branch_roles` removes the cheap JWT pre-filter, so every request now
hits the DB for authorization. Cache the effective set per (staff_id, branch_id)
with a short TTL, invalidated whenever any of tables 2–5 is written.

## Audit
`cashclose.cash_close_decision.acted_role` records the authority under which a
close was approved. Add `acted_permission TEXT` and write the permission that
authorized the act. Keep `acted_role` nullable for history and do NOT tighten
its CHECK — it already lists retired names (HR, SHIFT_LEAD) for that reason.

## Migration order (new Liquibase changesets only; never edit an applied one)
1. `permission` dictionary + seed.
2. `position_permission` (SCD-2) + seed each position's bundle.
3. `staff_branch_position` with SCD-2 columns.
4. `staff_branch_permission`, `staff_business_permission`.
5. Backfill: for every live `staff_branch_role` row, grant the equivalent
   permission set. ADMIN rows also get the BUSINESS-scope permissions.
6. `cash_close_decision.acted_permission`.
7. Cut code over to permission checks; run both models in parallel and verify.
8. LAST: drop `staff_branch_role`, `identity.app_role`, type `shared.user_role`,
   and `UserRole` from fnbx-shared.

## Code call sites to convert
`CashCloseServiceImpl` (~6 role checks), `CashCloseReportDao` (1),
`BranchServiceImpl` / `JoinRequestServiceImpl` / `BusinessServiceImpl`
(~11 `requireAnyBranch` calls → `requireBusiness`), `AccessPrincipal`
(drop `branchRoles`, `requireBranch`, `requireAnyBranch`, `branches()`),
`AssignBranchRoleRequest`, plus tests referencing UserRole.

## Non-goals
No deny rules. No tenant-editable permission dictionary. No permissions in the
JWT for authorization. Revisit only when a customer asks to configure their own
permission sets.

## Migration risk
This replaces a security model that currently works and is covered by tests
(`CashCloseAuthorizationTest`, `BranchAccessGuardTest`). Do it ADDITIVELY —
permissions alongside roles, cut the checks over, verify, drop
`staff_branch_role` last. A big-bang swap on authorization is the one migration
where a mistake stays invisible until it matters.

---

# Implementation status — verified 2026-09-22

Implemented on branch `feat/phase1-cashclose-endpoints` (migrations 014–019 plus
`007-cashclose/004`, `Permission`, `BranchAccessGuard`, `PermissionConfiguration`
in fnbx-shared, `AccessManagementService` / `PermissionController` /
`StaffAccessRepository` in identity-service, gateway routes, tests).

## What was verified, and how

A throwaway PostgreSQL 16 instance, the real changelog, the real role grants.

1. **Fresh install** — all 32 changesets in `db.changelog-master.xml` order apply
   cleanly to an empty database.
2. **Upgrade path** — changesets 1–25, then `tools/permission-upgrade-fixture.sql`
   (one business, one branch, four staff holding the legacy STAFF / MANAGER /
   ADMIN / ACCOUNTANT roles), then 26–32. `tools/permission-upgrade-verify.sql`
   passes: backfill parity, business grants confined to the former ADMIN, position
   assignments preserved through the SCD-2 rewrite, legacy objects gone, role
   history archived to `platform.audit_log`.
3. **The guard's own SQL, run as `svc_cashclose` with RLS live** — the effective
   permission query returns what the model promises:

   | was | effective at branch | CLOSE_REVIEW | CLOSE_VOID | FINANCE_READ | business-scope |
   |---|---|---|---|---|---|
   | STAFF | 7 | no | no | no | 0 |
   | MANAGER | 11 | yes | no | yes | 0 |
   | ACCOUNTANT | 11 | yes | no | yes | 0 |
   | ADMIN | 13 | yes | yes | yes | 6 |

4. **Negative tests** — a second tenant sees zero rows in all three grant tables;
   `svc_cashclose` is refused when it tries to grant itself `PERMISSION_GRANT`;
   `svc_identity` is refused when it tries to rewrite a live grant
   (`fn_guard_permission_history`).

## NOT verified

**Nothing was compiled and no Java test was run** — Maven Central is blocked by
egress policy in both available environments. What was checked: 79 changed Java
files parse under `javac 21`, every `com.fnbx.*` import resolves to a file that
exists, no reference to the deleted `UserRole` / `StaffBranchRole` / `AppRole`
survives, and the `Permission` enum matches `014-permission.sql` exactly on both
names and scopes. Run `mvn -pl services/identity-service,services/cashclose-service -am test`.

## Corrections applied on top of the implementation

- `014` — the `permission_revision` seed ran *after* `fn_apply_tenant_rls`. Moved
  ahead of it, the order `012-staff-branch-position.sql` uses. No live bug: the
  migration role needs BYPASSRLS anyway (`800-analytics` creates a BYPASSRLS
  role), so FORCE never bound it. This only keeps the file correct if that role is
  ever narrowed.
- `015` — the starter bundle seeded after the policy *and* after the audit
  trigger, making the migration emit one audited grant event per position per
  permission. Moved ahead of both.
- `018` — added one `permission_revision` bump after the backfill, so a service
  instance already running during a rolling upgrade drops its cached permission
  sets instead of serving pre-backfill authority for up to the cache TTL.
- `017` — stray blank lines from a removed column.
- `StaffPosition.java` — javadoc still linked the deleted `StaffBranchRole`.

## Open questions for the owner

1. **The `015` starter bundle grants all seven base permissions to every
   position** — so any position assigned at a branch can open, edit and submit a
   cash close and record a withdrawal, including QC Tester and Warehouse Checker.
   That is a faithful like-for-like migration of "everyone held at least STAFF",
   but it is a policy choice worth confirming rather than inheriting.
2. **`BranchAccessGuard.branches(Permission)`** calls `effective()` once per
   candidate branch. Fine at three branches; it is an N+1 at the scale this model
   was designed for.
3. **Code style** — the new files use one-space indentation and few comments,
   against a codebase whose convention is four spaces and heavy "why" javadoc.
   Worth a pass before merge.
