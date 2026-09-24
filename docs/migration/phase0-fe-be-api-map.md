# Phase 0 — Frontend `dev` → Backend `refactored-architecture` API map

Status: **read-only analysis. No code changed.**
Frontend: `comfy_case_close_fe` @ `dev`
Backend: `comfy_case_close` @ `refactored-architecture` (`01e81b1 Refactor architecture from fnb`)
Old backend for reference: `comfy_case_close` @ `dev` (`comfy-case-close-app`, the monolith the FE was written against)

---

## 1. Method

1. Every call site in the FE was enumerated from `src/lib/api/endpoints.ts` (the single
   place that builds requests) and cross-checked against actual component usage.
2. Every backend route was enumerated from `@RequestMapping`/`@*Mapping` across
   `services/**`, plus the gateway's `Path=` predicates in
   `services/api-gateway/src/main/resources/application.yml`.
3. The old contract was confirmed against the monolith's controllers on branch `dev`
   rather than trusting the FE's comments.

**Path prefix note.** New controllers declare resource-relative mappings
(`@RequestMapping("/cash-closes")`); `com.fnbx.shared.config.WebConfig` adds the
`/api/v1` prefix to every `@RestController` under `com.fnbx`. So the wire paths below
are directly comparable.

---

## 2. The four systemic breaks

| # | Break | Blast radius |
|---|-------|--------------|
| B1 | **Numeric `id` → `UUID`** on branch, shiftType, user, cashClose, movement | Every FE type, every route param, every fixture |
| B2 | **`role` → `branchRoles`** — one global role replaced by `Map<branchId, UserRole>` on the JWT | `stores/auth.ts`, `AuthGuard`, `Sidebar`, `ApprovalsView`, `TopBar` |
| B3 | **One-shot submit → draft/submit lifecycle** | `CashCloseForm.tsx`, `useCashCloseDraft.ts` |
| B4 | **Sign convention inverted.** Old: `signedAmount > 0` = SHORTAGE. New: `cashDifference = countedCash − posExpectedCash`, **negative = SHORT** | Every red/green indicator, `cash-close-math.ts`, `LiveSummary`, dashboards |

B4 is the dangerous one: nothing fails, the numbers just render with the wrong colour
and the wrong sign. It is stated explicitly in `CashCloseResponse` and `CashClose`
javadoc ("Any screen that turns red on a positive number needs updating").

---

## 3. Table A — cash closes

`✅` exists · `⚠️` exists but reshaped · `❌` gap

| # | Old FE call | New backend endpoint | Verdict |
|---|-------------|----------------------|---------|
| A1 | `POST /api/v1/cash-closes` (whole form in one body) | `POST /cash-closes` (open draft) → `POST /cash-closes/{id}/movements` ×N → `PUT /cash-closes/{id}/denominations` ❌ → `POST /cash-closes/{id}/submit` | ⚠️ 1 call → 3+ calls |
| A2 | `GET /api/v1/cash-closes?branchId&shiftTypeId&fromDate&toDate&status&page&size` | `GET /cash-closes` same + `createdBy`, `submittedBy`, `cashCloseCode`, `expectedCashSource` | ⚠️ ids now UUID; sort fixed server-side (`businessDate DESC, createdAt DESC`) |
| A3 | `GET /api/v1/cash-closes/{id}` | `GET /cash-closes/{id}` | ⚠️ UUID + response reshaped (§5) |
| A4 | `GET /api/v1/cash-closes/{id}/denominations` | — | ❌ **G2** (read) |
| A5 | *(implicit, inside A1 body)* denomination write | — | ❌ **G3** (the endpoint this phase is about) |
| A6 | `GET /api/v1/cash-closes/{id}/movements` | `GET /cash-closes/{id}/movements` | ⚠️ shape fully changed (§5.3) |
| A7 | `GET /api/v1/cash-closes/{id}/tips` | folded into the movement ledger (`diff_reason_group = 'TIP'`) | ⚠️ no endpoint; totals arrive as `tipsTotal` / `tipsInDrawerTotal` on the close |
| A8 | `GET /api/v1/cash-closes/{id}/explanations` | folded into the movement ledger (kinds where `affects_difference`) | ⚠️ same |
| A9 | `GET /api/v1/cash-closes/{id}/day-summary` | — | ❌ **G4** |
| A10 | `GET /api/v1/cash-closes/carry-forward?branchId&businessDate&shiftTypeId` | — | ❌ **G5** |
| A11 | `POST /api/v1/cash-closes/{id}/approve` → `204` | `POST /cash-closes/{id}/approve` → `200 CashCloseResponse` | ⚠️ now returns a body; refuses with `MOVEMENTS_PENDING` if any line is `PENDING` |
| A12 | `POST /api/v1/cash-closes/{id}/reject` → `204` | `POST /cash-closes/{id}/reject` → `200` | ⚠️ note mandatory in both |
| A13 | `POST /api/v1/cash-closes/{id}/void` (ADMIN) | — | ❌ **G6** — `VOIDED` is in the state machine, no route reaches it |
| A14 | `GET /api/v1/cash-closes/{id}/approvals` | `GET /cash-closes/{id}/history` | ⚠️ renamed + reshaped |
| A15 | — | `POST /cash-closes/{id}/reopen` | 🆕 REJECTED / PENDING_REVIEW → DRAFT |
| A16 | — | `GET /cash-closes/movements` (paged, cross-close) | 🆕 |
| A17 | — | `PATCH /cash-closes/movements/{movementId}` | 🆕 correct a PENDING line in place |
| A18 | — | `POST /cash-closes/movements/{id}/{approve\|reject\|reopen}` | 🆕 per-line approval |
| A19 | — | `GET /cash-closes/{id}/movement-history` | 🆕 |
| A20 | *(implicit, `withdrawalAmount` in A1 body)* | — | ❌ **G14** — `cash_close.withdrawal_amount` exists but **no code path ever writes it** (`grep setWithdrawalAmount` → 0 hits) |
| A21 | *(implicit, `posExpectedCash` in A1 body)* | — | ❌ **G15** — settable only at draft creation, and only from POS sync; `MANUAL` closes are stuck at 0 |

---

## 4. Table B — everything else

| # | Old FE call | New backend | Verdict |
|---|-------------|-------------|---------|
| B1 | `POST /api/v1/auth/login` | `POST /api/v1/auth/login` | ⚠️ **different credential model** (§5.1) |
| B2 | `POST /api/v1/auth/logout` (no body → 204) | `POST /api/v1/auth/logout` (requires `{refreshToken}`) → 200 `MessageResponse` | ⚠️ |
| B3 | — | `/auth/refresh`, `/auth/me` (GET+PATCH), `/auth/signup*`, `/auth/google`, `/auth/forgot-password`, `/auth/reset-password*`, `/auth/change-password` | 🆕 FE implements none |
| B4 | `GET /api/v1/branches?page&size` | `GET /api/v1/branches?includeInactive&page&size` | ⚠️ `id`→`branchId` UUID, `isActive`→`active`, `managerEmail` **dropped**, `address` added |
| B5 | `GET /api/v1/branches/{id}` | same | ⚠️ UUID |
| B6 | — | `POST/PATCH/DELETE /branches`, `GET/PUT/DELETE /branches/{branchId}/staff/{staffId}` | 🆕 |
| B7 | `GET /api/v1/shift-types` | — | ❌ **G1** — `ShiftType` entity exists in `fnbx-entities-identity`, no controller, **no gateway route** |
| B8 | `POST /api/v1/attachments/upload` (multipart) | — | ❌ **G7** — `files-service` has no controller; gateway routes `/api/v1/files/**` only, so the FE's path is unroutable |
| B9 | `GET /cash-closes/{id}/attachments`, `DELETE /attachments/{id}` | — | ❌ G7 |
| B10 | `alertsApi.*` (5 routes) | — | ❌ **G8** — `notify-service` has no controller (FE defines but never calls these) |
| B11 | `GET/POST /api/v1/fund-withdrawals` | — | ❌ **G9** — entity + repository + **gateway route exist**, service and controller do not |
| B12 | `reportsApi.*` — 9 endpoints (`kpi`, `by-branch`, `by-date`, `by-shift-type`, `by-employee`, `risk-breakdown`, `issues`, `details`, `monthly-export`) | 4 unrelated ones: `/reports/cash-close/by-branch`, `/cash-close/staff-risk`, `/cash-close/manual-expected-ratio`, `/alerts/unacknowledged` | ❌ **G10** — only `by-branch` overlaps by name; new ones return untyped `List<Map<String,Object>>`, require `from`+`to`, take no `branchId` |
| B13 | `usersApi.*` (4 routes, `pageNumber`/`pageSize`) | — | ❌ **G11** — `identity-service` has **no** `UserController`; gateway routes `/api/v1/users/**` to a dead path. Partly covered by `/branches/{id}/staff/*` |
| B14 | `GET/PUT /api/v1/config` | — | ❌ **G12** — `AppConfig` entity in `platform-service`, no controller, gateway route exists |
| B15 | — *(new need)* movement-kind catalogue for the entry dropdown | — | ❌ **G13** — `MovementKindRepository.findAllEffective` exists, no endpoint. Without it the FE cannot populate `kindCode`, so **A1 cannot be completed at all** |

---

## 5. Field-level changes

### 5.1 Login

| Old (`LoginRequest`/`LoginResponse`) | New (`LoginRequest`/`AuthResponse`) |
|---|---|
| `employeeCode` + `passcode` (`\d{4}`) + `userAgent?` | `businessCode` + `email` + `password` |
| `token` | `accessToken` (+ `refreshToken`) |
| `expiresAt` (ISO string) | `expiresInMs` (long) |
| `user.id: number` | `user.id: UUID`, `user.businessId: UUID` |
| `user.fullName` | `user.firstName` + `user.lastName` |
| `user.role: UserRole` | `user.branchRoles: Map<UUID, UserRole>` |
| `user.isActive` | `user.active` |
| `user.branchNames: string[]` | — (only branch UUIDs, via `branchRoles` keys) |
| `user.position` | — |

`branchRoles` is also the JWT claim (`AccessPrincipal.from`), so authorisation is
per-branch: **ADMIN at branch A grants nothing at branch B**. The one exception is
`requireAnyBranch`, used for business-wide acts (join requests, branch creation).
The FE's `canAccess(role, area)` in `stores/auth.ts` has no per-branch notion.

### 5.2 `CashClose` → `CashCloseResponse`

| Old | New | Note |
|---|---|---|
| `id: number` | `cashCloseId: UUID` | |
| `referenceCode` | `cashCloseCode` | |
| `branchId/shiftTypeId: number` | `UUID` | |
| `submittedByUserId` + `submittedByName` | `createdBy: UUID` only | no submitter name, no display names anywhere |
| `countedCash` **(input)** | `countedCash` **(derived)** | `SUM(face_value × quantity)` in `v_close_calc_v1`; no column, no input |
| `cashDiff` | `cashDifference` | **sign inverted** (B4) |
| `explainedDiff` / `unexplainedDiff` | `explainedDifference` / **`pendingDifference`** / `unexplainedDifference` | three figures, not two: pending = declared but unreviewed |
| `totalExpense` | `expenseTotal` (+ `cashOutTotal`, `cashInTotal`) | expense filtered by `expense_category`, not by cash direction |
| `tipsAmount` | `tipsTotal` + `tipsInDrawerTotal` | |
| `endOfDayExpenseAmount` | — | folded into movement kinds |
| `systemPotBefore/After`, `countedCashTotal` | — | |
| `cashRemaining` | `cashRemaining` | now `countedCash − withdrawal − Σabs(affects_remaining)` |
| `isLateSubmission` | `late` | |
| `approvalStatus`, `managerReviewNote` | — | reviewer comments live in `/history` |
| `notes` | `note` | |
| `movements[]`, `explanations[]`, `attachments[]`, `approvals[]` (embedded) | — | all fetched separately |
| — | `expectedCashSource`, `expectedCashLocked`, `calcVersion`, `appliedDiffAllowedAbs`, `appliedDiffAlertAbs`, `posExpectedCash` | 🆕 |
| `riskLevel: LOW\|MEDIUM\|HIGH\|CRITICAL` | `LOW\|MEDIUM\|HIGH` | **CRITICAL removed** |
| `status: SUBMITTED\|PENDING_REVIEW\|APPROVED\|REJECTED\|VOIDED\|OK` | `DRAFT\|SUBMITTED\|PENDING_REVIEW\|APPROVED\|REJECTED\|VOIDED` | `OK` gone, `DRAFT` added |

### 5.3 Movements — the model changed, not just the names

Old: `{category, type, amount, reason, description, affectsDiff}` with four FE enums
(`MOVEMENT_CATEGORIES`, `MOVEMENT_TYPES`, `DIFF_REASON_TYPES`, `DIFF_DIRECTIONS`).

New: `{kindCode, amount (always positive), staffUserId?, description?, receiptAttachmentId?}`.
`kindCode` resolves against `platform.movement_kind`, an **SCD-2 catalogue versioned by
the close's business date**, and the kind decides the sign, whether the line affects the
difference, whether it affects cash remaining, whether it is an expense, whether a
note/receipt is required. Response adds `signedAmount` + `absAmount` + `effectType`
(`CASH_IN`/`CASH_OUT`/`NO_CASH_FLOW`) and a **per-line approval status**
(`PENDING`/`APPROVED`/`REJECTED`) that did not exist before.

So the FE's four enums become **one server-driven dropdown** — which needs G13.

### 5.4 Errors

| | Old | New |
|---|---|---|
| Body | `{timestamp, status, error, message, path}` | `{timestamp, code, message, path, fieldErrors[], result}` |
| `code` | — | numeric app code (2000–9999), **not** the HTTP status |
| Conflicts | `409` | **`422`** for `CLOSE_ALREADY_EXISTS` (3001), `CLOSE_FROZEN` (3006), `ILLEGAL_TRANSITION` (3009), `MOVEMENT_DECIDED` (3007) |

`ApiError.isConflict` (`status === 409`) in `client.ts` will therefore **never fire** for
any cash-close domain error. See decision **D2**.

### 5.5 What survives unchanged

`PagedResponse<T>` — `{content, page, size, totalElements, totalPages, last}` is
byte-identical between old and new. It is the only envelope that needs no work.

---

## 6. Gap register

| ID | Gap | Service | Blocks |
|---|---|---|---|
| G1 | `GET /shift-types` | identity | opening any draft |
| G2 | `GET /cash-closes/{id}/denominations` | cashclose | history detail view |
| G3 | `PUT /cash-closes/{id}/denominations` | cashclose | **submitting anything** (submit requires ≥1 line) |
| G4 | `GET /cash-closes/{id}/day-summary` | cashclose | history detail |
| G5 | `GET /cash-closes/carry-forward` | cashclose | close form |
| G6 | `POST /cash-closes/{id}/void` | cashclose | admin |
| G7 | attachments upload / list / delete | files | close form |
| G8 | alerts (5 routes) | notify | nothing the FE actually calls |
| G9 | fund withdrawals (2 routes) | cashclose | `/withdraw` page |
| G10 | reports (9 routes) | reporting | `/dashboard` page |
| G11 | users (4 routes) | identity | `/users` page |
| G12 | `GET/PUT /config` | platform | thresholds, every form validation |
| G13 | `GET /movement-kinds?businessDate=` | platform or cashclose | **any movement entry** |
| G14 | write `withdrawalAmount` | cashclose | cash-remaining arithmetic |
| G15 | write `posExpectedCash` when `MANUAL` | cashclose | any close where POS is down |

**G3, G13, G14, G15 together mean the new backend cannot currently produce a single
complete, submittable cash close.** That is the honest headline of Phase 0.

---

## 7. What Phase 1 needs decided first

### D1 — where does the branch come from, and what verifies it?

Today: `OpenDraftRequest.branchId` in the body, verified by
`AccessPrincipal.requireBranch()` against the **signed `branch_roles` JWT claim**.
The brief asks for `X-Branch-Id` verified against **`identity.staff_branch_role`**.

Findings that bear on it:
- `SubmitRequest` is `{note}` only — there is no `branchId` in submit to remove. Only
  `openDraft` is affected.
- `svc_cashclose` already has **read grants on the `identity` schema**
  (`910-roles/001-roles-grants.sql` line 102), and `StaffBranchRole` is in the shared
  `fnbx-entities-identity` lib. So reading the table needs **no DDL and no grant change** —
  just a repository in cashclose-service.
- `X-Branch-Id` must be added to `cors.setAllowedHeaders(...)` in
  `ServletSecurityConfiguration`, or the browser preflight fails. The gateway strips
  `X-Business-Id`/`X-User-Id`/`X-Role`/`X-Staff-Id`; `X-Branch-Id` must **not** be added to
  that strip list, and is only safe because the service re-verifies it.
- Reading the DB instead of the JWT claim means a revoked assignment takes effect
  immediately rather than at token expiry — a real gain. It also means a DB round trip
  per request and a second source of truth alongside the signed claim.

**Choose:** (a) JWT claim stays authoritative, header is only a scope selector; or
(b) DB is authoritative, JWT claim becomes a hint. My recommendation is **(b) with the
JWT claim as a cheap pre-filter** — reject on the claim first, then confirm against the
table — so a revoked assignment cannot act, and an unrelated branch never reaches SQL.

### D2 — 409 or 422?

The brief says non-DRAFT edit/submit → **409**. `ErrorCode` maps `CLOSE_FROZEN` and
`ILLEGAL_TRANSITION` to **422**, and the enum's own comment says *"never reuse a published
numeric code"*. Options:
1. Add new codes (e.g. `CLOSE_NOT_DRAFT(3014, CONFLICT)`) for the denomination endpoint only — no existing code changes status. **Recommended.**
2. Change 3006/3009 to `CONFLICT` — smaller code, but silently restates a published contract.
3. Accept 422 and write the tests against 422.

### D3 — DRAFT-only is stricter than the database

`CloseStatus.isEditable()` = `DRAFT | SUBMITTED | PENDING_REVIEW`, and the DB trigger
`fn_close_child_guard` allows child writes in all three. A DRAFT-only rule on
`PUT /denominations` is a deliberate tightening — a manager could not correct a miscount
during review without `reopen` first. Confirm that is intended.

### D4 — denomination identity in the request body

`cash_denomination_line.denomination_id` is a `SMALLINT` FK into `platform.denomination`
(9 seeded VND rows). The old FE sends **face value** (`denominationValue: 500000`).
Recommend accepting `{faceValue, quantity}` and resolving to `denomination_id`
server-side: the FE already speaks face value, and the surrogate id is an
implementation detail.

### D5 — Phase 1 scope

Read literally, "add missing endpoints from Phase 0" is 15 gaps across 6 services. That is
not one branch. Recommend Phase 1 = **cashclose-service only**:

- `PUT /cash-closes/{id}/denominations` (replace full set, DRAFT only) + `GET` (G2/G3)
- `PATCH /cash-closes/{id}` for `withdrawalAmount` and `posExpectedCash`-when-MANUAL (G14/G15)
- `GET /movement-kinds` (G13)
- `POST /cash-closes/{id}/void` (G6)
- `X-Branch-Id` resolution + role derivation, per D1
- Integration tests: wrong-branch header → 403; non-DRAFT edit/submit → 409-or-422 per D2

Everything else (G1, G4, G5, G7–G12) becomes Phase 2, per service.

---

## 8. DDL verdict

**No DDL change is required for the denomination endpoint.** Verified:

- `cashclose.cash_denomination_line` exists with `UNIQUE (cash_close_id, denomination_id)`
  and `ON DELETE CASCADE` (`007-cashclose/001-tables.sql` L178–188).
- `trg_cash_denomination_line_guard` already blocks writes once a close leaves
  `DRAFT/SUBMITTED/PENDING_REVIEW` (`002-integrity.sql` L36–92).
- `counted_cash` is already `SUM(face_value × quantity)` in `v_close_calc_v1`
  (`002-integrity.sql` L388–394) and **no `counted_cash` column exists** on `cash_close`,
  so "no independent input" is structurally guaranteed, not merely enforced.
- Submit already refuses without a count, in both Java (`NO_DENOMINATION_COUNT`) and the
  DB trigger (`002-integrity.sql` L204–206).

`svc_cashclose` read grants on `identity` and `platform` are already in place, so D1(b)
needs no changeset either.
