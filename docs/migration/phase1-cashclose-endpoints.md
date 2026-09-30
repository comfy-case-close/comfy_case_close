# Cashclose API migration: dev → feat/phase1-cashclose-endpoints

The backend implements the current cashclose DDL rather than merging dev's legacy persistence model. A close is created and submitted in one request. Base URL: `/api/v1/cash-closes`.

## Every endpoint in dev's CashCloseController

| dev method/path (relative to base) | Current contract | Disposition |
|---|---|---|
| GET / | Paginated closes; UUID branch/shift IDs, current statuses, view-derived totals. Optional branch/date/status/source/creator filters. | Retained and refactored |
| POST / | Submits a new close using `X-Branch-Id` and a body containing shift, date, count, optional figures and movements. | Retained as one-step submission |
| GET /{id} | UUID ID; close metadata and derived calculations. Child collections use their own endpoints. | Retained and refactored |
| GET /{id}/denominations | `{cashCloseId,status,lines:[{lineId,faceValue,quantity,lineTotal}],countedCash}`, not the old array. | Retained; response changed |
| GET /{id}/movements | Unified ledger including physical cash movements, tips and discrepancy explanations. Each line includes kind, calculation flags, signed/absolute amounts and approval state. | Retained; expanded |
| GET /{id}/tips | Use `GET /api/v1/tips?shiftTypeId=...&businessDate=...` with `X-Branch-Id` to list `TIPS` movements and shift totals. Create through POST /{id}/movements with kindCode `TIPS`. | Replaced by shift tips endpoint |
| GET /{id}/explanations | Read the unified movements; kinds and `affectsDifference` explain their meaning. Pure discrepancy reasons use `NO_CASH_FLOW`; expenses and in-drawer tips can also explain differences. | Removed; replaced by ledger |
| GET /{id}/day-summary | `{branchId,businessDate,closes:[CashCloseResponse]}` for the anchor close's branch/day; excludes VOIDED and includes each other status explicitly. | Refactored to independent shift snapshots |
| GET /carry-forward | No replacement. The new DDL does not model cumulative sales, an opening drawer balance, or final-shift carry-forward. | Removed |

Do not sum shift drawer counts into a supposed day-end balance or carry prior shifts' sales into `posExpectedCash`. The current view computes each close independently. The day summary deliberately has no cumulative/final-shift figures. If that business process is required later, introduce an explicit opening/closing balance model and versioned formulas first.

Identifiers are UUIDs, except global denomination IDs. Legacy numeric IDs and the monolithic dev submission body are not compatible with these contracts.

## Current endpoint inventory

Every path below is relative to `/api/v1/cash-closes`.

| Area | Endpoints |
|---|---|
| Read | GET /, GET /{id}, GET /{id}/day-summary |
| Submission and count | POST /, GET /{id}/denominations |
| Lifecycle | POST /{id}/approve, /reject, /void; POST /{id}/corrections for reviewer edits |
| Catalogs | GET /shift-types, GET /denominations, GET /movement-kinds?businessDate=YYYY-MM-DD |
| Ledger | GET /movements (paginated/filterable), GET /{id}/movements, POST /{id}/movements, PATCH /movements/{movementId} |
| Movement decisions | POST /movements/{movementId}/approve, /reject, /reopen |
| Audit | GET /{id}/history, GET /{id}/movement-history |
| Existing file references | GET /{id}/attachments, POST /{id}/attachments |

Catalog details:
- Shift types are active definitions for the authenticated business, ordered by sort order/code.
- Denominations are active notes/coins in the business currency, largest first.
- Movement kinds are resolved on the close's business date. A tenant-specific version overrides a global version with the same code; the catalog returns one effective choice.
- Omitted catalog business date uses the business timezone. Prefer passing the close date explicitly.
- Movement-kind metadata includes `requiresNote`, `requiresReceipt`, `effectType`, `affectsDifference`, `affectsRemaining`, `expenseCategory` and `diffReasonGroup`.

## Frontend request sequence

Use the selected branch as `X-Branch-Id` on **every write**, including approvals and movement decisions. Business identity always comes from the signed JWT; do not send a business ID.

1. Fetch catalogs; choose an active shift and count drawer and tip-jar cash together.
2. POST / with `{"shiftTypeId":"<uuid>","businessDate":"2026-09-21","denominations":{"counts":[{"faceValue":500000,"quantity":1}]},"figures":{"posExpectedCash":500000,"withdrawalAmount":100000},"movements":[{"kindCode":"TIPS","amount":10000}],"note":"Shift complete"}`. This creates a SUBMITTED close. The count is required; figures, movements and note are optional. Expected cash is editable only for MANUAL closes; POS_SYNC uses the latest POS revision at submission. Keep the returned `cashCloseId`.
3. Additional movement rows can be submitted through POST /{id}/movements, for example:
   - `{"kindCode":"TIPS","amount":10000}` for each tip received, regardless of whether it was placed in the drawer or jar. Customer change given from the jar is already reflected in POS cash and needs no extra expense movement.
   - `{"kindCode":"POS_ERROR","amount":10000,"differenceDirection":"OVER","description":"POS correction"}`
4. For receipt-required kinds, create the attachment first and reference its `attachmentId` in a later movement. Review pending movements before approving the close.
5. Review the close with approve/reject. A reviewer may correct a submitted close through POST /{id}/corrections; the correction records an audit decision and sets PENDING_REVIEW. ADMIN may void with a reason.

After approving the tip movements, GET `/api/v1/tips?shiftTypeId=<uuid>&businessDate=2026-09-21` with the selected `X-Branch-Id`. `totalTips` sums approved TIPS movements; `pendingTips` and `pendingCount` show tips still under review. Rejected lines remain visible in `movements` but are excluded from both totals. The staff split the approved amount themselves; there is no payout API or tip-jar balance.

All monetary inputs obey the DDL's NUMERIC(14,2) precision. `amount` is positive; CASH_IN becomes positive, CASH_OUT negative. For NO_CASH_FLOW, `differenceDirection` is SHORT (negative, default) or OVER (positive). An amount-only patch preserves an existing discrepancy's direction. A direction on a physical cash movement is rejected.

Submission includes the initial count and typed figures. Corrections use POST /{id}/corrections. Movement rows and file links are editable while the close is SUBMITTED or PENDING_REVIEW. A movement must itself be PENDING to edit; reopening a decided movement creates an audit entry.

## Branch authorization

- All writes require a valid UUID `X-Branch-Id` matching the parent close.
- The branch must occur in the signed token and still have a live branch position or explicit permission grant for an active staff member and active branch.
- Review actions require the live `CLOSE_REVIEW` permission; void requires `CLOSE_VOID` at that branch.
- Reads validate live access too. Lists use the intersection of signed branches and live assignments. An optional branch filter narrows that set; it never expands it.
- A resource outside the token's branches or tenant is hidden as 404. A stale/revoked assignment or mismatched branch header is 403. Missing/malformed headers are 400.
- New branch membership requires a refreshed token. Permission changes/revocations for an existing signed branch apply immediately.
- Close decisions record the live authorizing permission. All mutations lock the parent close so counting, editing and lifecycle transitions cannot overwrite one another concurrently.
- Database RLS and composite foreign keys remain active under the least-privileged `svc_cashclose` role.

## Schema fixes and behavior preserved

- CashClose, CashCloseDecision and CloseAttachment bind actual PostgreSQL named enums. String binding passed mocked tests but failed PostgreSQL status comparisons.
- Calculated totals remain in `cashclose.v_close_calc`; Java never writes duplicate total columns. Pending writes are flushed and already-managed calculation rows refreshed before returning close totals.
- A replacement after VOID receives a distinct close code, satisfying both the all-history code uniqueness constraint and the live branch/shift/date uniqueness constraint.
- Submission reads back DB-generated thresholds/lateness; new resources and decisions read back generated timestamps.
- Invalid shift, movement note/receipt and cross-close attachment references are rejected before persistence.
- Native DDL state transitions, POS locks and append-only audit triggers are preserved.

## Scope and verification

The submitted-only change passed the cashclose and identity Maven test suites. Real-database integration tests require a disposable PostgreSQL database and have not been rerun for this change.

This changes the backend contract, not the frontend deployment. The old dev frontend must adopt the request sequence and response shapes above.

Attachment APIs reference existing `files.stored_file` rows. The files-service upload and signed URL endpoints are described in [the latest dev port](dev-2026-09-24-port.md). Dev's dashboard and standalone approval controllers remain separate from the nine CashCloseController endpoints compared above.

Run with Java 21, Maven, Python 3 and Docker:

```sh
bash tools/test-cashclose-api.sh
```

The script creates a disposable PostgreSQL 16 database, applies SQL files in master-changelog order (respecting transaction boundaries), enables only the test service login, runs the cashclose reactor tests, and removes the database. No existing application database is used.

The real-database suite exercises authenticated HTTP requests through Spring's security filter, service-role RLS, JPA mappings and DDL triggers: catalogs, one-step submission, reviewer correction, calculated totals, signed explanations, movement decisions/history, rejection, approval, void/replacement, POS revision locking, attachments, tenant isolation, wrong branch, revoked membership, and stale role claims. It is enabled by `FNB_CASHCLOSE_TEST_DB_URL`; ordinary Maven runs skip it unless the disposable database is supplied.
