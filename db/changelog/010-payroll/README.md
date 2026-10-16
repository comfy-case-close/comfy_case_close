# Payroll SQL migrations

This is the migration-only port of `feat/payroll` at `af2926c` onto
`dev-service-base`. It includes payroll, contracts, payments, payslip
confirmation, availability, shift scheduling, attendance, permissions and
`identity.staff.nickname`.

Run the existing Liquibase entry point from the repository root:

```sh
./db/apply.sh validate
./db/apply.sh status
./db/apply.sh update-sql
./db/apply.sh update
```

The master remains `db/changelog/db.changelog-master.xml`; migration bodies
are `.sql` files referenced by `sqlFile`, as on the original branch. Existing
changeset IDs, authors, SQL files and the master's `logicalFilePath` remain
unchanged. The 21 new changesets are appended in dependency order:

1. Payroll core tables, referencing `identity.position` because the existing
   identity migration has already renamed `identity.staff_position`.
2. Tenant RLS on the core payroll tables and the `svc_hrm` role/grants, including
   default privileges for later tables and append-only audit/decision ledgers.
3. Payroll permissions and staff nicknames (`002-identity/026-staff-nickname.sql`).
4. Period dates, employee/contract fields, salary components, contract documents,
   payments/confirmation, scheduling/attendance and the remaining permissions.

The SQL preserves the XML source's changeset boundaries, transaction settings
and statement splitting. In particular, `005-1-enum-values.sql` runs outside a
transaction with statement splitting enabled, so new enum values are committed
before subsequent migrations use them. The existing migration role remains the
owner; the port does not create an additional login with a development password.

Use `update` on a database already migrated by the SQL master. `changelog-sync`
would mark the new migrations applied without executing them. A database whose
history instead comes from `feat/payroll`'s XML master has different changeset
identities and requires a separate history reconciliation before using this
master.

## Later additions from `feat/payroll`

`010-validation-issue-codes` to `015-attendance-excused-adjusted-leave` port
`1.0.0.10` to `1.0.0.15` of the XML tree (issue codes, contract template kind,
staff home branch, lateness level T1, attendance `ADJUSTED` status, excused and
adjusted hours). They are appended at the end of `db.changelog-master.xml`, after
the cashclose changesets, with the same format as the ones above: author `hrm`,
id `010-payroll-NNN-name`, a `sqlFile` per changeset. Enum-value additions run
with `runInTransaction="false"`.

## Departments moved to `identity`

`016-merge-department-into-identity` (author `kaiser`) merges `payroll.department`
into the existing `identity.department` (rows that identity already has are not
copied again; `position_profile.department_id` is re-pointed to them) and drops
`payroll.department`. `payroll.position_profile` stays in `payroll`; its foreign key
now points at `identity.department`. identity-service owns the writes
(`/api/v1/departments`); `svc_hrm` only reads and the migration revokes any write
privilege it might hold. The same changeset adds the `DEPARTMENT_MANAGE` permission
(BUSINESS scope) to the dictionary; granting it to positions is done separately in
`identity.position_permission`. It needs a migration role with `BYPASSRLS` or
superuser. Earlier changesets still create `payroll.department`: applied changesets
stay as they are.

## Verification

`017-unknown-termination-date` adds `employee_profile.termination_date_unknown`
(default `false`). `is_active` stays generated, now from
`terminated_on IS NULL AND NOT termination_date_unknown`. This supports a
confirmed departure with an unknown date without inventing `terminated_on`.
The changeset updates the expression in place on PostgreSQL 17+, and recreates
the derived column on PostgreSQL 16. Existing profile data is retained; the
employee import sets the new flag for individual employees. A verified rehire
must clear both the departure date and the flag.

`018-employee-employment-type` adds the nullable HR classification
`employee_profile.employment_type`, restricted to `FULL_TIME` and `PART_TIME`.
The employee import sets it from the roster; it is independent of the existing
`employment_assignment.employment_type` (`FULLTIME`/`PARTTIME`) and its pay rules.
Existing profiles remain unclassified until the source supplies a value.

`019-employee-employment-type-enum` converts that column to the existing
`payroll.employment_type` enum. Previously stored `FULL_TIME`/`PART_TIME` become
`FULLTIME`/`PARTTIME`; NULL stays unknown. The original 018 changeset remains
unchanged for deployed Liquibase checksums. This schema migration does not
import the employee workbook or infer HR classification from assignments.
Run the updated standalone CMF employee import after Liquibase to populate it.
The 019 SQL was tested on PostgreSQL 17 with populated 018 profiles, followed
by all 87 migrations and the CMF import regression checks. The upgrade test is
`tools/test-employee-employment-type-enum.sql`; final enum behavior is covered
by `tools/test-employee-employment-type.sql`.

`020-assignment-current-hourly-base-rate` adds nullable
`employment_assignment.current_hourly_base_rate` with exactly the same domain as
`hourly_base_rate`: `shared.d_money_nonneg` (`numeric(14,2)`, nonnegative).
Existing assignments remain NULL until explicitly populated. It records the
current roster rate independently of the contractual rate; this migration does
not change `hourly_base_rate` or payroll calculation behavior.
The original 020-only CMF fill matched business + employee code across assignment
dates/types, preserving explicit source zero and ignoring blank source rates.
The final location and current fill script are now defined by 021 below.
Verified on PostgreSQL 17 with Liquibase 4.29.2: upgrade from 87 to 88 changesets,
all prior checksums/history retained, repeated update is a no-op. The standalone
fill was tested with 106 assignments across two businesses, including expired
and future assignments, both employment types and departed staff. Dry-run,
existing-value preservation, blank/zero handling and rerun checks pass.
Domain behavior is covered by `tools/test-current-hourly-base-rate.sql`.

`021-profile-current-hourly-base-rate` moves the roster rate to
`employee_profile.current_hourly_base_rate` using the same domain. 020 is retained
unchanged for deployed checksums. Existing assignment values are copied by
business + staff before the assignment column is dropped. Multiple identical
values (including zero) become one profile value; all-NULL values remain NULL.
Conflicting non-NULL values, or values without a matching profile, abort the
transaction so no data is silently discarded. Reconcile those rows before retrying.
The current standalone CMF fill SQL now targets employee_profile, fills only NULL
rates and preserves all contractual assignment fields. The domain regression SQL
in `tools/test-current-hourly-base-rate.sql` now verifies this final schema.
Verified on PostgreSQL 17 with Liquibase 4.29.2: upgrade 88 -> 89, unchanged prior
history/checksums, atomic failure for conflicting rates or missing profiles,
preserved contractual fields, zero/NULL transfer, and idempotent profile fill for
51 source employees across two businesses (including a profile without assignments).

Verified with Liquibase 4.29.2 and PostgreSQL 16 in disposable local databases:

- Fresh installation and upgrade from `dev-service-base` at `9466b40` succeed.
- The upgrade applies 21 new changesets; all 51 original history rows and
  checksums remain unchanged. A repeated update applies zero changesets.
- Existing-business permission grants and salary-component backfill succeed.
- Tenant RLS, role restrictions and append-only ledger guards pass.
- The final payroll schema matches a fresh application of the original
  `feat/payroll` XML migrations.

## Merging the application feature later

`feat/payroll` descends from `dev-service-base`, so merge it into the base branch
when the complete application feature is ready. A merge is unnecessary for this
migration-only port. During a later feature merge, retain this SQL changelog
tree, master path and migration configuration, and reconcile the XML migration
renames/deletions so the same payroll changes are not registered twice. Also
retain the base branch's Neon analytics ownership and migration-role handling.
