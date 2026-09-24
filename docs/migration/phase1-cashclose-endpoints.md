# Cashclose API migration: dev → feat/phase1-cashclose-endpoints

The backend implements the current cashclose DDL rather than merging dev's legacy persistence model. Existing phase-1 draft/count changes are retained and completed. Base URL: `/api/v1/cash-closes`.

## Every endpoint in dev's CashCloseController

| dev method/path (relative to base) | Current contract | Disposition |
|---|---|---|
| GET / | Paginated closes; UUID branch/shift IDs, current statuses, view-derived totals. Optional branch/date/status/source/creator filters. | Retained and refactored |
| POST / | Opens a DRAFT using `X-Branch-Id` and `{shiftTypeId,businessDate}`. Count, movements and submission are separate requests. | Replaces one-shot submission |
| GET /{id} | UUID ID; close metadata and derived calculations. Child collections use their own endpoints. | Retained and refactored |
| GET /{id}/denominations | `{cashCloseId,status,lines:[{lineId,faceValue,quantity,lineTotal}],countedCash}`, not the old array. | Retained; response changed |
| GET /{id}/movements | Unified ledger including physical cash movements, tips and discrepancy explanations. Each line includes kind, calculation flags, signed/absolute amounts and approval state. | Retained; expanded |
| GET /{id}/tips | Read the unified movements and select `TIP_IN_DRAWER` / `TIP_DIRECT`. Create through POST /{id}/movements. | Removed; replaced by ledger |
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
| Draft | POST /, PATCH /{id}, PUT /{id}/denominations, GET /{id}/denominations |
| Lifecycle | POST /{id}/submit, /approve, /reject, /reopen, /void |
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

1. Fetch catalogs; choose an active shift.
2. POST / with `{"shiftTypeId":"<uuid>","businessDate":"2026-09-21"}`. Keep the returned `cashCloseId`.
3. PATCH /{id} with, for example, `{"posExpectedCash":500000,"withdrawalAmount":100000}`. Null/omitted fields stay unchanged. Expected cash is editable only for MANUAL closes; POS_SYNC uses the latest POS revision available when the draft was created.
4. PUT /{id}/denominations with `{"counts":[{"faceValue":500000,"quantity":1}]}`. This replaces the complete set atomically. Repeating the request does not duplicate the count. Zero quantities omit rows; duplicate face values or unknown/inactive denominations are rejected.
5. POST /{id}/movements for each expense, tip or explanation:
   - `{"kindCode":"TIP_IN_DRAWER","amount":10000}`
   - `{"kindCode":"POS_ERROR","amount":10000,"differenceDirection":"OVER","description":"POS correction"}`
6. For receipt-required kinds, first POST /{id}/attachments with `{"fileId":"<existing-files.stored_file-uuid>","fileKind":"RECEIPT"}`, then use its `attachmentId` as the movement's `receiptAttachmentId`.
7. Review discrepancy movements when required by the business rule. A pending discrepancy can prevent submission. Movement approval uses the separate approve/reject endpoints; pending movements also block close approval.
8. POST /{id}/submit with optional `{"note":"Shift complete"}`. Refresh GET /{id} after movement decisions to display current calculations.
9. Review the close with approve/reject. Reopen REJECTED or PENDING_REVIEW closes before changing counts or typed figures. ADMIN may void a close with a reason.

All monetary inputs obey the DDL's NUMERIC(14,2) precision. `amount` is positive; CASH_IN becomes positive, CASH_OUT negative. For NO_CASH_FLOW, `differenceDirection` is SHORT (negative, default) or OVER (positive). An amount-only patch preserves an existing discrepancy's direction. A direction on a physical cash movement is rejected.

Counts and typed figures require DRAFT. Movement rows and file links follow the DDL's editable close states: DRAFT, SUBMITTED, PENDING_REVIEW. A movement must itself be PENDING to edit; reopening a decided movement creates an audit entry.

## Branch authorization

- All writes require a valid UUID `X-Branch-Id` matching the parent close.
- The branch must occur in the signed token and still have a live `identity.staff_branch_role` assignment for an active staff member and active branch.
- Review actions require the **live** MANAGER, ADMIN or ACCOUNTANT role at that branch. Void requires ADMIN there.
- Reads validate live access too. Lists use the intersection of signed branches and live assignments. An optional branch filter narrows that set; it never expands it.
- A resource outside the token's branches or tenant is hidden as 404. A stale/revoked assignment or mismatched branch header is 403. Missing/malformed headers are 400.
- New branch membership requires a refreshed token. Role changes/revocations for an existing signed branch apply immediately.
- Close decisions record the live authorizing role. All mutations lock the parent close so counting, editing and lifecycle transitions cannot overwrite one another concurrently.
- Database RLS and composite foreign keys remain active under the least-privileged `svc_cashclose` role.

## Schema fixes and behavior preserved

- CashClose, CashCloseDecision and CloseAttachment bind actual PostgreSQL named enums. String binding passed mocked tests but failed PostgreSQL status comparisons.
- Calculated totals remain in `cashclose.v_close_calc`; Java never writes duplicate total columns. Pending writes are flushed and already-managed calculation rows refreshed before returning close totals.
- A replacement after VOID receives a distinct close code, satisfying both the all-history code uniqueness constraint and the live branch/shift/date uniqueness constraint.
- Submission reads back DB-generated thresholds/lateness; new resources and decisions read back generated timestamps.
- Invalid shift, movement note/receipt and cross-close attachment references are rejected before persistence.
- Native DDL state transitions, POS locks and append-only audit triggers are preserved.

## Scope and verification

Verified on 2026-09-22: all 22 Maven reactor modules passed; the fresh PostgreSQL cashclose run passed 76 tests, including six real-database integration tests. Other services' environment-gated database suites were not enabled in the full reactor run.

This changes the backend contract, not the frontend deployment. The old dev frontend must adopt the request sequence and response shapes above.

Attachment APIs reference existing `files.stored_file` rows. Uploading bytes and issuing download URLs belong to files-service and are not implemented here. Dev's attachment upload, dashboard and standalone approval controllers are separate from the nine CashCloseController endpoints compared above; this is not a wholesale dev merge.

Run with Java 21, Maven, Python 3 and Docker:

```sh
bash tools/test-cashclose-api.sh
```

The script creates a disposable PostgreSQL 16 database, applies SQL files in master-changelog order (respecting transaction boundaries), enables only the test service login, runs the cashclose reactor tests, and removes the database. No existing application database is used.

The real-database suite exercises authenticated HTTP requests through Spring's security filter, service-role RLS, JPA mappings and DDL triggers: catalogs, draft uniqueness, recount replacement, calculated totals, signed explanations, movement decisions/history, submission, rejection/reopening, approval, void/replacement, POS revision locking, attachments, tenant isolation, wrong branch, revoked membership, and stale role claims. It is enabled by `FNB_CASHCLOSE_TEST_DB_URL`; ordinary Maven runs skip it unless the disposable database is supplied.
