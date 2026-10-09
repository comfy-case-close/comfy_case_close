# payroll (FEATURE) — schema, entities, API and engine (hrm-service)

Source documents live outside this repo, at `C:\comfy\`:

- `comfy-payroll-erd.mmd` — schema source of truth (21 entities in the original
  doc; 26 tables here after adapting PK/tenancy mechanics, see below)
- `comfy-payroll-springboot-spec.md` — behaviour, decision log (DEC-01..08)
- `comfy-payroll-formula-reference.md` — the Excel formulas being replaced
- `comfy-payroll-architecture.md` — DB-vs-API split, generated columns

## What was decided here (confirmed with the repo owner, not guessed)

The payroll spec was written against the **old** single-service monolith
(`backend_architecture.md` / `AGENTS.md` at `C:\comfy`: `BIGINT IDENTITY`
PKs, no multi-tenancy) and explicitly left **DEC-07** (how `branch` /
`employee` / `app_user` are shared with cash-close) pending.

This repo has since moved to the ADR-0003 split-service architecture (UUID
PKs, `business_id` + RLS on every table), and already answers DEC-07:

| ERD table | Resolved to |
|---|---|
| `BRANCH` | `identity.branch` + `payroll.branch_setting` (extension: shared-pool / selling-store flags) |
| `EMPLOYEE` | `identity.staff` + `payroll.employee_profile` (extension: bank details, hired/terminated dates) |
| `JOB_POSITION` | `identity.staff_position` + `payroll.position_profile` (extension: department, trainee flag) |
| `APP_USER` | `identity.staff` — it already logs in (`identity.staff_session`) |
| `USER_BRANCH_SCOPE` | dropped — identity permissions (`staff_branch_position`, `position_permission`, `staff_branch_permission`) via `BranchAccessGuard` |
| `AUDIT_LOG` | `payroll.payroll_audit_log` (trigger-written) + `payroll.payroll_period_decision` (period status ledger). Not `platform.audit_log`: that one is readable by every service and would carry salaries in `old_value`/`new_value` |

`identity.staff`'s own Javadoc already anticipated this:

> "Do not add salary, contract or ID-document columns. ADR-0003 decision 24:
> sensitive HR data lives in its own `payroll` schema with its own DB role."

So the **service module** stays named `hrm` (`services/hrm`,
`com.fnbx.hrm`, port 8090) while the **DB schema** is `payroll` — see
`ServiceBoundaryRules.DOMAINS` (`fnbx-archtest`) and `910-roles` for where
that split is threaded through.

## What is genuinely new here

Everything else in the ERD: `department` (new — no grouping concept existed),
`attendance_code`, `late_penalty_rule`, `payroll_config`, `insurance_scheme`,
`pay_component`, `employment_assignment`, `employee_allowance`,
`employee_leave_quota`, `payroll_period`, `payroll_run`, `payslip`,
`payroll_line`, `timesheet_entry`, `payroll_line_item`,
`payroll_line_insurance`, `payslip_email_log`, `branch_revenue`,
`shared_cost_allocation`, `data_validation_issue`, `import_job`. All carry `business_id` and get RLS automatically from the
existing dynamic loop in `900-rls` (just added `payroll` to its schema list).

## What is NOT done yet

- Reporting **views** (`v_payroll_line_hours`, `v_leave_balance`, `v_branch_labor_cost`,
  `v_dashboard_kpi`): the hrm service computes these in queries instead.
- **Seed data** (`pay_component`, `attendance_code`, `late_penalty_rule`,
  `insurance_scheme` rows), Excel import and parity check, payslip PDF rendering.
- Items of `comfy-payroll-architecture.md` still open: ARCH-02 (department to identity),
  ARCH-03 (drop `default_branch_id` / `branch_cost_policy`), ARCH-07 (enums to `shared`),
  ARCH-08 (global-or-tenant reference data), ARCH-09 (freeze trigger, `calc_version`),
  ARCH-11 (error-code renumbering).

## Tentative decisions carried over (spec section 14)

⚑ DEC-01 (do part-timers get the 4 allowances), DEC-02 (real weekend
multiplier) and DEC-05 (role model - identity already has a much richer
role/permission system than the spec's 4-role sketch) are still open; they
live in `services/hrm/src/main/resources/application.yml` under
`hrm.tentative-decisions` (logged at startup by `TentativeDecisionReminder`) and
in the spec's decision log (section 14). There is no decision table or endpoint.
DEC-09 (timesheet/leave ownership until `workforce` exists) is listed there too.

## Verified

The full migration chain (`001-shared` → … → `007-cashclose` →
**`010-payroll`** → `800-analytics` → `900-rls` → `910-roles` → `920-seed` →
every appended changeset) was applied end-to-end against a scratch
PostgreSQL 16 database and passed a functional smoke test: `ck_basis`,
the `employment_assignment` `EXCLUDE` constraint, `employee_profile.is_active`,
`payroll_period.days_in_period`, `timesheet_entry.is_weekend` (R01 — a real
Saturday correctly generates `true`), and the insurance `GENERATED` amounts
all behave as specified.
